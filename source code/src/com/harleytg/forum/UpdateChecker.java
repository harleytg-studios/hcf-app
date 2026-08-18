package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class UpdateChecker {
    static final String CHANNEL_STABLE = "stable";
    static final String CHANNEL_DEV = "dev";

    private static final String CACHE_ASSET_ID = "update_checked_asset_id";
    private static final String CACHE_ASSET_UPDATED = "update_checked_asset_updated";
    private static final String CACHE_VERSION_CODE = "update_checked_asset_version_code";
    private static final long MAX_CHECK_APK_BYTES = 100L * 1024L * 1024L;

    interface Callback {
        void onResult(Release release, boolean updateAvailable);
        void onError(String message);
    }

    static final class Release {
        final String tag;
        final String name;
        final String publishedAt;
        final String releaseUrl;
        final String apkUrl;
        final String apkName;
        final boolean prerelease;
        final long assetId;
        final String assetUpdatedAt;
        long versionCode;

        Release(String tag, String name, String publishedAt, String releaseUrl,
                String apkUrl, String apkName, boolean prerelease,
                long assetId, String assetUpdatedAt) {
            this.tag = tag;
            this.name = name;
            this.publishedAt = publishedAt;
            this.releaseUrl = releaseUrl;
            this.apkUrl = apkUrl;
            this.apkName = apkName;
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

    private static final String API_BASE = "https://api.github.com/repos/" + BuildInfo.UPDATE_REPOSITORY;

    static void check(Context context, String channel, Callback callback) {
        final Context app = context == null ? null : context.getApplicationContext();
        final String normalized = CHANNEL_DEV; // DEV/Beta package never consumes the stable feed.
        AppExecutors.network().execute(() -> {
            try {
                Release release = CHANNEL_STABLE.equals(normalized) ? fetchStable() : fetchDev();
                if (release == null) throw new IllegalStateException("No " + normalized + " release is published yet.");
                long remoteCode = resolveApkVersionCode(app, release);
                release.versionCode = remoteCode;
                boolean newer = remoteCode > 0L
                        ? remoteCode > BuildInfo.VERSION_CODE
                        : compareVersions(release.tag, BuildInfo.VERSION) > 0;
                post(() -> callback.onResult(release, newer));
            } catch (Throwable t) {
                String msg = t.getMessage();
                if (msg == null || msg.trim().isEmpty()) msg = t.getClass().getSimpleName();
                final String out = msg;
                post(() -> callback.onError(out));
            }
        });
    }

    static int compareReleaseToInstalled(Release release) {
        if (release == null) return 0;
        if (release.versionCode > 0L) {
            if (release.versionCode == BuildInfo.VERSION_CODE) return 0;
            return release.versionCode < BuildInfo.VERSION_CODE ? -1 : 1;
        }
        return compareVersions(release.tag, BuildInfo.VERSION);
    }

    static String displayVersion(Release release) {
        if (release == null) return "Unknown";
        String shown = release.tag == null ? "" : release.tag.trim();
        if (shown.startsWith("v") || shown.startsWith("V")) shown = shown.substring(1);
        int dash = shown.indexOf('-');
        if (dash > 0) shown = shown.substring(0, dash);
        if (shown.isEmpty()) shown = BuildInfo.VERSION;
        return shown;
    }

    private static Release fetchStable() throws Exception {
        JSONArray arr = new JSONArray(get(API_BASE + "/releases?per_page=30"));
        Release best = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue;
            Release candidate = parseRelease(item);
            if (candidate.tag == null || candidate.tag.trim().isEmpty()) continue;
            if (best == null || compareVersions(candidate.tag, best.tag) > 0) best = candidate;
        }
        if (best != null) return best;
        throw new IllegalStateException("No stable app release is published yet.");
    }

    private static Release fetchDev() throws Exception {
        JSONArray arr = new JSONArray(get(API_BASE + "/releases?per_page=30"));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("draft", false)) continue;
            if (!item.optBoolean("prerelease", false)) continue;
            return parseRelease(item);
        }
        throw new IllegalStateException("No preview app release is published for the dev channel yet.");
    }

    private static Release parseRelease(JSONObject json) {
        String tag = json.optString("tag_name", "").trim();
        String name = json.optString("name", tag).trim();
        String published = json.optString("published_at", "").trim();
        String releaseUrl = json.optString("html_url", "").trim();
        boolean prerelease = json.optBoolean("prerelease", false);

        String apkUrl = "";
        String apkName = "";
        long assetId = -1L;
        String assetUpdated = "";
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String candidateName = asset.optString("name", "").trim();
                String candidateUrl = asset.optString("browser_download_url", "").trim();
                if (candidateName.toLowerCase(Locale.US).endsWith(".apk")
                        && AppSecurity.isTrustedReleaseDownload(candidateUrl)) {
                    apkName = candidateName;
                    apkUrl = candidateUrl;
                    assetId = asset.optLong("id", -1L);
                    assetUpdated = asset.optString("updated_at", "").trim();
                    break;
                }
            }
        }
        return new Release(tag, name, published, releaseUrl, apkUrl, apkName,
                prerelease, assetId, assetUpdated);
    }

    private static long resolveApkVersionCode(Context context, Release release) throws Exception {
        if (context == null || release == null || release.apkUrl == null || release.apkUrl.trim().isEmpty()) return -1L;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long cachedId = prefs.getLong(CACHE_ASSET_ID, -1L);
        String cachedUpdated = prefs.getString(CACHE_ASSET_UPDATED, "");
        long cachedCode = prefs.getLong(CACHE_VERSION_CODE, -1L);
        boolean sameAsset = release.assetId > 0L && cachedId == release.assetId
                && release.assetUpdatedAt.equals(cachedUpdated);
        if (sameAsset && cachedCode > 0L) return cachedCode;

        File apk = new File(context.getCacheDir(), "hcf-update-check-" + Math.max(0L, release.assetId) + ".apk");
        try {
            downloadForInspection(release.apkUrl, apk);
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null) throw new IllegalStateException("The published Beta APK could not be read by Android.");
            if (!context.getPackageName().equals(info.packageName)) {
                throw new IllegalStateException("The published Beta APK uses the wrong Android package.");
            }
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            if (code <= 0L) throw new IllegalStateException("The published Beta APK has an invalid versionCode.");
            prefs.edit()
                    .putLong(CACHE_ASSET_ID, release.assetId)
                    .putString(CACHE_ASSET_UPDATED, release.assetUpdatedAt)
                    .putLong(CACHE_VERSION_CODE, code)
                    .apply();
            return code;
        } finally {
            try { if (apk.isFile()) apk.delete(); } catch (Throwable ignored) {}
        }
    }

    private static void downloadForInspection(String urlString, File output) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
        c.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("Beta APK metadata check failed (HTTP " + code + ").");
        }
        long declared = c.getContentLengthLong();
        if (declared > MAX_CHECK_APK_BYTES) {
            c.disconnect();
            throw new IllegalStateException("The published Beta APK is unexpectedly large.");
        }
        InputStream in = c.getInputStream();
        FileOutputStream out = new FileOutputStream(output, false);
        byte[] buffer = new byte[32768];
        long total = 0L;
        try {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                total += n;
                if (total > MAX_CHECK_APK_BYTES) throw new IllegalStateException("The published Beta APK is unexpectedly large.");
                out.write(buffer, 0, n);
            }
            out.flush();
        } finally {
            try { out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
            c.disconnect();
        }
    }

    private static String get(String urlString) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = read(in);
        c.disconnect();
        if (code < 200 || code >= 300) {
            if (code == 404) throw new IllegalStateException("No release is published for this channel yet.");
            throw new IllegalStateException("Update service check failed (HTTP " + code + ").");
        }
        return body;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line).append('\n');
        r.close();
        return b.toString();
    }

    static int compareVersions(String left, String right) {
        SemVer a = SemVer.parse(left);
        SemVer b = SemVer.parse(right);
        return a.compareTo(b);
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

        static SemVer parse(String raw) {
            String v = raw == null ? "" : raw.trim();
            if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
            v = v.replace("/dev", "-dev.0");
            int plus = v.indexOf('+');
            if (plus >= 0) v = v.substring(0, plus);
            String core = v;
            String preText = "";
            int dash = v.indexOf('-');
            if (dash >= 0) {
                core = v.substring(0, dash);
                preText = v.substring(dash + 1);
            }
            String[] nums = core.split("\\.");
            int major = numberAt(nums, 0);
            int minor = numberAt(nums, 1);
            int patch = numberAt(nums, 2);
            String[] pre = preText.trim().isEmpty() ? new String[0] : preText.split("\\.");
            return new SemVer(major, minor, patch, pre);
        }

        private static int numberAt(String[] nums, int index) {
            if (nums == null || index >= nums.length) return 0;
            try { return Integer.parseInt(nums[index].replaceAll("[^0-9]", "")); }
            catch (Throwable ignored) { return 0; }
        }

        @Override
        public int compareTo(SemVer other) {
            if (major != other.major) return major < other.major ? -1 : 1;
            if (minor != other.minor) return minor < other.minor ? -1 : 1;
            if (patch != other.patch) return patch < other.patch ? -1 : 1;
            if (pre.length == 0 && other.pre.length == 0) return 0;
            if (pre.length == 0) return 1;
            if (other.pre.length == 0) return -1;
            int len = Math.max(pre.length, other.pre.length);
            for (int i = 0; i < len; i++) {
                if (i >= pre.length) return -1;
                if (i >= other.pre.length) return 1;
                String a = pre[i];
                String b = other.pre[i];
                boolean an = a.matches("\\d+");
                boolean bn = b.matches("\\d+");
                if (an && bn) {
                    long av = safeLong(a);
                    long bv = safeLong(b);
                    if (av != bv) return av < bv ? -1 : 1;
                } else if (an != bn) {
                    return an ? -1 : 1;
                } else {
                    int c = a.compareToIgnoreCase(b);
                    if (c != 0) return c < 0 ? -1 : 1;
                }
            }
            return 0;
        }

        private static long safeLong(String value) {
            try { return Long.parseLong(value); }
            catch (Throwable ignored) { return 0L; }
        }
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private UpdateChecker() {}
}
