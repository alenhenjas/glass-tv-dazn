/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** glifi vettoriali essenziali (stile sf symbols, disegnati a mano) usati da controlli e popup */
final class Glyphs {
    enum Kind { WIFI, ETHERNET, VIDEO, AUDIO, BLUETOOTH, CLOSE_ALL, LOCK, CHECK, CHEVRON, INFO, STORAGE, APPS, CLOCK, GEAR, RECENTS, SPEAKER_MUTE, BACK, POWER, RESTART }

    private Glyphs() {}

    /** disegna il glifo centrato in (cx,cy); s = mezza dimensione indicativa */
    static void draw(Canvas c, Kind kind, float cx, float cy, float s, Paint g, float level) {
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeJoin(Paint.Join.ROUND);
        g.setStrokeWidth(s * 0.28f);
        g.setStyle(Paint.Style.STROKE);
        Path p = new Path();
        switch (kind) {
            case WIFI: {
                int bars = level < 0 ? 3 : Math.max(1, Math.round(level * 3));
                int alpha = g.getAlpha();
                for (int i = 1; i <= 3; i++) {
                    float rr = s * 0.52f * i;
                    g.setAlpha(i <= bars ? alpha : alpha / 4);
                    c.drawArc(new RectF(cx - rr, cy + s * 0.75f - rr, cx + rr, cy + s * 0.75f + rr), 225, 90, false, g);
                }
                g.setAlpha(alpha);
                g.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy + s * 0.72f, s * 0.16f, g);
                break;
            }
            case ETHERNET: {
                g.setStyle(Paint.Style.FILL);
                float bw = s * 0.42f;
                for (int i = 0; i < 3; i++) {
                    float bh = s * (0.7f + 0.5f * i), x = cx - s * 0.95f + i * bw * 1.6f;
                    c.drawRoundRect(new RectF(x, cy + s * 0.9f - bh, x + bw, cy + s * 0.9f), bw / 2, bw / 2, g);
                }
                break;
            }
            case VIDEO: {
                c.drawRoundRect(new RectF(cx - s * 1.15f, cy - s * 0.8f, cx + s * 1.15f, cy + s * 0.55f), s * 0.22f, s * 0.22f, g);
                p.moveTo(cx - s * 0.5f, cy + s * 1.0f); p.lineTo(cx + s * 0.5f, cy + s * 1.0f);
                c.drawPath(p, g);
                g.setStyle(Paint.Style.FILL);
                Path play = new Path();
                play.moveTo(cx - s * 0.25f, cy - s * 0.45f); play.lineTo(cx + s * 0.4f, cy - s * 0.12f); play.lineTo(cx - s * 0.25f, cy + s * 0.2f); play.close();
                c.drawPath(play, g);
                break;
            }
            case AUDIO:
            case SPEAKER_MUTE: {
                g.setStyle(Paint.Style.FILL);
                p.moveTo(cx - s * 1.0f, cy - s * 0.35f); p.lineTo(cx - s * 0.55f, cy - s * 0.35f); p.lineTo(cx - s * 0.05f, cy - s * 0.85f);
                p.lineTo(cx - s * 0.05f, cy + s * 0.85f); p.lineTo(cx - s * 0.55f, cy + s * 0.35f); p.lineTo(cx - s * 1.0f, cy + s * 0.35f); p.close();
                c.drawPath(p, g);
                g.setStyle(Paint.Style.STROKE);
                if (kind == Kind.SPEAKER_MUTE) {
                    c.drawLine(cx + s * 0.3f, cy - s * 0.35f, cx + s * 1.0f, cy + s * 0.35f, g);
                    c.drawLine(cx + s * 1.0f, cy - s * 0.35f, cx + s * 0.3f, cy + s * 0.35f, g);
                } else {
                    int waves = level < 0 ? 2 : (level <= 0.01f ? 0 : level < 0.5f ? 1 : 2);
                    if (waves >= 1) c.drawArc(new RectF(cx - s * 0.45f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f), -50, 100, false, g);
                    if (waves >= 2) c.drawArc(new RectF(cx - s * 0.75f, cy - s * 0.95f, cx + s * 1.05f, cy + s * 0.95f), -50, 100, false, g);
                }
                break;
            }
            case BLUETOOTH:
                p.moveTo(cx - s * 0.6f, cy - s * 0.5f); p.lineTo(cx + s * 0.55f, cy + s * 0.5f); p.lineTo(cx, cy + s * 1.0f);
                p.lineTo(cx, cy - s * 1.0f); p.lineTo(cx + s * 0.55f, cy - s * 0.5f); p.lineTo(cx - s * 0.6f, cy + s * 0.5f);
                c.drawPath(p, g);
                break;
            case POWER:
                // simbolo di accensione: arco aperto in alto + barretta verticale
                g.setStrokeWidth(s * 0.26f);
                c.drawArc(new RectF(cx - s * 0.85f, cy - s * 0.75f, cx + s * 0.85f, cy + s * 0.95f), -60, 300, false, g);
                c.drawLine(cx, cy - s * 1.0f, cx, cy + s * 0.05f, g);
                break;
            case RESTART: {
                // freccia circolare
                g.setStrokeWidth(s * 0.26f);
                c.drawArc(new RectF(cx - s * 0.85f, cy - s * 0.85f, cx + s * 0.85f, cy + s * 0.85f), -70, 290, false, g);
                double a = Math.toRadians(-70);
                float ex = cx + (float) Math.cos(a) * s * 0.85f, ey = cy + (float) Math.sin(a) * s * 0.85f;
                g.setStyle(Paint.Style.FILL);
                p.moveTo(ex + s * 0.42f, ey); p.lineTo(ex - s * 0.18f, ey - s * 0.42f); p.lineTo(ex - s * 0.12f, ey + s * 0.38f); p.close();
                c.drawPath(p, g);
                g.setStyle(Paint.Style.STROKE);
                break;
            }
            case CLOSE_ALL:
                g.setStrokeWidth(s * 0.32f);
                c.drawLine(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f, g);
                c.drawLine(cx + s * 0.7f, cy - s * 0.7f, cx - s * 0.7f, cy + s * 0.7f, g);
                break;
            case LOCK:
                g.setStrokeWidth(s * 0.22f);
                c.drawArc(new RectF(cx - s * 0.45f, cy - s * 0.95f, cx + s * 0.45f, cy - s * 0.05f), 180, 180, false, g);
                c.drawLine(cx - s * 0.45f, cy - s * 0.5f, cx - s * 0.45f, cy - s * 0.1f, g);
                c.drawLine(cx + s * 0.45f, cy - s * 0.5f, cx + s * 0.45f, cy - s * 0.1f, g);
                g.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(cx - s * 0.7f, cy - s * 0.15f, cx + s * 0.7f, cy + s * 0.85f), s * 0.18f, s * 0.18f, g);
                break;
            case CHECK:
                g.setStrokeWidth(s * 0.3f);
                p.moveTo(cx - s * 0.75f, cy); p.lineTo(cx - s * 0.2f, cy + s * 0.55f); p.lineTo(cx + s * 0.8f, cy - s * 0.6f);
                c.drawPath(p, g);
                break;
            case CHEVRON:
                g.setStrokeWidth(s * 0.24f);
                p.moveTo(cx - s * 0.3f, cy - s * 0.65f); p.lineTo(cx + s * 0.35f, cy); p.lineTo(cx - s * 0.3f, cy + s * 0.65f);
                c.drawPath(p, g);
                break;
            case BACK:
                g.setStrokeWidth(s * 0.26f);
                p.moveTo(cx + s * 0.3f, cy - s * 0.7f); p.lineTo(cx - s * 0.4f, cy); p.lineTo(cx + s * 0.3f, cy + s * 0.7f);
                c.drawPath(p, g);
                break;
            case INFO:
                g.setStrokeWidth(s * 0.2f);
                c.drawCircle(cx, cy, s * 0.95f, g);
                g.setStrokeWidth(s * 0.26f);
                c.drawLine(cx, cy - s * 0.05f, cx, cy + s * 0.5f, g);
                g.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - s * 0.42f, s * 0.15f, g);
                break;
            case STORAGE:
                g.setStrokeWidth(s * 0.2f);
                for (int i = 0; i < 3; i++) {
                    float y = cy - s * 0.8f + i * s * 0.6f;
                    c.drawRoundRect(new RectF(cx - s * 0.9f, y, cx + s * 0.9f, y + s * 0.42f), s * 0.12f, s * 0.12f, g);
                }
                break;
            case APPS: {
                g.setStyle(Paint.Style.FILL);
                float cell = s * 0.48f, gap = s * 0.22f, st = -(cell * 1.5f + gap);
                for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) {
                    float x = cx + st + k * (cell + gap), y = cy + st + r * (cell + gap);
                    c.drawRoundRect(new RectF(x, y, x + cell, y + cell), cell * 0.3f, cell * 0.3f, g);
                }
                break;
            }
            case CLOCK:
                g.setStrokeWidth(s * 0.2f);
                c.drawCircle(cx, cy, s * 0.95f, g);
                g.setStrokeWidth(s * 0.24f);
                c.drawLine(cx, cy, cx, cy - s * 0.55f, g);
                c.drawLine(cx, cy, cx + s * 0.4f, cy + s * 0.2f, g);
                break;
            case GEAR: {
                g.setStrokeWidth(s * 0.2f);
                int teeth = 8;
                for (int i = 0; i < teeth; i++) {
                    double a = Math.PI * 2 * i / teeth;
                    c.drawLine(cx + (float) Math.cos(a) * s * 0.6f, cy + (float) Math.sin(a) * s * 0.6f,
                            cx + (float) Math.cos(a) * s * 0.95f, cy + (float) Math.sin(a) * s * 0.95f, g);
                }
                c.drawCircle(cx, cy, s * 0.6f, g);
                c.drawCircle(cx, cy, s * 0.22f, g);
                break;
            }
            case RECENTS:
                g.setStrokeWidth(s * 0.2f);
                c.drawRoundRect(new RectF(cx - s * 0.55f, cy - s * 0.95f, cx + s * 0.95f, cy + s * 0.35f), s * 0.22f, s * 0.22f, g);
                g.setStyle(Paint.Style.FILL);
                c.drawRoundRect(new RectF(cx - s * 0.95f, cy - s * 0.5f, cx + s * 0.55f, cy + s * 0.85f), s * 0.22f, s * 0.22f, g);
                break;
        }
    }
}
