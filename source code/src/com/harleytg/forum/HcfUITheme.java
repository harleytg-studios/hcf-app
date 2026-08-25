package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URL;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

public final class HcfUITheme {
    private HcfUITheme() {}

    /**
     * Native startup gate for Harley's Clan Forum.
     *
     * Startup order:
     * Welcome -> App Setup -> access check -> system checks -> header/URL handoff -> forum WebView.
     */
    public static final class StartupActivity extends ThemedActivity {
        private static final int REQUEST_WELCOME = 4101;
        private static final int REQUEST_SETUP = 4102;

        private static final long QUICK_PATH_WINDOW_MS = 6L * 60L * 60L * 1000L;
        private static final long LOADER_FIRST_FRAME_HOLD_MS = 420L;
        private static final long STAGE_MIN_DWELL_MS = 110L;
        private static final long FULL_MIN_VISIBLE_MS = 1400L;
        private static final long FULL_EXTRA_WAIT_CAP_MS = 450L;
        private static final long HEADER_FADE_MS = 200L;
        private static final long URL_FADE_MS = 180L;
        private static final long LOADER_FADE_MS = 220L;
        private static final long WEBVIEW_HANDOFF_DELAY_MS = 90L;

        private static final String PREF_STARTUP_LAST_GOOD_AT = "startup_last_good_at";
        private static final String PREF_STARTUP_LAST_GOOD_HOST = "startup_last_good_host";
        private static final String PREF_STARTUP_LOADER_VERBOSE = "startup_loader_verbose";

        private static final int W_ACCESS = 110;
        private static final int W_PREFS = 55;
        private static final int W_WEBVIEW = 90;
        private static final int W_COOKIES = 55;
        private static final int W_IDENTITY = 90;
        private static final int W_DOMAINS = 75;
        private static final int W_INTEGRATION = 80;
        private static final int W_NOTIFICATIONS = 90;
        private static final int W_UPDATES = 75;
        private static final int W_RECOVERY = 60;
        private static final int W_STORAGE = 70;
        private static final int W_HOSTS = 100;
        private static final int W_READY = 50;
        private static final int FULL_WEIGHT_TOTAL = 1000;
        private static final int QUICK_WEIGHT_TOTAL = FULL_WEIGHT_TOTAL - W_DOMAINS - W_UPDATES - W_RECOVERY;
        private static final int FULL_STAGE_COUNT = 13;
        private static final int QUICK_STAGE_COUNT = 10;

        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final java.util.ArrayList<String> completedSteps = new java.util.ArrayList<String>();

        private SharedPreferences prefs;
        private View topAppBar;
        private View urlBar;
        private View loaderBackdrop;
        private View loaderOverlay;
        private LinearLayout loaderPanel;
        private ImageView loaderLogo;
        private TextView loaderTitle;
        private TextView loaderStep;
        private TextView loaderStatus;
        private TextView loaderDetail;
        private TextView loaderPercent;
        private TextView completedLabel;
        private TextView completedTicker;
        private ProgressBar loaderProgress;
        private Button retryButton;
        private WebView startupWebView;
        private android.animation.ValueAnimator progressAnimator;
        private android.animation.ValueAnimator logoPulseAnimator;

        private boolean gateInProgress;
        private boolean loaderStarted;
        private boolean handoffStarted;
        private boolean handoffPending;
        private boolean hardFailure;
        private boolean destroyed;
        private boolean resumed;
        private boolean quickPath;
        private boolean verboseLoader = true;
        private int runGeneration;
        private int completedWeight;
        private int totalWeight;
        private int totalStages;
        private long loaderVisibleAt;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            ThemeManager.apply(this);
            prefs = getSharedPreferences(AppPrefs.FILE, 0);

            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);

            try {
                setContentView(R.layout.activity_main);
                prepareNativeChrome();
                installStartupOverlay();
            } catch (Throwable error) {
                AppLogger.crash(this, error);
                showEmergencyStartupFailure(error);
                return;
            }

            if (state == null && (SetupCenter.shouldShowWelcome(this) || SetupCenter.shouldAutoLaunch(this))) {
                getWindow().getDecorView().setAlpha(0.0f);
            }

