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
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
            android.content.SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
            String string = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_NAME, "");
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
                        long candidateVersion = Build.VERSION.SDK_INT >= 28 ? packageArchiveInfo.getLongVersionCode() : packageArchiveInfo.versionCode;
                        long installedVersion = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                        long expectedVersion = prefs.getLong(AppPrefs.UPDATE_DOWNLOAD_VERSION_CODE, -1L);
                        String expectedSha256 = prefs.getString(AppPrefs.UPDATE_DOWNLOAD_SHA256, "");
                        if (candidateVersion != expectedVersion) {
                            return new ApkVerification(false, "Blocked update: APK versionCode changed after the release check.");
                        }
                        if (!isSha256(expectedSha256)) {
                            return new ApkVerification(false, "Blocked update: expected APK SHA-256 is missing.");
                        }
                        String downloadedSha256 = fileSha256(file);
                        if (!expectedSha256.equalsIgnoreCase(downloadedSha256)) {
                            return new ApkVerification(false, "Blocked update: APK SHA-256 does not match the checked release.");
                        }
                        if (candidateVersion < installedVersion) {
                            return new ApkVerification(false, "Blocked update: APK versionCode is older than the installed version.");
                        }
                        if (candidateVersion == installedVersion) {
                            String installedSha256 = installedApkSha256(context);
                            if (!isSha256(installedSha256) || installedSha256.equalsIgnoreCase(downloadedSha256)) {
                                return new ApkVerification(false, "Blocked update: this exact APK is already installed.");
                            }
                        }
                        if (signaturesCompatible(packageInfo, packageArchiveInfo)) {
                            String mode = candidateVersion == installedVersion ? "same-version hash revision" : "newer versionCode";
                            return new ApkVerification(true, "Verified package, SHA-256, " + mode + " and signing certificate lineage.");
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

    private static boolean signaturesCompatible(PackageInfo installed, PackageInfo candidate) throws Exception {
        Set<String> installedCurrent = currentSigningDigests(installed);
        Set<String> candidateCurrent = currentSigningDigests(candidate);
        if (installedCurrent.isEmpty() || candidateCurrent.isEmpty()) return false;
        if (installedCurrent.equals(candidateCurrent)) return true;

        if (Build.VERSION.SDK_INT >= 28) {
            boolean installedMulti = installed.signingInfo != null && installed.signingInfo.hasMultipleSigners();
            boolean candidateMulti = candidate.signingInfo != null && candidate.signingInfo.hasMultipleSigners();
            if (installedMulti || candidateMulti) return false;
        } else {
            return false;
        }

        Set<String> candidateHistory = signingHistoryDigests(candidate);
        return candidateHistory.containsAll(installedCurrent);
    }

    static String installedApkSha256(Context context) throws Exception {
        if (context == null || context.getApplicationInfo() == null) return "";
        String sourceDir = context.getApplicationInfo().sourceDir;
        return sourceDir == null || sourceDir.trim().isEmpty() ? "" : fileSha256(new File(sourceDir));
    }

    static String fileSha256(File file) throws Exception {
        if (file == null || !file.isFile()) return "";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(Locale.US, "%02x", Integer.valueOf(value & 255)));
        return out.toString();
    }

    static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static Set<String> currentSigningDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        addDigests(out, signatures);
        return out;
    }

    private static Set<String> signingHistoryDigests(PackageInfo info) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (info == null) return out;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return out;
            Signature[] signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
            addDigests(out, signatures);
        } else {
            addDigests(out, info.signatures);
        }
        return out;
    }

    private static void addDigests(Set<String> out, Signature[] signatures) throws Exception {
        if (signatures == null) return;
        for (Signature signature : signatures) {
            if (signature == null) continue;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte value : digest) sb.append(String.format(Locale.US, "%02x", Integer.valueOf(value & 255)));
            out.add(sb.toString());
        }
    }

    private AppSecurity() {
    }
}
