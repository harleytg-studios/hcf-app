package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * HCF_AUTHENTICATOR_TOP_LEVEL_SUBSETTINGS_V4_NEW_BADGE
 *
 * Moves HCF Authenticator out of Account Controls and into its own top-level
 * Account & Security subsettings panel. The panel is created with the same
 * private connectedSettingsPanel renderer used by the rest of Settings, so it
 * participates in the normal HCF accordion UI instead of introducing a second
 * settings design.
 *
 * Nearata forum/server controls remain under Account Controls > Two-Factor
 * Authentication. HCF Authenticator owns only the local encrypted TOTP setup,
 * live passcodes, scanner/import tools and local removal controls.
 *
 * Before enrollment the authenticator panel shows the complete QR / Setup-key
 * workflow. Once a working local TOTP secret exists it becomes a compact
 * code-first panel. The top-level HCF Authenticator card carries the same
 * compact NEW badge used by the new hamburger-menu shortcuts.
 */
public final class HcfAuthenticatorAdaptiveSettingsUi {
    private static final String AUTH_PANEL_TAG = "hcf_authenticator_top_level_subsetting_v4";
    private static final String AUTH_SUMMARY_TAG = AUTH_PANEL_TAG + ":summary";
    private static final String AUTH_NEW_BADGE_TAG = AUTH_PANEL_TAG + ":new_badge";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS = new WeakHashMap<>();
    private static boolean installed;

