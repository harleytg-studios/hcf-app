package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

/* loaded from: classes.dex */
final class AppSecurity {

    static final class ApkVerification {
        final String message;
        final boolean ok;

        ApkVerification(boolean z, String str) {
            this.ok = z;
            this.message = str;
        }
    }

    static boolean canInstallUpdates(Context context) {
        if (context == null) {
            return true;
        }
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable unused) {
            return false;
        }
    }

    static boolean isTrustedReleaseDownload(String str) {
        String trim;
        String path;
        if (str == null) {
            trim = "";
        } else {
            try {
                trim = str.trim();
            } catch (Throwable unused) {
                return false;
            }
        }
        Uri parse = Uri.parse(trim);
        if ("https".equalsIgnoreCase(parse.getScheme()) && "github.com".equalsIgnoreCase(parse.getHost()) && (path = parse.getPath()) != null && path.toLowerCase(Locale.US).startsWith("/markhitchk/hcf-app/releases/download/".toLowerCase(Locale.US))) {
            return path.toLowerCase(Locale.US).endsWith(".apk");
        }
        return false;
    }

    static ApkVerification verifyDownloadedUpdate(Context context, long j) {
        if (context == null || j <= 0) {
            return new ApkVerification(false, "Update file is unavailable.");
        }
        try {
            String string = context.getSharedPreferences("hcf_app", 0).getString("update_download_name", "");
            if (string != null && !string.trim().isEmpty()) {
                File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File file = externalFilesDir == null ? null : new File(externalFilesDir, string);
                if (file != null && file.isFile()) {
                    if (((DownloadManager) context.getSystemService("download")) != null && AppUpdateDownloader.status(context, j) != 8) {
                        return new ApkVerification(false, "Update download is not complete.");
                    }
                    PackageManager packageManager = context.getPackageManager();
                    int i = Build.VERSION.SDK_INT >= 28 ? 134217728 : 64;
                    PackageInfo packageArchiveInfo = packageManager.getPackageArchiveInfo(file.getAbsolutePath(), i);
                    PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), i);
                    if (packageArchiveInfo != null && packageInfo != null) {
                        if (!context.getPackageName().equals(packageArchiveInfo.packageName)) {
                            return new ApkVerification(false, "Blocked update: APK package name does not match this app.");
                        }
                        if ((Build.VERSION.SDK_INT >= 28 ? packageArchiveInfo.getLongVersionCode() : packageArchiveInfo.versionCode) <= (Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode)) {
                            return new ApkVerification(false, "Blocked update: APK is not newer than the installed version.");
                        }
                        String signingDigest = signingDigest(packageInfo);
                        String signingDigest2 = signingDigest(packageArchiveInfo);
                        if (!signingDigest.isEmpty() && !signingDigest2.isEmpty() && signingDigest.equals(signingDigest2)) {
                            return new ApkVerification(true, "Verified package, version and signing certificate.");
                        }
                        return new ApkVerification(false, "Blocked update: signing certificate does not match the installed app.");
                    }
                    return new ApkVerification(false, "APK package metadata could not be verified.");
                }
                return new ApkVerification(false, "Downloaded APK could not be found.");
            }
            return new ApkVerification(false, "Update filename is missing.");
        } catch (Throwable th) {
            return new ApkVerification(false, "Update security check failed: " + th.getClass().getSimpleName());
        }
    }

    static String securitySummary(Context context) {
        return "HTTPS only • SSL errors blocked • mixed HTTP blocked\nThird-party cookies blocked • file URL access blocked\nWebView debugging off • app backup disabled\nUpdate APK signature verification on • installer permission: ".concat(canInstallUpdates(context) ? "Allowed" : "Needs approval");
    }

    private static String signingDigest(PackageInfo packageInfo) throws Exception {
        Signature[] signingCertificateHistory;
        byte[] bArr = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (packageInfo.signingInfo == null) {
                return "";
            }
            if (packageInfo.signingInfo.hasMultipleSigners()) {
                signingCertificateHistory = packageInfo.signingInfo.getApkContentsSigners();
            } else {
                signingCertificateHistory = packageInfo.signingInfo.getSigningCertificateHistory();
            }
            if (signingCertificateHistory != null && signingCertificateHistory.length > 0) {
                bArr = signingCertificateHistory[0].toByteArray();
            }
        } else if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
            bArr = packageInfo.signatures[0].toByteArray();
        }
        if (bArr == null) {
            return "";
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bArr);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", Integer.valueOf(b & 255)));
        }
        return sb.toString();
    }

    private AppSecurity() {
    }
}
