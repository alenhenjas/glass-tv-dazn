/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ordine di utilizzo delle app + ultima anteprima di ciascuna (memoria + cache su disco) */
final class Recents {
    private static final LinkedHashMap<String, Long> order = new LinkedHashMap<>();
    private static final Map<String, Bitmap> shots = new LinkedHashMap<>();

    private Recents() {}

    static synchronized void touch(String pkg) {
        order.remove(pkg);
        order.put(pkg, System.currentTimeMillis());
    }

    static synchronized void shot(Context c, String pkg, Bitmap b) {
        if (b == null || isBlack(b)) return;
        shots.put(pkg, b);
        try (FileOutputStream os = new FileOutputStream(new File(c.getCacheDir(), "snap_" + pkg + ".jpg"))) {
            b.compress(Bitmap.CompressFormat.JPEG, 85, os);
        } catch (Exception ignored) { }
    }

    static synchronized Bitmap shotOf(Context c, String pkg) {
        Bitmap b = shots.get(pkg);
        if (b == null) {
            File f = new File(c.getCacheDir(), "snap_" + pkg + ".jpg");
            if (f.exists()) { b = BitmapFactory.decodeFile(f.getAbsolutePath()); if (b != null) shots.put(pkg, b); }
        }
        return b;
    }

    static synchronized void forget(String pkg) { order.remove(pkg); }

    /** pacchetti dal piu' recente */
    static synchronized List<String> ordered() {
        List<String> l = new ArrayList<>(order.keySet());
        java.util.Collections.reverse(l);
        return l;
    }

    /** un'anteprima quasi tutta nera (contenuto protetto drm) non e' utile: si mostra l'icona */
    private static boolean isBlack(Bitmap b) {
        int n = 0, dark = 0;
        for (int y = 8; y < b.getHeight(); y += 24) for (int x = 8; x < b.getWidth(); x += 24) {
            int p = b.getPixel(x, y);
            int l = ((p >> 16) & 0xff) + ((p >> 8) & 0xff) + (p & 0xff);
            if (l < 30) dark++;
            n++;
        }
        return n > 0 && dark > n * 0.97f;
    }
}
