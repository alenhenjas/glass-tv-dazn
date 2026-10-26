/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int MAX_COLS = 5;
    private ImageView bg;
    private GridLayout grid;
    private ControlButton powerBtn, restartBtn;
    private ClockView clockView;
    private static final int GRID_TOP = 214, CELL_W = 186, CELL_H = 206;
    private LinearLayout dockRow;
    private TextView netText;
    private NetGlyph netGlyph;
    private View content;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean dirty = true, loading;
    private int homeVersion = -1;
    private int lastFocus = 0;
    private FrameLayout rootView;
    private LinearLayout controls;
    private ControlButton netBtn;
    private View dockPanel;
    private List<Apps.Entry> allApps = new java.util.ArrayList<>();

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dirty = true; Icons.clear(); if (hasWindowFocus()) reload(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        immersive();
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundColor(Color.rgb(6, 10, 34));
        root.setClipChildren(false);
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        content = stage;

        // orario stile schermata di blocco, personalizzabile (ok o pressione lunga sull'orario)
        clockView = new ClockView(this);
        clockView.setOnClickListener(v -> clockMenu());
        clockView.setOnLongClickListener(v -> { clockMenu(); return true; });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(640), -2, Gravity.TOP | Gravity.START);
        clp.leftMargin = dp(72 - 20); clp.topMargin = dp(46 - 12);
        stage.addView(clockView, clp);

        // controlli rapidi in vetro: rete, video, audio, bluetooth, chiudi tutto
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setClipChildren(false);
        netBtn = control(Glyphs.Kind.WIFI, "Wi-Fi", v -> Controls.wifi(rootView, grid, dockRow, controls));
        control(Glyphs.Kind.VIDEO, "Video", v -> Controls.video(rootView, grid, dockRow, controls));
        control(Glyphs.Kind.AUDIO, "Audio", v -> Controls.audio(rootView, grid, dockRow, controls));
        control(Glyphs.Kind.BLUETOOTH, "Bluetooth", v -> Controls.bluetooth(rootView, grid, dockRow, controls));
        control(Glyphs.Kind.CLOSE_ALL, "Chiudi tutto", v -> closeAll());
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, dp(96), Gravity.TOP | Gravity.END);
        plp.rightMargin = dp(60); plp.topMargin = dp(48);
        stage.addView(controls, plp);

        // griglia app: si adatta al numero di app (piu' righe e icone piu' piccole), mai scorrimento
        grid = new GridLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        glp.topMargin = dp(GRID_TOP);
        stage.addView(grid, glp);

        // dock in vetro
        GlassPanel dock = new GlassPanel(this, 0.36f, 6, 0.12f);
        dockRow = new LinearLayout(this);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setPadding(dp(18), 0, dp(18), 0);
        dock.addView(dockRow, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-2, dp(96), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(30);
        stage.addView(dock, dlp);
        dockPanel = dock;

        // spegnimento tv in basso a sinistra, allineato al dock
        powerBtn = new ControlButton(this, Glyphs.Kind.POWER, "Spegni");
        powerBtn.setOnClickListener(v -> powerOff());
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(dp(84), dp(96), Gravity.BOTTOM | Gravity.START);
        wlp.leftMargin = dp(72); wlp.bottomMargin = dp(30);
        stage.addView(powerBtn, wlp);
        restartBtn = new ControlButton(this, Glyphs.Kind.RESTART, "Riavvia");
        restartBtn.setOnClickListener(v -> restart());
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(84), dp(96), Gravity.BOTTOM | Gravity.START);
        rlp.leftMargin = dp(72 + 84 + 6); rlp.bottomMargin = dp(30);
        stage.addView(restartBtn, rlp);

        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        content.setAlpha(0f);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        final int w = dm.widthPixels, h = dm.heightPixels;
        new Thread(() -> {
            Wallpaper.ensure(this, w, h);
            ui.post(() -> {
                bg.setImageBitmap(Wallpaper.full);
                bg.setAlpha(0f);
                bg.animate().alpha(1f).setDuration(260).start();
                invalidateGlass(grid);
                invalidateGlass(dockRow);
                content.invalidate();
            });
        }, "wallpaper").start();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        updateNet();
        Closer.prewarm(); // elenco app aperte pronto per il menu della pressione lunga
        ui.removeCallbacks(warm);
        ui.postDelayed(warm, 60000);
        if (Home.version() != homeVersion) dirty = true;
        if (dirty) reload();
        else focusIndex(lastFocus);
    }

    private final Runnable warm = new Runnable() {
        @Override public void run() { Closer.prewarm(); ui.postDelayed(this, 60000); }
    };

    @Override protected void onPause() {
        super.onPause();
        ui.removeCallbacks(warm);
        View f = getCurrentFocus();
        if (f != null && f.getParent() == grid) lastFocus = grid.indexOfChild(f);
    }

    /** carica app e icone in background: la ui resta reattiva */
    private void reload() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<Apps.Entry> all = Apps.all(this);
            List<Apps.Entry> favs = Apps.favorites(this, all);
            List<Apps.Entry> dock = Apps.dock(this, all);
            int hv = Home.version();
            ui.post(() -> { allApps = all; homeVersion = hv; build(favs, dock); loading = false; dirty = false; });
        }, "apps").start();
    }

    private void build(List<Apps.Entry> favs, List<Apps.Entry> dock) {
        grid.removeAllViews();
        // sceglie righe/colonne che danno le icone piu' grandi possibile nello spazio tra orologio e dock
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        float availW = dm.widthPixels - dp(120), availH = dm.heightPixels - dp(GRID_TOP) - dp(30 + 96 + 16);
        int n = Math.max(1, favs.size()), cols = n;
        float best = 0f;
        for (int r = 1; r <= 6; r++) {
            int c = (n + r - 1) / r;
            float s = Math.min(1f, Math.min(availW / (c * dp(CELL_W)), availH / (r * dp(CELL_H))));
            if (s > best + 0.001f) { best = s; cols = c; }
            if (c == 1) break;
        }
        float sc = best;
        grid.setColumnCount(cols);
        for (Apps.Entry e : favs) {
            AppCell cell = new AppCell(this, 148 * sc, 92 * sc, true, true, 1.10f);
            cell.textScale(sc);
            cell.bind(e.icon, e.label);
            cell.setOnClickListener(v -> launch(e));
            cell.setOnLongClickListener(v -> { appMenu(e); return true; });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(CELL_W * sc);
            lp.height = dp(CELL_H * sc);
            grid.addView(cell, lp);
        }
        dockRow.removeAllViews();
        for (Apps.Entry e : dock) {
            AppCell cell = new AppCell(this, 72, 62, false, false, 1.18f);
            cell.bind(e.icon, e.label);
            cell.setOnClickListener(v -> launch(e));
            if (e.pkg.contains(".")) cell.setOnLongClickListener(v -> { appMenu(e); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), dp(84));
            dockRow.addView(cell, lp);
        }
        linkFocus();
        if (content.getAlpha() < 1f) content.animate().alpha(1f).setDuration(240).setInterpolator(Ui.EASE).start();
        focusIndex(lastFocus);
    }

    private void focusIndex(int idx) {
        if (grid.getChildCount() > 0) {
            View first = grid.getChildAt(Math.max(0, Math.min(idx, grid.getChildCount() - 1)));
            first.setFocusedByDefault(true);
            first.requestFocus();
        }
        grid.post(() -> {
            if (grid.getChildCount() == 0) { if (dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus(); return; }
            grid.getChildAt(Math.max(0, Math.min(idx, grid.getChildCount() - 1))).requestFocus();
        });
    }

    /** navigazione esplicita: su dalle app ai controlli sopra, giu' dai controlli alle app, dock <-> app */
    private void linkFocus() {
        int nc = controls.getChildCount(), ng = grid.getChildCount(), nd = dockRow.getChildCount();
        for (View v : all(controls)) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (View v : all(grid)) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (View v : all(dockRow)) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        int cols = Math.max(1, grid.getColumnCount());
        for (int i = 0; i < ng; i++) {
            View cell = grid.getChildAt(i);
            if (i < cols && nc > 0) cell.setNextFocusUpId(controls.getChildAt(Math.min(i, nc - 1)).getId());
            if (i >= ng - cols && nd > 0) cell.setNextFocusDownId(dockRow.getChildAt(Math.min(i % cols, nd - 1)).getId());
        }
        for (int j = 0; j < nc; j++) if (ng > 0) controls.getChildAt(j).setNextFocusDownId(grid.getChildAt(Math.min(j, Math.min(cols, ng) - 1)).getId());
        for (View v : new View[]{powerBtn, restartBtn, clockView}) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        // spegni <-> riavvia <-> dock
        powerBtn.setNextFocusRightId(restartBtn.getId());
        restartBtn.setNextFocusLeftId(powerBtn.getId());
        if (nd > 0) {
            dockRow.getChildAt(0).setNextFocusLeftId(restartBtn.getId());
            restartBtn.setNextFocusRightId(dockRow.getChildAt(0).getId());
        }
        if (ng > 0) {
            int lastRow = Math.max(0, ng - Math.min(cols, ng));
            powerBtn.setNextFocusUpId(grid.getChildAt(lastRow).getId());
            restartBtn.setNextFocusUpId(grid.getChildAt(lastRow).getId());
            // orologio: su dalla prima app, giu' verso le app, destra verso i controlli
            grid.getChildAt(0).setNextFocusUpId(clockView.getId());
            clockView.setNextFocusDownId(grid.getChildAt(0).getId());
        }
        if (nc > 0) {
            clockView.setNextFocusRightId(controls.getChildAt(0).getId());
            controls.getChildAt(0).setNextFocusLeftId(clockView.getId());
        }
        for (int j = 0; j < nd; j++) if (ng > 0) dockRow.getChildAt(j).setNextFocusUpId(grid.getChildAt(Math.max(0, ng - Math.min(cols, ng)) + Math.min(j, Math.min(cols, ng) - 1)).getId());
    }

    private static java.util.List<View> all(android.view.ViewGroup g) {
        java.util.List<View> l = new java.util.ArrayList<>();
        for (int i = 0; i < g.getChildCount(); i++) l.add(g.getChildAt(i));
        return l;
    }

    private ControlButton control(Glyphs.Kind k, String label, View.OnClickListener l) {
        ControlButton b = new ControlButton(this, k, label);
        b.setOnClickListener(l);
        controls.addView(b, new LinearLayout.LayoutParams(dp(84), dp(96)));
        return b;
    }

    /** apre una schermata impostazioni: prima l'intent standard, poi il componente specifico, infine le impostazioni */
    private void openSettings(Intent intent, android.content.ComponentName fallback) {
        Intent[] tries = {
                intent,
                fallback == null ? null : new Intent().setComponent(fallback),
                new Intent(android.provider.Settings.ACTION_SETTINGS)};
        for (Intent i : tries) {
            if (i == null) continue;
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
            catch (Exception ignored) { }
        }
    }

    /** menu della pressione lunga (condiviso con "tutte le app") */
    private void appMenu(Apps.Entry e) {
        AppMenu.show(this, rootView, e, new View[]{grid, dockRow, controls}, () -> { dirty = true; lastFocus = 0; reload(); });
    }

    /** conferma in vetro, poi standby (la tv si spegne via hdmi-cec) */
    private void powerOff() {
        Sheet.show(rootView, null, "Spegnere la TV?", new String[]{"Spegni", "Annulla"}, new boolean[]{true, false},
                new Sheet.Action[]{() -> {
                    GlassService s = GlassService.instance;
                    if (s == null || !s.sleep()) Sheet.toast(rootView, "Servizio Glass non attivo: usa il tasto del telecomando");
                }, null}, grid, dockRow, controls, powerBtn);
    }

    /** conferma in vetro, poi riavvio del box */
    private void restart() {
        Sheet.show(rootView, null, "Riavviare il box?", new String[]{"Riavvia", "Annulla"}, new boolean[]{true, false},
                new Sheet.Action[]{() -> {
                    GlassService s = GlassService.instance;
                    if (s == null) { Sheet.toast(rootView, "Servizio Glass non attivo"); return; }
                    s.reboot(err -> { if (err != null) Sheet.toast(rootView, "Riavvio non riuscito: tieni premuto il tasto di accensione"); });
                }, null}, grid, dockRow, controls);
    }

    /** opzioni dell'orario in un popup laterale: le modifiche si vedono subito sull'orologio */
    private void clockMenu() {
        ClockView cv = clockView;
        Popup p = Popup.show(rootView, "Orario", 470, grid, dockRow, controls).side();
        Runnable[] build = new Runnable[1];
        build[0] = () -> {
            java.util.List<Popup.Item> it = Popup.items();
            it.add(new Popup.Item("Colore").picker(new Popup.Picker() {
                public String label() { return ClockView.COLOR_NAMES[cv.color]; }
                public int swatch() { return cv.swatch(cv.color); }
                public void step(int d) { cv.color = wrap(cv.color + d, ClockView.COLORS.length); cv.save(); }
            }));
            it.add(new Popup.Item("Stile").picker(new Popup.Picker() {
                public String label() { return ClockView.STYLE_NAMES[cv.style]; }
                public int swatch() { return 0; }
                public void step(int d) { cv.style = wrap(cv.style + d, ClockView.STYLE_NAMES.length); cv.save(); }
            }));
            it.add(new Popup.Item("Opacità").picker(new Popup.Picker() {
                public String label() { return ClockView.OPACITY[cv.opacity] + "%"; }
                public int swatch() { return 0; }
                // destra = piu' opaco, sinistra = piu' trasparente
                public void step(int d) { cv.opacity = Math.max(0, Math.min(ClockView.OPACITY.length - 1, cv.opacity - d)); cv.save(); }
            }));
            it.add(new Popup.Item("Carattere").picker(new Popup.Picker() {
                public String label() { return ClockView.FONT_NAMES[cv.font]; }
                public int swatch() { return 0; }
                public void step(int d) { cv.font = wrap(cv.font + d, ClockView.FONT_NAMES.length); cv.save(); }
            }));
            it.add(new Popup.Item("Dimensione").picker(new Popup.Picker() {
                public String label() { return ClockView.SIZE_NAMES[cv.size]; }
                public int swatch() { return 0; }
                public void step(int d) { cv.size = Math.max(0, Math.min(ClockView.SIZE_NAMES.length - 1, cv.size + d)); cv.save(); }
            }));
            it.add(new Popup.Item("Mostra la data").trail(cv.showDate ? Popup.Trail.SWITCH_ON : Popup.Trail.SWITCH_OFF)
                    .on(() -> { cv.showDate = !cv.showDate; cv.save(); build[0].run(); }));
            it.add(new Popup.Item("Ripristina predefinito").on(() -> { cv.reset(); build[0].run(); Sheet.toast(rootView, "Orario ripristinato"); }));
            p.set(it);
        };
        build[0].run();
    }

    private static int wrap(int v, int n) { return ((v % n) + n) % n; }

    /** chiude tutte le app in background, come la x sul pc */
    private void closeAll() {
        long before = Closer.availMem(this);
        int n = Closer.closeAll(this, allApps);
        ui.postDelayed(() -> Sheet.toast(rootView, "Tutte le app chiuse" + freed(before)), 700);
    }

    private String freed(long before) {
        long mb = (Closer.availMem(this) - before) / (1024 * 1024);
        return mb > 5 ? "  \u00b7  " + mb + " MB liberati" : "";
    }

    private void launch(Apps.Entry e) {
        try {
            startActivity(e.launch);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Impossibile aprire " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateNet() {
        String t = "Offline"; int kind = 0;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c != null) {
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { t = "Ethernet"; kind = 2; }
                else if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { t = "Wi-Fi"; kind = 1; }
                else { t = "Online"; kind = 2; }
            }
        } catch (Exception ignored) { }
        netBtn.set(kind == 2 ? Glyphs.Kind.ETHERNET : Glyphs.Kind.WIFI, t);
    }

    private static void invalidateGlass(View v) {
        v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateGlass(g.getChildAt(i));
        }
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        lastFocus = 0;
        focusIndex(0); // home premuto stando gia' sulla home
    }

    @Override public void onBackPressed() { /* la home non si chiude */ }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    int dp(float v) { return (int) Ui.dp(this, v); }

    /** glifo di rete minimale: archi wi-fi oppure tre barre */
    static final class NetGlyph extends View {
        private int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        NetGlyph(Context c) { super(c); }
        void setKind(int k) { kind = k; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(Color.argb(240, 255, 255, 255));
            if (kind == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(h * 0.14f);
                for (int i = 1; i <= 2; i++) {
                    float r = h * (0.38f + 0.34f * i);
                    c.drawArc(new RectF(w / 2 - r, h - r, w / 2 + r, h + r), 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(w / 2, h * 0.86f, h * 0.13f, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                float bw = w / 5f;
                for (int i = 0; i < 3; i++) {
                    float bh = h * (0.4f + 0.3f * i);
                    float x = i * bw * 1.7f;
                    c.drawRoundRect(new RectF(x, h - bh, x + bw, h), bw / 2, bw / 2, p);
                }
                if (kind == 0) p.setAlpha(90);
            }
        }
    }
}
