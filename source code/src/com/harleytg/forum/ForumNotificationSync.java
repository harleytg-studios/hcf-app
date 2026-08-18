package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Coordinates counter checks, exact alert hydration, deduplication and sync status. */
final class ForumNotificationSync {
    private static final long STATUS_WRITE_INTERVAL_MS = 15_000L;

    static final class Outcome {
        final int count;
        final int delivered;
        final long latencyMs;

        Outcome(int count, int delivered, long latencyMs) {
            this.count = count;
            this.delivered = delivered;
            this.latencyMs = latencyMs;
        }
    }

    static Outcome perform(Context context, String host, String userId, String source) throws Exception {
        long started = System.currentTimeMillis();
        try {
            int count = ForumNotificationClient.fetchNewCount(context, host, userId);
            int delta = NotificationHelper.recordForumNotificationCount(context, count, host, source);
            int delivered = 0;
            if (delta > 0) {
                try {
                    List<ForumNotificationClient.Alert> alerts =
                            ForumNotificationClient.fetchLatest(context, host, Math.max(delta + 4, 8));
                    delivered = NotificationHelper.deliverDetailedAlerts(context, alerts, delta, host, source);
                } catch (Throwable detailError) {
                    NotificationHelper.postGenericDelta(context, delta, host);
                    AppLogger.warn(context, "notification_detail",
                            detailError.getClass().getSimpleName() + " | generic-fallback");
                }
            }
            long latency = Math.max(0L, System.currentTimeMillis() - started);
            RuntimeDiagnostics.syncSucceeded(latency);
            recordStatus(context, "Live • synced", latency, false);
            return new Outcome(count, delivered, latency);
        } catch (Exception error) {
            long latency = Math.max(0L, System.currentTimeMillis() - started);
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Waiting for connection", latency, true);
            throw error;
        }
    }

    static void deliverObservedCountAsync(Context context, String host, int delta, String source) {
        if (context == null || delta <= 0) return;
        Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            long started = System.currentTimeMillis();
            try {
                List<ForumNotificationClient.Alert> alerts =
                        ForumNotificationClient.fetchLatest(app, host, Math.max(delta + 4, 8));
                NotificationHelper.deliverDetailedAlerts(app, alerts, delta, host, source);
                long latency = System.currentTimeMillis() - started;
                RuntimeDiagnostics.syncSucceeded(latency);
                recordStatus(app, "Live • synced", latency, false);
            } catch (Throwable error) {
                NotificationHelper.postGenericDelta(app, delta, host);
                long latency = System.currentTimeMillis() - started;
                RuntimeDiagnostics.syncFailed();
                recordStatus(app, "Live • detail unavailable", latency, true);
                AppLogger.warn(app, "notification_detail", error.getClass().getSimpleName());
            }
        });
    }

    private static void recordStatus(Context context, String status, long latency, boolean force) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String oldStatus = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
        long lastWrite = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, 0L);
        boolean statusChanged = !status.equals(oldStatus);
        if (!force && !statusChanged && now - lastWrite < STATUS_WRITE_INTERVAL_MS) return;

        prefs.edit()
                .putLong(AppPrefs.NOTIFICATION_LAST_SYNC_AT, now)
                .putString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, status)
                .putLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, Math.max(0L, latency))
                .apply();
    }

    private ForumNotificationSync() {}
}
