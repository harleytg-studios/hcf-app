package com.harleytg.forum.dev;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView;

import android.widget.Toast;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;


// ---- Consolidated from HcfUITheme.java ----
public final class HcfUI {
    private HcfUI() {}

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
        private LinearLayout loaderStepRow;
        private ImageView loaderLogo;
        private TextView loaderTitle;
        private TextView loaderWelcomeSubtitle;
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
        private boolean verboseLoader = false;
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
                Intent welcome = new Intent(this, HcfForum.WelcomeActivity.class);
                welcome.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
                startActivityForResult(welcome, REQUEST_WELCOME);
                AppLogger.info(this, "startup_gate", "welcome");
                return;
            }

            if (SetupCenter.shouldAutoLaunch(this)) {
                gateInProgress = true;
                SetupCenter.markSeen(this);
                getWindow().getDecorView().setAlpha(0.0f);
                Intent setup = new Intent(this, HcfForum.SetupActivity.class);
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

            loaderWelcomeSubtitle = text("", 12, getColor(R.color.hcf_muted));
            loaderWelcomeSubtitle.setGravity(Gravity.CENTER);
            loaderWelcomeSubtitle.setLineSpacing(0.0f, 1.08f);
            loaderWelcomeSubtitle.setVisibility(View.GONE);
            LinearLayout.LayoutParams welcomeSubtitleLp = new LinearLayout.LayoutParams(-1, -2);
            welcomeSubtitleLp.topMargin = dp(6);
            loaderPanel.addView(loaderWelcomeSubtitle, welcomeSubtitleLp);

            loaderStepRow = new LinearLayout(this);
            loaderStepRow.setOrientation(LinearLayout.HORIZONTAL);
            loaderStepRow.setGravity(Gravity.CENTER_VERTICAL);
            loaderStep = text("Step 0 of " + FULL_STAGE_COUNT, 10, getColor(R.color.hcf_meta));
            loaderStep.setTypeface(null, 1);
            loaderPercent = text("0%", 10, getColor(R.color.hcf_cyan_bright));
            loaderPercent.setTypeface(null, 1);
            loaderPercent.setGravity(Gravity.END);
            loaderStepRow.addView(loaderStep, new LinearLayout.LayoutParams(0, -2, 1.0f));
            loaderStepRow.addView(loaderPercent, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-1, -2);
            stepLp.topMargin = dp(16);
            loaderPanel.addView(loaderStepRow, stepLp);

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
            verboseLoader = prefs != null && prefs.getBoolean(PREF_STARTUP_LOADER_VERBOSE, false);
            loaderStarted = true;
            handoffPending = false;
            loaderVisibleAt = android.os.SystemClock.elapsedRealtime();
            final int token = ++runGeneration;

            resetLoaderVisuals();
            applyLoaderVerbosity();
            animateLogoEntrance();

            if (loaderTitle != null) loaderTitle.setText("Starting Harley's Clan Forum");
            updateLoaderWelcomeSubtitle();
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

        private void updateLoaderWelcomeSubtitle() {
            if (loaderWelcomeSubtitle == null) return;
            if (!quickPath) {
                loaderWelcomeSubtitle.setText("");
                loaderWelcomeSubtitle.setVisibility(View.GONE);
                return;
            }

            String value = "Welcome back";
            try {
                ForumIdentity.Snapshot identity = ForumIdentity.load(this);
                if (identity != null && identity.loggedIn && !TextUtils.isEmpty(identity.username)) {
                    String username = identity.username.trim();
                    while (username.startsWith("@")) username = username.substring(1);
                    if (!TextUtils.isEmpty(username)) value += "\n@" + username;
                }
            } catch (Throwable error) {
                AppLogger.warn(this, "startup_welcome_back", error.getClass().getSimpleName());
            }
            loaderWelcomeSubtitle.setText(value);
            loaderWelcomeSubtitle.setVisibility(View.VISIBLE);
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
            boolean verbose = prefs != null && prefs.getBoolean(PREF_STARTUP_LOADER_VERBOSE, false);
            verboseLoader = verbose;
            int visibility = verbose ? View.VISIBLE : View.GONE;
            if (loaderStepRow != null) loaderStepRow.setVisibility(visibility);
            if (loaderStep != null) loaderStep.setVisibility(visibility);
            if (loaderPercent != null) loaderPercent.setVisibility(visibility);
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
            if (loaderWelcomeSubtitle != null) {
                loaderWelcomeSubtitle.setText("");
                loaderWelcomeSubtitle.setVisibility(View.GONE);
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
                    if (loaderDetail != null) loaderDetail.setVisibility(verboseLoader ? View.VISIBLE : View.GONE);
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
    public static final class StartupMainActivity extends HcfForum.MainActivity {
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
        if (activity == null || root == null || !(activity instanceof HcfForum.WelcomeActivity)) return;
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

// ---- Consolidated from HcfBrandedLoader.java ----
/** Shared full-screen Harley's Clan Forum loading surface. */
final class HcfBrandedLoader {
    public enum Mode { STARTUP, CONNECT, RECONNECT, LOADING }

    private final Context context;
    private final FrameLayout overlay;
    private final ImageView logo;
    private final TextView welcomeSubtitle;
    private final TextView title;
    private final TextView status;
    private final TextView detail;
    private final TextView step;
    private final TextView percent;
    private final TextView ticker;
    private final TextView build;
    private final ProgressBar spinner;
    private final ProgressBar progress;
    private final LinearLayout startupProgressGroup;
    private final Button retry;
    private FrameLayout parent;
    private ObjectAnimator logoPulse;
    private ValueAnimator progressAnimator;
    private Mode mode = Mode.LOADING;

    public HcfBrandedLoader(Context context) {
        this.context = context;
        int background = ThemeManager.isAmoled(context) ? Color.BLACK : color(R.color.hcf_bg);
        int cyan = color(R.color.hcf_cyan_bright);

        overlay = new FrameLayout(context);
        overlay.setBackgroundColor(background);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setVisibility(View.GONE);
        overlay.setAlpha(1.0f);
        overlay.setElevation(dp(48));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        overlay.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        logo = new ImageView(context);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum");
        content.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView brand = label("HARLEY'S STUDIOS", 11.0f, cyan, true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams brandLp = wrap();
        brandLp.topMargin = dp(12);
        content.addView(brand, brandLp);

        welcomeSubtitle = label("", 11.0f, color(R.color.hcf_meta), false);
        welcomeSubtitle.setGravity(Gravity.CENTER);
        welcomeSubtitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams welcomeLp = matchWrap();
        welcomeLp.topMargin = dp(7);
        content.addView(welcomeSubtitle, welcomeLp);

        title = label(defaultTitle(Mode.LOADING), 21.0f, color(R.color.hcf_text), true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(18);
        content.addView(title, titleLp);

        status = label("Preparing forum…", 13.0f, cyan, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(8);
        content.addView(status, statusLp);

        detail = label("", 11.0f, color(R.color.hcf_muted), false);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(0.0f, 1.12f);
        LinearLayout.LayoutParams detailLp = matchWrap();
        detailLp.topMargin = dp(6);
        content.addView(detail, detailLp);

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(cyan));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        spinnerLp.topMargin = dp(22);
        content.addView(spinner, spinnerLp);

        startupProgressGroup = new LinearLayout(context);
        startupProgressGroup.setOrientation(LinearLayout.VERTICAL);
        startupProgressGroup.setVisibility(View.GONE);
        LinearLayout.LayoutParams startupLp = matchWrap();
        startupLp.topMargin = dp(20);
        content.addView(startupProgressGroup, startupLp);

        LinearLayout stepRow = new LinearLayout(context);
        stepRow.setOrientation(LinearLayout.HORIZONTAL);
        stepRow.setGravity(Gravity.CENTER_VERTICAL);
        startupProgressGroup.addView(stepRow, matchWrap());

        step = label("Step 0 of 0", 11.0f, color(R.color.hcf_meta), true);
        stepRow.addView(step, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        percent = label("0%", 11.0f, cyan, true);
        percent.setGravity(Gravity.END);
        stepRow.addView(percent, wrap());

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(cyan));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.hcf_app_bar)));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(8);
        startupProgressGroup.addView(progress, progressLp);

        ticker = label("", 10.0f, color(R.color.hcf_muted), false);
        ticker.setLineSpacing(0.0f, 1.15f);
        ticker.setVisibility(View.GONE);
        LinearLayout.LayoutParams tickerLp = matchWrap();
        tickerLp.topMargin = dp(11);
        startupProgressGroup.addView(ticker, tickerLp);

        retry = new Button(context);
        UiButtons.normalizeText(retry);
        retry.setText("Retry");
        retry.setAllCaps(false);
        retry.setTextColor(cyan);
        retry.setTypeface(null, Typeface.BOLD);
        retry.setBackgroundResource(R.drawable.button_background);
        retry.setVisibility(View.GONE);
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        retryLp.topMargin = dp(22);
        content.addView(retry, retryLp);

        build = label(BuildInfo.VERSION_BUILD_LINE, 9.0f, color(R.color.hcf_hint), false);
        build.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildLp = matchWrap();
        buildLp.topMargin = dp(18);
        content.addView(build, buildLp);
    }

    public void attach(FrameLayout parent) {
        if (parent == null) return;
        if (this.parent == parent && overlay.getParent() == parent) return;
        detach();
        this.parent = parent;
        parent.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void detach() {
        stopAnimations();
        ViewGroup currentParent = (overlay.getParent() instanceof ViewGroup)
                ? (ViewGroup) overlay.getParent() : null;
        if (currentParent != null) currentParent.removeView(overlay);
        parent = null;
    }

    public boolean isVisible() {
        return overlay.getParent() != null && overlay.getVisibility() == View.VISIBLE;
    }

    public void show(Mode mode, String titleText, String detailText) {
        this.mode = mode == null ? Mode.LOADING : mode;
        setTitle(isBlank(titleText) ? defaultTitle(this.mode) : titleText);
        setStatus(defaultStatus(this.mode));
        setDetail(detailText);
        setRetryVisible(false);
        boolean startup = this.mode == Mode.STARTUP;
        startupProgressGroup.setVisibility(startup ? View.VISIBLE : View.GONE);
        spinner.setVisibility(startup ? View.GONE : View.VISIBLE);
        overlay.animate().cancel();
        overlay.setAlpha(1.0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        startLogoPulse();
    }

    public void showConnecting(String host) {
        show(Mode.CONNECT, defaultTitle(Mode.CONNECT), host);
    }

    public void showReconnecting(String host) {
        show(Mode.RECONNECT, defaultTitle(Mode.RECONNECT), host);
    }

    public void showLoading(String message) {
        show(Mode.LOADING, defaultTitle(Mode.LOADING), message);
    }

    public void showStartup(String titleText, String statusText, String detailText) {
        show(Mode.STARTUP, titleText, detailText);
        setStatus(statusText);
    }

    public void hide() {
        hide(false);
    }

    public void hide(boolean animate) {
        if (overlay.getVisibility() != View.VISIBLE) {
            stopAnimations();
            overlay.setVisibility(View.GONE);
            overlay.setAlpha(1.0f);
            return;
        }
        if (!animate) {
            stopAnimations();
            overlay.animate().cancel();
            overlay.setAlpha(1.0f);
            overlay.setVisibility(View.GONE);
            return;
        }
        stopLogoPulse();
        overlay.animate().cancel();
        overlay.animate().alpha(0.0f).setDuration(180L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        overlay.setVisibility(View.GONE);
                        overlay.setAlpha(1.0f);
                        overlay.animate().setListener(null);
                    }
                }).start();
    }

    public void setTitle(String value) {
        title.setText(safe(value));
    }

    public void setStatus(String value) {
        String text = safe(value);
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setDetail(String value) {
        String text = safe(value);
        detail.setText(text);
        detail.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setWelcomeSubtitle(String value) {
        String text = safe(value);
        welcomeSubtitle.setText(text);
        welcomeSubtitle.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setProgress(int thousandths) {
        int value = clampProgress(thousandths);
        if (progressAnimator != null) progressAnimator.cancel();
        progress.setProgress(value);
        percent.setText(Math.round(value / 10.0f) + "%");
    }

    public void animateProgressTo(int thousandths, long durationMs) {
        int target = clampProgress(thousandths);
        if (progressAnimator != null) progressAnimator.cancel();
        progressAnimator = ValueAnimator.ofInt(progress.getProgress(), target);
        progressAnimator.setDuration(Math.max(0L, durationMs));
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            int value = (Integer) animation.getAnimatedValue();
            progress.setProgress(value);
            percent.setText(Math.round(value / 10.0f) + "%");
        });
        progressAnimator.start();
    }

    public void setStep(int stepValue, int total) {
        int safeTotal = Math.max(0, total);
        int safeStep = safeTotal == 0 ? Math.max(0, stepValue)
                : Math.min(Math.max(0, stepValue), safeTotal);
        step.setText("Step " + safeStep + " of " + safeTotal);
    }

    public void setTicker(String value) {
        String text = safe(value);
        ticker.setText(text);
        ticker.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setRetryVisible(boolean show) {
        retry.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void setRetryListener(View.OnClickListener listener) {
        retry.setOnClickListener(listener);
    }

    public void setRetryLabel(String label) {
        retry.setText(isBlank(label) ? "Retry" : label);
    }

    private void startLogoPulse() {
        stopLogoPulse();
        logoPulse = ObjectAnimator.ofFloat(logo, View.ALPHA, 1.0f, 0.72f, 1.0f);
        logoPulse.setDuration(1700L);
        logoPulse.setRepeatCount(ValueAnimator.INFINITE);
        logoPulse.setRepeatMode(ValueAnimator.RESTART);
        logoPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        logoPulse.start();
    }

    private void stopLogoPulse() {
        if (logoPulse != null) {
            logoPulse.cancel();
            logoPulse = null;
        }
        logo.setAlpha(1.0f);
    }

    private void stopAnimations() {
        stopLogoPulse();
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        overlay.animate().cancel();
    }

    private String defaultTitle(Mode value) {
        if (value == Mode.STARTUP) return "Starting Harley's Clan Forum";
        if (value == Mode.CONNECT) return "Connecting to Harley's Clan Forum…";
        if (value == Mode.RECONNECT) return "Reconnecting to Harley's Clan Forum…";
        return "Loading forum…";
    }

    private String defaultStatus(Mode value) {
        if (value == Mode.STARTUP) return "Preparing app…";
        if (value == Mode.CONNECT) return "Establishing secure connection…";
        if (value == Mode.RECONNECT) return "Restoring secure connection…";
        return "Preparing forum…";
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int color(int id) {
        return context.getColor(id);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int clampProgress(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

// ---- Consolidated from HcfSubActivities.java ----
final class HcfSubActivities {
    private HcfSubActivities() {}

    // ---- IdentityActivity.java ----
    /* loaded from: classes.dex */
    public static final class IdentityActivity extends ThemedActivity {
        private LinearLayout content;
        private ImageView identityAvatar;
        private Bitmap identityAvatarBitmap;
        private String identityAvatarRequestedUrl = "";
        private String identityAvatarLoadedUrl = "";

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            ThemeManager.apply(this);
            getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            setContentView(buildUi());
            render();
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onResume() {
            super.onResume();
            render();
        }

        private View buildUi() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setGravity(16);
            linearLayout2.setPadding(dp(8), dp(compact() ? 3 : 7), dp(10), dp(compact() ? 3 : 7));
            linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
            linearLayout2.setMinimumHeight(dp(compact() ? 46 : 56));
            ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, compact() ? 9 : 11, "Back");
            iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda5
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    IdentityActivity.this.m10lambda$buildUi$0$comharleytgforumdevIdentityActivity(view);
                }
            });
            linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.htg_app_logo);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setContentDescription("Harley's Clan Forum");
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
            layoutParams.leftMargin = dp(8);
            linearLayout2.addView(imageView, layoutParams);
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.leftMargin = dp(10);
            linearLayout2.addView(linearLayout3, layoutParams2);
            TextView text = text("Account & Identity", 18, getColor(R.color.hcf_text));
            text.setTypeface(null, 1);
            linearLayout3.addView(text);
            TextView text2 = text("Identity data stays scoped to the current signed-in forum user", 10, getColor(R.color.hcf_meta));
            text2.setMaxLines(1);
            text2.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout3.addView(text2);
            linearLayout.addView(linearLayout2);
            ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(true);
            LinearLayout linearLayout4 = new LinearLayout(this);
            this.content = linearLayout4;
            linearLayout4.setOrientation(1);
            this.content.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
            scrollView.addView(this.content, new FrameLayout.LayoutParams(-1, -2));
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            return linearLayout;
        }

        /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m10lambda$buildUi$0$comharleytgforumdevIdentityActivity(View view) {
            finish();
        }

        private void render() {
            LinearLayout linearLayout = this.content;
            if (linearLayout == null) {
                return;
            }
            linearLayout.removeAllViews();
            this.identityAvatar = null;
            ForumIdentity.Snapshot load = ForumIdentity.load(this);
            ForumSecurity.Snapshot load2 = ForumSecurity.load(this);
            this.content.addView(profileCard(load));
            this.content.addView(linkedAccountsCard(load, load2));
            this.content.addView(securityCard(load, load2));
            this.content.addView(activityCard(load));
            this.content.addView(quickActionsCard(load));
            this.content.addView(sessionCard(load, load2));
            loadIdentityAvatar(load);
        }

        private View profileCard(ForumIdentity.Snapshot snapshot) {
            String str;
            String str2;
            String str3;
            LinearLayout card = card();
            card.addView(sectionTitle("Account details", "Your current Harley's Clan Forum session"));
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            ImageView imageView = new HcfForum.IdentityAvatarView(this);
            this.identityAvatar = imageView;
            imageView.setImageResource(R.drawable.htg_app_logo);
            this.identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            this.identityAvatar.setAdjustViewBounds(false);
            this.identityAvatar.setCropToPadding(false);
            this.identityAvatar.setPadding(dp(3), dp(3), dp(3), dp(3));
            this.identityAvatar.setClipToOutline(true);
            this.identityAvatar.setContentDescription("Current forum identity avatar placeholder");
            FrameLayout identityAvatarFrame = HcfForum.IdentityAvatarView.frame(this, this.identityAvatar);
            linearLayout.addView(identityAvatarFrame, new LinearLayout.LayoutParams(dp(72), dp(72)));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.leftMargin = dp(12);
            linearLayout.addView(linearLayout2, layoutParams);
            TextView text = text(snapshot.loggedIn ? snapshot.identityLabel() : "Guest_Protocol", 18, getColor(R.color.hcf_accent_text));
            text.setTypeface(null, 1);
            linearLayout2.addView(text);
            if (snapshot.loggedIn) {
                if (snapshot.username.isEmpty()) {
                    str = "Identity sync";
                } else {
                    str = "@" + snapshot.username;
                }
            } else {
                str = "Not signed in";
            }
            linearLayout2.addView(text(str, 12, getColor(R.color.hcf_meta)));
            if (snapshot.loggedIn) {
                str2 = "Only self-visible details exposed by your current forum session";
            } else {
                str2 = "Sign in inside the forum and this page updates automatically.";
            }
            linearLayout2.addView(text(str2, 10, getColor(R.color.hcf_muted)));
            card.addView(linearLayout);
            if (snapshot.loggedIn) {
                addRow(card, "Display name", snapshot.identityLabel());
                if (snapshot.username.isEmpty()) {
                    str3 = "—";
                } else {
                    str3 = "@" + snapshot.username;
                }
                addRow(card, "Username", str3);
                addRow(card, "Groups / roles", snapshot.groups.isEmpty() ? snapshot.admin ? "Administrator" : "Member" : snapshot.groups);
                addRow(card, "Email status", snapshot.email.isEmpty() ? "Not exposed" : snapshot.emailConfirmed ? "Verified" : "Not verified");
            } else {
                addRow(card, "Forum session", "Guest_Protocol");
            }
            return card;
        }

        private View linkedAccountsCard(final ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
            String str;
            LinearLayout card = card();
            card.addView(sectionTitle("Linked accounts", "Sign-in providers detected for this forum account"));
            if (!snapshot.loggedIn) {
                card.addView(text("Sign in to the forum to view linked accounts.", 11, getColor(R.color.hcf_muted)));
                return card;
            }
            String mergeLabels = ForumSecurity.mergeLabels(snapshot.connections, snapshot2.seen ? snapshot2.providers : "");
            boolean z = !snapshot.email.isEmpty() || containsProvider(mergeLabels, "email");
            boolean containsProvider = containsProvider(mergeLabels, "discord");
            boolean containsProvider2 = containsProvider(mergeLabels, "google");
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            if (z) {
                linearLayout.addView(providerChip("Email", R.drawable.ic_provider_email, snapshot.emailConfirmed ? "Verified" : "Linked"));
            }
            if (containsProvider) {
                linearLayout.addView(providerChip("Discord", R.drawable.ic_provider_discord, "Linked"));
            }
            if (containsProvider2) {
                linearLayout.addView(providerChip("Google", R.drawable.ic_provider_google, "Linked"));
            }
            if (linearLayout.getChildCount() > 0) {
                HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
                horizontalScrollView.setHorizontalScrollBarEnabled(false);
                horizontalScrollView.setFillViewport(false);
                horizontalScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-2, -2));
                card.addView(horizontalScrollView);
                if (mergeLabels.isEmpty()) {
                    mergeLabels = z ? "Email" : "Linked";
                }
                addRow(card, "Detected providers", mergeLabels);
            } else {
                if (snapshot2.seen) {
                    str = "No linked sign-in providers were detected for this account.";
                } else {
                    str = "Linked sign-in providers sync automatically while the forum is signed in.";
                }
                card.addView(text(str, 11, getColor(R.color.hcf_muted)));
            }
            card.addView(actionRow("Manage Linked Accounts", "View and manage your forum sign-in methods", R.drawable.fa_shield, new View.OnClickListener() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    IdentityActivity.this.m11xe0d3a06d(snapshot, view);
                }
            }));
            return card;
        }

        /* renamed from: lambda$linkedAccountsCard$1$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m11xe0d3a06d(ForumIdentity.Snapshot snapshot, View view) {
            openAccountSecurity(snapshot);
        }

        private TextView providerChip(String str, int i, String str2) {
            String str3;
            StringBuilder sb = new StringBuilder();
            sb.append(str);
            if (str2 == null || str2.isEmpty()) {
                str3 = "";
            } else {
                str3 = "  •  " + str2;
            }
            sb.append(str3);
            TextView text = text(sb.toString(), 10, getColor(R.color.hcf_text));
            text.setGravity(16);
            text.setBackgroundResource(R.drawable.provider_chip_background);
            text.setCompoundDrawablesWithIntrinsicBounds(i, 0, 0, 0);
            text.setCompoundDrawablePadding(dp(5));
            text.setPadding(dp(9), dp(7), dp(9), dp(7));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
            layoutParams.rightMargin = dp(6);
            text.setLayoutParams(layoutParams);
            return text;
        }

        private static boolean containsProvider(String str, String str2) {
            if (str == null || str2 == null) {
                return false;
            }
            return str.toLowerCase(Locale.US).contains(str2.toLowerCase(Locale.US));
        }

        private View securityCard(ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
            LinearLayout card = card();
            card.addView(sectionTitle("Sign-in & security", "A safe summary synced from your forum account"));
            String mergeLabels = ForumSecurity.mergeLabels(snapshot.connections, snapshot2.seen ? snapshot2.providers : "");
            if (mergeLabels.isEmpty()) {
                mergeLabels = snapshot.connectionLabel();
            }
            if (mergeLabels.isEmpty()) {
                mergeLabels = "None detected";
            }
            addRow(card, "Connected sign-in methods", mergeLabels);
            addRow(card, "Email status", snapshot.email.isEmpty() ? "Not exposed" : snapshot.emailConfirmed ? "Verified" : "Not verified");
            addRow(card, "Security sync", snapshot2.seen ? snapshot2.sessionLabel() : "Syncing automatically");
            String str = "No controls detected";
            if (snapshot2.seen) {
                ArrayList arrayList = new ArrayList();
                if (snapshot2.passwordControls) {
                    arrayList.add("Password");
                }
                if (snapshot2.emailControls) {
                    arrayList.add("Email");
                }
                if (snapshot2.twoFactorControls) {
                    arrayList.add("2FA");
                }
                if (!arrayList.isEmpty()) {
                    str = TextUtils.join(" • ", arrayList);
                }
            }
            addRow(card, "Available controls", str);
            return card;
        }

        private View activityCard(ForumIdentity.Snapshot snapshot) {
            LinearLayout card = card();
            card.addView(sectionTitle("Forum activity", "A quick view of your current account activity"));
            if (snapshot.loggedIn) {
                addRow(card, "Discussions", Integer.toString(snapshot.discussionCount));
                addRow(card, "Comments", Integer.toString(snapshot.commentCount));
                addRow(card, "Unread notifications", Integer.toString(snapshot.unreadNotifications));
                addRow(card, "New notifications", Integer.toString(snapshot.newNotifications));
                addRow(card, "Joined", displayDate(snapshot.joinTime));
                addRow(card, "Last seen", displayDate(snapshot.lastSeenAt));
            } else {
                card.addView(text("Your account details appear here automatically after the forum session is signed in.", 11, getColor(R.color.hcf_muted)));
            }
            return card;
        }

        private View quickActionsCard(final ForumIdentity.Snapshot snapshot) {
            LinearLayout card = card();
            card.addView(sectionTitle("Account shortcuts", "Profile and security actions for this signed-in account"));
            if (!snapshot.loggedIn) {
                card.addView(actionRow("Open Forum Sign In", "Sign in to sync your forum identity", R.drawable.fa_user, new View.OnClickListener() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda0
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        IdentityActivity.this.m15xd192321f(view);
                    }
                }));
                return card;
            }
            card.addView(actionRow("Open My Forum Profile", "View your profile on Harley's Clan Forum", R.drawable.fa_user, new View.OnClickListener() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    IdentityActivity.this.m16x5e325d20(snapshot, view);
                }
            }));
            card.addView(actionRow("Open Account Security", "Manage password, email and available security controls", R.drawable.fa_shield, new View.OnClickListener() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    IdentityActivity.this.m17xead28821(snapshot, view);
                }
            }));
            return card;
        }

        /* renamed from: lambda$quickActionsCard$2$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m15xd192321f(View view) {
            openForumPath("/login");
        }

        /* renamed from: lambda$quickActionsCard$3$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m16x5e325d20(ForumIdentity.Snapshot snapshot, View view) {
            openProfile(snapshot);
        }

        /* renamed from: lambda$quickActionsCard$4$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m17xead28821(ForumIdentity.Snapshot snapshot, View view) {
            openAccountSecurity(snapshot);
        }

        private View sessionCard(ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
            LinearLayout card = card();
            card.addView(sectionTitle("Session & privacy", "Identity sync"));
            addRow(card, "Forum host", snapshot.host.isEmpty() ? "forum.harleytg.com" : snapshot.host);
            addRow(card, "Identity sync", snapshot.syncedAt <= 0 ? "Not synced yet" : DateFormat.getDateTimeInstance().format(new Date(snapshot.syncedAt)));
            addRow(card, "Security sync", snapshot2.syncedAt > 0 ? DateFormat.getDateTimeInstance().format(new Date(snapshot2.syncedAt)) : "Not synced yet");
            TextView text = text("The app stores only the current user's self-visible profile summary and safe security capability/status fields. It does not store passwords, recovery codes, access/session token values, or cookie values.", 10, getColor(R.color.hcf_muted));
            text.setPadding(0, dp(10), 0, 0);
            card.addView(text);
            return card;
        }

        private void openProfile(ForumIdentity.Snapshot snapshot) {
            if (snapshot == null || !snapshot.loggedIn) {
                return;
            }
            String str = !snapshot.slug.isEmpty() ? snapshot.slug : snapshot.username;
            if (str == null || str.trim().isEmpty()) {
                return;
            }
            openForumPath("/u/" + Uri.encode(str.trim()));
        }

        private void openAccountSecurity(ForumIdentity.Snapshot snapshot) {
            if (snapshot == null || !snapshot.loggedIn) {
                Toast.makeText(this, "Sign in to the forum first.", 0).show();
                return;
            }
            String str = !snapshot.slug.isEmpty() ? snapshot.slug : snapshot.username;
            if (str == null || str.trim().isEmpty()) {
                Toast.makeText(this, "Unable to determine your forum profile route.", 0).show();
                return;
            }
            openForumPath("/u/" + Uri.encode(str.trim()) + "/security");
        }

        private void openForumPath(String str) {
            ForumIdentity.Snapshot load = ForumIdentity.load(this);
            String str2 = ForumUrlRouter.isForumHost(load.host) ? load.host : "forum.harleytg.com";
            Intent intent = new Intent(this, (Class<?>) HcfForum.MainActivity.class);
            intent.setData(Uri.parse("https://" + str2 + str));
            intent.addFlags(603979776);
            startActivity(intent);
            finish();
        }

        private void loadIdentityAvatar(ForumIdentity.Snapshot snapshot) {
            if (this.identityAvatar == null) {
                return;
            }
            if (snapshot == null || !snapshot.loggedIn || snapshot.avatarUrl == null || snapshot.avatarUrl.trim().isEmpty()) {
                this.identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int dp = dp(3);
                this.identityAvatar.setPadding(dp, dp, dp, dp);
                this.identityAvatar.setImageResource(R.drawable.htg_app_logo);
                this.identityAvatarRequestedUrl = "";
                this.identityAvatarLoadedUrl = "";
                this.identityAvatarBitmap = null;
                return;
            }
            final String trim = snapshot.avatarUrl.trim();
            try {
                Uri parse = Uri.parse(trim);
                if ("https".equalsIgnoreCase(parse.getScheme()) && ForumUrlRouter.isForumHost(parse.getHost())) {
                    if (trim.equals(this.identityAvatarLoadedUrl) && this.identityAvatarBitmap != null) {
                        this.identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        int dp2 = dp(2);
                        this.identityAvatar.setPadding(dp2, dp2, dp2, dp2);
                        this.identityAvatar.setImageBitmap(this.identityAvatarBitmap);
                        this.identityAvatar.setTag(trim);
                        return;
                    }
                    if (trim.equals(this.identityAvatarRequestedUrl)) {
                        return;
                    }
                    this.identityAvatarRequestedUrl = trim;
                    this.identityAvatar.setTag(trim);
                    AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda4
                        @Override // java.lang.Runnable
                        public final void run() {
                            IdentityActivity.this.m14x27753261(trim);
                        }
                    });
                }
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$loadIdentityAvatar$7$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m14x27753261(final String str) {
            HttpsURLConnection httpsURLConnection = null;
            HttpsURLConnection httpsURLConnection2 = null;
            try {
                httpsURLConnection = (HttpsURLConnection) new URL(str).openConnection();
            } catch (Throwable unused) {
            }
            try {
                httpsURLConnection.setConnectTimeout(6000);
                httpsURLConnection.setReadTimeout(6000);
                httpsURLConnection.setUseCaches(true);
                httpsURLConnection.setInstanceFollowRedirects(false);
                httpsURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0");
                if (httpsURLConnection.getResponseCode() != 200) {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                        return;
                    }
                    return;
                }
                final Bitmap decodeStream = BitmapFactory.decodeStream(httpsURLConnection.getInputStream());
                if (decodeStream == null) {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                } else {
                    runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda6
                        @Override // java.lang.Runnable
                        public final void run() {
                            IdentityActivity.this.m12xe34dc5f(str, decodeStream);
                        }
                    });
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (Throwable unused2) {
                httpsURLConnection2 = httpsURLConnection;
                try {
                    runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.IdentityActivity$$ExternalSyntheticLambda7
                        @Override // java.lang.Runnable
                        public final void run() {
                            IdentityActivity.this.m13x9ad50760(str);
                        }
                    });
                } finally {
                    if (httpsURLConnection2 != null) {
                        httpsURLConnection2.disconnect();
                    }
                }
            }
        }

        /* renamed from: lambda$loadIdentityAvatar$5$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m12xe34dc5f(String str, Bitmap bitmap) {
            ImageView imageView = this.identityAvatar;
            if (imageView == null || !str.equals(imageView.getTag())) {
                return;
            }
            this.identityAvatarBitmap = bitmap;
            this.identityAvatarLoadedUrl = str;
            this.identityAvatarRequestedUrl = "";
            this.identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int dp = dp(2);
            this.identityAvatar.setPadding(dp, dp, dp, dp);
            this.identityAvatar.setImageBitmap(bitmap);
            this.identityAvatar.setContentDescription("Current forum identity avatar");
        }

        /* renamed from: lambda$loadIdentityAvatar$6$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
        /* synthetic */ void m13x9ad50760(String str) {
            if (str.equals(this.identityAvatarRequestedUrl)) {
                this.identityAvatarRequestedUrl = "";
            }
        }

        private LinearLayout card() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundResource(R.drawable.card_background);
            int dp = dp(compact() ? 12 : 16);
            linearLayout.setPadding(dp, dp, dp, dp);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(compact() ? 8 : 12);
            linearLayout.setLayoutParams(layoutParams);
            return linearLayout;
        }

        private View sectionTitle(String str, String str2) {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            TextView text = text(str, 14, getColor(R.color.hcf_text));
            text.setTypeface(null, 1);
            linearLayout.addView(text);
            if (str2 != null && !str2.isEmpty()) {
                linearLayout.addView(text(str2, 10, getColor(R.color.hcf_muted)));
            }
            linearLayout.setPadding(0, 0, 0, dp(9));
            return linearLayout;
        }

        private void addRow(LinearLayout linearLayout, String str, String str2) {
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            linearLayout2.setPadding(0, dp(6), 0, dp(6));
            TextView text = text(str, 10, getColor(R.color.hcf_muted));
            text.setTypeface(null, 1);
            linearLayout2.addView(text);
            if (str2 == null || str2.trim().isEmpty()) {
                str2 = "—";
            }
            TextView text2 = text(str2, 12, getColor(R.color.hcf_text));
            text2.setTextIsSelectable(true);
            linearLayout2.addView(text2);
            linearLayout.addView(linearLayout2);
        }

        private String displayDate(String str) {
            return (str == null || str.trim().isEmpty()) ? "—" : str;
        }

        private View actionRow(String str, String str2, int i, View.OnClickListener onClickListener) {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(17);
            linearLayout.setBackgroundResource(R.drawable.button_background);
            linearLayout.setPadding(dp(14), 0, dp(14), 0);
            linearLayout.setClickable(true);
            linearLayout.setFocusable(true);
            linearLayout.setOnClickListener(onClickListener);
            linearLayout.setContentDescription(str);
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(i);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setContentDescription(null);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            layoutParams.rightMargin = dp(10);
            linearLayout.addView(imageView, layoutParams);
            TextView text = text(str, 14, getColor(R.color.hcf_cyan_bright));
            text.setTypeface(null, 1);
            text.setIncludeFontPadding(false);
            text.setGravity(16);
            text.setMaxLines(1);
            linearLayout.addView(text, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(compact() ? 46 : 50));
            layoutParams2.topMargin = dp(8);
            linearLayout.setLayoutParams(layoutParams2);
            return linearLayout;
        }

        private TextView text(String str, int i, int i2) {
            TextView textView = new TextView(this);
            if (str == null) {
                str = "";
            }
            textView.setText(str);
            textView.setTextSize(i);
            textView.setTextColor(i2);
            textView.setLineSpacing(0.0f, 1.08f);
            return textView;
        }

        private boolean compact() {
            return getResources().getConfiguration().screenHeightDp <= 720;
        }

        private int dp(int i) {
            return Math.round(i * getResources().getDisplayMetrics().density);
        }
    }

    // ---- LogsActivity.java ----
    /* loaded from: classes.dex */
    public static final class LogsActivity extends ThemedActivity {
        private static final int EXPORT_TEXT = 611;
        private static final String FILTER_ALL = "ALL";
        private static final String FILTER_CRASH = "CRASH";
        private static final String FILTER_ERROR = "ERROR";
        private static final String FILTER_INFO = "INFO";
        private static final String FILTER_NETWORK = "NETWORK";
        private static final String FILTER_WARN = "WARN";
        private static final String FILTER_WEBVIEW = "WEBVIEW";
        private static final Pattern LOG_PATTERN = Pattern.compile("^(\\S+) \\[(INFO|WARN|ERROR|CRASH)] ([^|]+?)(?: \\| (.*))?$");
        private static final int MAX_DISPLAY_CHARS = 180000;
        private static final String MODE_DIAGNOSTICS = "diagnostics";
        private static final String MODE_LOGS = "logs";
        private LinearLayout chipRow;
        private Button clearButton;
        private ScrollView contentScroll;
        private TextView contentText;
        private Button diagnosticsTab;
        private LinearLayout logsControls;
        private Button logsTab;
        private Uri pendingExportUri;
        private EditText searchInput;
        private TextView statusLine;
        private TextView viewerMeta;
        private TextView viewerSubtitle;
        private TextView viewerTitle;
        private String currentMode = MODE_LOGS;
        private String activeFilter = FILTER_ALL;
        private boolean groupRepeats = true;
        private String rawLogs = "";
        private String visiblePlainText = "";

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            try {
                ThemeManager.apply(this);
                int color = ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg);
                getWindow().setStatusBarColor(color);
                getWindow().setNavigationBarColor(color);
                setContentView(buildView());
                AppLogger.info(this, "logs_open", "system-ui-v3");
                refreshData();
            } catch (Throwable th) {
                try {
                    AppLogger.error(this, "logs_screen_recovery", th.getClass().getSimpleName());
                } catch (Throwable unused) {
                }
                setContentView(buildRecoveryView(th));
            }
        }

        private View buildView() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            linearLayout.addView(buildHeader());
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            linearLayout2.setPadding(dp(14), dp(12), dp(14), dp(14));
            linearLayout2.addView(buildTabs(), new LinearLayout.LayoutParams(-1, dp(44)));
            linearLayout2.addView(buildStatusCard(), marginParams(-1, -2, 0, 10, 0, 0));
            linearLayout2.addView(buildActionRow(), marginParams(-1, dp(44), 0, 10, 0, 0));
            LinearLayout buildLogsControls = buildLogsControls();
            this.logsControls = buildLogsControls;
            linearLayout2.addView(buildLogsControls, marginParams(-1, -2, 0, 10, 0, 0));
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            if (ThemeManager.isAmoled(this)) {
                linearLayout3.setBackgroundColor(Color.rgb(3, 5, 7));
            } else {
                linearLayout3.setBackgroundResource(R.drawable.card_background);
            }
            linearLayout3.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout linearLayout4 = new LinearLayout(this);
            linearLayout4.setOrientation(0);
            linearLayout4.setGravity(16);
            TextView label = label("App Logs", 16.0f, R.color.hcf_accent_text, true);
            this.viewerTitle = label;
            linearLayout4.addView(label, new LinearLayout.LayoutParams(0, -2, 1.0f));
            TextView label2 = label("", 10.0f, R.color.hcf_muted, false);
            this.viewerMeta = label2;
            label2.setGravity(8388613);
            linearLayout4.addView(this.viewerMeta, new LinearLayout.LayoutParams(-2, -2));
            linearLayout3.addView(linearLayout4);
            TextView label3 = label("Local troubleshooting history from this app", 11.0f, R.color.hcf_muted, false);
            this.viewerSubtitle = label3;
            label3.setPadding(0, dp(2), 0, dp(8));
            linearLayout3.addView(this.viewerSubtitle);
            View view = new View(this);
            view.setBackgroundColor(getColor(R.color.hcf_divider));
            linearLayout3.addView(view, new LinearLayout.LayoutParams(-1, dp(1)));
            ScrollView scrollView = new ScrollView(this);
            this.contentScroll = scrollView;
            scrollView.setFillViewport(true);
            this.contentScroll.setClipToPadding(false);
            TextView textView = new TextView(this);
            this.contentText = textView;
            textView.setTextColor(getColor(R.color.hcf_text));
            this.contentText.setTextSize(10.5f);
            this.contentText.setTextIsSelectable(true);
            this.contentText.setTypeface(Typeface.MONOSPACE);
            this.contentText.setIncludeFontPadding(false);
            this.contentText.setLineSpacing(dp(1), 1.05f);
            this.contentText.setPadding(0, dp(10), 0, dp(8));
            this.contentText.setHorizontallyScrolling(false);
            this.contentText.setBreakStrategy(0);
            this.contentText.setHyphenationFrequency(0);
            this.contentScroll.addView(this.contentText, new FrameLayout.LayoutParams(-1, -2));
            linearLayout3.addView(this.contentScroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            linearLayout2.addView(linearLayout3, marginParams(-1, 0, 1.0f, 0, 10, 0, 0));
            linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            return linearLayout;
        }

        private View buildHeader() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            linearLayout.setPadding(dp(8), dp(5), dp(8), dp(5));
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
            linearLayout.setMinimumHeight(dp(56));
            ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back to App Settings");
            iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda7
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m26lambda$buildHeader$0$comharleytgforumdevLogsActivity(view);
                }
            });
            linearLayout.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.htg_app_logo);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            layoutParams.leftMargin = dp(4);
            linearLayout.addView(imageView, layoutParams);
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.leftMargin = dp(10);
            linearLayout.addView(linearLayout2, layoutParams2);
            linearLayout2.addView(label("Logs & Diagnostics", 18.0f, R.color.hcf_text, true));
            TextView label = label("v1.0 • Local troubleshooting", 10.0f, R.color.hcf_meta, true);
            label.setPadding(0, dp(2), 0, 0);
            linearLayout2.addView(label);
            return linearLayout;
        }

        /* renamed from: lambda$buildHeader$0$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m26lambda$buildHeader$0$comharleytgforumdevLogsActivity(View view) {
            finish();
        }

        private View buildTabs() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            this.logsTab = segmentButton("Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m29lambda$buildTabs$1$comharleytgforumdevLogsActivity(view);
                }
            });
            this.diagnosticsTab = segmentButton("Diagnostics", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m30lambda$buildTabs$2$comharleytgforumdevLogsActivity(view);
                }
            });
            linearLayout.addView(this.logsTab, weightedTabParams(false));
            linearLayout.addView(this.diagnosticsTab, weightedTabParams(true));
            updateTabStyles();
            return linearLayout;
        }

        /* renamed from: lambda$buildTabs$1$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m29lambda$buildTabs$1$comharleytgforumdevLogsActivity(View view) {
            switchMode(MODE_LOGS);
        }

        /* renamed from: lambda$buildTabs$2$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m30lambda$buildTabs$2$comharleytgforumdevLogsActivity(View view) {
            switchMode(MODE_DIAGNOSTICS);
        }

        private LinearLayout.LayoutParams weightedTabParams(boolean z) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
            if (z) {
                layoutParams.leftMargin = dp(8);
            }
            return layoutParams;
        }

        private View buildStatusCard() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            if (ThemeManager.isAmoled(this)) {
                linearLayout.setBackgroundColor(Color.rgb(3, 5, 7));
            } else {
                linearLayout.setBackgroundResource(R.drawable.card_background);
            }
            linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
            linearLayout.addView(label("Device status", 16.0f, R.color.hcf_accent_text, true));
            TextView label = label("Current app and forum health at a glance", 11.0f, R.color.hcf_muted, false);
            label.setPadding(0, dp(2), 0, dp(7));
            linearLayout.addView(label);
            TextView label2 = label("Checking app status…", 11.0f, R.color.hcf_text, false);
            this.statusLine = label2;
            label2.setLineSpacing(0.0f, 1.12f);
            linearLayout.addView(this.statusLine);
            return linearLayout;
        }

        private View buildActionRow() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.addView(actionButton("Refresh", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda9
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m22lambda$buildActionRow$3$comharleytgforumdevLogsActivity(view);
                }
            }), weightedActionParams(0, 0));
            linearLayout.addView(actionButton("Copy", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda10
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m23lambda$buildActionRow$4$comharleytgforumdevLogsActivity(view);
                }
            }), weightedActionParams(6, 0));
            linearLayout.addView(actionButton("Export", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda11
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m24lambda$buildActionRow$5$comharleytgforumdevLogsActivity(view);
                }
            }), weightedActionParams(6, 0));
            Button actionButton = actionButton("Clear", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda12
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m25lambda$buildActionRow$6$comharleytgforumdevLogsActivity(view);
                }
            });
            this.clearButton = actionButton;
            linearLayout.addView(actionButton, weightedActionParams(6, 0));
            return linearLayout;
        }

        /* renamed from: lambda$buildActionRow$3$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m22lambda$buildActionRow$3$comharleytgforumdevLogsActivity(View view) {
            refreshData();
        }

        /* renamed from: lambda$buildActionRow$4$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m23lambda$buildActionRow$4$comharleytgforumdevLogsActivity(View view) {
            copyVisible();
        }

        /* renamed from: lambda$buildActionRow$5$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m24lambda$buildActionRow$5$comharleytgforumdevLogsActivity(View view) {
            exportVisible();
        }

        /* renamed from: lambda$buildActionRow$6$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m25lambda$buildActionRow$6$comharleytgforumdevLogsActivity(View view) {
            confirmClearLogs();
        }

        private LinearLayout buildLogsControls() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            EditText editText = new EditText(this);
            this.searchInput = editText;
            editText.setSingleLine(true);
            this.searchInput.setTextColor(getColor(R.color.hcf_text));
            this.searchInput.setHintTextColor(getColor(R.color.hcf_hint));
            this.searchInput.setHint("Search app logs…");
            this.searchInput.setTextSize(13.0f);
            this.searchInput.setIncludeFontPadding(false);
            this.searchInput.setImeOptions(3);
            this.searchInput.setBackgroundResource(R.drawable.quick_action_background);
            this.searchInput.setPadding(dp(14), 0, dp(14), 0);
            linearLayout.addView(this.searchInput, new LinearLayout.LayoutParams(-1, dp(44)));
            this.searchInput.addTextChangedListener(new TextWatcher() { // from class: com.harleytg.forum.dev.LogsActivity.1
                @Override // android.text.TextWatcher
                public void afterTextChanged(Editable editable) {
                }

                @Override // android.text.TextWatcher
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                }

                @Override // android.text.TextWatcher
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    LogsActivity.this.renderLogs();
                }
            });
            TextView label = label("Filter logs", 10.0f, R.color.hcf_cyan, true);
            label.setPadding(dp(4), dp(8), 0, dp(6));
            linearLayout.addView(label);
            LinearLayout linearLayout2 = new LinearLayout(this);
            this.chipRow = linearLayout2;
            linearLayout2.setOrientation(1);
            this.chipRow.setClipChildren(false);
            this.chipRow.setClipToPadding(false);
            linearLayout.addView(this.chipRow, new LinearLayout.LayoutParams(-1, dp(70)));
            rebuildFilterChips();
            return linearLayout;
        }

        private Button segmentButton(String str, View.OnClickListener onClickListener) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(str);
            button.setTextSize(12.0f);
            button.setGravity(17);
            button.setPadding(dp(10), 0, dp(10), 0);
            button.setOnClickListener(onClickListener);
            return button;
        }

        private Button actionButton(String str, View.OnClickListener onClickListener) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(str);
            button.setTextSize(11.0f);
            button.setTextColor(getColor(R.color.hcf_accent_text));
            button.setTypeface(null, 1);
            button.setBackgroundResource(R.drawable.quick_action_background);
            button.setGravity(17);
            button.setPadding(dp(6), 0, dp(6), 0);
            button.setOnClickListener(onClickListener);
            return button;
        }

        private LinearLayout.LayoutParams weightedActionParams(int i, int i2) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
            layoutParams.leftMargin = dp(i);
            layoutParams.rightMargin = dp(i2);
            return layoutParams;
        }

        private void rebuildFilterChips() {
            LinearLayout linearLayout = this.chipRow;
            if (linearLayout == null) {
                return;
            }
            linearLayout.removeAllViews();
            LinearLayout filterChipLine = filterChipLine();
            addFilterChip(filterChipLine, "All", FILTER_ALL);
            addFilterChip(filterChipLine, "Info", FILTER_INFO);
            addFilterChip(filterChipLine, "Warning", FILTER_WARN);
            addFilterChip(filterChipLine, "Error", FILTER_ERROR);
            this.chipRow.addView(filterChipLine, new LinearLayout.LayoutParams(-1, dp(32)));
            LinearLayout filterChipLine2 = filterChipLine();
            addFilterChip(filterChipLine2, "Crash", FILTER_CRASH);
            addFilterChip(filterChipLine2, "WebView", FILTER_WEBVIEW);
            addFilterChip(filterChipLine2, "Network", FILTER_NETWORK);
            boolean z = this.groupRepeats;
            Button chipButton = chipButton(z ? "Group ✓" : "Group", z);
            chipButton.setContentDescription(this.groupRepeats ? "Grouping repeated events on" : "Grouping repeated events off");
            chipButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda8
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m32lambda$rebuildFilterChips$7$comharleytgforumdevLogsActivity(view);
                }
            });
            filterChipLine2.addView(chipButton, gridChipParams(filterChipLine2.getChildCount() > 0));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(32));
            layoutParams.topMargin = dp(6);
            this.chipRow.addView(filterChipLine2, layoutParams);
        }

        /* renamed from: lambda$rebuildFilterChips$7$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m32lambda$rebuildFilterChips$7$comharleytgforumdevLogsActivity(View view) {
            this.groupRepeats = !this.groupRepeats;
            rebuildFilterChips();
            renderLogs();
        }

        private LinearLayout filterChipLine() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(16);
            linearLayout.setClipChildren(false);
            linearLayout.setClipToPadding(false);
            return linearLayout;
        }

        private void addFilterChip(LinearLayout linearLayout, String str, final String str2) {
            Button chipButton = chipButton(str, str2.equals(this.activeFilter));
            chipButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda5
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m21lambda$addFilterChip$8$comharleytgforumdevLogsActivity(str2, view);
                }
            });
            linearLayout.addView(chipButton, gridChipParams(linearLayout.getChildCount() > 0));
        }

        /* renamed from: lambda$addFilterChip$8$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m21lambda$addFilterChip$8$comharleytgforumdevLogsActivity(String str, View view) {
            this.activeFilter = str;
            rebuildFilterChips();
            renderLogs();
        }

        private Button chipButton(String str, boolean z) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(str);
            button.setTextSize(10.2f);
            button.setSingleLine(true);
            button.setGravity(17);
            button.setTypeface(null, z ? 1 : 0);
            button.setTextColor(getColor(z ? R.color.hcf_accent_text : R.color.hcf_muted));
            button.setBackgroundResource(z ? R.drawable.status_chip_background : R.drawable.quick_action_background);
            button.setPadding(dp(5), 0, dp(5), 0);
            return button;
        }

        private LinearLayout.LayoutParams gridChipParams(boolean z) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
            if (z) {
                layoutParams.leftMargin = dp(6);
            }
            return layoutParams;
        }

        private void switchMode(String str) {
            View.OnClickListener onClickListener;
            String str2 = MODE_DIAGNOSTICS;
            if (!MODE_DIAGNOSTICS.equals(str)) {
                str2 = MODE_LOGS;
            }
            this.currentMode = str2;
            updateTabStyles();
            LinearLayout linearLayout = this.logsControls;
            if (linearLayout != null) {
                linearLayout.setVisibility(MODE_LOGS.equals(this.currentMode) ? 0 : 8);
            }
            Button button = this.clearButton;
            if (button != null) {
                button.setText(MODE_LOGS.equals(this.currentMode) ? "Clear" : "Logs");
                Button button2 = this.clearButton;
                if (MODE_LOGS.equals(this.currentMode)) {
                    onClickListener = new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda1
                        @Override // android.view.View.OnClickListener
                        public final void onClick(View view) {
                            LogsActivity.this.m36lambda$switchMode$9$comharleytgforumdevLogsActivity(view);
                        }
                    };
                } else {
                    onClickListener = new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda2
                        @Override // android.view.View.OnClickListener
                        public final void onClick(View view) {
                            LogsActivity.this.m35lambda$switchMode$10$comharleytgforumdevLogsActivity(view);
                        }
                    };
                }
                button2.setOnClickListener(onClickListener);
            }
            renderCurrentMode();
        }

        /* renamed from: lambda$switchMode$9$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m36lambda$switchMode$9$comharleytgforumdevLogsActivity(View view) {
            confirmClearLogs();
        }

        /* renamed from: lambda$switchMode$10$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m35lambda$switchMode$10$comharleytgforumdevLogsActivity(View view) {
            switchMode(MODE_LOGS);
        }

        private void updateTabStyles() {
            if (this.logsTab == null || this.diagnosticsTab == null) {
                return;
            }
            boolean equals = MODE_LOGS.equals(this.currentMode);
            styleSegment(this.logsTab, equals);
            styleSegment(this.diagnosticsTab, !equals);
        }

        private void styleSegment(Button button, boolean z) {
            button.setBackgroundResource(z ? R.drawable.status_chip_background : R.drawable.quick_action_background);
            button.setTextColor(getColor(z ? R.color.hcf_accent_text : R.color.hcf_muted));
            button.setTypeface(null, z ? 1 : 0);
        }

        private void refreshData() {
            this.rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
            updateStatusLine();
            renderCurrentMode();
        }

        private void renderCurrentMode() {
            if (MODE_DIAGNOSTICS.equals(this.currentMode)) {
                renderDiagnostics();
            } else {
                renderLogs();
            }
        }

        public void renderLogs() {
            if (contentText == null || !MODE_LOGS.equals(currentMode)) return;
            List<LogEntry> entries = parseLogs(rawLogs);
            String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.US);
            List<LogEntry> visible = new ArrayList<>();
            for (LogEntry entry : entries) {
                if (!matchesFilter(entry)) continue;
                if (!query.isEmpty() && !entry.searchable().contains(query)) continue;
                visible.add(entry);
            }

            if (groupRepeats) visible = groupEntries(visible);
            Collections.reverse(visible);
            SpannableStringBuilder styled = new SpannableStringBuilder();
            StringBuilder plain = new StringBuilder();
            int errors = 0;
            int warnings = 0;
            for (LogEntry entry : visible) {
                if ("ERROR".equals(entry.level) || "CRASH".equals(entry.level)) errors += entry.count;
                if ("WARN".equals(entry.level)) warnings += entry.count;
                appendStyledEntry(styled, plain, entry);
            }
            if (visible.isEmpty()) {
                String empty = query.isEmpty() && FILTER_ALL.equals(activeFilter)
                        ? "No app logs yet."
                        : "No log entries match the current filters.";
                styled.append(empty);
                plain.append(empty);
            }
            visiblePlainText = HcfSupportSanitizer.sanitize(plain.toString());
            contentText.setText(styled);
            viewerTitle.setText("App Logs");
            if (viewerSubtitle != null) viewerSubtitle.setText("Local troubleshooting history from this app");
            viewerMeta.setText(visible.size() + " shown" + (errors > 0 ? " • " + errors + " errors" : warnings > 0 ? " • " + warnings + " warnings" : ""));
            if (contentScroll != null) contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_UP));
        }

        /* renamed from: lambda$renderLogs$11$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m34lambda$renderLogs$11$comharleytgforumdevLogsActivity() {
            this.contentScroll.fullScroll(33);
        }

        private void renderDiagnostics() {
            if (this.contentText == null) {
                return;
            }
            String buildDiagnosticReport;
            try {
                buildDiagnosticReport = HcfSupportSanitizer.sanitize(
                        buildDiagnosticReport() + "\n\nNotification actions\n" + HcfNotificationActions.diagnosticSummary(this));
            } catch (Throwable error) {
                AppLogger.warn(this, "diagnostics_render", error.getClass().getSimpleName());
                buildDiagnosticReport = "Diagnostics temporarily unavailable.\nFailure category: "
                        + error.getClass().getSimpleName() + "\n\nNotification actions\n"
                        + HcfNotificationActions.diagnosticSummary(this);
                buildDiagnosticReport = HcfSupportSanitizer.sanitize(buildDiagnosticReport);
            }
            this.visiblePlainText = buildDiagnosticReport;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(buildDiagnosticReport);
            colorDiagnosticLabels(spannableStringBuilder);
            this.contentText.setText(spannableStringBuilder);
            this.viewerTitle.setText("Diagnostics");
            TextView textView = this.viewerSubtitle;
            if (textView != null) {
                textView.setText("Sanitized device and app information");
            }
            this.viewerMeta.setText("No cookies, tokens or passwords");
            ScrollView scrollView = this.contentScroll;
            if (scrollView != null) {
                scrollView.post(new Runnable() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda15
                    @Override // java.lang.Runnable
                    public final void run() {
                        LogsActivity.this.m33lambda$renderDiagnostics$12$comharleytgforumdevLogsActivity();
                    }
                });
            }
        }

        /* renamed from: lambda$renderDiagnostics$12$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m33lambda$renderDiagnostics$12$comharleytgforumdevLogsActivity() {
            this.contentScroll.fullScroll(33);
        }

        private List<LogEntry> parseLogs(String str) {
            ArrayList arrayList = new ArrayList();
            if (str != null && !str.trim().isEmpty() && !"No app logs yet.".equals(str.trim())) {
                String[] split = str.split("\\n");
                int length = split.length;
                for (int i = 0; i < length; i++) {
                    String str2 = split[i];
                    String trim = str2 == null ? "" : str2.trim();
                    if (!trim.isEmpty()) {
                        Matcher matcher = LOG_PATTERN.matcher(trim);
                        if (matcher.matches()) {
                            arrayList.add(new LogEntry(matcher.group(1), matcher.group(2), safe(matcher.group(3)).trim(), safe(matcher.group(4)).trim()));
                        } else if (!trim.startsWith("Older log entries omitted")) {
                            arrayList.add(new LogEntry("", FILTER_INFO, "log_message", trim));
                        }
                    }
                }
            }
            return arrayList;
        }

        private List<LogEntry> groupEntries(List<LogEntry> list) {
            LinkedHashMap linkedHashMap = new LinkedHashMap();
            for (LogEntry logEntry : list) {
                String str = logEntry.level + "\n" + logEntry.event + "\n" + logEntry.detail;
                LogEntry logEntry2 = (LogEntry) linkedHashMap.get(str);
                if (logEntry2 == null) {
                    linkedHashMap.put(str, logEntry.copy());
                } else {
                    logEntry2.count += logEntry.count;
                    logEntry2.lastTimestamp = logEntry.timestamp;
                    linkedHashMap.remove(str);
                    linkedHashMap.put(str, logEntry2);
                }
            }
            return new ArrayList(linkedHashMap.values());
        }

        private boolean matchesFilter(LogEntry logEntry) {
            if (logEntry == null) {
                return false;
            }
            if (FILTER_ALL.equals(this.activeFilter)) {
                return true;
            }
            if (FILTER_INFO.equals(this.activeFilter) || FILTER_WARN.equals(this.activeFilter) || FILTER_ERROR.equals(this.activeFilter) || FILTER_CRASH.equals(this.activeFilter)) {
                return this.activeFilter.equals(logEntry.level);
            }
            String lowerCase = (logEntry.event + " " + logEntry.detail).toLowerCase(Locale.US);
            if (FILTER_WEBVIEW.equals(this.activeFilter)) {
                return lowerCase.contains("webview") || lowerCase.contains("web_bridge") || lowerCase.contains("page_finished") || lowerCase.contains("renderer") || lowerCase.contains("javascript");
            }
            if (FILTER_NETWORK.equals(this.activeFilter)) {
                return lowerCase.contains("network") || lowerCase.contains("http") || lowerCase.contains("https") || lowerCase.contains("host") || lowerCase.contains("failover") || lowerCase.contains("ssl") || lowerCase.contains("download") || lowerCase.contains("online") || lowerCase.contains("offline");
            }
            return true;
        }

        private void appendStyledEntry(SpannableStringBuilder spannableStringBuilder, StringBuilder sb, LogEntry logEntry) {
            if (spannableStringBuilder.length() > 0) {
                spannableStringBuilder.append('\n');
                sb.append('\n');
            }
            String compactTimestamp = compactTimestamp(logEntry.timestamp);
            int length = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) compactTimestamp);
            spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), length, spannableStringBuilder.length(), 33);
            sb.append(compactTimestamp);
            spannableStringBuilder.append((CharSequence) "  ");
            sb.append("  ");
            String str = "[" + logEntry.level + "]";
            int length2 = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) str);
            spannableStringBuilder.setSpan(new ForegroundColorSpan(levelColor(logEntry.level)), length2, spannableStringBuilder.length(), 33);
            sb.append(str);
            spannableStringBuilder.append((CharSequence) "  ");
            sb.append("  ");
            int length3 = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) logEntry.event);
            spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_text)), length3, spannableStringBuilder.length(), 33);
            sb.append(logEntry.event);
            if (logEntry.count > 1) {
                String str2 = "  ×" + logEntry.count;
                int length4 = spannableStringBuilder.length();
                spannableStringBuilder.append((CharSequence) str2);
                spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), length4, spannableStringBuilder.length(), 33);
                sb.append(str2);
            }
            if (!logEntry.detail.isEmpty()) {
                spannableStringBuilder.append((CharSequence) "\n  ");
                sb.append("\n  ");
                int length5 = spannableStringBuilder.length();
                spannableStringBuilder.append((CharSequence) displayDetail(logEntry.detail));
                spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_detail)), length5, spannableStringBuilder.length(), 33);
                sb.append(logEntry.detail);
            }
            if (logEntry.count <= 1 || logEntry.lastTimestamp == null || logEntry.lastTimestamp.equals(logEntry.timestamp)) {
                return;
            }
            String str3 = "\n  latest " + compactTimestamp(logEntry.lastTimestamp);
            int length6 = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) str3);
            spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), length6, spannableStringBuilder.length(), 33);
            sb.append(str3);
        }

        private String displayDetail(String str) {
            if (str == null || str.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(str.length() + 24);
            for (int i = 0; i < str.length(); i++) {
                char charAt = str.charAt(i);
                sb.append(charAt);
                if (charAt == '/' || charAt == '?' || charAt == '&' || charAt == '=' || charAt == '-' || charAt == '_') {
                    sb.append((char) 8203);
                }
            }
            return sb.toString();
        }

        private int levelColor(String str) {
            if (FILTER_CRASH.equals(str) || FILTER_ERROR.equals(str)) {
                return getColor(R.color.hcf_error);
            }
            return FILTER_WARN.equals(str) ? getColor(R.color.hcf_warning) : getColor(R.color.hcf_info);
        }

        private String compactTimestamp(String str) {
            int i;
            if (str == null || str.isEmpty()) {
                return "--:--:--";
            }
            try {
                int indexOf = str.indexOf(84);
                if (indexOf >= 0 && str.length() >= (i = indexOf + 9)) {
                    return str.substring(indexOf + 1, i);
                }
            } catch (Throwable unused) {
            }
            return str.length() > 19 ? str.substring(0, 19) : str;
        }

        private void colorDiagnosticLabels(SpannableStringBuilder spannableStringBuilder) {
            String[] strArr = {"App:", "Package:", "Android:", "Device:", "Network:", "Forum host:", "Theme:", "Performance profile:", "Runtime reason:", "Notification mode:", "Notification poll:", "Live page poll:", "FCM:", "Battery Saver:", "API failures:", "Notifications:", "Live sync:", "Auto failover:", "Telemetry:", "WebView:", "Renderer recovery:", "Last count change:", "Last route:", "Privacy:"};
            String spannableStringBuilder2 = spannableStringBuilder.toString();
            for (int i = 0; i < 24; i++) {
                String str = strArr[i];
                int i2 = 0;
                while (true) {
                    int indexOf = spannableStringBuilder2.indexOf(str, i2);
                    if (indexOf < 0) {
                        break;
                    }
                    spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), indexOf, str.length() + indexOf, 33);
                    i2 = indexOf + str.length();
                }
            }
        }

        private String buildDiagnosticReport() {
            SharedPreferences sharedPreferences = getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("active_host", "forum.harleytg.com");
            String str = ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
            String string2 = sharedPreferences.getString("notification_last_sync_status", "Not synced yet");
            long j = sharedPreferences.getLong("notification_last_sync_latency_ms", 0L);
            String str2 = "Unknown";
            try {
                PackageInfo currentWebViewPackage = WebView.getCurrentWebViewPackage();
                if (currentWebViewPackage != null) {
                    str2 = currentWebViewPackage.packageName + " • " + currentWebViewPackage.versionName;
                }
            } catch (Throwable unused) {
            }
            String notificationPermissionLabel = notificationPermissionLabel();
            String str3 = "";
            String safeUrl = AppLogger.safeUrl(sharedPreferences.getString("last_recoverable_url", ""));
            if (safeUrl.trim().isEmpty()) {
                safeUrl = "Not recorded yet";
            }
            StringBuilder sb = new StringBuilder("Harley's Clan Forum • Sanitized Diagnostic Report\n\nApp: " + BuildInfo.installedVersionName() + "\nPackage: ");
            sb.append(getPackageName());
            sb.append("\nAndroid: SDK ");
            sb.append(Build.VERSION.SDK_INT);
            sb.append("\nDevice: ");
            sb.append(safe(Build.MANUFACTURER));
            sb.append(" ");
            sb.append(safe(Build.MODEL));
            sb.append("\nNetwork: ");
            sb.append(isNetworkAvailable() ? "Online" : "Offline");
            sb.append("\nForum host: ");
            sb.append(str);
            sb.append("\nTheme: ");
            sb.append(ThemeManager.label(this));
            sb.append("\nPerformance profile: ");
            sb.append(PerformanceProfile.settingLabel(this, sharedPreferences));
            sb.append("\nRuntime reason: ");
            sb.append(RuntimeDiagnostics.profileReason());
            sb.append("\nNotification mode: ");
            sb.append(RuntimeDiagnostics.notificationMode());
            sb.append("\nNotification poll: ");
            sb.append(formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs()));
            sb.append("\nLive page poll: ");
            sb.append(formatRuntimeInterval(RuntimeDiagnostics.livePollMs()));
            sb.append("\nFCM: ");
            sb.append(RuntimeDiagnostics.fcmState());
            sb.append("\nBattery Saver: ");
            sb.append(PerformanceProfile.isBatterySaver(this) ? "On" : "Off");
            sb.append("\nAPI failures: ");
            sb.append(RuntimeDiagnostics.failures());
            sb.append("\nNotifications: ");
            sb.append(notificationPermissionLabel);
            sb.append(" • ");
            sb.append(NotificationHelper.status(this));
            sb.append("\nLive sync: ");
            sb.append(safe(string2));
            if (j > 0) {
                str3 = " • " + j + " ms";
            }
            sb.append(str3);
            sb.append("\nAuto failover: ");
            sb.append(sharedPreferences.getBoolean("auto_failover", true) ? "On" : "Off");
            sb.append("\nTelemetry: ");
            sb.append(TelemetryService.status(this));
            sb.append("\nWebView: ");
            sb.append(str2);
            sb.append("\nRenderer recovery: Enabled (HCF-WV-001) • count ");
            sb.append(sharedPreferences.getInt("renderer_recovery_count", 0));
            sb.append("\nLast count change: ");
            sb.append(formatDiagnosticAge(sharedPreferences.getLong("notification_last_count_change_at", 0L)));
            sb.append("\nLast route: ");
            sb.append(safeUrl);
            sb.append("\nPrivacy: Cookies, session tokens, passwords and email are not included.");
            return sb.toString();
        }

        private String formatRuntimeInterval(long j) {
            if (j <= 0) {
                return "idle";
            }
            if (j < 1000) {
                return j + " ms";
            }
            if (j % 1000 != 0) {
                return String.format(Locale.US, "%.2f s", Double.valueOf(j / 1000.0d));
            }
            return (j / 1000) + " s";
        }

        private String formatDiagnosticAge(long j) {
            if (j <= 0) {
                return "not recorded yet";
            }
            long max = Math.max(0L, (System.currentTimeMillis() - j) / 1000);
            if (max < 60) {
                return max + " s ago";
            }
            long j2 = max / 60;
            if (j2 < 60) {
                return j2 + " min ago";
            }
            return (j2 / 60) + " h ago";
        }

        private String notificationPermissionLabel() {
            if (Build.VERSION.SDK_INT < 33) {
                return "System permission not required";
            }
            try {
                return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0 ? "Permission granted" : "Permission denied";
            } catch (Throwable unused) {
                return "Permission status unavailable";
            }
        }

        private void updateStatusLine() {
            if (this.statusLine == null) {
                return;
            }
            String string = getSharedPreferences("hcf_app", 0).getString("active_host", "forum.harleytg.com");
            if (!ForumUrlRouter.isForumHost(string)) {
                string = "forum.harleytg.com";
            }
            String str = "forum.harleytg.com".equalsIgnoreCase(string) ? "Primary" : "Backup";
            LogCounts countLogs = countLogs(this.rawLogs);
            TextView textView = this.statusLine;
            StringBuilder sb = new StringBuilder();
            sb.append(isNetworkAvailable() ? "Online" : "Offline");
            sb.append("  •  ");
            sb.append(str);
            sb.append(" forum  •  ");
            sb.append(countLogs.total);
            sb.append(" entries  •  ");
            sb.append(countLogs.errors);
            sb.append(" errors  •  ");
            sb.append(countLogs.warnings);
            sb.append(" warnings");
            textView.setText(sb.toString());
        }

        private LogCounts countLogs(String str) {
            LogCounts logCounts = new LogCounts();
            for (LogEntry logEntry : parseLogs(str)) {
                logCounts.total++;
                if (FILTER_WARN.equals(logEntry.level)) {
                    logCounts.warnings++;
                }
                if (FILTER_ERROR.equals(logEntry.level) || FILTER_CRASH.equals(logEntry.level)) {
                    logCounts.errors++;
                }
            }
            return logCounts;
        }

        private boolean isNetworkAvailable() {
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

        private void copyVisible() {
            String str = this.visiblePlainText;
            String trim = HcfSupportSanitizer.sanitize(str == null ? "" : str).trim();
            if (trim.isEmpty()) {
                Toast.makeText(this, "Nothing to copy.", 0).show();
                return;
            }
            try {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
                if (clipboardManager == null) {
                    throw new IllegalStateException("Clipboard unavailable");
                }
                clipboardManager.setPrimaryClip(ClipData.newPlainText(MODE_DIAGNOSTICS.equals(this.currentMode) ? "HCF diagnostic report" : "HCF app logs", trim));
                Toast.makeText(this, MODE_DIAGNOSTICS.equals(this.currentMode) ? "Diagnostic report copied." : "Visible logs copied.", 0).show();
            } catch (Throwable unused) {
                Toast.makeText(this, "Could not copy this content.", 0).show();
            }
        }

        private void confirmClearLogs() {
            new AlertDialog.Builder(this).setTitle("Clear App Logs?").setMessage("This removes only the local diagnostic log files. Forum cookies, account sessions and settings are not affected.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Clear Logs", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda6
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    LogsActivity.this.m31lambda$confirmClearLogs$13$comharleytgforumdevLogsActivity(dialogInterface, i);
                }
            }).show();
        }

        /* renamed from: lambda$confirmClearLogs$13$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m31lambda$confirmClearLogs$13$comharleytgforumdevLogsActivity(DialogInterface dialogInterface, int i) {
            clearLogs();
        }

        private void clearLogs() {
            try {
                AppLogger.clear(this);
                AppLogger.info(this, "logs_cleared", "manual");
                this.rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
                updateStatusLine();
                renderCurrentMode();
                Toast.makeText(this, "Local logs cleared.", 0).show();
            } catch (Throwable unused) {
                Toast.makeText(this, "Could not clear local logs.", 0).show();
            }
        }

        private void exportVisible() {
            String str;
            try {
                Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
                intent.addCategory("android.intent.category.OPENABLE");
                intent.setType("text/plain");
                if (MODE_DIAGNOSTICS.equals(this.currentMode)) {
                    str = "harleys-clan-forum-diagnostics.txt";
                } else {
                    str = "harleys-clan-forum-app-log.txt";
                }
                intent.putExtra("android.intent.extra.TITLE", str);
                startActivityForResult(intent, EXPORT_TEXT);
            } catch (Throwable th) {
                Toast.makeText(this, "No compatible document provider is available.", 1).show();
                try {
                    AppLogger.error(this, "logs_export_picker_failed", th.getClass().getSimpleName());
                } catch (Throwable unused) {
                }
            }
        }

         // android.app.Activity

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != EXPORT_TEXT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
            pendingExportUri = data.getData();
            String text = HcfSupportSanitizer.sanitize(visiblePlainText == null ? "" : visiblePlainText);
            try (OutputStream out = getContentResolver().openOutputStream(pendingExportUri, "w")) {
                if (out == null) throw new IllegalStateException("No output stream");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                AppLogger.info(this, MODE_DIAGNOSTICS.equals(currentMode) ? "diagnostics_exported" : "logs_exported", "document-provider");
                Toast.makeText(this, "Export complete.", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                AppLogger.error(this, "logs_export_failed", t.getClass().getSimpleName());
                Toast.makeText(this, "Could not export this content.", Toast.LENGTH_LONG).show();
            } finally {
                pendingExportUri = null;
            }
        }

        private View buildRecoveryView(Throwable th) {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setGravity(17);
            linearLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            if (ThemeManager.isAmoled(this)) {
                linearLayout2.setBackgroundColor(Color.rgb(3, 5, 7));
            } else {
                linearLayout2.setBackgroundResource(R.drawable.card_background);
            }
            linearLayout2.setPadding(dp(18), dp(18), dp(18), dp(18));
            TextView label = label("Logs & Diagnostics Recovery", 18.0f, R.color.hcf_accent_text, true);
            label.setGravity(17);
            linearLayout2.addView(label);
            StringBuilder sb = new StringBuilder("The diagnostics viewer recovered safely from ");
            sb.append(th == null ? "an unexpected problem" : th.getClass().getSimpleName());
            sb.append(". You can clear the local log files or return to App Settings.");
            TextView label2 = label(sb.toString(), 12.0f, R.color.hcf_text, false);
            label2.setGravity(17);
            label2.setPadding(0, dp(10), 0, dp(12));
            linearLayout2.addView(label2);
            linearLayout2.addView(actionButton("Clear Local Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda13
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m27lambda$buildRecoveryView$14$comharleytgforumdevLogsActivity(view);
                }
            }), new LinearLayout.LayoutParams(-1, dp(48)));
            Button actionButton = actionButton("Back to App Settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda14
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LogsActivity.this.m28lambda$buildRecoveryView$15$comharleytgforumdevLogsActivity(view);
                }
            });
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(48));
            layoutParams.topMargin = dp(8);
            linearLayout2.addView(actionButton, layoutParams);
            linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
            return linearLayout;
        }

        /* renamed from: lambda$buildRecoveryView$14$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m27lambda$buildRecoveryView$14$comharleytgforumdevLogsActivity(View view) {
            try {
                AppLogger.clear(this);
            } catch (Throwable unused) {
            }
            Toast.makeText(this, "Local logs cleared.", 0).show();
            recreate();
        }

        /* renamed from: lambda$buildRecoveryView$15$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
        /* synthetic */ void m28lambda$buildRecoveryView$15$comharleytgforumdevLogsActivity(View view) {
            finish();
        }

        private TextView label(String str, float f, int i, boolean z) {
            TextView textView = new TextView(this);
            textView.setText(str);
            textView.setTextSize(f);
            textView.setTextColor(getColor(i));
            textView.setIncludeFontPadding(false);
            if (z) {
                textView.setTypeface(null, 1);
            }
            return textView;
        }

        private LinearLayout.LayoutParams marginParams(int i, int i2, int i3, int i4, int i5, int i6) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, i2);
            layoutParams.setMargins(dp(i3), dp(i4), dp(i5), dp(i6));
            return layoutParams;
        }

        private LinearLayout.LayoutParams marginParams(int i, int i2, float f, int i3, int i4, int i5, int i6) {
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, i2, f);
            layoutParams.setMargins(dp(i3), dp(i4), dp(i5), dp(i6));
            return layoutParams;
        }

        private String safe(String str) {
            return str == null ? "" : str;
        }

        private int dp(int i) {
            return Math.round(i * getResources().getDisplayMetrics().density);
        }

        private static final class LogEntry {
            int count = 1;
            final String detail;
            final String event;
            String lastTimestamp;
            final String level;
            final String timestamp;

            LogEntry(String str, String str2, String str3, String str4) {
                str = str == null ? "" : str;
                this.timestamp = str;
                this.level = str2 == null ? LogsActivity.FILTER_INFO : str2;
                this.event = str3 == null ? "event" : str3;
                this.detail = str4 == null ? "" : str4;
                this.lastTimestamp = str;
            }

            LogEntry copy() {
                LogEntry logEntry = new LogEntry(this.timestamp, this.level, this.event, this.detail);
                logEntry.count = this.count;
                logEntry.lastTimestamp = this.lastTimestamp;
                return logEntry;
            }

            String searchable() {
                return (this.timestamp + " " + this.level + " " + this.event + " " + this.detail).toLowerCase(Locale.US);
            }
        }

        private static final class LogCounts {
            int errors;
            int total;
            int warnings;

            private LogCounts() {
            }
        }
    }

    // ---- MediaViewerActivity.java ----
    /* loaded from: classes.dex */
    public static final class MediaViewerActivity extends ThemedActivity {
        static final String EXTRA_KIND = "media_kind";
        static final String EXTRA_URL = "media_url";
        private String mediaUrl = "";

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            ThemeManager.apply(this);
            getWindow().setFlags(1024, 1024);
            String safeHttps = getIntent() == null ? "" : safeHttps(getIntent().getStringExtra(EXTRA_URL));
            this.mediaUrl = safeHttps;
            if (safeHttps.isEmpty()) {
                Toast.makeText(this, "This media link could not be opened safely.", 1).show();
                finish();
            } else {
                setContentView(buildUi());
                AppLogger.info(this, "media_viewer_open", AppLogger.safeUrl(this.mediaUrl));
            }
        }

        private LinearLayout buildUi() {
            String str;
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setGravity(16);
            linearLayout2.setPadding(dp(6), dp(4), dp(6), dp(4));
            linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
            ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, 0, 12, "Close media viewer");
            iconButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { MediaViewerActivity.this.m125lambda$buildUi$0$comharleytgforumdevMediaViewerActivity(view); }
            });
            linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
            TextView textView = new TextView(this);
            textView.setText("Media Viewer");
            textView.setTextColor(getColor(R.color.hcf_text));
            textView.setTextSize(14.0f);
            textView.setTypeface(null, 1);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.leftMargin = dp(8);
            linearLayout2.addView(textView, layoutParams);
            Button button = button("Share");
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { MediaViewerActivity.this.m126lambda$buildUi$1$comharleytgforumdevMediaViewerActivity(view); }
            });
            linearLayout2.addView(button, new LinearLayout.LayoutParams(dp(72), dp(46)));
            Button button2 = button("Open");
            button2.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { MediaViewerActivity.this.m127lambda$buildUi$2$comharleytgforumdevMediaViewerActivity(view); }
            });
            linearLayout2.addView(button2, new LinearLayout.LayoutParams(dp(72), dp(46)));
            linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, dp(54)));
            WebView webView = new WebView(this);
            webView.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(false);
            settings.setDomStorageEnabled(false);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(1);
            CookieManager.getInstance().setAcceptCookie(true);
            webView.setWebViewClient(new WebViewClient());
            String valueOf = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra(EXTRA_KIND));
            String html = html(this.mediaUrl);
            if (valueOf.toLowerCase(Locale.US).contains("video")) {
                str = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes'><style>html,body{margin:0;background:#000;width:100%;height:100%;display:flex;align-items:center;justify-content:center}video{width:100%;max-height:100vh;background:#000}</style></head><body><video controls autoplay playsinline src='" + html + "'></video></body></html>";
            } else {
                str = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes'><style>html,body{margin:0;background:#000;min-height:100%;display:flex;align-items:center;justify-content:center}img{max-width:100%;height:auto;object-fit:contain}</style></head><body><img src='" + html + "' alt='Forum media'></body></html>";
            }
            webView.loadDataWithBaseURL("https://" + Uri.parse(this.mediaUrl).getHost() + "/", str, "text/html", "UTF-8", null);
            linearLayout.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            return linearLayout;
        }

        /* synthetic */ void m125lambda$buildUi$0$comharleytgforumdevMediaViewerActivity(View view) { finish(); }
        /* synthetic */ void m126lambda$buildUi$1$comharleytgforumdevMediaViewerActivity(View view) { share(); }
        /* synthetic */ void m127lambda$buildUi$2$comharleytgforumdevMediaViewerActivity(View view) { openExternal(); }

        private Button button(String str) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(str != null ? str.replaceFirst("^[^A-Za-z0-9]+", "").trim() : "");
            button.setTextColor(getColor(R.color.hcf_cyan_bright));
            button.setTextSize(11.0f);
            button.setBackgroundColor(0);
            FaIcons.applyStart(button, str);
            return button;
        }

        private void share() {
            try {
                Intent intent = new Intent("android.intent.action.SEND");
                intent.setType("text/plain");
                intent.putExtra("android.intent.extra.TEXT", this.mediaUrl);
                HcfIntentChooser.showShare(this, intent, "Share forum media", "Copy the link or share it with another app.");
            } catch (Throwable unused) {
                Toast.makeText(this, "Unable to share this media.", 0).show();
            }
        }

        private void openExternal() {
            try {
                startActivity(new Intent("android.intent.action.VIEW", Uri.parse(this.mediaUrl)));
            } catch (ActivityNotFoundException unused) {
                Toast.makeText(this, "No app can open this media.", 0).show();
            }
        }

        private static String safeHttps(String str) {
            String trim = "";
            if (str != null) {
                try { trim = str.trim(); }
                catch (Throwable unused) { trim = ""; }
            }
            Uri parse = Uri.parse(trim);
            if ("https".equalsIgnoreCase(parse.getScheme()) && parse.getHost() != null && !parse.getHost().trim().isEmpty()) {
                return parse.toString();
            }
            return "";
        }

        private static String html(String str) {
            return str.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private int dp(int i) { return Math.round(i * getResources().getDisplayMetrics().density); }
    }

    // ---- SettingsActivity.java ----
    /**
     * Harley's Clan Forum settings center.
     *
     * v10000080 cleanup goals:
     * - one owner for each Advanced & About setting;
     * - version/build text comes from the installed package with BuildInfo fallback;
     * - search indexes individual settings and navigates to a stable target key.
     */
    public static final class SettingsActivity extends ThemedActivity {
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
        private TextView hostHealthStatus;
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

        private boolean handleSettingsBack() {
            if (currentSettingsSection != null && !currentSettingsSection.isEmpty()) {
                showSettingsHome();
                return true; // stayed inside Settings
            }
            return false; // caller should leave Settings
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onBackPressed() {
            if (handleSettingsBack()) return;
            super.onBackPressed();
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
                if (!handleSettingsBack()) finish();
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
            addSettingsCategory(list, "Home-screen Widget", "Theme, identity, content, layout and actions", "widget");
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
                new SettingTarget("notification_history_privacy", "Notification history privacy", "notification history local privacy off titles message retention", "notifications", "notification_history"),
                new SettingTarget("notification_history_retention", "Notification history retention", "notification history keep 10 30 60 events local", "notifications", "notification_history"),
                new SettingTarget("open_notification_history", "Open Notification History", "notification history recent local events", "notifications", "notification_history"),
                new SettingTarget("silence_hcf_silent_alerts", "Silence HCF Silent Alerts", "silent service status background notification scheduled jobs", "notifications", "silent_alerts"),
                new SettingTarget("open_developer_tools", "Open Developer Tools", "notification test developer", "notifications", "test_alerts"),
                new SettingTarget("theme", "Theme", "forum auto phone auto dark light appearance", "appearance", "appearance_performance"),
                new SettingTarget("performance_profile", "Performance Profile", "performance balanced quality animation motion", "appearance", "appearance_performance"),
                new SettingTarget("live_forum_updates", "Live forum updates", "live forum update refresh", "appearance", "appearance_performance"),
                new SettingTarget("show_secure_url_bar", "Show secure URL bar", "url address security header", "appearance", "appearance_performance"),
                new SettingTarget("show_startup_screen", "Show startup connection screen", "startup launch connection screen", "appearance", "appearance_performance"),
                new SettingTarget("verbose_startup_loader", "Verbose startup loader", "startup loading detailed checks progress completed verbose compact", "appearance", "appearance_performance"),
                new SettingTarget("widget_follow_app_theme", "Follow HCF app theme", "widget home screen theme app phone system light dark amoled", "widget", "widget_appearance"),
                new SettingTarget("widget_show_connected_username", "Show connected @username", "widget account identity connected username handle profile", "widget", "widget_appearance"),
                new SettingTarget("widget_show_unread_count", "Show unread count", "widget unread notifications count status", "widget", "widget_appearance"),
                new SettingTarget("widget_compact_mode", "Compact widget mode", "widget compact small logo title layout", "widget", "widget_appearance"),
                new SettingTarget("widget_show_last_updated", "Show last updated time", "widget refresh updated timestamp time", "widget", "widget_appearance"),
                new SettingTarget("widget_default_tap_action", "Default widget tap", "widget tap open forum notifications settings action", "widget", "widget_appearance"),
                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),
                new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),
                new SettingTarget("host_health_status", "Host Health", "primary backup server online offline latency health active host", "forum_data", "host_health"),
                new SettingTarget("test_host_health", "Test Both Forum Hosts", "primary backup server test latency health", "forum_data", "host_health"),
                new SettingTarget("use_primary_host", "Use Primary Forum", "manual switch primary host forum.harleytg.com", "forum_data", "host_health"),
                new SettingTarget("use_backup_host", "Use Backup Forum", "manual switch backup host freeflarum", "forum_data", "host_health"),
                new SettingTarget("external_links", "Allow external links to open in browser/apps", "links browser external apps", "forum_data", "connection_routing"),
                new SettingTarget("retry_primary", "Retry Primary Forum on Next Open", "primary server retry routing", "forum_data", "connection_routing"),
                new SettingTarget("forum_link_settings", "Open Forum Link Settings", "android links domains primary backup", "forum_data", "connection_routing"),
                new SettingTarget("cookie_manager", "Open Cookie Manager", "cookies site data privacy", "forum_data", "cookies_site_data"),
                new SettingTarget("clear_site_data", "Clear Forum Site Data & Sign Out", "cookies cache data sign out privacy", "forum_data", "cookies_site_data"),
                new SettingTarget("permission_status", "Android permission status", "permission security status foreground service boot network", "advanced", "permissions_security"),
                new SettingTarget("notification_permission", "Notification permission", "permission security notifications android alerts", "advanced", "permissions_security"),
                new SettingTarget("background_battery_permission", "Background battery access", "permission background battery unrestricted optimization foreground service realtime", "advanced", "permissions_security"),
                new SettingTarget("secure_updates_permission", "Secure app update install permission", "permission security install unknown apps apk", "advanced", "permissions_security"),
                new SettingTarget("android_permission_settings", "Android App Permission Settings", "permission security android settings app info", "advanced", "permissions_security"),
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
                new SettingTarget("ui_playground", "UI Playground", "ui user interface screen preview testing components cards buttons dialogs toast developer playground", "advanced", "developer_tools"),
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
                    settingsContent.addView(connectedSettingsPanel("Notification History", "Local history privacy and retention", notificationHistoryCard(), shouldExpand("notification_history", false)));
                    settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Silent service-status channel only", silentAlertsCard(), shouldExpand("silent_alerts", false)));
                    if (BuildInfo.ENABLE_DEV_TEST_MENU) {
                        settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", "Dev/Beta diagnostics only", testAlertsInfoCard(), shouldExpand("test_alerts", false)));
                    }
                    break;
                case "appearance":
                    settingsContent.addView(connectedSettingsPanel("Appearance & Performance", "Theme, interface and rendering preferences", interfaceCard(), shouldExpand("appearance_performance", true)));
                    break;
                case "widget":
                    settingsContent.addView(connectedSettingsPanel("Widget Appearance", "Theme source and home-screen widget controls", widgetCard(), shouldExpand("widget_appearance", true)));
                    break;
                case "forum_data":
                    settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));
                    settingsContent.addView(connectedSettingsPanel("Host Health", "Primary/backup reachability, latency and manual host selection", hostHealthCard(), shouldExpand("host_health", false)));
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
            if ("widget".equals(key)) return "Home-screen Widget";
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
            if ("notification_history".equals(key)) return "Notification History";
            if ("silent_alerts".equals(key)) return "HCF Silent Alerts";
            if ("test_alerts".equals(key)) return "HCF Test Alerts";
            if ("appearance_performance".equals(key)) return "Appearance & Performance";
            if ("widget_appearance".equals(key)) return "Widget Appearance";
            if ("connection_routing".equals(key)) return "Connection & Routing";
            if ("host_health".equals(key)) return "Host Health";
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
            card.addView(target(actionButton("Open Account & Identity", v -> startActivity(new Intent(this, HcfSubActivities.IdentityActivity.class))), "open_identity"));
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
            Intent intent = new Intent(this, HcfForum.MainActivity.class);
            intent.setData(Uri.parse("https://" + host + route));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }

        // HCF_ALERTS_RENDER_V3 — dedicated layout matching the selected second render.
        // HCF_ALERTS_RENDER_FINAL_V10000090 — selected newer expanded HCF Alerts design.
        private View mainAlertsCard() {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.TRANSPARENT);
            NotificationHelper.createChannel(this);

            final int green = android.graphics.Color.parseColor("#55E13B");
            final int yellow = android.graphics.Color.parseColor("#FFC21A");
            final int red = android.graphics.Color.parseColor("#FF4D57");

            final boolean runtimePermission = NotificationHelper.hasRuntimePermission(this);
            final boolean appNotificationsEnabled = NotificationHelper.areAppNotificationsEnabled(this);
            final int channelImportance = NotificationHelper.channelImportance(this, NotificationHelper.CHANNEL_ID);
            final boolean channelEnabled = channelImportance > 0;
            final boolean canPost = NotificationHelper.canPost(this);

            String alertState;
            int alertColor;
            if (!canPost) {
                alertState = "Blocked";
                alertColor = red;
            } else if (channelImportance < 4) {
                alertState = "Limited";
                alertColor = yellow;
            } else {
                alertState = "Ready";
                alertColor = green;
            }

            final boolean backgroundEnabled = prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
            final boolean silentStatusDisabled = prefs.getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false);
            String sessionUserId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            final boolean signedIn = sessionUserId != null && !sessionUserId.trim().isEmpty();
            final String lastSyncStatus = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
            final String normalizedSyncStatus = lastSyncStatus == null ? "" : lastSyncStatus.toLowerCase(Locale.US);
            final boolean syncStatusSucceeded = normalizedSyncStatus.contains("synced");
            final boolean syncStatusWaiting = normalizedSyncStatus.contains("waiting") || normalizedSyncStatus.contains("unavailable");
            final boolean syncStatusFailed = normalizedSyncStatus.contains("failed") || normalizedSyncStatus.contains("error");

            String backgroundState;
            int backgroundColor;
            if (!backgroundEnabled) {
                backgroundState = "Off";
                backgroundColor = red;
            } else if (!signedIn) {
                backgroundState = "Waiting";
                backgroundColor = yellow;
            } else if (silentStatusDisabled || syncStatusWaiting || syncStatusFailed) {
                backgroundState = "Delayed";
                backgroundColor = yellow;
            } else {
                backgroundState = "Live";
                backgroundColor = green;
            }

            long lastSyncAt = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
            long syncAgeSeconds = lastSyncAt <= 0L ? Long.MAX_VALUE : Math.max(0L, (System.currentTimeMillis() - lastSyncAt) / 1000L);
            String syncState;
            String syncDetail;
            int syncColor;
            if (!backgroundEnabled) {
                syncState = "Paused";
                syncDetail = "Sync off";
                syncColor = red;
            } else if (lastSyncAt <= 0L) {
                syncState = "Waiting";
                syncDetail = "No sync yet";
                syncColor = yellow;
            } else if (syncStatusFailed) {
                syncState = "Failed";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = red;
            } else if (syncStatusWaiting) {
                syncState = "Waiting";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = yellow;
            } else if (syncStatusSucceeded && syncAgeSeconds < 120L) {
                syncState = "Synced";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = green;
            } else if (syncStatusSucceeded && syncAgeSeconds < 900L) {
                syncState = "Recent";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = yellow;
            } else if (syncAgeSeconds >= 900L) {
                syncState = "Stale";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = red;
            } else {
                syncState = "Recent";
                syncDetail = hcfAlertSyncAge(syncAgeSeconds);
                syncColor = yellow;
            }

            // Newer reference: one grouped container with three equal status tiles.
            LinearLayout statusShell = new LinearLayout(this);
            statusShell.setOrientation(LinearLayout.HORIZONTAL);
            statusShell.setGravity(17);
            statusShell.setBackground(hcfAlertsPanelDrawable("#0C151B", "#29404B", 18, 1));
            statusShell.setPadding(dp(6), dp(8), dp(6), dp(8));
            LinearLayout.LayoutParams shellLp = new LinearLayout.LayoutParams(-1, -2);
            shellLp.bottomMargin = dp(14);
            root.addView(statusShell, shellLp);

            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, dp(compact() ? 96 : 110), 1.0f);
            tileLp.setMargins(dp(3), 0, dp(3), 0);
            statusShell.addView(hcfAlertStatusTile(alertState, "Alerts", "", alertColor), tileLp);
            statusShell.addView(hcfAlertStatusTile(backgroundState, "Background", "", backgroundColor), tileLp);
            statusShell.addView(hcfAlertStatusTile(syncState, "Last sync", syncDetail, syncColor), tileLp);

            // Background delivery.
            root.addView(hcfAlertsSectionHeader("Background delivery", R.drawable.fa_bell));

            LinearLayout deliveryCard = new LinearLayout(this);
            deliveryCard.setOrientation(LinearLayout.VERTICAL);
            deliveryCard.setBackground(hcfAlertsPanelDrawable("#0E171E", "#29404B", 17, 1));
            deliveryCard.setPadding(dp(13), dp(11), dp(13), dp(11));
            LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(-1, -2);
            deliveryLp.bottomMargin = dp(14);
            root.addView(deliveryCard, deliveryLp);

            LinearLayout switchRow = new LinearLayout(this);
            switchRow.setOrientation(LinearLayout.HORIZONTAL);
            switchRow.setGravity(16);
            TextView syncLabel = text("Background notification sync", 14, getColor(R.color.hcf_text));
            syncLabel.setTypeface(null, 1);
            switchRow.addView(syncLabel, new LinearLayout.LayoutParams(0, -2, 1.0f));
            Switch sync = target(toggle("", backgroundEnabled), "background_notification_sync");
            sync.setShowText(false);
            LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(dp(58), dp(40));
            switchRow.addView(sync, switchLp);
            deliveryCard.addView(switchRow);

            TextView deliveryText = text("Keep real HCF Alerts checking while the app is not open.", 10, getColor(R.color.hcf_muted));
            deliveryText.setPadding(0, dp(3), 0, dp(9));
            deliveryCard.addView(deliveryText);

            LinearLayout batteryTip = new LinearLayout(this);
            batteryTip.setOrientation(LinearLayout.HORIZONTAL);
            batteryTip.setGravity(16);
            batteryTip.setBackground(hcfAlertsPanelDrawable("#0A1319", "#203640", 14, 1));
            batteryTip.setPadding(dp(10), dp(8), dp(10), dp(8));
            TextView bolt = text("⚡", 17, getColor(R.color.hcf_text));
            bolt.setGravity(17);
            LinearLayout.LayoutParams boltLp = new LinearLayout.LayoutParams(dp(32), -2);
            boltLp.rightMargin = dp(7);
            batteryTip.addView(bolt, boltLp);

            final String batteryMessage = "For best reliability, set battery usage to Unrestricted in Android Settings > Apps > HCF Beta > Battery.";
            android.text.SpannableString batteryStyled = new android.text.SpannableString(batteryMessage);
            int unrestrictedStart = batteryMessage.indexOf("Unrestricted");
            if (unrestrictedStart >= 0) {
                int unrestrictedEnd = unrestrictedStart + "Unrestricted".length();
                batteryStyled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), unrestrictedStart, unrestrictedEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                batteryStyled.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.hcf_accent_text)), unrestrictedStart, unrestrictedEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView batteryText = text("", 10, getColor(R.color.hcf_muted));
            batteryText.setText(batteryStyled);
            batteryText.setLineSpacing(0.0f, 1.05f);
            batteryTip.addView(batteryText, new LinearLayout.LayoutParams(0, -2, 1.0f));
            deliveryCard.addView(batteryTip);

            sync.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, checked).apply();
                NotificationSyncScheduler.apply(this);
                AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
                Toast.makeText(this,
                        checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.",
                        Toast.LENGTH_SHORT).show();
                showSettingsSection("notifications");
            });

            // Android access compact status panel.
            root.addView(hcfAlertsSectionHeader("Android access", R.drawable.fa_shield));

            LinearLayout accessCard = new LinearLayout(this);
            accessCard.setOrientation(LinearLayout.VERTICAL);
            accessCard.setBackground(hcfAlertsPanelDrawable("#0E171E", "#29404B", 17, 1));
            accessCard.setPadding(dp(11), dp(5), dp(11), dp(10));
            root.addView(accessCard, new LinearLayout.LayoutParams(-1, -2));

            String permissionStatus;
            int permissionColor;
            if (!runtimePermission) {
                permissionStatus = "Permission denied";
                permissionColor = red;
            } else if (!appNotificationsEnabled) {
                permissionStatus = "Blocked by Android";
                permissionColor = red;
            } else {
                permissionStatus = "Permission allowed";
                permissionColor = green;
            }
            accessCard.addView(hcfAlertAccessRow(R.drawable.fa_shield, "Permission", permissionStatus, permissionColor));
            accessCard.addView(hcfAlertDivider());

            String channelStatus;
            int channelColor;
            if (!channelEnabled) {
                channelStatus = "Blocked / off";
                channelColor = red;
            } else if (channelImportance >= 4) {
                channelStatus = "High priority";
                channelColor = green;
            } else if (channelImportance >= 3) {
                channelStatus = "Normal priority";
                channelColor = yellow;
            } else {
                channelStatus = "Low priority";
                channelColor = yellow;
            }
            accessCard.addView(hcfAlertAccessRow(R.drawable.fa_bell, "Notification channel", channelStatus, channelColor));

            TextView openSettings = target(hcfAlertsActionRow("Open Android settings", R.drawable.fa_gear), "open_hcf_alerts_android_settings");
            openSettings.setOnClickListener(v -> NotificationHelper.openChannelSettings(this));
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(compact() ? 48 : 52));
            openLp.topMargin = dp(9);
            accessCard.addView(openSettings, openLp);

            // Informational footer above HCF Silent Alerts.
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.HORIZONTAL);
            info.setGravity(16);
            info.setBackground(hcfAlertsPanelDrawable("#0A1319", "#203640", 14, 1));
            info.setPadding(dp(12), dp(9), dp(12), dp(9));
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
            infoLp.topMargin = dp(11);
            root.addView(info, infoLp);
            ImageView infoIcon = settingsSectionIcon(R.drawable.fa_circle_info);
            LinearLayout.LayoutParams infoIconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
            infoIconLp.rightMargin = dp(10);
            info.addView(infoIcon, infoIconLp);
            TextView infoText = text("Real forum alerts (messages, mentions, replies, updates) use HCF Alerts. Controlled only in Android notification settings. App silent controls never affect this channel.", 10, getColor(R.color.hcf_muted));
            infoText.setLineSpacing(0.0f, 1.07f);
            info.addView(infoText, new LinearLayout.LayoutParams(0, -2, 1.0f));

            return root;
        }

        private String hcfAlertSyncAge(long syncAgeSeconds) {
            if (syncAgeSeconds == Long.MAX_VALUE) return "No sync yet";
            if (syncAgeSeconds < 60L) return "<1 min ago";
            long minutes = syncAgeSeconds / 60L;
            if (minutes < 60L) return minutes + (minutes == 1L ? " min ago" : " min ago");
            long hours = minutes / 60L;
            return hours + (hours == 1L ? " hr ago" : " hr ago");
        }

        private LinearLayout hcfAlertStatusTile(String state, String label, String detail, int color) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(17);
            tile.setBackground(hcfAlertsPanelDrawable("#101A21", "#314A56", 15, 1));
            tile.setPadding(dp(4), dp(8), dp(4), dp(7));

            View light = hcfAlertStatusLight(color);
            LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(11), dp(11));
            lightLp.bottomMargin = dp(6);
            tile.addView(light, lightLp);

            TextView stateText = text(state, compact() ? 12 : 14, color);
            stateText.setTypeface(null, 1);
            stateText.setGravity(17);
            stateText.setSingleLine(true);
            tile.addView(stateText);

            TextView labelText = text(label, compact() ? 9 : 10, getColor(R.color.hcf_muted));
            labelText.setGravity(17);
            labelText.setPadding(0, dp(3), 0, 0);
            labelText.setSingleLine(true);
            tile.addView(labelText);

            if (detail != null && !detail.isEmpty()) {
                TextView detailText = text(detail, 9, getColor(R.color.hcf_muted));
                detailText.setGravity(17);
                detailText.setSingleLine(true);
                tile.addView(detailText);
            }
            return tile;
        }

        private View hcfAlertStatusLight(int color) {
            View light = new View(this);
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(color);
            light.setBackground(dot);
            if (Build.VERSION.SDK_INT >= 21) {
                light.setElevation(dp(5));
                light.setTranslationZ(dp(2));
            }
            return light;
        }

        private View hcfAlertsSectionHeader(String title, int iconRes) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(16);
            row.setPadding(dp(5), dp(4), dp(5), dp(8));
            ImageView icon = settingsSectionIcon(iconRes);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconLp.rightMargin = dp(10);
            row.addView(icon, iconLp);
            TextView titleView = text(title, 14, getColor(R.color.hcf_accent_text));
            titleView.setTypeface(null, 1);
            row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));
            return row;
        }

        private LinearLayout hcfAlertAccessRow(int iconRes, String label, String status, int color) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(16);
            row.setPadding(dp(2), dp(10), dp(2), dp(10));

            ImageView icon = settingsSectionIcon(iconRes);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
            iconLp.rightMargin = dp(10);
            row.addView(icon, iconLp);

            TextView labelText = text(label, 11, getColor(R.color.hcf_text));
            labelText.setMaxLines(2);
            row.addView(labelText, new LinearLayout.LayoutParams(0, -2, 1.0f));

            LinearLayout statusWrap = new LinearLayout(this);
            statusWrap.setOrientation(LinearLayout.HORIZONTAL);
            statusWrap.setGravity(16);
            View light = hcfAlertStatusLight(color);
            LinearLayout.LayoutParams lightLp = new LinearLayout.LayoutParams(dp(9), dp(9));
            lightLp.rightMargin = dp(7);
            statusWrap.addView(light, lightLp);
            TextView statusText = text(status, 10, getColor(R.color.hcf_muted));
            statusText.setGravity(17);
            statusText.setMaxLines(2);
            statusWrap.addView(statusText, new LinearLayout.LayoutParams(-2, -2));
            row.addView(statusWrap, new LinearLayout.LayoutParams(-2, -2));
            return row;
        }

        private View hcfAlertDivider() {
            View divider = new View(this);
            divider.setBackgroundColor(android.graphics.Color.parseColor("#29404B"));
            divider.setAlpha(0.85f);
            divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
            return divider;
        }

        private TextView hcfAlertsActionRow(String label, int iconRes) {
            TextView action = text(label, 13, getColor(R.color.hcf_accent_text));
            action.setGravity(17);
            action.setClickable(true);
            action.setFocusable(true);
            action.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            action.setCompoundDrawablePadding(dp(10));
            action.setBackground(hcfAlertsPanelDrawable("#0D171E", "#00B8F0", 22, 1));
            action.setPadding(dp(14), 0, dp(14), 0);
            return action;
        }

        private android.graphics.drawable.GradientDrawable hcfAlertsPanelDrawable(String fill, String stroke, int radiusDp, int strokeDp) {
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            background.setColor(android.graphics.Color.parseColor(fill));
            background.setCornerRadius(dp(radiusDp));
            if (stroke != null && strokeDp > 0) background.setStroke(dp(strokeDp), android.graphics.Color.parseColor(stroke));
            return background;
        }

        private View silentAlertsCard() {
            LinearLayout card = card();
            NotificationHelper.createChannel(this);
            card.addView(settingsInfoCard("Silent service-status channel",
                    "When on, HCF Silent Alerts is fully quiet: optional status alerts are hidden and the ongoing live-service notification is removed. Background delivery then uses scheduled jobs only and may be delayed. Real HCF Alerts (messages, mentions, replies, updates) are never affected.",
                    R.drawable.fa_bell));
            Switch silence = target(toggle("Silence HCF Silent Alerts",
                    prefs.getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false)),
                    "silence_hcf_silent_alerts");
            silence.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, checked).apply();
                NotificationHelper.refreshChannels(this);
                NotificationSyncScheduler.apply(this);
                AppLogger.info(this, "setting_silence_passive_notifications", Boolean.toString(checked));
                Toast.makeText(this,
                        checked
                                ? "HCF Silent Alerts silenced. Live-service notification hidden; background delivery may be delayed. Real HCF Alerts stay on."
                                : "HCF Silent Alerts enabled. Live-service notification can run when background sync is on.",
                        Toast.LENGTH_LONG).show();
            });
            card.addView(silence);
            card.addView(text("When on, HCF Silent Alerts is fully quiet: optional status alerts are hidden and the ongoing live-service notification is removed. Background delivery then uses scheduled jobs only and may be delayed. Real HCF Alerts (messages, mentions, replies, updates) are never affected.", 10, getColor(R.color.hcf_muted)));
            card.addView(notificationChannelRow("HCF Silent Alerts", "Silent • live service + optional status", NotificationHelper.SILENT_CHANNEL_ID));
            return card;
        }

        private View notificationHistoryCard() {
            LinearLayout card = card();
            String mode = HcfWidget.historyMode(prefs);
            int limit = HcfWidget.historyLimit(prefs);
            card.addView(target(settingsInfoCard(
                    "Stored notification history",
                    HcfWidget.historyModeLabel(mode) + " • keep up to " + limit + " events • stored only on this device",
                    R.drawable.fa_lock), "notification_history_privacy"));

            Button privacy = target(actionButton("History content: " + HcfWidget.historyModeLabel(mode), null),
                    "notification_history_privacy");
            privacy.setOnClickListener(v -> {
                final String[] labels = {"Off", "Titles only", "Titles + message"};
                final String[] values = {HcfWidget.HISTORY_MODE_OFF, HcfWidget.HISTORY_MODE_TITLE, HcfWidget.HISTORY_MODE_FULL};
                String saved = HcfWidget.historyMode(prefs);
                int selected = HcfWidget.HISTORY_MODE_OFF.equals(saved) ? 0
                        : HcfWidget.HISTORY_MODE_TITLE.equals(saved) ? 1 : 2;
                new AlertDialog.Builder(this)
                        .setTitle("Notification history privacy")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            HcfWidget.setHistoryPrivacy(prefs, values[which], HcfWidget.historyLimit(prefs));
                            AppLogger.info(this, "notification_history_privacy", values[which]);
                            dialog.dismiss();
                            showSettingsSection("notifications");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(privacy);

            Button retention = target(actionButton("History retention: " + limit + " events", null),
                    "notification_history_retention");
            retention.setOnClickListener(v -> {
                final String[] labels = {"10 events", "30 events", "60 events"};
                final int[] values = {10, 30, 60};
                int saved = HcfWidget.historyLimit(prefs);
                int selected = saved <= 10 ? 0 : saved <= 30 ? 1 : 2;
                new AlertDialog.Builder(this)
                        .setTitle("Notification history retention")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            HcfWidget.setHistoryPrivacy(prefs, HcfWidget.historyMode(prefs), values[which]);
                            AppLogger.info(this, "notification_history_retention", Integer.toString(values[which]));
                            dialog.dismiss();
                            showSettingsSection("notifications");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(retention);
            card.addView(target(actionButton("Open Notification History", v ->
                    startActivity(new Intent(this, HcfWidget.NotificationHistoryActivity.class))),
                    "open_notification_history"));
            card.addView(text(
                    "Off stores no notification-history list. Titles only removes message bodies and destination URLs from retained history. Titles + message keeps the current local history behavior. Clearing history does not erase the separate home-screen widget preview.",
                    10, getColor(R.color.hcf_muted)));
            return card;
        }

        private View testAlertsInfoCard() {
            LinearLayout card = card();
            NotificationHelper.createChannel(this);
            card.addView(settingsInfoCard("Developer test channel",
                    "Use this only to test notification delivery. It never carries real forum alerts or the live-service notification.",
                    R.drawable.fa_bug));
            card.addView(notificationChannelRow("HCF Test Alerts", "Dev/Beta notification tests", NotificationHelper.TEST_CHANNEL_ID));
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

        private View widgetCard() {
            LinearLayout card = card();
            final boolean followAppTheme = prefs.getBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, true);
            final String themeState = followAppTheme
                    ? "Following HCF app theme • " + ThemeManager.autoSourceLabel(this)
                    : "Following Android phone theme";

            card.addView(settingsInfoCard(
                    "Widget theme source",
                    themeState,
                    R.drawable.fa_gear));

            Switch follow = target(toggle("Follow HCF app theme", followAppTheme), "widget_follow_app_theme");
            follow.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_theme_source", checked ? "app" : "phone");
                Toast.makeText(this,
                        checked
                                ? "Home-screen widget now follows the HCF app theme."
                                : "Home-screen widget now follows the Android phone theme.",
                        Toast.LENGTH_SHORT).show();
                showSettingsSection("widget");
            });
            card.addView(follow);

            card.addView(text(
                    "When on, the widget uses HCF's selected Light, Dark, AMOLED, or resolved Auto theme even when the phone/launcher uses the opposite theme. Turn it off only if you want the widget to follow Android's phone theme instead.",
                    10, getColor(R.color.hcf_muted)));

            final boolean showConnectedUsername = prefs.getBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, true);
            Switch connectedUsername = target(toggle("Show connected @username", showConnectedUsername), "widget_show_connected_username");
            connectedUsername.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_connected_username", checked ? "shown" : "hidden");
            });
            card.addView(connectedUsername);
            card.addView(text(
                    "When on, signed-in widgets show the connected forum identity as @username next to the cached notification state. No username is shown while signed out.",
                    10, getColor(R.color.hcf_muted)));

            Switch showUnread = target(toggle("Show unread count",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, true)), "widget_show_unread_count");
            showUnread.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_unread_count", checked ? "shown" : "hidden");
            });
            card.addView(showUnread);

            Switch compactWidget = target(toggle("Compact widget mode",
                    prefs.getBoolean(HcfWidget.PREF_COMPACT_MODE, false)), "widget_compact_mode");
            compactWidget.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_COMPACT_MODE, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_compact_mode", checked ? "on" : "off");
            });
            card.addView(compactWidget);
            card.addView(text(
                    "Compact mode hides the large widget logo and title so the connected identity/status gets more room. Quick actions stay available.",
                    10, getColor(R.color.hcf_muted)));

            Switch showUpdated = target(toggle("Show last updated time",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, false)), "widget_show_last_updated");
            showUpdated.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_last_updated", checked ? "shown" : "hidden");
            });
            card.addView(showUpdated);

            final String currentTap = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);
            final String currentTapLabel = HcfWidget.TAP_NOTIFICATIONS.equals(currentTap)
                    ? "Notifications" : HcfWidget.TAP_SETTINGS.equals(currentTap) ? "App Settings" : "Forum";
            Button defaultTap = target(actionButton("Default widget tap: " + currentTapLabel, null),
                    "widget_default_tap_action");
            defaultTap.setOnClickListener(v -> {
                final String[] labels = {"Forum", "Notifications", "App Settings"};
                String savedTap = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);
                int selected = HcfWidget.TAP_NOTIFICATIONS.equals(savedTap) ? 1
                        : HcfWidget.TAP_SETTINGS.equals(savedTap) ? 2 : 0;
                new AlertDialog.Builder(this)
                        .setTitle("Default widget tap")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            String value = which == 1 ? HcfWidget.TAP_NOTIFICATIONS
                                    : which == 2 ? HcfWidget.TAP_SETTINGS : HcfWidget.TAP_FORUM;
                            prefs.edit().putString(HcfWidget.PREF_DEFAULT_TAP_ACTION, value).apply();
                            HcfWidget.refreshAll(this);
                            defaultTap.setText("Default widget tap: " + labels[which]);
                            AppLogger.info(this, "setting_widget_default_tap", value);
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(defaultTap);

            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "widget_refresh", "settings");
                Toast.makeText(this, "Home-screen widget refreshed.", Toast.LENGTH_SHORT).show();
            }), "refresh_widget_now"));
            return card;
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
            Switch verboseStartup = target(toggle("Verbose startup loader", prefs.getBoolean("startup_loader_verbose", false)), "verbose_startup_loader");
            verboseStartup.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean("startup_loader_verbose", checked).apply();
                AppLogger.info(this, "setting_startup_loader_verbose", Boolean.toString(checked));
                Toast.makeText(this, checked ? "Detailed startup checks enabled." : "Startup loader will use the compact view.", Toast.LENGTH_SHORT).show();
            });
            card.addView(verboseStartup);
            card.addView(text("When off, every startup safety and system check still runs; only the detailed descriptions and completed-check list are hidden.", 10, getColor(R.color.hcf_muted)));
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

        private View hostHealthCard() {
            LinearLayout card = card();
            hostHealthStatus = target(text(hostHealthSummary(), 11, getColor(R.color.hcf_meta)), "host_health_status");
            hostHealthStatus.setBackgroundResource(R.drawable.quick_action_background);
            hostHealthStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
            card.addView(hostHealthStatus);
            card.addView(target(actionButton("Test Both Forum Hosts", v -> testHostHealth()), "test_host_health"));
            card.addView(target(actionButton("Use Primary Forum", v -> selectForumHost("forum.harleytg.com", true)), "use_primary_host"));
            card.addView(target(actionButton("Use Backup Forum", v -> selectForumHost("harleysclan.freeflarum.com", false)), "use_backup_host"));
            card.addView(text(
                    "Host tests use HTTPS only and record the last reachability result and round-trip latency locally. Manual selection changes the preferred host without disabling automatic failover.",
                    10, getColor(R.color.hcf_muted)));
            return card;
        }

        private String hostHealthSummary() {
            String active = prefs.getString("active_host", "forum.harleytg.com");
            if (!ForumUrlRouter.isForumHost(active)) active = "forum.harleytg.com";
            String primary = hostHealthLine("Primary", "host_health_primary");
            String backup = hostHealthLine("Backup", "host_health_backup");
            long lastSuccess = prefs.getLong("host_health_last_success_at", 0L);
            String lastSuccessHost = prefs.getString("host_health_last_success_host", "");
            String success = lastSuccess <= 0L
                    ? "Last successful probe: Not tested yet"
                    : "Last successful probe: " + ("harleysclan.freeflarum.com".equals(lastSuccessHost) ? "Backup" : "Primary")
                            + " • " + ageLabel(lastSuccess);
            return "Currently using: " + ("forum.harleytg.com".equals(active) ? "Primary" : "Backup") + " • " + active
                    + "\n" + primary + "\n" + backup + "\n" + success;
        }

        private String hostHealthLine(String label, String prefix) {
            long checkedAt = prefs.getLong(prefix + "_checked_at", 0L);
            if (checkedAt <= 0L) return label + ": Not tested";
            boolean healthy = prefs.getBoolean(prefix + "_ok", false);
            long latency = prefs.getLong(prefix + "_latency_ms", -1L);
            int status = prefs.getInt(prefix + "_http_status", -1);
            return label + ": " + (healthy ? "Online" : "Offline")
                    + (latency >= 0 ? " • " + latency + " ms" : "")
                    + (status > 0 ? " • HTTP " + status : "")
                    + " • " + ageLabel(checkedAt);
        }

        private void testHostHealth() {
            if (hostHealthStatus != null) {
                hostHealthStatus.setText("Testing primary and backup hosts…");
                hostHealthStatus.setTextColor(getColor(R.color.hcf_cyan));
            }
            new Thread(() -> {
                HostHealthResult primary = probeHost("forum.harleytg.com");
                HostHealthResult backup = probeHost("harleysclan.freeflarum.com");
                long now = System.currentTimeMillis();
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean("host_health_primary_ok", primary.healthy)
                        .putLong("host_health_primary_latency_ms", primary.latencyMs)
                        .putInt("host_health_primary_http_status", primary.httpStatus)
                        .putLong("host_health_primary_checked_at", now)
                        .putBoolean("host_health_backup_ok", backup.healthy)
                        .putLong("host_health_backup_latency_ms", backup.latencyMs)
                        .putInt("host_health_backup_http_status", backup.httpStatus)
                        .putLong("host_health_backup_checked_at", now);
                String active = prefs.getString("active_host", "forum.harleytg.com");
                if ("forum.harleytg.com".equals(active) && primary.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", primary.host);
                } else if ("harleysclan.freeflarum.com".equals(active) && backup.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", backup.host);
                } else if (primary.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", primary.host);
                } else if (backup.healthy) {
                    editor.putLong("host_health_last_success_at", now)
                            .putString("host_health_last_success_host", backup.host);
                }
                editor.apply();
                AppLogger.info(this, "host_health",
                        "primary=" + primary.summary() + " • backup=" + backup.summary());
                runOnUiThread(() -> {
                    if (hostHealthStatus != null) {
                        hostHealthStatus.setText(hostHealthSummary());
                        hostHealthStatus.setTextColor(getColor(
                                primary.healthy || backup.healthy ? R.color.hcf_meta : R.color.hcf_warning));
                    }
                    Toast.makeText(this,
                            primary.healthy || backup.healthy
                                    ? "Host health test complete."
                                    : "Neither forum host responded successfully.",
                            Toast.LENGTH_SHORT).show();
                });
            }, "hcf-host-health").start();
        }

        private HostHealthResult probeHost(String host) {
            long started = android.os.SystemClock.elapsedRealtime();
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL("https://" + host + "/").openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("HEAD");
                connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " HostHealth/1");
                int status = connection.getResponseCode();
                long latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - started);
                boolean healthy = status >= 200 && status < 500;
                return new HostHealthResult(host, healthy, latency, status, "");
            } catch (Throwable error) {
                long latency = Math.max(0L, android.os.SystemClock.elapsedRealtime() - started);
                return new HostHealthResult(host, false, latency, -1, error.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private void selectForumHost(String host, boolean primary) {
            if (!ForumUrlRouter.isForumHost(host)) return;
            SharedPreferences.Editor editor = prefs.edit().putString("active_host", host);
            if (primary) editor.remove("fallback_until");
            editor.apply();
            refreshStatusLabels();
            if (hostHealthStatus != null) hostHealthStatus.setText(hostHealthSummary());
            AppLogger.info(this, "settings_host_select", host);
            Toast.makeText(this,
                    (primary ? "Primary" : "Backup") + " forum selected for the next forum navigation.",
                    Toast.LENGTH_SHORT).show();
        }

        private static final class HostHealthResult {
            final String host;
            final boolean healthy;
            final long latencyMs;
            final int httpStatus;
            final String error;

            HostHealthResult(String host, boolean healthy, long latencyMs, int httpStatus, String error) {
                this.host = host;
                this.healthy = healthy;
                this.latencyMs = latencyMs;
                this.httpStatus = httpStatus;
                this.error = error == null ? "" : error;
            }

            String summary() {
                return (healthy ? "online" : "offline") + "/" + latencyMs + "ms"
                        + (httpStatus > 0 ? "/http" + httpStatus : "")
                        + (error.isEmpty() ? "" : "/" + error);
            }
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
            card.addView(target(actionButton("Open Cookie Manager", v -> startActivity(new Intent(this, HcfForum.CookieManagerActivity.class))), "cookie_manager"));
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
            card.addView(sectionTitle("Permissions & Security", "Live Android access status and app hardening"));

            securityStatus = target(text(permissionSecuritySummary(), 11, getColor(R.color.hcf_meta)), "permission_status");
            securityStatus.setBackgroundResource(R.drawable.quick_action_background);
            securityStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
            card.addView(securityStatus);

            card.addView(settingsSubsectionHeader("Android permissions", "Permissions HCF actually uses on this device", R.drawable.fa_shield));

            boolean notificationAllowed = NotificationHelper.hasRuntimePermission(this);
            card.addView(settingsInfoCard("Notifications",
                    notificationAllowed
                            ? "Allowed • HCF Alerts can post when the Android HCF Alerts channel is enabled."
                            : "Needs permission • Android is currently blocking the app-level notification permission.",
                    R.drawable.fa_bell));
            card.addView(target(actionButton(
                    notificationAllowed ? "Open HCF Alert Settings" : "Allow Notification Permission",
                    v -> {
                        if (NotificationHelper.hasRuntimePermission(this)) {
                            NotificationHelper.openChannelSettings(this, NotificationHelper.CHANNEL_ID);
                        } else {
                            requestNotificationPermissionIfNeeded();
                        }
                    }), "notification_permission"));

            boolean backgroundExempt = isBackgroundBatteryExempt();
            card.addView(settingsInfoCard("Background activity",
                    backgroundDeliveryPermissionSummary(backgroundExempt),
                    R.drawable.fa_bell));
            card.addView(target(actionButton(
                    backgroundExempt ? "Background Battery Access: Allowed" : "Allow Background Battery Access",
                    v -> openBackgroundBatteryAccess()), "background_battery_permission"));

            boolean installAllowed = AppSecurity.canInstallUpdates(this);
            card.addView(settingsInfoCard("Secure app updates",
                    installAllowed
                            ? "Allowed • Android permits HCF Beta to hand a verified APK to the package installer."
                            : "Approval required • downloaded updates can be verified, but Android will not install them until this app source is allowed.",
                    R.drawable.fa_shield));
            card.addView(target(actionButton(
                    installAllowed ? "Secure App Updates: Allowed" : "Allow Secure App Updates",
                    v -> openInstallPermission()), "secure_updates_permission"));

            card.addView(settingsSubsectionHeader("System access", "Declared Android capabilities that do not use runtime permission dialogs", R.drawable.fa_gear));
            card.addView(settingsInfoCard("Background service support",
                    "Foreground service: Declared • special-use foreground service: Declared • restart after boot/update: Declared • JobScheduler fallback: Enabled.",
                    R.drawable.fa_shield));
            card.addView(settingsInfoCard("Network access",
                    "Internet + network-state access are declared for the forum WebView, notification sync, domain failover, and update checks.",
                    R.drawable.fa_lock));

            card.addView(target(actionButton("Android App Permission Settings", v -> openAndroidAppSettings()), "android_permission_settings"));
            card.addView(text("HCF does not request location, contacts, microphone, camera, or broad storage access. Background battery access is an Android power-management setting, not a normal runtime permission. Turning on Silence HCF Silent Alerts still stops the live foreground service even when background battery access is allowed.", 10, getColor(R.color.hcf_muted)));
            return card;
        }

        private String permissionSecuritySummary() {
            boolean notifications = NotificationHelper.hasRuntimePermission(this);
            boolean backgroundExempt = isBackgroundBatteryExempt();
            boolean installs = AppSecurity.canInstallUpdates(this);
            return "Notifications: " + (notifications ? "Allowed" : "Needs permission")
                    + "\nBackground battery access: " + (backgroundExempt ? "Allowed / exempt" : "Android may restrict")
                    + "\nSecure update installs: " + (installs ? "Allowed" : "Needs approval")
                    + "\nForeground service: Declared • Boot restart: Declared";
        }

        private String backgroundDeliveryPermissionSummary(boolean backgroundExempt) {
            boolean backgroundSync = prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
            boolean silentSuppressed = prefs.getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false);
            String sessionUserId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
            boolean signedIn = sessionUserId != null && !sessionUserId.trim().isEmpty();

            String mode;
            if (!backgroundSync) {
                mode = "Background notification sync is off.";
            } else if (!signedIn) {
                mode = "Background sync is enabled but waiting for a signed-in forum session.";
            } else if (silentSuppressed) {
                mode = "Scheduled jobs only • HCF Silent Alerts is silenced, so the live foreground service is intentionally stopped.";
            } else {
                mode = "Live foreground service is eligible • notification 41070 can run on HCF Silent Alerts.";
            }

            return (backgroundExempt
                    ? "Battery optimization exemption is active. "
                    : "Battery optimization exemption is not active; Android may delay background work. ")
                    + mode;
        }

        private boolean isBackgroundBatteryExempt() {
            if (Build.VERSION.SDK_INT < 23) return true;
            try {
                android.os.PowerManager manager = (android.os.PowerManager) getSystemService("power");
                return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void openBackgroundBatteryAccess() {
            if (Build.VERSION.SDK_INT < 23) {
                Toast.makeText(this, "Background battery restrictions do not apply on this Android version.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isBackgroundBatteryExempt()) {
                Toast.makeText(this, "Background battery access is already allowed.", Toast.LENGTH_SHORT).show();
                try {
                    startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
                } catch (Throwable ignored) {
                    openAndroidAppSettings();
                }
                return;
            }
            try {
                Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Throwable error) {
                try {
                    startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
                } catch (Throwable ignored) {
                    openAndroidAppSettings();
                }
            }
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
            TextView verification = target(text("APK verification: HCF checks the downloaded package name, Android versionCode, exact SHA-256 file hash, and signing-certificate lineage before opening Android's installer. A changed SHA-256 can also identify a revised APK with the same versionCode. Android still requires your confirmation to install.", 10, getColor(R.color.hcf_muted)), "apk_verification");
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
                        updateStatus.setText(channelDisplayName(channel) + " Update Available\nInstalled: " + installed + "\nLatest available: " + remote + "\nReason: " + UpdateChecker.updateReason(release) + "\n" + releaseType + " • " + asset);
                        updateStatus.setTextColor(getColor(R.color.hcf_accent_text));
                        if (updateDownloadButton != null && release.apkUrl != null && !release.apkUrl.isEmpty()) updateDownloadButton.setVisibility(View.VISIBLE);
                        if (prefs.getBoolean("update_auto_download", false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                            long id = AppUpdateDownloader.enqueue(SettingsActivity.this, release, userInitiated);
                            if (id > 0) {
                                updateStatus.append("\nAutomatic download queued • installer opens when ready.");
                                watchUpdateDownloadForAutoInstall(id);
                            }
                        }
                        if (userInitiated) Toast.makeText(SettingsActivity.this, release.sameVersionHashUpdate
                                ? "Revised Dev/Beta APK available • SHA-256 changed"
                                : channelDisplayName(channel) + " update available" + (release.versionCode > 0 ? " • build " + release.versionCode : ""), Toast.LENGTH_LONG).show();
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
            card.addView(target(actionButton("View App & Crash Logs", v -> startActivity(new Intent(this, HcfSubActivities.LogsActivity.class))), "view_crash_logs"));
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
                    .setNeutralButton("View logs", (dialog, which) -> startActivity(new Intent(this, HcfSubActivities.LogsActivity.class))).show();
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
                HcfNotifications.InstantNotificationService.requestImmediateSync(this);
                Toast.makeText(this, "Immediate notification sync requested.", Toast.LENGTH_SHORT).show();
            }), "force_notification_sync"));

            card.addView(settingsSubsectionHeader(
                    "UI Playground",
                    "Preview newly designed HCF app screens",
                    R.drawable.fa_gear
            ));
            card.addView(target(actionButton("Open UI Playground", v -> showUiPlayground()), "ui_playground"));
            return card;
        }

        private String developerToolsSubtitle() {
            return "stable".equals(effectiveUpdateChannel()) ? "Stable test tools" : "Dev/Beta test controls";
        }

        private void showUiPlayground() {
            AppLogger.info(this, "ui_playground_open", BuildInfo.VERSION);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(8), dp(14), dp(16));
            scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

            content.addView(settingsInfoCard(
                    "Screen preview lab",
                    "Preview the newest HCF app screens without changing their normal layout.",
                    R.drawable.fa_bug
            ));

            content.addView(settingsSubsectionHeader(
                    "Screen previews",
                    "Open the newly designed app screens",
                    R.drawable.fa_circle_info
            ));
            content.addView(actionButton("Preview Welcome Screen", v ->
                    startActivity(new Intent(this, HcfForum.WelcomeActivity.class))));
            content.addView(actionButton("Preview App Settings", v ->
                    startActivity(new Intent(this, HcfSubActivities.SettingsActivity.class))));

            new AlertDialog.Builder(this)
                    .setTitle("UI Playground")
                    .setView(scroll)
                    .setNegativeButton("Close", null)
                    .show();
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
            HcfNotifications.InstantNotificationService.requestImmediateSync(this);
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
                startActivity(new Intent(this, HcfSubActivities.SupportContactActivity.class));
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
                boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) >= 4;
                notificationStatus.setText(NotificationHelper.status(this));
                notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
            }
            if (cookieStatus != null) cookieStatus.setText(cookieSummary());
            if (securityStatus != null) securityStatus.setText(permissionSecuritySummary());
            if (telemetryStatus != null) telemetryStatus.setText(TelemetryService.status(this));
            if (updateChannelStatus != null) updateChannelStatus.setText(updateChannelLine(effectiveUpdateChannel()));
            if (updateInstallButton != null) updateInstallButton.setVisibility(AppUpdateDownloader.isDownloaded(this) ? View.VISIBLE : View.GONE);
            if (serverStatus != null) {
                String host = prefs.getString("active_host", "forum.harleytg.com");
                boolean primary = "forum.harleytg.com".equalsIgnoreCase(host);
                serverStatus.setText("Current server: " + (primary ? "Primary • " : "Backup • ") + host);
                serverStatus.setTextColor(getColor(primary ? R.color.hcf_cyan : R.color.hcf_warning));
            }
            if (hostHealthStatus != null) hostHealthStatus.setText(hostHealthSummary());
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
            if ("widget".equals(key)) return R.drawable.fa_gear;
            if ("forum_data".equals(key)) return R.drawable.fa_globe;
            return R.drawable.fa_circle_info;
        }

        private int settingsIconForTitle(String title) {
            String lower = title == null ? "" : title.toLowerCase(Locale.US);
            if (lower.contains("account") || lower.contains("identity")) return R.drawable.fa_user;
            if (lower.contains("permission") || lower.contains("security")) return R.drawable.fa_shield;
            if (lower.contains("notification") || lower.contains("alert")) return R.drawable.fa_bell;
            if (lower.contains("appearance") || lower.contains("performance") || lower.contains("runtime") || lower.contains("widget")) return R.drawable.fa_gear;
            if (lower.contains("connection") || lower.contains("routing") || lower.contains("endpoint") || lower.contains("host")) return R.drawable.fa_globe;
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

    // ---- SupportContactActivity.java ----
    /* loaded from: classes.dex */
    public static final class SupportContactActivity extends ThemedActivity {
        private static final String SUPPORT_EMAIL = "harleytg.hq@gmail.com";
        private Spinner categoryField;
        private EditText expectedField;
        private EditText guestNameField;
        private EditText guestReplyEmailField;
        private ForumIdentity.Snapshot identity;
        private CheckBox includeDiagnostics;
        private CheckBox includeIdentity;
        private CheckBox includeRoute;
        private EditText messageField;
        private SharedPreferences prefs;
        private EditText stepsField;
        private EditText subjectField;

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            ThemeManager.apply(this);
            this.identity = ForumIdentity.load(this);
            this.prefs = getSharedPreferences("hcf_app", 0);
            setTitle("Contact Support");
            buildUi();
        }

        private void buildUi() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(getColor(R.color.hcf_bg));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setGravity(16);
            linearLayout2.setPadding(dp(12), dp(10), dp(12), dp(10));
            linearLayout2.setBackgroundColor(getColor(R.color.hcf_app_bar));
            ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
            iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SupportContactActivity.this.m204lambda$buildUi$0$comharleytgforumdevSupportContactActivity(view);
                }
            });
            linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.htg_app_logo);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setContentDescription("Harley's Clan Forum");
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            layoutParams.leftMargin = dp(4);
            linearLayout2.addView(imageView, layoutParams);
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.leftMargin = dp(10);
            linearLayout3.addView(label("Contact Support", 19, R.color.hcf_text, true));
            linearLayout3.addView(label("Forum help, app support & report tools", 10, R.color.hcf_cyan_bright, true));
            linearLayout2.addView(linearLayout3, layoutParams2);
            linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
            ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(true);
            LinearLayout linearLayout4 = new LinearLayout(this);
            linearLayout4.setOrientation(1);
            linearLayout4.setPadding(dp(12), dp(12), dp(12), dp(24));
            linearLayout4.addView(supportPanel("Your account", "Locked forum identity and reply information", R.drawable.fa_user, accountBody(), false));
            linearLayout4.addView(supportPanel("Support request", "Tell us what happened and what you expected", R.drawable.fa_envelope, requestBody(), false));
            linearLayout4.addView(supportPanel("Report context", "Read-only app and device information", R.drawable.fa_circle_info, contextBody(), false));
            linearLayout4.addView(supportPanel("Privacy & send", "Choose what to include, preview, then send", R.drawable.fa_shield, privacyBody(), false));
            TextView label = label("Harley's Clan Forum • Contact Support v2 • v1.0", 9, R.color.hcf_hint, false);
            label.setGravity(17);
            label.setPadding(0, dp(6), 0, dp(4));
            linearLayout4.addView(label);
            scrollView.addView(linearLayout4, new FrameLayout.LayoutParams(-1, -2));
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            setContentView(linearLayout);
        }

        /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m204lambda$buildUi$0$comharleytgforumdevSupportContactActivity(View view) {
            finish();
        }

        private View accountBody() {
            String str;
            LinearLayout bodyContainer = bodyContainer();
            if (this.identity.loggedIn) {
                String str2 = "Not exposed";
                addLockedRow(bodyContainer, "Display name", nonEmpty(this.identity.displayName, "Not exposed"));
                if (this.identity.username.isEmpty()) {
                    str = "Not exposed";
                } else {
                    str = "@" + this.identity.username;
                }
                addLockedRow(bodyContainer, "Username", str);
                if (!this.identity.email.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(this.identity.email);
                    sb.append(this.identity.emailConfirmed ? " • verified" : " • unverified");
                    str2 = sb.toString();
                }
                addLockedRow(bodyContainer, "Forum email", str2);
                addLockedRow(bodyContainer, "Role", this.identity.identityMetaLabel());
                addLockedRow(bodyContainer, "Forum host", nonEmpty(this.identity.host, "forum.harleytg.com"));
                TextView label = label("These identity fields are synced from the signed-in forum session and cannot be edited here.", 10, R.color.hcf_muted, false);
                label.setPadding(0, dp(4), 0, 0);
                bodyContainer.addView(label);
            } else {
                TextView label2 = label("No signed-in forum identity was detected. Enter a name and reply email for this support request.", 11, R.color.hcf_muted, false);
                label2.setPadding(0, 0, 0, dp(10));
                bodyContainer.addView(label2);
                EditText input = input("Name or display name", 1, false);
                this.guestNameField = input;
                addField(bodyContainer, "Name", input);
                EditText input2 = input("Email support can reply to", 33, false);
                this.guestReplyEmailField = input2;
                addField(bodyContainer, "Reply email", input2);
            }
            return bodyContainer;
        }

        private View requestBody() {
            LinearLayout bodyContainer = bodyContainer();
            addLockedRow(bodyContainer, "Support destination", SUPPORT_EMAIL);
            bodyContainer.addView(fieldLabel("Support type"));
            this.categoryField = new Spinner(this);
            this.categoryField.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"App issue", "Account issue", "Notifications", "Login / identity", "Bug report", "Feature request", "Update / install", "Forum / WebView", "Privacy / security", "Other"}));
            this.categoryField.setBackgroundResource(R.drawable.identity_card_background);
            this.categoryField.setPadding(dp(10), dp(4), dp(10), dp(4));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(50));
            layoutParams.bottomMargin = dp(11);
            bodyContainer.addView(this.categoryField, layoutParams);
            EditText input = input("Short description of the issue", 16385, false);
            this.subjectField = input;
            addField(bodyContainer, "Subject", input);
            EditText input2 = input("What happened? Include any error message you saw.", 147457, true);
            this.messageField = input2;
            input2.setMinLines(5);
            this.messageField.setGravity(8388659);
            addField(bodyContainer, "What happened", this.messageField);
            EditText input3 = input("Optional: steps that reproduce the problem", 147457, true);
            this.stepsField = input3;
            input3.setMinLines(3);
            this.stepsField.setGravity(8388659);
            addField(bodyContainer, "Steps to reproduce", this.stepsField);
            EditText input4 = input("Optional: what should have happened instead", 147457, true);
            this.expectedField = input4;
            input4.setMinLines(3);
            this.expectedField.setGravity(8388659);
            addField(bodyContainer, "Expected behavior", this.expectedField);
            return bodyContainer;
        }

        private View contextBody() {
            LinearLayout bodyContainer = bodyContainer();
            String activeHost = activeHost();
            String currentRoute = currentRoute();
            addLockedRow(bodyContainer, "App", "Harley's Clan Forum v" + BuildInfo.VERSION + " • build " + BuildInfo.VERSION_CODE);
            addLockedRow(bodyContainer, "Package", getPackageName());
            addLockedRow(bodyContainer, "Android", Build.VERSION.RELEASE + " • API " + Build.VERSION.SDK_INT);
            addLockedRow(bodyContainer, "Device", Build.MANUFACTURER + " " + Build.MODEL);
            addLockedRow(bodyContainer, "Forum host", activeHost);
            addLockedRow(bodyContainer, "Current route", currentRoute);
            addLockedRow(bodyContainer, "Theme", this.prefs.getString("app_theme", "system"));
            TextView label = label("Context is shown here for transparency. Only the items selected under Privacy & send are added to the email.", 10, R.color.hcf_muted, false);
            label.setPadding(0, dp(4), 0, 0);
            bodyContainer.addView(label);
            return bodyContainer;
        }

        private View privacyBody() {
            LinearLayout bodyContainer = bodyContainer();
            CheckBox check = check("Include forum identity", this.identity.loggedIn, this.identity.loggedIn);
            this.includeIdentity = check;
            bodyContainer.addView(check);
            CheckBox check2 = check("Include sanitized app/device diagnostics", false, true);
            this.includeDiagnostics = check2;
            bodyContainer.addView(check2);
            CheckBox check3 = check("Include current forum route", false, true);
            this.includeRoute = check3;
            bodyContainer.addView(check3);
            TextView label = label("Passwords, cookies, authentication tokens and private message contents are never included. The app opens your email client so you can review or cancel before sending.", 10, R.color.hcf_muted, false);
            label.setPadding(dp(2), dp(2), dp(2), dp(10));
            bodyContainer.addView(label);
            bodyContainer.addView(actionButton("Preview Report", R.drawable.fa_list, new View.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SupportContactActivity.this.m206x8f236721(view);
                }
            }));
            View actionButton = actionButton("Continue to Email", R.drawable.fa_envelope, new View.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SupportContactActivity.this.m207x49d8d62(view);
                }
            });
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
            layoutParams.bottomMargin = 0;
            actionButton.setLayoutParams(layoutParams);
            bodyContainer.addView(actionButton);
            TextView label2 = label("Support email: harleytg.hq@gmail.com  •  tap to copy", 10, R.color.hcf_cyan_bright, true);
            label2.setGravity(17);
            label2.setPadding(0, dp(10), 0, 0);
            label2.setClickable(true);
            label2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    SupportContactActivity.this.m208x7a17b3a3(view);
                }
            });
            bodyContainer.addView(label2);
            return bodyContainer;
        }

        /* renamed from: lambda$privacyBody$1$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m206x8f236721(View view) {
            previewReport();
        }

        /* renamed from: lambda$privacyBody$2$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m207x49d8d62(View view) {
            composeEmail();
        }

        /* renamed from: lambda$privacyBody$3$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m208x7a17b3a3(View view) {
            copySupportEmail();
        }

        private View supportPanel(String str, String str2, int i, View view, boolean z) {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(12);
            linearLayout.setLayoutParams(layoutParams);
            final LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setGravity(16);
            linearLayout2.setClickable(true);
            linearLayout2.setFocusable(true);
            linearLayout2.setPadding(dp(15), dp(13), dp(12), dp(13));
            linearLayout2.setBackgroundResource(z ? R.drawable.settings_section_header_expanded : R.drawable.settings_section_header_collapsed);
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(i);
            imageView.setColorFilter(getColor(R.color.hcf_cyan_bright));
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(dp(24), dp(24));
            layoutParams2.rightMargin = dp(11);
            linearLayout2.addView(imageView, layoutParams2);
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            linearLayout3.addView(label(str, 14, R.color.hcf_accent_text, true));
            linearLayout3.addView(label(str2, 10, R.color.hcf_muted, false));
            linearLayout2.addView(linearLayout3, new LinearLayout.LayoutParams(0, -2, 1.0f));
            final TextView label = label("›", 22, R.color.hcf_cyan_bright, false);
            label.setGravity(17);
            label.setRotation(z ? 90.0f : 0.0f);
            linearLayout2.addView(label, new LinearLayout.LayoutParams(dp(28), -1));
            linearLayout.addView(linearLayout2);
            final LinearLayout linearLayout4 = new LinearLayout(this);
            linearLayout4.setOrientation(1);
            linearLayout4.setBackgroundResource(R.drawable.settings_section_body);
            linearLayout4.setPadding(dp(14), dp(14), dp(14), dp(14));
            if (view != null) {
                linearLayout4.addView(view, new LinearLayout.LayoutParams(-1, -2));
            }
            linearLayout4.setVisibility(z ? 0 : 8);
            linearLayout4.setAlpha(z ? 1.0f : 0.0f);
            linearLayout4.setTranslationY(z ? 0.0f : -dp(6));
            linearLayout.addView(linearLayout4);
            final boolean[] zArr = {z};
            linearLayout2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    SupportContactActivity.this.m209xae9de76a(zArr, label, linearLayout4, linearLayout2, view2);
                }
            });
            return linearLayout;
        }

        /* renamed from: lambda$supportPanel$5$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m209xae9de76a(boolean[] zArr, TextView textView, final LinearLayout linearLayout, final LinearLayout linearLayout2, View view) {
            if (zArr[0]) {
                zArr[0] = false;
                textView.animate().rotation(0.0f).setDuration(150L).start();
                linearLayout.animate().alpha(0.0f).translationY(-dp(6)).setDuration(150L).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        SupportContactActivity.lambda$supportPanel$4(linearLayout, linearLayout2);
                    }
                }).start();
                return;
            }
            zArr[0] = true;
            linearLayout2.setBackgroundResource(R.drawable.settings_section_header_expanded);
            linearLayout.setVisibility(0);
            linearLayout.setAlpha(0.0f);
            linearLayout.setTranslationY(-dp(6));
            textView.animate().rotation(90.0f).setDuration(170L).start();
            linearLayout.animate().alpha(1.0f).translationY(0.0f).setDuration(180L).start();
        }

        static /* synthetic */ void lambda$supportPanel$4(LinearLayout linearLayout, LinearLayout linearLayout2) {
            linearLayout.setVisibility(8);
            linearLayout2.setBackgroundResource(R.drawable.settings_section_header_collapsed);
        }

        private LinearLayout bodyContainer() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(0);
            return linearLayout;
        }

        private void addLockedRow(LinearLayout linearLayout, String str, String str2) {
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            linearLayout2.setBackgroundResource(R.drawable.identity_card_background);
            linearLayout2.setPadding(dp(12), dp(9), dp(12), dp(9));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(8);
            linearLayout2.setLayoutParams(layoutParams);
            TextView label = label(str, 9, R.color.hcf_meta, true);
            TextView label2 = label(str2, 12, R.color.hcf_text, false);
            label2.setPadding(0, dp(2), 0, 0);
            linearLayout2.addView(label);
            linearLayout2.addView(label2);
            linearLayout.addView(linearLayout2);
        }

        private void addField(LinearLayout linearLayout, String str, EditText editText) {
            linearLayout.addView(fieldLabel(str));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.bottomMargin = dp(11);
            linearLayout.addView(editText, layoutParams);
        }

        private TextView fieldLabel(String str) {
            TextView label = label(str, 10, R.color.hcf_meta, true);
            label.setPadding(dp(2), 0, 0, dp(4));
            return label;
        }

        private EditText input(String str, int i, boolean z) {
            EditText editText = new EditText(this);
            editText.setHint(str);
            editText.setHintTextColor(getColor(R.color.hcf_hint));
            editText.setTextColor(getColor(R.color.hcf_text));
            editText.setTextSize(13.0f);
            editText.setInputType(i);
            editText.setSingleLine(!z);
            if (!z) {
                editText.setImeOptions(5);
            }
            editText.setBackgroundResource(R.drawable.identity_card_background);
            editText.setPadding(dp(12), dp(10), dp(12), dp(10));
            return editText;
        }

        private CheckBox check(String str, boolean z, boolean z2) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(str);
            checkBox.setTextColor(getColor(R.color.hcf_text));
            checkBox.setTextSize(12.0f);
            checkBox.setChecked(z);
            checkBox.setEnabled(z2);
            checkBox.setPadding(0, dp(1), 0, dp(1));
            return checkBox;
        }

        private View actionButton(String str, int i, View.OnClickListener onClickListener) {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(0);
            linearLayout.setGravity(17);
            linearLayout.setBackgroundResource(R.drawable.button_background);
            linearLayout.setClickable(true);
            linearLayout.setFocusable(true);
            linearLayout.setPadding(dp(16), 0, dp(16), 0);
            linearLayout.setContentDescription(str);
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(i);
            imageView.setColorFilter(getColor(R.color.hcf_cyan_bright));
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            layoutParams.rightMargin = dp(10);
            linearLayout.addView(imageView, layoutParams);
            TextView label = label(str, 13, R.color.hcf_cyan_bright, true);
            label.setGravity(17);
            label.setIncludeFontPadding(false);
            linearLayout.addView(label, new LinearLayout.LayoutParams(-2, -2));
            linearLayout.setOnClickListener(onClickListener);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(52));
            layoutParams2.bottomMargin = dp(9);
            linearLayout.setLayoutParams(layoutParams2);
            return linearLayout;
        }

        private void previewReport() {
            final String buildReportBody = buildReportBody();
            if (buildReportBody == null) {
                return;
            }
            new AlertDialog.Builder(this).setTitle("Support report preview").setMessage(buildReportBody).setNegativeButton("Close", (DialogInterface.OnClickListener) null).setPositiveButton("Continue to Email", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.SupportContactActivity$$ExternalSyntheticLambda6
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    SupportContactActivity.this.m205xc9453f78(buildReportBody, dialogInterface, i);
                }
            }).show();
        }

        /* renamed from: lambda$previewReport$6$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
        /* synthetic */ void m205xc9453f78(String str, DialogInterface dialogInterface, int i) {
            openEmail(str);
        }

        private void composeEmail() {
            String buildReportBody = buildReportBody();
            if (buildReportBody != null) {
                openEmail(buildReportBody);
            }
        }

        private String buildReportBody() {
            String clean;
            String clean2;
            String obj = this.categoryField.getSelectedItem() == null ? "App issue" : this.categoryField.getSelectedItem().toString();
            String clean3 = clean(this.subjectField.getText().toString());
            String clean4 = clean(this.messageField.getText().toString());
            String clean5 = clean(this.stepsField.getText().toString());
            String clean6 = clean(this.expectedField.getText().toString());
            if (clean4.isEmpty()) {
                Toast.makeText(this, "Please describe what happened.", 0).show();
                this.messageField.requestFocus();
                return null;
            }
            if (this.identity.loggedIn) {
                clean = nonEmpty(this.identity.displayName, this.identity.username);
            } else {
                EditText editText = this.guestNameField;
                clean = clean(editText == null ? "" : editText.getText().toString());
            }
            if (this.identity.loggedIn) {
                clean2 = this.identity.email;
            } else {
                EditText editText2 = this.guestReplyEmailField;
                clean2 = clean(editText2 == null ? "" : editText2.getText().toString());
            }
            StringBuilder sb = new StringBuilder("Hello Harley's Clan Forum Support,\n\nSupport type: ");
            sb.append(obj);
            sb.append("\nSubject: ");
            if (!clean3.isEmpty()) {
                obj = clean3;
            }
            sb.append(obj);
            sb.append("\n\n--- What happened ---\n");
            sb.append(clean4);
            sb.append("\n\n");
            if (!clean5.isEmpty()) {
                sb.append("--- Steps to reproduce ---\n");
                sb.append(clean5);
                sb.append("\n\n");
            }
            if (!clean6.isEmpty()) {
                sb.append("--- Expected behavior ---\n");
                sb.append(clean6);
                sb.append("\n\n");
            }
            sb.append("--- Contact ---\nName: ");
            if (clean.isEmpty()) {
                clean = "Not provided";
            }
            sb.append(clean);
            sb.append("\nReply email: ");
            if (clean2.isEmpty()) {
                clean2 = "Not provided";
            }
            sb.append(clean2);
            sb.append('\n');
            CheckBox checkBox = this.includeIdentity;
            if (checkBox != null && checkBox.isChecked() && this.identity.loggedIn) {
                sb.append("\n--- Forum Identity ---\nDisplay name: ");
                String str = "Not exposed";
                sb.append(nonEmpty(this.identity.displayName, "Not exposed"));
                sb.append("\nUsername: ");
                if (!this.identity.username.isEmpty()) {
                    str = "@" + this.identity.username;
                }
                sb.append(str);
                sb.append('\n');
                if (!this.identity.email.isEmpty()) {
                    sb.append("Forum email: ");
                    sb.append(this.identity.email);
                    sb.append(this.identity.emailConfirmed ? " (verified)" : "");
                    sb.append('\n');
                }
                sb.append("Role: ");
                sb.append(this.identity.identityMetaLabel());
                sb.append('\n');
            }
            CheckBox checkBox2 = this.includeDiagnostics;
            if (checkBox2 != null && checkBox2.isChecked()) {
                sb.append("\n--- Sanitized Diagnostics ---\nApp: Harley's Clan Forum v" + BuildInfo.VERSION + "\nVersion code: " + BuildInfo.VERSION_CODE + "\nPackage: ");
                sb.append(getPackageName());
                sb.append("\nAndroid: ");
                sb.append(Build.VERSION.RELEASE);
                sb.append(" (API ");
                sb.append(Build.VERSION.SDK_INT);
                sb.append(")\nDevice: ");
                sb.append(Build.MANUFACTURER);
                sb.append(' ');
                sb.append(Build.MODEL);
                sb.append("\nForum host: ");
                sb.append(activeHost());
                sb.append("\nTheme: ");
                sb.append(this.prefs.getString("app_theme", "system"));
                sb.append("\nNotifications: ");
                sb.append(NotificationHelper.status(this));
                sb.append('\n');
            }
            CheckBox checkBox3 = this.includeRoute;
            if (checkBox3 != null && checkBox3.isChecked()) {
                sb.append("\n--- Current Route ---\n");
                sb.append(currentRoute());
                sb.append('\n');
            }
            sb.append("\nPrivacy: passwords, cookies, tokens and private-message content are not included.\nSent from Harley's Clan Forum Contact Support v2.");
            return sb.toString();
        }

        private void openEmail(String str) {
            String obj = this.categoryField.getSelectedItem() == null ? "App issue" : this.categoryField.getSelectedItem().toString();
            String clean = clean(this.subjectField.getText().toString());
            if (clean.isEmpty()) {
                clean = obj;
            }
            String str2 = "HCF Support • " + obj + " • v1.0 • " + clean;
            try {
                String str3 = "mailto:harleytg.hq@gmail.com?subject=" + Uri.encode(str2) + "&body=" + Uri.encode(str);
                Intent intent = new Intent("android.intent.action.SENDTO");
                intent.setData(Uri.parse(str3));
                intent.putExtra("android.intent.extra.EMAIL", new String[]{SUPPORT_EMAIL});
                intent.putExtra("android.intent.extra.SUBJECT", str2);
                intent.putExtra("android.intent.extra.TEXT", str);
                startActivity(Intent.createChooser(intent, "Send support email"));
                AppLogger.info(this, "support_contact_v2", "mailto recipient=harleytg.hq@gmail.com");
            } catch (Throwable th) {
                Toast.makeText(this, "No email app is available. Email harleytg.hq@gmail.com", 1).show();
                AppLogger.error(this, "support_contact_v2", th.getClass().getSimpleName());
            }
        }

        private void copySupportEmail() {
            try {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
                if (clipboardManager != null) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF support email", SUPPORT_EMAIL));
                }
                Toast.makeText(this, "Support email copied.", 0).show();
            } catch (Throwable unused) {
                Toast.makeText(this, SUPPORT_EMAIL, 1).show();
            }
        }

        private String activeHost() {
            String string = this.prefs.getString("active_host", "forum.harleytg.com");
            return ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
        }

        private String currentRoute() {
            String string = this.prefs.getString("last_recoverable_url", "");
            if (string == null || string.trim().isEmpty()) {
                return "https://" + activeHost() + "/";
            }
            return AppLogger.safeUrl(string);
        }

        private TextView label(String str, int i, int i2, boolean z) {
            TextView textView = new TextView(this);
            if (str == null) {
                str = "";
            }
            textView.setText(str);
            textView.setTextSize(i);
            textView.setTextColor(getColor(i2));
            if (z) {
                textView.setTypeface(null, 1);
            }
            return textView;
        }

        private static String clean(String str) {
            return str == null ? "" : str.trim();
        }

        private static String nonEmpty(String str, String str2) {
            return (str == null || str.trim().isEmpty()) ? str2 : str.trim();
        }

        private int dp(int i) {
            return Math.round(i * getResources().getDisplayMetrics().density);
        }
    }
}

