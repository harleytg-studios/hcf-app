package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Small native enhancements that observe existing HCF systems without replacing them. */
public final class HcfEnhancements {
    static final String ACTION_NOTIFICATION_EVENT = "com.harleytg.forum.dev.NOTIFICATION_EVENT";
    static final String EXTRA_EVENT_TITLE = "event_title";
    static final String EXTRA_EVENT_BODY = "event_body";
    static final String EXTRA_EVENT_URL = "event_url";
    static final String EXTRA_EVENT_COUNT = "event_count";

    public static final String PREF_HISTORY_JSON = "native_notification_history_json";
    public static final String PREF_LAST_TITLE = "widget_last_notification_title";
    public static final String PREF_LAST_BODY = "widget_last_notification_body";
    public static final String PREF_LAST_URL = "widget_last_notification_url";
    public static final String PREF_LAST_EVENT_MS = "widget_last_notification_event_ms";

    public static final String ROUTE_EXTRA = "hcf_native_route";
    public static final String ROUTE_LATEST = "latest";
    public static final String ROUTE_PROFILE = "profile";
    public static final String ROUTE_NEW_DISCUSSION = "new_discussion";
    public static final String ROUTE_NOTIFICATIONS = "notifications";
    public static final String ROUTE_FORUM = "forum";

    private static final int HISTORY_LIMIT = 60;

    private HcfEnhancements() {}

    /** Captures the existing in-app notification event broadcast into local-only history. */
    public static final class NotificationEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null || !ACTION_NOTIFICATION_EVENT.equals(intent.getAction())) return;
            String title = safe(intent.getStringExtra(EXTRA_EVENT_TITLE));
            String body = safe(intent.getStringExtra(EXTRA_EVENT_BODY));
            String url = safe(intent.getStringExtra(EXTRA_EVENT_URL));
            int count = intent.getIntExtra(EXTRA_EVENT_COUNT, -1);
            long now = System.currentTimeMillis();

            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            JSONArray next = new JSONArray();
            try {
                JSONObject item = new JSONObject();
                item.put("title", title);
                item.put("body", body);
                item.put("url", url);
                item.put("count", count);
                item.put("time", now);
                next.put(item);
                for (int i = 0; i < history.length() && next.length() < HISTORY_LIMIT; i++) {
                    Object existing = history.opt(i);
                    if (existing != null) next.put(existing);
                }
            } catch (Throwable ignored) {
                next = history;
            }

            prefs.edit()
                    .putString(PREF_HISTORY_JSON, next.toString())
                    .putString(PREF_LAST_TITLE, title)
                    .putString(PREF_LAST_BODY, body)
                    .putString(PREF_LAST_URL, url)
                    .putLong(PREF_LAST_EVENT_MS, now)
                    .apply();
            try { HcfWidget.refreshAll(context); } catch (Throwable ignored) {}
        }
    }

    /** Local notification history viewer. No notification contents leave the device. */
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

            TextView title = text("Notification history", 22, true);
            root.addView(title, matchWrap());

            TextView subtitle = text("Recent HCF notification events stored locally on this device.", 13, false);
            subtitle.setTextColor(Color.rgb(174, 187, 194));
            LinearLayout.LayoutParams subtitleLp = matchWrap();
            subtitleLp.topMargin = dp(4);
            root.addView(subtitle, subtitleLp);

            Button clear = new Button(this);
            clear.setAllCaps(false);
            clear.setText("Clear history");
            LinearLayout.LayoutParams clearLp = matchWrap();
            clearLp.topMargin = dp(10);
            root.addView(clear, clearLp);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                            .remove(PREF_HISTORY_JSON)
                            .remove(PREF_LAST_TITLE)
                            .remove(PREF_LAST_BODY)
                            .remove(PREF_LAST_URL)
                            .remove(PREF_LAST_EVENT_MS)
                            .apply();
                    HcfWidget.refreshAll(NotificationHistoryActivity.this);
                    renderHistory();
                }
            });

            ScrollView scroll = new ScrollView(this);
            list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, dp(10), 0, dp(20));
            scroll.addView(list, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            root.addView(scroll, scrollLp);
            return root;
        }

        private void renderHistory() {
            if (list == null) return;
            list.removeAllViews();
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            JSONArray history = parseHistory(prefs.getString(PREF_HISTORY_JSON, "[]"));
            if (history.length() == 0) {
                TextView empty = text("No notification history yet.", 15, false);
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

                TextView t = text(TextUtils.isEmpty(title) ? "Harley's Clan Forum" : title, 15, true);
                card.addView(t, matchWrap());
                if (!TextUtils.isEmpty(body)) {
                    TextView b = text(body, 13, false);
                    b.setTextColor(Color.rgb(214, 225, 231));
                    LinearLayout.LayoutParams bodyLp = matchWrap();
                    bodyLp.topMargin = dp(3);
                    card.addView(b, bodyLp);
                }
                if (time > 0L) {
                    TextView when = text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(new Date(time)), 11, false);
                    when.setTextColor(Color.rgb(174, 187, 194));
                    LinearLayout.LayoutParams whenLp = matchWrap();
                    whenLp.topMargin = dp(5);
                    card.addView(when, whenLp);
                }
                if (!TextUtils.isEmpty(url)) {
                    card.setClickable(true);
                    card.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { openUrl(url); }
                    });
                }
                LinearLayout.LayoutParams cardLp = matchWrap();
                cardLp.topMargin = dp(8);
                list.addView(card, cardLp);
            }
        }

        private void openUrl(String url) {
            if (TextUtils.isEmpty(url)) return;
            try {
                Intent intent = new Intent(this, RouteActivity.class);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            } catch (Throwable ignored) {}
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
            return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    /** Lightweight route trampoline used by widgets. */
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
            String direct = source == null || source.getData() == null ? "" : source.getData().toString();
            if (direct.startsWith("http://") || direct.startsWith("https://")) {
                openForumUri(Uri.parse(direct));
                return;
            }

            String route = source == null ? "" : safe(source.getStringExtra(ROUTE_EXTRA));
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String host = prefs.getString("active_host", "forum.harleytg.com");
            if (TextUtils.isEmpty(host)) host = "forum.harleytg.com";
            String path = "/";
            if (ROUTE_LATEST.equals(route)) {
                path = "/all";
            } else if (ROUTE_NOTIFICATIONS.equals(route)) {
                path = "/notifications";
            } else if (ROUTE_NEW_DISCUSSION.equals(route)) {
                path = "/compose";
            } else if (ROUTE_PROFILE.equals(route)) {
                String username = safe(prefs.getString(AppPrefs.IDENTITY_USERNAME, "")).trim();
                path = TextUtils.isEmpty(username) ? "/settings" : "/u/" + Uri.encode(stripAt(username));
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

    static JSONArray parseHistory(String raw) {
        try { return new JSONArray(TextUtils.isEmpty(raw) ? "[]" : raw); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String stripAt(String value) { return value.startsWith("@") ? value.substring(1) : value; }
}
