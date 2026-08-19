package com.harleytg.forum.dev;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * App Settings V2.
 *
 * This screen keeps all existing preference keys and app services, but presents
 * common controls first, shows current values in plain language, and keeps
 * advanced / destructive controls out of the everyday path.
 */
public final class SettingsActivityV2 extends ThemedActivity {
    private static final int REQUEST_NOTIFICATIONS = 901;
    private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 2410;
    private static final String STATE_SECTION = "settings_v2_section";
    private static final String STATE_SCROLL_Y = "settings_v2_scroll_y";

    private SharedPreferences prefs;
    private ScrollView scroll;
    private LinearLayout content;
    private ImageButton backButton;
    private TextView titleView;
    private TextView subtitleView;
    private String section = "";

    private TextView homeStatus;
    private TextView notificationStatus;
    private TextView dndStatus;
    private TextView serverStatus;
    private TextView cookieStatus;
    private TextView securityStatus;
    private TextView telemetryStatus;
    private TextView updateStatus;
    private TextView updateChannelStatus;
    private Button updateDownloadButton;
    private Button updateInstallButton;
    private UpdateChecker.Release availableRelease;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        setContentView(buildUi());
        restoreLocation(savedInstanceState);
        handleInstallIntent(getIntent());
        AppLogger.info(this, "settings_v2_open", BuildInfo.VERSION + " | section=" + section);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SECTION, section == null ? "" : section);
        if (scroll != null) outState.putInt(STATE_SCROLL_Y, scroll.getScrollY());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        resumeUpdateInstallAfterPermission();
    }

    @Override
    public void onBackPressed() {
        if (section != null && !section.isEmpty()) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void restoreLocation(Bundle state) {
        if (state == null) return;
        String savedSection = state.getString(STATE_SECTION, "");
        int y = state.getInt(STATE_SCROLL_Y, 0);
        if (savedSection != null && !savedSection.trim().isEmpty()) showSection(savedSection.trim());
        if (scroll != null && y > 0) scroll.post(() -> scroll.scrollTo(0, y));
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        page.addView(buildHeader());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact() ? 10 : 14), dp(10), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showHome();

        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(compact() ? 2 : 5), dp(8), dp(compact() ? 2 : 5));
        bar.setMinimumHeight(dp(compact() ? 48 : 58));
        bar.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));

        backButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left,
                R.drawable.chrome_button_background, compact() ? 9 : 11, "Back");
        backButton.setOnClickListener(v -> onBackPressed());
        bar.addView(backButton, new LinearLayout.LayoutParams(dp(compact() ? 40 : 46), dp(compact() ? 40 : 46)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        lp.leftMargin = dp(5);
        bar.addView(logo, lp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsP.leftMargin = dp(10);
        bar.addView(labels, labelsP);

        titleView = text("App Settings", 18, getColor(R.color.hcf_text));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(titleView);
        subtitleView = text("Harley's Clan Forum • " + BuildInfo.VERSION_TAG, 10, getColor(R.color.hcf_meta));
        labels.addView(subtitleView);
        return bar;
    }

    private void showHome() {
        section = "";
        setHeader("App Settings", "Choose what you want to change");
        content.removeAllViews();

        content.addView(statusCard());
        content.addView(subheading("Quick settings"));
        content.addView(quickActionsCard());

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search settings");
        search.setHintTextColor(getColor(R.color.hcf_hint));
        search.setTextColor(getColor(R.color.hcf_text));
        search.setTextSize(14f);
        search.setBackgroundResource(R.drawable.quick_action_background);
        search.setPadding(dp(15), 0, dp(15), 0);
        LinearLayout.LayoutParams searchP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 44 : 50));
        searchP.topMargin = dp(4);
        searchP.bottomMargin = dp(12);
        content.addView(search, searchP);

        content.addView(subheading("All settings"));
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        content.addView(categories);

        addCategory(categories, "Account", accountSummary(), "account",
                "account identity profile username login security");
        addCategory(categories, "Notifications", notificationSummary(), "notifications",
                "notifications alerts dnd history direct message reply background");
        addCategory(categories, "Appearance", appearanceSummary(), "appearance",
                "appearance theme auto light dark amoled performance url header startup");
        addCategory(categories, "Connection", connectionSummary(), "connection",
                "connection primary backup server failover external links browser");
        addCategory(categories, "Privacy & Site Data", cookieSummary(), "privacy",
                "privacy cookies data webview clear sign out storage");
        addCategory(categories, "Permissions & Security", "Android permissions and update security", "security",
                "permissions notification installer updates security certificate");
        addCategory(categories, "App Updates", updateChannelLine(effectiveUpdateChannel()), "updates",
                "updates apk check download install beta version release");
        addCategory(categories, "Advanced", "Diagnostics, logs and optional telemetry", "advanced",
                "advanced diagnostics logs telemetry crash errors recovery");
        addCategory(categories, "About", BuildInfo.DEVELOPMENT_BUILD_LABEL, "about",
                "about version build release notes support what's new");

        TextView empty = text("No matching settings found.", 12, getColor(R.color.hcf_muted));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(18), 0, dp(18));
        empty.setVisibility(View.GONE);
        content.addView(empty);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories(categories, empty, s == null ? "" : s.toString());
            }
        });

        TextView footer = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 9, getColor(R.color.hcf_hint));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(14), 0, dp(4));
        content.addView(footer);

        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
        refreshStatus();
    }

    private View statusCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Status", "Important app state at a glance"));
        homeStatus = text("Checking app status…", 12, getColor(R.color.hcf_meta));
        homeStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(homeStatus);
        card.addView(statusLine("Account", accountSummary()));
        card.addView(statusLine("Theme", ThemeManager.label(this)));
        card.addView(statusLine("Notifications", notificationSummary()));
        card.addView(statusLine("Server", connectionSummary()));
        card.addView(actionButton("Sync now", v -> syncNow()));
        return card;
    }

    private View quickActionsCard() {
        LinearLayout card = card();
        card.addView(actionButton("Appearance & theme", v -> showSection("appearance")));
        card.addView(actionButton("Notification Center", v -> startActivity(new Intent(this, NotificationHistoryActivity.class))));
        card.addView(actionButton("Check for updates", v -> {
            showSection("updates");
            checkForUpdates(true);
        }));
        return card;
    }

    private void showSection(String key) {
        section = key == null ? "" : key;
        content.removeAllViews();
        setHeader(sectionName(section), sectionSubtitle(section));

        if ("account".equals(section)) content.addView(accountCard());
        else if ("notifications".equals(section)) content.addView(notificationsCard());
        else if ("appearance".equals(section)) content.addView(appearanceCard());
        else if ("connection".equals(section)) content.addView(connectionCard());
        else if ("privacy".equals(section)) content.addView(privacyCard());
        else if ("security".equals(section)) content.addView(securityCard());
        else if ("updates".equals(section)) content.addView(updatesCard());
        else if ("advanced".equals(section)) {
            content.addView(diagnosticsCard());
            content.addView(telemetryCard());
        } else if ("about".equals(section)) content.addView(aboutCard());
        else {
            showHome();
            return;
        }

        content.addView(actionButton("Back to all settings", v -> showHome()));
        if (scroll != null) scroll.post(() -> scroll.scrollTo(0, 0));
        refreshStatus();
    }

    private View accountCard() {
        LinearLayout card = card();
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        card.addView(sectionTitle("Account", "Your forum identity and account security"));
        card.addView(statusLine("Signed in", identity.loggedIn ? "Yes" : "No"));
        card.addView(statusLine("Profile", identity.loggedIn ? identity.identityLabel() : "Guest"));
        if (identity.loggedIn && identity.username != null && !identity.username.isEmpty()) {
            card.addView(statusLine("Username", "@" + identity.username));
        }
        card.addView(actionButton("Open Account & Identity", v -> startActivity(new Intent(this, IdentityActivity.class))));
        return card;
    }

    private View notificationsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Notifications", "Alerts, history and Do Not Disturb"));

        notificationStatus = text("Checking notification status…", 11, getColor(R.color.hcf_meta));
        notificationStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(notificationStatus);

        card.addView(actionButton("Open Notification Center & History", v ->
                startActivity(new Intent(this, NotificationHistoryActivity.class))));

        dndStatus = text("Do Not Disturb: " + AppSettings.dndLabel(this), 11, getColor(R.color.hcf_meta));
        dndStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(dndStatus);

        card.addView(subheading("Do Not Disturb"));
        card.addView(selectionButton("Off", AppSettings.DND_OFF.equals(AppSettings.dndMode(this)), v -> setDnd(AppSettings.DND_OFF)));
        card.addView(selectionButton("On", AppSettings.DND_ON.equals(AppSettings.dndMode(this)), v -> setDnd(AppSettings.DND_ON)));
        card.addView(selectionButton("Scheduled", AppSettings.DND_SCHEDULED.equals(AppSettings.dndMode(this)), v -> {
            AppSettings.setDndMode(this, AppSettings.DND_SCHEDULED);
            refreshStatus();
            startActivity(new Intent(this, NotificationHistoryActivity.class));
        }));

        Switch allow = toggle("Allow forum notifications", prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true));
        allow.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.NOTIFICATIONS_ENABLED, checked).apply();
            NotificationSyncScheduler.apply(this);
            if (checked) requestNotificationPermissionIfNeeded();
            toast(checked ? "Forum notifications enabled." : "Forum notifications disabled.");
            refreshStatus();
        });
        card.addView(allow);

        Switch background = toggle("Background notification sync", prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true));
        background.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, checked).apply();
            NotificationSyncScheduler.apply(this);
            toast(checked ? "Background sync enabled." : "Background sync disabled.");
        });
        card.addView(background);

        card.addView(actionButton("Send test notification", v -> {
            String host = safeHost();
            NotificationHelper.post(this, "Harley's Clan Forum", "Test notification from App Settings", "https://" + host + "/notifications");
        }));
        card.addView(actionButton("Android notification settings", v -> {
            NotificationHelper.createChannel(this);
            NotificationHelper.openChannelSettings(this);
        }));
        return card;
    }

    private View appearanceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Appearance", "Theme, performance and app-shell options"));

        card.addView(subheading("App theme"));
        card.addView(selectionButton("Auto • Follow phone", ThemeManager.SYSTEM.equals(ThemeManager.mode(this)), v -> selectTheme(ThemeManager.SYSTEM)));
        card.addView(selectionButton("Light • Day", ThemeManager.LIGHT.equals(ThemeManager.mode(this)), v -> selectTheme(ThemeManager.LIGHT)));
        card.addView(selectionButton("Dark • Night", ThemeManager.DARK.equals(ThemeManager.mode(this)), v -> selectTheme(ThemeManager.DARK)));
        card.addView(selectionButton("AMOLED • Black", ThemeManager.AMOLED.equals(ThemeManager.mode(this)), v -> selectTheme(ThemeManager.AMOLED)));

        Button performance = actionButton("Performance • " + PerformanceProfile.settingLabel(this, prefs), null);
        performance.setOnClickListener(v -> showPerformanceDialog(performance));
        card.addView(performance);

        Switch live = toggle("Live forum updates", prefs.getBoolean(AppPrefs.LIVE_FORUM_UPDATES, true));
        live.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.LIVE_FORUM_UPDATES, checked).apply();
            toast(checked ? "Live forum updates enabled." : "Live forum updates disabled.");
        });
        card.addView(live);

        Switch url = toggle("Show secure URL bar", prefs.getBoolean(AppPrefs.SHOW_URL_BAR, false));
        url.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.SHOW_URL_BAR, checked).apply();
            toast(checked ? "Secure URL bar will be shown." : "Secure URL bar hidden.");
        });
        card.addView(url);

        Switch startup = toggle("Show startup connection screen", prefs.getBoolean(AppPrefs.SHOW_STARTUP_SCREEN, true));
        startup.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.SHOW_STARTUP_SCREEN, checked).apply();
            toast(checked ? "Startup connection screen enabled." : "Startup screen disabled. ErrorSys still remains available for connection errors.");
        });
        card.addView(startup);

        card.addView(actionButton("Reset appearance defaults", v -> confirmResetAppearance()));
        return card;
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Connection", "Primary and backup forum routing"));
        serverStatus = text("Server: " + connectionSummary(), 12, getColor(R.color.hcf_meta));
        serverStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(serverStatus);

        Switch failover = toggle("Use backup if primary is unavailable", prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true));
        failover.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.AUTO_FAILOVER, checked).apply();
            toast(checked ? "Automatic backup routing enabled." : "Automatic backup routing disabled.");
        });
        card.addView(failover);

        Switch external = toggle("Open external links in browser/apps", prefs.getBoolean(AppPrefs.EXTERNAL_LINKS, true));
        external.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.EXTERNAL_LINKS, checked).apply();
            toast(checked ? "External links allowed." : "External links disabled.");
        });
        card.addView(external);

        card.addView(actionButton("Prefer primary forum again", v -> {
            prefs.edit().remove(AppPrefs.FALLBACK_UNTIL).putString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST).apply();
            toast("Primary forum restored as preferred server.");
            refreshStatus();
        }));
        card.addView(actionButton("Android forum link settings", v -> openForumLinkSettings()));
        return card;
    }

    private View privacyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Privacy & Site Data", "Forum data stored on this phone"));
        cookieStatus = text(cookieSummary(), 11, getColor(R.color.hcf_meta));
        cookieStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(cookieStatus);
        card.addView(actionButton("Open Cookie Manager", v -> startActivity(new Intent(this, CookieManagerActivity.class))));
        card.addView(actionButton("Clear site data & sign out", v -> confirmClearSiteData()));
        return card;
    }

    private View securityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Permissions & Security", "Android permissions and update-install security"));
        securityStatus = text(AppSecurity.securitySummary(this), 11, getColor(R.color.hcf_meta));
        card.addView(securityStatus);
        card.addView(actionButton("Allow notification permission", v -> requestNotificationPermissionIfNeeded()));
        card.addView(actionButton("Allow secure app updates", v -> openInstallPermission()));
        card.addView(actionButton("Android app permission settings", v -> openAndroidAppSettings()));
        card.addView(text("HCF does not request location, contacts, microphone, camera, or broad storage access. Update APKs must match the expected package and signing certificate.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    private View updatesCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("App Updates", "Check, download and install verified builds"));

        updateChannelStatus = text(updateChannelLine(effectiveUpdateChannel()), 12, getColor(R.color.hcf_meta));
        updateChannelStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(updateChannelStatus);

        updateStatus = text("Installed • " + BuildInfo.VERSION_TAG, 11, getColor(R.color.hcf_muted));
        card.addView(updateStatus);

        Switch autoCheck = toggle("Automatic update checks", prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true));
        autoCheck.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_CHECK, checked).apply();
            UpdateScheduler.apply(this);
            toast(checked ? "Automatic update checks enabled." : "Automatic update checks disabled.");
        });
        card.addView(autoCheck);

        Switch autoDownload = toggle("Automatically download new APKs", prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, true));
        autoDownload.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, checked).apply();
            toast(checked ? "Automatic APK downloads enabled." : "Automatic APK downloads disabled.");
        });
        card.addView(autoDownload);

        Switch autoInstall = toggle("Open installer after download", prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true));
        autoInstall.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_INSTALL, checked).apply();
            toast(checked ? "Installer will open after verification." : "Installer will wait for you to open it.");
        });
        card.addView(autoInstall);

        card.addView(actionButton("Check for updates now", v -> checkForUpdates(true)));

        updateDownloadButton = actionButton("Download available update", v -> downloadAvailableUpdate());
        updateDownloadButton.setVisibility(View.GONE);
        card.addView(updateDownloadButton);

        updateInstallButton = actionButton("Install downloaded update", v -> installDownloadedUpdate());
        updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        card.addView(updateInstallButton);
        return card;
    }

    private View diagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Diagnostics & Recovery", "Useful tools when the app or forum is not behaving normally"));
        card.addView(actionButton("Run connection & recovery check", v -> showRecoveryDiagnostics()));
        card.addView(actionButton("Copy sanitized diagnostic report", v -> copyDiagnosticReport()));
        card.addView(actionButton("View app & crash logs", v -> startActivity(new Intent(this, LogsActivity.class))));
        card.addView(actionButton("Clear WebView cache", v -> clearWebViewCache()));
        return card;
    }

    private View telemetryCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Telemetry", "Optional app-health and diagnostic reporting"));
        telemetryStatus = text(TelemetryService.status(this), 11, getColor(R.color.hcf_meta));
        telemetryStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(telemetryStatus);

        Switch enabled = toggle("Enable telemetry services", prefs.getBoolean(AppPrefs.TELEMETRY_ENABLED, false));
        enabled.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(AppPrefs.TELEMETRY_ENABLED, checked).apply();
            if (checked) TelemetryService.sendEvent(this, "telemetry_enabled", "Enabled from App Settings V2");
            toast(checked ? "Telemetry enabled." : "Telemetry disabled.");
            refreshStatus();
        });
        card.addView(enabled);

        card.addView(actionButton("Telemetry level • " + TelemetryService.levelLabel(this), v -> cycleTelemetryLevel()));
        card.addView(actionButton("Preview telemetry report", v -> TelemetryService.showPreview(this)));
        card.addView(actionButton("View report history", v -> TelemetryService.showHistory(this)));
        return card;
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("About", "Version, release notes and support"));
        TextView app = text("Harley's Clan Forum [Beta]", 18, getColor(R.color.hcf_text));
        app.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(app);
        card.addView(statusLine("Version", BuildInfo.VERSION));
        card.addView(statusLine("Version code", String.valueOf(BuildInfo.VERSION_CODE)));
        card.addView(statusLine("Package", getPackageName()));
        card.addView(actionButton("What's New", v -> ReleaseNotes.showCustom(this, prefs, true)));
        card.addView(actionButton("Release & build details", v -> showBuildDetails()));
        card.addView(actionButton("Contact Support", v -> startActivity(new Intent(this, SupportContactActivity.class))));
        return card;
    }

    private void addCategory(LinearLayout parent, String title, String subtitle, String key, String keywords) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(15), dp(compact() ? 9 : 12), dp(12), dp(compact() ? 9 : 12));
        row.setTag((title + " " + subtitle + " " + keywords).toLowerCase(Locale.US));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 14, getColor(R.color.hcf_text));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(t);
        labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = text("›", 24, getColor(R.color.hcf_cyan_bright));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT));
        row.setOnClickListener(v -> showSection(key));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(7);
        parent.addView(row, p);
    }

    private void filterCategories(LinearLayout parent, TextView empty, String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        int visible = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String data = child.getTag() == null ? "" : String.valueOf(child.getTag());
            boolean match = query.isEmpty() || data.contains(query);
            child.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) visible++;
        }
        empty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private void setHeader(String title, String subtitle) {
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);
        if (backButton != null) backButton.setContentDescription(section == null || section.isEmpty() ? "Back to forum" : "Back to all settings");
    }

    private String sectionName(String key) {
        if ("account".equals(key)) return "Account";
        if ("notifications".equals(key)) return "Notifications";
        if ("appearance".equals(key)) return "Appearance";
        if ("connection".equals(key)) return "Connection";
        if ("privacy".equals(key)) return "Privacy & Site Data";
        if ("security".equals(key)) return "Permissions & Security";
        if ("updates".equals(key)) return "App Updates";
        if ("advanced".equals(key)) return "Advanced";
        if ("about".equals(key)) return "About";
        return "App Settings";
    }

    private String sectionSubtitle(String key) {
        if ("appearance".equals(key)) return ThemeManager.label(this);
        if ("notifications".equals(key)) return AppSettings.dndLabel(this);
        if ("connection".equals(key)) return connectionSummary();
        if ("updates".equals(key)) return BuildInfo.VERSION_TAG;
        return BuildInfo.DEVELOPMENT_BUILD_LABEL;
    }

    private void selectTheme(String mode) {
        if (mode == null || mode.equals(ThemeManager.mode(this))) return;
        prefs.edit().putString(AppPrefs.APP_THEME, mode).apply();
        AppLogger.info(this, "setting_theme", mode + " | settings_v2 | keep_section=" + section);
        recreate();
    }

    private void setDnd(String mode) {
        AppSettings.setDndMode(this, mode);
        toast("Do Not Disturb: " + AppSettings.dndLabel(this));
        showSection("notifications");
    }

    private void showPerformanceDialog(Button target) {
        final String[] ids = {PerformanceProfile.AUTO, PerformanceProfile.PERFORMANCE, PerformanceProfile.BALANCED, PerformanceProfile.QUALITY};
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
                    target.setText("Performance • " + PerformanceProfile.settingLabel(this, prefs));
                    toast(PerformanceProfile.settingLabel(this, prefs) + " selected.");
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmResetAppearance() {
        new AlertDialog.Builder(this)
                .setTitle("Reset appearance?")
                .setMessage("This resets only app theme/interface preferences. Your account, notifications, updates and forum data are not changed.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    prefs.edit()
                            .putString(AppPrefs.APP_THEME, ThemeManager.SYSTEM)
                            .putString(AppPrefs.PERFORMANCE_PROFILE, PerformanceProfile.AUTO)
                            .putBoolean(AppPrefs.SHOW_URL_BAR, false)
                            .putBoolean(AppPrefs.LIVE_FORUM_UPDATES, true)
                            .putBoolean(AppPrefs.SHOW_STARTUP_SCREEN, true)
                            .apply();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearSiteData() {
        new AlertDialog.Builder(this)
                .setTitle("Clear forum data & sign out?")
                .setMessage("This removes forum cookies and WebView site data from this phone and signs the app out. This cannot be undone from App Settings.")
                .setPositiveButton("Clear & sign out", (dialog, which) -> clearSiteData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearSiteData() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(value -> cm.flush());
        WebStorage.getInstance().deleteAllData();
        ForumIdentity.clear(this);
        toast("Forum site data cleared and local identity signed out.");
        refreshStatus();
    }

    private void syncNow() {
        String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        if (userId == null || userId.trim().isEmpty()) {
            toast("Sign in to the forum first.");
            return;
        }
        InstantNotificationService.requestImmediateSync(this);
        if (homeStatus != null) homeStatus.setText("Syncing now…");
        getWindow().getDecorView().postDelayed(this::refreshStatus, 1400L);
    }

    private void refreshStatus() {
        if (homeStatus != null) {
            String sync = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "Waiting for first sync");
            long at = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            homeStatus.setText("Forum sync: " + sync + (at > 0L ? " • " + formatAge(System.currentTimeMillis() - at) : ""));
        }
        if (notificationStatus != null) {
            NotificationHelper.createChannel(this);
            notificationStatus.setText("Status: " + NotificationHelper.status(this));
            notificationStatus.setTextColor(NotificationHelper.canPost(this) ? getColor(R.color.hcf_accent_text) : getColor(R.color.hcf_yellow));
        }
        if (dndStatus != null) {
            dndStatus.setText("Do Not Disturb: " + AppSettings.dndLabel(this));
            dndStatus.setTextColor(AppSettings.isDndActive(this) ? getColor(R.color.hcf_yellow) : getColor(R.color.hcf_meta));
        }
        if (serverStatus != null) serverStatus.setText("Server: " + connectionSummary());
        if (cookieStatus != null) cookieStatus.setText(cookieSummary());
        if (securityStatus != null) securityStatus.setText(AppSecurity.securitySummary(this));
        if (telemetryStatus != null) telemetryStatus.setText(TelemetryService.status(this));
        if (updateChannelStatus != null) updateChannelStatus.setText(updateChannelLine(effectiveUpdateChannel()));
        if (updateInstallButton != null) updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
    }

    private String accountSummary() {
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        return identity.loggedIn ? identity.identityLabel() : "Guest";
    }

    private String notificationSummary() {
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)) return "Off";
        return AppSettings.dndLabel(this);
    }

    private String appearanceSummary() {
        return ThemeManager.label(this) + " • " + PerformanceProfile.settingLabel(this, prefs);
    }

    private String connectionSummary() {
        String host = safeHost();
        return ForumConfig.PRIMARY_HOST.equalsIgnoreCase(host) ? "Primary • " + host : "Backup • " + host;
    }

    private String safeHost() {
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        return ForumUrlRouter.isForumHost(host) ? host : ForumConfig.PRIMARY_HOST;
    }

    private String cookieSummary() {
        CookieManager cm = CookieManager.getInstance();
        int primary = countCookies(cm.getCookie("https://" + ForumConfig.PRIMARY_HOST + "/"));
        int backup = countCookies(cm.getCookie("https://" + ForumConfig.BACKUP_HOST + "/"));
        return "Cookies • " + (primary + backup) + " visible";
    }

    private int countCookies(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        int count = 0;
        for (String part : raw.split(";")) if (!part.trim().isEmpty()) count++;
        return count;
    }

    private String effectiveUpdateChannel() {
        if (!BuildInfo.ALLOW_UPDATE_CHANNEL_SWITCH) return UpdateChecker.CHANNEL_DEV;
        String channel = prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL);
        return UpdateChecker.CHANNEL_STABLE.equalsIgnoreCase(channel) ? UpdateChecker.CHANNEL_STABLE : UpdateChecker.CHANNEL_DEV;
    }

    private String updateChannelLine(String channel) {
        return "Channel • " + (UpdateChecker.CHANNEL_STABLE.equalsIgnoreCase(channel)
                ? "Stable / Official" : "Development / Beta");
    }

    private void checkForUpdates(boolean userRequested) {
        if (updateStatus == null) return;
        final String channel = effectiveUpdateChannel();
        updateStatus.setText("Checking for updates…");
        updateDownloadButton.setVisibility(View.GONE);
        availableRelease = null;

        UpdateChecker.check(this, channel, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                availableRelease = release;
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                if (updateAvailable) {
                    updateStatus.setText("Update available • " + UpdateChecker.displayVersion(release)
                            + (release.versionCode > 0 ? " • build " + release.versionCode : ""));
                    updateStatus.setTextColor(getColor(R.color.hcf_accent_text));
                    if (release.apkUrl != null && !release.apkUrl.isEmpty()) updateDownloadButton.setVisibility(View.VISIBLE);
                    if (userRequested) toast("Update available.");
                } else {
                    updateStatus.setText("Up to date • " + BuildInfo.VERSION_TAG);
                    updateStatus.setTextColor(getColor(R.color.hcf_meta));
                    if (userRequested) toast("This build is up to date.");
                }
            }

            @Override
            public void onError(String message) {
                updateStatus.setText("Update check unavailable • " + message);
                updateStatus.setTextColor(getColor(R.color.hcf_yellow));
                if (userRequested) toast(message);
            }
        });
    }

    private void downloadAvailableUpdate() {
        if (availableRelease == null || availableRelease.apkUrl == null || availableRelease.apkUrl.isEmpty()) {
            toast("No downloadable update is ready.");
            return;
        }
        long id = AppUpdateDownloader.enqueue(this, availableRelease, true);
        if (id > 0L) {
            updateStatus.setText("Downloading update… Android will verify it before installation.");
            toast("Update download started.");
        } else {
            toast("Update download could not start.");
        }
    }

    private void installDownloadedUpdate() {
        long id = AppUpdateDownloader.downloadedId(this);
        if (id <= 0L) {
            toast("No downloaded update is ready.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this)) {
            prefs.edit().putBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, true).apply();
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            } catch (Throwable t) {
                prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                toast("Android could not open install permission settings.");
            }
            return;
        }
        if (!AppUpdateDownloader.openInstaller(this, id)) toast("Android installer could not open the downloaded update.");
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null || !"com.harleytg.forum.dev.INSTALL_UPDATE".equals(intent.getAction())) return;
        long id = intent.getLongExtra("download_id", -1L);
        if (id > 0L) getWindow().getDecorView().postDelayed(this::installDownloadedUpdate, 250L);
    }

    private void resumeUpdateInstallAfterPermission() {
        if (!prefs.getBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, false)) return;
        if (Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this)) return;
        prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
        installDownloadedUpdate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(this)) resumeUpdateInstallAfterPermission();
            else {
                prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                toast("Install permission was not enabled. The downloaded APK was kept.");
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            toast("Notification permission is already available.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            toast(granted ? "Notifications allowed." : "Notifications not allowed.");
            NotificationSyncScheduler.apply(this);
            refreshStatus();
        }
    }

    private void openInstallPermission() {
        if (Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(this)) {
            toast("Secure update installation is already allowed.");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            toast("Android could not open install permission settings.");
        }
    }

    private void openAndroidAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            toast("Android app settings are unavailable.");
        }
    }

    private void openForumLinkSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Throwable first) {
            openAndroidAppSettings();
        }
    }

    private void showRecoveryDiagnostics() {
        String host = safeHost();
        boolean network = isNetworkAvailable();
        String webViewProvider = "Unknown";
        try {
            android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info != null) webViewProvider = info.packageName + " • " + info.versionName;
        } catch (Throwable ignored) {}

        new AlertDialog.Builder(this)
                .setTitle("Connection & Recovery Check")
                .setMessage("Network: " + (network ? "Connected" : "Offline")
                        + "\nServer: " + host
                        + "\nAutomatic failover: " + (prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "On" : "Off")
                        + "\nWebView: " + webViewProvider
                        + "\nRenderer recovery: Enabled"
                        + "\nSSL validation: Fail-closed")
                .setPositiveButton("OK", null)
                .setNeutralButton("View logs", (dialog, which) -> startActivity(new Intent(this, LogsActivity.class)))
                .show();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void clearWebViewCache() {
        try {
            WebView temp = new WebView(this);
            temp.clearCache(true);
            temp.clearHistory();
            temp.destroy();
            toast("WebView cache cleared.");
        } catch (Throwable t) {
            toast("WebView cache could not be cleared on this device.");
        }
    }

    private void copyDiagnosticReport() {
        String report = "Harley's Clan Forum • Sanitized Diagnostic Report\n"
                + "App: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n"
                + "Package: " + getPackageName() + "\n"
                + "Android SDK: " + Build.VERSION.SDK_INT + "\n"
                + "Theme: " + ThemeManager.label(this) + "\n"
                + "Performance: " + PerformanceProfile.settingLabel(this, prefs) + "\n"
                + "Notifications: " + NotificationHelper.status(this) + "\n"
                + "DND: " + AppSettings.dndLabel(this) + "\n"
                + "Server: " + safeHost() + "\n"
                + "Passwords/cookies/tokens/email: Not included";
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("HCF diagnostic report", report));
            toast("Sanitized diagnostic report copied.");
        } catch (Throwable t) {
            toast("Could not copy diagnostic report.");
        }
    }

    private void cycleTelemetryLevel() {
        String next = TelemetryService.LEVEL_DIAGNOSTICS.equals(TelemetryService.level(this))
                ? TelemetryService.LEVEL_BASIC : TelemetryService.LEVEL_DIAGNOSTICS;
        prefs.edit().putString(AppPrefs.TELEMETRY_LEVEL, next).apply();
        showSection("advanced");
    }

    private void showBuildDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Release & Build Details")
                .setMessage("Version: " + BuildInfo.VERSION
                        + "\nVersion code: " + BuildInfo.VERSION_CODE
                        + "\nChannel: " + BuildInfo.CHANNEL
                        + "\nPackage: " + getPackageName()
                        + "\nAPK: " + BuildInfo.APK_FILE_NAME
                        + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL)
                .setPositiveButton("Close", null)
                .show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(compact() ? 9 : 12);
        card.setLayoutParams(p);
        return card;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, dp(8));
        TextView t = text(title, 17, getColor(R.color.hcf_accent_text));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(t);
        box.addView(text(subtitle, 11, getColor(R.color.hcf_muted)));
        return box;
    }

    private TextView subheading(String value) {
        TextView t = text(value, 10, getColor(R.color.hcf_cyan));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(dp(4), dp(4), 0, dp(7));
        return t;
    }

    private TextView statusLine(String label, String value) {
        TextView t = text(label + "  •  " + (value == null || value.isEmpty() ? "—" : value), 11, getColor(R.color.hcf_text));
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
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

    private Switch toggle(String label, boolean checked) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(getColor(R.color.hcf_text));
        s.setTextSize(14);
        s.setChecked(checked);
        s.setPadding(0, dp(8), 0, dp(8));
        return s;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label);
        b.setTextColor(getColor(R.color.hcf_accent_text));
        b.setBackgroundResource(R.drawable.quick_action_background);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 44 : 50));
        p.topMargin = dp(7);
        b.setLayoutParams(p);
        return b;
    }

    private Button selectionButton(String label, boolean selected, View.OnClickListener listener) {
        Button b = actionButton((selected ? "✓  " : "") + label, listener);
        b.setTextColor(getColor(selected ? R.color.hcf_cyan_bright : R.color.hcf_text));
        if (selected) b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setContentDescription(label + (selected ? ", selected" : ", select"));
        return b;
    }

    private String formatAge(long elapsedMs) {
        long seconds = Math.max(0L, elapsedMs / 1000L);
        if (seconds < 5L) return "just now";
        if (seconds < 60L) return seconds + "s ago";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + "m ago";
        return (minutes / 60L) + "h ago";
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
