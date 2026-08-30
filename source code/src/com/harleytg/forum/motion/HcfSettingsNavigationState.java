package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Preserves the exact App Settings location across Activity recreation.
 *
 * Some settings intentionally recreate SettingsActivity (theme/profile/import
 * refreshes). HcfUI's normal create path starts on the App Settings home screen,
 * which made changing a setting look like Back had been pressed. This helper uses
 * Android saved-instance-state only, so a genuine close/reopen still starts from
 * the normal Settings entry point.
 */
public final class HcfSettingsNavigationState {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";
    private static final String STATE_SECTION =
            "hcf.settings.navigation.section.v1";
    private static final String STATE_OPEN_PANEL =
            "hcf.settings.navigation.open_panel.v1";
    private static final String STATE_PENDING_KEY =
            "hcf.settings.navigation.pending_key.v1";

    private static final WeakHashMap<Activity, RestoreState> PENDING =
            new WeakHashMap<>();
    private static boolean registered;

    private HcfSettingsNavigationState() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context app = context.getApplicationContext();
            if (registered || !(app instanceof Application)) return true;
            registered = true;
            ((Application) app).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity activity, Bundle state) {
                            if (!isSettings(activity) || state == null) return;
                            String section = clean(state.getString(STATE_SECTION));
                            if (section.isEmpty()) return;
                            RestoreState restore = new RestoreState(
                                    section,
                                    clean(state.getString(STATE_OPEN_PANEL)),
                                    clean(state.getString(STATE_PENDING_KEY)));
                            synchronized (PENDING) { PENDING.put(activity, restore); }
                            scheduleRestore(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}

                        @Override public void onActivityResumed(Activity activity) {
                            if (!isSettings(activity)) return;
                            synchronized (PENDING) {
                                if (!PENDING.containsKey(activity)) return;
                            }
                            scheduleRestore(activity);
                        }

                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}

                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                            if (!isSettings(activity) || outState == null) return;
                            String section = clean(readStringField(activity, "currentSettingsSection"));
                            if (section.isEmpty()) return;

                            outState.putString(STATE_SECTION, section);
                            String pendingKey = clean(readStringField(activity, "pendingSettingKey"));
                            if (!pendingKey.isEmpty()) outState.putString(STATE_PENDING_KEY, pendingKey);

                            ViewGroup content = readViewGroupField(activity, "settingsContent");
                            String openTitle = findOpenPanelTitle(content);
                            if (!openTitle.isEmpty()) outState.putString(STATE_OPEN_PANEL, openTitle);
                        }

                        @Override public void onActivityDestroyed(Activity activity) {
                            synchronized (PENDING) { PENDING.remove(activity); }
                        }
                    });
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

    private static void scheduleRestore(final Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        final View root = activity.getWindow().getDecorView();
        if (root == null) return;
        root.post(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                RestoreState state;
                synchronized (PENDING) { state = PENDING.get(activity); }
                if (state == null) return;

                // If HcfUI already restored the requested category itself, don't
                // rebuild it a second time. Otherwise restore the category now.
                String current = clean(readStringField(activity, "currentSettingsSection"));
                if (!state.section.equals(current)) {
                    if (!state.pendingKey.isEmpty()) {
                        writeStringField(activity, "pendingSettingKey", state.pendingKey);
                    }
                    invokeShowSettingsSection(activity, state.section);
                }

                // HcfSettingsStableUi owns final accordion wiring. Re-open the saved
                // subsetting after that owner has had frames to settle the rebuild.
                root.postOnAnimation(new Runnable() {
                    @Override public void run() {
                        root.postOnAnimation(new Runnable() {
                            @Override public void run() {
                                restoreOpenPanel(activity);
                            }
                        });
                    }
                });
            }
        });
    }

    private static void restoreOpenPanel(Activity activity) {
        RestoreState state;
        synchronized (PENDING) { state = PENDING.remove(activity); }
        if (state == null || state.openPanelTitle.isEmpty()
                || activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup content = readViewGroupField(activity, "settingsContent");
        ViewGroup panel = findPanelByTitle(content, state.openPanelTitle);
        if (panel == null || panel.getChildCount() < 2) return;
        View header = panel.getChildAt(0);
        View body = panel.getChildAt(1);
        if (body.getVisibility() != View.VISIBLE && header.isClickable()) {
            header.performClick();
        }
    }

    private static void invokeShowSettingsSection(Activity activity, String section) {
        try {
            Method method = activity.getClass().getDeclaredMethod("showSettingsSection", String.class);
            method.setAccessible(true);
            method.invoke(activity, section);
        } catch (Throwable ignored) {}
    }

    private static String findOpenPanelTitle(ViewGroup content) {
        if (content == null) return "";
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup panel = (ViewGroup) child;
            if (panel.getChildCount() < 2) continue;
            View header = panel.getChildAt(0);
            View body = panel.getChildAt(1);
            if (body.getVisibility() != View.VISIBLE) continue;
            String title = firstMeaningfulText(header);
            if (!title.isEmpty()) return title;
        }
        return "";
    }

    private static ViewGroup findPanelByTitle(ViewGroup content, String wanted) {
        if (content == null || wanted == null || wanted.isEmpty()) return null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup panel = (ViewGroup) child;
            if (panel.getChildCount() < 2) continue;
            String title = firstMeaningfulText(panel.getChildAt(0));
            if (wanted.equals(title)) return panel;
        }
        return null;
    }

    private static String firstMeaningfulText(View view) {
        if (view == null) return "";
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            String value = clean(text == null ? null : text.toString());
            // Skip the standalone chevron used by connected Settings headers.
            if (!value.isEmpty() && !"›".equals(value) && !"⌄".equals(value)) return value;
        }
        if (!(view instanceof ViewGroup)) return "";
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            String value = firstMeaningfulText(group.getChildAt(i));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static ViewGroup readViewGroupField(Activity activity, String name) {
        View value = readViewField(activity, name);
        return value instanceof ViewGroup ? (ViewGroup) value : null;
    }

    private static View readViewField(Activity activity, String name) {
        Object value = readField(activity, name);
        return value instanceof View ? (View) value : null;
    }

    private static String readStringField(Activity activity, String name) {
        Object value = readField(activity, name);
        return value instanceof String ? (String) value : "";
    }

    private static Object readField(Activity activity, String name) {
        if (activity == null) return null;
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(activity);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void writeStringField(Activity activity, String name, String value) {
        if (activity == null) return;
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(activity, value == null ? "" : value);
                return;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static boolean isSettings(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class RestoreState {
        final String section;
        final String openPanelTitle;
        final String pendingKey;

        RestoreState(String section, String openPanelTitle, String pendingKey) {
            this.section = clean(section);
            this.openPanelTitle = clean(openPanelTitle);
            this.pendingKey = clean(pendingKey);
        }
    }
}
