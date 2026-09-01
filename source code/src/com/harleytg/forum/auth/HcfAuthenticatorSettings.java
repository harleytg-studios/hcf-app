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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Adds the native HCF Authenticator controls to the existing
 * Settings -> Account & Security screen without creating a second settings UI.
 */
public final class HcfAuthenticatorSettings {
    private static final String TAG = "hcf_authenticator_settings_v1";
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
        MAIN.postDelayed(() -> inject(activity), 90L);
        MAIN.postDelayed(() -> inject(activity), 320L);
    }

    private static void inject(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        if (root.findViewWithTag(TAG) != null) {
            refreshStatus(root);
            return;
        }

        // Only inject when the Account & Security detail screen is actually open.
        TextView openSecurity = findText(root, "Open Account Security");
        if (openSecurity == null || !(openSecurity.getParent() instanceof ViewGroup)) return;

        ViewGroup card = (ViewGroup) openSecurity.getParent();
        LinearLayout section = buildSection(activity);
        section.setTag(TAG);

        int index = card.indexOfChild(openSecurity);
        int insertAt = index >= 0 ? Math.min(index + 1, card.getChildCount()) : card.getChildCount();
        card.addView(section, insertAt);
    }

    private static LinearLayout buildSection(Activity activity) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(activity, 14), 0, dp(activity, 4));

        View divider = new View(activity);
        divider.setBackgroundColor(color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dividerLp.bottomMargin = dp(activity, 13);
        box.addView(divider, dividerLp);

        TextView eyebrow = text(activity, "HCF AUTHENTICATOR", 10,
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(eyebrow);

        TextView title = text(activity, "Two-Factor Authentication", 15,
                color(activity, R.color.hcf_text, Color.WHITE));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(activity, 5);
        box.addView(title, titleLp);

        TextView status = text(activity, statusLine(activity), 11,
                isConfigured(activity)
                        ? Color.rgb(85, 225, 59)
                        : color(activity, R.color.hcf_muted, Color.LTGRAY));
        status.setTag(TAG + ":status");
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(activity, 5);
        box.addView(status, statusLp);

        TextView detail = text(activity,
                "Generate rotating 6-digit codes offline. Setup supports camera QR scanning, QR screenshots, otpauth links and manual Base32 keys.",
                10, color(activity, R.color.hcf_hint, Color.LTGRAY));
        detail.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(activity, 7);
        box.addView(detail, detailLp);

        Button open = new Button(activity);
        open.setAllCaps(false);
        open.setText(isConfigured(activity) ? "Open HCF Authenticator" : "Set Up HCF Authenticator");
        open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        open.setTextColor(Color.BLACK);
        open.setBackground(roundRect(activity,
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)),
                color(activity, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240)), 12));
        open.setStateListAnimator(null);
        open.setOnClickListener(v -> {
            Intent intent = new Intent(activity, HcfAuthenticator.Activity.class);
            activity.startActivity(intent);
        });
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        openLp.topMargin = dp(activity, 11);
        box.addView(open, openLp);

        TextView local = text(activity,
                "Authenticator secrets stay encrypted with Android Keystore and codes continue working without an internet connection after setup.",
                9, color(activity, R.color.hcf_muted, Color.GRAY));
        LinearLayout.LayoutParams localLp = new LinearLayout.LayoutParams(-1, -2);
        localLp.topMargin = dp(activity, 8);
        box.addView(local, localLp);

        return box;
    }

    private static void refreshStatus(View root) {
        View statusView = root.findViewWithTag(TAG + ":status");
        if (!(statusView instanceof TextView)) return;
        TextView status = (TextView) statusView;
        Activity activity = findActivity(status);
        if (activity == null) return;
        boolean configured = isConfigured(activity);
        status.setText(configured ? "Configured on this device" : "Not configured on this device");
        status.setTextColor(configured
                ? Color.rgb(85, 225, 59)
                : color(activity, R.color.hcf_muted, Color.LTGRAY));
    }

    private static String statusLine(Context context) {
        return isConfigured(context) ? "Configured on this device" : "Not configured on this device";
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
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

    private static GradientDrawable roundRect(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int color(Context context, int resId, int fallback) {
        try { return context.getColor(resId); } catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
