package com.github.catvod.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.spider.Init;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewUtil {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static View currentOverlay;
    private static WebView currentWebView;
    private static Activity currentActivity;

    private static OnBackInvokedCallback backCallback;

    private static CountDownLatch verificationLatch;
    private static AtomicReference<Map<String, String>> verificationResult;

    private static boolean finishActivityOnClose;

    private static WebView createWebView(Activity activity) {
        WebView webView = new WebView(activity);

        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        return webView;
    }

    private static void destroyWebView(WebView wv) {
        if (wv == null) return;

        try {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.clearHistory();
            wv.removeAllViews();
            wv.destroy();
        } catch (Exception ignored) {
        }
    }

    public static String getHtml(String url) {
        return getHtml(url, null);
    }

    public static String getHtml(
            String url,
            Map<String, String> headers
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> html =
                new AtomicReference<>("");

        MAIN.post(() -> {
            WebView webView;

            try {
                webView = createWebView(getActivity());
            } catch (Exception e) {
                html.set(String.valueOf(e));
                latch.countDown();
                return;
            }

            addView(
                    webView,
                    new ViewGroup.LayoutParams(0, 0)
            );

            final AtomicBoolean done =
                    new AtomicBoolean(false);

            webView.setWebViewClient(
                    new WebViewClient() {

                        private void finish(
                                WebView view
                        ) {
                            if (done.getAndSet(true)) {
                                return;
                            }

                            loadUrl(
                                    view,
                                    "document.documentElement.outerHTML",
                                    value -> {
                                        try {
                                            html.set(
                                                    value == null
                                                            ? ""
                                                            : value
                                            );
                                        } finally {
                                            try {
                                                destroyWebView(view);
                                                removeView(view);
                                            } catch (Exception ignored) {
                                            }

                                            latch.countDown();
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onPageFinished(
                                WebView view,
                                String url
                        ) {
                            finish(view);
                        }

                        @Override
                        public void onReceivedError(
                                WebView view,
                                int errorCode,
                                String description,
                                String failingUrl
                        ) {
                            finish(view);
                        }
                    }
            );

            applyHeaders(
                    webView,
                    url,
                    headers
            );

            if (headers != null
                    && !headers.isEmpty()) {

                webView.loadUrl(
                        url,
                        headers
                );

            } else {
                webView.loadUrl(url);
            }

            MAIN.postDelayed(() -> {
                if (done.getAndSet(true)) {
                    return;
                }

                try {
                    destroyWebView(webView);
                    removeView(webView);
                } catch (Exception ignored) {
                }

                latch.countDown();

            }, 20000);
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return html.get();
    }

    public static void loadUrl(
            WebView webView,
            String script
    ) {
        loadUrl(
                webView,
                script,
                null
        );
    }

    public static void loadUrl(
            WebView webView,
            String script,
            ValueCallback<String> callback
    ) {
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.KITKAT) {

            webView.evaluateJavascript(
                    script,
                    callback
            );

        } else {
            webView.loadUrl(
                    "javascript:" + script
            );
        }
    }

    public static void addView(
            View view,
            ViewGroup.LayoutParams params
    ) {
        try {
            ViewGroup group =
                    getActivity()
                            .getWindow()
                            .getDecorView()
                            .findViewById(
                                    android.R.id.content
                            );

            group.addView(
                    view,
                    params
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void removeView(View view) {
        try {
            ViewGroup group =
                    getActivity()
                            .getWindow()
                            .getDecorView()
                            .findViewById(
                                    android.R.id.content
                            );

            group.removeView(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadWebView(
            String url,
            WebViewClient client
    ) {
        Init.post(() -> {
            WebView webView;

            try {
                webView =
                        createWebView(getActivity());

            } catch (Exception e) {
                SpiderDebug.log(
                        "loadWebView error: " + e
                );
                return;
            }

            addView(
                    webView,
                    new ViewGroup.LayoutParams(
                            0,
                            0
                    )
            );

            webView.setWebViewClient(client);
            webView.loadUrl(url);
        });
    }

    private static FrameLayout createOverlayContainer(
            Activity activity
    ) {
        FrameLayout container =
                new FrameLayout(activity);

        container.setFitsSystemWindows(true);

        container.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    int top = 0;

                    if (Build.VERSION.SDK_INT
                            >= Build.VERSION_CODES.R) {

                        top = insets
                                .getInsets(
                                        WindowInsets.Type.statusBars()
                                )
                                .top;

                    } else {
                        top =
                                insets.getSystemWindowInsetTop();
                    }

                    v.setPadding(
                            0,
                            top,
                            0,
                            0
                    );

                    return insets;
                }
        );

        container.setBackgroundColor(Color.BLACK);

        container.setFocusableInTouchMode(true);
        container.requestFocus();

        // Android 12 及以下的返回键处理
        container.setOnKeyListener(
                (v, keyCode, event) -> {

                    if (keyCode
                            == KeyEvent.KEYCODE_BACK
                            && event.getAction()
                            == KeyEvent.ACTION_UP) {

                        closeWebOverlay();
                        return true;
                    }

                    return false;
                }
        );

        return container;
    }

    private static TextView createBackButton(
            Activity activity
    ) {
        TextView backBtn =
                new TextView(activity);

        backBtn.setText("←");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(22);
        backBtn.setPadding(
                40,
                20,
                40,
                20
        );

        backBtn.setBackgroundColor(
                Color.argb(
                        80,
                        0,
                        0,
                        0
                )
        );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        params.gravity =
                Gravity.TOP | Gravity.START;

        params.leftMargin = 12;
        params.topMargin = 12;

        backBtn.setLayoutParams(params);

        backBtn.setOnClickListener(
                v -> closeWebOverlay()
        );

        return backBtn;
    }

    public static void openHtml(String url) {
        openHtml(
                url,
                null,
                false
        );
    }

    public static void openHtml(
            String url,
            Map<String, String> headers
    ) {
        openHtml(
                url,
                headers,
                false
        );
    }

    public static void openHtml(
            String url,
            Map<String, String> headers,
            boolean finishActivity
    ) {
        MAIN.post(() -> {
            try {
                Activity activity =
                        getActivity();

                if (activity == null) {
                    return;
                }

                finishActivityOnClose =
                        finishActivity;

                currentActivity =
                        activity;

                // Android 13+ 使用系统返回回调
                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.TIRAMISU) {

                    backCallback =
                            WebViewUtil::closeWebOverlay;

                    activity
                            .getOnBackInvokedDispatcher()
                            .registerOnBackInvokedCallback(
                                    OnBackInvokedDispatcher
                                            .PRIORITY_OVERLAY,
                                    backCallback
                            );
                }

                FrameLayout container =
                        createOverlayContainer(
                                activity
                        );

                WebView webView =
                        createWebView(
                                activity
                        );

                currentOverlay =
                        container;

                currentWebView =
                        webView;

                applyHeaders(
                        webView,
                        url,
                        headers
                );

                FrameLayout.LayoutParams webParams =
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        );

                container.addView(
                        webView,
                        webParams
                );

                container.addView(
                        createBackButton(activity)
                );

                ViewGroup root =
                        activity
                                .getWindow()
                                .getDecorView()
                                .findViewById(
                                        android.R.id.content
                                );

                root.addView(
                        container,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                );

                if (headers != null
                        && !headers.isEmpty()) {

                    webView.loadUrl(
                            url,
                            headers
                    );

                } else {
                    webView.loadUrl(url);
                }

            } catch (Exception e) {
                SpiderDebug.log(
                        "WebOverlay error: " + e
                );
            }
        });
    }

    private static void applyHeaders(
            WebView webView,
            String url,
            Map<String, String> headers
    ) {
        if (headers == null
                || headers.isEmpty()) {
            return;
        }

        try {
            String ua =
                    headers.get("User-Agent");

            if (ua != null
                    && !ua.isEmpty()) {

                webView
                        .getSettings()
                        .setUserAgentString(ua);
            }

            String cookie =
                    headers.get("Cookie");

            if (cookie != null
                    && !cookie.isEmpty()) {

                try {
                    String domain = "";

                    try {
                        URL u =
                                new URL(url);

                        domain =
                                u.getProtocol()
                                        + "://"
                                        + u.getHost();

                    } catch (Exception ignored) {
                    }

                    CookieManager cm =
                            CookieManager.getInstance();

                    cm.setAcceptCookie(true);
                    cm.setCookie(
                            domain,
                            cookie
                    );
                    cm.flush();

                } catch (Exception ignored) {
                }
            }

        } catch (Exception ignored) {
        }
    }

    public static void closeWebOverlay() {
        MAIN.post(() -> {
            try {
                Activity activity =
                        currentActivity;

                if (activity == null) {
                    return;
                }

                if (currentWebView != null) {

                    if (verificationLatch != null
                            && verificationResult != null) {

                        Map<String, String> headers =
                                new java.util.HashMap<>();

                        try {
                            CookieManager cookieManager =
                                    CookieManager.getInstance();

                            cookieManager.setAcceptCookie(
                                    true
                            );

                            cookieManager.flush();

                            String currentUrl =
                                    currentWebView.getUrl();

                            if (currentUrl != null
                                    && !currentUrl.isEmpty()) {

                                String cookie =
                                        cookieManager
                                                .getCookie(
                                                        currentUrl
                                                );

                                if (cookie != null
                                        && !cookie.isEmpty()) {

                                    headers.put(
                                            "Cookie",
                                            cookie
                                    );
                                }
                            }

                        } catch (Exception ignored) {
                        }

                        try {
                            String userAgent =
                                    currentWebView
                                            .getSettings()
                                            .getUserAgentString();

                            if (userAgent != null
                                    && !userAgent.isEmpty()) {

                                headers.put(
                                        "User-Agent",
                                        userAgent
                                );
                            }

                        } catch (Exception ignored) {
                        }

                        verificationResult.set(
                                headers
                        );

                        verificationLatch.countDown();
                    }

                    destroyWebView(
                            currentWebView
                    );

                    currentWebView = null;
                }

                if (currentOverlay != null) {

                    try {
                        ViewGroup root =
                                activity
                                        .getWindow()
                                        .getDecorView()
                                        .findViewById(
                                                android.R.id.content
                                        );

                        root.removeView(
                                currentOverlay
                        );

                    } catch (Exception ignored) {
                    }

                    currentOverlay = null;
                }

                // Android 13+ 注销系统返回键回调
                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.TIRAMISU
                        && backCallback != null) {

                    activity
                            .getOnBackInvokedDispatcher()
                            .unregisterOnBackInvokedCallback(
                                    backCallback
                            );

                    backCallback = null;
                }

                // 根据创建 WebView 时指定的策略决定
                // 是否同时关闭当前 Activity
                if (finishActivityOnClose) {

                    finishActivityOnClose =
                            false;

                    activity.finish();
                }

                currentActivity = null;

            } catch (Exception ignored) {
            }
        });
    }

    public static Activity getActivity()
            throws Exception {

        Class<?> activityThreadClass =
                Class.forName(
                        "android.app.ActivityThread"
                );

        Object activityThread =
                activityThreadClass
                        .getMethod(
                                "currentActivityThread"
                        )
                        .invoke(null);

        Field activitiesField =
                activityThreadClass
                        .getDeclaredField(
                                "mActivities"
                        );

        activitiesField.setAccessible(true);

        Map<?, ?> activities =
                (Map<?, ?>)
                        activitiesField.get(
                                activityThread
                        );

        for (Object activityRecord
                : activities.values()) {

            Class<?> activityRecordClass =
                    activityRecord.getClass();

            Field pausedField =
                    activityRecordClass
                            .getDeclaredField(
                                    "paused"
                            );

            pausedField.setAccessible(true);

            if (!pausedField.getBoolean(
                    activityRecord
            )) {

                Field activityField =
                        activityRecordClass
                                .getDeclaredField(
                                        "activity"
                                );

                activityField.setAccessible(true);

                Activity activity =
                        (Activity)
                                activityField.get(
                                        activityRecord
                                );

                SpiderDebug.log(
                        activity
                                .getComponentName()
                                .getClassName()
                );

                return activity;
            }
        }

        return null;
    }

    public static Map<String, String>
    waitForVerification(
            String url
    ) {
        CountDownLatch latch =
                new CountDownLatch(1);

        AtomicReference<Map<String, String>>
                result =
                new AtomicReference<>(
                        new java.util.HashMap<>()
                );

        verificationLatch =
                latch;

        verificationResult =
                result;

        // 默认只关闭 WebView，
        // 不关闭当前 Activity
        openHtml(url);

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verificationLatch = null;
        verificationResult = null;

        return result.get();
    }
}