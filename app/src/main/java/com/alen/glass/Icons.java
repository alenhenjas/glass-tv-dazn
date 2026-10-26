/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * trasforma qualsiasi icona android in un'icona stile apple: squircle a tutto campo.
 * - icone adattive: si usano sfondo + primo piano a pieno formato
 * - icone gia' piene (quadrate/opache): ritagliate a squircle
 * - loghi su trasparente: appoggiati su una "piastra" chiara o scura, come le icone ios
 */
final class Icons {
    static final int SIZE = 256;
    private static final Map<String, Bitmap> cache = new HashMap<>();

    private Icons() {}

    static synchronized Bitmap of(String key, Drawable d) {
        Bitmap b = cache.get(key);
        if (b == null) { b = build(d); cache.put(key, b); }
        return b;
    }

    static synchronized void clear() { cache.clear(); }

    private static Bitmap build(Drawable d) {
        Bitmap art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int o = SIZE / 4; // area visibile 72/108 del livello pieno
            if (a.getBackground() != null) { a.getBackground().setBounds(-o, -o, SIZE + o, SIZE + o); a.getBackground().draw(c); }
            if (a.getForeground() != null) { a.getForeground().setBounds(-o, -o, SIZE + o, SIZE + o); a.getForeground().draw(c); }
            return mask(art);
        }
        Bitmap raw = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(new Canvas(raw));
        if (isFullBleed(raw)) {
            // leggero zoom per eliminare eventuali angoli arrotondati gia' presenti nell'icona
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.save();
            c.scale(1.06f, 1.06f, SIZE / 2f, SIZE / 2f);
            c.drawBitmap(raw, 0, 0, p);
            c.restore();
        } else {
            boolean lightArt = averageLuma(raw) > 0.78f;
            Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
            plate.setShader(lightArt
                    ? new LinearGradient(0, 0, 0, SIZE, Color.rgb(58, 58, 64), Color.rgb(22, 22, 26), Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, SIZE, Color.rgb(255, 255, 255), Color.rgb(236, 236, 242), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SIZE, SIZE, plate);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            float s = 0.66f;
            c.save();
            c.scale(s, s, SIZE / 2f, SIZE / 2f);
            c.drawBitmap(raw, 0, 0, p);
            c.restore();
        }
        raw.recycle();
        return mask(art);
    }

    /** ritaglio squircle con bordi antialias */
    static Bitmap mask(Bitmap art) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path sq = Ui.squircle(new RectF(0, 0, SIZE, SIZE), SIZE * 0.225f);
        c.drawPath(sq, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        p.setFilterBitmap(true);
        c.drawBitmap(art, 0, 0, p);
        // sottile riflesso in alto, come la luce sulle icone di visionos/ios 26
        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        gloss.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        gloss.setShader(new LinearGradient(0, 0, 0, SIZE * 0.5f, Color.argb(40, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, gloss);
        art.recycle();
        return out;
    }

    private static boolean isFullBleed(Bitmap b) {
        int in = (int) (SIZE * 0.06f), mid = SIZE / 2, e = SIZE - 1 - in;
        int[][] pts = {{in, in}, {e, in}, {in, e}, {e, e}, {mid, 1}, {mid, SIZE - 2}, {1, mid}, {SIZE - 2, mid}};
        int opaque = 0;
        for (int[] pt : pts) if (Color.alpha(b.getPixel(pt[0], pt[1])) > 230) opaque++;
        return opaque >= 7;
    }

    private static float averageLuma(Bitmap b) {
        double sum = 0; int n = 0;
        for (int y = 0; y < SIZE; y += 8) for (int x = 0; x < SIZE; x += 8) {
            int px = b.getPixel(x, y);
            if (Color.alpha(px) < 128) continue;
            sum += (0.2126 * Color.red(px) + 0.7152 * Color.green(px) + 0.0722 * Color.blue(px)) / 255.0;
            n++;
        }
        return n == 0 ? 0f : (float) (sum / n);
    }

    // ---------- icone di sistema disegnate a mano (glifi generici, nessun asset apple) ----------

    static Bitmap settings() {
        Bitmap art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, SIZE, Color.rgb(160, 160, 168), Color.rgb(98, 98, 106), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, bg);
        float cx = SIZE / 2f, cy = SIZE / 2f;
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(cx, cy - SIZE * 0.1f, SIZE * 0.45f, Color.rgb(250, 250, 252), Color.rgb(200, 200, 208), Shader.TileMode.CLAMP));
        // ruota dentata: corona + denti
        int teeth = 12;
        float rOut = SIZE * 0.37f, rIn = SIZE * 0.30f;
        Path gear = new Path();
        for (int i = 0; i < teeth * 2; i++) {
            double a0 = Math.PI * 2 * i / (teeth * 2) - Math.PI / 2;
            double a1 = Math.PI * 2 * (i + 1) / (teeth * 2) - Math.PI / 2;
            float r = (i % 2 == 0) ? rOut : rIn;
            float x0 = cx + (float) Math.cos(a0 + 0.06) * r, y0 = cy + (float) Math.sin(a0 + 0.06) * r;
            float x1 = cx + (float) Math.cos(a1 - 0.06) * r, y1 = cy + (float) Math.sin(a1 - 0.06) * r;
            if (i == 0) gear.moveTo(x0, y0); else gear.lineTo(x0, y0);
            gear.lineTo(x1, y1);
        }
        gear.close();
        gear.addCircle(cx, cy, SIZE * 0.13f, Path.Direction.CCW);
        gear.setFillType(Path.FillType.EVEN_ODD);
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(Color.argb(70, 0, 0, 0));
        c.save(); c.translate(0, SIZE * 0.012f); c.drawPath(gear, sh); c.restore();
        c.drawPath(gear, g);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(SIZE * 0.035f);
        ring.setColor(Color.rgb(120, 120, 128));
        c.drawCircle(cx, cy, SIZE * 0.2f, ring);
        return mask(art);
    }

