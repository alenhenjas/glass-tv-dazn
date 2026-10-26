/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * cella app disegnata interamente a mano (una sola view = massima fluidita'):
 * pannello liquid glass + icona squircle + etichetta sotto, come la home di ipad.
 * al focus: ingrandimento con curva apple, vetro piu' luminoso e scia di luce.
 */
final class AppCell extends View {
    private final float glassSize, iconSize, labelGap;
    private final boolean showLabel, showGlass;
    private final Glass glass;
    private Bitmap icon;
    private String label = "";
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF iconRect = new RectF();
    private boolean active;
    private float sweep = -1f;
    private ValueAnimator sweepAnim;
    private final float focusScale;

    AppCell(Context c, float glassDp, float iconDp, boolean showLabel, boolean showGlass, float focusScale) {
        super(c);
        this.glassSize = Ui.dp(c, glassDp);
        this.iconSize = Ui.dp(c, iconDp);
        this.labelGap = Ui.dp(c, 14);
        this.showLabel = showLabel;
        this.showGlass = showGlass;
        this.focusScale = focusScale;
        glass = new Glass(this, 0.26f, 16);
        setFocusable(true);
        setClickable(true);
        text.setTypeface(Ui.textMedium());
        text.setTextSize(Ui.dp(c, 14.5f));
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setLetterSpacing(-0.005f);
        text.setShadowLayer(Ui.dp(c, 5), 0, Ui.dp(c, 1), Color.argb(90, 0, 0, 0));
        setOnFocusChangeListener((v, has) -> setActive(has));
    }

    private boolean longFired;

    /** pressione lunga di ok: sia dal timer della view sia dal segnale FLAG_LONG_PRESS del sistema */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        int kc = e.getKeyCode();
        boolean confirm = kc == android.view.KeyEvent.KEYCODE_DPAD_CENTER || kc == android.view.KeyEvent.KEYCODE_ENTER || kc == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (confirm && isLongClickable()) {
            // gestione completa di ok: niente timer di pressione lunga di android (aprirebbe un secondo menu)
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { longFired = false; setPressed(true); }
                if (!longFired && ((e.getFlags() & android.view.KeyEvent.FLAG_LONG_PRESS) != 0 || e.getRepeatCount() >= 2)) {
                    longFired = true;
                    setPressed(false);
                    performLongClick();
                }
                return true;
            } else if (e.getAction() == android.view.KeyEvent.ACTION_UP) {
                boolean click = !longFired && isPressed();
                setPressed(false);
                longFired = false;
                if (click) performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** etichetta proporzionata quando la griglia rimpicciolisce le icone */
    void textScale(float s) { text.setTextSize(Ui.dp(getContext(), 14.5f * Math.max(0.72f, Math.min(1f, s)))); }

    void bind(Bitmap icon, String label) {
        this.icon = icon;
        this.label = label == null ? "" : label;
        setContentDescription(this.label);
        invalidate();
    }

    private void setActive(boolean on) {
        active = on;
        animate().cancel();
        float s = on ? focusScale : 1f;
        animate().scaleX(s).scaleY(s)
                .setDuration(on ? 140 : 100).setInterpolator(Ui.EASE).start();
        if (sweepAnim != null) sweepAnim.cancel();
        if (on && showGlass) {
            sweepAnim = ValueAnimator.ofFloat(0f, 1f);
            sweepAnim.setDuration(440);
            sweepAnim.setStartDelay(0);
            sweepAnim.setInterpolator(Ui.EASE);
            sweepAnim.addUpdateListener(a -> { sweep = (float) a.getAnimatedValue(); invalidate(); });
            sweepAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { sweep = -1f; invalidate(); }
            });
            sweepAnim.start();
        } else {
            sweep = -1f;
        }
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float gx = (w - glassSize) / 2f;
        float gy = showLabel ? Ui.dp(getContext(), 6) : (h - glassSize) / 2f;
        if (showGlass) glass.layout(gx, gy, gx + glassSize, gy + glassSize);
        else glass.rect.set(gx, gy, gx + glassSize, gy + glassSize);
        float ix = (w - iconSize) / 2f, iy = gy + (glassSize - iconSize) / 2f;
        iconRect.set(ix, iy, ix + iconSize, iy + iconSize);
        glow.setShader(null);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(Ui.dp(getContext(), 2.2f));
        glow.setColor(Color.argb(235, 255, 255, 255));
    }

    @Override protected void onDraw(Canvas c) {
        if (showGlass) glass.draw(c, active, sweep);
        if (icon != null) c.drawBitmap(icon, null, iconRect, iconPaint);
        if (!showGlass && active) {
            // anello di selezione squircle attorno all'icona del dock (dentro i limiti della view: nessun taglio)
            float g = Ui.dp(getContext(), 4.5f);
            RectF r = new RectF(iconRect.left - g, iconRect.top - g, iconRect.right + g, iconRect.bottom + g);
            c.drawPath(Ui.squircle(r, r.width() * 0.26f), glow);
        }
        if (showLabel && label.length() > 0) {
            text.setAlpha(active ? 255 : 215);
            text.setTypeface(active ? Ui.textSemibold() : Ui.textMedium());
            CharSequence t = TextUtils.ellipsize(label, text, getWidth() - Ui.dp(getContext(), 8), TextUtils.TruncateAt.END);
            float y = glass.rect.bottom + labelGap - text.ascent();
            c.drawText(t, 0, t.length(), getWidth() / 2f, y, text);
        }
    }
}
