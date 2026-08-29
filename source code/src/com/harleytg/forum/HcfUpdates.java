package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;


// ---- Consolidated from HcfUpdateEngine.java ----
public final class HcfUpdates {
    private HcfUpdates() {}

    // ---- UpdateCheckJobService.java ----
    /* loaded from: classes.dex */
    public static final class UpdateCheckJobService extends JobService {
        @Override // android.app.job.JobService
        public boolean onStopJob(JobParameters jobParameters) {
            return true;
        }

        /* renamed from: lambda$onStartJob$0$com-harleytg-forum-dev-UpdateCheckJobService, reason: not valid java name */
        /* synthetic */ void m211lambda$onStartJob$0$comharleytgforumdevUpdateCheckJobService(JobParameters jobParameters, UpdateChecker.Release release, boolean z, String str) {
            jobFinished(jobParameters, false);
        }

        @Override // android.app.job.JobService
        public boolean onStartJob(final JobParameters jobParameters) {
            UpdateAutomation.maybeCheck(this, true, new UpdateAutomation.Listener() { // from class: com.harleytg.forum.dev.UpdateCheckJobService$$ExternalSyntheticLambda0
                @Override // com.harleytg.forum.dev.UpdateAutomation.Listener
                public final void onFinished(UpdateChecker.Release release, boolean z, String str) {
                    UpdateCheckJobService.this.m211lambda$onStartJob$0$comharleytgforumdevUpdateCheckJobService(jobParameters, release, z, str);
                }
            });
            return true;
        }
    }

