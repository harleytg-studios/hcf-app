package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ReleaseNotes {
    static final String SUMMARY = "Beta/Dev v10000036 • Settings, notifications & ErrorSys UI update";

    static final String NOTES =
            "Harley\'s Clan Forum (app) v1.0 • Beta/Dev v10000036\n" +
            "• Development/Beta versionCode 10000036.\n" +
            "• App Settings now exposes the native Notification Center, Notification History, and Do Not Disturb schedule controls.\n" +
            "• Changing Day, Night, AMOLED, or Follow phone theme now returns to the Settings section you were editing instead of jumping back to Settings home.\n" +
            "• ErrorSys is explicitly layered over the forum WebView content area while the native header and app chrome remain available.\n" +
            "• Runtime domain/Firebase routing and the notification reply composer remain included in this Dev build.\n\n" +
            "Stable remains separate; this feature set is scoped to com.harleytg.forum.dev.";

    static void seedForFreshInstall(SharedPreferences prefs) {
        markSeen(prefs);
    }

    private static String releaseId() {
        return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE;
    }

    static boolean shouldNotify(SharedPreferences prefs) {
        if (prefs == null) return false;
        String seen = prefs.getString(AppPrefs.LAST_SEEN_WHATS_NEW_VERSION, "");
        return !releaseId().equals(seen);
    }

    static void markSeen(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().putString(AppPrefs.LAST_SEEN_WHATS_NEW_VERSION, releaseId()).apply();
    }

    static void show(Activity activity, SharedPreferences prefs, boolean markSeenOnDismiss) {
        showCustom(activity, prefs, markSeenOnDismiss);
    }

    static void showCustom(Activity activity, SharedPreferences prefs, boolean markSeenOnDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundResource(R.drawable.card_background);
        int outer = dp(activity, 18);
        shell.setPadding(outer, outer, outer, outer);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58));
        hero.addView(logo, logoParams);

        LinearLayout heroText = new LinearLayout(activity);
        heroText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams heroTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTextParams.leftMargin = dp(activity, 12);

        TextView eyebrow = label(activity, "Harley's Clan Forum • Release Notes", 9, R.color.hcf_meta, true);
        heroText.addView(eyebrow);

        TextView title = label(activity, "What's New", 24, R.color.hcf_text, true);
        title.setPadding(0, dp(activity, 2), 0, 0);
        heroText.addView(title);

        TextView version = label(activity, BuildInfo.VERSION_TAG + "  •  " + BuildInfo.CHANNEL, 11, R.color.hcf_cyan_bright, true);
        version.setPadding(0, dp(activity, 4), 0, 0);
        heroText.addView(version);
        hero.addView(heroText, heroTextParams);
        shell.addView(hero);

        TextView summary = label(activity, SUMMARY, 13, R.color.hcf_text, false);
        summary.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        shell.addView(summary);

        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.hcf_divider));
        shell.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(activity, 10), 0, dp(activity, 6));

        addSection(activity, body, "New • Notification Center in Settings",
                "Open Notification Center & History directly from App Settings → Notifications. The same area now shows current Do Not Disturb state and links to DND scheduling.");

        addSection(activity, body, "Fixed • Theme Settings Stay Put",
                "Changing Follow phone, Day, Night, or AMOLED now recreates the themed Settings screen and restores the section and scroll position you were using.");

        addSection(activity, body, "Updated • ErrorSys WebView Overlay",
                "Connection, HTTP, SSL, timeout and renderer recovery states are shown over the forum WebView content area. Native app chrome remains outside the overlay so the app does not look like a full-screen crash.");

        addSection(activity, body, "Preserved • Dev Foundation",
                "Notification reply composer, adaptive sync, domain failover, Firebase runtime config, App Links, updater verification, identity sync and diagnostics remain intact.");

        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(activity, 4);
        shell.addView(scroll, scrollParams);

        Button close = new Button(activity);
        UiButtons.normalizeText(close);
        close.setText("Done");
        close.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        close.setTextSize(14f);
        close.setBackgroundResource(R.drawable.button_background);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        close.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        closeParams.topMargin = dp(activity, 10);
        shell.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(shell);
        if (markSeenOnDismiss && prefs != null) {
            dialog.setOnDismissListener(d -> markSeen(prefs));
        }
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.62f;
            window.setAttributes(attrs);
            int width = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24);
            int height = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f);
            window.setLayout(Math.max(dp(activity, 280), width), Math.max(dp(activity, 420), height));
            window.setGravity(Gravity.CENTER);
        }

        AppLogger.info(activity, "whats_new_open", BuildInfo.VERSION + " custom_ui");
    }

    private static void addSection(Activity activity, LinearLayout parent, String heading, String text) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.identity_card_background);
        int pad = dp(activity, 12);
        card.setPadding(pad, pad, pad, pad);

        TextView title = label(activity, heading, 11, R.color.hcf_cyan_bright, true);
        card.addView(title);

        TextView body = label(activity, text, 12, R.color.hcf_muted, false);
        body.setLineSpacing(0f, 1.08f);
        body.setPadding(0, dp(activity, 6), 0, 0);
        card.addView(body);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, 9);
        parent.addView(card, params);
    }

    private static TextView label(Activity activity, String text, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(activity.getColor(colorRes));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private ReleaseNotes() {}
}
