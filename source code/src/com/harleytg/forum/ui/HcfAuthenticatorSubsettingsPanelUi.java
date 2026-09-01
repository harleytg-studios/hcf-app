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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Makes HCF Authenticator its own first-class Settings subsetting.
 *
 * Account Controls becomes:
 *   Forum Profile
 *   Password, Email & Sessions
 *   Two-Factor Authentication      (Nearata forum/server controls)
 *   HCF Authenticator              (local TOTP setup + live code)
 *
 * The adaptive authenticator helper still controls setup-vs-configured density.
 */
public final class HcfAuthenticatorSubsettingsPanelUi {
    private static final String PANEL_TAG = "hcf_authenticator_own_subsetting_v1";
    private static final String PREF_KEY = "account_controls_subsetting_hcf_authenticator";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS = new WeakHashMap<>();
    private static boolean installed;

    private HcfAuthenticatorSubsettingsPanelUi() {}

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
        MAIN.postDelayed(() -> apply(activity), 110L);
        MAIN.postDelayed(() -> apply(activity), 280L);
        MAIN.postDelayed(() -> apply(activity), 620L);
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        if (root.findViewWithTag(PANEL_TAG) != null) return;

        TextView twoFactorTitle = findText(root, "Two-Factor Authentication");
        TextView codeTitle = findText(root, "CURRENT 6-DIGIT PASSCODE");
        if (twoFactorTitle == null || codeTitle == null) return;

        View twoFactorPanel = findSubsettingPanel(twoFactorTitle);
        if (!(twoFactorPanel instanceof LinearLayout)) return;
        if (!(twoFactorPanel.getParent() instanceof LinearLayout)) return;
        LinearLayout accountControls = (LinearLayout) twoFactorPanel.getParent();

        View authBody = ancestorUntil(codeTitle, twoFactorPanel);
        if (!(authBody instanceof LinearLayout)) return;
        LinearLayout sourceBody = (LinearLayout) authBody;

        List<View> authViews = new ArrayList<>();
        addIfPresent(authViews, findText(root, "HCF AUTHENTICATOR • FULL SETTINGS"));
        addIfPresent(authViews, findTextStarting(root, "HCF Authenticator configured on this device"));
        addIfPresent(authViews, findTextStarting(root, "HCF Authenticator not configured on this device"));
        addIfPresent(authViews, parentOf(findText(root, "CURRENT 6-DIGIT PASSCODE")));
        addIfPresent(authViews, parentOf(findText(root, "SET UP HCF AUTHENTICATOR")));
        addIfPresent(authViews, parentOf(findText(root, "LOCAL AUTHENTICATOR STORAGE")));
        addIfPresent(authViews, findTextStarting(root, "RFC 6238 TOTP"));

        if (authViews.isEmpty()) return;

        LinearLayout authContent = new LinearLayout(activity);
        authContent.setOrientation(LinearLayout.VERTICAL);
        for (View view : authViews) {
            detach(view);
            authContent.addView(view, matchWrap(activity, authContent.getChildCount() == 0 ? 0 : 9));
        }

        // The Nearata panel now describes only forum/server-side 2FA.
        setTwoFactorSummary((LinearLayout) twoFactorPanel,
                "Nearata TwoFactor • forum User Settings");

        String summary = isConfigured(activity)
                ? "Configured • live 6-digit passcodes"
                : "Not configured • QR code or Setup key";
        View authPanel = buildSubsetting(activity, "HCF Authenticator", summary, authContent);
        authPanel.setTag(PANEL_TAG);

        int index = accountControls.indexOfChild(twoFactorPanel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, 9);
        accountControls.addView(authPanel, Math.min(index + 1, accountControls.getChildCount()), lp);
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

    private static View buildSubsetting(Activity activity, String title, String summary, View content) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 13));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription(title + ". " + summary);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(activity, title, 13,
                color(activity, R.color.hcf_text, Color.WHITE));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(heading);
        TextView summaryView = text(activity, summary, 10,
                color(activity, R.color.hcf_muted, Color.LTGRAY));
        summaryView.setMaxLines(2);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.topMargin = dp(activity, 3);
        labels.addView(summaryView, summaryLp);
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text(activity, "›", 22,
                color(activity, R.color.hcf_accent_text, Color.rgb(0, 184, 240)));
        arrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 36)));

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(activity, 14), 0, dp(activity, 14), dp(activity, 13));
        shell.addView(content);

        boolean expanded = activity.getSharedPreferences(AppPrefs.FILE, 0)
                .getBoolean(PREF_KEY, true);
        applyExpanded(shell, arrow, expanded);
        header.setOnClickListener(v -> {
            boolean next = shell.getVisibility() != View.VISIBLE;
            applyExpanded(shell, arrow, next);
            activity.getSharedPreferences(AppPrefs.FILE, 0).edit().putBoolean(PREF_KEY, next).apply();
        });

        panel.addView(header);
        panel.addView(shell);
        return panel;
    }

    private static void applyExpanded(View body, TextView arrow, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        arrow.setText(expanded ? "⌄" : "›");
    }

    private static View findSubsettingPanel(View title) {
        View current = title;
        while (current != null && current.getParent() instanceof View) {
            current = (View) current.getParent();
            if (!(current instanceof LinearLayout)) continue;
            LinearLayout layout = (LinearLayout) current;
            if (layout.getChildCount() == 2) {
                View first = layout.getChildAt(0);
                View second = layout.getChildAt(1);
                if (first instanceof LinearLayout && second instanceof LinearLayout) return layout;
            }
        }
        return null;
    }

    private static View ancestorUntil(View child, View stop) {
        View current = child;
        View candidate = child;
        while (current != null && current != stop && current.getParent() instanceof View) {
            candidate = current;
            current = (View) current.getParent();
            if (current != stop) candidate = current;
        }
        return candidate;
    }

    private static void addIfPresent(List<View> list, View view) {
        if (view != null && !list.contains(view)) list.add(view);
    }

    private static View parentOf(View view) {
        return view != null && view.getParent() instanceof View ? (View) view.getParent() : null;
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) { return false; }
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

    private static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap(Context context, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(context, topDp);
        return lp;
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