    // ---- UpdateDownloadReceiver.java ----
    /* loaded from: classes.dex */
    public static final class UpdateDownloadReceiver extends BroadcastReceiver {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null || !"android.intent.action.DOWNLOAD_COMPLETE".equals(intent.getAction())) {
                return;
            }
            long longExtra = intent.getLongExtra("extra_download_id", -1L);
            boolean installerOpened = false;
            SharedPreferences sharedPreferences = context.getSharedPreferences(AppPrefs.FILE, 0);
            long expectedId = sharedPreferences.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
            if (longExtra <= 0 || longExtra != expectedId) {
                return;
            }
            int status = AppUpdateDownloader.status(context, longExtra);
            String tag = sharedPreferences.getString(AppPrefs.UPDATE_DOWNLOAD_LABEL, "update");
            if (status == 8) {
                AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(context, longExtra);
                if (verification.ok) {
                    boolean autoInstall = sharedPreferences.getBoolean(AppPrefs.UPDATE_AUTO_INSTALL, true);
                    boolean foreground = RuntimeState.isForeground();

                    // Always remember that a verified APK is waiting. This survives Android
                    // blocking a background activity launch and lets Settings expose Install.
                    sharedPreferences.edit().putBoolean(AppPrefs.UPDATE_INSTALL_PENDING, true).apply();

                    // Android 10+ may block activities launched directly from a background
                    // DOWNLOAD_COMPLETE receiver. Only hand off immediately while HCF is
                    // visibly foregrounded; otherwise post a user-initiated install action.
                    if (autoInstall && foreground) {
                        try {
                            if (AppSecurity.canInstallUpdates(context)) {
                                installerOpened = AppUpdateDownloader.openInstaller(context, longExtra);
                            } else {
                                Intent permissionFlow = new Intent(context, (Class<?>) HcfSubActivities.SettingsActivity.class);
                                permissionFlow.setAction(context.getPackageName() + ".INSTALL_UPDATE");
                                permissionFlow.putExtra("download_id", longExtra);
                                permissionFlow.addFlags(335544320);
                                context.startActivity(permissionFlow);
                                installerOpened = true;
                            }
                        } catch (Throwable th) {
                            AppLogger.warn(context, "update_auto_install", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                        }
                    }

                    if (!installerOpened) {
                        NotificationHelper.postUpdateReady(context, tag, longExtra);
                    }
                    AppLogger.info(context, "update_download_complete", tag + " | verified | id=" + longExtra + " | foreground=" + foreground + " | autoInstaller=" + installerOpened);
                    return;
                }
                AppLogger.warn(context, "update_download_blocked", tag + " | " + verification.message);
                TelemetryService.sendDiagnosticEvent(context, "update_verification_blocked", verification.message);
                AppUpdateDownloader.cleanupAfterSuccessfulUpdate(context);
                return;
            }
            AppLogger.warn(context, "update_download_complete", tag + " | status=" + status);
            StringBuilder sb = new StringBuilder("DownloadManager status ");
            sb.append(status);
            TelemetryService.sendDiagnosticEvent(context, "update_download_failed", sb.toString());
        }
    }

    // ---- UpdateFileProvider.java ----
    /* loaded from: classes.dex */
    public static final class UpdateFileProvider extends ContentProvider {
        static final String AUTHORITY = "com.harleytg.forum.dev.updatefiles";

        @Override // android.content.ContentProvider
        public int delete(Uri uri, String str, String[] strArr) {
            return 0;
        }

        @Override // android.content.ContentProvider
        public boolean onCreate() {
            return true;
        }

        @Override // android.content.ContentProvider
        public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
            return 0;
        }

        static Uri uriForFile(Context context, File file) throws IOException {
            if (context == null || file == null) {
                throw new IOException("Missing update file");
            }
            File verifiedFile = verifiedFile(context, file.getName());
            if (!verifiedFile.getCanonicalFile().equals(file.getCanonicalFile())) {
                throw new IOException("Update file is outside the updater directory");
            }
            return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(verifiedFile.getName()).build();
        }

        @Override // android.content.ContentProvider
        public String getType(Uri uri) {
            return "application/vnd.android.package-archive";
        }

        @Override // android.content.ContentProvider
        public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
            Context context = getContext();
            if (context == null) {
                return null;
            }
            try {
                File fileForUri = fileForUri(context, uri);
                if (strArr == null || strArr.length == 0) {
                    strArr = new String[]{"_display_name", "_size"};
                }
                MatrixCursor matrixCursor = new MatrixCursor(strArr, 1);
                Object[] objArr = new Object[strArr.length];
                for (int i = 0; i < strArr.length; i++) {
                    if ("_display_name".equals(strArr[i])) {
                        objArr[i] = fileForUri.getName();
                    } else if ("_size".equals(strArr[i])) {
                        objArr[i] = Long.valueOf(fileForUri.length());
                    } else {
                        objArr[i] = null;
                    }
                }
                matrixCursor.addRow(objArr);
                return matrixCursor;
            } catch (Throwable unused) {
                return null;
            }
        }

        @Override // android.content.ContentProvider
        public ParcelFileDescriptor openFile(Uri uri, String str) throws FileNotFoundException {
            if (!"r".equals(str)) {
                throw new FileNotFoundException("Read-only update provider");
            }
            Context context = getContext();
            if (context == null) {
                throw new FileNotFoundException("Context unavailable");
            }
            try {
                return ParcelFileDescriptor.open(fileForUri(context, uri), 268435456);
            } catch (IOException e) {
                FileNotFoundException fnf = new FileNotFoundException(e.getMessage());
                fnf.initCause(e);
                throw fnf;
            }
        }

        private static File fileForUri(Context context, Uri uri) throws IOException {
            if (uri == null || !"content".equalsIgnoreCase(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())) {
                throw new IOException("Invalid update URI");
            }
            String lastPathSegment = uri.getLastPathSegment();
            if (lastPathSegment == null || lastPathSegment.trim().isEmpty()) {
                throw new IOException("Missing update filename");
            }
            return verifiedFile(context, lastPathSegment);
        }

        private static File verifiedFile(Context context, String str) throws IOException {
            String trim = str == null ? "" : str.trim();
            if (trim.isEmpty() || trim.contains("/") || trim.contains("\\") || trim.contains("..") || !trim.toLowerCase(Locale.US).endsWith(".apk")) {
                throw new IOException("Invalid update filename");
            }
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (externalFilesDir == null) {
                throw new IOException("Updater directory unavailable");
            }
            File canonicalFile = new File(externalFilesDir, trim).getCanonicalFile();
            if (externalFilesDir.getCanonicalFile().equals(canonicalFile.getParentFile()) && canonicalFile.isFile()) {
                return canonicalFile;
            }
            throw new IOException("Update file not found");
        }

        @Override // android.content.ContentProvider
        public Uri insert(Uri uri, ContentValues contentValues) {
            throw new SecurityException("UpdateFileProvider is read-only");
        }
    }
}

// ---- UpdateChecker.java ----
/** Dev/Beta release checker. This package is intentionally locked to prerelease releases. */
final class UpdateChecker {
    static final String CHANNEL_DEV = "dev";
    static final String CHANNEL_STABLE = "stable";
    private static final String RELEASES_URL = "https://api.github.com/repos/markhitchk/hcf-app/releases?per_page=30";
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
                Release release = fetchDev();
                resolveApkMetadata(app, release, CHANNEL_DEV);
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

