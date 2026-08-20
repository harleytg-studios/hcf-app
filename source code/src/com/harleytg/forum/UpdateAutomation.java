package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import com.harleytg.forum.dev.UpdateChecker;

/* loaded from: classes.dex */
final class UpdateAutomation {
    private static final long FOREGROUND_MIN_INTERVAL_MS = 1800000;

    interface Listener {
        void onFinished(UpdateChecker.Release release, boolean z, String str);
    }

    static void maybeCheck(Context context, boolean z, final Listener listener) {
        if (context == null) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        if (!z && !sharedPreferences.getBoolean("update_auto_check", true)) {
            finish(listener, null, false, "Automatic update checks are off.");
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        long j = sharedPreferences.getLong("update_last_check", 0L);
        if (!z && j > 0 && currentTimeMillis - j < FOREGROUND_MIN_INTERVAL_MS) {
            finish(listener, null, false, null);
        } else {
            final String str = "dev";
            UpdateChecker.check(applicationContext, "dev", new UpdateChecker.Callback() { // from class: com.harleytg.forum.dev.UpdateAutomation.1
                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onResult(UpdateChecker.Release release, boolean z2) {
                    String string = sharedPreferences.getString("update_last_available_tag", "");
                    String assetKey = release.assetKey();
                    sharedPreferences.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                    if (z2) {
                        sharedPreferences.edit().putString("update_last_available_tag", assetKey).apply();
                        if (sharedPreferences.getBoolean("update_auto_download", false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                            AppUpdateDownloader.enqueue(applicationContext, release, false);
                        } else if (!assetKey.equals(string)) {
                            NotificationHelper.postUpdateAvailable(applicationContext, release);
                        }
                    }
                    boolean z3 = UpdateChecker.compareReleaseToInstalled(release) < 0;
                    AppLogger.info(applicationContext, "update_auto_check", str + " | " + release.tag + " | newer=" + z2 + " | feedBehind=" + z3);
                    UpdateAutomation.finish(listener, release, z2, null);
                }

                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onError(String str2) {
                    sharedPreferences.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                    AppLogger.warn(applicationContext, "update_auto_check", str2);
                    TelemetryService.sendDiagnosticEvent(applicationContext, "update_check_failed", str2);
                    UpdateAutomation.finish(listener, null, false, str2);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void finish(Listener listener, UpdateChecker.Release release, boolean z, String str) {
        if (listener != null) {
            listener.onFinished(release, z, str);
        }
    }

    private UpdateAutomation() {
    }
}
