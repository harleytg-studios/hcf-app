package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.List;
import android.widget.Toast;

final class AppUpdateDownloader {
    static final String APK_MIME = "application/vnd.android.package-archive";

    static final class ProgressSnapshot {
        final int status;
        final long downloadedBytes;
        final long totalBytes;
        final int reason;

        ProgressSnapshot(int status, long downloadedBytes, long totalBytes, int reason) {
            this.status = status;
            this.downloadedBytes = Math.max(0L, downloadedBytes);
            this.totalBytes = totalBytes;
            this.reason = reason;
        }

        int percent() {
            if (totalBytes <= 0L) return status == DownloadManager.STATUS_SUCCESSFUL ? 100 : -1;
            long value = Math.min(totalBytes, downloadedBytes);
            return (int) Math.max(0L, Math.min(100L, (value * 100L) / totalBytes));
        }
    }

    static long enqueue(Context context, UpdateChecker.Release release, boolean userRequested) {
        if (context == null || release == null || release.apkUrl == null || release.apkUrl.isEmpty()) return -1L;
        if (!AppSecurity.isTrustedReleaseDownload(release.apkUrl)) {
            AppLogger.warn(context, "update_download_blocked", "untrusted release URL");
            return -1L;
        }
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long existingId = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        String existingTag = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_TAG, "");
        String releaseIdentity = release.assetKey();

        // Never accumulate obsolete updater-owned APKs. If GitHub has moved on to a
        // different release and the previous download is not still active, remove
        // the old DownloadManager row and app-owned APK before downloading again.
        if (existingId > 0L && existingTag != null && !existingTag.isEmpty() && !releaseIdentity.equals(existingTag)) {
            int previousStatus = status(app, existingId);
            if (previousStatus != DownloadManager.STATUS_PENDING
                    && previousStatus != DownloadManager.STATUS_RUNNING
                    && previousStatus != DownloadManager.STATUS_PAUSED) {
                cleanupAfterSuccessfulUpdate(app);
                existingId = -1L;
                existingTag = "";
            }
        }

