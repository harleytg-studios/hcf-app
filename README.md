# Harley's Clan Forum Android App — Stable

Stable Android release branch for Harley's Clan Forum.

## Current build

- App name: **Harley's Clan Forum**
- Branch: `stable`
- Android package: `com.harleytg.forum`
- Version name: `1.0 (10000098)`
- Version code: `10000098`
- Internal build: `112`
- Release channel: **Stable**
- Minimum SDK: `26`
- Target SDK: `34`
- Compile SDK: `35`
- Source directory: [`source code/`](./source%20code)

This branch promotes the current `dev` application/runtime implementation through
Dev commit `5c26f5f6d4dfc080c5e9167dada21758bf4653ea` while retaining Stable identity.
The merge is intentionally not a byte-for-byte copy of `dev`.

## Functional source of truth

The current consolidated `Hcf*` source set is promoted from `dev`, including the
latest setup/onboarding flow, native forum shell, updater hash verification,
notification UI and delivery logic, runtime domain handling, session persistence,
native `/app/settings` routing, link safety, performance/runtime improvements,
ban-system integration, and Discord observation binding.

Stable uses [`source code/stable-build-overlay.py`](./source%20code/stable-build-overlay.py)
to apply only channel-specific identity to a temporary compile tree. The promoted
functional source remains directly comparable with `dev`; the overlay changes the
package/channel/version/updater policy and user-visible Stable labels without
replacing the Dev implementation with the older Stable split-source runtime.

## Stable identity boundaries

- Stable launcher artwork: original blue/cyan HTG puppy badge.
- Stable package: `com.harleytg.forum`.
- Stable build label: `Harley's Clan Forum v1.0 [Stable]`.
- Stable version/build identity remains `1.0 (10000098)` / internal build `112`.
- Stable update feed accepts official non-prerelease releases only.
- Stable APK selection rejects Beta, Dev, Preview, Debug, and unsigned assets.
- Stable package/update channel is locked; it does not switch to the Dev feed.
- Stable APK output name: `HCF-Stable-v10000098.apk`.
- Stable signer alias: `hcf-stable-v2`.
- Expected Stable signer SHA-256:
  `77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`.

`build-release.sh` rejects another package or signing certificate. Never commit
the Stable private JKS, password, Discord webhook, or other private signing/build
credentials.

## Update verification

The Stable updater compares the numeric Android `versionCode` extracted from the
release APK rather than relying on its filename or GitHub tag. A release asset
with the same versionCode is offered only when its SHA-256 differs from the
installed APK. Before opening Android's installer, HCF rechecks the package,
versionCode, SHA-256, and signing-certificate lineage.

## Runtime configuration

The primary forum host is `forum.harleytg.com` and the backup is
`harleysclan.freeflarum.com`. Runtime domain configuration and shared Digital
Asset Links use the canonical `main`-branch configuration. The shared Stable +
Dev Digital Asset Links source is
[`configs/app-links/assetlinks.json`](./configs/app-links/assetlinks.json).

The current Dev observation feature requires `DISCORD_WEBHOOK_URL` at build time.
The release script encrypts that value into a generated temporary Java binding;
the plaintext webhook is not committed. Production signing additionally requires
the Stable keystore/password environment used by `build-release.sh`.

## Validation gates

`build-stable-v10000098.yml` runs the Stable release-readiness checks, validates
the live sanitized ban configuration, applies the Stable identity overlay,
compiles the complete promoted Java source with Android 35 tools, packages and
aligns an unsigned Stable APK, verifies package/version/DEX feature markers, and
uploads the verification artifact. `verify-hcf-alerts-ui.yml` protects the
approved HCF Alerts UI independently.
