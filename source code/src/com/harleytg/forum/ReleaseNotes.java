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

/** What's New UI for Stable v10000072. */
final class ReleaseNotes {
    static final String NOTES = "Harley's Clan Forum (app) v1.0 • Stable v10000072\n"
            + "• Stable versionCode 10000072.\n"
            + "• Theme selection uses Forum Auto, Phone Auto, Light, and Dark.\n"
            + "• Forum Auto follows FoF Night Mode; Phone Auto follows Android directly.\n"
            + "• Theme handoff recreation is debounced to prevent Auto-theme flicker.\n"
            + "• Stable diagnostics are grouped into notification, runtime, recovery, logs, and telemetry tools.\n"
            + "• About shows app identity, Stable build/channel, device/runtime, forum endpoints, privacy, release and support information.\n"
            + "• Forum Identity drawer profile photos fill the inner cyan frame without a letterboxing gap.\n"
            + "• Launcher icon uses the original square HTG artwork; dedicated round launcher resources are removed.\n"
            + "• Adaptive performance and notification polling improve foreground freshness while reducing background load.\n"
            + "• Network restoration, app resume, pull-to-refresh, notification opening, and successful forum API mutations request immediate freshness sync.\n"
            + "• Stable update checks, downloads, verification, and installation are locked to official Stable releases.\n\n"
            + "This is the complete public Stable v10000072 feature set.";
    static final String SUMMARY = "Stable v10000072 • Public Stable release";

    static void seedForFreshInstall(SharedPreferences prefs) {
        markSeen(prefs);
    }

    private static String releaseId() {
        return "1.0-10000072-stable";
    }

    static boolean shouldNotify(SharedPreferences prefs) {
        return prefs != null && !releaseId().equals(
                prefs.getString(AppPrefs.LAST_SEEN_WHATS_NEW_VERSION, ""));
    }

    static void markSeen(SharedPreferences prefs) {
        if (prefs != null) {
            prefs.edit().putString(AppPrefs.LAST_SEEN_WHATS_NEW_VERSION, releaseId()).apply();
        }
    }

    static void show(Activity activity, SharedPreferences prefs, boolean markOnDismiss) {
        showCustom(activity, prefs, markOnDismiss);
    }

    static void showCustom(Activity activity, final SharedPreferences prefs, boolean markOnDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.card_background);
        int padding = dp(activity, 18);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(16);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        titleParams.leftMargin = dp(activity, 12);
        titles.addView(label(activity, "Harley's Clan Forum • Release Notes", 9, R.color.hcf_meta, true));
        TextView whatsNew = label(activity, "What's New", 24, R.color.hcf_text, true);
        whatsNew.setPadding(0, dp(activity, 2), 0, 0);
        titles.addView(whatsNew);
        TextView version = label(activity, "v1.0  •  Stable", 11, R.color.hcf_cyan_bright, true);
        version.setPadding(0, dp(activity, 4), 0, 0);
        titles.addView(version);
        header.addView(titles, titleParams);
        root.addView(header);

        TextView summary = label(activity, SUMMARY, 13, R.color.hcf_text, false);
        summary.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        root.addView(summary);

        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.hcf_divider));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout sections = new LinearLayout(activity);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(0, dp(activity, 10), 0, dp(activity, 6));

        addSection(activity, sections, "Stable v10000072",
                "This public Stable build keeps package com.harleytg.forum and versionCode 10000072. Its updater is locked to official non-prerelease Stable APKs.");
        addSection(activity, sections, "Updated • Account & Security",
                "Account & Security keeps forum-account controls together: identity, profile shortcuts and Account Security access. Android app permissions and hardening remain under Advanced & About > Permissions & Security.");
        addSection(activity, sections, "Updated • Connected sub-settings",
                "Expanded sub-settings use the top row as the actual header of the content below it, removing duplicate titles and making each open section feel like one connected card.");
        addSection(activity, sections, "Updated • Passive notification silence",
                "Silence passive notifications keeps live background sync active while routing service status, generic summaries and diagnostics/status alerts through silent low-priority behavior. Messages, mentions and replies remain normal alerts.");
        addSection(activity, sections, "New • Contact Support v2",
                "Contact Support matches the HCF settings design with expandable sections, locked forum/app context, structured issue fields, privacy controls, report preview and email handoff.");
        addSection(activity, sections, "New • Performance Profiles",
                "Choose Auto, Performance, Balanced or Quality in App Settings. Auto adapts motion and background work to device conditions.");

        scroll.addView(sections, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollParams.topMargin = dp(activity, 4);
        root.addView(scroll, scrollParams);

        Button done = new Button(activity);
        UiButtons.normalizeText(done);
        done.setText("Done");
        done.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        done.setTextSize(14.0f);
        done.setBackgroundResource(R.drawable.button_background);
        done.setGravity(17);
        done.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        done.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        doneParams.topMargin = dp(activity, 10);
        root.addView(done, doneParams);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        dialog.setContentView(root);
        if (markOnDismiss && prefs != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    markSeen(prefs);
                }
            });
        }
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
            window.setLayout(
                    Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)),
                    Math.max(dp(activity, 420), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f)));
            window.setGravity(17);
        }
        AppLogger.info(activity, "whats_new_open", "1.0 | Stable | 10000072");
    }

    private static void addSection(Activity activity, LinearLayout parent, String title, String body) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.identity_card_background);
        int padding = dp(activity, 12);
        section.setPadding(padding, padding, padding, padding);
        section.addView(label(activity, title, 11, R.color.hcf_cyan_bright, true));
        TextView bodyView = label(activity, body, 12, R.color.hcf_muted, false);
        bodyView.setLineSpacing(0.0f, 1.08f);
        bodyView.setPadding(0, dp(activity, 6), 0, 0);
        section.addView(bodyView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 9);
        parent.addView(section, params);
    }

    private static TextView label(Activity activity, String text, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(activity.getColor(color));
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private ReleaseNotes() {}
}
