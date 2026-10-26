/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** utilita' comuni: font, misure, curve di animazione, forma squircle */
final class Ui {
    private static Typeface display, displayBold, textMed, textSemi, textReg;
    /** curva tipo "ease-out" apple: parte decisa e si posa morbida */
    static final Interpolator EASE = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private Ui() {}

    static void init(Context c) {
        if (display != null) return;
        display = c.getResources().getFont(R.font.inter_display_semibold);
        displayBold = c.getResources().getFont(R.font.inter_display_bold);
        textMed = c.getResources().getFont(R.font.inter_text_medium);
        textSemi = c.getResources().getFont(R.font.inter_text_semibold);
        textReg = c.getResources().getFont(R.font.inter_text_regular);
    }

    static Typeface display() { return display; }
    static Typeface displayBold() { return displayBold; }
    static Typeface textMedium() { return textMed; }
    static Typeface textSemibold() { return textSemi; }
    static Typeface textRegular() { return textReg; }

    static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static void text(TextView t, Typeface tf, float sp, int alpha, float tracking) {
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(Color.argb(alpha, 255, 255, 255));
        t.setLetterSpacing(tracking);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(Ui.dp(t.getContext(), 6), 0, Ui.dp(t.getContext(), 1), Color.argb(60, 0, 0, 0));
    }

    /**
     * rettangolo con angoli "continui" (superellisse) come le icone e i pannelli apple:
     * la curva parte prima e si raccorda senza lo scalino tipico dei cerchi.
     */
    static Path squircle(RectF r, float radius) {
        Path p = new Path();
        float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        float ext = Math.min(rad * 1.28f, Math.min(r.width(), r.height()) / 2f); // estensione della curva (smoothing)
        final int n = 14;
        final double exp = 2.0 / 4.6; // esponente superellisse
        p.moveTo(r.left + ext, r.top);
        p.lineTo(r.right - ext, r.top);
        corner(p, r.right - ext, r.top + ext, ext, 3, n, exp);
        p.lineTo(r.right, r.bottom - ext);
        corner(p, r.right - ext, r.bottom - ext, ext, 0, n, exp);
        p.lineTo(r.left + ext, r.bottom);
        corner(p, r.left + ext, r.bottom - ext, ext, 1, n, exp);
        p.lineTo(r.left, r.top + ext);
        corner(p, r.left + ext, r.top + ext, ext, 2, n, exp);
        p.close();
        return p;
    }

    /** quarto di superellisse centrato in (cx,cy); quadrant: 0=basso-dx,1=basso-sx,2=alto-sx,3=alto-dx */
    private static void corner(Path p, float cx, float cy, float a, int quadrant, int n, double exp) {
        for (int i = 0; i <= n; i++) {
            double t = (Math.PI / 2) * i / n;
            double ct = Math.cos(t), st = Math.sin(t);
            float x = (float) (a * Math.pow(ct, exp));
            float y = (float) (a * Math.pow(st, exp));
            float px, py;
            switch (quadrant) {
                case 3: px = cx + y; py = cy - x; break;       // da alto verso destra
                case 0: px = cx + x; py = cy + y; break;       // da destra verso basso
                case 1: px = cx - y; py = cy + x; break;       // da basso verso sinistra
                default: px = cx - x; py = cy - y; break;      // da sinistra verso alto
            }
            p.lineTo(px, py);
        }
    }
}
