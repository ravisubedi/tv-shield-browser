package com.tvshield.browser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.InputType;
import android.text.method.KeyListener;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/home.html";
    private WebView webView;
    private EditText addressBar;
    private ProgressBar progress;
    private Button overflowButton;
    private Button bookmarkButton;
    private View toolbar;
    private View mouseCursor;
    private AdBlocker adBlocker;
    private boolean shieldEnabled = true;
    private boolean fullScreen = false;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customVideoCallback;
    private float cursorX;
    private float cursorY;
    private GestureDetector mouseGestureDetector;
    private int pageZoom;
    private SharedPreferences browserData;
    private KeyListener addressKeyListener;
    private boolean addressEditing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        adBlocker = new AdBlocker(this);
        webView = findViewById(R.id.webView);
        addressBar = findViewById(R.id.addressBar);
        addressKeyListener = addressBar.getKeyListener();
        addressBar.setKeyListener(null);
        progress = findViewById(R.id.progress);
        overflowButton = findViewById(R.id.overflowButton);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        toolbar = findViewById(R.id.toolbar);
        mouseCursor = findViewById(R.id.mouseCursor);
        browserData = getSharedPreferences("browser_data", MODE_PRIVATE);
        pageZoom = getPreferences(MODE_PRIVATE).getInt("page_zoom", 90);
        updateZoomLabel();

        mouseGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return false; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (customVideoView != null) hideCustomVideo();
                else toggleFullScreen();
                return true;
            }
        });

        configureWebView();
        webView.addJavascriptInterface(new BrowserBridge(), "BrowserBridge");
        webView.setOnTouchListener((view, event) -> {
            mouseGestureDetector.onTouchEvent(event);
            if ((event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) && event.getY() <= 12) {
                revealToolbar();
            }
            return false;
        });
        webView.setOnGenericMotionListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                float wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                scrollPage((int) (-wheel * 420));
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE && event.getY() <= 12) {
                revealToolbar();
            }
            return false;
        });
        findViewById(R.id.backButton).setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.forwardButton).setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.homeButton).setOnClickListener(v -> webView.loadUrl(HOME));
        findViewById(R.id.goButton).setOnClickListener(v -> navigate());
        findViewById(R.id.clearButton).setOnClickListener(v -> {
            beginAddressEditing();
            addressBar.setText("");
        });
        overflowButton.setOnClickListener(v -> showBrowserMenu());
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
        addressBar.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { finishAddressEditing(); navigate(); return true; }
            return false;
        });
        addressBar.setOnClickListener(v -> beginAddressEditing());
        addressBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) finishAddressEditing();
        });
        addressBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (!addressEditing && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                beginAddressEditing();
                return true;
            }
            if (!addressEditing && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
                View next = addressBar.focusSearch(direction);
                if (next != null) next.requestFocus();
                return true;
            }
            return false;
        });
        if (state == null) webView.loadUrl(HOME); else webView.restoreState(state);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(90);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        // Keep WebView's real Android/codec identity. Pretending to be desktop
        // Chrome can make video sites select streams the TV cannot decode well.
        settings.setUserAgentString(settings.getUserAgentString() + " TVShield/0.2.2");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customVideoCallback = callback;
                applyDocumentZoom(100);
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                findViewById(android.R.id.content).setVisibility(View.GONE);
                applyImmersiveMode(true);
                view.requestFocus();
            }
            @Override public void onHideCustomView() {
                hideCustomVideo();
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog,
                                                     boolean isUserGesture, Message resultMsg) {
                // This browser has no tab model. Refuse popup and pop-under
                // windows instead of allowing ad scripts to hijack navigation.
                if (shieldEnabled) recordBlock(AdBlocker.BlockType.AD);
                return false;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!shieldEnabled || !request.isForMainFrame()) return false;
                Uri destination = request.getUrl();
                String scheme = destination.getScheme();
                if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return true;
                AdBlocker.BlockType destinationType = adBlocker.getBlockType(destination);
                if (destinationType != AdBlocker.BlockType.NONE) {
                    recordBlock(destinationType);
                    Toast.makeText(MainActivity.this, "Shield blocked an ad link", Toast.LENGTH_SHORT).show();
                    return true;
                }
                Uri current = Uri.parse(view.getUrl() == null ? "" : view.getUrl());
                if (!isCrossSite(current, destination) || request.hasGesture()) return false;
                recordBlock(AdBlocker.BlockType.AD);
                Toast.makeText(MainActivity.this, "Shield blocked an automatic redirect", Toast.LENGTH_SHORT).show();
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Video segments are already selected by the player. Never make
                // them wait on cosmetic/ad-filter logic.
                if (isMediaDeliveryRequest(request.getUrl())) return null;
                AdBlocker.BlockType blockType = shieldEnabled
                        ? adBlocker.getBlockType(request.getUrl()) : AdBlocker.BlockType.NONE;
                if (blockType != AdBlocker.BlockType.NONE) {
                    recordBlock(blockType);
                    return new WebResourceResponse("text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!addressEditing) addressBar.setText(HOME.equals(url) ? "" : url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!addressEditing) addressBar.setText(HOME.equals(url) ? "" : url);
                if (!HOME.equals(url)) recordVisit(url, view.getTitle());
                updateBookmarkButton(url);
                if (HOME.equals(url)) renderHomeData();
                applyResolutionViewport(url);
                applyUserZoom();
                installTvFocusStyle();
                installShieldHelpers();
                installYouTubeHelpers(url);
            }
        });
    }

    private void beginAddressEditing() {
        if (addressEditing) return;
        addressEditing = true;
        addressBar.setKeyListener(addressKeyListener);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.requestFocus();
        addressBar.selectAll();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT);
    }

    private void finishAddressEditing() {
        if (!addressEditing) return;
        addressEditing = false;
        addressBar.setKeyListener(null);
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private JSONArray readBookmarks() {
        try { return new JSONArray(browserData.getString("bookmarks", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void toggleBookmark() {
        String url = webView.getUrl();
        if (url == null || url.startsWith("file:") || url.startsWith("about:")) return;
        JSONArray source = readBookmarks();
        JSONArray result = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && url.equals(item.optString("url"))) removed = true;
            else if (item != null) result.put(item);
        }
        if (!removed) {
            JSONObject item = new JSONObject();
            try { item.put("url", url); item.put("title", safeTitle(webView.getTitle(), url)); }
            catch (Exception ignored) { }
            result.put(item);
        }
        browserData.edit().putString("bookmarks", result.toString()).apply();
        updateBookmarkButton(url);
        Toast.makeText(this, removed ? R.string.bookmark_removed : R.string.bookmark_added, Toast.LENGTH_SHORT).show();
    }

    private void removeBookmark(String url) {
        JSONArray source = readBookmarks();
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && !url.equals(item.optString("url"))) result.put(item);
        }
        browserData.edit().putString("bookmarks", result.toString()).apply();
        renderHomeData();
    }

    private void updateBookmarkButton(String url) {
        boolean saved = false;
        JSONArray bookmarks = readBookmarks();
        for (int i = 0; i < bookmarks.length(); i++) {
            JSONObject item = bookmarks.optJSONObject(i);
            if (item != null && url != null && url.equals(item.optString("url"))) { saved = true; break; }
        }
        bookmarkButton.setText(saved ? "★" : "☆");
        bookmarkButton.setContentDescription(getString(saved ? R.string.remove_bookmark : R.string.add_bookmark));
    }

    private void recordVisit(String url, String title) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return;
            JSONObject visits = new JSONObject(browserData.getString("visits", "{}"));
            JSONObject item = visits.optJSONObject(host);
            if (item == null) item = new JSONObject();
            item.put("url", url);
            item.put("title", safeTitle(title, url));
            item.put("count", item.optInt("count", 0) + 1);
            item.put("last", System.currentTimeMillis());
            visits.put(host, item);
            browserData.edit().putString("visits", visits.toString()).apply();
        } catch (Exception ignored) { }
    }

    private JSONArray mostVisited() {
        List<JSONObject> items = new ArrayList<>();
        try {
            JSONObject visits = new JSONObject(browserData.getString("visits", "{}"));
            JSONArray names = visits.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                JSONObject item = visits.optJSONObject(names.optString(i));
                if (item != null) items.add(item);
            }
        } catch (Exception ignored) { }
        Collections.sort(items, (a, b) -> {
            int byCount = Integer.compare(b.optInt("count"), a.optInt("count"));
            return byCount != 0 ? byCount : Long.compare(b.optLong("last"), a.optLong("last"));
        });
        JSONArray result = new JSONArray();
        for (int i = 0; i < Math.min(8, items.size()); i++) result.put(items.get(i));
        return result;
    }

    private String safeTitle(String title, String url) {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        String host = Uri.parse(url).getHost();
        return host == null ? url : host;
    }

    private void renderHomeData() {
        String stats = "{ads:" + browserData.getLong("blocked_ads", 0)
                + ",trackers:" + browserData.getLong("blocked_trackers", 0) + "}";
        String script = "renderBrowserData(" + readBookmarks().toString() + ","
                + mostVisited().toString() + "," + stats + ")";
        webView.evaluateJavascript(script, null);
    }

    private synchronized void recordBlock(AdBlocker.BlockType type) {
        if (type == AdBlocker.BlockType.NONE) return;
        String key = type == AdBlocker.BlockType.TRACKER ? "blocked_trackers" : "blocked_ads";
        browserData.edit().putLong(key, browserData.getLong(key, 0) + 1).apply();
    }

    private final class BrowserBridge {
        @JavascriptInterface public void removeBookmark(String url) {
            runOnUiThread(() -> MainActivity.this.removeBookmark(url));
        }
    }

    private boolean isMediaDeliveryRequest(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.US);
        return host.equals("googlevideo.com")
                || host.endsWith(".googlevideo.com")
                || host.equals("ytimg.com")
                || host.endsWith(".ytimg.com")
                || host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("vimeo.com")
                || host.endsWith(".vimeo.com")
                || host.equals("vimeocdn.com")
                || host.endsWith(".vimeocdn.com");
    }

    private boolean isCrossSite(Uri current, Uri destination) {
        String currentHost = current.getHost();
        String destinationHost = destination.getHost();
        if (currentHost == null || destinationHost == null) return false;
        currentHost = currentHost.toLowerCase(java.util.Locale.US);
        destinationHost = destinationHost.toLowerCase(java.util.Locale.US);
        return !(currentHost.equals(destinationHost)
                || currentHost.endsWith("." + destinationHost)
                || destinationHost.endsWith("." + currentHost));
    }

    private void applyResolutionViewport(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("youtube.com") || host.endsWith(".youtube.com"))) return;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int viewportWidth = Math.max(1280, metrics.widthPixels);
        String script = "(function(){var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}" +
                "m.content='width=" + viewportWidth + ",initial-scale=1';" +
                "document.documentElement.style.minWidth='" + viewportWidth + "px'})()";
        webView.evaluateJavascript(script, null);
    }

    private void installYouTubeHelpers(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("youtube.com") || host.endsWith(".youtube.com"))) return;
        String script = "(function(){if(window.__tvShieldYT)return;window.__tvShieldYT=true;" +
                "var style=document.createElement('style');style.id='__tvshield_youtube_layout';" +
                "style.textContent='ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer," +
                "ytd-promoted-video-renderer{display:none!important}';document.documentElement.appendChild(style);" +
                "var clean=function(root){" +
                "root=root&&root.querySelectorAll?root:document;" +
                "document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button').forEach(function(b){b.click()});" +
                "root.querySelectorAll('.ytp-ad-overlay-container,.ytp-ad-module,ytd-display-ad-renderer," +
                "ytd-promoted-sparkles-web-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer," +
                "ytd-promoted-video-renderer').forEach(function(e){e.style.display='none'});" +
                "var p=document.querySelector('.html5-video-player.ad-showing');var v=document.querySelector('video');" +
                "if(p&&v&&isFinite(v.duration)&&v.duration>0){v.muted=true;v.currentTime=v.duration}" +
                "};clean(document);var cleanTimer=0;" +
                "new MutationObserver(function(){if(cleanTimer)return;cleanTimer=setTimeout(function(){" +
                "cleanTimer=0;clean(document)},800)}).observe(document.documentElement,{childList:true,subtree:true});" +
                "document.addEventListener('loadedmetadata',function(){clean(document)},true)})()";
        webView.evaluateJavascript(script, null);
    }

    private void showZoomMenu() {
        PopupMenu menu = new PopupMenu(this, overflowButton);
        int[] levels = {60, 70, 80, 90, 100, 110, 125};
        for (int level : levels) menu.getMenu().add(0, level, level, level + "%");
        menu.setOnMenuItemClickListener(item -> {
            pageZoom = item.getItemId();
            getPreferences(MODE_PRIVATE).edit().putInt("page_zoom", pageZoom).apply();
            updateZoomLabel();
            applyUserZoom();
            webView.requestFocus();
            return true;
        });
        menu.show();
    }

    private void updateZoomLabel() {
        overflowButton.setContentDescription(getString(R.string.browser_menu) + ", zoom " + pageZoom + "%");
    }

    private void showBrowserMenu() {
        PopupMenu menu = new PopupMenu(this, overflowButton);
        menu.getMenu().add(0, 1, 0, shieldEnabled ? "Shield ON" : "Shield OFF");
        menu.getMenu().add(0, 2, 1, "Zoom: " + pageZoom + "%");
        menu.getMenu().add(0, 3, 2, fullScreen ? "Exit Full Screen" : "Full Screen");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) toggleShield();
            else if (item.getItemId() == 2) showZoomMenu();
            else if (item.getItemId() == 3) toggleFullScreen();
            return true;
        });
        menu.show();
    }

    private void applyUserZoom() {
        String currentUrl = webView.getUrl();
        boolean isYouTubeVideo = currentUrl != null &&
                (currentUrl.contains("youtube.com/watch") || currentUrl.contains("youtube.com/shorts/"));
        applyDocumentZoom(isYouTubeVideo ? 100 : pageZoom);
    }

    private void applyDocumentZoom(int percent) {
        double scale = percent / 100.0;
        double width = 10000.0 / percent;
        String script = "(function(){document.documentElement.style.zoom='" + scale + "';" +
                "document.documentElement.style.width='" + width + "%';})()";
        webView.evaluateJavascript(script, null);
    }

    private void applyImmersiveMode(boolean enabled) {
        getWindow().getDecorView().setSystemUiVisibility(enabled
                ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void hideCustomVideo() {
        if (customVideoView == null) return;
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        decor.removeView(customVideoView);
        customVideoView = null;
        findViewById(android.R.id.content).setVisibility(View.VISIBLE);
        if (customVideoCallback != null) customVideoCallback.onCustomViewHidden();
        customVideoCallback = null;
        applyImmersiveMode(fullScreen);
        applyUserZoom();
        webView.requestFocus();
    }

    private void installTvFocusStyle() {
        webView.evaluateJavascript("(function(){if(document.getElementById('__tvshield_style'))return;" +
                "var s=document.createElement('style');s.id='__tvshield_style';" +
                "s.textContent='a:focus,button:focus,input:focus,select:focus,textarea:focus,[role=button]:focus,[tabindex]:focus{" +
                "outline:5px solid #FB542B!important;outline-offset:3px!important;box-shadow:0 0 0 3px white!important}';" +
                "document.documentElement.appendChild(s)})()", null);
    }

    private void installShieldHelpers() {
        if (!shieldEnabled) return;
        String script = "(function(){if(window.__tvShieldPage)return;window.__tvShieldPage=true;" +
                "try{window.open=function(){return null}}catch(e){};" +
                "var clean=function(root){root=root&&root.querySelectorAll?root:document;" +
                "root.querySelectorAll('[class*=\\\"popup\\\" i],[class*=\\\"popunder\\\" i]," +
                "[id*=\\\"popup\\\" i],[id*=\\\"popunder\\\" i]," +
                "iframe[src*=\\\"teleibelock\\\"],script[src*=\\\"teleibelock\\\"]').forEach(function(e){e.remove()});" +
                "root.querySelectorAll('a[target=\\\"_blank\\\"]').forEach(function(a){a.removeAttribute('target')})};" +
                "clean(document);var timer=0;new MutationObserver(function(){if(timer)return;timer=setTimeout(function(){" +
                "timer=0;clean(document)},250)}).observe(document.documentElement,{childList:true,subtree:true})})()";
        webView.evaluateJavascript(script, null);
    }

    private void movePageFocus(boolean forward) {
        String direction = forward ? "1" : "-1";
        String script = "(function(){var q='a[href],button,input,select,textarea,[role=button],[tabindex]:not([tabindex=\"-1\"])';" +
                "var e=Array.from(document.querySelectorAll(q)).filter(function(x){var r=x.getBoundingClientRect();" +
                "return !x.disabled&&r.width>0&&r.height>0&&getComputedStyle(x).visibility!==\"hidden\"});" +
                "if(!e.length)return;var i=e.indexOf(document.activeElement);i=(i+" + direction + "+e.length)%e.length;" +
                "e[i].focus({preventScroll:true});e[i].scrollIntoView({block:'center',inline:'center',behavior:'auto'})})()";
        webView.evaluateJavascript(script, null);
    }

    private void activatePageFocus() {
        webView.evaluateJavascript("(function(){var a=document.activeElement;" +
                "if(a&&a!==document.body&&a!==document.documentElement){a.click();return true}return false})()",
                value -> { if (!"true".equals(value)) movePageFocus(true); });
    }

    private void initializeCursor() {
        if (mouseCursor.getVisibility() == View.VISIBLE) return;
        cursorX = Math.max(0, webView.getWidth() / 2f - mouseCursor.getWidth() / 2f);
        cursorY = Math.max(0, webView.getHeight() / 2f - mouseCursor.getHeight() / 2f);
        mouseCursor.setX(cursorX);
        mouseCursor.setY(cursorY);
        mouseCursor.setVisibility(View.VISIBLE);
    }

    private void moveCursor(float dx, float dy) {
        initializeCursor();
        float maxX = Math.max(0, webView.getWidth() - mouseCursor.getWidth());
        float maxY = Math.max(0, webView.getHeight() - mouseCursor.getHeight());
        boolean atTop = cursorY <= 0;
        boolean atBottom = cursorY >= maxY;
        cursorX = Math.max(0, Math.min(maxX, cursorX + dx));
        cursorY = Math.max(0, Math.min(maxY, cursorY + dy));
        mouseCursor.animate().x(cursorX).y(cursorY).setDuration(45).start();
        if (dy > 0 && atBottom) scrollPage(Math.max(280, webView.getHeight() / 3));
        else if (dy < 0 && atTop) scrollUpOrRevealToolbar();
    }

    private void scrollPage(int pixels) {
        // Native scrolling avoids stacking JavaScript smooth-scroll animations
        // when a mouse wheel or remote sends events in quick succession.
        webView.scrollBy(0, pixels);
    }

    private void scrollUpOrRevealToolbar() {
        webView.evaluateJavascript("String(Math.max(window.scrollY,document.documentElement.scrollTop||0))", value -> {
            try {
                String clean = value == null ? "0" : value.replace("\"", "");
                if (Double.parseDouble(clean) > 1) scrollPage(-Math.max(280, webView.getHeight() / 3));
                else enterToolbar();
            } catch (NumberFormatException ignored) {
                enterToolbar();
            }
        });
    }

    private void enterToolbar() {
        revealToolbar();
        mouseCursor.setVisibility(View.GONE);
        addressBar.requestFocus();
    }

    private void revealToolbar() {
        if (customVideoView != null) return;
        if (fullScreen) {
            fullScreen = false;
            toolbar.setVisibility(View.VISIBLE);
            applyImmersiveMode(false);
        }
    }

    private void clickCursor() {
        initializeCursor();
        long now = android.os.SystemClock.uptimeMillis();
        float x = cursorX + mouseCursor.getWidth() / 2f;
        float y = cursorY + mouseCursor.getHeight() / 2f;
        webView.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        webView.dispatchTouchEvent(MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0));
    }

    private void toggleFullScreen() {
        fullScreen = !fullScreen;
        toolbar.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
        applyImmersiveMode(fullScreen);
        webView.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                toggleFullScreen();
                return true;
            }
            if (webView.hasFocus()) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        moveCursor(36, 0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        moveCursor(-36, 0);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        moveCursor(0, 36);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (cursorY <= 0) {
                            scrollUpOrRevealToolbar();
                            return true;
                        }
                        moveCursor(0, -36);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        clickCursor();
                        return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void navigate() {
        String input = addressBar.getText().toString().trim();
        if (input.isEmpty()) return;
        Uri parsed = Uri.parse(input);
        if (parsed.getScheme() == null) {
            if (input.contains(".") && !input.contains(" ")) input = "https://" + input;
            else input = "https://search.brave.com/search?q=" + Uri.encode(input);
        } else if ("http".equalsIgnoreCase(parsed.getScheme())) {
            input = "https" + input.substring(4);
        }
        webView.loadUrl(input);
        webView.requestFocus();
    }

    private void toggleShield() {
        shieldEnabled = !shieldEnabled;
        updateZoomLabel();
        webView.reload();
    }

    @Override public void onBackPressed() {
        if (customVideoView != null) hideCustomVideo();
        else if (fullScreen) toggleFullScreen();
        else if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        webView.saveState(out);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        hideCustomVideo();
        webView.destroy();
        super.onDestroy();
    }
}
