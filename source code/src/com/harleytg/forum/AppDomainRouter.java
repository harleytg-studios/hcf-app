package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Local in-app URL namespace for Harley's Clan Forum.
 *
 * Format:
 *   app.forum.harleytg.com/<item>
 *
 * These addresses are intercepted by the Android app. They are not treated as
 * real forum hosts and are never sent to DNS/the network.
 */
public final class AppDomainRouter {
    public static final String APP_HOST = "app.forum.harleytg.com";

    private AppDomainRouter() {
    }

    /**
     * Installs a very small inflater hook before MainActivity inflates its UI.
     * Only R.id.currentUrlText is replaced; every other view is left to the
     * normal Android inflater.
     */
    static void installAddressBarInflater(final Activity activity) {
        if (activity == null) {
            return;
        }

        try {
            final LayoutInflater inflater = activity.getLayoutInflater();
            if (inflater == null || inflater.getFactory2() != null || inflater.getFactory() != null) {
                return;
            }

            inflater.setFactory2(new LayoutInflater.Factory2() {
                @Override
                public View onCreateView(View parent, String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }

                @Override
                public View onCreateView(String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }
            });
        } catch (Throwable error) {
            try {
                AppLogger.warn(activity, "app_domain_inflater", error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static View createAddressBarIfNeeded(
            String name,
            android.content.Context context,
            AttributeSet attrs
    ) {
        if (context == null || attrs == null || name == null) {
            return null;
        }

        if (!("EditText".equals(name) || "android.widget.EditText".equals(name))) {
            return null;
        }

        int id = attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res/android",
                "id",
                0
        );

        if (id != R.id.currentUrlText) {
            return null;
        }

        return new AppDomainEditText(context, attrs);
    }

    public static boolean isAppUrl(String rawInput) {
        Uri uri = parse(rawInput);
        return uri != null
                && uri.getHost() != null
                && APP_HOST.equalsIgnoreCase(uri.getHost());
    }

    /**
     * Handles app.forum.harleytg.com/<item>.
     *
     * @return true when this was an app-domain URL and normal URL handling
     * should stop.
     */
    public static boolean handle(Activity activity, String rawInput) {
        if (activity == null || !isAppUrl(rawInput)) {
            return false;
        }

        Uri uri = parse(rawInput);
        String item = firstPathItem(uri);

        try {
            switch (item) {
                case "":
                case "home":
                case "menu":
                    showMenu(activity);
                    break;

                case "settings":
                case "preferences":
                case "appearance":
                case "updates":
                case "advanced":
                case "about":
                    open(activity, SettingsActivity.class, item.isEmpty() ? "settings" : item);
                    break;

                case "identity":
                case "account":
                    open(activity, IdentityActivity.class, "identity");
                    break;

                case "setup":
                case "app-setup":
                    open(activity, SetupActivity.class, "setup");
                    break;

                case "welcome":
                    open(activity, WelcomeActivity.class, "welcome");
                    break;

                case "logs":
                case "diagnostics":
                case "crash-logs":
                    open(activity, LogsActivity.class, "logs");
                    break;

                case "support":
                case "contact":
                case "contact-support":
                    open(activity, SupportContactActivity.class, "support");
                    break;

                case "cookies":
                case "cookie-manager":
                    open(activity, CookieManagerActivity.class, "cookies");
                    break;

                case "notification-settings":
                case "alert-settings":
                    openNotificationSettings(activity);
                    break;

                case "permissions":
                case "app-info":
                case "android-settings":
                    openAndroidAppInfo(activity);
                    break;

                case "forum":
                    loadForumPath(activity, "/", "forum_home");
                    break;

                case "browse":
                case "all":
                    loadForumPath(activity, "/all", "browse");
                    break;

                case "notifications":
                case "alerts":
                    loadForumPath(activity, "/notifications", "notifications");
                    try {
                        InstantNotificationService.requestImmediateSync(activity);
                    } catch (Throwable ignored) {
                    }
                    break;

                case "profile":
                case "my-profile":
                    openForumProfile(activity);
                    break;

                case "compose":
                case "new":
                case "new-discussion":
                    loadForumPath(activity, "/compose", "compose");
                    break;

                case "downloads":
                    loadForumPath(activity, "/p/14-downloads", "downloads");
                    break;

                case "forum-support":
                case "support-page":
                    loadForumPath(activity, "/p/17-support", "forum_support");
                    break;

                default:
                    showUnknownItem(activity, item);
                    break;
            }

            return true;
        } catch (Throwable error) {
            try {
                AppLogger.error(activity, "app_domain", item + " | " + error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }

            Toast.makeText(
                    activity,
                    "Unable to open app item: " + item,
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        }
    }

    private static void showMenu(final Activity activity) {
        final String[] labels = {
                "App Settings",
                "Account & Identity",
                "App Setup",
                "Welcome Screen",
                "Logs & Diagnostics",
                "Contact Support",
                "Cookie Manager",
                "Notification Settings",
                "Android App Info",
                "Forum Home",
                "Browse Discussions",
                "Notifications",
                "My Profile",
                "New Discussion",
                "Downloads"
        };

        final String[] items = {
                "settings",
                "identity",
                "setup",
                "welcome",
                "logs",
                "support",
                "cookies",
                "notification-settings",
                "app-info",
                "forum",
                "browse",
                "notifications",
                "profile",
                "compose",
                "downloads"
        };

        new AlertDialog.Builder(activity)
                .setTitle("Harley's Clan Forum App")
                .setMessage(APP_HOST)
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < items.length) {
                        handle(activity, APP_HOST + "/" + items[which]);
                    }
                })
                .setNegativeButton("Close", null)
                .show();

        AppLogger.info(activity, "app_domain", "menu");
    }

    private static void open(Activity activity, Class<?> target, String destination) {
        activity.startActivity(new Intent(activity, target));
        AppLogger.info(activity, "app_domain", destination);
    }

    private static void openNotificationSettings(Activity activity) {
        try {
            NotificationHelper.createChannel(activity);
            NotificationHelper.openChannelSettings(activity);
            AppLogger.info(activity, "app_domain", "notification_settings");
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_domain_notification_settings", error.getClass().getSimpleName());
            openAndroidAppInfo(activity);
        }
    }

    private static void openAndroidAppInfo(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
        AppLogger.info(activity, "app_domain", "android_app_info");
    }

    private static void openForumProfile(Activity activity) {
        ForumIdentity.Snapshot snapshot = ForumIdentity.load(activity);

        if (snapshot == null || !snapshot.loggedIn) {
            loadForumPath(activity, "/login", "profile_login");
            return;
        }

        String slug = snapshot.slug;
        if (slug == null || slug.trim().isEmpty()) {
            slug = snapshot.username;
        }

        if (slug == null || slug.trim().isEmpty()) {
            Toast.makeText(activity, "Your profile is still syncing.", Toast.LENGTH_SHORT).show();
            try {
                InstantNotificationService.requestImmediateSync(activity);
            } catch (Throwable ignored) {
            }
            return;
        }

        loadForumPath(activity, "/u/" + Uri.encode(slug.trim()), "profile");
    }

    private static void loadForumPath(Activity activity, String path, String destination) {
        WebView webView = activity.findViewById(R.id.webView);
        if (webView == null) {
            Toast.makeText(activity, "Forum view is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = activeForumHost(activity);
        String safePath = path == null || path.trim().isEmpty() ? "/" : path.trim();
        if (!safePath.startsWith("/") || safePath.startsWith("//")) {
            safePath = "/";
        }

        String url = "https://" + host + safePath;
        webView.loadUrl(url);
        AppLogger.info(activity, "app_domain", destination + " | " + AppLogger.safeUrl(url));
    }

    private static String activeForumHost(Activity activity) {
        String host = "";
        try {
            SharedPreferences prefs = activity.getSharedPreferences("hcf_app", 0);
            host = prefs.getString("active_host", "");
        } catch (Throwable ignored) {
        }

        if (host != null) {
            host = host.trim().toLowerCase(Locale.US);
            if (ForumConfig.FORUM_HOSTS.contains(host)) {
                return host;
            }
        }

        String primary = ForumConfig.PRIMARY_HOST;
        if (primary == null || primary.trim().isEmpty()) {
            primary = ForumConfig.BUILTIN_PRIMARY_HOST;
        }
        return primary.toLowerCase(Locale.US);
    }

    private static Uri parse(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String value = rawInput.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            if (!value.contains("://")) {
                value = "https://" + value;
            }
            return Uri.parse(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Only the first item after app.forum.harleytg.com/ is the command. */
    private static String firstPathItem(Uri uri) {
        if (uri == null) {
            return "";
        }

        String path = uri.getPath();
        if (path == null || path.trim().isEmpty() || "/".equals(path)) {
            return "";
        }

        String value = path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }

        return value.trim().toLowerCase(Locale.US).replace('_', '-');
    }

    private static void showUnknownItem(Activity activity, String item) {
        new AlertDialog.Builder(activity)
                .setTitle("Unknown app item")
                .setMessage(
                        APP_HOST + "/" + item
                                + "\n\nUse " + APP_HOST
                                + " to see available in-app items."
                )
                .setPositiveButton("OK", null)
                .show();

        AppLogger.warn(activity, "app_domain", "unknown | " + item);
    }
}
