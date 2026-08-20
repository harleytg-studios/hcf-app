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

Normal user-facing features/fixes in the Stable promotion set include:

- Contact Support v2 presentation with all four sections collapsed on every new opening.
- Account and Forum Identity presentation/fit fixes.
- Settings UI improvements not marked Dev-only.
- Follow-phone/Day/Night theme refinements and light/dark readability work.
- Notification history and normal notification controls.
- Foreground notification bridge cooldown removed in the shipped v10000072 Stable APK.
- Live notification fallback interval fixed at 1000 ms.
- Failure retry effective cap fixed at 1000 ms.
- Network reconnect keeps immediate sync scheduling.
- Silencing HCF Silent Alerts does not stop the live notification service.
- Background/screen-off/Battery Saver/constrained-device polling backoff.
- Registered HCF domain routing and safe-link handling.
- Primary domain `forum.harleytg.com` and backup `harleysclan.freeflarum.com`.
- Retired `.online` forum domain excluded from Stable routing.
- Logs/diagnostics/error UI cleanup and reliability improvements.
- Existing Stable update checking/channel isolation fixes.

## Explicitly not promoted from Beta/Dev

These experimental update/install behaviors remain Beta/Dev-only unless separately approved for Stable:

- automatic installer handoff experiments
- Allow-from-this-source resume experiments
- experimental downloaded-APK verification flow
- experimental install-ready fallback flow

## Stable V2 signing line

v10000072 uses the established Stable V2 signer:

`77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`

The corresponding private key remains outside GitHub.
