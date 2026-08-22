package com.harleytg.forum;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import java.lang.Thread;

/**
 * Application entry. Applies resolved night mode as early as possible so the
 * first activity frame does not flash the opposite theme (light splash on a
 * dark preference, etc.).
 */
public final class HcfApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Theme must run before any activity attaches / draws windowBackground.
        try {
            ThemeManager.applyToApplication(this);
        } catch (Throwable ignored) {
        }

        RuntimeState.install(this);
        RemoteDomainConfig.initialize(this);

        // The old MainActivity permission dialog is now legacy. Set its guard
        // before MainActivity is created so the delayed 700 ms AlertDialog can
        // never appear. The versioned Setup Center owns onboarding from here on.
        try {
            getSharedPreferences(AppPrefs.FILE, 0).edit()
                    .putBoolean(AppPrefs.PERMISSION_ONBOARDING_DONE, true)
                    .apply();
        } catch (Throwable ignored) {
        }

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity) {
                    try {
                        SetupCenter.maybeLaunchForMainActivity((MainActivity) activity, state);
                    } catch (Throwable error) {
                        AppLogger.error(HcfApplication.this, "app_setup_lifecycle", error.getClass().getSimpleName());
                    }
                }
            }

            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) {
                    try {
                        SetupCenter.installDrawerEntry((MainActivity) activity);
                    } catch (Throwable error) {
                        AppLogger.warn(HcfApplication.this, "app_setup_drawer", error.getClass().getSimpleName());
                    }
                }
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) {
                HcfApplication.this.handleUncaught(previous, thread, error);
            }
        });

        try {
            UiPreferences.migrate(this);
        } catch (Throwable ignored) {
        }
        try {
            AppLogger.info(this, "app_start", BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE
                    + ") | SDK " + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL
                    + " | theme=" + ThemeManager.mode(this) + "/" + ThemeManager.webColorScheme(this));
        } catch (Throwable ignored) {
        }

        AppExecutors.main().postDelayed(new Runnable() {
            @Override public void run() {
                startDeferredWork();
            }
        }, 3500L);
    }

    private void handleUncaught(Thread.UncaughtExceptionHandler previous, Thread thread, Throwable error) {
        try {
            TelemetryService.captureCrash(this, thread, error);
        } catch (Throwable ignored) {
        }
        try {
            AppLogger.crash(this, error);
        } catch (Throwable ignored) {
        }
        if (previous != null) previous.uncaughtException(thread, error);
    }

    private void startDeferredWork() {
        AppExecutors.disk().execute(new Runnable() {
            @Override public void run() {
                try {
                    AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(HcfApplication.this);
                } catch (Throwable ignored) {
                }
            }
        });
        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                try {
                    TelemetryService.heartbeat(HcfApplication.this);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RuntimeState.noteTrimMemory(level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        RuntimeState.noteTrimMemory(80);
    }
}
