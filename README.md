# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.6`
- GitHub tag: `v0.2.6`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Native UI

The app now has compact native chrome in portrait and a dedicated extra-compact landscape layout so the forum keeps more vertical space. Native app screens support three local theme modes: **Follow phone (Auto)**, **Day (Light)** and **Night (Dark)**. Theme selection is available in App Settings and applies to the main shell, Settings, Identity, Cookie Data and Diagnostics screens.

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and does not install Dev prereleases. Android still requires the user to confirm APK installation. Updater-owned installer APKs are temporary: completed old update packages are cleaned before a newer download, after a successful in-place update the app removes its DownloadManager row and app-owned installer APK, and startup performs a recovery cleanup for stale updater APKs whose version is no newer than the installed app. APKs manually downloaded through another app remain user-owned.

## Forum identity

The native identity layer mirrors only the current signed-in Flarum session user. The drawer uses a dedicated identity card with that user's avatar, display name, username, roles and self-visible connection labels. `User #...` is not shown in the native identity UI. Guest sessions use `Guest_Protocol`. Profile-avatar loading is cached in memory to avoid repeated flicker during identity refreshes.

The app also provides an **Account Security** shortcut to the current user's `/u/<profile>/security` route. While that page is open, the app stores only a safe security summary: session counts, current-session detection, explicitly connected provider labels, capability flags for password/email/two-factor controls, and sync time. Access-token values, passwords, recovery codes, cookie values, provider account IDs and individual session/device details are excluded.

## Telemetry

Telemetry Services are optional and OFF by default. When a user opts in, only minimal app-health information is sent: event type, app version/build/channel, Android API level, active forum host and signed-in-vs-guest state. Account names, email addresses, user IDs, cookies, tokens and forum content are excluded. The configured webhook is encrypted at rest in the APK and bound to the Stable signing certificate; a server-side relay is still preferred when an endpoint must remain completely secret from reverse engineering.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
