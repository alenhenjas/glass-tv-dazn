/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * impostazioni in vetro stile ipad: le sezioni principali si gestiscono in popup liquid glass;
 * per tutto il resto c'e' il rimando alle impostazioni complete di android.
 */
public class SettingsActivity extends Activity {
    private FrameLayout root;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 8, 16));
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        if (Wallpaper.full != null) bg.setImageBitmap(Wallpaper.full);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("Impostazioni");
        Ui.text(title, Ui.displayBold(), 36, 255, -0.02f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.leftMargin = dp(80); tlp.topMargin = dp(48);
        root.addView(title, tlp);

        GlassPanel card = new GlassPanel(this, 0.05f, 10, 0.14f);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.getViewTreeObserver().addOnScrollChangedListener(card::invalidate);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(16), dp(18), dp(16));
        sv.addView(list);
        card.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(640), -1, Gravity.CENTER_HORIZONTAL);
        clp.topMargin = dp(118); clp.bottomMargin = dp(36);
        root.addView(card, clp);
        setContentView(root);

        View[] block = {list};
        add(Glyphs.Kind.WIFI, Color.rgb(10, 132, 255), "Wi-Fi", netSummary(), v -> Controls.wifi(root, block));
        add(Glyphs.Kind.BLUETOOTH, Color.rgb(10, 132, 255), "Bluetooth", "Telecomandi, cuffie, tastiere", v -> Controls.bluetooth(root, block));
        add(Glyphs.Kind.VIDEO, Color.rgb(48, 176, 199), "Video", Sys.XIAOMI ? "Risoluzione, 4K, frequenza" : "Risoluzione, 50 Hz", v -> Controls.video(root, block));
        add(Glyphs.Kind.AUDIO, Color.rgb(255, 55, 95), "Audio", "Volume, formato audio, suoni", v -> Controls.audio(root, block));
        add(Glyphs.Kind.RECENTS, Color.rgb(94, 92, 230), "App aperte", "Vedi e chiudi le app in uso", v -> startActivity(new Intent(this, RecentsActivity.class)));
        add(Glyphs.Kind.APPS, Color.rgb(88, 86, 214), "App", "Tutte le app installate", v -> startActivity(new Intent(this, AllAppsActivity.class)));
        add(Glyphs.Kind.STORAGE, Color.rgb(142, 142, 147), "Memoria e archiviazione", storageSummary(), v -> storage());
        add(Glyphs.Kind.INFO, Color.rgb(142, 142, 147), "Informazioni", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE, v -> info());
        add(Glyphs.Kind.CLOCK, Color.rgb(142, 142, 147), "Data e ora", null, v -> open(new Intent(Settings.ACTION_DATE_SETTINGS)));
        add(Glyphs.Kind.GEAR, Color.rgb(99, 99, 102), "Tutte le impostazioni Android", "Opzioni avanzate del sistema", v -> open(new Intent(Settings.ACTION_SETTINGS)));
        list.post(() -> { if (list.getChildCount() > 0) list.getChildAt(0).requestFocus(); });
    }

    private void open(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { Controls.openSystem(this, new Intent(Settings.ACTION_SETTINGS), null); }
    }

    private void add(Glyphs.Kind k, int color, String title, String sub, View.OnClickListener l) {
        SettingRow r = new SettingRow(this, k, color, title, sub);
        r.setOnClickListener(l);
        list.addView(r, new LinearLayout.LayoutParams(-1, dp(sub == null ? 62 : 70)));
    }

    // ---------------- riepiloghi e pannelli informativi ----------------

    private String netSummary() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) return "Non connesso";
            if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) return "Connesso via Ethernet";
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ssid = wm.getConnectionInfo().getSSID();
            if (ssid != null && ssid.startsWith("\"")) return "Connesso a " + ssid.replace("\"", "");
            return "Connesso";
        } catch (Exception e) { return null; }
    }

    private String storageSummary() {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        return gb(fs.getAvailableBytes()) + " liberi su " + gb(fs.getTotalBytes());
    }

    private void storage() {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        Popup p = Popup.show(root, "Memoria e archiviazione", 520, list);
        List<Popup.Item> it = new ArrayList<>();
        it.add(new Popup.Item("Archiviazione").header());
        it.add(new Popup.Item("Spazio libero").value(gb(fs.getAvailableBytes())).disabled());
        it.add(new Popup.Item("Spazio totale").value(gb(fs.getTotalBytes())).disabled());
        it.add(new Popup.Item("Memoria RAM").header());
        it.add(new Popup.Item("Disponibile").value(gb(mi.availMem)).disabled());
        it.add(new Popup.Item("Totale").value(gb(mi.totalMem)).disabled());
        it.add(new Popup.Item("Chiudi tutte le app").sub("Libera memoria").on(() -> {
            long before = Closer.availMem(this);
            Closer.closeAll(this, Apps.all(this));
            p.close();
            root.postDelayed(() -> {
                long mb = (Closer.availMem(this) - before) / (1024 * 1024);
                Sheet.toast(root, "Tutte le app chiuse" + (mb > 5 ? "  ·  " + mb + " MB liberati" : ""));
            }, 600);
        }));
        it.add(new Popup.Item("Gestisci archiviazione").trail(Popup.Trail.CHEVRON)
                .on(() -> { p.close(); open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }));
        p.set(it);
    }

    private void info() {
        Popup p = Popup.show(root, "Informazioni", 520, list);
        List<Popup.Item> it = new ArrayList<>();
        it.add(new Popup.Item("Dispositivo").header());
        it.add(new Popup.Item("Modello").value(Build.MANUFACTURER + " " + Build.MODEL).disabled());
        it.add(new Popup.Item("Android").value(Build.VERSION.RELEASE).disabled());
        it.add(new Popup.Item("Versione software").value(Build.DISPLAY.replaceAll(".*\\.(DTIW[^\\s]*)", "$1")).disabled());
        String wv = "";
        try { wv = getPackageManager().getPackageInfo("com.google.android.webview", 0).versionName; } catch (Exception ignored) { }
        if (!wv.isEmpty()) it.add(new Popup.Item("Motore web (Chromium)").value(wv.split("\\.")[0]).disabled());
        it.add(new Popup.Item("Glass TV").value(versionName()).disabled());
        it.add(new Popup.Item("Progettato e sviluppato da").value("Alen Henjas").disabled());
        it.add(new Popup.Item("Rete").header());
        it.add(new Popup.Item("Indirizzo IP").value(ip()).disabled());
        it.add(new Popup.Item("Acceso da").value(uptime()).disabled());
        p.set(it);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    private String ip() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null) for (LinkAddress a : lp.getLinkAddresses())
                if (a.getAddress() instanceof java.net.Inet4Address) return a.getAddress().getHostAddress();
        } catch (Exception ignored) { }
        return "—";
    }

    private static String uptime() {
        long m = SystemClock.elapsedRealtime() / 60000;
        long d = m / 1440, h = (m % 1440) / 60, mm = m % 60;
        return (d > 0 ? d + " g " : "") + (h > 0 ? h + " h " : "") + mm + " min";
    }

    private static String gb(long bytes) {
        double g = bytes / 1073741824.0;
        return g >= 1 ? String.format(java.util.Locale.ITALY, "%.1f GB", g) : (bytes / 1048576) + " MB";
    }

    private int dp(float v) { return (int) Ui.dp(this, v); }

    /** riga stile impostazioni ios: icona squircle colorata con glifo bianco, titolo, sottotitolo, freccia */
    static final class SettingRow extends View {
        private final Glyphs.Kind kind;
        private final int color;
        private final String title, sub;
        private boolean active;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG), sp = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        SettingRow(Context c, Glyphs.Kind k, int color, String title, String sub) {
            super(c);
            this.kind = k; this.color = color; this.title = title; this.sub = sub;
            setFocusable(true); setClickable(true);
            tp.setTextSize(Ui.dp(c, 17)); tp.setColor(Color.WHITE);
            sp.setTypeface(Ui.textRegular()); sp.setTextSize(Ui.dp(c, 13)); sp.setColor(Color.argb(160, 255, 255, 255));
            setOnFocusChangeListener((v, has) -> { active = has; invalidate(); });
        }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), h = getHeight(), u = Ui.dp(ctx, 1);
            if (active) {
                p.setColor(Color.argb(62, 255, 255, 255));
                c.drawPath(Ui.squircle(new RectF(0, 3 * u, w, h - 3 * u), 16 * u), p);
            }
            float is = 38 * u, x = 14 * u;
            RectF ir = new RectF(x, h / 2 - is / 2, x + is, h / 2 + is / 2);
            p.setColor(color);
            c.drawPath(Ui.squircle(ir, is * 0.26f), p);
            p.setColor(Color.WHITE);
            Glyphs.draw(c, kind, ir.centerX(), ir.centerY(), is * 0.24f, p, -1f);
            float tx = ir.right + 16 * u, right = w - 40 * u;
            tp.setTypeface(active ? Ui.textSemibold() : Ui.textMedium());
            CharSequence t = TextUtils.ellipsize(title, tp, right - tx, TextUtils.TruncateAt.END);
            if (sub == null) c.drawText(t, 0, t.length(), tx, h / 2 - (tp.ascent() + tp.descent()) / 2, tp);
            else {
                c.drawText(t, 0, t.length(), tx, h / 2 - 2 * u, tp);
                CharSequence s = TextUtils.ellipsize(sub, sp, right - tx, TextUtils.TruncateAt.END);
                c.drawText(s, 0, s.length(), tx, h / 2 + 4 * u - sp.ascent(), sp);
            }
            p.setColor(Color.argb(140, 255, 255, 255));
            Glyphs.draw(c, Glyphs.Kind.CHEVRON, w - 22 * u, h / 2, 8 * u, p, -1f);
        }
    }
}