        if (releaseIdentity.equals(existingTag) && existingId > 0L) {
            int status = status(app, existingId);
            if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) {
                return existingId;
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                NotificationHelper.postUpdateReady(app, release.tag, existingId);
                return existingId;
            }
        }

        try {
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return -1L;
            String fileName = safeApkName(release.apkName, release.tag);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.apkUrl));
            request.setTitle("Harley's Clan Forum " + release.tag);
            request.setDescription("Downloading app update");
            request.setMimeType(APK_MIME);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // The installer is app-owned temporary data, not a user document. Keep it
            // out of the general Downloads UI where supported, then remove it after
            // the package replacement succeeds.
            try { request.setVisibleInDownloadsUi(false); } catch (Throwable ignored) {}
            request.setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = dm.enqueue(request);
            prefs.edit()
                    .putLong(AppPrefs.UPDATE_DOWNLOAD_ID, id)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_TAG, releaseIdentity)
                    .putString(AppPrefs.UPDATE_DOWNLOAD_NAME, fileName)
                    .apply();
            AppLogger.info(app, "update_download", release.tag + " | id=" + id + " | " + fileName);
            return id;
        } catch (Throwable t) {
            AppLogger.error(app, "update_download", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            if (userRequested && release.releaseUrl != null && !release.releaseUrl.isEmpty()) {
                try {
                    Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl));
                    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(open);
                } catch (Throwable ignored) {}
            }
            return -1L;
        }
    }

    static boolean isDownloaded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long id = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        return id > 0L && status(context, id) == DownloadManager.STATUS_SUCCESSFUL;
    }

    static long downloadedId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        long id = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_ID, -1L);
        return id > 0L && status(context, id) == DownloadManager.STATUS_SUCCESSFUL ? id : -1L;
    }

    static boolean openInstaller(Context context, long id) {
        if (context == null || id <= 0L) return false;
        AppSecurity.ApkVerification verification = AppSecurity.verifyDownloadedUpdate(context, id);
        if (!verification.ok) {
            AppLogger.warn(context, "update_verify_blocked", verification.message);
            try { Toast.makeText(context, verification.message, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
            return false;
        }
        AppLogger.info(context, "update_verify", verification.message);

        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String name = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File apk = dir == null || name == null || name.trim().isEmpty() ? null : new File(dir, name);
        if (apk == null || !apk.isFile()) {
            AppLogger.error(context, "update_install", "Verified APK path disappeared before installer launch");
            return false;
        }

        // Prefer our own narrowly scoped content provider. Some OEM DownloadManager
        // providers do not reliably hand their content URI to Package Installer.
        try {
            Uri uri = UpdateFileProvider.uriForFile(context, apk);
            Intent install = installerIntent(uri);
            if (launchInstaller(context, install, uri)) {
                prefs.edit()
                        .putBoolean(AppPrefs.UPDATE_INSTALL_PENDING, true)
                        .remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION)
                        .apply();
                AppLogger.info(context, "update_install", "app-provider | " + uri.getAuthority());
                return true;
            }
        } catch (Throwable t) {
            AppLogger.warn(context, "update_install_provider", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }

        // Compatibility fallback for devices whose package installer specifically
        // expects DownloadManager's downloaded-file URI.
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return false;
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) return false;
            Intent install = installerIntent(uri);
            if (!launchInstaller(context, install, uri)) return false;
            prefs.edit()
                    .putBoolean(AppPrefs.UPDATE_INSTALL_PENDING, true)
                    .remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION)
                    .apply();
            AppLogger.info(context, "update_install", "download-manager-fallback");
            return true;
        } catch (Throwable t) {
            AppLogger.error(context, "update_install", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
            return false;
        }
    }

    private static Intent installerIntent(Uri uri) {
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, APK_MIME);
        install.setClipData(ClipData.newRawUri("Harley's Clan Forum update", uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return install;
    }

    private static boolean launchInstaller(Context context, Intent install, Uri uri) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> handlers = pm.queryIntentActivities(install, PackageManager.MATCH_DEFAULT_ONLY);
            if (handlers == null || handlers.isEmpty()) {
                AppLogger.warn(context, "update_install", "No package installer resolved ACTION_VIEW for APK");
                return false;
            }
            for (ResolveInfo info : handlers) {
                try {
                    if (info != null && info.activityInfo != null && info.activityInfo.packageName != null) {
                        context.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                } catch (Throwable ignored) {}
            }
            context.startActivity(install);
            return true;
        } catch (Throwable t) {
            AppLogger.warn(context, "update_install_launch", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
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

        // Also remove stale installer APKs from the app-owned update directory.
        // This directory is only used by the updater, never by Android's installed package.
        try {
            java.io.File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file == null || !file.isFile()) continue;
                        String name = file.getName().toLowerCase(java.util.Locale.US);
                        if (name.endsWith(".apk") && name.startsWith("harleysclanforum-")) {
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
                .apply();
        AppLogger.info(app, "update_cleanup", "installer artifacts removed=" + removed);
        return removed;
    }

    static void cleanupIfCurrentVersionWasDownloaded(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
        String tag = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_TAG, "");
        if (tag != null && !tag.trim().isEmpty()) {
            String normalized = tag.trim();
            if (normalized.startsWith("v") || normalized.startsWith("V")) normalized = normalized.substring(1);
            if (BuildInfo.VERSION.equalsIgnoreCase(normalized)) {
                cleanupAfterSuccessfulUpdate(context);
            }
        }
        // Recovery path: even if the package-replaced broadcast or DownloadManager
        // cleanup was missed by an OEM, remove installer APKs from the updater-only
        // external-files folder once their version is no newer than the installed app.
        cleanupStaleUpdaterApks(context);
    }

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
            java.io.File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null || !dir.isDirectory()) return false;
            java.io.File[] files = dir.listFiles();
            if (files == null) return false;
            PackageManager pm = app.getPackageManager();
            for (java.io.File file : files) {
                if (file == null || !file.isFile()) continue;
                String name = file.getName().toLowerCase(java.util.Locale.US);
                if (!name.startsWith("harleysclanforum-") || !name.endsWith(".apk")) continue;
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
        if (removed) AppLogger.info(app, "update_cleanup_stale", "old updater APKs removed");
        return removed;
    }

    private static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return archiveVersionCode(info);
        } catch (Throwable ignored) {
            return BuildInfo.VERSION_CODE;
        }
    }

    private static long archiveVersionCode(PackageInfo info) {
        if (info == null) return 0L;
        if (android.os.Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        @SuppressWarnings("deprecation") int legacy = info.versionCode;
        return legacy;
    }

    private static String safeApkName(String name, String tag) {
        String value = name == null ? "" : name.trim();
        if (!value.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
            value = "HarleysClanForum-" + (tag == null ? "update" : tag.replaceAll("[^A-Za-z0-9._-]", "-")) + ".apk";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private AppUpdateDownloader() {}
}
