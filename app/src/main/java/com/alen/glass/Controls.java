/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextPaint;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** popup in vetro per wi-fi, bluetooth, audio e video */
final class Controls {
    private Controls() {}

    static final ComponentName AV_SETTINGS = new ComponentName("com.sagemcom.stb.tvinput", "com.sagemcom.stb.base.ui.AvIoSettingsActivity");
    static final ComponentName XIAOMI_DISPLAY = new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.displaysound.DisplaySoundActivity");

    static void openSystem(Context c, Intent i, ComponentName fallback) {
        Intent[] tries = {i, fallback == null ? null : new Intent().setComponent(fallback), new Intent(Settings.ACTION_SETTINGS)};
        for (Intent t : tries) {
            if (t == null) continue;
            try { c.startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Exception ignored) { }
        }
    }

    // =====================================================================================
    // WI-FI
    // =====================================================================================

    @SuppressLint("MissingPermission")
    static void wifi(FrameLayout root, View... block) {
        Context c = root.getContext();
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Popup p = Popup.show(root, "Wi-Fi", 520, block);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            List<Popup.Item> it = Popup.items();
            boolean on = wm.isWifiEnabled();
            it.add(new Popup.Item("Wi-Fi").trail(on ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF)
                    .on(() -> { wm.setWifiEnabled(!wm.isWifiEnabled()); root.postDelayed(refresh[0], 900); }));
            String eth = ethernetInfo(c);
            if (eth != null) it.add(new Popup.Item("Ethernet").sub(eth).trail(Popup.Trail.CHECK).disabled());
            if (on) {
                WifiInfo cur = wm.getConnectionInfo();
                String curSsid = cur == null ? null : strip(cur.getSSID());
                boolean connected = curSsid != null && !curSsid.equals("<unknown ssid>") && cur.getNetworkId() != -1;
                if (connected) {
                    it.add(new Popup.Item("Rete attuale").header());
                    int lv = WifiManager.calculateSignalLevel(cur.getRssi(), 5);
                    String band = cur.getFrequency() > 4900 ? "5 GHz" : "2,4 GHz";
                    it.add(new Popup.Item(curSsid).sub("Connessa · " + band + " · " + cur.getLinkSpeed() + " Mbps")
                            .trail(Popup.Trail.CHECK).on(() -> forgetPrompt(root, wm, curSsid, refresh[0])));
                }
                it.add(new Popup.Item("Reti disponibili").header());
                Map<String, ScanResult> best = new HashMap<>();
                List<ScanResult> res;
                try { res = wm.getScanResults(); } catch (Exception e) { res = new ArrayList<>(); }
                for (ScanResult r : res) {
                    if (r.SSID == null || r.SSID.isEmpty() || (connected && r.SSID.equals(curSsid))) continue;
                    ScanResult o = best.get(r.SSID);
                    if (o == null || r.level > o.level) best.put(r.SSID, r);
                }
                List<ScanResult> sorted = new ArrayList<>(best.values());
                sorted.sort((a, b) -> Integer.compare(b.level, a.level));
                if (sorted.isEmpty()) it.add(new Popup.Item("Ricerca reti…").disabled());
                for (ScanResult r : sorted) {
                    boolean secure = r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("SAE");
                    it.add(new Popup.Item(r.SSID).signal(WifiManager.calculateSignalLevel(r.level, 5), secure)
                            .on(() -> connect(root, wm, r, refresh[0])));
                }
            }
            it.add(new Popup.Item("Altre impostazioni Wi-Fi").trail(Popup.Trail.CHEVRON)
                    .on(() -> { p.close(); openSystem(c, new Intent(Settings.ACTION_WIFI_SETTINGS), null); }));
            p.set(it);
        };
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context x, Intent i) { refresh[0].run(); }
        };
        IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        c.registerReceiver(rx, f);
        p.onClose(() -> { try { c.unregisterReceiver(rx); } catch (Exception ignored) { } });
        refresh[0].run();
        try { wm.startScan(); } catch (Exception ignored) { }
    }

    private static String strip(String s) {
        if (s == null) return null;
        return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static String ethernetInfo(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    int down = nc.getLinkDownstreamBandwidthKbps() / 1000;
                    return "Connessa via cavo" + (down > 0 ? " · " + down + " Mbps" : "");
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    @SuppressLint("MissingPermission")
    private static void connect(FrameLayout root, WifiManager wm, ScanResult r, Runnable refresh) {
        Context c = root.getContext();
        // rete gia' salvata: basta attivarla
        try {
            List<WifiConfiguration> saved = wm.getConfiguredNetworks();
            if (saved != null) for (WifiConfiguration w : saved) {
                if (r.SSID.equals(strip(w.SSID))) {
                    wm.enableNetwork(w.networkId, true);
                    wm.reconnect();
                    Sheet.toast(root, "Connessione a " + r.SSID + "…");
                    return;
                }
            }
        } catch (Exception ignored) { }
        boolean wep = r.capabilities.contains("WEP");
        boolean secure = wep || r.capabilities.contains("WPA") || r.capabilities.contains("SAE");
        if (!secure) { join(root, wm, r, null, false); return; }
        // richiesta password in un popup dedicato
        Popup pw = Popup.show(root, r.SSID, 520);
        pw.sub("Inserisci la password della rete");
        EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.argb(140, 255, 255, 255));
        field.setHint("Password");
        field.setTypeface(Ui.textMedium());
        field.setTextSize(17);
        int ph = Popup.dp(c, 18);
        field.setPadding(ph, 0, ph, 0);
        field.setBackground(new BrowserActivity.Capsule(c));
        pw.add(field, 54);
        boolean[] show = {false};
        Runnable[] rows = new Runnable[1];
        rows[0] = () -> {
            // ricostruisce solo le righe sotto al campo
            while (pw.list.getChildCount() > 1) pw.list.removeViewAt(1);
            List<Popup.Item> it = Popup.items();
            it.add(new Popup.Item("Mostra password").trail(show[0] ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF).on(() -> {
                show[0] = !show[0];
                field.setInputType(InputType.TYPE_CLASS_TEXT | (show[0] ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                field.setTypeface(Ui.textMedium());
                rows[0].run();
            }));
            it.add(new Popup.Item("Connetti").on(() -> {
                String pass = field.getText().toString();
                if (pass.length() < (wep ? 5 : 8)) { Sheet.toast(root, "Password troppo corta"); return; }
                pw.close();
                join(root, wm, r, pass, wep);
            }));
            for (Popup.Item i : it) {
                Popup.Row row = new Popup.Row(c, i);
                pw.list.addView(row, new android.widget.LinearLayout.LayoutParams(-1, Popup.dp(c, 54)));
            }
        };
        rows[0].run();
        field.setOnEditorActionListener((v, id, ev) -> { pw.list.getChildAt(2).performClick(); return true; });
        field.post(() -> {
            field.requestFocus();
            ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @SuppressLint("MissingPermission")
    private static void join(FrameLayout root, WifiManager wm, ScanResult r, String pass, boolean wep) {
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + r.SSID + "\"";
        if (pass == null) {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if (wep) {
            conf.wepKeys[0] = "\"" + pass + "\"";
            conf.wepTxKeyIndex = 0;
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else {
            conf.preSharedKey = "\"" + pass + "\"";
            if (r.capabilities.contains("SAE") && !r.capabilities.contains("PSK")) conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
            else conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        }
        int id = wm.addNetwork(conf);
        if (id == -1) { Sheet.toast(root, "Impossibile salvare la rete"); return; }
        wm.disconnect();
        wm.enableNetwork(id, true);
        wm.reconnect();
        Sheet.toast(root, "Connessione a " + r.SSID + "…");
    }

    @SuppressLint("MissingPermission")
    private static void forgetPrompt(FrameLayout root, WifiManager wm, String ssid, Runnable refresh) {
        Popup p = Popup.show(root, ssid, 440);
        List<Popup.Item> it = Popup.items();
        it.add(new Popup.Item("Dimentica questa rete").red().on(() -> {
            try {
                for (WifiConfiguration w : wm.getConfiguredNetworks()) if (ssid.equals(strip(w.SSID))) wm.removeNetwork(w.networkId);
                wm.saveConfiguration();
            } catch (Exception ignored) { }
            p.close();
            root.postDelayed(refresh, 800);
        }));
        it.add(new Popup.Item("Annulla").on(p::close));
        p.set(it);
    }

    // =====================================================================================
    // BLUETOOTH
    // =====================================================================================

    @SuppressLint("MissingPermission")
    static void bluetooth(FrameLayout root, View... block) {
        Context c = root.getContext();
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) { Sheet.toast(root, "Bluetooth non disponibile"); return; }
        Popup p = Popup.show(root, "Bluetooth", 520, block);
        Map<String, BluetoothDevice> found = new HashMap<>();
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            List<Popup.Item> it = Popup.items();
            boolean on = bt.isEnabled();
            it.add(new Popup.Item("Bluetooth").trail(on ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF).on(() -> {
                if (!bt.isEnabled()) { bt.enable(); root.postDelayed(refresh[0], 1200); return; }
                // il telecomando del box e' bluetooth: spegnere richiede conferma
                Popup ask = Popup.show(root, "Disattivare il Bluetooth?", 480);
                ask.sub("Il telecomando potrebbe smettere di funzionare finch\u00e9 non lo riattivi");
                List<Popup.Item> q = Popup.items();
                q.add(new Popup.Item("Annulla").on(ask::close));
                q.add(new Popup.Item("Disattiva").red().on(() -> { ask.close(); bt.disable(); root.postDelayed(refresh[0], 1200); }));
                ask.set(q);
            }));
            if (on) {
                Set<BluetoothDevice> bonded = bt.getBondedDevices();
                it.add(new Popup.Item("Dispositivi associati").header());
                if (bonded == null || bonded.isEmpty()) it.add(new Popup.Item("Nessun dispositivo").disabled());
                else for (BluetoothDevice d : bonded) {
                    boolean conn = isConnected(d);
                    it.add(new Popup.Item(name(d)).sub(kind(d) + (conn ? " · Connesso" : ""))
                            .trail(conn ? Popup.Trail.CHECK : Popup.Trail.CHEVRON)
                            .on(() -> deviceMenu(root, d, refresh[0])));
                }
                it.add(new Popup.Item(bt.isDiscovering() ? "Altri dispositivi · ricerca…" : "Altri dispositivi").header());
                int shown = 0;
                for (BluetoothDevice d : found.values()) {
                    if (d.getBondState() == BluetoothDevice.BOND_BONDED) continue;
                    shown++;
                    boolean bonding = d.getBondState() == BluetoothDevice.BOND_BONDING;
                    it.add(new Popup.Item(name(d)).sub(bonding ? "Abbinamento in corso…" : kind(d)).on(() -> {
                        bt.cancelDiscovery();
                        d.createBond();
                        Sheet.toast(root, "Abbinamento con " + name(d) + "…");
                    }));
                }
                if (shown == 0) it.add(new Popup.Item(bt.isDiscovering() ? "Metti il dispositivo in modalità abbinamento" : "Nessun dispositivo trovato").disabled());
                it.add(new Popup.Item("Cerca di nuovo").on(() -> { found.clear(); bt.cancelDiscovery(); bt.startDiscovery(); refresh[0].run(); }));
            }
            it.add(new Popup.Item("Telecomandi e accessori").trail(Popup.Trail.CHEVRON).on(() -> {
                p.close();
                openSystem(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                        new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AccessoriesActivity"));
            }));
            p.set(it);
        };
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override public void onReceive(Context x, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (BluetoothDevice.ACTION_FOUND.equals(i.getAction()) && d != null) found.put(d.getAddress(), d);
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(i.getAction()) && d != null
                        && d.getBondState() == BluetoothDevice.BOND_BONDED) Sheet.toast(root, name(d) + " associato");
                refresh[0].run();
            }
        };
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        c.registerReceiver(rx, f);
        p.onClose(() -> { try { c.unregisterReceiver(rx); bt.cancelDiscovery(); } catch (Exception ignored) { } });
        refresh[0].run();
        if (bt.isEnabled()) bt.startDiscovery();
    }

    @SuppressLint("MissingPermission")
    private static String name(BluetoothDevice d) {
        String n = d.getName();
        return n == null || n.isEmpty() ? d.getAddress() : n;
    }

    @SuppressLint("MissingPermission")
    private static String kind(BluetoothDevice d) {
        BluetoothClass bc = d.getBluetoothClass();
        if (bc == null) return "Dispositivo";
        switch (bc.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "Audio";
            case BluetoothClass.Device.Major.PERIPHERAL: return "Telecomando o tastiera";
            case BluetoothClass.Device.Major.PHONE: return "Telefono";
            case BluetoothClass.Device.Major.COMPUTER: return "Computer";
            default: return "Dispositivo";
        }
    }

    private static boolean isConnected(BluetoothDevice d) {
        try { return (boolean) d.getClass().getMethod("isConnected").invoke(d); } catch (Exception e) { return false; }
    }

    private static void deviceMenu(FrameLayout root, BluetoothDevice d, Runnable refresh) {
        Popup p = Popup.show(root, name(d), 440);
        List<Popup.Item> it = Popup.items();
        String n = name(d).toLowerCase();
        if (n.contains("dazn") || n.contains("remote") || n.contains("rcu") || n.contains("telecomando"))
            it.add(new Popup.Item("Attenzione: sembra il telecomando del box").disabled());
        it.add(new Popup.Item("Dimentica dispositivo").red().on(() -> {
            try { d.getClass().getMethod("removeBond").invoke(d); } catch (Exception ignored) { }
            p.close();
            root.postDelayed(refresh, 800);
        }));
        it.add(new Popup.Item("Annulla").on(p::close));
        p.set(it);
    }

    // =====================================================================================
    // AUDIO
    // =====================================================================================

    static void audio(FrameLayout root, View... block) {
        Context c = root.getContext();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        ContentResolver cr = c.getContentResolver();
        Popup p = Popup.show(root, "Audio", 520, block);
        Runnable[] refresh = new Runnable[1];
        VolumeSlider slider = new VolumeSlider(c, am);
        refresh[0] = () -> {
            p.list.removeAllViews();
            p.add(slider, 64);
            List<Popup.Item> it = Popup.items();
            boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
            it.add(new Popup.Item("Disattiva audio").trail(muted ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF).on(() -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
                refresh[0].run();
            }));
            it.add(new Popup.Item("Formato audio").header());
            int surround = Settings.Global.getInt(cr, "encoded_surround_output", 0);
            String[] names = {"Automatico", "Stereo PCM", "Dolby / surround sempre"};
            String[] subs = {"Usa quello che supporta la TV o la soundbar", "Massima compatibilità", "Invia Dolby Digital anche se non rilevato"};
            for (int i = 0; i < 3; i++) {
                final int v = i;
                it.add(new Popup.Item(names[i]).sub(subs[i]).trail(surround == i ? Popup.Trail.CHECK : Popup.Trail.NONE).on(() -> {
                    try { Settings.Global.putInt(cr, "encoded_surround_output", v); }
                    catch (SecurityException e) { Sheet.toast(root, "Permesso mancante per il formato audio"); }
                    refresh[0].run();
                }));
            }
            it.add(new Popup.Item("Interfaccia").header());
            boolean clicks = Settings.System.getInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1;
            it.add(new Popup.Item("Suoni dei tasti").trail(clicks ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF).on(() -> {
                try {
                    Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, clicks ? 0 : 1);
                    if (clicks) am.unloadSoundEffects(); else am.loadSoundEffects();
                } catch (Exception e) { Sheet.toast(root, "Permesso mancante"); }
                refresh[0].run();
            }));
            it.add(new Popup.Item("Altre impostazioni audio").trail(Popup.Trail.CHEVRON).on(() -> {
                p.close();
                openSystem(c, new Intent(Settings.ACTION_SOUND_SETTINGS), null);
            }));
            for (Popup.Item i : it) {
                Popup.Row row = new Popup.Row(c, i);
                p.list.addView(row, new android.widget.LinearLayout.LayoutParams(-1, Popup.dp(c, i.header ? 38 : (i.sub != null ? 64 : 54))));
            }
            slider.post(slider::requestFocus);
            p.panel.invalidate();
        };
        refresh[0].run();
    }

    /** cursore del volume: sinistra/destra per regolare */
    static final class VolumeSlider extends View {
        private final AudioManager am;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        VolumeSlider(Context c, AudioManager am) {
            super(c);
            this.am = am;
            setFocusable(true);
            tp.setTypeface(Ui.textSemibold());
            tp.setTextSize(Ui.dp(c, 15));
            tp.setColor(Color.WHITE);
            setOnFocusChangeListener((v, has) -> { active = has; invalidate(); });
        }

        @Override public boolean onKeyDown(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, k == KeyEvent.KEYCODE_DPAD_RIGHT ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                invalidate();
                if (GlassService.instance != null) GlassService.instance.showVolume();
                return true;
            }
            return super.onKeyDown(k, e);
        }

        @Override protected void onDraw(Canvas c) {
            float u = Ui.dp(getContext(), 1), w = getWidth(), h = getHeight(), pad = 16 * u;
            if (active) {
                p.setColor(Color.argb(62, 255, 255, 255));
                c.drawPath(Ui.squircle(new RectF(0, 2 * u, w, h - 2 * u), 14 * u), p);
            }
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            float lv = am.isStreamMute(AudioManager.STREAM_MUSIC) ? 0 : cur / (float) max;
            c.drawText("Volume", pad, h / 2f - (tp.ascent() + tp.descent()) / 2f, tp);
            String pct = Math.round(lv * 100) + "%";
            float pw = tp.measureText("100%");
            float bl = pad + tp.measureText("Volume") + 22 * u, br = w - pad - pw - 14 * u, bh = 8 * u, cy = h / 2f;
            p.setColor(Color.argb(70, 255, 255, 255));
            c.drawRoundRect(new RectF(bl, cy - bh / 2, br, cy + bh / 2), bh / 2, bh / 2, p);
            p.setColor(Color.WHITE);
            if (lv > 0) c.drawRoundRect(new RectF(bl, cy - bh / 2, bl + Math.max(bh, (br - bl) * lv), cy + bh / 2), bh / 2, bh / 2, p);
            if (active) c.drawCircle(bl + (br - bl) * lv, cy, 9 * u, p);
            c.drawText(pct, w - pad - tp.measureText(pct), h / 2f - (tp.ascent() + tp.descent()) / 2f, tp);
        }
    }

    // =====================================================================================
    // VIDEO
    // =====================================================================================

    static String sysprop(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) { return ""; }
    }

    /** {etichetta, sottotitolo, testi possibili nella pagina del box} */
    private static final String[][] RES = {
            {"Automatica", "La migliore supportata dalla TV", "Best resolution"},
            {"4K · 60 Hz", "2160p", "2160p 60Hz|4K 60Hz|2160p60"},
            {"4K · 50 Hz", "2160p", "2160p 50Hz|4K 50Hz|2160p50"},
            {"1080p · 60 Hz", "Full HD", "1080p 60Hz"},
            {"1080p · 50 Hz", "Full HD", "1080p 50Hz"},
            {"720p · 60 Hz", "HD", "720p 60Hz"},
            {"720p · 50 Hz", "HD", "720p 50Hz"},
    };

    /** {etichetta, sottotitolo, titolo nella pagina xiaomi, frequenza nella pagina xiaomi} */
    private static final String[][] XRES = {
            {"4K · 60 Hz", "2160p · se la TV lo consente", "4k", "60Hz"},
            {"4K · 50 Hz", "2160p · se la TV lo consente", "4k", "50Hz"},
            {"4K · 30 Hz", "2160p · massima qualità d'immagine", "4k", "30Hz"},
            {"4K · 25 Hz", "2160p · TV e sport italiani in 4K", "4k", "25Hz"},
            {"4K · 24 Hz", "2160p · film", "4k", "24Hz"},
            {"1080p · 60 Hz", "Full HD · interfaccia più fluida", "1080p", "60Hz"},
            {"1080p · 50 Hz", "Full HD · TV e sport italiani", "1080p", "50Hz"},
            {"1080p · 24 Hz", "Full HD · film", "1080p", "24Hz"},
            {"720p · 60 Hz", "HD", "720p", "60Hz"},
    };

    /** popup video del box xiaomi: risoluzione e frequenza scelte nella pagina di sistema, sotto la scheda in vetro */
    private static volatile String lastOut; // ultima uscita letta: il popup si apre subito con questa

    private static void videoXiaomi(FrameLayout root, View... block) {
        Context c = root.getContext();
        Popup p = Popup.show(root, "Video", 520, block);
        fillXiaomi(root, p, lastOut);
        // lettura della vera uscita hdmi in background (l'interfaccia e' sempre 1080p), poi aggiorna le spunte
        new Thread(() -> {
            String out = xiaomiOutput();
            if (out != null && !out.equals(lastOut)) {
                lastOut = out;
                root.post(() -> { if (p.overlay.getParent() != null) fillXiaomi(root, p, out); });
            }
        }, "video").start();
    }

    private static void fillXiaomi(FrameLayout root, Popup p, String out) {
        Context c = root.getContext();
        Display.Mode m = ((android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMode();
        int hz = Math.round(m.getRefreshRate());
        String cur;
        if (out != null) {
            cur = out.startsWith("2160") ? "4k" : out.startsWith("1080") ? "1080p" : out.startsWith("720") ? "720p" : "?";
            try { hz = Integer.parseInt(out.replaceAll("^\\d+p(\\d+).*$", "$1")); } catch (Exception ignored) { }
        } else {
            // senza dato: a 25/30 hz lo xiaomi offre solo il 4k
            cur = (hz == 30 || hz == 25) ? "4k" : "1080p";
        }
        p.sub("Uscita attuale: " + ("4k".equals(cur) ? "4K" : cur) + " · " + hz + " Hz");
        List<Popup.Item> it = Popup.items();
        it.add(new Popup.Item("Risoluzione e frequenza").header());
        for (String[] r : XRES) {
            boolean on = r[2].equals(cur) && r[3].equals(hz + "Hz");
            it.add(new Popup.Item(r[0]).sub(r[1]).trail(on ? Popup.Trail.CHECK : Popup.Trail.NONE).on(() -> {
                p.close();
                if (on) return;
                List<String[]> steps = new ArrayList<>();
                steps.add(new String[]{"~Risoluzione"}); // il focus apre l'elenco nel pannello a destra
                steps.add(new String[]{r[2] + "&" + r[3]});
                steps.add(new String[]{"OK"}); // conferma "resolution has been changed"
                GlassService s = GlassService.instance;
                if (s == null) { openSystem(c, new Intent().setComponent(XIAOMI_DISPLAY), null); return; }
                lastOut = null;
                s.automate(XIAOMI_DISPLAY, "Imposto " + r[0] + "…", steps, err -> {
                    if (err == null) Sheet.toast(root, "Fatto");
                    else Sheet.toast(root, r[2].equals("4k") && !r[3].equals("30Hz") && !r[3].equals("25Hz") && !r[3].equals("24Hz")
                            ? "La TV per ora accetta il 4K solo fino a 30 Hz (cavo o porta HDMI)" : "Risoluzione non disponibile");
                });
            }));
        }
        it.add(new Popup.Item("HDR, Dolby Vision e altre opzioni").trail(Popup.Trail.CHEVRON)
                .on(() -> { p.close(); openSystem(c, new Intent().setComponent(XIAOMI_DISPLAY), null); }));
        p.set(it);
    }

    /** modalita' hdmi reale dello xiaomi (es. "2160p30hz"), null se non leggibile */
    static String xiaomiOutput() {
        String s = Sys.exec("dumpsys", "SurfaceFlinger");
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("FixedSizeModeMgr:\\((\\d+p\\d+)hz").matcher(s);
        return mm.find() ? mm.group(1) : null;
    }

    static void video(FrameLayout root, View... block) {
        if (Sys.XIAOMI) { videoXiaomi(root, block); return; }
        Context c = root.getContext();
        SharedPreferences sp = c.getSharedPreferences("video", Context.MODE_PRIVATE);
        Popup p = Popup.show(root, "Video", 520, block);
        Display.Mode m = ((android.view.WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMode();
        p.sub("Uscita attuale: " + m.getPhysicalWidth() + "×" + m.getPhysicalHeight() + " · " + Math.round(m.getRefreshRate()) + " Hz");
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            List<Popup.Item> it = Popup.items();
            it.add(new Popup.Item("Risoluzione").header());
            String forced = sysprop("persist.vendor.nx.vidout.force");
            int chosen = sp.getInt("res", forced.isEmpty() ? 0 : -1);
            if (forced.isEmpty()) chosen = 0;
            for (int i = 0; i < RES.length; i++) {
                final int idx = i;
                it.add(new Popup.Item(RES[i][0]).sub(RES[i][1]).trail(i == chosen ? Popup.Trail.CHECK : Popup.Trail.NONE).on(() -> {
                    p.close();
                    applyRes(root, sp, idx);
                }));
            }
            it.add(new Popup.Item("Frequenza").header());
            boolean hz50 = "1".equals(sysprop("persist.vendor.nx.vidout.50hz"));
            it.add(new Popup.Item("Adatta a 50 Hz").sub("Consigliato per TV e sport italiani (50 fps)")
                    .trail(hz50 ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF).on(() -> {
                        p.close();
                        List<String[]> steps = new ArrayList<>();
                        steps.add(new String[]{"50Hz Abilitato (se disponibile)"});
                        run(root, (hz50 ? "Disattivo" : "Attivo") + " 50 Hz…", steps, null);
                    }));
            it.add(new Popup.Item("HDR e altre opzioni").trail(Popup.Trail.CHEVRON).on(() -> { p.close(); openSystem(c, new Intent().setComponent(AV_SETTINGS), null); }));
            p.set(it);
        };
        refresh[0].run();
    }

    private static void applyRes(FrameLayout root, SharedPreferences sp, int idx) {
        List<String[]> steps = new ArrayList<>();
        steps.add(new String[]{"Risoluzione"});
        steps.add(RES[idx][2].split("\\|"));
        run(root, "Imposto " + RES[idx][0] + "…", steps, () -> sp.edit().putInt("res", idx).apply());
    }

    /** esegue l'automazione tramite il servizio; senza servizio apre la pagina del box */
    private static void run(FrameLayout root, String msg, List<String[]> steps, Runnable ok) {
        Context c = root.getContext();
        GlassService s = GlassService.instance;
        if (s == null) { openSystem(c, new Intent().setComponent(AV_SETTINGS), null); return; }
        s.automate(AV_SETTINGS, msg, steps, err -> {
            if (err == null) { if (ok != null) ok.run(); Sheet.toast(root, "Fatto"); }
            else Sheet.toast(root, "Opzione non disponibile su questo schermo");
        });
    }
}
