package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;

final class UpdateAutomation {
    private static final long FOREGROUND_MIN_INTERVAL_MS = 30L * 60L * 1000L;

    interface Listener {
        void onFinished(UpdateChecker.Release release, boolean updateAvailable, String error);
    }

    static void maybeCheck(Context context, boolean force, Listener listener) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        if (!force && !prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true)) {
            finish(listener, null, false, "Automatic update checks are off.");
            return;
        }
        long now = System.currentTimeMillis();
        long last = prefs.getLong(AppPrefs.UPDATE_LAST_CHECK, 0L);
        if (!force && last > 0L && now - last < FOREGROUND_MIN_INTERVAL_MS) {
            finish(listener, null, false, null);
            return;
        }

        String channel = BuildInfo.ALLOW_UPDATE_CHANNEL_SWITCH
                ? prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL)
                : UpdateChecker.CHANNEL_STABLE;
        UpdateChecker.check(app, channel, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                String priorTag = prefs.getString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, "");
                String releaseIdentity = release.assetKey();
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                if (updateAvailable) {
                    prefs.edit().putString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, releaseIdentity).apply();
                    boolean autoDownload = prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, true);
                    if (autoDownload && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        AppUpdateDownloader.enqueue(app, release, false);
                    } else if (!releaseIdentity.equals(priorTag)) {
                        NotificationHelper.postUpdateAvailable(app, release);
                    }
                }
                boolean feedBehind = UpdateChecker.compareReleaseToInstalled(release) < 0;
                AppLogger.info(app, "update_auto_check", channel + " | " + release.tag + " | newer=" + updateAvailable + " | feedBehind=" + feedBehind);
                finish(listener, release, updateAvailable, null);
            }

            @Override
            public void onError(String message) {
                prefs.edit().putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis()).apply();
                AppLogger.warn(app, "update_auto_check", message);
                TelemetryService.sendDiagnosticEvent(app, "update_check_failed", message);
                finish(listener, null, false, message);
            }
        });
    }

    private static void finish(Listener listener, UpdateChecker.Release release, boolean updateAvailable, String error) {
        if (listener != null) listener.onFinished(release, updateAvailable, error);
    }

    private UpdateAutomation() {}
}
