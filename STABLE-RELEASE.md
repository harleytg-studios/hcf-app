# Harley's Clan Forum 0.2.8

Stable telemetry diagnostics and identity-provider cleanup release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.8`
- Android versionCode: `2089999`
- Internal build: `26`
- GitHub tag: `v0.2.8`
- Channel: **Stable / GitHub Latest**

## Changes
- Rebuilt **Telemetry Services** with two levels: **Basic** and **Diagnostics**.
- Diagnostics mode can capture uncaught crashes locally and show a next-launch crash feedback prompt with an HCF report ID.
- Crash reports can include sanitized stack traces and recent app-event breadcrumbs; credential-like values and URL query strings are redacted.
- Added independent settings for automatic crash reports, ask-before-send, automatic WebView/update error reports, forum identity sharing, email sharing, device manufacturer/model sharing, and sanitized route sharing.
- Added **Send Diagnostic Feedback**, **Preview Telemetry Report**, **View Report History**, and **Clear Local Telemetry Reports** controls.
- Forum identity is a separate opt-in and remains OFF by default; email requires its own additional opt-in.
- Passwords, cookies, access/session tokens, recovery codes, provider IDs, posts, messages, and page contents are never included in telemetry reports.
- Removed Google completely from the native identity/provider UI. Account Identity now shows only **Email** and **Discord** provider chips/icons.
- Keeps Account Security directly below Account Identity, compact portrait/landscape chrome, Follow phone/Day/Night themes, avatar anti-flicker, live forum updates, security hardening, and updater-owned APK cleanup.

## Integrity
- APK SHA-256: `2c461a9f29bc9558eee6c57fd1e1e0ee274f461778d63c382128bb0d6b87c41f`
- Source ZIP SHA-256: `4e860d48614ef445b1fa5b59cc1a0f1bfb31421c05024eaecfea0eda42128cfd`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
