/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** elenco (ordinato) delle app nella schermata home, salvato sul box */
final class Home {
    static final String BROWSER = "browser";
    // le app non installate vengono saltate: lo stesso elenco va bene su entrambi i box
    private static final String[] DEFAULT = {"com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.cbs.ca", "pe.hf.mavex", "com.mtssi.supernovabih", BROWSER};
    private static int version;

    private Home() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("home", Context.MODE_PRIVATE); }

    static synchronized List<String> list(Context c) {
        String s = sp(c).getString("apps", null);
        if (s == null) return new ArrayList<>(Arrays.asList(DEFAULT));
        List<String> l = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) l.add(p);
        return l;
    }

    static synchronized boolean contains(Context c, String pkg) { return list(c).contains(pkg); }

    static synchronized void add(Context c, String pkg) {
        List<String> l = list(c);
        if (!l.contains(pkg)) { l.add(pkg); save(c, l); }
    }

    static synchronized void remove(Context c, String pkg) {
        List<String> l = list(c);
        if (l.remove(pkg)) save(c, l);
    }

    private static void save(Context c, List<String> l) {
        sp(c).edit().putString("apps", String.join(",", l)).apply();
        version++;
    }

    /** cambia a ogni modifica: la home si ricarica quando torna visibile */
    static int version() { return version; }
}
