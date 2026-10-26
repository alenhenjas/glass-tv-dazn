/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.FrameLayout;

/** contenitore in vetro liquido (dock, pillola di stato): il vetro viene disegnato sotto ai figli */
final class GlassPanel extends FrameLayout {
    private final Glass glass;
    private final float inset;

    GlassPanel(Context c, float radiusFactor, float insetDp, float tintIdle) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        inset = Ui.dp(c, insetDp);
        glass = new Glass(this, radiusFactor, insetDp);
        glass.tint(tintIdle, tintIdle);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glass.layout(inset, inset, w - inset, h - inset);
    }

    @Override protected void onDraw(Canvas c) {
        glass.draw(c, false, -1f);
    }
}
