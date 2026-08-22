# Harley's Clan Forum 1.0 — Stable v10000092

Stable source-preparation metadata for the Harley's Clan Forum Android app.

## Release identity

- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `1.0 (10000092)`
- Android versionCode: `10000092`
- Internal build: `112`
- Channel: **Stable**
- Target SDK: `34`
- Compile SDK: `35`
- Branding: original blue/cyan Stable logo and `[Stable]` build badge

## Promotion boundary

The active runtime and feature source is promoted from `dev` commit `2ea85ab`
without changing the Development/Beta branch. Stable-specific identity is locked
through the manifest, Java package, resources, updater, local release script, and
CI release gates.

The Stable updater uses the official non-prerelease GitHub release endpoint. It
rejects Beta, Dev, Preview, Debug, and unsigned APK assets, then verifies the APK
versionCode, exact SHA-256, package name, and signing-certificate lineage. A
different APK hash can identify a revised release with the same versionCode.

## Stable signing line

- Key alias: `hcf-stable-v2`
- Certificate SHA-256:
  `77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`
- Signing compatibility: v1 + v2 + v3, with v4 sidecar generation

The private Stable key is not stored in this repository. APK and source hashes
remain pending until the production artifacts are generated and signed; this
source update does not fabricate release hashes or publish an APK.
