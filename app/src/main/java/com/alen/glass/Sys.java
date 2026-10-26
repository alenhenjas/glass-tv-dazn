/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** utilita' di sistema: comandi shell con i permessi dell'app (DUMP concesso via adb), proprieta' */
final class Sys {
    private static final Pattern PROC = Pattern.compile("ProcessRecord\\{\\w+ \\d+:([^/}\\s]+)");

    private Sys() {}

    /** xiaomi tv box (google tv): impostazioni video, volume e menu di sistema diversi dal box sagemcom */
    static final boolean XIAOMI = "xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER);

    static String exec(String... cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor();
        } catch (Exception ignored) { }
        return sb.toString();
    }

    static String prop(String key) {
        return exec("getprop", key).trim();
    }

    /** pacchetti con almeno un processo vivo (richiede android.permission.DUMP) */
    static Set<String> running() {
        Set<String> out = new HashSet<>();
        String s = exec("dumpsys", "activity", "processes");
        Matcher m = PROC.matcher(s);
        while (m.find()) {
            String name = m.group(1);
            int colon = name.indexOf(':');
            out.add(colon > 0 ? name.substring(0, colon) : name);
        }
        return out;
    }

    /** true se il permesso DUMP e' stato concesso (altrimenti lo stato "aperta/chiusa" non e' noto) */
    static boolean canDump() {
        return !exec("dumpsys", "activity", "processes").contains("Permission Denial") ;
    }
}
