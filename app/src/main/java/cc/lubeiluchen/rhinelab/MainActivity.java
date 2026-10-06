package cc.lubeiluchen.rhinelab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
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
 * pause). Version 1 ships those callbacks as defaults, so the page runs with
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
        Trace.init(this);
        Trace.log("onCreate begin, savedInstanceState=" + (savedInstanceState != null));
        try {
            build(savedInstanceState);
            Trace.log("onCreate end");
        } catch (Throwable error) {
            Trace.crash(Thread.currentThread(), error);
            showFailure(error);
        }
    }

    private void build(Bundle savedInstanceState) {
        // getWindow().getInsetsController() walks PhoneWindow.mDecor, which only
        // exists once the decor is installed -- i.e. after setContentView().
        getWindow().setBackgroundDrawable(new ColorDrawable(PAPER));
        getWindow().setDecorFitsSystemWindows(false);

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
        Trace.log("webview settings applied");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        Trace.log("asset loader built");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Trace.log("console " + message.messageLevel() + " "
                        + message.sourceId() + ":" + message.lineNumber() + " " + message.message());
                return true;
            }
        });

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
                } catch (Exception error) {
                    Trace.log("external link failed: " + url + " " + error);
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Trace.log("page finished " + url);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Trace.log("error " + errorCode + " " + description + " " + failingUrl);
            }
        });
        Trace.log("client set");

        applyInsets();
        setContentView(web);
        Trace.log("content view set");

        applyImmersiveMode();
        Trace.log("immersive applied");

        if (savedInstanceState == null) {
            Trace.log("loadUrl " + START_URL);
            web.loadUrl(START_URL);
        } else {
            Trace.log("restoreState");
            web.restoreState(savedInstanceState);
        }
    }

    /** Must run after setContentView: the insets controller needs the decor view. */
    private void applyImmersiveMode() {
        Window window = getWindow();
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

    /** Last resort: put the failure where it can actually be read on the device. */
    private void showFailure(Throwable error) {
        java.io.StringWriter buffer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(buffer));
        android.widget.TextView text = new android.widget.TextView(this);
        text.setText("启动失败 / START FAILED\n\n" + buffer
                + "\n完整记录：Android/media/" + getPackageName() + "/crash.txt");
        text.setTextColor(Color.parseColor("#7a2020"));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        text.setTypeface(Typeface.MONOSPACE);
        text.setBackgroundColor(PAPER);
        text.setPadding(24, 64, 24, 24);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(PAPER);
        scroll.addView(text);
        setContentView(scroll);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
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
            Trace.log("back handled=" + handled + " value=" + value);
            if (!handled) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Trace.log("onPause");
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Trace.log("onResume");
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        Trace.log("onDestroy");
        if (web != null) {
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
