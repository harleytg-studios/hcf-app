# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current Stable version: `1.0 (10000077)`
- Android versionCode: `10000077`
- GitHub tag: `v1.0`
- Update channel: Stable only
- Branding: original Stable logo/icon set

## Current Stable feature set

Stable v10000077 uses the current app UI generation with Stable production identity and runtime fixes. This includes Contact Support v2/default-collapsed behavior, account/identity fixes, Settings/theme improvements, notification history/routing, registered-domain routing, logs/diagnostics/error UI improvements, and performance/backoff work.

The v10000077 notification patch also removes the foreground bridge cooldown, uses a 1-second live fallback/retry cap, keeps reconnect sync immediate, and does not stop the live notification service merely because Silent Alerts are silenced.

Features explicitly designated Beta/Dev-only remain outside the Stable promotion policy until separately approved.

## Forum domains

- Primary: `forum.harleytg.com`
- Backup: `harleysclan.freeflarum.com`
- Retired `.online` domains are not part of the Stable registry.

## Release artifacts

The matching Stable release artifact names are:

- `HCF-Stable-v10000077.apk`
- `HCF-Stable-v10000077-source.zip`
- `HCF-Stable-v10000077-VERIFICATION.txt`
- `STABLE-LATEST.json`

The branch stores source and release metadata; private signing material is never stored here.

## Stable V2 signing line

Stable v10000077 uses the established **Stable V2** signing identity already used by the Stable v10000034 line.

Certificate SHA-256:

`77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`

Stable installs already signed with Stable V2 can update in place to v10000077. Older Stable installs signed by a different certificate still require the normal one-time signer migration.

**Never commit the Stable V2 private JKS, password, or other private signing material to this public repository.**
