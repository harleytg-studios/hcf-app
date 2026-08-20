package com.harleytg.forum.dev;

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

/* loaded from: classes.dex */
final class UpdateChecker {
    private static final String API_BASE = "https://api.github.com/repos/markhitchk/hcf-app";
    private static final String CACHE_ASSET_ID = "update_checked_asset_id";
    private static final String CACHE_ASSET_UPDATED = "update_checked_asset_updated";
    private static final String CACHE_VERSION_CODE = "update_checked_asset_version_code";
    static final String CHANNEL_DEV = "dev";
    static final String CHANNEL_STABLE = "stable";
    private static final long MAX_CHECK_APK_BYTES = 104857600;

    interface Callback {
        void onError(String str);

        void onResult(Release release, boolean z);
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

        Release(String str, String str2, String str3, String str4, String str5, String str6, boolean z, long j, String str7) {
            this.tag = str;
            this.name = str2;
            this.publishedAt = str3;
            this.releaseUrl = str4;
            this.apkUrl = str5;
            this.apkName = str6;
            this.prerelease = z;
            this.assetId = j;
            this.assetUpdatedAt = str7 == null ? "" : str7;
            this.versionCode = -1L;
        }

        String assetKey() {
            if (this.assetId > 0) {
                return this.tag + "#" + this.assetId;
            }
            if (this.assetUpdatedAt.isEmpty()) {
                return this.tag + "#" + this.apkUrl;
            }
            return this.tag + "#" + this.assetUpdatedAt;
        }
    }

