package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pulls the trusted forum-domain registry from the hcf-app main branch. */
final class RemoteDomainConfig {
    static final String CONFIG_URL = BuildInfo.REMOTE_DOMAIN_CONFIG;

    private static final String PREFS = "hcf_remote_domains";
    private static final String KEY_RAW = "raw";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final long REFRESH_MS = 60L * 60L * 1000L;
    private static final int MAX_BYTES = 16 * 1024;

    static void initialize(Context context) {
        final Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_RAW, null);
        if (cached != null) {
            Parsed parsed = parse(cached);
            if (parsed != null) ForumConfig.applyRemote(parsed.primary, parsed.backups);
        }

        long age = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0L);
        if (age >= 0L && age < REFRESH_MS) return;

        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                refresh(app);
            }
        });
    }

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/plain, text/*;q=0.9, */*;q=0.1");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " DomainConfig/" + BuildInfo.VERSION_CODE);
            String etag = prefs.getString(KEY_ETAG, null);
            if (etag != null && !etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                prefs.edit().putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
                return;
            }
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + status);

            String raw = readLimited(connection.getInputStream());
            Parsed parsed = parse(raw);
            if (parsed == null) throw new IllegalArgumentException("Invalid domains.config");

            ForumConfig.applyRemote(parsed.primary, parsed.backups);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(KEY_RAW, raw)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis());
            String newEtag = connection.getHeaderField("ETag");
            if (newEtag != null && !newEtag.isEmpty()) edit.putString(KEY_ETAG, newEtag);
            edit.apply();
            AppLogger.info(app, "domain_config", "github-main | primary=" + parsed.primary + " | hosts=" + ForumConfig.FORUM_HOSTS.size());
        } catch (Throwable error) {
            AppLogger.warn(app, "domain_config", "fallback | " + error.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Parsed parse(String raw) {
        if (raw == null || raw.length() > MAX_BYTES) return null;
        String section = "";
        String primary = null;
        Set<String> backups = new LinkedHashSet<>();
        Set<String> all = new LinkedHashSet<>();

        try {
            BufferedReader reader = new BufferedReader(new java.io.StringReader(raw));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.US);
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                String key = line.substring(0, equals).trim().toLowerCase(Locale.US);
                String value = line.substring(equals + 1).trim();
                if ("primary".equals(section) && "domain".equals(key)) {
                    primary = normalizeHost(value);
                    if (primary == null) return null;
                    all.add(primary);
                } else if ("backups".equals(section) && key.startsWith("domain")) {
                    String host = normalizeHost(value);
                    if (host == null) return null;
                    if (all.add(host)) backups.add(host);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        if (primary == null || primary.isEmpty()) return null;
        return new Parsed(primary, new ArrayList<>(backups));
    }

    private static String normalizeHost(String value) {
        if (value == null) return null;
        String host = value.trim().toLowerCase(Locale.US);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > 253) return null;
        if (host.contains("://") || host.contains("/") || host.contains("\\") || host.contains(":") || host.contains("@") || host.contains("?" ) || host.contains("#")) return null;
        try {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return null;
        }
        if (!(host.equals("harleytg.com") || host.endsWith(".harleytg.com") || host.equals("freeflarum.com") || host.endsWith(".freeflarum.com"))) return null;
        return host;
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new IllegalArgumentException("domains.config too large");
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Parsed {
        final String primary;
        final List<String> backups;
        Parsed(String primary, List<String> backups) {
            this.primary = primary;
            this.backups = backups;
        }
    }

    private RemoteDomainConfig() {}
}
