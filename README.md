# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Current build

- App name: **Harley's Clan Forum**
- Branch: `stable`
- Android package: `com.harleytg.forum`
- Version name: `1.0 (10000092)`
- Version code: `10000092`
- Internal build: `112`
- Minimum SDK: `26`
- Target SDK: `34`
- Compile SDK: `35`
- Source directory: [`source code/`](./source%20code)

This source promotes the current `dev` runtime and feature set from commit
`2ea85ab` while retaining Stable identity. It does not alter the `dev` branch or
replace its separate Development/Beta package, badges, updater, or signer.

## Stable identity boundaries

- Stable launcher artwork: original blue/cyan HTG puppy badge.
- Stable build label: `Harley's Clan Forum v1.0 [Stable]`.
- Stable update feed: GitHub's latest official, non-prerelease release only.
- Stable APK selection rejects Beta, Dev, Preview, Debug, and unsigned assets.
- Stable package and updater are locked to `com.harleytg.forum`.
- Stable signer alias: `hcf-stable-v2`.
- Expected signer SHA-256:
  `77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`.

`build-release.sh` rejects another package or signing certificate. Never commit
the Stable V2 private JKS, password, or other private signing material.

## Update verification

The Stable updater compares the numeric Android `versionCode` read from the APK,
not its filename or GitHub tag. It also checks the exact APK SHA-256. A release
asset with the same versionCode is offered only when its SHA-256 differs from the
installed APK. Before Android's installer opens, HCF rechecks the package,
versionCode, SHA-256, and signing-certificate lineage.

## Source and release gates

The branch contains the active Android source, two read-only GitHub Actions
verification workflows, and their static release gates. The v10000092 workflow
compiles and aligns an unsigned APK, checks Stable package/version/channel
identity, protects the approved HCF Alerts UI, and rejects Development/Beta badge
leakage and incomplete/decompiler-stub source.

Production signing remains local. The shared Stable + Dev Digital Asset Links
source is [`configs/app-links/assetlinks.json`](./configs/app-links/assetlinks.json);
its canonical location is the `main`-branch path `configs/app-links/assetlinks.json`.
