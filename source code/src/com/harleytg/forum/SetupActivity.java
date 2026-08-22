package com.harleytg.forum.dev;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Native first-run and troubleshooting setup center for HCF's Android integration. */
public final class SetupActivity extends ThemedActivity {
    private static final int REQUEST_NOTIFICATIONS = 1901;
    private static final int REQUEST_FORUM_LINKS = 1902;
    private static final int REQUEST_INSTALL_SOURCE = 1903;

    private SharedPreferences prefs;

    private TextView themeValue;
    private TextView performanceValue;

    private TextView notificationStatus;
    private TextView notificationDetail;
    private Button notificationAction;

    private TextView linksStatus;
    private TextView linksDetail;
    private Button linksAction;

    private TextView updateStatus;
    private TextView updateDetail;
    private Button updateAction;

    private TextView healthStatus;
    private TextView healthDetail;
    private Button healthAction;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.apply(this);
        prefs = getSharedPreferences(AppPrefs.FILE, 0);
        SetupCenter.markSeen(this);
        try {
            NotificationHelper.refreshChannels(this);
        } catch (Throwable ignored) {
        }

        int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(buildUi());

        AppLogger.info(this, "app_setup_open", "v" + SetupCenter.CURRENT_SETUP_VERSION
                + (getIntent() != null && getIntent().getBooleanExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, false)
                ? " | first-run" : " | manual"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChoiceLabels();
        refreshStatuses();
    }

