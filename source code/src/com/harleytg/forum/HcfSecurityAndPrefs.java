package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class HcfSecurityAndPrefs {
    private HcfSecurityAndPrefs() {}
}

// ---- AppSecurity.java ----
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


// ---- LinkSafety.java ----
/* loaded from: classes.dex */
final class LinkSafety {
    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6ISH = Pattern.compile("^[0-9a-fA-F:]+$");

    enum Status {
        OFFICIAL("🛡️", "Official"),
        EXTERNAL("🔗", "External"),
        SUSPICIOUS("⚠️", "Suspicious"),
        BLOCKED("⛔", "Blocked");

        final String icon;
        final String label;

        Status(String str, String str2) {
            this.icon = str;
            this.label = str2;
        }

        String display() {
            return this.icon + " " + this.label;
        }
    }

    static final class Result {
        final String host;
        final String reason;
        final Status status;

        Result(Status status, String str, String str2) {
            this.status = status;
            this.host = str == null ? "" : str;
            this.reason = str2 == null ? "" : str2;
        }
    }

    private LinkSafety() {
    }

    static Result classify(Uri uri) {
        if (uri == null) {
            return blocked("", "Invalid link");
        }
        if (ForumUrlRouter.isForumUrl(uri)) {
            return new Result(Status.OFFICIAL, safeHost(uri), "Harley's Clan Forum trusted domain");
        }
        String lower = lower(uri.getScheme());
        if (!"http".equals(lower) && !"https".equals(lower)) {
            return blocked(safeHost(uri), "Unsupported link type");
        }
        String canonicalHost = canonicalHost(uri.getHost());
        if (canonicalHost.isEmpty()) {
            return blocked("", "Missing website domain");
        }
        if (!"https".equals(lower)) {
            return suspicious(canonicalHost, "This website is not using HTTPS");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            return suspicious(canonicalHost, "The address contains embedded sign-in information");
        }
        if (canonicalHost.startsWith("xn--") || canonicalHost.contains(".xn--")) {
            return suspicious(canonicalHost, "The domain uses an encoded international name");
        }
        if (isIpAddress(canonicalHost)) {
            return suspicious(canonicalHost, "The link uses a direct IP address instead of a normal domain");
        }
        if (looksLikeForumImpersonation(canonicalHost)) {
            return suspicious(canonicalHost, "This domain resembles Harley's Clan Forum but is not an official domain");
        }
        return new Result(Status.EXTERNAL, canonicalHost, "External website");
    }

    static String canonicalHost(String str) {
        if (str == null) {
            return "";
        }
        String lowerCase = str.trim().toLowerCase(Locale.US);
        if (lowerCase.endsWith(".")) {
            lowerCase = lowerCase.substring(0, lowerCase.length() - 1);
        }
        try {
            return IDN.toASCII(lowerCase, 1).toLowerCase(Locale.US);
        } catch (Throwable unused) {
            return lowerCase;
        }
    }

    private static boolean looksLikeForumImpersonation(String str) {
        if (ForumUrlRouter.isForumHost(str)) {
            return false;
        }
        String replace = str.replace("-", "").replace("_", "");
        return replace.contains("harleytg") || replace.contains("harleysclan") || replace.contains("harleyclan") || (replace.contains("harley") && replace.contains("forum"));
    }

    private static boolean isIpAddress(String str) {
        if (IPV4.matcher(str).matches()) {
            return true;
        }
        return str.indexOf(58) >= 0 && IPV6ISH.matcher(str).matches();
    }

    private static Result suspicious(String str, String str2) {
        return new Result(Status.SUSPICIOUS, str, str2);
    }

    private static Result blocked(String str, String str2) {
        return new Result(Status.BLOCKED, str, str2);
    }

    private static String safeHost(Uri uri) {
        return canonicalHost(uri == null ? null : uri.getHost());
    }

