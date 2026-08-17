package com.harleytg.forum.dev;

import android.app.DownloadManager;
import android.webkit.WebViewClient;

final class ErrorSystem {
    static final class AppError {
        final String code;
        final String title;
        final String message;
        final String technical;

        AppError(String code, String title, String message, String technical) {
            this.code = code;
            this.title = title;
            this.message = message;
            this.technical = technical == null ? "" : technical;
        }
    }

    static AppError offline() {
        return new AppError(
                "HCF-NET-001",
                "You're offline",
                "Waiting for an internet connection. The forum will retry automatically.",
                "No validated network connection is currently available."
        );
    }

    static AppError fromWebView(int errorCode, String description, boolean offline) {
        if (offline) return offline();
        String detail = "WebView error " + errorCode + (description == null || description.trim().isEmpty() ? "" : " • " + description.trim());
        switch (errorCode) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                return new AppError("HCF-NET-002", "Forum server couldn't be found", "The forum address could not be resolved. Harley's Clan Forum will try the backup server when available.", detail);
            case WebViewClient.ERROR_CONNECT:
                return new AppError("HCF-NET-003", "Can't reach the forum server", "The server refused or could not accept the connection. Automatic recovery will keep trying.", detail);
            case WebViewClient.ERROR_TIMEOUT:
                return new AppError("HCF-NET-004", "Forum connection timed out", "The server took too long to respond. Try again or use the backup server.", detail);
            case WebViewClient.ERROR_IO:
                return new AppError("HCF-NET-005", "Connection interrupted", "The connection ended before the page could finish loading.", detail);
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return ssl(detail);
            case WebViewClient.ERROR_TOO_MANY_REQUESTS:
                return new AppError("HCF-WEB-429", "Too many requests", "The forum is temporarily limiting requests. Wait a moment and try again.", detail);
            case WebViewClient.ERROR_BAD_URL:
                return new AppError("HCF-WEB-001", "Invalid forum address", "The app could not load this forum address safely.", detail);
            case WebViewClient.ERROR_REDIRECT_LOOP:
                return new AppError("HCF-WEB-002", "Redirect loop detected", "The page keeps redirecting and cannot be opened safely.", detail);
            case WebViewClient.ERROR_UNSAFE_RESOURCE:
                return new AppError("HCF-SEC-002", "Unsafe resource blocked", "Android Safe Browsing blocked this page or resource for your protection.", detail);
            default:
                return new AppError("HCF-NET-099", "Can't load the forum", "Harley's Clan Forum could not load this page. Try again or switch servers.", detail);
        }
    }

    static AppError fromHttp(int status, String host) {
        String technical = "HTTP " + status + (host == null || host.isEmpty() ? "" : " • " + host);
        if (status == 500) return new AppError("HCF-WEB-500", "Forum server error", "The forum server hit an internal error. The app can try the backup server.", technical);
        if (status == 502) return new AppError("HCF-WEB-502", "Bad gateway", "A gateway between the app and forum returned an invalid response.", technical);
        if (status == 503) return new AppError("HCF-WEB-503", "Forum temporarily unavailable", "The forum is temporarily unavailable or under maintenance.", technical);
        if (status == 504) return new AppError("HCF-WEB-504", "Gateway timed out", "The forum gateway did not receive a response in time.", technical);
        return new AppError("HCF-WEB-5XX", "Forum server problem", "The forum server returned HTTP " + status + ". Try again or use the backup server.", technical);
    }


    static AppError connectionTimeout(String host) {
        String target = host == null || host.trim().isEmpty() ? "the forum server" : host.trim();
        return new AppError(
                "HCF-NET-004",
                "Forum connection timed out",
                "The forum is taking longer than expected to respond. Try again or use the backup server.",
                "Startup connection timeout while loading " + target + "."
        );
    }

    static AppError ssl(String technical) {
        return new AppError(
                "HCF-SSL-001",
                "Secure connection blocked",
                "Harley's Clan Forum could not verify the secure connection. For your safety, the app did not continue.",
                technical == null || technical.isEmpty() ? "TLS/SSL validation failed." : technical
        );
    }

    static AppError renderer(boolean crashed) {
        return new AppError(
                "HCF-WV-001",
                "Forum viewer restarted",
                "The Android web viewer stopped unexpectedly. The app is rebuilding it and restoring your forum page.",
                crashed ? "WebView renderer process crashed." : "WebView renderer process was terminated by Android."
        );
    }

    static AppError updateVerification(String detail) {
        return new AppError("HCF-UPD-002", "Update verification failed", "The downloaded APK did not pass Harley's Clan Forum security checks, so it will not be installed.", detail);
    }

    static AppError updateDownloadFailure(int reason) {
        String message;
        switch (reason) {
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                message = "There is not enough free storage to download the update.";
                break;
            case DownloadManager.ERROR_CANNOT_RESUME:
                message = "Android could not resume the interrupted update download. Start the download again.";
                break;
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                message = "Android could not access the selected download storage.";
                break;
            case DownloadManager.ERROR_FILE_ERROR:
                message = "Android could not write the update APK to storage.";
                break;
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                message = "The update connection ended while Android was receiving the APK.";
                break;
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                message = "The update download was stopped because the server redirected too many times.";
                break;
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                message = "The update server returned an unexpected HTTP response.";
                break;
            default:
                message = "Android could not finish downloading the update. Check your connection and try again.";
                break;
        }
        return new AppError("HCF-UPD-001", "Update download failed", message, "DownloadManager reason " + reason);
    }

    static AppError installerOpenFailure(String detail) {
        return new AppError("HCF-UPD-003", "Couldn't open Android installer", "The APK is downloaded, but Android could not open the package installer. Check the install-apps permission and try again.", detail);
    }

    static AppError generic(String detail) {
        return new AppError("HCF-APP-099", "Something went wrong", detail == null || detail.trim().isEmpty() ? "The app hit a recoverable problem." : detail.trim(), detail);
    }

    private ErrorSystem() {}
}
