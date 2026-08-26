package com.harleytg.forum.dev;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

/** Home-screen widget support for Harley's Clan Forum. */
public final class HcfWidget {
    private static final String ACTION_RELOAD =
            "com.harleytg.forum.dev.action.HCF_WIDGET_RELOAD";

    public static final String EXTRA_WIDGET_TARGET =
            "com.harleytg.forum.dev.extra.HCF_WIDGET_TARGET";

    public static final String TARGET_FORUM = "forum";
    public static final String TARGET_NOTIFICATIONS = "notifications";

    private static final int REQUEST_OPEN_BODY = 42100;
    private static final int REQUEST_OPEN_FORUM = 42101;
    private static final int REQUEST_OPEN_NOTIFICATIONS = 42102;
    private static final int REQUEST_RELOAD = 42103;
    private static final int REQUEST_SETTINGS = 42104;

    private static final int PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private static SharedPreferences monitoredPreferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private HcfWidget() {}

    /** Receiver registered as the Android home-screen App Widget provider. */
    public static final class NotificationsProvider extends AppWidgetProvider {
        public NotificationsProvider() {
            super();
        }

        @Override
        public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
            if (context == null || manager == null || appWidgetIds == null) return;
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, manager, appWidgetId);
            }
        }

        @Override
        public void onAppWidgetOptionsChanged(
                Context context,
                AppWidgetManager manager,
                int appWidgetId,
                Bundle newOptions) {
            super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
            if (context != null && manager != null) {
                updateWidget(context, manager, appWidgetId);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null && intent != null && ACTION_RELOAD.equals(intent.getAction())) {
                refreshAll(context);
                try {
                    HcfNotifications.InstantNotificationService.requestImmediateSync(context);
                } catch (Throwable error) {
                    try {
                        AppLogger.warn(context, "hcf_widget_reload", error.getClass().getSimpleName());
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
            super.onReceive(context, intent);
        }
    }

    /**
     * Registers a process-lifetime SharedPreferences listener before normal app UI starts.
     * This keeps placed widgets current whenever the notification engine updates the
     * cached unread count or a forum session is created/cleared, without adding network
     * work to the widget provider itself.
     */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            installPreferenceRefresh(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }

    private static synchronized void installPreferenceRefresh(Context context) {
        if (context == null || preferenceListener != null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context refreshContext = app;
        monitoredPreferences = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        preferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (AppPrefs.LAST_NOTIFICATION_COUNT.equals(key)
                        || AppPrefs.SESSION_USER_ID.equals(key)) {
                    refreshAll(refreshContext);
                }
            }
        };
        monitoredPreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    /** Public refresh hook for the notification engine and other HCF components. */
    public static void refreshAll(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ComponentName provider = new ComponentName(app, NotificationsProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            updateWidget(app, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        boolean signedIn = userId != null && !userId.trim().isEmpty();
        int unreadCount = Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, 0));

        CharSequence status;
        if (!signedIn) {
            status = context.getString(R.string.widget_hcf_signed_out);
        } else if (unreadCount == 0) {
            status = context.getString(R.string.widget_hcf_no_notifications);
        } else {
            status = context.getString(R.string.widget_hcf_unread_count, unreadCount);
        }

        RemoteViews views = new RemoteViews(
                context.getPackageName(),
                R.layout.widget_hcf_notifications
        );
        views.setTextViewText(R.id.widget_hcf_title, context.getString(R.string.widget_hcf_title));
        views.setTextViewText(R.id.widget_hcf_status, status);

        views.setOnClickPendingIntent(
                R.id.widget_hcf_body,
                startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_FORUM)
        );
        views.setOnClickPendingIntent(
                R.id.widget_hcf_forum,
                startupPendingIntent(context, REQUEST_OPEN_FORUM, TARGET_FORUM)
        );
        views.setOnClickPendingIntent(
                R.id.widget_hcf_notifications,
                startupPendingIntent(context, REQUEST_OPEN_NOTIFICATIONS, TARGET_NOTIFICATIONS)
        );
        views.setOnClickPendingIntent(R.id.widget_hcf_reload, reloadPendingIntent(context));
        views.setOnClickPendingIntent(R.id.widget_hcf_settings, settingsPendingIntent(context));
        manager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent startupPendingIntent(Context context, int requestCode, String target) {
        Intent intent = new Intent(context, HcfUI.StartupActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.putExtra(EXTRA_WIDGET_TARGET, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent settingsPendingIntent(Context context) {
        Intent intent = new Intent(context, HcfSubActivities.SettingsActivity.class);
        intent.setAction("com.harleytg.forum.dev.action.HCF_WIDGET_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, REQUEST_SETTINGS, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent reloadPendingIntent(Context context) {
        Intent intent = new Intent(context, NotificationsProvider.class);
        intent.setAction(ACTION_RELOAD);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(context, REQUEST_RELOAD, intent, PENDING_INTENT_FLAGS);
    }
}
