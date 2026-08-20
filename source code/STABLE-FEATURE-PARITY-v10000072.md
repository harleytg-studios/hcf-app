# Stable feature parity — v10000072

This record defines the Stable promotion boundary for Harley's Clan Forum `1.0 (10000072)`.

## Stable identity

- Package: `com.harleytg.forum`
- Channel: `Stable`
- versionCode: `10000072`
- Default update channel: `stable`
- Update-channel switching: disabled
- Dev/Beta test UI: disabled for Stable identity
- Stable logo/icon set retained

## Promoted to Stable

The following are normal user-facing features/fixes and are part of the Stable promotion set:

- Contact Support v2 presentation and latest default-collapsed section state.
- Account and Forum Identity presentation/fit fixes.
- Settings UI improvements that are not marked Dev-only.
- Follow-phone/Day/Night theme refinements and light/dark readability work.
- Notification history and normal notification controls.
- Notification routing and lower-latency/adaptive polling behavior intended for production use.
- Background/screen-off/Battery Saver/constrained-device polling backoff.
- Registered HCF domain routing and safe-link handling.
- Primary domain `forum.harleytg.com` and backup `harleysclan.freeflarum.com`.
- Removal of the retired `.online` forum domain from the Stable routing policy.
- Logs/diagnostics/error UI cleanup and reliability improvements.
- Performance improvements and reduced unnecessary network/change-detection work.
- Existing Stable update checking/channel isolation fixes.

## Explicitly not promoted from Beta/Dev

These experimental update/install behaviors remain Beta/Dev-only unless separately approved for Stable:

- automatic installer handoff experiments
- Allow-from-this-source resume experiments
- experimental downloaded-APK verification flow
- experimental install-ready fallback flow

The Stable branch should not advertise those items as Stable features.

## Signing line

v10000072 uses the local Stable signer:

`9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`

The corresponding private key remains outside GitHub.
