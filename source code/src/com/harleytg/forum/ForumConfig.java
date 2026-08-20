package com.harleytg.forum.dev;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/* loaded from: classes.dex */
final class ForumConfig {
    static final String HTTPS = "https";
    static final long PRIMARY_RETRY_COOLDOWN_MS = 21600000;
    static final String PRIMARY_HOST = "forum.harleytg.com";
    static final String BACKUP_HOST = "harleysclan.freeflarum.com";
    static final Set<String> FORUM_HOSTS = Collections.unmodifiableSet(new HashSet(Arrays.asList(PRIMARY_HOST, BACKUP_HOST)));

    private ForumConfig() {
    }
}
