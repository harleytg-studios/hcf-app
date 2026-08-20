# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `1.0 (10000072)`
- Android versionCode: `10000072`
- GitHub tag: `v1.0`
- Update channel: Stable only
- Branding: original Stable logo/icon set

## Current Stable feature set

The v10000072 Stable line promotes the user-facing work from newer builds that is not explicitly Beta/Dev-only. This includes Contact Support v2/default-collapsed behavior, account/identity fixes, Settings/theme improvements, notification history/routing/adaptive polling, registered-domain routing, logs/diagnostics/error UI improvements, and performance/backoff work.

Features explicitly designated Beta/Dev-only are not listed as Stable features and remain outside the Stable promotion policy until separately approved.

## Forum domains

- Primary: `forum.harleytg.com`
- Backup: `harleysclan.freeflarum.com`
- Retired `.online` domains are not part of the Stable registry.

## Update behavior

Stable builds use the Stable update channel only and must never consume Dev/Beta update feeds. The current v10000072 release identity and hashes are recorded in `STABLE-LATEST.json` and `STABLE-RELEASE.md`.

## Repository layout

- `source code/` — Stable Android source/reference tree
- `STABLE-RELEASE.md` — current Stable release details and feature policy
- `STABLE-LATEST.json` — machine-readable Stable release metadata
- `build-output/` — published Stable build pointers/artifacts when present

## Signing line

Starting with Stable v10000072, the local Stable signer certificate SHA-256 is:

`9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`

This differs from the earlier Stable certificate. Android cannot update across signing identities, so devices on the previous Stable signer require a one-time reinstall to join the v10000072 signing line. Future Stable releases intended to update v10000072 in place must use the same local private key.

**Never commit the private signing key, keystore, or password to this public repository.**
