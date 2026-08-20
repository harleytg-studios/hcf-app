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

/* loaded from: classes.dex */
final class ReleaseNotes {
    static final String NOTES = "Harley's Clan Forum (app) v1.0 • Stable v10000072\n• Stable versionCode 10000072.\n• Theme selection is now four buttons: Forum Auto, Phone Auto, Light, and Dark.\n• Forum Auto is the default and follows FoF Night Mode; Phone Auto follows Android directly.\n• Theme handoff recreation is debounced to prevent the previous Auto-theme flicker.\n• Developer Tools reorganized into Notification Lab, Runtime & WebView, Diagnostics & Logs, and Telemetry groups.\n• Added a compact Notification Test Console, Force Notification Sync, and copyable Runtime Snapshot.\n• About now shows app identity, build/channel, device/runtime, forum endpoints, privacy, release and support information in connected cards.\n• Forum Identity drawer profile photo now fills the entire inner cyan frame with no letterboxing gap.\n• Real drawer avatars use FIT_XY inside the protected 1dp border; the HTG placeholder keeps FIT_CENTER.\n• Downloaded-APK installer visibility fix from v10000046 is retained.\n• Launcher icon reverted to the original square HTG artwork.\n• Dedicated round/adaptive launcher-icon selection was removed from the app manifest.\n• Auto can now promote capable devices to Auto • Real-Time for faster foreground notification and forum freshness checks.\n• Notification polling is adaptive: fast while actively using HCF, progressively slower after backgrounding, screen-off, Battery Saver, or constrained-device conditions.\n• Live forum checks use smaller change signatures and HTTP validators instead of repeatedly hashing large API responses.\n• Shared executors reduce repeated background thread creation.\n• WebView timers/renderer priority now follow foreground/background state, while renderer-crash recovery remains enabled.\n• Notification state writes and routine success logging are reduced to meaningful changes.\n• Network restoration, app resume, pull-to-refresh, notification opening, and successful forum API mutations request immediate freshness sync.\n• Native FCM transport is not bundled in this source build, so adaptive polling remains the active fallback transport.\n\nThis Stable release contains the full v10000072 feature set promoted from Dev.";
    static final String SUMMARY = "Stable v10000072 • Four-button theme selector";

    static void seedForFreshInstall(SharedPreferences sharedPreferences) {
        markSeen(sharedPreferences);
    }

    private static String releaseId() {
        return "1.0-10000072";
    }

