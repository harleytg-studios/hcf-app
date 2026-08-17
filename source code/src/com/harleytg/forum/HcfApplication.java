package com.harleytg.forum.dev;

import android.app.Application;
import android.os.Build;

public final class HcfApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Install the crash boundary before any optional startup subsystem runs.
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try { TelemetryService.captureCrash(HcfApplication.this, thread, throwable); }
            catch (Throwable ignored) {}
            try { AppLogger.crash(HcfApplication.this, throwable); }
            catch (Throwable ignored) {}
            if (previous != null) previous.uncaughtException(thread, throwable);
        });

        // Every startup task is isolated so a damaged preference value, telemetry issue,
        // updater edge case, or OEM quirk cannot take down the whole application process.
        try { UiPreferences.migrate(this); }
        catch (Throwable ignored) {}

        try {
            AppLogger.info(this, "app_start",
                    BuildInfo.VERSION + " | SDK " + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL);
        } catch (Throwable ignored) {}

        try { StablePromoInjector.install(this); }
        catch (Throwable ignored) {}

        try { AppUpdateDownloader.cleanupIfCurrentVersionWasDownloaded(this); }
        catch (Throwable ignored) {}

        try { TelemetryService.heartbeat(this); }
        catch (Throwable ignored) {}
    }
}
