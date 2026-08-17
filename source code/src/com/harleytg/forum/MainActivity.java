package com.harleytg.forum.dev;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends ThemedActivity {
    private static final int FILE_CHOOSER_REQUEST = 1407;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1408;
    private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 1409;

    private WebView webView;
    private ProgressBar pageProgress;
    private View statusOverlay;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private LinearLayout errorActions;
    private Button retryButton;
    private Button alternateButton;
    private TextView errorCodeText;
    private Button errorDetailsButton;
    private ImageButton drawerButton;
    private ImageButton reloadButton;
    private ImageButton copyUrlButton;
    private View topAppBar;
    private ImageView appHeaderLogo;
    private TextView appHeaderTitle;
    private TextView appHeaderSubtitle;
    private TextView liveStatusBadge;
    private ImageButton headerNotificationsButton;
    private TextView headerNotificationCountBadge;
    private View urlBar;
    private View urlBarInner;
    private TextView secureForumLabel;
    private TextView currentUrlText;
    private TextView hostBadge;
    private View drawerScrim;
    private View drawerPanel;
    private TextView drawerHostText;
    private View drawerIdentityCard;
    private ImageView drawerIdentityAvatar;
    private TextView drawerIdentityText;
    private TextView drawerIdentityUsername;
    private TextView drawerIdentityMeta;
    private View drawerLinkedAccounts;
    private TextView drawerProviderEmail;
    private TextView drawerProviderDiscord;
    private Button drawerSecurity;
    private TextView welcomeBanner;
    private View bottomNav;
    private TextView bottomHome;
    private TextView bottomBrowse;
    private TextView bottomCreate;
    private TextView bottomAlerts;
    private TextView bottomProfile;
    private Button drawerHome;
    private Button drawerSwitchHost;
    private Button drawerOpenExternal;
    private Button drawerShare;
    private Button drawerNotifications;
    private TextView drawerNotificationCountBadge;
    private Button drawerSettings;
    private Button drawerLogs;
    private Button drawerSupport;
    private TextView drawerVersionText;
    private int connectionUiGeneration = 0;
    private ProgressBar startupProgress;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> filePathCallback;
    private String activeHost;
    private boolean switchingHosts;
    private long lastBridgeNotificationAt;
    private boolean launchFailed;
    private LiveForumUpdater liveUpdater;
    private boolean liveReloadInProgress;
    private int pendingLiveScrollY = -1;
    private boolean welcomeBackPending;
    private String identityAvatarRequestedUrl = "";
    private String identityAvatarLoadedUrl = "";
    private Bitmap identityAvatarBitmap;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog nativeUpdateDialog;
    private boolean nativeUpdateFlowActive;
    private long nativeUpdateDownloadId = -1L;
    private boolean notificationReceiverRegistered;
    private boolean networkCallbackRegistered;
    private String liveState = "SYNCING";
    private float pullStartY;
    private boolean pullFromTop;
    private long lastPullRefreshAt;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ErrorSystem.AppError lastAppError;
    private Uri lastErrorUri;
    private String lastRecoverableUrl = "";
    private boolean rendererRecoveryPending;
    private float drawerSwipeStartX;
    private float drawerSwipeStartY;
    private long drawerSwipeStartAt;
    private boolean drawerSwipeCandidate;
    private String appliedThemeSignature = "";

    private final BroadcastReceiver notificationEventReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !NotificationHelper.ACTION_NOTIFICATION_EVENT.equals(intent.getAction())) return;
            int count = intent.getIntExtra(NotificationHelper.EXTRA_EVENT_COUNT, -1);
            if (count >= 0) updateNotificationChrome(count);
            String title = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_TITLE);
            String body = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_BODY);
            if (title != null && !title.trim().isEmpty()) {
                String message = title.trim();
                if (body != null && !body.trim().isEmpty()) message += " • " + body.trim();
                showTransientBanner(message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        appliedThemeSignature = ThemeManager.signature(this);
        prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        boolean launchedBefore = prefs.getBoolean(AppPrefs.APP_HAS_LAUNCHED, false);
        welcomeBackPending = launchedBefore && savedInstanceState == null;
        prefs.edit().putBoolean(AppPrefs.APP_HAS_LAUNCHED, true).apply();

        try {
            setContentView(R.layout.activity_main);
            bindViews();
            applySolidIconSystem();
            applyAmoledSurfaces();
            validateCriticalViews();
            updateIdentityChrome(ForumIdentity.load(this));
            configureWebView();
            configureActions();
            liveUpdater = new LiveForumUpdater(this, new LiveForumUpdater.Listener() {
                @Override public String currentUrl() { return currentUrlString(); }
                @Override public void onChangeCandidate(String key, String fingerprint) {
                    handleLiveChangeCandidate(key, fingerprint);
                }
                @Override public void onStateChanged(String state) {
                    updateLiveState(state);
                }
            });
            applyChromePreferences();
            scheduleFirstRunPermissionSetup();
            scheduleWhatsNew(launchedBefore);

            AppLogger.info(this, "main_create", BuildInfo.VERSION + " | UA=" + BuildInfo.USER_AGENT_MARKER);

            if (savedInstanceState != null) {
                webView.restoreState(savedInstanceState);
                Uri current = Uri.parse(webView.getUrl() == null ? "" : webView.getUrl());
                activeHost = ForumUrlRouter.isForumUrl(current) ? current.getHost() : chooseInitialHost();
                hideStatus();
                handleNotificationIntent(getIntent());
                scheduleDeferredNativeSetup();
                return;
            }

            Uri incoming = getIntent() == null ? null : getIntent().getData();
            if (ForumUrlRouter.isForumUrl(incoming)) {
                activeHost = incoming.getHost();
                prefs.edit().putString(AppPrefs.ACTIVE_HOST, activeHost).apply();
            } else {
                activeHost = chooseInitialHost();
                prefs.edit().putString(AppPrefs.ACTIVE_HOST, activeHost).apply();
            }
            String startUrl = ForumUrlRouter.isForumUrl(incoming)
                    ? ForumUrlRouter.equivalentOnHost(incoming, incoming.getHost())
                    : ForumUrlRouter.home(activeHost);

            updateUrlChrome(startUrl);
            showChecking(activeHost);
            AppLogger.info(this, "initial_navigation", AppLogger.safeUrl(startUrl));
            webView.loadUrl(startUrl);
            scheduleDeferredNativeSetup();
        } catch (Throwable t) {
            launchFailed = true;
            AppLogger.crash(this, t);
            showCrashSafeScreen(t);
        }
    }

    private void applySolidIconSystem() {
        try {
            configureCenteredHeaderIcon(drawerButton, R.drawable.fa_bars, R.color.hcf_cyan_bright);
            configureCenteredHeaderIcon(headerNotificationsButton, R.drawable.fa_bell, R.color.hcf_cyan_bright);
            configureCenteredHeaderIcon(copyUrlButton, R.drawable.fa_copy, R.color.hcf_muted);
            configureCenteredHeaderIcon(reloadButton, R.drawable.fa_rotate_right, R.color.hcf_cyan_bright);
            FaIcons.applyStart(retryButton, R.drawable.fa_rotate_right);
            FaIcons.applyStart(alternateButton, R.drawable.fa_right_left);
            FaIcons.applyStart(errorDetailsButton, R.drawable.fa_list);
            FaIcons.applyStart(drawerSecurity, R.drawable.fa_shield);
            FaIcons.applyStart(drawerHome, R.drawable.fa_house);
            FaIcons.applyStart(drawerSwitchHost, R.drawable.fa_right_left);
            FaIcons.applyStart(drawerOpenExternal, R.drawable.fa_arrow_up_right_from_square);
            FaIcons.applyStart(drawerShare, R.drawable.fa_share_nodes);
            FaIcons.applyStart(drawerNotifications, R.drawable.fa_bell);
            FaIcons.applyStart(drawerSettings, R.drawable.fa_gear);
            FaIcons.applyStart(drawerLogs, R.drawable.fa_list);
            FaIcons.applyStart(drawerSupport, R.drawable.fa_envelope);
            FaIcons.applyTop(bottomHome, R.drawable.fa_house);
            FaIcons.applyTop(bottomBrowse, R.drawable.fa_list);
            FaIcons.applyTop(bottomCreate, R.drawable.fa_plus);
            FaIcons.applyTop(bottomAlerts, R.drawable.fa_bell);
            FaIcons.applyTop(bottomProfile, R.drawable.fa_user);
        } catch (Throwable t) {
            AppLogger.warn(this, "solid_icons", t.getClass().getSimpleName());
        }
    }

    private void configureCenteredHeaderIcon(ImageButton button, int drawableRes, int colorRes) {
        if (button == null) return;
        button.setImageResource(drawableRes);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) {
            button.setStateListAnimator(null);
            button.setImageTintList(ColorStateList.valueOf(getColor(colorRes)));
        }
    }

    private void applyAmoledSurfaces() {
        if (!ThemeManager.isAmoled(this)) return;
        try {
            View root = findViewById(R.id.rootFrame);
            if (root != null) root.setBackgroundColor(Color.BLACK);
            if (statusOverlay != null) statusOverlay.setBackgroundColor(Color.BLACK);
            if (webView != null) webView.setBackgroundColor(Color.BLACK);
            if (topAppBar != null) topAppBar.setBackgroundColor(Color.BLACK);
            if (urlBar != null) urlBar.setBackgroundColor(Color.BLACK);
            if (drawerPanel != null) drawerPanel.setBackgroundColor(Color.rgb(3, 5, 7));
        } catch (Throwable ignored) {}
    }

    private void validateCriticalViews() {
        if (webView == null || pageProgress == null || statusOverlay == null
                || statusTitle == null || statusSubtitle == null || errorActions == null
                || retryButton == null || alternateButton == null
                || errorCodeText == null || errorDetailsButton == null) {
            throw new IllegalStateException("Main native layout is incomplete");
        }
    }

    private void scheduleDeferredNativeSetup() {
        if (webView == null) return;
        webView.postDelayed(() -> {
            try {
                NotificationHelper.createChannel(MainActivity.this);
                NotificationSyncScheduler.apply(MainActivity.this);
                UpdateScheduler.apply(MainActivity.this);
                UpdateAutomation.maybeCheck(MainActivity.this, false, (release, updateAvailable, error) -> {
                    if (updateAvailable && release != null && !isFinishing() && !isDestroyed()) {
                        showBetaUpdateAvailableDialog(release);
                    }
                });
                TelemetryService.handlePendingCrash(MainActivity.this);
                AppLogger.info(MainActivity.this, "deferred_native_setup", "ok");
            } catch (Throwable t) {
                AppLogger.error(MainActivity.this, "deferred_native_setup", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            }
        }, 2500L);
    }

    private void showCrashSafeScreen(Throwable t) {
        try {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            page.setPadding(dp(24), dp(24), dp(24), dp(24));
            page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

            TextView title = new TextView(this);
            title.setText("Harley's Clan Forum • Recovery Mode");
            title.setTextColor(getColor(R.color.hcf_cyan_bright));
            title.setTextSize(20);
            title.setGravity(Gravity.CENTER);
            page.addView(title);

            TextView version = new TextView(this);
            version.setText(BuildInfo.META_LINE + "\nThe native shell hit a startup problem, so the app stayed open instead of crashing.\n\n" + t.getClass().getSimpleName());
            version.setTextColor(getColor(R.color.hcf_text));
            version.setTextSize(13);
            version.setGravity(Gravity.CENTER);
            version.setPadding(0, dp(14), 0, dp(18));
            page.addView(version);

            Button retry = new Button(this);
            UiButtons.normalizeText(retry);
            retry.setTextColor(getColor(R.color.hcf_cyan_bright));
            retry.setBackgroundResource(R.drawable.button_background);
            retry.setText("Retry App Start");
            retry.setAllCaps(false);
            retry.setOnClickListener(v -> recreate());
            page.addView(retry, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

            Button settings = new Button(this);
            UiButtons.normalizeText(settings);
            settings.setTextColor(getColor(R.color.hcf_cyan_bright));
            settings.setBackgroundResource(R.drawable.button_background);
            settings.setText("Open App Settings");
            settings.setAllCaps(false);
            settings.setOnClickListener(v -> {
                try { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); } catch (Throwable ignored) {}
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            sp.topMargin = dp(10);
            page.addView(settings, sp);

            Button cache = new Button(this);
            UiButtons.normalizeText(cache);
            cache.setTextColor(getColor(R.color.hcf_cyan_bright));
            cache.setBackgroundResource(R.drawable.button_background);
            cache.setText("Clear Web Cache");
            cache.setAllCaps(false);
            cache.setOnClickListener(v -> {
                try {
                    WebView temp = new WebView(MainActivity.this);
                    temp.clearCache(true);
                    temp.clearHistory();
                    temp.destroy();
                    Toast.makeText(MainActivity.this, "Web cache cleared.", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            cp.topMargin = dp(8);
            page.addView(cache, cp);

            Button logs = new Button(this);
            UiButtons.normalizeText(logs);
            logs.setTextColor(getColor(R.color.hcf_cyan_bright));
            logs.setBackgroundResource(R.drawable.button_background);
            logs.setText("Open Diagnostics & Logs");
            logs.setAllCaps(false);
            logs.setOnClickListener(v -> {
                try { startActivity(new Intent(MainActivity.this, LogsActivity.class)); } catch (Throwable ignored) {}
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            lp.topMargin = dp(8);
            page.addView(logs, lp);

            Button reset = new Button(this);
            UiButtons.normalizeText(reset);
            reset.setTextColor(getColor(R.color.hcf_cyan_bright));
            reset.setBackgroundResource(R.drawable.button_background);
            reset.setText("Reset App UI Only");
            reset.setAllCaps(false);
            reset.setOnClickListener(v -> {
                try {
                    prefs.edit()
                            .remove(AppPrefs.SHOW_URL_BAR)
                            .remove(AppPrefs.COMPACT_HEADER)
                            .remove(AppPrefs.SHOW_BOTTOM_NAV)
                            .remove(AppPrefs.APP_THEME)
                            .remove(AppPrefs.PERFORMANCE_MODE)
                            .remove(AppPrefs.PERFORMANCE_PROFILE)
                            .remove(AppPrefs.NATIVE_ACCENT)
                            .remove(AppPrefs.UI_REVAMP_VERSION)
                            .apply();
                    UiPreferences.migrate(MainActivity.this);
                    Toast.makeText(MainActivity.this, "App UI reset. Forum login and cookies were kept.", Toast.LENGTH_LONG).show();
                    recreate();
                } catch (Throwable ignored) {}
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            rp.topMargin = dp(8);
            page.addView(reset, rp);

            setContentView(page);
        } catch (Throwable ignored) {
            // Last-resort: do not throw again from the crash handler.
        }
    }

    private void bindViews() {
        webView = findViewById(R.id.webView);
        pageProgress = findViewById(R.id.pageProgress);
        statusOverlay = findViewById(R.id.statusOverlay);
        statusTitle = findViewById(R.id.statusTitle);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        errorActions = findViewById(R.id.errorActions);
        retryButton = findViewById(R.id.retryButton);
        alternateButton = findViewById(R.id.alternateButton);
        errorCodeText = findViewById(R.id.errorCodeText);
        errorDetailsButton = findViewById(R.id.errorDetailsButton);
        drawerButton = findViewById(R.id.drawerButton);
        reloadButton = findViewById(R.id.reloadButton);
        copyUrlButton = findViewById(R.id.copyUrlButton);
        topAppBar = findViewById(R.id.topAppBar);
        appHeaderLogo = findViewById(R.id.appHeaderLogo);
        appHeaderTitle = findViewById(R.id.appHeaderTitle);
        appHeaderSubtitle = findViewById(R.id.appHeaderSubtitle);
        liveStatusBadge = findViewById(R.id.liveStatusBadge);
        headerNotificationsButton = findViewById(R.id.headerNotificationsButton);
        headerNotificationCountBadge = findViewById(R.id.headerNotificationCountBadge);
        urlBar = findViewById(R.id.urlBar);
        urlBarInner = findViewById(R.id.urlBarInner);
        secureForumLabel = findViewById(R.id.secureForumLabel);
        currentUrlText = findViewById(R.id.currentUrlText);
        hostBadge = findViewById(R.id.hostBadge);
        drawerScrim = findViewById(R.id.drawerScrim);
        drawerPanel = findViewById(R.id.drawerPanel);
        drawerHostText = findViewById(R.id.drawerHostText);
        drawerIdentityCard = findViewById(R.id.drawerIdentityCard);
        drawerIdentityAvatar = findViewById(R.id.drawerIdentityAvatar);
        drawerIdentityText = findViewById(R.id.drawerIdentityText);
        drawerIdentityUsername = findViewById(R.id.drawerIdentityUsername);
        drawerIdentityMeta = findViewById(R.id.drawerIdentityMeta);
        drawerLinkedAccounts = findViewById(R.id.drawerLinkedAccounts);
        drawerProviderEmail = findViewById(R.id.drawerProviderEmail);
        drawerProviderDiscord = findViewById(R.id.drawerProviderDiscord);
        drawerSecurity = findViewById(R.id.drawerSecurity);
        welcomeBanner = findViewById(R.id.welcomeBanner);
        bottomNav = findViewById(R.id.bottomNav);
        bottomHome = findViewById(R.id.bottomHome);
        bottomBrowse = findViewById(R.id.bottomBrowse);
        bottomCreate = findViewById(R.id.bottomCreate);
        bottomAlerts = findViewById(R.id.bottomAlerts);
        bottomProfile = findViewById(R.id.bottomProfile);
        drawerHome = findViewById(R.id.drawerHome);
        drawerSwitchHost = findViewById(R.id.drawerSwitchHost);
        drawerOpenExternal = findViewById(R.id.drawerOpenExternal);
        drawerShare = findViewById(R.id.drawerShare);
        drawerNotifications = findViewById(R.id.drawerNotifications);
        drawerNotificationCountBadge = findViewById(R.id.drawerNotificationCountBadge);
        drawerSettings = findViewById(R.id.drawerSettings);
        drawerLogs = findViewById(R.id.drawerLogs);
        drawerSupport = findViewById(R.id.drawerSupport);
        drawerVersionText = findViewById(R.id.drawerVersionText);
        if (drawerVersionText != null) drawerVersionText.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);
        startupProgress = findViewById(R.id.startupProgress);
    }

    private void openSupportEmail() {
        try {
            startActivity(new Intent(this, SupportContactActivity.class));
            AppLogger.info(this, "support_contact_open", "drawer");
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open Contact Support.", Toast.LENGTH_LONG).show();
            AppLogger.error(this, "support_contact_open", t.getClass().getSimpleName());
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setDefaultTextEncodingName("UTF-8");
        s.setUserAgentString(BuildInfo.userAgent(s.getUserAgentString()));
        if (Build.VERSION.SDK_INT >= 29) {
            try { s.setForceDark(WebSettings.FORCE_DARK_OFF); }
            catch (Throwable ignored) {}
        }
        webView.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        WebView.setWebContentsDebuggingEnabled(false);
        webView.removeJavascriptInterface("searchBoxJavaBridge_");
        webView.removeJavascriptInterface("accessibility");
        webView.removeJavascriptInterface("accessibilityTraversal");
        if (Build.VERSION.SDK_INT >= 27) {
            try { WebView.startSafeBrowsing(this, value -> AppLogger.info(MainActivity.this, "safe_browsing", Boolean.TRUE.equals(value) ? "ready" : "unavailable")); }
            catch (Throwable t) { AppLogger.warn(this, "safe_browsing", t.getClass().getSimpleName()); }
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        // Narrow bridge: forum pages can only ask Android to show a message notification.
        // It cannot read cookies, files, credentials, or execute arbitrary Android actions.
        webView.addJavascriptInterface(new AppMessageBridge(), "HCFNative");
        webView.setWebViewClient(new HcfWebViewClient());
        webView.setWebChromeClient(new HcfChromeClient());
        if (Build.VERSION.SDK_INT >= 26) {
            try { webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false); }
            catch (Throwable ignored) {}
        }
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                enqueueForumDownload(url, userAgent, contentDisposition, mimetype, contentLength));
        configurePullToRefresh();
        applyPerformanceMode();
        applySavedAccent();
    }

    private void configureActions() {
        drawerButton.setOnClickListener(v -> openDrawer());
        drawerScrim.setOnClickListener(v -> closeDrawer());
        reloadButton.setOnClickListener(v -> reloadCurrentPage());
        copyUrlButton.setOnClickListener(v -> copyCurrentUrl());
        currentUrlText.setOnClickListener(v -> copyCurrentUrl());
        headerNotificationsButton.setOnClickListener(v -> navigateForumPath("/notifications", "header_alerts"));
        liveStatusBadge.setOnClickListener(v -> {
            InstantNotificationService.requestImmediateSync(this);
            if (liveUpdater != null) liveUpdater.poke();
            updateLiveState("SYNCING");
            Toast.makeText(this, "Syncing forum now…", Toast.LENGTH_SHORT).show();
        });

        bottomHome.setOnClickListener(v -> navigateForumPath("/", "bottom_home"));
        bottomBrowse.setOnClickListener(v -> navigateForumPath("/all", "bottom_browse"));
        bottomCreate.setOnClickListener(v -> navigateForumPath("/compose", "bottom_create"));
        bottomAlerts.setOnClickListener(v -> navigateForumPath("/notifications", "bottom_alerts"));
        bottomProfile.setOnClickListener(v -> openCurrentProfile());

        drawerHome.setOnClickListener(v -> {
            closeDrawer();
            String target = ForumUrlRouter.home(activeHost == null ? chooseInitialHost() : activeHost);
            updateUrlChrome(target);
            webView.loadUrl(target);
            AppLogger.info(this, "drawer_home", AppLogger.safeUrl(target));
        });
        drawerSwitchHost.setOnClickListener(v -> {
            closeDrawer();
            String target = ForumConfig.PRIMARY_HOST.equals(activeHost) ? ForumConfig.BACKUP_HOST : ForumConfig.PRIMARY_HOST;
            if (ForumConfig.PRIMARY_HOST.equals(target)) prefs.edit().remove(AppPrefs.FALLBACK_UNTIL).apply();
            switchHost(target, currentForumUri());
        });
        drawerOpenExternal.setOnClickListener(v -> { closeDrawer(); openCurrentPageExternally(); });
        if (drawerShare != null) drawerShare.setOnClickListener(v -> { closeDrawer(); shareCurrentPage(); });
        if (drawerIdentityCard != null) drawerIdentityCard.setOnClickListener(v -> {
            closeDrawer();
            startActivity(new Intent(this, IdentityActivity.class));
            AppLogger.info(this, "drawer_identity", ForumIdentity.load(this).loggedIn ? "member" : "guest");
        });
        if (drawerSecurity != null) drawerSecurity.setOnClickListener(v -> {
            closeDrawer();
            openAccountSecurity();
        });
        drawerNotifications.setOnClickListener(v -> {
            closeDrawer();
            String target = "https://" + (activeHost == null ? chooseInitialHost() : activeHost) + "/notifications";
            updateUrlChrome(target);
            webView.loadUrl(target);
            AppLogger.info(this, "drawer", "forum_notifications");
        });
        drawerSettings.setOnClickListener(v -> { closeDrawer(); openAppSettings("drawer"); });
        drawerLogs.setOnClickListener(v -> {
            closeDrawer();
            AppLogger.info(this, "drawer_logs", "tap");
            startActivity(new Intent(this, LogsActivity.class));
        });
        if (drawerSupport != null) drawerSupport.setOnClickListener(v -> {
            closeDrawer();
            openSupportEmail();
        });

        retryButton.setOnClickListener(v -> {
            showChecking(activeHost);
            String current = webView.getUrl();
            String target;
            if (current != null && ForumUrlRouter.isForumUrl(Uri.parse(current))) {
                target = ForumUrlRouter.equivalentOnHost(Uri.parse(current), activeHost);
            } else {
                target = ForumUrlRouter.home(activeHost);
            }
            AppLogger.info(this, "manual_retry", AppLogger.safeUrl(target));
            webView.loadUrl(target);
        });

        alternateButton.setOnClickListener(v -> {
            String target = ForumConfig.PRIMARY_HOST.equals(activeHost)
                    ? ForumConfig.BACKUP_HOST : ForumConfig.PRIMARY_HOST;
            if (ForumConfig.PRIMARY_HOST.equals(target)) {
                prefs.edit().remove(AppPrefs.FALLBACK_UNTIL).apply();
            }
            AppLogger.info(this, "manual_host_switch", activeHost + " -> " + target);
            switchHost(target, currentForumUri());
        });
        errorDetailsButton.setOnClickListener(v -> showLastErrorDetails());
    }

    private void navigateForumPath(String path, String source) {
        if (webView == null) return;
        String safePath = path == null ? "/" : path.trim();
        if (!safePath.startsWith("/") || safePath.startsWith("//")) safePath = "/";
        String host = activeHost == null ? chooseInitialHost() : activeHost;
        String target = "https://" + host + safePath;
        closeDrawer();
        updateUrlChrome(target);
        if (liveUpdater != null) liveUpdater.reset();
        webView.loadUrl(target);
        AppLogger.info(this, "quick_navigation", source + " | " + AppLogger.safeUrl(target));
    }

    private void openCurrentProfile() {
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        if (!identity.loggedIn) {
            navigateForumPath("/login", "bottom_profile_sign_in");
            return;
        }
        String handle = !identity.slug.isEmpty() ? identity.slug : identity.username;
        if (handle == null || handle.trim().isEmpty()) {
            Toast.makeText(this, "Your profile is still syncing.", Toast.LENGTH_SHORT).show();
            InstantNotificationService.requestImmediateSync(this);
            return;
        }
        navigateForumPath("/u/" + Uri.encode(handle.trim()), "bottom_profile");
    }

    private void updateLiveState(String state) {
        liveState = state == null ? "SYNCING" : state;
        if (liveStatusBadge == null) return;
        if ("LIVE".equals(liveState)) {
            liveStatusBadge.setText("Live");
            liveStatusBadge.setTextColor(getColor(R.color.hcf_cyan_bright));
        } else if ("WAITING".equals(liveState)) {
            liveStatusBadge.setText("Retry");
            liveStatusBadge.setTextColor(getColor(R.color.hcf_yellow));
        } else if ("PAUSED".equals(liveState)) {
            liveStatusBadge.setText("Ready");
            liveStatusBadge.setTextColor(getColor(R.color.hcf_muted));
        } else if ("OFFLINE".equals(liveState)) {
            liveStatusBadge.setText("Off");
            liveStatusBadge.setTextColor(getColor(R.color.hcf_yellow));
        } else {
            liveStatusBadge.setText("Sync");
            liveStatusBadge.setTextColor(getColor(R.color.hcf_meta));
        }
        updateHeaderSubtitle();
    }

    private void updateHeaderSubtitle() {
        if (appHeaderSubtitle == null) return;
        String host = activeHost == null ? chooseInitialHost() : activeHost;
        String server = ForumConfig.PRIMARY_HOST.equalsIgnoreCase(host) ? "Primary" : "Backup";
        String freshness = "OFFLINE".equals(liveState) ? "Offline"
                : ("WAITING".equals(liveState) ? "Reconnecting"
                : ("SYNCING".equals(liveState) ? "Syncing" : "Live forum"));
        appHeaderSubtitle.setText(freshness + " • " + server);
    }

    private void updateNotificationChrome(int count) {
        int safe = Math.max(0, count);
        String badge = safe > 999 ? "999+" : String.valueOf(safe);
        if (headerNotificationsButton != null) {
            headerNotificationsButton.setContentDescription(safe > 0
                    ? safe + " new forum notifications" : "Forum notifications");
        }
        if (headerNotificationCountBadge != null) {
            headerNotificationCountBadge.setText(badge);
            headerNotificationCountBadge.setVisibility(safe > 0 ? View.VISIBLE : View.GONE);
            headerNotificationCountBadge.setContentDescription(safe > 0
                    ? safe + " unread forum notifications" : "No unread forum notifications");
        }
        if (bottomAlerts != null) {
            bottomAlerts.setText(safe > 0 ? badge + "\nAlerts" : "Alerts");
            bottomAlerts.setTextColor(getColor(safe > 0 ? R.color.hcf_cyan_bright : R.color.hcf_muted));
        }
        if (drawerNotifications != null) {
            // Keep the Notifications button label stable and use the matching
            // blue right-side badge/dot for the unread count.
            drawerNotifications.setText("Notifications");
            drawerNotifications.setContentDescription(safe > 0
                    ? "Notifications, " + badge + " unread" : "Notifications");
        }
        if (drawerNotificationCountBadge != null) {
            drawerNotificationCountBadge.setText(badge);
            drawerNotificationCountBadge.setVisibility(safe > 0 ? View.VISIBLE : View.GONE);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerNotificationEvents() {
        if (notificationReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter(NotificationHelper.ACTION_NOTIFICATION_EVENT);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(notificationEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notificationEventReceiver, filter);
            }
            notificationReceiverRegistered = true;
        } catch (Throwable t) {
            AppLogger.warn(this, "notification_event_receiver", t.getClass().getSimpleName());
        }
    }

    private void unregisterNotificationEvents() {
        if (!notificationReceiverRegistered) return;
        try { unregisterReceiver(notificationEventReceiver); }
        catch (Throwable ignored) {}
        notificationReceiverRegistered = false;
    }

    private void openAppSettings(String source) {
        AppLogger.info(this, "settings_button", source);
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void applyChromePreferences() {
        // v0.4.1 intentionally restored one compact native header. Keep the
        // duplicate LIVE/alert controls and native bottom navigation removed.
        applyHeaderDensity(true);
        if (liveStatusBadge != null) liveStatusBadge.setVisibility(View.GONE);
        if (headerNotificationsButton != null) headerNotificationsButton.setVisibility(View.GONE);
        if (urlBar != null) {
            boolean show = prefs.getBoolean(AppPrefs.SHOW_URL_BAR, false);
            urlBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bottomNav != null) bottomNav.setVisibility(View.GONE);
    }

    private void applyHeaderDensity(boolean compactHeader) {
        int headerHeight = compactHeader ? R.dimen.compact_app_header_height : R.dimen.app_header_height;
        int headerButton = compactHeader ? R.dimen.compact_app_header_button : R.dimen.app_header_button;
        int headerLogo = compactHeader ? R.dimen.compact_app_header_logo : R.dimen.app_header_logo;
        int headerTitle = compactHeader ? R.dimen.compact_app_header_title_text : R.dimen.app_header_title_text;
        int urlHeight = compactHeader ? R.dimen.compact_url_bar_height : R.dimen.url_bar_height;
        int urlInnerHeight = compactHeader ? R.dimen.compact_url_bar_inner_height : R.dimen.url_bar_inner_height;
        int copySize = compactHeader ? R.dimen.compact_url_copy_button : R.dimen.url_copy_button;
        int reloadSize = compactHeader ? R.dimen.compact_url_reload_button : R.dimen.url_reload_button;

        setViewHeight(topAppBar, headerHeight);
        setSquareSize(drawerButton, headerButton);
        setSquareSize(appHeaderLogo, headerLogo);
        setSquareSize(headerNotificationsButton, headerButton);
        setTextSizeFromDimen(appHeaderTitle, headerTitle);

        setViewHeight(urlBar, urlHeight);
        setViewHeight(urlBarInner, urlInnerHeight);
        setSquareSize(copyUrlButton, copySize);
        setSquareSize(reloadButton, reloadSize);

        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (topAppBar != null) {
            int side = dp(compactHeader ? 6 : 8);
            topAppBar.setPadding(side, 0, side, 0);
        }
        if (urlBar != null) {
            int side = dp(compactHeader ? 6 : 8);
            int vertical = dp(compactHeader ? 2 : (landscape ? 3 : 4));
            urlBar.setPadding(side, vertical, side, vertical);
        }

        if (secureForumLabel != null) secureForumLabel.setVisibility(compactHeader ? View.GONE : View.VISIBLE);
        if (hostBadge != null) hostBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactHeader ? 7 : 8);
        if (currentUrlText != null) currentUrlText.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactHeader ? (landscape ? 9 : 10) : 11);
        if (appHeaderSubtitle != null) appHeaderSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        if (liveStatusBadge != null) {
            ViewGroup.LayoutParams params = liveStatusBadge.getLayoutParams();
            if (params != null) {
                params.height = dp(compactHeader ? 24 : 30);
                liveStatusBadge.setLayoutParams(params);
            }
        }
    }

    private void setViewHeight(View view, int dimenRes) {
        if (view == null || view.getLayoutParams() == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = getResources().getDimensionPixelSize(dimenRes);
        view.setLayoutParams(params);
    }

    private void setSquareSize(View view, int dimenRes) {
        if (view == null || view.getLayoutParams() == null) return;
        int size = getResources().getDimensionPixelSize(dimenRes);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = size;
        params.height = size;
        view.setLayoutParams(params);
    }

    private void setTextSizeFromDimen(TextView view, int dimenRes) {
        if (view == null) return;
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(dimenRes));
    }

    private void reloadCurrentPage() {
        if (webView == null) return;
        AppLogger.info(this, "reload", AppLogger.safeUrl(webView.getUrl()));
        if (liveUpdater != null) liveUpdater.reset();
        webView.reload();
    }

    private void applyLiveUpdatePreference() {
        if (liveUpdater == null) return;
        if (prefs.getBoolean(AppPrefs.LIVE_FORUM_UPDATES, true)) liveUpdater.start();
        else liveUpdater.stop();
    }

    private void handleLiveChangeCandidate(String key, String fingerprint) {
        if (webView == null || liveUpdater == null) return;
        if (!prefs.getBoolean(AppPrefs.LIVE_FORUM_UPDATES, true)) return;
        if (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE) return;
        if (statusOverlay != null && statusOverlay.getVisibility() == View.VISIBLE) return;

        String safeCheck = "(function(){" +
                "if(document.hidden)return 'busy';" +
                "var a=document.activeElement;" +
                "if(a&&((a.tagName==='INPUT')||(a.tagName==='TEXTAREA')||(a.tagName==='SELECT')||a.isContentEditable))return 'busy';" +
                "var c=document.querySelector('.Composer');" +
                "if(c&&c.offsetParent!==null)return 'busy';" +
                "var m=document.querySelector('.ModalManager .Modal, .ModalManager .Modal-backdrop');" +
                "if(m&&m.offsetParent!==null)return 'busy';" +
                "return 'safe';})()";
        try {
            webView.evaluateJavascript(safeCheck, result -> {
                if (result == null || !result.contains("safe")) return;
                softSyncCurrentPage(key, fingerprint);
            });
        } catch (Throwable t) {
            AppLogger.warn(this, "live_update_safe_check", t.getClass().getSimpleName());
        }
    }

    private void softSyncCurrentPage(String key, String fingerprint) {
        if (webView == null || liveUpdater == null) return;
        Uri page;
        try { page = Uri.parse(currentUrlString()); }
        catch (Throwable ignored) { return; }
        String endpoint = softSyncEndpoint(page);
        if (endpoint == null || endpoint.isEmpty()) {
            fallbackLiveReload(key, fingerprint, "no soft-sync endpoint");
            return;
        }

        String quoted = endpoint.replace("\\", "\\\\").replace("'", "\\'");
        String script = "(async function(){try{" +
                "var a=window.app;if(!a||!a.request||!a.store)return 'unsupported';" +
                "var p=await a.request({method:'GET',url:'" + quoted + "'});" +
                "if(a.store.pushPayload)a.store.pushPayload(p);" +
                "if(window.m&&m.redraw)m.redraw();" +
                "return 'ok';" +
                "}catch(e){return 'error';}})()";
        try {
            webView.evaluateJavascript(script, result -> {
                if (result != null && result.contains("ok")) {
                    liveUpdater.acknowledge(key, fingerprint);
                    AppLogger.info(MainActivity.this, "live_update", "soft-sync " + AppLogger.safeUrl(endpoint));
                } else {
                    AppLogger.warn(MainActivity.this, "live_update", "soft-sync unavailable; safe reload fallback");
                    fallbackLiveReload(key, fingerprint, "soft-sync unavailable");
                }
            });
        } catch (Throwable t) {
            AppLogger.warn(this, "live_update_soft_sync", t.getClass().getSimpleName());
            fallbackLiveReload(key, fingerprint, "soft-sync exception");
        }
    }

    private void fallbackLiveReload(String key, String fingerprint, String reason) {
        if (webView == null || liveUpdater == null || liveReloadInProgress) return;
        liveReloadInProgress = true;
        pendingLiveScrollY = Math.max(0, webView.getScrollY());
        liveUpdater.acknowledge(key, fingerprint);
        AppLogger.info(this, "live_update", "reload-fallback | " + reason);
        webView.reload();
    }

    private String softSyncEndpoint(Uri page) {
        if (page == null || !ForumUrlRouter.isForumUrl(page)) return null;
        String host = page.getHost();
        String path = page.getPath() == null ? "/" : page.getPath();
        String lower = path.toLowerCase(java.util.Locale.US);
        String base = "https://" + host;
        if (lower.startsWith("/d/")) {
            String tail = path.substring(3);
            int slash = tail.indexOf('/');
            if (slash >= 0) tail = tail.substring(0, slash);
            int dash = tail.indexOf('-');
            String id = dash >= 0 ? tail.substring(0, dash) : tail;
            if (id.matches("[0-9]+")) {
                return base + "/api/discussions/" + id + "?include=posts,posts.user";
            }
        }
        if (lower.startsWith("/notifications")) {
            return base + "/api/notifications?page%5Blimit%5D=20";
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following")
                || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return base + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=20&include=user,lastPostedUser,tags";
        }
        return null;
    }

    private int parseJavascriptInt(String value) {
        if (value == null) return -1;
        try {
            String cleaned = value.replace("\"", "").trim();
            return Integer.parseInt(cleaned);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void finishLiveRefresh(WebView view) {
        if (!liveReloadInProgress) return;
        final int y = pendingLiveScrollY;
        liveReloadInProgress = false;
        pendingLiveScrollY = -1;
        if (y <= 0) return;
        view.postDelayed(() -> {
            try { view.evaluateJavascript("window.scrollTo(0," + y + ");void(0);", null); }
            catch (Throwable ignored) {}
        }, 350L);
    }

    private String currentUrlString() {
        String value = webView == null ? null : webView.getUrl();
        if (value == null || value.trim().isEmpty()) {
            String host = activeHost == null ? chooseInitialHost() : activeHost;
            value = ForumUrlRouter.home(host);
        }
        return value;
    }

    private void copyCurrentUrl() {
        String value = currentUrlString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Harley's Clan Forum URL", value));
            Toast.makeText(this, "URL copied.", Toast.LENGTH_SHORT).show();
            AppLogger.info(this, "url_copy", AppLogger.safeUrl(value));
        }
    }

    private void openCurrentPageExternally() {
        try {
            Uri uri = Uri.parse(currentUrlString());
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            HcfIntentChooser.show(this, intent, "Open in browser",
                    "Choose an app for this forum page. Harley's Clan Forum is excluded to prevent a loop.", true);
            AppLogger.info(this, "url_open_external", AppLogger.safeUrl(uri.toString()));
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to open this page externally.", Toast.LENGTH_SHORT).show();
            AppLogger.error(this, "url_open_external", t.getClass().getSimpleName());
        }
    }


    private void shareCurrentPage() {
        try {
            String url = currentUrlString();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Harley's Clan Forum");
            share.putExtra(Intent.EXTRA_TEXT, url);
            HcfIntentChooser.showShare(this, share, "Share forum page", "Copy the link or share it with another app.");
            AppLogger.info(this, "url_share", AppLogger.safeUrl(url));
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to share this page.", Toast.LENGTH_SHORT).show();
            AppLogger.error(this, "url_share", t.getClass().getSimpleName());
        }
    }

    private void enqueueForumDownload(String rawUrl, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        Uri uri;
        try { uri = Uri.parse(rawUrl == null ? "" : rawUrl); }
        catch (Throwable t) { uri = null; }
        if (uri == null || uri.getScheme() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            Toast.makeText(this, "This download link is not supported.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            String name = URLUtil.guessFileName(rawUrl, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(uri)
                    .setTitle(name)
                    .setDescription("Harley's Clan Forum download")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true);
            if (mimeType != null && !mimeType.trim().isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null && !userAgent.trim().isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            if (ForumUrlRouter.isForumUrl(uri)) {
                String cookies = CookieManager.getInstance().getCookie("https://" + uri.getHost() + "/");
                if (cookies != null && !cookies.trim().isEmpty()) request.addRequestHeader("Cookie", cookies);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                try { request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name); }
                catch (Throwable ignored) {}
            }
            long id = manager.enqueue(request);
            Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            AppLogger.info(this, "forum_download", "id=" + id + " | " + AppLogger.safeUrl(rawUrl));
        } catch (Throwable t) {
            AppLogger.error(this, "forum_download", t.getClass().getSimpleName());
            openExternal(uri);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configurePullToRefresh() {
        if (webView == null) return;
        final float trigger = dp(92);
        webView.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pullStartY = event.getY();
                    pullFromTop = webView.getScrollY() <= 0;
                    break;
                case MotionEvent.ACTION_UP:
                    if (pullFromTop && webView.getScrollY() <= 0
                            && event.getY() - pullStartY >= trigger
                            && System.currentTimeMillis() - lastPullRefreshAt > 1200L) {
                        lastPullRefreshAt = System.currentTimeMillis();
                        showTransientBanner("Refreshing forum…");
                        reloadCurrentPage();
                        AppLogger.info(this, "pull_to_refresh", AppLogger.safeUrl(webView.getUrl()));
                    }
                    pullFromTop = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    pullFromTop = false;
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void applyPerformanceMode() {
        if (webView == null) return;
        String mode = PerformanceProfile.resolve(this, prefs);
        boolean performance = PerformanceProfile.PERFORMANCE.equals(mode);
        try {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.setOverScrollMode(performance ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            if (Build.VERSION.SDK_INT >= 26) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, performance);
            }
        } catch (Throwable ignored) {}
    }

    private long motionDuration(long qualityMs) {
        return PerformanceProfile.motionDuration(this, prefs, qualityMs);
    }

    private void installPerformanceCss() {
        if (webView == null || !isTrustedForumPage()) return;
        String mode = PerformanceProfile.resolve(this, prefs);
        String css = "";
        if (PerformanceProfile.PERFORMANCE.equals(mode)) {
            css = "*{animation-duration:.001ms!important;animation-delay:0ms!important;transition-duration:.001ms!important;scroll-behavior:auto!important}.Modal,.Dropdown-menu,.Composer,.App-header{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}";
        } else if (PerformanceProfile.BALANCED.equals(mode)) {
            css = ".Modal,.Dropdown-menu,.Composer,.App-header{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}html{scroll-behavior:auto!important}";
        }
        String escaped = css.replace("\\", "\\\\").replace("'", "\\'");
        String js = "(function(){try{" +
                "var id='hcf-native-performance-style',x=document.getElementById(id);" +
                (css.isEmpty()
                        ? "if(x&&x.parentNode)x.parentNode.removeChild(x);"
                        : "if(!x){x=document.createElement('style');x.id=id;document.head&&document.head.appendChild(x);}if(x)x.textContent='" + escaped + "';") +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    private void applySavedAccent() {
        if (prefs == null) return;
        String raw = prefs.getString(AppPrefs.NATIVE_ACCENT, "");
        int color = parseAccent(raw);
        if (color != 0) applyAccentColor(color);
    }

    private int parseAccent(String raw) {
        if (raw == null) return 0;
        String value = raw.trim();
        try {
            if (value.matches("#[0-9a-fA-F]{3}")) {
                char r=value.charAt(1), g=value.charAt(2), b=value.charAt(3);
                value = "#"+r+r+g+g+b+b;
            }
            if (value.matches("#[0-9a-fA-F]{6}")) return Color.parseColor(value);
        } catch (Throwable ignored) {}
        return 0;
    }

    private void applyAccentColor(int color) {
        try {
            if (pageProgress != null) pageProgress.setProgressTintList(ColorStateList.valueOf(color));
            if (drawerButton != null && Build.VERSION.SDK_INT >= 21) drawerButton.setImageTintList(ColorStateList.valueOf(color));
            if (reloadButton != null && Build.VERSION.SDK_INT >= 21) reloadButton.setImageTintList(ColorStateList.valueOf(color));
            if (hostBadge != null) hostBadge.setTextColor(color);
        } catch (Throwable ignored) {}
    }

    private void registerNetworkState() {
        if (networkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        String before = liveState;
                        updateLiveState("SYNCING");
                        if ("OFFLINE".equals(before)) {
                            showTransientBanner("Connection restored • syncing forum…");
                            if (statusOverlay != null && statusOverlay.getVisibility() == View.VISIBLE && webView != null) {
                                webView.postDelayed(MainActivity.this::reloadCurrentPage, 250L);
                            }
                        }
                    });
                }
                @Override public void onLost(Network network) {
                    runOnUiThread(() -> {
                        if (!isNetworkAvailable()) {
                            updateLiveState("OFFLINE");
                            showTransientBanner("Offline • forum will reconnect automatically");
                        }
                    });
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
            if (!isNetworkAvailable()) updateLiveState("OFFLINE");
        } catch (Throwable t) {
            AppLogger.warn(this, "network_callback", t.getClass().getSimpleName());
        }
    }

    private void unregisterNetworkState() {
        if (!networkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) {}
        networkCallback = null;
        networkCallbackRegistered = false;
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void openNotificationSystemSettings() {
        NotificationHelper.createChannel(this);
        NotificationHelper.openChannelSettings(this);
        AppLogger.info(this, "notification_settings", "drawer | " + NotificationHelper.status(this));
    }

    private void updateUrlChrome(String url) {
        if (currentUrlText == null) return;
        String value = url == null || url.trim().isEmpty() ? currentUrlString() : url;
        currentUrlText.setText(value);
        try {
            Uri uri = Uri.parse(value);
            if (ForumUrlRouter.isForumUrl(uri)) {
                updateHostChrome(uri.getHost());
                updateDrawerActiveState(uri);
            }
        } catch (Throwable ignored) {}
    }

    private void handleSpaRouteChange(String url) {
        try {
            Uri route = Uri.parse(url == null ? "" : url);
            if (!ForumUrlRouter.isForumUrl(route)) return;
            updateUrlChrome(route.toString());
            TelemetryService.noteRoute(this, route.toString());
            if (liveUpdater != null) {
                liveUpdater.reset();
                liveUpdater.poke();
            }
            AppLogger.info(this, "spa_route", AppLogger.safeUrl(route.toString()));
        } catch (Throwable ignored) {}
    }

    private void updateDrawerActiveState(Uri uri) {
        if (drawerHome == null) return;
        boolean home = false;
        boolean notifications = false;
        if (uri != null && ForumUrlRouter.isForumUrl(uri)) {
            String path = uri.getPath();
            home = path == null || path.isEmpty() || "/".equals(path);
            notifications = path != null && ("/notifications".equals(path) || path.startsWith("/notifications/"));
        }
        drawerHome.setBackgroundResource(home ? R.drawable.drawer_item_active_background : R.drawable.drawer_item_background);
        drawerHome.setTextColor(getColor(home ? R.color.hcf_cyan_bright : R.color.hcf_text));
        FaIcons.applyStart(drawerHome, R.drawable.fa_house);
        if (drawerNotifications != null) {
            drawerNotifications.setBackgroundResource(notifications ? R.drawable.drawer_item_active_background : R.drawable.drawer_item_background);
            drawerNotifications.setTextColor(getColor(notifications ? R.color.hcf_cyan_bright : R.color.hcf_text));
            FaIcons.applyStart(drawerNotifications, R.drawable.fa_bell);
        }
    }

    private void updateHostChrome(String host) {
        if (host == null) return;
        boolean primary = ForumConfig.PRIMARY_HOST.equalsIgnoreCase(host);
        if (hostBadge != null) {
            hostBadge.setText(primary ? "Primary" : "Backup");
            hostBadge.setTextColor(getColor(primary ? R.color.hcf_cyan_bright : R.color.hcf_yellow));
        }
        if (drawerHostText != null) {
            drawerHostText.setText((primary ? "Primary • " : "Backup • ") + host);
            drawerHostText.setTextColor(getColor(primary ? R.color.hcf_cyan : R.color.hcf_yellow));
        }
        if (drawerSwitchHost != null) {
            drawerSwitchHost.setText(primary ? "Switch to Backup Forum" : "Switch to Primary Forum");
        }
        updateHeaderSubtitle();
    }

    private void openDrawer() {
        if (drawerPanel.getVisibility() == View.VISIBLE) return;
        updateHostChrome(activeHost == null ? chooseInitialHost() : activeHost);
        updateIdentityChrome(ForumIdentity.load(this));
        drawerScrim.setAlpha(0f);
        drawerScrim.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(-dp(320));
        drawerPanel.setVisibility(View.VISIBLE);
        long scrimMs = motionDuration(170L);
        long panelMs = motionDuration(190L);
        if (panelMs <= 0L) {
            drawerScrim.setAlpha(1f);
            drawerPanel.setTranslationX(0f);
        } else {
            drawerScrim.animate().alpha(1f).setDuration(scrimMs).start();
            drawerPanel.animate().translationX(0f).setDuration(panelMs).start();
        }
        AppLogger.info(this, "drawer", "open");
    }

    private void closeDrawer() {
        if (drawerPanel.getVisibility() != View.VISIBLE) return;
        long scrimMs = motionDuration(150L);
        long panelMs = motionDuration(170L);
        if (panelMs <= 0L) {
            drawerScrim.setAlpha(0f);
            drawerScrim.setVisibility(View.GONE);
            drawerPanel.setTranslationX(-dp(320));
            drawerPanel.setVisibility(View.GONE);
        } else {
            drawerScrim.animate().alpha(0f).setDuration(scrimMs).withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
            drawerPanel.animate().translationX(-dp(320)).setDuration(panelMs).withEndAction(() -> drawerPanel.setVisibility(View.GONE)).start();
        }
        AppLogger.info(this, "drawer", "close");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void scheduleWhatsNew(boolean launchedBefore) {
        if (prefs == null) return;
        if (!launchedBefore) {
            // Fresh installs start on the current release and do not need an update notice.
            ReleaseNotes.seedForFreshInstall(prefs);
            return;
        }
        if (webView == null) return;
        webView.postDelayed(() -> {
            try {
                if (ReleaseNotes.shouldNotify(prefs)) showWhatsNewNotification();
            } catch (Throwable t) {
                AppLogger.error(MainActivity.this, "whats_new", t.getClass().getSimpleName());
            }
        }, 1500L);
    }

    private void showWhatsNewNotification() {
        if (welcomeBanner == null || prefs == null || isFinishing() || isDestroyed()) return;
        ReleaseNotes.markSeen(prefs);
        welcomeBanner.animate().cancel();
        welcomeBanner.setText("✨  What's New • " + BuildInfo.VERSION_TAG + "\n" + ReleaseNotes.SUMMARY + "  •  Tap to view");
        welcomeBanner.setContentDescription("What's new in " + BuildInfo.VERSION_TAG + ". Tap to view release notes.");
        welcomeBanner.setClickable(true);
        welcomeBanner.setFocusable(true);
        welcomeBanner.setAlpha(0f);
        welcomeBanner.setVisibility(View.VISIBLE);
        welcomeBanner.setOnClickListener(v -> {
            welcomeBanner.animate().cancel();
            welcomeBanner.setVisibility(View.GONE);
            welcomeBanner.setClickable(false);
            welcomeBanner.setFocusable(false);
            welcomeBanner.setOnClickListener(null);
            ReleaseNotes.showCustom(MainActivity.this, prefs, false);
        });
        welcomeBanner.animate().alpha(1f).setDuration(motionDuration(180L)).withEndAction(() ->
                welcomeBanner.postDelayed(() -> {
                    if (welcomeBanner == null || welcomeBanner.getVisibility() != View.VISIBLE) return;
                    welcomeBanner.animate().alpha(0f).setDuration(motionDuration(220L)).withEndAction(() -> {
                        welcomeBanner.setVisibility(View.GONE);
                        welcomeBanner.setClickable(false);
                        welcomeBanner.setFocusable(false);
                        welcomeBanner.setOnClickListener(null);
                    }).start();
                }, 7000L)).start();
        AppLogger.info(this, "whats_new_notification", BuildInfo.VERSION);
    }

    private void scheduleFirstRunPermissionSetup() {
        if (prefs.getBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, false)) return;
        if (webView == null) return;
        webView.postDelayed(this::showFirstRunPermissionSetup, 700L);
    }

    private void showFirstRunPermissionSetup() {
        if (isFinishing() || isDestroyed()) return;
        boolean needsNotifications = Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        boolean needsInstaller = Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this);
        if (!needsNotifications && !needsInstaller) {
            prefs.edit().putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true).apply();
            return;
        }
        StringBuilder detail = new StringBuilder("Harley's Clan Forum only asks for permissions it actually uses.\n\n");
        if (needsNotifications) detail.append("• Notifications — forum alerts and update status.\n");
        if (needsInstaller) detail.append("• Install app updates — lets Android install verified HCF update APKs after you confirm.\n");
        detail.append("\nThe app does not request location, contacts, microphone, or camera access.");
        new AlertDialog.Builder(this)
                .setTitle("Set up app permissions")
                .setMessage(detail.toString())
                .setPositiveButton("Continue", (dialog, which) -> requestFirstRunPermissions())
                .setNegativeButton("Not now", (dialog, which) -> {
                    prefs.edit().putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true).apply();
                    AppLogger.info(MainActivity.this, "permission_onboarding", "skipped");
                })
                .show();
    }

    private void requestFirstRunPermissions() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        requestUpdateInstallPermissionIfNeeded();
    }

    private void requestUpdateInstallPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(this)) {
            prefs.edit().putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true).apply();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Allow secure app updates")
                .setMessage("Android requires special approval before this app can open downloaded update APKs. Updates are limited to the trusted HCF release source and are verified for package name, newer version, and matching signing certificate before installation.")
                .setPositiveButton("Open Android settings", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(AppPrefs.INSTALL_PERMISSION_PROMPTED, true)
                            .putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true)
                            .apply();
                    try {
                        Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                        startActivityForResult(permission, UPDATE_INSTALL_PERMISSION_REQUEST);
                    } catch (Throwable t) {
                        AppLogger.error(MainActivity.this, "install_permission", t.getClass().getSimpleName());
                    }
                })
                .setNegativeButton("Later", (dialog, which) -> prefs.edit().putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true).apply())
                .show();
    }

    private void requestNotificationPermissionOnFirstRun() {
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)) return;
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        int promptedFor = prefs.getInt(AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION, 0);
        if (promptedFor >= BuildInfo.VERSION_CODE) return;
        prefs.edit()
                .putBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, true)
                .putInt(AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION, BuildInfo.VERSION_CODE)
                .apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    }

    private boolean isNativeInstallRoute(Uri uri) {
        if (uri == null || !ForumUrlRouter.isForumUrl(uri)) return false;
        String path = uri.getPath();
        if (path == null) return false;
        String normalized = path.trim().toLowerCase(java.util.Locale.US);
        return "/install".equals(normalized) || normalized.startsWith("/install/");
    }

    private void showBetaUpdateAvailableDialog(UpdateChecker.Release release) {
        if (release == null || isFinishing() || isDestroyed()) return;
        String availableCode = release.versionCode > 0L ? Long.toString(release.versionCode) : "Checking build code";
        String body = "A newer Harley's Clan Forum development build is ready.\n\n"
                + "Installed: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n"
                + "Available: v" + UpdateChecker.displayVersion(release) + " (" + availableCode + ")\n\n"
                + "Channel: Development / Beta\n"
                + "Update when you're ready to test the latest build.";
        new AlertDialog.Builder(this)
                .setTitle("Beta Update Available")
                .setMessage(body)
                .setNegativeButton("Later", null)
                .setPositiveButton("Update Now", (dialog, which) -> startNativeUpdateFlow())
                .show();
    }

    private void startNativeUpdateFlow() {
        if (nativeUpdateFlowActive || isFinishing() || isDestroyed()) return;
        nativeUpdateFlowActive = true;

        long downloaded = AppUpdateDownloader.downloadedId(this);
        if (downloaded > 0L) {
            nativeUpdateDownloadId = downloaded;
            AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(this, downloaded);
            if (verification.ok) {
                continueInstallAfterVerification(downloaded, verification.message);
                return;
            }
        }

        TextView message = new TextView(this);
        message.setText("Checking Development / Beta updates…");
        message.setTextColor(getColor(R.color.hcf_text));
        message.setTextSize(14f);
        int pad = dp(20);
        message.setPadding(pad, pad, pad, pad);
        nativeUpdateDialog = new AlertDialog.Builder(this)
                .setTitle("Harley's Clan Forum Update")
                .setView(message)
                .setNegativeButton("Cancel", (dialog, which) -> nativeUpdateFlowActive = false)
                .create();
        nativeUpdateDialog.setOnDismissListener(dialog -> {
            if (nativeUpdateDownloadId <= 0L) nativeUpdateFlowActive = false;
        });
        nativeUpdateDialog.show();

        UpdateChecker.check(this, UpdateChecker.CHANNEL_DEV, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                if (isFinishing() || isDestroyed()) return;
                if (!updateAvailable) {
                    if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                        message.setText("Installed build " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") is newer than the Development / Beta feed" + (release.versionCode > 0 ? " (" + release.versionCode + ")" : "") + ". No downgrade will be installed.");
                    } else {
                        message.setText("You're on the newest Development / Beta build.\n\nInstalled: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")");
                    }
                    nativeUpdateFlowActive = false;
                    return;
                }
                if (release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
                    message.setText("A newer Development / Beta build is published, but the release does not contain an installable APK.");
                    nativeUpdateFlowActive = false;
                    return;
                }
                long id = AppUpdateDownloader.enqueue(MainActivity.this, release, true);
                if (id <= 0L) {
                    message.setText("The update could not be downloaded. Check your connection and try again.");
                    nativeUpdateFlowActive = false;
                    return;
                }
                nativeUpdateDownloadId = id;
                showNativeUpdateDownload(release, id);
            }

            @Override
            public void onError(String error) {
                if (message != null) message.setText("Unable to check for updates.\n\n" + error);
                nativeUpdateFlowActive = false;
            }
        });
    }

    private void showNativeUpdateDownload(UpdateChecker.Release release, long downloadId) {
        if (nativeUpdateDialog != null) {
            try { nativeUpdateDialog.dismiss(); } catch (Throwable ignored) {}
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(12), pad, dp(8));

        TextView version = new TextView(this);
        version.setText("Installed: v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\nAvailable: v" + UpdateChecker.displayVersion(release) + (release.versionCode > 0 ? " (" + release.versionCode + ")" : ""));
        version.setTextColor(getColor(R.color.hcf_text));
        version.setTextSize(13f);
        box.addView(version, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        progressParams.topMargin = dp(14);
        box.addView(progress, progressParams);

        TextView detail = new TextView(this);
        detail.setText("Starting download…");
        detail.setTextColor(getColor(R.color.hcf_muted));
        detail.setTextSize(12f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(8);
        box.addView(detail, detailParams);

        nativeUpdateDialog = new AlertDialog.Builder(this)
                .setTitle("Downloading update")
                .setView(box)
                .setNegativeButton("Hide", null)
                .create();
        nativeUpdateDialog.setCanceledOnTouchOutside(false);
        nativeUpdateDialog.setOnDismissListener(dialog -> {
            // Hiding the progress UI never cancels DownloadManager. The APK keeps
            // downloading and the completion receiver will surface it if the app
            // is no longer in the foreground.
        });
        nativeUpdateDialog.show();
        pollNativeUpdateDownload(release, downloadId, progress, detail);
    }

    private void pollNativeUpdateDownload(UpdateChecker.Release release, long downloadId, ProgressBar progress, TextView detail) {
        if (downloadId <= 0L) return;
        AppUpdateDownloader.ProgressSnapshot state = AppUpdateDownloader.progress(this, downloadId);
        int percent = state.percent();
        if (percent >= 0) {
            progress.setIndeterminate(false);
            progress.setProgress(percent);
        } else {
            progress.setIndeterminate(true);
        }

        String bytes = formatUpdateBytes(state.downloadedBytes);
        String total = state.totalBytes > 0L ? formatUpdateBytes(state.totalBytes) : "unknown size";
        if (state.status == DownloadManager.STATUS_SUCCESSFUL) {
            progress.setIndeterminate(false);
            progress.setProgress(100);
            detail.setText("Download complete • verifying APK…");
            AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(this, downloadId);
            if (!verification.ok) {
                ErrorSystem.AppError updateError = ErrorSystem.updateVerification(verification.message);
                detail.setText(updateError.code + " • " + updateError.title + "\n" + updateError.message + "\n\n" + verification.message);
                AppLogger.error(MainActivity.this, "update_verification", updateError.code + " | " + verification.message);
                nativeUpdateFlowActive = false;
                nativeUpdateDownloadId = -1L;
                return;
            }
            detail.setText("Verified • opening Android installer…");
            continueInstallAfterVerification(downloadId, verification.message);
            return;
        }
        if (state.status == DownloadManager.STATUS_FAILED) {
            ErrorSystem.AppError updateError = ErrorSystem.updateDownloadFailure(state.reason);
            detail.setText(updateError.code + " • " + updateError.title + "\n" + updateError.message + "\n\nOpen /install to retry.");
            AppLogger.error(MainActivity.this, "update_download_failed", updateError.code + " | " + updateError.technical);
            nativeUpdateFlowActive = false;
            nativeUpdateDownloadId = -1L;
            return;
        }
        if (state.status == DownloadManager.STATUS_PAUSED) {
            detail.setText("Download paused • " + bytes + " / " + total + "\nAndroid will resume it automatically when possible.");
        } else {
            String prefix = percent >= 0 ? percent + "% • " : "Downloading • ";
            detail.setText(prefix + bytes + " / " + total);
        }
        mainHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && nativeUpdateDownloadId == downloadId) {
                pollNativeUpdateDownload(release, downloadId, progress, detail);
            }
        }, 350L);
    }

    private void continueInstallAfterVerification(long downloadId, String verificationMessage) {
        nativeUpdateDownloadId = downloadId;
        if (Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this)) {
            prefs.edit().putBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Allow Harley's Clan Forum to install updates")
                    .setMessage("The APK is fully downloaded and verified. Android needs permission for this app to open its update installer. The update will not be downloaded again.\n\n" + verificationMessage)
                    .setPositiveButton("Open Android settings", (dialog, which) -> {
                        try {
                            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                            startActivityForResult(permission, UPDATE_INSTALL_PERMISSION_REQUEST);
                        } catch (Throwable t) {
                            prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                            nativeUpdateFlowActive = false;
                            AppLogger.error(MainActivity.this, "install_permission", t.getClass().getSimpleName());
                        }
                    })
                    .setNegativeButton("Later", (dialog, which) -> nativeUpdateFlowActive = false)
                    .show();
            return;
        }
        boolean opened = AppUpdateDownloader.openInstaller(this, downloadId);
        AppLogger.info(this, "native_install_route", opened ? "installer-opened" : "installer-open-failed");
        if (!opened) {
            ErrorSystem.AppError updateError = ErrorSystem.installerOpenFailure("Package installer activity was unavailable or rejected the install intent.");
            new AlertDialog.Builder(this)
                    .setTitle(updateError.code + " • " + updateError.title)
                    .setMessage(updateError.message)
                    .setPositiveButton("OK", null)
                    .show();
            AppLogger.error(this, "native_install_route", updateError.code + " | " + updateError.technical);
        }
        nativeUpdateFlowActive = false;
        if (opened && nativeUpdateDialog != null) {
            try { nativeUpdateDialog.dismiss(); } catch (Throwable ignored) {}
        }
    }

    private String formatUpdateBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb);
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0);
    }

    private String chooseInitialHost() {
        if (!prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true)) return ForumConfig.PRIMARY_HOST;
        long fallbackUntil = prefs.getLong(AppPrefs.FALLBACK_UNTIL, 0L);
        return System.currentTimeMillis() < fallbackUntil
                ? ForumConfig.BACKUP_HOST
                : ForumConfig.PRIMARY_HOST;
    }

    private Uri currentForumUri() {
        String current = webView.getUrl();
        if (current != null) {
            Uri uri = Uri.parse(current);
            if (ForumUrlRouter.isForumUrl(uri)) return uri;
        }
        return Uri.parse(ForumUrlRouter.home(activeHost));
    }

    private void failOverFromPrimary(Uri failedUri, String reason) {
        failOverFromPrimary(failedUri, ErrorSystem.generic(reason));
    }

    private void failOverFromPrimary(Uri failedUri, ErrorSystem.AppError error) {
        if (!ForumConfig.PRIMARY_HOST.equals(activeHost) || switchingHosts) return;
        ErrorSystem.AppError failure = error == null ? ErrorSystem.generic("Primary forum connection failed.") : error;
        if (!prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true)) {
            AppLogger.warn(this, "primary_failure_no_failover", failure.code + " | " + failure.technical);
            showUnavailable(new ErrorSystem.AppError(failure.code, failure.title,
                    failure.message + " Automatic backup is disabled in App Settings.", failure.technical), failedUri);
            return;
        }

        prefs.edit().putLong(
                AppPrefs.FALLBACK_UNTIL,
                System.currentTimeMillis() + ForumConfig.PRIMARY_RETRY_COOLDOWN_MS
        ).apply();

        AppLogger.warn(this, "primary_failover", failure.code + " | route=" + AppLogger.safeUrl(failedUri.toString()));
        lastAppError = failure;
        lastErrorUri = failedUri;
        statusTitle.setText(R.string.status_switching);
        statusSubtitle.setText(failure.message + "\nTrying " + ForumConfig.BACKUP_HOST + "…");
        if (errorCodeText != null) {
            errorCodeText.setText(failure.code);
            errorCodeText.setVisibility(View.VISIBLE);
        }
        if (errorDetailsButton != null) errorDetailsButton.setVisibility(View.VISIBLE);
        statusOverlay.setVisibility(View.VISIBLE);
        errorActions.setVisibility(View.GONE);
        switchHost(ForumConfig.BACKUP_HOST, failedUri);
    }

    private void switchHost(String targetHost, Uri source) {
        switchingHosts = true;
        String previous = activeHost;
        activeHost = targetHost;
        prefs.edit().putString(AppPrefs.ACTIVE_HOST, targetHost).apply();
        showChecking(targetHost);
        webView.stopLoading();
        String target = ForumUrlRouter.equivalentOnHost(source, targetHost);
        updateHostChrome(targetHost);
        updateUrlChrome(target);
        AppLogger.info(this, "host_switch", previous + " -> " + targetHost + " | " + AppLogger.safeUrl(target));
        webView.loadUrl(target);
    }

    private void showChecking(String host) {
        final int generation = ++connectionUiGeneration;
        final String targetHost = host == null || host.trim().isEmpty() ? ForumConfig.PRIMARY_HOST : host;
        statusTitle.setText("Connecting to Harley's Clan Forum…");
        statusSubtitle.setText(targetHost);
        errorActions.setVisibility(View.GONE);
        if (errorCodeText != null) errorCodeText.setVisibility(View.GONE);
        if (errorDetailsButton != null) errorDetailsButton.setVisibility(View.GONE);
        startupProgress.setVisibility(View.VISIBLE);
        boolean showStartup = prefs == null || prefs.getBoolean(AppPrefs.SHOW_STARTUP_SCREEN, true);
        statusOverlay.setVisibility(showStartup ? View.VISIBLE : View.GONE);
        if (reloadButton != null) {
            reloadButton.setEnabled(false);
            reloadButton.setAlpha(0.45f);
        }
        updateHostChrome(targetHost);

        mainHandler.postDelayed(() -> {
            if (generation != connectionUiGeneration || startupProgress.getVisibility() != View.VISIBLE) return;
            statusTitle.setText("Securing connection…");
            statusSubtitle.setText("Verifying " + targetHost);
        }, 650L);
        mainHandler.postDelayed(() -> {
            if (generation != connectionUiGeneration || startupProgress.getVisibility() != View.VISIBLE) return;
            statusTitle.setText("Loading forum…");
            statusSubtitle.setText(targetHost);
        }, 1550L);
        mainHandler.postDelayed(() -> {
            if (generation != connectionUiGeneration || startupProgress.getVisibility() != View.VISIBLE) return;
            Uri failed = null;
            try {
                String current = webView == null ? null : webView.getUrl();
                failed = current == null || current.trim().isEmpty()
                        ? Uri.parse(ForumUrlRouter.home(targetHost)) : Uri.parse(current);
            } catch (Throwable ignored) {}
            ErrorSystem.AppError timeout = ErrorSystem.connectionTimeout(targetHost);
            if (ForumConfig.PRIMARY_HOST.equalsIgnoreCase(targetHost)
                    && prefs != null && prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true)) {
                failOverFromPrimary(failed == null ? Uri.parse(ForumUrlRouter.home(targetHost)) : failed, timeout);
            } else {
                showUnavailable(timeout, failed);
            }
        }, 18000L);
    }

    private void showUnavailable(String detail) {
        showUnavailable(ErrorSystem.generic(detail), currentForumUri());
    }

    private void showUnavailable(ErrorSystem.AppError error, Uri failedUri) {
        connectionUiGeneration++;
        switchingHosts = false;
        if (reloadButton != null) {
            reloadButton.setEnabled(true);
            reloadButton.setAlpha(1f);
        }
        boolean offline = !isNetworkAvailable();
        ErrorSystem.AppError shown = offline ? ErrorSystem.offline() : (error == null ? ErrorSystem.generic("The forum could not load.") : error);
        lastAppError = shown;
        lastErrorUri = failedUri;
        statusTitle.setText(shown.title);
        statusSubtitle.setText(shown.message);
        errorCodeText.setText(shown.code);
        errorCodeText.setVisibility(View.VISIBLE);
        errorDetailsButton.setVisibility(View.VISIBLE);
        if (offline) updateLiveState("OFFLINE");
        errorActions.setVisibility(View.VISIBLE);
        alternateButton.setText(ForumConfig.PRIMARY_HOST.equals(activeHost)
                ? R.string.use_backup : R.string.try_primary);
        statusOverlay.setVisibility(View.VISIBLE);
        pageProgress.setVisibility(View.GONE);
        startupProgress.setVisibility(View.GONE);
        updateHostChrome(activeHost);
        AppLogger.warn(this, "native_error_state", shown.code + " | " + shown.title + " | " + AppLogger.safeUrl(failedUri == null ? "" : failedUri.toString()));
    }

    private void showLastErrorDetails() {
        ErrorSystem.AppError error = lastAppError;
        if (error == null) return;
        String route = lastErrorUri == null ? "" : AppLogger.safeUrl(lastErrorUri.toString());
        String host = activeHost == null ? chooseInitialHost() : activeHost;
        String report = "Harley's Clan Forum Error Report\n"
                + "Error: " + error.code + "\n"
                + "Title: " + error.title + "\n"
                + "App: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n"
                + "Server: " + host + "\n"
                + "Network: " + (isNetworkAvailable() ? "connected" : "offline") + "\n"
                + "Route: " + (route.isEmpty() ? "not available" : route) + "\n"
                + "Technical: " + (error.technical.isEmpty() ? "not available" : error.technical);
        new AlertDialog.Builder(this)
                .setTitle(error.code + " • Technical details")
                .setMessage(report)
                .setPositiveButton("Copy report", (dialog, which) -> copyErrorReport(report))
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyErrorReport(String report) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("HCF error report", report));
            Toast.makeText(this, "Error report copied.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Could not copy the error report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideStatus() {
        connectionUiGeneration++;
        switchingHosts = false;
        if (reloadButton != null) {
            reloadButton.setEnabled(true);
            reloadButton.setAlpha(1f);
        }
        statusOverlay.setVisibility(View.GONE);
        startupProgress.setVisibility(View.GONE);
        if (errorCodeText != null) errorCodeText.setVisibility(View.GONE);
        if (errorDetailsButton != null) errorDetailsButton.setVisibility(View.GONE);
    }

    private boolean recoverWebViewRenderer(WebView deadView, String recoverUrl, ErrorSystem.AppError error) {
        try {
            Uri recoverUri = Uri.parse(recoverUrl);
            rendererRecoveryPending = true;
            showUnavailable(error, recoverUri);
            errorActions.setVisibility(View.GONE);
            startupProgress.setVisibility(View.VISIBLE);
            if (deadView != null) {
                try {
                    deadView.removeJavascriptInterface("HCFNative");
                    deadView.stopLoading();
                    ViewParentCompat.removeFromParent(deadView);
                    deadView.destroy();
                } catch (Throwable ignored) {}
            }
            ViewGroup content = findViewById(R.id.contentFrame);
            if (content == null) throw new IllegalStateException("Content frame unavailable");
            WebView replacement = new WebView(this);
            replacement.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (ThemeManager.isAmoled(this)) replacement.setBackgroundColor(Color.BLACK);
            content.addView(replacement, 0);
            webView = replacement;
            configureWebView();
            updateUrlChrome(recoverUrl);
            mainHandler.postDelayed(() -> {
                if (webView == replacement && !isFinishing() && !isDestroyed()) {
                    showChecking(activeHost == null ? chooseInitialHost() : activeHost);
                    replacement.loadUrl(recoverUrl);
                }
            }, 250L);
            return true;
        } catch (Throwable recoveryFailure) {
            AppLogger.error(this, "webview_renderer_recovery_failed", recoveryFailure.getClass().getSimpleName() + ": " + String.valueOf(recoveryFailure.getMessage()));
            showCrashSafeScreen(recoveryFailure);
            return true;
        }
    }

    private static final class ViewParentCompat {
        static void removeFromParent(View view) {
            if (view == null) return;
            android.view.ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
        }
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        if (!prefs.getBoolean(AppPrefs.EXTERNAL_LINKS, true)) {
            AppLogger.warn(this, "external_link_blocked", AppLogger.safeUrl(uri.toString()));
            Toast.makeText(this, "External links are disabled in App Settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.US);
        if ("mailto".equals(scheme) || "tel".equals(scheme)) {
            launchExternalIntent(uri);
            return;
        }

        LinkSafety.Result safety = LinkSafety.classify(uri);
        if (safety.status == LinkSafety.Status.BLOCKED) {
            showBlockedLink(safety);
            return;
        }
        if (safety.status == LinkSafety.Status.OFFICIAL) {
            launchExternalIntent(uri);
            return;
        }

        boolean firstVisit = !hasSeenExternalDomain(safety.host);
        if (safety.status == LinkSafety.Status.SUSPICIOUS || firstVisit) {
            showExternalLinkConfirmation(uri, safety, firstVisit);
            return;
        }
        launchExternalIntent(uri);
    }

    private void showExternalLinkConfirmation(Uri uri, LinkSafety.Result safety, boolean firstVisit) {
        String domain = safety.host == null || safety.host.isEmpty() ? "Unknown destination" : safety.host;
        StringBuilder message = new StringBuilder();
        message.append("You’re leaving Harley’s Clan Forum.\n\n");
        message.append(domain);
        if (safety.reason != null && !safety.reason.isEmpty()) {
            message.append("\n\n").append(safety.reason).append('.');
        }
        if (safety.status == LinkSafety.Status.SUSPICIOUS) {
            message.append("\n\nOnly continue if you trust this destination.");
        } else if (firstVisit) {
            message.append("\n\nThis is the first time this domain has been opened from the app.");
        }

        new AlertDialog.Builder(this)
                .setTitle(safety.status.label)
                .setIcon(linkSafetyIcon(safety.status))
                .setMessage(message.toString())
                .setPositiveButton("Open externally", (dialog, which) -> {
                    if (safety.status == LinkSafety.Status.EXTERNAL) markExternalDomainSeen(safety.host);
                    AppLogger.info(MainActivity.this, "safe_link_open", safety.status.label + " | " + safety.host);
                    launchExternalIntent(uri);
                })
                .setNegativeButton("Cancel", (dialog, which) ->
                        AppLogger.info(MainActivity.this, "safe_link_cancel", safety.status.label + " | " + safety.host))
                .show();
    }

    private int linkSafetyIcon(LinkSafety.Status status) {
        if (status == LinkSafety.Status.OFFICIAL) return R.drawable.fa_shield;
        if (status == LinkSafety.Status.EXTERNAL) return R.drawable.fa_arrow_up_right_from_square;
        if (status == LinkSafety.Status.SUSPICIOUS) return R.drawable.fa_triangle_exclamation;
        return R.drawable.fa_triangle_exclamation;
    }

    private void showBlockedLink(LinkSafety.Result safety) {
        String domain = safety.host == null || safety.host.isEmpty() ? "This destination" : safety.host;
        new AlertDialog.Builder(this)
                .setTitle(LinkSafety.Status.BLOCKED.label)
                .setIcon(linkSafetyIcon(LinkSafety.Status.BLOCKED))
                .setMessage(domain + " was blocked by Harley’s Clan Forum Safe Links.\n\n" + safety.reason)
                .setPositiveButton("OK", null)
                .show();
        AppLogger.warn(this, "safe_link_blocked", safety.reason + " | " + safety.host);
    }

    private void launchExternalIntent(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            HcfIntentChooser.show(this, intent, "Open with",
                    "Choose an app to continue outside Harley's Clan Forum.", true);
        } catch (Throwable e) {
            AppLogger.error(this, "external_link_failed", AppLogger.safeUrl(uri.toString()));
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasSeenExternalDomain(String host) {
        String clean = LinkSafety.canonicalHost(host);
        if (clean.isEmpty()) return false;
        java.util.Set<String> seen = prefs.getStringSet(AppPrefs.SAFE_LINKS_SEEN_DOMAINS, java.util.Collections.emptySet());
        return seen != null && seen.contains(clean);
    }

    private void markExternalDomainSeen(String host) {
        String clean = LinkSafety.canonicalHost(host);
        if (clean.isEmpty()) return;
        java.util.Set<String> current = prefs.getStringSet(AppPrefs.SAFE_LINKS_SEEN_DOMAINS, java.util.Collections.emptySet());
        java.util.Set<String> copy = new java.util.HashSet<>();
        if (current != null) copy.addAll(current);
        if (copy.add(clean)) prefs.edit().putStringSet(AppPrefs.SAFE_LINKS_SEEN_DOMAINS, copy).apply();
    }

    private boolean isTrustedForumPage() {
        try {
            String current = webView.getUrl();
            return current != null && ForumUrlRouter.isForumUrl(Uri.parse(current));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isUnsupportedPwaPushMessage(String message) {
        if (message == null) return false;
        String normalized = message.trim().toLowerCase(java.util.Locale.US);
        return normalized.contains("this browser does not support push notifications for progressive web apps")
                || normalized.contains("browser does not support push notifications")
                || normalized.contains("push notifications are not supported")
                || normalized.contains("push notification is not supported");
    }

    private void handleNativePushRequest() {
        AppLogger.info(this, "pwa_push_redirect", "native-notifications");
        if (!prefs.getBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true)) {
            prefs.edit().putBoolean(AppPrefs.NOTIFICATIONS_ENABLED, true).apply();
            NotificationSyncScheduler.apply(this);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        Toast.makeText(this, "Notifications are handled by the Harley's Clan Forum app.", Toast.LENGTH_LONG).show();
    }

    private void installForumBridge() {
        if (!isTrustedForumPage()) return;
        String script =
                "(function(){" +
                "if(window.__HCF_NATIVE_V040__){try{window.__HCF_NATIVE_V040__.sync();}catch(e){}return;}" +
                "var VERSION='" + BuildInfo.VERSION + "';" +
                "var WARNING='This browser does not support push notifications for progressive web apps.';" +
                "var REPLACEMENT=\"Push notifications are handled by the Harley's Clan Forum Android app. Use App Settings to manage notifications.\";" +
                "var lastIdentity='',lastSecurity='',lastRoute='';" +
                "try{if(!window.__HCF_ORIGINAL_ALERT__)window.__HCF_ORIGINAL_ALERT__=window.alert.bind(window);window.alert=function(m){var x=String(m||'');var l=x.toLowerCase();if(l.indexOf('browser does not support push notifications')>=0||l.indexOf('push notifications are not supported')>=0){try{HCFNative.requestNotificationPermission();}catch(e){}return;}return window.__HCF_ORIGINAL_ALERT__(m);};}catch(e){}" +
                "var send=function(t,b,u){try{HCFNative.notify(String(t||\"Harley's Clan Forum\"),String(b||''),String(u||location.href));}catch(e){}};" +
                "window.HCFApp={isNative:true,platform:'android',version:VERSION,notify:send,openSettings:function(){try{HCFNative.openSettings();}catch(e){}}};" +
                "try{" +
                "var NativeNotification=function(title,opts){opts=opts||{};send(title,opts.body||'',(opts.data&&opts.data.url)||location.href);};" +
                "Object.defineProperty(NativeNotification,'permission',{get:function(){try{return HCFNative.notificationsEnabled()?'granted':'default';}catch(e){return 'default';}}});" +
                "NativeNotification.requestPermission=function(){try{HCFNative.requestNotificationPermission();}catch(e){} return Promise.resolve('default');};" +
                "window.Notification=NativeNotification;" +
                "}catch(e){}" +
                "var iso=function(v){try{if(!v)return '';if(typeof v.toISOString==='function')return v.toISOString();if(v.$d&&typeof v.$d.toISOString==='function')return v.$d.toISOString();return String(v);}catch(e){return '';}};" +
                "var val=function(o,n,d){try{var x=o&&o[n];return typeof x==='function'?x.call(o):(x===undefined?d:x);}catch(e){return d;}};" +
                "var syncIdentity=function(){" +
                "try{" +
                "if(!window.app||!app.session){return;}" +
                "var u=app.session.user;" +
                "if(!u){var guest=JSON.stringify({loggedIn:false});if(guest!==lastIdentity){lastIdentity=guest;HCFNative.updateIdentity(guest,String(location.host||''));}return;}" +
                "var gs=[];try{var groups=val(u,'groups',[])||[];for(var gi=0;gi<groups.length;gi++){var g=groups[gi];var gn=val(g,'nameSingular','');if(gn)gs.push(String(gn));}}catch(e){}" +
                "var cs=[];var addc=function(x){x=String(x||'').trim();if(x&&cs.indexOf(x)<0)cs.push(x);};" +
                "var email=String(val(u,'email','')||'');if(email)addc(!!val(u,'isEmailConfirmed',false)?'Email (verified)':'Email');" +
                "try{var attrs=(u.data&&u.data.attributes)||{};var ks=Object.keys(attrs);var pm={discord:'Discord',google:'Google'};for(var ki=0;ki<ks.length;ki++){var lk=String(ks[ki]||'').toLowerCase();var av=attrs[ks[ki]];if(av===null||av===undefined||av===false||av==='')continue;for(var pk in pm){if(lk.indexOf(pk)>=0)addc(pm[pk]);}}}catch(e){}" +
                "try{var rel=u.data&&u.data.relationships&&u.data.relationships.loginProviders&&u.data.relationships.loginProviders.data;if(rel&&rel.length){for(var ri=0;ri<rel.length;ri++){var pid=String((rel[ri]&&rel[ri].id)||'').toLowerCase();if(pid.indexOf('discord')>=0)addc('Discord');if(pid.indexOf('google')>=0)addc('Google');}}}catch(e){}" +
                "var data={loggedIn:true,id:String(val(u,'id','')||''),username:String(val(u,'username','')||''),slug:String(val(u,'slug','')||''),displayName:String(val(u,'displayName','')||''),email:email,emailConfirmed:!!val(u,'isEmailConfirmed',false),avatarUrl:String(val(u,'avatarUrl','')||''),groups:gs,connections:cs,isAdmin:!!val(u,'isAdmin',false),joinTime:iso(val(u,'joinTime',null)),lastSeenAt:iso(val(u,'lastSeenAt',null)),unreadNotificationCount:Number(val(u,'unreadNotificationCount',0)||0),newNotificationCount:Number(val(u,'newNotificationCount',0)||0),discussionCount:Number(val(u,'discussionCount',0)||0),commentCount:Number(val(u,'commentCount',0)||0)};" +
                "var payload=JSON.stringify(data);if(payload!==lastIdentity){lastIdentity=payload;HCFNative.updateIdentity(payload,String(location.host||''));}" +
                "}catch(e){}" +
                "};" +
                "var syncSecurity=function(){" +
                "try{" +
                "if(!window.app||!app.session||!app.session.user||!document.body)return;" +
                "var path=String(location.pathname||'').replace(/\\/+$/,'');if(path.indexOf('/u/')!==0||path.slice(-9)!='/security')return;" +
                "var bodyText=String(document.body.innerText||'').toLowerCase();" +
                "var providers=[];var addp=function(x){x=String(x||'').trim();if(x&&providers.indexOf(x)<0)providers.push(x);};" +
                "var nodes=document.querySelectorAll('button,a,li,.Form-group,.Setting,.LoginProvider');" +
                "var pm={discord:'Discord',google:'Google'};" +
                "for(var ni=0;ni<nodes.length;ni++){var nt=String(nodes[ni].innerText||nodes[ni].textContent||'').toLowerCase();if(!(nt.indexOf('disconnect')>=0||nt.indexOf('connected')>=0||nt.indexOf('unlink')>=0||nt.indexOf('linked')>=0))continue;for(var pk in pm){if(nt.indexOf(pk)>=0)addp(pm[pk]);}}" +
                "var sessions=document.querySelectorAll('.AccessTokensList-item').length;" +
                "var activeSessions=document.querySelectorAll('.AccessTokensList-item--active').length;" +
                "var data={seen:true,path:path,sessionCount:Number(sessions||0),activeSessionCount:Number(activeSessions||0),providers:providers,passwordControls:(bodyText.indexOf('password')>=0),emailControls:(bodyText.indexOf('email')>=0),twoFactorControls:(bodyText.indexOf('two-factor')>=0||bodyText.indexOf('two factor')>=0||bodyText.indexOf('2fa')>=0||bodyText.indexOf('authenticator')>=0)};" +
                "var payload=JSON.stringify(data);if(payload!==lastSecurity){lastSecurity=payload;HCFNative.updateSecuritySummary(payload,String(location.host||''));}" +
                "}catch(e){}" +
                "};" +
                "var reportRoute=function(){try{var u=String(location.href||'');if(u!==lastRoute){lastRoute=u;HCFNative.routeChanged(u);}}catch(e){}};" +
                "var fixText=function(root){try{if(!root)return;if(root.nodeType===3){var v=root.nodeValue||'';if(v.indexOf(WARNING)>=0)root.nodeValue=v.split(WARNING).join(REPLACEMENT);return;}var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);var n,c=0;while((n=w.nextNode())&&c++<500){var v=n.nodeValue||'';if(v.indexOf(WARNING)>=0)n.nodeValue=v.split(WARNING).join(REPLACEMENT);}}catch(e){}};" +
                "var fixSecurityLabels=function(){try{if(String(location.pathname||'').indexOf('/security')<0)return;var active=document.querySelectorAll('.AccessTokensList-item--active .AccessTokensList-item-title-main');for(var i=0;i<active.length;i++){var t=(active[i].textContent||'').trim();if(t&&t.indexOf(\"Harley's Clan Forum App\")<0)active[i].textContent=\"Harley's Clan Forum App on AndroidOS\";}}catch(e){}};" +
                "var sync=function(){reportRoute();syncIdentity();syncSecurity();fixSecurityLabels();};" +
                "try{if(!window.__HCF_ROUTE_HOOK__){window.__HCF_ROUTE_HOOK__=true;['pushState','replaceState'].forEach(function(k){var old=history[k];if(typeof old!=='function')return;history[k]=function(){var r=old.apply(this,arguments);setTimeout(reportRoute,0);return r;};});window.addEventListener('popstate',reportRoute);window.addEventListener('hashchange',reportRoute);}}catch(e){}" +
                "if(document.body){fixText(document.body);window.__HCF_NATIVE_OBSERVER__=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var added=ms[i].addedNodes||[];for(var j=0;j<added.length;j++)fixText(added[j]);}try{clearTimeout(window.__HCF_SYNC_TIMER__);}catch(e){}window.__HCF_SYNC_TIMER__=setTimeout(sync,75);});window.__HCF_NATIVE_OBSERVER__.observe(document.body,{childList:true,subtree:true});}" +
                "window.__HCF_NATIVE_V040__={sync:sync};sync();" +
                "if(!window.__HCF_NATIVE_TIMER__){window.__HCF_NATIVE_TIMER__=setInterval(sync,1000);}" +
                "try{window.dispatchEvent(new CustomEvent('hcf-app-ready',{detail:{platform:'android',version:VERSION}}));}catch(e){}" +
                "})();";
        webView.evaluateJavascript(script, null);
        installPerformanceCss();
        installWebThemeBridge();
        installNativeMediaAndAccentHooks();
        AppLogger.info(this, "web_bridge_installed", AppLogger.safeUrl(webView.getUrl()));
    }

    private void installWebThemeBridge() {
        if (webView == null || !isTrustedForumPage()) return;
        String scheme = ThemeManager.webColorScheme(this);
        String fofType = "dark".equals(scheme) ? "night" : "day";
        String js = "(function(){try{" +
                "var m='" + scheme + "',ft='" + fofType + "';" +
                "window.__HCF_APP_THEME__=m;" +
                "var apply=function(){try{" +
                "var changed=false;" +
                "var d=document.documentElement;if(d){d.setAttribute('data-hcf-app-theme',m);d.style.colorScheme=m;}" +
                "var b=document.body;if(b)b.setAttribute('data-hcf-app-theme',m);" +
                "var meta=document.querySelector('meta[name=\\\"color-scheme\\\"]');" +
                "if(!meta){meta=document.createElement('meta');meta.name='color-scheme';document.head&&document.head.appendChild(meta);}" +
                "if(meta&&meta.content!==m){meta.content=m;changed=true;}" +
                "var u='';try{if(window.app&&app.data)u=String(app.data['fof-nightmode.assets.'+ft]||'');}catch(e){}" +
                "if(u&&document.head){" +
                "var own=document.getElementById('hcf-app-theme-stylesheet');" +
                "if(!own){own=document.createElement('link');own.id='hcf-app-theme-stylesheet';own.rel='stylesheet';document.head.appendChild(own);changed=true;}" +
                "if(own.href!==u){own.href=u;changed=true;}" +
                "var links=document.querySelectorAll('link.nightmode,link.nightmode-light,link.nightmode-dark');" +
                "for(var i=0;i<links.length;i++){var l=links[i];if(l===own)continue;if(!l.disabled){l.disabled=true;changed=true;}if(l.media!=='not all'){l.media='not all';changed=true;}}" +
                "}" +
                "if(changed||window.__HCF_APP_FORCED_FOF__!==ft){window.__HCF_APP_FORCED_FOF__=ft;try{document.dispatchEvent(new CustomEvent('fofnightmodechange',{detail:ft}));}catch(e){}}" +
                "}catch(e){}};" +
                "apply();" +
                "try{if(!window.__HCF_APP_THEME_OBSERVER__&&document.head){window.__HCF_APP_THEME_OBSERVER__=new MutationObserver(function(){setTimeout(apply,0);});window.__HCF_APP_THEME_OBSERVER__.observe(document.head,{childList:true,subtree:true,attributes:true,attributeFilter:['href','class','media','disabled']});}}catch(e){}" +
                "if(!window.__HCF_APP_THEME_TIMER__){window.__HCF_APP_THEME_TIMER__=setInterval(apply,1000);}" +
                "try{window.dispatchEvent(new CustomEvent('hcf-app-theme-change',{detail:{theme:m}}));}catch(e){}" +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    private void installNativeMediaAndAccentHooks() {
        if (webView == null || !isTrustedForumPage()) return;
        String js = "(function(){try{" +
                "var r=getComputedStyle(document.documentElement),c='';" +
                "var keys=['--primary-color','--hcf-cyan','--cyan','--hc','--hc2'];for(var i=0;i<keys.length&&!c;i++)c=String(r.getPropertyValue(keys[i])||'').trim();" +
                "if(!c){var m=document.querySelector('meta[name=theme-color]');if(m)c=String(m.content||'').trim();}" +
                "if(/^#[0-9a-f]{3}([0-9a-f]{3})?$/i.test(c)){try{HCFNative.updateAccent(c);}catch(e){}}" +
                "if(!window.__HCF_NATIVE_MEDIA__){window.__HCF_NATIVE_MEDIA__=true;document.addEventListener('click',function(ev){try{" +
                "var t=ev.target;if(!t||!t.closest)return;var body=t.closest('.Post-body');if(!body)return;" +
                "var el=t.closest('img,video');if(!el)return;var k=String(el.tagName||'').toLowerCase();var u='';" +
                "if(k==='img')u=String(el.currentSrc||el.src||'');else{u=String(el.currentSrc||el.src||'');if(!u){var src=el.querySelector('source');if(src)u=String(src.src||'');}}" +
                "if(!/^https:\\/\\//i.test(u))return;ev.preventDefault();ev.stopPropagation();HCFNative.openMedia(u,k);" +
                "}catch(e){}},true);}" +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    private void recordNotificationCount(String userId, int newCount, String host) {
        if (userId == null || userId.trim().isEmpty()) return;
        if (!ForumUrlRouter.isForumHost(host)) host = activeHost == null ? ForumConfig.PRIMARY_HOST : activeHost;
        String previousSessionUser = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        boolean changedUser = previousSessionUser == null || !userId.equals(previousSessionUser);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(AppPrefs.SESSION_USER_ID, userId)
                .putString(AppPrefs.ACTIVE_HOST, host);
        if (changedUser) {
            editor.remove(AppPrefs.LAST_NOTIFICATION_COUNT)
                    .remove(AppPrefs.DELIVERED_NOTIFICATION_IDS);
        }
        editor.apply();
        int delta = NotificationHelper.recordForumNotificationCount(this, newCount, host, "foreground");
        updateNotificationChrome(newCount);
        if (delta > 0) {
            ForumNotificationSync.deliverObservedCountAsync(this, host, delta, "foreground");
        }
        if (changedUser) {
            NotificationSyncScheduler.apply(this);
        }
    }

    private void handleIdentityUpdate(String json, String host) {
        if (!ForumUrlRouter.isForumHost(host)) host = activeHost == null ? ForumConfig.PRIMARY_HOST : activeHost;
        try {
            ForumIdentity.Snapshot previous = ForumIdentity.load(this);
            ForumIdentity.Snapshot snapshot = ForumIdentity.fromBridgeJson(json, host);
            if (!snapshot.loggedIn
                    || (previous.loggedIn && !previous.userId.isEmpty()
                    && !previous.userId.equals(snapshot.userId))) {
                ForumSecurity.clear(this);
            }
            ForumIdentity.save(this, snapshot);
            updateIdentityChrome(snapshot);
            if (snapshot.loggedIn && !snapshot.userId.isEmpty()) {
                recordNotificationCount(snapshot.userId, Math.max(snapshot.unreadNotifications, snapshot.newNotifications), host);
            } else {
                prefs.edit()
                        .remove(AppPrefs.SESSION_USER_ID)
                        .remove(AppPrefs.LAST_NOTIFICATION_COUNT)
                        .remove(AppPrefs.DELIVERED_NOTIFICATION_IDS)
                        .apply();
                updateNotificationChrome(0);
                NotificationSyncScheduler.apply(this);
            }
            if (welcomeBackPending) {
                welcomeBackPending = false;
                showWelcomeBanner(snapshot);
            }
        } catch (Throwable t) {
            AppLogger.warn(this, "identity_sync", t.getClass().getSimpleName());
        }
    }

    private void updateIdentityChrome(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null) snapshot = ForumIdentity.guest(activeHost == null ? ForumConfig.PRIMARY_HOST : activeHost);
        ForumSecurity.Snapshot security = ForumSecurity.load(this);
        if (drawerIdentityText != null) drawerIdentityText.setText(snapshot.identityLabel());
        if (drawerIdentityUsername != null) drawerIdentityUsername.setText(snapshot.usernameDisplay());
        if (drawerIdentityMeta != null) drawerIdentityMeta.setText(snapshot.identityMetaLabel());
        // Background notification sync can be newer than the cached ForumIdentity snapshot.
        // Use the persisted live count when it belongs to this signed-in session so opening
        // the drawer/resuming the app cannot incorrectly reset a visible badge back to 0.
        updateNotificationChrome(effectiveNotificationCount(snapshot));
        String connected = ForumSecurity.mergeLabels(snapshot.connections,
                security.seen ? security.providers : "");
        if (connected == null) connected = "";
        boolean emailConnected = snapshot.loggedIn
                && (!snapshot.email.isEmpty() || containsProvider(connected, "email"));
        boolean discordConnected = snapshot.loggedIn && containsProvider(connected, "discord");
        if (drawerLinkedAccounts != null) drawerLinkedAccounts.setVisibility(snapshot.loggedIn ? View.VISIBLE : View.GONE);
        updateProviderChip(drawerProviderEmail, "Email", emailConnected, snapshot.loggedIn);
        updateProviderChip(drawerProviderDiscord, "Discord", discordConnected, snapshot.loggedIn);
        if (drawerSecurity != null) {
            drawerSecurity.setEnabled(snapshot.loggedIn);
            drawerSecurity.setAlpha(snapshot.loggedIn ? 1f : 0.48f);
            drawerSecurity.setText(snapshot.loggedIn ? "Account Security" : "Account Security • Sign in required");
        }
        if (drawerIdentityAvatar != null) {
            // Real forum avatars should visually fill the rounded frame. Keep the bundled
            // HTG placeholder on FIT_CENTER so the app logo itself is never cropped.
            boolean hasForumAvatar = snapshot.loggedIn
                    && snapshot.avatarUrl != null
                    && !snapshot.avatarUrl.trim().isEmpty();
            drawerIdentityAvatar.setScaleType(hasForumAvatar
                    ? ImageView.ScaleType.CENTER_CROP
                    : ImageView.ScaleType.FIT_CENTER);
            drawerIdentityAvatar.setAdjustViewBounds(false);
            drawerIdentityAvatar.setCropToPadding(false);
            int avatarInset = dp(hasForumAvatar ? 2 : 6);
            drawerIdentityAvatar.setPadding(avatarInset, avatarInset, avatarInset, avatarInset);
            drawerIdentityAvatar.setClipToOutline(true);
        }
        loadIdentityAvatar(snapshot);
    }

    private int effectiveNotificationCount(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null || !snapshot.loggedIn) return 0;
        int identityCount = Math.max(snapshot.unreadNotifications, snapshot.newNotifications);
        if (prefs == null || !prefs.contains(AppPrefs.LAST_NOTIFICATION_COUNT)) return identityCount;

        String sessionUser = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        if (sessionUser != null && !sessionUser.trim().isEmpty()
                && snapshot.userId != null && !snapshot.userId.trim().isEmpty()
                && !sessionUser.equals(snapshot.userId)) {
            return identityCount;
        }
        return Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, identityCount));
    }

    private static boolean containsProvider(String labels, String provider) {
        if (labels == null || provider == null) return false;
        return labels.toLowerCase(java.util.Locale.US).contains(provider.toLowerCase(java.util.Locale.US));
    }

    private void updateProviderChip(TextView view, String label, boolean connected, boolean signedIn) {
        if (view == null) return;
        view.setText(label);
        view.setAlpha(connected ? 1f : (signedIn ? 0.58f : 0.42f));
        view.setContentDescription(label + (connected ? " connected" : (signedIn ? " not linked" : " unavailable while signed out")));
    }

    private void loadIdentityAvatar(ForumIdentity.Snapshot snapshot) {
        if (drawerIdentityAvatar == null) return;

        final boolean hasAvatar = snapshot != null
                && snapshot.loggedIn
                && snapshot.avatarUrl != null
                && !snapshot.avatarUrl.trim().isEmpty();

        if (!hasAvatar) {
            drawerIdentityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int placeholderInset = dp(6);
            drawerIdentityAvatar.setPadding(placeholderInset, placeholderInset, placeholderInset, placeholderInset);
            if (!"__hcf_default__".equals(drawerIdentityAvatar.getTag())) {
                drawerIdentityAvatar.setImageResource(R.drawable.htg_app_logo);
                drawerIdentityAvatar.setTag("__hcf_default__");
            }
            identityAvatarRequestedUrl = "";
            identityAvatarLoadedUrl = "";
            identityAvatarBitmap = null;
            return;
        }

        final String requested = snapshot.avatarUrl.trim();
        Uri uri;
        try { uri = Uri.parse(requested); } catch (Throwable ignored) { return; }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ForumUrlRouter.isForumHost(uri.getHost())) return;

        // Avoid resetting the ImageView on every 10-second identity sync. The old
        // implementation set the default image before each network fetch, which
        // caused the visible avatar flicker.
        if (requested.equals(identityAvatarLoadedUrl) && identityAvatarBitmap != null) {
            drawerIdentityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int avatarInset = dp(2);
            drawerIdentityAvatar.setPadding(avatarInset, avatarInset, avatarInset, avatarInset);
            if (!requested.equals(drawerIdentityAvatar.getTag())) {
                drawerIdentityAvatar.setImageBitmap(identityAvatarBitmap);
                drawerIdentityAvatar.setTag(requested);
            }
            return;
        }
        if (requested.equals(identityAvatarRequestedUrl)) return;

        identityAvatarRequestedUrl = requested;
        drawerIdentityAvatar.setTag(requested);

        new Thread(() -> {
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL(requested).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setUseCaches(true);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
                if (connection.getResponseCode() != 200) return;
                final Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    if (drawerIdentityAvatar != null && requested.equals(drawerIdentityAvatar.getTag())) {
                        identityAvatarBitmap = bitmap;
                        identityAvatarLoadedUrl = requested;
                        identityAvatarRequestedUrl = "";
                        drawerIdentityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        int avatarInset = dp(2);
                        drawerIdentityAvatar.setPadding(avatarInset, avatarInset, avatarInset, avatarInset);
                        drawerIdentityAvatar.setImageBitmap(bitmap);
                    }
                });
            } catch (Throwable ignored) {
                runOnUiThread(() -> {
                    if (requested.equals(identityAvatarRequestedUrl)) identityAvatarRequestedUrl = "";
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "hcf-identity-avatar").start();
    }

    private void handleSecuritySummaryUpdate(String json, String host) {
        if (!ForumUrlRouter.isForumHost(host)) host = activeHost == null ? ForumConfig.PRIMARY_HOST : activeHost;
        try {
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            if (!identity.loggedIn) {
                ForumSecurity.clear(this);
                return;
            }
            ForumSecurity.Snapshot summary = ForumSecurity.fromBridgeJson(json, host);
            ForumSecurity.save(this, summary);
            updateIdentityChrome(identity);
            AppLogger.info(this, "security_identity_sync",
                    "sessions=" + summary.sessionCount + " active=" + summary.activeSessionCount);
        } catch (Throwable t) {
            AppLogger.warn(this, "security_identity_sync", t.getClass().getSimpleName());
        }
    }

    private String accountSecurityUrl(ForumIdentity.Snapshot identity) {
        if (identity == null || !identity.loggedIn) return "";
        String handle = !identity.slug.isEmpty() ? identity.slug : identity.username;
        if (handle == null || handle.trim().isEmpty()) return "";
        String host = activeHost;
        if (!ForumUrlRouter.isForumHost(host)) host = identity.host;
        if (!ForumUrlRouter.isForumHost(host)) host = chooseInitialHost();
        return "https://" + host + "/u/" + Uri.encode(handle.trim()) + "/security";
    }

    private void openAccountSecurity() {
        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        String target = accountSecurityUrl(identity);
        if (target.isEmpty()) {
            Toast.makeText(this, "Sign in to the forum to open Account Security.", Toast.LENGTH_SHORT).show();
            return;
        }
        updateUrlChrome(target);
        if (webView != null) webView.loadUrl(target);
        AppLogger.info(this, "account_security_open", AppLogger.safeUrl(target));
    }

    private void showWelcomeBanner(ForumIdentity.Snapshot snapshot) {
        if (welcomeBanner == null || snapshot == null) return;
        String message = snapshot.loggedIn
                ? "Welcome back @" + snapshot.identityLabel()
                : "Welcome back • Guest_Protocol";
        showTransientBanner(message);
        AppLogger.info(this, "welcome_identity", snapshot.loggedIn ? snapshot.userId : "guest");
    }

    private void showTransientBanner(String message) {
        if (welcomeBanner == null || message == null || message.trim().isEmpty()) return;
        welcomeBanner.setOnClickListener(null);
        welcomeBanner.setClickable(false);
        welcomeBanner.setFocusable(false);
        welcomeBanner.setContentDescription(message);
        welcomeBanner.setText(message);
        welcomeBanner.animate().cancel();
        welcomeBanner.setAlpha(0f);
        welcomeBanner.setVisibility(View.VISIBLE);
        welcomeBanner.animate().alpha(1f).setDuration(motionDuration(180L)).withEndAction(() ->
                welcomeBanner.postDelayed(() -> {
                    if (welcomeBanner == null) return;
                    welcomeBanner.animate().alpha(0f).setDuration(motionDuration(220L)).withEndAction(() -> welcomeBanner.setVisibility(View.GONE)).start();
                }, 3200L)).start();
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (!ForumUrlRouter.isForumUrl(uri)) return;
        String host = uri.getHost();
        if (!ForumUrlRouter.isForumHost(host)) return;
        activeHost = host;
        if (prefs != null) prefs.edit().putString(AppPrefs.ACTIVE_HOST, activeHost).apply();
        String target = ForumUrlRouter.equivalentOnHost(uri, host);
        updateHostChrome(host);
        updateUrlChrome(target);
        AppLogger.info(this, "forum_link_open", AppLogger.safeUrl(target));
        if (webView != null) webView.loadUrl(target);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeManager.changedSince(this, appliedThemeSignature)) {
            recreate();
            return;
        }
        resumeUpdateInstallPermissionIfNeeded();
        if (launchFailed || webView == null) return;
        long pausedAt = prefs.getLong(AppPrefs.LAST_MAIN_PAUSED_AT, 0L);
        if (pausedAt > 0L && System.currentTimeMillis() - pausedAt >= 20000L) {
            welcomeBackPending = true;
        }
        try {
            registerNotificationEvents();
            registerNetworkState();
            applyChromePreferences();
            applyPerformanceMode();
            applySavedAccent();
            updateHostChrome(activeHost == null ? chooseInitialHost() : activeHost);
            updateUrlChrome(webView.getUrl());
            updateIdentityChrome(ForumIdentity.load(this));
            installForumBridge();
            applyLiveUpdatePreference();
            if (liveUpdater != null) liveUpdater.poke();
            NotificationSyncScheduler.apply(this);
            String sessionUser = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            if (sessionUser != null && !sessionUser.trim().isEmpty()) {
                InstantNotificationService.requestImmediateSync(this);
            }
            AppLogger.info(this, "main_resume", activeHost == null ? "starting" : activeHost);
        } catch (Throwable t) {
            AppLogger.error(this, "main_resume", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    private void resumeUpdateInstallPermissionIfNeeded() {
        if (prefs == null || !prefs.getBoolean(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION, false)) return;
        if (Build.VERSION.SDK_INT >= 26 && !AppSecurity.canInstallUpdates(this)) return;
        prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
        long ready = nativeUpdateDownloadId > 0L ? nativeUpdateDownloadId : AppUpdateDownloader.downloadedId(this);
        if (ready > 0L) {
            continueInstallAfterVerification(ready, "Install permission enabled • downloaded update remains verified.");
        }
    }

    @Override
    protected void onPause() {
        AppLogger.info(this, "main_pause", activeHost == null ? "" : activeHost);
        if (prefs != null) prefs.edit().putLong(AppPrefs.LAST_MAIN_PAUSED_AT, System.currentTimeMillis()).apply();
        if (liveUpdater != null) liveUpdater.stop();
        unregisterNotificationEvents();
        unregisterNetworkState();
        super.onPause();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        try {
            if (!launchFailed && event != null && drawerPanel != null) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        drawerSwipeStartX = event.getX();
                        drawerSwipeStartY = event.getY();
                        drawerSwipeStartAt = System.currentTimeMillis();
                        int width = getResources().getDisplayMetrics().widthPixels;
                        int edge = Math.max(dp(64), Math.round(width * 0.16f));
                        drawerSwipeCandidate = drawerPanel.getVisibility() != View.VISIBLE
                                && drawerSwipeStartX >= (width - edge);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (drawerSwipeCandidate) {
                            float dx = event.getX() - drawerSwipeStartX;
                            float dy = event.getY() - drawerSwipeStartY;
                            long elapsed = System.currentTimeMillis() - drawerSwipeStartAt;
                            boolean horizontal = Math.abs(dx) >= Math.max(dp(72), Math.abs(dy) * 1.35f);
                            if (dx <= -dp(72) && horizontal && elapsed <= 1000L) {
                                drawerSwipeCandidate = false;
                                openDrawer();
                                AppLogger.info(this, "drawer_swipe", "right-to-left");
                            }
                        }
                        drawerSwipeCandidate = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        drawerSwipeCandidate = false;
                        break;
                    default:
                        break;
                }
            }
        } catch (Throwable ignored) {}
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (launchFailed || webView == null) {
            super.onBackPressed();
            return;
        }
        if (drawerPanel != null && drawerPanel.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return;
        }
        if (statusOverlay.getVisibility() == View.VISIBLE && webView.getUrl() != null) {
            hideStatus();
            return;
        }

        // Give Flarum's transient UI first chance to consume Back. This prevents
        // Android Back from navigating away while a modal or composer is open.
        String js = "(function(){try{" +
                "var vis=function(e){return !!(e&&e.offsetParent!==null);};" +
                "var m=document.querySelector('.ModalManager .Modal,.Modal');" +
                "if(vis(m)){var c=m.querySelector('.Modal-close,button[aria-label=\\\"Close\\\"],button[title=\\\"Close\\\"]');if(c)c.click();else document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));return 'handled-modal';}" +
                "var p=document.querySelector('.Composer');" +
                "if(vis(p)){var b=p.querySelector('.Composer-controls .item-minimize button,.Composer-controls .item-close button,button[aria-label=\\\"Minimize\\\"],button[aria-label=\\\"Close\\\"]');if(b)b.click();else document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));return 'handled-composer';}" +
                "return 'navigate';}catch(e){return 'navigate';}})();";
        try {
            webView.evaluateJavascript(js, result -> {
                if (result != null && result.contains("handled")) {
                    AppLogger.info(MainActivity.this, "back_consumed", result.replace("\\\"", ""));
                    return;
                }
                if (webView.canGoBack()) webView.goBack();
                else MainActivity.super.onBackPressed();
            });
        } catch (Throwable t) {
            if (webView.canGoBack()) webView.goBack();
            else super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (!launchFailed && webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        AppLogger.info(this, "main_destroy", activeHost == null ? "" : activeHost);
        mainHandler.removeCallbacksAndMessages(null);
        if (nativeUpdateDialog != null) {
            try { nativeUpdateDialog.dismiss(); } catch (Throwable ignored) {}
            nativeUpdateDialog = null;
        }
        if (liveUpdater != null) liveUpdater.destroy();
        unregisterNotificationEvents();
        unregisterNetworkState();
        if (!launchFailed && webView != null) {
            webView.removeJavascriptInterface("HCFNative");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
            boolean allowed = AppSecurity.canInstallUpdates(this);
            AppLogger.info(this, "install_permission", allowed ? "allowed" : "not-allowed");
            prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
            Toast.makeText(this, allowed ? "Secure app updates are enabled." : "Update installation permission was not enabled.", Toast.LENGTH_SHORT).show();
            if (allowed) {
                long ready = nativeUpdateDownloadId > 0L ? nativeUpdateDownloadId : AppUpdateDownloader.downloadedId(this);
                if (ready > 0L) continueInstallAfterVerification(ready, "Previously downloaded update verified.");
            } else {
                nativeUpdateFlowActive = false;
            }
            return;
        }
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
        AppLogger.info(this, "file_chooser_result", result == null ? "cancelled" : "selected=" + result.length);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            AppLogger.info(this, "notification_permission", granted ? "granted" : "denied | " + NotificationHelper.status(this));
            if (granted) {
                NotificationHelper.post(this,
                        "Harley's Clan Forum",
                        "Heads-up notifications are enabled. This is a test alert.",
                        ForumUrlRouter.home(activeHost == null ? ForumConfig.PRIMARY_HOST : activeHost));
            }
            if (!prefs.getBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, false)) {
                requestUpdateInstallPermissionIfNeeded();
            }
        }
    }

    private final class AppMessageBridge {
        @JavascriptInterface
        public void notify(String title, String body, String url) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) {
                    AppLogger.warn(MainActivity.this, "bridge_notification_blocked", "untrusted-main-frame");
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastBridgeNotificationAt < 2500L) return;
                lastBridgeNotificationAt = now;
                NotificationHelper.post(MainActivity.this, title, body, url);
            });
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        @JavascriptInterface
        public boolean notificationsEnabled() {
            return NotificationHelper.canPost(MainActivity.this);
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                }
            });
        }

        @JavascriptInterface
        public void updateIdentity(String json, String host) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                handleIdentityUpdate(json, host);
            });
        }

        @JavascriptInterface
        public void updateSecuritySummary(String json, String host) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                handleSecuritySummaryUpdate(json, host);
            });
        }

        @JavascriptInterface
        public void updateSession(String userId, int newNotificationCount, String host) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                recordNotificationCount(userId, newNotificationCount, host);
            });
        }

        @JavascriptInterface
        public void openMedia(String url, String kind) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                try {
                    Uri uri = Uri.parse(url == null ? "" : url.trim());
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return;
                    Intent viewer = new Intent(MainActivity.this, MediaViewerActivity.class);
                    viewer.putExtra(MediaViewerActivity.EXTRA_URL, uri.toString());
                    viewer.putExtra(MediaViewerActivity.EXTRA_KIND, kind == null ? "image" : kind);
                    startActivity(viewer);
                } catch (Throwable t) {
                    AppLogger.warn(MainActivity.this, "media_viewer", t.getClass().getSimpleName());
                }
            });
        }

        @JavascriptInterface
        public void updateAccent(String rawColor) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                int color = parseAccent(rawColor);
                if (color == 0) return;
                prefs.edit().putString(AppPrefs.NATIVE_ACCENT, rawColor.trim()).apply();
                applyAccentColor(color);
            });
        }

        @JavascriptInterface
        public void routeChanged(String url) {
            runOnUiThread(() -> {
                if (!isTrustedForumPage()) return;
                handleSpaRouteChange(url);
            });
        }
    }

    private final class HcfChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            if (ForumUrlRouter.isForumUrl(Uri.parse(url == null ? "" : url))
                    && isUnsupportedPwaPushMessage(message)) {
                // WebView does not implement the browser PWA Push API. The app has its own
                // native Android notification path, so consume the website warning and
                // route the user's request into the native permission/settings flow.
                result.confirm();
                runOnUiThread(MainActivity.this::handleNativePushRequest);
                return true;
            }
            return super.onJsAlert(view, url, message, result);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            pageProgress.setProgress(newProgress);
            pageProgress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("*/*");
            String[] accepts = fileChooserParams == null ? null : fileChooserParams.getAcceptTypes();
            if (accepts != null) {
                java.util.ArrayList<String> clean = new java.util.ArrayList<>();
                for (String type : accepts) {
                    if (type != null && !type.trim().isEmpty() && type.contains("/")) clean.add(type.trim());
                }
                if (clean.size() == 1) picker.setType(clean.get(0));
                else if (clean.size() > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, clean.toArray(new String[0]));
            }
            boolean multiple = fileChooserParams != null && fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
            picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
            Intent chooser = Intent.createChooser(picker, multiple ? "Choose files to upload" : "Choose photo, video, or file");
            try {
                startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                AppLogger.info(MainActivity.this, "file_chooser_open", multiple ? "multi-picker" : "content-picker");
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                AppLogger.error(MainActivity.this, "file_chooser_failed", e.getClass().getSimpleName());
                Toast.makeText(MainActivity.this, "No photo/file picker is available.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private final class HcfWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isNativeInstallRoute(uri)) {
                startNativeUpdateFlow();
                return true;
            }
            if (ForumUrlRouter.isForumUrl(uri)) {
                String targetHost = uri.getHost();
                if (targetHost != null && (activeHost == null || !activeHost.equalsIgnoreCase(targetHost))) {
                    activeHost = targetHost;
                    prefs.edit().putString(AppPrefs.ACTIVE_HOST, activeHost).apply();
                    updateHostChrome(activeHost);
                    String target = ForumUrlRouter.equivalentOnHost(uri, activeHost);
                    view.loadUrl(target);
                    AppLogger.info(MainActivity.this, "forum_host_link", AppLogger.safeUrl(target));
                    return true;
                }
                if ("http".equalsIgnoreCase(uri.getScheme())) {
                    view.loadUrl(ForumUrlRouter.equivalentOnHost(uri, targetHost));
                    return true;
                }
                return false;
            }

            if ("hcf-app".equalsIgnoreCase(uri.getScheme()) && "settings".equalsIgnoreCase(uri.getHost())) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }

            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                    || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                openExternal(uri);
                return true;
            }
            LinkSafety.Result blocked = LinkSafety.classify(uri);
            showBlockedLink(blocked);
            AppLogger.warn(MainActivity.this, "unknown_scheme_blocked", scheme == null ? "null" : scheme);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Uri uri = Uri.parse(url);
            if (isNativeInstallRoute(uri)) {
                try { view.stopLoading(); } catch (Throwable ignored) {}
                startNativeUpdateFlow();
                return;
            }
            updateUrlChrome(url);
            if (ForumUrlRouter.isForumUrl(uri)) {
                lastRecoverableUrl = url;
                prefs.edit().putString(AppPrefs.LAST_RECOVERABLE_URL, url).apply();
                statusSubtitle.setText(uri.getHost());
                updateHostChrome(uri.getHost());
                view.postDelayed(MainActivity.this::installForumBridge, 350L);
                view.postDelayed(MainActivity.this::installForumBridge, 1200L);
            }
            TelemetryService.noteRoute(MainActivity.this, url);
            AppLogger.info(MainActivity.this, "page_started", AppLogger.safeUrl(url));
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            updateUrlChrome(url);
            installForumBridge();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (ForumUrlRouter.isForumUrl(uri)) {
                lastRecoverableUrl = url;
                prefs.edit().putString(AppPrefs.LAST_RECOVERABLE_URL, url).apply();
            }
            updateUrlChrome(url);
            TelemetryService.noteRoute(MainActivity.this, url);
            AppLogger.info(MainActivity.this, "page_finished", AppLogger.safeUrl(url));
            if (ForumUrlRouter.isForumUrl(uri) && activeHost.equalsIgnoreCase(uri.getHost())) {
                hideStatus();
                if (rendererRecoveryPending) {
                    rendererRecoveryPending = false;
                    showTransientBanner("Forum viewer restarted • page restored");
                }
                installForumBridge();
                if (liveUpdater != null) liveUpdater.reset();
                finishLiveRefresh(view);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            Uri failed = request.getUrl();
            String description = error.getDescription() == null ? "" : error.getDescription().toString();
            ErrorSystem.AppError appError = ErrorSystem.fromWebView(error.getErrorCode(), description, !isNetworkAvailable());
            AppLogger.warn(MainActivity.this, "web_error", appError.code + " | " + appError.technical + " | " + AppLogger.safeUrl(failed.toString()));
            TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_connection_error", appError.code + " | " + appError.technical);
            if (ForumConfig.PRIMARY_HOST.equalsIgnoreCase(failed.getHost())) {
                failOverFromPrimary(failed, appError);
            } else {
                showUnavailable(appError, failed);
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (!request.isForMainFrame()) return;
            int status = errorResponse.getStatusCode();
            AppLogger.warn(MainActivity.this, "http_error", status + " | " + AppLogger.safeUrl(request.getUrl().toString()));
            if (status >= 400) TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_http_error", "HTTP " + status);
            if (status < 500 || status > 599) return;

            Uri failed = request.getUrl();
            ErrorSystem.AppError appError = ErrorSystem.fromHttp(status, failed.getHost());
            if (ForumConfig.PRIMARY_HOST.equalsIgnoreCase(failed.getHost())) {
                failOverFromPrimary(failed, appError);
            } else {
                showUnavailable(appError, failed);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Uri failed = Uri.parse(error.getUrl());
            AppLogger.error(MainActivity.this, "ssl_error", AppLogger.safeUrl(error.getUrl()));
            TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_ssl_error", "SSL connection blocked");
            ErrorSystem.AppError appError = ErrorSystem.ssl("SSL validation failed for " + (failed.getHost() == null ? "forum host" : failed.getHost()));
            if (ForumConfig.PRIMARY_HOST.equalsIgnoreCase(failed.getHost())) {
                failOverFromPrimary(failed, appError);
            } else {
                showUnavailable(appError, failed);
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            boolean crashed = detail != null && detail.didCrash();
            ErrorSystem.AppError appError = ErrorSystem.renderer(crashed);
            String recover = lastRecoverableUrl;
            if (recover == null || recover.trim().isEmpty()) {
                recover = prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, "");
            }
            if (recover == null || recover.trim().isEmpty()) {
                recover = ForumUrlRouter.home(activeHost == null ? chooseInitialHost() : activeHost);
            }
            AppLogger.error(MainActivity.this, "webview_renderer_gone", appError.code + " | " + appError.technical + " | " + AppLogger.safeUrl(recover));
            TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_renderer_gone", appError.code + " | " + appError.technical);
            return recoverWebViewRenderer(view, recover, appError);
        }
    }
}
