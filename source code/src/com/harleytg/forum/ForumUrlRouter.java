package com.harleytg.forum;

import android.net.Uri;
import java.util.Locale;

/* loaded from: classes.dex */
final class ForumUrlRouter {
    private ForumUrlRouter() {
    }

    static boolean isForumHost(String str) {
        return str != null && ForumConfig.FORUM_HOSTS.contains(str.toLowerCase(Locale.US));
    }

    static boolean isForumUrl(Uri uri) {
        if (uri == null || !isForumHost(uri.getHost())) {
            return false;
        }
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    static String equivalentOnHost(Uri uri, String str) {
        Uri.Builder encodedPath = new Uri.Builder().scheme("https").authority(str).encodedPath(emptyToSlash(uri.getEncodedPath()));
        if (uri.getEncodedQuery() != null) {
            encodedPath.encodedQuery(uri.getEncodedQuery());
        }
        if (uri.getEncodedFragment() != null) {
            encodedPath.encodedFragment(uri.getEncodedFragment());
        }
        return encodedPath.build().toString();
    }

    static String home(String str) {
        return "https://" + str + "/";
    }

    private static String emptyToSlash(String str) {
        return (str == null || str.isEmpty()) ? "/" : str;
    }
}
