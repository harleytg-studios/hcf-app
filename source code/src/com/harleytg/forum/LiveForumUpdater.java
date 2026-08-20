package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class LiveForumUpdater {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final long FIRST_POLL_MS = 250;
    private static final long IDLE_ROUTE_POLL_MS = 5000;
    private static final int MAX_BODY_CHARS = 180000;
    private static final int READ_TIMEOUT_MS = 4500;
    private final Context app;
    private String baselineFingerprint;
    private String baselineKey;
    private int failures;
    private boolean inFlight;
    private final Listener listener;
    private final SharedPreferences prefs;
    private boolean running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, CacheEntry> cache = new HashMap();
    private String lastState = "";
    private final Runnable tick = new Runnable() { // from class: com.harleytg.forum.dev.LiveForumUpdater$$ExternalSyntheticLambda1
        @Override // java.lang.Runnable
        public final void run() {
            LiveForumUpdater.this.poll();
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
        state("SYNCING");
        schedule(FIRST_POLL_MS);
    }

    void stop() {
        this.running = false;
        this.main.removeCallbacks(this.tick);
        state("PAUSED");
    }

    void destroy() {
        stop();
    }

    void reset() {
        this.baselineKey = null;
        this.baselineFingerprint = null;
    }

    void poke() {
        if (this.running) {
            schedule(0L);
        }
    }

    void noteUserInteraction() {
        RuntimeState.noteUserInteraction();
        if (this.running) {
            schedule(Math.min(FIRST_POLL_MS, PerformanceProfile.livePollInterval(this.app, this.prefs)));
        }
    }

    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) {
            return;
        }
        this.baselineKey = str;
        this.baselineFingerprint = str2;
    }

    private void schedule(long j) {
        if (this.running) {
            long max = Math.max(0L, j);
            RuntimeDiagnostics.livePoll(max);
            this.main.removeCallbacks(this.tick);
            this.main.postDelayed(this.tick, max);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void poll() {
        if (this.running) {
            if (this.inFlight) {
                schedule(FIRST_POLL_MS);
                return;
            }
            if (!RuntimeState.networkAvailable(this.app)) {
                state("OFFLINE");
                schedule(IDLE_ROUTE_POLL_MS);
                return;
            }
            try {
                Uri parse = Uri.parse(this.listener.currentUrl());
                if (!ForumUrlRouter.isForumUrl(parse)) {
                    state("PAUSED");
                    schedule(IDLE_ROUTE_POLL_MS);
                    return;
                }
                final String endpointFor = endpointFor(parse);
                final String pageKey = pageKey(parse, endpointFor);
                if (pageKey == null || endpointFor == null) {
                    state("LIVE");
                    schedule(IDLE_ROUTE_POLL_MS);
                } else {
                    this.inFlight = true;
                    AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.LiveForumUpdater$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            LiveForumUpdater.this.m20lambda$poll$1$comharleytgforumdevLiveForumUpdater(endpointFor, pageKey);
                        }
                    });
                }
            } catch (Throwable unused) {
                schedule(IDLE_ROUTE_POLL_MS);
            }
        }
    }

    /* renamed from: lambda$poll$1$com-harleytg-forum-dev-LiveForumUpdater, reason: not valid java name */
    /* synthetic */ void m20lambda$poll$1$comharleytgforumdevLiveForumUpdater(String str, final String str2) {
        final String str3;
        try {
            str3 = fetchFingerprint(str);
        } catch (Throwable th) {
            if (this.failures == 0 || this.failures == 2) {
                AppLogger.warn(this.app, "live_update_poll", th.getClass().getSimpleName());
            }
            str3 = null;
        }
        this.main.post(new Runnable() { // from class: com.harleytg.forum.dev.LiveForumUpdater$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                LiveForumUpdater.this.m19lambda$poll$0$comharleytgforumdevLiveForumUpdater(str2, str3);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* renamed from: handleResult, reason: merged with bridge method [inline-methods] */
    public void m19lambda$poll$0$comharleytgforumdevLiveForumUpdater(String str, String str2) {
        String str3;
        this.inFlight = false;
        if (this.running) {
            if (str2 == null || str2.isEmpty()) {
                this.failures = Math.min(this.failures + 1, 5);
                state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
                schedule(Math.min(15000L, 2000 << Math.min(Math.max(0, this.failures - 1), 3)));
                return;
            }
            this.failures = 0;
            state("LIVE");
            if (!str.equals(this.baselineKey) || (str3 = this.baselineFingerprint) == null) {
                this.baselineKey = str;
                this.baselineFingerprint = str2;
            } else if (!str2.equals(str3)) {
                this.listener.onChangeCandidate(str, str2);
            }
            schedule(PerformanceProfile.livePollInterval(this.app, this.prefs));
        }
    }

    private void state(String str) {
        if (str.equals(this.lastState)) {
            return;
        }
        this.lastState = str;
        this.listener.onStateChanged(str);
    }

    private String endpointFor(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        String lowerCase = path.toLowerCase(Locale.US);
        String str = "https://" + host;
        if (lowerCase.startsWith("/d/")) {
            String substring = path.substring(3);
            int indexOf = substring.indexOf(47);
            if (indexOf >= 0) {
                substring = substring.substring(0, indexOf);
            }
            int indexOf2 = substring.indexOf(45);
            if (indexOf2 >= 0) {
                substring = substring.substring(0, indexOf2);
            }
            if (substring.matches("[0-9]+")) {
                return str + "/api/discussions/" + substring + "?fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
            }
        }
        if ("/".equals(lowerCase) || lowerCase.startsWith("/all") || lowerCase.startsWith("/following") || lowerCase.startsWith("/tags") || lowerCase.startsWith("/t/")) {
            return str + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1&fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
        }
        if (!lowerCase.startsWith("/notifications")) {
            return null;
        }
        return str + "/api/notifications?page%5Blimit%5D=1";
    }

    private String pageKey(Uri uri, String str) {
        if (str == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost() == null ? "" : uri.getHost());
        sb.append("|");
        sb.append(path);
        sb.append("|");
        sb.append(str);
        return sb.toString();
    }

    private String fetchFingerprint(String str) throws Exception {
        CacheEntry cacheEntry = this.cache.get(str);
        URL url = new URL(str);
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        try {
            httpURLConnection.setConnectTimeout(4500);
            httpURLConnection.setReadTimeout(4500);
            httpURLConnection.setUseCaches(true);
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            httpURLConnection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 LiveUpdate");
            if (cacheEntry != null) {
                if (!cacheEntry.etag.isEmpty()) {
                    httpURLConnection.setRequestProperty("If-None-Match", cacheEntry.etag);
                }
                if (cacheEntry.lastModified > 0) {
                    httpURLConnection.setIfModifiedSince(cacheEntry.lastModified);
                }
            }
            String cookie = CookieManager.getInstance().getCookie(url.getProtocol() + "://" + url.getHost() + "/");
            if (cookie != null && !cookie.trim().isEmpty()) {
                httpURLConnection.setRequestProperty("Cookie", cookie);
            }
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == 304 && cacheEntry != null) {
                return cacheEntry.signature;
            }
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }
            String compactSignature = compactSignature(readLimited(httpURLConnection.getInputStream()));
            if (compactSignature != null && !compactSignature.isEmpty()) {
                CacheEntry cacheEntry2 = new CacheEntry();
                String headerField = httpURLConnection.getHeaderField("ETag");
                if (headerField == null) {
                    headerField = "";
                }
                cacheEntry2.etag = headerField;
                cacheEntry2.lastModified = httpURLConnection.getLastModified();
                cacheEntry2.signature = compactSignature;
                this.cache.put(str, cacheEntry2);
                return compactSignature;
            }
            return null;
        } finally {
            httpURLConnection.disconnect();
        }
    }

    private String compactSignature(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        try {
            Object opt = new JSONObject(str).opt("data");
            StringBuilder sb = new StringBuilder(160);
            if (opt instanceof JSONObject) {
                appendResourceSignature(sb, (JSONObject) opt);
            } else if (opt instanceof JSONArray) {
                JSONArray jSONArray = (JSONArray) opt;
                for (int i = 0; i < jSONArray.length() && i < 2; i++) {
                    JSONObject optJSONObject = jSONArray.optJSONObject(i);
                    if (optJSONObject != null) {
                        appendResourceSignature(sb, optJSONObject);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Throwable unused) {
        }
        return "fallback|" + str.length() + "|" + Integer.toHexString(str.substring(0, Math.min(str.length(), 32768)).hashCode());
    }

    private void appendResourceSignature(StringBuilder sb, JSONObject jSONObject) {
        sb.append(jSONObject.optString("type", ""));
        sb.append(':');
        sb.append(jSONObject.optString("id", ""));
        JSONObject optJSONObject = jSONObject.optJSONObject("attributes");
        if (optJSONObject != null) {
            appendAttr(sb, optJSONObject, "lastPostNumber");
            appendAttr(sb, optJSONObject, "lastPostedAt");
            appendAttr(sb, optJSONObject, "commentCount");
            appendAttr(sb, optJSONObject, "newNotificationCount");
            appendAttr(sb, optJSONObject, "unreadNotificationCount");
            appendAttr(sb, optJSONObject, "time");
            appendAttr(sb, optJSONObject, "createdAt");
            appendAttr(sb, optJSONObject, "isRead");
            appendAttr(sb, optJSONObject, "type");
        }
        sb.append('|');
    }

    private void appendAttr(StringBuilder sb, JSONObject jSONObject, String str) {
        Object opt;
        if (!jSONObject.has(str) || (opt = jSONObject.opt(str)) == null || opt == JSONObject.NULL) {
            return;
        }
        sb.append(';');
        sb.append(str);
        sb.append('=');
        sb.append(String.valueOf(opt));
    }

    private String readLimited(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] cArr = new char[4096];
        while (true) {
            int read = bufferedReader.read(cArr);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(cArr, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        bufferedReader.close();
        return sb.toString();
    }

    private static final class CacheEntry {
        String etag;
        long lastModified;
        String signature;

        private CacheEntry() {
            this.etag = "";
            this.signature = "";
        }
    }
}
