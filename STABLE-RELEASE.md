# Harley's Clan Forum 1.0 — Stable v10000077

Stable release metadata for the Harley's Clan Forum Android app.

## Release identity

- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `1.0 (10000077)`
- Android versionCode: `10000077`
- GitHub tag: `v1.0`
- Channel: **Stable**
- Target SDK: `34`
- Stable branding: original Stable logo/icon set

## Stable-eligible feature promotion

v10000077 carries forward the non-Beta/Dev user-facing work while retaining the Stable package, branding, update channel, and production defaults.

Included Stable items include:

- Contact Support v2 with all four support sections collapsed each time it opens.
- Account and Forum Identity presentation/fit fixes.
- Settings and light/dark native UI refinements that are not Dev-only.
- Notification history and normal notification controls.
- Lower-latency notification runtime behavior: no foreground bridge cooldown, 1000 ms live fallback interval, 1000 ms effective failure-retry cap, immediate reconnect sync, and live service continuity when Silent Alerts are silenced.
- Registered HCF domain routing for `forum.harleytg.com` and `harleysclan.freeflarum.com`.
- Logs/diagnostics/error UI cleanup and reliability improvements.
- Performance/backoff improvements for background, screen-off, Battery Saver, and constrained-device conditions.
- Stable-only release-channel behavior; Dev/Beta test UI remains disabled for Stable identity.

## Beta / Dev-only items

Features explicitly designated Beta/Dev-only are **not promoted as Stable features**. In particular, the experimental update/install feature set remains outside the Stable promotion list until separately approved:

- automatic installer handoff experiments
- Allow-from-this-source resume experiments
- experimental downloaded-APK verification flow
- experimental install-ready fallback flow

## Release artifacts

- `HCF-Stable-v10000077.apk`
  - SHA-256: `6db249c8b0e53df8ac7ff3f378287ad1fe5b3f329731246e16a5d9c6ac726de5`
- `HCF-Stable-v10000077-source.zip`
  - SHA-256: `524595ace6704433e684f39549c3b653391c5499168011e141611baf79dc9836`
- `HCF-Stable-v10000077-VERIFICATION.txt`

The source ZIP is the exact local reconstruction/build bundle corresponding to the supplied Beta v10000077 payload after Stable package/branding promotion. The maintained Java source/reference tree remains under `source code/`.

## Stable V2 signing line

The v10000077 Stable APK is signed by the established Stable V2 certificate:

`77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`

This is the same Stable V2 signing identity used by Stable v10000034, so Stable V2 installs can update in place. Builds on older, different Stable signing identities still require a one-time signer migration.

The Stable V2 private key and password must never be committed to this public repository.