    @Override
    public void onBackPressed() {
        if (!prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) {
            SetupCenter.markSkipped(this);
        }
        AppLogger.info(this, "app_setup", "back");
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;

        boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == 0;
        AppLogger.info(this, "notification_permission",
                granted ? "granted_setup_center" : "denied_setup_center");
        try {
            NotificationSyncScheduler.apply(this);
        } catch (Throwable ignored) {
        }

        Toast.makeText(
                this,
                granted ? "Notifications allowed." : "Notifications were not allowed.",
                Toast.LENGTH_SHORT
        ).show();
        refreshStatuses();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FORUM_LINKS || requestCode == REQUEST_INSTALL_SOURCE) {
            refreshStatuses();
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(setupIntroCard());
        content.addView(appearanceAndPerformanceCard());
        content.addView(androidIntegrationCard());
        content.addView(backgroundHealthCard());
        content.addView(securityAndPrivacyCard());

        Button finish = primaryButton("Finish Setup");
        finish.setOnClickListener(v -> {
            SetupCenter.markCompleted(this);
            AppLogger.info(this, "app_setup", "completed_v" + SetupCenter.CURRENT_SETUP_VERSION);
            finish();
        });
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(-1, dp(50));
        finishLp.topMargin = dp(4);
        content.addView(finish, finishLp);

        Button continueNow = actionButton("Continue for now");
        continueNow.setOnClickListener(v -> {
            if (!prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) {
                SetupCenter.markSkipped(this);
            }
            AppLogger.info(this, "app_setup",
                    "continue_for_now_v" + SetupCenter.CURRENT_SETUP_VERSION);
            finish();
        });
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(-1, dp(48));
        continueLp.topMargin = dp(8);
        content.addView(continueNow, continueLp);

        TextView footer = text(
                "Setup Center v" + SetupCenter.CURRENT_SETUP_VERSION
                        + " • " + BuildInfo.VERSION_BUILD_LINE,
                9,
                getColor(R.color.hcf_hint)
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(14), 0, 0);
        content.addView(footer);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(8), dp(5));
        row.setMinimumHeight(dp(56));
        row.setBackgroundColor(
                ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar)
        );

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.fa_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan_bright)));
        back.setBackgroundResource(R.drawable.nav_button_background);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBackPressed());
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        logoLp.leftMargin = dp(5);
        row.addView(logo, logoLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        labelsLp.leftMargin = dp(10);
        row.addView(labels, labelsLp);

        TextView title = text("App Setup", 18, getColor(R.color.hcf_text));
        title.setTypeface(null, 1);
        labels.addView(title);

        TextView subtitle = text(
                "Appearance, performance and Android integration",
                10,
                getColor(R.color.hcf_meta)
        );
        subtitle.setTypeface(null, 1);
        labels.addView(subtitle);

        return row;
    }

    private View setupIntroCard() {
        LinearLayout card = settingsCard();
        card.addView(sectionTitle(
                "Set up this device",
                "Choose how HCF looks and performs, then connect the Android features you want."
        ));

        TextView intro = text(
                "Dark is the app default, and Auto performance is recommended. "
                        + "You can change either choice now or later in App Settings → Appearance & Performance. "
                        + "Android permissions and link settings stay optional and never block forum access.",
                11,
                getColor(R.color.hcf_muted)
        );
        card.addView(intro);
        return card;
    }

    private View appearanceAndPerformanceCard() {
        LinearLayout card = settingsCard();
        card.addView(sectionTitle(
                "Appearance & Performance",
                "Same controls used by App Settings"
        ));

        TextView themeTitle = settingTitle("Theme");
        FaIcons.applyStart(themeTitle, R.drawable.fa_circle_info);
        card.addView(themeTitle);

        themeValue = settingValue("");
        card.addView(themeValue);

        Button themeButton = actionButton("Choose Theme");
        themeButton.setOnClickListener(v -> showThemeDialog());
        card.addView(withTopMargin(themeButton, 7, 44));

        TextView themeHelp = text(
                "Night (Dark) is the default. AMOLED uses a true-black shell. "
                        + "Phone Auto follows Android; Forum Auto follows the forum's day/night mode.",
                10,
                getColor(R.color.hcf_muted)
        );
        themeHelp.setPadding(0, dp(6), 0, dp(12));
        card.addView(themeHelp);

        TextView performanceTitle = settingTitle("Performance Profile");
        FaIcons.applyStart(performanceTitle, R.drawable.fa_gear);
        card.addView(performanceTitle);

        performanceValue = settingValue("");
        card.addView(performanceValue);

        Button performanceButton = actionButton("Choose Performance Profile");
        performanceButton.setOnClickListener(v -> showPerformanceDialog());
        card.addView(withTopMargin(performanceButton, 7, 44));

        TextView performanceHelp = text(
                "Auto is recommended and adapts to memory, CPU, battery, thermal and network conditions. "
                        + "Manual profiles remain available for users who prefer a fixed behavior.",
                10,
                getColor(R.color.hcf_muted)
        );
        performanceHelp.setPadding(0, dp(6), 0, 0);
        card.addView(performanceHelp);

        refreshChoiceLabels();
        return card;
    }

    private View androidIntegrationCard() {
        LinearLayout card = settingsCard();
        card.addView(sectionTitle(
                "Android Integration",
                "Recommended setup items for alerts, links and verified app updates"
        ));

        notificationStatus = statusText();
        notificationDetail = detailText();
        notificationAction = actionButton("Allow Notifications");
        notificationAction.setOnClickListener(v -> handleNotificationAction());
        card.addView(integrationBlock(
                "Notifications",
                R.drawable.fa_bell,
                notificationStatus,
                notificationDetail,
                notificationAction
        ));

        linksStatus = statusText();
        linksDetail = detailText();
        linksAction = actionButton("Configure Forum Links");
        linksAction.setOnClickListener(
                v -> SetupCenter.openForumLinkSettings(this, REQUEST_FORUM_LINKS)
        );
        card.addView(integrationBlock(
                "Open Forum Links",
                R.drawable.fa_globe,
                linksStatus,
                linksDetail,
                linksAction
        ));

        updateStatus = statusText();
        updateDetail = detailText();
        updateAction = actionButton("Allow Secure Updates");
        updateAction.setOnClickListener(
                v -> SetupCenter.openInstallSourceSettings(this, REQUEST_INSTALL_SOURCE)
        );
        card.addView(integrationBlock(
                "Secure App Updates",
                R.drawable.fa_shield,
                updateStatus,
                updateDetail,
                updateAction
        ));

        return card;
    }

    private View backgroundHealthCard() {
        LinearLayout card = settingsCard();
        card.addView(sectionTitle(
                "Background Alerts",
                "Confirm that HCF Alerts can keep working outside the foreground app"
        ));

        healthStatus = statusText();
        healthDetail = detailText();
        healthAction = actionButton("Notification Settings");
        healthAction.setOnClickListener(v -> NotificationHelper.openAppNotificationSettings(this));

        card.addView(integrationBlock(
                "Notification / Background Alert Health",
                R.drawable.fa_circle_info,
                healthStatus,
                healthDetail,
                healthAction
        ));
        return card;
    }

    private View securityAndPrivacyCard() {
        LinearLayout card = settingsCard();
        card.addView(sectionTitle(
                "Security & Privacy",
                "Only the Android access HCF actually needs for these features"
        ));

        TextView securityNote = text(
                "HCF does not request location, contacts, microphone, camera, or broad storage access here. "
                        + "Update APKs remain restricted to the trusted HCF release source and are verified "
                        + "for package name, version/revision rules, and matching signing certificate before installation.",
                10,
                getColor(R.color.hcf_muted)
        );
        card.addView(securityNote);
        return card;
    }

    private LinearLayout settingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) {
            card.setBackgroundColor(Color.rgb(3, 5, 7));
        } else {
            card.setBackgroundResource(R.drawable.card_background);
        }
        card.setPadding(dp(15), dp(13), dp(15), dp(13));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        card.setLayoutParams(lp);
        return card;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, 0, dp(8));

        TextView titleView = text(title, 16, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        block.addView(titleView);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = text(subtitle, 10, getColor(R.color.hcf_muted));
            subtitleView.setPadding(0, dp(2), 0, 0);
            block.addView(subtitleView);
        }
        return block;
    }

    private View integrationBlock(
            String title,
            int iconRes,
            TextView status,
            TextView detail,
            Button action
    ) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.quick_action_background);
        block.setPadding(dp(13), dp(11), dp(13), dp(11));

        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(-1, -2);
        blockLp.bottomMargin = dp(8);
        block.setLayoutParams(blockLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = text(title, 14, getColor(R.color.hcf_text));
        heading.setTypeface(null, 1);
        FaIcons.applyStart(heading, iconRes);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1.0f));
        top.addView(status, new LinearLayout.LayoutParams(-2, -2));
        block.addView(top);

        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(6);
        block.addView(detail, detailLp);

        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(42));
        actionLp.topMargin = dp(8);
        block.addView(action, actionLp);

        return block;
    }

    private void showThemeDialog() {
        final String[] labels = new String[]{
                "Night (Dark) — Recommended",
                "AMOLED Black",
                "Day (Light)",
                "Auto • Phone",
                "Auto • Forum"
        };
        final String[] values = new String[]{
                ThemeManager.DARK,
                ThemeManager.AMOLED,
                ThemeManager.LIGHT,
                ThemeManager.AUTO_PHONE,
                ThemeManager.AUTO_FORUM
        };

        String current = ThemeManager.mode(this);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setSingleChoiceItems(labels, checked, null)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int selected = dialog.getListView().getCheckedItemPosition();
                    if (selected < 0 || selected >= values.length) return;

                    String value = values[selected];
                    prefs.edit().putString("app_theme", value).apply();
                    AppLogger.info(this, "setup_theme", value);
                    refreshChoiceLabels();
                    Toast.makeText(
                            this,
                            "Theme: " + ThemeManager.label(this),
                            Toast.LENGTH_SHORT
                    ).show();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showPerformanceDialog() {
        final String[] values = new String[]{
                PerformanceProfile.AUTO,
                PerformanceProfile.PERFORMANCE,
                PerformanceProfile.BALANCED,
                PerformanceProfile.QUALITY
        };
        final String[] labels = new String[]{
                "Auto — Recommended",
                "Performance",
                "Balanced",
                "High Performance"
        };

        String current = PerformanceProfile.saved(prefs);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Performance Profile")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < 0 || which >= values.length) return;

                    PerformanceProfile.save(prefs, values[which]);
                    String label = PerformanceProfile.settingLabel(this, prefs);
                    refreshChoiceLabels();
                    AppLogger.info(
                            this,
                            "setup_performance_profile",
                            values[which] + " -> " + PerformanceProfile.resolve(this, prefs)
                    );
                    Toast.makeText(
                            this,
                            label + " • " + PerformanceProfile.detail(this, prefs),
                            Toast.LENGTH_SHORT
                    ).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshChoiceLabels() {
        if (prefs == null) return;

        if (themeValue != null) {
            themeValue.setText(
                    ThemeManager.label(this)
                            + (ThemeManager.DARK.equals(ThemeManager.mode(this))
                            ? " • Default" : "")
            );
        }

        if (performanceValue != null) {
            performanceValue.setText(
                    PerformanceProfile.settingLabel(this, prefs)
                            + " • " + PerformanceProfile.detail(this, prefs)
            );
        }
    }

    private void refreshStatuses() {
        if (notificationStatus == null) return;

        try {
            NotificationHelper.refreshChannels(this);
        } catch (Throwable ignored) {
        }

        boolean runtimeAllowed = NotificationHelper.hasRuntimePermission(this);
        boolean appNotificationsAllowed = NotificationHelper.areAppNotificationsEnabled(this);
        int alertImportance = NotificationHelper.channelImportance(this);
        boolean asked = prefs.getBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, false);

        if (runtimeAllowed && appNotificationsAllowed && alertImportance != 0) {
            setStatus(notificationStatus, "✓ Allowed", true);
            notificationDetail.setText(
                    "Forum alerts and app/update status can post notifications. "
                            + NotificationHelper.status(this)
            );
            notificationAction.setVisibility(View.GONE);
        } else if (!runtimeAllowed) {
            boolean hardBlocked = Build.VERSION.SDK_INT >= 33
                    && asked
                    && !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS");
            setStatus(
                    notificationStatus,
                    hardBlocked ? "! Blocked by Android" : (asked ? "! Not allowed" : "! Needs setup"),
                    false
            );
            notificationDetail.setText(
                    asked
                            ? "Notification permission is off. Android notification settings can restore it "
                            + "without repeatedly showing the system permission prompt."
                            : "Notifications are used for forum alerts and app/update status. Android 13+ "
                            + "asks for notification permission when you choose Allow Notifications."
            );
            notificationAction.setText(
                    asked ? "Open Notification Settings" : "Allow Notifications"
            );
            notificationAction.setVisibility(View.VISIBLE);
        } else {
            setStatus(notificationStatus, "! Blocked by Android", false);
            notificationDetail.setText(
                    "The Android notification permission is present, but app or HCF Alerts channel "
                            + "settings are currently blocking notifications. "
                            + NotificationHelper.status(this)
            );
            notificationAction.setText("Open Notification Settings");
            notificationAction.setVisibility(View.VISIBLE);
        }

        SetupCenter.ForumLinksState linkState = SetupCenter.forumLinksState(this);
        if (!linkState.inspectable) {
            setStatus(linksStatus, "Optional", true);
        } else {
            setStatus(linksStatus, linkState.ready ? "✓ Ready" : "! Needs setup", linkState.ready);
        }
        linksDetail.setText(
                linkState.detail
                        + " Supported domains: "
                        + SetupCenter.PRIMARY_FORUM_HOST
                        + " and "
                        + SetupCenter.BACKUP_FORUM_HOST
                        + "."
        );
        linksAction.setVisibility(View.VISIBLE);

        boolean canInstall = AppSecurity.canInstallUpdates(this);
        setStatus(
                updateStatus,
                canInstall ? "✓ Allowed" : "! Permission required",
                canInstall
        );
        updateDetail.setText(
                canInstall
                        ? "Android allows HCF to hand verified update APKs to the package installer. "
                        + "HCF's package, version/revision and signing-certificate checks remain enforced."
                        : "Android requires per-app approval before HCF can open a verified update APK. "
                        + "This does not weaken HCF's trusted-source, package, version/revision or "
                        + "signing-certificate verification."
        );
        updateAction.setVisibility(canInstall ? View.GONE : View.VISIBLE);

        boolean backgroundSync = prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
        String lastSync = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
        if (lastSync == null || lastSync.trim().isEmpty()) {
            lastSync = "Waiting for first sync";
        }

        boolean healthReady = runtimeAllowed
                && appNotificationsAllowed
                && alertImportance != 0
                && backgroundSync;
        setStatus(
                healthStatus,
                healthReady ? "✓ Ready" : "! Needs attention",
                healthReady
        );
        healthDetail.setText(
                "Android notification permission: " + (runtimeAllowed ? "Allowed" : "Not allowed")
                        + "\nApp notifications: " + (appNotificationsAllowed ? "Enabled" : "Blocked")
                        + "\nHCF Alerts channel: "
                        + NotificationHelper.channelStatus(this, NotificationHelper.CHANNEL_ID)
                        + "\nHCF Silent Alerts: "
                        + NotificationHelper.channelStatus(this, NotificationHelper.SILENT_CHANNEL_ID)
                        + "\nBackground notification sync: "
                        + (backgroundSync ? "Enabled" : "Paused")
                        + "\nLast sync status: " + lastSync
        );
        healthAction.setVisibility(healthReady ? View.GONE : View.VISIBLE);
    }

    private void handleNotificationAction() {
        if (Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            NotificationHelper.openAppNotificationSettings(this);
            return;
        }

        boolean asked = prefs.getBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, false);
        if (asked) {
            NotificationHelper.openAppNotificationSettings(this);
            return;
        }

        prefs.edit()
                .putBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, true)
                .putInt(AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION, BuildInfo.VERSION_CODE)
                .apply();

        requestPermissions(
                new String[]{"android.permission.POST_NOTIFICATIONS"},
                REQUEST_NOTIFICATIONS
        );
    }

    private void setStatus(TextView view, String value, boolean ready) {
        view.setText(value);
        view.setTextColor(getColor(ready ? R.color.hcf_cyan_bright : R.color.hcf_error));
    }

    private TextView statusText() {
        TextView view = text("Checking…", 10, getColor(R.color.hcf_cyan_bright));
        view.setTypeface(null, 1);
        view.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView detailText() {
        return text("Checking Android status…", 11, getColor(R.color.hcf_muted));
    }

    private TextView settingTitle(String value) {
        TextView view = text(value, 13, getColor(R.color.hcf_text));
        view.setTypeface(null, 1);
        view.setPadding(0, dp(3), 0, 0);
        return view;
    }

    private TextView settingValue(String value) {
        TextView view = text(value, 11, getColor(R.color.hcf_cyan_bright));
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0.0f, 1.08f);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setTextColor(getColor(R.color.hcf_on_accent));
        button.setBackgroundResource(R.drawable.error_primary_button_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(12.0f);
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private View withTopMargin(View view, int topDp, int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(heightDp));
        lp.topMargin = dp(topDp);
        view.setLayoutParams(lp);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
