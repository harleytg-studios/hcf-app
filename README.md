# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Stable identity

- App name: **Harley's Clan Forum**
- Android package: `com.harleytg.forum`
- Current stable version: `1.0`
- Android versionCode: `10000032`
- GitHub tag: `v1.0`
- Release type: normal GitHub Release marked **Latest**
- Update channel: Stable only

## Native UI

The 1.0 line includes the refreshed native UI, Follow phone/Day/Night themes, performance profiles with Auto as the default, notification-count work, simplified sharing, account/identity fixes, diagnostics/log cleanup, update/install fixes, and profile-avatar fit corrections.

## Update behavior

The stable app checks normal GitHub Latest releases from this repository and ignores Dev prereleases. Android still requires the user to confirm APK installation.

## Repository layout

- `.github/workflows/` — Stable build automation
- `source code/` — authoritative buildable Stable Android source
- `STABLE-RELEASE.md` — Stable release details
- `STABLE-LATEST.json` — machine-readable Stable release metadata
- `build-output/` — workflow-published APK/build pointer when present

The branch no longer keeps duplicate source ZIP snapshots beside the extracted source tree. Git history and GitHub Releases provide historical snapshots without cluttering the working branch.

## Signing migration

The current permanent Stable signing certificate SHA-256 is:

`D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

The previously published v0.3.0 APK used a different Android Debug certificate. Android therefore cannot perform an in-place update from that legacy signer to the current permanent signing line. Users on the legacy signer need a one-time uninstall/reinstall unless the old private key is recovered.

All future `com.harleytg.forum` releases should remain on the current permanent Stable signing certificate. Never commit private signing keys to this public repository.

See `STABLE-RELEASE.md` and `STABLE-LATEST.json` for current release metadata and artifact hashes.
