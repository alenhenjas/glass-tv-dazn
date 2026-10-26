/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import java.io.File;
import java.io.FileOutputStream;

/**
 * sfondo astratto in stile apple generato a runtime (nessuna immagine protetta):
 * campi di colore fluidi + nastri di luce, poi una versione "vetro" sfocata e piu' satura
 * che i pannelli liquid glass ritagliano dietro di se'. entrambe salvate in cache su disco.
 */
final class Wallpaper {
    static final int BLUR_SCALE = 6;
    private static final int VERSION = 4;
    static Bitmap full, blurred;

    private Wallpaper() {}

    static synchronized void ensure(Context ctx, int w, int h) {
        if (full != null && full.getWidth() == w && full.getHeight() == h) return;
        File dir = ctx.getCacheDir();
        File ff = new File(dir, "wp_" + VERSION + "_" + w + "x" + h + ".png");
        File fb = new File(dir, "wpb_" + VERSION + "_" + w + "x" + h + ".png");
        if (ff.exists() && fb.exists()) {
            Bitmap a = BitmapFactory.decodeFile(ff.getAbsolutePath());
            Bitmap b = BitmapFactory.decodeFile(fb.getAbsolutePath());
            if (a != null && b != null) { full = a; blurred = b; return; }
        }
        full = fromResource(ctx, w, h);
        blurred = glass(full, w, h);
        save(full, ff);
        save(blurred, fb);
    }

    /** sfondo scelto dall'utente (res/drawable-nodpi/wallpaper.jpg), adattato allo schermo */
    private static Bitmap fromResource(Context ctx, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap src = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.wallpaper, o);
        if (src == null) return render(w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        float dw = src.getWidth() * s, dh = src.getHeight() * s;
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        c.drawBitmap(src, null, new android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), p);
        src.recycle();
        return out;
    }

    private static Bitmap render(int w, int h) {
        // si disegna a 1/3 di risoluzione e si sfoca: i gradienti risultano setosi e senza banding
        int sw = w / 3, sh = h / 3;
        Bitmap s = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(s);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, sw, sh, Color.rgb(6, 10, 34), Color.rgb(18, 8, 40), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, sw, sh, p);
        field(c, sw * 0.10f, sh * 0.05f, sw * 0.62f, Color.rgb(36, 92, 255), 235);
        field(c, sw * 0.62f, sh * 0.00f, sw * 0.52f, Color.rgb(120, 70, 255), 225);
        field(c, sw * 1.02f, sh * 0.62f, sw * 0.50f, Color.rgb(255, 64, 140), 215);
        field(c, sw * 0.38f, sh * 1.05f, sw * 0.48f, Color.rgb(255, 120, 72), 150);
        field(c, sw * 0.02f, sh * 0.92f, sw * 0.40f, Color.rgb(0, 190, 210), 190);
        field(c, sw * 0.55f, sh * 0.48f, sw * 0.30f, Color.rgb(170, 110, 255), 120);
        // nastri di luce diagonali
        ribbon(c, sw, sh, 0.05f, 0.78f, 0.55f, 0.35f, 1.05f, 0.05f, Color.argb(90, 255, 255, 255), sh * 0.10f);
        ribbon(c, sw, sh, -0.05f, 0.98f, 0.45f, 0.62f, 1.05f, 0.30f, Color.argb(60, 190, 220, 255), sh * 0.07f);
        Bitmap soft = Blur.stack(s, 16);
        s.recycle();

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        oc.drawBitmap(soft, null, new Rect(0, 0, w, h), fp);
        soft.recycle();
        // vignettatura leggera per leggibilita' di orario ed etichette
        Paint v = new Paint(Paint.DITHER_FLAG);
        v.setShader(new RadialGradient(w * 0.5f, h * 0.42f, (float) Math.hypot(w, h) * 0.6f,
                new int[]{Color.TRANSPARENT, Color.argb(60, 0, 0, 10), Color.argb(140, 0, 0, 10)},
                new float[]{0.5f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        oc.drawRect(0, 0, w, h, v);
        return out;
    }

    /** versione vetro: piu' sfocata, piu' satura e leggermente piu' luminosa (vibrancy) */
    private static Bitmap glass(Bitmap src, int w, int h) {
        Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, w / BLUR_SCALE), Math.max(1, h / BLUR_SCALE), true);
        Bitmap b = Blur.stack(small, 12);
        small.recycle();
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.6f);
        ColorMatrix bright = new ColorMatrix(new float[]{
                1.12f, 0, 0, 0, 20,
                0, 1.12f, 0, 0, 20,
                0, 0, 1.12f, 0, 26,
                0, 0, 0, 1, 0});
        cm.postConcat(bright);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(b, 0, 0, p);
        b.recycle();
        return out;
    }

    private static void field(Canvas c, float x, float y, float r, int rgb, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int col = (alpha << 24) | (rgb & 0xFFFFFF);
        p.setShader(new RadialGradient(x, y, r, new int[]{col, (alpha / 2 << 24) | (rgb & 0xFFFFFF), rgb & 0xFFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    private static void ribbon(Canvas c, int w, int h, float x0, float y0, float x1, float y1, float x2, float y2,
                               int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        Path path = new Path();
        path.moveTo(x0 * w, y0 * h);
        path.quadTo(x1 * w, y1 * h, x2 * w, y2 * h);
        c.drawPath(path, p);
    }

    private static void save(Bitmap b, File f) {
        try (FileOutputStream os = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (Exception ignored) { }
    }
}