    private static String lower(String str) {
        return str == null ? "" : str.trim().toLowerCase(Locale.US);
    }
}


// ---- AppPrefs.java ----
/* loaded from: classes.dex */
final class AppPrefs {
    static final String ACTIVE_HOST = "active_host";
    static final String APP_HAS_LAUNCHED = "app_has_launched";
    static final String APP_THEME = "app_theme";
    static final String AUTO_FAILOVER = "auto_failover";
    static final String BACKGROUND_NOTIFICATION_SYNC = "background_notification_sync";
    static final String COMPACT_HEADER = "compact_header";
    static final String DELIVERED_NOTIFICATION_IDS = "delivered_notification_ids";
    static final String EXTERNAL_LINKS = "external_links";
    static final String FALLBACK_UNTIL = "fallback_until";
    static final String FILE = "hcf_app";
    static final String FIREBASE_CONFIG_CACHE = "firebase_config_cache";
    static final String FIREBASE_CONFIG_SOURCE = "firebase_config_source";
    static final String FIREBASE_CONFIG_URL = "firebase_config_url";
    static final String FORUM_AUTO_THEME = "forum_auto_theme";
    static final String FORUM_AUTO_THEME_UPDATED_AT = "forum_auto_theme_updated_at";
    static final String IDENTITY_ADMIN = "identity_admin";
    static final String IDENTITY_AVATAR_URL = "identity_avatar_url";
    static final String IDENTITY_COMMENT_COUNT = "identity_comment_count";
    static final String IDENTITY_CONNECTIONS = "identity_connections";
    static final String IDENTITY_DISCUSSION_COUNT = "identity_discussion_count";
    static final String IDENTITY_DISPLAY_NAME = "identity_display_name";
    static final String IDENTITY_EMAIL = "identity_email";
    static final String IDENTITY_EMAIL_CONFIRMED = "identity_email_confirmed";
    static final String IDENTITY_GROUPS = "identity_groups";
    static final String IDENTITY_HOST = "identity_host";
    static final String IDENTITY_JOIN_TIME = "identity_join_time";
    static final String IDENTITY_LAST_SEEN_AT = "identity_last_seen_at";
    static final String IDENTITY_LOGGED_IN = "identity_logged_in";
    static final String IDENTITY_NEW_NOTIFICATIONS = "identity_new_notifications";
    static final String IDENTITY_SECURITY_ACTIVE_SESSION_COUNT = "identity_security_active_session_count";
    static final String IDENTITY_SECURITY_EMAIL_CONTROLS = "identity_security_email_controls";
    static final String IDENTITY_SECURITY_HOST = "identity_security_host";
    static final String IDENTITY_SECURITY_PASSWORD_CONTROLS = "identity_security_password_controls";
    static final String IDENTITY_SECURITY_PATH = "identity_security_path";
    static final String IDENTITY_SECURITY_PROVIDERS = "identity_security_providers";
    static final String IDENTITY_SECURITY_SEEN = "identity_security_seen";
    static final String IDENTITY_SECURITY_SESSION_COUNT = "identity_security_session_count";
    static final String IDENTITY_SECURITY_SYNCED_AT = "identity_security_synced_at";
    static final String IDENTITY_SECURITY_TWO_FACTOR_CONTROLS = "identity_security_two_factor_controls";
    static final String IDENTITY_SLUG = "identity_slug";
    static final String IDENTITY_SYNCED_AT = "identity_synced_at";
    static final String IDENTITY_UNREAD_NOTIFICATIONS = "identity_unread_notifications";
    static final String IDENTITY_USERNAME = "identity_username";
    static final String IDENTITY_USER_ID = "identity_user_id";
    static final String INSTALL_PERMISSION_PROMPTED = "install_permission_prompted";
    static final String LAST_MAIN_PAUSED_AT = "last_main_paused_at";
    static final String LAST_NOTIFICATION_COUNT = "last_notification_count";
    static final String LAST_RECOVERABLE_URL = "last_recoverable_url";
    static final String LAST_SEEN_WHATS_NEW_VERSION = "last_seen_whats_new_version";
    static final String LIVE_FORUM_UPDATES = "live_forum_updates";
    static final String NATIVE_ACCENT = "native_accent";
    static final String NOTIFICATIONS_ENABLED = "notifications_enabled";
    static final String NOTIFICATION_LAST_COUNT_CHANGE_AT = "notification_last_count_change_at";
    static final String NOTIFICATION_LAST_SYNC_AT = "notification_last_sync_at";
    static final String NOTIFICATION_LAST_SYNC_LATENCY_MS = "notification_last_sync_latency_ms";
    static final String NOTIFICATION_LAST_SYNC_STATUS = "notification_last_sync_status";
    static final String NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked";
    static final String NOTIFICATION_PERMISSION_PROMPT_VERSION = "notification_permission_prompt_version";
    static final String PERFORMANCE_MODE = "performance_mode";
    static final String PERFORMANCE_PROFILE = "performance_profile";
    static final String PERMISSION_ONBOARDING_DONE = "permission_onboarding_done";
    static final String RENDERER_RECOVERY_COUNT = "renderer_recovery_count";
    static final String SAFE_LINKS_SEEN_DOMAINS = "safe_links_seen_domains";
    static final String SESSION_USER_ID = "session_user_id";
    static final String SETUP_COMPLETED = "setup_completed";
    static final String SETUP_SEEN = "setup_seen";
    static final String SETUP_VERSION = "setup_version";
    static final String WELCOME_SEEN = "welcome_seen";
    static final String WELCOME_VERSION = "welcome_version";
    static final String SHOW_BOTTOM_NAV = "show_bottom_nav";
    static final String SHOW_STARTUP_SCREEN = "show_startup_screen";
    static final String SHOW_URL_BAR = "show_url_bar";
    static final String SILENCE_BACKGROUND_SERVICE_NOTIFICATION = "silence_background_service_notification";
    static final String TELEMETRY_ASK_BEFORE_CRASH_REPORT = "telemetry_ask_before_crash_report";
    static final String TELEMETRY_AUTO_CRASH_REPORTS = "telemetry_auto_crash_reports";
    static final String TELEMETRY_AUTO_ERROR_REPORTS = "telemetry_auto_error_reports";
    static final String TELEMETRY_BREADCRUMBS = "telemetry_breadcrumbs";
    static final String TELEMETRY_ENABLED = "telemetry_enabled";
    static final String TELEMETRY_INCLUDE_DEVICE_MODEL = "telemetry_include_device_model";
    static final String TELEMETRY_INCLUDE_EMAIL = "telemetry_include_email";
    static final String TELEMETRY_INCLUDE_IDENTITY = "telemetry_include_identity";
    static final String TELEMETRY_INCLUDE_ROUTE = "telemetry_include_route";
    static final String TELEMETRY_LAST_HEARTBEAT = "telemetry_last_heartbeat";
    static final String TELEMETRY_LAST_RESULT = "telemetry_last_result";
    static final String TELEMETRY_LAST_ROUTE = "telemetry_last_route";
    static final String TELEMETRY_LEVEL = "telemetry_level";
    static final String TELEMETRY_PENDING_CRASH_ID = "telemetry_pending_crash_id";
    static final String TELEMETRY_REPORT_HISTORY = "telemetry_report_history";
    static final String UI_REVAMP_VERSION = "ui_revamp_version";
    static final String UPDATE_AUTO_CHECK = "update_auto_check";
    static final String UPDATE_AUTO_DOWNLOAD = "update_auto_download";
    static final String UPDATE_AUTO_INSTALL = "update_auto_install";
    static final String UPDATE_CHANNEL = "update_channel";
    static final String UPDATE_DOWNLOAD_ID = "update_download_id";
    static final String UPDATE_DOWNLOAD_LABEL = "update_download_label";
    static final String UPDATE_DOWNLOAD_NAME = "update_download_name";
    static final String UPDATE_DOWNLOAD_SHA256 = "update_download_sha256";
    static final String UPDATE_DOWNLOAD_TAG = "update_download_tag";
    static final String UPDATE_DOWNLOAD_VERSION_CODE = "update_download_version_code";
    static final String UPDATE_INSTALL_PENDING = "update_install_pending";
    static final String UPDATE_LAST_AVAILABLE_TAG = "update_last_available_tag";
    static final String UPDATE_LAST_CHECK = "update_last_check";
    static final String UPDATE_RESUME_AFTER_PERMISSION = "update_resume_after_permission";

