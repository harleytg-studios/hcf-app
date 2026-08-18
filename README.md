# Harley's Clan Forum Android App — DEV

Development/Beta branch for the Harley's Clan Forum Android app.

## Current patch

- App name: **Harley's Clan Forum [Beta]**
- Branch: `dev`
- Android package: `com.harleytg.forum.dev`
- Version name: `1.0`
- Version code: `10000033`
- Current patch/build: **v10000033 v2-key**
- Minimum SDK: `26`
- Target SDK: `34`
- Source directory: [`source code/`](./source%20code)

## Beta/DEV v2 signing line

The current source is configured for the Beta/DEV v2 signing identity used by the v10000033 build.

- Key alias: `hcf-beta-v2`
- Expected signer SHA-256: `93:D4:9B:F9:A8:77:C7:CF:B1:B3:7F:90:64:BD:95:5C:D6:7B:D7:DD:8D:B7:3A:9E:3F:76:6B:59:C4:BC:CE:63`
- APK signing: v2 + v3

The build script rejects a different signing certificate so Beta/DEV builds do not accidentally move to another signing identity.

## Repository layout

The `dev` branch intentionally keeps the root minimal:

- `source code/` — canonical Android source for the current Beta/Dev patch.
- `README.md` — current branch/build information.

Old source ZIPs, stale release metadata, outdated verification files, and Stable-only release notes are intentionally not kept in the `dev` root after their contents are no longer needed.

## Current source status

`source code/AndroidManifest.xml` is the source of truth for the installed Android version and currently declares `versionName="1.0"` and `versionCode="10000033"` for `com.harleytg.forum.dev`.

The current restored source corresponds to **HCF Beta/Dev v10000033 v2-key**.
