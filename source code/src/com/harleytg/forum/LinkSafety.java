package com.harleytg.forum;

import android.net.Uri;
import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

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