            AppLogger.info(this, "startup_gate", "created | " + BuildInfo.VERSION_BUILD_LINE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            resumed = true;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (handoffPending) {
                        handoffPending = false;
                        beginChromeHandoff();
                    } else {
                        advanceStartupGate();
                    }
                }
            });
        }

        @Override
        protected void onPause() {
            resumed = false;
            HcfSessionPersistence.flushCookies();
            super.onPause();
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_WELCOME || requestCode == REQUEST_SETUP) {
                gateInProgress = false;
            }
        }

        @Override
        protected void onDestroy() {
            destroyed = true;
            resumed = false;
            runGeneration++;
            mainHandler.removeCallbacksAndMessages(null);
            stopLoaderAnimations();
            HcfSessionPersistence.flushCookies();
            destroyStartupWebView();
            super.onDestroy();
        }

        private void advanceStartupGate() {
            if (!resumed || destroyed || isFinishing() || isDestroyed()
                    || gateInProgress || loaderStarted || handoffStarted || hardFailure) {
                return;
            }

            if (SetupCenter.shouldShowWelcome(this)) {
                gateInProgress = true;
                getWindow().getDecorView().setAlpha(0.0f);
                Intent welcome = new Intent(this, HcfMainActivities.WelcomeActivity.class);
                welcome.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
                startActivityForResult(welcome, REQUEST_WELCOME);
                AppLogger.info(this, "startup_gate", "welcome");
                return;
            }

            if (SetupCenter.shouldAutoLaunch(this)) {
                gateInProgress = true;
                SetupCenter.markSeen(this);
                getWindow().getDecorView().setAlpha(0.0f);
                Intent setup = new Intent(this, HcfMainActivities.SetupActivity.class);
                setup.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
                startActivityForResult(setup, REQUEST_SETUP);
                AppLogger.info(this, "startup_gate", "setup");
                return;
            }

            getWindow().getDecorView().setAlpha(1.0f);
            startSystemLoader();
        }

        private void prepareNativeChrome() {
            topAppBar = findViewById(R.id.topAppBar);
            urlBar = findViewById(R.id.urlBar);
            startupWebView = findViewById(R.id.webView);

            applyStartupChromeDensity();

            if (topAppBar != null) {
                topAppBar.animate().cancel();
                topAppBar.setAlpha(0.0f);
                topAppBar.setTranslationY(-dp(8));
                topAppBar.setVisibility(View.INVISIBLE);
            }
            if (urlBar != null) {
                urlBar.animate().cancel();
                urlBar.setAlpha(0.0f);
                urlBar.setTranslationY(-dp(6));
                urlBar.setVisibility(View.INVISIBLE);
            }

            hideView(R.id.welcomeBanner);
            hideView(R.id.bottomNav);
            hideView(R.id.drawerPanel);
            hideView(R.id.drawerScrim);
            hideView(R.id.pageProgress);
            hideView(R.id.statusOverlay);

            if (startupWebView != null) {
                startupWebView.setVisibility(View.GONE);
                startupWebView.setAlpha(0.0f);
            }

            View contentFrame = findViewById(R.id.contentFrame);
            if (contentFrame != null) {
                contentFrame.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            }

            updateChromeHostDisplay();

            TextView subtitle = findViewById(R.id.appHeaderSubtitle);
            if (subtitle != null) subtitle.setText("Native startup • System checks");
        }

        private void applyStartupChromeDensity() {
            setStartupHeight(topAppBar, R.dimen.compact_app_header_height);
            setStartupSquare(findViewById(R.id.drawerButton), R.dimen.compact_app_header_button);
            setStartupSquare(findViewById(R.id.appHeaderLogo), R.dimen.compact_app_header_logo);
            setStartupSquare(findViewById(R.id.headerNotificationsButton), R.dimen.compact_app_header_button);

            TextView title = findViewById(R.id.appHeaderTitle);
            if (title != null) {
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.compact_app_header_title_text));
            }

            setStartupHeight(urlBar, R.dimen.compact_url_bar_height);
            setStartupHeight(findViewById(R.id.urlBarInner), R.dimen.compact_url_bar_inner_height);
            setStartupSquare(findViewById(R.id.urlBackButton), R.dimen.compact_url_reload_button);
            setStartupSquare(findViewById(R.id.reloadButton), R.dimen.compact_url_reload_button);
            setStartupSquare(findViewById(R.id.copyUrlButton), R.dimen.compact_url_copy_button);
            setStartupSquare(findViewById(R.id.urlHomeButton), R.dimen.compact_url_reload_button);
            styleStartupUrlNav(R.id.urlBackButton, R.drawable.fa_arrow_left);
            styleStartupUrlNav(R.id.reloadButton, R.drawable.fa_rotate_right);
            styleStartupUrlNav(R.id.copyUrlButton, R.drawable.fa_copy);
            styleStartupUrlNav(R.id.urlHomeButton, R.drawable.fa_house);

            if (topAppBar != null) topAppBar.setPadding(dp(6), 0, dp(6), 0);
            if (urlBar != null) urlBar.setPadding(dp(6), dp(2), dp(6), dp(2));

            View secureLabel = findViewById(R.id.secureForumLabel);
            if (secureLabel != null) secureLabel.setVisibility(View.GONE);
        }

        private void styleStartupUrlNav(int viewId, int iconRes) {
            View view = findViewById(viewId);
            if (!(view instanceof ImageButton)) return;
            ImageButton button = (ImageButton) view;
            button.setImageResource(iconRes);
            button.setBackgroundResource(R.drawable.nav_button_background);
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setMinimumWidth(0);
            button.setMinimumHeight(0);
            button.setStateListAnimator(null);
            button.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan_bright)));
        }

        private void setStartupHeight(View view, int dimenRes) {
            if (view == null || view.getLayoutParams() == null) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = getResources().getDimensionPixelSize(dimenRes);
            view.setLayoutParams(lp);
            view.requestLayout();
        }

        private void setStartupSquare(View view, int dimenRes) {
            if (view == null || view.getLayoutParams() == null) return;
            int size = getResources().getDimensionPixelSize(dimenRes);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = size;
            lp.height = size;
            view.setLayoutParams(lp);
            view.requestLayout();
        }

        private void installStartupOverlay() {
            FrameLayout root = findViewById(R.id.rootFrame);
            if (root == null) throw new IllegalStateException("rootFrame missing from activity_main");

            loaderBackdrop = new View(this);
            loaderBackdrop.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            root.addView(loaderBackdrop, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout overlay = new FrameLayout(this);
            loaderOverlay = overlay;
            overlay.setClickable(true);
            overlay.setFocusable(true);

            loaderPanel = new LinearLayout(this);
            loaderPanel.setOrientation(LinearLayout.VERTICAL);
            loaderPanel.setGravity(Gravity.CENTER_HORIZONTAL);
            loaderPanel.setPadding(dp(24), dp(20), dp(24), dp(20));

            loaderLogo = new ImageView(this);
            loaderLogo.setImageResource(R.drawable.htg_app_logo);
            loaderLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            loaderLogo.setContentDescription("Harley's Clan Forum logo");
            loaderPanel.addView(loaderLogo, new LinearLayout.LayoutParams(dp(96), dp(96)));

            TextView brand = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(12);
            loaderPanel.addView(brand, brandLp);

            loaderTitle = text("Starting Harley's Clan Forum", 21, getColor(R.color.hcf_text));
            loaderTitle.setTypeface(null, 1);
            loaderTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(5);
            loaderPanel.addView(loaderTitle, titleLp);

            LinearLayout stepRow = new LinearLayout(this);
            stepRow.setOrientation(LinearLayout.HORIZONTAL);
            stepRow.setGravity(Gravity.CENTER_VERTICAL);
            loaderStep = text("Step 0 of " + FULL_STAGE_COUNT, 10, getColor(R.color.hcf_meta));
            loaderStep.setTypeface(null, 1);
            loaderPercent = text("0%", 10, getColor(R.color.hcf_cyan_bright));
            loaderPercent.setTypeface(null, 1);
            loaderPercent.setGravity(Gravity.END);
            stepRow.addView(loaderStep, new LinearLayout.LayoutParams(0, -2, 1.0f));
            stepRow.addView(loaderPercent, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-1, -2);
            stepLp.topMargin = dp(16);
            loaderPanel.addView(stepRow, stepLp);

            loaderStatus = text("Waiting for startup gate…", 13, getColor(R.color.hcf_cyan_bright));
            loaderStatus.setTypeface(null, 1);
            loaderStatus.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
            statusLp.topMargin = dp(8);
            loaderPanel.addView(loaderStatus, statusLp);

            loaderDetail = text("Welcome and App Setup run before native system initialization.", 11,
                    getColor(R.color.hcf_muted));
            loaderDetail.setGravity(Gravity.CENTER);
            loaderDetail.setLineSpacing(0.0f, 1.12f);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(5);
            loaderPanel.addView(loaderDetail, detailLp);

            loaderProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            loaderProgress.setIndeterminate(false);
            loaderProgress.setMax(1000);
            loaderProgress.setProgress(0);
            loaderProgress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
            progressLp.topMargin = dp(16);
            loaderPanel.addView(loaderProgress, progressLp);

            completedLabel = text("COMPLETED CHECKS", 9, getColor(R.color.hcf_meta));
            completedLabel.setTypeface(null, 1);
            LinearLayout.LayoutParams completedLabelLp = new LinearLayout.LayoutParams(-1, -2);
            completedLabelLp.topMargin = dp(12);
            loaderPanel.addView(completedLabel, completedLabelLp);

            completedTicker = text("Waiting for completed checks…", 10, getColor(R.color.hcf_hint));
            completedTicker.setLineSpacing(dp(1), 1.05f);
            LinearLayout.LayoutParams tickerLp = new LinearLayout.LayoutParams(-1, -2);
            tickerLp.topMargin = dp(4);
            loaderPanel.addView(completedTicker, tickerLp);

            TextView build = text(BuildInfo.VERSION_BUILD_LINE, 9, getColor(R.color.hcf_hint));
            build.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams buildLp = new LinearLayout.LayoutParams(-1, -2);
            buildLp.topMargin = dp(12);
            loaderPanel.addView(build, buildLp);

            retryButton = new Button(this);
            UiButtons.normalizeText(retryButton);
            retryButton.setText("Retry Startup");
            retryButton.setTextColor(getColor(R.color.hcf_on_accent));
            retryButton.setBackgroundResource(R.drawable.error_primary_button_background);
            retryButton.setGravity(Gravity.CENTER);
            retryButton.setVisibility(View.GONE);
            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    resetLoaderStateForRetry();
                    startSystemLoader();
                }
            });
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-1, dp(48));
            retryLp.topMargin = dp(14);
            loaderPanel.addView(retryButton, retryLp);

            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            panelLp.leftMargin = dp(18);
            panelLp.rightMargin = dp(18);
            overlay.addView(loaderPanel, panelLp);

            root.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private void startSystemLoader() {
            if (!resumed || loaderStarted || handoffStarted || destroyed || hardFailure) return;

            quickPath = shouldUseQuickPath();
            totalWeight = quickPath ? QUICK_WEIGHT_TOTAL : FULL_WEIGHT_TOTAL;
            totalStages = quickPath ? QUICK_STAGE_COUNT : FULL_STAGE_COUNT;
            completedWeight = 0;
            verboseLoader = prefs == null || prefs.getBoolean(PREF_STARTUP_LOADER_VERBOSE, true);
            loaderStarted = true;
            handoffPending = false;
            loaderVisibleAt = android.os.SystemClock.elapsedRealtime();
            final int token = ++runGeneration;

            resetLoaderVisuals();
            applyLoaderVerbosity();
            animateLogoEntrance();

            if (loaderTitle != null) loaderTitle.setText(quickPath ? "Welcome back" : "Starting Harley's Clan Forum");
            if (loaderStep != null) loaderStep.setText("Step 0 of " + totalStages);
            if (loaderStatus != null) loaderStatus.setText(quickPath ? "Quick system check" : "Starting native systems");
            if (loaderDetail != null) {
                loaderDetail.setText(quickPath
                        ? "Recent successful startup found. Running safety-critical checks and connectivity only."
                        : "Preparing the complete native system gate before the forum opens.");
            }

            AppLogger.info(this, "startup_loader",
                    "visible_zero | mode=" + (quickPath ? "quick" : "full") + " | stages=" + totalStages);

            mainHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!isRunValid(token)) return;
                    if (!resumed) {
                        loaderStarted = false;
                        return;
                    }
                    startLogoPulse();
                    Thread worker = new Thread(new Runnable() {
                        @Override public void run() {
                            runSystemChecks(token);
                        }
                    }, "hcf-startup-checks");
                    worker.setPriority(Thread.NORM_PRIORITY);
                    worker.start();
                }
            }, LOADER_FIRST_FRAME_HOLD_MS);
        }

        private void runSystemChecks(int token) {
            int step = 0;
            try {
                long started = beginStage(token, ++step, "Checking access status",
                        "Checking account and network against the HCF ban list.");
                try {
                    HcfBanSystem.CheckResult access = HcfBanSystem.checkCurrentAccess(this);
                    if (access != null && access.banned) {
                        AppLogger.warn(this, "startup_ban_gate",
                                "blocked | scope=" + access.scope + " | id=" + access.banId);
                        openAccessRestricted(token, access);
                        return;
                    }
                    if (!completeStage(token, W_ACCESS, started,
                            "No active HCF access restriction was found.", "Access • allowed")) return;
                } catch (Throwable accessError) {
                    AppLogger.warn(this, "startup_ban_gate",
                            "fail-open | " + accessError.getClass().getSimpleName());
                    if (!completeStage(token, W_ACCESS, started,
                            "Ban list unavailable; startup is continuing fail-open.", "Access • fail-open")) return;
                }

                started = beginStage(token, ++step, "Loading app preferences",
                        "Validating saved native settings and the active theme profile.");
                int preferenceCount = prefs.getAll().size();
                String themeLabel = ThemeManager.label(this);
                if (!completeStage(token, W_PREFS, started,
                        "Preferences are readable • " + preferenceCount + " entries • theme: " + themeLabel + ".",
                        "Preferences • " + preferenceCount + " • " + themeLabel)) return;

                started = beginStage(token, ++step, "Checking WebView engine",
                        "Verifying Android System WebView provider and package version.");
                PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
                if (webViewPackage == null || TextUtils.isEmpty(webViewPackage.packageName)) {
                    failStartup(token, "Android System WebView is unavailable",
                            "HCF cannot open the forum until Android has a working WebView provider.");
                    return;
                }
                String webViewVersion = TextUtils.isEmpty(webViewPackage.versionName)
                        ? "unknown version" : webViewPackage.versionName;
                if (!completeStage(token, W_WEBVIEW, started,
                        "Provider: " + webViewPackage.packageName + " • " + webViewVersion + ".",
                        "WebView • " + shortText(webViewVersion, 34))) return;

                started = beginStage(token, ++step, "Flushing cookie store",
                        "Persisting the existing first-party forum session without creating a temporary WebView.");
                try {
                    HcfSessionPersistence.flushCookies();
                    if (!completeStage(token, W_COOKIES, started,
                            "Android WebView cookie storage was flushed to persistent storage.",
                            "Cookies • persisted")) return;
                } catch (Throwable cookieError) {
                    AppLogger.warn(this, "startup_cookies", cookieError.getClass().getSimpleName());
                    if (!completeStage(token, W_COOKIES, started,
                            "Cookie flush could not be confirmed; the lifecycle persistence hook will retry.",
                            "Cookies • deferred")) return;
                }

                started = beginStage(token, ++step, "Restoring forum identity",
                        "Loading the saved forum identity and session marker.");
                ForumIdentity.Snapshot identity = ForumIdentity.load(this);
                String sessionUserId = prefs.getString("session_user_id", "");
                String identityDetail;
                String identitySummary;
                if (identity != null && identity.loggedIn) {
                    identityDetail = "Restored " + identity.usernameDisplay()
                            + (TextUtils.isEmpty(sessionUserId) ? " • session marker will refresh in the forum." : " • signed-in session marker present.");
                    identitySummary = "Identity • " + shortText(identity.usernameDisplay(), 32);
                } else {
                    identityDetail = "Guest forum identity ready; sign-in can continue inside the forum.";
                    identitySummary = "Identity • guest";
                }
                if (!completeStage(token, W_IDENTITY, started, identityDetail, identitySummary)) return;

                if (!quickPath) {
                    started = beginStage(token, ++step, "Refreshing domain registry",
                            "Loading the trusted primary and backup forum-domain configuration.");
                    try {
                        RemoteDomainConfig.initialize(this);
                        if (hasInternetNetwork()) RemoteDomainConfig.refresh(this);
                        if (!completeStage(token, W_DOMAINS, started,
                                "Trusted domains ready • primary " + ForumConfig.PRIMARY_HOST
                                        + " • backup " + ForumConfig.BACKUP_HOST + ".",
                                "Domains • registry ready")) return;
                    } catch (Throwable domainError) {
                        AppLogger.warn(this, "startup_domains", domainError.getClass().getSimpleName());
                        if (!completeStage(token, W_DOMAINS, started,
                                "Remote domain refresh failed; cached/built-in trusted domains remain active.",
                                "Domains • cached fallback")) return;
                    }
                }

                started = beginStage(token, ++step, "Checking Android integration",
                        "Verifying forum links and secure APK installer permission state.");
                SetupCenter.ForumLinksState links = SetupCenter.forumLinksState(this);
                boolean canInstallUpdates = AppSecurity.canInstallUpdates(this);
                String integrationDetail = (links != null && links.ready
                        ? "Forum links verified" : "Forum links are managed by Android")
                        + " • installer " + (canInstallUpdates ? "ready" : "permission not granted") + ".";
                if (!completeStage(token, W_INTEGRATION, started, integrationDetail,
                        "Android • links " + (links != null && links.ready ? "ready" : "managed"))) return;

                started = beginStage(token, ++step, "Starting notification systems",
                        "Checking HCF notification channels, foreground sync and the safety-net schedule.");
                try {
                    NotificationHelper.refreshChannels(this);
                    NotificationSyncScheduler.apply(this);
                    if (!completeStage(token, W_NOTIFICATIONS, started,
                            "Notification channels and background alert schedule are ready.",
                            "Notifications • ready")) return;
                } catch (Throwable notificationError) {
                    AppLogger.warn(this, "startup_notifications", notificationError.getClass().getSimpleName());
                    if (!completeStage(token, W_NOTIFICATIONS, started,
                            "Notification startup was deferred; the main app can recover it after handoff.",
                            "Notifications • deferred")) return;
                }

                if (!quickPath) {
                    started = beginStage(token, ++step, "Checking update system",
                            "Applying the update schedule and cleaning same-version downloaded update state.");
                    try {
                        UpdateScheduler.apply(this);
                        AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(this);
                        if (!completeStage(token, W_UPDATES, started,
                                "Update scheduler and same-version cleanup completed.",
                                "Updates • ready")) return;
                    } catch (Throwable updateError) {
                        AppLogger.warn(this, "startup_updates", updateError.getClass().getSimpleName());
                        if (!completeStage(token, W_UPDATES, started,
                                "Update maintenance was deferred; it will retry after startup.",
                                "Updates • deferred")) return;
                    }

                    started = beginStage(token, ++step, "Checking recovery state",
                            "Inspecting the saved crash-recovery state before forum handoff.");
                    try {
                        boolean pendingCrash = TelemetryService.hasPendingCrash(this);
                        if (!completeStage(token, W_RECOVERY, started,
                                pendingCrash
                                        ? "A saved recovery report is present; the main app can process it safely."
                                        : "No pending crash recovery report was found.",
                                pendingCrash ? "Recovery • report pending" : "Recovery • clean")) return;
                    } catch (Throwable recoveryError) {
                        AppLogger.warn(this, "startup_recovery", recoveryError.getClass().getSimpleName());
                        if (!completeStage(token, W_RECOVERY, started,
                                "Recovery state could not be read; no blocking action is required.",
                                "Recovery • fail-open")) return;
                    }
                }

                started = beginStage(token, ++step, "Checking app storage",
                        "Measuring free space on the app data volume for cache, cookies and update metadata.");
                try {
                    android.os.StatFs stat = new android.os.StatFs(getFilesDir().getAbsolutePath());
                    long freeBytes = stat.getAvailableBytes();
                    String free = formatBytes(freeBytes);
                    String detail = freeBytes < 64L * 1024L * 1024L
                            ? "Low free app-data storage: " + free + ". Startup will continue, but Android may reclaim caches aggressively."
                            : "Free app-data storage: " + free + ".";
                    if (!completeStage(token, W_STORAGE, started, detail,
                            "Storage • " + free + " free")) return;
                } catch (Throwable storageError) {
                    AppLogger.warn(this, "startup_storage", storageError.getClass().getSimpleName());
                    if (!completeStage(token, W_STORAGE, started,
                            "Free-space check was unavailable; startup will continue fail-open.",
                            "Storage • unavailable")) return;
                }

                if (quickPath) {
                    started = beginStage(token, ++step, "Checking connectivity",
                            "Using the recent healthy-host result and checking only Android network connectivity.");
                    boolean network = hasInternetNetwork();
                    String lastGoodHost = prefs.getString(PREF_STARTUP_LAST_GOOD_HOST, "");
                    if (!ForumUrlRouter.isForumHost(lastGoodHost)) lastGoodHost = preferredHost();
                    String detail = network
                            ? "Internet network available • recent good host: " + lastGoodHost + "."
                            : "No active internet network; the forum recovery UI remains available.";
                    if (!completeStage(token, W_HOSTS, started, detail,
                            network ? "Connectivity • online" : "Connectivity • offline")) return;
                } else {
                    started = beginStage(token, ++step, "Probing forum hosts",
                            "Checking primary and backup forum hosts with 2.5-second connection/read timeouts.");
                    boolean network = hasInternetNetwork();
                    if (!network) {
                        AppLogger.warn(this, "startup_network", "no active internet network");
                        if (!completeStage(token, W_HOSTS, started,
                                "No active internet connection; host probes were skipped and recovery UI remains available.",
                                "Hosts • offline")) return;
                    } else {
                        HostProbeResult probes = probeForumHosts();
                        applyHealthyHostSelection(probes);
                        String detail;
                        String summary;
                        if (probes.primaryHealthy && probes.backupHealthy) {
                            detail = "Primary and backup forum hosts responded.";
                            summary = "Hosts • primary + backup ready";
                        } else if (probes.primaryHealthy) {
                            detail = "Primary forum host responded; backup is currently unavailable.";
                            summary = "Hosts • primary ready";
                        } else if (probes.backupHealthy) {
                            detail = "Primary is unavailable; backup forum host is ready and selected if needed.";
                            summary = "Hosts • backup ready";
                        } else {
                            detail = "Neither host answered the startup probe; the forum recovery system will retry after handoff.";
                            summary = "Hosts • recovery mode";
                        }
                        if (!completeStage(token, W_HOSTS, started, detail, summary)) return;
                    }
                }

                started = beginStage(token, ++step, "Systems ready",
                        "Finalizing the native gate and preparing the forum chrome handoff.");
                if (!completeStage(token, W_READY, started,
                        quickPath ? "Quick system check complete. Preparing the forum interface."
                                : "Full native startup gate complete. Preparing the forum interface.",
                        "Systems • ready")) return;

                if (!quickPath) persistSuccessfulFullGate();
                enforceFullMinimumVisibleTime(token);
                if (!isRunValid(token)) return;

                AppLogger.info(this, "startup_loader",
                        "systems_ready | mode=" + (quickPath ? "quick" : "full") + " | host=" + preferredHost());
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        beginChromeHandoff();
                    }
                });
            } catch (Throwable error) {
                AppLogger.error(this, "startup_loader",
                        error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
                failStartup(token, "Startup check failed",
                        error.getClass().getSimpleName()
                                + (error.getMessage() == null ? "" : " • " + error.getMessage()));
            }
        }

        private long beginStage(final int token, final int step, final String status, final String detail) {
            final long started = android.os.SystemClock.elapsedRealtime();
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (!isRunValid(token)) return;
                    if (loaderStep != null) loaderStep.setText("Step " + step + " of " + totalStages);
                    if (loaderStatus != null) loaderStatus.setText(status);
                    if (loaderDetail != null) loaderDetail.setText(detail);
                }
            });
            return started;
        }

        private boolean completeStage(final int token, int weight, long stageStarted,
                                      final String finalDetail, final String summary) {
            if (!isRunValid(token)) return false;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (isRunValid(token) && loaderDetail != null) loaderDetail.setText(finalDetail);
                }
            });
            dwellStage(stageStarted);
            if (!isRunValid(token)) return false;

            completedWeight = Math.min(totalWeight, completedWeight + weight);
            final int target = Math.min(1000,
                    Math.round((completedWeight * 1000.0f) / Math.max(1, totalWeight)));
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (!isRunValid(token)) return;
                    animateProgressTo(target);
                    recordCompletedStep(summary);
                }
            });
            return true;
        }

        private void dwellStage(long stageStarted) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - stageStarted;
            long remaining = STAGE_MIN_DWELL_MS - elapsed;
            if (remaining <= 0L) return;
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void enforceFullMinimumVisibleTime(int token) {
            if (quickPath || !isRunValid(token)) return;
            long elapsed = android.os.SystemClock.elapsedRealtime() - loaderVisibleAt;
            long remaining = FULL_MIN_VISIBLE_MS - elapsed;
            if (remaining <= 0L) return;
            long capped = Math.min(remaining, FULL_EXTRA_WAIT_CAP_MS);
            try {
                Thread.sleep(capped);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private boolean shouldUseQuickPath() {
            Intent launchIntent = getIntent();
            if (launchIntent != null && launchIntent.getData() != null) return false;
            long lastGoodAt = prefs == null ? 0L : prefs.getLong(PREF_STARTUP_LAST_GOOD_AT, 0L);
            if (lastGoodAt <= 0L) return false;
            long age = System.currentTimeMillis() - lastGoodAt;
            return age >= 0L && age <= QUICK_PATH_WINDOW_MS;
        }

        private void persistSuccessfulFullGate() {
            if (prefs == null || quickPath) return;
            String host = preferredHost();
            prefs.edit()
                    .putLong(PREF_STARTUP_LAST_GOOD_AT, System.currentTimeMillis())
                    .putString(PREF_STARTUP_LAST_GOOD_HOST, host)
                    .apply();
            AppLogger.info(this, "startup_gate", "full_gate_good | host=" + host);
        }

        private HostProbeResult probeForumHosts() {
            final String primary = ForumConfig.PRIMARY_HOST;
            final String backup = ForumConfig.BACKUP_HOST;
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.Future<Boolean> primaryFuture = pool.submit(new java.util.concurrent.Callable<Boolean>() {
                @Override public Boolean call() {
                    return Boolean.valueOf(probeHost(primary));
                }
            });
            java.util.concurrent.Future<Boolean> backupFuture = pool.submit(new java.util.concurrent.Callable<Boolean>() {
                @Override public Boolean call() {
                    return Boolean.valueOf(probeHost(backup));
                }
            });

            boolean primaryHealthy = false;
            boolean backupHealthy = false;
            try {
                primaryHealthy = Boolean.TRUE.equals(primaryFuture.get(5500L, java.util.concurrent.TimeUnit.MILLISECONDS));
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_host_probe", primary + " | future " + error.getClass().getSimpleName());
            }
            try {
                backupHealthy = Boolean.TRUE.equals(backupFuture.get(5500L, java.util.concurrent.TimeUnit.MILLISECONDS));
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_host_probe", backup + " | future " + error.getClass().getSimpleName());
            } finally {
                pool.shutdownNow();
            }
            return new HostProbeResult(primary, backup, primaryHealthy, backupHealthy);
        }

        private void applyHealthyHostSelection(HostProbeResult probes) {
            if (prefs == null || probes == null) return;
            String selected = prefs.getString(AppPrefs.ACTIVE_HOST, "");
            boolean selectedPrimary = probes.primary.equalsIgnoreCase(selected);
            boolean selectedBackup = probes.backup.equalsIgnoreCase(selected);

            if (probes.primaryHealthy && !probes.backupHealthy) {
                selected = probes.primary;
            } else if (!probes.primaryHealthy && probes.backupHealthy) {
                selected = probes.backup;
            } else if (probes.primaryHealthy && probes.backupHealthy && !selectedPrimary && !selectedBackup) {
                selected = probes.primary;
            }

            if (!TextUtils.isEmpty(selected)) {
                String current = prefs.getString(AppPrefs.ACTIVE_HOST, "");
                if (!selected.equalsIgnoreCase(current)) {
                    prefs.edit().putString(AppPrefs.ACTIVE_HOST, selected).apply();
                    AppLogger.info(this, "startup_host_select", "active_host=" + selected);
                }
            }
        }

        private void openAccessRestricted(final int token, final HcfBanSystem.CheckResult result) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (!isRunValid(token) || isFinishing() || isDestroyed()) return;
                    handoffStarted = true;
                    loaderStarted = false;
                    stopLoaderAnimations();
                    Intent intent = new Intent(StartupActivity.this, HcfBanSystem.BanActivity.class);
                    intent.putExtra("ban_id", result.banId);
                    intent.putExtra("reason", result.reason);
                    intent.putExtra("expires_at", result.expiresAt);
                    intent.putExtra("scope", result.scope);
                    intent.putExtra("username", result.username);
                    intent.putExtra("masked_ip", result.maskedIp);
                    intent.putExtra("appeal_allowed", result.appealAllowed);
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }
            });
        }

        private void beginChromeHandoff() {
            if (destroyed || isFinishing() || isDestroyed() || handoffStarted) return;
            if (!resumed) {
                handoffPending = true;
                return;
            }
            handoffStarted = true;
            loaderStarted = false;
            stopLogoPulse();
            updateChromeHostDisplay();

            TextView subtitle = findViewById(R.id.appHeaderSubtitle);
            if (subtitle != null) {
                subtitle.setText("Live forum • "
                        + (ForumConfig.BACKUP_HOST.equalsIgnoreCase(preferredHost()) ? "Backup" : "Primary"));
            }

            dockBackdropBelowChrome();
            animateHeaderIn();
        }

        private void dockBackdropBelowChrome() {
            if (loaderBackdrop == null) return;
            try {
                View content = findViewById(R.id.contentFrame);
                if (!(content instanceof ViewGroup)) return;

                ViewGroup currentParent = loaderBackdrop.getParent() instanceof ViewGroup
                        ? (ViewGroup) loaderBackdrop.getParent() : null;
                if (currentParent != null) currentParent.removeView(loaderBackdrop);

                loaderBackdrop.animate().cancel();
                loaderBackdrop.setAlpha(1.0f);
                loaderBackdrop.setVisibility(View.VISIBLE);

                ((ViewGroup) content).addView(loaderBackdrop, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_chrome", "dock backdrop: " + error.getClass().getSimpleName());
            }
        }

        private void animateHeaderIn() {
            if (topAppBar == null) {
                animateUrlBarIn();
                return;
            }
            topAppBar.animate().cancel();
            topAppBar.setAlpha(0.0f);
            topAppBar.setTranslationY(-dp(8));
            topAppBar.setVisibility(View.VISIBLE);
            topAppBar.animate()
                    .alpha(1.0f)
                    .translationY(0.0f)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .setDuration(HEADER_FADE_MS)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            animateUrlBarIn();
                        }
                    })
                    .start();
        }

        private void animateUrlBarIn() {
            if (urlBar == null) {
                fadeLoaderOut();
                return;
            }
            urlBar.animate().cancel();
            urlBar.setAlpha(0.0f);
            urlBar.setTranslationY(-dp(6));
            urlBar.setVisibility(View.VISIBLE);
            urlBar.animate()
                    .alpha(1.0f)
                    .translationY(0.0f)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .setDuration(URL_FADE_MS)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            fadeLoaderOut();
                        }
                    })
                    .start();
        }

        private void fadeLoaderOut() {
            stopLogoPulse();

            if (loaderPanel != null) {
                loaderPanel.animate().cancel();
                loaderPanel.animate()
                        .alpha(0.0f)
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                        .setDuration(LOADER_FADE_MS)
                        .start();
            }

            if (loaderBackdrop != null) {
                loaderBackdrop.animate().cancel();
                loaderBackdrop.animate()
                        .alpha(0.0f)
                        .setDuration(LOADER_FADE_MS)
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                if (loaderOverlay != null) loaderOverlay.setVisibility(View.GONE);
                                scheduleForumHandoff();
                            }
                        })
                        .start();
            } else if (loaderPanel != null) {
                loaderPanel.animate()
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                if (loaderOverlay != null) loaderOverlay.setVisibility(View.GONE);
                                scheduleForumHandoff();
                            }
                        })
                        .start();
            } else {
                scheduleForumHandoff();
            }
        }

        private void scheduleForumHandoff() {
            mainHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    launchForumMainActivity();
                }
            }, WEBVIEW_HANDOFF_DELAY_MS);
        }

        private void launchForumMainActivity() {
            if (!resumed || destroyed || isFinishing() || isDestroyed()) return;

            destroyStartupWebView();

            Intent target = new Intent(this, StartupMainActivity.class);
            target.putExtra(StartupMainActivity.EXTRA_STARTUP_HANDOFF, true);

            Intent source = getIntent();
            if (source != null) {
                Uri data = source.getData();
                if (data != null) target.setData(data);
                if (source.getAction() != null && !Intent.ACTION_MAIN.equals(source.getAction())) {
                    target.setAction(source.getAction());
                }
            }

            AppLogger.info(this, "startup_handoff",
                    "launch_main_after_" + WEBVIEW_HANDOFF_DELAY_MS + "ms | mode=" + (quickPath ? "quick" : "full"));
            startActivity(target);
            overridePendingTransition(0, 0);
            finish();
        }

        private void animateProgressTo(int target) {
            if (loaderProgress == null) return;
            int bounded = Math.max(0, Math.min(1000, target));
            int start = loaderProgress.getProgress();
            if (progressAnimator != null) progressAnimator.cancel();
            if (start == bounded) {
                updatePercentLabel(bounded);
                return;
            }
            progressAnimator = android.animation.ValueAnimator.ofInt(start, bounded);
            int distance = Math.abs(bounded - start);
            progressAnimator.setDuration(Math.max(120L, Math.min(240L, 100L + distance / 3L)));
            progressAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            progressAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                    if (loaderProgress == null) return;
                    int value = ((Integer) animation.getAnimatedValue()).intValue();
                    loaderProgress.setProgress(value);
                    updatePercentLabel(value);
                }
            });
            progressAnimator.start();
        }

        private void updatePercentLabel(int progress) {
            if (loaderPercent == null) return;
            int percent = Math.max(0, Math.min(100, Math.round(progress / 10.0f)));
            loaderPercent.setText(percent + "%");
        }

        private void recordCompletedStep(String summary) {
            if (completedTicker == null) return;
            String value = shortText(summary, 62);
            completedSteps.add(value);
            while (completedSteps.size() > 4) completedSteps.remove(0);
            StringBuilder text = new StringBuilder();
            for (String item : completedSteps) {
                if (text.length() > 0) text.append('\n');
                text.append("✓ ").append(item);
            }
            completedTicker.setText(text.toString());
        }

        private void animateLogoEntrance() {
            if (loaderLogo == null) return;
            loaderLogo.animate().cancel();
            loaderLogo.setAlpha(0.0f);
            loaderLogo.setScaleX(0.78f);
            loaderLogo.setScaleY(0.78f);
            loaderLogo.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(400L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.75f))
                    .start();
        }

        private void startLogoPulse() {
            stopLogoPulse();
            if (loaderLogo == null || handoffStarted || hardFailure) return;
            logoPulseAnimator = android.animation.ValueAnimator.ofFloat(0.88f, 1.0f);
            logoPulseAnimator.setDuration(900L);
            logoPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            logoPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            logoPulseAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                    if (loaderLogo != null) {
                        loaderLogo.setAlpha(((Float) animation.getAnimatedValue()).floatValue());
                    }
                }
            });
            logoPulseAnimator.start();
        }

        private void stopLogoPulse() {
            if (logoPulseAnimator != null) {
                logoPulseAnimator.cancel();
                logoPulseAnimator = null;
            }
            if (loaderLogo != null) loaderLogo.setAlpha(1.0f);
        }

        private void stopLoaderAnimations() {
            stopLogoPulse();
            if (progressAnimator != null) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
            if (loaderLogo != null) loaderLogo.animate().cancel();
            if (loaderPanel != null) loaderPanel.animate().cancel();
            if (loaderBackdrop != null) loaderBackdrop.animate().cancel();
            if (loaderOverlay != null) loaderOverlay.animate().cancel();
            if (topAppBar != null) topAppBar.animate().cancel();
            if (urlBar != null) urlBar.animate().cancel();
        }

        private void applyLoaderVerbosity() {
            boolean verbose = prefs == null || prefs.getBoolean(PREF_STARTUP_LOADER_VERBOSE, true);
            verboseLoader = verbose;
            int visibility = verbose ? View.VISIBLE : View.GONE;
            if (loaderDetail != null) loaderDetail.setVisibility(visibility);
            if (completedLabel != null) completedLabel.setVisibility(visibility);
            if (completedTicker != null) completedTicker.setVisibility(visibility);
            if (loaderStatus != null) {
                loaderStatus.setContentDescription(verbose
                        ? "Startup check status with detailed diagnostics"
                        : "Startup check status");
            }
        }

        private void resetLoaderVisuals() {
            stopLoaderAnimations();
            completedSteps.clear();

            if (loaderOverlay != null) {
                loaderOverlay.setAlpha(1.0f);
                loaderOverlay.setVisibility(View.VISIBLE);
            }
            if (loaderBackdrop != null) {
                loaderBackdrop.setAlpha(1.0f);
                loaderBackdrop.setVisibility(View.VISIBLE);
            }
            if (loaderPanel != null) {
                loaderPanel.setAlpha(1.0f);
                loaderPanel.setScaleX(1.0f);
                loaderPanel.setScaleY(1.0f);
                loaderPanel.setVisibility(View.VISIBLE);
            }
            if (loaderProgress != null) loaderProgress.setProgress(0);
            if (loaderPercent != null) loaderPercent.setText("0%");
            if (completedTicker != null) completedTicker.setText("Waiting for completed checks…");
            applyLoaderVerbosity();
            if (retryButton != null) retryButton.setVisibility(View.GONE);
        }

        private void resetLoaderStateForRetry() {
            runGeneration++;
            loaderStarted = false;
            handoffStarted = false;
            handoffPending = false;
            hardFailure = false;
            completedWeight = 0;
            resetLoaderVisuals();
            if (loaderTitle != null) loaderTitle.setText("Retrying Harley's Clan Forum");
            if (loaderStatus != null) loaderStatus.setText("Resetting startup checks");
            if (loaderDetail != null) loaderDetail.setText("Starting a clean validation pass.");
        }

        private void failStartup(final int token, final String title, final String detail) {
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    if (!isRunValid(token) || isFinishing() || isDestroyed()) return;
                    loaderStarted = false;
                    handoffStarted = false;
                    handoffPending = false;
                    hardFailure = true;
                    stopLogoPulse();
                    if (loaderTitle != null) loaderTitle.setText(title);
                    if (loaderStatus != null) loaderStatus.setText("Startup paused");
                    if (loaderDetail != null) loaderDetail.setVisibility(View.VISIBLE);
                    if (loaderDetail != null) loaderDetail.setText(detail);
                    if (retryButton != null) retryButton.setVisibility(View.VISIBLE);
                    AppLogger.error(StartupActivity.this, "startup_blocked", title + " | " + detail);
                }
            });
        }

        private boolean isRunValid(int token) {
            return token == runGeneration && !destroyed && !handoffStarted && !hardFailure;
        }

        private boolean hasInternetNetwork() {
            try {
                ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (manager == null) return false;
                Network active = manager.getActiveNetwork();
                if (active == null) return false;
                NetworkCapabilities caps = manager.getNetworkCapabilities(active);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_network", error.getClass().getSimpleName());
                return false;
            }
        }

        private boolean probeHost(String host) {
            HttpsURLConnection connection = null;
            try {
                URL url = new URL("https://" + host + "/");
                connection = (HttpsURLConnection) url.openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "HarleysClanForum-Startup/" + BuildInfo.VERSION_CODE);
                connection.setRequestProperty("Range", "bytes=0-0");
                int code = connection.getResponseCode();
                boolean healthy = code >= 200 && code < 400;
                AppLogger.info(this, "startup_host_probe",
                        host + " | HTTP " + code + " | " + (healthy ? "ready" : "not-ready"));
                return healthy;
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_host_probe", host + " | " + error.getClass().getSimpleName());
                return false;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private String preferredHost() {
            String host = prefs == null ? "" : prefs.getString(AppPrefs.ACTIVE_HOST, "");
            if (ForumUrlRouter.isForumHost(host)) return host;
            return ForumConfig.PRIMARY_HOST;
        }

        private void updateChromeHostDisplay() {
            String host = preferredHost();
            TextView hostBadge = findViewById(R.id.hostBadge);
            if (hostBadge != null) {
                hostBadge.setText(ForumConfig.BACKUP_HOST.equalsIgnoreCase(host) ? "Backup" : "Primary");
            }
            EditText currentUrl = findViewById(R.id.currentUrlText);
            if (currentUrl != null) {
                currentUrl.setText("https://" + host + "/");
                currentUrl.setFocusable(false);
                currentUrl.setCursorVisible(false);
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024L * 1024L) return Math.max(0L, bytes / 1024L) + " KB";
            long mb = bytes / (1024L * 1024L);
            if (mb < 1024L) return mb + " MB";
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0d * 1024.0d * 1024.0d));
        }

        private String shortText(String value, int max) {
            String clean = value == null ? "" : value.trim();
            if (clean.length() <= max) return clean;
            return clean.substring(0, Math.max(1, max - 1)) + "…";
        }

        private void destroyStartupWebView() {
            WebView view = startupWebView;
            startupWebView = null;
            if (view == null) return;
            try {
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
            } catch (Throwable ignored) {
            }
            try {
                view.stopLoading();
                view.destroy();
            } catch (Throwable ignored) {
            }
        }

        private void hideView(int id) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }

        private TextView text(String value, int sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextSize(sp);
            view.setTextColor(color);
            return view;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        private void showEmergencyStartupFailure(Throwable error) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));
            root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(logo, new LinearLayout.LayoutParams(dp(88), dp(88)));

            TextView title = text("Harley's Clan Forum • Startup Recovery", 19, getColor(R.color.hcf_cyan_bright));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(16);
            root.addView(title, titleLp);

            TextView body = text("The native startup screen could not be created.\n\n"
                            + error.getClass().getSimpleName(),
                    12, getColor(R.color.hcf_text));
            body.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
            bodyLp.topMargin = dp(10);
            root.addView(body, bodyLp);

            setContentView(root);
        }

        private static final class HostProbeResult {
            final String primary;
            final String backup;
            final boolean primaryHealthy;
            final boolean backupHealthy;

            HostProbeResult(String primary, String backup, boolean primaryHealthy, boolean backupHealthy) {
                this.primary = primary;
                this.backup = backup;
                this.primaryHealthy = primaryHealthy;
                this.backupHealthy = backupHealthy;
            }
        }
    }

    /** MainActivity handoff host that lets the website own its own loading animation. */
    public static final class StartupMainActivity extends HcfMainActivities.MainActivity {
        public static final String EXTRA_STARTUP_HANDOFF = "hcf_startup_handoff";

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);

            if (getIntent() == null || !getIntent().getBooleanExtra(EXTRA_STARTUP_HANDOFF, false)) {
                return;
            }

            try {
                View statusOverlay = findViewById(R.id.statusOverlay);
                if (statusOverlay != null) {
                    statusOverlay.animate().cancel();
                    statusOverlay.setAlpha(0.0f);
                    statusOverlay.setVisibility(View.GONE);
                }

                View startupState = findViewById(R.id.startupStateContainer);
                if (startupState != null) {
                    startupState.setVisibility(View.GONE);
                }

                WebView webView = findViewById(R.id.webView);
                if (webView != null) {
                    webView.setAlpha(1.0f);
                    webView.setVisibility(View.VISIBLE);
                }

                AppLogger.info(this, "startup_handoff", "native loader complete; forum WebView owns loading UI");
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_handoff", error.getClass().getSimpleName());
            }
        }
    }
}

