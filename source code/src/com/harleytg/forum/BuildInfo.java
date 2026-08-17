package com.harleytg.forum.dev;

final class BuildInfo {
    // Public versioning now follows SemVer. Development releases use
    // MAJOR.MINOR.PATCH-dev.N and GitHub tags add the leading "v".
    static final String VERSION = "1.0";
    static final String VERSION_TAG = "v1.0";
    static final int VERSION_CODE = 10000032;
    static final int INTERNAL_BUILD = 85;
    static final String CHANNEL = "Dev";
    static final String BRAND = "Harley's Studio's";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String APK_FILE_NAME = "Harley's Clan Forum [Beta].apk";
    static final String DEVELOPMENT_BUILD_LABEL = "Harley's Clan Forum v1.0 [Development Build / Beta]";
    static final String META_LINE = "1.0 • Development / Beta";
    static final String VERSION_BUILD_LINE = "1.0 • Development / Beta • Foundation Release";

    // Small Beta/Development-only promotion for the daily-use Stable app.
    static final int STABLE_PROMO_VERSION_CODE = 10000033;
    static final String STABLE_PROMO_LABEL = "Stable v10000033";
    static final String STABLE_PROMO_URL = "https://github.com/markhitchk/hcf-app/releases/download/v1.0/HCF-1.0.apk";

    // v1.0 starts the major-release versionCode range at 10,000,000.
    // Future builds must remain monotonic for Android in-place updates.
    static final String VERSION_CODE_SCHEME = "major-release-v1";

    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final boolean FCM_CONFIGURED = false;
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String DEFAULT_UPDATE_CHANNEL = "dev";
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;

    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }

    private BuildInfo() {}
}
