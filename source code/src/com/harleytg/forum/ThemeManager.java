package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * App theme controller: light / dark / AMOLED / auto_forum / auto_phone.
 *
 * Cold-start: applyToApplication(Application) updates the process
 * Configuration so the first windowBackground and night resources match the
 * last known preference (including persisted forum_auto_theme).
 */
final class ThemeManager {
    static final String AMOLED = "amoled";
    static final String AUTO_FORUM = "auto_forum";
    static final String AUTO_PHONE = "auto_phone";
    static final String DARK = "dark";
    private static final String FORUM_AUTO = "auto";
    private static final String FORUM_DARK = "dark";
    private static final String FORUM_LIGHT = "light";
    private static final String LEGACY_SYSTEM = "system";
    static final String LIGHT = "light";
    static final String SYSTEM = "auto_forum";

    /** UI_MODE_NIGHT_YES */
    private static final int NIGHT_YES = 0x20;
    /** UI_MODE_NIGHT_NO */
    private static final int NIGHT_NO = 0x10;
    private static final int UI_MODE_NIGHT_MASK = 0x30;

    /**
     * Apply resolved night mode to the Application resources as early as
     * possible (call from HcfApplication.onCreate() before activities).
     */
    static void applyToApplication(Application application) {
        if (application == null) {
            return;
        }
        try {
            Context base = application.getBaseContext() != null ? application.getBaseContext() : application;
            String mode = mode(base);
            Configuration current = application.getResources().getConfiguration();
            int night = resolvedNightMode(base, current, mode);
            if ((current.uiMode & UI_MODE_NIGHT_MASK) == night) {
                return;
            }
            Configuration updated = new Configuration(current);
            updated.uiMode = night | (updated.uiMode & ~UI_MODE_NIGHT_MASK);
            Resources resources = application.getResources();
            //noinspection deprecation
            resources.updateConfiguration(updated, resources.getDisplayMetrics());
        } catch (Throwable unused) {
        }
    }

    static Context wrap(Context context) {
        if (context == null) {
            return null;
        }
        try {
            String mode = mode(context);
            Configuration configuration = context.getResources().getConfiguration();
            int resolvedNightMode = resolvedNightMode(context, configuration, mode);
            if ((configuration.uiMode & UI_MODE_NIGHT_MASK) == resolvedNightMode) {
                return context;
            }
            Configuration configuration2 = new Configuration(configuration);
            configuration2.uiMode = resolvedNightMode | (configuration2.uiMode & ~UI_MODE_NIGHT_MASK);
            return context.createConfigurationContext(configuration2);
        } catch (Throwable unused) {
            return context;
        }
    }

    static void prepare(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
    }

    static void apply(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
        applySystemBars(activity);
    }

    static int resolvedNightMode(Context context) {
        return resolvedNightMode(context, context.getResources().getConfiguration(), mode(context));
    }

    private static int resolvedNightMode(Context context, Configuration configuration, String str) {
        if (DARK.equals(str) || AMOLED.equals(str)) {
            return NIGHT_YES;
        }
        if (LIGHT.equals(str)) {
            return NIGHT_NO;
        }
        if (AUTO_PHONE.equals(str)) {
            return phoneNightMode(configuration);
        }
        // auto_forum: prefer last known forum day/night so cold start matches
        // the previous session instead of flashing phone light first.
        String forumAutoTheme = forumAutoTheme(context);
        if (FORUM_DARK.equals(forumAutoTheme)) {
            return NIGHT_YES;
        }
        if (FORUM_LIGHT.equals(forumAutoTheme)) {
            return NIGHT_NO;
        }
        return phoneNightMode(configuration);
    }

