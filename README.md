# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.7`
- GitHub tag: `v0.2.7`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Native UI

The app uses compact native chrome in portrait and an extra-compact landscape layout. Native app screens support **Follow phone (Auto)**, **Day (Light)** and **Night (Dark)** themes.

## Forum identity

The drawer contains a dedicated Account Identity card for the current signed-in Flarum user. **Account Security** sits directly below that card. Sign-in methods use compact provider chips with icons for **Email**, **Google (unsupported)** and **Discord**. Email/Discord state is inferred only from the current self-session/security summary; provider account IDs and OAuth/access tokens are not stored. Guest sessions use `Guest_Protocol`.

The app also provides an Account Security shortcut to the current user's `/u/<profile>/security` route. While that page is open, the app stores only a safe security summary: session counts, current-session detection, explicitly connected provider labels, capability flags for password/email/two-factor controls, and sync time. Access-token values, passwords, recovery codes, cookie values, provider account IDs and individual session/device details are excluded.

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and does not install Dev prereleases. Android still requires the user to confirm APK installation. Updater-owned installer APKs are temporary and are cleaned after successful replacement or during stale-update recovery. APKs manually downloaded through another app remain user-owned.

## Telemetry

Telemetry Services are optional and OFF by default. When a user opts in, only minimal app-health information is sent: event type, app version/build/channel, Android API level, active forum host and signed-in-vs-guest state. Account names, email addresses, user IDs, cookies, tokens and forum content are excluded.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