    private AppPrefs() {
    }
}


// ---- UiPreferences.java ----
/* loaded from: classes.dex */
final class UiPreferences {
    private static final int CURRENT_REVAMP = 4;

    static void migrate(Context context) {
        int i;
        if (context == null) {
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        sanitizePreferenceTypes(sharedPreferences);
        try {
            i = sharedPreferences.getInt("ui_revamp_version", 0);
        } catch (Throwable unused) {
            sharedPreferences.edit().remove("ui_revamp_version").apply();
            i = 0;
        }
        if (i >= CURRENT_REVAMP) {
            return;
        }
        sharedPreferences.edit().putBoolean("update_auto_download", false).putBoolean("show_url_bar", true).putInt("ui_revamp_version", CURRENT_REVAMP).apply();
    }

    private static void sanitizePreferenceTypes(SharedPreferences sharedPreferences) {
        try {
            Map<String, ?> all = sharedPreferences.getAll();
            SharedPreferences.Editor edit = sharedPreferences.edit();
            String[] strArr = {"notifications_enabled", "background_notification_sync", "auto_failover", "external_links", "show_url_bar", "compact_header", "show_bottom_nav", "show_startup_screen", "live_forum_updates", "performance_mode", "notification_permission_asked", "permission_onboarding_done", "install_permission_prompted", "app_has_launched", "update_auto_check", "update_auto_download", "update_install_pending", "update_resume_after_permission", "telemetry_enabled", "telemetry_auto_crash_reports", "telemetry_ask_before_crash_report", "telemetry_auto_error_reports", "telemetry_include_identity", "telemetry_include_email", "telemetry_include_device_model", "telemetry_include_route", "identity_logged_in", "identity_email_confirmed", "identity_admin", "identity_security_seen", "identity_security_password_controls", "identity_security_email_controls", "identity_security_two_factor_controls"};
            boolean z = false;
            for (int i = 0; i < strArr.length; i++) {
                z |= removeIfWrongType(all, edit, strArr[i], Boolean.class);
            }
            String[] strArr2 = {"safe_links_seen_domains", "app_theme", "performance_profile", "native_accent", "last_recoverable_url", "last_seen_whats_new_version", "session_user_id", "active_host", "delivered_notification_ids", "notification_last_sync_status", "firebase_config_url", "firebase_config_cache", "firebase_config_source", "update_channel", "update_last_available_tag", "update_download_tag", "update_download_label", "update_download_name", "update_download_sha256", "telemetry_level", "telemetry_last_route", "telemetry_breadcrumbs", "telemetry_report_history", "telemetry_pending_crash_id", "telemetry_last_result", "identity_user_id", "identity_username", "identity_slug", "identity_display_name", "identity_email", "identity_avatar_url", "identity_groups", "identity_connections", "identity_join_time", "identity_last_seen_at", "identity_host", "identity_security_providers", "identity_security_host", "identity_security_path"};
            for (int i2 = 0; i2 < strArr2.length; i2++) {
                z |= removeIfWrongType(all, edit, strArr2[i2], String.class);
            }
            String[] strArr3 = {"fallback_until", "last_main_paused_at", "notification_last_sync_at", "notification_last_sync_latency_ms", "update_last_check", "update_download_id", "update_download_version_code", "telemetry_last_heartbeat", "identity_synced_at", "identity_security_synced_at"};
            for (int i3 = 0; i3 < strArr3.length; i3++) {
                z |= removeIfWrongType(all, edit, strArr3[i3], Long.class);
            }
            String[] strArr4 = {"ui_revamp_version", "notification_permission_prompt_version", "last_notification_count", "identity_unread_notifications", "identity_new_notifications", "identity_discussion_count", "identity_comment_count", "identity_security_session_count", "identity_security_active_session_count"};
            for (int i4 = 0; i4 < strArr4.length; i4++) {
                z |= removeIfWrongType(all, edit, strArr4[i4], Integer.class);
            }
            if (z) {
                edit.apply();
            }
        } catch (Throwable unused) {
        }
    }

    private static boolean removeIfWrongType(Map<String, ?> map, SharedPreferences.Editor editor, String str, Class<?> cls) {
        Object obj;
        if (!map.containsKey(str) || (obj = map.get(str)) == null || cls.isInstance(obj)) {
            return false;
        }
        editor.remove(str);
        return true;
    }

    private UiPreferences() {
    }
}


// ---- AppLogger.java ----
/* loaded from: classes.dex */
final class AppLogger {
    private static final Object LOCK = new Object();
    private static final String LOG_DIR = "app-logs";
    private static final String LOG_FILE = "hcf-app.log";
    private static final long MAX_LOG_BYTES = 524288;
    private static final String OLD_LOG_FILE = "hcf-app.previous.log";

