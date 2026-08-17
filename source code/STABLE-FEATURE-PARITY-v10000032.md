# Harley's Clan Forum Stable v1.0 — versionCode 10000032

This Stable source update ports only the newer Beta updater and forum-message notification improvements onto the Stable application identity.

## Added / improved

- Update detection reads the downloaded APK's embedded Android `versionCode` instead of relying only on the GitHub tag or APK filename.
- Release assets are tracked by asset identity so a replaced APK under the same release/tag is treated as a new update candidate.
- Optional automatic Android installer handoff after a verified update download (enabled by default).
- "Allow from this source" flow resumes the pending verified installer when permission is granted.
- Install-ready notification remains as a fallback when Android cannot open the installer automatically.
- Update settings show installed and available versionCode values.
- Update status uses versionCode-aware newer/equal/older comparison.
- Private-message / Messenger notification payloads are recursively unwrapped to readable user text instead of displaying raw JSON/API containers.
- Private-message notifications use `New message from <sender>` and route to `/conversations/<conversation_id>` when an ID is available.
- Safe private-message fallback text is used when a readable body cannot be extracted.

## Intentionally unchanged

- Stable application ID: `com.harleytg.forum`
- Public version name: `1.0`
- Stable update channel only
- Stable app name, icons, and branding
- Stable signing certificate expectation
- Existing Stable features and UI outside the updater/notification changes

## Build identity

- Version: `1.0`
- versionCode: `10000032`
- Channel: Stable
- Target SDK: 34


## Version strings

- Public version: `v1.0`
- Android versionName: `1.0`
- Android versionCode: `10000032`
- Internal build: `77`
- Channel: `Stable`
- Display metadata: `1.0 • Stable • Build 10000032`
- Release header: `Harley's Clan Forum v1.0 Stable • Build 10000032`