    static Bitmap allApps() {
        Bitmap art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, SIZE, SIZE, Color.rgb(64, 120, 255), Color.rgb(150, 80, 255), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, bg);
        Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
        d.setColor(Color.argb(240, 255, 255, 255));
        float cell = SIZE * 0.17f, gap = SIZE * 0.07f;
        float start = (SIZE - (cell * 3 + gap * 2)) / 2f;
        for (int r = 0; r < 3; r++) for (int col = 0; col < 3; col++) {
            float x = start + col * (cell + gap), y = start + r * (cell + gap);
            c.drawPath(Ui.squircle(new RectF(x, y, x + cell, y + cell), cell * 0.3f), d);
        }
        return mask(art);
    }

    /** browser: globo bianco su gradiente blu */
    static Bitmap browser() {
        Bitmap art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, SIZE, Color.rgb(70, 170, 255), Color.rgb(16, 92, 230), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, bg);
        float cx = SIZE / 2f, cy = SIZE / 2f, r = SIZE * 0.32f;
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(SIZE * 0.032f);
        g.setColor(Color.WHITE);
        c.drawCircle(cx, cy, r, g);
        c.drawOval(new RectF(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r), g);
        c.drawLine(cx, cy - r, cx, cy + r, g);
        c.drawLine(cx - r, cy, cx + r, cy, g);
        c.save();
        c.clipPath(circlePath(cx, cy, r));
        float dy = r * 0.52f;
        c.drawLine(cx - r, cy - dy, cx + r, cy - dy, g);
        c.drawLine(cx - r, cy + dy, cx + r, cy + dy, g);
        c.restore();
        return mask(art);
    }

    /** app aperte: due schede sovrapposte bianche su grigio grafite */
    static Bitmap recents() {
        Bitmap art = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, SIZE, Color.rgb(84, 84, 96), Color.rgb(40, 40, 48), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, bg);
        Paint w = new Paint(Paint.ANTI_ALIAS_FLAG);
        w.setColor(Color.argb(120, 255, 255, 255));
        c.drawPath(Ui.squircle(new RectF(SIZE * 0.34f, SIZE * 0.22f, SIZE * 0.80f, SIZE * 0.58f), SIZE * 0.07f), w);
        w.setColor(Color.WHITE);
        c.drawPath(Ui.squircle(new RectF(SIZE * 0.20f, SIZE * 0.40f, SIZE * 0.66f, SIZE * 0.76f), SIZE * 0.07f), w);
        return mask(art);
    }

    private static Path circlePath(float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }

    static Bitmap fromDrawable(Drawable d) {
        if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(new Canvas(b));
        return b;
    }

}