    private static Release fetchDev() throws Exception {
        JSONArray releases = new JSONArray(get(RELEASES_URL));
        for (int i = 0; i < releases.length(); i++) {
            JSONObject object = releases.optJSONObject(i);
            if (object != null && !object.optBoolean("draft", false) && object.optBoolean("prerelease", false)) {
                Release release = parseRelease(object);
                if (!release.apkUrl.isEmpty()) return release;
            }
        }
        throw new IllegalStateException("No Dev/Beta release with a trusted APK is published yet.");
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
                if (candidateName.toLowerCase(Locale.US).endsWith(".apk") && AppSecurity.isTrustedReleaseDownload(candidateUrl)) {
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


// ---- AppUpdateDownloader.java ----
/* loaded from: classes.dex */
final class AppUpdateDownloader {
    static final String APK_MIME = "application/vnd.android.package-archive";

    static final class ProgressSnapshot {
        final long downloadedBytes;
        final int reason;
        final int status;
        final long totalBytes;

        ProgressSnapshot(int i, long j, long j2, int i2) {
            this.status = i;
            this.downloadedBytes = Math.max(0L, j);
            this.totalBytes = j2;
            this.reason = i2;
        }

        int percent() {
            long j = this.totalBytes;
            if (j <= 0) {
                return this.status == 8 ? 100 : -1;
            }
            return (int) Math.max(0L, Math.min(100L, (Math.min(j, this.downloadedBytes) * 100) / this.totalBytes));
        }
    }

    static long enqueue(Context context, UpdateChecker.Release release, boolean z) {
        if (context == null || release == null || release.apkUrl == null || release.apkUrl.isEmpty()) {
            return -1L;
        }
        if (!AppSecurity.isTrustedReleaseDownload(release.apkUrl)) {
            AppLogger.warn(context, "update_download_blocked", "untrusted release URL");
            return -1L;
        }
        if (release.versionCode <= 0L || !AppSecurity.isSha256(release.sha256)) {
            AppLogger.warn(context, "update_download_blocked", "release version or SHA-256 was not verified");
            return -1L;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences sharedPreferences = applicationContext.getSharedPreferences(AppPrefs.FILE, 0);
        long existingId = sharedPreferences.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        String existingKey = sharedPreferences.getString(AppPrefs.UPDATE_DOWNLOAD_TAG, "");
        String assetKey = release.assetKey();
        if (existingId > 0L) {
            int existingStatus = status(applicationContext, existingId);
            if (assetKey.equals(existingKey)
                    && (existingStatus == DownloadManager.STATUS_PENDING
                    || existingStatus == DownloadManager.STATUS_RUNNING
                    || existingStatus == DownloadManager.STATUS_PAUSED)) {
                return existingId;
            }
            if (assetKey.equals(existingKey) && existingStatus == DownloadManager.STATUS_SUCCESSFUL) {
                NotificationHelper.postUpdateReady(applicationContext, release.tag, existingId);
                return existingId;
            }
            // Failed, missing, or superseded downloads must never block a retry.
            cleanupAfterSuccessfulUpdate(applicationContext);
        }
        try {
            DownloadManager downloadManager = (DownloadManager) applicationContext.getSystemService("download");
            if (downloadManager == null) {
                return -1L;
            }
            String safeApkName = safeApkName(release.apkName, release.tag);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkUrl));
            request.setTitle("Harley's Clan Forum " + release.tag);
            request.setDescription("Downloading app update");
            request.setMimeType(APK_MIME);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(1);
            try {
                request.setVisibleInDownloadsUi(false);
            } catch (Throwable unused) {
            }
            request.setDestinationInExternalFilesDir(applicationContext, Environment.DIRECTORY_DOWNLOADS, safeApkName);
            long enqueue = downloadManager.enqueue(request);
            sharedPreferences.edit()
                    .putLong(AppPrefs.UPDATE_DOWNLOAD_ID, enqueue)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_TAG, assetKey)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_LABEL, release.tag)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_NAME, safeApkName)
                    .putLong(AppPrefs.UPDATE_DOWNLOAD_VERSION_CODE, release.versionCode)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_SHA256, release.sha256.toLowerCase(Locale.US))
                    .apply();
            AppLogger.info(applicationContext, "update_download", release.tag + " | id=" + enqueue + " | " + safeApkName);
            return enqueue;
        } catch (Throwable th) {
            AppLogger.error(applicationContext, "update_download", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            if (!z || release.releaseUrl == null || release.releaseUrl.isEmpty()) {
                return -1L;
            }
            try {
                Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(release.releaseUrl));
                intent.addFlags(268435456);
                applicationContext.startActivity(intent);
                return -1L;
            } catch (Throwable unused2) {
                return -1L;
            }
        }
    }

    static boolean isDownloaded(Context context) {
        long j = context.getSharedPreferences(AppPrefs.FILE, 0).getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        return j > 0 && status(context, j) == 8;
    }

    static long downloadedId(Context context) {
        long j = context.getSharedPreferences(AppPrefs.FILE, 0).getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        if (j <= 0 || status(context, j) != 8) {
            return -1L;
        }
        return j;
    }

    static boolean openInstaller(Context context, long j) {
        Uri uriForDownloadedFile;
        if (context != null && j > 0) {
            AppSecurity.ApkVerification verifyDownloadedUpdate = AppSecurity.verifyDownloadedUpdate(context, j);
            if (!verifyDownloadedUpdate.ok) {
                AppLogger.warn(context, "update_verify_blocked", verifyDownloadedUpdate.message);
                try {
                    Toast.makeText(context, verifyDownloadedUpdate.message, 1).show();
                } catch (Throwable unused) {
                }
                return false;
            }
            AppLogger.info(context, "update_verify", verifyDownloadedUpdate.message);
            SharedPreferences sharedPreferences = context.getSharedPreferences(AppPrefs.FILE, 0);
            String string = sharedPreferences.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File file = (externalFilesDir == null || string == null || string.trim().isEmpty()) ? null : new File(externalFilesDir, string);
            if (file != null && file.isFile()) {
                try {
                    Uri uriForFile = HcfUpdates.UpdateFileProvider.uriForFile(context, file);
                    if (launchInstaller(context, installerIntent(uriForFile), uriForFile)) {
                        sharedPreferences.edit().putBoolean("update_install_pending", true).remove("update_resume_after_permission").apply();
                        AppLogger.info(context, "update_install", "app-provider | " + uriForFile.getAuthority());
                        return true;
                    }
                } catch (Throwable th) {
                    AppLogger.warn(context, "update_install_provider", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                }
                try {
                    DownloadManager downloadManager = (DownloadManager) context.getSystemService("download");
                    if (downloadManager == null || (uriForDownloadedFile = downloadManager.getUriForDownloadedFile(j)) == null || !launchInstaller(context, installerIntent(uriForDownloadedFile), uriForDownloadedFile)) {
                        return false;
                    }
                    sharedPreferences.edit().putBoolean("update_install_pending", true).remove("update_resume_after_permission").apply();
                    AppLogger.info(context, "update_install", "download-manager-fallback");
                    return true;
                } catch (Throwable th2) {
                    AppLogger.error(context, "update_install", th2.getClass().getSimpleName() + ": " + String.valueOf(th2.getMessage()));
                    return false;
                }
            }
            AppLogger.error(context, "update_install", "Verified APK path disappeared before installer launch");
        }
        return false;
    }

    private static Intent installerIntent(Uri uri) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setDataAndType(uri, APK_MIME);
        intent.setClipData(ClipData.newRawUri("Harley's Clan Forum update", uri));
        intent.addFlags(268435457);
        return intent;
    }

    private static boolean launchInstaller(Context context, Intent intent, Uri uri) {
        try {
            List<ResolveInfo> queryIntentActivities = context.getPackageManager().queryIntentActivities(intent, 65536);
            if (queryIntentActivities != null && !queryIntentActivities.isEmpty()) {
                for (ResolveInfo resolveInfo : queryIntentActivities) {
                    if (resolveInfo != null) {
                        try {
                            if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != null) {
                                context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, 1);
                            }
                        } catch (Throwable unused) {
                        }
                    }
                }
                context.startActivity(intent);
                return true;
            }
            AppLogger.warn(context, "update_install", "No package installer resolved ACTION_VIEW for APK");
            return false;
        } catch (Throwable th) {
            AppLogger.warn(context, "update_install_launch", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            return false;
        }
    }

    static ProgressSnapshot progress(Context context, long id) {
        if (context == null || id <= 0L) return new ProgressSnapshot(0, 0L, -1L, 0);
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return new ProgressSnapshot(0, 0L, -1L, 0);
        Cursor c = null;
        try {
            c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c != null && c.moveToFirst()) {
                int statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadedIndex = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int reasonIndex = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                int status = statusIndex >= 0 ? c.getInt(statusIndex) : 0;
                long downloaded = downloadedIndex >= 0 ? c.getLong(downloadedIndex) : 0L;
                long total = totalIndex >= 0 ? c.getLong(totalIndex) : -1L;
                int reason = reasonIndex >= 0 ? c.getInt(reasonIndex) : 0;
                return new ProgressSnapshot(status, downloaded, total, reason);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return new ProgressSnapshot(0, 0L, -1L, 0);
    }

    static int status(Context context, long id) {
        if (id <= 0L) return 0;
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return 0;
        Cursor c = null;
        try {
            c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                return index >= 0 ? c.getInt(index) : 0;
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    static boolean cleanupAfterSuccessfulUpdate(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long id = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        boolean removed = false;
        if (id > 0L) {
            try {
                DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.remove(id);
                    removed = true;
                }
            } catch (Throwable t) {
                AppLogger.warn(app, "update_cleanup_dm", t.getClass().getSimpleName());
            }
        }

        try {
            File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file == null || !file.isFile()) continue;
                        String name = file.getName().toLowerCase(Locale.US);
                        boolean updaterApk = name.startsWith("hcf-beta-")
                                || name.startsWith("harleysclanforum-beta-")
                                || name.startsWith("harleysclanforum-");
                        if (name.endsWith(".apk") && updaterApk) {
                            try { if (file.delete()) removed = true; } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AppLogger.warn(app, "update_cleanup_files", t.getClass().getSimpleName());
        }

        prefs.edit()
                .remove(AppPrefs.UPDATE_DOWNLOAD_ID)
                .remove(AppPrefs.UPDATE_DOWNLOAD_LABEL)
                .remove(AppPrefs.UPDATE_DOWNLOAD_TAG)
                .remove(AppPrefs.UPDATE_DOWNLOAD_NAME)
                .remove(AppPrefs.UPDATE_DOWNLOAD_SHA256)
                .remove(AppPrefs.UPDATE_DOWNLOAD_VERSION_CODE)
                .remove(AppPrefs.UPDATE_INSTALL_PENDING)
                .remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION)
                .putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL)
                .apply();
        AppLogger.info(app, "update_cleanup", "beta installer artifacts removed=" + removed);
        return removed;
    }

    static void cleanupIfCurrentVersionWasDownloaded(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long downloadedVersion = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_VERSION_CODE, -1L);
        long installedVersion = installedVersionCode(context);
        boolean installedDownload = downloadedVersion > 0L && downloadedVersion < installedVersion;
        if (downloadedVersion > 0L && downloadedVersion == installedVersion) {
            String expectedSha256 = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_SHA256, "");
            try {
                String installedSha256 = AppSecurity.installedApkSha256(context);
                installedDownload = AppSecurity.isSha256(expectedSha256)
                        && expectedSha256.equalsIgnoreCase(installedSha256);
            } catch (Throwable error) {
                AppLogger.warn(context, "update_cleanup_hash", error.getClass().getSimpleName());
            }
        }
        if (installedDownload) {
            cleanupAfterSuccessfulUpdate(context);
        }
        cleanupStaleUpdaterApks(context);
    }

    static boolean cleanupStaleUpdaterApks(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        boolean removed = false;
        SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long activeId = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        String activeName = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
        activeName = activeName == null ? "" : activeName.toLowerCase(Locale.US);
        int activeStatus = activeId > 0L ? status(app, activeId) : 0;
        boolean activeDownload = activeStatus == DownloadManager.STATUS_PENDING
                || activeStatus == DownloadManager.STATUS_RUNNING
                || activeStatus == DownloadManager.STATUS_PAUSED
                || activeStatus == DownloadManager.STATUS_SUCCESSFUL;
        long installedVersion = installedVersionCode(app);
        if (installedVersion <= 0L) installedVersion = BuildInfo.VERSION_CODE;
        try {
            File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null || !dir.isDirectory()) return false;
            File[] files = dir.listFiles();
            if (files == null) return false;
            PackageManager pm = app.getPackageManager();
            for (File file : files) {
                if (file == null || !file.isFile()) continue;
                String name = file.getName().toLowerCase(Locale.US);
                boolean updaterApk = name.startsWith("hcf-beta-")
                        || name.startsWith("harleysclanforum-beta-")
                        || name.startsWith("harleysclanforum-");
                if (!updaterApk || !name.endsWith(".apk")) continue;
                if (activeDownload && name.equals(activeName)) continue;
                PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (archive == null || archive.packageName == null || !app.getPackageName().equals(archive.packageName)) continue;
                long archiveVersion = archiveVersionCode(archive);
                if (archiveVersion > 0L && archiveVersion <= installedVersion) {
                    try { if (file.delete()) removed = true; } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            AppLogger.warn(app, "update_cleanup_stale", t.getClass().getSimpleName());
        }
        if (removed) AppLogger.info(app, "update_cleanup_stale", "old Beta updater APKs removed");
        return removed;
    }

    private static long installedVersionCode(Context context) {
        try {
            return archiveVersionCode(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        } catch (Throwable unused) {
            return BuildInfo.VERSION_CODE;
        }
    }

    private static long archiveVersionCode(PackageInfo packageInfo) {
        if (packageInfo == null) {
            return 0L;
        }
        return Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
    }

    private static String safeApkName(String str, String str2) {
        String trim = str == null ? "" : str.trim();
        if (!trim.toLowerCase(Locale.US).endsWith(".apk")) {
            StringBuilder sb = new StringBuilder("HarleysClanForum-");
            sb.append(str2 == null ? "update" : str2.replaceAll("[^A-Za-z0-9._-]", "-"));
            sb.append(".apk");
            trim = sb.toString();
        }
        return trim.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private AppUpdateDownloader() {
    }
}


// ---- UpdateScheduler.java ----
/* loaded from: classes.dex */
final class UpdateScheduler {
    private static final int JOB_ID = 41072;
    private static final long PERIOD_MS = 21600000;

    static void apply(Context context) {
        if (context == null) {
            return;
        }
        if (!context.getSharedPreferences("hcf_app", 0).getBoolean("update_auto_check", true)) {
            cancel(context);
        } else {
            schedule(context);
        }
    }

    static void schedule(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler == null) {
                return;
            }
            AppLogger.info(context, "update_schedule", jobScheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, (Class<?>) HcfUpdates.UpdateCheckJobService.class)).setRequiredNetworkType(1).setPeriodic(PERIOD_MS).setPersisted(true).build()) == 1 ? "scheduled_6h" : "failed");
        } catch (Throwable th) {
            AppLogger.error(context, "update_schedule", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
        }
    }

    static void cancel(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService("jobscheduler");
            if (jobScheduler != null) {
                jobScheduler.cancel(JOB_ID);
            }
            AppLogger.info(context, "update_schedule", "cancelled");
        } catch (Throwable th) {
            AppLogger.error(context, "update_schedule_cancel", th.getClass().getSimpleName());
        }
    }

    private UpdateScheduler() {
    }
}


