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
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.WeakHashMap;

/** Adds a developer recovery dashboard to Settings -> Developer Tools and skins real recovery like App Settings. */
public final class HcfSafeModeSettings {
    private static final String INJECTED_TAG = "hcf_safe_mode_developer_tools";
    private static final String RECOVERY_SKIN_TAG = "hcf_recovery_settings_skin";
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
                            if (activity instanceof HcfSubActivities.SettingsActivity) {
                                attach(activity);
                            } else if (activity instanceof HcfSafeMode.SafeModeActivity) {
                                View root = activity.getWindow().getDecorView();
                                if (root != null) root.post(() -> decorateRecoveryActivity(activity));
                            }
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            if (activity instanceof HcfSubActivities.SettingsActivity) {
                                attach(activity);
                                View root = activity.getWindow().getDecorView();
                                if (root != null) root.post(() -> inject(activity, root));
                            } else if (activity instanceof HcfSafeMode.SafeModeActivity) {
                                View root = activity.getWindow().getDecorView();
                                if (root != null) root.post(() -> decorateRecoveryActivity(activity));
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

    /**
     * Restyles the real post-crash SafeModeActivity using the same visual language as
     * Settings and Settings subpages. Existing Button instances are reused, so their
     * recovery listeners and crash-handling behavior remain untouched.
     */
    private static void decorateRecoveryActivity(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup androidContent = activity.findViewById(android.R.id.content);
        if (androidContent == null) return;
        if (findTaggedView(androidContent, RECOVERY_SKIN_TAG) != null) return;

        ScrollView scroll = findScrollView(androidContent);
        if (scroll == null || scroll.getChildCount() == 0) return;
        View content = scroll.getChildAt(0);
        if (!(content instanceof LinearLayout)) return;
        LinearLayout recoveryContent = (LinearLayout) content;

        activity.getWindow().setStatusBarColor(activity.getColor(R.color.hcf_bg));
        activity.getWindow().setNavigationBarColor(activity.getColor(R.color.hcf_bg));
        scroll.setBackgroundColor(activity.getColor(R.color.hcf_bg));
        recoveryContent.setBackgroundColor(activity.getColor(R.color.hcf_bg));
        recoveryContent.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 28));

        hideLegacyRecoveryHeading(recoveryContent);
        styleRecoveryTree(activity, recoveryContent);
        insertRecoverySectionHeaders(activity, recoveryContent);

        ViewParent parent = scroll.getParent();
        if (!(parent instanceof ViewGroup)) return;
        ((ViewGroup) parent).removeView(scroll);

