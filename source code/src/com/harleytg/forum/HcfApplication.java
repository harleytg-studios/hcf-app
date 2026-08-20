package com.harleytg.forum;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;

/** Application entry point for the public Stable build. */
public final class HcfApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RuntimeState.install(this);

        // Repair preferences inherited from older test/dev builds before any scheduler runs.
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, 0);
        if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(
                prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
            prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
        }

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    TelemetryService.captureCrash(HcfApplication.this, thread, error);
                } catch (Throwable ignored) {
                }
                try {
                    AppLogger.crash(HcfApplication.this, error);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });

        try {
            UiPreferences.migrate(this);
        } catch (Throwable ignored) {
        }
        try {
            AppLogger.info(this, "app_start",
                    BuildInfo.VERSION + " | Stable | SDK " + Build.VERSION.SDK_INT
                            + " | " + Build.MANUFACTURER + " " + Build.MODEL);
        } catch (Throwable ignored) {
        }

        AppExecutors.main().postDelayed(new Runnable() {
            @Override
            public void run() {
                AppExecutors.disk().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(HcfApplication.this);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                AppExecutors.network().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            TelemetryService.heartbeat(HcfApplication.this);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }, 3500L);
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
