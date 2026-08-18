package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Lightweight, foreground-only freshness watcher for supported Flarum routes. */
final class LiveForumUpdater {
    interface Listener {
        String currentUrl();
        void onChangeCandidate(String key, String fingerprint);
        void onStateChanged(String state);
    }

    private static final long FIRST_POLL_MS = 250L;
    private static final long IDLE_ROUTE_POLL_MS = 5000L;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 180000;

    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    private boolean running;
    private boolean inFlight;
    private int failures;
    private String baselineKey;
    private String baselineFingerprint;
    private String lastState = "";

    LiveForumUpdater(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
        this.prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
    }

    void start() {
        if (running) return;
        running = true;
        failures = 0;
        state("SYNCING");
        schedule(FIRST_POLL_MS);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
        state("PAUSED");
    }

    void destroy() { stop(); }

    void reset() {
        baselineKey = null;
        baselineFingerprint = null;
    }

    void poke() {
        if (!running) return;
        schedule(0L);
    }

    void noteUserInteraction() {
        RuntimeState.noteUserInteraction();
        if (running) schedule(Math.min(250L, PerformanceProfile.livePollInterval(app, prefs)));
    }

    void acknowledge(String key, String fingerprint) {
        if (key == null || fingerprint == null) return;
        baselineKey = key;
        baselineFingerprint = fingerprint;
    }

    private final Runnable tick = this::poll;

    private void schedule(long delay) {
        if (!running) return;
        long safe = Math.max(0L, delay);
        RuntimeDiagnostics.livePoll(safe);
        main.removeCallbacks(tick);
        main.postDelayed(tick, safe);
    }

    private void poll() {
        if (!running) return;
        if (inFlight) {
            schedule(250L);
            return;
        }
        if (!RuntimeState.networkAvailable(app)) {
            state("OFFLINE");
            schedule(IDLE_ROUTE_POLL_MS);
            return;
        }

        final Uri page;
        try { page = Uri.parse(listener.currentUrl()); }
        catch (Throwable ignored) {
            schedule(IDLE_ROUTE_POLL_MS);
            return;
        }
        if (!ForumUrlRouter.isForumUrl(page)) {
            state("PAUSED");
            schedule(IDLE_ROUTE_POLL_MS);
            return;
        }

        final String endpoint = endpointFor(page);
        final String key = pageKey(page, endpoint);
        if (key == null || endpoint == null) {
            state("LIVE");
            schedule(IDLE_ROUTE_POLL_MS);
            return;
        }

        inFlight = true;
        AppExecutors.network().execute(() -> {
            String fingerprint = null;
            try {
                fingerprint = fetchFingerprint(endpoint);
            } catch (Throwable t) {
                if (failures == 0 || failures == 2) {
                    AppLogger.warn(app, "live_update_poll", t.getClass().getSimpleName());
                }
            }
            final String result = fingerprint;
            main.post(() -> handleResult(key, result));
        });
    }

    private void handleResult(String key, String result) {
        inFlight = false;
        if (!running) return;
        if (result == null || result.isEmpty()) {
            failures = Math.min(failures + 1, 5);
            state(RuntimeState.networkAvailable(app) ? "WAITING" : "OFFLINE");
            schedule(Math.min(15000L, 2000L << Math.min(Math.max(0, failures - 1), 3)));
            return;
        }

        failures = 0;
        state("LIVE");
        if (!key.equals(baselineKey) || baselineFingerprint == null) {
            baselineKey = key;
            baselineFingerprint = result;
        } else if (!result.equals(baselineFingerprint)) {
            listener.onChangeCandidate(key, result);
        }
        schedule(PerformanceProfile.livePollInterval(app, prefs));
    }

    private void state(String value) {
        if (value.equals(lastState)) return;
        lastState = value;
        listener.onStateChanged(value);
    }

