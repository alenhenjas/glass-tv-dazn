/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import java.nio.ByteBuffer;

/**
 * cattura dello schermo per le anteprime del multitasking.
 * il consenso MediaProjection e' concesso una volta via adb (appops PROJECT_MEDIA allow),
 * quindi la richiesta viene approvata in silenzio, senza finestre.
 */
final class Grabber {
    interface Done { void got(Bitmap b); }

    private static MediaProjection projection;
    private static Handler bg;
    static final int W = 640, H = 360;

    private Grabber() {}

    static boolean ready() { return projection != null; }

    static void init(Context c, int resultCode, Intent data) {
        MediaProjectionManager m = (MediaProjectionManager) c.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        try {
            projection = m.getMediaProjection(resultCode, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { projection = null; }
            }, handler());
        } catch (Exception e) { projection = null; }
    }

    private static Handler handler() {
        if (bg == null) { HandlerThread t = new HandlerThread("grab"); t.start(); bg = new Handler(t.getLooper()); }
        return bg;
    }

    /** un fotogramma dello schermo attuale, ridotto a 640x360; done riceve null se non disponibile */
    static void capture(Context c, Done done) {
        if (projection == null) { done.got(null); return; }
        Handler h = handler();
        final ImageReader reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2);
        final VirtualDisplay[] vd = new VirtualDisplay[1];
        final boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            try { if (vd[0] != null) vd[0].release(); } catch (Exception ignored) { }
            try { reader.close(); } catch (Exception ignored) { }
        };
        reader.setOnImageAvailableListener(r -> {
            if (finished[0]) return;
            Bitmap out = null;
            try (Image img = r.acquireLatestImage()) {
                if (img != null) {
                    Image.Plane pl = img.getPlanes()[0];
                    ByteBuffer buf = pl.getBuffer();
                    int pxStride = pl.getPixelStride(), rowStride = pl.getRowStride();
                    int padW = rowStride / pxStride;
                    Bitmap tmp = Bitmap.createBitmap(padW, H, Bitmap.Config.ARGB_8888);
                    tmp.copyPixelsFromBuffer(buf);
                    out = Bitmap.createBitmap(tmp, 0, 0, W, H);
                    if (out != tmp) tmp.recycle();
                }
            } catch (Exception ignored) { }
            finish.run();
            done.got(out);
        }, h);
        try {
            vd[0] = projection.createVirtualDisplay("glass-snap", W, H, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, h);
        } catch (Exception e) { finish.run(); done.got(null); return; }
        // tempo massimo: se il fotogramma non arriva si prosegue comunque
        h.postDelayed(() -> { if (!finished[0]) { finish.run(); done.got(null); } }, 450);
    }

    /** activity invisibile che ottiene il token di cattura (approvato in automatico) */
    public static class TokenActivity extends Activity {
        @Override protected void onCreate(Bundle b) {
            super.onCreate(b);
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            try { startActivityForResult(m.createScreenCaptureIntent(), 7); }
            catch (Exception e) { finish(); }
        }
        @Override protected void onActivityResult(int req, int res, Intent data) {
            if (req == 7 && res == RESULT_OK && data != null) init(this, res, data);
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