// ---- Consolidated from HcfSettingsImportUi.java ----
/**
 * Adds HCF settings backup/import controls and account-scoped App Settings profiles.
 *
 * Every signed-in forum username keeps an independent set of user-facing App Settings.
 * Guest has a separate profile. Account/session data is never copied between profiles.
 */
final class HcfSettingsImportUi {
    private static final String SETUP_TAG = "hcf_setup_import_settings";
    private static final String SETTINGS_TAG = "hcf_settings_backup_transfer";
    private static final String REFRESH_PREF = "settings_transfer_refresh_ui";
    private static final WeakHashMap<Activity, Boolean> SETTINGS_OBSERVERS = new WeakHashMap<>();
    private static boolean registered;

    private HcfSettingsImportUi() {}

    /** Starts the account-scoped settings system as soon as the application process starts. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            UserSettingsProfiles.install(appContext);
            if (registered || !(appContext instanceof Application)) return true;
            registered = true;
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityResumed(Activity activity) {
                    try {
                        boolean profileChanged = UserSettingsProfiles.ensureActiveProfile(activity);
                        if (profileChanged && activity instanceof HcfSubActivities.SettingsActivity) {
                            activity.recreate();
                            return;
                        }
                        if (activity instanceof HcfForum.SetupActivity) {
                            if (consumeRefresh(activity)) {
                                activity.recreate();
                                return;
                            }
                            injectSetupImport(activity);
                        } else if (activity instanceof HcfSubActivities.SettingsActivity) {
                            if (consumeRefresh(activity)) {
                                activity.recreate();
                                return;
                            }
                            installSettingsObserver(activity);
                            injectAdvancedSettingsTransfer(activity);
                        }
                    } catch (Throwable error) {
                        AppLogger.warn(activity, "settings_transfer_ui", error.getClass().getSimpleName());
                    }
                }

                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                @Override
                public void onActivityDestroyed(Activity activity) {
                    synchronized (SETTINGS_OBSERVERS) {
                        SETTINGS_OBSERVERS.remove(activity);
                    }
                }
            });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    public static final class TransferActivity extends Activity {
        private static final String EXTRA_MODE = "mode";
        private static final String EXTRA_FROM_SETUP = "from_setup";
        private static final String MODE_IMPORT = "import";
        private static final String MODE_EXPORT = "export";
        private static final int REQUEST_IMPORT = 2911;
        private static final int REQUEST_EXPORT = 2912;
        private String mode;

        static void startImport(Activity activity, boolean fromSetup) {
            Intent intent = new Intent(activity, TransferActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_IMPORT);
            intent.putExtra(EXTRA_FROM_SETUP, fromSetup);
            activity.startActivity(intent);
        }

        static void startExport(Activity activity) {
            Intent intent = new Intent(activity, TransferActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_EXPORT);
            activity.startActivity(intent);
        }

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            ThemeManager.apply(this);
            UserSettingsProfiles.ensureActiveProfile(this);
            mode = getIntent() == null ? MODE_IMPORT : getIntent().getStringExtra(EXTRA_MODE);
            if (!MODE_EXPORT.equals(mode)) mode = MODE_IMPORT;
            if (state == null) launchPicker();
        }

        private void launchPicker() {
            try {
                Intent intent;
                if (MODE_EXPORT.equals(mode)) {
                    intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, suggestedExportFileName());
                    startActivityForResult(intent, REQUEST_EXPORT);
                } else {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "text/json", "text/plain", "application/octet-stream"
                    });
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(intent, REQUEST_IMPORT);
                }
            } catch (Throwable error) {
                Toast.makeText(this, "No compatible file picker is available.", Toast.LENGTH_SHORT).show();
                AppLogger.warn(this, "settings_transfer_picker", error.getClass().getSimpleName());
                finish();
            }
        }

        private String suggestedExportFileName() {
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, 0);
            String username = readStringPreference(prefs, AppPrefs.IDENTITY_USERNAME);
            boolean signedIn = isSignedIn(prefs);
            String accountPart = signedIn && !username.isEmpty()
                    ? "@" + safeFilePart(username, "User")
                    : "Guest";

            String channel = BuildInfo.DEFAULT_UPDATE_CHANNEL == null
                    ? ""
                    : BuildInfo.DEFAULT_UPDATE_CHANNEL.trim();
            String channelPart = ("dev".equalsIgnoreCase(channel) || "beta".equalsIgnoreCase(channel))
                    ? "-Beta"
                    : "";
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());

            return "HCF-Settings-" + accountPart
                    + channelPart
                    + "-v" + shortVersionCode()
                    + "-" + stamp
                    + ".json";
        }

        private static boolean isSignedIn(SharedPreferences prefs) {
            try {
                if (prefs.getBoolean(AppPrefs.IDENTITY_LOGGED_IN, false)) return true;
            } catch (Throwable ignored) {}
            return !readStringPreference(prefs, AppPrefs.SESSION_USER_ID).isEmpty();
        }

        private static String readStringPreference(SharedPreferences prefs, String key) {
            try {
                String value = prefs.getString(key, "");
                return value == null ? "" : value.trim();
            } catch (Throwable ignored) {
                return "";
            }
        }

        private static String safeFilePart(String value, String fallback) {
            String part = value == null ? "" : value.trim();
            part = part.replaceAll("[^A-Za-z0-9._-]+", "-");
            part = part.replaceAll("^-+|-+$", "");
            return part.isEmpty() ? fallback : part;
        }

        private static long shortVersionCode() {
            long code = BuildInfo.VERSION_CODE;
            if (code >= 10000000L && code < 20000000L) {
                return 100L + (code - 10000000L);
            }
            return code;
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finish();
                return;
            }
            Uri uri = data.getData();
            if (requestCode == REQUEST_IMPORT) {
                HcfSettingsTransfer.Result result = HcfSettingsTransfer.importFromUri(this, uri);
                Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show();
                if (result.ok) {
                    UserSettingsProfiles.captureActiveProfile(this);
                    try {
                        NotificationSyncScheduler.apply(this);
                    } catch (Throwable ignored) {}
                    getSharedPreferences(AppPrefs.FILE, 0).edit().putBoolean(REFRESH_PREF, true).apply();
                    AppLogger.info(this, "settings_transfer", "import_ok | " + result.summary());
                } else {
                    AppLogger.warn(this, "settings_transfer", "import_failed | " + result.message);
                }
            } else if (requestCode == REQUEST_EXPORT) {
                try {
                    UserSettingsProfiles.captureActiveProfile(this);
                    HcfSettingsTransfer.exportToUri(this, uri);
                    Toast.makeText(this, "HCF settings backup exported.", Toast.LENGTH_SHORT).show();
                    AppLogger.info(this, "settings_transfer", "export_ok | " + UserSettingsProfiles.displayLabel(this));
                } catch (Throwable error) {
                    Toast.makeText(this, "HCF could not export the settings backup.", Toast.LENGTH_LONG).show();
                    AppLogger.warn(this, "settings_transfer", "export_failed | " + error.getClass().getSimpleName());
                }
            }
            finish();
        }
    }

    private static void injectSetupImport(Activity activity) {
        ViewGroup content = findScrollContent(activity);
        if (content == null || findTagged(content, SETUP_TAG) != null) return;

        LinearLayout card = card(activity, SETUP_TAG);
        addTitle(activity, card, "Import Settings", "Restore App Settings into the current account profile.");
        TextView detail = text(activity,
                "Current settings profile: " + UserSettingsProfiles.displayLabel(activity)
                        + "\nChoose an HCF settings backup to restore the user-configurable settings for this profile.",
                11,
                activity.getColor(R.color.hcf_muted));
        detail.setPadding(0, 0, 0, dp(activity, 10));
        card.addView(detail);

        Button importButton = actionButton(activity, "Import Settings   ›");
        importButton.setOnClickListener(v -> TransferActivity.startImport(activity, true));
        card.addView(importButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        int index = Math.min(1, content.getChildCount());
        content.addView(card, index);
        AppLogger.info(activity, "settings_transfer_ui", "setup_control_added");
    }

    private static void installSettingsObserver(final Activity activity) {
        synchronized (SETTINGS_OBSERVERS) {
            if (SETTINGS_OBSERVERS.containsKey(activity)) return;
            SETTINGS_OBSERVERS.put(activity, Boolean.TRUE);
        }
        final View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    injectAdvancedSettingsTransfer(activity);
                } catch (Throwable error) {
                    AppLogger.warn(activity, "settings_transfer_advanced", error.getClass().getSimpleName());
                }
            }
        });
    }

    /** Adds Backup & Transfer only inside Advanced & About using the native settings panel style. */
    private static void injectAdvancedSettingsTransfer(Activity activity) {
        if (!"advanced".equals(readStringField(activity, "currentSettingsSection"))) return;
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null || findTagged(content, SETTINGS_TAG) != null) return;