    static void info(Context context, String str, String str2) {
        write(context, "INFO", str, str2);
    }

    static void warn(Context context, String str, String str2) {
        write(context, "WARN", str, str2);
    }

    static void error(Context context, String str, String str2) {
        write(context, "ERROR", str, str2);
    }

    static void crash(Context context, Throwable th) {
        StringWriter stringWriter = new StringWriter();
        th.printStackTrace(new PrintWriter(stringWriter));
        write(context, "CRASH", "uncaught_exception", stringWriter.toString());
    }

    static String safeUrl(String str) {
        String str2 = "";
        if (str == null || str.isEmpty()) {
            return "";
        }
        try {
            Uri parse = Uri.parse(str);
            String scheme = parse.getScheme() == null ? "" : parse.getScheme();
            if (parse.getHost() != null) {
                str2 = parse.getHost();
            }
            return scheme + "://" + str2 + (parse.getPath() == null ? "/" : parse.getPath());
        } catch (Throwable unused) {
            return "[unparseable-url]";
        }
    }

    static String readAll(Context context) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            appendFile(sb, oldFile(context));
            appendFile(sb, logFile(context));
            if (sb.length() == 0) {
                return "No app logs yet.";
            }
            return HcfSupportSanitizer.sanitize(sb.toString());
        }
    }

    static String readRecent(Context context, int i) {
        int max = Math.max(4096, i);
        synchronized (LOCK) {
            try {
                File oldFile = oldFile(context);
                File logFile = logFile(context);
                long length = (oldFile.exists() ? oldFile.length() : 0L) + (logFile.exists() ? logFile.length() : 0L);
                StringBuilder sb = new StringBuilder(Math.min(max + 256, 200000));
                String readTail = readTail(logFile, Math.min(max, Math.max(4096, (max * 3) / 4)));
                int max2 = Math.max(0, max - readTail.length());
                String readTail2 = max2 > 0 ? readTail(oldFile, max2) : "";
                if (length > max) {
                    sb.append("Older log entries omitted from the on-screen viewer. Export logs to save the complete local history.\n\n");
                }
                if (!readTail2.isEmpty()) {
                    sb.append(readTail2);
                }
                if (!readTail.isEmpty()) {
                    sb.append(readTail);
                }
                if (sb.length() == 0) {
                    return "No app logs yet.";
                }
                int i2 = max + 220;
                if (sb.length() > i2) {
                    return HcfSupportSanitizer.sanitize(sb.substring(sb.length() - i2));
                }
                return HcfSupportSanitizer.sanitize(sb.toString());
            } catch (Throwable unused) {
                return "App logs are temporarily unavailable.";
            }
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            try {
                oldFile(context).delete();
            } catch (Throwable unused) {
            }
            try {
                logFile(context).delete();
            } catch (Throwable unused2) {
            }
        }
    }

    private static void write(Context context, String str, String str2, String str3) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                File logFile = logFile(context);
                if (logFile.length() >= MAX_LOG_BYTES) {
                    rotate(context);
                }
                String format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String clean = clean(str2, 120);
                String clean2 = clean(str3, 12000);
                TelemetryService.recordBreadcrumb(context, clean, clean2);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8);
                outputStreamWriter.write(format + " [" + str + "] " + clean);
                if (!clean2.isEmpty()) {
                    outputStreamWriter.write(" | " + clean2);
                }
                outputStreamWriter.write("\n");
                outputStreamWriter.flush();
                outputStreamWriter.close();
            } catch (Throwable unused) {
            }
        }
    }

    private static void rotate(Context context) {
        File logFile = logFile(context);
        File oldFile = oldFile(context);
        try {
            oldFile.delete();
        } catch (Throwable unused) {
        }
        if (logFile.exists()) {
            logFile.renameTo(oldFile);
        }
    }

    private static File logFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, LOG_FILE);
    }

    private static File oldFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, OLD_LOG_FILE);
    }

    private static void appendFile(StringBuilder sb, File file) {
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    bufferedReader.close();
                    return;
                } else {
                    sb.append(readLine);
                    sb.append('\n');
                }
            }
        } catch (Throwable unused) {
        }
    }

    private static String readTail(File file, int i) {
        int indexOf;
        int i2;
        if (file != null && file.exists() && i > 0) {
            RandomAccessFile randomAccessFile = null;
            try {
                RandomAccessFile randomAccessFile2 = new RandomAccessFile(file, "r");
                try {
                    long length = randomAccessFile2.length();
                    long min = Math.min(512000, Math.max(8192, i * 2));
                    long max = Math.max(0L, length - min);
                    randomAccessFile2.seek(max);
                    byte[] bArr = new byte[(int) Math.min(min, length - max)];
                    randomAccessFile2.readFully(bArr);
                    String str = new String(bArr, StandardCharsets.UTF_8);
                    if (max > 0 && (indexOf = str.indexOf(10)) >= 0 && (i2 = indexOf + 1) < str.length()) {
                        str = str.substring(i2);
                    }
                    if (str.length() > i) {
                        str = str.substring(str.length() - i);
                    }
                    try {
                        randomAccessFile2.close();
                    } catch (Throwable unused) {
                    }
                    return str;
                } catch (Throwable unused2) {
                    randomAccessFile = randomAccessFile2;
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (Throwable unused3) {
                        }
                    }
                    return "";
                }
            } catch (Throwable unused4) {
            }
        }
        return "";
    }

    private static String clean(String str, int i) {
        if (str == null) {
            return "";
        }
        String replace = HcfSupportSanitizer.sanitize(str).replace((char) 0, ' ').replace("\r", "");
        if (replace.length() <= i) {
            return replace;
        }
        return replace.substring(0, i) + "…";
    }

    private AppLogger() {
    }
}


