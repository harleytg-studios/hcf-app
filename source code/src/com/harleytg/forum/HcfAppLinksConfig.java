package com.harleytg.forum.dev;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

/**
 * Runtime mirror/validator for the shared HCF Digital Asset Links configuration.
 *
 * The repository main branch is the single source of truth for both Stable and
 * Dev/Beta package fingerprints. Android's OS-level App Links verifier still
 * checks each website's /.well-known/assetlinks.json; this class gives the HCF
 * app its own live view of the same canonical repository data.
 */
public final class HcfAppLinksConfig {
    public static final String MASTER_PAGE_URL =
            "https://github.com/markhitchk/hcf-app/blob/main/configs%2Fapp-links%2Fassetlinks.json";
    public static final String MASTER_RAW_URL =
            "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/app-links/assetlinks.json";

    private static final String PREFS = "hcf_app";
    private static final String KEY_JSON = "app_links_master_json";
    private static final String KEY_VALID = "app_links_master_valid";
    private static final String KEY_LAST_CHECK = "app_links_master_last_check";
    private static final String KEY_SOURCE = "app_links_master_source";
    private static final String KEY_STATUS = "app_links_master_status";
    private static final String KEY_PACKAGE = "app_links_master_package";
    private static final long REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private HcfAppLinksConfig() {}

    /** Starts a non-blocking refresh when the cached copy is missing or stale. */
    public static void bootstrap(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, 0);
        long checked = prefs.getLong(KEY_LAST_CHECK, 0L);
        if (prefs.contains(KEY_JSON) && System.currentTimeMillis() - checked < REFRESH_INTERVAL_MS) {
            return;
        }
        refreshAsync(app);
    }

    /** Forces a background refresh from the main-branch raw GitHub URL. */
    public static void refreshAsync(Context context) {
        if (context == null || !REFRESHING.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    refreshNow(app);
                } finally {
                    REFRESHING.set(false);
                }
            }
        }, "hcf-app-links-config");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private static void refreshNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        long now = System.currentTimeMillis();
        try {
            String json = download(MASTER_RAW_URL);
            Validation validation = validateForInstalledPackage(context, json);
            prefs.edit()
                    .putString(KEY_JSON, json)
                    .putBoolean(KEY_VALID, validation.valid)
                    .putLong(KEY_LAST_CHECK, now)
                    .putString(KEY_SOURCE, MASTER_RAW_URL)
                    .putString(KEY_PACKAGE, context.getPackageName())
                    .putString(KEY_STATUS, validation.status)
                    .apply();
            try {
                AppLogger.info(context, "app_links_master",
                        (validation.valid ? "verified" : "not_verified") + " | " + MASTER_RAW_URL);
            } catch (Throwable ignored) {
            }
        } catch (Throwable error) {
            prefs.edit()
                    .putLong(KEY_LAST_CHECK, now)
                    .putString(KEY_SOURCE, MASTER_RAW_URL)
                    .putString(KEY_PACKAGE, context.getPackageName())
                    .putString(KEY_STATUS, "Fetch failed: " + error.getClass().getSimpleName())
                    .apply();
            try {
                AppLogger.warn(context, "app_links_master", error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static String download(String source) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 AppLinksConfig");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        InputStream input = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line).append('\n');
            if (out.length() > 256 * 1024) {
                reader.close();
                connection.disconnect();
                throw new IllegalStateException("assetlinks.json too large");
            }
        }
        reader.close();
        connection.disconnect();
        return out.toString().trim();
    }

    private static Validation validateForInstalledPackage(Context context, String json) throws Exception {
        JSONArray statements = new JSONArray(json);
        String packageName = context.getPackageName();
        String signer = installedSignerSha256(context);
        if (signer.isEmpty()) return new Validation(false, "Installed signing certificate unavailable");

        for (int i = 0; i < statements.length(); i++) {
            JSONObject statement = statements.optJSONObject(i);
            if (statement == null || !hasHandleAllUrls(statement.optJSONArray("relation"))) continue;
            JSONObject target = statement.optJSONObject("target");
            if (target == null) continue;
            if (!"android_app".equals(target.optString("namespace"))) continue;
            if (!packageName.equals(target.optString("package_name"))) continue;

            JSONArray fingerprints = target.optJSONArray("sha256_cert_fingerprints");
            if (fingerprints == null) continue;
            for (int j = 0; j < fingerprints.length(); j++) {
                String expected = normalizeFingerprint(fingerprints.optString(j));
                if (signer.equals(expected)) {
                    return new Validation(true,
                            "Current package and signing certificate match the main assetlinks.json");
                }
            }
            return new Validation(false,
                    "Package is listed, but the installed signing certificate does not match");
        }
        return new Validation(false, "Current package is not listed in the main assetlinks.json");
    }

    private static boolean hasHandleAllUrls(JSONArray relations) {
        if (relations == null) return false;
        for (int i = 0; i < relations.length(); i++) {
            if ("delegate_permission/common.handle_all_urls".equals(relations.optString(i))) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static String installedSignerSha256(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo info;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) return "";
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(signatures[0].toByteArray()));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }

    private static String normalizeFingerprint(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").toUpperCase(Locale.US);
    }

    public static Snapshot snapshot(Context context) {
        if (context == null) return new Snapshot(false, 0L, MASTER_RAW_URL, "No context");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return new Snapshot(
                prefs.getBoolean(KEY_VALID, false),
                prefs.getLong(KEY_LAST_CHECK, 0L),
                prefs.getString(KEY_SOURCE, MASTER_RAW_URL),
                prefs.getString(KEY_STATUS, "Not checked yet"));
    }

    public static final class Snapshot {
        public final boolean valid;
        public final long lastCheckedAt;
        public final String source;
        public final String status;

        Snapshot(boolean valid, long lastCheckedAt, String source, String status) {
            this.valid = valid;
            this.lastCheckedAt = lastCheckedAt;
            this.source = source == null ? MASTER_RAW_URL : source;
            this.status = status == null ? "" : status;
        }
    }

    private static final class Validation {
        final boolean valid;
        final String status;

        Validation(boolean valid, String status) {
            this.valid = valid;
            this.status = status;
        }
    }

    /**
     * Auto-start hook. Android creates providers before Application.onCreate(), so
     * this makes the main-branch assetlinks source refresh without blocking launch.
     */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context != null) HcfAppLinksConfig.bootstrap(context);
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
}
