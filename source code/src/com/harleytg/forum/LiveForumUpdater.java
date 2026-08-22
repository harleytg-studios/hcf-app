package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.CookieManager;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class LiveForumUpdater {
    private static final long AUTH_CHECK_MS = 15000L;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final long FIRST_POLL_MS = 250L;
    private static final long IDLE_ROUTE_POLL_MS = 5000L;
    private static final int MAX_BODY_CHARS = 180000;
    private static final int MAX_WS_PAYLOAD_BYTES = 524288;
    private static final int PUSH_READ_TIMEOUT_MS = 60000;
    private static final long RECONNECT_MAX_MS = 30000L;
    private static final long RECONNECT_MIN_MS = 1000L;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Context app;
    private String baselineFingerprint;
    private String baselineKey;
    private final Map<String, CacheEntry> cache = new HashMap();
    private int failures;
    private boolean forceQueuedRefresh;
    private boolean inFlight;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private boolean queuedRefresh;
    private int reconnectAttempts;
    private volatile boolean running;
    private volatile PushConnection socket;
    private volatile boolean socketConnected;
    private boolean socketConnecting;
    private volatile int socketGeneration;
    private String socketIdentity = "";
    private String lastState = "";
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.poll();
        }
    };
    private final Runnable reconnect = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.connectSocket();
        }
    };
    private final Runnable authCheck = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.checkRealtimeIdentity();
        }
    };
    private final Runnable pushedRefresh = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.refreshCurrentRoute(false, true);
        }
    };

    interface Listener {
        String currentUrl();

        void onChangeCandidate(String str, String str2);

        void onStateChanged(String str);
    }

    LiveForumUpdater(Context context, Listener listener) {
        Context applicationContext = context.getApplicationContext();
        this.app = applicationContext;
        this.listener = listener;
        this.prefs = applicationContext.getSharedPreferences("hcf_app", 0);
    }

    void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.failures = 0;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        state("SYNCING");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
        scheduleAuthCheck();
    }

    void stop() {
        this.running = false;
        this.socketGeneration++;
        this.main.removeCallbacks(this.tick);
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.authCheck);
        this.main.removeCallbacks(this.pushedRefresh);
        this.socketConnecting = false;
        this.socketConnected = false;
        closeSocket();
        state("PAUSED");
    }

    void destroy() {
        stop();
    }

    void reset() {
        this.baselineKey = null;
        this.baselineFingerprint = null;
        if (this.running) {
            poke();
        }
    }

    void poke() {
        if (!this.running) {
            return;
        }
        if (!realtimeIdentity().equals(this.socketIdentity)) {
            restartSocket("auth-or-host-changed");
        } else if (!this.socketConnected) {
            scheduleReconnect(0L);
        }
        if (this.socketConnected) {
            schedulePushedRefresh(0L);
        } else {
            scheduleFallback(0L);
        }
    }

    void noteUserInteraction() {
        RuntimeState.noteUserInteraction();
        if (!this.running || this.socketConnected) {
            return;
        }
        scheduleFallback(Math.min(FIRST_POLL_MS, PerformanceProfile.livePollInterval(this.app, this.prefs)));
    }

    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) return;
        this.baselineKey = str;
        this.baselineFingerprint = str2;
        saveBaseline(str, str2);
    }

    private void scheduleAuthCheck() {
        if (!this.running) {
            return;
        }
        this.main.removeCallbacks(this.authCheck);
        this.main.postDelayed(this.authCheck, AUTH_CHECK_MS);
    }

    private void checkRealtimeIdentity() {
        if (!this.running) {
            return;
        }
        String realtimeIdentity = realtimeIdentity();
        if (!realtimeIdentity.equals(this.socketIdentity)) {
            restartSocket("auth-or-cookie-changed");
        } else if (!this.socketConnected && !this.socketConnecting && RuntimeState.networkAvailable(this.app)) {
            scheduleReconnect(0L);
        }
        scheduleAuthCheck();
    }

    private String realtimeIdentity() {
        String str = "";
        try {
            Uri parse = Uri.parse(this.listener.currentUrl());
            if (ForumUrlRouter.isForumUrl(parse) && parse.getHost() != null) {
                str = parse.getHost().toLowerCase(Locale.US);
            }
        } catch (Throwable unused) {
        }
        String string = this.prefs.getString("session_user_id", "");
        if (string == null) {
            string = "";
        }
        String cookie = cookieForHost(str);
        return str + "|" + string.trim() + "|" + Integer.toHexString(cookie.hashCode());
    }

    private String cookieForHost(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        try {
            String cookie = CookieManager.getInstance().getCookie("https://" + str + "/");
            return cookie == null ? "" : cookie;
        } catch (Throwable unused) {
            return "";
        }
    }

    private void restartSocket(String str) {
        this.socketGeneration++;
        this.socketConnected = false;
        this.socketConnecting = false;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        closeSocket();
        AppLogger.info(this.app, "live_update_socket", "restart | " + str);
        state(RuntimeState.networkAvailable(this.app) ? "SYNCING" : "OFFLINE");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
    }

    private void closeSocket() {
        PushConnection pushConnection = this.socket;
        this.socket = null;
        if (pushConnection != null) {
            pushConnection.closeQuietly();
        }
    }

    private void scheduleReconnect(long j) {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        long max = Math.max(0L, j);
        this.main.removeCallbacks(this.reconnect);
        this.main.postDelayed(this.reconnect, max);
    }

    private long reconnectDelay() {
        int min = Math.min(this.reconnectAttempts, 5);
        long min2 = Math.min(RECONNECT_MAX_MS, RECONNECT_MIN_MS << min);
        return Math.min(RECONNECT_MAX_MS, min2 + RANDOM.nextInt(350));
    }

    private void connectSocket() {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final Uri parse;
        try {
            parse = Uri.parse(this.listener.currentUrl());
        } catch (Throwable unused) {
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        if (!ForumUrlRouter.isForumUrl(parse) || parse.getHost() == null) {
            state("PAUSED");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final String lowerCase = parse.getHost().toLowerCase(Locale.US);
        final int i = this.socketGeneration;
        this.socketIdentity = realtimeIdentity();
        this.socketConnecting = true;
        state("SYNCING");
        AppExecutors.network().execute(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.runSocketSession(i, lowerCase);
            }
        });
    }

    private void runSocketSession(final int i, String str) {
        PushConnection pushConnection = null;
        String str2 = "closed";
        try {
            RealtimeConfig realtimeConfig = fetchRealtimeConfig(str);
            if (!this.running || i != this.socketGeneration) {
                return;
            }
            if (realtimeConfig.key.isEmpty()) {
                throw new IOException("Flarum Pusher extension is not configured");
            }
            pushConnection = new PushConnection(realtimeConfig, str);
            pushConnection.open();
            if (!this.running || i != this.socketGeneration) {
                pushConnection.closeQuietly();
                return;
            }
            this.socket = pushConnection;
            String socketId = awaitConnectionEstablished(pushConnection);
            subscribe(pushConnection, "public", "");
            if (!realtimeConfig.userId.isEmpty() && !realtimeConfig.csrfToken.isEmpty()) {
                try {
                    String auth = authorizePrivateChannel(realtimeConfig, socketId);
                    if (!auth.isEmpty()) {
                        subscribe(pushConnection, "private-user" + realtimeConfig.userId, auth);
                    }
                } catch (Throwable th) {
                    AppLogger.warn(this.app, "live_update_socket_auth", th.getClass().getSimpleName());
                }
            }
            final PushConnection connected = pushConnection;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketConnected(i, connected);
                }
            });
            int heartbeatTimeouts = 0;
            while (this.running && i == this.socketGeneration) {
                try {
                    String text = pushConnection.readText();
                    heartbeatTimeouts = 0;
                    handlePusherMessage(pushConnection, text);
                } catch (SocketTimeoutException unused) {
                    heartbeatTimeouts++;
                    if (heartbeatTimeouts > 1) {
                        throw new IOException("Pusher heartbeat timeout");
                    }
                    pushConnection.sendPusherEvent("pusher:ping", new JSONObject());
                }
            }
        } catch (Throwable th2) {
            str2 = th2.getClass().getSimpleName() + ": " + String.valueOf(th2.getMessage());
        } finally {
            if (pushConnection != null) {
                pushConnection.closeQuietly();
            }
            final String reason = str2;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketDisconnected(i, reason);
                }
            });
        }
    }

    private String awaitConnectionEstablished(PushConnection pushConnection) throws Exception {
        int i = 0;
        while (i < 8) {
            i++;
            String readText = pushConnection.readText();
            JSONObject message = new JSONObject(readText);
            String event = message.optString("event", "");
            if ("pusher:connection_established".equals(event)) {
                JSONObject data = jsonData(message);
                String socketId = data.optString("socket_id", "");
                int activityTimeout = data.optInt("activity_timeout", 60);
                pushConnection.setReadTimeout(Math.max(20000, Math.min(PUSH_READ_TIMEOUT_MS, activityTimeout * 1000)));
                if (socketId.isEmpty()) {
                    throw new IOException("Pusher socket id missing");
                }
                return socketId;
            }
            if ("pusher:error".equals(event)) {
                throw new IOException("Pusher connection error");
            }
        }
        throw new IOException("Pusher connection handshake timed out");
    }

    private void subscribe(PushConnection pushConnection, String channel, String auth) throws Exception {
        JSONObject data = new JSONObject();
        data.put("channel", channel);
        if (auth != null && !auth.isEmpty()) {
            data.put("auth", auth);
        }
        pushConnection.sendPusherEvent("pusher:subscribe", data);
    }

    private String authorizePrivateChannel(RealtimeConfig realtimeConfig, String socketId) throws Exception {
        String channel = "private-user" + realtimeConfig.userId;
        String body = "socket_id=" + URLEncoder.encode(socketId, "UTF-8") + "&channel_name=" + URLEncoder.encode(channel, "UTF-8");
        byte[] bytes = body.getBytes("UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + realtimeConfig.forumHost + "/api/pusher/auth").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("X-CSRF-Token", realtimeConfig.csrfToken);
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " Realtime");
            String cookie = cookieForHost(realtimeConfig.forumHost);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Pusher auth HTTP " + code);
            }
            return new JSONObject(readLimited(connection.getInputStream())).optString("auth", "");
        } finally {
            connection.disconnect();
        }
    }

    private void handleSocketConnected(int generation, PushConnection pushConnection) {
        if (!this.running || generation != this.socketGeneration || this.socket != pushConnection) {
            pushConnection.closeQuietly();
            return;
        }
        this.socketConnecting = false;
        this.socketConnected = true;
        this.reconnectAttempts = 0;
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.tick);
        state("LIVE");
        AppLogger.info(this.app, "live_update_socket", "connected • push primary");
        schedulePushedRefresh(0L);
    }

    private void handleSocketDisconnected(int generation, String reason) {
        if (generation != this.socketGeneration) {
            return;
        }
        this.socket = null;
        this.socketConnecting = false;
        this.socketConnected = false;
        if (!this.running) {
            return;
        }
        this.reconnectAttempts = Math.min(this.reconnectAttempts + 1, 6);
        state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
        if (this.reconnectAttempts == 1 || this.reconnectAttempts == 3) {
            AppLogger.warn(this.app, "live_update_socket", "fallback polling | " + reason);
        }
        scheduleFallback(0L);
        scheduleReconnect(reconnectDelay());
    }

    private void handlePusherMessage(PushConnection pushConnection, String text) throws Exception {
        JSONObject message = new JSONObject(text);
        String event = message.optString("event", "");
        if ("pusher:ping".equals(event)) {
            pushConnection.sendPusherEvent("pusher:pong", new JSONObject());
            return;
        }
        if ("pusher:error".equals(event)) {
            throw new IOException("Pusher protocol error");
        }
        if (!"newPost".equals(event) && !"notification".equals(event)) {
            return;
        }
        final String pushEvent = event;
        final JSONObject data = jsonData(message);
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handlePushEvent(pushEvent, data);
            }
        });
    }

    private JSONObject jsonData(JSONObject message) {
        Object data = message.opt("data");
        if (data instanceof JSONObject) {
            return (JSONObject) data;
        }
        if (data instanceof String) {
            try {
                return new JSONObject((String) data);
            } catch (Throwable unused) {
            }
        }
        return new JSONObject();
    }

    private void handlePushEvent(String event, JSONObject data) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        if ("notification".equals(event)) {
            InstantNotificationService.requestImmediateSync(this.app);
        }
        if (eventAffectsCurrentRoute(event, data)) {
            schedulePushedRefresh(60L);
        }
    }

    private boolean eventAffectsCurrentRoute(String event, JSONObject data) {
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                return false;
            }
            String path = uri.getPath() == null ? "/" : uri.getPath();
            String lower = path.toLowerCase(Locale.US);
            if (lower.startsWith("/notifications")) {
                return "notification".equals(event);
            }
            if (!"newPost".equals(event)) {
                return false;
            }
            if (lower.startsWith("/d/")) {
                String discussionId = discussionIdFromPath(path);
                String eventDiscussionId = data.optString("discussionId", "");
                return discussionId.isEmpty() || eventDiscussionId.isEmpty() || discussionId.equals(eventDiscussionId);
            }
            return "/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/");
        } catch (Throwable unused) {
            return false;
        }
    }

    private String discussionIdFromPath(String path) {
        if (path == null || path.length() < 4) {
            return "";
        }
        String value = path.substring(3);
        int slash = value.indexOf(47);
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int dash = value.indexOf(45);
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        return value.matches("[0-9]+") ? value : "";
    }

    private void schedulePushedRefresh(long delayMs) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        this.main.removeCallbacks(this.pushedRefresh);
        this.main.postDelayed(this.pushedRefresh, Math.max(0L, delayMs));
    }

    private RealtimeConfig fetchRealtimeConfig(String host) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + host + "/api").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " RealtimeBootstrap");
            String cookie = cookieForHost(host);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Flarum realtime bootstrap HTTP " + code);
            }
            JSONObject root = new JSONObject(readLimited(connection.getInputStream()));
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null ? null : data.optJSONObject("attributes");
            RealtimeConfig config = new RealtimeConfig();
            config.forumHost = host;
            config.key = attributes == null ? "" : attributes.optString("pusherKey", "").trim();
            config.cluster = attributes == null ? "" : attributes.optString("pusherCluster", "").trim().toLowerCase(Locale.US);
            if (config.cluster.isEmpty()) {
                config.cluster = "mt1";
            }
            if (!config.cluster.matches("[a-z0-9-]+")) {
                throw new IOException("Invalid Pusher cluster");
            }
            config.csrfToken = header(connection, "X-CSRF-Token");
            String userId = this.prefs.getString("session_user_id", "");
            config.userId = userId == null ? "" : userId.trim();
            return config;
        } finally {
            connection.disconnect();
        }
    }

    private String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value.trim();
    }

    private void scheduleFallback(long delayMs) {
        if (!this.running || this.socketConnected) {
            return;
        }
        long delay = Math.max(0L, delayMs);
        RuntimeDiagnostics.livePoll(delay);
        this.main.removeCallbacks(this.tick);
        this.main.postDelayed(this.tick, delay);
    }

    public void poll() {
        refreshCurrentRoute(true, false);
    }

    private void refreshCurrentRoute(final boolean fallbackPoll, final boolean forceRefresh) {
        if (!this.running || (fallbackPoll && this.socketConnected)) {
            return;
        }
        if (this.inFlight) {
            this.queuedRefresh = true;
            this.forceQueuedRefresh |= forceRefresh;
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
            return;
        }
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                state("PAUSED");
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            final String endpoint = endpointFor(uri);
            final String key = pageKey(uri, endpoint);
            if (key == null || endpoint == null) {
                if (this.socketConnected) {
                    state("LIVE");
                }
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            this.inFlight = true;
            AppExecutors.network().execute(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.fetchRouteFingerprint(endpoint, key, fallbackPoll, forceRefresh);
                }
            });
        } catch (Throwable unused) {
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
        }
    }

    private void fetchRouteFingerprint(String endpoint, final String key, final boolean fallbackPoll, boolean forceRefresh) {
        final String fingerprint;
        try {
            fingerprint = fetchFingerprint(endpoint, forceRefresh);
        } catch (Throwable th) {
            if (this.failures == 0 || this.failures == 2) {
                AppLogger.warn(this.app, fallbackPoll ? "live_update_poll" : "live_update_push_refresh", th.getClass().getSimpleName());
            }
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleFingerprintResult(key, null, fallbackPoll);
                }
            });
            return;
        }
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handleFingerprintResult(key, fingerprint, fallbackPoll);
            }
        });
    }

    private void handleFingerprintResult(String key, String fingerprint, boolean fallbackPoll) {
        String previous;
        this.inFlight = false;
        if (!this.running) {
            return;
        }
        if (fingerprint == null || fingerprint.isEmpty()) {
            this.failures = Math.min(this.failures + 1, 5);
            if (!this.socketConnected) {
                state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
                if (fallbackPoll) {
                    scheduleFallback(Math.min(15000L, 2000L << Math.min(Math.max(0, this.failures - 1), 3)));
                }
            }
        } else {
            this.failures = 0;
            state("LIVE");
            if (!key.equals(this.baselineKey) || (previous = this.baselineFingerprint) == null) {
                previous = loadBaseline(key);
                this.baselineKey = key;
                this.baselineFingerprint = previous == null ? fingerprint : previous;
                if (previous == null) saveBaseline(key, fingerprint);
            }
            previous = this.baselineFingerprint;
            if (previous != null && !fingerprint.equals(previous)) {
                this.listener.onChangeCandidate(key, fingerprint);
            }
            if (fallbackPoll && !this.socketConnected) {
                scheduleFallback(PerformanceProfile.livePollInterval(this.app, this.prefs));
            }
        }
        if (this.queuedRefresh) {
            boolean force = this.forceQueuedRefresh;
            this.queuedRefresh = false;
            this.forceQueuedRefresh = false;
            refreshCurrentRoute(!this.socketConnected, force);
        }
    }

    private void state(String state) {
        if (state.equals(this.lastState)) return;
        String previous = this.lastState == null || this.lastState.isEmpty() ? "NONE" : this.lastState;
        this.lastState = state;
        AppLogger.info(this.app, "live_update_state", previous + " -> " + state);
        this.listener.onStateChanged(state);
    }

    private String baselinePrefKey(String key) {
        if (key == null) return "";
        return "live_fp_" + Integer.toHexString(key.hashCode());
    }

    private String loadBaseline(String key) {
        try {
            String value = this.prefs.getString(baselinePrefKey(key), null);
            return value == null || value.isEmpty() ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveBaseline(String key, String fingerprint) {
        if (key == null || fingerprint == null || fingerprint.isEmpty()) return;
        try {
            this.prefs.edit().putString(baselinePrefKey(key), fingerprint).apply();
        } catch (Throwable ignored) {
        }
    }

    private String endpointFor(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        String lower = path.toLowerCase(Locale.US);
        String root = "https://" + host;
        if (lower.startsWith("/d/")) {
            String discussionId = discussionIdFromPath(path);
            if (!discussionId.isEmpty()) {
                return root + "/api/discussions/" + discussionId + "?fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
            }
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return root + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1&fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
        }
        if (!lower.startsWith("/notifications")) {
            return null;
        }
        return root + "/api/notifications?page%5Blimit%5D=1";
    }

    private String pageKey(Uri uri, String endpoint) {
        if (endpoint == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost() == null ? "" : uri.getHost());
        sb.append("|");
        sb.append(path);
        sb.append("|");
        sb.append(endpoint);
        return sb.toString();
    }

    private String fetchFingerprint(String endpoint, boolean forceRefresh) throws Exception {
        CacheEntry cacheEntry = this.cache.get(endpoint);
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " LiveUpdate");
            if (!forceRefresh && cacheEntry != null) {
                if (!cacheEntry.etag.isEmpty()) {
                    connection.setRequestProperty("If-None-Match", cacheEntry.etag);
                }
                if (cacheEntry.lastModified > 0) {
                    connection.setIfModifiedSince(cacheEntry.lastModified);
                }
            }
            String cookie = CookieManager.getInstance().getCookie(url.getProtocol() + "://" + url.getHost() + "/");
            if (cookie != null && !cookie.trim().isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code == 304 && cacheEntry != null) {
                return cacheEntry.signature;
            }
            if (code < 200 || code >= 300) {
                return null;
            }
            String signature = compactSignature(readLimited(connection.getInputStream()));
            if (signature != null && !signature.isEmpty()) {
                CacheEntry next = new CacheEntry();
                String etag = connection.getHeaderField("ETag");
                next.etag = etag == null ? "" : etag;
                next.lastModified = connection.getLastModified();
                next.signature = signature;
                this.cache.put(endpoint, next);
                return signature;
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private String compactSignature(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            Object data = new JSONObject(body).opt("data");
            StringBuilder sb = new StringBuilder(160);
            if (data instanceof JSONObject) {
                appendResourceSignature(sb, (JSONObject) data);
            } else if (data instanceof JSONArray) {
                JSONArray array = (JSONArray) data;
                for (int i = 0; i < array.length() && i < 2; i++) {
                    JSONObject resource = array.optJSONObject(i);
                    if (resource != null) {
                        appendResourceSignature(sb, resource);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Throwable unused) {
        }
        return "fallback|" + body.length() + "|" + Integer.toHexString(body.substring(0, Math.min(body.length(), 32768)).hashCode());
    }

    private void appendResourceSignature(StringBuilder sb, JSONObject resource) {
        sb.append(resource.optString("type", ""));
        sb.append(':');
        sb.append(resource.optString("id", ""));
        JSONObject attributes = resource.optJSONObject("attributes");
        if (attributes != null) {
            appendAttr(sb, attributes, "lastPostNumber");
            appendAttr(sb, attributes, "lastPostedAt");
            appendAttr(sb, attributes, "commentCount");
            appendAttr(sb, attributes, "newNotificationCount");
            appendAttr(sb, attributes, "unreadNotificationCount");
            appendAttr(sb, attributes, "time");
            appendAttr(sb, attributes, "createdAt");
            appendAttr(sb, attributes, "isRead");
            appendAttr(sb, attributes, "type");
        }
        sb.append('|');
    }

    private void appendAttr(StringBuilder sb, JSONObject attributes, String name) {
        Object value;
        if (!attributes.has(name) || (value = attributes.opt(name)) == null || value == JSONObject.NULL) {
            return;
        }
        sb.append(';');
        sb.append(name);
        sb.append('=');
        sb.append(String.valueOf(value));
    }

    private String readLimited(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        while (true) {
            int read = reader.read(buffer);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        reader.close();
        return sb.toString();
    }

    private static final class RealtimeConfig {
        String cluster = "mt1";
        String csrfToken = "";
        String forumHost = "";
        String key = "";
        String userId = "";
    }

    private static final class PushConnection {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final RealtimeConfig config;
        private final String originHost;
        private InputStream input;
        private OutputStream output;
        private SSLSocket socket;

        PushConnection(RealtimeConfig realtimeConfig, String originHost) {
            this.config = realtimeConfig;
            this.originHost = originHost;
        }

        void open() throws Exception {
            String wsHost = "ws-" + this.config.cluster + ".pusher.com";
            String path = "/app/" + this.config.key + "?protocol=7&client=android-hcf&version=1.0&flash=false";
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(wsHost, 443), CONNECT_TIMEOUT_MS);
            SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(rawSocket, wsHost, 443, true);
            this.socket = sslSocket;
            sslSocket.setSoTimeout(PUSH_READ_TIMEOUT_MS);
            this.socket.startHandshake();
            this.input = this.socket.getInputStream();
            this.output = this.socket.getOutputStream();
            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String webSocketKey = Base64.encodeToString(nonce, Base64.NO_WRAP);
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(wsHost).append("\r\n");
            request.append("Upgrade: websocket\r\n");
            request.append("Connection: Upgrade\r\n");
            request.append("Sec-WebSocket-Key: ").append(webSocketKey).append("\r\n");
            request.append("Sec-WebSocket-Version: 13\r\n");
            request.append("Origin: https://").append(this.originHost).append("\r\n");
            request.append("User-Agent: HarleysClanForumApp/1.0 Realtime\r\n\r\n");
            this.output.write(request.toString().getBytes("UTF-8"));
            this.output.flush();
            String status = readHttpLine(this.input);
            if (status == null || status.indexOf(" 101 ") < 0) {
                throw new IOException("WebSocket upgrade failed: " + status);
            }
            String accept = "";
            while (true) {
                String header = readHttpLine(this.input);
                if (header == null || header.isEmpty()) {
                    break;
                }
                int colon = header.indexOf(58);
                if (colon > 0 && "sec-websocket-accept".equals(header.substring(0, colon).trim().toLowerCase(Locale.US))) {
                    accept = header.substring(colon + 1).trim();
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String expected = Base64.encodeToString(digest.digest((webSocketKey + WS_GUID).getBytes("UTF-8")), Base64.NO_WRAP);
            if (!expected.equals(accept)) {
                throw new IOException("WebSocket accept mismatch");
            }
        }

        void setReadTimeout(int timeoutMs) throws Exception {
            SSLSocket sslSocket = this.socket;
            if (sslSocket != null) {
                sslSocket.setSoTimeout(timeoutMs);
            }
        }

        synchronized void sendPusherEvent(String event, JSONObject data) throws Exception {
            JSONObject message = new JSONObject();
            message.put("event", event);
            message.put("data", data);
            sendText(message.toString());
        }

        synchronized void sendText(String text) throws Exception {
            sendFrame(1, text.getBytes("UTF-8"));
        }

        private synchronized void sendControl(int opcode, byte[] payload) throws Exception {
            sendFrame(opcode, payload);
        }

        private void sendFrame(int opcode, byte[] payload) throws Exception {
            OutputStream outputStream = this.output;
            if (outputStream == null) {
                throw new EOFException("WebSocket closed");
            }
            if (payload == null) {
                payload = new byte[0];
            }
            if (payload.length > MAX_WS_PAYLOAD_BYTES) {
                throw new IOException("WebSocket payload too large");
            }
            outputStream.write(128 | (opcode & 15));
            int length = payload.length;
            if (length < 126) {
                outputStream.write(128 | length);
            } else if (length <= 65535) {
                outputStream.write(254);
                outputStream.write((length >>> 8) & 255);
                outputStream.write(length & 255);
            } else {
                outputStream.write(255);
                long frameLength = length;
                for (int i = 7; i >= 0; i--) {
                    outputStream.write((int) ((frameLength >>> (i * 8)) & 255));
                }
            }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            outputStream.write(mask);
            for (int i = 0; i < payload.length; i++) {
                outputStream.write(payload[i] ^ mask[i & 3]);
            }
            outputStream.flush();
        }

        String readText() throws Exception {
            ByteArrayOutputStream fragments = null;
            int fragmentedOpcode = 0;
            while (true) {
                int first = readRequired(this.input);
                int second = readRequired(this.input);
                boolean fin = (first & 128) != 0;
                int opcode = first & 15;
                boolean masked = (second & 128) != 0;
                long length = second & 127;
                if (length == 126) {
                    length = (readRequired(this.input) << 8) | readRequired(this.input);
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        length = (length << 8) | readRequired(this.input);
                    }
                }
                if (length < 0 || length > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket payload exceeds limit");
                }
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    readFully(this.input, mask);
                }
                byte[] payload = new byte[(int) length];
                readFully(this.input, payload);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i & 3]);
                    }
                }
                if (opcode == 8) {
                    throw new EOFException("WebSocket closed by peer");
                }
                if (opcode == 9) {
                    sendControl(10, payload);
                    continue;
                }
                if (opcode == 10) {
                    continue;
                }
                if (opcode == 1) {
                    fragments = new ByteArrayOutputStream(Math.max(64, payload.length));
                    fragments.write(payload);
                    fragmentedOpcode = 1;
                } else if (opcode == 0 && fragments != null && fragmentedOpcode == 1) {
                    fragments.write(payload);
                } else {
                    continue;
                }
                if (fragments.size() > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket fragmented payload exceeds limit");
                }
                if (fin) {
                    return new String(fragments.toByteArray(), "UTF-8");
                }
            }
        }

        void closeQuietly() {
            try {
                SSLSocket sslSocket = this.socket;
                if (sslSocket != null) {
                    sslSocket.close();
                }
            } catch (Throwable unused) {
            }
            this.socket = null;
            this.input = null;
            this.output = null;
        }

        private static int readRequired(InputStream inputStream) throws Exception {
            if (inputStream == null) {
                throw new EOFException("WebSocket input closed");
            }
            int value = inputStream.read();
            if (value < 0) {
                throw new EOFException("WebSocket EOF");
            }
            return value;
        }

        private static void readFully(InputStream inputStream, byte[] bytes) throws Exception {
            int offset = 0;
            while (offset < bytes.length) {
                int read = inputStream.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new EOFException("WebSocket EOF");
                }
                offset += read;
            }
        }

        private static String readHttpLine(InputStream inputStream) throws Exception {
            StringBuilder sb = new StringBuilder();
            int previous = -1;
            while (sb.length() < 8192) {
                int value = inputStream.read();
                if (value < 0) {
                    return sb.length() == 0 ? null : sb.toString();
                }
                if (previous == 13 && value == 10) {
                    sb.setLength(Math.max(0, sb.length() - 1));
                    return sb.toString();
                }
                sb.append((char) value);
                previous = value;
            }
            throw new IOException("WebSocket HTTP header line too long");
        }
    }

    private static final class CacheEntry {
        String etag = "";
        long lastModified;
        String signature = "";
    }
}
