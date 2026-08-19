package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable application/build configuration.
 *
 * Canonical domain and Firebase values live on MAIN under configs/. The Dev build
 * packages generated runtime assets and this class only reads those generated assets.
 */
final class AppConfig {
    private static volatile Context appContext;
    private static volatile Domains domains;
    private static volatile Firebase firebase;

    static void initialize(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        appContext = context.getApplicationContext();
        domains = loadDomains(appContext);
        firebase = loadFirebase(appContext);
    }

    static Domains domains() {
        Domains value = domains;
        if (value != null) return value;
        Context context = appContext;
        if (context == null) throw new IllegalStateException("AppConfig has not been initialized");
        synchronized (AppConfig.class) {
            if (domains == null) domains = loadDomains(context);
            return domains;
        }
    }

    static Firebase firebase() {
        Firebase value = firebase;
        if (value != null) return value;
        Context context = appContext;
        if (context == null) throw new IllegalStateException("AppConfig has not been initialized");
        synchronized (AppConfig.class) {
            if (firebase == null) firebase = loadFirebase(context);
            return firebase;
        }
    }

    static final class Domains {
        final String primaryHost;
        final List<String> backupHosts;
        final Set<String> trustedHosts;
        final boolean httpsOnly;
        final boolean preservePath;
        final boolean preserveQuery;
        final boolean preserveFragment;

        Domains(String primaryHost, List<String> backupHosts, boolean httpsOnly,
                boolean preservePath, boolean preserveQuery, boolean preserveFragment) {
            this.primaryHost = primaryHost;
            this.backupHosts = Collections.unmodifiableList(new ArrayList<>(backupHosts));
            LinkedHashSet<String> all = new LinkedHashSet<>();
            all.add(primaryHost);
            all.addAll(backupHosts);
            this.trustedHosts = Collections.unmodifiableSet(all);
            this.httpsOnly = httpsOnly;
            this.preservePath = preservePath;
            this.preserveQuery = preserveQuery;
            this.preserveFragment = preserveFragment;
        }

        String firstBackupOrPrimary() {
            return backupHosts.isEmpty() ? primaryHost : backupHosts.get(0);
        }
    }

    static final class Firebase {
        final String apiKey;
        final String authDomain;
        final String projectId;
        final String storageBucket;
        final String messagingSenderId;
        final String appId;
        final String measurementId;

        Firebase(Map<String, String> values) {
            apiKey = required(values, "api_key");
            authDomain = required(values, "auth_domain");
            projectId = required(values, "project_id");
            storageBucket = required(values, "storage_bucket");
            messagingSenderId = required(values, "messaging_sender_id");
            appId = required(values, "app_id");
            measurementId = required(values, "measurement_id");
        }

        boolean isValid() {
            return !apiKey.isEmpty() && !authDomain.isEmpty() && !projectId.isEmpty()
                    && !storageBucket.isEmpty() && !messagingSenderId.isEmpty()
                    && !appId.isEmpty() && !measurementId.isEmpty();
        }
    }

