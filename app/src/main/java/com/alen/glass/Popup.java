/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * popup in vetro liquido con righe selezionabili (stile pannelli di controllo apple).
 * le righe si possono ricostruire al volo (es. elenco reti che si aggiorna).
 */
final class Popup {
    enum Trail { NONE, CHECK, CHEVRON, SWITCH_ON, SWITCH_OFF, VALUE, SIGNAL, PICKER }

    /** selettore a scorrimento: sinistra/destra (o ok) cambiano il valore, mostrato come "‹ valore ›" */
    interface Picker { String label(); int swatch(); void step(int dir); }

    static final class Item {
        String label, sub, value;
        Trail trail = Trail.NONE;
        int level;          // per SIGNAL: 0..4
        boolean lock, enabled = true, destructive, header;
        Runnable click;
        Picker picker;
        Item(String l) { label = l; }
        Item picker(Picker p) { picker = p; trail = Trail.PICKER; return this; }
        Item sub(String s) { sub = s; return this; }
        Item trail(Trail t) { trail = t; return this; }
        Item value(String v) { value = v; trail = Trail.VALUE; return this; }
        Item signal(int lv, boolean locked) { level = lv; lock = locked; trail = Trail.SIGNAL; return this; }
        Item on(Runnable r) { click = r; return this; }
        Item disabled() { enabled = false; return this; }
        Item red() { destructive = true; return this; }
        Item header() { header = true; enabled = false; return this; }
    }

    final FrameLayout root, overlay;
    final GlassPanel panel;
    final LinearLayout list;
    final TextView title, subtitle;
    private final View[] block;
    private final View previous;
    private Runnable onClose;

