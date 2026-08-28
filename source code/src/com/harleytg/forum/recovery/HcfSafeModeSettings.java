package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.WeakHashMap;

/** Adds a developer recovery dashboard to Settings -> Developer Tools. */
public final class HcfSafeModeSettings {
    private static final String INJECTED_TAG = "hcf_safe_mode_developer_tools";
    private static final String PREF_FILE = "hcf_app";
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>();

    private HcfSafeModeSettings() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;

            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {
                            if (activity instanceof HcfSubActivities.SettingsActivity) attach(activity);
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            if (activity instanceof HcfSubActivities.SettingsActivity) {
                                attach(activity);
                                View root = activity.getWindow().getDecorView();
                                if (root != null) root.post(() -> inject(activity, root));
                            }
                        }

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            detach(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void attach(final Activity activity) {
        synchronized (LISTENERS) {
            if (LISTENERS.containsKey(activity)) return;
            final View root = activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    inject(activity, root);
                }
            };
            LISTENERS.put(activity, listener);
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            root.post(() -> inject(activity, root));
        }
    }

    private static void detach(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (LISTENERS) {
            listener = LISTENERS.remove(activity);
        }
        if (listener == null) return;
        try {
            View root = activity.getWindow().getDecorView();
            ViewTreeObserver observer = root == null ? null : root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void inject(final Activity activity, View root) {
        if (activity == null || activity.isFinishing() || root == null) return;
        View playgroundButton = findButton(root, "Open UI Playground");
        if (!(playgroundButton != null && playgroundButton.getParent() instanceof LinearLayout)) return;

        LinearLayout card = (LinearLayout) playgroundButton.getParent();
        for (int i = 0; i < card.getChildCount(); i++) {
            if (INJECTED_TAG.equals(card.getChildAt(i).getTag())) return;
        }

        int playgroundIndex = card.indexOfChild(playgroundButton);
        int insertionIndex = playgroundIndex;
        for (int i = playgroundIndex - 1; i >= 0; i--) {
            View candidate = card.getChildAt(i);
            if (containsText(candidate, "UI Playground")) {
                insertionIndex = i;
                break;
            }
        }

        LinearLayout section = buildRecoverySection(activity);
        section.setTag(INJECTED_TAG);
        card.addView(section, insertionIndex);
        AppLogger.info(activity, "developer_recovery_added", "developer_tools");
    }

    private static LinearLayout buildRecoverySection(final Activity activity) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 4), dp(activity, 14), dp(activity, 4), dp(activity, 5));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.fa_shield);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.hcf_cyan_bright)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20));
        iconLp.rightMargin = dp(activity, 10);
        header.addView(icon, iconLp);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Developer Recovery");
        title.setTextColor(activity.getColor(R.color.hcf_text));
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        labels.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Safe Mode status, crash-recovery preview, diagnostics, and Android recovery shortcuts.");
        subtitle.setTextColor(activity.getColor(R.color.hcf_muted));
        subtitle.setTextSize(10);
        subtitle.setLineSpacing(0.0f, 1.08f);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        section.addView(header);

        TextView status = new TextView(activity);
        status.setText(recoveryStatus(activity));
        status.setTextColor(activity.getColor(R.color.hcf_muted));
        status.setTextSize(10.5f);
        status.setLineSpacing(0.0f, 1.12f);
        status.setBackgroundResource(R.drawable.quick_action_background);
        status.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
        section.addView(status, spaced(activity, 7, ViewGroup.LayoutParams.WRAP_CONTENT));

        section.addView(subsectionLabel(activity, "Recovery screen"), spaced(activity, 12, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(actionButton(activity, "Preview Normal Crash Recovery", R.drawable.fa_shield, v -> {
            AppLogger.info(activity, "normal_recovery_preview", "developer_tools");
            Toast.makeText(activity, "This is the same recovery screen HCF shows after a crash.", Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(activity, HcfSafeMode.SafeModeActivity.class));
        }), spaced(activity, 7, dp(activity, 52)));

        section.addView(subsectionLabel(activity, "Diagnostics"), spaced(activity, 12, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(actionButton(activity, "Open Logs & Diagnostics", R.drawable.fa_bug, v -> {
            AppLogger.info(activity, "recovery_logs_open", "developer_tools");
            activity.startActivity(new Intent(activity, HcfSubActivities.LogsActivity.class));
        }), spaced(activity, 7, dp(activity, 52)));

        section.addView(actionButton(activity, "Open Android App Settings", R.drawable.fa_gear, v -> {
            AppLogger.info(activity, "recovery_android_settings", "developer_tools");
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }), spaced(activity, 7, dp(activity, 52)));

        TextView note = new TextView(activity);
        note.setText("Normal recovery is normally automatic after an uncaught crash. The preview above lets Dev/Beta builds open that exact screen without crashing first.");
        note.setTextColor(activity.getColor(R.color.hcf_hint));
        note.setTextSize(9.5f);
        note.setLineSpacing(0.0f, 1.08f);
        note.setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), 0);
        section.addView(note);

        return section;
    }

    private static String recoveryStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        boolean active = prefs.getBoolean("safe_mode_active", false)
                && prefs.getInt("safe_mode_session_pid", -1) == android.os.Process.myPid();
        boolean pending = prefs.getBoolean("safe_mode_pending", false);
        int crashes = prefs.getInt("safe_mode_crash_count", 0);
        String summary = prefs.getString("safe_mode_last_crash_summary", "");
        StringBuilder out = new StringBuilder();
        out.append("Safe Mode: ").append(active ? "ACTIVE" : "Inactive");
        out.append("\nCrash recovery pending: ").append(pending ? "Yes" : "No");
        out.append("\nRecent crash count: ").append(crashes);
        if (summary != null && !summary.trim().isEmpty()) {
            String clean = summary.replace('\n', ' ').replace('\r', ' ').trim();
            if (clean.length() > 120) clean = clean.substring(0, 120) + "…";
            out.append("\nLast crash: ").append(clean);
        }
        return out.toString();
    }

    private static TextView subsectionLabel(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(context.getColor(R.color.hcf_cyan_bright));
        label.setTextSize(10.5f);
        label.setTypeface(null, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        return label;
    }

    private static Button actionButton(Activity activity, String text, int iconRes, View.OnClickListener listener) {
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText(text);
        button.setTextColor(activity.getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        FaIcons.applyStart(button, iconRes);
        button.setOnClickListener(listener);
        return button;
    }

    private static LinearLayout.LayoutParams spaced(Context context, int topDp, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = dp(context, topDp);
        return lp;
    }

    private static View findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findButton(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(text)) return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
