# Stable v10000072 notification patch

This source-level record tracks the Stable-eligible notification changes verified in `1.0 (10000072)`.

- Foreground notification bridge cooldown removed in the shipped v10000072 Stable APK.
- Live notification polling/fallback interval: 1000 ms.
- Failure retry effective cap: 1000 ms.
- Network reconnect wakes notification synchronization immediately.
- Silencing the HCF Silent Alerts channel does not disable the live notification service.
- HCF Alerts, HCF Silent Alerts, and HCF Test Alerts remain separate channel roles.

The maintained `InstantNotificationService.java` source is aligned to the 1000 ms live/failure timing and reconnect wake behavior. The exact promoted APK payload and reconstructed source bundle are verified separately by `HCF-Stable-v10000072-VERIFICATION.txt`.

Beta/Dev-only installer experiments are not part of this Stable notification patch.
