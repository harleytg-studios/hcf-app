# Harley's Clan Forum 0.2.4

Stable identity-card, telemetry, and installer-cleanup release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.4`
- Android versionCode: `2049999`
- Internal build: `22`
- GitHub tag: `v0.2.4`
- Channel: **Stable / GitHub Latest**

## Changes
- Moved the current forum identity out of the app-title block into a dedicated identity card directly above the **FORUM** section.
- The identity card shows the current user's forum avatar, display name, username, user/role summary and available sign-in connection labels.
- Tapping the identity card opens **My Forum Identity**; the duplicate drawer identity button was removed.
- Guest sessions use the exact label `Guest_Protocol`.
- Connection detection stores provider names only (for example Email, Discord, Google, GitHub, Microsoft, Apple, Facebook or X/Twitter) when the current self-session exposes a matching attribute/relationship. Provider account IDs and tokens are never stored.
- Added optional **Telemetry Services** to App Settings. Telemetry is OFF by default and requires explicit user opt-in.
- Telemetry sends only event type, app version/build/channel, Android API level, active forum host and `SIGNED_IN` vs `Guest_Protocol`. It never sends usernames, display names, emails, user IDs, cookies, tokens, posts, messages or page contents.
- The configured Discord webhook is stored as AES-GCM ciphertext and decrypted only when the installed Stable package has the expected signing certificate. Transport uses HTTPS.
- A direct client webhook can still be recovered by a determined reverse engineer; a server-side relay is recommended if the endpoint must remain completely private.
- Strengthened updater cleanup: `MY_PACKAGE_REPLACED` cleanup remains, and startup now also scans only the updater-owned external-files directory and removes stale APKs for this exact package when their version is no newer than the installed app.
- Pending/running newer updater downloads are preserved. APKs manually downloaded through browsers, ChatGPT or other apps remain user-owned and are not silently deleted.
- Existing HTTPS-only WebView security, SSL blocking, Safe Browsing, matching-signature update verification, live forum updates, per-cookie inspection, permission onboarding and high-resolution HTG branding remain enabled.

## Integrity
- APK SHA-256: `85a51a60d5b1d58fbbd4f4644d4d7cb09780e3845b873ac7bb43010186657904`
- Source ZIP SHA-256: `c0129986f37002331aadf2e3ea0a904c76f6fe9a213e97292df273316e9b6fb3`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
