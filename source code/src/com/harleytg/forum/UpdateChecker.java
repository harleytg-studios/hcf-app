package com.harleytg.forum;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stable release checker. This package is locked to GitHub's latest official release. */
final class UpdateChecker {
    static final String CHANNEL_STABLE = "stable";
    private static final String RELEASES_URL = "https://api.github.com/repos/markhitchk/hcf-app/releases/latest";
    private static final String CACHE_ASSET_ID = "update_checked_asset_id";
    private static final String CACHE_ASSET_SHA256 = "update_checked_asset_sha256";
    private static final String CACHE_ASSET_UPDATED = "update_checked_asset_updated";
    private static final String CACHE_VERSION_CODE = "update_checked_asset_version_code";
    private static final long MAX_CHECK_APK_BYTES = 104857600L;

    interface Callback {
        void onError(String message);
        void onResult(Release release, boolean updateAvailable);
    }

    static final class Release {
        final String apkName;
        final String apkUrl;
        final long assetId;
        final String assetUpdatedAt;
        final String name;
        final boolean prerelease;
        final String publishedAt;
        final String releaseUrl;
        final String tag;
        boolean sameVersionHashUpdate;
        String sha256 = "";
        long versionCode = -1L;

        Release(String tag, String name, String publishedAt, String releaseUrl, String apkUrl,
                String apkName, boolean prerelease, long assetId, String assetUpdatedAt) {
            this.tag = tag == null ? "" : tag;
            this.name = name == null ? "" : name;
            this.publishedAt = publishedAt == null ? "" : publishedAt;
            this.releaseUrl = releaseUrl == null ? "" : releaseUrl;
            this.apkUrl = apkUrl == null ? "" : apkUrl;
            this.apkName = apkName == null ? "" : apkName;
            this.prerelease = prerelease;
            this.assetId = assetId;
            this.assetUpdatedAt = assetUpdatedAt == null ? "" : assetUpdatedAt;
        }

        String assetKey() {
            String base = assetId > 0L ? tag + "#" + assetId + "#" + assetUpdatedAt : tag + "#" + apkUrl;
            return sha256.isEmpty() ? base : base + "#sha256:" + sha256;
        }
    }

