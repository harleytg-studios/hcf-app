# Harley's Clan Forum 0.2.7

Stable identity provider UI refinement release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.7`
- Android versionCode: `2079999`
- Internal build: `25`
- GitHub tag: `v0.2.7`
- Channel: **Stable / GitHub Latest**

## Changes
- Moved **Account Security** directly below the Account Identity card in the drawer.
- Added compact provider chips with icons for **Email**, **Google (unsupported)** and **Discord**.
- Email and Discord chips reflect the current signed-in user's self-session connection state when that state is exposed by Flarum/security-page sync.
- Google is explicitly labeled unsupported rather than being shown as a usable sign-in provider.
- Added the same icon/status treatment to **My Forum Identity**.
- Provider account IDs and OAuth/access tokens are never stored by this UI.
- Keeps the 0.2.6 compact portrait/landscape layout and **Follow phone / Day / Night** native theme system.
- Existing avatar anti-flicker, Account Security sync, updater cleanup, live forum updates, telemetry opt-in and security hardening remain included.

## Integrity
- APK SHA-256: `5762f3a209abdba7d98db3e7f64b87abf7327d97294005d5d4aa10224c3857a6`
- Source ZIP SHA-256: `43fdf1dabe34f88292cb28e01686dbddc3068e4f6279750610ef86b98daa6c66`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
