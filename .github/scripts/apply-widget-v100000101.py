#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "source code"
JAVA = SOURCE / "src/com/harleytg/forum"
RES = SOURCE / "res"
OLD_CODE = "10000099"
NEW_CODE = "100000101"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_required(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected {label}: {old!r}")
    return text.replace(old, new)


# -----------------------------------------------------------------------------
# Widget Java source
# -----------------------------------------------------------------------------
widget_java = r'''package com.harleytg.forum.dev;

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
'''
write(JAVA / "HcfWidget.java", widget_java)


# -----------------------------------------------------------------------------
# Widget resources
# -----------------------------------------------------------------------------
layout = r'''<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_hcf_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_hcf_background">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="10dp">

        <LinearLayout
            android:id="@+id/widget_hcf_body"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/widget_hcf_logo"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:contentDescription="@string/widget_hcf_logo_content_description"
                android:scaleType="centerInside"
                android:src="@drawable/htg_app_logo" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:gravity="center_vertical"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/widget_hcf_title"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:ellipsize="end"
                    android:fontFamily="sans-serif-medium"
                    android:maxLines="1"
                    android:text="@string/widget_hcf_title"
                    android:textColor="@color/hcf_text"
                    android:textSize="15sp" />

                <TextView
                    android:id="@+id/widget_hcf_status"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="2dp"
                    android:ellipsize="end"
                    android:maxLines="1"
                    android:text="@string/widget_hcf_status_initial"
                    android:textColor="@color/hcf_muted"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>

        <LinearLayout
            android:id="@+id/widget_hcf_actions"
            android:layout_width="match_parent"
            android:layout_height="36dp"
            android:layout_marginTop="6dp"
            android:gravity="center"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/widget_hcf_forum"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="@drawable/widget_hcf_action_background"
                android:ellipsize="end"
                android:fontFamily="sans-serif-medium"
                android:gravity="center"
                android:maxLines="1"
                android:paddingStart="3dp"
                android:paddingEnd="3dp"
                android:text="@string/widget_hcf_forum"
                android:textColor="@color/hcf_cyan"
                android:textSize="11sp" />

            <TextView
                android:id="@+id/widget_hcf_notifications"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_marginStart="5dp"
                android:layout_weight="1"
                android:background="@drawable/widget_hcf_action_background"
                android:ellipsize="end"
                android:fontFamily="sans-serif-medium"
                android:gravity="center"
                android:maxLines="1"
                android:paddingStart="3dp"
                android:paddingEnd="3dp"
                android:text="@string/widget_hcf_notifications"
                android:textColor="@color/hcf_cyan"
                android:textSize="11sp" />

            <TextView
                android:id="@+id/widget_hcf_reload"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_marginStart="5dp"
                android:layout_weight="1"
                android:background="@drawable/widget_hcf_action_background"
                android:ellipsize="end"
                android:fontFamily="sans-serif-medium"
                android:gravity="center"
                android:maxLines="1"
                android:paddingStart="3dp"
                android:paddingEnd="3dp"
                android:text="@string/widget_hcf_reload"
                android:textColor="@color/hcf_cyan"
                android:textSize="11sp" />

            <TextView
                android:id="@+id/widget_hcf_settings"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_marginStart="5dp"
                android:layout_weight="1"
                android:background="@drawable/widget_hcf_action_background"
                android:ellipsize="end"
                android:fontFamily="sans-serif-medium"
                android:gravity="center"
                android:maxLines="1"
                android:paddingStart="3dp"
                android:paddingEnd="3dp"
                android:text="@string/widget_hcf_settings"
                android:textColor="@color/hcf_cyan"
                android:textSize="11sp" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
'''
write(RES / "layout/widget_hcf_notifications.xml", layout)

widget_info = r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:minResizeWidth="220dp"
    android:minResizeHeight="110dp"
    android:maxResizeWidth="530dp"
    android:maxResizeHeight="300dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_hcf_notifications"
    android:previewLayout="@layout/widget_hcf_notifications"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_hcf_description" />
'''
write(RES / "xml/hcf_widget_info.xml", widget_info)

widget_bg = r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/hcf_panel" />
    <stroke android:width="1dp" android:color="@color/hcf_border" />
    <corners android:radius="18dp" />
</shape>
'''
write(RES / "drawable/widget_hcf_background.xml", widget_bg)

action_bg = r'''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/hcf_quick_pressed" />
            <stroke android:width="1dp" android:color="@color/hcf_cyan" />
            <corners android:radius="11dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/hcf_quick_bg" />
            <stroke android:width="1dp" android:color="@color/hcf_border" />
            <corners android:radius="11dp" />
        </shape>
    </item>
</selector>
'''
write(RES / "drawable/widget_hcf_action_background.xml", action_bg)


# -----------------------------------------------------------------------------
# Manifest + strings
# -----------------------------------------------------------------------------
manifest_path = SOURCE / "AndroidManifest.xml"
manifest = read(manifest_path)
manifest = replace_required(manifest, 'android:versionCode="10000099"', 'android:versionCode="100000101"', "manifest versionCode")
manifest = replace_required(manifest, 'android:versionName="1.0 (10000099)"', 'android:versionName="1.1 (100000101)"', "manifest versionName")
manifest = replace_required(manifest, 'android:value="1.0 (10000099)"', 'android:value="1.1 (100000101)"', "manifest app version metadata")
manifest = replace_required(manifest, 'android:name="com.harleytg.APP_VERSION_CODE" android:value="10000099"', 'android:name="com.harleytg.APP_VERSION_CODE" android:value="100000101"', "manifest version code metadata")
if "HcfWidget$NotificationsProvider" not in manifest:
    anchor = '        <receiver android:name="com.harleytg.forum.dev.HcfNotifications$NotificationActionReceiver" android:enabled="true" android:exported="false"/>\n'
    block = anchor + '''        <receiver android:name="com.harleytg.forum.dev.HcfWidget$NotificationsProvider" android:enabled="true" android:exported="false" android:label="@string/widget_hcf_name">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE"/></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/hcf_widget_info"/>
        </receiver>
        <provider android:name="com.harleytg.forum.dev.HcfWidget$BootstrapProvider" android:exported="false" android:authorities="com.harleytg.forum.dev.widgetbootstrap" android:initOrder="80"/>
'''
    manifest = replace_required(manifest, anchor, block, "notification receiver insertion point")
write(manifest_path, manifest)

strings_path = RES / "values/strings.xml"
strings = read(strings_path)
if 'name="widget_hcf_name"' not in strings:
    addition = r'''    <string name="widget_hcf_name">HCF Notifications</string>
    <string name="widget_hcf_description">Harley\'s Clan Forum notifications and quick actions</string>
    <string name="widget_hcf_title">Harley\'s Clan Forum</string>
    <string name="widget_hcf_logo_content_description">Harley\'s Clan Forum logo</string>
    <string name="widget_hcf_status_initial">Tap to open forum</string>
    <string name="widget_hcf_signed_out">Sign in for notifications</string>
    <string name="widget_hcf_no_notifications">No new notifications · tap to open</string>
    <string name="widget_hcf_unread_count">%1$d unread · tap to open</string>
    <string name="widget_hcf_forum">Forum</string>
    <string name="widget_hcf_notifications">Notifs</string>
    <string name="widget_hcf_reload">Reload</string>
    <string name="widget_hcf_settings">Settings</string>
'''
    strings = replace_required(strings, "</resources>", addition + "</resources>", "strings closing tag")
write(strings_path, strings)


# -----------------------------------------------------------------------------
# Build identity
# -----------------------------------------------------------------------------
core_path = JAVA / "HcfCore.java"
core = read(core_path)
for old, new, label in [
    ('static final String APK_FILE_NAME = "HCF-Beta-v10000099.apk";', 'static final String APK_FILE_NAME = "HCF-Beta-v100000101.apk";', "APK filename"),
    ('static final String DEVELOPMENT_BUILD_LABEL = "Harley\'s Clan Forum v1.0 [Development Build / Beta]";', 'static final String DEVELOPMENT_BUILD_LABEL = "Harley\'s Clan Forum v1.1 [Development Build / Beta]";', "development label"),
    ('static final int INTERNAL_BUILD = 118;', 'static final int INTERNAL_BUILD = 119;', "internal build"),
    ('static final String META_LINE = "1.0 • Development / Beta";', 'static final String META_LINE = "1.1 • Development / Beta";', "meta line"),
    ('static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0 Build/10000099";', 'static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.1 Build/100000101";', "user agent"),
    ('static final String VERSION = "1.0";', 'static final String VERSION = "1.1";', "version"),
    ('static final int VERSION_CODE = 10000099;', 'static final int VERSION_CODE = 100000101;', "BuildInfo versionCode"),
    ('static final String VERSION_TAG = "v1.0";', 'static final String VERSION_TAG = "v1.1";', "version tag"),
]:
    core = replace_required(core, old, new, label)
write(core_path, core)


# -----------------------------------------------------------------------------
# README + release verifier
# -----------------------------------------------------------------------------
readme_path = ROOT / "README.md"
readme = read(readme_path)
readme = replace_required(readme, '- Version name: `1.0 (10000099)`', '- Version name: `1.1 (100000101)`', "README version name")
readme = replace_required(readme, '- Version code: `10000099`', '- Version code: `100000101`', "README version code")
readme = replace_required(readme, '- Internal build: `118`', '- Internal build: `119`', "README internal build")
readme = replace_required(readme, 'six consolidated Java subsystem sources: `HcfCore`, `HcfForum`, `HcfUI`,\n  `HcfNotifications`, `HcfUpdates`, and `HcfPlatform`.', 'seven consolidated Java subsystem sources: `HcfCore`, `HcfForum`, `HcfUI`,\n  `HcfNotifications`, `HcfUpdates`, `HcfPlatform`, plus the home-screen `HcfWidget` provider.', "README Java source description")
readme = readme.replace("v10000099", "v100000101")
write(readme_path, readme)

verifier_path = ROOT / ".github/scripts/verify-release-readiness.py"
verifier = read(verifier_path)
verifier = replace_required(verifier, "EXPECTED_VERSION_CODE = 10000099", "EXPECTED_VERSION_CODE = 100000101", "verifier version code")
verifier = replace_required(verifier, "EXPECTED_INTERNAL_BUILD = 118", "EXPECTED_INTERNAL_BUILD = 119", "verifier internal build")
verifier = replace_required(verifier, 'require("wrong versionName", name_match.group(1) == f"1.0 ({version_code})")', 'require("wrong versionName", name_match.group(1) == f"1.1 ({version_code})")', "verifier version name")
verifier = replace_required(verifier, '    "HcfUI.java",\n}', '    "HcfUI.java",\n    "HcfWidget.java",\n}', "verifier Java source set")
verifier = replace_required(verifier, 'require("canonical v10000099 workflow missing", any(path.name == "build-dev-v10000099.yml" for path in workflows))', 'require("canonical v100000101 workflow missing", any(path.name == "build-dev-v100000101.yml" for path in workflows))', "verifier canonical workflow")
write(verifier_path, verifier)


# -----------------------------------------------------------------------------
# Versioned build workflow rename/update
# -----------------------------------------------------------------------------
old_workflow = ROOT / ".github/workflows/build-dev-v10000099.yml"
new_workflow = ROOT / ".github/workflows/build-dev-v100000101.yml"
workflow = read(old_workflow)
workflow = replace_required(workflow, "versionName='1.0 (10000099)'", "versionName='1.1 (100000101)'", "workflow versionName assertion")
workflow = workflow.replace("10000099", "100000101")
write(new_workflow, workflow)
old_workflow.unlink()

print("HCF widget + Beta 1.1 (100000101) patch applied successfully.")
