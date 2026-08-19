package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;

/**
 * Shared native UI system.
 *
 * Package-private UI/theme helpers live together here so the Android source tree
 * is organized by feature area instead of one tiny .java file per helper class.
 * Class names stay unchanged, so existing callers do not need rewrites.
 */

final class ThemeManager {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String AMOLED = "amoled";

    static Context wrap(Context base) {
        if (base == null) return null;
        String selected = mode(base);
        Configuration current = base.getResources().getConfiguration();
        int targetNight = resolvedNightMode(base, current, selected);
        int currentNight = current.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNight == targetNight) return base;

        Configuration override = new Configuration(current);
        override.uiMode = (override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | targetNight;
        return base.createConfigurationContext(override);
    }

    static void prepare(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
    }

    static void apply(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
        applySystemBars(activity);
    }

    static int resolvedNightMode(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        return resolvedNightMode(context, configuration, mode(context));
    }

    private static int resolvedNightMode(Context context, Configuration configuration, String selected) {
        if (DARK.equals(selected) || AMOLED.equals(selected)) return Configuration.UI_MODE_NIGHT_YES;
        if (LIGHT.equals(selected)) return Configuration.UI_MODE_NIGHT_NO;
        int systemUi = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return systemUi == Configuration.UI_MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
    }

    static String signature(Context context) {
        return mode(context) + ":" + resolvedNightMode(context);
    }

    static boolean changedSince(Context context, String appliedSignature) {
        return appliedSignature != null && !appliedSignature.equals(signature(context));
    }

    static String webColorScheme(Context context) {
        return resolvedNightMode(context) == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
    }

    static void applySystemBars(Activity activity) {
        boolean dark = isDark(activity);
        int bar = isAmoled(activity) ? Color.BLACK : activity.getColor(R.color.hcf_bg);
        activity.getWindow().setStatusBarColor(bar);
        activity.getWindow().setNavigationBarColor(bar);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean isAmoled(Context context) {
        return AMOLED.equals(mode(context));
    }

    static String mode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String value = prefs.getString(AppPrefs.APP_THEME, SYSTEM);
        if (!LIGHT.equals(value) && !DARK.equals(value) && !AMOLED.equals(value)) return SYSTEM;
        return value;
    }

    static String label(Context context) {
        String mode = mode(context);
        if (LIGHT.equals(mode)) return "Day (Light)";
        if (DARK.equals(mode)) return "Night (Dark)";
        if (AMOLED.equals(mode)) return "AMOLED Black";
        return "Follow phone (Auto)";
    }

    static String next(String current) {
        if (SYSTEM.equals(current)) return LIGHT;
        if (LIGHT.equals(current)) return DARK;
        if (DARK.equals(current)) return AMOLED;
        return SYSTEM;
    }

    private ThemeManager() {}
}

/** Base Activity that applies the user-selected HCF theme before page resources resolve. */
abstract class ThemedActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.prepare(this);
        super.onCreate(savedInstanceState);
        ThemeManager.applySystemBars(this);
    }
}

/** Consistent native button geometry across all HCF app surfaces. */
final class UiButtons {
    static void normalizeText(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
    }

    static ImageButton iconButton(Context context, int iconRes, int backgroundRes, int paddingDp, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (backgroundRes != 0) button.setBackgroundResource(backgroundRes);
        else button.setBackgroundColor(Color.TRANSPARENT);
        int padding = dp(context, paddingDp);
        button.setPadding(padding, padding, padding, padding);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setAdjustViewBounds(false);
        button.setContentDescription(description == null || description.trim().isEmpty() ? "Button" : description);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private UiButtons() {}
}

/** One-time defaults/migrations and preference repair for the app shell. */
final class UiPreferences {
    private static final int CURRENT_REVAMP = 3;

    static void migrate(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        sanitizePreferenceTypes(prefs);

        int rev = 0;
        try { rev = prefs.getInt(AppPrefs.UI_REVAMP_VERSION, 0); }
        catch (Throwable ignored) {
            prefs.edit().remove(AppPrefs.UI_REVAMP_VERSION).apply();
        }
        if (rev >= CURRENT_REVAMP) return;

        SharedPreferences.Editor edit = prefs.edit()
                .putBoolean(AppPrefs.COMPACT_HEADER, true)
                .putBoolean(AppPrefs.SHOW_BOTTOM_NAV, false)
                .putInt(AppPrefs.UI_REVAMP_VERSION, CURRENT_REVAMP);
        if (!prefs.contains(AppPrefs.SHOW_URL_BAR)) edit.putBoolean(AppPrefs.SHOW_URL_BAR, false);
        edit.apply();
    }

