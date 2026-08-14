# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.8`
- GitHub tag: `v0.2.8`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Native UI

The app uses compact native chrome in portrait and an extra-compact landscape layout. Native app screens support **Follow phone (Auto)**, **Day (Light)** and **Night (Dark)** themes.

## Forum identity

The drawer contains a dedicated Account Identity card for the current signed-in Flarum user, with **Account Security** directly below it. Native provider chips/icons now show only **Email** and **Discord**. Google has been removed from the native identity UI. Provider state is inferred only from the current user's self-session/security summary; provider account IDs and OAuth/access tokens are not stored. Guest sessions use `Guest_Protocol`.

The app also provides an Account Security shortcut to the current user's `/u/<profile>/security` route. While that page is open, the app stores only a safe security summary: session counts, current-session detection, connected provider labels, capability flags for password/email/two-factor controls, and sync time. Access-token values, passwords, recovery codes, cookie values, provider account IDs and individual session/device details are excluded.

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and does not install Dev prereleases. Android still requires the user to confirm APK installation. Updater-owned installer APKs are temporary and are cleaned after successful replacement or during stale-update recovery. APKs manually downloaded through another app remain user-owned.

## Telemetry & diagnostics

Telemetry Services are optional and OFF by default. **Basic** mode sends coarse app-health events. **Diagnostics** mode can add crash reports, sanitized stack traces, recent app-event breadcrumbs, and optional WebView/update error reporting. Crash reports receive HCF report IDs and can be reviewed on the next launch before sending.

Identity sharing is a separate opt-in. Users can independently choose whether reports may include forum identity, email, device manufacturer/model, or a sanitized forum route. The app also provides manual diagnostic feedback, report preview, local report history, and local-report cleanup controls. Passwords, cookies, session/access tokens, recovery codes, provider IDs, posts, messages and page contents are excluded.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
