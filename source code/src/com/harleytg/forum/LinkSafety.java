package com.harleytg.forum;

import android.net.Uri;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

final class LinkSafety {
    enum Status {
        OFFICIAL("🛡️", "Official"),
        EXTERNAL("🔗", "External"),
        SUSPICIOUS("⚠️", "Suspicious"),
        BLOCKED("⛔", "Blocked");

        final String icon;
        final String label;

        Status(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        String display() {
            return icon + " " + label;
        }
    }

    static final class Result {
        final Status status;
        final String host;
        final String reason;

        Result(Status status, String host, String reason) {
            this.status = status;
            this.host = host == null ? "" : host;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6ISH = Pattern.compile("^[0-9a-fA-F:]+$");

    private LinkSafety() {}

    static Result classify(Uri uri) {
        if (uri == null) return blocked("", "Invalid link");
        if (ForumUrlRouter.isForumUrl(uri)) {
            return new Result(Status.OFFICIAL, safeHost(uri), "Harley's Clan Forum trusted domain");
        }

        String scheme = lower(uri.getScheme());
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            return blocked(safeHost(uri), "Unsupported link type");
        }

        String host = canonicalHost(uri.getHost());
        if (host.isEmpty()) return blocked("", "Missing website domain");

        if (!"https".equals(scheme)) {
            return suspicious(host, "This website is not using HTTPS");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            return suspicious(host, "The address contains embedded sign-in information");
        }
        if (host.startsWith("xn--") || host.contains(".xn--")) {
            return suspicious(host, "The domain uses an encoded international name");
        }
        if (isIpAddress(host)) {
            return suspicious(host, "The link uses a direct IP address instead of a normal domain");
        }
        if (looksLikeForumImpersonation(host)) {
            return suspicious(host, "This domain resembles Harley's Clan Forum but is not an official domain");
        }
        return new Result(Status.EXTERNAL, host, "External website");
    }

    static String canonicalHost(String host) {
        if (host == null) return "";
        String clean = host.trim().toLowerCase(Locale.US);
        if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1);
        try { clean = IDN.toASCII(clean, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.US); }
        catch (Throwable ignored) {}
        return clean;
    }

    private static boolean looksLikeForumImpersonation(String host) {
        if (ForumUrlRouter.isForumHost(host)) return false;
        String compact = host.replace("-", "").replace("_", "");
        return compact.contains("harleytg")
                || compact.contains("harleysclan")
                || compact.contains("harleyclan")
                || (compact.contains("harley") && compact.contains("forum"));
    }

    private static boolean isIpAddress(String host) {
        if (IPV4.matcher(host).matches()) return true;
        return host.indexOf(':') >= 0 && IPV6ISH.matcher(host).matches();
    }

    private static Result suspicious(String host, String reason) {
        return new Result(Status.SUSPICIOUS, host, reason);
    }

    private static Result blocked(String host, String reason) {
        return new Result(Status.BLOCKED, host, reason);
    }

    private static String safeHost(Uri uri) {
        return canonicalHost(uri == null ? null : uri.getHost());
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
