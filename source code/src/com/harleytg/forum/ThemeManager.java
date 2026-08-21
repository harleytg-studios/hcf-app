package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/* loaded from: classes.dex */
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

    static Context wrap(Context context) {
        if (context == null) {
            return null;
        }
        try {
            String mode = mode(context);
            Configuration configuration = context.getResources().getConfiguration();
            int resolvedNightMode = resolvedNightMode(context, configuration, mode);
            if ((configuration.uiMode & 48) == resolvedNightMode) {
                return context;
            }
            Configuration configuration2 = new Configuration(configuration);
            configuration2.uiMode = resolvedNightMode | (configuration2.uiMode & (-49));
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
        if ("dark".equals(str) || AMOLED.equals(str)) {
            return 32;
        }
        if ("light".equals(str)) {
            return 16;
        }
        if (AUTO_PHONE.equals(str)) {
            return phoneNightMode(configuration);
        }
        String forumAutoTheme = forumAutoTheme(context);
        if ("dark".equals(forumAutoTheme)) {
            return 32;
        }
        if ("light".equals(forumAutoTheme)) {
            return 16;
        }
        return phoneNightMode(configuration);
    }

    private static int phoneNightMode(Configuration configuration) {
        return (configuration.uiMode & 48) == 32 ? 32 : 16;
    }

    static String signature(Context context) {
        return mode(context) + ":" + resolvedNightMode(context);
    }

    static boolean changedSince(Context context, String str) {
        try {
            return (str == null || str.equals(signature(context))) ? false : true;
        } catch (Throwable unused) {
            return false;
        }
    }

    static String webColorScheme(Context context) {
        return resolvedNightMode(context) == 32 ? "dark" : "light";
    }

    static void applySystemBars(Activity activity) {
        try {
            boolean isDark = isDark(activity);
            int color = isAmoled(activity) ? -16777216 : activity.getColor(R.color.hcf_bg);
            activity.getWindow().setStatusBarColor(color);
            activity.getWindow().setNavigationBarColor(color);
            int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
            int i = !isDark ? systemUiVisibility | 8192 : systemUiVisibility & (-8193);
            activity.getWindow().getDecorView().setSystemUiVisibility(!isDark ? i | 16 : i & (-17));
        } catch (Throwable unused) {
        }
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & 48) == 32;
    }

    static boolean isAmoled(Context context) {
        return AMOLED.equals(mode(context));
    }

    static boolean isAutoForum(Context context) {
        return "auto_forum".equals(mode(context));
    }

    static boolean isAutoPhone(Context context) {
        return AUTO_PHONE.equals(mode(context));
    }

    static String mode(Context context) {
        if (context == null) {
            return "auto_forum";
        }
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("app_theme", "auto_forum");
            if (!LEGACY_SYSTEM.equals(string)) {
                return ("auto_forum".equals(string) || AUTO_PHONE.equals(string) || "light".equals(string) || "dark".equals(string) || AMOLED.equals(string)) ? string : "auto_forum";
            }
            sharedPreferences.edit().putString("app_theme", "auto_forum").apply();
            return "auto_forum";
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try {
                    sharedPreferences.edit().remove("app_theme").apply();
                } catch (Throwable unused2) {
                }
            }
            return "auto_forum";
        }
    }

    static String label(Context context) {
        String mode = mode(context);
        return AUTO_PHONE.equals(mode) ? "Auto • Phone" : "light".equals(mode) ? "Day (Light)" : "dark".equals(mode) ? "Night (Dark)" : AMOLED.equals(mode) ? "AMOLED Black" : "Auto • Forum";
    }

    static boolean updateForumAutoTheme(Context context, String str) {
        if (context == null || !"auto_forum".equals(mode(context))) {
            return false;
        }
        String lowerCase = str == null ? "" : str.trim().toLowerCase();
        String str2 = "dark";
        if (!"dark".equals(lowerCase) && !"night".equals(lowerCase) && !"2".equals(lowerCase)) {
            str2 = "light";
            if (!"light".equals(lowerCase) && !"day".equals(lowerCase) && !"1".equals(lowerCase)) {
                if (!FORUM_AUTO.equals(lowerCase) && !LEGACY_SYSTEM.equals(lowerCase) && !"phone".equals(lowerCase) && !"0".equals(lowerCase)) {
                    return false;
                }
                str2 = FORUM_AUTO;
            }
        }
        if (str2.equals(forumAutoTheme(context))) {
            return false;
        }
        try {
            context.getSharedPreferences("hcf_app", 0).edit().putString("forum_auto_theme", str2).putLong("forum_auto_theme_updated_at", System.currentTimeMillis()).apply();
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
            return ("light".equals(string) || "dark".equals(string)) ? string : FORUM_AUTO;
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
            return resolvedNightMode(context) == 32 ? "Auto • Phone Dark" : "Auto • Phone Light";
        }
        if (!"auto_forum".equals(mode)) {
            return label(context);
        }
        String forumAutoTheme = forumAutoTheme(context);
        return "dark".equals(forumAutoTheme) ? "Auto • Forum Dark" : "light".equals(forumAutoTheme) ? "Auto • Forum Light" : resolvedNightMode(context) == 32 ? "Auto • Forum Auto → Phone Dark" : "Auto • Forum Auto → Phone Light";
    }

    static String next(String str) {
        return ("auto_forum".equals(str) || LEGACY_SYSTEM.equals(str)) ? AUTO_PHONE : AUTO_PHONE.equals(str) ? "light" : "light".equals(str) ? "dark" : "dark".equals(str) ? AMOLED : "auto_forum";
    }

    private ThemeManager() {
    }
}
