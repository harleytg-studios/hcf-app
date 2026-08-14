# Harley's Clan Forum 0.2.6

Stable compact-layout and day/night theme release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.6`
- Android versionCode: `2069999`
- Internal build: `24`
- GitHub tag: `v0.2.6`
- Channel: **Stable / GitHub Latest**

## Changes
- Reduced the normal portrait native app header and secure URL chrome so more forum content is visible.
- Added dedicated landscape dimensions: the native header, URL bar, drawer, identity card, drawer actions and startup branding all become more compact in landscape.
- Rotation now uses normal Activity recreation with WebView state restoration so Android applies the landscape resource set correctly.
- Added app-wide native Day/Night themes across the main shell, App Settings, My Forum Identity, Cookie Data and Diagnostics.
- Theme choices are **Follow phone (Auto)**, **Day (Light)** and **Night (Dark)** and are stored locally.
- The theme control lives in **App Settings → App Interface**.
- Day mode uses a light neutral surface and darker cyan accents for contrast; Night mode keeps the existing HCF dark/cyan appearance.
- Status and navigation bar icon contrast follows the selected native app theme.
- All 0.2.5 identity-card, Account Security, avatar anti-flicker, telemetry opt-in, updater cleanup, live forum updates and security hardening remain included.

## Integrity
- APK SHA-256: `d414bcdf882c5338123eb864d1c14304ceb05bbe47107f785a813633cca6da54`
- Source ZIP SHA-256: `ac51ed9dfd01854e5bcc6a89f5c620eb7983bd46d5edc61c07346e661ae59428`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
