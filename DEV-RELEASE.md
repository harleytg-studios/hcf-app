# Harley's Clan Forum 1.0 DEV

Development-channel build for Harley's Clan Forum Android.

## Build identity

- Application ID: `com.harleytg.forum.dev`
- Public version: `1.0`
- Version code: `10000017`
- Internal build: `73`
- Channel: `dev`
- Expected prerelease tag: `v1.0-dev`

## Included update set

This DEV variant carries the newer 1.0 app work, including the system-wide Day Theme changes, performance profiles with Auto as the default, notification controls/count badge work, share chooser cleanup, account/identity fixes, diagnostics/log UI changes, update/install handling, and the profile-avatar fit correction.

The old v0.1.7 development logo and launcher icon assets are preserved for the development build.

## Signing

The APK uses the Harley's Clan Forum Development certificate subject:

`CN=Harley's Clan Forum Development, O=HTG, C=US`

Current development certificate SHA-256:

`17:4F:64:C0:F5:29:FE:53:54:80:E2:EB:6D:E6:BF:01:75:D1:B8:31:4B:74:CF:0C:FF:69:38:F8:E9:2A:13:38`

The original v0.1.7 development certificate fingerprint is different because its private signing key was not available. As a result, this build cannot install as an in-place update over an APK signed with that original certificate.

## Generated artifacts

- `HarleysClanForum-1.0-dev.apk`
  - SHA-256: `f97072bf84677d613263186fe59fa87af36ae9045c984a8868bee596406e1e24`
- `HarleysClanForum-1.0-dev-source.zip`
  - SHA-256: `a82c3e97edcb30071b4f6cd3b4621ae515942656eb59ef69ebff880a8ac83505`
- `HarleysClanForum-1.0-dev-VERIFICATION.txt`

### Repository upload status

`DEV-LATEST.json`, this release note, and the verification report are committed to the `dev` branch. The current connected GitHub API exposes text/blob metadata operations but no local-file parameter for transferring the generated APK and ZIP bytes, so the APK and source ZIP are not falsely represented as uploaded branch files.
