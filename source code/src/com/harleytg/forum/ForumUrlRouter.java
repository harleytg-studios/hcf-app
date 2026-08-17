package com.harleytg.forum;

import android.net.Uri;

import java.util.Locale;

final class ForumUrlRouter {
    private ForumUrlRouter() {}

    static boolean isForumHost(String host) {
        return host != null && ForumConfig.FORUM_HOSTS.contains(host.toLowerCase(Locale.US));
    }

    static boolean isForumUrl(Uri uri) {
        if (uri == null || !isForumHost(uri.getHost())) return false;
        String scheme = uri.getScheme();
        return ForumConfig.HTTPS.equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    static String equivalentOnHost(Uri source, String targetHost) {
        Uri.Builder b = new Uri.Builder()
                .scheme(ForumConfig.HTTPS)
                .authority(targetHost)
                .encodedPath(emptyToSlash(source.getEncodedPath()));

        if (source.getEncodedQuery() != null) b.encodedQuery(source.getEncodedQuery());
        if (source.getEncodedFragment() != null) b.encodedFragment(source.getEncodedFragment());
        return b.build().toString();
    }

    static String home(String host) {
        return ForumConfig.HTTPS + "://" + host + "/";
    }

    private static String emptyToSlash(String path) {
        return (path == null || path.isEmpty()) ? "/" : path;
    }
}
