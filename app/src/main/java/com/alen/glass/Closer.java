/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;

/**
 * chiusura app: dalla home le app sono in background, quindi killBackgroundProcesses le termina
 * (permesso normale, niente root). i servizi di sistema essenziali non vengono toccati.
 */
final class Closer {
    private static final String[] KEEP = {
            "com.alen.glass", "com.google.android.gms", "com.android.systemui", "com.google.android.katniss",
            "com.google.android.inputmethod.latin", "com.google.android.tv.remote.service", "com.sagemcom.",
            "com.google.android.apps.mediashell", "android", "com.android.providers.", "com.android.bluetooth",
            "com.google.android.apps.tv.", "com.xiaomi.", "com.mitv.", "com.mi.", "xiaomi.", "com.droidlogic",
            "com.google.android.tungsten.setupwraith", "com.android.tv.settings",
    };

    private Closer() {}

    static boolean closable(String pkg) {
        if (pkg == null) return false;
        for (String k : KEEP) if (k.endsWith(".") ? pkg.startsWith(k) : pkg.equals(k)) return false;
        return true;
    }

    static void close(Context c, String pkg) {
        if (!closable(pkg)) return;
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(pkg);
        Running.closed(c, pkg);
        Recents.forget(pkg);
        java.util.Set<String> s = cache;
        if (s != null) s.remove(pkg);
    }

    /** l'app ha processi vivi? con il permesso DUMP lo chiede al sistema, altrimenti usa il registro */
    static boolean isRunning(Context c, String pkg, java.util.Set<String> running) {
        if (running != null) return running.contains(pkg);
        return Running.isOpen(c, pkg);
    }

    // ultimo elenco dei processi vivi: dumpsys costa ~0,3-0,5 s, quindi si tiene in cache e si aggiorna in background
    private static volatile java.util.Set<String> cache;
    private static volatile long cacheAt;

    /** insieme dei pacchetti vivi, oppure null se il sistema non lo consente */
    static java.util.Set<String> runningSet() {
        java.util.Set<String> s = Sys.running();
        s = s.isEmpty() ? null : s;
        if (s == null) cache = null;
        else { java.util.Set<String> c = java.util.concurrent.ConcurrentHashMap.newKeySet(); c.addAll(s); cache = c; }
        cacheAt = android.os.SystemClock.elapsedRealtime();
        return s;
    }

    /** come runningSet ma istantaneo se l'elenco e' recente (maxAgeMs); altrimenti lo ricalcola */
    static java.util.Set<String> runningSetFast(long maxAgeMs) {
        java.util.Set<String> s = cache;
        if (s != null && android.os.SystemClock.elapsedRealtime() - cacheAt < maxAgeMs) return s;
        return runningSet();
    }

    static boolean hasFresh(long maxAgeMs) { return cache != null && android.os.SystemClock.elapsedRealtime() - cacheAt < maxAgeMs; }

    /** aggiorna la cache senza bloccare la ui */
    static void prewarm() { new Thread(Closer::runningSet, "running").start(); }

    static long availMem(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }

    /** chiude tutte le app avviabili; restituisce quante ne ha chiuse */
    static int closeAll(Context c, List<Apps.Entry> all) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        int n = 0;
        for (Apps.Entry e : all) {
            if (!closable(e.pkg)) continue;
            am.killBackgroundProcesses(e.pkg);
            Recents.forget(e.pkg);
            n++;
        }
        Running.clear(c);
        java.util.Set<String> s = cache;
        if (s != null) { s.clear(); s.addAll(java.util.Arrays.asList(KEEP)); }
        return n;
    }
}
