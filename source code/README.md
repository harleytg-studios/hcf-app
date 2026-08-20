# Stable source code

This folder is the Stable source/reference tree for the **Stable** Harley's Clan Forum Android app.

## Current build

- Version: `1.0 (10000072)`
- versionCode: `10000072`
- Internal build: `100`
- Package: `com.harleytg.forum`
- Channel: `Stable`
- Default update channel: `stable`

## v10000072 promotion policy

Promote user-facing functionality from newer Beta/Dev builds only when it is not explicitly designated Beta/Dev-only. Stable keeps production package identity, Stable branding, Stable-only update-channel defaults, and Dev/Beta test UI disabled.

See `STABLE-FEATURE-PARITY-v10000072.md` for the current promoted feature list and the Beta/Dev-only exclusions.

## Layout

- `AndroidManifest.xml` — Android manifest and Stable version identity
- `src/` — Java source under `com.harleytg.forum`
- `res/` — layouts, drawables, themes, strings, launcher icons, and other resources
- `assets/` — bundled runtime assets/configuration
- `branding/` — Stable branding source assets
- `app-links/` — Android App Links verification files
- `build-release.sh` — release APK build/sign/verify script
- `BUILD-NOTES.md` — Stable build notes
- `RELEASE-SIGNING.md` — signing guidance and certificate information
- `STABLE-FEATURE-PARITY-v10000072.md` — current Stable promotion/parity record

## Signing

Stable v10000072 starts the local Stable signing line. The private key is intentionally **not stored in this public repository**. Local signed builds must use the retained v10000072 `.p12` and password from private storage.

## Repository hygiene

Generated local build files belong in `out/`. Private signing keys, keystores, passwords, tokens, and local credentials must never be committed.
