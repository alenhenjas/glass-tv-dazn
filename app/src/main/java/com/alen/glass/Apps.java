/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** catalogo app: preferite (mostrate solo se installate), dock di sistema, elenco completo */
final class Apps {
    static final class Entry {
        String label, pkg;
        Intent launch;
        Drawable raw;
        Bitmap icon;
    }

    /** preferite in ordine: etichetta, pacchetti candidati, parola chiave sul nome */
    private static final String[][] FAVORITES = {
            {"YouTube", "com.google.android.youtube.tv", "youtube"},
            {"Mavex", "pe.hf.mavex", "mavex"},
            {"MOVE", "com.mtssi.supernovabih", "move"},
            {"Netflix", "com.netflix.ninja", "netflix"},
            {"Prime Video", "com.amazon.amazonvideo.livingroom", "prime"},
            {"Disney+", "com.disney.disneyplus", "disney"},
            {"Paramount+", "com.cbs.ca", "paramount"},
            {"RaiPlay", "it.rainet.androidtv", "raiplay"},
            {"Mediaset Infinity", "it.mediaset.infinitytv", "infinity"},
    };

    private Apps() {}

    /** app della home nell'ordine scelto (Home.list); quelle non installate vengono saltate */
    static List<Entry> favorites(Context ctx, List<Entry> all) {
        Map<String, Entry> byPkg = new HashMap<>();
        for (Entry e : all) byPkg.put(e.pkg, e);
        List<Entry> out = new ArrayList<>();
        for (String pkg : Home.list(ctx)) {
            if (Home.BROWSER.equals(pkg)) { out.add(browser(ctx)); continue; }
            Entry found = byPkg.get(pkg);
            if (found == null) continue;
            Entry e = new Entry();
            e.label = niceLabel(pkg, found.label); e.pkg = found.pkg; e.launch = found.launch; e.raw = found.raw;
            e.icon = Icons.of(e.pkg, e.raw);
            out.add(e);
        }
        return out;
    }

    /** il browser integrato come voce app (per home e "tutte le app") */
    static Entry browser(Context ctx) {
        Entry web = new Entry();
        web.label = "Browser";
        web.pkg = Home.BROWSER;
        web.launch = new Intent(ctx, BrowserActivity.class);
        web.icon = cachedSystem("__browser");
        return web;
    }

    /** nomi brevi e puliti per alcune app note */
    static String niceLabel(String pkg, String label) {
        for (String[] f : FAVORITES) for (String p : f[1].split("\\|")) if (p.equals(pkg)) return f[0];
        return label;
    }

    /** dock: play store, app aperte, impostazioni (in vetro), tutte le app */
    static List<Entry> dock(Context ctx, List<Entry> all) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) if (e.pkg.equals("com.android.vending")) {
            Entry s = new Entry();
            s.label = "Play Store"; s.pkg = e.pkg; s.launch = e.launch; s.icon = Icons.of(e.pkg, e.raw);
            out.add(s);
        }
        Entry r = new Entry();
        r.label = "App aperte";
        r.pkg = "recents";
        r.launch = new Intent(ctx, RecentsActivity.class);
        r.icon = cachedSystem("__recents");
        out.add(r);
        Entry set = new Entry();
        set.label = "Impostazioni";
        set.pkg = "settings";
        set.launch = new Intent(ctx, SettingsActivity.class);
        set.icon = cachedSystem("__settings");
        out.add(set);
        Entry a = new Entry();
        a.label = "Tutte le app";
        a.pkg = "all";
        a.launch = new Intent(ctx, AllAppsActivity.class);
        a.icon = cachedSystem("__all");
        out.add(a);
        return out;
    }

    private static final Map<String, Bitmap> system = new HashMap<>();
    private static synchronized Bitmap cachedSystem(String k) {
        Bitmap b = system.get(k);
        if (b == null) {
            b = k.equals("__settings") ? Icons.settings() : k.equals("__browser") ? Icons.browser()
                    : k.equals("__recents") ? Icons.recents() : Icons.allApps();
            system.put(k, b);
        }
        return b;
    }

    /** tutte le app avviabili (leanback prima, poi launcher classico), ordinate per nome */
    static List<Entry> all(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, Entry> map = new HashMap<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, map);
        List<Entry> list = new ArrayList<>(map.values());
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return list;
    }

    private static void collect(Context ctx, PackageManager pm, String cat, Map<String, Entry> map) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(ctx.getPackageName()) || map.containsKey(ai.packageName)) continue;
            Entry e = new Entry();
            e.pkg = ai.packageName;
            e.label = String.valueOf(ri.loadLabel(pm));
            e.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            e.raw = hiResIcon(pm, ai);
            map.put(e.pkg, e);
        }
    }

    /** icona alla densita' piu' alta disponibile, cosi' resta nitida anche ingrandita */
    private static Drawable hiResIcon(PackageManager pm, ActivityInfo ai) {
        try {
            int res = ai.getIconResource();
            if (res != 0) {
                Resources r = pm.getResourcesForApplication(ai.applicationInfo);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            }
        } catch (Exception ignored) { }
        return ai.loadIcon(pm);
    }
}
