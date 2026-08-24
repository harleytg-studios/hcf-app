package com.harleytg.forum.dev;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sends a minimal HCF security observation to a private Discord moderation webhook.
 *
 * The webhook is supplied only at build time through a generated HcfDiscordSecret class.
 * The generated class stores an AES-encrypted value rather than plaintext, but because a
 * distributed APK must be able to decrypt its own credential this is obfuscation, not a
 * trusted secret store. Rotate the webhook if an APK or credential is exposed.
 */
public final class HcfDiscordObservation {
    private static final String PREFS = "hcf_discord_observation";
    private static final long TOUCH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String IPIFY = "https://api.ipify.org?format=json";
    private static final String IPINFO = "https://ipinfo.io/json";
    private static final String VISITOR_FIRST_PREFIX = "visitor:first:";
    private static final String VISITOR_LAST_PREFIX = "visitor:last:";
    private static final String VISITOR_COUNT_PREFIX = "visitor:count:";

    private HcfDiscordObservation() {}

    /** Starts observation asynchronously when the app process starts. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            final Context context = getContext();
            if (context == null) return true;
            final Context app = context.getApplicationContext();
            Thread worker = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        observe(app);
                    } catch (Throwable error) {
                        AppLogger.warn(app, "discord_observation", error.getClass().getSimpleName());
                    }
                }
            }, "hcf-discord-observation");
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.start();
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    static void observe(Context context) throws Exception {
        String webhook = decryptWebhookUrl();
        if (TextUtils.isEmpty(webhook)) {
            AppLogger.info(context, "discord_observation", "webhook not provisioned; skipped");
            return;
        }
        if (!isDiscordWebhook(webhook)) {
            AppLogger.warn(context, "discord_observation", "generated webhook failed host validation");
            return;
        }

        ForumIdentity.Snapshot identity = ForumIdentity.load(context);
        boolean loggedIn = identity != null && identity.loggedIn && !safe(identity.username).isEmpty();
        String username = loggedIn ? safe(identity.username) : "";

        if (HcfBanSystem.isBypassedUsername(username)) {
            AppLogger.info(context, "discord_observation", "bypass matched | scope=user; skipped");
            return;
        }

        PublicIp publicIp = lookupPublicIp();
        if (TextUtils.isEmpty(publicIp.address)) {
            AppLogger.warn(context, "discord_observation", "public IP lookup unavailable");
            return;
        }

        String ipHash = HcfBanSystem.sha256Hex(publicIp.address);
        if (HcfBanSystem.isBypassedIpHash(ipHash)) {
            AppLogger.info(context, "discord_observation", "bypass matched | scope=ip; skipped");
            return;
        }

        String touchKey = loggedIn
                ? "user:" + normalizedUsername(username) + ":" + ipHash
                : "guest:" + ipHash;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        long now = System.currentTimeMillis();
        long last = prefs.getLong("touch:" + touchKey, 0L);
        if (last > 0L && now - last < TOUCH_INTERVAL_MS) return;

        // New/returning is tracked by subject, not by the current user+IP touch key.
        // Signed-in users remain returning even when their public IP changes. Guests are
        // tracked by hashed public IP. This history is local to this app installation.
        String visitorKey = loggedIn
                ? "user:" + normalizedUsername(username)
                : "guest:" + ipHash;
        long storedFirst = prefs.getLong(VISITOR_FIRST_PREFIX + visitorKey, 0L);
        long storedLast = prefs.getLong(VISITOR_LAST_PREFIX + visitorKey, 0L);
        int previousCount = Math.max(0, prefs.getInt(VISITOR_COUNT_PREFIX + visitorKey, 0));
        boolean returning = storedFirst > 0L || previousCount > 0;
        long firstObservedMillis = storedFirst > 0L ? storedFirst : now;
        VisitorState visitorState = new VisitorState(
                returning ? "returning" : "new",
                returning,
                previousCount + 1,
                isoUtc(firstObservedMillis),
                storedLast > 0L ? isoUtc(storedLast) : ""
        );

        String observedAt = isoUtc(now);
        String privatePath = loggedIn
                ? "users/" + normalizedUsername(username) + ".json"
                : "guests/ip-" + safeFilename(publicIp.address.replace(':', '-').replace('.', '-')) + ".json";
        JSONObject record = loggedIn
                ? buildUserRecord(username, publicIp, ipHash, observedAt, context, privatePath, visitorState)
                : buildGuestRecord(publicIp, ipHash, observedAt, context, privatePath, visitorState);
        String filename = attachmentName(loggedIn, username, publicIp.address, now);

        JSONObject message = new JSONObject();
        message.put("username", "HCF Ban Uplink");
        message.put("content", "**HCF security observation**\n"
                + "Type: `" + (loggedIn ? "user" : "guest") + "`\n"
                + "Visitor: `" + (visitorState.returning ? "RETURNING" : "NEW") + "`\n"
                + "Observation #: `" + visitorState.observationCount + "`\n"
                + "First observed: `" + visitorState.firstObservedAt + "`\n"
                + (visitorState.previousObservedAt.isEmpty()
                    ? ""
                    : "Previous observed: `" + visitorState.previousObservedAt + "`\n")
                + (loggedIn ? "Account: `@" + discordSafe(username) + "`\n" : "Account: `guest`\n")
                + "IP: `" + discordSafe(publicIp.address) + "`\n"
                + "IP SHA-256: `" + ipHash + "`\n"
                + "IP source: `" + discordSafe(publicIp.source) + "`\n"
                + "Private record: `" + discordSafe(privatePath) + "`\n"
                + "Public ban list: `configs/ban-list.json`\n"
                + "JSON record attached for manual uplink.");

        postMultipart(webhook, message, filename, record.toString(2));
        SharedPreferences.Editor editor = prefs.edit()
                .putLong("touch:" + touchKey, now)
                .putLong(VISITOR_LAST_PREFIX + visitorKey, now)
                .putInt(VISITOR_COUNT_PREFIX + visitorKey, visitorState.observationCount);
        if (storedFirst <= 0L) {
            editor.putLong(VISITOR_FIRST_PREFIX + visitorKey, now);
        }
        editor.apply();
        AppLogger.info(context, "discord_observation",
                (loggedIn ? "user" : "guest") + " observation delivered | " + visitorState.status);
    }

    private static JSONObject buildUserRecord(String username, PublicIp ip, String ipHash,
                                               String observedAt, Context context,
                                               String privatePath, VisitorState visitorState) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("address", ip.address);
        entry.put("first_seen", observedAt);
        entry.put("last_seen", observedAt);
        entry.put("reported_source", ip.source);

        JSONArray ips = new JSONArray();
        ips.put(entry);

        JSONObject record = baseRecord("user", username, observedAt, ip, ipHash, context, privatePath, visitorState);
        record.put("ips", ips);
        return record;
    }

    private static JSONObject buildGuestRecord(PublicIp ip, String ipHash,
                                                String observedAt, Context context,
                                                String privatePath, VisitorState visitorState) throws Exception {
        JSONObject record = baseRecord("guest", null, observedAt, ip, ipHash, context, privatePath, visitorState);
        record.put("ip", ip.address);
        record.put("ip_sha256", ipHash);
        return record;
    }

    private static JSONObject baseRecord(String type, String username, String observedAt,
                                         PublicIp ip, String ipHash, Context context,
                                         String privatePath, VisitorState visitorState) throws Exception {
        JSONObject metadata = new JSONObject();
        metadata.put("last_app_version", BuildInfo.installedVersionName());
        metadata.put("last_package_name", context.getPackageName());
        metadata.put("last_reported_ip", ip.address);
        metadata.put("last_reported_ip_source", ip.source);
        metadata.put("last_reported_ip_sha256", ipHash);

        JSONObject ban = new JSONObject();
        ban.put("active", false);
        ban.put("ban_id", JSONObject.NULL);
        ban.put("reason", JSONObject.NULL);
        ban.put("created_at", JSONObject.NULL);
        ban.put("expires_at", JSONObject.NULL);
        ban.put("appeal_allowed", true);
        ban.put("notes", JSONObject.NULL);

        JSONObject uplink = new JSONObject();
        uplink.put("private_record", privatePath);
        uplink.put("public_ban_list", "configs/ban-list.json");
        uplink.put("username_key", username == null ? JSONObject.NULL : normalizedUsername(username));
        uplink.put("ip_sha256", ipHash);

        JSONObject record = new JSONObject();
        record.put("schema_version", 1);
        record.put("type", type);
        record.put("username", username == null ? JSONObject.NULL : username);
        record.put("first_seen", visitorState.firstObservedAt);
        record.put("last_seen", observedAt);
        record.put("visitor_status", visitorState.status);
        record.put("is_returning", visitorState.returning);
        record.put("observation_count", visitorState.observationCount);
        record.put("first_observed_at", visitorState.firstObservedAt);
        record.put("previous_observed_at", visitorState.previousObservedAt.isEmpty()
                ? JSONObject.NULL : visitorState.previousObservedAt);
        record.put("visitor_detection_scope", "app_install_observation_history");
        record.put("metadata", metadata);
        record.put("manual_uplink", uplink);
        record.put("ban", ban);
        return record;
    }

    private static void postMultipart(String webhook, JSONObject payload,
                                      String filename, String jsonAttachment) throws Exception {
        String boundary = "----HCFDiscord" + Long.toHexString(System.nanoTime());
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(webhook).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " DiscordObservation/2");

            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                writeAscii(out, "--" + boundary + "\r\n");
                writeAscii(out, "Content-Disposition: form-data; name=\"payload_json\"\r\n");
                writeAscii(out, "Content-Type: application/json; charset=utf-8\r\n\r\n");
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                writeAscii(out, "\r\n");

                writeAscii(out, "--" + boundary + "\r\n");
                writeAscii(out, "Content-Disposition: form-data; name=\"files[0]\"; filename=\""
                        + safeFilename(filename) + "\"\r\n");
                writeAscii(out, "Content-Type: application/json; charset=utf-8\r\n\r\n");
                out.write(jsonAttachment.getBytes(StandardCharsets.UTF_8));
                writeAscii(out, "\r\n--" + boundary + "--\r\n");
                out.flush();
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                InputStream error = connection.getErrorStream();
                if (error != null) readAll(error, 4096);
                throw new IllegalStateException("Discord webhook HTTP " + code);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static PublicIp lookupPublicIp() {
        PublicIp primary = lookupIp(IPIFY, "ipify");
        if (!TextUtils.isEmpty(primary.address)) return primary;
        return lookupIp(IPINFO, "IPinfo");
    }

    private static PublicIp lookupIp(String urlText, String source) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/plain;q=0.9");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " DiscordIpLookup/2");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return new PublicIp("", source);
            String body = readAll(connection.getInputStream(), 8192);
            String address;
            try { address = new JSONObject(body).optString("ip", ""); }
            catch (Throwable ignored) { address = body.trim(); }
            return new PublicIp(normalizeIp(address), source);
        } catch (Throwable ignored) {
            return new PublicIp("", source);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String decryptWebhookUrl() {
        try {
            Class<?> generated = Class.forName("com.harleytg.forum.dev.HcfDiscordSecret");
            Object value = generated.getMethod("decrypt").invoke(null);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isDiscordWebhook(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = safe(uri.getHost()).toLowerCase(Locale.US);
            String path = safe(uri.getPath());
            boolean hostOk = "discord.com".equals(host) || "www.discord.com".equals(host)
                    || "discordapp.com".equals(host) || "www.discordapp.com".equals(host);
            return "https".equalsIgnoreCase(uri.getScheme()) && hostOk && path.startsWith("/api/webhooks/");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String isoUtc(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    private static String attachmentName(boolean loggedIn, String username, String ip, long now) {
        String who = loggedIn ? normalizedUsername(username)
                : safeFilename(ip.replace(':', '-').replace('.', '-'));
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (loggedIn ? "user-" : "guest-") + who + "-" + format.format(new Date(now)) + ".json";
    }

    private static String normalizedUsername(String value) {
        String normalized = safe(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]", "-");
        while (normalized.contains("--")) normalized = normalized.replace("--", "-");
        if (normalized.isEmpty()) normalized = "unknown-user";
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private static String safeFilename(String value) {
        String clean = safe(value).replaceAll("[^A-Za-z0-9._-]", "-");
        return clean.isEmpty() ? "hcf-observation.json" : clean;
    }

    private static String discordSafe(String value) {
        return safe(value).replace("`", "'").replace("@", "＠");
    }

    private static void writeAscii(DataOutputStream out, String value) throws Exception {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String readAll(InputStream stream, int max) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                if (out.length() > max) throw new IllegalStateException("Response too large");
            }
            return out.toString();
        }
    }

    private static String normalizeIp(String value) {
        String raw = safe(value);
        if (raw.isEmpty() || raw.length() > 64) return "";
        if (raw.startsWith("::ffff:")) raw = raw.substring(7);
        if (raw.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            String[] parts = raw.split("\\.");
            for (String part : parts) {
                try {
                    int number = Integer.parseInt(part);
                    if (number < 0 || number > 255) return "";
                } catch (Throwable ignored) { return ""; }
            }
            return raw;
        }
        if (raw.indexOf(':') >= 0 && raw.matches("^[0-9a-fA-F:]+$")) return raw.toLowerCase(Locale.US);
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace((char) 0, ' ').trim();
    }

    private static final class VisitorState {
        final String status;
        final boolean returning;
        final int observationCount;
        final String firstObservedAt;
        final String previousObservedAt;

        VisitorState(String status, boolean returning, int observationCount,
                     String firstObservedAt, String previousObservedAt) {
            this.status = safe(status);
            this.returning = returning;
            this.observationCount = Math.max(1, observationCount);
            this.firstObservedAt = safe(firstObservedAt);
            this.previousObservedAt = safe(previousObservedAt);
        }
    }

    private static final class PublicIp {
        final String address;
        final String source;
        PublicIp(String address, String source) {
            this.address = safe(address);
            this.source = safe(source);
        }
    }
}
