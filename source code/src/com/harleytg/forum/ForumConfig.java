package com.harleytg.forum.dev;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Runtime forum-domain registry with safe built-in fallbacks. */
final class ForumConfig {
    static final String HTTPS = "https";
    static final long PRIMARY_RETRY_COOLDOWN_MS = 21600000L;

    static final String BUILTIN_PRIMARY_HOST = "forum.harleytg.com";
    static final String BUILTIN_BACKUP_HOST = "harleysclan.freeflarum.com";

    static volatile String PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
    static volatile String BACKUP_HOST = BUILTIN_BACKUP_HOST;
    static volatile Set<String> FORUM_HOSTS = builtInHosts();

    static synchronized void applyRemote(String primary, Collection<String> backups) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (primary != null && !primary.isEmpty()) hosts.add(primary);
        if (backups != null) {
            for (String host : backups) {
                if (host != null && !host.isEmpty()) hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            resetBuiltIn();
            return;
        }

        PRIMARY_HOST = hosts.iterator().next();
        List<String> ordered = new ArrayList<>(hosts);
        BACKUP_HOST = ordered.size() > 1 ? ordered.get(1) : BUILTIN_BACKUP_HOST;
        if (ordered.size() == 1 && !BACKUP_HOST.equals(PRIMARY_HOST)) hosts.add(BACKUP_HOST);
        FORUM_HOSTS = Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
    }

    static synchronized void resetBuiltIn() {
        PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
        BACKUP_HOST = BUILTIN_BACKUP_HOST;
        FORUM_HOSTS = builtInHosts();
    }

    private static Set<String> builtInHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add(BUILTIN_PRIMARY_HOST);
        hosts.add(BUILTIN_BACKUP_HOST);
        return Collections.unmodifiableSet(hosts);
    }

    private ForumConfig() {}
}
