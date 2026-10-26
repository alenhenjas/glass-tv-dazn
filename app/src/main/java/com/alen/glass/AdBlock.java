/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.net.Uri;

import java.util.HashSet;
import java.util.Set;

/** blocco pubblicita'/tracker/popup per dominio (confronto sul dominio e sui suoi sottodomini) */
final class AdBlock {
    private static final Set<String> HOSTS = new HashSet<>();
    static {
        String[] list = {
                // reti pubblicitarie e tracker piu' diffusi
                "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
                "pagead2.googlesyndication.com", "googletagservices.com", "adnxs.com", "adsrvr.org",
                "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
                "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
                "teads.tv", "yieldlab.net", "adform.net", "adition.com", "bidswitch.net", "sharethrough.com",
                "3lift.com", "triplelift.com", "media.net", "moatads.com", "scorecardresearch.com",
                "quantserve.com", "zemanta.com", "revcontent.com", "mgid.com", "adroll.com", "yandex.ru/ads",
                "adsafeprotected.com", "serving-sys.com", "contextweb.com", "lijit.com", "sovrn.com",
                "indexww.com", "gumgum.com", "undertone.com", "spotxchange.com", "springserve.com",
                "admixer.net", "adkernel.com", "adtelligent.com", "seedtag.com", "onetag-sys.com",
                // popup, popunder, redirect aggressivi
                "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "adsterra.com",
                "adsterratech.com", "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net",
                "hilltopads.net", "clickadu.com", "onclickads.net", "onclkds.com", "popmyads.com",
                "admaven.com", "ad-maven.com", "a-ads.com", "richpush.co", "pushame.com", "monetag.com",
                "galaksion.com", "clickaine.com", "adcash.com", "bidvertiser.com", "revenuehits.com",
                "zeropark.com", "trafficstars.com", "tsyndicate.com", "realsrv.com", "pemsrv.com",
                "intellipopup.com", "notifpush.com", "pushwhy.com",
        };
        for (String h : list) HOSTS.add(h);
    }

    private AdBlock() {}

    static boolean blocked(Uri u) {
        String host = u == null ? null : u.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        while (true) {
            if (HOSTS.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0 || dot == host.lastIndexOf('.')) return false;
            host = host.substring(dot + 1);
        }
    }
}
