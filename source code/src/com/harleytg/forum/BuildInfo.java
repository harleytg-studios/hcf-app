package com.harleytg.forum.dev;

/** Build identity for the Dev/Beta Harley's Clan Forum Android app. */
final class BuildInfo {
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;
    static final String APK_FILE_NAME = "HCF-Beta-v10000080.apk";
    static final String BRAND = "Harley's Studio's";
    static final String CHANNEL = "Dev";
    static final String DEFAULT_UPDATE_CHANNEL = "dev";
    static final String DEVELOPMENT_BUILD_LABEL = "Harley's Clan Forum v1.0 [Development Build / Beta]";
    static final boolean ENABLE_DEV_TEST_MENU = true;
    static final boolean FCM_CONFIGURED = false;
    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final int INTERNAL_BUILD = 100;
    static final String META_LINE = "1.0 • Development / Beta";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0";
    static final String VERSION = "1.0";
    static final int VERSION_CODE = 10000080;
    static final String VERSION_BUILD_LINE = VERSION + " • Development / Beta • Build " + VERSION_CODE;
    static final String VERSION_CODE_SCHEME = "major-release-v1";
    static final String VERSION_TAG = "v1.0";

    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }

    private BuildInfo() {}
}
