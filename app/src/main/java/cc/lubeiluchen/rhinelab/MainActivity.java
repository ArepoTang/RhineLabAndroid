package cc.lubeiluchen.rhinelab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

/**
 * Minimal native shell around the Rhine Lab wallpaper build.
 *
 * The web layer keeps every pixel of UI; the host only supplies what Wallpaper
 * Engine used to: a non-file origin, immersive system bars, a back gesture and
 * a second face for its five host callbacks (properties / audio / media / fps /
 * pause). Versions 1 ships the callbacks as defaults, so the page runs with
 * `rhineWallpaperHost = { properties: {}, fps: 30, paused: false }`.
 */
public class MainActivity extends Activity {

    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String START_URL = ORIGIN + "/assets/www/index.html";
    private static final int PAPER = 0xFFE8E5E1;

    /**
     * Lets the page consume the back gesture when it owns something dismissable.
     * Returns the name of what it closed, or "" when the app should exit.
     */
    private static final String BACK_JS =
        "(function(){try{"
        + "var v=document.querySelector('.model-viewer');"
        + "if(v&&!v.hidden){var b=v.querySelector('[data-viewer=\"close\"]');if(b){b.click();return 'viewer';}}"
        + "var mr=document.getElementById('modal-root');"
        + "if(mr&&mr.firstElementChild){var c=mr.querySelector('[data-action=\"close-modal\"]');"
        + "if(c){c.click();return 'modal';}}"
        + "var st=document.getElementById('stage');"
        + "if(st&&st.getAttribute('data-mode')==='detail'){"
        + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return 'detail';}"
        + "}catch(e){}return '';})()";

    private WebView web;
    private int lastBottomInset = -1;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveMode();

        web = new WebView(this);
        web.setBackgroundColor(PAPER);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // The terminal starts its own ambience; there is no gesture to unlock audio with.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Nothing is read from the filesystem or a content provider: assets only.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url.getScheme() != null && "appassets.androidplatform.net".equals(url.getHost())) {
                    return false;
                }
                // Archive lore links belong in the browser, not inside the terminal.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        applyInsets();
        setContentView(web);

        if (savedInstanceState == null) {
            web.loadUrl(START_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    private void applyImmersiveMode() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(PAPER));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.systemBars());
        }
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        window.setAttributes(attributes);
    }

    /** Edge-to-edge content, but the keyboard still has to push the layout up. */
    private void applyInsets() {
        web.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            if (bottom != lastBottomInset) {
                lastBottomInset = bottom;
                view.setPadding(0, 0, 0, bottom);
            }
            return insets;
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(BACK_JS, value -> {
            // "" means the page owns nothing dismissable right now, so leave.
            boolean handled = value != null && value.length() > 2 && !"null".equals(value);
            if (!handled) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