// ---- ThemeManager.java ----
/**
 * App theme controller: light / dark / AMOLED / auto_forum / auto_phone.
 *
 * Cold-start: applyToApplication(Application) updates the process
 * Configuration so the first windowBackground and night resources match the
 * last known preference (including persisted forum_auto_theme).
 */
final class ThemeManager {
    static final String AMOLED = "amoled";
    static final String AUTO_FORUM = "auto_forum";
    static final String AUTO_PHONE = "auto_phone";
    static final String DARK = "dark";
    private static final String FORUM_AUTO = "auto";
    private static final String FORUM_DARK = "dark";
    private static final String FORUM_LIGHT = "light";
    private static final String LEGACY_SYSTEM = "system";
    static final String LIGHT = "light";
    static final String SYSTEM = "auto_forum";

    /** UI_MODE_NIGHT_YES */
    private static final int NIGHT_YES = 0x20;
    /** UI_MODE_NIGHT_NO */
    private static final int NIGHT_NO = 0x10;
    private static final int UI_MODE_NIGHT_MASK = 0x30;

    static void applyToApplication(Application application) {
        if (application == null) return;
        try {
            Context base = application.getBaseContext() != null ? application.getBaseContext() : application;
            String mode = mode(base);
            Configuration current = application.getResources().getConfiguration();
            int night = resolvedNightMode(base, current, mode);
            if ((current.uiMode & UI_MODE_NIGHT_MASK) == night) return;
            Configuration updated = new Configuration(current);
            updated.uiMode = night | (updated.uiMode & ~UI_MODE_NIGHT_MASK);
            Resources resources = application.getResources();
            resources.updateConfiguration(updated, resources.getDisplayMetrics());
        } catch (Throwable unused) {}
    }