    private static void sanitizePreferenceTypes(SharedPreferences prefs) {
        final Map<String, ?> all;
        try { all = prefs.getAll(); }
        catch (Throwable ignored) { return; }

        SharedPreferences.Editor edit = prefs.edit();
        boolean changed = false;

        String[] boolKeys = new String[]{
                AppPrefs.NOTIFICATIONS_ENABLED,
                AppPrefs.BACKGROUND_NOTIFICATION_SYNC,
                AppPrefs.AUTO_FAILOVER,
                AppPrefs.EXTERNAL_LINKS,
                AppPrefs.SHOW_URL_BAR,
                AppPrefs.COMPACT_HEADER,
                AppPrefs.SHOW_BOTTOM_NAV,
                AppPrefs.SHOW_STARTUP_SCREEN,
                AppPrefs.LIVE_FORUM_UPDATES,
                AppPrefs.PERFORMANCE_MODE,
                AppPrefs.NOTIFICATION_PERMISSION_ASKED,
                AppPrefs.PERMISSION_ONBOARDING_DONE,
                AppPrefs.INSTALL_PERMISSION_PROMPTED,
                AppPrefs.APP_HAS_LAUNCHED,
                AppPrefs.UPDATE_AUTO_CHECK,
                AppPrefs.UPDATE_AUTO_DOWNLOAD,
                AppPrefs.UPDATE_INSTALL_PENDING,
                AppPrefs.UPDATE_RESUME_AFTER_PERMISSION,
                AppPrefs.TELEMETRY_ENABLED,
                AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS,
                AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT,
                AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS,
                AppPrefs.TELEMETRY_INCLUDE_IDENTITY,
                AppPrefs.TELEMETRY_INCLUDE_EMAIL,
                AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL,
                AppPrefs.TELEMETRY_INCLUDE_ROUTE,
                AppPrefs.IDENTITY_LOGGED_IN,
                AppPrefs.IDENTITY_EMAIL_CONFIRMED,
                AppPrefs.IDENTITY_ADMIN,
                AppPrefs.IDENTITY_SECURITY_SEEN,
                AppPrefs.IDENTITY_SECURITY_PASSWORD_CONTROLS,
                AppPrefs.IDENTITY_SECURITY_EMAIL_CONTROLS,
                AppPrefs.IDENTITY_SECURITY_TWO_FACTOR_CONTROLS
        };
        for (String key : boolKeys) changed |= removeIfWrongType(all, edit, key, Boolean.class);

        String[] stringKeys = new String[]{
                AppPrefs.SAFE_LINKS_SEEN_DOMAINS,
                AppPrefs.APP_THEME,
                AppPrefs.PERFORMANCE_PROFILE,
                AppPrefs.NATIVE_ACCENT,
                AppPrefs.LAST_RECOVERABLE_URL,
                AppPrefs.LAST_SEEN_WHATS_NEW_VERSION,
                AppPrefs.SESSION_USER_ID,
                AppPrefs.ACTIVE_HOST,
                AppPrefs.DELIVERED_NOTIFICATION_IDS,
                AppPrefs.NOTIFICATION_LAST_SYNC_STATUS,
                AppPrefs.FIREBASE_CONFIG_URL,
                AppPrefs.FIREBASE_CONFIG_CACHE,
                AppPrefs.FIREBASE_CONFIG_SOURCE,
                AppPrefs.UPDATE_CHANNEL,
                AppPrefs.UPDATE_LAST_AVAILABLE_TAG,
                AppPrefs.UPDATE_DOWNLOAD_TAG,
                AppPrefs.UPDATE_DOWNLOAD_NAME,
                AppPrefs.TELEMETRY_LEVEL,
                AppPrefs.TELEMETRY_LAST_ROUTE,
                AppPrefs.TELEMETRY_BREADCRUMBS,
                AppPrefs.TELEMETRY_REPORT_HISTORY,
                AppPrefs.TELEMETRY_PENDING_CRASH_ID,
                AppPrefs.TELEMETRY_LAST_RESULT,
                AppPrefs.IDENTITY_USER_ID,
                AppPrefs.IDENTITY_USERNAME,
                AppPrefs.IDENTITY_SLUG,
                AppPrefs.IDENTITY_DISPLAY_NAME,
                AppPrefs.IDENTITY_EMAIL,
                AppPrefs.IDENTITY_AVATAR_URL,
                AppPrefs.IDENTITY_GROUPS,
                AppPrefs.IDENTITY_CONNECTIONS,
                AppPrefs.IDENTITY_JOIN_TIME,
                AppPrefs.IDENTITY_LAST_SEEN_AT,
                AppPrefs.IDENTITY_HOST,
                AppPrefs.IDENTITY_SECURITY_PROVIDERS,
                AppPrefs.IDENTITY_SECURITY_HOST,
                AppPrefs.IDENTITY_SECURITY_PATH
        };
        for (String key : stringKeys) changed |= removeIfWrongType(all, edit, key, String.class);

        String[] longKeys = new String[]{
                AppPrefs.FALLBACK_UNTIL,
                AppPrefs.LAST_MAIN_PAUSED_AT,
                AppPrefs.NOTIFICATION_LAST_SYNC_AT,
                AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS,
                AppPrefs.UPDATE_LAST_CHECK,
                AppPrefs.UPDATE_DOWNLOAD_ID,
                AppPrefs.TELEMETRY_LAST_HEARTBEAT,
                AppPrefs.IDENTITY_SYNCED_AT,
                AppPrefs.IDENTITY_SECURITY_SYNCED_AT
        };
        for (String key : longKeys) changed |= removeIfWrongType(all, edit, key, Long.class);

        String[] intKeys = new String[]{
                AppPrefs.UI_REVAMP_VERSION,
                AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION,
                AppPrefs.LAST_NOTIFICATION_COUNT,
                AppPrefs.IDENTITY_UNREAD_NOTIFICATIONS,
                AppPrefs.IDENTITY_NEW_NOTIFICATIONS,
                AppPrefs.IDENTITY_DISCUSSION_COUNT,
                AppPrefs.IDENTITY_COMMENT_COUNT,
                AppPrefs.IDENTITY_SECURITY_SESSION_COUNT,
                AppPrefs.IDENTITY_SECURITY_ACTIVE_SESSION_COUNT
        };
        for (String key : intKeys) changed |= removeIfWrongType(all, edit, key, Integer.class);

        if (changed) edit.apply();
    }

