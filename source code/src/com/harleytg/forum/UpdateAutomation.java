package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;

/** Background/foreground automation for the locked Stable release channel. */
final class UpdateAutomation {
    private static final long FOREGROUND_MIN_INTERVAL_MS = 1800000L;

    interface Listener {
        void onFinished(UpdateChecker.Release release, boolean updateAvailable, String error);
    }

    static void maybeCheck(Context context, boolean force, final Listener listener) {
        if (context == null) return;

        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, 0);
        if (!BuildInfo.DEFAULT_UPDATE_CHANNEL.equalsIgnoreCase(
                prefs.getString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL))) {
            prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL).apply();
        }

        if (!force && !prefs.getBoolean(AppPrefs.UPDATE_AUTO_CHECK, true)) {
            finish(listener, null, false, "Automatic Stable update checks are off.");
            return;
        }

        long now = System.currentTimeMillis();
        long lastCheck = prefs.getLong(AppPrefs.UPDATE_LAST_CHECK, 0L);
        if (!force && lastCheck > 0L && now - lastCheck < FOREGROUND_MIN_INTERVAL_MS) {
            finish(listener, null, false, null);
            return;
        }

        UpdateChecker.check(app, BuildInfo.DEFAULT_UPDATE_CHANNEL, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Release release, boolean updateAvailable) {
                String previousAsset = prefs.getString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, "");
                String assetKey = release.assetKey();
                SharedPreferences.Editor editor = prefs.edit()
                        .putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis())
                        .putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL);
                if (updateAvailable) editor.putString(AppPrefs.UPDATE_LAST_AVAILABLE_TAG, assetKey);
                editor.apply();

                if (updateAvailable) {
                    if (prefs.getBoolean(AppPrefs.UPDATE_AUTO_DOWNLOAD, false)
                            && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                        AppUpdateDownloader.enqueue(app, release, false);
                    } else if (!assetKey.equals(previousAsset)) {
                        NotificationHelper.postUpdateAvailable(app, release);
                    }
                }

                boolean feedBehind = UpdateChecker.compareReleaseToInstalled(release) < 0;
                AppLogger.info(app, "update_auto_check",
                        "stable | " + release.tag + " | newer=" + updateAvailable + " | feedBehind=" + feedBehind);
                finish(listener, release, updateAvailable, null);
            }

            @Override
            public void onError(String message) {
                prefs.edit()
                        .putLong(AppPrefs.UPDATE_LAST_CHECK, System.currentTimeMillis())
                        .putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL)
                        .apply();
                AppLogger.warn(app, "update_auto_check", "stable | " + message);
                TelemetryService.sendDiagnosticEvent(app, "update_check_failed", "stable | " + message);
                finish(listener, null, false, message);
            }
        });
    }

    private static void finish(Listener listener, UpdateChecker.Release release,
                               boolean updateAvailable, String error) {
        if (listener != null) listener.onFinished(release, updateAvailable, error);
    }

    private UpdateAutomation() {}
}