// ---- ErrorSystem.java ----
/* loaded from: classes.dex */
final class ErrorSystem {

    static final class AppError {
        final String code;
        final String message;
        final String technical;
        final String title;

        AppError(String str, String str2, String str3, String str4) {
            this.code = str;
            this.title = str2;
            this.message = str3;
            this.technical = str4 == null ? "" : str4;
        }
    }

    static AppError offline() {
        return new AppError("HCF-NET-001", "You're offline", "Waiting for an internet connection. The forum will retry automatically.", "No validated network connection is currently available.");
    }

    static AppError fromWebView(int i, String str, boolean z) {
        String str2;
        if (z) {
            return offline();
        }
        StringBuilder sb = new StringBuilder("WebView error ");
        sb.append(i);
        if (str == null || str.trim().isEmpty()) {
            str2 = "";
        } else {
            str2 = " • " + str.trim();
        }
        sb.append(str2);
        String sb2 = sb.toString();
        if (i == -16) {
            return new AppError("HCF-SEC-002", "Unsafe resource blocked", "Android Safe Browsing blocked this page or resource for your protection.", sb2);
        }
        if (i == -15) {
            return new AppError("HCF-WEB-429", "Too many requests", "The forum is temporarily limiting requests. Wait a moment and try again.", sb2);
        }
        if (i == -12) {
            return new AppError("HCF-WEB-001", "Invalid forum address", "The app could not load this forum address safely.", sb2);
        }
        if (i == -11) {
            return ssl(sb2);
        }
        if (i == -9) {
            return new AppError("HCF-WEB-002", "Redirect loop detected", "The page keeps redirecting and cannot be opened safely.", sb2);
        }
        if (i == -8) {
            return new AppError("HCF-NET-004", "Forum connection timed out", "The server took too long to respond. Try again or use the backup server.", sb2);
        }
        if (i == -7) {
            return new AppError("HCF-NET-005", "Connection interrupted", "The connection ended before the page could finish loading.", sb2);
        }
        if (i == -6) {
            return new AppError("HCF-NET-003", "Can't reach the forum server", "The server refused or could not accept the connection. Automatic recovery will keep trying.", sb2);
        }
        if (i == -2) {
            return new AppError("HCF-NET-002", "Forum server couldn't be found", "The forum address could not be resolved. Harley's Clan Forum will try the backup server when available.", sb2);
        }
        return new AppError("HCF-NET-099", "Can't load the forum", "Harley's Clan Forum could not load this page. Try again or switch servers.", sb2);
    }

