package com.harleytg.forum;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** What's New UI for the current HCF build. */
final class ReleaseNotes {
    static final String NOTES = "Harley's Clan Forum (app) v" + BuildInfo.VERSION
            + " • " + BuildInfo.CHANNEL + " • Build " + BuildInfo.VERSION_CODE
            + "\n• Settings Advanced & About cleanup."
            + "\n• Individual settings are searchable and open directly to their exact controls."
            + "\n• Version/build labels use the current build identity instead of duplicated hard-coded values."
            + "\n• Error & Recovery, Telemetry, Developer Tools, and About each have one primary owner."
            + "\n• Update-channel routing is locked to the correct package channel.";
    static final String SUMMARY = BuildInfo.CHANNEL + " • Build " + BuildInfo.VERSION_CODE + " • Settings cleanup";

    static void seedForFreshInstall(SharedPreferences preferences) {
        markSeen(preferences);
    }

    private static String releaseId() {
        return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE + "-" + BuildInfo.CHANNEL.toLowerCase();
    }

    static boolean shouldNotify(SharedPreferences preferences) {
        return preferences != null && !releaseId().equals(preferences.getString("last_seen_whats_new_version", ""));
    }

    static void markSeen(SharedPreferences preferences) {
        if (preferences != null) preferences.edit().putString("last_seen_whats_new_version", releaseId()).apply();
    }

    static void show(Activity activity, SharedPreferences preferences, boolean markOnDismiss) {
        showCustom(activity, preferences, markOnDismiss);
    }

    static void showCustom(Activity activity, final SharedPreferences preferences, boolean markOnDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_background);
        int pad = dp(activity, 18);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(16);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(logo, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        labelsLp.leftMargin = dp(activity, 12);
        labels.addView(label(activity, "Harley's Clan Forum • Release Notes", 9, R.color.hcf_meta, true));
        TextView title = label(activity, "What's New", 24, R.color.hcf_text, true);
        title.setPadding(0, dp(activity, 2), 0, 0);
        labels.addView(title);
        TextView version = label(activity, "v" + BuildInfo.VERSION + "  •  " + BuildInfo.CHANNEL + "  •  Build " + BuildInfo.VERSION_CODE, 11, R.color.hcf_cyan_bright, true);
        version.setPadding(0, dp(activity, 4), 0, 0);
        labels.addView(version);
        hero.addView(labels, labelsLp);
        root.addView(hero);

        TextView summary = label(activity, SUMMARY, 13, R.color.hcf_text, false);
        summary.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        root.addView(summary);
        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.hcf_divider));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout sections = new LinearLayout(activity);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        addSection(activity, sections, "Settings cleanup", "Advanced & About now keeps Permissions & Security, App Updates, Error & Recovery, Telemetry, Developer Tools, and About as six clear owners with duplicated controls removed.");
        addSection(activity, sections, "Exact settings search", "Search now indexes individual controls. Selecting a result opens its category and expandable section, then scrolls to the actual target control using a stable setting key.");
        addSection(activity, sections, "Current build identity", "Visible version information now resolves from the current build. This build is v" + BuildInfo.VERSION + " • Build " + BuildInfo.VERSION_CODE + " • " + BuildInfo.CHANNEL + ".");
        addSection(activity, sections, "Update channel safety", "The app update checker is locked to this package's " + BuildInfo.CHANNEL + " release feed and verifies the published APK package and Android versionCode before presenting an update.");
        addSection(activity, sections, "Telemetry & recovery", "Telemetry has one privacy-focused section, while runtime snapshots, logs, WebView cache recovery, renderer recovery state, and sanitized diagnostics stay in Error & Recovery.");
        scroll.addView(sections, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollLp.topMargin = dp(activity, 4);
        root.addView(scroll, scrollLp);

        Button done = new Button(activity);
        UiButtons.normalizeText(done);
        done.setText("Done");
        done.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        done.setTextSize(14.0f);
        done.setBackgroundResource(R.drawable.button_background);
        done.setGravity(17);
        done.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        done.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        doneLp.topMargin = dp(activity, 10);
        root.addView(done, doneLp);
        done.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        if (markOnDismiss && preferences != null) {
            dialog.setOnDismissListener((DialogInterface ignored) -> markSeen(preferences));
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
            window.setLayout(Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)),
                    Math.max(dp(activity, 420), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f)));
            window.setGravity(17);
        }
        AppLogger.info(activity, "whats_new_open", releaseId());
    }

    private static void addSection(Activity activity, LinearLayout parent, String title, String body) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.identity_card_background);
        int pad = dp(activity, 12);
        section.setPadding(pad, pad, pad, pad);
        section.addView(label(activity, title, 11, R.color.hcf_cyan_bright, true));
        TextView text = label(activity, body, 12, R.color.hcf_muted, false);
        text.setLineSpacing(0.0f, 1.08f);
        text.setPadding(0, dp(activity, 6), 0, 0);
        section.addView(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(activity, 9);
        parent.addView(section, lp);
    }

    private static TextView label(Activity activity, String text, int size, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(activity.getColor(colorRes));
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private ReleaseNotes() {}
}