        LinearLayout inner = nativeCard(activity);
        inner.setTag(SETTINGS_TAG + "_content");

        View nativeTitle = nativeSectionTitle(activity,
                "Backup & Transfer",
                "Per-account App Settings backup and restore");
        if (nativeTitle != null) inner.addView(nativeTitle);
        else addTitle(activity, inner, "Backup & Transfer", "Per-account App Settings backup and restore.");

        TextView profile = text(activity,
                "Settings profile: " + UserSettingsProfiles.displayLabel(activity),
                11,
                activity.getColor(R.color.hcf_accent_text));
        profile.setTypeface(null, 1);
        profile.setPadding(0, 0, 0, dp(activity, 9));
        inner.addView(profile);

        Button exportButton = nativeActionButton(activity, "Export Settings", v -> TransferActivity.startExport(activity));
        inner.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        Button importButton = nativeActionButton(activity, "Import Settings", v -> TransferActivity.startImport(activity, false));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        importLp.topMargin = dp(activity, 8);
        inner.addView(importButton, importLp);

        TextView note = text(activity,
                "Each signed-in forum username has its own App Settings profile. Guest has a separate profile. Switching accounts automatically saves the previous profile and restores the new one. Login/session data is never transferred.",
                10,
                activity.getColor(R.color.hcf_hint));
        note.setPadding(0, dp(activity, 9), 0, 0);
        inner.addView(note);

