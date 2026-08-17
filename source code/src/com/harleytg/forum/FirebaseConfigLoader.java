package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FirebaseConfigLoader {
    interface Callback {
        void onResult(Config config, String message);
    }

    static final class Config {
        final String apiKey;
        final String authDomain;
        final String projectId;
        final String storageBucket;
        final String messagingSenderId;
        final String appId;
        final String measurementId;
        final String source;

        Config(String apiKey, String authDomain, String projectId, String storageBucket,
               String messagingSenderId, String appId, String measurementId, String source) {
            this.apiKey = apiKey;
            this.authDomain = authDomain;
            this.projectId = projectId;
            this.storageBucket = storageBucket;
            this.messagingSenderId = messagingSenderId;
            this.appId = appId;
            this.measurementId = measurementId;
            this.source = source;
        }

        boolean isValid() {
            return notBlank(apiKey) && notBlank(projectId) && notBlank(messagingSenderId) && notBlank(appId);
        }

        String safeSummary() {
            return isValid() ? "Loaded • project " + projectId + " • " + source : "Configuration unavailable";
        }
    }

    static Config load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String cached = prefs.getString(AppPrefs.FIREBASE_CONFIG_CACHE, "");
        String cachedSource = prefs.getString(AppPrefs.FIREBASE_CONFIG_SOURCE, "Cached HTTPS config");
        Config parsed = parse(cached, cachedSource);
        if (parsed != null && parsed.isValid()) return parsed;

        try {
            InputStream in = context.getAssets().open("firebase-config.js");
            String raw = readAll(in);
            Config bundled = parse(raw, "Bundled firebase-config.js");
            if (bundled != null && bundled.isValid()) return bundled;
        } catch (Throwable ignored) {}
        return null;
    }

    static void refresh(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        final String configuredUrl = prefs.getString(AppPrefs.FIREBASE_CONFIG_URL, "").trim();
        if (configuredUrl.isEmpty()) {
            Config bundled = load(app);
            callback.onResult(bundled, bundled == null ? "Bundled Firebase config could not be read." : "Using bundled Firebase config.");
            return;
        }
        if (!configuredUrl.startsWith("https://")) {
            callback.onResult(load(app), "Firebase config URL must use HTTPS.");
            return;
        }

        new Thread(() -> {
            Config result = null;
            String message;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(configuredUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " FirebaseConfig");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                String raw = readAll(connection.getInputStream());
                Config remote = parse(raw, "HTTPS config");
                if (remote == null || !remote.isValid()) throw new IllegalStateException("Invalid Firebase config");
                prefs.edit()
                        .putString(AppPrefs.FIREBASE_CONFIG_CACHE, raw)
                        .putString(AppPrefs.FIREBASE_CONFIG_SOURCE, "HTTPS config")
                        .apply();
                result = remote;
                message = "Firebase config refreshed from HTTPS.";
            } catch (Throwable t) {
                result = load(app);
                message = "Remote refresh failed; kept " + (result == null ? "no config" : result.source) + ".";
                AppLogger.error(app, "firebase_config_refresh", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
            final Config finalResult = result;
            final String finalMessage = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalResult, finalMessage));
        }, "hcf-firebase-config").start();
    }

    static void clearRemoteCache(Context context) {
        context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE).edit()
                .remove(AppPrefs.FIREBASE_CONFIG_CACHE)
                .remove(AppPrefs.FIREBASE_CONFIG_SOURCE)
                .apply();
    }

    private static Config parse(String raw, String source) {
        if (raw == null || raw.trim().isEmpty()) return null;
        Config c = new Config(
                value(raw, "apiKey"), value(raw, "authDomain"), value(raw, "projectId"),
                value(raw, "storageBucket"), value(raw, "messagingSenderId"), value(raw, "appId"),
                value(raw, "measurementId"), source);
        return c.isValid() ? c : null;
    }

    private static String value(String raw, String key) {
        Pattern p = Pattern.compile("(?:\\\"?" + Pattern.quote(key) + "\\\"?)\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']");
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line).append('\n');
        reader.close();
        return out.toString();
    }

    private FirebaseConfigLoader() {}
}
