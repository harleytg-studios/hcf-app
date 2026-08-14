# Harley's Clan Forum 0.2.0

First full stable Android release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.0`
- Android versionCode: `2009999`
- GitHub tag: `v0.2.0`
- Channel: **Stable / GitHub Latest**

## Changes
- Production package no longer uses `.dev`, so Stable and Dev can be installed side-by-side.
- Uses the HTG App Icon black/yellow caution branding.
- Stable updater is locked to normal GitHub Latest releases; it will not pull Dev prereleases.
- Automatic update checks and automatic APK downloads remain supported.
- After Android successfully replaces the stable package, the app removes the downloaded installer APK and its DownloadManager entry.
- A startup fallback also cleans the installer if the installed version matches the downloaded update tag.
- Live forum page updates, cookie inspection, simplified settings, and the prior 0.2.0-dev.1 improvements are included.

## Integrity
- APK SHA-256: `648a86a5246fc89849c70e8b8cb9630a71f76dc24bfb8d34296b9fb03b93828b`
- Source ZIP SHA-256: `87c4802b8061a41d0839b8201ecc8205a4450683bd2bd795fc8453c2fc12b6f2`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
