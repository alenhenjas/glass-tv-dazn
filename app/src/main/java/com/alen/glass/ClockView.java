/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * orologio della home disegnato a mano: colore, stile (pieno, liquid glass, vetro, contorno),
 * opacita', carattere e dimensione personalizzabili. ok o pressione lunga aprono le opzioni.
 */
final class ClockView extends View {
    // ---------------- opzioni ----------------
    static final String[] COLOR_NAMES = {"Bianco", "Dallo sfondo", "Azzurro", "Blu", "Viola", "Rosa", "Rosso", "Arancione", "Giallo", "Verde", "Menta", "Grigio"};
    static final int[] COLORS = {0xFFFFFFFF, 0, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF6482, 0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF66D4CF, 0xFFAEAEB2};
    static final String[] STYLE_NAMES = {"Pieno", "Liquid glass", "Vetro trasparente", "Contorno"};
    static final int[] OPACITY = {100, 90, 80, 70, 60, 50, 40, 30};
    static final String[] FONT_NAMES = {"Predefinito", "Grassetto", "Medio", "Leggero", "Sottile"};
    static final String[] SIZE_NAMES = {"Piccolo", "Medio", "Grande", "Molto grande"};
    private static final float[] SIZES = {72, 84, 96, 116};

    int color = 0, style = 0, opacity = 0, font = 0, size = 2;
    boolean showDate = true;

