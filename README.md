# Harley's Clan Forum Android App — DEV

Development Android release branch for Harley's Clan Forum.

## DEV identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum.dev`
- Current DEV version: `1.0`
- Android versionCode: `10000021`
- GitHub prerelease tag: `v1.0-dev`
- Release type: **Pre-release**
- Target SDK: `34`

The DEV package is separate from Stable (`com.harleytg.forum`), allowing the two channels to be installed side-by-side when their signing identities are compatible with any already-installed version of the same package.

## Current 1.0 DEV line

The current build includes Day Theme/native UI updates, Auto/default performance profiles, notification-count and notification-button work, simplified sharing, account/identity fixes, diagnostics/log UI improvements, installer/update fixes, profile-avatar fit correction, and reduced animation load for lower-end devices.

## Permanent DEV signing

Current DEV certificate SHA-256:

`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

Older DEV APKs used different signing keys. Android cannot update an installed `com.harleytg.forum.dev` package across an incompatible signing identity, so legacy DEV installs may require a one-time uninstall/reinstall before joining the permanent signing line.

## Release channels

- Stable: `v1.0` from branch `stable`, normal GitHub Release / Latest.
- DEV: `v1.0-dev` from branch `dev`, GitHub Pre-release.

Attach the matching APK to each GitHub Release because the app updater consumes the release APK asset. See `DEV-LATEST.json`, `DEV-RELEASE.md`, and `HarleysClanForum-1.0-dev-VERIFICATION.txt` for current DEV metadata and integrity details.
