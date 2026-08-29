package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Home-screen widget support and closely related local notification utilities. */
public final class HcfWidget {
    private static final String ACTION_RELOAD =
            "com.harleytg.forum.dev.action.HCF_WIDGET_RELOAD";
    private static final String ACTION_SCHEDULED_REFRESH =
            "com.harleytg.forum.dev.action.HCF_WIDGET_SCHEDULED_REFRESH";
    private static final String ACTION_NOTIFICATION_EVENT =
            "com.harleytg.forum.dev.NOTIFICATION_EVENT";

    private static final String EXTRA_EVENT_TITLE = "event_title";
    private static final String EXTRA_EVENT_BODY = "event_body";
    private static final String EXTRA_EVENT_URL = "event_url";
    private static final String EXTRA_EVENT_COUNT = "event_count";

    public static final String EXTRA_WIDGET_TARGET =
            "com.harleytg.forum.dev.extra.HCF_WIDGET_TARGET";
    public static final String TARGET_FORUM = "forum";
    public static final String TARGET_NOTIFICATIONS = "notifications";

    public static final String PREF_SHOW_CONNECTED_USERNAME = AppPrefs.WIDGET_SHOW_CONNECTED_USERNAME;
    public static final String PREF_SHOW_UNREAD_COUNT = AppPrefs.WIDGET_SHOW_UNREAD_COUNT;
    public static final String PREF_COMPACT_MODE = AppPrefs.WIDGET_COMPACT_MODE;
    public static final String PREF_SHOW_LAST_UPDATED = AppPrefs.WIDGET_SHOW_LAST_UPDATED;
    public static final String PREF_DEFAULT_TAP_ACTION = AppPrefs.WIDGET_DEFAULT_TAP_ACTION;
    public static final String PREF_LAST_REALTIME_SYNC_MS = "widget_last_realtime_sync_ms";

    public static final String PREF_BACKGROUND_ALPHA = "widget_background_alpha";
    public static final String PREF_TEXT_SIZE_SP = "widget_text_size_sp";
    public static final String PREF_REFRESH_INTERVAL_MIN = "widget_refresh_interval_min";
    public static final String PREF_SHOW_LAST_NOTIFICATION_PREVIEW =
            "widget_show_last_notification_preview";
    public static final String PREF_HISTORY_MODE = "native_notification_history_mode";
    public static final String PREF_HISTORY_LIMIT = "native_notification_history_limit";
    public static final String HISTORY_MODE_OFF = "off";
    public static final String HISTORY_MODE_TITLE = "title";
    public static final String HISTORY_MODE_FULL = "full";

    private static final String PREF_HISTORY_JSON = "native_notification_history_json";
    private static final String PREF_LAST_TITLE = "widget_last_notification_title";
    private static final String PREF_LAST_BODY = "widget_last_notification_body";
    private static final String PREF_LAST_URL = "widget_last_notification_url";
    private static final String PREF_LAST_EVENT_MS = "widget_last_notification_event_ms";

    public static final String TAP_FORUM = "forum";
    public static final String TAP_NOTIFICATIONS = "notifications";
    public static final String TAP_SETTINGS = "settings";
    public static final String TAP_LATEST = "latest";
    public static final String TAP_PROFILE = "profile";

    private static final String ROUTE_EXTRA = "hcf_native_route";
    private static final String ROUTE_LATEST = "latest";
    private static final String ROUTE_PROFILE = "profile";

    private static final int HISTORY_LIMIT = 60;
    private static final int REQUEST_OPEN_BODY = 42100;
    private static final int REQUEST_OPEN_FORUM = 42101;
    private static final int REQUEST_OPEN_NOTIFICATIONS = 42102;
    private static final int REQUEST_RELOAD = 42103;
    private static final int REQUEST_SETTINGS = 42104;
    private static final int REQUEST_LATEST = 42105;
    private static final int REQUEST_PROFILE = 42106;
    private static final int REQUEST_SCHEDULED_REFRESH = 42108;

    private static final int PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private static SharedPreferences monitoredPreferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    private HcfWidget() {}

    /** Standard HCF widget. */
    public static final class NotificationsProvider extends AppWidgetProvider {
        @Override
        public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
            if (context == null || manager == null || appWidgetIds == null) return;
            for (int appWidgetId : appWidgetIds) updateWidget(context, manager, appWidgetId, false);
            scheduleAutomaticRefresh(context);
        }

        @Override
        public void onEnabled(Context context) {
            super.onEnabled(context);
            scheduleAutomaticRefresh(context);
        }

        @Override
        public void onDisabled(Context context) {
            super.onDisabled(context);
            cancelAutomaticRefreshIfNoWidgets(context);
        }

