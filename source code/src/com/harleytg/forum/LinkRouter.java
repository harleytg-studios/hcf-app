package com.harleytg.forum.dev;

import android.net.Uri;

import java.util.Locale;
import java.util.Set;

/** Single source of truth for trusted in-app forum URL routing. */
final class LinkRouter {
    private static final String HTTPS = "https";

    static String primaryHost() {
        return AppConfig.domains().primaryHost;
    }

    static String backupHost() {
        return AppConfig.domains().firstBackupOrPrimary();
    }

    static Set<String> trustedHosts() {
        return AppConfig.domains().trustedHosts;
    }

    static boolean isTrustedHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(Locale.US);
        return !normalized.isEmpty() && AppConfig.domains().trustedHosts.contains(normalized);
    }

    static boolean isInternal(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!HTTPS.equalsIgnoreCase(scheme) || !isTrustedHost(host)) return false;
        int port = uri.getPort();
        return port == -1 || port == 443;
    }

    static Uri normalizeInternal(Uri uri) {
        if (!isInternal(uri)) return null;
        return Uri.parse(equivalentOnHost(uri, uri.getHost().toLowerCase(Locale.US)));
    }

    static String equivalentOnHost(Uri source, String targetHost) {
        String host = targetHost == null ? "" : targetHost.trim().toLowerCase(Locale.US);
        if (!isTrustedHost(host)) throw new IllegalArgumentException("Unregistered forum host");
        Uri safeSource = source == null ? Uri.EMPTY : source;
        Uri.Builder builder = new Uri.Builder().scheme(HTTPS).authority(host);
        String path = safeSource.getEncodedPath();
        builder.encodedPath(path == null || path.isEmpty() ? "/" : path);
        if (AppConfig.domains().preserveQuery && safeSource.getEncodedQuery() != null) {
            builder.encodedQuery(safeSource.getEncodedQuery());
        }
        if (AppConfig.domains().preserveFragment && safeSource.getEncodedFragment() != null) {
            builder.encodedFragment(safeSource.getEncodedFragment());
        }
        return builder.build().toString();
    }

    static String home(String host) {
        String safe = isTrustedHost(host) ? host.trim().toLowerCase(Locale.US) : primaryHost();
        return HTTPS + "://" + safe + "/";
    }

    static Uri forumPath(String host, String path) {
        String base = home(host);
        String safePath = path == null ? "" : path.trim();
        while (safePath.startsWith("/")) safePath = safePath.substring(1);
        return Uri.parse(base + safePath);
    }

    static String conversationId(Uri uri) {
        if (!isInternal(uri)) return "";
        java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() >= 2 && "conversations".equalsIgnoreCase(segments.get(0))) {
            String id = segments.get(1);
            return id != null && id.matches("[0-9]+") ? id : "";
        }
        return "";
    }

    private LinkRouter() {}
}

/** Compatibility facade for existing source references; values come from generated runtime config. */
final class ForumConfig {
    static final String HTTPS = "https";
    static final long PRIMARY_RETRY_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    static final String PRIMARY_HOST = LinkRouter.primaryHost();
    static final String BACKUP_HOST = LinkRouter.backupHost();
    static final Set<String> FORUM_HOSTS = LinkRouter.trustedHosts();

    private ForumConfig() {}
}

/** Compatibility facade. New code should call LinkRouter directly. */
final class ForumUrlRouter {
    static boolean isForumHost(String host) { return LinkRouter.isTrustedHost(host); }
    static boolean isForumUrl(Uri uri) { return LinkRouter.isInternal(uri); }
    static String equivalentOnHost(Uri source, String targetHost) { return LinkRouter.equivalentOnHost(source, targetHost); }
    static String home(String host) { return LinkRouter.home(host); }
    private ForumUrlRouter() {}
}
