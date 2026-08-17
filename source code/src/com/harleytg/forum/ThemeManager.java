package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

final class ThemeManager {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String AMOLED = "amoled";

    /**
     * Wrap an Activity base context before Activity.onCreate so Android resolves
     * values/values-night, styles and drawables from the selected app theme.
     * This is the important part of the system-wide light-mode fix.
     */
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

    /** Must be called before Activity.onCreate. */
    static void prepare(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
    }

    /** Safe to call again after Activity.onCreate for chrome/system bars. */
    static void apply(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
        applySystemBars(activity);
    }

    static int resolvedNightMode(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        return resolvedNightMode(context, configuration, mode(context));
    }

    private static int resolvedNightMode(Context context, Configuration configuration, String selected) {
        if (DARK.equals(selected) || AMOLED.equals(selected)) {
            return Configuration.UI_MODE_NIGHT_YES;
        }
        if (LIGHT.equals(selected)) {
            return Configuration.UI_MODE_NIGHT_NO;
        }
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
