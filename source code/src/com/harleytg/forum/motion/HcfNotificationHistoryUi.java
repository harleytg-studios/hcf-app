package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.util.Date;
import java.util.WeakHashMap;

/**
 * Keeps local notification history inside App Settings > Notifications instead of
 * sending users to the legacy standalone history activity.
 *
 * The provider only augments the native Settings hierarchy. It does not touch the
 * forum WebView, notification delivery, or the home-screen widget preview store.
 */
public final class HcfNotificationHistoryUi {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";
    private static final String HISTORY_PREF = "native_notification_history_json";
    private static final String TARGET_HISTORY = "hcf_setting:open_notification_history";
    private static final String INLINE_TAG = "hcf_notification_history_inline";
    private static final String CLEAR_TAG = "hcf_notification_history_clear";
    private static final String ROUTE_TAG = "hcf_notification_history_route";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>
            OBSERVERS = new WeakHashMap<>();
    private static boolean registered;

    private HcfNotificationHistoryUi() {}

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
                            install(activity);
                        }
                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityResumed(Activity activity) {
                            install(activity);
                        }
                        @Override public void onActivityPaused(Activity activity) {
                            removeObserver(activity);
                        }
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                        @Override public void onActivityDestroyed(Activity activity) {
                            removeObserver(activity);
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

    private static void install(final Activity activity) {
        if (!isSettingsActivity(activity) || activity.isFinishing()) return;
        enhance(activity);

        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            final View root = activity.getWindow() == null
                    ? null : activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            enhance(activity);
                        }
                    };
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    private static void removeObserver(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) {
            listener = OBSERVERS.remove(activity);
        }
        if (listener == null || activity.getWindow() == null) return;
        View root = activity.getWindow().getDecorView();
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
    }

    private static void enhance(Activity activity) {
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null) return;

        ViewGroup historyPanel = findConnectedPanel(content, "Notification History");
        if (historyPanel != null && historyPanel.getChildCount() == 2) {
            View body = historyPanel.getChildAt(1);
            if (body instanceof ViewGroup) embedHistory(activity, (ViewGroup) body);
        }

        // The widget-preview subsetting used to launch the old standalone history
        // activity. Keep the shortcut, but route it back to the Notifications
        // accordion so there is one UI owner for notification history.
        rerouteStandaloneButtons(activity, content, historyPanel);
    }

    private static void embedHistory(final Activity activity, ViewGroup body) {
        if (findTaggedView(body, INLINE_TAG) != null) return;

        Button openButton = findButton(body, "Open Notification History");
        if (openButton == null) return;
        ViewGroup parent = openButton.getParent() instanceof ViewGroup
                ? (ViewGroup) openButton.getParent() : null;
        if (parent == null) return;

        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setTag(INLINE_TAG);
        // Settings search for the old "Open Notification History" target now lands
        // directly on the inline history content instead of a launcher button.
        list.setContentDescription("Notification history list");
        list.setTag(TARGET_HISTORY);

        int buttonIndex = parent.indexOfChild(openButton);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(activity, 8);
        listLp.bottomMargin = dp(activity, 6);
        parent.addView(list, Math.min(parent.getChildCount(), buttonIndex + 1), listLp);

        openButton.setTag(CLEAR_TAG);
        openButton.setText("Clear history");
        openButton.setContentDescription("Clear notification history");
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = activity.getSharedPreferences(
                        AppPrefs.FILE, Context.MODE_PRIVATE);
                prefs.edit().remove(HISTORY_PREF).apply();
                try { HcfWidget.refreshAll(activity); } catch (Throwable ignored) {}
                renderHistory(activity, list, (Button) v);
            }
        });

        renderHistory(activity, list, openButton);
    }

    private static void renderHistory(final Activity activity, LinearLayout list, Button clear) {
        if (activity == null || list == null) return;
        list.removeAllViews();

        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String mode = HcfWidget.historyMode(prefs);
        JSONArray history = parseHistory(prefs.getString(HISTORY_PREF, "[]"));

        TextView heading = text(activity, "Recent notifications", 12,
                activity.getColor(R.color.hcf_cyan), true);
        heading.setPadding(dp(activity, 3), dp(activity, 3), 0, dp(activity, 5));
        list.addView(heading, matchWrap());

        boolean hasHistory = history.length() > 0;
        if (clear != null) {
            clear.setEnabled(hasHistory);
            clear.setAlpha(hasHistory ? 1.0f : 0.55f);
        }

        if (!hasHistory) {
            TextView empty = text(activity,
                    HcfWidget.HISTORY_MODE_OFF.equals(mode)
                            ? "Notification history is turned off."
                            : "No notification history yet.",
                    12, activity.getColor(R.color.hcf_muted), false);
            empty.setBackgroundResource(R.drawable.quick_action_background);
            empty.setPadding(dp(activity, 14), dp(activity, 12),
                    dp(activity, 14), dp(activity, 12));
            list.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;

            final String url = safe(item.optString("url", ""));
            String title = safe(item.optString("title", "Harley's Clan Forum"));
            String body = safe(item.optString("body", ""));
            long time = item.optLong("time", 0L);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.quick_action_background);
            card.setPadding(dp(activity, 14), dp(activity, 11),
                    dp(activity, 14), dp(activity, 11));

            TextView titleView = text(activity,
                    TextUtils.isEmpty(title) ? "Harley's Clan Forum" : title,
                    14, activity.getColor(R.color.hcf_text), true);
            card.addView(titleView, matchWrap());

            if (!TextUtils.isEmpty(body)) {
                TextView bodyView = text(activity, body, 11,
                        activity.getColor(R.color.hcf_muted), false);
                LinearLayout.LayoutParams bodyLp = matchWrap();
                bodyLp.topMargin = dp(activity, 3);
                card.addView(bodyView, bodyLp);
            }

            if (time > 0L) {
                TextView when = text(activity,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(new Date(time)),
                        10, activity.getColor(R.color.hcf_meta), false);
                LinearLayout.LayoutParams whenLp = matchWrap();
                whenLp.topMargin = dp(activity, 5);
                card.addView(when, whenLp);
            }

            if (!TextUtils.isEmpty(url)) {
                TextView hint = text(activity, "Tap to open", 10,
                        activity.getColor(R.color.hcf_cyan), true);
                LinearLayout.LayoutParams hintLp = matchWrap();
                hintLp.topMargin = dp(activity, 5);
                card.addView(hint, hintLp);
                card.setClickable(true);
                card.setFocusable(true);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(activity, HcfWidget.RouteActivity.class);
                        intent.setData(Uri.parse(url));
                        activity.startActivity(intent);
                    }
                });
            }

            LinearLayout.LayoutParams cardLp = matchWrap();
            cardLp.topMargin = dp(activity, 7);
            list.addView(card, cardLp);
        }
    }

    private static void rerouteStandaloneButtons(final Activity activity, View root,
                                                 ViewGroup historyPanel) {
        if (root == null) return;
        if (root instanceof Button) {
            final Button button = (Button) root;
            CharSequence label = button.getText();
            if (label != null && "Open Notification History".equalsIgnoreCase(
                    label.toString().trim())) {
                if (historyPanel != null && isDescendantOf(button, historyPanel)) return;
                if (ROUTE_TAG.equals(button.getTag())) return;
                button.setTag(ROUTE_TAG);
                button.setText("Notification History");
                button.setContentDescription("Open notification history settings");
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigateToInlineHistory(activity);
                    }
                });
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                rerouteStandaloneButtons(activity, group.getChildAt(i), historyPanel);
            }
        }
    }

    private static void navigateToInlineHistory(Activity activity) {
        try {
            Method method = findMethod(activity.getClass(), "navigateToSettingKey", String.class);
            if (method != null) {
                method.setAccessible(true);
                method.invoke(activity, "open_notification_history");
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback for future Settings refactors: set the existing navigation fields
        // then call the category renderer directly.
        try {
            writeStringField(activity, "pendingSettingKey", "open_notification_history");
            writeStringField(activity, "pendingSettingSection", "notification_history");
            Method method = findMethod(activity.getClass(), "showSettingsSection", String.class);
            if (method != null) {
                method.setAccessible(true);
                method.invoke(activity, "notifications");
            }
        } catch (Throwable ignored) {}
    }

    private static ViewGroup findConnectedPanel(ViewGroup content, String title) {
        if (content == null) return null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup panel = (ViewGroup) child;
            if (panel.getChildCount() != 2) continue;
            View header = panel.getChildAt(0);
            View body = panel.getChildAt(1);
            if (!(header instanceof LinearLayout) || !(body instanceof LinearLayout)) continue;
            if (containsText(header, title)) return panel;
        }
        return null;
    }

    private static boolean containsText(View root, String expected) {
        if (root == null || expected == null) return false;
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && expected.equalsIgnoreCase(value.toString().trim())) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected)) return true;
            }
        }
        return false;
    }

    private static Button findButton(View root, String label) {
        if (root == null) return null;
        if (root instanceof Button) {
            CharSequence value = ((Button) root).getText();
            if (value != null && label.equalsIgnoreCase(value.toString().trim())) {
                return (Button) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findTaggedView(View root, Object tag) {
        if (root == null || tag == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isDescendantOf(View child, ViewGroup ancestor) {
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static JSONArray parseHistory(String raw) {
        try {
            return new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    private static TextView text(Activity activity, String value, int sp,
                                 int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static boolean isSettingsActivity(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static ViewGroup readViewGroupField(Activity activity, String fieldName) {
        Object value = readField(activity, fieldName);
        return value instanceof ViewGroup ? (ViewGroup) value : null;
    }

    private static Object readField(Activity activity, String fieldName) {
        if (activity == null || fieldName == null) return null;
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(activity);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writeStringField(Activity activity, String fieldName, String value) {
        if (activity == null || fieldName == null) return;
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(activity, value == null ? "" : value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