    static Context wrap(Context context) {
        if (context == null) return null;
        try {
            String mode = mode(context);
            Configuration configuration = context.getResources().getConfiguration();
            int resolvedNightMode = resolvedNightMode(context, configuration, mode);
            if ((configuration.uiMode & UI_MODE_NIGHT_MASK) == resolvedNightMode) return context;
            Configuration configuration2 = new Configuration(configuration);
            configuration2.uiMode = resolvedNightMode | (configuration2.uiMode & ~UI_MODE_NIGHT_MASK);
            return context.createConfigurationContext(configuration2);
        } catch (Throwable unused) {
            return context;
        }
    }

    static void prepare(Activity activity) { activity.setTheme(R.style.Theme_HCF); }

    static void apply(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
        applySystemBars(activity);
    }

    static int resolvedNightMode(Context context) {
        return resolvedNightMode(context, context.getResources().getConfiguration(), mode(context));
    }

    private static int resolvedNightMode(Context context, Configuration configuration, String str) {
        if (DARK.equals(str) || AMOLED.equals(str)) return NIGHT_YES;
        if (LIGHT.equals(str)) return NIGHT_NO;
        if (AUTO_PHONE.equals(str)) return phoneNightMode(configuration);
        String forumAutoTheme = forumAutoTheme(context);
        if (FORUM_DARK.equals(forumAutoTheme)) return NIGHT_YES;
        if (FORUM_LIGHT.equals(forumAutoTheme)) return NIGHT_NO;
        return phoneNightMode(configuration);
    }

