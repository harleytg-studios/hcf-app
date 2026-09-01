package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * HCF_SUBSETTINGS_UI_V1
 *
 * Dedicated native owner for the Account & Security > Account Controls UI.
 * Authentication behavior stays in HcfAuthenticator; this class owns only the
 * settings/subsettings presentation, state and runtime attachment.
 */
public final class HcfSubsettingsUi {
    private static final String TAG = "hcf_account_controls_subsettings_ui_v1";
    private static final String TAG_2FA_STATUS = TAG + ":2fa_status";
    private static final String TAG_2FA_SUMMARY = TAG + ":2fa_summary";
    private static final String TAG_2FA_BUTTON = TAG + ":2fa_button";
    private static final String PREF_PREFIX = "account_controls_subsetting_";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS = new WeakHashMap<>();
    private static boolean installed;

    private HcfSubsettingsUi() {}

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
                    installObserver(activity);
                    scheduleRender(activity);
                }
            }

            @Override public void onActivityResumed(Activity activity) {
                if (isSettings(activity)) {
                    installObserver(activity);
                    scheduleRender(activity);
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) { removeObserver(activity); }
        });
    }

    private static boolean isSettings(Activity activity) {
        return activity != null
                && "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity"
                .equals(activity.getClass().getName());
    }

    private static void installObserver(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;

            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (!activity.isFinishing()) render(activity, false);
                }
            };
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    private static void removeObserver(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) {
            listener = OBSERVERS.remove(activity);
        }
        if (listener == null) return;

        try {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {
        }
    }

    private static void scheduleRender(Activity activity) {
        MAIN.postDelayed(() -> render(activity, true), 60L);
        MAIN.postDelayed(() -> render(activity, true), 180L);
        MAIN.postDelayed(() -> render(activity, true), 420L);
    }

    private static void render(Activity activity, boolean refreshExisting) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        View existing = root.findViewWithTag(TAG);
        if (existing != null) {
            if (refreshExisting) refreshTwoFactor(root);
            return;
        }

        TextView profileText = findText(root, "Open My Forum Profile");
        TextView securityText = findText(root, "Open Account Security");
        if (profileText == null || securityText == null) return;

        ViewGroup common = commonCardAncestor(profileText, securityText);
        if (!(common instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) common;

        View profileAction = clickableAncestor(profileText, card);
        View securityAction = clickableAncestor(securityText, card);
        if (profileAction == null || securityAction == null || profileAction == securityAction) return;

        ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
        ForumSecurity.Snapshot security = ForumSecurity.load(activity);
        if (!identity.loggedIn) return;

        detach(profileAction);
        detach(securityAction);
        card.removeAllViews();
        card.setTag(TAG);
        card.setPadding(0, 0, 0, 0);

        String handle = identity.username == null || identity.username.trim().isEmpty()
                ? identity.identityLabel()
                : "@" + identity.username.trim();

        card.addView(buildProfileSubsetting(activity, profileAction, handle),
                lp(activity, -1, -2, 0, 9));
        card.addView(buildSecuritySubsetting(activity, securityAction, security),
                lp(activity, -1, -2, 0, 9));
        card.addView(buildTwoFactorSubsetting(activity, security),
                lp(activity, -1, -2, 0, 0));
    }

    private static View buildProfileSubsetting(Activity activity, View profileAction, String handle) {
        LinearLayout body = body(activity);
        body.addView(detail(activity,
                "Open your public Harley's Clan Forum profile, activity and account identity."));
        styleExistingAction(activity, profileAction);
        body.addView(profileAction, lp(activity, -1, dp(activity, 52), 10, 0));
        return subsetting(activity,
                "profile",
                "Forum Profile",
                "Signed in as " + handle,
                body,
                true);
    }

    private static View buildSecuritySubsetting(Activity activity, View securityAction,
                                                ForumSecurity.Snapshot security) {
        LinearLayout body = body(activity);
        body.addView(securityStatusRow(activity, "Password",
                security.passwordControls ? "Available" : forumState(security),
                security.passwordControls));
        body.addView(securityStatusRow(activity, "Email",
                security.emailControls ? "Available" : forumState(security),
                security.emailControls), lp(activity, -1, -2, 7, 0));
        body.addView(securityStatusRow(activity, "Active sessions",
                security.sessionCount > 0 ? String.valueOf(security.sessionCount)
                        : (security.seen ? "None synced" : "Sync needed"),
                security.sessionCount > 0), lp(activity, -1, -2, 7, 0));

        if (!security.seen) {
            body.addView(detail(activity,
                    "Open Account Security once to sync the controls available for this forum account."),
                    lp(activity, -1, -2, 9, 0));
        }

        styleExistingAction(activity, securityAction);
        body.addView(securityAction, lp(activity, -1, dp(activity, 52), 11, 0));

        String summary = security.seen
                ? "Password, email and session controls"
                : "Open once to sync forum security controls";
        return subsetting(activity,
                "security",
                "Password, Email & Sessions",
                summary,
                body,
                false);
    }

    private static View buildTwoFactorSubsetting(Activity activity, ForumSecurity.Snapshot security) {
        LinearLayout body = body(activity);

        TextView authLabel = text(activity, "HCF AUTHENTICATOR", 9,
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)));
        authLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(authLabel);

        TextView status = twoFactorStatus(activity);
        status.setTag(TAG_2FA_STATUS);
        body.addView(status, lp(activity, -1, -2, 7, 0));

        body.addView(detail(activity,
                "Use HCF as your authenticator for rotating 6-digit codes. Setup supports camera QR scanning, QR screenshots, otpauth links and manual Base32 setup keys."),
                lp(activity, -1, -2, 9, 0));

        Button auth = new Button(activity);
        auth.setTag(TAG_2FA_BUTTON);
        auth.setAllCaps(false);
        auth.setText(isConfigured(activity) ? "Open HCF Authenticator" : "Set Up HCF Authenticator");
        auth.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        auth.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        auth.setTextColor(Color.BLACK);
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        auth.setBackground(roundRect(activity, cyan, cyan, 12));
        auth.setStateListAnimator(null);
        auth.setOnClickListener(v -> activity.startActivity(new Intent(activity, HcfAuthenticator.Activity.class)));
        body.addView(auth, lp(activity, -1, dp(activity, 48), 11, 0));

        TextView offline = text(activity,
                "Codes work offline after setup • secret encrypted with Android Keystore",
                9, color(activity, R.color.hcf_muted, Color.GRAY));
        offline.setGravity(Gravity.CENTER);
        body.addView(offline, lp(activity, -1, -2, 8, 0));

        String summary = isConfigured(activity)
                ? "Configured on this device"
                : (security.twoFactorControls
                        ? "Available • set up HCF Authenticator"
                        : "Set up HCF Authenticator");

        View panel = subsetting(activity,
                "two_factor",
                "Two-Factor Authentication",
                summary,
                body,
                true);
        TextView summaryView = findText(panel, summary);
        if (summaryView != null) summaryView.setTag(TAG_2FA_SUMMARY);
        return panel;
    }

    private static View subsetting(Activity activity, String key, String title,
                                   String summary, View content, boolean defaultExpanded) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)),
                13));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription(title + ". " + summary);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(activity, title, 13, color(activity, R.color.hcf_text, Color.WHITE));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(heading);

        TextView summaryView = text(activity, summary, 10,
                color(activity, R.color.hcf_muted, Color.LTGRAY));
        summaryView.setMaxLines(2);
        labels.addView(summaryView, lp(activity, -1, -2, 3, 0));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text(activity, "›", 22,
                color(activity, R.color.hcf_accent_text, Color.rgb(0, 184, 240)));
        arrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 36)));

        LinearLayout contentShell = new LinearLayout(activity);
        contentShell.setOrientation(LinearLayout.VERTICAL);
        contentShell.setPadding(dp(activity, 14), 0, dp(activity, 14), dp(activity, 13));
        contentShell.addView(content);

        boolean expanded = activity.getSharedPreferences(AppPrefs.FILE, 0)
                .getBoolean(PREF_PREFIX + key, defaultExpanded);
        applyExpanded(contentShell, arrow, expanded);

        header.setOnClickListener(v -> {
            boolean next = contentShell.getVisibility() != View.VISIBLE;
            applyExpanded(contentShell, arrow, next);
            activity.getSharedPreferences(AppPrefs.FILE, 0).edit()
                    .putBoolean(PREF_PREFIX + key, next)
                    .apply();
        });

        panel.addView(header);
        panel.addView(contentShell);
        return panel;
    }

    private static void applyExpanded(View body, TextView arrow, boolean expanded) {
        body.animate().cancel();
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        body.setAlpha(1f);
        arrow.setText(expanded ? "⌄" : "›");
        arrow.setRotation(0f);
    }

    private static LinearLayout body(Activity activity) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        return body;
    }

    private static View securityStatusRow(Activity activity, String label, String value,
                                          boolean positive) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 11), dp(activity, 9), dp(activity, 11), dp(activity, 9));
        row.setBackground(roundRect(activity,
                alpha(color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)), 235),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)),
                10));

        TextView name = text(activity, label, 11, color(activity, R.color.hcf_text, Color.WHITE));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        int valueColor = positive
                ? color(activity, R.color.hcf_accent_text, Color.rgb(0, 184, 240))
                : color(activity, R.color.hcf_muted, Color.LTGRAY);
        TextView state = text(activity, value, 10, valueColor);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(state);
        return row;
    }

    private static TextView twoFactorStatus(Activity activity) {
        boolean configured = isConfigured(activity);
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        int statusColor = configured ? cyan : color(activity, R.color.hcf_muted, Color.LTGRAY);

        TextView status = text(activity,
                configured ? "Configured on this device" : "Not configured on this device",
                11,
                statusColor);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(activity, 11), dp(activity, 8), dp(activity, 11), dp(activity, 8));
        status.setBackground(roundRect(activity,
                alpha(statusColor, 18),
                alpha(statusColor, 100),
                10));
        return status;
    }

    private static void refreshTwoFactor(View root) {
        Activity activity = findActivity(root);
        if (activity == null) return;
        boolean configured = isConfigured(activity);
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        int statusColor = configured ? cyan : color(activity, R.color.hcf_muted, Color.LTGRAY);

        View statusView = root.findViewWithTag(TAG_2FA_STATUS);
        if (statusView instanceof TextView) {
            TextView status = (TextView) statusView;
            status.setText(configured ? "Configured on this device" : "Not configured on this device");
            status.setTextColor(statusColor);
            status.setBackground(roundRect(activity,
                    alpha(statusColor, 18),
                    alpha(statusColor, 100),
                    10));
        }

        View summaryView = root.findViewWithTag(TAG_2FA_SUMMARY);
        if (summaryView instanceof TextView) {
            ((TextView) summaryView).setText(configured
                    ? "Configured on this device"
                    : "Set up HCF Authenticator");
        }

        View buttonView = root.findViewWithTag(TAG_2FA_BUTTON);
        if (buttonView instanceof Button) {
            ((Button) buttonView).setText(configured
                    ? "Open HCF Authenticator"
                    : "Set Up HCF Authenticator");
        }
    }

    private static TextView detail(Activity activity, String value) {
        TextView detail = text(activity, value, 10,
                color(activity, R.color.hcf_hint, Color.LTGRAY));
        detail.setLineSpacing(0f, 1.08f);
        return detail;
    }

    private static void styleExistingAction(Activity activity, View action) {
        action.setMinimumHeight(dp(activity, 52));
        action.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        action.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)),
                11));
    }

    private static String forumState(ForumSecurity.Snapshot security) {
        return security.seen ? "Forum managed" : "Sync needed";
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static View clickableAncestor(View child, ViewGroup stop) {
        View current = child;
        View candidate = child;
        while (current != null && current != stop) {
            if (current.isClickable()) candidate = current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return candidate == child && !child.isClickable() ? null : candidate;
    }

    private static ViewGroup commonCardAncestor(View first, View second) {
        View cursor = first;
        while (cursor != null && cursor.getParent() instanceof View) {
            cursor = (View) cursor.getParent();
            if (!(cursor instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) cursor;
            if (isDescendant(group, second) && group.getChildCount() >= 3) return group;
        }
        return null;
    }

    private static boolean isDescendant(ViewGroup parent, View target) {
        View cursor = target;
        while (cursor != null && cursor.getParent() instanceof View) {
            if (cursor.getParent() == parent) return true;
            cursor = (View) cursor.getParent();
        }
        return false;
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

    private static Activity findActivity(View view) {
        Context context = view == null ? null : view.getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout.LayoutParams lp(Context context, int width, int height,
                                                 int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = dp(context, topDp);
        lp.bottomMargin = dp(context, bottomDp);
        return lp;
    }

    private static GradientDrawable roundRect(Context context, int fill, int stroke,
                                              int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int alpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int color(Context context, int resId, int fallback) {
        try { return context.getColor(resId); }
        catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
