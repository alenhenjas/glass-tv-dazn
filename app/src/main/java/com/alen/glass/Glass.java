/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * pittore "liquid glass": per ogni dimensione pre-calcola ombra e riflessi in bitmap,
 * cosi' ogni frame costa solo pochi drawBitmap (niente lag sul box).
 * strati: ombra, sfondo sfocato ritagliato, bordo che rifrange (sfondo ingrandito sul bordo),
 * tinta, riflesso speculare + bordo luminoso, scia di luce al focus.
 */
final class Glass {
    private final View view;
    private final float radiusFactor; // raggio in frazione del lato corto (0.5 = capsula)
    private final float shadowPad;
    final RectF rect = new RectF();
    private Path path, inner;
    private Bitmap shadow;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // strati del riflesso, disegnati in diretta dalla gpu: [0] = a riposo, [1] = con focus
    private final Paint[] tint = new Paint[2], light = new Paint[2], depth = new Paint[2], rim = new Paint[2], top = new Paint[2];
    private float[] rimW = new float[2];
    private final Matrix m = new Matrix();
    private final Matrix sm = new Matrix();
    private final Matrix shm = new Matrix();
    private final int[] loc = new int[2];
    private final int[] rootLoc = new int[2];
    private float tintIdle = 0.12f, tintActive = 0.26f;

