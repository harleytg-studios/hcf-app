# Harley's Clan Forum Android App

Official Android app release repository for Harley's Clan Forum.

## Update channels

The app uses GitHub Releases for updates and maps them to two source branches:

- **Stable** — source branch `stable`; publish a normal GitHub Release and mark it as the repository's **Latest** release.
- **Dev** — source branch `dev`; publish the GitHub Release as a **Pre-release**.

Attach the built `.apk` file to each GitHub Release. The Android app checks this repository directly, compares the release tag with its installed version, and offers the APK asset when a newer version is available.

### Recommended release tags

- Stable: `v0.1.5`
- Dev: `v0.1.7-dev`

The `main` branch is the repository landing/documentation branch. Release source should be maintained in `stable` or `dev` depending on channel.

## Live forum updates

Dev builds include automatic live-refresh support for forum pages. The WebView checks Flarum's API for changes while the app is visible and refreshes the current forum page only when new discussion/post activity is detected. It avoids refreshing while the user is typing or using the composer.
