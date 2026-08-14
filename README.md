# Harley's Clan Forum Android App

Official Android app release repository for Harley's Clan Forum.

## App packages

- **Stable:** `com.harleytg.forum`
- **Dev / prerelease:** `com.harleytg.forum.dev`

Both use the same app name, **Harley's Clan Forum**, and can be installed side-by-side because their Android package names are different.

## Update channels

The app uses GitHub Releases for updates and maps them to two source branches:

- **Stable** — source branch `stable`; publish a normal GitHub Release and mark it as the repository's **Latest** release.
- **Dev** — source branch `dev`; publish the GitHub Release as a **Pre-release**.

Attach the built `.apk` file to each GitHub Release. Each app checks only its own release channel and compares the release tag with its installed SemVer version.

### Version scheme

- Stable: `MAJOR.MINOR.PATCH`, for example `v0.2.0`
- Dev: `MAJOR.MINOR.PATCH-dev.N`, for example `v0.2.0-dev.1`

Android `versionCode` uses the `semver-stage-v1` ordering scheme so prereleases remain below the matching stable release.

## Current versions

- Stable: `v0.2.0` — package `com.harleytg.forum`
- Dev: `v0.2.0-dev.1` — package `com.harleytg.forum.dev`

## Automatic updates

The apps can check GitHub Releases automatically and download a newer APK. Android still requires user confirmation to install an APK. After a successful in-place update, the app cleans up the downloaded installer APK and its DownloadManager entry.

## Live forum updates

The Android WebView includes foreground live-refresh support for forum pages. It checks Flarum activity while the app is visible and refreshes relevant pages when new discussion/post activity is detected, while avoiding refreshes during typing, composer use, or open modals.

The `main` branch is the repository landing/documentation branch. Release source/metadata should be maintained in `stable` or `dev` depending on channel.