    static AppError fromHttp(int i, String str) {
        String str2;
        StringBuilder sb = new StringBuilder("HTTP ");
        sb.append(i);
        if (str == null || str.isEmpty()) {
            str2 = "";
        } else {
            str2 = " • " + str;
        }
        sb.append(str2);
        String sb2 = sb.toString();
        if (i == 403) {
            return new AppError("HCF-WEB-403", "Access Forbidden", "Access to this forum area is restricted. Your account may not have permission to view this resource.", sb2);
        }
        if (i == 404) {
            return new AppError("HCF-WEB-404", "Page Not Found", "The forum page could not be found. It may have been moved, renamed, or removed.", sb2);
        }
        if (i == 429) {
            return new AppError("HCF-WEB-429", "Too Many Requests", "The forum is temporarily limiting requests. Wait a moment and try again.", sb2);
        }
        if (i == 500) {
            return new AppError("HCF-WEB-500", "Internal Server Error", "The forum hit an unexpected server error while processing your request. Please try again in a moment.", sb2);
        }
        if (i == 502) {
            return new AppError("HCF-WEB-502", "Bad Gateway", "A gateway between the app and forum returned an invalid response.", sb2);
        }
        if (i == 503) {
            return new AppError("HCF-WEB-503", "Service Unavailable", "The forum is temporarily unavailable, usually because of maintenance or a short service interruption.", sb2);
        }
        if (i == 504) {
            return new AppError("HCF-WEB-504", "Gateway Timeout", "The forum gateway did not receive a response in time.", sb2);
        }
        return new AppError("HCF-WEB-5XX", "Forum Server Problem", "The forum server returned HTTP " + i + ". Try again or use the backup server.", sb2);
    }

