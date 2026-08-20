package com.harleytg.forum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Handles completed downloads from the locked Stable update channel. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !"android.intent.action.DOWNLOAD_COMPLETE".equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(
                prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
            prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
        }

        long completedId = intent.getLongExtra("extra_download_id", -1L);
        long expectedId = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        if (completedId <= 0L || completedId != expectedId) return;

        int status = AppUpdateDownloader.status(context, completedId);
        String assetKey = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_TAG, "Stable update");
        if (status == 8) {
            AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(context, completedId);
            if (!verification.ok) {
                AppLogger.warn(context, "update_download_blocked",
                        "Stable | " + assetKey + " | " + verification.message);
                TelemetryService.sendDiagnosticEvent(context, "update_verification_blocked",
                        "Stable | " + verification.message);
                AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
                return;
            }

            boolean installerOpened = false;
            if (prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true)) {
                try {
                    if (AppSecurity.canInstallUpdates(context)) {
                        installerOpened = AppUpdateDownloader.openInstaller(context, completedId);
                    } else {
                        Intent settings = new Intent(context, SettingsActivity.class);
                        settings.setAction("com.harleytg.forum.INSTALL_UPDATE");
                        settings.putExtra("download_id", completedId);
                        settings.addFlags(335544320);
                        context.startActivity(settings);
                        installerOpened = true;
                    }
                } catch (Throwable error) {
                    AppLogger.warn(context, "update_auto_install",
                            "Stable | " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
                }
            }

            if (!installerOpened) {
                NotificationHelper.postUpdateReady(context, assetKey, completedId);
            }
            AppLogger.info(context, "update_download_complete",
                    "Stable | " + assetKey + " | verified | id=" + completedId
                            + " | autoInstaller=" + installerOpened);
            return;
        }

        AppLogger.warn(context, "update_download_complete",
                "Stable | " + assetKey + " | status=" + status);
        TelemetryService.sendDiagnosticEvent(context, "update_download_failed",
                "Stable DownloadManager status " + status);
    }
}
