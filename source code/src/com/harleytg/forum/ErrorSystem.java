package com.harleytg.forum.dev;

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
