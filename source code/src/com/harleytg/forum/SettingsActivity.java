package com.harleytg.forum.dev;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/**
 * Harley's Clan Forum settings center.
 *
 * v10000080 cleanup goals:
 * - one owner for each Advanced & About setting;
 * - version/build text comes from the installed package with BuildInfo fallback;
 * - search indexes individual settings and navigates to a stable target key.
 */
public final class SettingsActivity extends ThemedActivity {
    private static final int REQUEST_NOTIFICATIONS = 901;
    private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 2410;
    private static final String TARGET_TAG_PREFIX = "hcf_setting:";

    private UpdateChecker.Release availableRelease;
    private TextView cookieStatus;
    private ImageButton headerBackButton;
    private TextView headerSubtitleView;
    private TextView headerTitleView;
    private TextView notificationStatus;
    private SharedPreferences prefs;
    private TextView securityStatus;
    private TextView serverStatus;
    private LinearLayout settingsContent;
    private ScrollView settingsScroll;
    private TextView telemetryStatus;
    private TextView updateChannelStatus;
    private Button updateDownloadButton;
    private Button updateInstallButton;
    private TextView updateStatus;
    private String currentSettingsSection = "";
    private String pendingSettingKey = "";
    private String pendingSettingSection = "";

    private static final class SettingTarget {
        final String key;
        final String title;
        final String keywords;
        final String category;
        final String section;

        SettingTarget(String key, String title, String keywords, String category, String section) {
            this.key = key;
            this.title = title;
            this.keywords = keywords == null ? "" : keywords;
            this.category = category;
            this.section = section;
        }

        String haystack() {
            return (title + " " + keywords + " " + category + " " + section).toLowerCase(Locale.US);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.apply(this);
        prefs = getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("notifications_enabled", true)) {
            prefs.edit().putBoolean("notifications_enabled", true).apply();
        }
        try {
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi());
            handleInstallIntent(getIntent());
            AppLogger.info(this, "settings_open", BuildInfo.VERSION);
        } catch (Throwable error) {
            AppLogger.crash(this, error);
            showSettingsRecovery(error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            refreshStatusLabels();
            resumeUpdateInstallAfterPermission();
        } catch (Throwable error) {
            AppLogger.error(this, "settings_resume", error.getClass().getSimpleName());
        }
    }