    private static int phoneNightMode(Configuration configuration) {
        return (configuration.uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES ? NIGHT_YES : NIGHT_NO;
    }

    static String signature(Context context) {
        return mode(context) + ":" + resolvedNightMode(context) + (isAmoled(context) ? ":amoled" : "");
    }

    static boolean changedSince(Context context, String str) {
        try { return str == null || !str.equals(signature(context)); }
        catch (Throwable unused) { return false; }
    }

    static String webColorScheme(Context context) {
        return resolvedNightMode(context) == NIGHT_YES ? "dark" : "light";
    }

    static void applySystemBars(Activity activity) {
        try {
            boolean isDark = isDark(activity);
            int color = isAmoled(activity) ? 0xFF000000 : activity.getColor(R.color.hcf_bg);
            activity.getWindow().setStatusBarColor(color);
            activity.getWindow().setNavigationBarColor(color);
            int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
            int i = !isDark ? systemUiVisibility | 8192 : systemUiVisibility & ~8192;
            activity.getWindow().getDecorView().setSystemUiVisibility(!isDark ? i | 16 : i & ~16);
        } catch (Throwable unused) {}
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES;
    }

    static boolean isAmoled(Context context) { return AMOLED.equals(mode(context)); }
    static boolean isAutoForum(Context context) { return AUTO_FORUM.equals(mode(context)); }
    static boolean isAutoPhone(Context context) { return AUTO_PHONE.equals(mode(context)); }

    static String mode(Context context) {
        if (context == null) return DARK;
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("app_theme", DARK);
            if (!LEGACY_SYSTEM.equals(string)) {
                return (AUTO_FORUM.equals(string) || AUTO_PHONE.equals(string) || LIGHT.equals(string)
                        || DARK.equals(string) || AMOLED.equals(string)) ? string : DARK;
            }
            sharedPreferences.edit().putString("app_theme", AUTO_FORUM).apply();
            return AUTO_FORUM;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try { sharedPreferences.edit().remove("app_theme").apply(); }
                catch (Throwable unused2) {}
            }
            return DARK;
        }
    }

    static String label(Context context) {
        String mode = mode(context);
        return AUTO_PHONE.equals(mode) ? "Auto • Phone"
                : LIGHT.equals(mode) ? "Day (Light)"
                : DARK.equals(mode) ? "Night (Dark)"
                : AMOLED.equals(mode) ? "AMOLED Black"
                : "Auto • Forum";
    }

    static boolean updateForumAutoTheme(Context context, String str) {
        if (context == null || !AUTO_FORUM.equals(mode(context))) return false;
        String lowerCase = str == null ? "" : str.trim().toLowerCase();
        String str2 = FORUM_DARK;
        if (!"dark".equals(lowerCase) && !"night".equals(lowerCase) && !"2".equals(lowerCase)) {
            str2 = FORUM_LIGHT;
            if (!"light".equals(lowerCase) && !"day".equals(lowerCase) && !"1".equals(lowerCase)) {
                if (!FORUM_AUTO.equals(lowerCase) && !LEGACY_SYSTEM.equals(lowerCase)
                        && !"phone".equals(lowerCase) && !"0".equals(lowerCase)) return false;
                str2 = FORUM_AUTO;
            }
        }
        if (str2.equals(forumAutoTheme(context))) return false;
        try {
            context.getSharedPreferences("hcf_app", 0).edit()
                    .putString("forum_auto_theme", str2)
                    .putLong("forum_auto_theme_updated_at", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Throwable unused) { return false; }
    }

    static String forumAutoTheme(Context context) {
        if (context == null) return FORUM_AUTO;
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("forum_auto_theme", FORUM_AUTO);
            return (FORUM_LIGHT.equals(string) || FORUM_DARK.equals(string)) ? string : FORUM_AUTO;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try { sharedPreferences.edit().remove("forum_auto_theme").apply(); }
                catch (Throwable unused2) {}
            }
            return FORUM_AUTO;
        }
    }

    static String autoSourceLabel(Context context) {
        String mode = mode(context);
        if (AUTO_PHONE.equals(mode)) {
            return resolvedNightMode(context) == NIGHT_YES ? "Auto • Phone Dark" : "Auto • Phone Light";
        }
        if (!AUTO_FORUM.equals(mode)) return label(context);
        String forumAutoTheme = forumAutoTheme(context);
        return FORUM_DARK.equals(forumAutoTheme) ? "Auto • Forum Dark"
                : FORUM_LIGHT.equals(forumAutoTheme) ? "Auto • Forum Light"
                : resolvedNightMode(context) == NIGHT_YES
                ? "Auto • Forum Auto → Phone Dark"
                : "Auto • Forum Auto → Phone Light";
    }

    static String next(String str) {
        return (AUTO_FORUM.equals(str) || LEGACY_SYSTEM.equals(str)) ? AUTO_PHONE
                : AUTO_PHONE.equals(str) ? LIGHT
                : LIGHT.equals(str) ? DARK
                : DARK.equals(str) ? AMOLED
                : AUTO_FORUM;
    }

    private ThemeManager() {}
}

