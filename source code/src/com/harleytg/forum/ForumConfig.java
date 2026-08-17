package com.harleytg.forum.dev;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class ForumConfig {
    static final String PRIMARY_HOST = "forum.harleytg.com";
    static final String BACKUP_HOST = "harleysclan.freeflarum.com";
    static final String HTTPS = "https";
    static final Set<String> FORUM_HOSTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(PRIMARY_HOST, BACKUP_HOST))
    );

    static final long PRIMARY_RETRY_COOLDOWN_MS = 6L * 60L * 60L * 1000L;

    private ForumConfig() {}
}
