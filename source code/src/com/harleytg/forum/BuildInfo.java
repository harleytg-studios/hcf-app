package com.harleytg.forum;

final class BuildInfo {
    static final String VERSION = "1.0";
    static final String VERSION_TAG = "v1.0";
    static final int VERSION_CODE = 10000034;
    static final int INTERNAL_BUILD = 79;
    static final String CHANNEL = "Stable";
    static final String BRAND = "Harley's Studio's";
    static final String SESSION_CLIENT = "Harley's Clan Forum App";
    static final String APK_FILE_NAME = "HarleysClanForum-1.0.apk";
    static final String META_LINE = "1.0 • Stable • Build 10000034";
    static final String VERSION_BUILD_LINE = "1.0 • Stable • Updater Fix • Build 10000034";

    static final String VERSION_CODE_SCHEME = "major-release-v1";

    static final boolean FIREBASE_WEB_CONFIG_BUNDLED = true;
    static final boolean FCM_CONFIGURED = false;
    static final String USER_AGENT_MARKER = "HarleysClanForumApp/1.0";
    static final String UPDATE_REPOSITORY = "markhitchk/hcf-app";
    static final String UPDATE_STABLE_BRANCH = "stable";
    static final String UPDATE_DEV_BRANCH = "dev";
    static final String DEFAULT_UPDATE_CHANNEL = "stable";
    static final boolean ALLOW_UPDATE_CHANNEL_SWITCH = false;

    static String userAgent(String baseUserAgent) {
        String base = baseUserAgent == null ? "" : baseUserAgent.trim();
        if (base.contains(USER_AGENT_MARKER)) return base;
        return base.isEmpty() ? USER_AGENT_MARKER : base + " " + USER_AGENT_MARKER + " NativeApp";
    }

    private BuildInfo() {}
}
