package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.net.URL;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/**
 * Native startup gate for Harley's Clan Forum.
 *
 * Startup order:
 *   Welcome -> App Setup -> native system checks -> native chrome handoff -> MainActivity/WebView.
 *
 * No forum URL is loaded by this activity. The forum request begins only after
 * the system checks and handoff animations have completed.
 */
public final class HcfStartupActivity extends ThemedActivity {
    private static final int REQUEST_WELCOME = 4101;
    private static final int REQUEST_SETUP = 4102;

    private static final long BACKDROP_REVEAL_MS = 120L;
    private static final long HEADER_FADE_MS = 180L;
    private static final long URL_FADE_MS = 180L;
    private static final long LOADER_FADE_MS = 220L;
    private static final long WEBVIEW_HANDOFF_DELAY_MS = 80L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private View topAppBar;
    private View urlBar;
    private View loaderBackdrop;
    private View loaderOverlay;
    private LinearLayout loaderPanel;
    private TextView loaderTitle;
    private TextView loaderStatus;
    private TextView loaderDetail;
    private ProgressBar loaderProgress;
    private Button retryButton;
    private WebView startupWebView;

    private boolean gateInProgress;
    private boolean loaderStarted;
    private boolean handoffStarted;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.apply(this);
        prefs = getSharedPreferences(AppPrefs.FILE, 0);

        int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        try {
            // Reuse the real MainActivity layout so the header and URL bar shown
            // during handoff are the exact same native UI the user sees next.
            setContentView(R.layout.activity_main);
            prepareNativeChrome();
            installStartupOverlay();
        } catch (Throwable error) {
            AppLogger.crash(this, error);
            showEmergencyStartupFailure(error);
            return;
        }

        if (state == null && (SetupCenter.shouldShowWelcome(this) || SetupCenter.shouldAutoLaunch(this))) {
            // Prevent a one-frame loader flash behind first-run Welcome / Setup.
            getWindow().getDecorView().setAlpha(0.0f);
        }