// ---- ThemedActivity.java ----
abstract class ThemedActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String appliedThemeSignature;
    private SharedPreferences themePrefs;
    private boolean themeRecreatePending;

    ThemedActivity() {}

    @Override
    protected void attachBaseContext(Context context) {
        Context wrapped = context;
        try {
            Context candidate = ThemeManager.wrap(context);
            if (candidate != null) wrapped = candidate;
        } catch (Throwable unused) {}
        super.attachBaseContext(wrapped);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        try { ThemeManager.prepare(this); } catch (Throwable unused) {}
        super.onCreate(bundle);
        try { AppDomainRouter.installAddressBarInflater(this); } catch (Throwable unused2) {}
        try { ThemeManager.applySystemBars(this); } catch (Throwable unused3) {}
        try { this.appliedThemeSignature = ThemeManager.signature(this); }
        catch (Throwable unused4) { this.appliedThemeSignature = "auto_forum"; }
        try { this.themePrefs = getSharedPreferences("hcf_app", 0); }
        catch (Throwable unused5) { this.themePrefs = null; }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        try { WelcomeScreenFitter.apply(this, view); } catch (Throwable ignored) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try { sharedPreferences.registerOnSharedPreferenceChangeListener(this); }
            catch (Throwable unused) {}
        }
        recreateForThemeIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        recreateForThemeIfNeeded();
    }

    @Override
    protected void onStop() {
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try { sharedPreferences.unregisterOnSharedPreferenceChangeListener(this); }
            catch (Throwable unused) {}
        }
        super.onStop();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("app_theme".equals(str) || "forum_auto_theme".equals(str)) recreateForThemeIfNeeded();
    }

    private void recreateForThemeIfNeeded() {
        try {
            if (this.themeRecreatePending || isFinishing() || isDestroyed() || !ThemeManager.changedSince(this, this.appliedThemeSignature)) return;
            this.themeRecreatePending = true;
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() { ThemedActivity.this.m210xc87fab7b(); }
            }, 90L);
        } catch (Throwable unused) {}
    }

    void m210xc87fab7b() {
        if (isFinishing() || isDestroyed()) return;
        try { recreate(); }
        catch (Throwable unused) { this.themeRecreatePending = false; }
    }
}

