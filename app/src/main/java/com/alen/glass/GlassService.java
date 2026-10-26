/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * servizio di sistema di glass:
 * - indicatore volume in vetro al posto di quello android (intercetta i tasti volume)
 * - registra le app aperte (per "app aperte" e per "chiudi app")
 * - automazione: applica impostazioni del box (es. risoluzione) premendo le voci al posto tuo,
 *   coprendo lo schermo con una scheda in vetro, poi torna alla home
 */
public class GlassService extends AccessibilityService {
    static GlassService instance;
    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private VolumeHud hud;
    private View cover;
    private AudioManager audio;
    private String fg;              // app attualmente in primo piano
    private boolean homeBusy;

    // automazione
    private List<String[]> steps;   // ogni passo: alternative di testo da cliccare
    private int stepIdx;
    private Runnable onDone;
    private String result;
    private long stepDeadline;

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        Ui.init(this);
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
        // token di cattura per le anteprime del multitasking (approvato in automatico via appops)
        h.postDelayed(this::ensureProjection, 2500);
        h.postDelayed(periodicShot, 20000);
        // diagnostica: "am broadcast -a com.alen.glass.POWER_MENU" apre il menu di accensione di sistema
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); }
        }, new android.content.IntentFilter("com.alen.glass.POWER_MENU"));
        // volume cambiato da qualunque parte (telecomando gestito dal sistema, app...): mostra il nostro indicatore
        android.content.IntentFilter vf = new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        vf.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int st = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                // sullo xiaomi i tasti volume non passano dal filtro tasti: l'indicatore parte dal cambio di volume
                if (st == AudioManager.STREAM_MUSIC) h.post(GlassService.this::showVolume);
            }
        }, vf);
        // diagnostica: "am broadcast -a com.alen.glass.SHOW_VOLUME" mostra l'indicatore del volume
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { showVolume(); }
        }, new android.content.IntentFilter("com.alen.glass.SHOW_VOLUME"));
        new Thread(() -> {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            Wallpaper.ensure(this, dm.widthPixels, dm.heightPixels);
        }).start();
    }

    void ensureProjection() {
        if (Grabber.ready()) return;
        try {
            startActivity(new Intent(this, Grabber.TokenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception ignored) { }
    }

    /** ogni 20 s aggiorna l'anteprima dell'app in primo piano (per il multitasking) */
    private final Runnable periodicShot = new Runnable() {
        @Override public void run() {
            final String pkg = fg;
            if (pkg != null && steps == null && cover == null && isUserApp(pkg)) {
                Grabber.capture(GlassService.this, b -> { if (b != null && pkg.equals(fg)) Recents.shot(GlassService.this, pkg, b); });
            }
            h.postDelayed(this, 20000);
        }
    };

    private boolean isUserApp(String pkg) {
        return pkg != null && !pkg.equals(getPackageName()) && !pkg.startsWith("com.android.systemui")
                && !pkg.contains("inputmethod") && isLaunchable(pkg);
    }

    @Override public boolean onUnbind(Intent i) { instance = null; return super.onUnbind(i); }

    @Override public void onInterrupt() { }

    // ---------------- app in primo piano ----------------

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.getPackageName() != null) {
            String pkg = e.getPackageName().toString();
            if (isLaunchable(pkg) && !pkg.equals(getPackageName())) {
                Running.opened(this, pkg);
                Recents.touch(pkg);
            }
            if (!pkg.contains("inputmethod") && !pkg.equals("android")) fg = pkg;
        }
        if (steps != null) h.post(this::advance);
    }

    private boolean isLaunchable(String pkg) {
        PackageManager pm = getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    // ---------------- volume ----------------

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_HOME) return onHome(e);
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN && k != KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (audio.isVolumeFixed()) return false; // volume a livello fisso (gestito dalla tv): lo lascia al sistema
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_VOLUME_MUTE) {
                if (e.getRepeatCount() == 0) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
            } else {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        k == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            }
            showVolume();
        }
        return true; // consumato: niente indicatore android
    }

    /**
     * tasto home premuto dentro un'app: prima si cattura l'anteprima della schermata (multitasking),
     * poi si va alla home. senza cattura disponibile si lascia il comportamento normale.
     */
    private boolean onHome(KeyEvent e) {
        final String pkg = fg;
        if (!isUserApp(pkg) || !Grabber.ready() || steps != null) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && !homeBusy) {
            homeBusy = true;
            Grabber.capture(this, b -> h.post(() -> {
                if (b != null) Recents.shot(this, pkg, b);
                performGlobalAction(GLOBAL_ACTION_HOME);
                homeBusy = false;
            }));
            h.postDelayed(() -> { if (homeBusy) { homeBusy = false; performGlobalAction(GLOBAL_ACTION_HOME); } }, 900);
        }
        return true;
    }

    String foreground() { return fg; }

    /** riavvio: apre il menu di accensione di sistema e preme "riavvia" sotto la scheda in vetro */
    void reboot(java.util.function.Consumer<String> done) {
        showCover("Riavvio in corso…");
        steps = new ArrayList<>();
        steps.add(new String[]{"Riavvia", "Riavvia il dispositivo", "Restart", "Reboot"});
        stepIdx = 0;
        result = null;
        onDone = () -> { if (done != null) done.accept(result); };
        stepDeadline = System.currentTimeMillis() + 5000;
        if (!performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) { result = "menu non disponibile"; finish(); return; }
        h.postDelayed(this::advance, 500);
        h.postDelayed(watchdog, 600);
    }

    /** standby del box: con hdmi-cec attivo si spegne anche la tv */
    boolean sleep() { return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }

    void showVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
        if (hud == null) {
            hud = new VolumeHud(this);
            int size = (int) Ui.dp(this, 178);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            // in basso al centro, come su iphone/ipad/mac
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) Ui.dp(this, 44);
            hud.setAlpha(0f);
            hud.setScaleX(0.92f);
            hud.setScaleY(0.92f);
            wm.addView(hud, lp);
        }
        hud.set(muted ? 0 : cur / (float) max, muted);
        h.removeCallbacks(hideHud);
        hud.animate().cancel();
        hud.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(Ui.EASE).start();
        h.postDelayed(hideHud, 1600);
    }

    private final Runnable hideHud = () -> {
        if (hud != null) hud.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(280).start();
    };

    /**
     * indicatore volume "liquid glass": quadrato di vetro trasparente (si vede cio' che c'e' sotto),
     * riflesso speculare e bordo luminoso, altoparlante bianco con onde in base al livello
     * e barra a capsula in vetro sotto.
     */
    static final class VolumeHud extends View {
        private float level, shown = -1f;
        private boolean muted;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path sq, speaker;
        private RectF box;
        private Shader tintSh, frost, glow, rim, trackSh;
        private android.animation.ValueAnimator anim;

        VolumeHud(Context c) { super(c); setLayerType(LAYER_TYPE_HARDWARE, null); }

        /** il livello scorre morbido verso il nuovo valore */
        void set(float l, boolean m) {
            muted = m;
            if (shown < 0) { shown = level = l; invalidate(); return; }
            level = l;
            if (anim != null) anim.cancel();
            anim = android.animation.ValueAnimator.ofFloat(shown, l);
            anim.setDuration(160);
            anim.setInterpolator(Ui.EASE);
            anim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            float u = Ui.dp(getContext(), 1);
            box = new RectF(12 * u, 10 * u, w - 12 * u, hh - 14 * u);
            sq = Ui.squircle(box, box.width() * 0.24f);
            // vetro: tinta scura molto leggera + brina chiara in alto, tutto trasparente
            tintSh = new LinearGradient(0, box.top, 0, box.bottom, Color.argb(78, 28, 28, 36), Color.argb(96, 10, 10, 16), Shader.TileMode.CLAMP);
            frost = new LinearGradient(0, box.top, 0, box.top + box.height() * 0.55f, Color.argb(62, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP);
            glow = new android.graphics.RadialGradient(box.left + box.width() * 0.28f, box.top, box.width() * 0.75f,
                    Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP);
            rim = new LinearGradient(box.left, box.top, box.right, box.bottom,
                    new int[]{Color.argb(230, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.argb(25, 255, 255, 255), Color.argb(150, 255, 255, 255)},
                    new float[]{0f, 0.38f, 0.62f, 1f}, Shader.TileMode.CLAMP);
            float k = box.width() / 150f;
            float cx = box.left + box.width() * 0.36f, cy = box.top + box.height() * 0.40f;
            speaker = new Path();
            speaker.moveTo(cx - 25 * k, cy - 11 * k);
            speaker.lineTo(cx - 11 * k, cy - 11 * k);
            speaker.lineTo(cx + 6 * k, cy - 26 * k);
            speaker.lineTo(cx + 6 * k, cy + 26 * k);
            speaker.lineTo(cx - 11 * k, cy + 11 * k);
            speaker.lineTo(cx - 25 * k, cy + 11 * k);
            speaker.close();
            float th = 9 * u, ty = box.bottom - box.height() * 0.2f;
            trackSh = new LinearGradient(0, ty - th / 2, 0, ty + th / 2, Color.argb(70, 0, 0, 0), Color.argb(40, 255, 255, 255), Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas c) {
            if (box == null) return;
            float u = Ui.dp(getContext(), 1);
            float lv = muted ? 0 : Math.max(0, Math.min(1, shown));
            // ombra leggerissima: stacca il vetro dal video senza coprirlo
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(38, 0, 0, 0));
            c.save(); c.translate(0, 5 * u); c.drawPath(sq, p); c.restore();
            // vetro trasparente
            p.setColor(Color.WHITE);
            p.setShader(tintSh); c.drawPath(sq, p);
            p.setShader(frost); c.drawPath(sq, p);
            p.setShader(glow); c.drawPath(sq, p);
            // bordo speculare (solo meta' interna del tratto: nitido)
            p.setShader(rim); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f * u);
            c.save(); c.clipPath(sq); c.drawPath(sq, p); c.restore();
            p.setShader(null);

            float k = box.width() / 150f;
            float cx = box.left + box.width() * 0.36f, cy = box.top + box.height() * 0.40f;
            int ink = Color.WHITE;
            // ombra morbida sotto i simboli: leggibili anche su immagini chiare
            p.setShadowLayer(6 * u, 0, 1.5f * u, Color.argb(110, 0, 0, 0));
            p.setStyle(Paint.Style.FILL);
            p.setColor(ink);
            p.setPathEffect(new android.graphics.CornerPathEffect(3.5f * k));
            c.drawPath(speaker, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(5.5f * k);
            if (muted || lv <= 0.001f) {
                // altoparlante barrato
                p.setStrokeWidth(5f * k);
                float x0 = cx + 18 * k;
                c.drawLine(x0, cy - 11 * k, x0 + 22 * k, cy + 11 * k, p);
                c.drawLine(x0 + 22 * k, cy - 11 * k, x0, cy + 11 * k, p);
            } else {
                float waves = lv * 3f;
                for (int i = 1; i <= 3; i++) {
                    float on = Math.max(0, Math.min(1, waves - (i - 1)));
                    p.setColor(Color.argb((int) (70 + 185 * on), 255, 255, 255));
                    float r = (9 + 11.5f * i) * k;
                    c.drawArc(new RectF(cx + 3 * k - r, cy - r, cx + 3 * k + r, cy + r), -46, 92, false, p);
                }
            }
            p.clearShadowLayer();
            // barra a capsula in vetro
            float th = 9 * u, ty = box.bottom - box.height() * 0.2f;
            float left = box.left + box.width() * 0.16f, right = box.right - box.width() * 0.16f;
            RectF track = new RectF(left, ty - th / 2, right, ty + th / 2);
            p.setStyle(Paint.Style.FILL);
            p.setShader(trackSh);
            p.setColor(Color.WHITE);
            c.drawRoundRect(track, th / 2, th / 2, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * u);
            p.setColor(Color.argb(70, 255, 255, 255));
            c.drawRoundRect(track, th / 2, th / 2, p);
            if (lv > 0) {
                RectF fill = new RectF(left, track.top, left + Math.max(th, (right - left) * lv), track.bottom);
                p.setStyle(Paint.Style.FILL);
                p.setShadowLayer(5 * u, 0, 0, Color.argb(120, 255, 255, 255)); // bagliore della parte piena
                p.setColor(Color.WHITE);
                c.drawRoundRect(fill, th / 2, th / 2, p);
                p.clearShadowLayer();
            }
        }
    }

    // ---------------- automazione impostazioni ----------------

    /**
     * apre 'component', poi clicca in ordine le voci indicate (ognuna con testi alternativi),
     * infine torna alla home. 'done' riceve null se ok, altrimenti il testo non trovato.
     */
    void automate(ComponentName component, String message, List<String[]> clickSteps, java.util.function.Consumer<String> done) {
        showCover(message);
        steps = new ArrayList<>(clickSteps);
        stepIdx = 0;
        result = null;
        onDone = () -> { if (done != null) done.accept(result); };
        stepDeadline = System.currentTimeMillis() + 6000;
        try {
            startActivity(new Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (Exception ex) {
            result = "impostazione non disponibile";
            finish();
            return;
        }
        h.postDelayed(this::advance, 700);
        h.postDelayed(watchdog, 800);
    }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (steps == null) return;
            if (System.currentTimeMillis() > stepDeadline) {
                result = steps.get(Math.min(stepIdx, steps.size() - 1))[0];
                finish();
                return;
            }
            advance();
            h.postDelayed(this, 500);
        }
    };

    private void advance() {
        if (steps == null) return;
        if (stepIdx >= steps.size()) { finish(); return; }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active == null) return;
        String pkg = active.getPackageName() == null ? "" : active.getPackageName().toString();
        if (pkg.equals(getPackageName())) return; // la pagina non e' ancora in primo piano
        // finestre in cui cercare: quella attiva + quelle sovrapposte (es. la conferma risoluzione dello xiaomi)
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null || r.getPackageName().toString().equals(getPackageName())) continue;
                roots.add(r);
            }
        } catch (Exception ignored) { }
        if (!roots.contains(active)) roots.add(0, active);
        for (AccessibilityNodeInfo root : roots)
        for (String step : steps.get(stepIdx)) {
            // "titolo&dettaglio": voce con quel titolo il cui riquadro contiene anche il dettaglio (es. 1080p&60Hz)
            // "~voce": la voce va solo selezionata (focus), es. per mostrarne il pannello di anteprima
            boolean focusOnly = step.startsWith("~");
            String text = focusOnly ? step.substring(1) : step, detail = null;
            int amp = text.indexOf('&');
            if (amp > 0) { detail = text.substring(amp + 1); text = text.substring(0, amp); }
            // ricerca per testo + visita completa dell'albero (le pagine "slice" delle impostazioni sfuggono alla prima)
            List<AccessibilityNodeInfo> found = new ArrayList<>(root.findAccessibilityNodeInfosByText(text));
            if (found.isEmpty()) collectText(root, text, found, 0);
            for (AccessibilityNodeInfo n : found) {
                if (n.getText() == null || !n.getText().toString().trim().equalsIgnoreCase(text)) continue;
                AccessibilityNodeInfo t = n;
                while (t != null && !t.isClickable()) t = t.getParent();
                if (detail != null && (t == null || !hasText(t, detail, 0))) continue;
                android.util.Log.d("GlassAuto", "candidato " + text + " in " + root.getPackageName() + " cliccabile=" + (t != null));
                if (t != null && focusOnly) {
                    AccessibilityNodeInfo f = t;
                    while (f != null && !f.isFocusable()) f = f.getParent();
                    if (f == null) f = t;
                    f.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    f.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                    android.util.Log.d("GlassAuto", "focus su " + text);
                    stepIdx++;
                    stepDeadline = System.currentTimeMillis() + 5000;
                    if (stepIdx >= steps.size()) h.postDelayed(this::finish, 900);
                    return;
                }
                if (t != null && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    android.util.Log.d("GlassAuto", "cliccato " + step + " bounds " + boundsOf(t));
                    stepIdx++;
                    stepDeadline = System.currentTimeMillis() + 5000;
                    if (stepIdx >= steps.size()) h.postDelayed(this::finish, 900);
                    return;
                }
            }
        }
    }

    private static void collectText(AccessibilityNodeInfo n, String text, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 40 || out.size() > 20) return;
        CharSequence t = n.getText();
        if (t != null && t.toString().trim().equalsIgnoreCase(text)) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectText(n.getChild(i), text, out, depth + 1);
    }

    private static String boundsOf(AccessibilityNodeInfo n) { android.graphics.Rect r = new android.graphics.Rect(); n.getBoundsInScreen(r); return r.toShortString(); }

    private static boolean hasText(AccessibilityNodeInfo n, String text, int depth) {
        if (n == null || depth > 6) return false;
        CharSequence t = n.getText();
        if (t != null && t.toString().trim().equalsIgnoreCase(text)) return true;
        for (int i = 0; i < n.getChildCount(); i++) if (hasText(n.getChild(i), text, depth + 1)) return true;
        return false;
    }

    private void finish() {
        if (steps == null) return;
        steps = null;
        h.removeCallbacks(watchdog);
        try {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ignored) { }
        h.postDelayed(() -> { hideCover(); if (onDone != null) onDone.run(); onDone = null; }, 450);
    }

    /** scheda in vetro a schermo intero che copre la pagina android durante l'automazione */
    private void showCover(String msg) {
        hideCover();
        CoverView v = new CoverView(this, msg);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);
        wm.addView(v, lp);
        cover = v;
    }

    private void hideCover() {
        if (cover != null) { try { wm.removeView(cover); } catch (Exception ignored) { } cover = null; }
    }

    static final class CoverView extends View {
        private final String msg;
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        CoverView(Context c, String m) {
            super(c);
            msg = m;
            tp.setTypeface(Ui.textSemibold());
            tp.setTextSize(Ui.dp(c, 20));
            tp.setColor(Color.WHITE);
            tp.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            if (Wallpaper.full != null) c.drawBitmap(Wallpaper.full, null, new RectF(0, 0, w, hh), null);
            else c.drawColor(Color.rgb(12, 12, 24));
            p.setColor(Color.argb(120, 0, 0, 10));
            c.drawRect(0, 0, w, hh, p);
            float u = Ui.dp(getContext(), 1);
            RectF r = new RectF(w / 2 - 200 * u, hh / 2 - 44 * u, w / 2 + 200 * u, hh / 2 + 44 * u);
            Path sq = Ui.squircle(r, 30 * u);
            p.setColor(Color.argb(200, 44, 44, 54));
            c.drawPath(sq, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * u);
            p.setColor(Color.argb(120, 255, 255, 255));
            c.drawPath(sq, p);
            p.setStyle(Paint.Style.FILL);
            c.drawText(msg, w / 2, hh / 2 - (tp.ascent() + tp.descent()) / 2f, tp);
        }
    }
}
