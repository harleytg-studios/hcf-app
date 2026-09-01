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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Modern Account Controls UI + native HCF Authenticator integration.
 *
 * The existing SettingsActivity remains the single owner of App Settings and of
 * forum profile/security navigation. This helper only restyles/recomposes the
 * Account Controls card after it is created, reusing its existing clickable
 * action views so route behavior is preserved.
 */
public final class HcfAuthenticatorSettings {
    private static final String TAG = "hcf_account_controls_v2";
    private static final String TAG_STATUS = TAG + ":status";
    private static final String TAG_AUTH_BUTTON = TAG + ":auth_button";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean installed;

    private HcfAuthenticatorSettings() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;
            install((Application) appContext);
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (isSettings(activity)) scheduleInjection(activity);
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (isSettings(activity)) scheduleInjection(activity);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static boolean isSettings(Activity activity) {
        return activity != null
                && "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity".equals(activity.getClass().getName());
    }

    private static void scheduleInjection(Activity activity) {
        MAIN.postDelayed(() -> inject(activity), 70L);
        MAIN.postDelayed(() -> inject(activity), 220L);
        MAIN.postDelayed(() -> inject(activity), 520L);
    }

    private static void inject(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        View existing = root.findViewWithTag(TAG);
        if (existing != null) {
            refreshStatus(root);
            return;
        }

        // Account Controls only exists in the signed-in Account & Security detail.
        TextView profileText = findText(root, "Open My Forum Profile");
        TextView securityText = findText(root, "Open Account Security");
        if (profileText == null || securityText == null) return;

        ViewGroup card = commonCardAncestor(profileText, securityText);
        if (card == null) return;

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
        card.setPadding(dp(activity, 16), dp(activity, 15), dp(activity, 16), dp(activity, 16));

        buildHeader(activity, card, identity);
        card.addView(buildSecurityOverview(activity, security));
        card.addView(sectionLabel(activity, "ACCOUNT ACTIONS"), spaced(-1, -2, 16, 0));

        styleExistingAction(activity, profileAction, "Profile", "View your public forum profile and activity");
        styleExistingAction(activity, securityAction, "Security", "Password, email, sessions and forum-side 2FA settings");
        card.addView(profileAction, spaced(-1, dp(activity, 54), 8, 0));
        card.addView(securityAction, spaced(-1, dp(activity, 54), 8, 0));

        card.addView(buildAuthenticatorSection(activity), spaced(-1, -2, 17, 0));

        TextView footer = text(activity,
                "Forum account changes remain protected by Harley's Clan Forum. Authenticator secrets are encrypted locally with Android Keystore.",
                9, color(activity, R.color.hcf_muted, Color.GRAY));
        footer.setLineSpacing(0f, 1.08f);
        card.addView(footer, spaced(-1, -2, 10, 2));
    }

    private static void buildHeader(Activity activity, ViewGroup card, ForumIdentity.Snapshot identity) {
        TextView eyebrow = sectionLabel(activity, "ACCOUNT SECURITY");
        card.addView(eyebrow);

        TextView title = text(activity, "Account Controls", 20,
                color(activity, R.color.hcf_text, Color.WHITE));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, spaced(-1, -2, 4, 0));

