package com.harleytg.forum;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CompoundButton;
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

/* loaded from: classes.dex */
public final class SettingsActivity extends ThemedActivity {
    private static final int REQUEST_NOTIFICATIONS = 901;
    private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 2410;
    private UpdateChecker.Release availableRelease;
    private TextView cookieStatus;
    private ImageButton headerBackButton;
    private TextView headerSubtitleView;
    private TextView headerTitleView;
    private TextView liveSyncStatus;
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
    private boolean openDeveloperToolsOnAdvanced = false;

    @Override
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        SharedPreferences sharedPreferences = getSharedPreferences(AppPrefs.FILE, 0);
        this.prefs = sharedPreferences;
        if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(sharedPreferences.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
            this.prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
            AppLogger.info(this, "update_channel_lock", BuildInfo.DEFAULT_UPDATE_CHANNEL);
        }
        if (!sharedPreferences.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)) {
            this.prefs.edit().putBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true).apply();
        }
        try {
            int navColor = -16777216;
            getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            Window window = getWindow();
            if (!ThemeManager.isAmoled(this)) navColor = getColor(R.color.hcf_bg);
            window.setNavigationBarColor(navColor);
            setContentView(buildUi());
            handleInstallIntent(getIntent());
            AppLogger.info(this, "settings_open", BuildInfo.VERSION);
        } catch (Throwable th) {
            AppLogger.crash(this, th);
            showSettingsRecovery(th);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(this.prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
                this.prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
            }
            refreshStatusLabels();
            resumeUpdateInstallAfterPermission();
        } catch (Throwable th) {
            AppLogger.error(this, "settings_resume", th.getClass().getSimpleName());
        }
    }

    private void showSettingsRecovery(Throwable th) {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(17);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));
            root.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            TextView title = text("App Control Center • Recovery Mode", 18, getColor(R.color.hcf_accent_text));
            title.setGravity(17);
            title.setTypeface(null, 1);
            root.addView(title);
            TextView detail = text(BuildInfo.VERSION + " • Build " + BuildInfo.VERSION_CODE + " • Stable\n\nSettings UI recovered from: " + th.getClass().getSimpleName(), 13, getColor(R.color.hcf_text));
            detail.setGravity(17);
            root.addView(detail);
            Button back = new Button(this);
            UiButtons.normalizeText(back);
            back.setText("Back to Forum");
            back.setAllCaps(false);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) { finish(); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
            params.topMargin = dp(18);
            root.addView(back, params);
            setContentView(root);
        } catch (Throwable ignored) {
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        root.addView(buildHeader());
        ScrollView scroll = new ScrollView(this);
        this.settingsScroll = scroll;
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        this.settingsContent = content;
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        showSettingsHome();
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return root;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(8), dp(compact() ? 2 : 5), dp(8), dp(compact() ? 2 : 5));
        row.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        row.setMinimumHeight(dp(compact() ? 46 : 56));
        ImageButton back = chromeButton("Back");
        this.headerBackButton = back;
        back.setContentDescription("Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentSettingsSection == null || currentSettingsSection.isEmpty()) finish();
                else showSettingsHome();
            }
        });
        row.addView(back, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        logoParams.leftMargin = dp(4);
        row.addView(logo, logoParams);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        titleParams.leftMargin = dp(10);
        row.addView(titles, titleParams);
        TextView title = text("App Settings", 18, getColor(R.color.hcf_text));
        this.headerTitleView = title;
        title.setTypeface(null, 1);
        titles.addView(title);
        TextView subtitle = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 10, getColor(R.color.hcf_meta));
        this.headerSubtitleView = subtitle;
        subtitle.setTypeface(null, 1);
        titles.addView(subtitle);
        return row;
    }

    private void showSettingsHome() {
        this.currentSettingsSection = "";
        updateSettingsHeader("App Settings", BuildInfo.DEVELOPMENT_BUILD_LABEL);
        if (this.settingsContent == null) return;
        this.settingsContent.removeAllViews();

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search settings…");
        search.setHintTextColor(getColor(R.color.hcf_hint));
        search.setTextColor(getColor(R.color.hcf_text));
        search.setTextSize(14.0f);
        search.setBackgroundResource(R.drawable.quick_action_background);
        search.setPadding(dp(15), 0, dp(15), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 42 : 48));
        searchParams.bottomMargin = dp(10);
        this.settingsContent.addView(search, searchParams);

        TextView section = text("Settings", 10, getColor(R.color.hcf_cyan));
        section.setTypeface(null, 1);
        section.setPadding(dp(4), dp(2), 0, dp(7));
        this.settingsContent.addView(section);

        final LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        this.settingsContent.addView(categories, new LinearLayout.LayoutParams(-1, -2));
        addSettingsCategory(categories, "Account & Security", "Forum identity, profile and account controls", "account_security", "profile username login identity account security password email two factor sessions");
        addSettingsCategory(categories, "Notifications", "Required alerts, silent background alerts and stable diagnostics", "notifications", "alerts notification history background silent diagnostics test delivery");
        addSettingsCategory(categories, "Appearance & Performance", "Theme, interface density and performance", "appearance", "theme forum auto phone auto dark light performance header url live updates");
        addSettingsCategory(categories, "Forum & Site Data", "Server routing, links, cookies and local site data", "forum_data", "server primary backup failover links cookies data cache privacy sign out");
        addSettingsCategory(categories, "Advanced & About", "App permissions, Stable updates, recovery, diagnostics and build information", "advanced", "permissions security android stable update apk diagnostics logs telemetry recovery version about build");

        final TextView noMatch = text("No matching settings.", 12, getColor(R.color.hcf_muted));
        noMatch.setGravity(17);
        noMatch.setPadding(0, dp(18), 0, dp(18));
        noMatch.setVisibility(View.GONE);
        this.settingsContent.addView(noMatch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable editable) {}
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSettingsCategories(categories, noMatch, s == null ? "" : s.toString());
            }
        });

        TextView footer = text(BuildInfo.DEVELOPMENT_BUILD_LABEL, 9, getColor(R.color.hcf_hint));
        footer.setGravity(17);
        footer.setPadding(0, dp(14), 0, dp(4));
        this.settingsContent.addView(footer);
        if (this.settingsScroll != null) {
            this.settingsScroll.post(new Runnable() {
                @Override public void run() { settingsScroll.scrollTo(0, 0); }
            });
        }
        refreshStatusLabels();
    }

    private void showSettingsSection(String key) {
        if (key == null) key = "";
        this.currentSettingsSection = key;
        if (this.settingsContent == null) return;
        this.settingsContent.removeAllViews();
        updateSettingsHeader(settingsSectionName(key), settingsSectionSubtitle(key));

        if ("forum_data".equals(key)) {
            this.settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), true));
            this.settingsContent.addView(connectedSettingsPanel("Cookies & Site Data", "Forum data stored locally on this device", privacyCard(), false));
        } else if ("advanced".equals(key)) {
            this.settingsContent.addView(connectedSettingsPanel("Permissions & Security", "Android permissions and app hardening", securityCard(), false));
            this.settingsContent.addView(connectedSettingsPanel("App Updates", "Stable official release checks, download and install controls", updateCard(), false));
            this.settingsContent.addView(connectedSettingsPanel("Error & Recovery", "Diagnostics, logs and WebView recovery tools", diagnosticsCard(), false));
            this.settingsContent.addView(connectedSettingsPanel("Telemetry", "Optional app health and diagnostic reporting", telemetryCard(), false));
            if (devToolsEnabled()) {
                this.settingsContent.addView(connectedSettingsPanel("Developer Tools", "Internal Stable diagnostics", developerToolsCard(), this.openDeveloperToolsOnAdvanced));
            }
            this.openDeveloperToolsOnAdvanced = false;
            this.settingsContent.addView(connectedSettingsPanel("About Harley's Clan Forum", "Version, release notes and Stable build information", aboutCard(), false));
        } else if ("notifications".equals(key)) {
            this.settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Required main alerts • messages, mentions, replies and important activity", mainAlertsCard(), true));
            this.settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Background sync, service status and passive notifications", silentAlertsCard(), false));
            this.settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", "Stable diagnostics channel • isolated from normal HCF Alerts", testAlertsInfoCard(), false));
        } else if ("account_security".equals(key)) {
            this.settingsContent.addView(connectedSettingsPanel("Account & Identity", "Signed-in profile and forum identity controls", accountIdentityCard(), true));
            this.settingsContent.addView(connectedSettingsPanel("Account Controls", "Profile, password, email and session security shortcuts", accountControlsCard(), false));
        } else if ("appearance".equals(key)) {
            this.settingsContent.addView(connectedSettingsPanel("Appearance & Performance", "Theme, interface and rendering preferences", interfaceCard(), true));
        } else {
            showSettingsHome();
            return;
        }

        this.settingsContent.addView(actionButton("‹  Back to all settings", new View.OnClickListener() {
            @Override public void onClick(View view) { showSettingsHome(); }
        }));
        if (this.settingsScroll != null) {
            this.settingsScroll.post(new Runnable() {
                @Override public void run() { settingsScroll.scrollTo(0, 0); }
            });
        }
        refreshStatusLabels();
    }

    private void addSettingsCategory(LinearLayout parent, String title, String subtitle, String key, String keywords) {
        View row = settingsCategoryRow(title, subtitle, key);
        row.setTag((title + " " + subtitle + " " + keywords).toLowerCase(Locale.US));
        parent.addView(row);
    }

    private View settingsCategoryRow(String title, String subtitle, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(15), dp(compact() ? 8 : 11), dp(12), dp(compact() ? 8 : 11));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(7);
        row.setLayoutParams(rowParams);
        ImageView icon = settingsSectionIcon(settingsIconForKey(key));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.rightMargin = dp(12);
        row.addView(icon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView chevron = text("›", 24, getColor(R.color.hcf_cyan_bright));
        chevron.setGravity(17);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(30), -1));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showSettingsSection(key); }
        });
        return row;
    }

    public void filterSettingsCategories(LinearLayout parent, TextView noMatch, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        int visible = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            boolean match = needle.isEmpty() || (child.getTag() == null ? "" : String.valueOf(child.getTag())).contains(needle);
            child.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) visible++;
        }
        if (noMatch != null) noMatch.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateSettingsHeader(String title, String subtitle) {
        if (this.headerTitleView != null) this.headerTitleView.setText(title == null ? "App Settings" : title);
        if (this.headerSubtitleView != null) this.headerSubtitleView.setText(subtitle == null ? BuildInfo.DEVELOPMENT_BUILD_LABEL : subtitle);
        if (this.headerBackButton != null) {
            this.headerBackButton.setContentDescription((this.currentSettingsSection == null || this.currentSettingsSection.isEmpty()) ? "Back to forum" : "Back to all settings");
        }
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

    private String channelDisplay() {
        return BuildInfo.CHANNEL;
    }

    private String channelDisplayName(String ignored) {
        return BuildInfo.CHANNEL;
    }

    private View statusDashboardCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Live Status", "Forum and notification service at a glance"));
        TextView status = text("Live sync: checking…", 12, getColor(R.color.hcf_meta));
        this.liveSyncStatus = status;
        status.setTypeface(null, 1);
        card.addView(status);
        card.addView(actionButton("Sync Forum & Notifications Now", new View.OnClickListener() {
            @Override public void onClick(View view) {
                String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
                if (userId == null || userId.trim().isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "Sign in to the forum first, then sync again.", Toast.LENGTH_LONG).show();
                    return;
                }
                InstantNotificationService.requestImmediateSync(SettingsActivity.this);
                if (liveSyncStatus != null) liveSyncStatus.setText("Live sync: syncing now…");
                Toast.makeText(SettingsActivity.this, "Forum sync requested.", Toast.LENGTH_SHORT).show();
                getWindow().getDecorView().postDelayed(new Runnable() {
                    @Override public void run() { refreshStatusLabels(); }
                }, 1600L);
            }
        }));
        return card;
    }

    private View accountIdentityCard() {
        LinearLayout card = card();
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        card.addView(sectionTitle("Account & Identity", "Your signed-in forum profile and account security"));
        String headline = identity.loggedIn ? "Signed in as " + identity.identityLabel() : "Forum session: Guest_Protocol";
        TextView signedIn = text(headline, 13, getColor(identity.loggedIn ? R.color.hcf_accent_text : R.color.hcf_meta));
        signedIn.setTypeface(null, 1);
        card.addView(signedIn);
        String detail;
        if (identity.loggedIn) detail = identity.username.isEmpty() ? "Identity sync active" : "@" + identity.username + " • Identity sync active";
        else detail = "Sign in to connect your identity";
        card.addView(text(detail, 11, getColor(R.color.hcf_muted)));
        card.addView(actionButton("Open Account & Identity", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, IdentityActivity.class)); }
        }));
        return card;
    }

    private View accountControlsCard() {
        LinearLayout card = card();
        final ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        ForumSecurity.Snapshot security = ForumSecurity.load(this);
        if (!identity.loggedIn) {
            TextView info = text("Sign in to manage your forum account controls.", 12, getColor(R.color.hcf_muted));
            info.setPadding(dp(2), dp(2), dp(2), dp(6));
            card.addView(info);
            card.addView(actionButton("Open Forum Sign In", new View.OnClickListener() {
                @Override public void onClick(View view) { openAccountForumPath("/login"); }
            }));
            return card;
        }
        String identityLabel = identity.username == null || identity.username.trim().isEmpty() ? identity.identityLabel() : "@" + identity.username.trim();
        TextView signedIn = text("Signed in as " + identityLabel, 12, getColor(R.color.hcf_accent_text));
        signedIn.setTypeface(null, 1);
        card.addView(signedIn);
        StringBuilder available = new StringBuilder();
        if (security.seen) {
            if (security.passwordControls) available.append("Password");
            if (security.emailControls) appendDot(available, "Email");
            if (security.twoFactorControls) appendDot(available, "Two-factor");
            if (security.sessionCount > 0) appendDot(available, "Sessions: " + security.sessionCount);
        }
        if (available.length() == 0) available.append(security.seen ? "Security controls are managed by the forum" : "Open Account Security once to sync available controls");
        TextView availableView = text("Available: " + available, 10, getColor(R.color.hcf_muted));
        availableView.setPadding(dp(2), dp(2), dp(2), dp(5));
        card.addView(availableView);
        card.addView(actionButton("Open My Forum Profile", new View.OnClickListener() {
            @Override public void onClick(View view) {
                String handle = accountHandle(identity);
                if (handle.isEmpty()) Toast.makeText(SettingsActivity.this, "Unable to determine your forum profile route.", Toast.LENGTH_SHORT).show();
                else openAccountForumPath("/u/" + Uri.encode(handle));
            }
        }));
        card.addView(actionButton("Open Account Security", new View.OnClickListener() {
            @Override public void onClick(View view) {
                String handle = accountHandle(identity);
                if (handle.isEmpty()) Toast.makeText(SettingsActivity.this, "Unable to determine your forum profile route.", Toast.LENGTH_SHORT).show();
                else openAccountForumPath("/u/" + Uri.encode(handle) + "/security");
            }
        }));
        TextView note = text("Password, email, two-factor and session changes remain on Harley's Clan Forum. The app only exposes safe shortcuts and synced capability/status information.", 10, getColor(R.color.hcf_hint));
        note.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(note);
        return card;
    }

    private void appendDot(StringBuilder builder, String value) {
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
        intent.addFlags(603979776);
        startActivity(intent);
        finish();
    }

    private View mainAlertsCard() {
        LinearLayout card = card();
        if (!this.prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)) this.prefs.edit().putBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true).apply();
        NotificationHelper.createChannel(this);
        TextView status = text("Checking HCF Alerts status…", 12, getColor(R.color.hcf_muted));
        this.notificationStatus = status;
        card.addView(status);
        LinearLayout required = new LinearLayout(this);
        required.setOrientation(LinearLayout.VERTICAL);
        required.setPadding(dp(14), dp(12), dp(14), dp(12));
        required.setBackgroundResource(R.drawable.quick_action_background);
        TextView title = text("Required in Harley's Clan Forum", 14, getColor(R.color.hcf_accent_text));
        title.setTypeface(null, 1);
        required.addView(title);
        required.addView(text("HCF does not provide an Off or Silent control for this channel. It carries direct messages, mentions, replies and important forum/app alerts.", 10, getColor(R.color.hcf_muted)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        card.addView(required, params);
        card.addView(notificationChannelStatusRow("HCF Alerts", "Required • audible • heads-up capable • not affected by HCF silence controls", "hcf_alerts_v1"));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            card.addView(actionButton("Allow Android Notification Permission", new View.OnClickListener() {
                @Override public void onClick(View view) { requestNotificationPermissionIfNeeded(); }
            }));
        }
        TextView note = text("Android is the final owner of notification permission and channel controls. If Android has blocked or changed HCF Alerts, HCF will show that state here, but the app itself never turns this channel off or silent.", 10, getColor(R.color.hcf_muted));
        note.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(note);
        return card;
    }

    private View silentAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        Switch sync = toggle("Background notification sync", this.prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true));
        sync.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, checked).apply();
                NotificationSyncScheduler.apply(SettingsActivity.this);
                AppLogger.info(SettingsActivity.this, "setting_background_sync", Boolean.toString(checked));
            }
        });
        card.addView(sync);
        Switch silence = toggle("Silence HCF Silent Alerts", this.prefs.getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false));
        silence.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, checked).apply();
                NotificationHelper.refreshChannels(SettingsActivity.this);
                NotificationSyncScheduler.apply(SettingsActivity.this);
                AppLogger.info(SettingsActivity.this, "setting_silence_passive_notifications", Boolean.toString(checked));
                Toast.makeText(SettingsActivity.this, "Updated • Passive notification silence", Toast.LENGTH_LONG).show();
            }
        });
        card.addView(silence);
        card.addView(notificationChannelRow("HCF Silent Alerts", "Silent/background channel • hidden when Silence HCF Silent Alerts is enabled", "hcf_silent_alerts_v1"));
        TextView note = text("Silence passive notifications keeps live background sync active while routing service status, generic summaries and diagnostics/status alerts through silent low-priority behavior. Messages, mentions and replies remain normal alerts.", 10, getColor(R.color.hcf_muted));
        note.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(note);
        return card;
    }

    private View testAlertsInfoCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(notificationChannelRow("HCF Test Alerts", "Stable diagnostics notifications • isolated from real HCF Alerts", "hcf_test_alerts_v1"));
        TextView note = text("This channel is reserved for HCF notification diagnostics and test delivery. It never carries normal forum messages, mentions, replies or background-service status.", 10, getColor(R.color.hcf_muted));
        note.setPadding(dp(2), dp(8), dp(2), dp(4));
        card.addView(note);
        if (devToolsEnabled()) {
            card.addView(actionButton("Open Developer Tools", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    openDeveloperToolsOnAdvanced = true;
                    showSettingsSection("advanced");
                }
            }));
        }
        return card;
    }

    private View notificationToolsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Android notification settings", "System permission and channel controls"));
        card.addView(actionButton("All Android Notification Settings", new View.OnClickListener() {
            @Override public void onClick(View view) { openNotificationSettings(); }
        }));
        return card;
    }

    private View themeModeSelector() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(2));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setWeightSum(2.0f);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setWeightSum(2.0f);
        String mode = ThemeManager.mode(this);
        row1.addView(themeChoiceButton("Forum Auto", "auto_forum", "auto_forum".equals(mode)), themeChoiceParams(false));
        row1.addView(themeChoiceButton("Phone Auto", "auto_phone", "auto_phone".equals(mode)), themeChoiceParams(true));
        boolean dark = "dark".equals(mode) || "amoled".equals(mode);
        row2.addView(themeChoiceButton("Light", "light", "light".equals(mode)), themeChoiceParams(false));
        row2.addView(themeChoiceButton("Dark", "dark", dark), themeChoiceParams(true));
        root.addView(row1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        root.addView(row2, params);
        return root;
    }

    private LinearLayout.LayoutParams themeChoiceParams(boolean right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(compact() ? 46 : 52), 1.0f);
        if (right) params.leftMargin = dp(8);
        return params;
    }

    private Button themeChoiceButton(String label, final String value, boolean selected) {
        final Button button = new Button(this);
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
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { selectThemeMode(value, button); }
        });
        return button;
    }

    private void selectThemeMode(final String value, final Button button) {
        String mode = ThemeManager.mode(this);
        if (value.equals(mode) || ("dark".equals(value) && "dark".equals(mode))) return;
        button.animate().scaleX(0.97f).scaleY(0.97f).setDuration(65L).withEndAction(new Runnable() {
            @Override public void run() {
                prefs.edit().putString(AppPrefs.APP_THEME, value).apply();
                AppLogger.info(SettingsActivity.this, "setting_theme", value);
                button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90L).start();
            }
        }).start();
    }

    private View interfaceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Appearance & Interface", "A simpler app shell with more room for the forum"));
        TextView theme = text("Theme", 13, getColor(R.color.hcf_text));
        theme.setTypeface(null, 1);
        theme.setPadding(0, dp(4), 0, dp(2));
        card.addView(theme);
        card.addView(themeModeSelector());
        card.addView(text("Forum Auto is the default. It follows your FoF Night Mode account/per-device setting; when the forum itself uses Auto, the phone theme decides. Phone Auto follows Android directly.", 10, getColor(R.color.hcf_muted)));
        final Button performance = actionButton("Performance Profile: " + PerformanceProfile.settingLabel(this, this.prefs), null);
        performance.setContentDescription("Choose app performance profile");
        performance.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showPerformanceProfileDialog(performance); }
        });
        card.addView(performance);
        card.addView(text("Auto is the default adaptive engine. Capable devices can promote to Auto • Real-Time; HCF automatically drops to Balanced, Performance, or Extreme Saver when network, memory, battery, thermal, or renderer conditions require it.", 10, getColor(R.color.hcf_muted)));
        Switch live = toggle("Live forum updates", this.prefs.getBoolean(AppPrefs.LIVE_FORUM_UPDATES, true));
        live.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.LIVE_FORUM_UPDATES, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_live_updates", Boolean.toString(checked));
                Toast.makeText(SettingsActivity.this, checked ? "Live forum updates enabled." : "Live forum updates disabled.", Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(live);
        Switch url = toggle("Show secure URL bar", this.prefs.getBoolean(AppPrefs.SHOW_URL_BAR, true));
        url.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.SHOW_URL_BAR, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_url_bar", Boolean.toString(checked));
            }
        });
        card.addView(url);
        TextView header = text("Header: Classic compact • bottom app bar removed", 11, getColor(R.color.hcf_cyan));
        header.setTypeface(null, 1);
        card.addView(header);
        card.addView(text("The extra LIVE badge, duplicate header alert button, and native bottom navigation bar stay removed. Forum alerts remain available inside the forum and app drawer.", 10, getColor(R.color.hcf_muted)));
        Switch startup = toggle("Show startup connection screen", this.prefs.getBoolean(AppPrefs.SHOW_STARTUP_SCREEN, true));
        startup.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.SHOW_STARTUP_SCREEN, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_startup_screen", Boolean.toString(checked));
            }
        });
        card.addView(startup);
        card.addView(text("Live updates check for new forum activity while the app is open and refresh safely when you are not typing.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Connection", "Primary and backup forum routing"));
        TextView status = text("Current server: checking…", 12, getColor(R.color.hcf_cyan));
        this.serverStatus = status;
        card.addView(status);
        Switch failover = toggle("Automatically use backup if primary fails", this.prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true));
        failover.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.AUTO_FAILOVER, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_auto_failover", Boolean.toString(checked));
            }
        });
        card.addView(failover);
        Switch external = toggle("Allow external links to open in browser/apps", this.prefs.getBoolean(AppPrefs.EXTERNAL_LINKS, true));
        external.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.EXTERNAL_LINKS, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_external_links", Boolean.toString(checked));
            }
        });
        card.addView(external);
        card.addView(actionButton("Retry Primary Forum on Next Open", new View.OnClickListener() {
            @Override public void onClick(View view) {
                prefs.edit().remove(AppPrefs.FALLBACK_UNTIL).putString(AppPrefs.ACTIVE_HOST, "forum.harleytg.com").apply();
                refreshStatusLabels();
                Toast.makeText(SettingsActivity.this, "Primary forum restored as preferred server.", Toast.LENGTH_SHORT).show();
                AppLogger.info(SettingsActivity.this, "fallback_reset", "settings");
            }
        }));
        TextView links = text("Forum links: primary + backup registered with Android", 10, getColor(R.color.hcf_muted));
        links.setPadding(0, dp(10), 0, 0);
        card.addView(links);
        card.addView(actionButton("Open Forum Link Settings", new View.OnClickListener() {
            @Override public void onClick(View view) { openForumLinkSettings(); }
        }));
        return card;
    }

    private View privacyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Cookies & Site Data", "Forum data stored on this phone"));
        TextView status = text(cookieSummary(), 12, getColor(R.color.hcf_meta));
        this.cookieStatus = status;
        status.setTypeface(null, 1);
        card.addView(status);
        card.addView(actionButton("Open Cookie Manager", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, CookieManagerActivity.class)); }
        }));
        card.addView(actionButton("Clear Forum Site Data & Sign Out", new View.OnClickListener() {
            @Override public void onClick(View view) {
                final CookieManager manager = CookieManager.getInstance();
                manager.removeAllCookies(new ValueCallback<Boolean>() {
                    @Override public void onReceiveValue(Boolean value) {
                        manager.flush();
                        if (cookieStatus != null) cookieStatus.setText(cookieSummary());
                    }
                });
                WebStorage.getInstance().deleteAllData();
                ForumIdentity.clear(SettingsActivity.this);
                AppLogger.info(SettingsActivity.this, "forum_site_data_cleared", "settings");
                Toast.makeText(SettingsActivity.this, "Forum cookies and site data cleared.", Toast.LENGTH_LONG).show();
            }
        }));
        return card;
    }

    private View telemetryCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Telemetry & Diagnostics", "Crash reports, app health and privacy controls"));
        TextView status = text(TelemetryService.status(this), 11, getColor(R.color.hcf_meta));
        this.telemetryStatus = status;
        status.setTypeface(null, 1);
        card.addView(status);
        Switch enabled = toggle("Enable Telemetry Services", this.prefs.getBoolean(AppPrefs.TELEMETRY_ENABLED, false));
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.TELEMETRY_ENABLED, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_telemetry", Boolean.toString(checked));
                if (checked) {
                    TelemetryService.sendEvent(SettingsActivity.this, "telemetry_enabled", "User enabled Telemetry Services in App Settings");
                    Toast.makeText(SettingsActivity.this, "Telemetry Services enabled.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, "Telemetry disabled. No reports will be sent.", Toast.LENGTH_SHORT).show();
                }
                refreshStatusLabels();
            }
        });
        card.addView(enabled);
        final Button level = actionButton("Telemetry level: " + TelemetryService.levelLabel(this), null);
        level.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String value = "diagnostics".equals(TelemetryService.level(SettingsActivity.this)) ? "basic" : "diagnostics";
                prefs.edit().putString(AppPrefs.TELEMETRY_LEVEL, value).apply();
                level.setText("Telemetry level: " + TelemetryService.levelLabel(SettingsActivity.this));
                AppLogger.info(SettingsActivity.this, "setting_telemetry_level", value);
                refreshStatusLabels();
            }
        });
        card.addView(level);
        card.addView(text("Basic: coarse app health only. Diagnostics: adds crashes, sanitized stack traces, recent app events, and optional WebView/update errors.", 10, getColor(R.color.hcf_muted)));
        card.addView(telemetryToggle("Automatically send crash reports", AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS, false, "setting_auto_crash_reports"));
        card.addView(telemetryToggle("Ask me before every crash report", AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT, true, "setting_ask_crash_report"));
        card.addView(telemetryToggle("Automatically send WebView/update errors", AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS, false, "setting_auto_error_reports"));
        TextView privacy = text("Report Privacy", 10, getColor(R.color.hcf_cyan));
        privacy.setTypeface(null, 1);
        privacy.setPadding(0, dp(8), 0, 0);
        card.addView(privacy);
        card.addView(telemetryToggle("Include my forum identity with reports", AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false, "setting_telemetry_identity"));
        card.addView(telemetryToggle("Include my email when identity is included", AppPrefs.TELEMETRY_INCLUDE_EMAIL, false, "setting_telemetry_email"));
        card.addView(telemetryToggle("Include device manufacturer/model", AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL, false, "setting_telemetry_device"));
        card.addView(telemetryToggle("Include sanitized forum route", AppPrefs.TELEMETRY_INCLUDE_ROUTE, false, "setting_telemetry_route"));
        card.addView(actionButton("Send Diagnostic Feedback", new View.OnClickListener() {
            @Override public void onClick(View view) { TelemetryService.showManualFeedbackDialog(SettingsActivity.this); }
        }));
        card.addView(actionButton("Preview Telemetry Report", new View.OnClickListener() {
            @Override public void onClick(View view) { TelemetryService.showPreview(SettingsActivity.this); }
        }));
        card.addView(actionButton("View Report History", new View.OnClickListener() {
            @Override public void onClick(View view) { TelemetryService.showHistory(SettingsActivity.this); }
        }));
        card.addView(actionButton("Clear Local Telemetry Reports", new View.OnClickListener() {
            @Override public void onClick(View view) {
                TelemetryService.clearLocalReports(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Local telemetry reports and breadcrumbs cleared.", Toast.LENGTH_SHORT).show();
                refreshStatusLabels();
            }
        }));
        TextView note = text("Crash reports get an HCF report ID and can include a sanitized stack trace plus recent app events. Identity, email, device model and page route are separate opt-ins. Passwords, cookies, access/session tokens, recovery codes, provider IDs, posts, messages and page contents are never sent. The Discord endpoint remains encrypted at rest in the APK, but a server-side relay is still safer for long-term webhook secrecy.", 10, getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private Switch telemetryToggle(String label, final String key, boolean defaultValue, final String logKey) {
        Switch toggle = toggle(label, this.prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                AppLogger.info(SettingsActivity.this, logKey, Boolean.toString(checked));
            }
        });
        return toggle;
    }

    private View securityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Permissions & Security", "Android permissions and app hardening"));
        TextView status = text(AppSecurity.securitySummary(this), 11, getColor(R.color.hcf_meta));
        this.securityStatus = status;
        card.addView(status);
        card.addView(actionButton("Allow Notification Permission", new View.OnClickListener() {
            @Override public void onClick(View view) { requestNotificationPermissionIfNeeded(); }
        }));
        card.addView(actionButton("Allow Secure App Updates", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (AppSecurity.canInstallUpdates(SettingsActivity.this)) {
                    Toast.makeText(SettingsActivity.this, "Secure app update installation is already allowed.", Toast.LENGTH_SHORT).show();
                    refreshStatusLabels();
                    return;
                }
                try {
                    startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())));
                } catch (Throwable ignored) {
                    Toast.makeText(SettingsActivity.this, "Android could not open the update install permission screen.", Toast.LENGTH_LONG).show();
                }
            }
        }));
        card.addView(actionButton("Android App Permission Settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
                } catch (Throwable ignored) {
                    Toast.makeText(SettingsActivity.this, "Android app settings could not be opened.", Toast.LENGTH_LONG).show();
                }
            }
        }));
        TextView note = text("No location, contacts, microphone, camera, or broad storage permission is requested. Stable update APKs are accepted only from HCF's trusted release source and must match the installed package and signing certificate.", 10, getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private View diagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Error & Recovery Center", "Troubleshooting and smart-recovery tools"));
        card.addView(text("HCF error codes are enabled • network, server, SSL, WebView and Stable update failures use friendly native explanations.", 10, getColor(R.color.hcf_muted)));
        card.addView(actionButton("Run Error & Recovery Check", new View.OnClickListener() {
            @Override public void onClick(View view) { showRecoveryDiagnostics(); }
        }));
        card.addView(actionButton("Copy Sanitized Diagnostic Report", new View.OnClickListener() {
            @Override public void onClick(View view) { copyDiagnosticReport(); }
        }));
        card.addView(actionButton("View App & Crash Logs", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, LogsActivity.class)); }
        }));
        card.addView(actionButton("Clear WebView Cache", new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    WebView webView = new WebView(SettingsActivity.this);
                    webView.clearCache(true);
                    webView.clearHistory();
                    webView.destroy();
                    AppLogger.info(SettingsActivity.this, "webview_cache_cleared", "settings");
                    Toast.makeText(SettingsActivity.this, "WebView cache cleared.", Toast.LENGTH_SHORT).show();
                } catch (Throwable th) {
                    AppLogger.error(SettingsActivity.this, "webview_cache_clear", th.getClass().getSimpleName());
                    Toast.makeText(SettingsActivity.this, "WebView cache could not be cleared on this device.", Toast.LENGTH_SHORT).show();
                }
            }
        }));
        return card;
    }

    private void postTestAlert(String title, String body, String path) {
        String active = this.prefs.getString(AppPrefs.ACTIVE_HOST, "forum.harleytg.com");
        String host = ForumUrlRouter.isForumHost(active) ? active : "forum.harleytg.com";
        String route = path == null ? "/notifications" : path;
        NotificationHelper.postTest(this, title, body, "https://" + host + route);
        AppLogger.info(this, "notification_test", title);
    }

    private void showRecoveryDiagnostics() {
        String active = this.prefs.getString(AppPrefs.ACTIVE_HOST, "forum.harleytg.com");
        String host = ForumUrlRouter.isForumHost(active) ? active : "forum.harleytg.com";
        boolean connected = isValidatedNetworkAvailable();
        String webView = "Unknown";
        try {
            PackageInfo current = WebView.getCurrentWebViewPackage();
            if (current != null) webView = current.packageName + " • " + current.versionName;
        } catch (Throwable ignored) {}
        String last = this.prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, "");
        String safeUrl = last == null || last.trim().isEmpty() ? "Not recorded yet" : AppLogger.safeUrl(last);
        String message = "Network: " + (connected ? "✓ Connected" : "✕ Offline")
                + "\nCurrent server: " + host
                + "\nAutomatic failover: " + (this.prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "✓ Enabled" : "Disabled")
                + "\nWebView provider: " + webView
                + "\nRenderer recovery: ✓ Enabled (HCF-WV-001)"
                + "\nSSL fail-closed: ✓ Enabled (HCF-SSL-001)"
                + "\nUpdate channel: Stable"
                + "\nLast recoverable route: " + safeUrl;
        new AlertDialog.Builder(this)
                .setTitle("Error & Recovery Check")
                .setMessage(message)
                .setPositiveButton("OK", (DialogInterface.OnClickListener) null)
                .setNeutralButton("View logs", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { startActivity(new Intent(SettingsActivity.this, LogsActivity.class)); }
                }).show();
        AppLogger.info(this, "recovery_diagnostics", connected ? "network-ok" : "offline");
    }

    private boolean isValidatedNetworkAvailable() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService("connectivity");
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(12);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copyDiagnosticReport() {
        String active = this.prefs.getString(AppPrefs.ACTIVE_HOST, "forum.harleytg.com");
        String sync = this.prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "not synced yet");
        long latency = this.prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, 0L);
        String report = "Harley's Clan Forum • Sanitized Diagnostic Report"
                + "\nApp: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")"
                + "\nChannel: Stable"
                + "\nUpdate feed: stable"
                + "\nPackage: " + getPackageName()
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nForum host: " + active
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance profile: " + PerformanceProfile.settingLabel(this, this.prefs)
                + "\nNotifications: " + NotificationHelper.status(this)
                + "\nLive sync: " + sync + (latency > 0 ? " • " + latency + " ms" : "")
                + "\nAuto failover: " + (this.prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "On" : "Off")
                + "\nRenderer recovery: Enabled (HCF-WV-001)"
                + "\nLast route: " + AppLogger.safeUrl(this.prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, ""))
                + "\nCookies/tokens/passwords/email: Not included";
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService("clipboard");
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("HCF diagnostic report", report));
            Toast.makeText(this, "Sanitized diagnostic report copied.", Toast.LENGTH_SHORT).show();
            AppLogger.info(this, "diagnostic_report_copy", "sanitized");
        } catch (Throwable ignored) {
            Toast.makeText(this, "Could not copy the diagnostic report.", Toast.LENGTH_SHORT).show();
        }
    }

    private View updateCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("App Updates", "Secure Stable official app updates"));
        TextView channel = text(updateChannelLine(effectiveUpdateChannel()), 12, getColor(R.color.hcf_meta));
        this.updateChannelStatus = channel;
        channel.setTypeface(null, 1);
        card.addView(channel);
        long lastCheck = this.prefs.getLong(AppPrefs.UPDATE_LAST_CHECK, 0L);
        String checked;
        if (lastCheck <= 0L) checked = "Not checked yet on this install";
        else {
            String age = formatAge(System.currentTimeMillis() - lastCheck);
            checked = "Last checked " + ("just now".equals(age) ? age : age + " ago");
        }
        TextView status = text("Installed • v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n" + checked, 11, getColor(R.color.hcf_muted));
        this.updateStatus = status;
        card.addView(status);
        Switch checks = toggle("Automatic update checks", this.prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true));
        checks.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_CHECK, checked).putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
                UpdateScheduler.apply(SettingsActivity.this);
                AppLogger.info(SettingsActivity.this, "setting_update_auto_check", Boolean.toString(checked));
            }
        });
        card.addView(checks);
        Switch downloads = toggle("Automatically download new Stable APKs", this.prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, false));
        downloads.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, checked).putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
                AppLogger.info(SettingsActivity.this, "setting_update_auto_download", Boolean.toString(checked));
            }
        });
        card.addView(downloads);
        Switch installs = toggle("Open installer automatically after download", this.prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true));
        installs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(AppPrefs.UPDATE_AUTO_INSTALL, checked).apply();
                AppLogger.info(SettingsActivity.this, "setting_update_auto_install", Boolean.toString(checked));
            }
        });
        card.addView(installs);
        card.addView(actionButton("Check Stable for Updates", new View.OnClickListener() {
            @Override public void onClick(View view) { checkForUpdates(true); }
        }));
        Button download = actionButton("Download Stable Update Now", new View.OnClickListener() {
            @Override public void onClick(View view) {
                UpdateChecker.Release release = availableRelease;
                if (release != null && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                    long id = AppUpdateDownloader.enqueue(SettingsActivity.this, release, true);
                    if (id > 0L) {
                        Toast.makeText(SettingsActivity.this, "Stable update download started.", Toast.LENGTH_SHORT).show();
                        if (updateStatus != null) updateStatus.setText("Downloading Stable build" + (release.versionCode > 0 ? " " + release.versionCode : "") + "… The Android installer will open automatically when verification finishes.");
                        watchUpdateDownloadForAutoInstall(id);
                        return;
                    }
                    Toast.makeText(SettingsActivity.this, "Stable update download could not start.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(SettingsActivity.this, "This Stable release does not have a valid Stable APK asset yet.", Toast.LENGTH_LONG).show();
            }
        });
        this.updateDownloadButton = download;
        download.setVisibility(View.GONE);
        card.addView(download);
        Button install = actionButton("Install Downloaded Stable Update", new View.OnClickListener() {
            @Override public void onClick(View view) { installDownloadedUpdate(); }
        });
        this.updateInstallButton = install;
        install.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        card.addView(install);
        TextView note = text("This app is locked to the Stable update channel. HCF only accepts official non-prerelease Stable APK assets, verifies package, versionCode, and signing certificate, then opens Android’s installer. Android still requires your confirmation to install. After a successful in-place update, the temporary Stable APK is removed automatically.", 10, getColor(R.color.hcf_muted));
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private String updateChannelLine(String ignored) {
        return "Channel: Stable • Official Releases • Locked";
    }

    private String effectiveUpdateChannel() {
        return BuildInfo.DEFAULT_UPDATE_CHANNEL;
    }

    private void showUpdateChannelDialog() {
        this.prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
        new AlertDialog.Builder(this)
                .setTitle("Update Channel")
                .setMessage("Stable — Official releases\n\nThis public build is locked to the Stable update channel.")
                .setPositiveButton("OK", (DialogInterface.OnClickListener) null)
                .show();
    }

    private void checkForUpdates(final boolean userInitiated) {
        if (this.updateStatus == null) return;
        final String channel = effectiveUpdateChannel();
        this.prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, channel).apply();
        this.updateStatus.setText("Checking Stable official release channel…");
        this.updateStatus.setTextColor(getColor(R.color.hcf_meta));
        if (this.updateDownloadButton != null) this.updateDownloadButton.setVisibility(View.GONE);
        this.availableRelease = null;
        UpdateChecker.check(this, channel, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean newer) {
                availableRelease = release;
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
                String asset = release.apkName == null || release.apkName.isEmpty() ? "No Stable APK asset attached" : release.apkName;
                String remote = UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "");
                if (newer) {
                    updateStatus.setText("Stable Update Available\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nAvailable: v" + remote + "\nLatest stable • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_accent_text));
                    if (updateDownloadButton != null && release.apkUrl != null && !release.apkUrl.isEmpty()) updateDownloadButton.setVisibility(View.VISIBLE);
                    if (prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        long id = AppUpdateDownloader.enqueue(SettingsActivity.this, release, userInitiated);
                        if (id > 0L) {
                            updateStatus.append("\nAutomatic Stable download queued • installer opens when ready.");
                            watchUpdateDownloadForAutoInstall(id);
                        }
                    }
                    if (userInitiated) Toast.makeText(SettingsActivity.this, "Stable update available" + (release.versionCode > 0 ? " • build " + release.versionCode : ""), Toast.LENGTH_LONG).show();
                } else if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                    updateStatus.setText("Installed build is newer\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nPublished Stable: v" + remote + " • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_meta));
                    if (userInitiated) Toast.makeText(SettingsActivity.this, "The published Stable release feed is behind this installed build.", Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus.setText("Up to date\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nNewest Stable: v" + remote + " • " + asset);
                    updateStatus.setTextColor(getColor(R.color.hcf_meta));
                    if (userInitiated) Toast.makeText(SettingsActivity.this, "This build is up to date for Stable.", Toast.LENGTH_SHORT).show();
                }
                AppLogger.info(SettingsActivity.this, "update_check", "stable | " + release.tag + " | newer=" + newer);
            }

            @Override
            public void onError(String message) {
                updateStatus.setText("Stable update check unavailable • " + message);
                updateStatus.setTextColor(getColor(R.color.hcf_warning));
                if (userInitiated) Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                AppLogger.error(SettingsActivity.this, "update_check", "stable | " + message);
            }
        });
    }

    public void watchUpdateDownloadForAutoInstall(final long id) {
        if (id <= 0L || !this.prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true)) return;
        final Runnable[] task = new Runnable[1];
        task[0] = new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed() || !prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true)) return;
                AppUpdateDownloader.ProgressSnapshot progress = AppUpdateDownloader.progress(SettingsActivity.this, id);
                if (progress.status == DownloadManagerStatus.SUCCESSFUL) {
                    openDownloadedInstaller(id);
                } else if (progress.status != DownloadManagerStatus.FAILED && settingsScroll != null) {
                    settingsScroll.postDelayed(task[0], 500L);
                }
            }
        };
        if (this.settingsScroll != null) this.settingsScroll.postDelayed(task[0], 500L);
    }

    private static final class DownloadManagerStatus {
        static final int SUCCESSFUL = 8;
        static final int FAILED = 16;
        private DownloadManagerStatus() {}
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null || !"com.harleytg.forum.INSTALL_UPDATE".equals(intent.getAction())) return;
        final long id = intent.getLongExtra("download_id", -1L);
        if (id > 0L) {
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() { openDownloadedInstaller(id); }
            }, 250L);
        }
    }

    private void installDownloadedUpdate() {
        openDownloadedInstaller(AppUpdateDownloader.downloadedId(this));
    }

    private void openDownloadedInstaller(long id) {
        if (id <= 0L) {
            Toast.makeText(this, "No downloaded Stable update is ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getPackageManager().canRequestPackageInstalls()) {
            if (AppUpdateDownloader.openInstaller(this, id)) return;
            Toast.makeText(this, "The Android installer could not open this verified Stable update.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            this.prefs.edit().putBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, true).apply();
            startActivityForResult(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            Toast.makeText(this, "Allow installs from this source. HCF will resume the verified Stable update automatically when you return.", Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            this.prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
            Toast.makeText(this, "Android blocked installation permission settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void resumeUpdateInstallAfterPermission() {
        if (this.prefs.getBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, false) && AppSecurity.canInstallUpdates(this)) {
            this.prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
            long id = AppUpdateDownloader.downloadedId(this);
            if (id > 0L) {
                Toast.makeText(this, "Install permission enabled • opening verified Stable update…", Toast.LENGTH_SHORT).show();
                if (!AppUpdateDownloader.openInstaller(this, id)) Toast.makeText(this, "The Android installer could not open this verified Stable update.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
            if (AppSecurity.canInstallUpdates(this)) resumeUpdateInstallAfterPermission();
            else {
                this.prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                Toast.makeText(this, "Install permission was not enabled. The downloaded Stable APK was kept.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openSupportEmail() {
        try {
            startActivity(new Intent(this, SupportContactActivity.class));
            AppLogger.info(this, "support_contact_open", "about");
        } catch (Throwable th) {
            Toast.makeText(this, "Unable to open Contact Support.", Toast.LENGTH_LONG).show();
            AppLogger.error(this, "support_contact_open", th.getClass().getSimpleName());
        }
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(settingsInfoCard("App identity", "Harley's Clan Forum\nVersion " + BuildInfo.VERSION + " • Build " + BuildInfo.VERSION_CODE + "\n" + BuildInfo.DEVELOPMENT_BUILD_LABEL, R.drawable.fa_circle_info));
        card.addView(settingsInfoCard("Build & channel", "Channel: Stable • Update feed: stable • Locked\nPackage: " + getPackageName() + "\nAPK: " + BuildInfo.APK_FILE_NAME, R.drawable.fa_download));
        card.addView(settingsInfoCard("Device & runtime", "Android SDK " + Build.VERSION.SDK_INT + " • " + Build.MANUFACTURER + " " + Build.MODEL + "\nTheme: " + ThemeManager.label(this) + "\n" + PerformanceProfile.settingLabel(this, this.prefs) + " • Network: " + RuntimeState.networkType(this), R.drawable.fa_gear));
        card.addView(settingsInfoCard("Forum endpoints", "Primary: forum.harleytg.com\nBackup: harleysclan.freeflarum.com", R.drawable.fa_globe));
        card.addView(settingsSubsectionHeader("Release & support", "Stable release notes, support and portable build information", R.drawable.fa_circle_info));
        card.addView(actionButton("View What's New • v" + BuildInfo.VERSION, new View.OnClickListener() {
            @Override public void onClick(View view) { ReleaseNotes.showCustom(SettingsActivity.this, prefs, true); }
        }));
        card.addView(actionButton("Release & Build Details", new View.OnClickListener() {
            @Override public void onClick(View view) { showBuildDetails(); }
        }));
        card.addView(actionButton("Copy App Information", new View.OnClickListener() {
            @Override public void onClick(View view) { copyAboutInformation(); }
        }));
        card.addView(actionButton("Contact Support", new View.OnClickListener() {
            @Override public void onClick(View view) { openSupportEmail(); }
        }));
        card.addView(settingsInfoCard("Privacy", "Sanitized reports never include passwords, cookies, access/session tokens, recovery codes, posts, messages or page contents.", R.drawable.fa_shield));
        return card;
    }

    private void copyAboutInformation() {
        String info = "Harley's Clan Forum"
                + "\nVersion: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")"
                + "\nChannel: Stable / stable"
                + "\nUpdate channel switch: Disabled"
                + "\nPackage: " + getPackageName()
                + "\nAPK: " + BuildInfo.APK_FILE_NAME
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance: " + PerformanceProfile.settingLabel(this, this.prefs)
                + "\nPrimary forum: forum.harleytg.com"
                + "\nBackup forum: harleysclan.freeflarum.com";
        ClipboardManager clipboard = (ClipboardManager) getSystemService("clipboard");
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("HCF app information", info));
        Toast.makeText(this, "App information copied.", Toast.LENGTH_SHORT).show();
    }

    private void showBuildDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Release & Build Details")
                .setMessage("Harley's Clan Forum Android app"
                        + "\n\nVersion: " + BuildInfo.VERSION
                        + "\nVersion code: " + BuildInfo.VERSION_CODE
                        + "\nChannel: Stable"
                        + "\nUpdate feed: stable (locked)"
                        + "\nPackage: " + getPackageName()
                        + "\nAPK: " + BuildInfo.APK_FILE_NAME
                        + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL)
                .setPositiveButton("Close", (DialogInterface.OnClickListener) null)
                .show();
    }

    public void refreshStatusLabels() {
        if (this.liveSyncStatus != null) {
            String sync = this.prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "Waiting for first sync");
            long at = this.prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            long latency = this.prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, 0L);
            String age = at <= 0L ? "not synced yet" : formatAge(System.currentTimeMillis() - at);
            if (at > 0L && !"just now".equals(age)) age += " ago";
            this.liveSyncStatus.setText("Live sync: " + sync + " • " + age + (latency > 0 ? " • " + latency + " ms" : "")
                    + "\n" + PerformanceProfile.settingLabel(this, this.prefs)
                    + " • " + RuntimeDiagnostics.notificationMode()
                    + " • poll " + formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs())
                    + " • " + RuntimeState.networkType(this));
        }
        int warningColor = R.color.hcf_warning;
        if (this.notificationStatus != null) {
            NotificationHelper.createChannel(this);
            boolean canPost = NotificationHelper.canPost(this);
            boolean headsUp = NotificationHelper.headsUpChannelReady(this);
            String summary = NotificationHelper.status(this) + " • channel importance=" + NotificationHelper.channelImportance(this);
            this.notificationStatus.setText("Status: " + summary);
            this.notificationStatus.setTextColor((canPost && headsUp) ? getColor(R.color.hcf_accent_text) : getColor(R.color.hcf_warning));
        }
        if (this.cookieStatus != null) this.cookieStatus.setText(cookieSummary());
        if (this.securityStatus != null) this.securityStatus.setText(AppSecurity.securitySummary(this));
        if (this.telemetryStatus != null) this.telemetryStatus.setText(TelemetryService.status(this));
        if (this.updateChannelStatus != null) this.updateChannelStatus.setText(updateChannelLine(BuildInfo.DEFAULT_UPDATE_CHANNEL));
        if (this.updateInstallButton != null) this.updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
        if (this.serverStatus != null) {
            String host = this.prefs.getString(AppPrefs.ACTIVE_HOST, "forum.harleytg.com");
            boolean primary = "forum.harleytg.com".equalsIgnoreCase(host);
            this.serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
            this.serverStatus.setTextColor(getColor(primary ? R.color.hcf_cyan : warningColor));
        }
    }

    private String cookieSummary() {
        CookieManager manager = CookieManager.getInstance();
        int primary = countCookies(manager.getCookie("https://forum.harleytg.com/"));
        int backup = countCookies(manager.getCookie("https://harleysclan.freeflarum.com/"));
        return "Cookie data: " + (primary + backup) + " visible • Primary " + primary + " • Backup " + backup;
    }

    private String formatRuntimeInterval(long value) {
        if (value <= 0L) return "idle";
        if (value < 1000L) return value + "ms";
        if (value % 1000L != 0L) return String.format(Locale.US, "%.2fs", Double.valueOf(value / 1000.0d));
        return (value / 1000L) + "s";
    }

    private String formatAge(long value) {
        long seconds = Math.max(0L, value / 1000L);
        if (seconds < 2L) return "just now";
        if (seconds < 60L) return seconds + " seconds";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + (minutes == 1L ? " minute" : " minutes");
        long hours = minutes / 60L;
        return hours + (hours == 1L ? " hour" : " hours");
    }

    private int countCookies(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        int count = 0;
        for (String cookie : value.split(";")) if (!cookie.trim().isEmpty()) count++;
        return count;
    }

    private ImageView settingsSectionIcon(int resId) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setColorFilter(getColor(R.color.hcf_cyan));
        view.setContentDescription(null);
        return view;
    }

    private int settingsIconForKey(String key) {
        if ("account_security".equals(key)) return R.drawable.fa_shield;
        if ("notifications".equals(key)) return R.drawable.fa_bell;
        if ("appearance".equals(key)) return R.drawable.fa_gear;
        if ("forum_data".equals(key)) return R.drawable.fa_globe;
        if ("advanced".equals(key)) return R.drawable.fa_circle_info;
        return R.drawable.fa_gear;
    }

    private int settingsIconForTitle(String title) {
        String lower = title == null ? "" : title.toLowerCase(Locale.US);
        if (lower.contains("account") || lower.contains("identity")) return R.drawable.fa_user;
        if (lower.contains("permission") || lower.contains("security")) return R.drawable.fa_shield;
        if (lower.contains("notification")) return R.drawable.fa_bell;
        if (lower.contains("appearance") || lower.contains("performance")) return R.drawable.fa_gear;
        if (lower.contains("connection") || lower.contains("routing")) return R.drawable.fa_globe;
        if (lower.contains("cookie") || lower.contains("site data")) return R.drawable.fa_lock;
        if (lower.contains("update")) return R.drawable.fa_download;
        if (lower.contains("error") || lower.contains("recovery")) return R.drawable.fa_triangle_exclamation;
        if (lower.contains("telemetry")) return R.drawable.fa_circle_info;
        if (lower.contains("developer")) return R.drawable.fa_bug;
        if (lower.contains("about")) return R.drawable.fa_circle_info;
        return R.drawable.fa_gear;
    }

    private View connectedSettingsPanel(String title, String subtitle, View body, boolean expanded) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(-1, -2);
        rootParams.bottomMargin = dp(compact() ? 8 : 12);
        root.setLayoutParams(rootParams);
        final LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(16);
        header.setClickable(true);
        header.setFocusable(true);
        header.setPadding(dp(15), dp(compact() ? 10 : 13), dp(12), dp(compact() ? 10 : 13));
        header.setBackgroundResource(expanded ? R.drawable.settings_section_header_expanded : R.drawable.settings_section_header_collapsed);
        ImageView icon = settingsSectionIcon(settingsIconForTitle(title));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.rightMargin = dp(11);
        header.addView(icon, iconParams);
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
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.settings_section_body);
        content.setPadding(dp(14), dp(12), dp(14), dp(14));
        content.setPivotY(0.0f);
        if (body != null) {
            if (body instanceof LinearLayout) {
                LinearLayout bodyLayout = (LinearLayout) body;
                if (bodyLayout.getChildCount() > 0 && "hcf_section_title".equals(bodyLayout.getChildAt(0).getTag())) bodyLayout.removeViewAt(0);
            }
            body.setBackgroundColor(0);
            body.setPadding(0, 0, 0, 0);
            body.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            content.addView(body);
        }
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        content.setAlpha(expanded ? 1.0f : 0.0f);
        content.setScaleY(expanded ? 1.0f : 0.96f);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] state = {expanded};
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (state[0]) {
                    state[0] = false;
                    arrow.animate().rotation(0.0f).setDuration(150L).start();
                    content.animate().alpha(0.0f).scaleY(0.96f).setDuration(150L).withEndAction(new Runnable() {
                        @Override public void run() {
                            content.setVisibility(View.GONE);
                            header.setBackgroundResource(R.drawable.settings_section_header_collapsed);
                        }
                    }).start();
                } else {
                    state[0] = true;
                    header.setBackgroundResource(R.drawable.settings_section_header_expanded);
                    content.setVisibility(View.VISIBLE);
                    content.setAlpha(0.0f);
                    content.setScaleY(0.94f);
                    arrow.animate().rotation(90.0f).setDuration(170L).start();
                    content.animate().alpha(1.0f).scaleY(1.0f).setDuration(180L).start();
                }
            }
        });
        return root;
    }

    private boolean devToolsEnabled() {
        return BuildInfo.ENABLE_DEV_TEST_MENU;
    }

    private View developerToolsCard() {
        LinearLayout card = card();
        card.addView(settingsInfoCard("Stable environment", "Internal Stable diagnostics • " + getPackageName() + "\nBuild " + BuildInfo.VERSION_CODE + " • channel Stable • update feed stable", R.drawable.fa_bug));
        card.addView(settingsSubsectionHeader("Notification Lab", "Test HCF alert types, delivery and background synchronization", R.drawable.fa_bell));
        card.addView(actionButton("Notification Test Console", new View.OnClickListener() {
            @Override public void onClick(View view) { showNotificationTestConsole(); }
        }));
        card.addView(actionButton("Test Notification Service", new View.OnClickListener() {
            @Override public void onClick(View view) {
                InstantNotificationService.requestImmediateSync(SettingsActivity.this);
                String result = NotificationHelper.postNotificationServiceTest(SettingsActivity.this)
                        ? "Notification service test sent • background sync requested."
                        : "Notification service test could not post. Check Android notification permission.";
                Toast.makeText(SettingsActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        }));
        card.addView(actionButton("Force Notification Sync", new View.OnClickListener() {
            @Override public void onClick(View view) {
                InstantNotificationService.requestImmediateSync(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Immediate notification sync requested.", Toast.LENGTH_SHORT).show();
            }
        }));
        card.addView(settingsSubsectionHeader("Runtime & WebView", "Inspect current network, renderer and recovery state", R.drawable.fa_globe));
        card.addView(actionButton("Runtime Snapshot", new View.OnClickListener() {
            @Override public void onClick(View view) { showDeveloperRuntimeSnapshot(); }
        }));
        card.addView(actionButton("Run Error & Recovery Check", new View.OnClickListener() {
            @Override public void onClick(View view) { showRecoveryDiagnostics(); }
        }));
        card.addView(settingsSubsectionHeader("Diagnostics & Logs", "Sanitized troubleshooting data and local app logs", R.drawable.fa_list));
        card.addView(actionButton("Copy Sanitized Diagnostic Report", new View.OnClickListener() {
            @Override public void onClick(View view) { copyDiagnosticReport(); }
        }));
        card.addView(actionButton("View App & Crash Logs", new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, LogsActivity.class)); }
        }));
        card.addView(settingsSubsectionHeader("Telemetry", "Stable diagnostics health reporting test", R.drawable.fa_circle_info));
        card.addView(actionButton("Send Test Telemetry", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!TelemetryService.isEnabled(SettingsActivity.this)) Toast.makeText(SettingsActivity.this, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show();
                else {
                    TelemetryService.sendTest(SettingsActivity.this);
                    Toast.makeText(SettingsActivity.this, "Telemetry test queued.", Toast.LENGTH_SHORT).show();
                }
            }
        }));
        return card;
    }

    private void showNotificationTestConsole() {
        new AlertDialog.Builder(this)
                .setTitle("Notification Test Console")
                .setItems(new String[]{"Direct message", "Mention", "Discussion reply", "General HCF alert"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) postTestAlert("Direct message", "New private message test", "/notifications");
                        else if (which == 1) postTestAlert("Mention", "@you were mentioned in a forum post", "/notifications");
                        else if (which == 2) postTestAlert("Discussion reply", "New reply test notification", "/notifications");
                        else postTestAlert("Forum alert", "General Harley's Clan Forum notification test", "/notifications");
                    }
                })
                .setNegativeButton("Cancel", (DialogInterface.OnClickListener) null)
                .show();
    }

    private void showDeveloperRuntimeSnapshot() {
        final String snapshot = "HCF Stable Runtime Snapshot"
                + "\n\nApp: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")"
                + "\nChannel: Stable"
                + "\nPackage: " + getPackageName()
                + "\nAndroid SDK: " + Build.VERSION.SDK_INT
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nNetwork: " + RuntimeState.networkType(this)
                + "\nTheme: " + ThemeManager.label(this)
                + "\nPerformance: " + PerformanceProfile.settingLabel(this, this.prefs)
                + "\nNotifications: " + NotificationHelper.status(this)
                + "\nPrimary: forum.harleytg.com"
                + "\nBackup: harleysclan.freeflarum.com"
                + "\nRenderer recovery: Enabled (HCF-WV-001)";
        new AlertDialog.Builder(this)
                .setTitle("Runtime Snapshot")
                .setMessage(snapshot)
                .setPositiveButton("Close", (DialogInterface.OnClickListener) null)
                .setNeutralButton("Copy", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService("clipboard");
                        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("HCF runtime snapshot", snapshot));
                        Toast.makeText(SettingsActivity.this, "Runtime snapshot copied.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private View settingsSubsectionHeader(String title, String subtitle, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(dp(4), dp(14), dp(4), dp(5));
        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(19), dp(19));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 13, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        labels.addView(text(subtitle, 10, getColor(R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        return row;
    }

    private View settingsInfoCard(String title, String detail, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(48);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setClickable(false);
        row.setFocusable(false);
        ImageView icon = settingsSectionIcon(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.rightMargin = dp(11);
        iconParams.topMargin = dp(2);
        row.addView(icon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 13, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        TextView detailView = text(detail, 10, getColor(R.color.hcf_muted));
        detailView.setSingleLine(false);
        detailView.setMaxLines(Integer.MAX_VALUE);
        labels.addView(detailView);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(7);
        row.setLayoutParams(rowParams);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(compact() ? 8 : 12);
        card.setLayoutParams(params);
        return card;
    }

    private View notificationChannelStatusRow(String title, String detail, String channelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(8), dp(12), dp(8));
        ImageView icon = settingsSectionIcon(R.drawable.fa_lock);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        String status = NotificationHelper.channelStatus(this, channelId);
        labels.addView(text(detail + " • " + status, 10, getColor(NotificationHelper.channelImportance(this, channelId) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView required = text("REQUIRED", 9, getColor(R.color.hcf_accent_text));
        required.setTypeface(null, 1);
        required.setGravity(17);
        required.setPadding(dp(8), dp(4), dp(8), dp(4));
        required.setBackgroundResource(R.drawable.status_chip_background);
        row.addView(required);
        row.setContentDescription(title + ", required in HCF. " + status);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(compact() ? 58 : 66));
        params.topMargin = dp(6);
        row.setLayoutParams(params);
        return row;
    }

    private View notificationChannelRow(String title, String detail, String channelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setBackgroundResource(R.drawable.quick_action_background);
        row.setPadding(dp(14), dp(9), dp(14), dp(9));
        row.setClickable(false);
        row.setFocusable(false);
        ImageView icon = settingsSectionIcon(R.drawable.fa_bell);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView titleView = text(title, 14, getColor(R.color.hcf_text));
        titleView.setTypeface(null, 1);
        labels.addView(titleView);
        String status = NotificationHelper.channelStatus(this, channelId);
        labels.addView(text(detail + " • " + status, 10, getColor(NotificationHelper.channelImportance(this, channelId) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        row.addView(labels);
        row.setContentDescription(title + ". " + detail + ". " + status);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        row.setLayoutParams(params);
        return row;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("hcf_section_title");
        section.setPadding(0, 0, 0, dp(8));
        TextView titleView = text(title, 16, getColor(R.color.hcf_accent_text));
        titleView.setTypeface(null, 1);
        section.addView(titleView);
        section.addView(text(subtitle, 11, getColor(R.color.hcf_muted)));
        return section;
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

    private void showPerformanceProfileDialog(final Button button) {
        final String[] values = {"auto", "performance", "balanced", "quality"};
        String[] labels = {"Auto — Recommended", "Performance — Minimal motion", "Balanced — Short smooth motion", "High Performance — Full visual effects"};
        String saved = PerformanceProfile.saved(this.prefs);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selected = i;
        new AlertDialog.Builder(this)
                .setTitle("Performance Profile")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which < 0 || which >= values.length) return;
                        PerformanceProfile.save(prefs, values[which]);
                        String label = PerformanceProfile.settingLabel(SettingsActivity.this, prefs);
                        button.setText("Performance Profile: " + label);
                        button.setContentDescription("Performance profile " + label);
                        AppLogger.info(SettingsActivity.this, "setting_performance_profile", values[which] + " -> " + PerformanceProfile.resolve(SettingsActivity.this, prefs));
                        Toast.makeText(SettingsActivity.this, label + " • " + PerformanceProfile.detail(SettingsActivity.this, prefs), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", (DialogInterface.OnClickListener) null)
                .show();
    }

    private Switch toggle(String label, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(label);
        toggle.setTextColor(getColor(R.color.hcf_text));
        toggle.setTextSize(14.0f);
        toggle.setChecked(checked);
        toggle.setPadding(0, dp(7), 0, dp(7));
        return toggle;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(cleanIconPrefix(label));
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        button.setGravity(8388627);
        button.setPadding(dp(14), 0, dp(14), 0);
        FaIcons.applyStart(button, label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
        params.topMargin = dp(7);
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton chromeButton(String description) {
        int inset = compact() ? 9 : 11;
        String label = description == null || description.trim().isEmpty() ? "Back" : description;
        return UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, inset, label);
    }

    private String cleanIconPrefix(String value) {
        return value == null ? "" : value.replaceFirst("^[^A-Za-z0-9]+", "").trim();
    }

    private void openForumLinkSettings() {
        try {
            startActivity(new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", Uri.parse("package:" + getPackageName())));
            AppLogger.info(this, "forum_link_settings", "open-by-default");
        } catch (Throwable ignored) {
            try {
                startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignoredAgain) {
                Toast.makeText(this, "Android link settings are unavailable on this device.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) return;
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
    }

    private void openNotificationSettings() {
        NotificationHelper.createChannel(this);
        NotificationHelper.openAppNotificationSettings(this);
        AppLogger.info(this, "notification_settings", NotificationHelper.status(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == 0;
            AppLogger.info(this, "notification_permission", granted ? "granted" : "denied | " + NotificationHelper.status(this));
            Toast.makeText(this, granted ? "Notifications allowed. Sending a heads-up test…" : "Notifications not allowed.", Toast.LENGTH_SHORT).show();
            NotificationSyncScheduler.apply(this);
            refreshStatusLabels();
            if (granted) NotificationHelper.post(this, "Harley's Clan Forum", "Heads-up notifications are enabled. This is a test alert.", ForumUrlRouter.home("forum.harleytg.com"));
        }
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == 2;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
