# Harley's Clan Forum 1.0 — Stable v10000072

Stable release metadata for the Harley's Clan Forum Android app.

## Release identity

- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `1.0 (10000072)`
- Android versionCode: `10000072`
- GitHub tag: `v1.0`
- Channel: **Stable**
- Target SDK: `34`
- Stable branding: original Stable logo/icon set

## Stable-eligible feature promotion

v10000072 carries forward the non-Beta/Dev feature work from the newer builds while retaining the Stable package, branding, update channel, and production defaults.

Included Stable items:

- Contact Support v2 UI and the latest default-collapsed section behavior.
- Account & Forum Identity UI fixes, including avatar/frame sizing and identity presentation improvements.
- Settings redesign improvements that are not Dev-only.
- Light/Dark theme and native UI refinements.
- Notification runtime improvements, notification history, routing, DND-facing behavior, and lower-latency/adaptive polling work intended for normal users.
- Safer forum URL/app-link routing for the registered HCF domains.
- Primary forum domain `forum.harleytg.com` plus backup `harleysclan.freeflarum.com`; retired `.online` domains are not part of the Stable registry.
- Logs/diagnostics and error-system UI improvements.
- Performance/backoff improvements for background, screen-off, Battery Saver, and constrained-device conditions.
- Stable-only release channel behavior; Dev/Beta test UI and Dev package identity are disabled/removed from the Stable build identity.

## Beta / Dev-only items

Features explicitly designated Beta/Dev-only are **not promoted as Stable features**. In particular, the experimental update/install flow remains excluded from the Stable promotion list until it is separately approved for Stable:

- automatic installer handoff experiments
- Allow-from-this-source resume experiments
- experimental downloaded-APK verification flow
- experimental install-ready fallback flow

## Release artifacts

- `HCF-Stable-v10000072.apk`
  - SHA-256: `f6c2f0022c891676c025fc833b2cbeb05ed8e854b74cbab9ec0f8d08b8ec507d`
- `HCF-Stable-v10000072-source.zip`
  - SHA-256: `f7bc833918bb55c4e90a6c6ad965bddb4922d5368c7ff0b1675e063610ca8923`
- `HCF-Stable-v10000072-VERIFICATION.txt`

## Stable signing line from v10000072

The v10000072 Stable APK is signed with the locally generated Stable key whose certificate SHA-256 is:

`9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`

The previous Stable signing line used:

`D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

Android requires the same signing certificate for an in-place package update. Devices on the previous signer therefore need a one-time uninstall/reinstall to move to the v10000072 local Stable signing line. Future Stable APKs intended to update v10000072 in place must use the local v10000072 private key. The private key and password must never be committed to this public repository.
