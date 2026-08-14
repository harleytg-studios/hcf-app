# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.3`
- GitHub tag: `v0.2.3`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and does not install Dev prereleases. Android still requires the user to confirm APK installation. Updater-owned installer APKs are temporary: completed old update packages are cleaned before a newer download, and after a successful in-place update the app removes its DownloadManager row and app-owned installer APK. APKs manually downloaded through another app remain user-owned.

## Forum identity

The native identity layer mirrors only the current signed-in Flarum session user. It can show that user's display name, username, ID, profile slug, groups/roles, counts and other self-visible profile fields. It does not collect passwords, access/session tokens, or private account information for other forum users.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
