# Harley's Clan Forum Android App — DEV

Development/Beta branch for the Harley's Clan Forum Android app.

## Current build

- App name: **Harley's Clan Forum [Beta]**
- Branch: `dev`
- Android package: `com.harleytg.forum.dev`
- Version name: `1.0 (10000035)`
- Version code: `10000035`
- Internal build: `88`
- Minimum SDK: `26`
- Target SDK: `34`
- Source directory: [`source code/`](./source%20code)

## Beta/DEV v2 signing line

The current source uses the Beta/DEV v2 signing identity.

- Key alias: `hcf-beta-v2`
- Expected signer SHA-256: `93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63`
- APK signing: v2 + v3

The build script rejects a different signing certificate so Beta/DEV builds do not accidentally move to another signing identity.

## Repository layout

This branch is Android-only and intentionally minimal:

- `source code/AndroidManifest.xml` — Android package/version/component manifest.
- `source code/src/` — Java source.
- `source code/res/` — Android resources and launcher assets.
- `source code/assets/` — bundled runtime assets.
- `source code/app-links/` — deployable Digital Asset Links files.
- `source code/build-release.sh` — local Android build/signing script.
- `source code/APP-LINKS-SETUP.md` — App Links deployment notes.
- `source code/BUILD-NOTES.md` — current build notes.
- `source code/RELEASE-SIGNING.md` — signing-line notes.
- `source code/V10000035-ANDROID-APP-INFO.md` — current Android build metadata.

Historical patch notes, old test-build notes, duplicate branding files, temporary artifacts, and iOS files/workflows are intentionally excluded from the active `dev` branch.
