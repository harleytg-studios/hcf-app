package com.harleytg.forum.dev;

import android.app.Application;
import android.os.Build;
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
