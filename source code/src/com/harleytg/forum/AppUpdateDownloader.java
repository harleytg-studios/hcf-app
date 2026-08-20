package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
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
    static com.harleytg.forum.dev.AppUpdateDownloader.ProgressSnapshot progress(android.content.Context r18, long r19) {
        /*
            r0 = r18
            if (r0 == 0) goto L99
            r1 = 0
            int r3 = (r19 > r1 ? 1 : (r19 == r1 ? 0 : -1))
            if (r3 > 0) goto Lc
            goto L99
        Lc:
            java.lang.String r3 = "download"
            java.lang.Object r0 = r0.getSystemService(r3)
            android.app.DownloadManager r0 = (android.app.DownloadManager) r0
            if (r0 != 0) goto L23
            com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot r0 = new com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot
            r7 = -1
            r9 = 0
            r4 = 0
            r5 = 0
            r3 = r0
            r3.<init>(r4, r5, r7, r9)
            return r0
        L23:
            r3 = 0
            android.app.DownloadManager$Query r4 = new android.app.DownloadManager$Query     // Catch: java.lang.Throwable -> L87
            r4.<init>()     // Catch: java.lang.Throwable -> L87
            r5 = 1
            long[] r5 = new long[r5]     // Catch: java.lang.Throwable -> L87
            r6 = 0
            r5[r6] = r19     // Catch: java.lang.Throwable -> L87
            android.app.DownloadManager$Query r4 = r4.setFilterById(r5)     // Catch: java.lang.Throwable -> L87
            android.database.Cursor r3 = r0.query(r4)     // Catch: java.lang.Throwable -> L87
            if (r3 == 0) goto L84
            boolean r0 = r3.moveToFirst()     // Catch: java.lang.Throwable -> L87
            if (r0 == 0) goto L84
            java.lang.String r0 = "status"
            int r0 = r3.getColumnIndex(r0)     // Catch: java.lang.Throwable -> L87
            java.lang.String r4 = "bytes_so_far"
            int r4 = r3.getColumnIndex(r4)     // Catch: java.lang.Throwable -> L87
            java.lang.String r5 = "total_size"
            int r5 = r3.getColumnIndex(r5)     // Catch: java.lang.Throwable -> L87
            java.lang.String r7 = "reason"
            int r7 = r3.getColumnIndex(r7)     // Catch: java.lang.Throwable -> L87
            if (r0 < 0) goto L5f
            int r0 = r3.getInt(r0)     // Catch: java.lang.Throwable -> L87
            r9 = r0
            goto L60
        L5f:
            r9 = r6
        L60:
            if (r4 < 0) goto L66
            long r1 = r3.getLong(r4)     // Catch: java.lang.Throwable -> L87
        L66:
            r10 = r1
            if (r5 < 0) goto L6e
            long r0 = r3.getLong(r5)     // Catch: java.lang.Throwable -> L87
            goto L70
        L6e:
            r0 = -1
        L70:
            r12 = r0
            if (r7 < 0) goto L77
            int r6 = r3.getInt(r7)     // Catch: java.lang.Throwable -> L87
        L77:
            r14 = r6
            com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot r0 = new com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot     // Catch: java.lang.Throwable -> L87
            r8 = r0
            r8.<init>(r9, r10, r12, r14)     // Catch: java.lang.Throwable -> L87
            if (r3 == 0) goto L83
            r3.close()
        L83:
            return r0
        L84:
            if (r3 == 0) goto L8c
            goto L89
        L87:
            if (r3 == 0) goto L8c
        L89:
            r3.close()
        L8c:
            com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot r0 = new com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot
            r8 = -1
            r10 = 0
            r5 = 0
            r6 = 0
            r4 = r0
            r4.<init>(r5, r6, r8, r10)
            return r0
        L99:
            com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot r0 = new com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot
            r15 = -1
            r17 = 0
            r12 = 0
            r13 = 0
            r11 = r0
            r11.<init>(r12, r13, r15, r17)
            return r0
        */
        throw new UnsupportedOperationException("Method not decompiled: com.harleytg.forum.dev.AppUpdateDownloader.progress(android.content.Context, long):com.harleytg.forum.dev.AppUpdateDownloader$ProgressSnapshot");
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
    static int status(android.content.Context r4, long r5) {
        /*
            r0 = 0
            int r0 = (r5 > r0 ? 1 : (r5 == r0 ? 0 : -1))
            r1 = 0
            if (r0 > 0) goto L8
            return r1
        L8:
            java.lang.String r0 = "download"
            java.lang.Object r4 = r4.getSystemService(r0)
            android.app.DownloadManager r4 = (android.app.DownloadManager) r4
            if (r4 != 0) goto L13
            return r1
        L13:
            r0 = 0
            android.app.DownloadManager$Query r2 = new android.app.DownloadManager$Query     // Catch: java.lang.Throwable -> L43
            r2.<init>()     // Catch: java.lang.Throwable -> L43
            r3 = 1
            long[] r3 = new long[r3]     // Catch: java.lang.Throwable -> L43
            r3[r1] = r5     // Catch: java.lang.Throwable -> L43
            android.app.DownloadManager$Query r5 = r2.setFilterById(r3)     // Catch: java.lang.Throwable -> L43
            android.database.Cursor r0 = r4.query(r5)     // Catch: java.lang.Throwable -> L43
            if (r0 == 0) goto L40
            boolean r4 = r0.moveToFirst()     // Catch: java.lang.Throwable -> L43
            if (r4 == 0) goto L40
            java.lang.String r4 = "status"
            int r4 = r0.getColumnIndex(r4)     // Catch: java.lang.Throwable -> L43
            if (r4 < 0) goto L3a
            int r1 = r0.getInt(r4)     // Catch: java.lang.Throwable -> L43
        L3a:
            if (r0 == 0) goto L3f
            r0.close()
        L3f:
            return r1
        L40:
            if (r0 == 0) goto L48
            goto L45
        L43:
            if (r0 == 0) goto L48
        L45:
            r0.close()
        L48:
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.harleytg.forum.dev.AppUpdateDownloader.status(android.content.Context, long):int");
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
    static boolean cleanupAfterSuccessfulUpdate(android.content.Context r10) {
        /*
            r0 = 0
            if (r10 != 0) goto L4
            return r0
        L4:
            android.content.Context r10 = r10.getApplicationContext()
            java.lang.String r1 = "hcf_app"
            android.content.SharedPreferences r1 = r10.getSharedPreferences(r1, r0)
            r2 = -1
            java.lang.String r4 = "update_download_id"
            long r2 = r1.getLong(r4, r2)
            r5 = 0
            int r5 = (r2 > r5 ? 1 : (r2 == r5 ? 0 : -1))
            r6 = 1
            if (r5 <= 0) goto L3e
            java.lang.String r5 = "download"
            java.lang.Object r5 = r10.getSystemService(r5)     // Catch: java.lang.Throwable -> L30
            android.app.DownloadManager r5 = (android.app.DownloadManager) r5     // Catch: java.lang.Throwable -> L30
            if (r5 == 0) goto L3e
            long[] r7 = new long[r6]     // Catch: java.lang.Throwable -> L30
            r7[r0] = r2     // Catch: java.lang.Throwable -> L30
            r5.remove(r7)     // Catch: java.lang.Throwable -> L30
            r2 = r6
            goto L3f
        L30:
            r2 = move-exception
            java.lang.Class r2 = r2.getClass()
            java.lang.String r2 = r2.getSimpleName()
            java.lang.String r3 = "update_cleanup_dm"
            com.harleytg.forum.dev.AppLogger.warn(r10, r3, r2)
        L3e:
            r2 = r0
        L3f:
            java.lang.String r3 = android.os.Environment.DIRECTORY_DOWNLOADS     // Catch: java.lang.Throwable -> L85
            java.io.File r3 = r10.getExternalFilesDir(r3)     // Catch: java.lang.Throwable -> L85
            if (r3 == 0) goto L93
            boolean r5 = r3.isDirectory()     // Catch: java.lang.Throwable -> L85
            if (r5 == 0) goto L93
            java.io.File[] r3 = r3.listFiles()     // Catch: java.lang.Throwable -> L85
            if (r3 == 0) goto L93
            int r5 = r3.length     // Catch: java.lang.Throwable -> L85
        L54:
            if (r0 >= r5) goto L93
            r7 = r3[r0]     // Catch: java.lang.Throwable -> L85
            if (r7 == 0) goto L82
            boolean r8 = r7.isFile()     // Catch: java.lang.Throwable -> L85
            if (r8 != 0) goto L61
            goto L82
        L61:
            java.lang.String r8 = r7.getName()     // Catch: java.lang.Throwable -> L85
            java.util.Locale r9 = java.util.Locale.US     // Catch: java.lang.Throwable -> L85
            java.lang.String r8 = r8.toLowerCase(r9)     // Catch: java.lang.Throwable -> L85
            java.lang.String r9 = ".apk"
            boolean r9 = r8.endsWith(r9)     // Catch: java.lang.Throwable -> L85
            if (r9 == 0) goto L82
            java.lang.String r9 = "harleysclanforum-"
            boolean r8 = r8.startsWith(r9)     // Catch: java.lang.Throwable -> L85
            if (r8 == 0) goto L82
            boolean r7 = r7.delete()     // Catch: java.lang.Throwable -> L82
            if (r7 == 0) goto L82
            r2 = r6
        L82:
            int r0 = r0 + 1
            goto L54
        L85:
            r0 = move-exception
            java.lang.Class r0 = r0.getClass()
            java.lang.String r0 = r0.getSimpleName()
            java.lang.String r3 = "update_cleanup_files"
            com.harleytg.forum.dev.AppLogger.warn(r10, r3, r0)
        L93:
            android.content.SharedPreferences$Editor r0 = r1.edit()
            android.content.SharedPreferences$Editor r0 = r0.remove(r4)
            java.lang.String r1 = "update_download_tag"
            android.content.SharedPreferences$Editor r0 = r0.remove(r1)
            java.lang.String r1 = "update_download_name"
            android.content.SharedPreferences$Editor r0 = r0.remove(r1)
            java.lang.String r1 = "update_install_pending"
            android.content.SharedPreferences$Editor r0 = r0.remove(r1)
            java.lang.String r1 = "update_resume_after_permission"
            android.content.SharedPreferences$Editor r0 = r0.remove(r1)
            r0.apply()
            java.lang.StringBuilder r0 = new java.lang.StringBuilder
            java.lang.String r1 = "installer artifacts removed="
            r0.<init>(r1)
            r0.append(r2)
            java.lang.String r0 = r0.toString()
            java.lang.String r1 = "update_cleanup"
            com.harleytg.forum.dev.AppLogger.info(r10, r1, r0)
            return r2
        */
        throw new UnsupportedOperationException("Method not decompiled: com.harleytg.forum.dev.AppUpdateDownloader.cleanupAfterSuccessfulUpdate(android.content.Context):boolean");
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
    static boolean cleanupStaleUpdaterApks(android.content.Context r18) {
        /*
            Method dump skipped, instructions count: 216
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.harleytg.forum.dev.AppUpdateDownloader.cleanupStaleUpdaterApks(android.content.Context):boolean");
    }

    private static long installedVersionCode(Context context) {
        try {
            return archiveVersionCode(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        } catch (Throwable unused) {
            return 10000071L;
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