    static void check(Context context, String ignoredRequestedChannel, final Callback callback) {
        final Context app = context == null ? null : context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                Release release = fetchStable();
                resolveApkMetadata(app, release, CHANNEL_STABLE);
                boolean available = isUpdateAvailable(app, release);
                post(() -> callback.onResult(release, available));
            } catch (Throwable error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
                final String out = message;
                post(() -> callback.onError(out));
            }
        });
    }

    static int compareReleaseToInstalled(Release release) {
        if (release == null) return 0;
        if (release.versionCode > 0) return Long.compare(release.versionCode, BuildInfo.VERSION_CODE);
        return compareVersions(release.tag, BuildInfo.VERSION);
    }

    static String updateReason(Release release) {
        return release != null && release.sameVersionHashUpdate
                ? "same build code, revised APK hash"
                : "newer build code";
    }

    static String displayVersion(Release release) {
        if (release == null) return "Unknown";
        String value = release.tag == null ? "" : release.tag.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        int dash = value.indexOf('-');
        if (dash > 0) value = value.substring(0, dash);
        return value.isEmpty() ? BuildInfo.VERSION : value;
    }

    private static Release fetchStable() throws Exception {
        JSONObject object = new JSONObject(get(RELEASES_URL));
        if (object.optBoolean("draft", false) || object.optBoolean("prerelease", false)) {
            throw new IllegalStateException("The latest release is not an official Stable release.");
        }
        Release release = parseRelease(object);
        if (release.apkUrl.isEmpty()) {
            throw new IllegalStateException("The latest Stable release does not contain a trusted Stable APK.");
        }
        return release;
    }

    private static Release parseRelease(JSONObject object) {
        String tag = object.optString("tag_name", "").trim();
        String name = object.optString("name", tag).trim();
        String published = object.optString("published_at", "").trim();
        String page = object.optString("html_url", "").trim();
        boolean prerelease = object.optBoolean("prerelease", false);
        String apkUrl = "";
        String apkName = "";
        String assetUpdated = "";
        long assetId = -1L;
        JSONArray assets = object.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String candidateName = asset.optString("name", "").trim();
                String candidateUrl = asset.optString("browser_download_url", "").trim();
                if (isStableApkAsset(candidateName) && AppSecurity.isTrustedReleaseDownload(candidateUrl)) {
                    long candidateId = asset.optLong("id", -1L);
                    String candidateUpdated = asset.optString("updated_at", "").trim();
                    boolean newerAsset = apkUrl.isEmpty()
                            || candidateUpdated.compareTo(assetUpdated) > 0
                            || (candidateUpdated.equals(assetUpdated) && candidateId > assetId);
                    if (newerAsset) {
                        apkName = candidateName;
                        apkUrl = candidateUrl;
                        assetId = candidateId;
                        assetUpdated = candidateUpdated;
                    }
                }
            }
        }
        return new Release(tag, name, published, page, apkUrl, apkName, prerelease, assetId, assetUpdated);
    }

    private static boolean isStableApkAsset(String name) {
        String value = name == null ? "" : name.trim().toLowerCase(Locale.US);
        if (!value.endsWith(".apk")) return false;
        return !value.contains("beta")
                && !value.contains("dev")
                && !value.contains("preview")
                && !value.contains("debug")
                && !value.contains("unsigned");
    }

    private static void resolveApkMetadata(Context context, Release release, String channel) throws Exception {
        if (context == null || release == null || release.apkUrl.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("hcf_app", 0);
        long cachedId = prefs.getLong(CACHE_ASSET_ID, -1L);
        String cachedUpdated = prefs.getString(CACHE_ASSET_UPDATED, "");
        long cachedCode = prefs.getLong(CACHE_VERSION_CODE, -1L);
        String cachedSha256 = prefs.getString(CACHE_ASSET_SHA256, "");
        if (release.assetId > 0 && cachedId == release.assetId
                && release.assetUpdatedAt.equals(cachedUpdated) && cachedCode > 0
                && AppSecurity.isSha256(cachedSha256)) {
            release.versionCode = cachedCode;
            release.sha256 = cachedSha256.toLowerCase(Locale.US);
            return;
        }
        File apk = new File(context.getCacheDir(), "hcf-update-check-" + channel + "-" + Math.max(0L, release.assetId) + ".apk");
        try {
            downloadForInspection(release.apkUrl, apk, channel);
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null) throw new IllegalStateException("The published " + channel + " APK could not be read by Android.");
            if (!context.getPackageName().equals(info.packageName)) throw new IllegalStateException("The published " + channel + " APK uses the wrong Android package.");
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (code <= 0) throw new IllegalStateException("The published " + channel + " APK has an invalid versionCode.");
            String sha256 = AppSecurity.fileSha256(apk);
            if (!AppSecurity.isSha256(sha256)) throw new IllegalStateException("The published " + channel + " APK hash could not be verified.");
            release.versionCode = code;
            release.sha256 = sha256;
            prefs.edit()
                    .putLong(CACHE_ASSET_ID, release.assetId)
                    .putString(CACHE_ASSET_UPDATED, release.assetUpdatedAt)
                    .putLong(CACHE_VERSION_CODE, code)
                    .putString(CACHE_ASSET_SHA256, sha256)
                    .apply();
        } finally {
            try { if (apk.isFile()) apk.delete(); } catch (Throwable ignored) {}
        }
    }

    private static boolean isUpdateAvailable(Context context, Release release) throws Exception {
        if (release == null) return false;
        if (release.versionCode <= 0L) return compareVersions(release.tag, BuildInfo.VERSION) > 0;
        long installedVersion = installedVersionCode(context);
        if (release.versionCode > installedVersion) return true;
        if (release.versionCode < installedVersion) return false;
        String installedSha256 = AppSecurity.installedApkSha256(context);
        release.sameVersionHashUpdate = AppSecurity.isSha256(release.sha256)
                && AppSecurity.isSha256(installedSha256)
                && !release.sha256.equalsIgnoreCase(installedSha256);
        return release.sameVersionHashUpdate;
    }

    private static long installedVersionCode(Context context) {
        if (context == null) return BuildInfo.VERSION_CODE;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Throwable ignored) {
            return BuildInfo.VERSION_CODE;
        }
    }

    private static void downloadForInspection(String url, File file, String channel) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
        connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException(channel + " APK metadata check failed (HTTP " + code + ").");
        }
        if (connection.getContentLengthLong() > MAX_CHECK_APK_BYTES) {
            connection.disconnect();
            throw new IllegalStateException("The published " + channel + " APK is unexpectedly large.");
        }
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file, false)) {
            byte[] buffer = new byte[32768];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_CHECK_APK_BYTES) throw new IllegalStateException("The published " + channel + " APK is unexpectedly large.");
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            connection.disconnect();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
        int code = connection.getResponseCode();
        String body = read(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code >= 200 && code < 300) return body;
        if (code == 404) throw new IllegalStateException("No release is published for this channel yet.");
        throw new IllegalStateException("Update service check failed (HTTP " + code + ").");
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        }
    }

    static int compareVersions(String left, String right) {
        int[] a = numericVersion(left);
        int[] b = numericVersion(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] < b[i] ? -1 : 1;
        }
        return 0;
    }

    private static int[] numericVersion(String value) {
        String text = value == null ? "" : value.trim().replaceFirst("^[vV]", "");
        int dash = text.indexOf('-');
        if (dash >= 0) text = text.substring(0, dash);
        String[] parts = text.split("\\.");
        int[] out = {0, 0, 0};
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); } catch (Throwable ignored) {}
        }
        return out;
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private UpdateChecker() {}
}