        AppLogger.info(this, "startup_gate", "created | " + BuildInfo.VERSION_BUILD_LINE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.post(new Runnable() {
            @Override public void run() {
                advanceStartupGate();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WELCOME || requestCode == REQUEST_SETUP) {
            gateInProgress = false;
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    advanceStartupGate();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        destroyStartupWebView();
        super.onDestroy();
    }

    private void advanceStartupGate() {
        if (destroyed || isFinishing() || isDestroyed() || gateInProgress || loaderStarted || handoffStarted) {
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

        if (topAppBar != null) topAppBar.setAlpha(0.0f);
        if (urlBar != null) urlBar.setAlpha(0.0f);

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

        String host = preferredHost();
        TextView hostBadge = findViewById(R.id.hostBadge);
        if (hostBadge != null) {
            hostBadge.setText(SetupCenter.BACKUP_FORUM_HOST.equalsIgnoreCase(host) ? "Backup" : "Primary");
        }

        EditText currentUrl = findViewById(R.id.currentUrlText);
        if (currentUrl != null) {
            currentUrl.setText("https://" + host + "/");
            currentUrl.setFocusable(false);
            currentUrl.setCursorVisible(false);
        }

        TextView subtitle = findViewById(R.id.appHeaderSubtitle);
        if (subtitle != null) {
            subtitle.setText("Native startup • System checks");
        }
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
        loaderPanel.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum logo");
        loaderPanel.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));

        TextView brand = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
        brand.setTypeface(null, 1);
        brand.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
        brandLp.topMargin = dp(14);
        loaderPanel.addView(brand, brandLp);

        loaderTitle = text("Starting Harley's Clan Forum", 21, getColor(R.color.hcf_text));
        loaderTitle.setTypeface(null, 1);
        loaderTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(6);
        loaderPanel.addView(loaderTitle, titleLp);

        loaderStatus = text("Waiting for startup gate…", 13, getColor(R.color.hcf_cyan_bright));
        loaderStatus.setTypeface(null, 1);
        loaderStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(18);
        loaderPanel.addView(loaderStatus, statusLp);

        loaderDetail = text("Welcome and App Setup run before native system initialization.", 11,
                getColor(R.color.hcf_muted));
        loaderDetail.setGravity(Gravity.CENTER);
        loaderDetail.setLineSpacing(0.0f, 1.12f);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(6);
        loaderPanel.addView(loaderDetail, detailLp);

        loaderProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loaderProgress.setIndeterminate(false);
        loaderProgress.setMax(100);
        loaderProgress.setProgress(0);
        loaderProgress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
        progressLp.topMargin = dp(22);
        loaderPanel.addView(loaderProgress, progressLp);

        TextView build = text(BuildInfo.VERSION_BUILD_LINE, 9, getColor(R.color.hcf_hint));
        build.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildLp = new LinearLayout.LayoutParams(-1, -2);
        buildLp.topMargin = dp(14);
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
                retryButton.setVisibility(View.GONE);
                loaderStarted = false;
                startSystemLoader();
            }
        });
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-1, dp(50));
        retryLp.topMargin = dp(18);
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
        if (loaderStarted || handoffStarted || destroyed) return;
        loaderStarted = true;

        publishStage(4, "Loading app preferences", "Applying theme, performance and saved native settings.");
        AppLogger.info(this, "startup_loader", "begin");

        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                runSystemChecks();
            }
        }, "hcf-startup-checks");
        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
    }

    private void runSystemChecks() {
        try {
            // 1. Preferences / app state.
            prefs.getAll().size();
            publishStage(12, "Loading native configuration", "Preferences and app configuration are readable.");

            // 2. WebView engine availability. The hidden WebView in activity_main is never navigated.
            PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
            if (webViewPackage == null || TextUtils.isEmpty(webViewPackage.packageName)) {
                failStartup("Android System WebView is unavailable",
                        "HCF cannot open the forum until Android has a working WebView provider.");
                return;
            }
            publishStage(24, "Checking WebView engine", "WebView provider: " + webViewPackage.packageName);

            // 3. Restore forum identity/session metadata without loading a URL.
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            String identityText = identity != null && identity.loggedIn
                    ? "Restored signed-in forum identity."
                    : "Guest forum session ready.";
            publishStage(36, "Restoring forum session", identityText);

            // 4. Android app-link and secure-update capability checks.
            SetupCenter.ForumLinksState links = SetupCenter.forumLinksState(this);
            boolean canInstallUpdates = AppSecurity.canInstallUpdates(this);
            String integrationDetail = (links != null && links.ready ? "Forum links ready" : "Forum links managed by Android")
                    + " • secure installer " + (canInstallUpdates ? "ready" : "permission not granted");
            publishStage(49, "Checking Android integration", integrationDetail);

            // 5. Notification system initialization.
            try {
                NotificationHelper.refreshChannels(this);
                NotificationSyncScheduler.apply(this);
                publishStage(61, "Starting notification systems", "Notification channels and background alert schedule checked.");
            } catch (Throwable notificationError) {
                AppLogger.warn(this, "startup_notifications", notificationError.getClass().getSimpleName());
                publishStage(61, "Checking notification systems", "Notifications are optional; startup can continue.");
            }

            // 6. Update system initialization.
            try {
                UpdateScheduler.apply(this);
                AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(this);
                publishStage(72, "Starting update system", "Update scheduler and downloaded-update state checked.");
            } catch (Throwable updateError) {
                AppLogger.warn(this, "startup_updates", updateError.getClass().getSimpleName());
                publishStage(72, "Checking update system", "Update checks can recover after the forum opens.");
            }

            // 7. Previous crash/recovery state.
            try {
                TelemetryService.handlePendingCrash(this);
                publishStage(80, "Checking recovery state", "Crash recovery and diagnostics state checked.");
            } catch (Throwable recoveryError) {
                AppLogger.warn(this, "startup_recovery", recoveryError.getClass().getSimpleName());
                publishStage(80, "Checking recovery state", "No blocking recovery action is required.");
            }

            // 8. Network and forum host checks. These do not load the forum page.
            boolean network = hasInternetNetwork();
            if (!network) {
                AppLogger.warn(this, "startup_network", "no active internet network");
                publishStage(92, "Checking forum connection", "No active internet connection; HCF recovery UI remains available.");
            } else {
                String savedHost = preferredHost();
                boolean primaryHealthy = probeHost(SetupCenter.PRIMARY_FORUM_HOST);
                boolean backupHealthy = probeHost(SetupCenter.BACKUP_FORUM_HOST);

                if (!primaryHealthy && backupHealthy) {
                    prefs.edit().putString("active_host", SetupCenter.BACKUP_FORUM_HOST).apply();
                } else if (primaryHealthy
                        && !SetupCenter.PRIMARY_FORUM_HOST.equalsIgnoreCase(savedHost)
                        && !SetupCenter.BACKUP_FORUM_HOST.equalsIgnoreCase(savedHost)) {
                    prefs.edit().putString("active_host", SetupCenter.PRIMARY_FORUM_HOST).apply();
                }

                String detail;
                if (primaryHealthy && backupHealthy) {
                    detail = "Primary and backup forum hosts responded.";
                } else if (primaryHealthy) {
                    detail = "Primary forum host responded; backup is currently unavailable.";
                } else if (backupHealthy) {
                    detail = "Primary is unavailable; backup forum host is ready.";
                } else {
                    detail = "Hosts did not respond to the startup probe; the forum recovery system will retry.";
                }
                publishStage(92, "Checking forum connection", detail);
            }

            publishStage(100, "Systems ready", "Native startup complete. Preparing the forum interface.");
            AppLogger.info(this, "startup_loader", "systems_ready");
            mainHandler.post(new Runnable() {
                @Override public void run() {
                    beginChromeHandoff();
                }
            });
        } catch (Throwable error) {
            AppLogger.error(this, "startup_loader", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            failStartup("Startup check failed",
                    error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " • " + error.getMessage()));
        }
    }

    private void beginChromeHandoff() {
        if (destroyed || isFinishing() || isDestroyed() || handoffStarted) return;
        handoffStarted = true;

        TextView subtitle = findViewById(R.id.appHeaderSubtitle);
        if (subtitle != null) {
            subtitle.setText("Live forum • " + (SetupCenter.BACKUP_FORUM_HOST.equalsIgnoreCase(preferredHost()) ? "Backup" : "Primary"));
        }

        // Reveal the real native shell beneath the loader, then stage the chrome.
        loaderBackdrop.animate()
                .alpha(0.0f)
                .setDuration(BACKDROP_REVEAL_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        animateHeaderIn();
                    }
                })
                .start();
    }

    private void animateHeaderIn() {
        if (topAppBar == null) {
            animateUrlBarIn();
            return;
        }
        topAppBar.animate()
                .alpha(1.0f)
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
        urlBar.animate()
                .alpha(1.0f)
                .setDuration(URL_FADE_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        fadeLoaderOut();
                    }
                })
                .start();
    }

    private void fadeLoaderOut() {
        if (loaderPanel == null) {
            scheduleForumHandoff();
            return;
        }
        loaderPanel.animate()
                .alpha(0.0f)
                .setDuration(LOADER_FADE_MS)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (loaderOverlay != null) loaderOverlay.setVisibility(View.GONE);
                        scheduleForumHandoff();
                    }
                })
                .start();
    }

    private void scheduleForumHandoff() {
        // Android's UI scheduler is millisecond/frame based; 80 ms provides the
        // tiny visual pause requested before the WebView navigation begins.
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                launchForumMainActivity();
            }
        }, WEBVIEW_HANDOFF_DELAY_MS);
    }

    private void launchForumMainActivity() {
        if (destroyed || isFinishing() || isDestroyed()) return;

        destroyStartupWebView();

        Intent target = new Intent(this, HcfStartupMainActivity.class);
        target.putExtra(HcfStartupMainActivity.EXTRA_STARTUP_HANDOFF, true);

        Intent source = getIntent();
        if (source != null) {
            Uri data = source.getData();
            if (data != null) target.setData(data);
            if (source.getAction() != null && !Intent.ACTION_MAIN.equals(source.getAction())) {
                target.setAction(source.getAction());
            }
        }

        AppLogger.info(this, "startup_handoff", "launch_main_after_" + WEBVIEW_HANDOFF_DELAY_MS + "ms");
        startActivity(target);
        overridePendingTransition(0, 0);
        finish();
    }

    private void publishStage(final int progress, final String status, final String detail) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (destroyed || isFinishing() || isDestroyed()) return;
                if (loaderStatus != null) loaderStatus.setText(status);
                if (loaderDetail != null) loaderDetail.setText(detail);
                if (loaderProgress != null) loaderProgress.setProgress(Math.max(0, Math.min(100, progress)), true);
            }
        });
    }

    private void failStartup(final String title, final String detail) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (destroyed || isFinishing() || isDestroyed()) return;
                loaderStarted = false;
                handoffStarted = false;
                if (loaderTitle != null) loaderTitle.setText(title);
                if (loaderStatus != null) loaderStatus.setText("Startup paused");
                if (loaderDetail != null) loaderDetail.setText(detail);
                if (loaderProgress != null) loaderProgress.setProgress(0, true);
                if (retryButton != null) retryButton.setVisibility(View.VISIBLE);
                AppLogger.error(HcfStartupActivity.this, "startup_blocked", title + " | " + detail);
            }
        });
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
            AppLogger.info(this, "startup_host_probe", host + " | HTTP " + code + " | " + (healthy ? "ready" : "not-ready"));
            return healthy;
        } catch (Throwable error) {
            AppLogger.warn(this, "startup_host_probe", host + " | " + error.getClass().getSimpleName());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String preferredHost() {
        String host = prefs == null ? "" : prefs.getString("active_host", "");
        if (SetupCenter.BACKUP_FORUM_HOST.equalsIgnoreCase(host)) return SetupCenter.BACKUP_FORUM_HOST;
        return SetupCenter.PRIMARY_FORUM_HOST;
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

        TextView body = text("The native startup screen could not be created.\n\n" + error.getClass().getSimpleName(),
                12, getColor(R.color.hcf_text));
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(10);
        root.addView(body, bodyLp);

        setContentView(root);
    }
}