    private HcfAuthenticatorAdaptiveSettingsUi() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (appContext instanceof Application) install((Application) appContext);
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
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (isSettings(activity)) {
                    observe(activity);
                    schedule(activity);
                }
            }

            @Override public void onActivityResumed(Activity activity) {
                if (isSettings(activity)) {
                    observe(activity);
                    schedule(activity);
                }
            }

            @Override public void onActivityDestroyed(Activity activity) { remove(activity); }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    private static boolean isSettings(Activity activity) {
        return activity != null
                && "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity"
                .equals(activity.getClass().getName());
    }

    private static void observe(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> apply(activity);
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    private static void remove(Activity activity) {
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

    private static void schedule(Activity activity) {
        MAIN.postDelayed(() -> apply(activity), 70L);
        MAIN.postDelayed(() -> apply(activity), 180L);
        MAIN.postDelayed(() -> apply(activity), 360L);
        MAIN.postDelayed(() -> apply(activity), 650L);
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        TextView codeTitle = findText(root, "CURRENT 6-DIGIT PASSCODE");
        if (codeTitle == null) return;

        ensureTopLevelPanel(activity, (ViewGroup) root);

        boolean configured = isConfigured(activity);
        View heading = findText(root, "HCF AUTHENTICATOR • FULL SETTINGS");
        View localStatus = findTextStarting(root, "HCF Authenticator configured on this device");
        if (localStatus == null) {
            localStatus = findTextStarting(root, "HCF Authenticator not configured on this device");
        }
        View nearataStatus = parentOf(findText(root, "Nearata 2FA controls"));
        View setupPanel = parentOf(findText(root, "SET UP HCF AUTHENTICATOR"));
        View finishPanel = parentOf(findText(root, "FINISH IN FORUM USER SETTINGS"));
        View managePanel = parentOf(findText(root, "LOCAL AUTHENTICATOR STORAGE"));
        View footer = findTextStarting(root, "RFC 6238 TOTP");

        if (configured) {
            setVisible(heading, false);
            setVisible(localStatus, false);
            setVisible(nearataStatus, false);
            setVisible(setupPanel, false);
            setVisible(finishPanel, false);
            setVisible(footer, false);
            setVisible(managePanel, true);
            compactManagePanel(managePanel, activity);
        } else {
            setVisible(heading, true);
            setVisible(localStatus, true);
            setVisible(nearataStatus, true);
            setVisible(setupPanel, true);
            setVisible(finishPanel, true);
            setVisible(footer, true);
            setVisible(managePanel, false);
        }

        View summary = root.findViewWithTag(AUTH_SUMMARY_TAG);
        if (summary instanceof TextView) {
            ((TextView) summary).setText(configured
                    ? "Configured • live 6-digit passcodes"
                    : "Offline 2FA passcodes and setup tools");
        }
    }

    /**
     * Pull the local-authenticator controls out of Account Controls and insert a
     * true sibling panel next to Account & Identity and Account Controls.
     */
    private static void ensureTopLevelPanel(Activity activity, ViewGroup root) {
        if (root.findViewWithTag(AUTH_PANEL_TAG) != null) return;

        TextView twoFactorTitle = findText(root, "Two-Factor Authentication");
        TextView codeTitle = findText(root, "CURRENT 6-DIGIT PASSCODE");
        if (twoFactorTitle == null || codeTitle == null) return;

        View twoFactorPanel = findConnectedPanel(twoFactorTitle);
        if (!(twoFactorPanel instanceof LinearLayout)) return;

        List<View> authViews = new ArrayList<>();
        addIfPresent(authViews, findText(root, "HCF AUTHENTICATOR • FULL SETTINGS"));
        View status = findTextStarting(root, "HCF Authenticator configured on this device");
        if (status == null) status = findTextStarting(root, "HCF Authenticator not configured on this device");
        addIfPresent(authViews, status);
        addIfPresent(authViews, parentOf(findText(root, "CURRENT 6-DIGIT PASSCODE")));
        addIfPresent(authViews, parentOf(findText(root, "SET UP HCF AUTHENTICATOR")));
        addIfPresent(authViews, parentOf(findText(root, "LOCAL AUTHENTICATOR STORAGE")));
        addIfPresent(authViews, findTextStarting(root, "RFC 6238 TOTP"));
        if (authViews.isEmpty()) return;

        TextView accountControlsSubtitle = findText(root,
                "Profile, password, email and session security shortcuts");
        if (accountControlsSubtitle == null) return;
        View accountControlsTopPanel = findConnectedPanel(accountControlsSubtitle);
        if (!(accountControlsTopPanel instanceof LinearLayout)
                || !(accountControlsTopPanel.getParent() instanceof LinearLayout)) return;
        LinearLayout settingsContent = (LinearLayout) accountControlsTopPanel.getParent();

        LinearLayout authContent = new LinearLayout(activity);
        authContent.setOrientation(LinearLayout.VERTICAL);
        for (View view : authViews) {
            detach(view);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (authContent.getChildCount() > 0) lp.topMargin = dp(activity, 9);
            authContent.addView(view, lp);
        }

        setTwoFactorSummary((LinearLayout) twoFactorPanel,
                "Nearata TwoFactor • forum User Settings");

        boolean configured = isConfigured(activity);
        String summary = configured
                ? "Configured • live 6-digit passcodes"
                : "Offline 2FA passcodes and setup tools";

        View authPanel = invokeConnectedSettingsPanel(activity,
                "HCF Authenticator", summary, authContent, false);
        if (authPanel == null) {
            authPanel = buildFallbackPanel(activity, summary, authContent);
        }
        authPanel.setTag(AUTH_PANEL_TAG);
        TextView summaryView = findText(authPanel, summary);
        if (summaryView != null) summaryView.setTag(AUTH_SUMMARY_TAG);
        ImageView icon = findFirstImageView(authPanel);
        if (icon != null) {
            try { icon.setImageResource(R.drawable.fa_lock); } catch (Throwable ignored) {}
        }
        addNewBadge(activity, authPanel);

        int index = settingsContent.indexOfChild(accountControlsTopPanel);
        settingsContent.addView(authPanel,
                Math.min(index + 1, settingsContent.getChildCount()));
    }

    /** Adds the compact cyan NEW pill beside the HCF Authenticator title. */
    private static void addNewBadge(Activity activity, View authPanel) {
        if (authPanel == null || authPanel.findViewWithTag(AUTH_NEW_BADGE_TAG) != null) return;
        TextView heading = findText(authPanel, "HCF Authenticator");
        if (heading == null || !(heading.getParent() instanceof LinearLayout)) return;

        LinearLayout parent = (LinearLayout) heading.getParent();
        TextView badge = text(activity, "NEW", 8,
                color(activity, R.color.hcf_bg, Color.rgb(8, 13, 17)));
        badge.setTag(AUTH_NEW_BADGE_TAG);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setPadding(dp(activity, 7), dp(activity, 2), dp(activity, 7), dp(activity, 2));
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        badge.setBackground(roundRect(activity, cyan, cyan, 8));

        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            int index = parent.indexOfChild(heading);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 20));
            badgeLp.leftMargin = dp(activity, 7);
            parent.addView(badge, Math.min(index + 1, parent.getChildCount()), badgeLp);
            return;
        }

        int index = parent.indexOfChild(heading);
        if (index < 0) return;
        parent.removeView(heading);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 20));
        badgeLp.leftMargin = dp(activity, 7);
        row.addView(badge, badgeLp);
        parent.addView(row, index, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Use the app's own private Settings renderer so this panel is visually native. */
    private static View invokeConnectedSettingsPanel(Activity activity, String title,
                                                     String subtitle, View content,
                                                     boolean expanded) {
        Class<?> cursor = activity.getClass();
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod("connectedSettingsPanel",
                        String.class, String.class, View.class, boolean.class);
                method.setAccessible(true);
                Object result = method.invoke(activity, title, subtitle, content, expanded);
                return result instanceof View ? (View) result : null;
            } catch (NoSuchMethodException missing) {
                cursor = cursor.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** Only used if a future Settings implementation removes connectedSettingsPanel. */
    private static View buildFallbackPanel(Activity activity, String summary, View content) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 15));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        header.setClickable(true);
        header.setFocusable(true);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.fa_lock);
        try { icon.setColorFilter(color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240))); }
        catch (Throwable ignored) {}
        header.addView(icon, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsLp.leftMargin = dp(activity, 12);

        TextView heading = text(activity, "HCF Authenticator", 14,
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(heading);

        TextView summaryView = text(activity, summary, 10,
                color(activity, R.color.hcf_muted, Color.LTGRAY));
        summaryView.setTag(AUTH_SUMMARY_TAG);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.topMargin = dp(activity, 3);
        labels.addView(summaryView, summaryLp);
        header.addView(labels, labelsLp);

        TextView arrow = text(activity, "›", 22,
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)));
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 40)));

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(activity, 14), 0, dp(activity, 14), dp(activity, 14));
        shell.addView(content);
        shell.setVisibility(View.GONE);
        header.setOnClickListener(v -> {
            boolean open = shell.getVisibility() != View.VISIBLE;
            shell.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setText(open ? "⌄" : "›");
        });

        panel.addView(header);
        panel.addView(shell);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.bottomMargin = dp(activity, 12);
        panel.setLayoutParams(panelLp);
        return panel;
    }

    private static void setTwoFactorSummary(LinearLayout panel, String summary) {
        try {
            View header = panel.getChildAt(0);
            if (!(header instanceof LinearLayout)) return;
            View labels = ((LinearLayout) header).getChildAt(0);
            if (!(labels instanceof LinearLayout)) return;
            if (((LinearLayout) labels).getChildCount() < 2) return;
            View summaryView = ((LinearLayout) labels).getChildAt(1);
            if (summaryView instanceof TextView) ((TextView) summaryView).setText(summary);
        } catch (Throwable ignored) {}
    }

    private static void compactManagePanel(View managePanel, Activity activity) {
        if (!(managePanel instanceof ViewGroup)) return;
        ViewGroup panel = (ViewGroup) managePanel;
        setVisible(findText(panel, "LOCAL AUTHENTICATOR STORAGE"), false);
        setVisible(findTextStarting(panel, "Only the TOTP setup secret is stored by HCF"), false);
        setVisible(findTextStarting(panel, "Removing the local key does not disable Nearata 2FA"), false);
        if (panel instanceof LinearLayout) {
            int pad = dp(activity, 6);
            panel.setPadding(pad, pad, pad, pad);
        }
    }

    /** Finds either a native connectedSettingsPanel or one of the inner subsetting panels. */
    private static View findConnectedPanel(View titleOrSubtitle) {
        View current = titleOrSubtitle;
        while (current != null && current.getParent() instanceof View) {
            current = (View) current.getParent();
            if (!(current instanceof LinearLayout)) continue;
            LinearLayout layout = (LinearLayout) current;
            if (layout.getChildCount() == 2
                    && layout.getChildAt(0) instanceof LinearLayout
                    && layout.getChildAt(1) instanceof LinearLayout) {
                return layout;
            }
        }
        return null;
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) { return false; }
    }

    private static void addIfPresent(List<View> views, View view) {
        if (view != null && !views.contains(view)) views.add(view);
    }

    private static View parentOf(View view) {
        return view != null && view.getParent() instanceof View ? (View) view.getParent() : null;
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private static TextView findText(View view, String exact) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && exact.equals(value.toString().trim())) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTextStarting(View view, String prefix) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().trim().startsWith(prefix)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextStarting(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ImageView findFirstImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = findFirstImageView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private static GradientDrawable roundRect(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int color(Context context, int resId, int fallback) {
        try { return context.getColor(resId); }
        catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
