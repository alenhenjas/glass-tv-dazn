/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** menu contestuale in vetro (pressione lunga su un'app) e avviso temporaneo in vetro */
final class Sheet {
    private Sheet() {}

    interface Action { void run(); }

    /** riga del menu: evidenziata in vetro chiaro quando ha il focus */
    static final class Row extends View {
        private final String text;
        private final boolean destructive;
        private boolean enabled = true;
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        Row(Context c, String text, boolean destructive) {
            super(c);
            this.text = text;
            this.destructive = destructive;
            setFocusable(true);
            setClickable(true);
            tp.setTypeface(Ui.textMedium());
            tp.setTextSize(Ui.dp(c, 16));
            hl.setColor(Color.argb(60, 255, 255, 255));
            setOnFocusChangeListener((v, has) -> { active = has; invalidate(); });
        }

        void disable() { enabled = false; setFocusable(false); setClickable(false); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (active) {
                float r = Ui.dp(getContext(), 14);
                c.drawPath(Ui.squircle(new android.graphics.RectF(0, 0, getWidth(), getHeight()), r), hl);
            }
            tp.setColor(!enabled ? Color.argb(90, 255, 255, 255) : destructive ? Color.rgb(255, 105, 97) : Color.WHITE);
            tp.setTypeface(active ? Ui.textSemibold() : Ui.textMedium());
            float y = getHeight() / 2f - (tp.ascent() + tp.descent()) / 2f;
            c.drawText(text, Ui.dp(getContext(), 18), y, tp);
        }
    }

    /**
     * mostra il menu sopra alla root. 'block' sono i contenitori a cui togliere il focus finche' il menu e' aperto.
     * restituisce la view overlay (gia' aggiunta).
     */
    static View show(FrameLayout root, Bitmap icon, String title, String[] labels, boolean[] destructive,
                     Action[] actions, View... block) {
        return show(root, icon, title, labels, destructive, null, actions, block);
    }

    /** come sopra; 'enabled' (facoltativo) mostra in grigio e rende non selezionabili le voci false */
    static View show(FrameLayout root, Bitmap icon, String title, String[] labels, boolean[] destructive,
                     boolean[] enabled, Action[] actions, View... block) {
        Context c = root.getContext();
        if (overlayOpen(root)) return null; // mai due menu sovrapposti
        final View previous = root.findFocus(); // al termine il focus torna dove era
        FrameLayout overlay = new FrameLayout(c) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) { dismiss(root, this, block); restore(previous); }
                    return true;
                }
                // il focus resta sempre dentro al menu: su/giu' scorrono le voci attive, sinistra/destra ignorate
                int kc = e.getKeyCode();
                if (kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) move(this, kc == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                    return true;
                }
                if (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                return super.dispatchKeyEvent(e);
            }
        };
        overlay.setTag(OVERLAY);
        overlay.setBackgroundColor(Color.argb(110, 0, 0, 8));
        overlay.setClickable(true);

        GlassPanel panel = new GlassPanel(c, 0.12f, 8, 0.16f);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) Ui.dp(c, 22);
        col.setPadding(pad, pad, pad, (int) Ui.dp(c, 16));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(icon);
            head.addView(iv, new LinearLayout.LayoutParams((int) Ui.dp(c, 44), (int) Ui.dp(c, 44)));
        }
        TextView t = new TextView(c);
        t.setText(title);
        Ui.text(t, Ui.displayBold(), 22, 255, -0.01f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = (int) Ui.dp(c, 14);
        head.addView(t, tlp);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.bottomMargin = (int) Ui.dp(c, 12);
        col.addView(head, hlp);

        View first = null;
        for (int i = 0; i < labels.length; i++) {
            Row row = new Row(c, labels[i], destructive[i]);
            if (enabled != null && !enabled[i]) row.disable();
            final Action a = actions[i];
            row.setOnClickListener(v -> { dismiss(root, overlay, block); restore(previous); if (a != null) a.run(); });
            col.addView(row, new LinearLayout.LayoutParams(-1, (int) Ui.dp(c, 50)));
            if (first == null && row.isFocusable()) first = row;
        }
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
        overlay.addView(panel, new FrameLayout.LayoutParams((int) Ui.dp(c, 360), -2, Gravity.CENTER));

        for (View b : block) if (b instanceof ViewGroup) ((ViewGroup) b).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        overlay.animate().alpha(1f).setDuration(110).setInterpolator(Ui.EASE).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(Ui.EASE).start();
        if (first != null) { View f = first; f.post(f::requestFocus); }
        return overlay;
    }

    /** sposta il focus alla voce attiva successiva/precedente (si ferma ai bordi) */
    private static void move(ViewGroup overlay, int dir) {
        java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        collect(overlay, rows);
        if (rows.isEmpty()) return;
        View cur = overlay.findFocus();
        int i = rows.indexOf(cur);
        int j = i < 0 ? 0 : Math.max(0, Math.min(rows.size() - 1, i + dir));
        rows.get(j).requestFocus();
    }

    private static void collect(ViewGroup g, java.util.List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof Row) { if (v.isFocusable()) out.add(v); }
            else if (v instanceof ViewGroup) collect((ViewGroup) v, out);
        }
    }

    private static void restore(View v) {
        if (v != null) v.post(v::requestFocus);
    }

    static final String OVERLAY = "glass-overlay";

    /** c'e' gia' un menu o un popup aperto sopra questa schermata? */
    static boolean overlayOpen(FrameLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) if (OVERLAY.equals(root.getChildAt(i).getTag())) return true;
        return false;
    }

    static void dismiss(FrameLayout root, View overlay, View... block) {
        if (overlay.getTag() == null) return; // gia' in chiusura
        overlay.setTag(null);
        for (View b : block) if (b instanceof ViewGroup) ((ViewGroup) b).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        overlay.animate().alpha(0f).setDuration(90).withEndAction(() -> root.removeView(overlay)).start();
    }

    /** avviso in vetro in basso al centro, scompare da solo */
    static void toast(FrameLayout root, String msg) {
        Context c = root.getContext();
        GlassPanel p = new GlassPanel(c, 0.5f, 4, 0.18f);
        TextView t = new TextView(c);
        t.setText(msg);
        Ui.text(t, Ui.textSemibold(), 15, 255, 0f);
        t.setGravity(Gravity.CENTER);
        int ph = (int) Ui.dp(c, 26);
        t.setPadding(ph, 0, ph, 0);
        p.addView(t, new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, (int) Ui.dp(c, 52), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) Ui.dp(c, 150);
        root.addView(p, lp);
        p.setAlpha(0f);
        p.setTranslationY(-Ui.dp(c, 10));
        p.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(Ui.EASE).start();
        p.postDelayed(() -> p.animate().alpha(0f).setDuration(250).withEndAction(() -> root.removeView(p)).start(), 2600);
    }
}
