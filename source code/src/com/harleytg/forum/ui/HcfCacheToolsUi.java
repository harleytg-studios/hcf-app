package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Dev/Beta Content Cache tools.
 *
 * Adds a native Settings panel under Advanced & About with three controls:
 *  - Refresh Forum Content: clears the app WebView cache and reloads the current page.
 *  - Force Fresh Page Load: bypasses local WebView cache without clearing sign-in state.
 *  - Purge HCF CDN Cache: Dev-only jsDelivr purge for the current FoF page and shared loaders.
 *
 * No GitHub token or private credential is bundled. The CDN purge uses jsDelivr's public
 * purge endpoint and is guarded by an explicit confirmation plus a local cooldown.
 */
public final class HcfCacheToolsUi {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";
    private static final String PANEL_TAG = "hcf_content_cache_tools";
    private static final String PREF_FILE = "hcf_cache_tools";
    private static final String PREF_LAST_PURGE = "last_jsdelivr_purge_ms";
    private static final long PURGE_COOLDOWN_MS = 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS =
            new WeakHashMap<>();
    private static final Pattern FOF_ROUTE = Pattern.compile("^/p/(\\d+-[A-Za-z0-9-]+)$");

    private static volatile WeakReference<Activity> forumActivity = new WeakReference<>(null);
    private static boolean registered;

    private HcfCacheToolsUi() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context app = context.getApplicationContext();
            if (!(app instanceof Application)) return true;
            install((Application) app);
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

    private static synchronized void install(Application app) {
        if (registered) return;
        registered = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                rememberForum(activity);
                if (isSettings(activity)) installObserver(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                rememberForum(activity);
                if (isSettings(activity)) {
                    installObserver(activity);
                    scheduleRender(activity);
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

            @Override public void onActivityDestroyed(Activity activity) {
                removeObserver(activity);
                Activity remembered = forumActivity.get();
                if (remembered == activity) forumActivity = new WeakReference<>(null);
            }
        });
    }

    private static void rememberForum(Activity activity) {
        if (activity instanceof HcfForum.MainActivity) {
            forumActivity = new WeakReference<>(activity);
        }
    }