        View panel = nativeConnectedSettingsPanel(activity,
                "Backup & Transfer",
                "Per-user settings • " + UserSettingsProfiles.displayLabel(activity),
                inner,
                false);
        panel.setTag(SETTINGS_TAG);

        int aboutIndex = directChildContainingText(content, "About Harley's Clan Forum");
        if (aboutIndex < 0) aboutIndex = content.getChildCount();
        content.addView(panel, aboutIndex);
        AppLogger.info(activity, "settings_transfer_ui", "advanced_control_added | " + UserSettingsProfiles.displayLabel(activity));
    }

    /**
     * Keeps the existing global AppPrefs contract intact while making user-facing settings
     * account-scoped. This avoids changing every settings consumer in the app.
     */
    private static final class UserSettingsProfiles implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String ACTIVE_PROFILE_KEY = "settings_profile_active";
        private static final String PROFILE_FILE_PREFIX = "hcf_user_settings_profile_";
        private static final String PROFILE_INITIALIZED = "__initialized";
        private static final String PROFILE_LABEL = "__label";

        private static final Set<String> BOOLEAN_KEYS = new LinkedHashSet<>(Arrays.asList(
                AppPrefs.AUTO_FAILOVER,
                AppPrefs.BACKGROUND_NOTIFICATION_SYNC,
                AppPrefs.COMPACT_HEADER,
                AppPrefs.EXTERNAL_LINKS,
                AppPrefs.LIVE_FORUM_UPDATES,
                AppPrefs.NOTIFICATIONS_ENABLED,
                AppPrefs.PERFORMANCE_MODE,
                AppPrefs.SHOW_BOTTOM_NAV,
                AppPrefs.SHOW_STARTUP_SCREEN,
                AppPrefs.WIDGET_FOLLOW_APP_THEME,
                HcfWidget.PREF_SHOW_CONNECTED_USERNAME,
                AppPrefs.SHOW_URL_BAR,
                AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION,
                AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT,
                AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS,
                AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS,
                AppPrefs.TELEMETRY_ENABLED,
                AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL,
                AppPrefs.TELEMETRY_INCLUDE_EMAIL,
                AppPrefs.TELEMETRY_INCLUDE_IDENTITY,
                AppPrefs.TELEMETRY_INCLUDE_ROUTE,
                AppPrefs.UPDATE_AUTO_CHECK,
                AppPrefs.UPDATE_AUTO_DOWNLOAD,
                AppPrefs.UPDATE_AUTO_INSTALL
        ));

        private static final Set<String> STRING_KEYS = new LinkedHashSet<>(Arrays.asList(
                AppPrefs.APP_THEME,
                AppPrefs.NATIVE_ACCENT,
                AppPrefs.PERFORMANCE_PROFILE,
                AppPrefs.TELEMETRY_LEVEL,
                AppPrefs.FIREBASE_CONFIG_URL
        ));

        private static UserSettingsProfiles instance;
        private final Context appContext;
        private final SharedPreferences global;
        private boolean switching;

        private UserSettingsProfiles(Context context) {
            appContext = context.getApplicationContext();
            global = appContext.getSharedPreferences(AppPrefs.FILE, 0);
        }

        static synchronized void install(Context context) {
            if (context == null) return;
            if (instance == null) {
                instance = new UserSettingsProfiles(context);
                instance.global.registerOnSharedPreferenceChangeListener(instance);
            }
            instance.ensureProfile();
        }

        static boolean ensureActiveProfile(Context context) {
            install(context);
            return instance != null && instance.ensureProfile();
        }

        static void captureActiveProfile(Context context) {
            install(context);
            if (instance != null) instance.captureActive();
        }

        static String displayLabel(Context context) {
            install(context);
            if (instance == null) return "Guest";
            String username = readString(instance.global, AppPrefs.IDENTITY_USERNAME);
            if (instance.signedIn() && !username.isEmpty()) return "@" + username;
            return "Guest";
        }

        private synchronized boolean ensureProfile() {
            if (switching) return false;
            String target = desiredProfileKey();
            if (target.isEmpty()) return false; // Identity is currently syncing; keep the existing profile.
            String active = readString(global, ACTIVE_PROFILE_KEY);

            if (active.isEmpty()) {
                switching = true;
                try {
                    SharedPreferences targetPrefs = profilePrefs(target);
                    if (targetPrefs.getBoolean(PROFILE_INITIALIZED, false)) {
                        loadProfile(target);
                    } else {
                        saveGlobalToProfile(target);
                    }
                    global.edit().putString(ACTIVE_PROFILE_KEY, target).commit();
                    AppLogger.info(appContext, "settings_profile_init", displayLabelNoInstall());
                } finally {
                    switching = false;
                }
                return false;
            }

            if (active.equals(target)) return false;

            switching = true;
            try {
                saveGlobalToProfile(active);
                SharedPreferences targetPrefs = profilePrefs(target);
                if (targetPrefs.getBoolean(PROFILE_INITIALIZED, false)) {
                    loadProfile(target);
                } else {
                    clearGlobalUserSettings();
                    targetPrefs.edit()
                            .putBoolean(PROFILE_INITIALIZED, true)
                            .putString(PROFILE_LABEL, targetLabel())
                            .commit();
                }
                global.edit().putString(ACTIVE_PROFILE_KEY, target).commit();
                try {
                    NotificationSyncScheduler.apply(appContext);
                } catch (Throwable ignored) {}
                AppLogger.info(appContext, "settings_profile_switch", active + " -> " + target);
                return true;
            } finally {
                switching = false;
            }
        }

        private synchronized void captureActive() {
            if (switching) return;
            String active = readString(global, ACTIVE_PROFILE_KEY);
            if (active.isEmpty()) {
                ensureProfile();
                active = readString(global, ACTIVE_PROFILE_KEY);
            }
            if (!active.isEmpty()) saveGlobalToProfile(active);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (switching || key == null) return;
            if (AppPrefs.IDENTITY_USERNAME.equals(key)
                    || AppPrefs.IDENTITY_LOGGED_IN.equals(key)
                    || AppPrefs.SESSION_USER_ID.equals(key)) {
                ensureProfile();
                return;
            }
            if (!isUserSettingKey(key)) return;
            String active = readString(global, ACTIVE_PROFILE_KEY);
            if (active.isEmpty()) {
                ensureProfile();
                active = readString(global, ACTIVE_PROFILE_KEY);
            }
            if (!active.isEmpty()) saveSingleSetting(active, key);
        }

        private String desiredProfileKey() {
            boolean signedIn = signedIn();
            String username = readString(global, AppPrefs.IDENTITY_USERNAME);
            if (signedIn) {
                if (username.isEmpty()) return "";
                return "user:" + username.toLowerCase(Locale.US);
            }
            return "guest";
        }

        private boolean signedIn() {
            try {
                if (global.getBoolean(AppPrefs.IDENTITY_LOGGED_IN, false)) return true;
            } catch (Throwable ignored) {}
            return !readString(global, AppPrefs.SESSION_USER_ID).isEmpty();
        }

        private String targetLabel() {
            String username = readString(global, AppPrefs.IDENTITY_USERNAME);
            return signedIn() && !username.isEmpty() ? "@" + username : "Guest";
        }

        private String displayLabelNoInstall() {
            return targetLabel();
        }

        private SharedPreferences profilePrefs(String profileKey) {
            String safe = profileKey.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_");
            if (safe.isEmpty()) safe = "profile";
            safe = safe + "_" + Integer.toHexString(profileKey.hashCode());
            return appContext.getSharedPreferences(PROFILE_FILE_PREFIX + safe, 0);
        }

        private void saveGlobalToProfile(String profileKey) {
            if (profileKey == null || profileKey.isEmpty()) return;
            Map<String, ?> all = global.getAll();
            SharedPreferences.Editor out = profilePrefs(profileKey).edit().clear();
            out.putBoolean(PROFILE_INITIALIZED, true);
            out.putString(PROFILE_LABEL, "guest".equals(profileKey) ? "Guest" : profileKey.substring(profileKey.indexOf(':') + 1));
            for (String key : BOOLEAN_KEYS) {
                Object value = all.get(key);
                if (value instanceof Boolean) out.putBoolean(key, ((Boolean) value).booleanValue());
            }
            for (String key : STRING_KEYS) {
                Object value = all.get(key);
                if (value instanceof String) out.putString(key, (String) value);
            }
            out.commit();
        }

        private void saveSingleSetting(String profileKey, String key) {
            Object value = global.getAll().get(key);
            SharedPreferences.Editor out = profilePrefs(profileKey).edit();
            out.putBoolean(PROFILE_INITIALIZED, true);
            if (value instanceof Boolean) out.putBoolean(key, ((Boolean) value).booleanValue());
            else if (value instanceof String) out.putString(key, (String) value);
            else out.remove(key);
            out.apply();
        }

        private void loadProfile(String profileKey) {
            SharedPreferences source = profilePrefs(profileKey);
            Map<String, ?> saved = source.getAll();
            SharedPreferences.Editor edit = global.edit();
            for (String key : BOOLEAN_KEYS) edit.remove(key);
            for (String key : STRING_KEYS) edit.remove(key);
            for (String key : BOOLEAN_KEYS) {
                Object value = saved.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, ((Boolean) value).booleanValue());
            }
            for (String key : STRING_KEYS) {
                Object value = saved.get(key);
                if (value instanceof String) edit.putString(key, (String) value);
            }
            edit.commit();
        }

        private void clearGlobalUserSettings() {
            SharedPreferences.Editor edit = global.edit();
            for (String key : BOOLEAN_KEYS) edit.remove(key);
            for (String key : STRING_KEYS) edit.remove(key);
            edit.commit();
        }

        private static boolean isUserSettingKey(String key) {
            return BOOLEAN_KEYS.contains(key) || STRING_KEYS.contains(key);
        }

        private static String readString(SharedPreferences prefs, String key) {
            try {
                String value = prefs.getString(key, "");
                return value == null ? "" : value.trim();
            } catch (Throwable ignored) {
                return "";
            }
        }
    }

    private static String readStringField(Activity activity, String fieldName) {
        try {
            Field field = activity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static ViewGroup readViewGroupField(Activity activity, String fieldName) {
        try {
            Field field = activity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof ViewGroup ? (ViewGroup) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LinearLayout nativeCard(Activity activity) {
        try {
            Method method = activity.getClass().getDeclaredMethod("card");
            method.setAccessible(true);
            Object value = method.invoke(activity);
            if (value instanceof LinearLayout) return (LinearLayout) value;
        } catch (Throwable ignored) {}
        return card(activity, SETTINGS_TAG + "_fallback");
    }

    private static View nativeSectionTitle(Activity activity, String title, String subtitle) {
        try {
            Method method = activity.getClass().getDeclaredMethod("sectionTitle", String.class, String.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle);
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Button nativeActionButton(Activity activity, String title, View.OnClickListener listener) {
        try {
            Method method = activity.getClass().getDeclaredMethod("actionButton", String.class, View.OnClickListener.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, listener);
            if (value instanceof Button) return (Button) value;
        } catch (Throwable ignored) {}
        Button fallback = actionButton(activity, title + "   ›");
        fallback.setOnClickListener(listener);
        return fallback;
    }

    private static View nativeConnectedSettingsPanel(Activity activity, String title, String subtitle, View inner, boolean expanded) {
        try {
            Method method = activity.getClass().getDeclaredMethod(
                    "connectedSettingsPanel", String.class, String.class, View.class, boolean.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle, inner, expanded);
            if (value instanceof View) return (View) value;
        } catch (Throwable ignored) {}
        return inner;
    }

    private static int directChildContainingText(ViewGroup parent, String expected) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (containsText(parent.getChildAt(i), expected)) return i;
        }
        return -1;
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && expected.contentEquals(value)) return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }

    private static boolean consumeRefresh(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.FILE, 0);
        if (!prefs.getBoolean(REFRESH_PREF, false)) return false;
        prefs.edit().remove(REFRESH_PREF).apply();
        return true;
    }

    private static ViewGroup findScrollContent(Activity activity) {
        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        ScrollView scroll = findFirstScroll(root);
        if (scroll == null || scroll.getChildCount() == 0) return null;
        View child = scroll.getChildAt(0);
        return child instanceof ViewGroup ? (ViewGroup) child : null;
    }

    private static ScrollView findFirstScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findFirstScroll(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static View findTagged(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static LinearLayout card(Activity activity, String tag) {
        LinearLayout card = new LinearLayout(activity);
        card.setTag(tag);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(activity, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private static void addTitle(Activity activity, LinearLayout card, String title, String subtitle) {
        TextView titleView = text(activity, title, 15, activity.getColor(R.color.hcf_cyan_bright));
        titleView.setTypeface(null, 1);
        card.addView(titleView);
        TextView subtitleView = text(activity, subtitle, 11, activity.getColor(R.color.hcf_muted));
        subtitleView.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        card.addView(subtitleView);
    }

    private static Button actionButton(Activity activity, String label) {
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(12.0f);
        button.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button.setBackgroundResource(R.drawable.error_secondary_button_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private static TextView text(Activity activity, String value, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
