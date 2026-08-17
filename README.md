# Harley's Clan Forum Android App — DEV

Development Android release branch for Harley's Clan Forum.

## DEV identity

- App name: **Harley's Clan Forum [Beta]**
- Android package: `com.harleytg.forum.dev`
- Current DEV version: `1.0`
- Android versionCode: `10000032`
- GitHub prerelease tag: `v1.0-dev`
- Release type: **Pre-release**
- Target SDK: `34`

The DEV package is separate from Stable (`com.harleytg.forum`), allowing both apps to be installed side-by-side when the installed signing identities are compatible.

## Current 1.0 DEV line

The current build includes the 1.0 native shell, Day Theme/light-mode work, performance profiles with Auto as default, notification counts/badges, simplified sharing, Account & Identity fixes, diagnostics/log UI improvements, app-link handling, safe external links, updater/install improvements, profile-avatar fit correction, reduced animation load for lower-end devices, and the current forum-message notification parser fix.

### Update system

- DEV-only update channel.
- Update detection uses the downloaded APK's embedded Android `versionCode` rather than the GitHub tag, visible version name, or APK filename.
- Downloaded APKs are validated before install handoff.
- After verification, HCF opens Android's installer automatically when allowed.
- If Android blocks background installer launch, HCF falls back to an install-ready notification.

### Forum message notifications

Private-message payloads are unwrapped before displaying Android notifications. Raw API JSON is not shown to the user. Conversation notifications use `New message from <sender>` plus the readable message body, and notification taps route to `/conversations/<conversation_id>` when available.

## Permanent DEV signing

Current DEV certificate SHA-256:

`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

Older DEV APKs used different signing keys. Android cannot update an installed `com.harleytg.forum.dev` package across an incompatible signing identity, so legacy DEV installs may require a one-time uninstall/reinstall before joining the permanent signing line.

## Release channels

- Stable: `v1.0` from branch `stable`, normal GitHub Release / Latest.
- DEV: `v1.0-dev` from branch `dev`, GitHub Pre-release.

Attach the matching APK to the `v1.0-dev` GitHub prerelease because the app updater consumes the release APK asset. The APK filename does not need to contain the versionCode.

See `DEV-LATEST.json`, `DEV-RELEASE.md`, `HarleysClanForum-1.0-dev-VERIFICATION.txt`, and `STABLE-RELEASE-UPDATE-ITEMS.md` for current metadata and release-preparation notes.