    private String endpointFor(Uri page) {
        String host = page.getHost();
        if (host == null) return null;
        String path = page.getPath() == null ? "/" : page.getPath();
        String lower = path.toLowerCase(Locale.US);
        String base = "https://" + host;

        if (lower.startsWith("/d/")) {
            String tail = path.substring(3);
            int slash = tail.indexOf('/');
            if (slash >= 0) tail = tail.substring(0, slash);
            int dash = tail.indexOf('-');
            String id = dash >= 0 ? tail.substring(0, dash) : tail;
            if (id.matches("[0-9]+")) {
                return base + "/api/discussions/" + id
                        + "?fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
            }
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following")
                || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return base + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1"
                    + "&fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
        }
        if (lower.startsWith("/notifications")) {
            return base + "/api/notifications?page%5Blimit%5D=1";
        }
        return null;
    }

    private String pageKey(Uri page, String endpoint) {
        if (endpoint == null) return null;
        String path = page.getPath() == null ? "/" : page.getPath();
        return (page.getHost() == null ? "" : page.getHost()) + "|" + path + "|" + endpoint;
    }

    private String fetchFingerprint(String endpoint) throws Exception {
        CacheEntry previous = cache.get(endpoint);
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " LiveUpdate");
            if (previous != null) {
                if (!previous.etag.isEmpty()) connection.setRequestProperty("If-None-Match", previous.etag);
                if (previous.lastModified > 0L) connection.setIfModifiedSince(previous.lastModified);
            }

            String origin = url.getProtocol() + "://" + url.getHost() + "/";
            String cookies = CookieManager.getInstance().getCookie(origin);
            if (cookies != null && !cookies.trim().isEmpty()) connection.setRequestProperty("Cookie", cookies);

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && previous != null) return previous.signature;
            if (code < 200 || code >= 300) return null;

            String body = readLimited(connection.getInputStream());
            String signature = compactSignature(body);
            if (signature == null || signature.isEmpty()) return null;

            CacheEntry next = new CacheEntry();
            String etag = connection.getHeaderField("ETag");
            next.etag = etag == null ? "" : etag;
            next.lastModified = connection.getLastModified();
            next.signature = signature;
            cache.put(endpoint, next);
            return signature;
        } finally {
            connection.disconnect();
        }
    }

    private String compactSignature(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(body);
            Object data = root.opt("data");
            StringBuilder out = new StringBuilder(160);
            if (data instanceof JSONObject) {
                appendResourceSignature(out, (JSONObject) data);
            } else if (data instanceof JSONArray) {
                JSONArray array = (JSONArray) data;
                for (int i = 0; i < array.length() && i < 2; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) appendResourceSignature(out, item);
                }
            }
            if (out.length() > 0) return out.toString();
        } catch (Throwable ignored) {}

        // Small bounded fallback only; v10000033 no longer SHA-256 hashes a huge response.
        int length = Math.min(body.length(), 32768);
        String sample = body.substring(0, length);
        return "fallback|" + body.length() + "|" + Integer.toHexString(sample.hashCode());
    }

    private void appendResourceSignature(StringBuilder out, JSONObject resource) {
        out.append(resource.optString("type", "")).append(':')
                .append(resource.optString("id", ""));
        JSONObject attrs = resource.optJSONObject("attributes");
        if (attrs != null) {
            appendAttr(out, attrs, "lastPostNumber");
            appendAttr(out, attrs, "lastPostedAt");
            appendAttr(out, attrs, "commentCount");
            appendAttr(out, attrs, "newNotificationCount");
            appendAttr(out, attrs, "unreadNotificationCount");
            appendAttr(out, attrs, "time");
            appendAttr(out, attrs, "createdAt");
            appendAttr(out, attrs, "isRead");
            appendAttr(out, attrs, "type");
        }
        out.append('|');
    }

    private void appendAttr(StringBuilder out, JSONObject attrs, String name) {
        if (!attrs.has(name)) return;
        Object value = attrs.opt(name);
        if (value == null || value == JSONObject.NULL) return;
        out.append(';').append(name).append('=').append(String.valueOf(value));
    }

    private String readLimited(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1 && body.length() < MAX_BODY_CHARS) {
            body.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - body.length()));
        }
        reader.close();
        return body.toString();
    }

    private static final class CacheEntry {
        String etag = "";
        long lastModified;
        String signature = "";
    }
}
