# Harley's Clan Forum 0.2.5

Stable identity-card and Account Security integration release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.5`
- Android versionCode: `2059999`
- Internal build: `23`
- GitHub tag: `v0.2.5`
- Channel: **Stable / GitHub Latest**

## Changes
- Reworked the drawer identity area as a clearer dedicated card directly above the **FORUM** section.
- The card shows the current user's forum avatar, display name, `@username`, role/group summary and connected sign-in labels.
- Removed the visible `User #...` label from the identity card and detailed identity screen.
- Fixed profile-avatar flickering by keeping the loaded bitmap in memory and no longer resetting to the HTG fallback image on every identity sync.
- Duplicate network requests for the same avatar URL are suppressed; if the avatar URL changes, the old image stays visible until the new one is ready.
- Added an **Account Security** button below the identity card and another button on **My Forum Identity**.
- Signed-in users are routed to `/u/<profile>/security` on the active forum host.
- While the current user's security page is open, the native bridge stores only a safe summary: visible signed-in session count, whether the current session is marked active, connected-provider labels explicitly shown as connected/linked/disconnect states, presence of password/email/two-factor controls, route path and sync time.
- Access-token values, passwords, recovery codes, cookies, provider account IDs, session contents and per-device session names are never stored.
- Guest sessions continue to use the exact `Guest_Protocol` label.
- Existing HTTPS-only WebView security, SSL blocking, Safe Browsing, matching-signature update verification, live forum updates, per-cookie inspection, permission onboarding, telemetry opt-in and updater-owned APK cleanup remain enabled.

## Integrity
- APK SHA-256: `89ffa37d715769c00223fd5e1b682032bb321854f1e4561629205526513c7606`
- Source ZIP SHA-256: `260ddbf9fb5b84d9d52eb0eacc5a91733bbae5ee1d318f0dcbf7649844e46f76`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
