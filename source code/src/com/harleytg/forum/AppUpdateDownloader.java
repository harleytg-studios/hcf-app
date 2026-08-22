package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;
import com.harleytg.forum.dev.AppSecurity;
import com.harleytg.forum.dev.UpdateChecker;
import java.io.File;
import java.util.List;
import java.util.Locale;

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
        int status;
        if (context == null || release == null || release.apkUrl == null || release.apkUrl.isEmpty()) {
            return -1L;
        }
        if (!AppSecurity.isTrustedReleaseDownload(release.apkUrl)) {
            AppLogger.warn(context, "update_download_blocked", "untrusted release URL");
            return -1L;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        long j = sharedPreferences.getLong("update_download_id", -1L);
        String str = "";
        String string = sharedPreferences.getString("update_download_tag", "");
        String assetKey = release.assetKey();
        if (j <= 0 || string == null || string.isEmpty() || assetKey.equals(string) || (status = status(applicationContext, j)) == 1 || status == 2 || status == 4) {
            str = string;
        } else {
            cleanupAfterSuccessfulUpdate(applicationContext);
            j = -1;
        }
        if (assetKey.equals(str) && j > 0) {
            int status2 = status(applicationContext, j);
            if (status2 != 1 && status2 != 2 && status2 != 4) {
                if (status2 == 8) {
                    NotificationHelper.postUpdateReady(applicationContext, release.tag, j);
                }
            }
            return j;
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
            sharedPreferences.edit().putLong("update_download_id", enqueue).putString("update_download_tag", assetKey).putString("update_download_name", safeApkName).apply();
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
        long j = context.getSharedPreferences("hcf_app", 0).getLong("update_download_id", -1L);
        return j > 0 && status(context, j) == 8;
    }

    static long downloadedId(Context context) {
        long j = context.getSharedPreferences("hcf_app", 0).getLong("update_download_id", -1L);
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
            SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("update_download_name", "");
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File file = (externalFilesDir == null || string == null || string.trim().isEmpty()) ? null : new File(externalFilesDir, string);
            if (file != null && file.isFile()) {
                try {
                    Uri uriForFile = UpdateFileProvider.uriForFile(context, file);
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

    /* JADX WARN: Code restructure failed: missing block: B:34:0x0084, code lost:
    
        if (r3 != null) goto L36;
     */
    /* JADX WARN: Code restructure failed: missing block: B:36:0x0098, code lost:
    
        return new com.harleytg.forum.dev.AppUpdateDownloader.ProgressSnapshot(0, 0, -1, 0);
     */
    /* JADX WARN: Code restructure failed: missing block: B:37:0x0089, code lost:
    
        r3.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:38:0x0087, code lost:
    
        if (r3 != null) goto L36;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

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

    /* JADX WARN: Code restructure failed: missing block: B:21:0x0040, code lost:
    
        if (r0 != null) goto L22;
     */
    /* JADX WARN: Code restructure failed: missing block: B:22:0x0048, code lost:
    
        return 0;
     */
    /* JADX WARN: Code restructure failed: missing block: B:23:0x0045, code lost:
    
        r0.close();
     */
    /* JADX WARN: Code restructure failed: missing block: B:24:0x0043, code lost:
    
        if (r0 != null) goto L22;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

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

    /* JADX WARN: Can't wrap try/catch for region: R(8:5|(3:45|46|(7:48|49|8|9|(3:15|(3:17|(2:24|(3:28|29|(2:31|32)(1:33)))|23)|40)|41|42))|7|8|9|(5:11|13|15|(0)|40)|41|42) */
    /* JADX WARN: Code restructure failed: missing block: B:43:0x0085, code lost:
    
        r0 = move-exception;
     */
    /* JADX WARN: Code restructure failed: missing block: B:44:0x0086, code lost:
    
        com.harleytg.forum.dev.AppLogger.warn(r10, "update_cleanup_files", r0.getClass().getSimpleName());
     */
    /* JADX WARN: Removed duplicated region for block: B:17:0x0056 A[Catch: all -> 0x0085, TryCatch #2 {all -> 0x0085, blocks: (B:9:0x003f, B:11:0x0047, B:13:0x004d, B:15:0x0053, B:17:0x0056, B:19:0x005a, B:24:0x0061, B:26:0x0073), top: B:8:0x003f }] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

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
                .remove(AppPrefs.UPDATE_DOWNLOAD_TAG)
                .remove(AppPrefs.UPDATE_DOWNLOAD_NAME)
                .remove(AppPrefs.UPDATE_INSTALL_PENDING)
                .remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION)
                .putString(AppPrefs.UPDATE_CHANNEL, BuildInfo.DEFAULT_UPDATE_CHANNEL)
                .apply();
        AppLogger.info(app, "update_cleanup", "beta installer artifacts removed=" + removed);
        return removed;
    }

    static void cleanupIfCurrentVersionWasDownloaded(Context context) {
        if (context == null) {
            return;
        }
        String string = context.getSharedPreferences("hcf_app", 0).getString("update_download_tag", "");
        if (string != null && !string.trim().isEmpty()) {
            String trim = string.trim();
            if (trim.startsWith("v") || trim.startsWith("V")) {
                trim = trim.substring(1);
            }
            if ("1.0".equalsIgnoreCase(trim)) {
                cleanupAfterSuccessfulUpdate(context);
            }
        }
        cleanupStaleUpdaterApks(context);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:82:0x00d2  */
    /* JADX WARN: Type inference failed for: r13v4 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */

    static boolean cleanupStaleUpdaterApks(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        boolean removed = false;
        long activeId = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE)
                .getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        int activeStatus = activeId > 0L ? status(app, activeId) : 0;
        boolean activeDownload = activeStatus == DownloadManager.STATUS_PENDING
                || activeStatus == DownloadManager.STATUS_RUNNING
                || activeStatus == DownloadManager.STATUS_PAUSED;
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
                PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (archive == null || archive.packageName == null || !app.getPackageName().equals(archive.packageName)) continue;
                long archiveVersion = archiveVersionCode(archive);
                if (activeDownload && archiveVersion > installedVersion) continue;
                if (archiveVersion > 0L && archiveVersion <= installedVersion) {
                    try { if (file.delete()) removed = true; } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            AppLogger.warn(app, "update_cleanup_stale", t.getClass().getSimpleName());
        }
        if (removed) AppLogger.info(app, "update_cleanup_stale", "old Stable updater APKs removed");
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