// ---- UpdateAutomation.java ----
/* loaded from: classes.dex */
final class UpdateAutomation {
    private static final long FOREGROUND_MIN_INTERVAL_MS = 1800000;

    interface Listener {
        void onFinished(UpdateChecker.Release release, boolean z, String str);
    }

    static void maybeCheck(Context context, boolean z, final Listener listener) {
        if (context == null) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        if (!z && !sharedPreferences.getBoolean("update_auto_check", true)) {
            finish(listener, null, false, "Automatic update checks are off.");
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        long j = sharedPreferences.getLong("update_last_check", 0L);
        if (!z && j > 0 && currentTimeMillis - j < FOREGROUND_MIN_INTERVAL_MS) {
            finish(listener, null, false, null);
        } else {
            final String str = "dev";
            UpdateChecker.check(applicationContext, "dev", new UpdateChecker.Callback() { // from class: com.harleytg.forum.dev.UpdateAutomation.1
                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onResult(UpdateChecker.Release release, boolean z2) {
                    String string = sharedPreferences.getString("update_last_available_tag", "");
                    String assetKey = release.assetKey();
                    sharedPreferences.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                    if (z2) {
                        sharedPreferences.edit().putString("update_last_available_tag", assetKey).apply();
                        if (sharedPreferences.getBoolean("update_auto_download", false) && release.apkUrl != null && !release.apkUrl.isEmpty()) {
                            AppUpdateDownloader.enqueue(applicationContext, release, false);
                        } else if (!assetKey.equals(string)) {
                            NotificationHelper.postUpdateAvailable(applicationContext, release);
                        }
                    }
                    boolean z3 = UpdateChecker.compareReleaseToInstalled(release) < 0;
                    AppLogger.info(applicationContext, "update_auto_check", str + " | " + release.tag + " | newer=" + z2 + " | feedBehind=" + z3);
                    UpdateAutomation.finish(listener, release, z2, null);
                }

                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onError(String str2) {
                    sharedPreferences.edit().putLong("update_last_check", System.currentTimeMillis()).apply();
                    AppLogger.warn(applicationContext, "update_auto_check", str2);
                    TelemetryService.sendDiagnosticEvent(applicationContext, "update_check_failed", str2);
                    UpdateAutomation.finish(listener, null, false, str2);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void finish(Listener listener, UpdateChecker.Release release, boolean z, String str) {
        if (listener != null) {
            listener.onFinished(release, z, str);
        }
    }

    private UpdateAutomation() {
    }
}


// ---- ReleaseNotes.java ----
/* loaded from: classes.dex */
final class ReleaseNotes {
    static final String NOTES = "Harley's Clan Forum • v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG + "
"
            + "• Build identity now shows version, versionCode, and the Development Build / Beta tag in App Settings and the forum drawer.
"
            + "• What's New now reads the live BuildInfo version/build instead of the stale v1.0 label.
"
            + "• Android 14 foreground-service reliability fix is retained: network and screen callbacks sync the already-running notification service instead of self-restarting it.
"
            + "• Safe Mode, crash recovery, diagnostics, and sanitized crash reporting remain available for recovery builds.
"
            + "• Home-screen widget controls include theme following, compact mode, unread count, last-updated status, refresh behavior, and tap actions.
"
            + "• Theme selection includes Forum Auto, Phone Auto, Light, Dark, and AMOLED.
"
            + "• Developer notification/runtime tools and secure same-version APK hash updates remain enabled for the Dev/Beta channel.

"
            + "Stable remains separate; this feature set is scoped to com.harleytg.forum.dev.";
    static final String SUMMARY = "v" + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ") • " + BuildInfo.BUILD_TAG;
    private static final String NOTES_REVISION = "build-identity-v2";

    static void seedForFreshInstall(SharedPreferences sharedPreferences) {
        markSeen(sharedPreferences);
    }

    private static String releaseId() {
        return BuildInfo.VERSION + "-" + BuildInfo.VERSION_CODE + "-" + NOTES_REVISION;
    }

    static boolean shouldNotify(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return false;
        }
        return !releaseId().equals(sharedPreferences.getString("last_seen_whats_new_version", ""));
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public static void markSeen(SharedPreferences sharedPreferences) {
        if (sharedPreferences == null) {
            return;
        }
        sharedPreferences.edit().putString("last_seen_whats_new_version", releaseId()).apply();
    }

    static void show(Activity activity, SharedPreferences sharedPreferences, boolean z) {
        showCustom(activity, sharedPreferences, z);
    }

    static void showCustom(Activity activity, final SharedPreferences sharedPreferences, boolean z) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        dialog.setCancelable(true);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundResource(R.drawable.card_background);
        int dp = dp(activity, 18);
        linearLayout.setPadding(dp, dp, dp, dp);
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        ImageView imageView = new ImageView(activity);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout2.addView(imageView, new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)));
        LinearLayout linearLayout3 = new LinearLayout(activity);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(activity, 12);
        linearLayout3.addView(label(activity, "Harley's Clan Forum • Release Notes", 9, R.color.hcf_meta, true));
        TextView label = label(activity, "What's New", 24, R.color.hcf_text, true);
        label.setPadding(0, dp(activity, 2), 0, 0);
        linearLayout3.addView(label);
        TextView label2 = label(activity, "v" + BuildInfo.VERSION + "  •  " + BuildInfo.VERSION_CODE + "  •  " + BuildInfo.BUILD_TAG, 11, R.color.hcf_cyan_bright, true);
        label2.setPadding(0, dp(activity, 4), 0, 0);
        linearLayout3.addView(label2);
        linearLayout2.addView(linearLayout3, layoutParams);
        linearLayout.addView(linearLayout2);
        TextView label3 = label(activity, SUMMARY, 13, R.color.hcf_text, false);
        label3.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        linearLayout.addView(label3);
        View view = new View(activity);
        view.setBackgroundColor(activity.getColor(R.color.hcf_divider));
        linearLayout.addView(view, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(1);
        LinearLayout linearLayout4 = new LinearLayout(activity);
        linearLayout4.setOrientation(1);
        linearLayout4.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
        addSection(activity, linearLayout4, "Harley's Clan Forum (app) v1.0", "This is Beta/Dev build " + BuildInfo.VERSION_CODE + ". The public version remains 1.0 while the Android versionCode advances for normal in-place upgrades.");
        addSection(activity, linearLayout4, "New • VersionCode + SHA-256 updates", "Update checks now compare both Android versionCode and the exact APK SHA-256. A revised APK with the same versionCode is offered only when its hash differs, then its hash, package, version and signing-certificate lineage are verified again before Android's installer opens.");
        addSection(activity, linearLayout4, "Improved • Secure update reliability", "Failed downloads can be retried, superseded downloads are cleaned up, the newest trusted release APK is selected, and stale installer files no longer block a same-version hash revision.");
        addSection(activity, linearLayout4, "Fixed • Alerts and first-run setup", "Real forum-alert fallbacks stay on HCF Alerts even when passive Silent Alerts are disabled. What's New waits until the versioned App Setup Center has finished instead of being hidden behind it.");
        addSection(activity, linearLayout4, "Updated • Account & Security", "Account & Security now keeps forum-account controls together: identity, profile shortcuts and Account Security access. Android app permissions and hardening remain under Advanced & About > Permissions & Security.");
        addSection(activity, linearLayout4, "Updated • Connected sub-settings", "Expanded sub-settings now use the top row as the actual header of the content below it, removing duplicate titles and making each open section feel like one connected card.");
        addSection(activity, linearLayout4, "Updated • HCF Alerts background delivery", "Background notification sync now lives under HCF Alerts. HCF Alerts remains the real alert channel and is never disabled by the Silent Alerts setting. Disabling HCF Silent Alerts stops the continuous foreground sync service, so delivery while the app is closed may fall back to delayed Android background checks.");
        addSection(activity, linearLayout4, "New • Contact Support v2", "Contact Support now matches the HCF settings design with expandable sections, locked forum/app context, structured issue fields, privacy controls, report preview and email handoff.");
        addSection(activity, linearLayout4, "New • Performance Profiles", "Choose Auto, Performance, Balanced or Quality in App Settings. Auto reduces motion automatically on low-RAM devices or while Android battery saver is active.");
        scrollView.addView(linearLayout4, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        layoutParams2.topMargin = dp(activity, 4);
        linearLayout.addView(scrollView, layoutParams2);
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText("Done");
        button.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button.setTextSize(14.0f);
        button.setBackgroundResource(R.drawable.button_background);
        button.setGravity(17);
        button.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        layoutParams3.topMargin = dp(activity, 10);
        linearLayout.addView(button, layoutParams3);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.ReleaseNotes$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                dialog.dismiss();
            }
        });
        dialog.setContentView(linearLayout);
        if (z && sharedPreferences != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.harleytg.forum.dev.ReleaseNotes$$ExternalSyntheticLambda1
                @Override // android.content.DialogInterface.OnDismissListener
                public final void onDismiss(DialogInterface dialogInterface) {
                    ReleaseNotes.markSeen(sharedPreferences);
                }
            });
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(2);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
            window.setLayout(Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)), Math.max(dp(activity, 420), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.84f)));
            window.setGravity(17);
        }
        AppLogger.info(activity, "whats_new_open", "1.0 custom_ui");
    }

    private static void addSection(Activity activity, LinearLayout linearLayout, String str, String str2) {
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(1);
        linearLayout2.setBackgroundResource(R.drawable.identity_card_background);
        int dp = dp(activity, 12);
        linearLayout2.setPadding(dp, dp, dp, dp);
        linearLayout2.addView(label(activity, str, 11, R.color.hcf_cyan_bright, true));
        TextView label = label(activity, str2, 12, R.color.hcf_muted, false);
        label.setLineSpacing(0.0f, 1.08f);
        label.setPadding(0, dp(activity, 6), 0, 0);
        linearLayout2.addView(label);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(activity, 9);
        linearLayout.addView(linearLayout2, layoutParams);
    }

    private static TextView label(Activity activity, String str, int i, int i2, boolean z) {
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(activity.getColor(i2));
        if (z) {
            textView.setTypeface(null, 1);
        }
        return textView;
    }

    private static int dp(Activity activity, int i) {
        return Math.round(i * activity.getResources().getDisplayMetrics().density);
    }

    private ReleaseNotes() {
    }
}
