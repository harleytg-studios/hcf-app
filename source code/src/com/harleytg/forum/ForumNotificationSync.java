package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

/* loaded from: classes.dex */
final class ForumNotificationSync {
    private static final long STATUS_WRITE_INTERVAL_MS = 15000;

    static final class Outcome {
        final int count;
        final int delivered;
        final long latencyMs;

        Outcome(int i, int i2, long j) {
            this.count = i;
            this.delivered = i2;
            this.latencyMs = j;
        }
    }

    static Outcome perform(Context context, String str, String str2, String str3) throws Exception {
        int i = 0;
        long currentTimeMillis = System.currentTimeMillis();
        try {
            int fetchNewCount = ForumNotificationClient.fetchNewCount(context, str, str2);
            int recordForumNotificationCount = NotificationHelper.recordForumNotificationCount(context, fetchNewCount, str, str3);
            if (recordForumNotificationCount > 0) {
                try {
                    i = NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(recordForumNotificationCount + 4, 8)), recordForumNotificationCount, str, str3);
                } catch (Throwable th) {
                    NotificationHelper.postGenericDelta(context, recordForumNotificationCount, str);
                    AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName() + " | generic-fallback");
                }
                long max = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
                RuntimeDiagnostics.syncSucceeded(max);
                recordStatus(context, "Live • synced", max, false);
                return new Outcome(fetchNewCount, i, max);
            }
            i = 0;
            long max2 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncSucceeded(max2);
            recordStatus(context, "Live • synced", max2, false);
            return new Outcome(fetchNewCount, i, max2);
        } catch (Exception e) {
            long max3 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Waiting for connection", max3, true);
            throw e;
        }
    }

    static void deliverObservedCountAsync(Context context, final String str, final int i, final String str2) {
        if (context == null || i <= 0) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.ForumNotificationSync$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                ForumNotificationSync.lambda$deliverObservedCountAsync$0(applicationContext, str, i, str2);
            }
        });
    }

    static /* synthetic */ void lambda$deliverObservedCountAsync$0(Context context, String str, int i, String str2) {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(i + 4, 8)), i, str, str2);
            long currentTimeMillis2 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncSucceeded(currentTimeMillis2);
            recordStatus(context, "Live • synced", currentTimeMillis2, false);
        } catch (Throwable th) {
            NotificationHelper.postGenericDelta(context, i, str);
            long currentTimeMillis3 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Live • detail unavailable", currentTimeMillis3, true);
            AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName());
        }
    }

    private static void recordStatus(Context context, String str, long j, boolean z) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long currentTimeMillis = System.currentTimeMillis();
        String string = sharedPreferences.getString("notification_last_sync_status", "");
        long j2 = sharedPreferences.getLong("notification_last_sync_at", 0L);
        boolean z2 = !str.equals(string);
        if (z || z2 || currentTimeMillis - j2 >= STATUS_WRITE_INTERVAL_MS) {
            sharedPreferences.edit().putLong("notification_last_sync_at", currentTimeMillis).putString("notification_last_sync_status", str).putLong("notification_last_sync_latency_ms", Math.max(0L, j)).apply();
        }
    }

    private ForumNotificationSync() {
    }
}