    static boolean shouldNotify(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return false;
        }
        return !releaseId().equals(sharedPreferences.getString("last_seen_whats_new_version", ""));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void markSeen(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return;
        }
        sharedPreferences.edit().putString("last_seen_whats_new_version", releaseId()).apply();
    }

    static void show(Activity activity, SharedPreferences sharedPreferences, boolean z) {
        showCustom(activity, sharedPreferences, z);
    }

    static void showCustom(Activity activity, final SharedPreferences sharedPreferences, boolean z) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        dialog.setCancelable(true);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundResource(R.drawable.card_background);
        int dp = dp(activity, 18);
        linearLayout.setPadding(dp, dp, dp, dp);
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        ImageView imageView = new ImageView(activity);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout2.addView(imageView, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));
        LinearLayout linearLayout3 = new LinearLayout(activity);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(activity, 12);
        linearLayout3.addView(label(activity, "Harley's Clan Forum • Release Notes", 9, R.color.hcf_meta, true));
        TextView label = label(activity, "What's New", 24, R.color.hcf_text, true);
        label.setPadding(0, dp(activity, 2), 0, 0);
        linearLayout3.addView(label);
        TextView label2 = label(activity, "v1.0  •  Dev", 11, R.color.hcf_cyan_bright, true);
        label2.setPadding(0, dp(activity, 4), 0, 0);
        linearLayout3.addView(label2);
        linearLayout2.addView(linearLayout3, layoutParams);
        linearLayout.addView(linearLayout2);
        TextView label3 = label(activity, SUMMARY, 13, R.color.hcf_text, false);
        label3.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        linearLayout.addView(label3);
        View view = new View(activity);
        view.setBackgroundColor(activity.getColor(R.color.hcf_divider));
        linearLayout.addView(view, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(1);
        LinearLayout linearLayout4 = new LinearLayout(activity);
        linearLayout4.setOrientation(1);
        linearLayout4.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        addSection(activity, linearLayout4, "Harley's Clan Forum (app) v1.0", "This is the release for Harley's Clan Forum (app) v1.0. The public version remains 1.0 while the internal build number advances so it installs over the earlier v1.0 package.");
        addSection(activity, linearLayout4, "Updated • Account & Security", "Account & Security now keeps forum-account controls together: identity, profile shortcuts and Account Security access. Android app permissions and hardening remain under Advanced & About > Permissions & Security.");
        addSection(activity, linearLayout4, "Updated • Connected sub-settings", "Expanded sub-settings now use the top row as the actual header of the content below it, removing duplicate titles and making each open section feel like one connected card.");
        addSection(activity, linearLayout4, "Updated • Passive notification silence", "Silence passive notifications now keeps live background sync active while routing service status, generic summaries and test/status alerts through silent low-priority behavior. Messages, mentions and replies remain normal alerts.");
        addSection(activity, linearLayout4, "New • Contact Support v2", "Contact Support now matches the HCF settings design with expandable sections, locked forum/app context, structured issue fields, privacy controls, report preview and email handoff.");
        addSection(activity, linearLayout4, "New • Performance Profiles", "Choose Auto, Performance, Balanced or Quality in App Settings. Auto reduces motion automatically on low-RAM devices or while Android battery saver is active.");
        scrollView.addView(linearLayout4, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams2.topMargin = dp(activity, 4);
        linearLayout.addView(scrollView, layoutParams2);
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText("Done");
        button.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button.setTextSize(14.0f);
        button.setBackgroundResource(R.drawable.button_background);
        button.setGravity(17);
        button.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        layoutParams3.topMargin = dp(activity, 10);
        linearLayout.addView(button, layoutParams3);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.ReleaseNotes$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                dialog.dismiss();
            }
        });
        dialog.setContentView(linearLayout);
        if (z && sharedPreferences != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.harleytg.forum.ReleaseNotes$$ExternalSyntheticLambda1
                @Override // android.content.DialogInterface.OnDismissListener
                public final void onDismiss(DialogInterface dialogInterface) {
                    ReleaseNotes.markSeen(sharedPreferences);
                }
            });
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(2);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
            window.setLayout(Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)), Math.max(dp(activity, 420), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f)));
            window.setGravity(17);
        }
        AppLogger.info(activity, "whats_new_open", "1.0 custom_ui");
    }

    private static void addSection(Activity activity, LinearLayout linearLayout, String str, String str2) {
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(1);
        linearLayout2.setBackgroundResource(R.drawable.identity_card_background);
        int dp = dp(activity, 12);
        linearLayout2.setPadding(dp, dp, dp, dp);
        linearLayout2.addView(label(activity, str, 11, R.color.hcf_cyan_bright, true));
        TextView label = label(activity, str2, 12, R.color.hcf_muted, false);
        label.setLineSpacing(0.0f, 1.08f);
        label.setPadding(0, dp(activity, 6), 0, 0);
        linearLayout2.addView(label);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(activity, 9);
        linearLayout.addView(linearLayout2, layoutParams);
    }

    private static TextView label(Activity activity, String str, int i, int i2, boolean z) {
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(activity.getColor(i2));
        if (z) {
            textView.setTypeface(null, 1);
        }
        return textView;
    }

    private static int dp(Activity activity, int i) {
        return Math.round(i * activity.getResources().getDisplayMetrics().density);
    }

    private ReleaseNotes() {
    }
}
