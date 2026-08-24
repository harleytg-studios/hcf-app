package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
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
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the forum WebView cookie store synchronized with persistent storage and
 * owns the single visible native startup loader used by the Dev/Beta app.
 *
 * Cookie values are never read or exported here. Android WebView's CookieManager
 * is only asked to persist its existing first-party session state.
 */
public final class HcfSessionPersistence {
    private static final long FOREGROUND_FLUSH_MS = 15000L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger RESUMED_ACTIVITIES = new AtomicInteger(0);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HcfSessionPersistence() {}

    private static final Runnable FOREGROUND_FLUSH = new Runnable() {
        @Override public void run() {
            if (RESUMED_ACTIVITIES.get() <= 0) return;
            flushCookies();
            MAIN.postDelayed(this, FOREGROUND_FLUSH_MS);
        }
    };

    static void flushCookies() {
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            manager.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        flushCookies();

        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        Application application = (Application) appContext;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override public void onActivityResumed(Activity activity) {
                int resumed = RESUMED_ACTIVITIES.incrementAndGet();
                flushCookies();
                if (resumed == 1) {
                    MAIN.removeCallbacks(FOREGROUND_FLUSH);
                    MAIN.postDelayed(FOREGROUND_FLUSH, FOREGROUND_FLUSH_MS);
                }
            }

            @Override public void onActivityPaused(Activity activity) {
                flushCookies();
                int remaining = Math.max(0, RESUMED_ACTIVITIES.decrementAndGet());
                RESUMED_ACTIVITIES.set(remaining);
                if (remaining == 0) MAIN.removeCallbacks(FOREGROUND_FLUSH);
            }

            @Override public void onActivityStopped(Activity activity) {
                flushCookies();
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                flushCookies();
            }

            @Override public void onActivityDestroyed(Activity activity) {
                flushCookies();
            }
        });
    }

    /**
     * The only visible native startup loader.
     *
     * It performs onboarding, the account/network ban check, and lightweight
     * Android readiness checks before handing directly to the forum MainActivity.
     * The older GateActivity and HcfUITheme.StartupActivity remain internal only
     * and are never part of a normal launch, preventing the duplicated loader.
     */
    public static final class SingleLoaderActivity extends ThemedActivity {
        private static final int REQUEST_WELCOME = 5101;
        private static final int REQUEST_SETUP = 5102;
        private static final long FIRST_FRAME_HOLD_MS = 240L;
        private static final long HANDOFF_DELAY_MS = 80L;

        private final Handler main = new Handler(Looper.getMainLooper());
        private ProgressBar progress;
        private TextView status;
        private TextView detail;
        private boolean resumed;
        private boolean gateInProgress;
        private boolean checksStarted;
        private boolean handoffStarted;
        private boolean destroyed;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            try { ThemeManager.apply(this); } catch (Throwable ignored) {}

            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildLoader(bg));

            if (state == null && (SetupCenter.shouldShowWelcome(this) || SetupCenter.shouldAutoLaunch(this))) {
                getWindow().getDecorView().setAlpha(0.0f);
            }

            AppLogger.info(this, "single_loader", "created | " + BuildInfo.VERSION_BUILD_LINE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            resumed = true;
            main.post(new Runnable() {
                @Override public void run() { advanceStartup(); }
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
            main.removeCallbacksAndMessages(null);
            HcfSessionPersistence.flushCookies();
            super.onDestroy();
        }

        private View buildLoader(int bg) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(34), dp(24), dp(28));
            root.setBackgroundColor(bg);

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            root.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));

            TextView brand = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(14);
            root.addView(brand, brandLp);

            TextView title = text("Starting Harley's Clan Forum", 21, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(6);
            root.addView(title, titleLp);

            status = text("Starting native systems", 13, getColor(R.color.hcf_cyan_bright));
            status.setTypeface(null, 1);
            status.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
            statusLp.topMargin = dp(20);
            root.addView(status, statusLp);

            detail = text("Preparing system checks before the forum opens.", 11, getColor(R.color.hcf_muted));
            detail.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(6);
            root.addView(detail, detailLp);

            progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(0);
            progress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
            progressLp.topMargin = dp(22);
            root.addView(progress, progressLp);

            TextView build = text(BuildInfo.VERSION_BUILD_LINE, 9, getColor(R.color.hcf_hint));
            build.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams buildLp = new LinearLayout.LayoutParams(-1, -2);
            buildLp.topMargin = dp(14);
            root.addView(build, buildLp);

            return root;
        }

        private void advanceStartup() {
            if (!resumed || destroyed || isFinishing() || isDestroyed()
                    || gateInProgress || checksStarted || handoffStarted) {
                return;
            }

            if (SetupCenter.shouldShowWelcome(this)) {
                gateInProgress = true;
                getWindow().getDecorView().setAlpha(0.0f);
                Intent welcome = new Intent(this, HcfMainActivities.WelcomeActivity.class);
                welcome.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
                startActivityForResult(welcome, REQUEST_WELCOME);
                return;
            }

            if (SetupCenter.shouldAutoLaunch(this)) {
                gateInProgress = true;
                SetupCenter.markSeen(this);
                getWindow().getDecorView().setAlpha(0.0f);
                Intent setup = new Intent(this, HcfMainActivities.SetupActivity.class);
                setup.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
                startActivityForResult(setup, REQUEST_SETUP);
                return;
            }

            getWindow().getDecorView().setAlpha(1.0f);
            checksStarted = true;
            publish(0, "Starting native systems", "Preparing system checks before the forum opens.");

            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!resumed || destroyed || handoffStarted) {
                        checksStarted = false;
                        return;
                    }
                    Thread worker = new Thread(new Runnable() {
                        @Override public void run() { runChecks(); }
                    }, "hcf-single-startup");
                    worker.setPriority(Thread.NORM_PRIORITY);
                    worker.start();
                }
            }, FIRST_FRAME_HOLD_MS);
        }

        private void runChecks() {
            try {
                publish(8, "Checking access status", "Checking account and network against the HCF ban list.");
                try {
                    HcfBanSystem.CheckResult access = checkCurrentAccess();
                    if (access != null && access.banned) {
                        openBan(access);
                        return;
                    }
                    publish(18, "Access allowed", "No active HCF ban was found.");
                } catch (Throwable accessError) {
                    AppLogger.warn(this, "single_loader_ban", "fail-open | " + accessError.getClass().getSimpleName());
                    publish(18, "Access check unavailable", "The HCF ban list could not be reached; startup will continue.");
                }

                HcfSessionPersistence.flushCookies();
                publish(30, "Restoring forum session", "Persisted first-party forum cookies are ready for WebView.");

                if (WebView.getCurrentWebViewPackage() == null) {
                    publish(44, "Checking WebView engine", "Android WebView provider was not reported; recovery remains available.");
                } else {
                    publish(44, "Checking WebView engine", "Android WebView engine is ready.");
                }

                ForumIdentity.Snapshot identity = ForumIdentity.load(this);
                publish(56, "Loading forum identity",
                        identity != null && identity.loggedIn ? "Restored signed-in forum identity." : "Guest forum session ready.");

                try {
                    NotificationHelper.refreshChannels(this);
                    NotificationSyncScheduler.apply(this);
                    publish(68, "Starting notification systems", "Notification channels and background alert schedule checked.");
                } catch (Throwable notificationError) {
                    publish(68, "Checking notification systems", "Notifications are optional; startup can continue.");
                }

                try {
                    UpdateScheduler.apply(this);
                    AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(this);
                    publish(80, "Starting update system", "Update scheduler and downloaded-update state checked.");
                } catch (Throwable updateError) {
                    publish(80, "Checking update system", "Update checks can recover after the forum opens.");
                }

                publish(92, "Checking forum connection",
                        hasInternetNetwork() ? "Android reports an active internet connection." : "No active internet connection; recovery remains available.");

                HcfSessionPersistence.flushCookies();
                publish(100, "Systems ready", "Opening the forum.");
                main.postDelayed(new Runnable() {
                    @Override public void run() { continueToForum(); }
                }, HANDOFF_DELAY_MS);
            } catch (Throwable error) {
                AppLogger.error(this, "single_loader", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
                publish(100, "Startup recovery", "A non-blocking startup check failed; opening the forum.");
                main.postDelayed(new Runnable() {
                    @Override public void run() { continueToForum(); }
                }, HANDOFF_DELAY_MS);
            }
        }

        private HcfBanSystem.CheckResult checkCurrentAccess() throws Exception {
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);

            Method loadConfig = HcfBanSystem.class.getDeclaredMethod("loadRuntimeConfig", Context.class);
            loadConfig.setAccessible(true);
            HcfBanSystem.RuntimeConfig config = (HcfBanSystem.RuntimeConfig) loadConfig.invoke(null, this);
            if (config == null || !config.ready()) return null;

            Method resolveIp = HcfBanSystem.class.getDeclaredMethod("resolvePublicIp", HcfBanSystem.RuntimeConfig.class);
            resolveIp.setAccessible(true);
            HcfBanSystem.PublicIp publicIp = (HcfBanSystem.PublicIp) resolveIp.invoke(null, config);

            Method check = HcfBanSystem.class.getDeclaredMethod(
                    "checkBanList",
                    HcfBanSystem.RuntimeConfig.class,
                    ForumIdentity.Snapshot.class,
                    HcfBanSystem.PublicIp.class);
            check.setAccessible(true);
            return (HcfBanSystem.CheckResult) check.invoke(null, config, identity, publicIp);
        }

        private void openBan(final HcfBanSystem.CheckResult result) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed() || handoffStarted) return;
                    handoffStarted = true;
                    Intent intent = new Intent(SingleLoaderActivity.this, HcfBanSystem.BanActivity.class);
                    intent.putExtra("ban_id", result.banId);
                    intent.putExtra("reason", result.reason);
                    intent.putExtra("expires_at", result.expiresAt);
                    intent.putExtra("scope", result.scope);
                    intent.putExtra("username", result.username);
                    intent.putExtra("masked_ip", result.maskedIp);
                    intent.putExtra("appeal_allowed", result.appealAllowed);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }
            });
        }

        private void continueToForum() {
            if (!resumed || destroyed || isFinishing() || isDestroyed() || handoffStarted) return;
            handoffStarted = true;
            HcfSessionPersistence.flushCookies();

            Intent target = new Intent(this, HcfUITheme.StartupMainActivity.class);
            target.putExtra(HcfUITheme.StartupMainActivity.EXTRA_STARTUP_HANDOFF, true);

            Intent source = getIntent();
            if (source != null) {
                Uri data = source.getData();
                if (data != null) target.setData(data);
                if (!TextUtils.isEmpty(source.getAction()) && !Intent.ACTION_MAIN.equals(source.getAction())) {
                    target.setAction(source.getAction());
                }
            }

            startActivity(target);
            overridePendingTransition(0, 0);
            finish();
        }

        private boolean hasInternetNetwork() {
            try {
                ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (manager == null) return false;
                Network active = manager.getActiveNetwork();
                if (active == null) return false;
                NetworkCapabilities caps = manager.getNetworkCapabilities(active);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void publish(final int value, final String statusText, final String detailText) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed()) return;
                    if (status != null) status.setText(statusText);
                    if (detail != null) detail.setText(detailText);
                    if (progress != null) progress.setProgress(Math.max(0, Math.min(100, value)), true);
                }
            });
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
    }

    /** Installs persistence before the first Activity/WebView is created. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            return 0;
        }
    }
}