    private Popup(FrameLayout root, String t, float widthDp, View... block) {
        this.root = root;
        this.block = block;
        Context c = root.getContext();
        previous = root.findFocus();
        overlay = new FrameLayout(c) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && !(findFocus() instanceof android.widget.EditText)) {
                    if (e.getAction() == KeyEvent.ACTION_UP) close();
                    return true;
                }
                if (super.dispatchKeyEvent(e)) return true;
                // il focus non esce mai dal popup: la ricerca della prossima voce resta dentro al pannello
                int dir = dirOf(e.getKeyCode());
                if (dir != 0) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        View f = findFocus();
                        View n = android.view.FocusFinder.getInstance().findNextFocus(this, f, dir);
                        if (n != null) n.requestFocus(dir);
                    }
                    return true;
                }
                return false;
            }
        };
        overlay.setTag(Sheet.OVERLAY);
        overlay.setBackgroundColor(Color.argb(120, 0, 0, 8));
        overlay.setClickable(true);
        panel = new GlassPanel(c, 0.08f, 8, 0.16f);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 26);
        col.setPadding(pad, dp(c, 24), pad, dp(c, 18));
        title = new TextView(c);
        title.setText(t);
        Ui.text(title, Ui.displayBold(), 24, 255, -0.015f);
        col.addView(title);
        subtitle = new TextView(c);
        Ui.text(subtitle, Ui.textMedium(), 13.5f, 170, 0f);
        subtitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(c, 4);
        col.addView(subtitle, slp);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 14);
        col.addView(sv, lp);
        panel.addView(col, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(c, widthDp), -2, Gravity.CENTER);
        plp.topMargin = dp(c, 40); plp.bottomMargin = dp(c, 40);
        overlay.addView(panel, plp);
        sv.getViewTreeObserver().addOnScrollChangedListener(panel::invalidate);
    }

    static Popup show(FrameLayout root, String title, float widthDp, View... block) {
        Popup p = new Popup(root, title, widthDp, block);
        for (View b : block) if (b instanceof ViewGroup) ((ViewGroup) b).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(p.overlay, new FrameLayout.LayoutParams(-1, -1));
        p.overlay.setAlpha(0f);
        p.panel.setScaleX(0.95f);
        p.panel.setScaleY(0.95f);
        p.overlay.animate().alpha(1f).setDuration(110).setInterpolator(Ui.EASE).start();
        p.panel.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(Ui.EASE).start();
        return p;
    }

    Popup sub(String s) {
        subtitle.setText(s);
        subtitle.setVisibility(s == null ? View.GONE : View.VISIBLE);
        return this;
    }

    Popup onClose(Runnable r) { onClose = r; return this; }

    /** sostituisce le righe; mantiene il focus sulla stessa posizione se possibile */
    void set(List<Item> items) {
        View f = list.findFocus();
        int idx = f == null ? -1 : list.indexOfChild(f);
        list.removeAllViews();
        Context c = root.getContext();
        View firstEnabled = null;
        for (Item it : items) {
            Row r = new Row(c, it);
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(c, it.header ? 38 : (it.sub != null ? 64 : 54))));
            if (firstEnabled == null && it.enabled) firstEnabled = r;
        }
        View target = idx >= 0 && idx < list.getChildCount() && list.getChildAt(idx).isFocusable() ? list.getChildAt(idx) : firstEnabled;
        if (target != null) { View t = target; t.post(t::requestFocus); }
        panel.invalidate();
    }

    void add(View v, int heightDp) {
        list.addView(v, new LinearLayout.LayoutParams(-1, heightDp > 0 ? dp(root.getContext(), heightDp) : -2));
    }

    void close() {
        if (overlay.getTag() == null) return; // gia' in chiusura
        overlay.setTag(null);
        for (View b : block) if (b instanceof ViewGroup) ((ViewGroup) b).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        overlay.animate().alpha(0f).setDuration(90).withEndAction(() -> root.removeView(overlay)).start();
        if (previous != null) previous.post(previous::requestFocus);
        if (onClose != null) onClose.run();
    }

    static int dp(Context c, float v) { return (int) Ui.dp(c, v); }

    private static int dirOf(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }

    /** sposta il pannello sul lato destro con sfondo meno scuro (anteprima dal vivo di cio' che sta a sinistra) */
    Popup side() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.rightMargin = dp(root.getContext(), 56);
        lp.topMargin = lp.bottomMargin = dp(root.getContext(), 20);
        panel.setLayoutParams(lp);
        overlay.setBackgroundColor(Color.argb(40, 0, 0, 8));
        return this;
    }

    /** riga del popup */
    static final class Row extends View {
        private final Item it;
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG), sp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        Row(Context c, Item it) {
            super(c);
            this.it = it;
            setFocusable(it.enabled);
            setClickable(it.enabled);
            tp.setTextSize(Ui.dp(c, it.header ? 12.5f : 16));
            sp.setTextSize(Ui.dp(c, 12.5f));
            sp.setTypeface(Ui.textMedium());
            setOnFocusChangeListener((v, has) -> { active = has; invalidate(); });
            setOnClickListener(v -> {
                if (it.picker != null) { it.picker.step(1); invalidate(); }
                if (it.click != null) it.click.run();
            });
        }

        @Override public boolean onKeyDown(int kc, KeyEvent e) {
            if (it.picker != null && (kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                it.picker.step(kc == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                invalidate();
                return true;
            }
            return super.onKeyDown(kc, e);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), pad = Ui.dp(getContext(), 16);
            if (active) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(62, 255, 255, 255));
                c.drawPath(Ui.squircle(new RectF(0, Ui.dp(getContext(), 2), w, h - Ui.dp(getContext(), 2)), Ui.dp(getContext(), 14)), p);
            }
            if (it.header) {
                tp.setTypeface(Ui.textSemibold());
                tp.setColor(Color.argb(150, 255, 255, 255));
                tp.setLetterSpacing(0.06f);
                c.drawText(it.label.toUpperCase(), pad, h - Ui.dp(getContext(), 10), tp);
                return;
            }
            int alpha = it.enabled ? 255 : 95;
            tp.setTypeface(active ? Ui.textSemibold() : Ui.textMedium());
            tp.setColor(it.destructive && it.enabled ? Color.rgb(255, 105, 97) : Color.argb(alpha, 255, 255, 255));
            float trailW = trailWidth();
            float maxText = w - pad * 2 - trailW;
            CharSequence label = TextUtils.ellipsize(it.label, tp, maxText, TextUtils.TruncateAt.END);
            if (it.sub != null) {
                c.drawText(label, 0, label.length(), pad, h / 2f - Ui.dp(getContext(), 3), tp);
                sp.setColor(Color.argb(it.enabled ? 160 : 80, 255, 255, 255));
                CharSequence s = TextUtils.ellipsize(it.sub, sp, maxText, TextUtils.TruncateAt.END);
                c.drawText(s, 0, s.length(), pad, h / 2f + Ui.dp(getContext(), 17), sp);
            } else {
                float y = h / 2f - (tp.ascent() + tp.descent()) / 2f;
                c.drawText(label, 0, label.length(), pad, y, tp);
            }
            drawTrail(c, w - pad, h / 2f, alpha);
        }

        private float trailWidth() {
            switch (it.trail) {
                case SWITCH_ON: case SWITCH_OFF: return Ui.dp(getContext(), 60);
                case VALUE: sp.setTextSize(Ui.dp(getContext(), 14)); return sp.measureText(it.value == null ? "" : it.value) + Ui.dp(getContext(), 16);
                case SIGNAL: return Ui.dp(getContext(), 56);
                case PICKER: {
                    sp.setTextSize(Ui.dp(getContext(), 14));
                    float tw = sp.measureText(it.picker.label()) + Ui.dp(getContext(), 44);
                    if (it.picker.swatch() != 0) tw += Ui.dp(getContext(), 24);
                    return tw;
                }
                case NONE: return 0;
                default: return Ui.dp(getContext(), 30);
            }
        }

        private void drawTrail(Canvas c, float right, float cy, int alpha) {
            float u = Ui.dp(getContext(), 1);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (it.trail) {
                case CHECK: {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.6f * u);
                    p.setColor(Color.rgb(64, 156, 255));
                    Path pa = new Path();
                    pa.moveTo(right - 18 * u, cy); pa.lineTo(right - 12 * u, cy + 6 * u); pa.lineTo(right - 1 * u, cy - 7 * u);
                    c.drawPath(pa, p);
                    break;
                }
                case CHEVRON: {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.2f * u);
                    p.setColor(Color.argb(150, 255, 255, 255));
                    Path pa = new Path();
                    pa.moveTo(right - 8 * u, cy - 7 * u); pa.lineTo(right - 1 * u, cy); pa.lineTo(right - 8 * u, cy + 7 * u);
                    c.drawPath(pa, p);
                    break;
                }
                case SWITCH_ON: case SWITCH_OFF: {
                    boolean on = it.trail == Trail.SWITCH_ON;
                    RectF r = new RectF(right - 51 * u, cy - 15.5f * u, right, cy + 15.5f * u);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(on ? Color.rgb(52, 199, 89) : Color.argb(70, 255, 255, 255));
                    c.drawRoundRect(r, 15.5f * u, 15.5f * u, p);
                    p.setColor(Color.WHITE);
                    float kx = on ? r.right - 15.5f * u : r.left + 15.5f * u;
                    c.drawCircle(kx, cy, 13 * u, p);
                    break;
                }
                case VALUE: {
                    sp.setColor(Color.argb(Math.min(alpha, 175), 255, 255, 255));
                    sp.setTextSize(Ui.dp(getContext(), 14));
                    String v = it.value == null ? "" : it.value;
                    c.drawText(v, right - sp.measureText(v), cy - (sp.ascent() + sp.descent()) / 2f, sp);
                    sp.setTextSize(Ui.dp(getContext(), 12.5f));
                    break;
                }
                case PICKER: {
                    String v = it.picker.label();
                    sp.setTextSize(Ui.dp(getContext(), 14));
                    sp.setColor(Color.argb(active ? 255 : 185, 255, 255, 255));
                    float ty = cy - (sp.ascent() + sp.descent()) / 2f;
                    // freccia destra
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.2f * u);
                    p.setColor(Color.argb(active ? 230 : 110, 255, 255, 255));
                    Path pa = new Path();
                    pa.moveTo(right - 7 * u, cy - 6 * u); pa.lineTo(right - 1 * u, cy); pa.lineTo(right - 7 * u, cy + 6 * u);
                    c.drawPath(pa, p);
                    float tx = right - 18 * u - sp.measureText(v);
                    c.drawText(v, tx, ty, sp);
                    float lx = tx - 12 * u;
                    int sw = it.picker.swatch();
                    if (sw != 0) {
                        float r = 8 * u, cx = tx - 10 * u - r;
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(sw | 0xFF000000);
                        c.drawCircle(cx, cy, r, p);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(1.2f * u);
                        p.setColor(Color.argb(120, 255, 255, 255));
                        c.drawCircle(cx, cy, r, p);
                        lx = cx - r - 12 * u;
                    }
                    // freccia sinistra
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.2f * u);
                    p.setColor(Color.argb(active ? 230 : 110, 255, 255, 255));
                    Path pb = new Path();
                    pb.moveTo(lx + 6 * u, cy - 6 * u); pb.lineTo(lx, cy); pb.lineTo(lx + 6 * u, cy + 6 * u);
                    c.drawPath(pb, p);
                    break;
                }
                case SIGNAL: {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.4f * u);
                    float bx = right - 12 * u, by = cy + 7 * u;
                    for (int i = 1; i <= 3; i++) {
                        p.setColor(i <= Math.max(1, it.level - 1) ? Color.argb(alpha, 255, 255, 255) : Color.argb(60, 255, 255, 255));
                        float rr = 5.5f * u * i;
                        c.drawArc(new RectF(bx - rr, by - rr, bx + rr, by + rr), 225, 90, false, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(alpha, 255, 255, 255));
                    c.drawCircle(bx, by - 0.5f * u, 2 * u, p);
                    if (it.lock) {
                        float lx = right - 38 * u, ly = cy;
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Color.argb(Math.min(alpha, 200), 255, 255, 255));
                        c.drawRoundRect(new RectF(lx - 5.5f * u, ly - 1 * u, lx + 5.5f * u, ly + 7 * u), 1.5f * u, 1.5f * u, p);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(1.8f * u);
                        c.drawArc(new RectF(lx - 3.6f * u, ly - 7 * u, lx + 3.6f * u, ly + 1 * u), 180, 180, false, p);
                    }
                    break;
                }
                default:
            }
        }
    }

    static List<Item> items() { return new ArrayList<>(); }
}
