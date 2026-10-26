/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * menu della pressione lunga, uguale ovunque (home e "tutte le app"):
 * apri, chiudi app (rossa se aperta, grigia se gia' chiusa), aggiungi/rimuovi dalla home, informazioni.
 */
final class AppMenu {
    private AppMenu() {}

    private static final long FRESH_MS = 120000;

    static void show(Activity a, FrameLayout root, Apps.Entry e, View[] block, Runnable onHomeChanged) {
        Handler ui = new Handler(Looper.getMainLooper());
        boolean real = e.pkg != null && e.pkg.contains(".");
        boolean closable = real && Closer.closable(e.pkg);
        // menu immediato se l'elenco dei processi e' recente (la home lo aggiorna in background)
        if (!closable || Closer.hasFresh(FRESH_MS)) {
            boolean open = closable && Closer.isRunning(a, e.pkg, Closer.runningSetFast(FRESH_MS));
            build(a, root, e, block, onHomeChanged, ui, real, closable, open);
            return;
        }
        new Thread(() -> {
            boolean open = Closer.isRunning(a, e.pkg, Closer.runningSet());
            ui.post(() -> build(a, root, e, block, onHomeChanged, ui, real, closable, open));
        }, "menu").start();
    }

    private static void build(Activity a, FrameLayout root, Apps.Entry e, View[] block, Runnable onHomeChanged,
                              Handler ui, boolean real, boolean closable, boolean open) {
        if (a.isFinishing()) return;
        boolean inHome = Home.contains(a, e.pkg);
        List<String> labels = new ArrayList<>();
        List<Boolean> red = new ArrayList<>(), enabled = new ArrayList<>();
        List<Sheet.Action> acts = new ArrayList<>();

        labels.add("Apri"); red.add(false); enabled.add(true);
        acts.add(() -> { try { a.startActivity(e.launch); } catch (Exception ignored) { } });

        if (closable) {
            labels.add(open ? "Chiudi app" : "Chiudi app  ·  già chiusa"); red.add(true); enabled.add(open);
            acts.add(() -> {
                long before = Closer.availMem(a);
                Closer.close(a, e.pkg);
                ui.postDelayed(() -> Sheet.toast(root, e.label + " chiusa" + freed(a, before)), 500);
            });
        }

        if (inHome) {
            labels.add("Rimuovi dalla Home"); red.add(false); enabled.add(true);
            acts.add(() -> {
                Home.remove(a, e.pkg);
                Sheet.toast(root, e.label + " rimossa dalla Home");
                if (onHomeChanged != null) onHomeChanged.run();
            });
        } else {
            labels.add("Aggiungi alla Home"); red.add(false); enabled.add(true);
            acts.add(() -> {
                Home.add(a, e.pkg);
                Sheet.toast(root, e.label + " aggiunta alla Home");
                if (onHomeChanged != null) onHomeChanged.run();
            });
        }

        if (real) {
            labels.add("Informazioni app"); red.add(false); enabled.add(true);
            acts.add(() -> {
                try {
                    a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) { }
            });
        }

        labels.add("Annulla"); red.add(false); enabled.add(true); acts.add(null);

        boolean[] r = new boolean[red.size()], en = new boolean[enabled.size()];
        for (int i = 0; i < r.length; i++) { r[i] = red.get(i); en[i] = enabled.get(i); }
        Sheet.show(root, e.icon, e.label, labels.toArray(new String[0]), r, en,
                acts.toArray(new Sheet.Action[0]), block);
    }

    static String freed(Activity a, long before) {
        long mb = (Closer.availMem(a) - before) / (1024 * 1024);
        return mb > 5 ? "  ·  " + mb + " MB liberati" : "";
    }
}
