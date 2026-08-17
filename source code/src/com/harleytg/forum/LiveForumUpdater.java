package com.harleytg.forum.dev;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight, foreground-only freshness watcher for supported Flarum routes. */
final class LiveForumUpdater {
    interface Listener {
        String currentUrl();
        void onChangeCandidate(String key, String fingerprint);
        void onStateChanged(String state);
    }

    private static final long FIRST_POLL_MS = 350L;
    private static final long POLL_MS = 1250L;
    private static final long IDLE_ROUTE_POLL_MS = 4000L;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;

    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private boolean running;
    private boolean inFlight;
    private int failures;
    private String baselineKey;
    private String baselineFingerprint;
    private String lastState = "";

    LiveForumUpdater(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
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

    void destroy() {
        stop();
        worker.shutdownNow();
    }

    void reset() {
        baselineKey = null;
        baselineFingerprint = null;
    }

    void poke() {
        if (!running) return;
        schedule(0L);
    }

    void acknowledge(String key, String fingerprint) {
        if (key == null || fingerprint == null) return;
        baselineKey = key;
        baselineFingerprint = fingerprint;
    }

    private final Runnable tick = this::poll;

    private void schedule(long delay) {
        if (!running) return;
        main.removeCallbacks(tick);
        main.postDelayed(tick, Math.max(0L, delay));
    }

    private void poll() {
        if (!running) return;
        if (inFlight) {
            schedule(250L);
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
            // Alerts still remain live through the foreground notification service;
            // this route simply has no lightweight page-refresh endpoint.
            state("LIVE");
            schedule(IDLE_ROUTE_POLL_MS);
            return;
        }

        inFlight = true;
        worker.execute(() -> {
            String fingerprint = null;
            try {
                fingerprint = fetchFingerprint(endpoint);
            } catch (Throwable t) {
                AppLogger.warn(app, "live_update_poll", t.getClass().getSimpleName());
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
            state("WAITING");
            schedule(Math.min(15000L, 2000L << Math.min(failures - 1, 3)));
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
        schedule(POLL_MS);
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
            if (id.matches("[0-9]+")) return base + "/api/discussions/" + id;
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following")
                || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return base + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1";
        }
        if (lower.startsWith("/notifications")) {
            return base + "/api/notifications?page%5Blimit%5D=8";
        }
        return null;
    }

    private String pageKey(Uri page, String endpoint) {
        if (endpoint == null) return null;
        String path = page.getPath() == null ? "/" : page.getPath();
        return (page.getHost() == null ? "" : page.getHost()) + "|" + path + "|" + endpoint;
    }

    private String fetchFingerprint(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " LiveUpdate");

            String origin = url.getProtocol() + "://" + url.getHost() + "/";
            String cookies = CookieManager.getInstance().getCookie(origin);
            if (cookies != null && !cookies.trim().isEmpty()) connection.setRequestProperty("Cookie", cookies);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;

            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1 && body.length() < MAX_BODY_CHARS) {
                body.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - body.length()));
            }
            reader.close();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(body.toString().getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } finally {
            connection.disconnect();
        }
    }
}
