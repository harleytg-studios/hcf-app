# Harley's Clan Forum Android App — DEV

Development/Beta branch for the Harley's Clan Forum Android app.

## Current build

- App name: **Harley's Clan Forum [Beta]**
- Branch: `dev`
- Android package: `com.harleytg.forum.dev`
- Version name: `1.0 (10000094)`
- Version code: `10000094`
- Internal build: `114`
- Minimum SDK: `26`
- Target SDK: `34`
- Source directory: [`source code/`](./source%20code)

## Beta/DEV v2 signing line

- Key alias: `hcf-beta-v2`
- Expected signer SHA-256: `93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63`
- APK signing: v1 + v2 + v3

`build-release.sh` rejects a different signing certificate to protect in-place Beta/Dev updates. The updater verifies the exact APK SHA-256 as well as package name, versionCode, and signing-certificate lineage. If a release intentionally replaces an APK without changing versionCode, a changed SHA-256 identifies it as a same-version revision; an identical hash is treated as already installed.

## Repository layout

This branch is Android-only and intentionally minimal.

`source code/` contains only active Android build/runtime inputs:

- `AndroidManifest.xml` — package, version, permissions, components, and App Link declarations.
- `src/` — Java source.
- `res/` — Android resources and launcher assets.
- `assets/` — bundled runtime assets.
- `build-release.sh` — local compile, package, align, and signing script.

Historical patch notes, old test-build notes, duplicate branding, deployment helper copies, temporary artifacts, and iOS files are intentionally excluded from the active `dev` branch.

Only the two active read-only GitHub Actions workflows and their release-verification scripts are retained. Generated trigger/output logs, one-time source-patching scripts, and unreferenced Android drawables are excluded.

## Release gates

The v10000094 workflow compiles the complete Java/resource source, packages and aligns an unsigned CI APK, verifies package/version identity, checks the approved three-tile HCF Alerts UI, and rejects decompiler stubs or missing same-version SHA-256 update protections. Production signing remains local so the Beta private key is never stored in GitHub Actions.

The shared Stable + Dev Digital Asset Links source is [`configs/app-links/assetlinks.json`](./configs/app-links/assetlinks.json). Its canonical deployment source is the `main`-branch path `configs/app-links/assetlinks.json`; this Dev release does not modify or rebuild the Stable app.