final class WelcomeScreenFitter {
    private WelcomeScreenFitter() {}

    static void apply(final Activity activity, final View root) {
        if (activity == null || root == null || !(activity instanceof HcfMainActivities.WelcomeActivity)) return;
        if (root instanceof ScrollView) {
            ScrollView scroll = (ScrollView) root;
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        root.post(new Runnable() {
            @Override public void run() { fit(activity, root); }
        });
    }

    private static void fit(final Activity activity, final View root) {
        View content = root;
        int viewportHeight = root.getHeight();
        if (root instanceof ScrollView) {
            ScrollView scroll = (ScrollView) root;
            viewportHeight = scroll.getHeight();
            if (scroll.getChildCount() > 0) content = scroll.getChildAt(0);
        }
        if (content == null || viewportHeight <= 0) return;
        final View finalContent = content;
        final int finalViewportHeight = viewportHeight;
        int usedHeight = contentBottom(content);
        int safeBottom = dp(activity, 10);
        int targetHeight = Math.max(dp(activity, 520), viewportHeight - safeBottom);
        if (usedHeight > targetHeight) {
            float scale = ((float) targetHeight) / ((float) usedHeight);
            scale = Math.max(0.80f, Math.min(1.0f, scale));
            compact(content, scale, Math.max(0.88f, scale));
            content.requestLayout();
            content.post(new Runnable() {
                @Override public void run() { balance(activity, finalContent, finalViewportHeight); }
            });
        } else {
            balance(activity, content, viewportHeight);
        }
    }

    private static void balance(Activity activity, View content, int viewportHeight) {
        if (!(content instanceof LinearLayout) || viewportHeight <= 0) return;
        LinearLayout column = (LinearLayout) content;
        int usedHeight = contentBottom(column);
        int extra = viewportHeight - usedHeight;
        if (extra <= dp(activity, 6)) return;
        int visible = 0;
        for (int i = 0; i < column.getChildCount(); i++) if (column.getChildAt(i).getVisibility() != View.GONE) visible++;
        if (visible <= 0) return;
        int slots = visible + 1;
        int add = Math.max(1, extra / slots);
        column.setPadding(column.getPaddingLeft(), column.getPaddingTop() + add,
                column.getPaddingRight(), column.getPaddingBottom() + add);
        boolean first = true;
        for (int i = 0; i < column.getChildCount(); i++) {
            View child = column.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            if (first) { first = false; continue; }
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
                lp.topMargin += add;
                child.setLayoutParams(lp);
            }
        }
        column.setMinimumHeight(viewportHeight);
        column.requestLayout();
    }

