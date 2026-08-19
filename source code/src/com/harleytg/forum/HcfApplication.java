package com.harleytg.forum.dev;

import android.app.Application;
import android.os.Build;

public final class HcfApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Generated MAIN configuration is required for secure routing and Firebase
        // client setup. A release APK without it is considered invalid rather than
        // silently falling back to a hardcoded hostname or stale config.
        AppConfig.initialize(this);
        RuntimeState.install(this);

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try { TelemetryService.captureCrash(HcfApplication.this, thread, throwable); }
            catch (Throwable ignored) {}
            try { AppLogger.crash(HcfApplication.this, throwable); }
            catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, throwable);
        });

        try { UiPreferences.migrate(this); }
        catch (Throwable ignored) {}

        try {
            AppLogger.info(this, "app_start",
                    BuildInfo.VERSION + " | SDK " + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL);
        } catch (Throwable ignored) {}

        AppExecutors.main().postDelayed(() -> {
            AppExecutors.disk().execute(() -> {
                try { AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(HcfApplication.this); }
                catch (Throwable ignored) {}
            });
            AppExecutors.network().execute(() -> {
                try { TelemetryService.heartbeat(HcfApplication.this); }
                catch (Throwable ignored) {}
            });
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
        RuntimeState.noteTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
    }
}
