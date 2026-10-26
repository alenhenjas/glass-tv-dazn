/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * registro delle app aperte: il servizio di accessibilita' annota ogni app portata in primo piano,
 * la chiusura dal launcher la toglie. si azzera a ogni riavvio del box.
 */
final class Running {
    private static final String P = "running";

    private Running() {}

    private static SharedPreferences prefs(Context c) {
        SharedPreferences sp = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(sp.getLong("__boot", 0) - boot) > 60_000) sp.edit().clear().putLong("__boot", boot).apply();
        return sp;
    }

    static void opened(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName()) || !Closer.closable(pkg)) return;
        prefs(c).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    static void closed(Context c, String pkg) { prefs(c).edit().remove(pkg).apply(); }

    static void clear(Context c) {
        SharedPreferences sp = prefs(c);
        long boot = sp.getLong("__boot", 0);
        sp.edit().clear().putLong("__boot", boot).apply();
    }

    static boolean isOpen(Context c, String pkg) { return pkg != null && prefs(c).contains(pkg); }

    static long since(Context c, String pkg) { return prefs(c).getLong(pkg, 0); }

    /** pacchetti aperti, dal piu' recente */
    static List<String> list(Context c) {
        List<Map.Entry<String, ?>> all = new ArrayList<>(prefs(c).getAll().entrySet());
        List<String> out = new ArrayList<>();
        Collections.sort(all, (a, b) -> Long.compare((Long) b.getValue(), (Long) a.getValue()));
        for (Map.Entry<String, ?> e : all) if (!e.getKey().startsWith("__")) out.add(e.getKey());
        return out;
    }
}
