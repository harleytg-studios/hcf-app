package com.harleytg.forum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.harleytg.forum.AppSecurity;

/* loaded from: classes.dex */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !"android.intent.action.DOWNLOAD_COMPLETE".equals(intent.getAction())) {
            return;
        }
        long longExtra = intent.getLongExtra("extra_download_id", -1L);
        boolean z = false;
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long j = sharedPreferences.getLong("update_download_id", -1L);
        if (longExtra <= 0 || longExtra != j) {
            return;
        }
        int status = AppUpdateDownloader.status(context, longExtra);
        String string = sharedPreferences.getString("update_download_tag", "update");
        if (status == 8) {
            AppSecurity.ApkVerification verifyDownloadedUpdate = AppSecurity.verifyDownloadedUpdate(context, longExtra);
            if (verifyDownloadedUpdate.ok) {
                if (sharedPreferences.getBoolean("update_auto_install", true)) {
                    try {
                        if (AppSecurity.canInstallUpdates(context)) {
                            z = AppUpdateDownloader.openInstaller(context, longExtra);
                        } else {
                            Intent intent2 = new Intent(context, (Class<?>) SettingsActivity.class);
                            intent2.setAction("com.harleytg.forum.INSTALL_UPDATE");
                            intent2.putExtra("download_id", longExtra);
                            intent2.addFlags(335544320);
                            context.startActivity(intent2);
                            z = true;
                        }
                    } catch (Throwable th) {
                        AppLogger.warn(context, "update_auto_install", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                    }
                }
                if (!z) {
                    NotificationHelper.postUpdateReady(context, string, longExtra);
                }
                AppLogger.info(context, "update_download_complete", string + " | verified | id=" + longExtra + " | autoInstaller=" + z);
                return;
            }
            AppLogger.warn(context, "update_download_blocked", string + " | " + verifyDownloadedUpdate.message);
            TelemetryService.sendDiagnosticEvent(context, "update_verification_blocked", verifyDownloadedUpdate.message);
            AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            return;
        }
        AppLogger.warn(context, "update_download_complete", string + " | status=" + status);
        StringBuilder sb = new StringBuilder("DownloadManager status ");
        sb.append(status);
        TelemetryService.sendDiagnosticEvent(context, "update_download_failed", sb.toString());
    }
}
