# Harley's Clan Forum Android App — DEV

Development/Beta branch for the Harley's Clan Forum Android app.

## Current build

- App name: **Harley's Clan Forum [Beta]**
- Branch: `dev`
- Android package: `com.harleytg.forum.dev`
- Version name: `1.0 (10000090)`
- Version code: `10000090`
- Internal build: `110`
- Minimum SDK: `26`
- Target SDK: `34`
- Source directory: [`source code/`](./source%20code)

## Beta/DEV v2 signing line

- Key alias: `hcf-beta-v2`
- Expected signer SHA-256: `93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63`
- APK signing: v1 + v2 + v3

`build-release.sh` rejects a different signing certificate to protect in-place Beta/Dev updates.

## Repository layout

This branch is Android-only and intentionally minimal.

`source code/` contains only active Android build/runtime inputs:

- `AndroidManifest.xml` — package, version, permissions, components, and App Link declarations.
- `src/` — Java source.
- `res/` — Android resources and launcher assets.
- `assets/` — bundled runtime assets.
- `build-release.sh` — local compile, package, align, and signing script.

Historical patch notes, old test-build notes, duplicate branding, deployment helper copies, temporary artifacts, and iOS files are intentionally excluded from the active `dev` branch.
