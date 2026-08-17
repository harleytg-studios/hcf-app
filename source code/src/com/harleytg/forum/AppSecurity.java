package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

final class AppSecurity {
    static final class ApkVerification {
        final boolean ok;
        final String message;

        ApkVerification(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    static boolean canInstallUpdates(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 26) return true;
        try { return context.getPackageManager().canRequestPackageInstalls(); }
        catch (Throwable ignored) { return false; }
    }

    static boolean isTrustedReleaseDownload(String raw) {
        try {
            Uri uri = Uri.parse(raw == null ? "" : raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (!"github.com".equalsIgnoreCase(uri.getHost())) return false;
            String path = uri.getPath();
            if (path == null) return false;
            String prefix = "/" + BuildInfo.UPDATE_REPOSITORY + "/releases/download/";
            return path.toLowerCase(Locale.US).startsWith(prefix.toLowerCase(Locale.US))
                    && path.toLowerCase(Locale.US).endsWith(".apk");
        } catch (Throwable ignored) {
            return false;
        }
    }

    static ApkVerification verifyDownloadedUpdate(Context context, long downloadId) {
        if (context == null || downloadId <= 0L) return new ApkVerification(false, "Update file is unavailable.");
        try {
            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
            String name = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
            if (name == null || name.trim().isEmpty()) return new ApkVerification(false, "Update filename is missing.");
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File apk = dir == null ? null : new File(dir, name);
            if (apk == null || !apk.isFile()) return new ApkVerification(false, "Downloaded APK could not be found.");

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null && AppUpdateDownloader.status(context, downloadId) != DownloadManager.STATUS_SUCCESSFUL) {
                return new ApkVerification(false, "Update download is not complete.");
            }

            PackageManager pm = context.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            if (candidate == null || installed == null) return new ApkVerification(false, "APK package metadata could not be verified.");
            if (!context.getPackageName().equals(candidate.packageName)) {
                return new ApkVerification(false, "Blocked update: APK package name does not match this app.");
            }

            long candidateCode = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
            long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            if (candidateCode <= installedCode) {
                return new ApkVerification(false, "Blocked update: APK is not newer than the installed version.");
            }

            String expectedSigner = signingDigest(installed);
            String candidateSigner = signingDigest(candidate);
            if (expectedSigner.isEmpty() || candidateSigner.isEmpty() || !expectedSigner.equals(candidateSigner)) {
                return new ApkVerification(false, "Blocked update: signing certificate does not match the installed app.");
            }
            return new ApkVerification(true, "Verified package, version and signing certificate.");
        } catch (Throwable t) {
            return new ApkVerification(false, "Update security check failed: " + t.getClass().getSimpleName());
        }
    }

    static String securitySummary(Context context) {
        String install = canInstallUpdates(context) ? "Allowed" : "Needs approval";
        return "HTTPS only • SSL errors blocked • mixed HTTP blocked\n"
                + "Third-party cookies blocked • file URL access blocked\n"
                + "WebView debugging off • app backup disabled\n"
                + "Update APK signature verification on • installer permission: " + install;
    }

    private static String signingDigest(PackageInfo info) throws Exception {
        byte[] cert = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return "";
            android.content.pm.Signature[] signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            if (signatures != null && signatures.length > 0) cert = signatures[0].toByteArray();
        } else if (info.signatures != null && info.signatures.length > 0) {
            cert = info.signatures[0].toByteArray();
        }
        if (cert == null) return "";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private AppSecurity() {}
}
