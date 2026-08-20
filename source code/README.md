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

Promote normal user-facing functionality from newer Beta/Dev builds only when it is not explicitly designated Beta/Dev-only. Stable keeps production package identity, Stable branding, Stable-only update-channel defaults, and Dev/Beta test UI disabled.

See `STABLE-FEATURE-PARITY-v10000072.md` for the promoted feature list and Beta/Dev-only exclusions.

## Layout

- `AndroidManifest.xml` — Android manifest and Stable version identity
- `src/` — Java source/reference tree under `com.harleytg.forum`
- `res/` — layouts, drawables, themes, strings, launcher icons, and resources
- `assets/` — bundled runtime assets/configuration
- `branding/` — Stable branding source assets
- `app-links/` — Android App Links verification files
- `build-release.sh` — Stable V2 release build/sign/verify script
- `BUILD-NOTES.md` — current build details
- `RELEASE-SIGNING.md` — Stable V2 signing guidance
- `STABLE-FEATURE-PARITY-v10000072.md` — current promotion/parity record

## Signing

Stable v10000072 continues the established **Stable V2** signing line. Local release builds must use `HCF-Stable-v2.jks` with alias `hcf-stable-v2`. The private keystore/password are intentionally not stored in this public repository.

## Repository hygiene

Generated local build files belong in `out/`. Private signing keys, keystores, passwords, tokens, and credentials must never be committed.