    // ---------------- disegno ----------------
    private final TextPaint timeP = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint dateP = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint rimP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tintP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderM = new Matrix();
    private final int[] loc = new int[2], rootLoc = new int[2];
    private final Glass glass;
    private final float padH, padV;
    private boolean active, longFired;
    private String time = "", date = "";
    private final SimpleDateFormat tf = new SimpleDateFormat("H:mm", Locale.ITALIAN);
    private final SimpleDateFormat df = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN);
    private int autoColor = 0xFFFFFFFF;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateText(); }
    };

    ClockView(Context c) {
        super(c);
        padH = Ui.dp(c, 20);
        padV = Ui.dp(c, 12);
        glass = new Glass(this, 0.22f, 12);
        glass.tint(0.10f, 0.16f);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setOnFocusChangeListener((v, has) -> {
            active = has;
            animate().cancel();
            float s = has ? 1.03f : 1f;
            setPivotX(padH);
            setPivotY(getHeight() / 2f);
            animate().scaleX(s).scaleY(s).setDuration(has ? 140 : 100).setInterpolator(Ui.EASE).start();
            invalidate();
        });
        load();
        updateText();
    }

    // ---------------- preferenze ----------------
    private SharedPreferences sp() { return getContext().getSharedPreferences("clock", Context.MODE_PRIVATE); }

    void load() {
        SharedPreferences p = sp();
        color = clamp(p.getInt("color", 0), COLORS.length);
        style = clamp(p.getInt("style", 0), STYLE_NAMES.length);
        opacity = clamp(p.getInt("opacity", 0), OPACITY.length);
        font = clamp(p.getInt("font", 0), FONT_NAMES.length);
        size = clamp(p.getInt("size", 2), SIZES.length);
        showDate = p.getBoolean("date", true);
        apply();
    }

    void save() {
        sp().edit().putInt("color", color).putInt("style", style).putInt("opacity", opacity)
                .putInt("font", font).putInt("size", size).putBoolean("date", showDate).apply();
        apply();
    }

    void reset() { color = 0; style = 0; opacity = 0; font = 0; size = 2; showDate = true; save(); }

    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    int swatch(int idx) { return COLORS[idx] == 0 ? autoColor : COLORS[idx]; }

    /** applica le opzioni ai pennelli e ricalcola le dimensioni */
    void apply() {
        Context c = getContext();
        Typeface t;
        switch (font) {
            case 1: t = Ui.displayBold(); break;
            case 2: t = Ui.textMedium(); break;
            case 3: t = Typeface.create("sans-serif-light", Typeface.NORMAL); break;
            case 4: t = Typeface.create("sans-serif-thin", Typeface.NORMAL); break;
            default: t = Ui.display();
        }
        timeP.setTypeface(t);
        timeP.setTextSize(Ui.dp(c, SIZES[size]));
        timeP.setLetterSpacing(font >= 3 ? -0.01f : -0.035f);
        dateP.setTypeface(Ui.textSemibold());
        dateP.setTextSize(Ui.dp(c, 19));
        dateP.setLetterSpacing(0.01f);
        if (Wallpaper.full != null) autoColor = wallpaperTone(Wallpaper.blurred != null ? Wallpaper.blurred : Wallpaper.full);
        requestLayout();
        relayoutGlass();
        invalidate();
    }

    /** tono chiaro e saturo preso dallo sfondo (media dei colori, schiarita) */
    private static int wallpaperTone(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 8, 8, true);
        long r = 0, g = 0, bl = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int p = s.getPixel(x, y);
            r += Color.red(p); g += Color.green(p); bl += Color.blue(p);
        }
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / 64), (int) (g / 64), (int) (bl / 64), hsv);
        hsv[1] = Math.min(0.45f, Math.max(0.25f, hsv[1]));
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    private void updateText() {
        Date now = new Date();
        time = tf.format(now);
        date = df.format(now);
        relayoutGlass();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(tick, f);
        updateText();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try { getContext().unregisterReceiver(tick); } catch (Exception ignored) { }
    }

    private float dateLine() {
        if (!showDate) return 0;
        Paint.FontMetrics fm = dateP.getFontMetrics();
        return fm.bottom - fm.top + Ui.dp(getContext(), 2);
    }

    private float timeLine() {
        Paint.FontMetrics fm = timeP.getFontMetrics();
        return fm.bottom - fm.top;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int h = (int) Math.ceil(padV * 2 + dateLine() + timeLine());
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayoutGlass();
    }

    private void relayoutGlass() {
        if (getWidth() == 0 || getHeight() == 0) return;
        float tw = Math.max(timeP.measureText(time), showDate ? dateP.measureText(date) : 0);
        float inset = Ui.dp(getContext(), 4);
        glass.layout(inset, inset, Math.min(getWidth() - inset, padH * 2 + tw - inset * 0), getHeight() - inset);
    }

    // ---------------- tasti: ok e pressione lunga aprono le opzioni (una sola volta) ----------------
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        boolean confirm = kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (confirm) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) longFired = false;
                if (!longFired && ((e.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 || e.getRepeatCount() >= 2)) {
                    longFired = true;
                    performLongClick();
                }
                return true;
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!longFired) performClick();
                longFired = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------- disegno ----------------
    @Override protected void onDraw(Canvas c) {
        if (active) glass.draw(c, true, -1f);
        int base = COLORS[color] == 0 ? autoColor : COLORS[color];
        int a = (int) (255 * OPACITY[opacity] / 100f);
        float x = padH;
        float y = padV;
        if (showDate) {
            Paint.FontMetrics fm = dateP.getFontMetrics();
            drawStyled(c, date, x, y - fm.top, dateP, base, (int) (a * 0.9f), Ui.dp(getContext(), 1.1f));
            y += dateLine();
        }
        Paint.FontMetrics fm = timeP.getFontMetrics();
        drawStyled(c, time, x, y - fm.top, timeP, base, a, Ui.dp(getContext(), font >= 3 ? 1.4f : 2f));
    }

    private void drawStyled(Canvas c, String s, float x, float baseline, TextPaint p, int col, int alpha, float stroke) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.clearShadowLayer();
        // contorno unico delle cifre: senza le linee interne dove i tratti del carattere si sovrappongono (es. il 4)
        Path gp = style == 0 ? null : glyphs(s, x, baseline, p);
        switch (style) {
            case 1: { // liquid glass: sfondo sfocato dentro le cifre, tinta, bordo luminoso e ombra morbida
                Bitmap bg = Wallpaper.blurred;
                // ombra
                p.setColor(Color.argb((int) (alpha * 0.35f), 0, 0, 0));
                p.setShadowLayer(Ui.dp(getContext(), 10), 0, Ui.dp(getContext(), 3), Color.argb((int) (alpha * 0.45f), 0, 0, 10));
                c.drawPath(gp, p);
                p.clearShadowLayer();
                if (bg != null) {
                    getLocationInWindow(loc);
                    getRootView().getLocationInWindow(rootLoc);
                    float k = Wallpaper.BLUR_SCALE / getScaleX();
                    shaderM.setScale(k, k);
                    shaderM.postTranslate(-(loc[0] - rootLoc[0]) / getScaleX(), -(loc[1] - rootLoc[1]) / getScaleX());
                    BitmapShader bs = new BitmapShader(bg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    bs.setLocalMatrix(shaderM);
                    p.setShader(bs);
                    p.setColor(Color.argb(alpha, 255, 255, 255));
                    c.drawPath(gp, p);
                    p.setShader(null);
                }
                tintP.set(p);
                tintP.setColor(withAlpha(col, (int) (alpha * 0.42f)));
                c.drawPath(gp, tintP);
                rim(c, gp, baseline, p, alpha, stroke, 245, 70);
                break;
            }
            case 2: { // vetro trasparente: riempimento leggero + bordo sottile
                p.setColor(withAlpha(col, (int) (alpha * 0.22f)));
                c.drawPath(gp, p);
                rim(c, gp, baseline, p, alpha, stroke * 0.8f, 230, 120);
                break;
            }
            case 3: { // contorno
                rimP.set(p);
                rimP.setStyle(Paint.Style.STROKE);
                rimP.setStrokeWidth(stroke * 1.1f);
                rimP.setColor(withAlpha(col, alpha));
                c.drawPath(gp, rimP);
                break;
            }
            default: // pieno
                p.setColor(withAlpha(col, alpha));
                c.drawText(s, x, baseline, p);
        }
    }

    /** bordo speculare: luminoso in alto, piu' tenue in basso */
    private void rim(Canvas c, Path gp, float baseline, TextPaint p, int alpha, float stroke, int topA, int botA) {
        rimP.set(p);
        rimP.setShader(null);
        rimP.setStyle(Paint.Style.STROKE);
        rimP.setStrokeWidth(stroke);
        Paint.FontMetrics fm = p.getFontMetrics();
        rimP.setShader(new LinearGradient(0, baseline + fm.ascent, 0, baseline,
                Color.argb(alpha * topA / 255, 255, 255, 255), Color.argb(alpha * botA / 255, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawPath(gp, rimP);
    }

    // cache dei contorni: si ricalcolano solo quando cambia il testo o lo stile
    private final java.util.HashMap<String, Path> glyphCache = new java.util.HashMap<>();

    private Path glyphs(String s, float x, float baseline, TextPaint p) {
        String key = s + "|" + x + "|" + baseline + "|" + p.getTextSize() + "|" + p.getTypeface() + "|" + p.getLetterSpacing();
        Path cached = glyphCache.get(key);
        if (cached != null) return cached;
        Path raw = new Path();
        p.getTextPath(s, 0, s.length(), x, baseline, raw);
        Path merged = new Path();
        if (!merged.op(raw, new Path(), Path.Op.UNION)) merged = raw; // unione = contorni sovrapposti fusi
        if (glyphCache.size() > 16) glyphCache.clear();
        glyphCache.put(key, merged);
        return merged;
    }

    private static int withAlpha(int col, int a) { return (col & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24); }
}
