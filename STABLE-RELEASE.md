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

The active application/runtime implementation is promoted from current `dev`
commit `5c26f5f6d4dfc080c5e9167dada21758bf4653ea` without changing the Development/Beta
branch. Stable retains its package, version/tag, production release channel,
artwork, signer, and deployment metadata.

The checked-in consolidated `Hcf*` runtime remains directly comparable with
`dev`. `source code/stable-build-overlay.py` applies Stable-only package/channel/
version/updater labels to a temporary compile tree. That allows new Dev features
to be promoted without restoring the obsolete split Stable runtime or leaking
Development/Beta identity into the Stable APK.

The Stable updater accepts official non-prerelease GitHub releases only and
rejects Beta, Dev, Preview, Debug, and unsigned APK assets. It retains the current
Dev versionCode + SHA-256 update mechanism, downloaded-package verification, and
signing-certificate-lineage protection. A different APK hash can identify a
revised Stable release with the same versionCode.

## Stable signing line

- Key alias: `hcf-stable-v2`
- Certificate SHA-256:
  `77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`
- Signing compatibility: v1 + v2 + v3, with v4 sidecar generation

The private Stable key is not stored in this repository. The Discord observation
webhook is also build-time input and is encrypted into a generated temporary
binding. APK/source hashes remain pending until production artifacts are built
and signed; this source promotion does not fabricate release hashes or publish an
APK.
