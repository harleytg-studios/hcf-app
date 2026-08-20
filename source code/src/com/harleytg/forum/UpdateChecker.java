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

/** Stable-only release checker for the public Harley's Clan Forum app. */
final class UpdateChecker {
    private static final String RELEASES_URL = "https://api.github.com/repos/" + BuildInfo.UPDATE_REPOSITORY + "/releases?per_page=30";
    private static final String CACHE_ASSET_ID = "update_checked_asset_id";
    private static final String CACHE_ASSET_UPDATED = "update_checked_asset_updated";
    private static final String CACHE_VERSION_CODE = "update_checked_asset_version_code";
    static final String CHANNEL_STABLE = "stable";
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
        long versionCode;

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
            this.versionCode = -1L;
        }

        String assetKey() {
            if (assetId > 0L) return tag + "#" + assetId;
            if (!assetUpdatedAt.isEmpty()) return tag + "#" + assetUpdatedAt;
            return tag + "#" + apkUrl;
        }
    }

    static void check(Context context, String requestedChannel, final Callback callback) {
        if (callback == null) return;
        final Context app = context == null ? null : context.getApplicationContext();

        // Stable builds never honor a previously saved Dev/Beta channel preference.
        if (app != null) {
            SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, 0);
            if (!CHANNEL_STABLE.equalsIgnoreCase(prefs.getString(AppPrefs.UPDATE_CHANNEL, CHANNEL_STABLE))) {
                prefs.edit().putString(AppPrefs.UPDATE_CHANNEL, CHANNEL_STABLE).apply();
                AppLogger.info(app, "update_channel_lock", "stable");
            }
        }

        AppExecutors.network().execute(new Runnable() {
            @Override
            public void run() {
                runStableCheck(app, callback);
            }
        });
    }

    private static void runStableCheck(Context context, final Callback callback) {
        try {
            final Release release = fetchStable();
            long remoteCode = resolveApkVersionCode(context, release);
            release.versionCode = remoteCode;
            final boolean updateAvailable = remoteCode > 0L
                    ? remoteCode > BuildInfo.VERSION_CODE
                    : compareVersions(release.tag, BuildInfo.VERSION) > 0;
            post(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(release, updateAvailable);
                }
            });
        } catch (Throwable t) {
            String message = t.getMessage();
            if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
            final String out = message;
            post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(out);
                }
            });
        }
    }

    static int compareReleaseToInstalled(Release release) {
        if (release == null) return 0;
        if (release.versionCode <= 0L) return compareVersions(release.tag, BuildInfo.VERSION);
        if (release.versionCode == BuildInfo.VERSION_CODE) return 0;
        return release.versionCode < BuildInfo.VERSION_CODE ? -1 : 1;
    }

    static String displayVersion(Release release) {
        if (release == null) return "Unknown";
        String version = release.tag == null ? "" : release.tag.trim();
        if (version.startsWith("v") || version.startsWith("V")) version = version.substring(1);
        int dash = version.indexOf('-');
        if (dash > 0) version = version.substring(0, dash);
        return version.isEmpty() ? BuildInfo.VERSION : version;
    }

    private static Release fetchStable() throws Exception {
        JSONArray releases = new JSONArray(get(RELEASES_URL));
        Release best = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject item = releases.optJSONObject(i);
            if (item == null || item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue;

            Release candidate = parseStableRelease(item);
            if (candidate.tag.trim().isEmpty()) continue;
            if (best == null || compareVersions(candidate.tag, best.tag) > 0) best = candidate;
        }
        if (best == null) throw new IllegalStateException("No stable app release is published yet.");
        return best;
    }

    private static Release parseStableRelease(JSONObject releaseJson) {
        String tag = releaseJson.optString("tag_name", "").trim();
        String name = releaseJson.optString("name", tag).trim();
        String publishedAt = releaseJson.optString("published_at", "").trim();
        String releaseUrl = releaseJson.optString("html_url", "").trim();

        String apkUrl = "";
        String apkName = "";
        String assetUpdatedAt = "";
        long assetId = -1L;

        JSONArray assets = releaseJson.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;

                String candidateName = asset.optString("name", "").trim();
                String candidateUrl = asset.optString("browser_download_url", "").trim();
                String lowerName = candidateName.toLowerCase(Locale.US);

                // Stable releases must publish a clearly stable-labelled APK asset.
                boolean stableName = lowerName.startsWith("hcf-stable-")
                        || lowerName.startsWith("harleysclanforum-stable-");
                if (!stableName || !lowerName.endsWith(".apk")) continue;
                if (!AppSecurity.isTrustedReleaseDownload(candidateUrl)) continue;

                apkName = candidateName;
                apkUrl = candidateUrl;
                assetId = asset.optLong("id", -1L);
                assetUpdatedAt = asset.optString("updated_at", "").trim();
                break;
            }
        }

        return new Release(tag, name, publishedAt, releaseUrl, apkUrl, apkName,
                false, assetId, assetUpdatedAt);
    }

    private static long resolveApkVersionCode(Context context, Release release) throws Exception {
        if (context == null || release == null || release.apkUrl.trim().isEmpty()) return -1L;

        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        long cachedAssetId = prefs.getLong(CACHE_ASSET_ID, -1L);
        String cachedUpdated = prefs.getString(CACHE_ASSET_UPDATED, "");
        long cachedVersionCode = prefs.getLong(CACHE_VERSION_CODE, -1L);
        if (release.assetId > 0L
                && cachedAssetId == release.assetId
                && release.assetUpdatedAt.equals(cachedUpdated)
                && cachedVersionCode > 0L) {
            return cachedVersionCode;
        }

        File temp = new File(context.getCacheDir(), "hcf-stable-update-check-" + Math.max(0L, release.assetId) + ".apk");
        try {
            downloadForInspection(release.apkUrl, temp);
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(), 0);
            if (archive == null) throw new IllegalStateException("The published Stable APK could not be read by Android.");
            if (!context.getPackageName().equals(archive.packageName)) {
                throw new IllegalStateException("The published Stable APK uses the wrong Android package.");
            }
            long versionCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
            if (versionCode <= 0L) throw new IllegalStateException("The published Stable APK has an invalid versionCode.");

            prefs.edit()
                    .putLong(CACHE_ASSET_ID, release.assetId)
                    .putString(CACHE_ASSET_UPDATED, release.assetUpdatedAt)
                    .putLong(CACHE_VERSION_CODE, versionCode)
                    .apply();
            return versionCode;
        } finally {
            try {
                if (temp.isFile()) temp.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void downloadForInspection(String url, File file) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        InputStream input = null;
        FileOutputStream output = null;
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Stable APK metadata check failed (HTTP " + responseCode + ").");
            }

            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > MAX_CHECK_APK_BYTES) {
                throw new IllegalStateException("The published Stable APK is unexpectedly large.");
            }

            input = connection.getInputStream();
            output = new FileOutputStream(file, false);
            byte[] buffer = new byte[32768];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_CHECK_APK_BYTES) {
                    throw new IllegalStateException("The published Stable APK is unexpectedly large.");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            if (output != null) {
                try { output.close(); } catch (Throwable ignored) {}
            }
            if (input != null) {
                try { input.close(); } catch (Throwable ignored) {}
            }
            connection.disconnect();
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);

            int responseCode = connection.getResponseCode();
            String body = read(responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (responseCode >= 200 && responseCode < 300) return body;
            if (responseCode == 404) throw new IllegalStateException("No stable release is published yet.");
            throw new IllegalStateException("Stable update service check failed (HTTP " + responseCode + ").");
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        try {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            return out.toString();
        } finally {
            reader.close();
        }
    }

    static int compareVersions(String left, String right) {
        return SemVer.parse(left).compareTo(SemVer.parse(right));
    }

    private static final class SemVer implements Comparable<SemVer> {
        final int major;
        final int minor;
        final int patch;
        final String[] pre;

        SemVer(int major, int minor, int patch, String[] pre) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.pre = pre;
        }

        static SemVer parse(String value) {
            String text = value == null ? "" : value.trim();
            if (text.startsWith("v") || text.startsWith("V")) text = text.substring(1);
            int plus = text.indexOf('+');
            if (plus >= 0) text = text.substring(0, plus);

            String preText = "";
            int dash = text.indexOf('-');
            if (dash >= 0) {
                preText = text.substring(dash + 1);
                text = text.substring(0, dash);
            }

            String[] parts = text.split("\\.");
            return new SemVer(numberAt(parts, 0), numberAt(parts, 1), numberAt(parts, 2),
                    preText.trim().isEmpty() ? new String[0] : preText.split("\\."));
        }

        private static int numberAt(String[] values, int index) {
            if (values == null || index >= values.length) return 0;
            try {
                String digits = values[index].replaceAll("[^0-9]", "");
                return digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        @Override
        public int compareTo(SemVer other) {
            if (major != other.major) return major < other.major ? -1 : 1;
            if (minor != other.minor) return minor < other.minor ? -1 : 1;
            if (patch != other.patch) return patch < other.patch ? -1 : 1;
            if (pre.length == 0 && other.pre.length == 0) return 0;
            if (pre.length == 0) return 1;
            if (other.pre.length == 0) return -1;

            int max = Math.max(pre.length, other.pre.length);
            for (int i = 0; i < max; i++) {
                if (i >= pre.length) return -1;
                if (i >= other.pre.length) return 1;
                String a = pre[i];
                String b = other.pre[i];
                boolean aNumeric = a.matches("\\d+");
                boolean bNumeric = b.matches("\\d+");
                if (aNumeric && bNumeric) {
                    long av = safeLong(a);
                    long bv = safeLong(b);
                    if (av != bv) return av < bv ? -1 : 1;
                } else if (aNumeric != bNumeric) {
                    return aNumeric ? -1 : 1;
                } else {
                    int compared = a.compareToIgnoreCase(b);
                    if (compared != 0) return compared < 0 ? -1 : 1;
                }
            }
            return 0;
        }

        private static long safeLong(String value) {
            try {
                return Long.parseLong(value);
            } catch (Throwable ignored) {
                return 0L;
            }
        }
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private UpdateChecker() {}
}
