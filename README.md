# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `0.2.0`
- GitHub tag: `v0.2.0`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Update behavior

The stable app checks normal GitHub Latest releases from this repository. It does not install Dev prereleases. Android still requires the user to confirm APK installation. After a successful in-place update, the app removes the downloaded installer APK and its DownloadManager entry.

## Signing

All future `com.harleytg.forum` APKs must use the same persistent Stable signing certificate established by 0.2.0. Never commit the private signing key to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for the current release metadata.
