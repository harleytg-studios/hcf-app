package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class SettingsActivity extends ThemedActivity {
    private static final int REQUEST_NOTIFICATIONS = 901;
    private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 2410;
    private static final String STATE_SETTINGS_SECTION = "settings_section";
    private static final String STATE_SETTINGS_SCROLL_Y = "settings_scroll_y";
    private SharedPreferences prefs;
    private TextView notificationStatus;
    private TextView dndStatus;
    private TextView serverStatus;
    private TextView cookieStatus;
    private TextView securityStatus;
    private TextView telemetryStatus;
    private TextView updateStatus;
    private TextView updateChannelStatus;
    private TextView liveSyncStatus;
    private Button updateDownloadButton;
    private Button updateInstallButton;
        private UpdateChecker.Release availableRelease;
    private ScrollView settingsScroll;
    private LinearLayout settingsContent;
    private ImageButton headerBackButton;
    private TextView headerTitleView;
    private TextView headerSubtitleView;
    private String currentSettingsSection = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        try {
            getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            setContentView(buildUi());
            restoreSettingsLocation(savedInstanceState);
            handleInstallIntent(getIntent());
            AppLogger.info(this, "settings_open", BuildInfo.VERSION + " | section=" + currentSettingsSection);
        } catch (Throwable t) {
            AppLogger.crash(this, t);
            showSettingsRecovery(t);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SETTINGS_SECTION, currentSettingsSection == null ? "" : currentSettingsSection);
        if (settingsScroll != null) outState.putInt(STATE_SETTINGS_SCROLL_Y, settingsScroll.getScrollY());
        super.onSaveInstanceState(outState);
    }

    private void restoreSettingsLocation(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        String section = savedInstanceState.getString(STATE_SETTINGS_SECTION, "");
        int scrollY = savedInstanceState.getInt(STATE_SETTINGS_SCROLL_Y, 0);
        if (section != null && !section.trim().isEmpty()) showSettingsSection(section.trim());
        if (settingsScroll != null && scrollY > 0) {
            settingsScroll.post(() -> settingsScroll.scrollTo(0, scrollY));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            refreshStatusLabels();
            resumeUpdateInstallAfterPermission();
        } catch (Throwable t) { AppLogger.error(this, "settings_resume", t.getClass().getSimpleName()); }
    }

    private void showSettingsRecovery(Throwable t) {
        try {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            page.setPadding(dp(24), dp(24), dp(24), dp(24));
            page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            TextView title = text("App Control Center • Recovery Mode", 18, getColor(R.color.hcf_accent_text));
            title.setGravity(Gravity.CENTER);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            page.addView(title);
            TextView detail = text(BuildInfo.VERSION + " • Build " + BuildInfo.VERSION_CODE + "\n\nSettings UI recovered from: " + t.getClass().getSimpleName(), 13, getColor(R.color.hcf_text));
            detail.setGravity(Gravity.CENTER);
            page.addView(detail);
            Button done = new Button(this);
            UiButtons.normalizeText(done);
            done.setText("Back to Forum");
            done.setAllCaps(false);
            done.setOnClickListener(v -> finish());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 44 : 52));
            p.topMargin = dp(18);
            page.addView(done, p);
            setContentView(page);
        } catch (Throwable ignored) {}
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        page.addView(buildHeader());

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        settingsContent.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        settingsScroll.addView(settingsContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        showSettingsHome();

        page.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(compact() ? 2 : 5), dp(8), dp(compact() ? 2 : 5));
        bar.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));
        bar.setMinimumHeight(dp(compact() ? 46 : 56));

        headerBackButton = chromeButton("‹");
        headerBackButton.setContentDescription("Back");
        headerBackButton.setOnClickListener(v -> {
            if (currentSettingsSection != null && !currentSettingsSection.isEmpty()) showSettingsHome();
            else finish();
        });
        bar.addView(headerBackButton, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoP = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        logoP.leftMargin = dp(4);
        bar.addView(logo, logoP);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        bar.addView(titles, tp);
        headerTitleView = text("App Settings", 18, getColor(R.color.hcf_text));
        headerTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(headerTitleView);
        headerSubtitleView = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 10, getColor(R.color.hcf_meta));
        headerSubtitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(headerSubtitleView);
        return bar;
    }

    private void showSettingsHome() {
        currentSettingsSection = "";
        updateSettingsHeader("App Settings", BuildInfo.DEVELOPMENT_BUILD_LABEL);
        if (settingsContent == null) return;
        settingsContent.removeAllViews();

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search settings…");
        search.setHintTextColor(getColor(R.color.hcf_hint));
        search.setTextColor(getColor(R.color.hcf_text));
        search.setTextSize(14f);
        search.setBackgroundResource(R.drawable.quick_action_background);
        search.setPadding(dp(15), 0, dp(15), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 42 : 48));
        searchParams.bottomMargin = dp(10);
        settingsContent.addView(search, searchParams);

        settingsContent.addView(statusDashboardCard());

        TextView categoriesTitle = text("Settings", 10, getColor(R.color.hcf_cyan));
        categoriesTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        categoriesTitle.setPadding(dp(4), dp(2), 0, dp(7));
        settingsContent.addView(categoriesTitle);

        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        settingsContent.addView(categories, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSettingsCategory(categories, "Account & Identity", "Profile, sign-in state and account security", "account", "profile username login identity account security");
        addSettingsCategory(categories, "Notifications", "Forum alerts, background delivery and testing", "notifications", "alerts heads-up direct message mention reply background instant");
        addSettingsCategory(categories, "Appearance & Interface", "Theme, performance and app-shell options", "appearance", "theme dark light amoled performance header url startup live updates");
        addSettingsCategory(categories, "Connection", "Primary/backup routing and external-link behavior", "connection", "server primary backup failover external browser links");
        addSettingsCategory(categories, "Privacy & Site Data", "Cookies, WebView site data and sign-out cleanup", "privacy", "cookies data cache storage privacy clear sign out");
        addSettingsCategory(categories, "Permissions & Security", "Android permissions and app security status", "security", "permissions installer notification security certificate safe");
        addSettingsCategory(categories, "App Updates", "Update checks, downloads and installation", "updates", "update apk download install stable channel official releases");
        addSettingsCategory(categories, "Diagnostics & Telemetry", "Logs, diagnostics and optional telemetry controls", "advanced", "logs diagnostics telemetry technical debug status");
        addSettingsCategory(categories, "About & What's New", "Version, release notes and build details", "about", "about version build patch notes what's new release support email contact");

        TextView empty = text("No matching settings.", 12, getColor(R.color.hcf_muted));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(18), 0, dp(18));
        empty.setVisibility(View.GONE);
        settingsContent.addView(empty);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                filterSettingsCategories(categories, empty, value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) {}
        });

        TextView footer = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 9, getColor(R.color.hcf_hint));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(14), 0, dp(4));
        settingsContent.addView(footer);

        if (settingsScroll != null) settingsScroll.post(() -> settingsScroll.scrollTo(0, 0));
        refreshStatusLabels();
    }

    private void showSettingsSection(String key) {
        currentSettingsSection = key == null ? "" : key;
        if (settingsContent == null) return;
        settingsContent.removeAllViews();
        updateSettingsHeader(settingsSectionName(currentSettingsSection), settingsSectionSubtitle(currentSettingsSection));

        switch (currentSettingsSection) {
            case "account":
                settingsContent.addView(accountIdentityCard());
                break;
            case "notifications":
                settingsContent.addView(notificationsCard());
                break;
            case "appearance":
                settingsContent.addView(interfaceCard());
                break;
            case "connection":
                settingsContent.addView(connectionCard());
                break;
            case "privacy":
                settingsContent.addView(privacyCard());
                break;
            case "security":
                settingsContent.addView(securityCard());
                break;
            case "updates":
                settingsContent.addView(updateCard());
                break;
            case "advanced":
                settingsContent.addView(telemetryCard());
                settingsContent.addView(diagnosticsCard());
                break;
            case "about":
                settingsContent.addView(aboutCard());
                break;
            default:
                showSettingsHome();
                return;
        }

        Button allSettings = actionButton("‹  Back to all settings", v -> showSettingsHome());
        settingsContent.addView(allSettings);
        if (settingsScroll != null) settingsScroll.post(() -> settingsScroll.scrollTo(0, 0));
        refreshStatusLabels();
    }

    private void addSettingsCategory(LinearLayout parent, String title, String subtitle, String key, String keywords) {
        View row = settingsCategoryRow(title, subtitle, key);
        row.setTag((title + " " + subtitle + " " + keywords).toLowerCase(Locale.US));
        parent.addView(row);
    }

    private View settingsCategoryRow(String title, String subtitle, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(15), dp(compact() ? 8 : 11), dp(12), dp(compact() ? 8 : 11));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(7);
        row.setLayoutParams(rowParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(titleView);
        TextView subtitleView = text(subtitle, 10, getColor(R.color.hcf_muted));
        labels.addView(subtitleView);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text("›", 24, getColor(R.color.hcf_cyan_bright));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> showSettingsSection(key));
        return row;
    }

    private void filterSettingsCategories(LinearLayout categories, TextView empty, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.US);
        int visible = 0;
        for (int i = 0; i < categories.getChildCount(); i++) {
            View child = categories.getChildAt(i);
            String haystack = child.getTag() == null ? "" : String.valueOf(child.getTag());
            boolean match = query.isEmpty() || haystack.contains(query);
            child.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) visible++;
        }
        if (empty != null) empty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateSettingsHeader(String title, String subtitle) {
        if (headerTitleView != null) headerTitleView.setText(title == null ? "App Settings" : title);
        if (headerSubtitleView != null) headerSubtitleView.setText(subtitle == null ? BuildInfo.DEVELOPMENT_BUILD_LABEL : subtitle);
        if (headerBackButton != null) headerBackButton.setContentDescription(currentSettingsSection == null || currentSettingsSection.isEmpty() ? "Back to forum" : "Back to all settings");
    }

    private String settingsSectionName(String key) {
        if ("account".equals(key)) return "Account & Identity";
        if ("notifications".equals(key)) return "Notifications";
        if ("appearance".equals(key)) return "Appearance & Interface";
        if ("connection".equals(key)) return "Connection";
        if ("privacy".equals(key)) return "Privacy & Site Data";
        if ("security".equals(key)) return "Permissions & Security";
        if ("updates".equals(key)) return "App Updates";
        if ("advanced".equals(key)) return "Diagnostics & Telemetry";
        if ("about".equals(key)) return "About";
        return "App Settings";
    }

    private String settingsSectionSubtitle(String key) {
        return BuildInfo.DEVELOPMENT_BUILD_LABEL;
    }

    private String channelDisplay() {
        String value = BuildInfo.CHANNEL == null ? "Stable" : BuildInfo.CHANNEL.toLowerCase(Locale.US);
        if (value.isEmpty()) return "Stable";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String channelDisplayName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Stable";
        String value = raw.trim().toLowerCase(Locale.US);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private View statusDashboardCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Live Status", "Forum and notification service at a glance"));

        liveSyncStatus = text("Live sync: checking…", 12, getColor(R.color.hcf_meta));
        liveSyncStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(liveSyncStatus);

        card.addView(actionButton("Sync Forum & Notifications Now", v -> {
            String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            if (userId == null || userId.trim().isEmpty()) {
                Toast.makeText(this, "Sign in to the forum first, then sync again.", Toast.LENGTH_LONG).show();
                return;
            }
            InstantNotificationService.requestImmediateSync(this);
            if (liveSyncStatus != null) liveSyncStatus.setText("Live sync: syncing now…");
            Toast.makeText(this, "Forum sync requested.", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::refreshStatusLabels, 1600L);
        }));
        return card;
    }

    private View accountIdentityCard() {
        LinearLayout card = card();
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        card.addView(sectionTitle("Account & Identity", "Your signed-in forum profile and account security"));

        TextView state = text(identity.loggedIn
                ? "Signed in as " + identity.identityLabel()
                : "Forum session: Guest_Protocol", 13,
                getColor(identity.loggedIn ? R.color.hcf_accent_text : R.color.hcf_meta));
        state.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(state);

        TextView detail = text(identity.loggedIn
                ? (identity.username.isEmpty() ? "Identity sync active" : "@" + identity.username + " • Identity sync active")
                : "Sign in to connect your identity", 11, getColor(R.color.hcf_muted));
        card.addView(detail);

        card.addView(actionButton("Open Account & Identity", v ->
                startActivity(new Intent(this, IdentityActivity.class))));
        return card;
    }

    private View notificationsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Notifications", "Near-instant alerts in foreground and background"));

        notificationStatus = text("Checking notification status…", 12, getColor(R.color.hcf_muted));
        card.addView(notificationStatus);

        TextView centerTitle = text("Notification Center", 10, getColor(R.color.hcf_cyan));
        centerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        centerTitle.setPadding(0, dp(8), 0, 0);
        card.addView(centerTitle);
        card.addView(actionButton("Open Notification Center & History", v ->
                startActivity(new Intent(this, NotificationHistoryActivity.class))));
        dndStatus = text("Do Not Disturb: " + AppSettings.dndLabel(this), 11, getColor(R.color.hcf_meta));
        dndStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(dndStatus);
        card.addView(actionButton("Do Not Disturb & Schedule", v ->
                startActivity(new Intent(this, NotificationHistoryActivity.class))));
        card.addView(text("Notification History keeps recent app alerts locally. Do Not Disturb can be Off, always On, or Scheduled inside the Notification Center.", 10, getColor(R.color.hcf_muted)));

        Switch notifications = toggle("Allow forum notifications", prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true));
        notifications.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.NOTIFICATIONS_ENABLED, checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_notifications", Boolean.toString(checked));
            if (checked) requestNotificationPermissionIfNeeded();
            refreshStatusLabels();
        });
        card.addView(notifications);

        Switch background = toggle("Instant foreground + background notifications", prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true));
        background.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
        });
        card.addView(background);

        TextView instantNote = text("Background notification service: available when enabled. Android may show a small ongoing service notification so forum alerts can continue while the app is off-screen.", 11, getColor(R.color.hcf_muted));
        instantNote.setPadding(dp(2), dp(6), dp(2), dp(6));
        card.addView(instantNote);

        TextView tests = text("Notification Test Center", 10, getColor(R.color.hcf_cyan));
        tests.setTypeface(null, android.graphics.Typeface.BOLD);
        tests.setPadding(0, dp(8), 0, 0);
        card.addView(tests);
        card.addView(actionButton("Test Direct Message Alert", v -> postTestAlert("Direct message", "New private message test", "/notifications")));
        card.addView(actionButton("Test Mention Alert", v -> postTestAlert("Mention", "@you were mentioned in a forum post", "/notifications")));
        card.addView(actionButton("Test Reply Alert", v -> postTestAlert("Discussion reply", "New reply test notification", "/notifications")));
        card.addView(actionButton("Test General Alert", v -> postTestAlert("Forum alert", "General Harley's Clan Forum notification test", "/notifications")));

        card.addView(actionButton("Android Notification Settings", v -> openNotificationSettings()));
        return card;
    }

    private View interfaceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Appearance & Interface", "A simpler app shell with more room for the forum"));

        Button themeButton = actionButton("Theme: " + ThemeManager.label(this), v -> {
            String next = ThemeManager.next(ThemeManager.mode(this));
            prefs.edit().putString(AppPrefs.APP_THEME, next).apply();
            AppLogger.info(this, "setting_theme", next + " | keep_section=" + currentSettingsSection);
            recreate();
        });
        themeButton.setContentDescription("Change app day, night and AMOLED theme");
        card.addView(themeButton);

        Button performance = actionButton("Performance Profile: " + PerformanceProfile.settingLabel(this, prefs), null);
        performance.setContentDescription("Choose app performance profile");
        performance.setOnClickListener(v -> showPerformanceProfileDialog(performance));
        card.addView(performance);
        card.addView(text("Auto is the default adaptive engine. Capable devices can promote to Auto • Real-Time; HCF automatically drops to Balanced, Performance, or Extreme Saver when network, memory, battery, thermal, or renderer conditions require it.", 10, getColor(R.color.hcf_muted)));

        Switch live = toggle("Live forum updates", prefs.getBoolean(AppPrefs.LIVE_FORUM_UPDATES, true));
        live.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.LIVE_FORUM_UPDATES, checked).apply();
            AppLogger.info(this, "setting_live_updates", Boolean.toString(checked));
            Toast.makeText(this, checked ? "Live forum updates enabled." : "Live forum updates disabled.", Toast.LENGTH_SHORT).show();
        });
        card.addView(live);

        Switch url = toggle("Show secure URL bar", prefs.getBoolean(AppPrefs.SHOW_URL_BAR, false));
        url.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.SHOW_URL_BAR, checked).apply();
            AppLogger.info(this, "setting_url_bar", Boolean.toString(checked));
        });
        card.addView(url);

        TextView shell = text("Header: Classic compact • bottom app bar removed", 11, getColor(R.color.hcf_cyan));
        shell.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(shell);
        card.addView(text("The extra LIVE badge, duplicate header alert button, and native bottom navigation bar stay removed. Forum alerts remain available inside the forum and app drawer.", 10, getColor(R.color.hcf_muted)));

        Switch startup = toggle("Show startup connection screen", prefs.getBoolean(AppPrefs.SHOW_STARTUP_SCREEN, true));
        startup.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.SHOW_STARTUP_SCREEN, checked).apply();
            AppLogger.info(this, "setting_startup_screen", Boolean.toString(checked));
        });
        card.addView(startup);

        card.addView(text("Live updates check for new forum activity while the app is open and refresh safely when you are not typing.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Connection", "Primary and backup forum routing"));

        serverStatus = text("Current server: checking…", 12, getColor(R.color.hcf_cyan));
        card.addView(serverStatus);

        Switch autoFailover = toggle("Automatically use backup if primary fails", prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true));
        autoFailover.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.AUTO_FAILOVER, checked).apply();
            AppLogger.info(this, "setting_auto_failover", Boolean.toString(checked));
        });
        card.addView(autoFailover);

        Switch external = toggle("Allow external links to open in browser/apps", prefs.getBoolean(AppPrefs.EXTERNAL_LINKS, true));
        external.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.EXTERNAL_LINKS, checked).apply();
            AppLogger.info(this, "setting_external_links", Boolean.toString(checked));
        });
        card.addView(external);

        card.addView(actionButton("Retry Primary Forum on Next Open", v -> {
            prefs.edit().remove(AppPrefs.FALLBACK_UNTIL).putString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST).apply();
            refreshStatusLabels();
            Toast.makeText(this, "Primary forum restored as preferred server.", Toast.LENGTH_SHORT).show();
            AppLogger.info(this, "fallback_reset", "settings");
        }));

        TextView links = text("Forum links: primary + backup registered with Android", 10, getColor(R.color.hcf_muted));
        links.setPadding(0, dp(10), 0, 0);
        card.addView(links);
        card.addView(actionButton("Open Forum Link Settings", v -> openForumLinkSettings()));
        return card;
    }

    private View privacyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Cookies & Site Data", "Forum data stored on this phone"));
        cookieStatus = text(cookieSummary(), 12, getColor(R.color.hcf_meta));
        cookieStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(cookieStatus);
        card.addView(actionButton("Open Cookie Manager", v -> startActivity(new Intent(this, CookieManagerActivity.class))));
        card.addView(actionButton("Clear Forum Site Data & Sign Out", v -> {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(value -> {
                cm.flush();
                if (cookieStatus != null) cookieStatus.setText(cookieSummary());
            });
            WebStorage.getInstance().deleteAllData();
            ForumIdentity.clear(this);
            AppLogger.info(this, "forum_site_data_cleared", "settings");
            Toast.makeText(this, "Forum cookies and site data cleared.", Toast.LENGTH_LONG).show();
        }));
        return card;
    }


    private View telemetryCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Telemetry & Diagnostics", "Crash reports, app health and privacy controls"));

        telemetryStatus = text(TelemetryService.status(this), 11, getColor(R.color.hcf_meta));
        telemetryStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(telemetryStatus);

        Switch telemetry = toggle("Enable Telemetry Services", prefs.getBoolean(AppPrefs.TELEMETRY_ENABLED, false));
        telemetry.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_ENABLED, checked).apply();
            AppLogger.info(this, "setting_telemetry", Boolean.toString(checked));
            if (checked) {
                TelemetryService.sendEvent(this, "telemetry_enabled", "User enabled Telemetry Services in App Settings");
                Toast.makeText(this, "Telemetry Services enabled.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Telemetry disabled. No reports will be sent.", Toast.LENGTH_SHORT).show();
            }
            refreshStatusLabels();
        });
        card.addView(telemetry);

        Button levelButton = actionButton("Telemetry level: " + TelemetryService.levelLabel(this), null);
        levelButton.setOnClickListener(v -> {
            String next = TelemetryService.LEVEL_DIAGNOSTICS.equals(TelemetryService.level(this))
                    ? TelemetryService.LEVEL_BASIC : TelemetryService.LEVEL_DIAGNOSTICS;
            prefs.edit().putString(AppPrefs.TELEMETRY_LEVEL, next).apply();
            levelButton.setText("Telemetry level: " + TelemetryService.levelLabel(this));
            AppLogger.info(this, "setting_telemetry_level", next);
            refreshStatusLabels();
        });
        card.addView(levelButton);
        card.addView(text("Basic: coarse app health only. Diagnostics: adds crashes, sanitized stack traces, recent app events, and optional WebView/update errors.", 10, getColor(R.color.hcf_muted)));

        Switch autoCrash = toggle("Automatically send crash reports", prefs.getBoolean(AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS, false));
        autoCrash.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS, checked).apply();
            AppLogger.info(this, "setting_auto_crash_reports", Boolean.toString(checked));
        });
        card.addView(autoCrash);

        Switch askCrash = toggle("Ask me before every crash report", prefs.getBoolean(AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT, true));
        askCrash.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT, checked).apply();
            AppLogger.info(this, "setting_ask_crash_report", Boolean.toString(checked));
        });
        card.addView(askCrash);

        Switch autoErrors = toggle("Automatically send WebView/update errors", prefs.getBoolean(AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS, false));
        autoErrors.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS, checked).apply();
            AppLogger.info(this, "setting_auto_error_reports", Boolean.toString(checked));
        });
        card.addView(autoErrors);

        TextView privacyTitle = text("Report Privacy", 10, getColor(R.color.hcf_cyan));
        privacyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        privacyTitle.setPadding(0, dp(8), 0, 0);
        card.addView(privacyTitle);

        Switch identity = toggle("Include my forum identity with reports", prefs.getBoolean(AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false));
        identity.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_INCLUDE_IDENTITY, checked).apply();
            AppLogger.info(this, "setting_telemetry_identity", Boolean.toString(checked));
        });
        card.addView(identity);

        Switch email = toggle("Include my email when identity is included", prefs.getBoolean(AppPrefs.TELEMETRY_INCLUDE_EMAIL, false));
        email.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_INCLUDE_EMAIL, checked).apply();
            AppLogger.info(this, "setting_telemetry_email", Boolean.toString(checked));
        });
        card.addView(email);

        Switch device = toggle("Include device manufacturer/model", prefs.getBoolean(AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL, false));
        device.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL, checked).apply();
            AppLogger.info(this, "setting_telemetry_device", Boolean.toString(checked));
        });
        card.addView(device);

        Switch route = toggle("Include sanitized forum route", prefs.getBoolean(AppPrefs.TELEMETRY_INCLUDE_ROUTE, false));
        route.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_INCLUDE_ROUTE, checked).apply();
            AppLogger.info(this, "setting_telemetry_route", Boolean.toString(checked));
        });
        card.addView(route);

        card.addView(actionButton("Send Test Telemetry", v -> {
            if (!TelemetryService.isEnabled(this)) {
                Toast.makeText(this, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show();
                return;
            }
            TelemetryService.sendTest(this);
            Toast.makeText(this, "Telemetry test queued.", Toast.LENGTH_SHORT).show();
            if (telemetryStatus != null) telemetryStatus.setText("Telemetry: On • sending test…");
        }));
        card.addView(actionButton("Send Diagnostic Feedback", v -> TelemetryService.showManualFeedbackDialog(this)));
        card.addView(actionButton("Preview Telemetry Report", v -> TelemetryService.showPreview(this)));
        card.addView(actionButton("View Report History", v -> TelemetryService.showHistory(this)));
        card.addView(actionButton("Clear Local Telemetry Reports", v -> {
            TelemetryService.clearLocalReports(this);
            Toast.makeText(this, "Local telemetry reports and breadcrumbs cleared.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        }));

        TextView note = text(
                "Crash reports get an HCF report ID and can include a sanitized stack trace plus recent app events. Identity, email, device model and page route are separate opt-ins. Passwords, cookies, access/session tokens, recovery codes, provider IDs, posts, messages and page contents are never sent. The Discord endpoint remains encrypted at rest in the APK, but a server-side relay is still safer for long-term webhook secrecy.",
                10, getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private View securityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Permissions & Security", "Android permissions and app hardening"));

        securityStatus = text(AppSecurity.securitySummary(this), 11, getColor(R.color.hcf_meta));
        card.addView(securityStatus);

        card.addView(actionButton("Allow Notification Permission", v -> requestNotificationPermissionIfNeeded()));
        card.addView(actionButton("Allow Secure App Updates", v -> {
            if (Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(this)) {
                Toast.makeText(this, "Secure app update installation is already allowed.", Toast.LENGTH_SHORT).show();
                refreshStatusLabels();
                return;
            }
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivity(permission);
            } catch (Throwable t) {
                Toast.makeText(this, "Android could not open the update install permission screen.", Toast.LENGTH_LONG).show();
            }
        }));
        card.addView(actionButton("Android App Permission Settings", v -> {
            try {
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(details);
            } catch (Throwable t) {
                Toast.makeText(this, "Android app settings could not be opened.", Toast.LENGTH_LONG).show();
            }
        }));

        TextView note = text(
                "No location, contacts, microphone, camera, or broad storage permission is requested. Update APKs are accepted only from HCF's trusted release source and must match the installed package and signing certificate.",
                10, getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private View diagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Error & Recovery Center", "Troubleshooting and smart-recovery tools"));
        card.addView(text("HCF error codes are enabled • network, server, SSL, WebView and update failures use friendly native explanations.", 10, getColor(R.color.hcf_muted)));
        card.addView(actionButton("Run Error & Recovery Check", v -> showRecoveryDiagnostics()));
        card.addView(actionButton("Copy Sanitized Diagnostic Report", v -> copyDiagnosticReport()));
        card.addView(actionButton("View App & Crash Logs", v -> startActivity(new Intent(this, LogsActivity.class))));
        card.addView(actionButton("Clear WebView Cache", v -> {
            try {
                WebView temp = new WebView(this);
                temp.clearCache(true);
                temp.clearHistory();
                temp.destroy();
                AppLogger.info(this, "webview_cache_cleared", "settings");
                Toast.makeText(this, "WebView cache cleared.", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                AppLogger.error(this, "webview_cache_clear", t.getClass().getSimpleName());
                Toast.makeText(this, "WebView cache could not be cleared on this device.", Toast.LENGTH_SHORT).show();
            }
        }));
        return card;
    }

    private void postTestAlert(String title, String body, String path) {
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        NotificationHelper.post(this, title, body, "https://" + host + (path == null ? "/notifications" : path));
        AppLogger.info(this, "notification_test", title);
    }

    private void showRecoveryDiagnostics() {
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        boolean network = isValidatedNetworkAvailable();
        String webViewProvider = "Unknown";
        try {
            android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info != null) webViewProvider = info.packageName + " • " + info.versionName;
        } catch (Throwable ignored) {}
        String lastRoute = prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, "");
        lastRoute = lastRoute == null || lastRoute.trim().isEmpty() ? "Not recorded yet" : AppLogger.safeUrl(lastRoute);
        String message = "Network: " + (network ? "✓ Connected" : "✕ Offline") + "\n"
                + "Current server: " + host + "\n"
                + "Automatic failover: " + (prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "✓ Enabled" : "Disabled") + "\n"
                + "WebView provider: " + webViewProvider + "\n"
                + "Renderer recovery: ✓ Enabled (HCF-WV-001)\n"
                + "SSL fail-closed: ✓ Enabled (HCF-SSL-001)\n"
                + "Last recoverable route: " + lastRoute;
        new AlertDialog.Builder(this)
                .setTitle("Error & Recovery Check")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNeutralButton("View logs", (dialog, which) -> startActivity(new Intent(this, LogsActivity.class)))
                .show();
        AppLogger.info(this, "recovery_diagnostics", network ? "network-ok" : "offline");
    }

    private boolean isValidatedNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copyDiagnosticReport() {
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        String sync = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "not synced yet");
        long latency = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, 0L);
        String report = "Harley's Clan Forum • Sanitized Diagnostic Report\n"
                + "App: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n"
                + "Package: " + getPackageName() + "\n"
                + "Android SDK: " + Build.VERSION.SDK_INT + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Forum host: " + host + "\n"
                + "Theme: " + ThemeManager.label(this) + "\n"
                + "Performance profile: " + PerformanceProfile.settingLabel(this, prefs) + "\n"
                + "Notifications: " + NotificationHelper.status(this) + "\n"
                + "Live sync: " + sync + (latency > 0L ? " • " + latency + " ms" : "") + "\n"
                + "Auto failover: " + (prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "On" : "Off") + "\n"
                + "Renderer recovery: Enabled (HCF-WV-001)\n"
                + "Last route: " + AppLogger.safeUrl(prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, "")) + "\n"
                + "Cookies/tokens/passwords/email: Not included";
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("HCF diagnostic report", report));
            Toast.makeText(this, "Sanitized diagnostic report copied.", Toast.LENGTH_SHORT).show();
            AppLogger.info(this, "diagnostic_report_copy", "sanitized");
        } catch (Throwable t) {
            Toast.makeText(this, "Could not copy the diagnostic report.", Toast.LENGTH_SHORT).show();
        }
    }

    private View updateCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("App Updates", "Secure automatic app updates"));

        String channel = effectiveUpdateChannel();
        updateChannelStatus = text(updateChannelLine(channel), 12, getColor(R.color.hcf_meta));
        updateChannelStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(updateChannelStatus);

        long lastCheckAt = prefs.getLong(AppPrefs.UPDATE_LAST_CHECK, 0L);
        String lastCheckLine = lastCheckAt <= 0L ? "Not checked yet on this install"
                : ("Last checked " + ("just now".equals(formatAge(System.currentTimeMillis() - lastCheckAt))
                ? "just now" : formatAge(System.currentTimeMillis() - lastCheckAt) + " ago"));
        updateStatus = text(
                "Installed • " + BuildInfo.VERSION_TAG + "\n" + lastCheckLine,
                11,
                getColor(R.color.hcf_muted));
        card.addView(updateStatus);

        Switch autoCheck = toggle("Automatic update checks", prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true));
        autoCheck.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_CHECK, checked).apply();
            UpdateScheduler.apply(this);
            AppLogger.info(this, "setting_update_auto_check", Boolean.toString(checked));
        });
        card.addView(autoCheck);

        Switch autoDownload = toggle("Automatically download new APKs", prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, true));
        autoDownload.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, checked).apply();
            AppLogger.info(this, "setting_update_auto_download", Boolean.toString(checked));
        });
        card.addView(autoDownload);

        Switch autoInstall = toggle("Open installer automatically after download", prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true));
        autoInstall.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_INSTALL, checked).apply();
            AppLogger.info(this, "setting_update_auto_install", Boolean.toString(checked));
        });
        card.addView(autoInstall);

        if (BuildInfo.ALLOW_UPDATE_CHANNEL_SWITCH) {
            card.addView(actionButton("Update Channel", v -> showUpdateChannelDialog()));
        }
        card.addView(actionButton("Check for Updates", v -> checkForUpdates(true)));

        updateDownloadButton = actionButton("Download Update Now", v -> {
            if (availableRelease != null && availableRelease.apkUrl != null && !availableRelease.apkUrl.isEmpty()) {
                long id = AppUpdateDownloader.enqueue(this, availableRelease, true);
                if (id > 0L) {
                    Toast.makeText(this, "Update download started.", Toast.LENGTH_SHORT).show();
                    updateStatus.setText("Downloading Beta build" + (availableRelease.versionCode > 0 ? " " + availableRelease.versionCode : "") + "… The Android installer will open automatically when verification finishes.");
                    watchUpdateDownloadForAutoInstall(id);
                } else {
                    Toast.makeText(this, "Update download could not start.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "This release does not have an APK asset yet.", Toast.LENGTH_LONG).show();
            }
        });
        updateDownloadButton.setVisibility(View.GONE);
        card.addView(updateDownloadButton);

        updateInstallButton = actionButton("Install Downloaded Update", v -> installDownloadedUpdate());
        updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        card.addView(updateInstallButton);

        TextView note = text(
                "After an update APK finishes downloading, HCF verifies its package, versionCode, and signing certificate, then opens Android’s installer automatically. Android still requires your confirmation to install. If Android asks for “Allow from this source,” HCF resumes the verified installer when you return. After a successful in-place update, the temporary APK is removed automatically.",
                10,
                getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);

        return card;
    }

    private String updateChannelLine(String channel) {
        boolean stable = UpdateChecker.CHANNEL_STABLE.equalsIgnoreCase(channel);
        return "Channel: " + (stable ? "Stable • Official Releases" : "Development / Beta • Preview Releases");
    }

    private String effectiveUpdateChannel() {
        if (!BuildInfo.ALLOW_UPDATE_CHANNEL_SWITCH) return UpdateChecker.CHANNEL_DEV;
        String channel = prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL);
        return UpdateChecker.CHANNEL_STABLE.equalsIgnoreCase(channel)
                ? UpdateChecker.CHANNEL_STABLE : UpdateChecker.CHANNEL_DEV;
    }

    private void showUpdateChannelDialog() {
        final String[] labels = new String[]{
                "Stable — Official releases",
                "Dev — Preview releases"
        };
        String current = prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL);
        int checked = UpdateChecker.CHANNEL_STABLE.equalsIgnoreCase(current) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("Update Channel")
                .setSingleChoiceItems(labels, checked, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    AlertDialog alert = (AlertDialog) dialog;
                    int selected = alert.getListView().getCheckedItemPosition();
                    String channel = selected == 0 ? UpdateChecker.CHANNEL_STABLE : UpdateChecker.CHANNEL_DEV;
                    prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, channel).apply();
                    availableRelease = null;
                    if (updateDownloadButton != null) updateDownloadButton.setVisibility(View.GONE);
                                        if (updateInstallButton != null) updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(SettingsActivity.this) ? View.VISIBLE : View.GONE);
                    if (updateChannelStatus != null) updateChannelStatus.setText(updateChannelLine(channel));
                    if (updateStatus != null) updateStatus.setText("Channel changed. Tap Check for Updates.");
                    AppLogger.info(this, "update_channel", channel);
                    checkForUpdates(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkForUpdates(boolean userRequested) {
        if (updateStatus == null) return;
        final String channel = effectiveUpdateChannel();
        updateStatus.setText("Checking " + channelDisplayName(channel) + " release channel…");
        updateStatus.setTextColor(getColor(R.color.hcf_meta));
        if (updateDownloadButton != null) updateDownloadButton.setVisibility(View.GONE);
                availableRelease = null;

        UpdateChecker.check(this, channel, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                availableRelease = release;
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                String releaseType = release.prerelease ? "Pre-release" : "Latest stable";
                String apk = release.apkName == null || release.apkName.isEmpty() ? "No APK asset attached" : release.apkName;
                if (updateAvailable) {
                    updateStatus.setText("Beta Update Available\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nAvailable: v" + UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "") + "\n" + releaseType + " • " + apk);
                    updateStatus.setTextColor(getColor(R.color.hcf_accent_text));
                    if (updateDownloadButton != null && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        updateDownloadButton.setVisibility(View.VISIBLE);
                    }
                    if (prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, true) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        long id = AppUpdateDownloader.enqueue(SettingsActivity.this, release, userRequested);
                        if (id > 0L) {
                            updateStatus.append("\nAutomatic download queued • installer opens when ready.");
                            watchUpdateDownloadForAutoInstall(id);
                        }
                    }
                    if (userRequested) Toast.makeText(SettingsActivity.this, "Beta update available" + (release.versionCode > 0 ? " • build " + release.versionCode : ""), Toast.LENGTH_LONG).show();
                } else {
                    if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                        updateStatus.setText("Installed build is newer\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nPublished: v" + UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "") + " • " + apk);
                        updateStatus.setTextColor(getColor(R.color.hcf_meta));
                        if (userRequested) Toast.makeText(SettingsActivity.this, "The published release feed is behind this installed build.", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatus.setText("Up to date\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nNewest " + channel + ": v" + UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "") + " • " + apk);
                        updateStatus.setTextColor(getColor(R.color.hcf_meta));
                        if (userRequested) Toast.makeText(SettingsActivity.this, "This build is up to date for " + channel + ".", Toast.LENGTH_SHORT).show();
                    }
                }
                AppLogger.info(SettingsActivity.this, "update_check", channel + " | " + release.tag + " | newer=" + updateAvailable);
            }

            @Override
            public void onError(String message) {
                updateStatus.setText("Update check unavailable • " + message);
                updateStatus.setTextColor(getColor(R.color.hcf_yellow));
                if (userRequested) Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                AppLogger.error(SettingsActivity.this, "update_check", message);
            }
        });
    }

    private void watchUpdateDownloadForAutoInstall(long id) {
        if (id <= 0L || !prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true)) return;
        final long watchId = id;
        final Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (isFinishing() || isDestroyed() || !prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true)) return;
            AppUpdateDownloader.ProgressSnapshot state = AppUpdateDownloader.progress(SettingsActivity.this, watchId);
            if (state.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                installDownloadedUpdate(watchId);
                return;
            }
            if (state.status == android.app.DownloadManager.STATUS_FAILED) return;
            if (settingsScroll != null) settingsScroll.postDelayed(poll[0], 500L);
        };
        if (settingsScroll != null) settingsScroll.postDelayed(poll[0], 500L);
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null || !"com.harleytg.forum.dev.INSTALL_UPDATE".equals(intent.getAction())) return;
        long id = intent.getLongExtra("download_id", -1L);
        if (id > 0L) {
            final long installId = id;
            getWindow().getDecorView().postDelayed(() -> installDownloadedUpdate(installId), 250L);
        }
    }

    private void installDownloadedUpdate() {
        installDownloadedUpdate(AppUpdateDownloader.downloadedId(this));
    }

    private void installDownloadedUpdate(long id) {
        if (id <= 0L) {
            Toast.makeText(this, "No downloaded update is ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                prefs.edit().putBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, true).apply();
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, UPDATE_INSTALL_PERMISSION_REQUEST);
                Toast.makeText(this, "Allow installs from this source. HCF will resume the verified update automatically when you return.", Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                Toast.makeText(this, "Android blocked installation permission settings.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!AppUpdateDownloader.openInstaller(this, id)) {
            Toast.makeText(this, "The Android installer could not open this download.", Toast.LENGTH_LONG).show();
        }
    }

    private void resumeUpdateInstallAfterPermission() {
        if (!prefs.getBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, false)) return;
        if (Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this)) return;
        prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
        long ready = AppUpdateDownloader.downloadedId(this);
        if (ready > 0L) {
            Toast.makeText(this, "Install permission enabled • opening verified update…", Toast.LENGTH_SHORT).show();
            if (!AppUpdateDownloader.openInstaller(this, ready)) {
                Toast.makeText(this, "The Android installer could not open this verified update.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(this)) {
                resumeUpdateInstallAfterPermission();
            } else {
                prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                Toast.makeText(this, "Install permission was not enabled. The downloaded APK was kept.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openSupportEmail() {
        try {
            startActivity(new Intent(this, SupportContactActivity.class));
            AppLogger.info(this, "support_contact_open", "about");
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open Contact Support.", Toast.LENGTH_LONG).show();
            AppLogger.error(this, "support_contact_open", t.getClass().getSimpleName());
        }
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("About Harley's Clan Forum", "Version, release notes and technical build information"));

        TextView appName = text("Harley's Clan Forum [Beta]", 18, getColor(R.color.hcf_text));
        appName.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(appName);

        TextView version = text(BuildInfo.DEVELOPMENT_BUILD_LABEL + "\nBuild " + BuildInfo.VERSION_CODE,
                12, getColor(R.color.hcf_cyan));
        version.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(version);

        TextView releaseSummary = text("What's New • " + ReleaseNotes.SUMMARY, 10, getColor(R.color.hcf_accent_text));
        releaseSummary.setTypeface(null, android.graphics.Typeface.BOLD);
        releaseSummary.setPadding(0, dp(10), 0, 0);
        card.addView(releaseSummary);

        card.addView(actionButton("✨  View What's New • " + BuildInfo.VERSION_TAG, v ->
                ReleaseNotes.showCustom(this, prefs, true)));
        card.addView(actionButton("Release & Build Details", v -> showBuildDetails()));
        card.addView(actionButton("✉  Contact Support", v -> openSupportEmail()));
        return card;
    }

    private void showBuildDetails() {
        String details = "Harley's Clan Forum Android app\n\n"
                + "Version: " + BuildInfo.VERSION + "\n"
                + "Version code: " + BuildInfo.VERSION_CODE + "\n"
                + "Channel: " + BuildInfo.CHANNEL + "\n"
                + "Package: " + getPackageName() + "\n"
                + "APK: " + BuildInfo.APK_FILE_NAME + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL;
        new AlertDialog.Builder(this)
                .setTitle("Release & Build Details")
                .setMessage(details)
                .setPositiveButton("Close", null)
                .show();
    }

    private void refreshStatusLabels() {
        if (liveSyncStatus != null) {
            String status = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "Waiting for first sync");
            long at = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            long latency = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, 0L);
            String relative = formatAge(System.currentTimeMillis() - at);
            String age = at <= 0L ? "not synced yet" : ("just now".equals(relative) ? relative : relative + " ago");
            liveSyncStatus.setText("Live sync: " + status + " • " + age
                    + (latency > 0L ? " • " + latency + " ms" : "")
                    + "\n" + PerformanceProfile.settingLabel(this, prefs)
                    + " • " + RuntimeDiagnostics.notificationMode()
                    + " • poll " + formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs())
                    + " • " + RuntimeState.networkType(this));
        }
        if (notificationStatus != null) {
            NotificationHelper.createChannel(this);
            boolean allowed = NotificationHelper.canPost(this);
            boolean headsUp = NotificationHelper.headsUpChannelReady(this);
            String state = NotificationHelper.status(this);
            if (Build.VERSION.SDK_INT >= 26) {
                state += " • channel importance=" + NotificationHelper.channelImportance(this);
            }
            notificationStatus.setText("Status: " + state);
            notificationStatus.setTextColor(allowed && headsUp ? getColor(R.color.hcf_accent_text) : getColor(R.color.hcf_yellow));
        }
        if (dndStatus != null) {
            dndStatus.setText("Do Not Disturb: " + AppSettings.dndLabel(this));
            dndStatus.setTextColor(AppSettings.isDndActive(this)
                    ? getColor(R.color.hcf_yellow) : getColor(R.color.hcf_meta));
        }
        if (cookieStatus != null) {
            cookieStatus.setText(cookieSummary());
        }
        if (securityStatus != null) {
            securityStatus.setText(AppSecurity.securitySummary(this));
        }
        if (telemetryStatus != null) {
            telemetryStatus.setText(TelemetryService.status(this));
        }
        if (updateChannelStatus != null) {
            updateChannelStatus.setText(updateChannelLine(effectiveUpdateChannel()));
        }
        if (updateInstallButton != null) {
            updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        }
        if (serverStatus != null) {
            String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
            boolean primary = ForumConfig.PRIMARY_HOST.equalsIgnoreCase(host);
            serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
            serverStatus.setTextColor(primary ? getColor(R.color.hcf_cyan) : getColor(R.color.hcf_yellow));
        }
    }

    private String cookieSummary() {
        CookieManager cm = CookieManager.getInstance();
        int primary = countCookies(cm.getCookie("https://" + ForumConfig.PRIMARY_HOST + "/"));
        int backup = countCookies(cm.getCookie("https://" + ForumConfig.BACKUP_HOST + "/"));
        return "Cookie data: " + (primary + backup) + " visible • Primary " + primary + " • Backup " + backup;
    }

    private String formatRuntimeInterval(long ms) {
        if (ms <= 0L) return "idle";
        if (ms < 1000L) return ms + "ms";
        if (ms % 1000L == 0L) return (ms / 1000L) + "s";
        return String.format(Locale.US, "%.2fs", ms / 1000.0d);
    }

    private String formatAge(long elapsedMs) {
        long seconds = Math.max(0L, elapsedMs / 1000L);
        if (seconds < 2L) return "just now";
        if (seconds < 60L) return seconds + " seconds";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + (minutes == 1L ? " minute" : " minutes");
        long hours = minutes / 60L;
        return hours + (hours == 1L ? " hour" : " hours");
    }

    private int countCookies(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        int count = 0;
        for (String part : raw.split(";")) if (!part.trim().isEmpty()) count++;
        return count;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(compact() ? 8 : 12);
        card.setLayoutParams(p);
        return card;
    }

    private View sectionTitle(String titleValue, String subtitleValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(8));
        TextView title = text(titleValue, 16, getColor(R.color.hcf_accent_text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        TextView subtitle = text(subtitleValue, 11, getColor(R.color.hcf_muted));
        box.addView(subtitle);
        return box;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private void showPerformanceProfileDialog(Button target) {
        final String[] ids = {
                PerformanceProfile.AUTO,
                PerformanceProfile.PERFORMANCE,
                PerformanceProfile.BALANCED,
                PerformanceProfile.QUALITY
        };
        final String[] labels = {
                "Auto — Recommended",
                "Performance — Minimal motion",
                "Balanced — Short smooth motion",
                "High Performance — Full visual effects"
        };
        String current = PerformanceProfile.saved(prefs);
        int checked = 0;
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(current)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Performance Profile")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < 0 || which >= ids.length) return;
                    PerformanceProfile.save(prefs, ids[which]);
                    String visible = PerformanceProfile.settingLabel(this, prefs);
                    target.setText("Performance Profile: " + visible);
                    target.setContentDescription("Performance profile " + visible);
                    AppLogger.info(this, "setting_performance_profile", ids[which] + " -> " + PerformanceProfile.resolve(this, prefs));
                    Toast.makeText(this, visible + " • " + PerformanceProfile.detail(this, prefs), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Switch toggle(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(getColor(R.color.hcf_text));
        s.setTextSize(14);
        s.setChecked(checked);
        s.setPadding(0, dp(7), 0, dp(7));
        return s;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(cleanIconPrefix(label));
        b.setTextColor(getColor(R.color.hcf_accent_text));
        b.setBackgroundResource(R.drawable.quick_action_background);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(14), 0, dp(14), 0);
        FaIcons.applyStart(b, label);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 44 : 52));
        p.topMargin = dp(7);
        b.setLayoutParams(p);
        return b;
    }

    private ImageButton chromeButton(String label) {
        return UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, compact() ? 9 : 11,
                label == null || label.trim().isEmpty() ? "Back" : label);
    }

    private String cleanIconPrefix(String label) {
        if (label == null) return "";
        return label.replaceFirst("^[^A-Za-z0-9]+", "").trim();
    }

    private void openForumLinkSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            AppLogger.info(this, "forum_link_settings", "open-by-default");
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(fallback);
            } catch (Throwable second) {
                Toast.makeText(this, "Android link settings are unavailable on this device.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void openNotificationSettings() {
        NotificationHelper.createChannel(this);
        NotificationHelper.openChannelSettings(this);
        AppLogger.info(this, "notification_settings", NotificationHelper.status(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            AppLogger.info(this, "notification_permission", granted ? "granted" : "denied | " + NotificationHelper.status(this));
            Toast.makeText(this, granted ? "Notifications allowed. Sending a heads-up test…" : "Notifications not allowed.", Toast.LENGTH_SHORT).show();
            NotificationSyncScheduler.apply(this);
            refreshStatusLabels();
            if (granted) {
                NotificationHelper.post(this,
                        "Harley's Clan Forum",
                        "Heads-up notifications are enabled. This is a test alert.",
                        ForumUrlRouter.home(ForumConfig.PRIMARY_HOST));
            }
        }
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
