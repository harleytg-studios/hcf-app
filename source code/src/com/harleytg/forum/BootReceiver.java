package com.harleytg.forum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* loaded from: classes.dex */
public final class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            AppUpdateDownloader.cleanupStaleUpdaterApks(context);
            TelemetryService.sendEvent(context, "update_installed", "1.0");
        }
        NotificationSyncScheduler.apply(context);
        UpdateScheduler.apply(context);
        AppLogger.info(context, "boot_receiver", intent == null ? "boot" : String.valueOf(intent.getAction()));
    }
}
