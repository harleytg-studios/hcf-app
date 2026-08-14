# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.4`
- GitHub tag: `v0.2.4`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and does not install Dev prereleases. Android still requires the user to confirm APK installation. Updater-owned installer APKs are temporary: completed old update packages are cleaned before a newer download, after a successful in-place update the app removes its DownloadManager row and app-owned installer APK, and startup performs a recovery cleanup for stale updater APKs whose version is no newer than the installed app. APKs manually downloaded through another app remain user-owned.

## Forum identity

The native identity layer mirrors only the current signed-in Flarum session user. The drawer uses a dedicated identity card with that user's avatar, display name, username, roles and self-visible connection labels. Guest sessions use `Guest_Protocol`. Provider labels never include provider account IDs or tokens, and the app does not collect private account information for other forum users.

## Telemetry

Telemetry Services are optional and OFF by default. When a user opts in, only minimal app-health information is sent: event type, app version/build/channel, Android API level, active forum host and signed-in-vs-guest state. Account names, email addresses, user IDs, cookies, tokens and forum content are excluded. The configured webhook is encrypted at rest in the APK and bound to the Stable signing certificate; a server-side relay is still preferred when an endpoint must remain completely secret from reverse engineering.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
