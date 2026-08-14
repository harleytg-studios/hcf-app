# Harley's Clan Forum 0.2.2

Stable startup branding correction.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.2`
- Android versionCode: `2029999`
- Internal build: `20`
- GitHub tag: `v0.2.2`
- Channel: **Stable / GitHub Latest**

## Changes
- Fixed the round/masked logo that could appear during app startup.
- Android 12+ now uses a transparent system splash icon and immediately hands off to the app's own square HTG startup screen, avoiding the OS icon mask.
- Android 8-11 startup now draws the full HTG app artwork directly instead of the adaptive launcher mipmap.
- Removed the manifest `roundIcon` override so the app no longer explicitly advertises a separate round icon.
- The supplied 1254×1254 HTG puppy artwork remains the launcher artwork and the visible custom startup logo.
- All 0.2.1 permissions, security, live forum updates, cookie inspection, automatic updater, and installer cleanup behavior remain included.

## Integrity
- APK SHA-256: `1c5c422a208dad6c73b3958244882eedcc556f26b212bbbeede334b3339178fa`
- Source ZIP SHA-256: `bbc0b0cc07808a305d7dd9e99faf188a8c174415d99fe0fb9d198c53dda9dfd4`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
