/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** tutte le app; tieni premuto OK su un'app per aprirne le informazioni (disattiva/disinstalla) */
public class AllAppsActivity extends Activity {
    private GridLayout grid;
    private FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(6, 10, 34));
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        if (Wallpaper.full != null) bg.setImageBitmap(Wallpaper.full);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(72), dp(44), dp(72), 0);
        TextView title = new TextView(this);
        title.setText("App");
        Ui.text(title, Ui.displayBold(), 38, 255, -0.02f);
        col.addView(title);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setSmoothScrollingEnabled(true);
        sv.setPadding(0, dp(12), 0, dp(48));
        grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(grid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        sv.addView(wrap);
        // allo scorrimento il vetro deve ricalcolare cosa c'e' dietro
        sv.getViewTreeObserver().addOnScrollChangedListener(() -> {
            for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).invalidate();
        });
        col.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        load();
    }

    @Override protected void onResume() {
        super.onResume();
        Closer.prewarm();
    }

    private void load() {
        new Thread(() -> {
            List<Apps.Entry> all = Apps.all(this);
            for (Apps.Entry e : all) { e.icon = Icons.of(e.pkg, e.raw); e.label = Apps.niceLabel(e.pkg, e.label); }
            // il browser integrato compare qui, cosi' si puo' rimettere in home
            Apps.Entry web = Apps.browser(this);
            int pos = 0;
            while (pos < all.size() && all.get(pos).label.compareToIgnoreCase(web.label) < 0) pos++;
            all.add(pos, web);
            ui.post(() -> show(all));
        }, "allapps").start();
    }

    private void show(List<Apps.Entry> all) {
        grid.removeAllViews();
        for (Apps.Entry e : all) {
            AppCell cell = new AppCell(this, 112, 70, true, true, 1.12f);
            cell.bind(e.icon, e.label);
            cell.setOnClickListener(v -> {
                try { startActivity(e.launch); }
                catch (ActivityNotFoundException ex) { Toast.makeText(this, "Impossibile aprire " + e.label, Toast.LENGTH_SHORT).show(); }
            });
            cell.setOnLongClickListener(v -> { AppMenu.show(this, root, e, new View[]{grid}, null); return true; });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(136);
            lp.height = dp(164);
            grid.addView(cell, lp);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private int dp(float v) { return (int) Ui.dp(this, v); }
}
