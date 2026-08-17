package com.harleytg.forum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            AppUpdateDownloader.cleanupStaleUpdaterApks(context);
            TelemetryService.sendEvent(context, "update_installed", BuildInfo.VERSION);
        }
        NotificationSyncScheduler.apply(context);
        UpdateScheduler.apply(context);
        AppLogger.info(context, "boot_receiver", intent == null ? "boot" : String.valueOf(intent.getAction()));
    }
}