        LinearLayout shell = new LinearLayout(activity);
        shell.setTag(RECOVERY_SKIN_TAG);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(activity.getColor(R.color.hcf_bg));
        shell.addView(buildRecoveryAppHeader(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.hcf_border));
        shell.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 1))));

        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        activity.setContentView(shell);
        AppLogger.info(activity, "recovery_ui", "settings_subpage_skin");
    }

    private static View buildRecoveryAppHeader(final Activity activity) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 10), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        header.setBackgroundColor(activity.getColor(R.color.hcf_bg));

        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.fa_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.hcf_cyan_bright)));
        back.setBackgroundResource(R.drawable.chrome_button_background);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> activity.finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setContentDescription("Harley's Clan Forum");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
        logoLp.leftMargin = dp(activity, 9);
        logoLp.rightMargin = dp(activity, 10);
        header.addView(logo, logoLp);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Recovery & Safe Mode");
        title.setTextColor(activity.getColor(R.color.hcf_text));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        labels.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Harley's Clan Forum v" + BuildInfo.VERSION + " | Development Build / Beta");
        subtitle.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        subtitle.setTextSize(10.5f);
        subtitle.setTypeface(null, Typeface.BOLD);
        subtitle.setSingleLine(true);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(activity, 4);
        labels.addView(subtitle, subtitleLp);

        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        return header;
    }

    private static void hideLegacyRecoveryHeading(LinearLayout root) {
        int hidden = 0;
        for (int i = 0; i < root.getChildCount() && hidden < 3; i++) {
            View child = root.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            CharSequence text = ((TextView) child).getText();
            if (text == null) continue;
            String value = text.toString();
            if (value.contains("HARLEY'S CLAN FORUM") || "Safe Mode".equals(value)
                    || value.startsWith("The previous app run ended unexpectedly")) {
                child.setVisibility(View.GONE);
                hidden++;
            }
        }
    }

    private static void styleRecoveryTree(Activity activity, View view) {
        if (view instanceof Button) {
            styleRecoveryButton(activity, (Button) view);
        } else if (view instanceof TextView) {
            styleRecoveryText(activity, (TextView) view);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            styleRecoveryTree(activity, group.getChildAt(i));
        }
    }

    private static void styleRecoveryButton(Activity activity, Button button) {
        UiButtons.normalizeText(button);
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(activity, 48));
        button.setTextSize(12);

        String text = button.getText() == null ? "" : button.getText().toString();
        if (text.contains("Test Crash Handler")) {
            button.setTextColor(activity.getColor(R.color.hcf_muted));
        } else {
            button.setTextColor(activity.getColor(R.color.hcf_accent_text));
        }
        FaIcons.applyStart(button, text);

        ViewGroup.LayoutParams current = button.getLayoutParams();
        if (current != null) {
            current.height = dp(activity, 52);
            button.setLayoutParams(current);
        }
    }

    private static void styleRecoveryText(Activity activity, TextView textView) {
        CharSequence chars = textView.getText();
        if (chars == null) return;
        String text = chars.toString();

        if ("Crash report".equals(text) || "Recovery tools".equals(text) || "Developer test".equals(text)) {
            textView.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
            textView.setTextSize(11);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setIncludeFontPadding(false);
            textView.setPadding(dp(activity, 4), dp(activity, 7), dp(activity, 4), dp(activity, 2));
            return;
        }

        if (text.contains("Safe Mode is intentionally temporary")) {
            textView.setTextColor(activity.getColor(R.color.hcf_hint));
            textView.setTextSize(10);
            return;
        }

        if (text.contains("Crash detected") || text.contains("Repeated crash detected")) {
            textView.setTextColor(activity.getColor(R.color.hcf_text));
            textView.setTextSize(13);
            textView.setTypeface(null, Typeface.BOLD);
        }
    }

    private static void insertRecoverySectionHeaders(Activity activity, LinearLayout root) {
        if (containsDirectText(root, "Recovery status")) return;

        View statusCard = findDirectChildContaining(root, "Crash detected", "Repeated crash detected");
        if (statusCard != null) {
            int index = root.indexOfChild(statusCard);
            root.addView(settingsSectionHeader(activity, "Recovery status",
                    "Crash state and the last recorded recovery event", R.drawable.fa_shield), index);
            statusCard.setBackgroundResource(R.drawable.quick_action_background);
            statusCard.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        }

        View safeStart = findButton(root, "Start in Safe Mode");
        if (safeStart != null && safeStart.getParent() == root) {
            int index = root.indexOfChild(safeStart);
            root.addView(settingsSectionHeader(activity, "Startup mode",
                    "Choose a protected startup or retry the normal app runtime", R.drawable.fa_shield), index);
        }
    }

    private static View settingsSectionHeader(Activity activity, String titleText, String subtitleText, int iconRes) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 4), dp(activity, 13), dp(activity, 4), dp(activity, 5));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.hcf_cyan_bright)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20));
        iconLp.rightMargin = dp(activity, 10);
        row.addView(icon, iconLp);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(activity.getColor(R.color.hcf_text));
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        labels.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(activity.getColor(R.color.hcf_muted));
        subtitle.setTextSize(9.5f);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = dp(activity, 2);
        labels.addView(subtitle, subtitleLp);

        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        return row;
    }

    private static ScrollView findScrollView(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findScrollView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static View findDirectChildContaining(LinearLayout root, String first, String second) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (containsText(child, first) || containsText(child, second)) return child;
        }
        return null;
    }

    private static boolean containsDirectText(LinearLayout root, String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) return true;
            if (child instanceof ViewGroup && containsText(child, text)) return true;
        }
        return false;
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

    private static View findTaggedView(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTaggedView(group.getChildAt(i), tag);
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
