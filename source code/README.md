# Stable source code

This folder is the authoritative buildable source for the **Stable** Harley's Clan Forum Android app.

## Current build

- Version: `1.0`
- versionCode: `10000032`
- Internal build: `77`
- Package: `com.harleytg.forum`
- Channel: `Stable`

## Layout

- `AndroidManifest.xml` — Android manifest and app components
- `src/` — Java source under `com.harleytg.forum`
- `res/` — layouts, drawables, themes, strings, launcher icons, and other resources
- `assets/` — bundled runtime assets/configuration
- `branding/` — Stable branding source assets
- `app-links/` — Android App Links verification files
- `build-release.sh` — release APK build/sign/verify script
- `BUILD-NOTES.md` — current Stable build notes
- `RELEASE-SIGNING.md` — signing guidance and certificate information
- `APP-LINKS-SETUP.md` — App Links deployment notes
- `STABLE-FEATURE-PARITY-v10000032.md` — Stable feature-parity record for this build

## Repository hygiene

Generated local build files belong in `out/` and are ignored by this folder's `.gitignore`. Private signing keys, keystores, passwords, and local credentials must never be committed.

Old one-off fix verification files and per-patch note files were removed from the active source tree during cleanup. Their history remains available through Git, while current release information lives in the branch-level `README.md`, `STABLE-RELEASE.md`, and `STABLE-LATEST.json` files.