    private void showSettingsRecovery(Throwable error) {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(17);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));
            root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            TextView title = text("App Control Center • Recovery Mode", 18, getColor(R.color.hcf_accent_text));
            title.setGravity(17);
            title.setTypeface(null, 1);
            root.addView(title);
            TextView details = text("Version " + BuildInfo.VERSION + " • Build " + installedVersionCode()
                    + "\n\nSettings UI recovered from: " + error.getClass().getSimpleName(), 13, getColor(R.color.hcf_text));
            details.setGravity(17);
            root.addView(details);
            Button back = actionButton("Back to Forum", v -> finish());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
            lp.topMargin = dp(18);
            root.addView(back, lp);
            setContentView(root);
        } catch (Throwable ignored) {
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        root.addView(buildHeader());
        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        settingsScroll.addView(settingsContent, new FrameLayout.LayoutParams(-1, -2));
        showSettingsHome();
        root.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(8), dp(compact() ? 2 : 5), dp(8), dp(compact() ? 2 : 5));
        row.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));
        row.setMinimumHeight(dp(compact() ? 46 : 56));
        headerBackButton = chromeButton("Back");
        headerBackButton.setContentDescription("Back");
        headerBackButton.setOnClickListener(v -> {
            if (currentSettingsSection == null || currentSettingsSection.isEmpty()) finish();
            else showSettingsHome();
        });
        row.addView(headerBackButton, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        logoLp.leftMargin = dp(4);
        row.addView(logo, logoLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        labelsLp.leftMargin = dp(10);
        row.addView(labels, labelsLp);
        headerTitleView = text("App Settings", 18, getColor(R.color.hcf_text));
        headerTitleView.setTypeface(null, 1);
        labels.addView(headerTitleView);
        headerSubtitleView = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 10, getColor(R.color.hcf_meta));
        headerSubtitleView.setTypeface(null, 1);
        labels.addView(headerSubtitleView);
        return row;
    }

    private void showSettingsHome() {
        currentSettingsSection = "";
        pendingSettingKey = "";
        pendingSettingSection = "";
        updateSettingsHeader("App Settings", BuildInfo.DEVELOPMENT_BUILD_LABEL);
        if (settingsContent == null) return;
        settingsContent.removeAllViews();
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search settings…");
        search.setHintTextColor(getColor(R.color.hcf_hint));
        search.setTextColor(getColor(R.color.hcf_text));
        search.setTextSize(14.0f);
        search.setBackgroundResource(R.drawable.quick_action_background);
        search.setPadding(dp(15), 0, dp(15), 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(compact() ? 42 : 48));
        searchLp.bottomMargin = dp(10);
        settingsContent.addView(search, searchLp);
        TextView heading = text("Settings", 10, getColor(R.color.hcf_cyan));
        heading.setTypeface(null, 1);
        heading.setPadding(dp(4), dp(2), 0, dp(7));
        settingsContent.addView(heading);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        settingsContent.addView(results, new LinearLayout.LayoutParams(-1, -2));
        populateSettingsCategories(results);
        TextView noMatches = text("No matching settings.", 12, getColor(R.color.hcf_muted));
        noMatches.setGravity(17);
        noMatches.setPadding(0, dp(18), 0, dp(18));
        noMatches.setVisibility(View.GONE);
        settingsContent.addView(noMatches);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSettingsSearchResults(results, noMatches, s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        TextView footer = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 9, getColor(R.color.hcf_hint));
        footer.setGravity(17);
        footer.setPadding(0, dp(14), 0, dp(4));
        settingsContent.addView(footer);
        settingsScroll.post(() -> settingsScroll.scrollTo(0, 0));
        refreshStatusLabels();
    }

    private void populateSettingsCategories(LinearLayout list) {
        list.removeAllViews();
        addSettingsCategory(list, "Account & Security", "Forum identity, profile and account controls", "account_security");
        addSettingsCategory(list, "Notifications", "Required alerts, silent background alerts and notification tools", "notifications");
        addSettingsCategory(list, "Appearance & Performance", "Theme, interface density and performance", "appearance");
        addSettingsCategory(list, "Forum & Site Data", "Server routing, links, cookies and local site data", "forum_data");
        addSettingsCategory(list, "Advanced & About", "App permissions, updates, recovery, telemetry, developer tools and build information", "advanced");
    }

    private void addSettingsCategory(LinearLayout list, String title, String subtitle, String key) {
        list.addView(settingsCategoryRow(title, subtitle, key));
    }

    private View settingsCategoryRow(String title, String subtitle, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(15), dp(compact() ? 8 : 11), dp(12), dp(compact() ? 8 : 11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        row.setLayoutParams(lp);
        ImageView icon = settingsSectionIcon(settingsIconForKey(key));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconLp.rightMargin = dp(12);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView arrow = text("›", 24, getColor(R.color.hcf_cyan_bright));
        arrow.setGravity(17);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), -1));
        row.setOnClickListener(v -> showSettingsSection(key));
        return row;
    }

    private SettingTarget[] settingsSearchIndex() {
        return new SettingTarget[]{
            new SettingTarget("open_identity", "Open Account & Identity", "profile username identity account", "account_security", "account_identity"),
            new SettingTarget("open_forum_profile", "Open My Forum Profile", "profile account user", "account_security", "account_controls"),
            new SettingTarget("open_account_security", "Open Account Security", "password email two factor 2fa sessions security", "account_security", "account_controls"),
            new SettingTarget("allow_android_notification_permission", "Allow Android Notification Permission", "notification alerts permission android", "notifications", "hcf_alerts"),
            new SettingTarget("background_notification_sync", "Background notification sync", "notification sync background HCF Alerts real forum alerts outside app closed app", "notifications", "hcf_alerts"),
            new SettingTarget("silence_hcf_silent_alerts", "Disable HCF Silent Alerts", "silent service status background notification", "notifications", "silent_alerts"),
            new SettingTarget("open_developer_tools", "Open Developer Tools", "notification test developer", "notifications", "test_alerts"),
            new SettingTarget("theme", "Theme", "forum auto phone auto dark light appearance", "appearance", "appearance_performance"),
            new SettingTarget("performance_profile", "Performance Profile", "performance balanced quality animation motion", "appearance", "appearance_performance"),
            new SettingTarget("live_forum_updates", "Live forum updates", "live forum update refresh", "appearance", "appearance_performance"),
            new SettingTarget("show_secure_url_bar", "Show secure URL bar", "url address security header", "appearance", "appearance_performance"),
            new SettingTarget("show_startup_screen", "Show startup connection screen", "startup launch connection screen", "appearance", "appearance_performance"),
            new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),
            new SettingTarget("external_links", "Allow external links to open in browser/apps", "links browser external apps", "forum_data", "connection_routing"),
            new SettingTarget("retry_primary", "Retry Primary Forum on Next Open", "primary server retry routing", "forum_data", "connection_routing"),
            new SettingTarget("forum_link_settings", "Open Forum Link Settings", "android links domains primary backup", "forum_data", "connection_routing"),
            new SettingTarget("cookie_manager", "Open Cookie Manager", "cookies site data privacy", "forum_data", "cookies_site_data"),
            new SettingTarget("clear_site_data", "Clear Forum Site Data & Sign Out", "cookies cache data sign out privacy", "forum_data", "cookies_site_data"),
            new SettingTarget("permission_status", "Permission status", "permission security status", "advanced", "permissions_security"),
            new SettingTarget("notification_permission", "Allow Notification Permission", "permission security notifications android", "advanced", "permissions_security"),
            new SettingTarget("secure_updates_permission", "Allow Secure App Updates", "permission security install unknown apps apk", "advanced", "permissions_security"),
            new SettingTarget("android_permission_settings", "Android App Permission Settings", "permission security android settings", "advanced", "permissions_security"),
            new SettingTarget("update_channel", "Update channel", "updates channel stable dev beta feed official releases", "advanced", "app_updates"),
            new SettingTarget("installed_version", "Installed version", "version versioncode build installed", "advanced", "app_updates"),
            new SettingTarget("automatic_update_checks", "Automatic update checks", "updates check automatic", "advanced", "app_updates"),
            new SettingTarget("auto_download_apk", "Automatically download new APKs", "updates apk download automatic", "advanced", "app_updates"),
            new SettingTarget("auto_installer", "Open installer automatically after download", "updates apk installer install unknown apps", "advanced", "app_updates"),
            new SettingTarget("check_updates", "Check for Updates", "updates latest release version", "advanced", "app_updates"),
            new SettingTarget("apk_verification", "APK verification information", "updates apk verification signing certificate package versioncode", "advanced", "app_updates"),
            new SettingTarget("error_recovery_check", "Run Error & Recovery Check", "errors crash recovery diagnostics webview", "advanced", "error_recovery"),
            new SettingTarget("runtime_snapshot", "Runtime Snapshot", "runtime webview renderer network native error recovery", "advanced", "error_recovery"),
            new SettingTarget("copy_diagnostic_report", "Copy Sanitized Diagnostic Report", "logs crash errors reports diagnostics", "advanced", "error_recovery"),
            new SettingTarget("view_crash_logs", "View App & Crash Logs", "logs crash errors diagnostics", "advanced", "error_recovery"),
            new SettingTarget("clear_webview_cache", "Clear WebView Cache", "webview cache recovery clear errors", "advanced", "error_recovery"),
            new SettingTarget("renderer_recovery", "Renderer recovery state", "renderer recovery webview native error state", "advanced", "error_recovery"),
            new SettingTarget("telemetry_enabled", "Enable Telemetry Services", "telemetry reports diagnostics privacy", "advanced", "telemetry"),
            new SettingTarget("telemetry_level", "Telemetry level", "telemetry reports diagnostics", "advanced", "telemetry"),
            new SettingTarget("auto_crash_reports", "Automatically send crash reports", "telemetry reports crash automatic", "advanced", "telemetry"),
            new SettingTarget("ask_before_crash_report", "Ask me before every crash report", "telemetry reports crash ask privacy", "advanced", "telemetry"),
            new SettingTarget("auto_error_reports", "Automatically send WebView/update errors", "telemetry webview updates errors reports", "advanced", "telemetry"),
            new SettingTarget("report_privacy", "Report Privacy", "telemetry privacy report identity email device route", "advanced", "telemetry"),
            new SettingTarget("diagnostic_feedback", "Send Diagnostic Feedback", "telemetry diagnostic feedback reports", "advanced", "telemetry"),
            new SettingTarget("preview_telemetry", "Preview Telemetry Report", "telemetry preview reports privacy", "advanced", "telemetry"),
            new SettingTarget("telemetry_history", "View Report History", "telemetry reports history", "advanced", "telemetry"),
            new SettingTarget("clear_telemetry", "Clear Local Telemetry Reports", "telemetry reports clear privacy", "advanced", "telemetry"),
            new SettingTarget("developer_environment", "Environment information", "stable dev beta package channel build update feed developer", "advanced", "developer_tools"),
            new SettingTarget("notification_test_console", "Notification Test Console", "notification test developer alerts", "advanced", "developer_tools"),
            new SettingTarget("test_notification_service", "Test Notification Service", "notification test service developer", "advanced", "developer_tools"),
            new SettingTarget("force_notification_sync", "Force Notification Sync", "notification force sync test developer", "advanced", "developer_tools"),
            new SettingTarget("app_identity", "App identity", "version versioncode build about app name", "advanced", "about"),
            new SettingTarget("build_channel", "Build & channel", "version versioncode build channel package update feed about", "advanced", "about"),
            new SettingTarget("device_runtime", "Device & runtime", "device runtime android webview build about", "advanced", "about"),
            new SettingTarget("forum_endpoints", "Forum endpoints", "forum primary backup domain endpoint about", "advanced", "about"),
            new SettingTarget("whats_new", "View What's New", "release notes changes version about", "advanced", "about"),
            new SettingTarget("release_build_details", "Release & Build Details", "version versioncode build release about", "advanced", "about"),
            new SettingTarget("copy_app_information", "Copy App Information", "version versioncode build about copy", "advanced", "about"),
            new SettingTarget("contact_support", "Contact Support", "support help about", "advanced", "about"),
            new SettingTarget("privacy", "Privacy", "privacy reports data about", "advanced", "about")
        };
    }

    private void renderSettingsSearchResults(LinearLayout list, TextView noMatches, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            populateSettingsCategories(list);
            noMatches.setVisibility(View.GONE);
            return;
        }
        list.removeAllViews();
        int matches = 0;
        for (SettingTarget target : settingsSearchIndex()) {
            if (matchesSearch(target, normalized)) {
                list.addView(settingsSearchResultRow(target));
                matches++;
            }
        }
        noMatches.setVisibility(matches == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matchesSearch(SettingTarget target, String query) {
        String haystack = target.haystack();
        for (String term : query.split("\\s+")) {
            if (!term.isEmpty() && !haystack.contains(term)) return false;
        }
        return true;
    }

    private View settingsSearchResultRow(final SettingTarget target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(15), dp(compact() ? 9 : 12), dp(12), dp(compact() ? 9 : 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        row.setLayoutParams(lp);
        ImageView icon = settingsSectionIcon(settingsIconForTitle(sectionDisplayName(target.section)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(23), dp(23));
        iconLp.rightMargin = dp(12);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(target.title, 14, getColor(R.color.hcf_text));
        title.setTypeface(null, 1);
        labels.addView(title);
        labels.addView(text("App Settings › " + settingsSectionName(target.category) + " › " + sectionDisplayName(target.section), 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView arrow = text("›", 24, getColor(R.color.hcf_cyan_bright));
        arrow.setGravity(17);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), -1));
        row.setOnClickListener(v -> navigateToSetting(target));
        return row;
    }

    private void navigateToSetting(SettingTarget target) {
        if (target == null) return;
        pendingSettingKey = target.key;
        pendingSettingSection = target.section;
        showSettingsSection(target.category);
    }

    private void navigateToSettingKey(String key) {
        for (SettingTarget target : settingsSearchIndex()) {
            if (target.key.equals(key)) {
                navigateToSetting(target);
                return;
            }
        }
    }

    private void showSettingsSection(String section) {
        if (section == null) section = "";
        currentSettingsSection = section;
        if (settingsContent == null) return;
        settingsContent.removeAllViews();
        updateSettingsHeader(settingsSectionName(section), settingsSectionSubtitle(section));
        switch (section) {
            case "account_security":
                settingsContent.addView(connectedSettingsPanel("Account & Identity", "Signed-in profile and forum identity controls", accountIdentityCard(), shouldExpand("account_identity", true)));
                settingsContent.addView(connectedSettingsPanel("Account Controls", "Profile, password, email and session security shortcuts", accountControlsCard(), shouldExpand("account_controls", false)));
                break;
            case "notifications":
                settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Real forum notifications • background delivery", mainAlertsCard(), shouldExpand("hcf_alerts", true)));
                settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Silent service-status channel only", silentAlertsCard(), shouldExpand("silent_alerts", false)));
                settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", "Dev/Beta diagnostics only", testAlertsInfoCard(), shouldExpand("test_alerts", false)));
                break;
            case "appearance":
                settingsContent.addView(connectedSettingsPanel("Appearance & Performance", "Theme, interface and rendering preferences", interfaceCard(), shouldExpand("appearance_performance", true)));
                break;
            case "forum_data":
                settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));
                settingsContent.addView(connectedSettingsPanel("Cookies & Site Data", "Forum data stored locally on this device", privacyCard(), shouldExpand("cookies_site_data", false)));
                break;
            case "advanced":
                settingsContent.addView(connectedSettingsPanel("Permissions & Security", "Android permissions and app hardening", securityCard(), shouldExpand("permissions_security", false)));
                settingsContent.addView(connectedSettingsPanel("App Updates", "Secure update checks, download and install controls", updateCard(), shouldExpand("app_updates", false)));
                settingsContent.addView(connectedSettingsPanel("Error & Recovery", "Diagnostics, runtime state, logs and WebView recovery tools", diagnosticsCard(), shouldExpand("error_recovery", false)));
                settingsContent.addView(connectedSettingsPanel("Telemetry", "Optional app health and diagnostic reporting", telemetryCard(), shouldExpand("telemetry", false)));
                settingsContent.addView(connectedSettingsPanel("Developer Tools", developerToolsSubtitle(), developerToolsCard(), shouldExpand("developer_tools", false)));
                settingsContent.addView(connectedSettingsPanel("About Harley's Clan Forum", "Version, release notes and build information", aboutCard(), shouldExpand("about", false)));
                break;
            default:
                showSettingsHome();
                return;
        }
        settingsContent.addView(actionButton("‹  Back to all settings", v -> showSettingsHome()));
        settingsScroll.postDelayed(() -> {
            if (!pendingSettingKey.isEmpty()) scrollToPendingSetting();
            else settingsScroll.scrollTo(0, 0);
        }, 80L);
        refreshStatusLabels();
    }

    private boolean shouldExpand(String section, boolean defaultOpen) {
        if (!pendingSettingKey.isEmpty()) return section.equals(pendingSettingSection);
        return defaultOpen;
    }

    private void scrollToPendingSetting() {
        if (settingsContent == null || settingsScroll == null || pendingSettingKey.isEmpty()) return;
        View target = findTaggedView(settingsContent, TARGET_TAG_PREFIX + pendingSettingKey);
        if (target == null) {
            pendingSettingKey = "";
            pendingSettingSection = "";
            settingsScroll.scrollTo(0, 0);
            return;
        }
        Rect rect = new Rect();
        target.getDrawingRect(rect);
        settingsContent.offsetDescendantRectToMyCoords(target, rect);
        settingsScroll.smoothScrollTo(0, Math.max(0, rect.top - dp(24)));
        target.animate().alpha(0.68f).setDuration(120L).withEndAction(() -> target.animate().alpha(1.0f).setDuration(260L).start()).start();
        pendingSettingKey = "";
        pendingSettingSection = "";
    }

    private View findTaggedView(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private <T extends View> T target(T view, String key) {
        if (view != null) view.setTag(TARGET_TAG_PREFIX + key);
        return view;
    }

    private void updateSettingsHeader(String title, String subtitle) {
        if (headerTitleView != null) headerTitleView.setText(title == null ? "App Settings" : title);
        if (headerSubtitleView != null) headerSubtitleView.setText(subtitle == null ? BuildInfo.DEVELOPMENT_BUILD_LABEL : subtitle);
        if (headerBackButton != null) headerBackButton.setContentDescription(currentSettingsSection == null || currentSettingsSection.isEmpty() ? "Back to forum" : "Back to all settings");
    }

    private String settingsSectionName(String key) {
        if ("account_security".equals(key)) return "Account & Security";
        if ("notifications".equals(key)) return "Notifications";
        if ("appearance".equals(key)) return "Appearance & Performance";
        if ("forum_data".equals(key)) return "Forum & Site Data";
        if ("advanced".equals(key)) return "Advanced & About";
        return "App Settings";
    }

    private String settingsSectionSubtitle(String key) {
        return BuildInfo.DEVELOPMENT_BUILD_LABEL;
    }

    private String sectionDisplayName(String key) {
        if ("account_identity".equals(key)) return "Account & Identity";
        if ("account_controls".equals(key)) return "Account Controls";
        if ("hcf_alerts".equals(key)) return "HCF Alerts";
        if ("silent_alerts".equals(key)) return "HCF Silent Alerts";
        if ("test_alerts".equals(key)) return "HCF Test Alerts";
        if ("appearance_performance".equals(key)) return "Appearance & Performance";
        if ("connection_routing".equals(key)) return "Connection & Routing";
        if ("cookies_site_data".equals(key)) return "Cookies & Site Data";
        if ("permissions_security".equals(key)) return "Permissions & Security";
        if ("app_updates".equals(key)) return "App Updates";
        if ("error_recovery".equals(key)) return "Error & Recovery";
        if ("telemetry".equals(key)) return "Telemetry";
        if ("developer_tools".equals(key)) return "Developer Tools";
        if ("about".equals(key)) return "About Harley's Clan Forum";
        return "Settings";
    }

    private View accountIdentityCard() {
        LinearLayout card = card();
        ForumIdentity.Snapshot snapshot = ForumIdentity.load(this);
        card.addView(sectionTitle("Account & Identity", "Your signed-in forum profile and account security"));
        String status = snapshot.loggedIn ? "Signed in as " + snapshot.identityLabel() : "Forum session: Guest_Protocol";
        TextView statusView = text(status, 13, getColor(snapshot.loggedIn ? R.color.hcf_accent_text : R.color.hcf_meta));
        statusView.setTypeface(null, 1);
        card.addView(statusView);
        String detail = snapshot.loggedIn ? ((snapshot.username == null || snapshot.username.isEmpty()) ? "Identity sync active" : "@" + snapshot.username + " • Identity sync active") : "Sign in to connect your identity";
        card.addView(text(detail, 11, getColor(R.color.hcf_muted)));
        card.addView(target(actionButton("Open Account & Identity", v -> startActivity(new Intent(this, IdentityActivity.class))), "open_identity"));
        return card;
    }

    private View accountControlsCard() {
        LinearLayout card = card();
        final ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        ForumSecurity.Snapshot security = ForumSecurity.load(this);
        if (!identity.loggedIn) {
            card.addView(text("Sign in to manage your forum account controls.", 12, getColor(R.color.hcf_muted)));
            card.addView(actionButton("Open Forum Sign In", v -> openAccountForumPath("/login")));
            return card;
        }
        String identityLabel = identity.username == null || identity.username.trim().isEmpty() ? identity.identityLabel() : "@" + identity.username.trim();
        TextView signedIn = text("Signed in as " + identityLabel, 12, getColor(R.color.hcf_accent_text));
        signedIn.setTypeface(null, 1);
        card.addView(signedIn);
        StringBuilder available = new StringBuilder();
        if (security.seen) {
            if (security.passwordControls) available.append("Password");
            if (security.emailControls) appendBullet(available, "Email");
            if (security.twoFactorControls) appendBullet(available, "Two-factor");
            if (security.sessionCount > 0) appendBullet(available, "Sessions: " + security.sessionCount);
        }
        if (available.length() == 0) available.append(security.seen ? "Security controls are managed by the forum" : "Open Account Security once to sync available controls");
        card.addView(text("Available: " + available, 10, getColor(R.color.hcf_muted)));
        card.addView(target(actionButton("Open My Forum Profile", v -> {
            String handle = accountHandle(identity);
            if (handle.isEmpty()) Toast.makeText(this, "Unable to determine your forum profile route.", Toast.LENGTH_SHORT).show();
            else openAccountForumPath("/u/" + Uri.encode(handle));
        }), "open_forum_profile"));
        card.addView(target(actionButton("Open Account Security", v -> {
            String handle = accountHandle(identity);
            if (handle.isEmpty()) Toast.makeText(this, "Unable to determine your forum profile route.", Toast.LENGTH_SHORT).show();
            else openAccountForumPath("/u/" + Uri.encode(handle) + "/security");
        }), "open_account_security"));
        card.addView(text("Password, email, two-factor and session changes remain on Harley's Clan Forum. The app only exposes safe shortcuts and synced capability/status information.", 10, getColor(R.color.hcf_hint)));
        return card;
    }

    private void appendBullet(StringBuilder builder, String value) {
        if (builder.length() > 0) builder.append(" • ");
        builder.append(value);
    }

    private String accountHandle(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null || !snapshot.loggedIn) return "";
        String handle = snapshot.slug == null || snapshot.slug.trim().isEmpty() ? snapshot.username : snapshot.slug;
        return handle == null ? "" : handle.trim();
    }

    private void openAccountForumPath(String path) {
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        String host = ForumUrlRouter.isForumHost(identity.host) ? identity.host : "forum.harleytg.com";
        String route = path == null || path.trim().isEmpty() ? "/" : path.trim();
        if (!route.startsWith("/")) route = "/" + route;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setData(Uri.parse("https://" + host + route));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private View mainAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        boolean permissionAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
        boolean channelAvailable = NotificationHelper.channelImportance(this, "hcf_alerts_v1") != 0;
        boolean ready = permissionAllowed && channelAvailable;

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(16);
        hero.setBackgroundResource(R.drawable.quick_action_background);
        hero.setPadding(dp(13), dp(11), dp(11), dp(11));
        ImageView heroIcon = settingsSectionIcon(R.drawable.fa_bell);
        LinearLayout.LayoutParams heroIconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        heroIconLp.rightMargin = dp(11);
        hero.addView(heroIcon, heroIconLp);
        LinearLayout heroLabels = new LinearLayout(this);
        heroLabels.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = text("Real forum alerts", 14, getColor(R.color.hcf_text));
        heroTitle.setTypeface(null, 1);
        heroLabels.addView(heroTitle);
        heroLabels.addView(text("Messages • mentions • replies • important activity", 10, getColor(R.color.hcf_muted)));
        hero.addView(heroLabels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView readyChip = text(ready ? "READY" : "CHECK", 9, getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
        readyChip.setTypeface(null, 1);
        readyChip.setGravity(17);
        readyChip.setPadding(dp(8), dp(4), dp(8), dp(4));
        readyChip.setBackgroundResource(R.drawable.status_chip_background);
        hero.addView(readyChip);
        card.addView(hero);

        notificationStatus = text("Checking HCF Alerts status…", 11, getColor(R.color.hcf_muted));
        notificationStatus.setPadding(dp(2), dp(7), dp(2), dp(5));
        card.addView(notificationStatus);

        card.addView(settingsSubsectionHeader("Background delivery", "Keep real HCF Alerts checking while the app is not open", R.drawable.fa_bell));
        Switch sync = target(toggle("Background notification sync", prefs.getBoolean("background_notification_sync", true)), "background_notification_sync");
        sync.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("background_notification_sync", checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
            Toast.makeText(this, checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        });
        card.addView(sync);
        card.addView(text("Recommended: keep this ON so new forum alerts can be discovered when HCF is in the background.", 10, getColor(R.color.hcf_muted)));
        card.addView(text("If Android delays background alerts, set HCF Beta battery usage to Unrestricted in Android Settings > Apps > HCF Beta > Battery.", 10, getColor(R.color.hcf_muted)));

        card.addView(settingsSubsectionHeader("Android access", "Permission and channel status", R.drawable.fa_shield));
        if (!permissionAllowed) {
            card.addView(target(actionButton("Allow Notification Permission", v -> requestNotificationPermissionIfNeeded()), "allow_android_notification_permission"));
        } else {
            TextView granted = target(text("✓ Android notification permission allowed", 11, getColor(R.color.hcf_accent_text)), "allow_android_notification_permission");
            granted.setTypeface(null, 1);
            granted.setPadding(dp(2), dp(6), dp(2), dp(6));
            card.addView(granted);
        }
        card.addView(notificationChannelStatusRow("HCF Alerts", "Required real-alert channel • never controlled by HCF silence settings", "hcf_alerts_v1"));
        card.addView(actionButton("Open HCF Alerts Android Settings", v -> NotificationHelper.openChannelSettings(this)));
        return card;
    }

    private View silentAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(settingsInfoCard("Service-status channel",
                "HCF Silent Alerts only carries quiet background-service status. It never carries direct messages, mentions or replies.",
                R.drawable.fa_bell));
        Switch silence = target(toggle("Disable HCF Silent Alerts", prefs.getBoolean("silence_background_service_notification", false)), "silence_hcf_silent_alerts");
        silence.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("silence_background_service_notification", checked).apply();
            NotificationHelper.refreshChannels(this);
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_silence_passive_notifications", Boolean.toString(checked));
            Toast.makeText(this, checked ? "HCF Silent Alerts disabled." : "HCF Silent Alerts enabled • silent.", Toast.LENGTH_LONG).show();
        });
        card.addView(silence);
        card.addView(text("This affects the silent service-status channel only. Android may limit continuous background checking when the service status is disabled.", 10, getColor(R.color.hcf_muted)));
        card.addView(notificationChannelRow("HCF Silent Alerts", "Silent • service status only", "hcf_silent_alerts_v1"));
        return card;
    }

    private View testAlertsInfoCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(settingsInfoCard("Developer test channel",
                "Use this only to test notification delivery. It never carries real forum alerts or background-service status.",
                R.drawable.fa_bug));
        card.addView(notificationChannelRow("HCF Test Alerts", "Dev/Beta notification tests", "hcf_test_alerts_v1"));
        card.addView(target(actionButton("Open Developer Notification Tools", v -> navigateToSettingKey("notification_test_console")), "open_developer_tools"));
        return card;
    }

    private View themeModeSelector() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(2));
        root.setTag(TARGET_TAG_PREFIX + "theme");

        LinearLayout first = new LinearLayout(this);
        first.setOrientation(LinearLayout.HORIZONTAL);
        first.setWeightSum(2.0f);
        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.setWeightSum(2.0f);
        LinearLayout third = new LinearLayout(this);
        third.setOrientation(LinearLayout.HORIZONTAL);
        third.setWeightSum(1.0f);

        String mode = ThemeManager.mode(this);
        first.addView(themeChoiceButton("Forum Auto", "auto_forum", "auto_forum".equals(mode)), themeChoiceParams(false));
        first.addView(themeChoiceButton("Phone Auto", "auto_phone", "auto_phone".equals(mode)), themeChoiceParams(true));
        second.addView(themeChoiceButton("Light", "light", "light".equals(mode)), themeChoiceParams(false));
        second.addView(themeChoiceButton("Dark", "dark", "dark".equals(mode)), themeChoiceParams(true));
        third.addView(themeChoiceButton("AMOLED", "amoled", "amoled".equals(mode)), themeChoiceParams(false));

        root.addView(first);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(-1, -2);
        secondLp.topMargin = dp(8);
        root.addView(second, secondLp);
        LinearLayout.LayoutParams thirdLp = new LinearLayout.LayoutParams(-1, -2);
        thirdLp.topMargin = dp(8);
        root.addView(third, thirdLp);

        TextView live = text(ThemeManager.autoSourceLabel(this), 10, getColor(R.color.hcf_cyan));
        live.setPadding(dp(2), dp(8), dp(2), 0);
        root.addView(live);
        return root;
    }

    private LinearLayout.LayoutParams themeChoiceParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(compact() ? 46 : 52), 1.0f);
        if (right) lp.leftMargin = dp(8);
        return lp;
    }

    private Button themeChoiceButton(String label, final String value, boolean selected) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setTypeface(null, 1);
        button.setGravity(17);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(getColor(selected ? R.color.hcf_chip_selected_text : R.color.hcf_text));
        button.setBackgroundResource(selected ? R.drawable.theme_choice_selected_background : R.drawable.theme_choice_background);
        button.setContentDescription(label + (selected ? ", selected" : ""));
        button.setOnClickListener(v -> {
            if (value.equals(ThemeManager.mode(this))) return;
            prefs.edit().putString("app_theme", value).apply();
            AppLogger.info(this, "setting_theme", value);
            recreate();
        });
        return button;
    }

    private View interfaceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Appearance & Interface", "A simpler app shell with more room for the forum"));
        TextView theme = text("Theme", 13, getColor(R.color.hcf_text));
        theme.setTypeface(null, 1);
        card.addView(theme);
        card.addView(themeModeSelector());
        card.addView(text(
                "Forum Auto follows Flarum Night Mode (uses last known value on startup). "
                        + "Phone Auto follows Android. Light / Dark / AMOLED force the app chrome immediately.",
                10, getColor(R.color.hcf_muted)));
        Button performance = target(actionButton("Performance Profile: " + PerformanceProfile.settingLabel(this, prefs), null), "performance_profile");
        performance.setContentDescription("Choose app performance profile");
        performance.setOnClickListener(v -> showPerformanceProfileDialog(performance));
        card.addView(performance);
        Switch live = target(toggle("Live forum updates", prefs.getBoolean("live_forum_updates", true)), "live_forum_updates");
        live.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("live_forum_updates", checked).apply();
            AppLogger.info(this, "setting_live_updates", Boolean.toString(checked));
            Toast.makeText(this, checked ? "Live forum updates enabled." : "Live forum updates disabled.", Toast.LENGTH_SHORT).show();
        });
        card.addView(live);
        Switch url = target(toggle("Show secure URL bar", prefs.getBoolean("show_url_bar", true)), "show_secure_url_bar");
        url.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("show_url_bar", checked).apply());
        card.addView(url);
        Switch startup = target(toggle("Show startup connection screen", prefs.getBoolean("show_startup_screen", true)), "show_startup_screen");
        startup.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("show_startup_screen", checked).apply());
        card.addView(startup);
        card.addView(text("Header: Classic compact • bottom app bar removed", 11, getColor(R.color.hcf_cyan)));
        return card;
    }

    private void showPerformanceProfileDialog(final Button button) {
        final String[] values = {"auto", "performance", "balanced", "quality"};
        String[] labels = {"Auto — Recommended", "Performance — Minimal motion", "Balanced — Short smooth motion", "High Performance — Full visual effects"};
        String saved = PerformanceProfile.saved(prefs);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) checked = i;
        new AlertDialog.Builder(this).setTitle("Performance Profile").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            if (which < 0 || which >= values.length) return;
            PerformanceProfile.save(prefs, values[which]);
            String label = PerformanceProfile.settingLabel(this, prefs);
            button.setText("Performance Profile: " + label);
            button.setContentDescription("Performance profile " + label);
            AppLogger.info(this, "setting_performance_profile", values[which] + " -> " + PerformanceProfile.resolve(this, prefs));
            Toast.makeText(this, label + " • " + PerformanceProfile.detail(this, prefs), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Connection", "Primary and backup forum routing"));
        serverStatus = text("Current server: checking…", 12, getColor(R.color.hcf_cyan));
        card.addView(serverStatus);
        Switch failover = target(toggle("Automatically use backup if primary fails", prefs.getBoolean("auto_failover", true)), "auto_failover");
        failover.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("auto_failover", checked).apply());
        card.addView(failover);
        Switch external = target(toggle("Allow external links to open in browser/apps", prefs.getBoolean("external_links", true)), "external_links");
        external.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("external_links", checked).apply());
        card.addView(external);
        card.addView(target(actionButton("Retry Primary Forum on Next Open", v -> {
            prefs.edit().remove("fallback_until").putString("active_host", "forum.harleytg.com").apply();
            refreshStatusLabels();
            Toast.makeText(this, "Primary forum restored as preferred server.", Toast.LENGTH_SHORT).show();
        }), "retry_primary"));
        TextView links = text("Forum links: primary + backup registered with Android", 10, getColor(R.color.hcf_muted));
        links.setPadding(0, dp(10), 0, 0);
        card.addView(links);
        card.addView(target(actionButton("Open Forum Link Settings", v -> openForumLinkSettings()), "forum_link_settings"));
        return card;
    }

    private View privacyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Cookies & Site Data", "Forum data stored on this phone"));
        cookieStatus = text(cookieSummary(), 12, getColor(R.color.hcf_meta));
        cookieStatus.setTypeface(null, 1);
        card.addView(cookieStatus);
        card.addView(target(actionButton("Open Cookie Manager", v -> startActivity(new Intent(this, CookieManagerActivity.class))), "cookie_manager"));
        card.addView(target(actionButton("Clear Forum Site Data & Sign Out", v -> clearForumSiteData()), "clear_site_data"));
        return card;
    }

    private void clearForumSiteData() {
        final CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies((ValueCallback<Boolean>) value -> {
            manager.flush();
            if (cookieStatus != null) cookieStatus.setText(cookieSummary());
        });
        WebStorage.getInstance().deleteAllData();
        ForumIdentity.clear(this);
        AppLogger.info(this, "forum_site_data_cleared", "settings");
        Toast.makeText(this, "Forum cookies and site data cleared.", Toast.LENGTH_LONG).show();
    }

    private View securityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Permissions & Security", "Android permissions and app hardening"));
        securityStatus = target(text(AppSecurity.securitySummary(this), 11, getColor(R.color.hcf_meta)), "permission_status");
        card.addView(securityStatus);
        card.addView(target(actionButton("Allow Notification Permission", v -> requestNotificationPermissionIfNeeded()), "notification_permission"));
        card.addView(target(actionButton("Allow Secure App Updates", v -> openInstallPermission()), "secure_updates_permission"));
        card.addView(target(actionButton("Android App Permission Settings", v -> openAndroidAppSettings()), "android_permission_settings"));
        card.addView(text("No location, contacts, microphone, camera, or broad storage permission is requested. Update APKs are accepted only from HCF's trusted release source and must match the installed package and signing certificate.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    private void openInstallPermission() {
        if (AppSecurity.canInstallUpdates(this)) {
            Toast.makeText(this, "Secure app update installation is already allowed.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
            return;
        }
        try {
            startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())));
        } catch (Throwable error) {
            Toast.makeText(this, "Android could not open the update install permission screen.", Toast.LENGTH_LONG).show();
        }
    }

    private void openAndroidAppSettings() {
        try {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
        } catch (Throwable error) {
            Toast.makeText(this, "Android app settings could not be opened.", Toast.LENGTH_LONG).show();
        }
    }

    private View updateCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("App Updates", "Secure automatic app updates"));
        updateChannelStatus = target(text(updateChannelLine(effectiveUpdateChannel()), 12, getColor(R.color.hcf_meta)), "update_channel");
        updateChannelStatus.setTypeface(null, 1);
        card.addView(updateChannelStatus);
        long lastCheck = prefs.getLong("update_last_check", 0L);
        String checked = lastCheck <= 0 ? "Not checked yet on this install" : "Last checked " + ageLabel(lastCheck);
        updateStatus = target(text("Installed: v" + BuildInfo.VERSION + " (" + installedVersionCode() + ")\nLatest available: Not checked\n" + checked, 11, getColor(R.color.hcf_muted)), "installed_version");
        card.addView(updateStatus);
        Switch autoCheck = target(toggle("Automatic update checks", prefs.getBoolean("update_auto_check", true)), "automatic_update_checks");
        autoCheck.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("update_auto_check", enabled).apply();
            UpdateScheduler.apply(this);
            AppLogger.info(this, "setting_update_auto_check", Boolean.toString(enabled));
        });
        card.addView(autoCheck);
        Switch autoDownload = target(toggle("Automatically download new APKs", prefs.getBoolean("update_auto_download", false)), "auto_download_apk");
        autoDownload.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("update_auto_download", enabled).apply();
            AppLogger.info(this, "setting_update_auto_download", Boolean.toString(enabled));
        });
        card.addView(autoDownload);
        Switch autoInstall = target(toggle("Open installer automatically after download", prefs.getBoolean("update_auto_install", true)), "auto_installer");
        autoInstall.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("update_auto_install", enabled).apply();
            AppLogger.info(this, "setting_update_auto_install", Boolean.toString(enabled));
        });
        card.addView(autoInstall);
        card.addView(target(actionButton("Check for Updates", v -> checkForUpdates(true)), "check_updates"));
        updateDownloadButton = actionButton("Download Update Now", v -> downloadAvailableUpdate());
        updateDownloadButton.setVisibility(View.GONE);
        card.addView(updateDownloadButton);
        updateInstallButton = actionButton("Install Downloaded Update", v -> installDownloadedUpdate());
        updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        card.addView(updateInstallButton);
        TextView verification = target(text("APK verification: HCF checks the downloaded package name, Android versionCode, and signing certificate before opening Android's installer. Android still requires your confirmation to install.", 10, getColor(R.color.hcf_muted)), "apk_verification");
        verification.setPadding(0, dp(8), 0, 0);
        card.addView(verification);
        return card;
    }

    private void downloadAvailableUpdate() {
        UpdateChecker.Release release = availableRelease;
        if (release == null || release.apkUrl == null || release.apkUrl.isEmpty()) {
            Toast.makeText(this, "This release does not have an APK asset yet.", Toast.LENGTH_LONG).show();
            return;
        }
        long id = AppUpdateDownloader.enqueue(this, release, true);
        if (id <= 0) {
            Toast.makeText(this, "Update download could not start.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Update download started.", Toast.LENGTH_SHORT).show();
        String build = release.versionCode > 0 ? " " + release.versionCode : "";
        updateStatus.setText("Downloading " + channelDisplayName(effectiveUpdateChannel()) + " build" + build + "…\nThe Android installer will open automatically when verification finishes.");
        watchUpdateDownloadForAutoInstall(id);
    }

    private String updateChannelLine(String channel) {
        return "stable".equalsIgnoreCase(channel) ? "Channel: Stable • Official Releases" : "Channel: Dev • Development / Beta Releases";
    }

    private String effectiveUpdateChannel() {
        return BuildInfo.DEFAULT_UPDATE_CHANNEL;
    }

    private String channelDisplayName(String channel) {
        return "stable".equalsIgnoreCase(channel) ? "Stable" : "Dev/Beta";
    }

    private void checkForUpdates(final boolean userInitiated) {
        if (updateStatus == null) return;
        final String channel = effectiveUpdateChannel();
        updateStatus.setText("Checking " + channelDisplayName(channel) + " release channel…");
        updateStatus.setTextColor(getColor(R.color.hcf_meta));
        if (updateDownloadButton != null) updateDownloadButton.setVisibility(View.GONE);
        availableRelease = null;
        UpdateChecker.check(this, channel, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateChecker.Release release, boolean newer) {
                availableRelease = release;
                prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                String remote = "v" + UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "");
                String asset = release.apkName == null || release.apkName.isEmpty() ? "No APK asset attached" : release.apkName;
                String releaseType = release.prerelease ? "Pre-release" : "Official release";
                String installed = "v" + BuildInfo.VERSION + " (" + installedVersionCode() + ")";
                if (newer) {
                    updateStatus.setText(channelDisplayName(channel) + " Update Available\nInstalled: " + installed + "\nLatest available: " + remote + "\n" + releaseType + " • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_accent_text));
                    if (updateDownloadButton != null && release.apkUrl != null && !release.apkUrl.isEmpty()) updateDownloadButton.setVisibility(View.VISIBLE);
                    if (prefs.getBoolean("update_auto_download", false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        long id = AppUpdateDownloader.enqueue(SettingsActivity.this, release, userInitiated);
                        if (id > 0) {
                            updateStatus.append("\nAutomatic download queued • installer opens when ready.");
                            watchUpdateDownloadForAutoInstall(id);
                        }
                    }
                    if (userInitiated) Toast.makeText(SettingsActivity.this, channelDisplayName(channel) + " update available" + (release.versionCode > 0 ? " • build " + release.versionCode : ""), Toast.LENGTH_LONG).show();
                } else if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                    updateStatus.setText("Installed build is newer\nInstalled: " + installed + "\nLatest published: " + remote + " • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_meta));
                    if (userInitiated) Toast.makeText(SettingsActivity.this, "The published release feed is behind this installed build.", Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus.setText("Up to date\nInstalled: " + installed + "\nLatest available: " + remote + " • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_meta));
                    if (userInitiated) Toast.makeText(SettingsActivity.this, "This build is up to date for " + channelDisplayName(channel) + ".", Toast.LENGTH_SHORT).show();
                }
                AppLogger.info(SettingsActivity.this, "update_check", channel + " | " + release.tag + " | newer=" + newer);
            }
            @Override public void onError(String message) {
                updateStatus.setText("Update check unavailable • " + message + "\nInstalled: v" + BuildInfo.VERSION + " (" + installedVersionCode() + ")");
                updateStatus.setTextColor(getColor(R.color.hcf_warning));
                if (userInitiated) Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                AppLogger.error(SettingsActivity.this, "update_check", message);
            }
        });
    }

    private void watchUpdateDownloadForAutoInstall(final long id) {
        if (id <= 0 || !prefs.getBoolean("update_auto_install", true)) return;
        final Runnable[] task = new Runnable[1];
        task[0] = () -> {
            if (isFinishing() || isDestroyed() || !prefs.getBoolean("update_auto_install", true)) return;
            AppUpdateDownloader.ProgressSnapshot progress = AppUpdateDownloader.progress(this, id);
            if (progress.status == 8) installDownloadedUpdate(id);
            else if (progress.status != 16 && settingsScroll != null) settingsScroll.postDelayed(task[0], 500L);
        };
        if (settingsScroll != null) settingsScroll.postDelayed(task[0], 500L);
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null || !(getPackageName() + ".INSTALL_UPDATE").equals(intent.getAction())) return;
        final long id = intent.getLongExtra("download_id", -1L);
        if (id > 0) getWindow().getDecorView().postDelayed(() -> installDownloadedUpdate(id), 250L);
    }

    private void installDownloadedUpdate() {
        installDownloadedUpdate(AppUpdateDownloader.downloadedId(this));
    }

    private void installDownloadedUpdate(long id) {
        if (id <= 0) {
            Toast.makeText(this, "No downloaded update is ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getPackageManager().canRequestPackageInstalls()) {
            if (!AppUpdateDownloader.openInstaller(this, id)) Toast.makeText(this, "The Android installer could not open this download.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            prefs.edit().putBoolean("update_resume_after_permission", true).apply();
            startActivityForResult(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            Toast.makeText(this, "Allow installs from this source. HCF will resume the verified update automatically when you return.", Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            prefs.edit().remove("update_resume_after_permission").apply();
            Toast.makeText(this, "Android blocked installation permission settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void resumeUpdateInstallAfterPermission() {
        if (!prefs.getBoolean("update_resume_after_permission", false) || !AppSecurity.canInstallUpdates(this)) return;
        prefs.edit().remove("update_resume_after_permission").apply();
        long id = AppUpdateDownloader.downloadedId(this);
        if (id > 0) {
            Toast.makeText(this, "Install permission enabled • opening verified update…", Toast.LENGTH_SHORT).show();
            if (!AppUpdateDownloader.openInstaller(this, id)) Toast.makeText(this, "The Android installer could not open this verified update.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
            if (AppSecurity.canInstallUpdates(this)) resumeUpdateInstallAfterPermission();
            else {
                prefs.edit().remove("update_resume_after_permission").apply();
                Toast.makeText(this, "Install permission was not enabled. The downloaded APK was kept.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private View diagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Error & Recovery Center", "Troubleshooting, runtime state and smart-recovery tools"));
        TextView recoveryState = target(settingsInfoText("Runtime & WebView", runtimeRecoverySummary()), "renderer_recovery");
        card.addView(recoveryState);
        card.addView(target(actionButton("Run Error & Recovery Check", v -> showRecoveryDiagnostics()), "error_recovery_check"));
        card.addView(target(actionButton("Runtime Snapshot", v -> showRuntimeSnapshot()), "runtime_snapshot"));
        card.addView(target(actionButton("Copy Sanitized Diagnostic Report", v -> copyDiagnosticReport()), "copy_diagnostic_report"));
        card.addView(target(actionButton("View App & Crash Logs", v -> startActivity(new Intent(this, LogsActivity.class))), "view_crash_logs"));
        card.addView(target(actionButton("Clear WebView Cache", v -> clearWebViewCache()), "clear_webview_cache"));
        return card;
    }

    private TextView settingsInfoText(String title, String body) {
        TextView view = text(title + "\n" + body, 10, getColor(R.color.hcf_muted));
        view.setBackgroundResource(R.drawable.quick_action_background);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        return view;
    }

    private String runtimeRecoverySummary() {
        String webView = "Unknown";
        try {
            PackageInfo current = WebView.getCurrentWebViewPackage();
            if (current != null) webView = current.packageName + " • " + current.versionName;
        } catch (Throwable ignored) {}
        String last = prefs.getString("last_recoverable_url", "");
        if (last == null || last.trim().isEmpty()) last = "Not recorded yet";
        else last = AppLogger.safeUrl(last);
        return "WebView: " + webView + "\nRenderer recovery: Enabled (HCF-WV-001)\nNative error state: Enabled • SSL fail-closed\nLast recoverable route: " + last;
    }

    private void showRecoveryDiagnostics() {
        String active = prefs.getString("active_host", "forum.harleytg.com");
        String host = ForumUrlRouter.isForumHost(active) ? active : "forum.harleytg.com";
        String message = "Network: " + (isValidatedNetworkAvailable() ? "✓ Connected" : "✕ Offline")
                + "\nCurrent server: " + host
                + "\nAutomatic failover: " + (prefs.getBoolean("auto_failover", true) ? "✓ Enabled" : "Disabled")
                + "\n" + runtimeRecoverySummary();
        new AlertDialog.Builder(this).setTitle("Error & Recovery Check").setMessage(message).setPositiveButton("OK", null)
                .setNeutralButton("View logs", (dialog, which) -> startActivity(new Intent(this, LogsActivity.class))).show();
        AppLogger.info(this, "recovery_diagnostics", isValidatedNetworkAvailable() ? "network-ok" : "offline");
    }

    private void showRuntimeSnapshot() {
        final String snapshot = "HCF Runtime Snapshot\n\nApp: " + BuildInfo.VERSION + " (" + installedVersionCode() + ")"
                + "\nPackage: " + getPackageName()
                + "\nChannel: " + BuildInfo.CHANNEL
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nNetwork: " + RuntimeState.networkType(this)
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance: " + PerformanceProfile.settingLabel(this, prefs)
                + "\nNotifications: " + NotificationHelper.status(this)
                + "\nPrimary: forum.harleytg.com\nBackup: harleysclan.freeflarum.com\nRenderer recovery: Enabled (HCF-WV-001)";
        new AlertDialog.Builder(this).setTitle("Runtime Snapshot").setMessage(snapshot).setPositiveButton("Close", null)
                .setNeutralButton("Copy", (dialog, which) -> copyText("HCF runtime snapshot", snapshot, "Runtime snapshot copied.")).show();
    }

    private void copyDiagnosticReport() {
        String active = prefs.getString("active_host", "forum.harleytg.com");
        String sync = prefs.getString("notification_last_sync_status", "not synced yet");
        long latency = prefs.getLong("notification_last_sync_latency_ms", 0L);
        String report = "Harley's Clan Forum • Sanitized Diagnostic Report"
                + "\nApp: " + BuildInfo.VERSION + " (" + installedVersionCode() + ")"
                + "\nPackage: " + getPackageName()
                + "\nChannel: " + BuildInfo.CHANNEL
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nForum host: " + active
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance profile: " + PerformanceProfile.settingLabel(this, prefs)
                + "\nNotifications: " + NotificationHelper.status(this)
                + "\nLive sync: " + sync + (latency > 0 ? " • " + latency + " ms" : "")
                + "\nAuto failover: " + (prefs.getBoolean("auto_failover", true) ? "On" : "Off")
                + "\nRenderer recovery: Enabled (HCF-WV-001)"
                + "\nLast route: " + AppLogger.safeUrl(prefs.getString("last_recoverable_url", ""))
                + "\nCookies/tokens/passwords/email: Not included";
        copyText("HCF diagnostic report", report, "Sanitized diagnostic report copied.");
        AppLogger.info(this, "diagnostic_report_copy", "sanitized");
    }

    private void clearWebViewCache() {
        try {
            WebView webView = new WebView(this);
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            AppLogger.info(this, "webview_cache_cleared", "settings");
            Toast.makeText(this, "WebView cache cleared.", Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            AppLogger.error(this, "webview_cache_clear", error.getClass().getSimpleName());
            Toast.makeText(this, "WebView cache could not be cleared on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isValidatedNetworkAvailable() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService("connectivity");
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private View telemetryCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Telemetry & Diagnostics", "Crash reports, app health and privacy controls"));
        telemetryStatus = text(TelemetryService.status(this), 11, getColor(R.color.hcf_meta));
        telemetryStatus.setTypeface(null, 1);
        card.addView(telemetryStatus);
        Switch enabled = target(toggle("Enable Telemetry Services", prefs.getBoolean("telemetry_enabled", false)), "telemetry_enabled");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("telemetry_enabled", checked).apply();
            AppLogger.info(this, "setting_telemetry", Boolean.toString(checked));
            if (checked) {
                TelemetryService.sendEvent(this, "telemetry_enabled", "User enabled Telemetry Services in App Settings");
                Toast.makeText(this, "Telemetry Services enabled.", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "Telemetry disabled. No reports will be sent.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        });
        card.addView(enabled);
        Button level = target(actionButton("Telemetry level: " + TelemetryService.levelLabel(this), null), "telemetry_level");
        level.setOnClickListener(v -> {
            String next = "diagnostics".equals(TelemetryService.level(this)) ? "basic" : "diagnostics";
            prefs.edit().putString("telemetry_level", next).apply();
            level.setText("Telemetry level: " + TelemetryService.levelLabel(this));
            refreshStatusLabels();
        });
        card.addView(level);
        card.addView(text("Basic: coarse app health only. Diagnostics: adds crashes, sanitized stack traces, recent app events, and optional WebView/update errors.", 10, getColor(R.color.hcf_muted)));
        Switch crashes = target(toggle("Automatically send crash reports", prefs.getBoolean("telemetry_auto_crash_reports", false)), "auto_crash_reports");
        crashes.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("telemetry_auto_crash_reports", checked).apply());
        card.addView(crashes);
        Switch ask = target(toggle("Ask me before every crash report", prefs.getBoolean("telemetry_ask_before_crash_report", true)), "ask_before_crash_report");
        ask.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("telemetry_ask_before_crash_report", checked).apply());
        card.addView(ask);
        Switch errors = target(toggle("Automatically send WebView/update errors", prefs.getBoolean("telemetry_auto_error_reports", false)), "auto_error_reports");
        errors.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("telemetry_auto_error_reports", checked).apply());
        card.addView(errors);
        TextView privacyHeader = target(text("Report Privacy", 10, getColor(R.color.hcf_cyan)), "report_privacy");
        privacyHeader.setTypeface(null, 1);
        privacyHeader.setPadding(0, dp(8), 0, 0);
        card.addView(privacyHeader);
        card.addView(telemetryPrivacyToggle("Include my forum identity with reports", "telemetry_include_identity"));
        card.addView(telemetryPrivacyToggle("Include my email when identity is included", "telemetry_include_email"));
        card.addView(telemetryPrivacyToggle("Include device manufacturer/model", "telemetry_include_device_model"));
        card.addView(telemetryPrivacyToggle("Include sanitized forum route", "telemetry_include_route"));
        card.addView(target(actionButton("Send Diagnostic Feedback", v -> TelemetryService.showManualFeedbackDialog(this)), "diagnostic_feedback"));
        card.addView(target(actionButton("Preview Telemetry Report", v -> TelemetryService.showPreview(this)), "preview_telemetry"));
        card.addView(target(actionButton("View Report History", v -> TelemetryService.showHistory(this)), "telemetry_history"));
        card.addView(target(actionButton("Clear Local Telemetry Reports", v -> {
            TelemetryService.clearLocalReports(this);
            Toast.makeText(this, "Local telemetry reports and breadcrumbs cleared.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        }), "clear_telemetry"));
        card.addView(text("Crash reports can include a sanitized stack trace plus recent app events. Identity, email, device model and page route are separate opt-ins. Passwords, cookies, access/session tokens, recovery codes, posts, messages and page contents are never sent.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    private Switch telemetryPrivacyToggle(String title, String prefKey) {
        Switch toggle = toggle(title, prefs.getBoolean(prefKey, false));
        toggle.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(prefKey, checked).apply());
        return toggle;
    }

    private View developerToolsCard() {
        LinearLayout card = card();
        String environmentTitle = "stable".equals(effectiveUpdateChannel()) ? "Stable environment" : "Dev/Beta environment";
        String toolLabel = "stable".equals(effectiveUpdateChannel()) ? "Stable test tools" : "Development/Beta test tools";
        View environment = target(settingsInfoCard(environmentTitle,
                toolLabel + "\nPackage: " + getPackageName()
                        + "\nBuild " + installedVersionCode()
                        + "\nChannel: " + BuildInfo.CHANNEL
                        + "\nUpdate feed: " + effectiveUpdateChannel(), R.drawable.fa_bug), "developer_environment");
        card.addView(environment);
        card.addView(settingsSubsectionHeader("Notification Lab", "Test HCF alert types, delivery and background synchronization", R.drawable.fa_bell));
        card.addView(target(actionButton("Notification Test Console", v -> showNotificationTestConsole()), "notification_test_console"));
        card.addView(target(actionButton("Test Notification Service", v -> testNotificationService()), "test_notification_service"));
        card.addView(target(actionButton("Force Notification Sync", v -> {
            InstantNotificationService.requestImmediateSync(this);
            Toast.makeText(this, "Immediate notification sync requested.", Toast.LENGTH_SHORT).show();
        }), "force_notification_sync"));
        return card;
    }

    private String developerToolsSubtitle() {
        return "stable".equals(effectiveUpdateChannel()) ? "Stable test tools" : "Dev/Beta test controls";
    }

    private void showNotificationTestConsole() {
        new AlertDialog.Builder(this).setTitle("Notification Test Console")
                .setItems(new String[]{"Direct message", "Mention", "Discussion reply", "General HCF alert"}, (dialog, which) -> {
                    if (which == 0) postTestAlert("Direct message", "New private message test", "/notifications");
                    else if (which == 1) postTestAlert("Mention", "@you were mentioned in a forum post", "/notifications");
                    else if (which == 2) postTestAlert("Discussion reply", "New reply test notification", "/notifications");
                    else postTestAlert("Forum alert", "General Harley's Clan Forum notification test", "/notifications");
                }).setNegativeButton("Cancel", null).show();
    }

    private void testNotificationService() {
        InstantNotificationService.requestImmediateSync(this);
        String message = NotificationHelper.postNotificationServiceTest(this)
                ? "Notification service test sent • background sync requested."
                : "Notification service test could not post. Check Android notification permission.";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void postTestAlert(String title, String body, String route) {
        String active = prefs.getString("active_host", "forum.harleytg.com");
        String host = ForumUrlRouter.isForumHost(active) ? active : "forum.harleytg.com";
        NotificationHelper.postTest(this, title, body, "https://" + host + (route == null ? "/notifications" : route));
        AppLogger.info(this, "notification_test", title);
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(target(settingsInfoCard("App identity",
                getString(R.string.app_name) + "\nVersion " + BuildInfo.VERSION + " • Build " + installedVersionCode() + "\n" + BuildInfo.DEVELOPMENT_BUILD_LABEL,
                R.drawable.fa_circle_info), "app_identity"));
        card.addView(target(settingsInfoCard("Build & channel",
                "Channel: " + BuildInfo.CHANNEL + " • Update feed: " + effectiveUpdateChannel()
                        + "\nPackage: " + getPackageName() + "\nAPK: " + BuildInfo.APK_FILE_NAME,
                R.drawable.fa_download), "build_channel"));
        card.addView(target(settingsInfoCard("Device & runtime",
                "Android SDK " + Build.VERSION.SDK_INT + " • " + Build.MANUFACTURER + " " + Build.MODEL
                        + "\nTheme: " + ThemeManager.label(this)
                        + "\n" + PerformanceProfile.settingLabel(this, prefs) + " • Network: " + RuntimeState.networkType(this),
                R.drawable.fa_gear), "device_runtime"));
        card.addView(target(settingsInfoCard("Forum endpoints", "Primary: forum.harleytg.com\nBackup: harleysclan.freeflarum.com", R.drawable.fa_globe), "forum_endpoints"));
        card.addView(settingsSubsectionHeader("Release & support", "Release notes, support and portable build information", R.drawable.fa_circle_info));
        card.addView(target(actionButton("View What's New • v" + BuildInfo.VERSION, v -> ReleaseNotes.showCustom(this, prefs, true)), "whats_new"));
        card.addView(target(actionButton("Release & Build Details", v -> showBuildDetails()), "release_build_details"));
        card.addView(target(actionButton("Copy App Information", v -> copyAboutInformation()), "copy_app_information"));
        card.addView(target(actionButton("Contact Support", v -> openSupport()), "contact_support"));
        card.addView(target(settingsInfoCard("Privacy", "Sanitized reports never include passwords, cookies, access/session tokens, recovery codes, posts, messages or page contents.", R.drawable.fa_shield), "privacy"));
        return card;
    }

    private void showBuildDetails() {
        new AlertDialog.Builder(this).setTitle("Release & Build Details")
                .setMessage(getString(R.string.app_name) + " Android app"
                        + "\n\nVersion: " + BuildInfo.VERSION
                        + "\nVersion code: " + installedVersionCode()
                        + "\nChannel: " + BuildInfo.CHANNEL
                        + "\nUpdate feed: " + effectiveUpdateChannel()
                        + "\nPackage: " + getPackageName()
                        + "\nAPK: " + BuildInfo.APK_FILE_NAME
                        + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL)
                .setPositiveButton("Close", null).show();
    }

    private void copyAboutInformation() {
        String info = getString(R.string.app_name)
                + "\nVersion: " + BuildInfo.VERSION + " (" + installedVersionCode() + ")"
                + "\nChannel: " + BuildInfo.CHANNEL + " / " + effectiveUpdateChannel()
                + "\nPackage: " + getPackageName()
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance: " + PerformanceProfile.settingLabel(this, prefs)
                + "\nPrimary forum: forum.harleytg.com\nBackup forum: harleysclan.freeflarum.com";
        copyText("HCF app information", info, "App information copied.");
    }

    private void openSupport() {
        try {
            startActivity(new Intent(this, SupportContactActivity.class));
            AppLogger.info(this, "support_contact_open", "about");
        } catch (Throwable error) {
            Toast.makeText(this, "Unable to open Contact Support.", Toast.LENGTH_LONG).show();
            AppLogger.error(this, "support_contact_open", error.getClass().getSimpleName());
        }
    }

    private void copyText(String label, String value, String toast) {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService("clipboard");
            if (manager == null) throw new IllegalStateException("Clipboard unavailable");
            manager.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Could not copy this information.", Toast.LENGTH_SHORT).show();
        }
    }

    private long installedVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable ignored) {
            return BuildInfo.VERSION_CODE;
        }
    }

    private String ageLabel(long timestamp) {
        long age = Math.max(0L, System.currentTimeMillis() - timestamp);
        long seconds = age / 1000L;
        if (seconds < 2) return "just now";
        if (seconds < 60) return seconds + " seconds ago";
        long minutes = seconds / 60L;
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60L;
        return hours + (hours == 1 ? " hour ago" : " hours ago");
    }

    public void refreshStatusLabels() {
        if (notificationStatus != null) {
            NotificationHelper.createChannel(this);
            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) != 0;
            boolean background = prefs.getBoolean("background_notification_sync", true);
            String delivery = background ? "Background delivery ON" : "Background delivery paused";
            long lastSyncAt = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            String lastSync;
            if (lastSyncAt <= 0L) {
                lastSync = "No background sync yet";
            } else {
                long ageSeconds = Math.max(0L, (System.currentTimeMillis() - lastSyncAt) / 1000L);
                if (ageSeconds < 60L) lastSync = "Last sync <1 min ago";
                else if (ageSeconds < 3600L) lastSync = "Last sync " + (ageSeconds / 60L) + " min ago";
                else lastSync = "Last sync " + (ageSeconds / 3600L) + " hr ago";
            }
            notificationStatus.setText((ready ? "HCF Alerts ready" : NotificationHelper.status(this)) + " • " + delivery + " • " + lastSync);
            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
        }
        if (cookieStatus != null) cookieStatus.setText(cookieSummary());
        if (securityStatus != null) securityStatus.setText(AppSecurity.securitySummary(this));
        if (telemetryStatus != null) telemetryStatus.setText(TelemetryService.status(this));
        if (updateChannelStatus != null) updateChannelStatus.setText(updateChannelLine(effectiveUpdateChannel()));
        if (updateInstallButton != null) updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        if (serverStatus != null) {
            String host = prefs.getString("active_host", "forum.harleytg.com");
            boolean primary = "forum.harleytg.com".equalsIgnoreCase(host);
            serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
            serverStatus.setTextColor(getColor(primary ? R.color.hcf_cyan : R.color.hcf_warning));
        }
    }

    private String cookieSummary() {
        CookieManager manager = CookieManager.getInstance();
        int primary = countCookies(manager.getCookie("https://forum.harleytg.com/"));
        int backup = countCookies(manager.getCookie("https://harleysclan.freeflarum.com/"));
        return "Cookie data: " + (primary + backup) + " visible • Primary " + primary + " • Backup " + backup;
    }

    private int countCookies(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        int count = 0;
        for (String part : value.split(";")) if (!part.trim().isEmpty()) count++;
        return count;
    }

    private View connectedSettingsPanel(String title, String subtitle, View content, boolean expanded) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.bottomMargin = dp(compact() ? 8 : 12);
        panel.setLayoutParams(panelLp);
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(16);
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(dp(15), dp(compact() ? 10 : 13), dp(12), dp(compact() ? 10 : 13));
        header.setBackgroundResource(expanded ? R.drawable.settings_section_header_expanded : R.drawable.settings_section_header_collapsed);
        ImageView icon = settingsSectionIcon(settingsIconForTitle(title));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconLp.rightMargin = dp(11);
        header.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        if (subtitle != null && !subtitle.trim().isEmpty()) labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final TextView arrow = text("›", 22, getColor(R.color.hcf_cyan_bright));
        arrow.setGravity(17);
        arrow.setRotation(expanded ? 90.0f : 0.0f);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(R.drawable.settings_section_body);
        body.setPadding(dp(14), dp(12), dp(14), dp(14));
        body.setPivotY(0.0f);
        if (content != null) {
            if (content instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) content;
                if (layout.getChildCount() > 0 && "hcf_section_title".equals(layout.getChildAt(0).getTag())) layout.removeViewAt(0);
            }
            content.setBackgroundColor(Color.TRANSPARENT);
            content.setPadding(0, 0, 0, 0);
            content.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            body.addView(content);
        }
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        body.setAlpha(expanded ? 1.0f : 0.0f);
        body.setScaleY(expanded ? 1.0f : 0.96f);
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] isExpanded = {expanded};
        header.setOnClickListener(v -> {
            if (isExpanded[0]) {
                isExpanded[0] = false;
                arrow.animate().rotation(0.0f).setDuration(150L).start();
                body.animate().alpha(0.0f).scaleY(0.96f).setDuration(150L).withEndAction(() -> {
                    body.setVisibility(View.GONE);
                    header.setBackgroundResource(R.drawable.settings_section_header_collapsed);
                }).start();
            } else {
                isExpanded[0] = true;
                header.setBackgroundResource(R.drawable.settings_section_header_expanded);
                body.setVisibility(View.VISIBLE);
                body.setAlpha(0.0f);
                body.setScaleY(0.94f);
                arrow.animate().rotation(90.0f).setDuration(170L).start();
                body.animate().alpha(1.0f).scaleY(1.0f).setDuration(180L).start();
            }
        });
        return panel;
    }

    private View settingsSubsectionHeader(String title, String subtitle, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(4), dp(14), dp(4), dp(5));
        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(19), dp(19));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 13, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        return row;
    }

    private View settingsInfoCard(String title, String body, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(48);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.rightMargin = dp(11);
        iconLp.topMargin = dp(2);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 13, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        TextView bodyView = text(body, 10, getColor(R.color.hcf_muted));
        bodyView.setSingleLine(false);
        bodyView.setMaxLines(Integer.MAX_VALUE);
        labels.addView(bodyView);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(7);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(compact() ? 8 : 12);
        card.setLayoutParams(lp);
        return card;
    }

    private View notificationChannelStatusRow(String title, String subtitle, String channelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(8), dp(12), dp(8));
        ImageView icon = settingsSectionIcon(R.drawable.fa_lock);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        String status = NotificationHelper.channelStatus(this, channelId);
        labels.addView(text(subtitle + " • " + status, 10, getColor(NotificationHelper.channelImportance(this, channelId) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView chip = text("REQUIRED", 9, getColor(R.color.hcf_accent_text));
        chip.setTypeface(null, 1);
        chip.setGravity(17);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackgroundResource(R.drawable.status_chip_background);
        row.addView(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(compact() ? 58 : 66));
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private View notificationChannelRow(String title, String subtitle, String channelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(9), dp(14), dp(9));
        ImageView icon = settingsSectionIcon(R.drawable.fa_bell);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconLp.rightMargin = dp(10);
        row.addView(icon, iconLp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        String status = NotificationHelper.channelStatus(this, channelId);
        labels.addView(text(subtitle + " • " + status, 10, getColor(NotificationHelper.channelImportance(this, channelId) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setTag("hcf_section_title");
        block.setPadding(0, 0, 0, dp(8));
        TextView titleView = text(title, 16, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        block.addView(titleView);
        block.addView(text(subtitle, 11, getColor(R.color.hcf_muted)));
        return block;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0.0f, 1.12f);
        view.setPadding(0, dp(3), 0, dp(3));
        return view;
    }

    private Switch toggle(String title, boolean checked) {
        Switch view = new Switch(this);
        view.setText(title);
        view.setTextColor(getColor(R.color.hcf_text));
        view.setTextSize(14.0f);
        view.setChecked(checked);
        view.setPadding(0, dp(7), 0, dp(7));
        return view;
    }

    private Button actionButton(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(cleanIconPrefix(title));
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        button.setGravity(8388627);
        button.setPadding(dp(14), 0, dp(14), 0);
        FaIcons.applyStart(button, title);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
        lp.topMargin = dp(7);
        button.setLayoutParams(lp);
        return button;
    }

    private ImageButton chromeButton(String description) {
        return UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, compact() ? 9 : 11,
                description == null || description.trim().isEmpty() ? "Back" : description);
    }

    private String cleanIconPrefix(String value) {
        return value == null ? "" : value.replaceFirst("^[^A-Za-z0-9]+", "").trim();
    }

    private ImageView settingsSectionIcon(int resId) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(resId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(getColor(R.color.hcf_cyan));
        icon.setContentDescription(null);
        return icon;
    }

    private int settingsIconForKey(String key) {
        if ("account_security".equals(key)) return R.drawable.fa_shield;
        if ("notifications".equals(key)) return R.drawable.fa_bell;
        if ("appearance".equals(key)) return R.drawable.fa_gear;
        if ("forum_data".equals(key)) return R.drawable.fa_globe;
        return R.drawable.fa_circle_info;
    }

    private int settingsIconForTitle(String title) {
        String lower = title == null ? "" : title.toLowerCase(Locale.US);
        if (lower.contains("account") || lower.contains("identity")) return R.drawable.fa_user;
        if (lower.contains("permission") || lower.contains("security")) return R.drawable.fa_shield;
        if (lower.contains("notification") || lower.contains("alert")) return R.drawable.fa_bell;
        if (lower.contains("appearance") || lower.contains("performance") || lower.contains("runtime")) return R.drawable.fa_gear;
        if (lower.contains("connection") || lower.contains("routing") || lower.contains("endpoint")) return R.drawable.fa_globe;
        if (lower.contains("cookie") || lower.contains("site data")) return R.drawable.fa_lock;
        if (lower.contains("update")) return R.drawable.fa_download;
        if (lower.contains("error") || lower.contains("recovery")) return R.drawable.fa_triangle_exclamation;
        if (lower.contains("developer")) return R.drawable.fa_bug;
        return R.drawable.fa_circle_info;
    }

    private void openForumLinkSettings() {
        try {
            startActivity(new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", Uri.parse("package:" + getPackageName())));
            AppLogger.info(this, "forum_link_settings", "open-by-default");
        } catch (Throwable first) {
            try {
                startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
            } catch (Throwable second) {
                Toast.makeText(this, "Android link settings are unavailable on this device.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            Toast.makeText(this, "Notification permission is already allowed.", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == 0;
        AppLogger.info(this, "notification_permission", granted ? "granted" : "denied | " + NotificationHelper.status(this));
        Toast.makeText(this, granted ? "Notifications allowed. Sending a heads-up test…" : "Notifications not allowed.", Toast.LENGTH_SHORT).show();
        NotificationSyncScheduler.apply(this);
        refreshStatusLabels();
        if (granted) NotificationHelper.post(this, "Harley's Clan Forum", "Heads-up notifications are enabled. This is a test alert.", ForumUrlRouter.home("forum.harleytg.com"));
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == 2;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