    private static boolean isSettings(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean isDevBuild(Activity activity) {
        String pkg = activity == null ? "" : activity.getPackageName();
        return pkg != null && pkg.endsWith(".dev");
    }

    private static void installObserver(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;
            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) render(activity);
                }
            };
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
        scheduleRender(activity);
    }

    private static void removeObserver(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) { listener = OBSERVERS.remove(activity); }
        if (listener == null) return;
        try {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void scheduleRender(Activity activity) {
        MAIN.postDelayed(() -> render(activity), 70L);
        MAIN.postDelayed(() -> render(activity), 220L);
        MAIN.postDelayed(() -> render(activity), 500L);
    }

    private static void render(Activity activity) {
        if (!isSettings(activity) || activity.isFinishing()) return;
        if (!"advanced".equals(readStringField(activity, "currentSettingsSection"))) return;

        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null || findTagged(content, PANEL_TAG) != null) return;

        LinearLayout body = nativeCard(activity);
        body.setTag(PANEL_TAG + ":body");

        View title = nativeSectionTitle(activity,
                "Content Cache",
                "Refresh local forum content and Dev CDN cache");
        if (title != null) body.addView(title);

        TextView note = text(activity,
                "These tools do not clear forum sign-in cookies or account data. Local refresh affects this app only; CDN purge requests a fresh copy from jsDelivr.",
                10,
                color(activity, R.color.hcf_muted, Color.LTGRAY));
        note.setLineSpacing(0f, 1.08f);
        body.addView(note, lp(activity, -1, -2, 0, 10));

        Button refresh = actionButton(activity, "Refresh Forum Content");
        refresh.setOnClickListener(v -> refreshForum(activity, true));
        body.addView(refresh, lp(activity, -1, dp(activity, 50), 0, 8));

        Button forceFresh = actionButton(activity, "Force Fresh Page Load");
        forceFresh.setOnClickListener(v -> refreshForum(activity, false));
        body.addView(forceFresh, lp(activity, -1, dp(activity, 50), 0, 8));

        if (isDevBuild(activity)) {
            Button purge = actionButton(activity, "Purge HCF CDN Cache");
            purge.setOnClickListener(v -> confirmPurge(activity, purge));
            body.addView(purge, lp(activity, -1, dp(activity, 50), 0, 6));

            TextView devNote = text(activity,
                    "Dev only • purges the current FoF page, /p/31-hcf-app, and shared HCF page-loader assets. A 60-second cooldown prevents accidental repeat requests.",
                    9,
                    color(activity, R.color.hcf_hint, Color.GRAY));
            devNote.setLineSpacing(0f, 1.08f);
            body.addView(devNote, lp(activity, -1, -2, 0, 0));
        }

        View panel = nativeConnectedSettingsPanel(activity,
                "Content Cache",
                isDevBuild(activity)
                        ? "Local WebView refresh • force fresh load • Dev CDN purge"
                        : "Local WebView refresh • force fresh load",
                body,
                false);
        panel.setTag(PANEL_TAG);

        int aboutIndex = directChildContainingText(content, "About Harley's Clan Forum");
        if (aboutIndex < 0) aboutIndex = content.getChildCount();
        content.addView(panel, aboutIndex);
        AppLogger.info(activity, "cache_tools_ui", "advanced_control_added");
    }

    private static void refreshForum(Activity settingsActivity, boolean clearCache) {
        Activity main = forumActivity.get();
        WebView webView = main == null ? null : main.findViewById(R.id.webView);

        if (clearCache) {
            try {
                if (webView != null) {
                    webView.clearCache(true);
                } else {
                    WebView temp = new WebView(settingsActivity);
                    temp.clearCache(true);
                    temp.destroy();
                }
            } catch (Throwable error) {
                AppLogger.warn(settingsActivity, "cache_tools_clear", error.getClass().getSimpleName());
            }
        }

        if (main != null && webView != null) {
            final WebView target = webView;
            main.runOnUiThread(() -> {
                try {
                    String current = target.getUrl();
                    if (current == null || current.trim().isEmpty()) {
                        current = "https://forum.harleytg.com/";
                    }
                    final int previousMode = target.getSettings().getCacheMode();
                    target.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                    target.loadUrl(withCacheBuster(current));
                    target.postDelayed(() -> {
                        try { target.getSettings().setCacheMode(previousMode); }
                        catch (Throwable ignored) {}
                    }, 2500L);
                } catch (Throwable error) {
                    AppLogger.warn(settingsActivity, "cache_tools_reload", error.getClass().getSimpleName());
                }
            });
            Toast.makeText(settingsActivity,
                    clearCache ? "Forum cache cleared and fresh reload requested."
                            : "Fresh page load requested.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(settingsActivity, HcfForum.MainActivity.class);
            intent.setData(Uri.parse(withCacheBuster("https://forum.harleytg.com/")));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            settingsActivity.startActivity(intent);
            Toast.makeText(settingsActivity,
                    clearCache ? "Forum cache cleared. Opening a fresh forum load."
                            : "Opening a fresh forum load.",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            AppLogger.error(settingsActivity, "cache_tools_open", error.getClass().getSimpleName());
            Toast.makeText(settingsActivity, "Fresh forum load could not be started.", Toast.LENGTH_SHORT).show();
        }
    }

    private static String withCacheBuster(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl);
            return uri.buildUpon()
                    .appendQueryParameter("hcf_refresh", String.valueOf(System.currentTimeMillis()))
                    .build()
                    .toString();
        } catch (Throwable ignored) {
            String join = rawUrl != null && rawUrl.contains("?") ? "&" : "?";
            return String.valueOf(rawUrl) + join + "hcf_refresh=" + System.currentTimeMillis();
        }
    }

    private static void confirmPurge(Activity activity, Button button) {
        if (!isDevBuild(activity)) return;
        long last = activity.getSharedPreferences(PREF_FILE, 0).getLong(PREF_LAST_PURGE, 0L);
        long remaining = PURGE_COOLDOWN_MS - (System.currentTimeMillis() - last);
        if (remaining > 0L) {
            Toast.makeText(activity,
                    "CDN purge cooldown: " + Math.max(1L, (remaining + 999L) / 1000L) + "s",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Purge HCF CDN Cache?")
                .setMessage("This asks jsDelivr to invalidate the current HCF FoF page and shared page-loader assets. Use this after publishing a page update that is still showing stale content.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Purge Cache", (dialog, which) -> purgeCdn(activity, button))
                .show();
    }

    private static void purgeCdn(Activity activity, Button button) {
        if (!isDevBuild(activity)) return;
        activity.getSharedPreferences(PREF_FILE, 0).edit()
                .putLong(PREF_LAST_PURGE, System.currentTimeMillis())
                .apply();

        button.setEnabled(false);
        button.setText("Purging HCF CDN…");

        AppExecutors.network().execute(() -> {
            Set<String> paths = purgePaths();
            String current = currentForumUrl();
            String currentPage = fofFileForUrl(current);
            if (currentPage != null) paths.add(currentPage);

            int ok = 0;
            int failed = 0;
            for (String path : paths) {
                if (purgeOne(path)) ok++;
                else failed++;
            }

            final int successCount = ok;
            final int failedCount = failed;
            MAIN.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                button.setEnabled(true);
                button.setText("Purge HCF CDN Cache");
                if (failedCount == 0) {
                    Toast.makeText(activity,
                            "HCF CDN purge requested for " + successCount + " files.",
                            Toast.LENGTH_LONG).show();
                    refreshForum(activity, true);
                } else {
                    Toast.makeText(activity,
                            "CDN purge finished: " + successCount + " succeeded, " + failedCount + " failed.",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static Set<String> purgePaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.add("v1.x/pages/fof-pages/31-hcf-app.html");
        paths.add("v1.x/pages/fof-pages/hcf-page-entry.js");
        paths.add("v1.x/pages/fof-pages/hcf-page-bootstrap.js");
        paths.add("v1.x/pages/fof-pages/hcf-page.js");
        paths.add("v1.x/pages/fof-pages/hcf-page-v2.1.css");
        paths.add("v1.x/pages/fof-pages/hcf-page-runtime.css");
        paths.add("v1.x/pages/fof-pages/hcf-fof-loader.js");
        paths.add("v1.x/pages/fof-pages/hcf-domain-router.js");
        return paths;
    }

    private static boolean purgeOne(String path) {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL("https://purge.jsdelivr.net/gh/markhitchk/hcf@main/" + path);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " CacheTools");
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Throwable error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String currentForumUrl() {
        try {
            Activity main = forumActivity.get();
            WebView web = main == null ? null : main.findViewById(R.id.webView);
            return web == null ? null : web.getUrl();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fofFileForUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            String path = Uri.parse(rawUrl).getPath();
            if (path == null) return null;
            Matcher matcher = FOF_ROUTE.matcher(path);
            if (!matcher.matches()) return null;
            return "v1.x/pages/fof-pages/" + matcher.group(1) + ".html";
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LinearLayout nativeCard(Activity activity) {
        try {
            Method method = activity.getClass().getDeclaredMethod("card");
            method.setAccessible(true);
            Object value = method.invoke(activity);
            if (value instanceof LinearLayout) return (LinearLayout) value;
        } catch (Throwable ignored) {}
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        try { card.setBackgroundResource(R.drawable.settings_section_body); }
        catch (Throwable ignored) {}
        return card;
    }

    private static View nativeSectionTitle(Activity activity, String title, String subtitle) {
        try {
            Method method = activity.getClass().getDeclaredMethod("sectionTitle", String.class, String.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle);
            if (value instanceof View) return (View) value;
        } catch (Throwable ignored) {}

        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(activity, title, 15, color(activity, R.color.hcf_text, Color.WHITE));
        titleView.setTypeface(null, 1);
        block.addView(titleView);
        block.addView(text(activity, subtitle, 10, color(activity, R.color.hcf_muted, Color.LTGRAY)));
        return block;
    }

    private static View nativeConnectedSettingsPanel(Activity activity, String title, String subtitle,
                                                     View inner, boolean expanded) {
        try {
            Method method = activity.getClass().getDeclaredMethod(
                    "connectedSettingsPanel", String.class, String.class, View.class, boolean.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle, inner, expanded);
            if (value instanceof View) return (View) value;
        } catch (Throwable ignored) {}

        LinearLayout fallback = new LinearLayout(activity);
        fallback.setOrientation(LinearLayout.VERTICAL);
        fallback.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
        fallback.addView(nativeSectionTitle(activity, title, subtitle));
        fallback.addView(inner);
        return fallback;
    }

    private static Button actionButton(Activity activity, String label) {
        Button button = new Button(activity);
        try { UiButtons.normalizeText(button); } catch (Throwable ignored) { button.setAllCaps(false); }
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(color(activity, R.color.hcf_cyan_bright, Color.CYAN));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        try { button.setBackgroundResource(R.drawable.button_background); } catch (Throwable ignored) {}
        return button;
    }

    private static TextView text(Activity activity, String value, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout.LayoutParams lp(Activity activity, int width, int height,
                                                int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = dp(activity, top);
        lp.bottomMargin = dp(activity, bottom);
        return lp;
    }

    private static int color(Activity activity, int id, int fallback) {
        try { return activity.getColor(id); } catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static ViewGroup readViewGroupField(Activity activity, String name) {
        try {
            Field field = activity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof ViewGroup ? (ViewGroup) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readStringField(Activity activity, String name) {
        try {
            Field field = activity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static View findTagged(View root, String tag) {
        if (root == null) return null;
        Object own = root.getTag();
        if (tag.equals(own)) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static int directChildContainingText(ViewGroup group, String needle) {
        if (group == null || needle == null) return -1;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), needle)) return i;
        }
        return -1;
    }

    private static boolean containsText(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().contains(needle)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), needle)) return true;
            }
        }
        return false;
    }
}