    static void check(Context context, String str, final Callback callback) {
        final Context applicationContext = context == null ? null : context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.UpdateChecker$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                UpdateChecker.lambda$check$2(applicationContext, callback);
            }
        });
    }

    /* JADX WARN: Code restructure failed: missing block: B:14:0x0026, code lost:
    
        if (compareVersions(r0.tag, "1.0") > 0) goto L12;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    static /* synthetic */ void lambda$check$2(android.content.Context r7, final com.harleytg.forum.dev.UpdateChecker.Callback r8) {
        /*
            com.harleytg.forum.dev.UpdateChecker$Release r0 = fetchDev()     // Catch: java.lang.Throwable -> L39
            if (r0 == 0) goto L31
            long r1 = resolveApkVersionCode(r7, r0)     // Catch: java.lang.Throwable -> L39
            r0.versionCode = r1     // Catch: java.lang.Throwable -> L39
            r3 = 0
            int r7 = (r1 > r3 ? 1 : (r1 == r3 ? 0 : -1))
            r3 = 1
            r4 = 0
            if (r7 <= 0) goto L1e
            r5 = 10000071(0x9896c7, double:4.9406915E-317)
            int r7 = (r1 > r5 ? 1 : (r1 == r5 ? 0 : -1))
            if (r7 <= 0) goto L1c
            goto L28
        L1c:
            r3 = r4
            goto L28
        L1e:
            java.lang.String r7 = r0.tag     // Catch: java.lang.Throwable -> L39
            java.lang.String r1 = "1.0"
            int r7 = compareVersions(r7, r1)     // Catch: java.lang.Throwable -> L39
            if (r7 <= 0) goto L1c
        L28:
            com.harleytg.forum.dev.UpdateChecker$$ExternalSyntheticLambda1 r7 = new com.harleytg.forum.dev.UpdateChecker$$ExternalSyntheticLambda1     // Catch: java.lang.Throwable -> L39
            r7.<init>()     // Catch: java.lang.Throwable -> L39
            post(r7)     // Catch: java.lang.Throwable -> L39
            goto L5a
        L31:
            java.lang.IllegalStateException r7 = new java.lang.IllegalStateException     // Catch: java.lang.Throwable -> L39
            java.lang.String r0 = "No dev release is published yet."
            r7.<init>(r0)     // Catch: java.lang.Throwable -> L39
            throw r7     // Catch: java.lang.Throwable -> L39
        L39:
            r7 = move-exception
            java.lang.String r0 = r7.getMessage()
            if (r0 == 0) goto L4a
            java.lang.String r1 = r0.trim()
            boolean r1 = r1.isEmpty()
            if (r1 == 0) goto L52
        L4a:
            java.lang.Class r7 = r7.getClass()
            java.lang.String r0 = r7.getSimpleName()
        L52:
            com.harleytg.forum.dev.UpdateChecker$$ExternalSyntheticLambda2 r7 = new com.harleytg.forum.dev.UpdateChecker$$ExternalSyntheticLambda2
            r7.<init>()
            post(r7)
        L5a:
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.harleytg.forum.dev.UpdateChecker.lambda$check$2(android.content.Context, com.harleytg.forum.dev.UpdateChecker$Callback):void");
    }

    static int compareReleaseToInstalled(Release release) {
        if (release == null) {
            return 0;
        }
        if (release.versionCode <= 0) {
            return compareVersions(release.tag, "1.0");
        }
        if (release.versionCode == 10000071) {
            return 0;
        }
        return release.versionCode < 10000071 ? -1 : 1;
    }

    static String displayVersion(Release release) {
        if (release == null) {
            return "Unknown";
        }
        String trim = release.tag == null ? "" : release.tag.trim();
        if (trim.startsWith("v") || trim.startsWith("V")) {
            trim = trim.substring(1);
        }
        int indexOf = trim.indexOf(45);
        if (indexOf > 0) {
            trim = trim.substring(0, indexOf);
        }
        return trim.isEmpty() ? "1.0" : trim;
    }

    private static Release fetchStable() throws Exception {
        JSONArray jSONArray = new JSONArray(get("https://api.github.com/repos/markhitchk/hcf-app/releases?per_page=30"));
        Release release = null;
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null && !optJSONObject.optBoolean("draft", false) && !optJSONObject.optBoolean("prerelease", false)) {
                Release parseRelease = parseRelease(optJSONObject);
                if (parseRelease.tag != null && !parseRelease.tag.trim().isEmpty() && (release == null || compareVersions(parseRelease.tag, release.tag) > 0)) {
                    release = parseRelease;
                }
            }
        }
        if (release != null) {
            return release;
        }
        throw new IllegalStateException("No stable app release is published yet.");
    }

    private static Release fetchDev() throws Exception {
        JSONArray jSONArray = new JSONArray(get("https://api.github.com/repos/markhitchk/hcf-app/releases?per_page=30"));
        for (int i = 0; i < jSONArray.length(); i++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(i);
            if (optJSONObject != null && !optJSONObject.optBoolean("draft", false) && optJSONObject.optBoolean("prerelease", false)) {
                return parseRelease(optJSONObject);
            }
        }
        throw new IllegalStateException("No preview app release is published for the dev channel yet.");
    }

    private static Release parseRelease(JSONObject jSONObject) {
        String str;
        String str2;
        String str3;
        String trim = jSONObject.optString("tag_name", "").trim();
        String trim2 = jSONObject.optString("name", trim).trim();
        String trim3 = jSONObject.optString("published_at", "").trim();
        String trim4 = jSONObject.optString("html_url", "").trim();
        boolean optBoolean = jSONObject.optBoolean("prerelease", false);
        JSONArray optJSONArray = jSONObject.optJSONArray("assets");
        long j = -1;
        if (optJSONArray != null) {
            for (int i = 0; i < optJSONArray.length(); i++) {
                JSONObject optJSONObject = optJSONArray.optJSONObject(i);
                if (optJSONObject != null) {
                    str2 = optJSONObject.optString("name", "").trim();
                    String trim5 = optJSONObject.optString("browser_download_url", "").trim();
                    if (str2.toLowerCase(Locale.US).endsWith(".apk") && AppSecurity.isTrustedReleaseDownload(trim5)) {
                        j = optJSONObject.optLong("id", -1L);
                        str = trim5;
                        str3 = optJSONObject.optString("updated_at", "").trim();
                        break;
                    }
                }
            }
        }
        str = "";
        str2 = str;
        str3 = str2;
        return new Release(trim, trim2, trim3, trim4, str, str2, optBoolean, j, str3);
    }

    private static long resolveApkVersionCode(Context context, Release release) throws Exception {
        if (context == null || release == null || release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
            return -1L;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long j = sharedPreferences.getLong(CACHE_ASSET_ID, -1L);
        String string = sharedPreferences.getString(CACHE_ASSET_UPDATED, "");
        long j2 = sharedPreferences.getLong(CACHE_VERSION_CODE, -1L);
        if (release.assetId > 0 && j == release.assetId && release.assetUpdatedAt.equals(string) && j2 > 0) {
            return j2;
        }
        File file = new File(context.getCacheDir(), "hcf-update-check-" + Math.max(0L, release.assetId) + ".apk");
        try {
            downloadForInspection(release.apkUrl, file);
            PackageInfo packageArchiveInfo = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (packageArchiveInfo == null) {
                throw new IllegalStateException("The published Beta APK could not be read by Android.");
            }
            if (!context.getPackageName().equals(packageArchiveInfo.packageName)) {
                throw new IllegalStateException("The published Beta APK uses the wrong Android package.");
            }
            long longVersionCode = Build.VERSION.SDK_INT >= 28 ? packageArchiveInfo.getLongVersionCode() : packageArchiveInfo.versionCode;
            if (longVersionCode <= 0) {
                throw new IllegalStateException("The published Beta APK has an invalid versionCode.");
            }
            sharedPreferences.edit().putLong(CACHE_ASSET_ID, release.assetId).putString(CACHE_ASSET_UPDATED, release.assetUpdatedAt).putLong(CACHE_VERSION_CODE, longVersionCode).apply();
            return longVersionCode;
        } finally {
            try {
                if (file.isFile()) {
                    file.delete();
                }
            } catch (Throwable unused) {
            }
        }
    }

    private static void downloadForInspection(String str, File file) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.setConnectTimeout(12000);
        httpURLConnection.setReadTimeout(20000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*");
        httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0");
        int responseCode = httpURLConnection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            httpURLConnection.disconnect();
            throw new IllegalStateException("Beta APK metadata check failed (HTTP " + responseCode + ").");
        }
        if (httpURLConnection.getContentLengthLong() > MAX_CHECK_APK_BYTES) {
            httpURLConnection.disconnect();
            throw new IllegalStateException("The published Beta APK is unexpectedly large.");
        }
        InputStream inputStream = httpURLConnection.getInputStream();
        FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        byte[] bArr = new byte[32768];
        long j = 0;
        while (true) {
            try {
                int read = inputStream.read(bArr);
                if (read < 0) {
                    fileOutputStream.flush();
                    return;
                } else if (read != 0) {
                    j += read;
                    if (j > MAX_CHECK_APK_BYTES) {
                        throw new IllegalStateException("The published Beta APK is unexpectedly large.");
                    }
                    fileOutputStream.write(bArr, 0, read);
                }
            } finally {
                try {
                    fileOutputStream.close();
                } catch (Throwable unused) {
                }
                try {
                    inputStream.close();
                } catch (Throwable unused2) {
                }
                httpURLConnection.disconnect();
            }
        }
    }

    private static String get(String str) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.setConnectTimeout(8000);
        httpURLConnection.setReadTimeout(8000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty("Accept", "application/vnd.github+json");
        httpURLConnection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0");
        int responseCode = httpURLConnection.getResponseCode();
        String read = read((responseCode < 200 || responseCode >= 300) ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream());
        httpURLConnection.disconnect();
        if (responseCode >= 200 && responseCode < 300) {
            return read;
        }
        if (responseCode == 404) {
            throw new IllegalStateException("No release is published for this channel yet.");
        }
        throw new IllegalStateException("Update service check failed (HTTP " + responseCode + ").");
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                return sb.toString();
            }
            sb.append(readLine);
            sb.append('\n');
        }
    }

    static int compareVersions(String str, String str2) {
        return SemVer.parse(str).compareTo(SemVer.parse(str2));
    }

    private static final class SemVer implements Comparable<SemVer> {
        final int major;
        final int minor;
        final int patch;
        final String[] pre;

        SemVer(int i, int i2, int i3, String[] strArr) {
            this.major = i;
            this.minor = i2;
            this.patch = i3;
            this.pre = strArr;
        }

        static SemVer parse(String str) {
            String str2 = "";
            String trim = str == null ? "" : str.trim();
            if (trim.startsWith("v") || trim.startsWith("V")) {
                trim = trim.substring(1);
            }
            String replace = trim.replace("/dev", "-dev.0");
            int indexOf = replace.indexOf(43);
            if (indexOf >= 0) {
                replace = replace.substring(0, indexOf);
            }
            int indexOf2 = replace.indexOf(45);
            if (indexOf2 >= 0) {
                String substring = replace.substring(0, indexOf2);
                str2 = replace.substring(indexOf2 + 1);
                replace = substring;
            }
            String[] split = replace.split("\\.");
            return new SemVer(numberAt(split, 0), numberAt(split, 1), numberAt(split, 2), str2.trim().isEmpty() ? new String[0] : str2.split("\\."));
        }

        private static int numberAt(String[] strArr, int i) {
            if (strArr != null && i < strArr.length) {
                try {
                    return Integer.parseInt(strArr[i].replaceAll("[^0-9]", ""));
                } catch (Throwable unused) {
                }
            }
            return 0;
        }

        @Override // java.lang.Comparable
        public int compareTo(SemVer semVer) {
            int i = this.major;
            int i2 = semVer.major;
            if (i != i2) {
                return i < i2 ? -1 : 1;
            }
            int i3 = this.minor;
            int i4 = semVer.minor;
            if (i3 != i4) {
                return i3 < i4 ? -1 : 1;
            }
            int i5 = this.patch;
            int i6 = semVer.patch;
            if (i5 != i6) {
                return i5 < i6 ? -1 : 1;
            }
            String[] strArr = this.pre;
            if (strArr.length == 0 && semVer.pre.length == 0) {
                return 0;
            }
            if (strArr.length == 0) {
                return 1;
            }
            String[] strArr2 = semVer.pre;
            if (strArr2.length == 0) {
                return -1;
            }
            int max = Math.max(strArr.length, strArr2.length);
            for (int i7 = 0; i7 < max; i7++) {
                String[] strArr3 = this.pre;
                if (i7 >= strArr3.length) {
                    return -1;
                }
                String[] strArr4 = semVer.pre;
                if (i7 >= strArr4.length) {
                    return 1;
                }
                String str = strArr3[i7];
                String str2 = strArr4[i7];
                boolean matches = str.matches("\\d+");
                boolean matches2 = str2.matches("\\d+");
                if (matches && matches2) {
                    long safeLong = safeLong(str);
                    long safeLong2 = safeLong(str2);
                    if (safeLong != safeLong2) {
                        return safeLong < safeLong2 ? -1 : 1;
                    }
                } else {
                    if (matches != matches2) {
                        return matches ? -1 : 1;
                    }
                    int compareToIgnoreCase = str.compareToIgnoreCase(str2);
                    if (compareToIgnoreCase != 0) {
                        return compareToIgnoreCase < 0 ? -1 : 1;
                    }
                }
            }
            return 0;
        }

        private static long safeLong(String str) {
            try {
                return Long.parseLong(str);
            } catch (Throwable unused) {
                return 0L;
            }
        }
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private UpdateChecker() {
    }
}