        @Override
        public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                              int appWidgetId, Bundle newOptions) {
            super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
            if (context != null && manager != null) {
                updateWidget(context, manager, appWidgetId, false);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null && intent != null && ACTION_RELOAD.equals(intent.getAction())) {
                forceRefresh(context, "manual");
                return;
            }
            super.onReceive(context, intent);
        }
    }

    /** Optional second layout focused on unread notifications. */
    public static final class UnreadProvider extends AppWidgetProvider {
        @Override
        public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
            if (context == null || manager == null || appWidgetIds == null) return;
            for (int appWidgetId : appWidgetIds) updateWidget(context, manager, appWidgetId, true);
            scheduleAutomaticRefresh(context);
        }

        @Override
        public void onEnabled(Context context) {
            super.onEnabled(context);
            scheduleAutomaticRefresh(context);
        }

        @Override
        public void onDisabled(Context context) {
            super.onDisabled(context);
            cancelAutomaticRefreshIfNoWidgets(context);
        }

        @Override
        public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                              int appWidgetId, Bundle newOptions) {
            super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
            if (context != null && manager != null) {
                updateWidget(context, manager, appWidgetId, true);
            }
        }
    }

    /** Receives configurable inexact refresh alarms. */
    public static final class RefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null
                    || !ACTION_SCHEDULED_REFRESH.equals(intent.getAction())) return;
            forceRefresh(context, "scheduled");
        }
    }

    /**
     * Captures the existing package-scoped HCF notification event into a rolling local history.
     * This observes the existing notification engine rather than replacing its delivery logic.
     */
    public static final class NotificationEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null
                    || !ACTION_NOTIFICATION_EVENT.equals(intent.getAction())) return;

            String title = safe(intent.getStringExtra(EXTRA_EVENT_TITLE));
            String body = safe(intent.getStringExtra(EXTRA_EVENT_BODY));
            String url = safe(intent.getStringExtra(EXTRA_EVENT_URL));
            int count = intent.getIntExtra(EXTRA_EVENT_COUNT, -1);
            long now = System.currentTimeMillis();

            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String historyMode = historyMode(prefs);
            int historyLimit = historyLimit(prefs);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(PREF_LAST_TITLE, title)
                    .putString(PREF_LAST_BODY, body)
                    .putString(PREF_LAST_URL, url)
                    .putLong(PREF_LAST_EVENT_MS, now);

            if (HISTORY_MODE_OFF.equals(historyMode)) {
                editor.remove(PREF_HISTORY_JSON);
            } else {
                JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
                JSONArray next = new JSONArray();
                try {
                    JSONObject item = historyItem(title, body, url, count, now, historyMode);
                    next.put(item);
                    for (int i = 0; i < history.length() && next.length() < historyLimit; i++) {
                        JSONObject existing = history.optJSONObject(i);
                        if (existing == null) continue;
                        next.put(historyItem(
                                existing.optString("title", ""),
                                existing.optString("body", ""),
                                existing.optString("url", ""),
                                existing.optInt("count", -1),
                                existing.optLong("time", 0L),
                                historyMode));
                    }
                    editor.putString(PREF_HISTORY_JSON, next.toString());
                } catch (Throwable ignored) {}
            }

            editor.apply();
            refreshAll(context);
        }
    }

    /** Widget-specific native settings. */
    public static final class SettingsActivity extends Activity {
        private SharedPreferences prefs;
        private TextView alphaValue;
        private TextView sizeValue;
        private TextView refreshStatus;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            setTitle("Home-screen Widget");
            setContentView(buildUi());
        }

        private View buildUi() {
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(16), dp(18), dp(28));
            root.setBackgroundColor(Color.rgb(13, 16, 20));
            scroll.addView(root, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(label("Home-screen Widget", 22, true), matchWrap());
            TextView subtitle = label(
                    "Appearance, refresh, preview and tap behavior for HCF widgets.", 13, false);
            subtitle.setTextColor(Color.rgb(174, 187, 194));
            root.addView(subtitle, spaced(4));

            root.addView(section("Background transparency"), spaced(18));
            alphaValue = label("", 13, false);
            alphaValue.setTextColor(Color.rgb(174, 187, 194));
            root.addView(alphaValue, spaced(2));
            SeekBar alpha = new SeekBar(this);
            alpha.setMax(80);
            int currentAlpha = clamp(prefs.getInt(PREF_BACKGROUND_ALPHA, 96), 20, 100);
            alpha.setProgress(currentAlpha - 20);
            updateAlphaText(currentAlpha);
            root.addView(alpha, matchWrap());
            alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int value = progress + 20;
                    updateAlphaText(value);
                    if (fromUser) prefs.edit().putInt(PREF_BACKGROUND_ALPHA, value).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    refreshAll(SettingsActivity.this);
                }
            });

            root.addView(section("Widget text size"), spaced(16));
            sizeValue = label("", 13, false);
            sizeValue.setTextColor(Color.rgb(174, 187, 194));
            root.addView(sizeValue, spaced(2));
            SeekBar size = new SeekBar(this);
            size.setMax(8);
            int currentSize = clamp(prefs.getInt(PREF_TEXT_SIZE_SP, 12), 10, 18);
            size.setProgress(currentSize - 10);
            updateSizeText(currentSize);
            root.addView(size, matchWrap());
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int value = progress + 10;
                    updateSizeText(value);
                    if (fromUser) prefs.edit().putInt(PREF_TEXT_SIZE_SP, value).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    refreshAll(SettingsActivity.this);
                }
            });

            final CheckBox preview = checkbox(
                    "Show last notification preview",
                    prefs.getBoolean(PREF_SHOW_LAST_NOTIFICATION_PREVIEW, true));
            root.addView(preview, spaced(14));
            preview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putBoolean(
                            PREF_SHOW_LAST_NOTIFICATION_PREVIEW, preview.isChecked()).apply();
                    refreshAll(SettingsActivity.this);
                }
            });

            root.addView(section("Notification history privacy"), spaced(16));
            TextView historyHelp = label(
                    "Choose what HCF keeps in the local notification history. Widget preview storage is controlled separately above.",
                    12, false);
            historyHelp.setTextColor(Color.rgb(174, 187, 194));
            root.addView(historyHelp, spaced(2));

            RadioGroup historyModeGroup = new RadioGroup(this);
            historyModeGroup.setOrientation(RadioGroup.VERTICAL);
            final String[] historyModes = {HISTORY_MODE_OFF, HISTORY_MODE_TITLE, HISTORY_MODE_FULL};
            final String[] historyModeNames = {"Off", "Titles only", "Titles + message"};
            String selectedHistoryMode = historyMode(prefs);
            for (int i = 0; i < historyModes.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47200 + i);
                button.setText(historyModeNames[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(historyModes[i]);
                historyModeGroup.addView(button);
                if (historyModes[i].equals(selectedHistoryMode)) button.setChecked(true);
            }
            root.addView(historyModeGroup, matchWrap());
            historyModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof String)) return;
                    setHistoryPrivacy(prefs, (String) checked.getTag(), historyLimit(prefs));
                }
            });

            TextView retentionTitle = label("History retention", 14, true);
            retentionTitle.setTextColor(Color.rgb(0, 184, 240));
            root.addView(retentionTitle, spaced(12));
            RadioGroup retentionGroup = new RadioGroup(this);
            retentionGroup.setOrientation(RadioGroup.VERTICAL);
            final int[] retentionValues = {10, 30, 60};
            final String[] retentionNames = {"Keep 10 events", "Keep 30 events", "Keep 60 events"};
            int selectedRetention = historyLimit(prefs);
            for (int i = 0; i < retentionValues.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47300 + i);
                button.setText(retentionNames[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(Integer.valueOf(retentionValues[i]));
                retentionGroup.addView(button);
                if (retentionValues[i] == selectedRetention) button.setChecked(true);
            }
            root.addView(retentionGroup, matchWrap());
            retentionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof Integer)) return;
                    setHistoryPrivacy(prefs, historyMode(prefs), ((Integer) checked.getTag()).intValue());
                }
            });

            root.addView(section("Automatic widget refresh"), spaced(16));
            refreshStatus = label(refreshStatusText(), 13, false);
            refreshStatus.setTextColor(Color.rgb(174, 187, 194));
            root.addView(refreshStatus, spaced(2));
            RadioGroup refreshGroup = new RadioGroup(this);
            refreshGroup.setOrientation(RadioGroup.VERTICAL);
            final int[] intervals = {0, 15, 30, 60, 120};
            final String[] names = {
                    "Off", "Every 15 minutes", "Every 30 minutes", "Every hour", "Every 2 hours"
            };
            int selectedInterval = prefs.getInt(PREF_REFRESH_INTERVAL_MIN, 30);
            for (int i = 0; i < intervals.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47000 + i);
                button.setText(names[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(Integer.valueOf(intervals[i]));
                refreshGroup.addView(button);
                if (intervals[i] == selectedInterval) button.setChecked(true);
            }
            root.addView(refreshGroup, matchWrap());
            refreshGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof Integer)) return;
                    int minutes = ((Integer) checked.getTag()).intValue();
                    prefs.edit().putInt(PREF_REFRESH_INTERVAL_MIN, minutes).apply();
                    scheduleAutomaticRefresh(SettingsActivity.this);
                    refreshAll(SettingsActivity.this);
                    refreshStatus.setText(refreshStatusText());
                }
            });

            root.addView(section("Default widget tap action"), spaced(16));
            RadioGroup tapGroup = new RadioGroup(this);
            tapGroup.setOrientation(RadioGroup.VERTICAL);
            final String[] tapValues = {
                    TAP_FORUM, TAP_NOTIFICATIONS, TAP_LATEST, TAP_PROFILE, TAP_SETTINGS
            };
            final String[] tapNames = {
                    "Forum home", "Notifications", "Latest Discussions", "Profile", "Widget settings"
            };
            String selectedTap = prefs.getString(PREF_DEFAULT_TAP_ACTION, TAP_FORUM);
            for (int i = 0; i < tapValues.length; i++) {
                RadioButton button = new RadioButton(this);
                button.setId(47100 + i);
                button.setText(tapNames[i]);
                button.setTextColor(Color.rgb(232, 248, 255));
                button.setTag(tapValues[i]);
                tapGroup.addView(button);
                if (tapValues[i].equals(selectedTap)) button.setChecked(true);
            }
            root.addView(tapGroup, matchWrap());
            tapGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    View checked = group.findViewById(checkedId);
                    if (checked == null || !(checked.getTag() instanceof String)) return;
                    prefs.edit().putString(PREF_DEFAULT_TAP_ACTION, (String) checked.getTag()).apply();
                    refreshAll(SettingsActivity.this);
                }
            });

            Button history = button("Open notification history");
            root.addView(history, spaced(18));
            history.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, NotificationHistoryActivity.class));
                }
            });

            Button refresh = button("Refresh widget now");
            root.addView(refresh, spaced(8));
            refresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    forceRefresh(SettingsActivity.this, "settings");
                    refreshStatus.setText(refreshStatusText());
                }
            });
            return scroll;
        }

        private String refreshStatusText() {
            int minutes = prefs.getInt(PREF_REFRESH_INTERVAL_MIN, 30);
            long last = prefs.getLong(PREF_LAST_REALTIME_SYNC_MS, 0L);
            String interval = minutes <= 0
                    ? "Auto refresh off" : "Auto refresh every " + minutes + " min";
            if (last <= 0L) return interval + " • waiting for first sync";
            return interval + " • last sync "
                    + android.text.format.DateFormat.getTimeFormat(this).format(new Date(last));
        }

        private void updateAlphaText(int value) {
            alphaValue.setText(value + "% background opacity");
        }

        private void updateSizeText(int value) {
            sizeValue.setText(value + " sp base text size");
        }

        private TextView section(String text) {
            TextView view = label(text, 16, true);
            view.setTextColor(Color.rgb(0, 184, 240));
            return view;
        }

        private TextView label(String text, int sp, boolean bold) {
            TextView view = new TextView(this);
            view.setText(text);
            view.setTextSize(sp);
            view.setTextColor(Color.rgb(232, 248, 255));
            if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            return view;
        }

        private CheckBox checkbox(String text, boolean checked) {
            CheckBox box = new CheckBox(this);
            box.setText(text);
            box.setChecked(checked);
            box.setTextColor(Color.rgb(232, 248, 255));
            return box;
        }

        private Button button(String text) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(text);
            return button;
        }

        private LinearLayout.LayoutParams matchWrap() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private LinearLayout.LayoutParams spaced(int topDp) {
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(topDp);
            return params;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    /** Local-only notification history screen. */
    public static final class NotificationHistoryActivity extends Activity {
        private LinearLayout list;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            setTitle("Notification history");
            setContentView(buildUi());
            renderHistory();
        }

        @Override
        protected void onResume() {
            super.onResume();
            renderHistory();
        }

        private View buildUi() {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(16), dp(18), dp(18));
            root.setBackgroundColor(Color.rgb(13, 16, 20));

            root.addView(text("Notification history", 22, true), matchWrap());
            TextView subtitle = text(
                    "Recent HCF notification events stored locally on this device.", 13, false);
            subtitle.setTextColor(Color.rgb(174, 187, 194));
            LinearLayout.LayoutParams subtitleParams = matchWrap();
            subtitleParams.topMargin = dp(4);
            root.addView(subtitle, subtitleParams);

            Button clear = new Button(this);
            clear.setAllCaps(false);
            clear.setText("Clear history");
            LinearLayout.LayoutParams clearParams = matchWrap();
            clearParams.topMargin = dp(10);
            root.addView(clear, clearParams);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                            .remove(PREF_HISTORY_JSON)
                            .apply();
                    refreshAll(NotificationHistoryActivity.this);
                    renderHistory();
                }
            });

            ScrollView scroll = new ScrollView(this);
            list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, dp(10), 0, dp(20));
            scroll.addView(list, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        private void renderHistory() {
            if (list == null) return;
            list.removeAllViews();
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String mode = historyMode(prefs);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            if (history.length() == 0) {
                TextView empty = text(HISTORY_MODE_OFF.equals(mode)
                        ? "Notification history is turned off."
                        : "No notification history yet.", 15, false);
                empty.setTextColor(Color.rgb(174, 187, 194));
                list.addView(empty, matchWrap());
                return;
            }

            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                final String url = item.optString("url", "");
                String title = item.optString("title", "Harley's Clan Forum");
                String body = item.optString("body", "");
                long time = item.optLong("time", 0L);

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackgroundColor(Color.rgb(24, 31, 37));
                card.addView(text(
                        TextUtils.isEmpty(title) ? "Harley's Clan Forum" : title, 15, true),
                        matchWrap());

                if (!TextUtils.isEmpty(body)) {
                    TextView bodyView = text(body, 13, false);
                    bodyView.setTextColor(Color.rgb(214, 225, 231));
                    LinearLayout.LayoutParams bodyParams = matchWrap();
                    bodyParams.topMargin = dp(3);
                    card.addView(bodyView, bodyParams);
                }

                if (time > 0L) {
                    TextView when = text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(new Date(time)), 11, false);
                    when.setTextColor(Color.rgb(174, 187, 194));
                    LinearLayout.LayoutParams whenParams = matchWrap();
                    whenParams.topMargin = dp(5);
                    card.addView(when, whenParams);
                }

                if (!TextUtils.isEmpty(url)) {
                    card.setClickable(true);
                    card.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(
                                    NotificationHistoryActivity.this, RouteActivity.class);
                            intent.setData(Uri.parse(url));
                            startActivity(intent);
                        }
                    });
                }

                LinearLayout.LayoutParams cardParams = matchWrap();
                cardParams.topMargin = dp(8);
                list.addView(card, cardParams);
            }
        }

        private TextView text(String value, int sp, boolean bold) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextColor(Color.rgb(232, 248, 255));
            view.setTextSize(sp);
            if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            return view;
        }

        private LinearLayout.LayoutParams matchWrap() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    /** Lightweight internal route trampoline for widget actions/history. */
    public static final class RouteActivity extends Activity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            route(getIntent());
            finish();
        }

        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            route(intent);
            finish();
        }

        private void route(Intent source) {
            String direct = source == null || source.getData() == null
                    ? "" : source.getData().toString();
            if (direct.startsWith("http://") || direct.startsWith("https://")) {
                openForumUri(Uri.parse(direct));
                return;
            }

            String route = source == null ? "" : safe(source.getStringExtra(ROUTE_EXTRA));
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String host = prefs.getString("active_host", "forum.harleytg.com");
            if (TextUtils.isEmpty(host)) host = "forum.harleytg.com";

            String path = "/";
            if (ROUTE_PROFILE.equals(route)) {
                String username = safe(prefs.getString(AppPrefs.IDENTITY_USERNAME, "")).trim();
                path = TextUtils.isEmpty(username)
                        ? "/settings" : "/u/" + Uri.encode(stripAt(username));
            } else if (ROUTE_LATEST.equals(route)) {
                path = "/";
            }
            openForumUri(Uri.parse("https://" + host + path));
        }

        private void openForumUri(Uri uri) {
            try {
                Intent next = new Intent(this, HcfSafeMode.EntryActivity.class);
                next.setAction(Intent.ACTION_VIEW);
                next.setData(uri);
                next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(next);
            } catch (Throwable error) {
                try {
                    Intent fallback = new Intent(this, HcfForum.MainActivity.class);
                    fallback.setAction(Intent.ACTION_VIEW);
                    fallback.setData(uri);
                    startActivity(fallback);
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Starts preference monitoring early and keeps widget scheduling synchronized. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            installPreferenceRefresh(getContext());
            scheduleAutomaticRefresh(getContext());
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
                        || AppPrefs.SESSION_USER_ID.equals(key)
                        || AppPrefs.IDENTITY_USERNAME.equals(key)
                        || PREF_SHOW_CONNECTED_USERNAME.equals(key)
                        || PREF_SHOW_UNREAD_COUNT.equals(key)
                        || PREF_COMPACT_MODE.equals(key)
                        || PREF_SHOW_LAST_UPDATED.equals(key)
                        || PREF_DEFAULT_TAP_ACTION.equals(key)
                        || PREF_LAST_REALTIME_SYNC_MS.equals(key)
                        || PREF_BACKGROUND_ALPHA.equals(key)
                        || PREF_TEXT_SIZE_SP.equals(key)
                        || PREF_SHOW_LAST_NOTIFICATION_PREVIEW.equals(key)
                        || PREF_LAST_TITLE.equals(key)
                        || PREF_LAST_BODY.equals(key)
                        || PREF_LAST_EVENT_MS.equals(key)
                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)
                        || AppPrefs.APP_THEME.equals(key)
                        || AppPrefs.FORUM_AUTO_THEME.equals(key)) {
                    refreshAll(refreshContext);
                }
            }
        };
        monitoredPreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    /** Public refresh hook for notification/runtime components. */
    public static void refreshAll(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        refreshProvider(app, manager, NotificationsProvider.class, false);
        refreshProvider(app, manager, UnreadProvider.class, true);
    }

    private static void refreshProvider(Context context, AppWidgetManager manager,
                                        Class<?> providerClass, boolean unreadFocused) {
        ComponentName provider = new ComponentName(context, providerClass);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) updateWidget(context, manager, id, unreadFocused);
    }

    private static void updateWidget(Context context, AppWidgetManager manager,
                                     int appWidgetId, boolean unreadFocused) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String userId = prefs.getString(AppPrefs.SESSION_USER_ID, "");
        boolean signedIn = userId != null && !userId.trim().isEmpty();
        int unreadCount = Math.max(0, prefs.getInt(AppPrefs.LAST_NOTIFICATION_COUNT, 0));
        boolean showConnectedUsername = prefs.getBoolean(PREF_SHOW_CONNECTED_USERNAME, true);
        boolean showUnreadCount = prefs.getBoolean(PREF_SHOW_UNREAD_COUNT, true);
        boolean compactMode = prefs.getBoolean(PREF_COMPACT_MODE, false);
        boolean showLastUpdated = prefs.getBoolean(PREF_SHOW_LAST_UPDATED, false);
        boolean showPreview = prefs.getBoolean(PREF_SHOW_LAST_NOTIFICATION_PREVIEW, true);
        int backgroundAlpha = clamp(prefs.getInt(PREF_BACKGROUND_ALPHA, 96), 20, 100);
        int textSize = clamp(prefs.getInt(PREF_TEXT_SIZE_SP, 12), 10, 18);
        int refreshMinutes = Math.max(0, prefs.getInt(PREF_REFRESH_INTERVAL_MIN, 30));
        long lastRealtimeSyncMs = Math.max(
                0L, prefs.getLong(PREF_LAST_REALTIME_SYNC_MS, 0L));
        String username = prefs.getString(AppPrefs.IDENTITY_USERNAME, "");
        String connectedHandle = username == null ? "" : username.trim();
        if (!connectedHandle.isEmpty() && !connectedHandle.startsWith("@")) {
            connectedHandle = "@" + connectedHandle;
        }

        CharSequence status;
        if (!signedIn) {
            status = context.getString(R.string.widget_hcf_signed_out);
        } else {
            String identityState = showConnectedUsername && !connectedHandle.isEmpty()
                    ? connectedHandle : "";
            String notificationState = "";
            if (showUnreadCount || unreadFocused) {
                notificationState = unreadCount == 0
                        ? context.getString(R.string.widget_hcf_no_notifications)
                        : context.getString(R.string.widget_hcf_unread_count, unreadCount);
            }
            if (!identityState.isEmpty() && !notificationState.isEmpty() && !unreadFocused) {
                status = identityState + " • " + notificationState;
            } else if (!notificationState.isEmpty()) {
                status = notificationState;
            } else if (!identityState.isEmpty()) {
                status = identityState;
            } else {
                status = "Connected to forum";
            }
        }

        String updatedText = lastRealtimeSyncMs > 0L
                ? "Synced " + android.text.format.DateFormat.getTimeFormat(context)
                        .format(new Date(lastRealtimeSyncMs))
                : "Waiting for live sync";
        updatedText += refreshMinutes > 0
                ? " • auto " + refreshMinutes + "m" : " • auto off";

        String previewTitle = prefs.getString(PREF_LAST_TITLE, "");
        String previewBody = prefs.getString(PREF_LAST_BODY, "");
        String previewText = buildPreview(previewTitle, previewBody);

        int layout = unreadFocused
                ? R.layout.widget_hcf_unread : R.layout.widget_hcf_notifications;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setTextViewText(
                R.id.widget_hcf_title,
                unreadFocused ? "Unread notifications" : context.getString(R.string.widget_hcf_title));
        views.setTextViewText(R.id.widget_hcf_status, status);
        views.setTextViewText(R.id.widget_hcf_updated, updatedText);
        views.setTextViewText(R.id.widget_hcf_preview, previewText);
        views.setViewVisibility(
                R.id.widget_hcf_preview,
                showPreview && !TextUtils.isEmpty(previewText) ? View.VISIBLE : View.GONE);

        if (!unreadFocused) {
            views.setViewVisibility(
                    R.id.widget_hcf_logo, compactMode ? View.GONE : View.VISIBLE);
            views.setViewVisibility(
                    R.id.widget_hcf_title, compactMode ? View.GONE : View.VISIBLE);
            views.setViewVisibility(
                    R.id.widget_hcf_updated,
                    showLastUpdated && !compactMode ? View.VISIBLE : View.GONE);
        } else {
            views.setViewVisibility(
                    R.id.widget_hcf_updated, showLastUpdated ? View.VISIBLE : View.GONE);
            views.setTextViewText(
                    R.id.widget_hcf_unread_number,
                    signedIn ? String.valueOf(unreadCount) : "—");
        }

        applyWidgetTheme(context, prefs, views, backgroundAlpha, textSize, unreadFocused);

        views.setOnClickPendingIntent(
                R.id.widget_hcf_body, bodyPendingIntent(context, prefs));
        views.setOnClickPendingIntent(
                R.id.widget_hcf_notifications,
                startupPendingIntent(context, REQUEST_OPEN_NOTIFICATIONS, TARGET_NOTIFICATIONS));
        views.setOnClickPendingIntent(
                R.id.widget_hcf_reload, reloadPendingIntent(context));
        views.setOnClickPendingIntent(
                R.id.widget_hcf_settings, settingsPendingIntent(context));

        if (!unreadFocused) {
            views.setOnClickPendingIntent(
                    R.id.widget_hcf_forum,
                    startupPendingIntent(context, REQUEST_OPEN_FORUM, TARGET_FORUM));
        } else {
            views.setOnClickPendingIntent(
                    R.id.widget_hcf_latest,
                    routePendingIntent(context, REQUEST_LATEST, ROUTE_LATEST));
            views.setOnClickPendingIntent(
                    R.id.widget_hcf_profile,
                    routePendingIntent(context, REQUEST_PROFILE, ROUTE_PROFILE));
        }
        manager.updateAppWidget(appWidgetId, views);
    }

    private static void applyWidgetTheme(Context context, SharedPreferences prefs,
                                         RemoteViews views, int backgroundAlpha,
                                         int textSize, boolean unreadFocused) {
        if (context == null || prefs == null || views == null) return;

        boolean followAppTheme = prefs.getBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, true);
        boolean amoled = followAppTheme && ThemeManager.isAmoled(context);
        boolean dark = followAppTheme
                ? "dark".equals(ThemeManager.webColorScheme(context))
                : systemPhoneDark();

        int rootBackground = amoled
                ? R.drawable.widget_hcf_background_amoled
                : dark ? R.drawable.widget_hcf_background_dark
                : R.drawable.widget_hcf_background_light;
        int actionBackground = amoled
                ? R.drawable.widget_hcf_action_background_amoled
                : dark ? R.drawable.widget_hcf_action_background_dark
                : R.drawable.widget_hcf_action_background_light;
        int titleColor = (amoled || dark) ? 0xFFE8F8FF : 0xFF10232B;
        int mutedColor = (amoled || dark) ? 0xFFAEBBC2 : 0xFF53666F;
        int accentColor = 0xFF00B8F0;

        views.setInt(R.id.widget_hcf_background_layer, "setBackgroundResource", rootBackground);
        views.setFloat(
                R.id.widget_hcf_background_layer, "setAlpha", backgroundAlpha / 100f);
        views.setTextColor(R.id.widget_hcf_title, titleColor);
        views.setTextColor(R.id.widget_hcf_status, mutedColor);
        views.setTextColor(R.id.widget_hcf_preview, titleColor);
        views.setTextColor(R.id.widget_hcf_updated, mutedColor);
        views.setTextViewTextSize(
                R.id.widget_hcf_title, TypedValue.COMPLEX_UNIT_SP, textSize + 3f);
        views.setTextViewTextSize(
                R.id.widget_hcf_status, TypedValue.COMPLEX_UNIT_SP, textSize);
        views.setTextViewTextSize(
                R.id.widget_hcf_preview,
                TypedValue.COMPLEX_UNIT_SP, Math.max(10f, textSize - 1f));
        views.setTextViewTextSize(
                R.id.widget_hcf_updated,
                TypedValue.COMPLEX_UNIT_SP, Math.max(9f, textSize - 2f));

        int[] actions = unreadFocused
                ? new int[]{
                        R.id.widget_hcf_notifications,
                        R.id.widget_hcf_latest,
                        R.id.widget_hcf_profile,
                        R.id.widget_hcf_reload,
                        R.id.widget_hcf_settings
                }
                : new int[]{
                        R.id.widget_hcf_forum,
                        R.id.widget_hcf_notifications,
                        R.id.widget_hcf_reload,
                        R.id.widget_hcf_settings
                };
        for (int action : actions) {
            views.setInt(action, "setBackgroundResource", actionBackground);
            views.setTextColor(action, accentColor);
            views.setTextViewTextSize(
                    action, TypedValue.COMPLEX_UNIT_SP, Math.max(9f, textSize - 1f));
        }
        if (unreadFocused) {
            views.setTextColor(R.id.widget_hcf_unread_number, accentColor);
            views.setTextViewTextSize(
                    R.id.widget_hcf_unread_number,
                    TypedValue.COMPLEX_UNIT_SP, textSize + 14f);
        }
    }

    private static String buildPreview(String title, String body) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanBody = body == null ? "" : body.trim();
        if (cleanTitle.isEmpty()) return cleanBody;
        if (cleanBody.isEmpty()) return cleanTitle;
        return cleanTitle + " — " + cleanBody;
    }

    private static boolean systemPhoneDark() {
        try {
            int uiMode = android.content.res.Resources.getSystem().getConfiguration().uiMode;
            return (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PendingIntent bodyPendingIntent(Context context, SharedPreferences prefs) {
        String action = prefs == null
                ? TAP_FORUM : prefs.getString(PREF_DEFAULT_TAP_ACTION, TAP_FORUM);
        if (TAP_SETTINGS.equals(action)) return settingsPendingIntent(context);
        if (TAP_NOTIFICATIONS.equals(action)) {
            return startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_NOTIFICATIONS);
        }
        if (TAP_LATEST.equals(action)) {
            return routePendingIntent(context, REQUEST_OPEN_BODY, ROUTE_LATEST);
        }
        if (TAP_PROFILE.equals(action)) {
            return routePendingIntent(context, REQUEST_OPEN_BODY, ROUTE_PROFILE);
        }
        return startupPendingIntent(context, REQUEST_OPEN_BODY, TARGET_FORUM);
    }

    private static PendingIntent startupPendingIntent(
            Context context, int requestCode, String target) {
        Intent intent = new Intent(context, HcfUI.StartupActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.putExtra(EXTRA_WIDGET_TARGET, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent routePendingIntent(
            Context context, int requestCode, String route) {
        Intent intent = new Intent(context, RouteActivity.class);
        intent.setAction("com.harleytg.forum.dev.action.HCF_ROUTE." + route);
        intent.putExtra(ROUTE_EXTRA, route);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent settingsPendingIntent(Context context) {
        Intent intent = new Intent(context, HcfSubActivities.SettingsActivity.class);
        intent.setAction("com.harleytg.forum.dev.action.HCF_WIDGET_SETTINGS");
        intent.putExtra(HcfSubActivities.SettingsActivity.EXTRA_SETTINGS_SECTION, "widget");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, REQUEST_SETTINGS, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent reloadPendingIntent(Context context) {
        Intent intent = new Intent(context, NotificationsProvider.class);
        intent.setAction(ACTION_RELOAD);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(context, REQUEST_RELOAD, intent, PENDING_INTENT_FLAGS);
    }

    private static PendingIntent scheduledRefreshPendingIntent(Context context) {
        Intent intent = new Intent(context, RefreshReceiver.class);
        intent.setAction(ACTION_SCHEDULED_REFRESH);
        return PendingIntent.getBroadcast(
                context, REQUEST_SCHEDULED_REFRESH, intent, PENDING_INTENT_FLAGS);
    }

    private static void forceRefresh(Context context, String source) {
        if (context == null) return;
        refreshAll(context);
        try {
            HcfNotifications.InstantNotificationService.requestImmediateSync(context);
        } catch (Throwable error) {
            try {
                AppLogger.warn(
                        context,
                        "hcf_widget_reload",
                        source + " • " + error.getClass().getSimpleName());
            } catch (Throwable ignored) {}
        }
    }

    static void scheduleAutomaticRefresh(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        int minutes = Math.max(0, prefs.getInt(PREF_REFRESH_INTERVAL_MIN, 30));
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pending = scheduledRefreshPendingIntent(app);
        alarm.cancel(pending);
        if (minutes <= 0 || !hasAnyPlacedWidgets(app)) return;

        long interval = Math.max(15L * 60L * 1000L, minutes * 60L * 1000L);
        long first = SystemClock.elapsedRealtime() + interval;
        alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, first, interval, pending);
    }

    private static void cancelAutomaticRefreshIfNoWidgets(Context context) {
        if (context == null || hasAnyPlacedWidgets(context)) return;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(scheduledRefreshPendingIntent(context));
    }

    private static boolean hasAnyPlacedWidgets(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] standard = manager.getAppWidgetIds(
                    new ComponentName(context, NotificationsProvider.class));
            int[] unread = manager.getAppWidgetIds(
                    new ComponentName(context, UnreadProvider.class));
            return (standard != null && standard.length > 0)
                    || (unread != null && unread.length > 0);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String historyMode(SharedPreferences prefs) {
        if (prefs == null) return HISTORY_MODE_FULL;
        return normalizeHistoryMode(prefs.getString(PREF_HISTORY_MODE, HISTORY_MODE_FULL));
    }

    public static int historyLimit(SharedPreferences prefs) {
        if (prefs == null) return HISTORY_LIMIT;
        return normalizeHistoryLimit(prefs.getInt(PREF_HISTORY_LIMIT, HISTORY_LIMIT));
    }

    public static String historyModeLabel(String mode) {
        String normalized = normalizeHistoryMode(mode);
        if (HISTORY_MODE_OFF.equals(normalized)) return "Off";
        if (HISTORY_MODE_TITLE.equals(normalized)) return "Titles only";
        return "Titles + message";
    }

    public static void setHistoryPrivacy(SharedPreferences prefs, String mode, int limit) {
        if (prefs == null) return;
        String normalizedMode = normalizeHistoryMode(mode);
        int normalizedLimit = normalizeHistoryLimit(limit);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(PREF_HISTORY_MODE, normalizedMode)
                .putInt(PREF_HISTORY_LIMIT, normalizedLimit);
        if (HISTORY_MODE_OFF.equals(normalizedMode)) {
            editor.remove(PREF_HISTORY_JSON).apply();
            return;
        }

        JSONArray current = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
        JSONArray sanitized = new JSONArray();
        try {
            for (int i = 0; i < current.length() && sanitized.length() < normalizedLimit; i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                sanitized.put(historyItem(
                        item.optString("title", ""),
                        item.optString("body", ""),
                        item.optString("url", ""),
                        item.optInt("count", -1),
                        item.optLong("time", 0L),
                        normalizedMode));
            }
            editor.putString(PREF_HISTORY_JSON, sanitized.toString());
        } catch (Throwable ignored) {}
        editor.apply();
    }

    private static JSONObject historyItem(String title, String body, String url,
                                          int count, long time, String mode) throws Exception {
        JSONObject item = new JSONObject();
        item.put("title", safe(title));
        if (HISTORY_MODE_FULL.equals(normalizeHistoryMode(mode))) {
            item.put("body", safe(body));
            item.put("url", safe(url));
        } else {
            item.put("body", "");
            item.put("url", "");
        }
        item.put("count", count);
        item.put("time", time);
        return item;
    }

    private static String normalizeHistoryMode(String mode) {
        if (HISTORY_MODE_OFF.equals(mode) || HISTORY_MODE_TITLE.equals(mode)) return mode;
        return HISTORY_MODE_FULL;
    }

    private static int normalizeHistoryLimit(int limit) {
        if (limit <= 10) return 10;
        if (limit <= 30) return 30;
        return 60;
    }

    private static JSONArray parseHistory(String raw) {
        try {
            return new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw);
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String stripAt(String value) {
        return value.startsWith("@") ? value.substring(1) : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
