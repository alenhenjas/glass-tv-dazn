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
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/** controllo rapido stile centro di controllo: cerchio in vetro con glifo, etichetta visibile al focus */
final class ControlButton extends View {
    private Glyphs.Kind kind;
    private String label;
    private final Glass glass;
    private final float circle;
    private boolean active;
    private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    ControlButton(Context c, Glyphs.Kind k, String label) {
        super(c);
        this.kind = k;
        this.label = label;
        circle = Ui.dp(c, 54);
        glass = new Glass(this, 0.5f, 10);
        glass.tint(0.12f, 0.34f);
        setFocusable(true);
        setClickable(true);
        text.setTypeface(Ui.textSemibold());
        text.setTextSize(Ui.dp(c, 12.5f));
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setShadowLayer(Ui.dp(c, 4), 0, Ui.dp(c, 1), Color.argb(90, 0, 0, 0));
        setContentDescription(label);
        setOnFocusChangeListener((v, has) -> {
            active = has;
            animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(has ? 140 : 100).setInterpolator(Ui.EASE).start();
            invalidate();
        });
    }

    void set(Glyphs.Kind k, String l) { kind = k; label = l; setContentDescription(l); invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float x = (w - circle) / 2f, y = Ui.dp(getContext(), 4);
        glass.layout(x, y, x + circle, y + circle);
    }

    @Override protected void onDraw(Canvas c) {
        glass.draw(c, active, -1f);
        RectF r = glass.rect;
        float cx = r.centerX(), cy = r.centerY(), s = r.width() * 0.2f;
        g.setColor(Color.WHITE);
        Glyphs.draw(c, kind, cx, cy, s, g, -1f);
        if (active && label != null) {
            c.drawText(label, getWidth() / 2f, r.bottom + Ui.dp(getContext(), 12) - text.ascent(), text);
        }
    }
}
