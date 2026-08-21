package com.harleytg.forum.dev;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.harleytg.forum.dev.AppSecurity;

/* loaded from: classes.dex */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !"android.intent.action.DOWNLOAD_COMPLETE".equals(intent.getAction())) {
            return;
        }
        long longExtra = intent.getLongExtra("extra_download_id", -1L);
        boolean installerOpened = false;
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long expectedId = sharedPreferences.getLong("update_download_id", -1L);
        if (longExtra <= 0 || longExtra != expectedId) {
            return;
        }
        int status = AppUpdateDownloader.status(context, longExtra);
        String tag = sharedPreferences.getString("update_download_tag", "update");
        if (status == 8) {
            AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(context, longExtra);
            if (verification.ok) {
                boolean autoInstall = sharedPreferences.getBoolean("update_auto_install", true);
                boolean foreground = RuntimeState.isForeground();

                // Always remember that a verified APK is waiting. This survives Android
                // blocking a background activity launch and lets Settings expose Install.
                sharedPreferences.edit().putBoolean("update_install_pending", true).apply();

                // Android 10+ may block activities launched directly from a background
                // DOWNLOAD_COMPLETE receiver. Only hand off immediately while HCF is
                // visibly foregrounded; otherwise post a user-initiated install action.
                if (autoInstall && foreground) {
                    try {
                        if (AppSecurity.canInstallUpdates(context)) {
                            installerOpened = AppUpdateDownloader.openInstaller(context, longExtra);
                        } else {
                            Intent permissionFlow = new Intent(context, (Class<?>) SettingsActivity.class);
                            permissionFlow.setAction(context.getPackageName() + ".INSTALL_UPDATE");
                            permissionFlow.putExtra("download_id", longExtra);
                            permissionFlow.addFlags(335544320);
                            context.startActivity(permissionFlow);
                            installerOpened = true;
                        }
                    } catch (Throwable th) {
                        AppLogger.warn(context, "update_auto_install", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                    }
                }

                if (!installerOpened) {
                    NotificationHelper.postUpdateReady(context, tag, longExtra);
                }
                AppLogger.info(context, "update_download_complete", tag + " | verified | id=" + longExtra + " | foreground=" + foreground + " | autoInstaller=" + installerOpened);
                return;
            }
            AppLogger.warn(context, "update_download_blocked", tag + " | " + verification.message);
            TelemetryService.sendDiagnosticEvent(context, "update_verification_blocked", verification.message);
            AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
            return;
        }
        AppLogger.warn(context, "update_download_complete", tag + " | status=" + status);
        StringBuilder sb = new StringBuilder("DownloadManager status ");
        sb.append(status);
        TelemetryService.sendDiagnosticEvent(context, "update_download_failed", sb.toString());
    }
}