    static AppError externalBlocked(String str) {
        String trim = (str == null || str.trim().isEmpty()) ? "unknown destination" : str.trim();
        if (trim.length() > 180) {
            trim = trim.substring(0, 180) + "…";
        }
        return new AppError("HCF-SEC-003", "External Site Blocked", "This app only opens registered Harley's Clan Forum websites inside the forum viewer.", "Blocked non-forum navigation: " + trim);
    }

    static AppError connectionTimeout(String str) {
        return new AppError("HCF-NET-004", "Forum connection timed out", "The forum is taking longer than expected to respond. Try again or use the backup server.", "Startup connection timeout while loading " + ((str == null || str.trim().isEmpty()) ? "the forum server" : str.trim()) + ".");
    }

    static AppError ssl(String str) {
        if (str == null || str.isEmpty()) {
            str = "TLS/SSL validation failed.";
        }
        return new AppError("HCF-SSL-001", "Secure connection blocked", "Harley's Clan Forum could not verify the secure connection. For your safety, the app did not continue.", str);
    }

    static AppError renderer(boolean z) {
        return new AppError("HCF-WV-001", "Forum viewer restarted", "The Android web viewer stopped unexpectedly. The app is rebuilding it and restoring your forum page.", z ? "WebView renderer process crashed." : "WebView renderer process was terminated by Android.");
    }

