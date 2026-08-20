package com.harleytg.forum.dev;

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
import com.harleytg.forum.dev.AppUpdateDownloader;
import com.harleytg.forum.dev.ForumIdentity;
import com.harleytg.forum.dev.ForumSecurity;
import com.harleytg.forum.dev.UpdateChecker;
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

    @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        SharedPreferences sharedPreferences = getSharedPreferences("hcf_app", 0);
        this.prefs = sharedPreferences;
        if (!sharedPreferences.getBoolean("notifications_enabled", true)) {
            this.prefs.edit().putBoolean("notifications_enabled", true).apply();
        }
        try {
            int i = -16777216;
            getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            Window window = getWindow();
            if (!ThemeManager.isAmoled(this)) {
                i = getColor(R.color.hcf_bg);
            }
            window.setNavigationBarColor(i);
            setContentView(buildUi());
            handleInstallIntent(getIntent());
            AppLogger.info(this, "settings_open", "1.0");
        } catch (Throwable th) {
            AppLogger.crash(this, th);
            showSettingsRecovery(th);
        }
    }

    @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        try {
            refreshStatusLabels();
            resumeUpdateInstallAfterPermission();
        } catch (Throwable th) {
            AppLogger.error(this, "settings_resume", th.getClass().getSimpleName());
        }
    }

    private void showSettingsRecovery(Throwable th) {
        try {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setGravity(17);
            linearLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            TextView text = text("App Control Center • Recovery Mode", 18, getColor(R.color.hcf_accent_text));
            text.setGravity(17);
            text.setTypeface(null, 1);
            linearLayout.addView(text);
            TextView text2 = text("1.0 • Build 10000072\n\nSettings UI recovered from: " + th.getClass().getSimpleName(), 13, getColor(R.color.hcf_text));
            text2.setGravity(17);
            linearLayout.addView(text2);
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText("Back to Forum");
            button.setAllCaps(false);
            button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda38
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SettingsActivity.this.m175x7ca22e37(view);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
            layoutParams.topMargin = dp(18);
            linearLayout.addView(button, layoutParams);
            setContentView(linearLayout);
        } catch (Throwable unused) {
        }
    }

    /* renamed from: lambda$showSettingsRecovery$0$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m175x7ca22e37(View view) {
        finish();
    }

    private View buildUi() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        linearLayout.addView(buildHeader());
        ScrollView scrollView = new ScrollView(this);
        this.settingsScroll = scrollView;
        scrollView.setFillViewport(true);
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.settingsContent = linearLayout2;
        linearLayout2.setOrientation(1);
        this.settingsContent.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        this.settingsScroll.addView(this.settingsContent, new FrameLayout.LayoutParams(-1, -2));
        showSettingsHome();
        linearLayout.addView(this.settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return linearLayout;
    }

    private View buildHeader() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(8), dp(compact() ? 2 : 5), dp(8), dp(compact() ? 2 : 5));
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        linearLayout.setMinimumHeight(dp(compact() ? 46 : 56));
        ImageButton chromeButton = chromeButton("‹");
        this.headerBackButton = chromeButton;
        chromeButton.setContentDescription("Back");
        this.headerBackButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda74
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m138lambda$buildHeader$1$comharleytgforumdevSettingsActivity(view);
            }
        });
        linearLayout.addView(this.headerBackButton, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        layoutParams.leftMargin = dp(4);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.leftMargin = dp(10);
        linearLayout.addView(linearLayout2, layoutParams2);
        TextView text = text("App Settings", 18, getColor(R.color.hcf_text));
        this.headerTitleView = text;
        text.setTypeface(null, 1);
        linearLayout2.addView(this.headerTitleView);
        TextView text2 = text("Harley's Clan Forum v1.0 [Development Build / Beta]", 10, getColor(R.color.hcf_meta));
        this.headerSubtitleView = text2;
        text2.setTypeface(null, 1);
        linearLayout2.addView(this.headerSubtitleView);
        return linearLayout;
    }

    /* renamed from: lambda$buildHeader$1$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m138lambda$buildHeader$1$comharleytgforumdevSettingsActivity(View view) {
        String str = this.currentSettingsSection;
        if (str == null || str.isEmpty()) {
            finish();
        } else {
            showSettingsHome();
        }
    }

    private void showSettingsHome() {
        this.currentSettingsSection = "";
        updateSettingsHeader("App Settings", "Harley's Clan Forum v1.0 [Development Build / Beta]");
        LinearLayout linearLayout = this.settingsContent;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint("Search settings…");
        editText.setHintTextColor(getColor(R.color.hcf_hint));
        editText.setTextColor(getColor(R.color.hcf_text));
        editText.setTextSize(14.0f);
        editText.setBackgroundResource(R.drawable.quick_action_background);
        editText.setPadding(dp(15), 0, dp(15), 0);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 42 : 48));
        layoutParams.bottomMargin = dp(10);
        this.settingsContent.addView(editText, layoutParams);
        TextView text = text("Settings", 10, getColor(R.color.hcf_cyan));
        text.setTypeface(null, 1);
        text.setPadding(dp(4), dp(2), 0, dp(7));
        this.settingsContent.addView(text);
        final LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        this.settingsContent.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        addSettingsCategory(linearLayout2, "Account & Security", "Forum identity, profile and account controls", "account_security", "profile username login identity account security password email two factor sessions");
        addSettingsCategory(linearLayout2, "Notifications", "Required alerts, silent background alerts and Beta test alerts", "notifications", "alerts notification history background silent test delivery");
        addSettingsCategory(linearLayout2, "Appearance & Performance", "Theme, interface density and performance", "appearance", "theme forum auto phone auto dark light performance header url live updates");
        addSettingsCategory(linearLayout2, "Forum & Site Data", "Server routing, links, cookies and local site data", "forum_data", "server primary backup failover links cookies data cache privacy sign out");
        addSettingsCategory(linearLayout2, "Advanced & About", "App permissions, updates, recovery, diagnostics and build information", "advanced", "permissions security android update apk diagnostics logs telemetry recovery version about build dev tools");
        final TextView text2 = text("No matching settings.", 12, getColor(R.color.hcf_muted));
        text2.setGravity(17);
        text2.setPadding(0, dp(18), 0, dp(18));
        text2.setVisibility(8);
        this.settingsContent.addView(text2);
        editText.addTextChangedListener(new TextWatcher() { // from class: com.harleytg.forum.dev.SettingsActivity.1
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                SettingsActivity.this.filterSettingsCategories(linearLayout2, text2, charSequence == null ? "" : charSequence.toString());
            }
        });
        TextView text3 = text("Harley's Clan Forum v1.0 [Development Build / Beta]", 9, getColor(R.color.hcf_hint));
        text3.setGravity(17);
        text3.setPadding(0, dp(14), 0, dp(4));
        this.settingsContent.addView(text3);
        ScrollView scrollView = this.settingsScroll;
        if (scrollView != null) {
            scrollView.post(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda29
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.this.m174xffa2b383();
                }
            });
        }
        refreshStatusLabels();
    }

    /* renamed from: lambda$showSettingsHome$2$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m174xffa2b383() {
        this.settingsScroll.scrollTo(0, 0);
    }

    private void showSettingsSection(String str) {
        if (str == null) {
            str = "";
        }
        this.currentSettingsSection = str;
        LinearLayout linearLayout = this.settingsContent;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        updateSettingsHeader(settingsSectionName(this.currentSettingsSection), settingsSectionSubtitle(this.currentSettingsSection));
        String str2 = this.currentSettingsSection;
        str2.hashCode();
        switch (str2) {
            case "forum_data":
                this.settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), true));
                this.settingsContent.addView(connectedSettingsPanel("Cookies & Site Data", "Forum data stored locally on this device", privacyCard(), false));
                break;
            case "advanced":
                this.settingsContent.addView(connectedSettingsPanel("Permissions & Security", "Android permissions and app hardening", securityCard(), false));
                this.settingsContent.addView(connectedSettingsPanel("App Updates", "Secure update checks, download and install controls", updateCard(), false));
                this.settingsContent.addView(connectedSettingsPanel("Error & Recovery", "Diagnostics, logs and WebView recovery tools", diagnosticsCard(), false));
                this.settingsContent.addView(connectedSettingsPanel("Telemetry", "Optional app health and diagnostic reporting", telemetryCard(), false));
                if (devToolsEnabled()) {
                    this.settingsContent.addView(connectedSettingsPanel("Developer Tools", "Dev/Beta-only test controls", developerToolsCard(), this.openDeveloperToolsOnAdvanced));
                }
                this.openDeveloperToolsOnAdvanced = false;
                this.settingsContent.addView(connectedSettingsPanel("About Harley's Clan Forum", "Version, release notes and build information", aboutCard(), false));
                break;
            case "notifications":
                this.settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Required main alerts • messages, mentions, replies and important activity", mainAlertsCard(), true));
                this.settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Background sync, service status and passive notifications", silentAlertsCard(), false));
                this.settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", "Development/Beta test channel • controls live in Developer Tools", testAlertsInfoCard(), false));
                break;
            case "account_security":
                this.settingsContent.addView(connectedSettingsPanel("Account & Identity", "Signed-in profile and forum identity controls", accountIdentityCard(), true));
                this.settingsContent.addView(connectedSettingsPanel("Account Controls", "Profile, password, email and session security shortcuts", accountControlsCard(), false));
                break;
            case "appearance":
                this.settingsContent.addView(connectedSettingsPanel("Appearance & Performance", "Theme, interface and rendering preferences", interfaceCard(), true));
                break;
            default:
                showSettingsHome();
                return;
        }
        this.settingsContent.addView(actionButton("‹  Back to all settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m176x50dfb530(view);
            }
        }));
        ScrollView scrollView = this.settingsScroll;
        if (scrollView != null) {
            scrollView.post(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.this.m177xdd7fe031();
                }
            });
        }
        refreshStatusLabels();
    }

    /* renamed from: lambda$showSettingsSection$3$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m176x50dfb530(View view) {
        showSettingsHome();
    }

    /* renamed from: lambda$showSettingsSection$4$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m177xdd7fe031() {
        this.settingsScroll.scrollTo(0, 0);
    }

    private void addSettingsCategory(LinearLayout linearLayout, String str, String str2, String str3, String str4) {
        View view = settingsCategoryRow(str, str2, str3);
        view.setTag((str + " " + str2 + " " + str4).toLowerCase(Locale.US));
        linearLayout.addView(view);
    }

    private View settingsCategoryRow(String str, String str2, final String str3) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setBackgroundResource(R.drawable.quick_action_background);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setPadding(dp(15), dp(compact() ? 8 : 11), dp(12), dp(compact() ? 8 : 11));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(7);
        linearLayout.setLayoutParams(layoutParams);
        ImageView imageView = settingsSectionIcon(settingsIconForKey(str3));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(dp(24), dp(24));
        layoutParams2.rightMargin = dp(12);
        linearLayout.addView(imageView, layoutParams2);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        TextView text = text(str, 14, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        linearLayout2.addView(text(str2, 10, getColor(R.color.hcf_muted)));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView text2 = text("›", 24, getColor(R.color.hcf_cyan_bright));
        text2.setGravity(17);
        linearLayout.addView(text2, new LinearLayout.LayoutParams(dp(30), -1));
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda68
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m169xe82f4586(str3, view);
            }
        });
        return linearLayout;
    }

    /* renamed from: lambda$settingsCategoryRow$5$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m169xe82f4586(String str, View view) {
        showSettingsSection(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void filterSettingsCategories(LinearLayout linearLayout, TextView textView, String str) {
        String lowerCase = str == null ? "" : str.trim().toLowerCase(Locale.US);
        int i = 0;
        int i2 = 0;
        while (true) {
            if (i >= linearLayout.getChildCount()) {
                break;
            }
            View childAt = linearLayout.getChildAt(i);
            boolean z = lowerCase.isEmpty() || (childAt.getTag() == null ? "" : String.valueOf(childAt.getTag())).contains(lowerCase);
            childAt.setVisibility(z ? 0 : 8);
            if (z) {
                i2++;
            }
            i++;
        }
        if (textView != null) {
            textView.setVisibility(i2 != 0 ? 8 : 0);
        }
    }

    private void updateSettingsHeader(String str, String str2) {
        TextView textView = this.headerTitleView;
        if (textView != null) {
            if (str == null) {
                str = "App Settings";
            }
            textView.setText(str);
        }
        TextView textView2 = this.headerSubtitleView;
        if (textView2 != null) {
            if (str2 == null) {
                str2 = "Harley's Clan Forum v1.0 [Development Build / Beta]";
            }
            textView2.setText(str2);
        }
        ImageButton imageButton = this.headerBackButton;
        if (imageButton != null) {
            String str3 = this.currentSettingsSection;
            imageButton.setContentDescription((str3 == null || str3.isEmpty()) ? "Back to forum" : "Back to all settings");
        }
    }

    private String settingsSectionName(String str) {
        return "account_security".equals(str) ? "Account & Security" : "notifications".equals(str) ? "Notifications" : "appearance".equals(str) ? "Appearance & Performance" : "forum_data".equals(str) ? "Forum & Site Data" : "advanced".equals(str) ? "Advanced & About" : "App Settings";
    }

    private String settingsSectionSubtitle(String str) {
        return "Harley's Clan Forum v1.0 [Development Build / Beta]";
    }

    private String channelDisplay() {
        String lowerCase = "Dev".toLowerCase(Locale.US);
        if (lowerCase.isEmpty()) {
            return "Stable";
        }
        return Character.toUpperCase(lowerCase.charAt(0)) + lowerCase.substring(1);
    }

    private String channelDisplayName(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "Stable";
        }
        String lowerCase = str.trim().toLowerCase(Locale.US);
        return Character.toUpperCase(lowerCase.charAt(0)) + lowerCase.substring(1);
    }

    private View statusDashboardCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Live Status", "Forum and notification service at a glance"));
        TextView text = text("Live sync: checking…", 12, getColor(R.color.hcf_meta));
        this.liveSyncStatus = text;
        text.setTypeface(null, 1);
        card.addView(this.liveSyncStatus);
        card.addView(actionButton("Sync Forum & Notifications Now", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m181xc36f4660(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$statusDashboardCard$6$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m181xc36f4660(View view) {
        String string = this.prefs.getString("session_user_id", "");
        if (string == null || string.trim().isEmpty()) {
            Toast.makeText(this, "Sign in to the forum first, then sync again.", 1).show();
            return;
        }
        InstantNotificationService.requestImmediateSync(this);
        TextView textView = this.liveSyncStatus;
        if (textView != null) {
            textView.setText("Live sync: syncing now…");
        }
        Toast.makeText(this, "Forum sync requested.", 0).show();
        getWindow().getDecorView().postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda44
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.refreshStatusLabels();
            }
        }, 1600L);
    }

    private View accountIdentityCard() {
        String str;
        String str2;
        LinearLayout card = card();
        ForumIdentity.Snapshot load = ForumIdentity.load(this);
        card.addView(sectionTitle("Account & Identity", "Your signed-in forum profile and account security"));
        if (load.loggedIn) {
            str = "Signed in as " + load.identityLabel();
        } else {
            str = "Forum session: Guest_Protocol";
        }
        TextView text = text(str, 13, getColor(load.loggedIn ? R.color.hcf_accent_text : R.color.hcf_meta));
        text.setTypeface(null, 1);
        card.addView(text);
        if (load.loggedIn) {
            if (load.username.isEmpty()) {
                str2 = "Identity sync active";
            } else {
                str2 = "@" + load.username + " • Identity sync active";
            }
        } else {
            str2 = "Sign in to connect your identity";
        }
        card.addView(text(str2, 11, getColor(R.color.hcf_muted)));
        card.addView(actionButton("Open Account & Identity", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda42
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m137x9b42f0ea(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$accountIdentityCard$7$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m137x9b42f0ea(View view) {
        startActivity(new Intent(this, (Class<?>) IdentityActivity.class));
    }

    private View accountControlsCard() {
        String identityLabel;
        String str;
        LinearLayout card = card();
        final ForumIdentity.Snapshot load = ForumIdentity.load(this);
        ForumSecurity.Snapshot load2 = ForumSecurity.load(this);
        if (!load.loggedIn) {
            TextView text = text("Sign in to manage your forum account controls.", 12, getColor(R.color.hcf_muted));
            text.setPadding(dp(2), dp(2), dp(2), dp(6));
            card.addView(text);
            card.addView(actionButton("Open Forum Sign In", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda70
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SettingsActivity.this.m135x59583fe3(view);
                }
            }));
            return card;
        }
        if (load.username == null || load.username.trim().isEmpty()) {
            identityLabel = load.identityLabel();
        } else {
            identityLabel = "@" + load.username.trim();
        }
        TextView text2 = text("Signed in as " + identityLabel, 12, getColor(R.color.hcf_accent_text));
        text2.setTypeface(null, 1);
        card.addView(text2);
        StringBuilder sb = new StringBuilder();
        if (load2.seen) {
            if (load2.passwordControls) {
                sb.append("Password");
            }
            if (load2.emailControls) {
                if (sb.length() > 0) {
                    sb.append(" • ");
                }
                sb.append("Email");
            }
            if (load2.twoFactorControls) {
                if (sb.length() > 0) {
                    sb.append(" • ");
                }
                sb.append("Two-factor");
            }
            if (load2.sessionCount > 0) {
                if (sb.length() > 0) {
                    sb.append(" • ");
                }
                sb.append("Sessions: ");
                sb.append(load2.sessionCount);
            }
        }
        if (sb.length() == 0) {
            if (load2.seen) {
                str = "Security controls are managed by the forum";
            } else {
                str = "Open Account Security once to sync available controls";
            }
            sb.append(str);
        }
        TextView text3 = text("Available: " + ((Object) sb), 10, getColor(R.color.hcf_muted));
        text3.setPadding(dp(2), dp(2), dp(2), dp(5));
        card.addView(text3);
        card.addView(actionButton("Open My Forum Profile", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda71
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m136xe5f86ae4(load, view);
            }
        }));
        card.addView(actionButton("Open Account Security", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda72
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m134x703b04a6(load, view);
            }
        }));
        TextView text4 = text("Password, email, two-factor and session changes remain on Harley's Clan Forum. The app only exposes safe shortcuts and synced capability/status information.", 10, getColor(R.color.hcf_hint));
        text4.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(text4);
        return card;
    }

    /* renamed from: lambda$accountControlsCard$8$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m135x59583fe3(View view) {
        openAccountForumPath("/login");
    }

    /* renamed from: lambda$accountControlsCard$9$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m136xe5f86ae4(ForumIdentity.Snapshot snapshot, View view) {
        String accountHandle = accountHandle(snapshot);
        if (accountHandle.isEmpty()) {
            Toast.makeText(this, "Unable to determine your forum profile route.", 0).show();
            return;
        }
        openAccountForumPath("/u/" + Uri.encode(accountHandle));
    }

    /* renamed from: lambda$accountControlsCard$10$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m134x703b04a6(ForumIdentity.Snapshot snapshot, View view) {
        String accountHandle = accountHandle(snapshot);
        if (accountHandle.isEmpty()) {
            Toast.makeText(this, "Unable to determine your forum profile route.", 0).show();
            return;
        }
        openAccountForumPath("/u/" + Uri.encode(accountHandle) + "/security");
    }

    private String accountHandle(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null || !snapshot.loggedIn) {
            return "";
        }
        String str = (snapshot.slug == null || snapshot.slug.trim().isEmpty()) ? snapshot.username : snapshot.slug;
        if (str == null) {
            return "";
        }
        return str.trim();
    }

    private void openAccountForumPath(String str) {
        ForumIdentity.Snapshot load = ForumIdentity.load(this);
        String str2 = ForumUrlRouter.isForumHost(load.host) ? load.host : "forum.harleytg.com";
        String trim = (str == null || str.trim().isEmpty()) ? "/" : str.trim();
        if (!trim.startsWith("/")) {
            trim = "/" + trim;
        }
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setData(Uri.parse("https://" + str2 + trim));
        intent.addFlags(603979776);
        startActivity(intent);
        finish();
    }

    private View mainAlertsCard() {
        LinearLayout card = card();
        if (!this.prefs.getBoolean("notifications_enabled", true)) {
            this.prefs.edit().putBoolean("notifications_enabled", true).apply();
        }
        NotificationHelper.createChannel(this);
        TextView text = text("Checking HCF Alerts status…", 12, getColor(R.color.hcf_muted));
        this.notificationStatus = text;
        card.addView(text);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        linearLayout.setBackgroundResource(R.drawable.quick_action_background);
        TextView text2 = text("Required in Harley's Clan Forum", 14, getColor(R.color.hcf_accent_text));
        text2.setTypeface(null, 1);
        linearLayout.addView(text2);
        linearLayout.addView(text("HCF does not provide an Off or Silent control for this channel. It carries direct messages, mentions, replies and important forum/app alerts.", 10, getColor(R.color.hcf_muted)));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(6);
        layoutParams.bottomMargin = dp(6);
        card.addView(linearLayout, layoutParams);
        card.addView(notificationChannelStatusRow("HCF Alerts", "Required • audible • heads-up capable • not affected by HCF silence controls", "hcf_alerts_v1"));
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            card.addView(actionButton("Allow Android Notification Permission", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda43
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SettingsActivity.this.m160lambda$mainAlertsCard$11$comharleytgforumdevSettingsActivity(view);
                }
            }));
        }
        TextView text3 = text("Android is the final owner of notification permission and channel controls. If Android has blocked or changed HCF Alerts, HCF will show that state here, but the app itself never turns this channel off or silent.", 10, getColor(R.color.hcf_muted));
        text3.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(text3);
        return card;
    }

    /* renamed from: lambda$mainAlertsCard$11$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m160lambda$mainAlertsCard$11$comharleytgforumdevSettingsActivity(View view) {
        requestNotificationPermissionIfNeeded();
    }

    private View silentAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        Switch r1 = toggle("Background notification sync", this.prefs.getBoolean("background_notification_sync", true));
        r1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda36
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m179xb60ad465(compoundButton, z);
            }
        });
        card.addView(r1);
        Switch r12 = toggle("Silence HCF Silent Alerts", this.prefs.getBoolean("silence_background_service_notification", false));
        r12.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda37
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m180x42aaff66(compoundButton, z);
            }
        });
        card.addView(r12);
        card.addView(notificationChannelRow("HCF Silent Alerts", "Silent/background channel • hidden when Silence HCF Silent Alerts is enabled", "hcf_silent_alerts_v1"));
        TextView text = text("Silence passive notifications now keeps live background sync active while routing service status, generic summaries and test/status alerts through silent low-priority behavior. Messages, mentions and replies remain normal alerts.", 10, getColor(R.color.hcf_muted));
        text.setPadding(dp(2), dp(8), dp(2), dp(2));
        card.addView(text);
        return card;
    }

    /* renamed from: lambda$silentAlertsCard$12$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m179xb60ad465(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("background_notification_sync", z).apply();
        NotificationSyncScheduler.apply(this);
        AppLogger.info(this, "setting_background_sync", Boolean.toString(z));
    }

    /* renamed from: lambda$silentAlertsCard$13$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m180x42aaff66(CompoundButton compoundButton, boolean z) {
        String str;
        this.prefs.edit().putBoolean("silence_background_service_notification", z).apply();
        NotificationHelper.refreshChannels(this);
        NotificationSyncScheduler.apply(this);
        AppLogger.info(this, "setting_silence_passive_notifications", Boolean.toString(z));
        if (z) {
            str = "Updated • Passive notification silence";
        } else {
            str = "Updated • Passive notification silence";
        }
        Toast.makeText(this, str, 1).show();
    }

    private View testAlertsInfoCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(notificationChannelRow("HCF Test Alerts", "Development/Beta-only test notifications • isolated from real HCF Alerts", "hcf_test_alerts_v1"));
        TextView text = text("This channel is reserved for HCF notification diagnostics and test delivery. It never carries normal forum messages, mentions, replies or background-service status.", 10, getColor(R.color.hcf_muted));
        text.setPadding(dp(2), dp(8), dp(2), dp(4));
        card.addView(text);
        if (devToolsEnabled()) {
            card.addView(actionButton("Open Developer Tools", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda30
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SettingsActivity.this.m195x2abd0f7c(view);
                }
            }));
        }
        return card;
    }

    /* renamed from: lambda$testAlertsInfoCard$14$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m195x2abd0f7c(View view) {
        this.openDeveloperToolsOnAdvanced = true;
        showSettingsSection("advanced");
    }

    private View notificationToolsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Android notification settings", "System permission and channel controls"));
        card.addView(actionButton("All Android Notification Settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda45
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m161x2b7a411e(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$notificationToolsCard$15$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m161x2b7a411e(View view) {
        openNotificationSettings();
    }

    private View themeModeSelector() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(0, dp(4), 0, dp(2));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setWeightSum(2.0f);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(0);
        linearLayout3.setWeightSum(2.0f);
        String mode = ThemeManager.mode(this);
        linearLayout2.addView(themeChoiceButton("Forum Auto", "auto_forum", "auto_forum".equals(mode)), themeChoiceParams(false));
        linearLayout2.addView(themeChoiceButton("Phone Auto", "auto_phone", "auto_phone".equals(mode)), themeChoiceParams(true));
        boolean z = "dark".equals(mode) || "amoled".equals(mode);
        linearLayout3.addView(themeChoiceButton("Light", "light", "light".equals(mode)), themeChoiceParams(false));
        linearLayout3.addView(themeChoiceButton("Dark", "dark", z), themeChoiceParams(true));
        linearLayout.addView(linearLayout2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(8);
        linearLayout.addView(linearLayout3, layoutParams);
        return linearLayout;
    }

    private LinearLayout.LayoutParams themeChoiceParams(boolean z) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(compact() ? 46 : 52), 1.0f);
        if (z) {
            layoutParams.leftMargin = dp(8);
        }
        return layoutParams;
    }

    private Button themeChoiceButton(String str, final String str2, boolean z) {
        final Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setAllCaps(false);
        button.setText(str);
        button.setTextSize(13.0f);
        button.setTypeface(null, 1);
        button.setGravity(17);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextColor(getColor(z ? R.color.hcf_chip_selected_text : R.color.hcf_text));
        button.setBackgroundResource(z ? R.drawable.theme_choice_selected_background : R.drawable.theme_choice_background);
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        sb.append(z ? ", selected" : "");
        button.setContentDescription(sb.toString());
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda59
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m196x94b53f63(str2, button, view);
            }
        });
        return button;
    }

    /* renamed from: lambda$themeChoiceButton$16$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m196x94b53f63(String str, Button button, View view) {
        selectThemeMode(str, button);
    }

    private void selectThemeMode(final String str, final Button button) {
        String mode = ThemeManager.mode(this);
        if (str.equals(mode)) {
            return;
        }
        if ("dark".equals(str) && "dark".equals(mode)) {
            return;
        }
        button.animate().scaleX(0.97f).scaleY(0.97f).setDuration(65L).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda40
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m168x6a907450(str, button);
            }
        }).start();
    }

    /* renamed from: lambda$selectThemeMode$17$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m168x6a907450(String str, Button button) {
        this.prefs.edit().putString("app_theme", str).apply();
        AppLogger.info(this, "setting_theme", str);
        button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90L).start();
    }

    private View interfaceCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Appearance & Interface", "A simpler app shell with more room for the forum"));
        TextView text = text("Theme", 13, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        text.setPadding(0, dp(4), 0, dp(2));
        card.addView(text);
        card.addView(themeModeSelector());
        card.addView(text("Forum Auto is the default. It follows your FoF Night Mode account/per-device setting; when the forum itself uses Auto, the phone theme decides. Phone Auto follows Android directly.", 10, getColor(R.color.hcf_muted)));
        final Button actionButton = actionButton("Performance Profile: " + PerformanceProfile.settingLabel(this, this.prefs), null);
        actionButton.setContentDescription("Choose app performance profile");
        actionButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m156lambda$interfaceCard$18$comharleytgforumdevSettingsActivity(actionButton, view);
            }
        });
        card.addView(actionButton);
        card.addView(text("Auto is the default adaptive engine. Capable devices can promote to Auto • Real-Time; HCF automatically drops to Balanced, Performance, or Extreme Saver when network, memory, battery, thermal, or renderer conditions require it.", 10, getColor(R.color.hcf_muted)));
        Switch r4 = toggle("Live forum updates", this.prefs.getBoolean("live_forum_updates", true));
        r4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda3
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m157lambda$interfaceCard$19$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r4);
        Switch r42 = toggle("Show secure URL bar", this.prefs.getBoolean("show_url_bar", true));
        r42.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda4
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m158lambda$interfaceCard$20$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r42);
        TextView text2 = text("Header: Classic compact • bottom app bar removed", 11, getColor(R.color.hcf_cyan));
        text2.setTypeface(null, 1);
        card.addView(text2);
        card.addView(text("The extra LIVE badge, duplicate header alert button, and native bottom navigation bar stay removed. Forum alerts remain available inside the forum and app drawer.", 10, getColor(R.color.hcf_muted)));
        Switch r2 = toggle("Show startup connection screen", this.prefs.getBoolean("show_startup_screen", true));
        r2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda5
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m159lambda$interfaceCard$21$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r2);
        card.addView(text("Live updates check for new forum activity while the app is open and refresh safely when you are not typing.", 10, getColor(R.color.hcf_muted)));
        return card;
    }

    /* renamed from: lambda$interfaceCard$18$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m156lambda$interfaceCard$18$comharleytgforumdevSettingsActivity(Button button, View view) {
        showPerformanceProfileDialog(button);
    }

    /* renamed from: lambda$interfaceCard$19$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m157lambda$interfaceCard$19$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("live_forum_updates", z).apply();
        AppLogger.info(this, "setting_live_updates", Boolean.toString(z));
        Toast.makeText(this, z ? "Live forum updates enabled." : "Live forum updates disabled.", 0).show();
    }

    /* renamed from: lambda$interfaceCard$20$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m158lambda$interfaceCard$20$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("show_url_bar", z).apply();
        AppLogger.info(this, "setting_url_bar", Boolean.toString(z));
    }

    /* renamed from: lambda$interfaceCard$21$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m159lambda$interfaceCard$21$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("show_startup_screen", z).apply();
        AppLogger.info(this, "setting_startup_screen", Boolean.toString(z));
    }

    private View connectionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Connection", "Primary and backup forum routing"));
        TextView text = text("Current server: checking…", 12, getColor(R.color.hcf_cyan));
        this.serverStatus = text;
        card.addView(text);
        Switch r1 = toggle("Automatically use backup if primary fails", this.prefs.getBoolean("auto_failover", true));
        r1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda13
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m139lambda$connectionCard$22$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r1);
        Switch r12 = toggle("Allow external links to open in browser/apps", this.prefs.getBoolean("external_links", true));
        r12.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda14
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m140lambda$connectionCard$23$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r12);
        card.addView(actionButton("Retry Primary Forum on Next Open", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda15
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m141lambda$connectionCard$24$comharleytgforumdevSettingsActivity(view);
            }
        }));
        TextView text2 = text("Forum links: primary + backup registered with Android", 10, getColor(R.color.hcf_muted));
        text2.setPadding(0, dp(10), 0, 0);
        card.addView(text2);
        card.addView(actionButton("Open Forum Link Settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda16
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m142lambda$connectionCard$25$comharleytgforumdevSettingsActivity(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$connectionCard$22$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m139lambda$connectionCard$22$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("auto_failover", z).apply();
        AppLogger.info(this, "setting_auto_failover", Boolean.toString(z));
    }

    /* renamed from: lambda$connectionCard$23$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m140lambda$connectionCard$23$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("external_links", z).apply();
        AppLogger.info(this, "setting_external_links", Boolean.toString(z));
    }

    /* renamed from: lambda$connectionCard$24$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m141lambda$connectionCard$24$comharleytgforumdevSettingsActivity(View view) {
        this.prefs.edit().remove("fallback_until").putString("active_host", "forum.harleytg.com").apply();
        refreshStatusLabels();
        Toast.makeText(this, "Primary forum restored as preferred server.", 0).show();
        AppLogger.info(this, "fallback_reset", "settings");
    }

    /* renamed from: lambda$connectionCard$25$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m142lambda$connectionCard$25$comharleytgforumdevSettingsActivity(View view) {
        openForumLinkSettings();
    }

    private View privacyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Cookies & Site Data", "Forum data stored on this phone"));
        TextView text = text(cookieSummary(), 12, getColor(R.color.hcf_meta));
        this.cookieStatus = text;
        text.setTypeface(null, 1);
        card.addView(this.cookieStatus);
        card.addView(actionButton("Open Cookie Manager", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda75
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m162lambda$privacyCard$26$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Clear Forum Site Data & Sign Out", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda76
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m164lambda$privacyCard$28$comharleytgforumdevSettingsActivity(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$privacyCard$26$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m162lambda$privacyCard$26$comharleytgforumdevSettingsActivity(View view) {
        startActivity(new Intent(this, (Class<?>) CookieManagerActivity.class));
    }

    /* renamed from: lambda$privacyCard$28$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m164lambda$privacyCard$28$comharleytgforumdevSettingsActivity(View view) {
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(new ValueCallback() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda69
            @Override // android.webkit.ValueCallback
            public final void onReceiveValue(Object obj) {
                SettingsActivity.this.m163lambda$privacyCard$27$comharleytgforumdevSettingsActivity(cookieManager, (Boolean) obj);
            }
        });
        WebStorage.getInstance().deleteAllData();
        ForumIdentity.clear(this);
        AppLogger.info(this, "forum_site_data_cleared", "settings");
        Toast.makeText(this, "Forum cookies and site data cleared.", 1).show();
    }

    /* renamed from: lambda$privacyCard$27$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m163lambda$privacyCard$27$comharleytgforumdevSettingsActivity(CookieManager cookieManager, Boolean bool) {
        cookieManager.flush();
        TextView textView = this.cookieStatus;
        if (textView != null) {
            textView.setText(cookieSummary());
        }
    }

    private View telemetryCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Telemetry & Diagnostics", "Crash reports, app health and privacy controls"));
        TextView text = text(TelemetryService.status(this), 11, getColor(R.color.hcf_meta));
        this.telemetryStatus = text;
        text.setTypeface(null, 1);
        card.addView(this.telemetryStatus);
        Switch r1 = toggle("Enable Telemetry Services", this.prefs.getBoolean("telemetry_enabled", false));
        r1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda46
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m182lambda$telemetryCard$29$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r1);
        final Button actionButton = actionButton("Telemetry level: " + TelemetryService.levelLabel(this), null);
        actionButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda50
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m183lambda$telemetryCard$30$comharleytgforumdevSettingsActivity(actionButton, view);
            }
        });
        card.addView(actionButton);
        card.addView(text("Basic: coarse app health only. Diagnostics: adds crashes, sanitized stack traces, recent app events, and optional WebView/update errors.", 10, getColor(R.color.hcf_muted)));
        Switch r4 = toggle("Automatically send crash reports", this.prefs.getBoolean("telemetry_auto_crash_reports", false));
        r4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda51
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m184lambda$telemetryCard$31$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r4);
        Switch r42 = toggle("Ask me before every crash report", this.prefs.getBoolean("telemetry_ask_before_crash_report", true));
        r42.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda52
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m185lambda$telemetryCard$32$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r42);
        Switch r43 = toggle("Automatically send WebView/update errors", this.prefs.getBoolean("telemetry_auto_error_reports", false));
        r43.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda53
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m186lambda$telemetryCard$33$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r43);
        TextView text2 = text("Report Privacy", 10, getColor(R.color.hcf_cyan));
        text2.setTypeface(null, 1);
        text2.setPadding(0, dp(8), 0, 0);
        card.addView(text2);
        Switch r3 = toggle("Include my forum identity with reports", this.prefs.getBoolean("telemetry_include_identity", false));
        r3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda54
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m187lambda$telemetryCard$34$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r3);
        Switch r32 = toggle("Include my email when identity is included", this.prefs.getBoolean("telemetry_include_email", false));
        r32.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda55
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m188lambda$telemetryCard$35$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r32);
        Switch r33 = toggle("Include device manufacturer/model", this.prefs.getBoolean("telemetry_include_device_model", false));
        r33.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda56
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m189lambda$telemetryCard$36$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r33);
        Switch r34 = toggle("Include sanitized forum route", this.prefs.getBoolean("telemetry_include_route", false));
        r34.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda57
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m190lambda$telemetryCard$37$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r34);
        card.addView(actionButton("Send Diagnostic Feedback", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda58
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m191lambda$telemetryCard$38$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Preview Telemetry Report", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda47
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m192lambda$telemetryCard$39$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("View Report History", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda48
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m193lambda$telemetryCard$40$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Clear Local Telemetry Reports", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda49
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m194lambda$telemetryCard$41$comharleytgforumdevSettingsActivity(view);
            }
        }));
        TextView text3 = text("Crash reports get an HCF report ID and can include a sanitized stack trace plus recent app events. Identity, email, device model and page route are separate opt-ins. Passwords, cookies, access/session tokens, recovery codes, provider IDs, posts, messages and page contents are never sent. The Discord endpoint remains encrypted at rest in the APK, but a server-side relay is still safer for long-term webhook secrecy.", 10, getColor(R.color.hcf_muted));
        text3.setPadding(0, dp(8), 0, 0);
        card.addView(text3);
        return card;
    }

    /* renamed from: lambda$telemetryCard$29$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m182lambda$telemetryCard$29$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_enabled", z).apply();
        AppLogger.info(this, "setting_telemetry", Boolean.toString(z));
        if (z) {
            TelemetryService.sendEvent(this, "telemetry_enabled", "User enabled Telemetry Services in App Settings");
            Toast.makeText(this, "Telemetry Services enabled.", 0).show();
        } else {
            Toast.makeText(this, "Telemetry disabled. No reports will be sent.", 0).show();
        }
        refreshStatusLabels();
    }

    /* renamed from: lambda$telemetryCard$30$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m183lambda$telemetryCard$30$comharleytgforumdevSettingsActivity(Button button, View view) {
        String str = "diagnostics".equals(TelemetryService.level(this)) ? "basic" : "diagnostics";
        this.prefs.edit().putString("telemetry_level", str).apply();
        button.setText("Telemetry level: " + TelemetryService.levelLabel(this));
        AppLogger.info(this, "setting_telemetry_level", str);
        refreshStatusLabels();
    }

    /* renamed from: lambda$telemetryCard$31$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m184lambda$telemetryCard$31$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_auto_crash_reports", z).apply();
        AppLogger.info(this, "setting_auto_crash_reports", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$32$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m185lambda$telemetryCard$32$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_ask_before_crash_report", z).apply();
        AppLogger.info(this, "setting_ask_crash_report", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$33$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m186lambda$telemetryCard$33$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_auto_error_reports", z).apply();
        AppLogger.info(this, "setting_auto_error_reports", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$34$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m187lambda$telemetryCard$34$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_include_identity", z).apply();
        AppLogger.info(this, "setting_telemetry_identity", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$35$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m188lambda$telemetryCard$35$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_include_email", z).apply();
        AppLogger.info(this, "setting_telemetry_email", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$36$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m189lambda$telemetryCard$36$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_include_device_model", z).apply();
        AppLogger.info(this, "setting_telemetry_device", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$37$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m190lambda$telemetryCard$37$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("telemetry_include_route", z).apply();
        AppLogger.info(this, "setting_telemetry_route", Boolean.toString(z));
    }

    /* renamed from: lambda$telemetryCard$38$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m191lambda$telemetryCard$38$comharleytgforumdevSettingsActivity(View view) {
        TelemetryService.showManualFeedbackDialog(this);
    }

    /* renamed from: lambda$telemetryCard$39$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m192lambda$telemetryCard$39$comharleytgforumdevSettingsActivity(View view) {
        TelemetryService.showPreview(this);
    }

    /* renamed from: lambda$telemetryCard$40$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m193lambda$telemetryCard$40$comharleytgforumdevSettingsActivity(View view) {
        TelemetryService.showHistory(this);
    }

    /* renamed from: lambda$telemetryCard$41$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m194lambda$telemetryCard$41$comharleytgforumdevSettingsActivity(View view) {
        TelemetryService.clearLocalReports(this);
        Toast.makeText(this, "Local telemetry reports and breadcrumbs cleared.", 0).show();
        refreshStatusLabels();
    }

    private View securityCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Permissions & Security", "Android permissions and app hardening"));
        TextView text = text(AppSecurity.securitySummary(this), 11, getColor(R.color.hcf_meta));
        this.securityStatus = text;
        card.addView(text);
        card.addView(actionButton("Allow Notification Permission", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda31
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m165lambda$securityCard$42$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Allow Secure App Updates", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda32
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m166lambda$securityCard$43$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Android App Permission Settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda33
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m167lambda$securityCard$44$comharleytgforumdevSettingsActivity(view);
            }
        }));
        TextView text2 = text("No location, contacts, microphone, camera, or broad storage permission is requested. Update APKs are accepted only from HCF's trusted release source and must match the installed package and signing certificate.", 10, getColor(R.color.hcf_muted));
        text2.setPadding(0, dp(8), 0, 0);
        card.addView(text2);
        return card;
    }

    /* renamed from: lambda$securityCard$42$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m165lambda$securityCard$42$comharleytgforumdevSettingsActivity(View view) {
        requestNotificationPermissionIfNeeded();
    }

    /* renamed from: lambda$securityCard$43$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m166lambda$securityCard$43$comharleytgforumdevSettingsActivity(View view) {
        if (AppSecurity.canInstallUpdates(this)) {
            Toast.makeText(this, "Secure app update installation is already allowed.", 0).show();
            refreshStatusLabels();
            return;
        }
        try {
            startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())));
        } catch (Throwable unused) {
            Toast.makeText(this, "Android could not open the update install permission screen.", 1).show();
        }
    }

    /* renamed from: lambda$securityCard$44$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m167lambda$securityCard$44$comharleytgforumdevSettingsActivity(View view) {
        try {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
        } catch (Throwable unused) {
            Toast.makeText(this, "Android app settings could not be opened.", 1).show();
        }
    }

    private View diagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("Error & Recovery Center", "Troubleshooting and smart-recovery tools"));
        card.addView(text("HCF error codes are enabled • network, server, SSL, WebView and update failures use friendly native explanations.", 10, getColor(R.color.hcf_muted)));
        card.addView(actionButton("Run Error & Recovery Check", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda24
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m151xf21cbf(view);
            }
        }));
        card.addView(actionButton("Copy Sanitized Diagnostic Report", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda25
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m152x8d9247c0(view);
            }
        }));
        card.addView(actionButton("View App & Crash Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda26
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m153x1a3272c1(view);
            }
        }));
        card.addView(actionButton("Clear WebView Cache", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda27
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m154xa6d29dc2(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$diagnosticsCard$45$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m151xf21cbf(View view) {
        showRecoveryDiagnostics();
    }

    /* renamed from: lambda$diagnosticsCard$46$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m152x8d9247c0(View view) {
        copyDiagnosticReport();
    }

    /* renamed from: lambda$diagnosticsCard$47$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m153x1a3272c1(View view) {
        startActivity(new Intent(this, (Class<?>) LogsActivity.class));
    }

    /* renamed from: lambda$diagnosticsCard$48$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m154xa6d29dc2(View view) {
        try {
            WebView webView = new WebView(this);
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            AppLogger.info(this, "webview_cache_cleared", "settings");
            Toast.makeText(this, "WebView cache cleared.", 0).show();
        } catch (Throwable th) {
            AppLogger.error(this, "webview_cache_clear", th.getClass().getSimpleName());
            Toast.makeText(this, "WebView cache could not be cleared on this device.", 0).show();
        }
    }

    private void postTestAlert(String str, String str2, String str3) {
        String string = this.prefs.getString("active_host", "forum.harleytg.com");
        String str4 = ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
        StringBuilder sb = new StringBuilder("https://");
        sb.append(str4);
        if (str3 == null) {
            str3 = "/notifications";
        }
        sb.append(str3);
        NotificationHelper.postTest(this, str, str2, sb.toString());
        AppLogger.info(this, "notification_test", str);
    }

    private void showRecoveryDiagnostics() {
        String string = this.prefs.getString("active_host", "forum.harleytg.com");
        String str = ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
        boolean isValidatedNetworkAvailable = isValidatedNetworkAvailable();
        String str2 = "Unknown";
        try {
            PackageInfo currentWebViewPackage = WebView.getCurrentWebViewPackage();
            if (currentWebViewPackage != null) {
                str2 = currentWebViewPackage.packageName + " • " + currentWebViewPackage.versionName;
            }
        } catch (Throwable unused) {
        }
        String string2 = this.prefs.getString("last_recoverable_url", "");
        String safeUrl = (string2 == null || string2.trim().isEmpty()) ? "Not recorded yet" : AppLogger.safeUrl(string2);
        StringBuilder sb = new StringBuilder("Network: ");
        sb.append(isValidatedNetworkAvailable ? "✓ Connected" : "✕ Offline");
        sb.append("\nCurrent server: ");
        sb.append(str);
        sb.append("\nAutomatic failover: ");
        sb.append(this.prefs.getBoolean("auto_failover", true) ? "✓ Enabled" : "Disabled");
        sb.append("\nWebView provider: ");
        sb.append(str2);
        sb.append("\nRenderer recovery: ✓ Enabled (HCF-WV-001)\nSSL fail-closed: ✓ Enabled (HCF-SSL-001)\nLast recoverable route: ");
        sb.append(safeUrl);
        new AlertDialog.Builder(this).setTitle("Error & Recovery Check").setMessage(sb.toString()).setPositiveButton("OK", (DialogInterface.OnClickListener) null).setNeutralButton("View logs", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda19
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.m173x332ab745(dialogInterface, i);
            }
        }).show();
        AppLogger.info(this, "recovery_diagnostics", isValidatedNetworkAvailable ? "network-ok" : "offline");
    }

    /* renamed from: lambda$showRecoveryDiagnostics$49$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m173x332ab745(DialogInterface dialogInterface, int i) {
        startActivity(new Intent(this, (Class<?>) LogsActivity.class));
    }

    private boolean isValidatedNetworkAvailable() {
        Network activeNetwork;
        NetworkCapabilities networkCapabilities;
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
            if (connectivityManager == null || (activeNetwork = connectivityManager.getActiveNetwork()) == null || (networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)) == null) {
                return false;
            }
            return networkCapabilities.hasCapability(12);
        } catch (Throwable unused) {
            return false;
        }
    }

    private void copyDiagnosticReport() {
        String str;
        String string = this.prefs.getString("active_host", "forum.harleytg.com");
        String string2 = this.prefs.getString("notification_last_sync_status", "not synced yet");
        long j = this.prefs.getLong("notification_last_sync_latency_ms", 0L);
        StringBuilder sb = new StringBuilder("Harley's Clan Forum • Sanitized Diagnostic Report\nApp: 1.0 (10000072)\nPackage: ");
        sb.append(getPackageName());
        sb.append("\nAndroid SDK: ");
        sb.append(Build.VERSION.SDK_INT);
        sb.append("\nDevice: ");
        sb.append(Build.MANUFACTURER);
        sb.append(" ");
        sb.append(Build.MODEL);
        sb.append("\nForum host: ");
        sb.append(string);
        sb.append("\nTheme: ");
        sb.append(ThemeManager.label(this));
        sb.append("\nPerformance profile: ");
        sb.append(PerformanceProfile.settingLabel(this, this.prefs));
        sb.append("\nNotifications: ");
        sb.append(NotificationHelper.status(this));
        sb.append("\nLive sync: ");
        sb.append(string2);
        if (j > 0) {
            str = " • " + j + " ms";
        } else {
            str = "";
        }
        sb.append(str);
        sb.append("\nAuto failover: ");
        sb.append(this.prefs.getBoolean("auto_failover", true) ? "On" : "Off");
        sb.append("\nRenderer recovery: Enabled (HCF-WV-001)\nLast route: ");
        sb.append(AppLogger.safeUrl(this.prefs.getString("last_recoverable_url", "")));
        sb.append("\nCookies/tokens/passwords/email: Not included");
        String sb2 = sb.toString();
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
            if (clipboardManager == null) {
                throw new IllegalStateException("Clipboard unavailable");
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF diagnostic report", sb2));
            Toast.makeText(this, "Sanitized diagnostic report copied.", 0).show();
            AppLogger.info(this, "diagnostic_report_copy", "sanitized");
        } catch (Throwable unused) {
            Toast.makeText(this, "Could not copy the diagnostic report.", 0).show();
        }
    }

    private View updateCard() {
        String sb;
        LinearLayout card = card();
        card.addView(sectionTitle("App Updates", "Secure automatic app updates"));
        TextView text = text(updateChannelLine(effectiveUpdateChannel()), 12, getColor(R.color.hcf_meta));
        this.updateChannelStatus = text;
        text.setTypeface(null, 1);
        card.addView(this.updateChannelStatus);
        long j = this.prefs.getLong("update_last_check", 0L);
        if (j <= 0) {
            sb = "Not checked yet on this install";
        } else {
            StringBuilder sb2 = new StringBuilder("Last checked ");
            String str = "just now";
            if (!"just now".equals(formatAge(System.currentTimeMillis() - j))) {
                str = formatAge(System.currentTimeMillis() - j) + " ago";
            }
            sb2.append(str);
            sb = sb2.toString();
        }
        TextView text2 = text("Installed • v1.0\n" + sb, 11, getColor(R.color.hcf_muted));
        this.updateStatus = text2;
        card.addView(text2);
        Switch r1 = toggle("Automatic update checks", this.prefs.getBoolean("update_auto_check", true));
        r1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda6
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m197lambda$updateCard$50$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r1);
        Switch r12 = toggle("Automatically download new APKs", this.prefs.getBoolean("update_auto_download", false));
        r12.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda7
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m198lambda$updateCard$51$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r12);
        Switch r13 = toggle("Open installer automatically after download", this.prefs.getBoolean("update_auto_install", true));
        r13.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda8
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                SettingsActivity.this.m199lambda$updateCard$52$comharleytgforumdevSettingsActivity(compoundButton, z);
            }
        });
        card.addView(r13);
        card.addView(actionButton("Check for Updates", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m200lambda$updateCard$54$comharleytgforumdevSettingsActivity(view);
            }
        }));
        Button actionButton = actionButton("Download Update Now", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m201lambda$updateCard$55$comharleytgforumdevSettingsActivity(view);
            }
        });
        this.updateDownloadButton = actionButton;
        actionButton.setVisibility(8);
        card.addView(this.updateDownloadButton);
        Button actionButton2 = actionButton("Install Downloaded Update", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m202lambda$updateCard$56$comharleytgforumdevSettingsActivity(view);
            }
        });
        this.updateInstallButton = actionButton2;
        actionButton2.setVisibility(AppUpdateDownloader.isDownloaded(this) ? 0 : 8);
        card.addView(this.updateInstallButton);
        TextView text3 = text("After an update APK finishes downloading, HCF verifies its package, versionCode, and signing certificate, then opens Android’s installer automatically. Android still requires your confirmation to install. If Android asks for “Allow from this source,” HCF resumes the verified installer when you return. After a successful in-place update, the temporary APK is removed automatically.", 10, getColor(R.color.hcf_muted));
        text3.setPadding(0, dp(8), 0, 0);
        card.addView(text3);
        return card;
    }

    /* renamed from: lambda$updateCard$50$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m197lambda$updateCard$50$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("update_auto_check", z).apply();
        UpdateScheduler.apply(this);
        AppLogger.info(this, "setting_update_auto_check", Boolean.toString(z));
    }

    /* renamed from: lambda$updateCard$51$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m198lambda$updateCard$51$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("update_auto_download", z).apply();
        AppLogger.info(this, "setting_update_auto_download", Boolean.toString(z));
    }

    /* renamed from: lambda$updateCard$52$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m199lambda$updateCard$52$comharleytgforumdevSettingsActivity(CompoundButton compoundButton, boolean z) {
        this.prefs.edit().putBoolean("update_auto_install", z).apply();
        AppLogger.info(this, "setting_update_auto_install", Boolean.toString(z));
    }

    private /* synthetic */ void lambda$updateCard$53(View view) {
        showUpdateChannelDialog();
    }

    /* renamed from: lambda$updateCard$54$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m200lambda$updateCard$54$comharleytgforumdevSettingsActivity(View view) {
        checkForUpdates(true);
    }

    /* renamed from: lambda$updateCard$55$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m201lambda$updateCard$55$comharleytgforumdevSettingsActivity(View view) {
        String str;
        UpdateChecker.Release release = this.availableRelease;
        if (release != null && release.apkUrl != null && !this.availableRelease.apkUrl.isEmpty()) {
            long enqueue = AppUpdateDownloader.enqueue(this, this.availableRelease, true);
            if (enqueue > 0) {
                Toast.makeText(this, "Update download started.", 0).show();
                TextView textView = this.updateStatus;
                StringBuilder sb = new StringBuilder("Downloading Beta build");
                if (this.availableRelease.versionCode > 0) {
                    str = " " + this.availableRelease.versionCode;
                } else {
                    str = "";
                }
                sb.append(str);
                sb.append("… The Android installer will open automatically when verification finishes.");
                textView.setText(sb.toString());
                watchUpdateDownloadForAutoInstall(enqueue);
                return;
            }
            Toast.makeText(this, "Update download could not start.", 1).show();
            return;
        }
        Toast.makeText(this, "This release does not have an APK asset yet.", 1).show();
    }

    /* renamed from: lambda$updateCard$56$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m202lambda$updateCard$56$comharleytgforumdevSettingsActivity(View view) {
        installDownloadedUpdate();
    }

    private String updateChannelLine(String str) {
        return "Channel: ".concat("stable".equalsIgnoreCase(str) ? "Stable • Official Releases" : "Development / Beta • Preview Releases");
    }

    private String effectiveUpdateChannel() {
        return "dev";
    }

    private void showUpdateChannelDialog() {
        int i = !"stable".equalsIgnoreCase(this.prefs.getString("update_channel", "dev")) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("Update Channel").setSingleChoiceItems(new String[]{"Stable — Official releases", "Dev — Preview releases"}, i, (DialogInterface.OnClickListener) null).setPositiveButton("Save", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda28
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                SettingsActivity.this.m178xef5a4277(dialogInterface, i2);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$showUpdateChannelDialog$57$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m178xef5a4277(DialogInterface dialogInterface, int i) {
        String str = ((AlertDialog) dialogInterface).getListView().getCheckedItemPosition() == 0 ? "stable" : "dev";
        this.prefs.edit().putString("update_channel", str).apply();
        this.availableRelease = null;
        Button button = this.updateDownloadButton;
        if (button != null) {
            button.setVisibility(8);
        }
        Button button2 = this.updateInstallButton;
        if (button2 != null) {
            button2.setVisibility(AppUpdateDownloader.isDownloaded(this) ? 0 : 8);
        }
        TextView textView = this.updateChannelStatus;
        if (textView != null) {
            textView.setText(updateChannelLine(str));
        }
        TextView textView2 = this.updateStatus;
        if (textView2 != null) {
            textView2.setText("Channel changed. Tap Check for Updates.");
        }
        AppLogger.info(this, "update_channel", str);
        checkForUpdates(true);
    }

    private void checkForUpdates(final boolean z) {
        if (this.updateStatus == null) {
            return;
        }
        final String effectiveUpdateChannel = effectiveUpdateChannel();
        this.updateStatus.setText("Checking " + channelDisplayName(effectiveUpdateChannel) + " release channel…");
        this.updateStatus.setTextColor(getColor(R.color.hcf_meta));
        Button button = this.updateDownloadButton;
        if (button != null) {
            button.setVisibility(8);
        }
        this.availableRelease = null;
        UpdateChecker.check(this, effectiveUpdateChannel, new UpdateChecker.Callback() { // from class: com.harleytg.forum.dev.SettingsActivity.2
            @Override // com.harleytg.forum.dev.UpdateChecker.Callback
            public void onResult(UpdateChecker.Release release, boolean z2) {
                String str;
                SettingsActivity.this.availableRelease = release;
                SettingsActivity.this.prefs.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                String str2 = release.prerelease ? "Pre-release" : "Latest stable";
                String str3 = (release.apkName == null || release.apkName.isEmpty()) ? "No APK asset attached" : release.apkName;
                String str4 = "";
                if (z2) {
                    TextView textView = SettingsActivity.this.updateStatus;
                    StringBuilder sb = new StringBuilder("Beta Update Available\nInstalled: v1.0 (10000072)\nAvailable: v");
                    sb.append(UpdateChecker.displayVersion(release));
                    if (release.versionCode > 0) {
                        str = " (" + release.versionCode + ")";
                    } else {
                        str = "";
                    }
                    sb.append(str);
                    sb.append("\n");
                    sb.append(str2);
                    sb.append(" • ");
                    sb.append(str3);
                    textView.setText(sb.toString());
                    SettingsActivity.this.updateStatus.setTextColor(SettingsActivity.this.getColor(R.color.hcf_accent_text));
                    if (SettingsActivity.this.updateDownloadButton != null && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        SettingsActivity.this.updateDownloadButton.setVisibility(0);
                    }
                    if (SettingsActivity.this.prefs.getBoolean("update_auto_download", false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        long enqueue = AppUpdateDownloader.enqueue(SettingsActivity.this, release, z);
                        if (enqueue > 0) {
                            SettingsActivity.this.updateStatus.append("\nAutomatic download queued • installer opens when ready.");
                            SettingsActivity.this.watchUpdateDownloadForAutoInstall(enqueue);
                        }
                    }
                    if (z) {
                        SettingsActivity settingsActivity = SettingsActivity.this;
                        StringBuilder sb2 = new StringBuilder("Beta update available");
                        if (release.versionCode > 0) {
                            str4 = " • build " + release.versionCode;
                        }
                        sb2.append(str4);
                        Toast.makeText(settingsActivity, sb2.toString(), 1).show();
                    }
                } else if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                    TextView textView2 = SettingsActivity.this.updateStatus;
                    StringBuilder sb3 = new StringBuilder("Installed build is newer\nInstalled: v1.0 (10000072)\nPublished: v");
                    sb3.append(UpdateChecker.displayVersion(release));
                    if (release.versionCode > 0) {
                        str4 = " (" + release.versionCode + ")";
                    }
                    sb3.append(str4);
                    sb3.append(" • ");
                    sb3.append(str3);
                    textView2.setText(sb3.toString());
                    SettingsActivity.this.updateStatus.setTextColor(SettingsActivity.this.getColor(R.color.hcf_meta));
                    if (z) {
                        Toast.makeText(SettingsActivity.this, "The published release feed is behind this installed build.", 0).show();
                    }
                } else {
                    TextView textView3 = SettingsActivity.this.updateStatus;
                    StringBuilder sb4 = new StringBuilder("Up to date\nInstalled: v1.0 (10000072)\nNewest ");
                    sb4.append(effectiveUpdateChannel);
                    sb4.append(": v");
                    sb4.append(UpdateChecker.displayVersion(release));
                    if (release.versionCode > 0) {
                        str4 = " (" + release.versionCode + ")";
                    }
                    sb4.append(str4);
                    sb4.append(" • ");
                    sb4.append(str3);
                    textView3.setText(sb4.toString());
                    SettingsActivity.this.updateStatus.setTextColor(SettingsActivity.this.getColor(R.color.hcf_meta));
                    if (z) {
                        Toast.makeText(SettingsActivity.this, "This build is up to date for " + effectiveUpdateChannel + ".", 0).show();
                    }
                }
                AppLogger.info(SettingsActivity.this, "update_check", effectiveUpdateChannel + " | " + release.tag + " | newer=" + z2);
            }

            @Override // com.harleytg.forum.dev.UpdateChecker.Callback
            public void onError(String str) {
                SettingsActivity.this.updateStatus.setText("Update check unavailable • " + str);
                SettingsActivity.this.updateStatus.setTextColor(SettingsActivity.this.getColor(R.color.hcf_yellow));
                if (z) {
                    Toast.makeText(SettingsActivity.this, str, 1).show();
                }
                AppLogger.error(SettingsActivity.this, "update_check", str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void watchUpdateDownloadForAutoInstall(final long j) {
        if (j <= 0 || !this.prefs.getBoolean("update_auto_install", true)) {
            return;
        }
        final Runnable[] runnableArr = new Runnable[1];
        runnableArr[0] = new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda73
            @Override // java.lang.Runnable
            public final void run() {
                SettingsActivity.this.m203xd7706e7a(j, runnableArr);
            }
        };
        ScrollView scrollView = this.settingsScroll;
        if (scrollView != null) {
            scrollView.postDelayed(runnableArr[0], 500L);
        }
    }

    /* renamed from: lambda$watchUpdateDownloadForAutoInstall$58$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m203xd7706e7a(long j, Runnable[] runnableArr) {
        ScrollView scrollView;
        if (isFinishing() || isDestroyed() || !this.prefs.getBoolean("update_auto_install", true)) {
            return;
        }
        AppUpdateDownloader.ProgressSnapshot progress = AppUpdateDownloader.progress(this, j);
        if (progress.status == 8) {
            m155x154e458f(j);
        } else {
            if (progress.status == 16 || (scrollView = this.settingsScroll) == null) {
                return;
            }
            scrollView.postDelayed(runnableArr[0], 500L);
        }
    }

    private void handleInstallIntent(Intent intent) {
        if (intent == null || !"com.harleytg.forum.dev.INSTALL_UPDATE".equals(intent.getAction())) {
            return;
        }
        final long longExtra = intent.getLongExtra("download_id", -1L);
        if (longExtra > 0) {
            getWindow().getDecorView().postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda18
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.this.m155x154e458f(longExtra);
                }
            }, 250L);
        }
    }

    private void installDownloadedUpdate() {
        m155x154e458f(AppUpdateDownloader.downloadedId(this));
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: installDownloadedUpdate, reason: merged with bridge method [inline-methods] */
    public void m155x154e458f(long j) {
        if (j <= 0) {
            Toast.makeText(this, "No downloaded update is ready yet.", 0).show();
            return;
        }
        if (getPackageManager().canRequestPackageInstalls()) {
            if (AppUpdateDownloader.openInstaller(this, j)) {
                return;
            }
            Toast.makeText(this, "The Android installer could not open this download.", 1).show();
            return;
        }
        try {
            this.prefs.edit().putBoolean("update_resume_after_permission", true).apply();
            startActivityForResult(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            Toast.makeText(this, "Allow installs from this source. HCF will resume the verified update automatically when you return.", 1).show();
        } catch (Throwable unused) {
            this.prefs.edit().remove("update_resume_after_permission").apply();
            Toast.makeText(this, "Android blocked installation permission settings.", 1).show();
        }
    }

    private void resumeUpdateInstallAfterPermission() {
        if (this.prefs.getBoolean("update_resume_after_permission", false) && AppSecurity.canInstallUpdates(this)) {
            this.prefs.edit().remove("update_resume_after_permission").apply();
            long downloadedId = AppUpdateDownloader.downloadedId(this);
            if (downloadedId > 0) {
                Toast.makeText(this, "Install permission enabled • opening verified update…", 0).show();
                if (AppUpdateDownloader.openInstaller(this, downloadedId)) {
                    return;
                }
                Toast.makeText(this, "The Android installer could not open this verified update.", 1).show();
            }
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == UPDATE_INSTALL_PERMISSION_REQUEST) {
            if (AppSecurity.canInstallUpdates(this)) {
                resumeUpdateInstallAfterPermission();
            } else {
                this.prefs.edit().remove("update_resume_after_permission").apply();
                Toast.makeText(this, "Install permission was not enabled. The downloaded APK was kept.", 1).show();
            }
        }
    }

    private void openSupportEmail() {
        try {
            startActivity(new Intent(this, (Class<?>) SupportContactActivity.class));
            AppLogger.info(this, "support_contact_open", "about");
        } catch (Throwable th) {
            Toast.makeText(this, "Unable to open Contact Support.", 1).show();
            AppLogger.error(this, "support_contact_open", th.getClass().getSimpleName());
        }
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.addView(settingsInfoCard("App identity", "Harley's Clan Forum [Beta]\nVersion 1.0 • Build 10000072\nHarley's Clan Forum v1.0 [Development Build / Beta]", R.drawable.fa_circle_info));
        card.addView(settingsInfoCard("Build & channel", "Channel: Dev • Update feed: dev\nPackage: " + getPackageName() + "\nAPK: Harley's Clan Forum [Beta].apk", R.drawable.fa_download));
        card.addView(settingsInfoCard("Device & runtime", "Android SDK " + Build.VERSION.SDK_INT + " • " + Build.MANUFACTURER + " " + Build.MODEL + "\nTheme: " + ThemeManager.label(this) + "\n" + PerformanceProfile.settingLabel(this, this.prefs) + " • Network: " + RuntimeState.networkType(this), R.drawable.fa_gear));
        card.addView(settingsInfoCard("Forum endpoints", "Primary: forum.harleytg.com\nBackup: harleysclan.freeflarum.com", R.drawable.fa_globe));
        card.addView(settingsSubsectionHeader("Release & support", "Release notes, support and portable build information", R.drawable.fa_circle_info));
        card.addView(actionButton("View What's New • v1.0", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda20
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m130lambda$aboutCard$60$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Release & Build Details", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda21
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m131lambda$aboutCard$61$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Copy App Information", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda22
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m132lambda$aboutCard$62$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(actionButton("Contact Support", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda23
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m133lambda$aboutCard$63$comharleytgforumdevSettingsActivity(view);
            }
        }));
        card.addView(settingsInfoCard("Privacy", "Sanitized reports never include passwords, cookies, access/session tokens, recovery codes, posts, messages or page contents.", R.drawable.fa_shield));
        return card;
    }

    /* renamed from: lambda$aboutCard$60$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m130lambda$aboutCard$60$comharleytgforumdevSettingsActivity(View view) {
        ReleaseNotes.showCustom(this, this.prefs, true);
    }

    /* renamed from: lambda$aboutCard$61$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m131lambda$aboutCard$61$comharleytgforumdevSettingsActivity(View view) {
        showBuildDetails();
    }

    /* renamed from: lambda$aboutCard$62$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m132lambda$aboutCard$62$comharleytgforumdevSettingsActivity(View view) {
        copyAboutInformation();
    }

    /* renamed from: lambda$aboutCard$63$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m133lambda$aboutCard$63$comharleytgforumdevSettingsActivity(View view) {
        openSupportEmail();
    }

    private void copyAboutInformation() {
        String str = "Harley's Clan Forum [Beta]\nVersion: 1.0 (10000072)\nChannel: Dev / dev\nPackage: " + getPackageName() + "\nAndroid SDK: " + Build.VERSION.SDK_INT + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\nTheme: " + ThemeManager.label(this) + "\nPerformance: " + PerformanceProfile.settingLabel(this, this.prefs) + "\nPrimary forum: forum.harleytg.com\nBackup forum: harleysclan.freeflarum.com";
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF app information", str));
        }
        Toast.makeText(this, "App information copied.", 0).show();
    }

    private void showBuildDetails() {
        new AlertDialog.Builder(this).setTitle("Release & Build Details").setMessage("Harley's Clan Forum Android app\n\nVersion: 1.0\nVersion code: 10000072\nChannel: Dev\nPackage: " + getPackageName() + "\nAPK: Harley's Clan Forum [Beta].apk\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL).setPositiveButton("Close", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshStatusLabels() {
        String str;
        if (this.liveSyncStatus != null) {
            String string = this.prefs.getString("notification_last_sync_status", "Waiting for first sync");
            long j = this.prefs.getLong("notification_last_sync_at", 0L);
            long j2 = this.prefs.getLong("notification_last_sync_latency_ms", 0L);
            String formatAge = formatAge(System.currentTimeMillis() - j);
            if (j <= 0) {
                formatAge = "not synced yet";
            } else if (!"just now".equals(formatAge)) {
                formatAge = formatAge + " ago";
            }
            TextView textView = this.liveSyncStatus;
            StringBuilder sb = new StringBuilder("Live sync: ");
            sb.append(string);
            sb.append(" • ");
            sb.append(formatAge);
            if (j2 > 0) {
                str = " • " + j2 + " ms";
            } else {
                str = "";
            }
            sb.append(str);
            sb.append("\n");
            sb.append(PerformanceProfile.settingLabel(this, this.prefs));
            sb.append(" • ");
            sb.append(RuntimeDiagnostics.notificationMode());
            sb.append(" • poll ");
            sb.append(formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs()));
            sb.append(" • ");
            sb.append(RuntimeState.networkType(this));
            textView.setText(sb.toString());
        }
        TextView textView2 = this.notificationStatus;
        int i = R.color.hcf_yellow;
        if (textView2 != null) {
            NotificationHelper.createChannel(this);
            boolean canPost = NotificationHelper.canPost(this);
            boolean headsUpChannelReady = NotificationHelper.headsUpChannelReady(this);
            String str2 = NotificationHelper.status(this) + " • channel importance=" + NotificationHelper.channelImportance(this);
            this.notificationStatus.setText("Status: " + str2);
            this.notificationStatus.setTextColor((canPost && headsUpChannelReady) ? getColor(R.color.hcf_accent_text) : getColor(R.color.hcf_yellow));
        }
        TextView textView3 = this.cookieStatus;
        if (textView3 != null) {
            textView3.setText(cookieSummary());
        }
        TextView textView4 = this.securityStatus;
        if (textView4 != null) {
            textView4.setText(AppSecurity.securitySummary(this));
        }
        TextView textView5 = this.telemetryStatus;
        if (textView5 != null) {
            textView5.setText(TelemetryService.status(this));
        }
        TextView textView6 = this.updateChannelStatus;
        if (textView6 != null) {
            textView6.setText(updateChannelLine(effectiveUpdateChannel()));
        }
        Button button = this.updateInstallButton;
        if (button != null) {
            button.setVisibility(AppUpdateDownloader.isDownloaded(this) ? 0 : 8);
        }
        if (this.serverStatus != null) {
            String string2 = this.prefs.getString("active_host", "forum.harleytg.com");
            boolean equalsIgnoreCase = "forum.harleytg.com".equalsIgnoreCase(string2);
            TextView textView7 = this.serverStatus;
            StringBuilder sb2 = new StringBuilder("Current server: ");
            sb2.append(equalsIgnoreCase ? "Primary • " : "Backup • ");
            sb2.append(string2);
            textView7.setText(sb2.toString());
            TextView textView8 = this.serverStatus;
            if (equalsIgnoreCase) {
                i = R.color.hcf_cyan;
            }
            textView8.setTextColor(getColor(i));
        }
    }

    private String cookieSummary() {
        CookieManager cookieManager = CookieManager.getInstance();
        int countCookies = countCookies(cookieManager.getCookie("https://forum.harleytg.com/"));
        int countCookies2 = countCookies(cookieManager.getCookie("https://harleysclan.freeflarum.com/"));
        return "Cookie data: " + (countCookies + countCookies2) + " visible • Primary " + countCookies + " • Backup " + countCookies2;
    }

    private String formatRuntimeInterval(long j) {
        if (j <= 0) {
            return "idle";
        }
        if (j < 1000) {
            return j + "ms";
        }
        if (j % 1000 != 0) {
            return String.format(Locale.US, "%.2fs", Double.valueOf(j / 1000.0d));
        }
        return (j / 1000) + "s";
    }

    private String formatAge(long j) {
        long max = Math.max(0L, j / 1000);
        if (max < 2) {
            return "just now";
        }
        if (max < 60) {
            return max + " seconds";
        }
        long j2 = max / 60;
        if (j2 < 60) {
            StringBuilder sb = new StringBuilder();
            sb.append(j2);
            sb.append(j2 == 1 ? " minute" : " minutes");
            return sb.toString();
        }
        long j3 = j2 / 60;
        StringBuilder sb2 = new StringBuilder();
        sb2.append(j3);
        sb2.append(j3 == 1 ? " hour" : " hours");
        return sb2.toString();
    }

    private int countCookies(String str) {
        if (str == null || str.trim().isEmpty()) {
            return 0;
        }
        int i = 0;
        for (String str2 : str.split(";")) {
            if (!str2.trim().isEmpty()) {
                i++;
            }
        }
        return i;
    }

    private ImageView settingsSectionIcon(int i) {
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(i);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setColorFilter(getColor(R.color.hcf_cyan));
        imageView.setContentDescription(null);
        return imageView;
    }

    private int settingsIconForKey(String str) {
        return "account_security".equals(str) ? R.drawable.fa_shield : "notifications".equals(str) ? R.drawable.fa_bell : "appearance".equals(str) ? R.drawable.fa_gear : "forum_data".equals(str) ? R.drawable.fa_globe : "advanced".equals(str) ? R.drawable.fa_circle_info : R.drawable.fa_gear;
    }

    private int settingsIconForTitle(String str) {
        String lowerCase = str == null ? "" : str.toLowerCase(Locale.US);
        return (lowerCase.contains("account") || lowerCase.contains("identity")) ? R.drawable.fa_user : (lowerCase.contains("permission") || lowerCase.contains("security")) ? R.drawable.fa_shield : lowerCase.contains("notification") ? R.drawable.fa_bell : (lowerCase.contains("appearance") || lowerCase.contains("performance")) ? R.drawable.fa_gear : (lowerCase.contains("connection") || lowerCase.contains("routing")) ? R.drawable.fa_globe : (lowerCase.contains("cookie") || lowerCase.contains("site data")) ? R.drawable.fa_lock : lowerCase.contains("update") ? R.drawable.fa_download : (lowerCase.contains("error") || lowerCase.contains("recovery")) ? R.drawable.fa_triangle_exclamation : lowerCase.contains("telemetry") ? R.drawable.fa_circle_info : lowerCase.contains("developer") ? R.drawable.fa_bug : lowerCase.contains("about") ? R.drawable.fa_circle_info : R.drawable.fa_gear;
    }

    private View connectedSettingsPanel(String str, String str2, View view, boolean z) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(compact() ? 8 : 12);
        linearLayout.setLayoutParams(layoutParams);
        final LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setClickable(true);
        linearLayout2.setFocusable(true);
        linearLayout2.setPadding(dp(15), dp(compact() ? 10 : 13), dp(12), dp(compact() ? 10 : 13));
        linearLayout2.setBackgroundResource(z ? R.drawable.settings_section_header_expanded : R.drawable.settings_section_header_collapsed);
        ImageView imageView = settingsSectionIcon(settingsIconForTitle(str));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(dp(24), dp(24));
        layoutParams2.rightMargin = dp(11);
        linearLayout2.addView(imageView, layoutParams2);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        TextView text = text(str, 14, getColor(R.color.hcf_accent_text));
        text.setTypeface(null, 1);
        linearLayout3.addView(text);
        if (str2 != null && !str2.trim().isEmpty()) {
            linearLayout3.addView(text(str2, 10, getColor(R.color.hcf_muted)));
        }
        linearLayout2.addView(linearLayout3, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final TextView text2 = text("›", 22, getColor(R.color.hcf_cyan_bright));
        text2.setGravity(17);
        text2.setRotation(z ? 90.0f : 0.0f);
        linearLayout2.addView(text2, new LinearLayout.LayoutParams(dp(28), -1));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setBackgroundResource(R.drawable.settings_section_body);
        linearLayout4.setPadding(dp(14), dp(12), dp(14), dp(14));
        linearLayout4.setPivotY(0.0f);
        if (view != null) {
            if (view instanceof LinearLayout) {
                LinearLayout linearLayout5 = (LinearLayout) view;
                if (linearLayout5.getChildCount() > 0 && "hcf_section_title".equals(linearLayout5.getChildAt(0).getTag())) {
                    linearLayout5.removeViewAt(0);
                }
            }
            view.setBackgroundColor(0);
            view.setPadding(0, 0, 0, 0);
            view.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            linearLayout4.addView(view);
        }
        linearLayout4.setVisibility(z ? 0 : 8);
        linearLayout4.setAlpha(z ? 1.0f : 0.0f);
        linearLayout4.setScaleY(z ? 1.0f : 0.96f);
        linearLayout.addView(linearLayout4, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] zArr = {z};
        linearLayout2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda41
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                SettingsActivity.lambda$connectedSettingsPanel$65(zArr, text2, linearLayout4, linearLayout2, view2);
            }
        });
        return linearLayout;
    }

    static /* synthetic */ void lambda$connectedSettingsPanel$65(boolean[] zArr, TextView textView, final LinearLayout linearLayout, final LinearLayout linearLayout2, View view) {
        if (zArr[0]) {
            zArr[0] = false;
            textView.animate().rotation(0.0f).setDuration(150L).start();
            linearLayout.animate().alpha(0.0f).scaleY(0.96f).setDuration(150L).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda35
                @Override // java.lang.Runnable
                public final void run() {
                    SettingsActivity.lambda$connectedSettingsPanel$64(linearLayout, linearLayout2);
                }
            }).start();
            return;
        }
        zArr[0] = true;
        linearLayout2.setBackgroundResource(R.drawable.settings_section_header_expanded);
        linearLayout.setVisibility(0);
        linearLayout.setAlpha(0.0f);
        linearLayout.setScaleY(0.94f);
        textView.animate().rotation(90.0f).setDuration(170L).start();
        linearLayout.animate().alpha(1.0f).scaleY(1.0f).setDuration(180L).start();
    }

    static /* synthetic */ void lambda$connectedSettingsPanel$64(LinearLayout linearLayout, LinearLayout linearLayout2) {
        linearLayout.setVisibility(8);
        linearLayout2.setBackgroundResource(R.drawable.settings_section_header_collapsed);
    }

    private boolean devToolsEnabled() {
        return "com.harleytg.forum.dev".equals(getPackageName());
    }

    private View developerToolsCard() {
        LinearLayout card = card();
        card.addView(settingsInfoCard("Development environment active", "Beta-only tools • " + getPackageName() + "\nBuild 10000072 • channel Dev • update feed dev", R.drawable.fa_bug));
        card.addView(settingsSubsectionHeader("Notification Lab", "Test HCF alert types, delivery and background synchronization", R.drawable.fa_bell));
        card.addView(actionButton("Notification Test Console", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda60
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m143xad9949bf(view);
            }
        }));
        card.addView(actionButton("Test Notification Service", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda61
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m144x3a3974c0(view);
            }
        }));
        card.addView(actionButton("Force Notification Sync", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda62
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m145xc6d99fc1(view);
            }
        }));
        card.addView(settingsSubsectionHeader("Runtime & WebView", "Inspect current network, renderer and recovery state", R.drawable.fa_globe));
        card.addView(actionButton("Runtime Snapshot", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda63
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m146x5379cac2(view);
            }
        }));
        card.addView(actionButton("Run Error & Recovery Check", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda64
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m147x693d7cd8(view);
            }
        }));
        card.addView(settingsSubsectionHeader("Diagnostics & Logs", "Sanitized troubleshooting data and local app logs", R.drawable.fa_list));
        card.addView(actionButton("Copy Sanitized Diagnostic Report", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda65
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m148xf5dda7d9(view);
            }
        }));
        card.addView(actionButton("View App & Crash Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda66
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m149x827dd2da(view);
            }
        }));
        card.addView(settingsSubsectionHeader("Telemetry", "Development-only health reporting test", R.drawable.fa_circle_info));
        card.addView(actionButton("Send Test Telemetry", new View.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda67
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.m150xf1dfddb(view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$developerToolsCard$66$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m143xad9949bf(View view) {
        showNotificationTestConsole();
    }

    /* renamed from: lambda$developerToolsCard$67$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m144x3a3974c0(View view) {
        String str;
        InstantNotificationService.requestImmediateSync(this);
        if (NotificationHelper.postNotificationServiceTest(this)) {
            str = "Notification service test sent • background sync requested.";
        } else {
            str = "Notification service test could not post. Check Android notification permission.";
        }
        Toast.makeText(this, str, 0).show();
    }

    /* renamed from: lambda$developerToolsCard$68$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m145xc6d99fc1(View view) {
        InstantNotificationService.requestImmediateSync(this);
        Toast.makeText(this, "Immediate notification sync requested.", 0).show();
    }

    /* renamed from: lambda$developerToolsCard$69$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m146x5379cac2(View view) {
        showDeveloperRuntimeSnapshot();
    }

    /* renamed from: lambda$developerToolsCard$70$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m147x693d7cd8(View view) {
        showRecoveryDiagnostics();
    }

    /* renamed from: lambda$developerToolsCard$71$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m148xf5dda7d9(View view) {
        copyDiagnosticReport();
    }

    /* renamed from: lambda$developerToolsCard$72$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m149x827dd2da(View view) {
        startActivity(new Intent(this, (Class<?>) LogsActivity.class));
    }

    /* renamed from: lambda$developerToolsCard$73$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m150xf1dfddb(View view) {
        if (!TelemetryService.isEnabled(this)) {
            Toast.makeText(this, "Enable Telemetry Services first.", 0).show();
        } else {
            TelemetryService.sendTest(this);
            Toast.makeText(this, "Telemetry test queued.", 0).show();
        }
    }

    private void showNotificationTestConsole() {
        new AlertDialog.Builder(this).setTitle("Notification Test Console").setItems(new String[]{"Direct message", "Mention", "Discussion reply", "General HCF alert"}, new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda17
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.m171xa0b05f1a(dialogInterface, i);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$showNotificationTestConsole$74$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m171xa0b05f1a(DialogInterface dialogInterface, int i) {
        if (i == 0) {
            postTestAlert("Direct message", "New private message test", "/notifications");
            return;
        }
        if (i == 1) {
            postTestAlert("Mention", "@you were mentioned in a forum post", "/notifications");
        } else if (i != 2) {
            postTestAlert("Forum alert", "General Harley's Clan Forum notification test", "/notifications");
        } else {
            postTestAlert("Discussion reply", "New reply test notification", "/notifications");
        }
    }

    private void showDeveloperRuntimeSnapshot() {
        final String str = "HCF Developer Runtime Snapshot\n\nApp: 1.0 (10000072)\nPackage: " + getPackageName() + "\nAndroid SDK: " + Build.VERSION.SDK_INT + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\nNetwork: " + RuntimeState.networkType(this) + "\nTheme: " + ThemeManager.label(this) + "\nPerformance: " + PerformanceProfile.settingLabel(this, this.prefs) + "\nNotifications: " + NotificationHelper.status(this) + "\nPrimary: forum.harleytg.com\nBackup: harleysclan.freeflarum.com\nRenderer recovery: Enabled (HCF-WV-001)";
        new AlertDialog.Builder(this).setTitle("Runtime Snapshot").setMessage(str).setPositiveButton("Close", (DialogInterface.OnClickListener) null).setNeutralButton("Copy", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda39
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.m170xed12a18f(str, dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$showDeveloperRuntimeSnapshot$75$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m170xed12a18f(String str, DialogInterface dialogInterface, int i) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF runtime snapshot", str));
        }
        Toast.makeText(this, "Runtime snapshot copied.", 0).show();
    }

    private View settingsSubsectionHeader(String str, String str2, int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(4), dp(14), dp(4), dp(5));
        ImageView imageView = settingsSectionIcon(i);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(19), dp(19));
        layoutParams.rightMargin = dp(10);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        TextView text = text(str, 13, getColor(R.color.hcf_accent_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        linearLayout2.addView(text(str2, 10, getColor(R.color.hcf_muted)));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        return linearLayout;
    }

    private View settingsInfoCard(String str, String str2, int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(48);
        linearLayout.setBackgroundResource(R.drawable.quick_action_background);
        linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        linearLayout.setClickable(false);
        linearLayout.setFocusable(false);
        ImageView imageView = settingsSectionIcon(i);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        layoutParams.rightMargin = dp(11);
        layoutParams.topMargin = dp(2);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        TextView text = text(str, 13, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        TextView text2 = text(str2, 10, getColor(R.color.hcf_muted));
        text2.setSingleLine(false);
        text2.setMaxLines(Integer.MAX_VALUE);
        linearLayout2.addView(text2);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(7);
        linearLayout.setLayoutParams(layoutParams2);
        return linearLayout;
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        if (ThemeManager.isAmoled(this)) {
            linearLayout.setBackgroundColor(Color.rgb(3, 5, 7));
        } else {
            linearLayout.setBackgroundResource(R.drawable.card_background);
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(compact() ? 8 : 12);
        linearLayout.setLayoutParams(layoutParams);
        return linearLayout;
    }

    private View notificationChannelStatusRow(String str, String str2, String str3) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setBackgroundResource(R.drawable.quick_action_background);
        linearLayout.setPadding(dp(14), dp(8), dp(12), dp(8));
        ImageView imageView = settingsSectionIcon(R.drawable.fa_lock);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        layoutParams.rightMargin = dp(10);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        TextView text = text(str, 14, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        String channelStatus = NotificationHelper.channelStatus(this, str3);
        linearLayout2.addView(text(str2 + " • " + channelStatus, 10, getColor(NotificationHelper.channelImportance(this, str3) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView text2 = text("REQUIRED", 9, getColor(R.color.hcf_accent_text));
        text2.setTypeface(null, 1);
        text2.setGravity(17);
        text2.setPadding(dp(8), dp(4), dp(8), dp(4));
        text2.setBackgroundResource(R.drawable.status_chip_background);
        linearLayout.addView(text2);
        linearLayout.setContentDescription(str + ", required in HCF. " + channelStatus);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(compact() ? 58 : 66));
        layoutParams2.topMargin = dp(6);
        linearLayout.setLayoutParams(layoutParams2);
        return linearLayout;
    }

    private View notificationChannelRow(String str, String str2, String str3) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setBackgroundResource(R.drawable.quick_action_background);
        linearLayout.setPadding(dp(14), dp(9), dp(14), dp(9));
        linearLayout.setClickable(false);
        linearLayout.setFocusable(false);
        ImageView imageView = settingsSectionIcon(R.drawable.fa_bell);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        layoutParams.rightMargin = dp(10);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView text = text(str, 14, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        String channelStatus = NotificationHelper.channelStatus(this, str3);
        linearLayout2.addView(text(str2 + " • " + channelStatus, 10, getColor(NotificationHelper.channelImportance(this, str3) == 0 ? R.color.hcf_warning : R.color.hcf_muted)));
        linearLayout.addView(linearLayout2);
        linearLayout.setContentDescription(str + ". " + str2 + ". " + channelStatus);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = dp(6);
        linearLayout.setLayoutParams(layoutParams2);
        return linearLayout;
    }

    private View sectionTitle(String str, String str2) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setTag("hcf_section_title");
        linearLayout.setPadding(0, 0, 0, dp(8));
        TextView text = text(str, 16, getColor(R.color.hcf_accent_text));
        text.setTypeface(null, 1);
        linearLayout.addView(text);
        linearLayout.addView(text(str2, 11, getColor(R.color.hcf_muted)));
        return linearLayout;
    }

    private TextView text(String str, int i, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        textView.setLineSpacing(0.0f, 1.12f);
        textView.setPadding(0, dp(3), 0, dp(3));
        return textView;
    }

    private void showPerformanceProfileDialog(final Button button) {
        final String[] strArr = {"auto", "performance", "balanced", "quality"};
        String[] strArr2 = {"Auto — Recommended", "Performance — Minimal motion", "Balanced — Short smooth motion", "High Performance — Full visual effects"};
        String saved = PerformanceProfile.saved(this.prefs);
        int i = 0;
        for (int i2 = 0; i2 < 4; i2++) {
            if (strArr[i2].equals(saved)) {
                i = i2;
            }
        }
        new AlertDialog.Builder(this).setTitle("Performance Profile").setSingleChoiceItems(strArr2, i, new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SettingsActivity$$ExternalSyntheticLambda34
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i3) {
                SettingsActivity.this.m172x6df2d3c1(strArr, button, dialogInterface, i3);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$showPerformanceProfileDialog$76$com-harleytg-forum-dev-SettingsActivity, reason: not valid java name */
    /* synthetic */ void m172x6df2d3c1(String[] strArr, Button button, DialogInterface dialogInterface, int i) {
        if (i < 0 || i >= strArr.length) {
            return;
        }
        PerformanceProfile.save(this.prefs, strArr[i]);
        String str = PerformanceProfile.settingLabel(this, this.prefs);
        button.setText("Performance Profile: " + str);
        button.setContentDescription("Performance profile " + str);
        AppLogger.info(this, "setting_performance_profile", strArr[i] + " -> " + PerformanceProfile.resolve(this, this.prefs));
        Toast.makeText(this, str + " • " + PerformanceProfile.detail(this, this.prefs), 0).show();
        dialogInterface.dismiss();
    }

    private Switch toggle(String str, boolean z) {
        Switch r0 = new Switch(this);
        r0.setText(str);
        r0.setTextColor(getColor(R.color.hcf_text));
        r0.setTextSize(14.0f);
        r0.setChecked(z);
        r0.setPadding(0, dp(7), 0, dp(7));
        return r0;
    }

    private Button actionButton(String str, View.OnClickListener onClickListener) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(cleanIconPrefix(str));
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        button.setGravity(8388627);
        button.setPadding(dp(14), 0, dp(14), 0);
        FaIcons.applyStart(button, str);
        button.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
        layoutParams.topMargin = dp(7);
        button.setLayoutParams(layoutParams);
        return button;
    }

    private ImageButton chromeButton(String str) {
        int i = compact() ? 9 : 11;
        if (str == null || str.trim().isEmpty()) {
            str = "Back";
        }
        return UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, i, str);
    }

    private String cleanIconPrefix(String str) {
        return str == null ? "" : str.replaceFirst("^[^A-Za-z0-9]+", "").trim();
    }

    private void openForumLinkSettings() {
        try {
            try {
                startActivity(new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", Uri.parse("package:" + getPackageName())));
                AppLogger.info(this, "forum_link_settings", "open-by-default");
            } catch (Throwable unused) {
                Toast.makeText(this, "Android link settings are unavailable on this device.", 0).show();
            }
        } catch (Throwable unused2) {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            return;
        }
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATIONS);
    }

    private void openNotificationSettings() {
        NotificationHelper.createChannel(this);
        NotificationHelper.openAppNotificationSettings(this);
        AppLogger.info(this, "notification_settings", NotificationHelper.status(this));
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        String str;
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == REQUEST_NOTIFICATIONS) {
            boolean z = iArr.length > 0 && iArr[0] == 0;
            if (z) {
                str = "granted";
            } else {
                str = "denied | " + NotificationHelper.status(this);
            }
            AppLogger.info(this, "notification_permission", str);
            Toast.makeText(this, z ? "Notifications allowed. Sending a heads-up test…" : "Notifications not allowed.", 0).show();
            NotificationSyncScheduler.apply(this);
            refreshStatusLabels();
            if (z) {
                NotificationHelper.post(this, "Harley's Clan Forum", "Heads-up notifications are enabled. This is a test alert.", ForumUrlRouter.home("forum.harleytg.com"));
            }
        }
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == 2;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