    private static int phoneNightMode(Configuration configuration) {
        return (configuration.uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES ? NIGHT_YES : NIGHT_NO;
    }

    static String signature(Context context) {
        return mode(context) + ":" + resolvedNightMode(context) + (isAmoled(context) ? ":amoled" : "");
    }

    static boolean changedSince(Context context, String str) {
        try {
            return str == null || !str.equals(signature(context));
        } catch (Throwable unused) {
            return false;
        }
    }

    static String webColorScheme(Context context) {
        return resolvedNightMode(context) == NIGHT_YES ? "dark" : "light";
    }

    static void applySystemBars(Activity activity) {
        try {
            boolean isDark = isDark(activity);
            int color = isAmoled(activity) ? 0xFF000000 : activity.getColor(R.color.hcf_bg);
            activity.getWindow().setStatusBarColor(color);
            activity.getWindow().setNavigationBarColor(color);
            int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
            int i = !isDark ? systemUiVisibility | 8192 : systemUiVisibility & ~8192;
            activity.getWindow().getDecorView().setSystemUiVisibility(!isDark ? i | 16 : i & ~16);
        } catch (Throwable unused) {
        }
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES;
    }

    static boolean isAmoled(Context context) {
        return AMOLED.equals(mode(context));
    }

    static boolean isAutoForum(Context context) {
        return AUTO_FORUM.equals(mode(context));
    }

    static boolean isAutoPhone(Context context) {
        return AUTO_PHONE.equals(mode(context));
    }

    static String mode(Context context) {
        if (context == null) {
            return DARK;
        }
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("app_theme", DARK);
            if (!LEGACY_SYSTEM.equals(string)) {
                return (AUTO_FORUM.equals(string) || AUTO_PHONE.equals(string) || LIGHT.equals(string)
                        || DARK.equals(string) || AMOLED.equals(string)) ? string : DARK;
            }
            sharedPreferences.edit().putString("app_theme", AUTO_FORUM).apply();
            return AUTO_FORUM;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try {
                    sharedPreferences.edit().remove("app_theme").apply();
                } catch (Throwable unused2) {
                }
            }
            return DARK;
        }
    }

    static String label(Context context) {
        String mode = mode(context);
        return AUTO_PHONE.equals(mode) ? "Auto • Phone"
                : LIGHT.equals(mode) ? "Day (Light)"
                : DARK.equals(mode) ? "Night (Dark)"
                : AMOLED.equals(mode) ? "AMOLED Black"
                : "Auto • Forum";
    }

    static boolean updateForumAutoTheme(Context context, String str) {
        if (context == null || !AUTO_FORUM.equals(mode(context))) {
            return false;
        }
        String lowerCase = str == null ? "" : str.trim().toLowerCase();
        String str2 = FORUM_DARK;
        if (!"dark".equals(lowerCase) && !"night".equals(lowerCase) && !"2".equals(lowerCase)) {
            str2 = FORUM_LIGHT;
            if (!"light".equals(lowerCase) && !"day".equals(lowerCase) && !"1".equals(lowerCase)) {
                if (!FORUM_AUTO.equals(lowerCase) && !LEGACY_SYSTEM.equals(lowerCase)
                        && !"phone".equals(lowerCase) && !"0".equals(lowerCase)) {
                    return false;
                }
                str2 = FORUM_AUTO;
            }
        }
        if (str2.equals(forumAutoTheme(context))) {
            return false;
        }
        try {
            context.getSharedPreferences("hcf_app", 0).edit()
                    .putString("forum_auto_theme", str2)
                    .putLong("forum_auto_theme_updated_at", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Throwable unused) {
            return false;
        }
    }

    static String forumAutoTheme(Context context) {
        if (context == null) {
            return FORUM_AUTO;
        }
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("forum_auto_theme", FORUM_AUTO);
            return (FORUM_LIGHT.equals(string) || FORUM_DARK.equals(string)) ? string : FORUM_AUTO;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try {
                    sharedPreferences.edit().remove("forum_auto_theme").apply();
                } catch (Throwable unused2) {
                }
            }
            return FORUM_AUTO;
        }
    }

    static String autoSourceLabel(Context context) {
        String mode = mode(context);
        if (AUTO_PHONE.equals(mode)) {
            return resolvedNightMode(context) == NIGHT_YES ? "Auto • Phone Dark" : "Auto • Phone Light";
        }
        if (!AUTO_FORUM.equals(mode)) {
            return label(context);
        }
        String forumAutoTheme = forumAutoTheme(context);
        return FORUM_DARK.equals(forumAutoTheme) ? "Auto • Forum Dark"
                : FORUM_LIGHT.equals(forumAutoTheme) ? "Auto • Forum Light"
                : resolvedNightMode(context) == NIGHT_YES
                ? "Auto • Forum Auto → Phone Dark"
                : "Auto • Forum Auto → Phone Light";
    }

    static String next(String str) {
        return (AUTO_FORUM.equals(str) || LEGACY_SYSTEM.equals(str)) ? AUTO_PHONE
                : AUTO_PHONE.equals(str) ? LIGHT
                : LIGHT.equals(str) ? DARK
                : DARK.equals(str) ? AMOLED
                : AUTO_FORUM;
    }

    private ThemeManager() {
    }
}
