/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * multitasking stile ipad: una scheda per ogni app aperta con l'anteprima di dove eri rimasto.
 * ok = riapri | tieni premuto ok = chiudi l'app | in alto: chiudi tutto.
 */
public class RecentsActivity extends Activity {
    private LinearLayout row;
    private FrameLayout root;
    private TextView empty;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 8, 16));
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        if (Wallpaper.blurred != null) bg.setImageBitmap(Wallpaper.blurred);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackgroundColor(Color.argb(90, 0, 0, 10));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("App aperte");
        Ui.text(title, Ui.displayBold(), 34, 255, -0.02f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.leftMargin = dp(72); tlp.topMargin = dp(52);
        root.addView(title, tlp);

        ControlButton closeAll = new ControlButton(this, Glyphs.Kind.CLOSE_ALL, "Chiudi tutto");
        closeAll.setOnClickListener(v -> closeAll());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(110), dp(96), Gravity.TOP | Gravity.END);
        clp.rightMargin = dp(56); clp.topMargin = dp(36);
        root.addView(closeAll, clp);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setSmoothScrollingEnabled(true);
        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setPadding(dp(72), dp(30), dp(72), dp(30));
        hs.addView(row);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, dp(360), Gravity.CENTER_VERTICAL);
        hlp.topMargin = dp(30);
        root.addView(hs, hlp);

        empty = new TextView(this);
        empty.setText("Nessuna app aperta");
        Ui.text(empty, Ui.textMedium(), 20, 190, 0f);
        empty.setVisibility(View.GONE);
        root.addView(empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        load();
    }

    private void load() {
        new Thread(() -> {
            Set<String> alive = Closer.runningSet();
            PackageManager pm = getPackageManager();
            List<Apps.Entry> all = Apps.all(this);
            List<Apps.Entry> cards = new ArrayList<>();
            List<String> order = Recents.ordered();
            // anche le app aperte prima che il servizio le registrasse
            for (String p : Running.list(this)) if (!order.contains(p)) order.add(p);
            for (String pkg : order) {
                if (!Closer.isRunning(this, pkg, alive) || !Closer.closable(pkg)) continue;
                for (Apps.Entry e : all) if (e.pkg.equals(pkg)) { e.icon = Icons.of(e.pkg, e.raw); cards.add(e); break; }
            }
            List<Bitmap> shots = new ArrayList<>();
            for (Apps.Entry e : cards) shots.add(Recents.shotOf(this, e.pkg));
            ui.post(() -> show(cards, shots));
        }, "recents").start();
    }

    private void show(List<Apps.Entry> cards, List<Bitmap> shots) {
        row.removeAllViews();
        empty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < cards.size(); i++) {
            Apps.Entry e = cards.get(i);
            Card c = new Card(this, e, shots.get(i));
            c.setOnClickListener(v -> {
                try { startActivity(e.launch); } catch (Exception ignored) { }
                finish();
            });
            c.setOnLongClickListener(v -> { close(c, e); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(400), dp(300));
            lp.rightMargin = dp(36);
            row.addView(c, lp);
        }
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    private void close(Card c, Apps.Entry e) {
        long before = Closer.availMem(this);
        Closer.close(this, e.pkg);
        c.animate().translationY(-dp(120)).alpha(0f).setDuration(240).setInterpolator(Ui.EASE).withEndAction(() -> {
            int idx = row.indexOfChild(c);
            row.removeView(c);
            if (row.getChildCount() == 0) empty.setVisibility(View.VISIBLE);
            else row.getChildAt(Math.min(idx, row.getChildCount() - 1)).requestFocus();
            long mb = (Closer.availMem(this) - before) / (1024 * 1024);
            Sheet.toast(root, e.label + " chiusa" + (mb > 5 ? "  ·  " + mb + " MB liberati" : ""));
        }).start();
    }

    private void closeAll() {
        long before = Closer.availMem(this);
        Closer.closeAll(this, Apps.all(this));
        for (int i = 0; i < row.getChildCount(); i++)
            row.getChildAt(i).animate().translationY(-dp(120)).alpha(0f).setStartDelay(i * 40L).setDuration(240).start();
        ui.postDelayed(() -> {
            row.removeAllViews();
            empty.setVisibility(View.VISIBLE);
            long mb = (Closer.availMem(this) - before) / (1024 * 1024);
            Sheet.toast(root, "Tutte le app chiuse" + (mb > 5 ? "  ·  " + mb + " MB liberati" : ""));
        }, 420);
    }

    private int dp(float v) { return (int) Ui.dp(this, v); }

    /** scheda: anteprima arrotondata (o icona grande su vetro), icona e nome sotto */
    static final class Card extends View {
        private final Apps.Entry e;
        private final Bitmap shot;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private boolean active, longFired;

        Card(Context c, Apps.Entry e, Bitmap shot) {
            super(c);
            this.e = e; this.shot = shot;
            setFocusable(true); setClickable(true); setLongClickable(true);
            tp.setTypeface(Ui.textSemibold());
            tp.setTextSize(Ui.dp(c, 16));
            tp.setColor(Color.WHITE);
            tp.setShadowLayer(Ui.dp(c, 5), 0, Ui.dp(c, 1), Color.argb(90, 0, 0, 0));
            setOnFocusChangeListener((v, has) -> {
                active = has;
                animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(190).setInterpolator(Ui.EASE).start();
                invalidate();
            });
        }

        @Override public boolean dispatchKeyEvent(KeyEvent ev) {
            int k = ev.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                    if (ev.getRepeatCount() == 0) longFired = false;
                    if (!longFired && ((ev.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 || ev.getRepeatCount() >= 2)) {
                        longFired = true; performLongClick(); return true;
                    }
                } else if (longFired) { longFired = false; return true; }
            }
            return super.dispatchKeyEvent(ev);
        }

        @Override protected void onDraw(Canvas c) {
            Context ctx = getContext();
            float w = getWidth(), u = Ui.dp(ctx, 1);
            float sh = w * 9f / 16f;
            RectF r = new RectF(0, u * 4, w, u * 4 + sh);
            Path sq = Ui.squircle(r, u * 26);
            // ombra
            p.setShader(null);
            p.setColor(Color.argb(active ? 120 : 70, 0, 0, 0));
            c.save(); c.translate(0, u * (active ? 10 : 6)); c.drawPath(sq, p); c.restore();
            c.save();
            c.clipPath(sq);
            if (shot != null) {
                c.drawBitmap(shot, null, r, p);
            } else {
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom, Color.rgb(60, 60, 76), Color.rgb(28, 28, 38), Shader.TileMode.CLAMP));
                c.drawRect(r, p);
                p.setShader(null);
                if (e.icon != null) {
                    float is = sh * 0.42f;
                    c.drawBitmap(e.icon, null, new RectF(r.centerX() - is / 2, r.centerY() - is / 2, r.centerX() + is / 2, r.centerY() + is / 2), p);
                }
            }
            c.restore();
            // bordo vetro
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(u * (active ? 2.4f : 1.2f));
            rim.setColor(Color.argb(active ? 240 : 90, 255, 255, 255));
            c.drawPath(sq, rim);
            // icona + nome
            float is = u * 40, y = r.bottom + u * 18;
            if (e.icon != null) c.drawBitmap(e.icon, null, new RectF(u * 6, y, u * 6 + is, y + is), p);
            CharSequence t = TextUtils.ellipsize(e.label, tp, w - is - u * 24, TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), u * 6 + is + u * 12, y + is / 2 - (tp.ascent() + tp.descent()) / 2, tp);
        }
    }
}
