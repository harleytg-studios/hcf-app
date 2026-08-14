# Harley's Clan Forum 0.2.1

Stable security and permissions update.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.1`
- Android versionCode: `2019999`
- Internal build: `19`
- GitHub tag: `v0.2.1`
- Channel: **Stable / GitHub Latest**

## Changes
- Replaced the launcher/startup/native UI artwork with the new 1254×1254 HTG puppy icon.
- Added first-open permission setup for Android notifications and secure app-update installation access.
- The permission explanation explicitly states that location, contacts, microphone, camera, and broad storage access are not requested.
- Added a Permissions & Security card to App Settings.
- Disabled stable WebView debugging, geolocation, JavaScript pop-up windows, file URL access, file-to-network access, and mixed HTTP content.
- Keeps Android Safe Browsing enabled when available, blocks SSL-error bypasses, blocks third-party WebView cookies, uses HTTPS-only network security, and keeps app backup disabled.
- Update APK download URLs are restricted to the official `markhitchk/hcf-app` GitHub release-download path.
- Before Android's installer is opened, an update APK is verified for package name, a newer versionCode, and the same signing certificate as the installed app.
- Automatic update-download cleanup after successful replacement remains enabled.

## Integrity
- APK SHA-256: `54b6905ba7eb8ab01e7eab6822a5fcdbd5c12fbe4bcfb55f9834eb5bc0d60344`
- Source ZIP SHA-256: `c3834d495f4dd0bea7c8bbb63073d344a19bad44723d9e5eb7741989f4ef931e`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
