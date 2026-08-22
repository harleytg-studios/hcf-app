package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.Map;

/** Shared state and Android integration helpers for the versioned App Setup Center. */
final class SetupCenter {
    static final int CURRENT_SETUP_VERSION = 1;
    static final int CURRENT_WELCOME_VERSION = 1;
    static final String EXTRA_AUTO_LAUNCHED = "hcf_setup_auto_launched";
    static final String PRIMARY_FORUM_HOST = "forum.harleytg.com";
    static final String BACKUP_FORUM_HOST = "harleysclan.freeflarum.com";

    private static final String DRAWER_TAG = "hcf_app_setup_drawer";

    static final class ForumLinksState {
        final boolean inspectable;
        final boolean ready;
        final String detail;

        ForumLinksState(boolean inspectable, boolean ready, String detail) {
            this.inspectable = inspectable;
            this.ready = ready;
            this.detail = detail;
        }
    }

    static boolean shouldShowWelcome(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int storedVersion = prefs.getInt(AppPrefs.WELCOME_VERSION, 0);
        boolean seen = prefs.getBoolean(AppPrefs.WELCOME_SEEN, false);
        return storedVersion < CURRENT_WELCOME_VERSION || !seen;
    }

    static void markWelcomeSeen(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.WELCOME_VERSION, CURRENT_WELCOME_VERSION)
                .putBoolean(AppPrefs.WELCOME_SEEN, true)
                .apply();
    }

    static boolean shouldAutoLaunch(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int storedVersion = prefs.getInt(AppPrefs.SETUP_VERSION, 0);
        boolean seen = prefs.getBoolean(AppPrefs.SETUP_SEEN, false);
        return storedVersion < CURRENT_SETUP_VERSION || !seen;
    }

    static void markSeen(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int oldVersion = prefs.getInt(AppPrefs.SETUP_VERSION, 0);
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true);
        if (oldVersion < CURRENT_SETUP_VERSION) {
            editor.putBoolean(AppPrefs.SETUP_COMPLETED, false);
        }
        editor.apply();
    }

    static void markCompleted(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true)
                .putBoolean(AppPrefs.SETUP_COMPLETED, true)
                .apply();
    }

    static void markSkipped(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true)
                .putBoolean(AppPrefs.SETUP_COMPLETED, false)
                .apply();
    }

    static void maybeLaunchForMainActivity(MainActivity activity, android.os.Bundle savedInstanceState) {
        if (activity == null) return;
        installDrawerEntry(activity);
        if (savedInstanceState != null) return;

        // New onboarding gate: welcome the user before asking them to configure
        // optional Android integrations. The welcome screen records its own state
        // only after the user chooses Setup or Continue, so an interrupted first
        // launch can safely show the welcome screen again.
        if (shouldShowWelcome(activity)) {
            Intent welcome = new Intent(activity, WelcomeActivity.class);
            welcome.putExtra(EXTRA_AUTO_LAUNCHED, true);
            activity.startActivity(welcome);
            AppLogger.info(activity, "app_welcome", "auto_launch_v" + CURRENT_WELCOME_VERSION);
            return;
        }

        // Compatibility path for an install whose welcome screen was already
        // acknowledged but whose current Setup Center version has never been seen.
        if (!shouldAutoLaunch(activity)) return;
        markSeen(activity);
        Intent intent = new Intent(activity, SetupActivity.class);
        intent.putExtra(EXTRA_AUTO_LAUNCHED, true);
        activity.startActivity(intent);
        AppLogger.info(activity, "app_setup", "auto_launch_v" + CURRENT_SETUP_VERSION);
    }

    static void installDrawerEntry(final MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            View settingsView = activity.findViewById(R.id.drawerSettings);
            if (!(settingsView instanceof Button)) return;
            ViewParentResult parentResult = findLinearParent(settingsView);
            if (parentResult == null) return;
            LinearLayout parent = parentResult.parent;

            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (DRAWER_TAG.equals(child.getTag())) return;
            }

            Button setup = new Button(activity);
            setup.setTag(DRAWER_TAG);
            setup.setText("App Setup");
            setup.setContentDescription("Open App Setup Center");
            setup.setAllCaps(false);
            setup.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            setup.setTextColor(activity.getColor(R.color.hcf_text));
            setup.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    activity.getResources().getDimension(R.dimen.drawer_item_text));
            setup.setIncludeFontPadding(false);
            setup.setMinWidth(0);
            setup.setMinHeight(0);
            setup.setMinimumWidth(0);
            setup.setMinimumHeight(0);
            setup.setPadding(dp(activity, 14), 0, dp(activity, 12), 0);
            setup.setBackgroundResource(R.drawable.drawer_item_background);
            setup.setStateListAnimator(null);
            FaIcons.applyStart(setup, R.drawable.fa_shield);
            setup.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        activity.startActivity(new Intent(activity, SetupActivity.class));
                        AppLogger.info(activity, "app_setup", "drawer_open");
                    } catch (Throwable error) {
                        AppLogger.error(activity, "app_setup_drawer", error.getClass().getSimpleName());
                    }
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.getResources().getDimensionPixelSize(R.dimen.drawer_item_height));
            lp.bottomMargin = dp(activity, 5);
            int index = parent.indexOfChild(settingsView);
            parent.addView(setup, Math.max(0, index), lp);
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_setup_drawer", error.getClass().getSimpleName());
        }
    }

    private static final class ViewParentResult {
        final LinearLayout parent;
        ViewParentResult(LinearLayout parent) { this.parent = parent; }
    }

    private static ViewParentResult findLinearParent(View view) {
        if (view == null || !(view.getParent() instanceof LinearLayout)) return null;
        return new ViewParentResult((LinearLayout) view.getParent());
    }

    static ForumLinksState forumLinksState(Context context) {
        if (context == null) return new ForumLinksState(false, false, "Unable to inspect Android link settings.");
        if (Build.VERSION.SDK_INT < 31) {
            return new ForumLinksState(false, false,
                    "Android manages supported links on this version. Open Forum Link Settings to verify both HCF domains.");
        }
        try {
            DomainVerificationManager manager = context.getSystemService(DomainVerificationManager.class);
            if (manager == null) {
                return new ForumLinksState(true, false, "Android's supported-link service is unavailable.");
            }
            DomainVerificationUserState state = manager.getDomainVerificationUserState(context.getPackageName());
            if (state == null) {
                return new ForumLinksState(true, false, "No supported-link state is available yet.");
            }
            Map<String, Integer> hosts = state.getHostToStateMap();
            boolean primaryReady = hostReady(hosts, PRIMARY_FORUM_HOST);
            boolean backupReady = hostReady(hosts, BACKUP_FORUM_HOST);
            boolean handlingAllowed = state.isLinkHandlingAllowed();
            boolean ready = handlingAllowed && primaryReady && backupReady;
            String detail = ready
                    ? "Both supported forum domains are enabled to open directly in HCF."
                    : "Android controls this setting. Enable HCF for forum.harleytg.com and harleysclan.freeflarum.com.";
            return new ForumLinksState(true, ready, detail);
        } catch (Throwable error) {
            return new ForumLinksState(true, false,
                    "Android link status could not be read: " + error.getClass().getSimpleName());
        }
    }

    private static boolean hostReady(Map<String, Integer> states, String host) {
        if (states == null || host == null) return false;
        Integer value = states.get(host);
        if (value == null) return false;
        int state = value.intValue();
        return state == DomainVerificationUserState.DOMAIN_STATE_SELECTED
                || state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED;
    }

    static void openForumLinkSettings(Activity activity, int requestCode) {
        if (activity == null) return;
        Intent primary = new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS",
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivityForResult(primary, requestCode);
            AppLogger.info(activity, "forum_link_settings", "open-by-default");
            return;
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(fallback, requestCode);
                AppLogger.warn(activity, "forum_link_settings", "fallback-app-details");
                return;
            } catch (Throwable second) {
                AppLogger.error(activity, "forum_link_settings", second.getClass().getSimpleName());
            }
        }
    }

    static void openInstallSourceSettings(Activity activity, int requestCode) {
        if (activity == null) return;
        try {
            Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, requestCode);
            AppLogger.info(activity, "install_permission", "setup_center_open");
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(fallback, requestCode);
            } catch (Throwable second) {
                AppLogger.error(activity, "install_permission", second.getClass().getSimpleName());
            }
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private SetupCenter() {}
}