    private static int contentBottom(View view) {
        if (!(view instanceof ViewGroup)) return Math.max(view.getMeasuredHeight(), view.getHeight());
        ViewGroup group = (ViewGroup) view;
        int bottom = group.getPaddingTop();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            bottom = Math.max(bottom, child.getBottom());
        }
        return bottom + group.getPaddingBottom();
    }

    private static void compact(View view, float layoutScale, float textScale) {
        if (view == null) return;
        view.setPadding(scaled(view.getPaddingLeft(), layoutScale), scaled(view.getPaddingTop(), layoutScale),
                scaled(view.getPaddingRight(), layoutScale), scaled(view.getPaddingBottom(), layoutScale));
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params.width > 0) params.width = scaled(params.width, layoutScale);
            if (params.height > 0) params.height = scaled(params.height, layoutScale);
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.leftMargin = scaled(margins.leftMargin, layoutScale);
                margins.topMargin = scaled(margins.topMargin, layoutScale);
                margins.rightMargin = scaled(margins.rightMargin, layoutScale);
                margins.bottomMargin = scaled(margins.bottomMargin, layoutScale);
            }
            view.setLayoutParams(params);
        }
        int minWidth = view.getMinimumWidth();
        int minHeight = view.getMinimumHeight();
        if (minWidth > 0) view.setMinimumWidth(scaled(minWidth, layoutScale));
        if (minHeight > 0) view.setMinimumHeight(scaled(minHeight, layoutScale));
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize() * textScale);
            text.setLineSpacing(text.getLineSpacingExtra() * textScale, text.getLineSpacingMultiplier());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) compact(group.getChildAt(i), layoutScale, textScale);
        }
    }

    private static int scaled(int value, float scale) { return value == 0 ? 0 : Math.max(1, Math.round(value * scale)); }
    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}

// ---- FaIcons.java ----
final class FaIcons {
    private FaIcons() {}

    static int forLabel(String str) {
        String lowerCase = str == null ? "" : str.toLowerCase(Locale.US);
        if (lowerCase.contains("account security") || lowerCase.contains("security") || lowerCase.contains("safe link")) return R.drawable.fa_shield;
        if (lowerCase.contains("home")) return R.drawable.fa_house;
        if (lowerCase.contains("backup") || lowerCase.contains("primary") || lowerCase.contains("switch host") || lowerCase.contains("failover")) return R.drawable.fa_right_left;
        if (lowerCase.contains("browser") || lowerCase.contains("externally") || lowerCase.contains("open link")) return R.drawable.fa_arrow_up_right_from_square;
        if (lowerCase.contains("share")) return R.drawable.fa_share_nodes;
        if (lowerCase.contains("notification") || lowerCase.contains("alert") || lowerCase.contains("mention") || lowerCase.contains("reply")) return R.drawable.fa_bell;
        if (lowerCase.contains("setting") || lowerCase.contains("theme") || lowerCase.contains("appearance") || lowerCase.contains("permission")) return R.drawable.fa_gear;
        if (lowerCase.contains("log") || lowerCase.contains("diagnostic") || lowerCase.contains("report") || lowerCase.contains("history") || lowerCase.contains("details")) return R.drawable.fa_list;
        if (lowerCase.contains("support") || lowerCase.contains("email") || lowerCase.contains("contact")) return R.drawable.fa_envelope;
        if (lowerCase.contains("copy")) return R.drawable.fa_copy;
        if (lowerCase.contains("retry") || lowerCase.contains("refresh") || lowerCase.contains("sync") || lowerCase.contains("check for updates")) return R.drawable.fa_rotate_right;
        if (lowerCase.contains("back")) return R.drawable.fa_arrow_left;
        if (lowerCase.contains("create") || lowerCase.contains("post") || lowerCase.contains("new discussion")) return R.drawable.fa_plus;
        if (lowerCase.contains("identity") || lowerCase.contains("profile") || lowerCase.contains("account")) return R.drawable.fa_user;
        if (lowerCase.contains("connection") || lowerCase.contains("server") || lowerCase.contains("forum") || lowerCase.contains("cookie") || lowerCase.contains("site data") || lowerCase.contains("link")) return R.drawable.fa_globe;
        if (lowerCase.contains("install") || lowerCase.contains("download") || lowerCase.contains("update")) return R.drawable.fa_download;
        if (lowerCase.contains("error") || lowerCase.contains("recovery") || lowerCase.contains("crash")) return R.drawable.fa_bug;
        return R.drawable.fa_circle_info;
    }

    static void applyStart(TextView textView, String str) { applyStart(textView, forLabel(str)); }

    static void applyStart(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) return;
        drawable.setBounds(0, 0, dp(textView.getContext(), 18), dp(textView.getContext(), 18));
        textView.setCompoundDrawablesRelative(drawable, null, null, null);
        textView.setCompoundDrawablePadding(dp(textView.getContext(), 10));
        tint(textView);
    }

    static void applyTop(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) return;
        drawable.setBounds(0, 0, dp(textView.getContext(), 19), dp(textView.getContext(), 19));
        textView.setCompoundDrawables(null, drawable, null, null);
        textView.setCompoundDrawablePadding(dp(textView.getContext(), 2));
        tint(textView);
        textView.setGravity(17);
    }

    static void applyOnly(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) return;
        drawable.setBounds(0, 0, dp(textView.getContext(), 20), dp(textView.getContext(), 20));
        textView.setCompoundDrawables(null, null, null, null);
        textView.setBackground(textView.getBackground());
        textView.setCompoundDrawablesRelative(drawable, null, null, null);
        textView.setCompoundDrawablePadding(0);
        tint(textView);
        textView.setGravity(17);
    }

    private static void tint(TextView textView) {
        try { textView.setCompoundDrawableTintList(ColorStateList.valueOf(textView.getCurrentTextColor())); }
        catch (Throwable unused) {}
    }

    private static int dp(Context context, int i) { return Math.round(i * context.getResources().getDisplayMetrics().density); }
}

// ---- UiButtons.java ----
final class UiButtons {
    static void normalizeText(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setGravity(17);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
    }

    static ImageButton iconButton(Context context, int i, int i2, int i3, String str) {
        ImageButton imageButton = new ImageButton(context);
        imageButton.setImageResource(i);
        imageButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (i2 != 0) imageButton.setBackgroundResource(i2); else imageButton.setBackgroundColor(0);
        int dp = dp(context, i3);
        imageButton.setPadding(dp, dp, dp, dp);
        imageButton.setMinimumWidth(0);
        imageButton.setMinimumHeight(0);
        imageButton.setAdjustViewBounds(false);
        if (str == null || str.trim().isEmpty()) str = "Button";
        imageButton.setContentDescription(str);
        return imageButton;
    }

    private static int dp(Context context, int i) { return Math.round(i * context.getResources().getDisplayMetrics().density); }
    private UiButtons() {}
}

// ---- AppDomainEditText.java ----
final class AppDomainEditText extends EditText {
    public AppDomainEditText(Context context) { super(context); }
    public AppDomainEditText(Context context, AttributeSet attrs) { super(context, attrs); }
    public AppDomainEditText(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override
    public void setOnEditorActionListener(final TextView.OnEditorActionListener listener) {
        super.setOnEditorActionListener((view, actionId, event) -> {
            if (isSubmitAction(actionId, event)) {
                Activity activity = findActivity(getContext());
                String raw = getText() == null ? "" : getText().toString().trim();
                if (activity != null && AppDomainRouter.handle(activity, raw)) {
                    hideKeyboard(activity);
                    clearFocus();
                    restoreDisplayedForumUrl(activity);
                    return true;
                }
            }
            return listener != null && listener.onEditorAction(view, actionId, event);
        });
    }

    private boolean isSubmitAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) return true;
        return event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }

    private void hideKeyboard(Activity activity) {
        try {
            InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private void restoreDisplayedForumUrl(final Activity activity) {
        post(() -> {
            try {
                WebView webView = activity.findViewById(R.id.webView);
                if (webView == null) return;
                String url = webView.getUrl();
                if (url != null && !url.trim().isEmpty()) {
                    setText(url);
                    setSelection(length());
                }
            } catch (Throwable ignored) {}
        });
    }

    private Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}

final class UrlBackButton extends ImageButton {
    UrlBackButton(Context c){super(c);init();}
    UrlBackButton(Context c,AttributeSet a){super(c,a);init();}
    UrlBackButton(Context c,AttributeSet a,int d){super(c,a,d);init();}
    private void init(){setOnClickListener(v->back());}
    private void back(){Activity a=act(getContext());if(a==null)return;WebView w=a.findViewById(R.id.webView);if(w!=null&&w.canGoBack()){w.goBack();try{AppLogger.info(a,"url_back",AppLogger.safeUrl(w.getUrl()));}catch(Throwable ignored){}return;}android.widget.Toast.makeText(a,"No previous forum page.",android.widget.Toast.LENGTH_SHORT).show();}
    private Activity act(Context c){Context x=c;while(x instanceof ContextWrapper){if(x instanceof Activity)return(Activity)x;x=((ContextWrapper)x).getBaseContext();}return x instanceof Activity?(Activity)x:null;}
}