    static AppError updateVerification(String str) {
        return new AppError("HCF-UPD-002", "Update verification failed", "The downloaded APK did not pass Harley's Clan Forum security checks, so it will not be installed.", str);
    }

    static AppError updateDownloadFailure(int i) {
        String str;
        switch (i) {
            case 1001:
                str = "Android could not write the update APK to storage.";
                break;
            case 1002:
                str = "The update server returned an unexpected HTTP response.";
                break;
            case 1003:
            default:
                str = "Android could not finish downloading the update. Check your connection and try again.";
                break;
            case 1004:
                str = "The update connection ended while Android was receiving the APK.";
                break;
            case 1005:
                str = "The update download was stopped because the server redirected too many times.";
                break;
            case 1006:
                str = "There is not enough free storage to download the update.";
                break;
            case 1007:
                str = "Android could not access the selected download storage.";
                break;
            case 1008:
                str = "Android could not resume the interrupted update download. Start the download again.";
                break;
        }
        return new AppError("HCF-UPD-001", "Update download failed", str, "DownloadManager reason " + i);
    }

    static AppError installerOpenFailure(String str) {
        return new AppError("HCF-UPD-003", "Couldn't open Android installer", "The APK is downloaded, but Android could not open the package installer. Check the install-apps permission and try again.", str);
    }

    static AppError generic(String str) {
        return new AppError("HCF-APP-099", "Something went wrong", (str == null || str.trim().isEmpty()) ? "The app hit a recoverable problem." : str.trim(), str);
    }

    private ErrorSystem() {
    }
}

// ---- HcfSupportSanitizer.java ----
final class HcfSupportSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern HEADER = Pattern.compile(
            "(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n|]+"
    );
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"
    );
    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "(?i)https://(?:www\\.)?discord(?:app)?\\.com/api/webhooks/[^\\s\\\"']+"
    );
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|session[_-]?(?:id|token)|access[_-]?token|refresh[_-]?token|auth(?:entication)?[_-]?token|csrf[_-]?token|reply[_-]?(?:text|body|content)|message[_-]?(?:body|content|text)|notification[_-]?(?:body|message|content)|private[_-]?(?:message|body|content))\\b\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,\\s|;}]+)"
    );

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        String out = value;
        out = replaceHeader(out);
        out = BEARER.matcher(out).replaceAll("Bearer " + REDACTED);
        out = DISCORD_WEBHOOK.matcher(out).replaceAll("https://discord.com/api/webhooks/" + REDACTED);
        Matcher matcher = KEY_VALUE.matcher(out);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceHeader(String value) {
        Matcher matcher = HEADER.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private HcfSupportSanitizer() {}
}