    private static Domains loadDomains(Context context) {
        Map<String, String> values = readIniAsset(context, "domains.runtime");
        String primary = normalizeHost(required(values, "primary.domain"));
        List<String> backups = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("backups.domain_")) {
                backups.add(normalizeHost(entry.getValue()));
            }
        }
        if (!"true".equalsIgnoreCase(required(values, "config.https_only"))) {
            throw new IllegalStateException("Runtime domain registry must require HTTPS");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (!seen.add(primary)) throw new IllegalStateException("Duplicate primary domain");
        for (String backup : backups) {
            if (!seen.add(backup)) throw new IllegalStateException("Duplicate runtime domain: " + backup);
        }
        return new Domains(primary, backups, true,
                bool(values, "config.preserve_path", true),
                bool(values, "config.preserve_query", true),
                bool(values, "config.preserve_fragment", true));
    }

    private static Firebase loadFirebase(Context context) {
        Firebase value = new Firebase(readIniAsset(context, "firebase.runtime"));
        if (!value.isValid()) throw new IllegalStateException("Generated Firebase config is incomplete");
        return value;
    }

    private static Map<String, String> readIniAsset(Context context, String assetName) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        try (InputStream in = context.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String section = "";
            String line;
            while ((line = reader.readLine()) != null) {
                String raw = line.trim();
                if (raw.isEmpty() || raw.startsWith("#")) continue;
                if (raw.startsWith("[") && raw.endsWith("]")) {
                    section = raw.substring(1, raw.length() - 1).trim().toLowerCase(Locale.US);
                    continue;
                }
                int equals = raw.indexOf('=');
                if (equals <= 0 || section.isEmpty()) {
                    throw new IllegalStateException("Invalid generated config line in " + assetName);
                }
                String key = raw.substring(0, equals).trim().toLowerCase(Locale.US);
                String value = raw.substring(equals + 1).trim();
                String namespaced = section + "." + key;
                if (values.put(namespaced, value) != null) {
                    throw new IllegalStateException("Duplicate generated config key: " + namespaced);
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read generated " + assetName, error);
        }
        return values;
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : Boolean.parseBoolean(value);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("Missing config key: " + key);
        return value.trim();
    }

    private static String normalizeHost(String raw) {
        String host = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (host.isEmpty() || host.contains("://") || host.contains("/") || host.contains("\\")
                || host.contains(" ") || host.contains("\t") || host.endsWith(".online")
                || host.startsWith(".") || host.endsWith(".") || host.contains("..")
                || !host.matches("[a-z0-9.-]+") || !host.contains(".")) {
            throw new IllegalStateException("Invalid runtime forum hostname");
        }
        return host;
    }

    private AppConfig() {}
}

/** Compatibility name retained so existing UI code does not duplicate build constants. */
final class BuildInfo {
    static final String VERSION = "1.0";
    static final String VERSION_TAG = "v1.0";
    static final int VERSION_CODE = 10000035;
    static final int INTERNAL_BUILD = 88;
    static final String CHANNEL = "Dev";
    static final String BRAND = "Harley's Studio's";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String APK_FILE_NAME = "Harley's Clan Forum [Beta].apk";
    static final String DEVELOPMENT_BUILD_LABEL = "Harley's Clan Forum v1.0 [Development Build / Beta]";
    static final String META_LINE = "1.0 • Development / Beta";
    static final String VERSION_BUILD_LINE = "1.0 • Development / Beta • Foundation Release";
    static final String VERSION_CODE_SCHEME = "major-release-v1";
    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final boolean FCM_CONFIGURED = false;
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String DEFAULT_UPDATE_CHANNEL = "dev";
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;

    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }

    private BuildInfo() {}
}

/** Compatibility facade. Runtime Firebase values now come only from generated MAIN config. */
final class FirebaseConfigLoader {
    interface Callback { void onResult(Config config, String message); }

    static final class Config {
        final String apiKey;
        final String authDomain;
        final String projectId;
        final String storageBucket;
        final String messagingSenderId;
        final String appId;
        final String measurementId;
        final String source;

        Config(AppConfig.Firebase value) {
            apiKey = value.apiKey;
            authDomain = value.authDomain;
            projectId = value.projectId;
            storageBucket = value.storageBucket;
            messagingSenderId = value.messagingSenderId;
            appId = value.appId;
            measurementId = value.measurementId;
            source = "Generated MAIN firebase.config";
        }

        boolean isValid() {
            return apiKey != null && !apiKey.isEmpty() && projectId != null && !projectId.isEmpty()
                    && messagingSenderId != null && !messagingSenderId.isEmpty() && appId != null && !appId.isEmpty();
        }

        String safeSummary() {
            return isValid() ? "Loaded • project " + projectId + " • " + source : "Configuration unavailable";
        }
    }

    static Config load(Context context) {
        try {
            if (context != null) AppConfig.initialize(context);
            return new Config(AppConfig.firebase());
        } catch (Throwable error) {
            if (context != null) AppLogger.error(context, "firebase_config_load", error.getClass().getSimpleName());
            return null;
        }
    }

    static void refresh(Context context, Callback callback) {
        Config config = load(context);
        if (callback != null) callback.onResult(config,
                config == null ? "Generated MAIN Firebase config could not be read." : "Using generated MAIN Firebase config.");
    }

    static void clearRemoteCache(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        prefs.edit().remove(AppPrefs.FIREBASE_CONFIG_CACHE).remove(AppPrefs.FIREBASE_CONFIG_SOURCE)
                .remove(AppPrefs.FIREBASE_CONFIG_URL).apply();
    }

    private FirebaseConfigLoader() {}
}
