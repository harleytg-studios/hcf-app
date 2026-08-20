package com.harleytg.forum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Restores Stable services after boot or an in-place Stable app update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(
                prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
            prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
        }

        String action = intent == null ? "boot" : String.valueOf(intent.getAction());
        if (intent != null && "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            AppUpdateDownloader.cleanupStaleUpdaterApks(context);
            TelemetryService.sendEvent(context, "update_installed",
                    BuildInfo.VERSION + " | Stable | " + BuildInfo.VERSION_CODE);
        }

        NotificationSyncScheduler.apply(context);
        UpdateScheduler.apply(context);
        AppLogger.info(context, "boot_receiver", "Stable | " + action);
    }
}