    /** ombre gia' calcolate, condivise tra tutte le view della stessa forma (es. tutte le icone) */
    private static final java.util.LinkedHashMap<String, Bitmap> SHADOWS = new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) { return size() > 40; }
    };
    private static final int SHADOW_DOWNSCALE = 4;

    Glass(View v, float radiusFactor, float shadowPadDp) {
        this.view = v;
        this.radiusFactor = radiusFactor;
        this.shadowPad = Ui.dp(v.getContext(), shadowPadDp);
    }

    void tint(float idle, float active) { tintIdle = idle; tintActive = active; }

    /** chiamare da onSizeChanged: rect = area del vetro dentro la view */
    void layout(float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        float rad = Math.min(rect.width(), rect.height()) * radiusFactor;
        path = Ui.squircle(rect, rad);
        float band = Math.max(3f, Math.min(rect.width(), rect.height()) * 0.05f);
        RectF in = new RectF(rect.left + band, rect.top + band, rect.right - band, rect.bottom - band);
        inner = Ui.squircle(in, Math.max(0, rad - band));
        shadow = shadowFor();
        for (int i = 0; i < 2; i++) buildPaints(i, i == 1);
        ov[0] = ov[1] = null;
        sweepPaint.setShader(new LinearGradient(0, 0, rect.width() * 0.45f, 0,
                new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    /** ombra a 1/4 di risoluzione (e' sfocata: non si nota) e in cache: costa pochi ms una volta sola */
    private Bitmap shadowFor() {
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 0 || h <= 0) return null;
        String key = w + "x" + h + "|" + rect + "|" + radiusFactor + "|" + shadowPad;
        synchronized (SHADOWS) {
            Bitmap cached = SHADOWS.get(key);
            if (cached != null) return cached;
        }
        int d = SHADOW_DOWNSCALE;
        Bitmap sb = Bitmap.createBitmap(Math.max(1, (w + d - 1) / d), Math.max(1, (h + d - 1) / d), Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(sb);
        c.scale(1f / d, 1f / d);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, shadowPad * 0.8f), BlurMaskFilter.Blur.NORMAL));
        c.translate(0, shadowPad * 0.35f);
        c.drawPath(path, p);
        synchronized (SHADOWS) { SHADOWS.put(key, sb); }
        return sb;
    }

    private void buildPaints(int i, boolean active) {
        // tinta lattiginosa
        tint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        tint[i].setColor(Color.argb((int) (255 * (active ? tintActive : tintIdle)), 255, 255, 255));
        // luce diffusa in alto a sinistra
        light[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        light[i].setShader(new RadialGradient(rect.left + rect.width() * 0.22f, rect.top + rect.height() * 0.05f,
                Math.max(rect.width(), rect.height()) * 0.75f,
                new int[]{Color.argb(active ? 70 : 46, 255, 255, 255), Color.argb(0, 255, 255, 255)}, null, Shader.TileMode.CLAMP));
        // ombra interna in basso: da' spessore al vetro
        depth[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        depth[i].setShader(new LinearGradient(0, rect.bottom - rect.height() * 0.45f, 0, rect.bottom,
                Color.argb(0, 0, 0, 0), Color.argb(active ? 26 : 34, 0, 0, 30), Shader.TileMode.CLAMP));
        // bordo speculare: brillante in alto a sinistra e in basso a destra
        float sw = Math.max(1.5f, Ui.dp(view.getContext(), active ? 1.6f : 1.2f));
        rimW[i] = sw;
        rim[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim[i].setStyle(Paint.Style.STROKE);
        rim[i].setStrokeWidth(sw);
        rim[i].setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{Color.argb(active ? 245 : 200, 255, 255, 255), Color.argb(30, 255, 255, 255),
                        Color.argb(20, 255, 255, 255), Color.argb(active ? 170 : 120, 255, 255, 255)},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        // filo di luce interno sul lato alto
        top[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        top[i].setStyle(Paint.Style.STROKE);
        top[i].setStrokeWidth(sw * 0.7f);
        top[i].setShader(new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.3f,
                Color.argb(active ? 120 : 80, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
    }

    /** riflessi pre-disegnati in bitmap (blit velocissimo per frame), condivisi tra view uguali e creati solo se servono */
    private static final java.util.LinkedHashMap<String, Bitmap> OVERLAYS = new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> e) { return size() > 24; }
    };
    private final Bitmap[] ov = new Bitmap[2];

    private Bitmap overlay(int i) {
        if (ov[i] != null) return ov[i];
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 0 || h <= 0) return null;
        String key = w + "x" + h + "|" + rect + "|" + radiusFactor + "|" + (i == 1 ? tintActive : tintIdle) + "|" + i;
        synchronized (OVERLAYS) {
            Bitmap cached = OVERLAYS.get(key);
            if (cached != null) return ov[i] = cached;
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.save();
        c.clipPath(path);
        c.drawRect(rect, tint[i]);
        c.drawRect(rect, light[i]);
        c.drawRect(rect, depth[i]);
        c.drawPath(path, rim[i]); // solo la meta' interna del tratto: bordo nitido
        c.restore();
        c.save();
        c.translate(0, rimW[i]);
        c.clipPath(path);
        c.drawPath(path, top[i]);
        c.restore();
        synchronized (OVERLAYS) { OVERLAYS.put(key, b); }
        return ov[i] = b;
    }

    /** sweep: 0..1 posizione della scia di luce, <0 nessuna scia */
    void draw(Canvas c, boolean active, float sweep) {
        if (path == null) return;
        if (shadow != null) {
            bmp.setColor(Color.argb(active ? 120 : 70, 0, 0, 0));
            shm.setScale(SHADOW_DOWNSCALE, SHADOW_DOWNSCALE);
            c.drawBitmap(shadow, shm, bmp);
        }
        bmp.setColor(Color.WHITE); // alpha piena: lo sfondo sfocato deve essere opaco
        Bitmap bg = Wallpaper.blurred;
        if (bg != null) {
            view.getLocationInWindow(loc);
            view.getRootView().getLocationInWindow(rootLoc);
            float sx = scaleOf(view);
            float x = loc[0] - rootLoc[0], y = loc[1] - rootLoc[1];
            float k = Wallpaper.BLUR_SCALE / sx;
            c.save();
            c.clipPath(path);
            m.setScale(k, k);
            m.postTranslate(-x / sx, -y / sx);
            c.drawBitmap(bg, m, bmp);
            // rifrazione: sul bordo lo sfondo e' leggermente ingrandito, come attraverso una lente
            c.clipOutPath(inner);
            m.postScale(1.035f, 1.035f, rect.centerX(), rect.centerY());
            c.drawBitmap(bg, m, bmp);
            c.restore();
        }
        Bitmap o = overlay(active ? 1 : 0);
        if (o != null) c.drawBitmap(o, 0, 0, bmp);
        if (sweep >= 0f && sweep <= 1f) {
            c.save();
            c.clipPath(path);
            float span = rect.width() * 1.8f;
            sm.setRotate(20f);
            sm.postTranslate(rect.left - rect.width() * 0.6f + span * sweep, rect.top);
            sweepPaint.getShader().setLocalMatrix(sm);
            c.drawRect(rect, sweepPaint);
            c.restore();
        }
    }

    /** scala effettiva (la view o un suo contenitore possono essere ingranditi dal focus) */
    private static float scaleOf(View v) {
        float s = 1f;
        View cur = v;
        while (cur != null) {
            s *= cur.getScaleX();
            if (!(cur.getParent() instanceof View)) break;
            cur = (View) cur.getParent();
        }
        return s;
    }
}
