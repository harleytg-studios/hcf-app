package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long expected = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        if (completed <= 0L || completed != expected) return;
        int status = AppUpdateDownloader.status(context, completed);
        String tag = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_TAG, "update");
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(context, completed);
            if (verification.ok) {
                boolean autoInstall = prefs.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true);
                boolean installerOpened = false;
                if (autoInstall) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT < 26 || AppSecurity.canInstallUpdates(context)) {
                            installerOpened = AppUpdateDownloader.openInstaller(context, completed);
                        } else {
                            Intent installFlow = new Intent(context, SettingsActivity.class);
                            installFlow.setAction("com.harleytg.forum.dev.INSTALL_UPDATE");
                            installFlow.putExtra("download_id", completed);
                            installFlow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            context.startActivity(installFlow);
                            installerOpened = true;
                        }
                    } catch (Throwable t) {
                        AppLogger.warn(context, "update_auto_install", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
                    }
                }
                if (!installerOpened) {
                    NotificationHelper.postUpdateReady(context, tag, completed);
                }
                AppLogger.info(context, "update_download_complete", tag + " | verified | id=" + completed + " | autoInstaller=" + installerOpened);
            } else {
                AppLogger.warn(context, "update_download_blocked", tag + " | " + verification.message);
                TelemetryService.sendDiagnosticEvent(context, "update_verification_blocked", verification.message);
                AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            }
        } else {
            AppLogger.warn(context, "update_download_complete", tag + " | status=" + status);
            TelemetryService.sendDiagnosticEvent(context, "update_download_failed", "DownloadManager status " + status);
        }
    }
}
