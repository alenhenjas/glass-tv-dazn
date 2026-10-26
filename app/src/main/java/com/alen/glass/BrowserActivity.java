/*
 * Glass TV — interfaccia liquid glass per Android TV
 * Copyright (c) 2026 Alen Henjas — https://github.com/alenhenjas
 * Rilasciato con licenza MIT (vedi LICENSE).
 */
package com.alen.glass;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * browser su motore chromium (webview di sistema = stesso motore di chrome),
 * pensato per il telecomando: puntatore come un mouse, rotella ai bordi, siti in versione desktop,
 * blocco pubblicita' e popup.
 *
 * tasti: frecce = muovi puntatore | ok = clic (tieni premuto = pressione lunga) | indietro = pagina precedente
 * ch+/ch- = scorri | rew/ff = indietro/avanti | giallo/blu = zoom -/+ | info/guida/menu = barra indirizzi
 */
public class BrowserActivity extends Activity {
    private static final String HOME = "https://www.google.com/";
    private WebView web;
    private EditText url;
    private FrameLayout root, fullscreen;
    private GlassPanel bar;
    private ProgressLine progress;
    private Pointer pointer;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private float px, py;                 // posizione puntatore (coordinate finestra)
    private int dirX, dirY;               // direzione frecce premute
    private long moveStart;
    private boolean moving, desktop = true;
    private long downTime;
    private SharedPreferences prefs;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = getSharedPreferences("browser", MODE_PRIVATE);
        desktop = prefs.getBoolean("desktop", true);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 10, 18));
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        final int sw = dm.widthPixels, sh = dm.heightPixels;
        new Thread(() -> { Wallpaper.ensure(this, sw, sh); runOnUiThread(() -> { bg.setImageBitmap(Wallpaper.full); bar.invalidate(); }); }).start();

        // barra in vetro: indietro, avanti, ricarica, indirizzo, home, desktop/mobile
        bar = new GlassPanel(this, 0.5f, 4, 0.12f);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.addView(tool(Tool.BACK, v -> { if (web.canGoBack()) web.goBack(); }));
        row.addView(tool(Tool.FORWARD, v -> { if (web.canGoForward()) web.goForward(); }));
        row.addView(tool(Tool.RELOAD, v -> web.reload()));
        url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        url.setHint("Cerca su Google o scrivi un indirizzo");
        url.setHintTextColor(Color.argb(150, 255, 255, 255));
        url.setTextColor(Color.WHITE);
        url.setTypeface(Ui.textMedium());
        url.setTextSize(15);
        url.setPadding(dp(18), 0, dp(18), 0);
        url.setBackground(new Capsule(this));
        url.setSelectAllOnFocus(true);
        url.setFocusable(false);            // non ruba il focus (e le frecce) finche' non la si clicca
        url.setFocusableInTouchMode(false);
        url.setOnClickListener(v -> editUrl());
        url.setOnEditorActionListener((v, id, ev) -> { go(url.getText().toString()); return true; });
        url.setOnFocusChangeListener((v, has) -> {
            if (has) {
                String cur = web.getUrl();
                url.setText(cur == null || cur.startsWith(HOME) ? "" : cur);
                url.post(url::selectAll); // quello che scrivi sostituisce l'indirizzo
                showKeyboard();
            }
            else { url.setText(pretty(web.getUrl())); url.setFocusable(false); url.setFocusableInTouchMode(false); }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        ulp.leftMargin = dp(10); ulp.rightMargin = dp(10);
        row.addView(url, ulp);
        row.addView(tool(Tool.HOME, v -> web.loadUrl(HOME)));
        row.addView(tool(Tool.DESKTOP, v -> toggleDesktop()));
        bar.addView(row, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, dp(60), Gravity.TOP);
        blp.leftMargin = dp(20); blp.rightMargin = dp(20); blp.topMargin = dp(10);
        root.addView(bar, blp);

        progress = new ProgressLine(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        plp.topMargin = dp(74);
        root.addView(progress, plp);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setSupportMultipleWindows(false);           // i link "nuova finestra" si aprono qui
        s.setJavaScriptCanOpenWindowsAutomatically(false); // niente popup automatici
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setTextZoom(100);
        applyUserAgent();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener((u, ua, cd, mime, len) ->
                Toast.makeText(this, "I download non sono supportati nel browser", Toast.LENGTH_SHORT).show());
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(-1, -1);
        wlp.topMargin = dp(80);
        root.addView(web, wlp);

        fullscreen = new FrameLayout(this);
        fullscreen.setBackgroundColor(Color.BLACK);
        fullscreen.setVisibility(View.GONE);
        root.addView(fullscreen, new FrameLayout.LayoutParams(-1, -1));

        pointer = new Pointer(this);
        root.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        immersive();
        web.requestFocus();

        root.post(() -> { px = root.getWidth() / 2f; py = root.getHeight() * 0.45f; pointer.move(px, py); pointer.wake(); });
        if (b != null) web.restoreState(b);
        else {
            String last = prefs.getString("last", null);
            long when = prefs.getLong("lastAt", 0);
            web.loadUrl(last != null && System.currentTimeMillis() - when < 6 * 3600_000L ? last : HOME);
        }
    }

    // ---------------- navigazione ----------------

    private void go(String text) {
        String t = text.trim();
        if (t.isEmpty()) return;
        String target;
        if (t.startsWith("http://") || t.startsWith("https://")) target = t;
        else if (!t.contains(" ") && (Patterns.WEB_URL.matcher(t).matches() || t.startsWith("localhost"))) target = "https://" + t;
        else target = "https://www.google.com/search?q=" + Uri.encode(t);
        web.loadUrl(target);
        url.clearFocus();
        hideKeyboard();
        web.requestFocus();
    }

    private void editUrl() {
        url.setFocusable(true);
        url.setFocusableInTouchMode(true);
        url.requestFocus();
    }

    private void toggleDesktop() {
        desktop = !desktop;
        prefs.edit().putBoolean("desktop", desktop).apply();
        applyUserAgent();
        web.reload();
        Toast.makeText(this, desktop ? "Versione desktop" : "Versione mobile", Toast.LENGTH_SHORT).show();
    }

    private void applyUserAgent() {
        String def = WebSettings.getDefaultUserAgent(this);
        if (!desktop) { web.getSettings().setUserAgentString(def.replace("; wv", "")); return; }
        String ver = "155.0.0.0";
        Matcher m = Pattern.compile("Chrome/(\\d+)").matcher(def);
        if (m.find()) ver = m.group(1) + ".0.0.0";
        web.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + ver + " Safari/537.36");
    }

    private static String pretty(String u) {
        if (u == null) return "";
        Uri p = Uri.parse(u);
        if ("www.google.com".equals(p.getHost()) && "/search".equals(p.getPath()) && p.getQueryParameter("q") != null)
            return p.getQueryParameter("q");
        return p.getHost() != null ? p.getHost().replaceFirst("^www\\.", "") : u;
    }

    private final class Client extends WebViewClient {
        private final WebResourceResponse empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));

        @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            if (AdBlock.blocked(r.getUrl()))
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            return null;
        }

        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String sch = u.getScheme() == null ? "" : u.getScheme();
            if (AdBlock.blocked(u)) return true;                       // redirect pubblicitari
            if (!sch.equals("http") && !sch.equals("https")) return true; // intent://, market:// ecc.
            if (r.isForMainFrame() && !r.hasGesture() && r.isRedirect() && AdBlock.blocked(u)) return true;
            return false;
        }

        @Override public void onPageStarted(WebView v, String u, Bitmap f) {
            if (!url.hasFocus()) url.setText(pretty(u));
        }

        @Override public void onPageFinished(WebView v, String u) {
            if (!url.hasFocus()) url.setText(pretty(u));
            prefs.edit().putString("last", u).putLong("lastAt", System.currentTimeMillis()).apply();
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v, int p) { progress.set(p); }

        @Override public void onShowCustomView(View view, CustomViewCallback cb) {
            if (customView != null) { cb.onCustomViewHidden(); return; }
            customView = view; customCb = cb;
            fullscreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
            fullscreen.setVisibility(View.VISIBLE);
            pointer.setVisibility(View.GONE);
        }

        @Override public void onHideCustomView() { exitFullscreen(); }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        pointer.setVisibility(View.VISIBLE);
    }

    // ---------------- telecomando ----------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (url.hasFocus()) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!down) { url.clearFocus(); hideKeyboard(); web.requestFocus(); }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (customView != null) {
            if (k == KeyEvent.KEYCODE_BACK) { if (!down) exitFullscreen(); return true; }
            return super.dispatchKeyEvent(e); // in video a schermo intero i tasti vanno al player
        }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: if (e.getRepeatCount() == 0) arrow(down, 0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (e.getRepeatCount() == 0) arrow(down, 0, 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (e.getRepeatCount() == 0) arrow(down, -1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (e.getRepeatCount() == 0) arrow(down, 1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) press(true);
                else if (!down) press(false);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) { if (web.canGoBack()) web.goBack(); else finish(); }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down) wheel(0, -6); return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down) wheel(0, 6); return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!down && web.canGoBack()) web.goBack(); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!down && web.canGoForward()) web.goForward(); return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
                if (!down) web.zoomOut(); return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                if (!down) web.zoomIn(); return true;
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
                if (!down) editUrl();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // play/pausa sui video della pagina (come la barra spaziatrice sul pc)
                web.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), KeyEvent.KEYCODE_SPACE, 0));
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void arrow(boolean down, int dx, int dy) {
        if (down) {
            // ogni pressione sposta di un passo; tenendo premuto parte lo spostamento continuo che accelera
            nudge(dx, dy);
            if (dx != 0) dirX = dx;
            if (dy != 0) dirY = dy;
            moveStart = SystemClock.uptimeMillis();
            if (!moving) { moving = true; Choreographer.getInstance().postFrameCallback(frame); }
        } else {
            if (dx != 0 && dirX == dx) dirX = 0;
            if (dy != 0 && dirY == dy) dirY = 0;
        }
        pointer.wake();
    }

    private void nudge(int dx, int dy) {
        float step = dp(9);
        px = Math.max(0, Math.min(root.getWidth() - 1, px + dx * step));
        py = Math.max(0, Math.min(root.getHeight() - 1, py + dy * step));
        pointer.move(px, py);
        mouse(MotionEvent.ACTION_HOVER_MOVE, 0, true);
    }

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (dirX == 0 && dirY == 0) { moving = false; return; }
            float secs = (SystemClock.uptimeMillis() - moveStart) / 1000f;
            if (secs < 0.16f) { Choreographer.getInstance().postFrameCallback(this); return; }
            float speed = dp(4) + Math.min(1f, (secs - 0.16f) / 0.7f) * dp(20); // accelera dolcemente come un trackpad
            float nx = px + dirX * speed, ny = py + dirY * speed;
            int w = root.getWidth(), h = root.getHeight();
            int webTop = web.getTop();
            // bordo pagina: invece di uscire, scorre (rotella) l'elemento sotto al puntatore
            if (ny > h - dp(6) && dirY > 0) { wheel(0, -1.2f); ny = h - dp(6); }
            if (ny < webTop + dp(4) && dirY < 0 && py >= webTop + dp(4)) {
                if (web.getScrollY() > 0 || secs < 0.6f) { wheel(0, 1.2f); ny = webTop + dp(4); }
            }
            if (nx < dp(4) && dirX < 0) { wheel(1.2f, 0); nx = dp(4); }
            if (nx > w - dp(4) && dirX > 0) { wheel(-1.2f, 0); nx = w - dp(4); }
            px = Math.max(0, Math.min(w - 1, nx));
            py = Math.max(0, Math.min(h - 1, ny));
            pointer.move(px, py);
            mouse(MotionEvent.ACTION_HOVER_MOVE, 0, true);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private boolean pressOnWeb;

    /**
     * clic: dentro la pagina si invia un tocco vero (chromium lo gestisce sempre, anche sui pulsanti
     * dei banner come "accetta tutto"); sulla barra in vetro un clic mouse alle view android.
     */
    private void press(boolean down) {
        pointer.press(down);
        if (down) {
            downTime = SystemClock.uptimeMillis();
            pressOnWeb = py >= web.getTop() && customView == null;
            if (pressOnWeb) touch(MotionEvent.ACTION_DOWN);
            else mouse(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, false);
        } else {
            if (pressOnWeb) touch(MotionEvent.ACTION_UP);
            else mouse(MotionEvent.ACTION_UP, 0, false);
        }
    }

    private void touch(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = px - web.getLeft(); pc[0].y = py - web.getTop(); pc[0].pressure = 1f; pc[0].size = 1f;
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, 1, pp, pc, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** evento mouse sintetico alla posizione del puntatore (coordinate finestra) */
    private void mouse(int action, int buttons, boolean generic) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = px; pc[0].y = py; pc[0].pressure = 1f; pc[0].size = 1f;
        MotionEvent ev = MotionEvent.obtain(action == MotionEvent.ACTION_HOVER_MOVE ? now : downTime, now, action, 1, pp, pc,
                0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        View decor = getWindow().getDecorView();
        if (generic) decor.dispatchGenericMotionEvent(ev); else decor.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** rotella del mouse: v>0 su, v<0 giu' (scorre l'elemento sotto al puntatore, come sul pc) */
    private void wheel(float h, float v) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        float wx = px - web.getLeft(), wy = Math.max(1, py - web.getTop());
        pc[0].x = wx; pc[0].y = wy;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, h);
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    // ---------------- ciclo di vita ----------------

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }

    @Override public void onWindowFocusChanged(boolean has) { super.onWindowFocusChanged(has); if (has) immersive(); }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showKeyboard() {
        url.post(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(url, InputMethodManager.SHOW_IMPLICIT));
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(url.getWindowToken(), 0);
    }

    private int dp(float v) { return (int) Ui.dp(this, v); }

    // ---------------- componenti grafici ----------------

    enum Tool { BACK, FORWARD, RELOAD, HOME, DESKTOP }

    private View tool(Tool t, View.OnClickListener l) {
        ToolButton b = new ToolButton(this, t);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.leftMargin = dp(2); lp.rightMargin = dp(2);
        b.setLayoutParams(lp);
        return b;
    }

    /** pulsante circolare con glifo disegnato; evidenziato quando il puntatore ci passa sopra */
    static final class ToolButton extends View {
        private final Tool tool;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ToolButton(Context c, Tool t) { super(c); tool = t; setClickable(true); setFocusable(false); }
        @Override public boolean onHoverEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_HOVER_ENTER || a == MotionEvent.ACTION_HOVER_MOVE) setHovered(true);
            else if (a == MotionEvent.ACTION_HOVER_EXIT) setHovered(false);
            invalidate();
            return true;
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            if (isPressed() || isHovered()) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(isPressed() ? 90 : 55, 255, 255, 255));
                c.drawCircle(cx, cy, Math.min(w, h) / 2f, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(w * 0.07f);
            p.setColor(Color.WHITE);
            float s = w * 0.17f;
            Path path = new Path();
            switch (tool) {
                case BACK: path.moveTo(cx + s * 0.4f, cy - s); path.lineTo(cx - s * 0.6f, cy); path.lineTo(cx + s * 0.4f, cy + s); break;
                case FORWARD: path.moveTo(cx - s * 0.4f, cy - s); path.lineTo(cx + s * 0.6f, cy); path.lineTo(cx - s * 0.4f, cy + s); break;
                case RELOAD:
                    c.drawArc(new RectF(cx - s, cy - s, cx + s, cy + s), -60, 300, false, p);
                    path.moveTo(cx + s * 0.5f, cy - s * 1.25f); path.lineTo(cx + s * 0.62f, cy - s * 0.8f); path.lineTo(cx + s * 0.12f, cy - s * 0.62f);
                    break;
                case HOME:
                    path.moveTo(cx - s * 1.1f, cy - s * 0.05f); path.lineTo(cx, cy - s * 1.05f); path.lineTo(cx + s * 1.1f, cy - s * 0.05f);
                    path.moveTo(cx - s * 0.75f, cy - s * 0.35f); path.lineTo(cx - s * 0.75f, cy + s); path.lineTo(cx + s * 0.75f, cy + s); path.lineTo(cx + s * 0.75f, cy - s * 0.35f);
                    break;
                case DESKTOP:
                    c.drawRoundRect(new RectF(cx - s * 1.15f, cy - s * 0.85f, cx + s * 1.15f, cy + s * 0.5f), s * 0.2f, s * 0.2f, p);
                    path.moveTo(cx - s * 0.5f, cy + s * 1.05f); path.lineTo(cx + s * 0.5f, cy + s * 1.05f);
                    break;
            }
            c.drawPath(path, p);
        }
    }

    /** sfondo capsula semitrasparente per la barra degli indirizzi */
    static final class Capsule extends android.graphics.drawable.Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float sw;
        Capsule(Context c) {
            sw = Ui.dp(c, 1);
            fill.setColor(Color.argb(55, 0, 0, 0));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(sw);
            stroke.setColor(Color.argb(70, 255, 255, 255));
        }
        @Override public void draw(Canvas c) {
            RectF r = new RectF(getBounds());
            r.inset(sw, sw);
            float rad = r.height() / 2f;
            c.drawRoundRect(r, rad, rad, fill);
            c.drawRoundRect(r, rad, rad, stroke);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** barra di caricamento sottile con gradiente */
    static final class ProgressLine extends View {
        private int value = 100;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ProgressLine(Context c) { super(c); }
        void set(int v) { value = v; setVisibility(v >= 100 ? INVISIBLE : VISIBLE); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, w, 0, Color.rgb(90, 160, 255), Color.rgb(170, 110, 255), Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth() * value / 100f, h = getHeight();
            c.drawRoundRect(new RectF(getWidth() * 0.012f, 0, Math.max(h, w), h), h / 2, h / 2, p);
        }
    }

    /** puntatore circolare stile ipados: si rimpicciolisce al clic, sfuma quando inattivo */
    static final class Pointer extends View {
        private float x, y, scale = 1f;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r;
        private final Runnable fade = () -> animate().alpha(0.25f).setDuration(600).start();
        Pointer(Context c) {
            super(c);
            r = Ui.dp(c, 13);
            fill.setColor(Color.argb(150, 120, 120, 128));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 1.6f));
            ring.setColor(Color.argb(235, 255, 255, 255));
            shadow.setColor(Color.argb(70, 0, 0, 0));
            setWillNotDraw(false);
        }
        void move(float nx, float ny) { x = nx; y = ny; invalidate(); }
        void wake() {
            removeCallbacks(fade);
            animate().cancel();
            setAlpha(1f);
            postDelayed(fade, 4000);
        }
        void press(boolean down) {
            wake();
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(scale, down ? 0.72f : 1f);
            a.setDuration(down ? 90 : 160);
            a.setInterpolator(Ui.EASE);
            a.addUpdateListener(v -> { scale = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }
        @Override protected void onDraw(Canvas c) {
            float rr = r * scale;
            c.drawCircle(x, y + Ui.dp(getContext(), 1.5f), rr + Ui.dp(getContext(), 1), shadow);
            c.drawCircle(x, y, rr, fill);
            c.drawCircle(x, y, rr, ring);
        }
    }
}
