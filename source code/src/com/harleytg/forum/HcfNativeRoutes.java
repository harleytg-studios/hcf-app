package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native URL bridge for HCF-only routes that should open Android UI instead of a forum page.
 *
 * https://forum.harleytg.com/app/settings and the backup-domain equivalent both open the
 * native App Settings screen. The existing hcf-app://settings route remains supported.
 */
public final class HcfNativeRoutes {
    private static final String PRIMARY_HOST = "forum.harleytg.com";
    private static final String BACKUP_HOST = "harleysclan.freeflarum.com";
    private static final String SETTINGS_PATH = "/app/settings";
    private static final String EXTRA_SETTINGS_CONSUMED = "hcf_native_settings_route_consumed";
    private static final long ROUTE_POLL_MS = 300L;
    private static final long HOOK_REFRESH_MS = 1200L;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static long lastHookAt;
    private static boolean pollRunning;

    private HcfNativeRoutes() {}

    static boolean isNativeSettingsUri(Uri uri) {
        if (uri == null) return false;

        String scheme = safeLower(uri.getScheme());
        String host = safeLower(uri.getHost());
        if ("hcf-app".equals(scheme) && "settings".equals(host)) return true;
        if (!"https".equals(scheme)) return false;
        if (!PRIMARY_HOST.equals(host) && !BACKUP_HOST.equals(host)) return false;

        String path = uri.getPath();
        if (path == null) return false;
        String normalized = path.trim().toLowerCase(Locale.US);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return SETTINGS_PATH.equals(normalized);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        resumedActivity = new WeakReference<>(activity);
                        if (activity instanceof HcfMainActivities.MainActivity) {
                            guardMainActivity((HcfMainActivities.MainActivity) activity);
                            startPolling();
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) resumedActivity.clear();
                    }

                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) resumedActivity.clear();
                    }
                });
    }

    private static void startPolling() {
        if (pollRunning) return;
        pollRunning = true;
        MAIN.post(ROUTE_POLL);
    }

    private static final Runnable ROUTE_POLL = new Runnable() {
        @Override public void run() {
            Activity activity = resumedActivity.get();
            if (!(activity instanceof HcfMainActivities.MainActivity)
                    || activity.isFinishing() || activity.isDestroyed()) {
                pollRunning = false;
                return;
            }
            guardMainActivity((HcfMainActivities.MainActivity) activity);
            MAIN.postDelayed(this, ROUTE_POLL_MS);
        }
    };

    private static void guardMainActivity(HcfMainActivities.MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Intent intent = activity.getIntent();
        Uri incoming = intent == null ? null : intent.getData();
        if (isNativeSettingsUri(incoming)
                && (intent == null || !intent.getBooleanExtra(EXTRA_SETTINGS_CONSUMED, false))) {
            if (intent != null) intent.putExtra(EXTRA_SETTINGS_CONSUMED, true);
            openSettings(activity, "https_app_link");
            return;
        }

        WebView webView = activity.findViewById(R.id.webView);
        if (webView == null) return;

        Uri current = parse(webView.getUrl());
        if (isNativeSettingsUri(current)) {
            String host = current == null ? PRIMARY_HOST : safeLower(current.getHost());
            if (!PRIMARY_HOST.equals(host) && !BACKUP_HOST.equals(host)) host = PRIMARY_HOST;
            try {
                webView.stopLoading();
                webView.loadUrl("https://" + host + "/");
            } catch (Throwable ignored) {}
            openSettings(activity, "webview_url");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHookAt >= HOOK_REFRESH_MS) {
            lastHookAt = now;
            installWebRouteHook(webView);
        }
    }

    private static Uri parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void openSettings(Activity activity, String source) {
        if (activity == null || activity instanceof HcfSubActivities.SettingsActivity
                || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            AppLogger.info(activity, "native_settings_route", source);
            activity.startActivity(new Intent(activity, HcfSubActivities.SettingsActivity.class));
        } catch (Throwable error) {
            AppLogger.error(activity, "native_settings_route", error.getClass().getSimpleName());
        }
    }

    /**
     * Stops Flarum SPA links/history changes before they turn /app/settings into a web page.
     * HCFNative.openSettings() is already exposed by MainActivity on trusted forum pages.
     */
    private static void installWebRouteHook(WebView webView) {
        if (webView == null) return;
        final String script =
                "(function(){try{" +
                "if(window.__HCF_NATIVE_SETTINGS_URL_V1__)return;" +
                "window.__HCF_NATIVE_SETTINGS_URL_V1__=true;" +
                "var hosts={'forum.harleytg.com':1,'harleysclan.freeflarum.com':1};" +
                "var isSettings=function(v){try{var u=new URL(String(v||''),location.href);" +
                "var p=String(u.pathname||'').replace(/\\/+$/,'').toLowerCase();" +
                "return u.protocol==='https:'&&!!hosts[String(u.hostname||'').toLowerCase()]&&p==='/app/settings';" +
                "}catch(e){return false;}};" +
                "var open=function(){try{if(window.HCFNative&&HCFNative.openSettings)HCFNative.openSettings();}catch(e){}};" +
                "document.addEventListener('click',function(e){try{var t=e.target&&e.target.closest?e.target.closest('a[href]'):null;" +
                "if(t&&isSettings(t.href)){e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation();open();}}catch(x){}},true);" +
                "['pushState','replaceState'].forEach(function(k){try{var old=history[k];if(typeof old!=='function')return;" +
                "history[k]=function(){try{var u=arguments.length>2?arguments[2]:'';if(u&&isSettings(u)){open();return null;}}catch(e){}" +
                "return old.apply(this,arguments);};}catch(e){}});" +
                "}catch(e){}})();";
        try {
            webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {}
    }

    /** Installs the native URL bridge before MainActivity is shown. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}