    private static boolean removeIfWrongType(Map<String, ?> all, SharedPreferences.Editor edit,
                                             String key, Class<?> expected) {
        if (!all.containsKey(key)) return false;
        Object value = all.get(key);
        if (value == null || expected.isInstance(value)) return false;
        edit.remove(key);
        return true;
    }

    private UiPreferences() {}
}

/** Font Awesome Solid-style native vector icon mapping. */
final class FaIcons {
    private FaIcons() {}

    static int forLabel(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.US);
        if (s.contains("account security") || s.contains("security") || s.contains("safe link")) return R.drawable.fa_shield;
        if (s.contains("home")) return R.drawable.fa_house;
        if (s.contains("backup") || s.contains("primary") || s.contains("switch host") || s.contains("failover")) return R.drawable.fa_right_left;
        if (s.contains("browser") || s.contains("externally") || s.contains("open link")) return R.drawable.fa_arrow_up_right_from_square;
        if (s.contains("share")) return R.drawable.fa_share_nodes;
        if (s.contains("notification") || s.contains("alert") || s.contains("mention") || s.contains("reply")) return R.drawable.fa_bell;
        if (s.contains("setting") || s.contains("theme") || s.contains("appearance") || s.contains("permission")) return R.drawable.fa_gear;
        if (s.contains("log") || s.contains("diagnostic") || s.contains("report") || s.contains("history") || s.contains("details")) return R.drawable.fa_list;
        if (s.contains("support") || s.contains("email") || s.contains("contact")) return R.drawable.fa_envelope;
        if (s.contains("copy")) return R.drawable.fa_copy;
        if (s.contains("retry") || s.contains("refresh") || s.contains("sync") || s.contains("check for updates")) return R.drawable.fa_rotate_right;
        if (s.contains("back")) return R.drawable.fa_arrow_left;
        if (s.contains("create") || s.contains("post") || s.contains("new discussion")) return R.drawable.fa_plus;
        if (s.contains("identity") || s.contains("profile") || s.contains("account")) return R.drawable.fa_user;
        if (s.contains("connection") || s.contains("server") || s.contains("forum") || s.contains("cookie") || s.contains("site data") || s.contains("link")) return R.drawable.fa_globe;
        if (s.contains("install") || s.contains("download") || s.contains("update")) return R.drawable.fa_download;
        if (s.contains("error") || s.contains("recovery") || s.contains("crash")) return R.drawable.fa_bug;
        if (s.contains("about") || s.contains("what's new") || s.contains("release")) return R.drawable.fa_circle_info;
        return R.drawable.fa_circle_info;
    }

    static void applyStart(TextView view, String label) {
        applyStart(view, forLabel(label));
    }

    static void applyStart(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 18), dp(view.getContext(), 18));
        view.setCompoundDrawablesRelative(d, null, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 10));
        tint(view);
    }

    static void applyTop(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 19), dp(view.getContext(), 19));
        view.setCompoundDrawables(null, d, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 2));
        tint(view);
        view.setGravity(Gravity.CENTER);
    }

    static void applyOnly(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 20), dp(view.getContext(), 20));
        view.setCompoundDrawables(null, null, null, null);
        view.setBackground(view.getBackground());
        view.setCompoundDrawablesRelative(d, null, null, null);
        view.setCompoundDrawablePadding(0);
        tint(view);
        view.setGravity(Gravity.CENTER);
    }

    private static void tint(TextView view) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                view.setCompoundDrawableTintList(ColorStateList.valueOf(view.getCurrentTextColor()));
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