        String handle = identity.username == null || identity.username.trim().isEmpty()
                ? identity.identityLabel()
                : "@" + identity.username.trim();
        TextView account = text(activity, "Signed in as " + handle, 11,
                color(activity, R.color.hcf_accent_text, Color.rgb(0, 184, 240)));
        account.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        account.setGravity(Gravity.CENTER_VERTICAL);
        account.setPadding(dp(activity, 11), dp(activity, 7), dp(activity, 11), dp(activity, 7));
        account.setBackground(roundRect(activity,
                alpha(color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)), 24),
                alpha(color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)), 105), 12));
        card.addView(account, spaced(-1, -2, 10, 0));
    }

    private static View buildSecurityOverview(Activity activity, ForumSecurity.Snapshot security) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 13), dp(activity, 12));
        panel.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 14));

        TextView label = sectionLabel(activity, "SECURITY OVERVIEW");
        panel.addView(label);

        LinearLayout first = statusRow(activity);
        first.addView(statusTile(activity, "PASSWORD", security.passwordControls ? "Available" : forumState(security), security.passwordControls), weightedTile());
        first.addView(statusTile(activity, "EMAIL", security.emailControls ? "Available" : forumState(security), security.emailControls), weightedTileWithStart(8));
        panel.addView(first, spaced(-1, -2, 9, 0));

        LinearLayout second = statusRow(activity);
        boolean local2fa = isConfigured(activity);
        String twoFactor = local2fa ? "Configured" : (security.twoFactorControls ? "Available" : forumState(security));
        second.addView(statusTile(activity, "TWO-FACTOR", twoFactor, local2fa), weightedTile());
        String sessions = security.sessionCount > 0 ? String.valueOf(security.sessionCount) : (security.seen ? "—" : "Sync needed");
        second.addView(statusTile(activity, "SESSIONS", sessions, security.sessionCount > 0), weightedTileWithStart(8));
        panel.addView(second, spaced(-1, -2, 8, 0));

        if (!security.seen) {
            TextView note = text(activity,
                    "Open Account Security once to sync the forum's available security controls.",
                    9, color(activity, R.color.hcf_hint, Color.LTGRAY));
            panel.addView(note, spaced(-1, -2, 9, 0));
        }
        return panel;
    }

    private static LinearLayout statusRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        return row;
    }

    private static View statusTile(Activity activity, String label, String value, boolean positive) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(activity, 10), dp(activity, 9), dp(activity, 10), dp(activity, 9));
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        int border = positive ? alpha(cyan, 105) : color(activity, R.color.hcf_border, Color.rgb(41, 64, 75));
        tile.setBackground(roundRect(activity,
                alpha(color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)), 235), border, 11));

        TextView name = text(activity, label, 8, color(activity, R.color.hcf_meta, Color.GRAY));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tile.addView(name);

        TextView state = text(activity, value, 11,
                positive ? color(activity, R.color.hcf_accent_text, cyan)
                        : color(activity, R.color.hcf_muted, Color.LTGRAY));
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tile.addView(state, spaced(-1, -2, 3, 0));
        return tile;
    }

    private static View buildAuthenticatorSection(Activity activity) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 13), dp(activity, 13), dp(activity, 13), dp(activity, 13));
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        box.setBackground(roundRect(activity, alpha(cyan, 18), alpha(cyan, 120), 14));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(activity, "HCF AUTHENTICATOR", 9, cyan);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        names.addView(eyebrow);
        TextView title = text(activity, "Two-Factor Authentication", 15,
                color(activity, R.color.hcf_text, Color.WHITE));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        names.addView(title, spaced(-1, -2, 3, 0));
        heading.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView status = statusBadge(activity);
        status.setTag(TAG_STATUS);
        heading.addView(status);
        box.addView(heading);

        TextView detail = text(activity,
                "Generate rotating 6-digit codes offline. Scan a QR code, import a QR screenshot, open an otpauth link, or enter a setup key manually.",
                10, color(activity, R.color.hcf_hint, Color.LTGRAY));
        detail.setLineSpacing(0f, 1.08f);
        box.addView(detail, spaced(-1, -2, 9, 0));

        Button open = new Button(activity);
        open.setTag(TAG_AUTH_BUTTON);
        open.setAllCaps(false);
        open.setText(isConfigured(activity) ? "Open HCF Authenticator" : "Set Up HCF Authenticator");
        open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        open.setTextColor(Color.BLACK);
        open.setBackground(roundRect(activity, cyan, cyan, 12));
        open.setStateListAnimator(null);
        open.setOnClickListener(v -> activity.startActivity(new Intent(activity, HcfAuthenticator.Activity.class)));
        box.addView(open, spaced(-1, dp(activity, 48), 11, 0));

        TextView offline = text(activity, "Codes work offline after setup • secret encrypted on this device",
                9, color(activity, R.color.hcf_muted, Color.GRAY));
        offline.setGravity(Gravity.CENTER);
        box.addView(offline, spaced(-1, -2, 7, 0));
        return box;
    }

    private static TextView statusBadge(Activity activity) {
        boolean configured = isConfigured(activity);
        int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
        int textColor = configured ? cyan : color(activity, R.color.hcf_muted, Color.LTGRAY);
        TextView badge = text(activity, configured ? "CONFIGURED" : "NOT SET", 8, textColor);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 9), dp(activity, 6), dp(activity, 9), dp(activity, 6));
        badge.setBackground(roundRect(activity, alpha(textColor, 18), alpha(textColor, 100), 99));
        return badge;
    }

    private static void refreshStatus(View root) {
        Activity activity = findActivity(root);
        if (activity == null) return;
        boolean configured = isConfigured(activity);

        View statusView = root.findViewWithTag(TAG_STATUS);
        if (statusView instanceof TextView) {
            TextView status = (TextView) statusView;
            int cyan = color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
            int textColor = configured ? cyan : color(activity, R.color.hcf_muted, Color.LTGRAY);
            status.setText(configured ? "CONFIGURED" : "NOT SET");
            status.setTextColor(textColor);
            status.setBackground(roundRect(activity, alpha(textColor, 18), alpha(textColor, 100), 99));
        }

        View buttonView = root.findViewWithTag(TAG_AUTH_BUTTON);
        if (buttonView instanceof Button) {
            ((Button) buttonView).setText(configured ? "Open HCF Authenticator" : "Set Up HCF Authenticator");
        }
    }

    private static void styleExistingAction(Activity activity, View action, String label, String detail) {
        if (action == null) return;
        action.setMinimumHeight(dp(activity, 54));
        action.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        action.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 12));
        action.setContentDescription(label + ". " + detail);
    }

    private static TextView sectionLabel(Activity activity, String value) {
        TextView label = text(activity, value, 9,
                color(activity, R.color.hcf_meta, Color.GRAY));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private static String forumState(ForumSecurity.Snapshot security) {
        return security.seen ? "Forum managed" : "Sync needed";
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

    private static LinearLayout.LayoutParams spaced(int width, int height, int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = topDp;
        lp.bottomMargin = bottomDp;
        return lp;
    }

    private static LinearLayout.LayoutParams weightedTile() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private static LinearLayout.LayoutParams weightedTileWithStart(int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        // dp is applied by the caller's density through a small safe default below.
        lp.leftMargin = marginDp;
        return lp;
    }

    private static GradientDrawable roundRect(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int alpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int color(Context context, int resId, int fallback) {
        try { return context.getColor(resId); } catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
