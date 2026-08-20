Harley's Clan Forum Android v1.0 — Stable Build Notes
=====================================================

Version: 1.0 (10000072)  
Version code: 10000072  
Internal build: 100  
Package: com.harleytg.forum  
Channel: Stable

Current Stable baseline
-----------------------
- Stable-only update channel; Development/Beta feeds are not part of the Stable package identity.
- Stable keeps the original Stable launcher/logo branding.
- Contact Support v2/default-collapsed behavior and normal account/identity/settings/theme improvements are part of the Stable promotion set.
- Notification history/routing and production notification improvements are included.
- v10000072 notification timing: 1000 ms live polling/fallback, 1000 ms effective failure-retry cap, immediate reconnect wake.
- The shipped v10000072 Stable APK removes the foreground WebView notification bridge cooldown and keeps the live service running when Silent Alerts are silenced.
- Primary/backup HCF routing uses `forum.harleytg.com` and `harleysclan.freeflarum.com`; retired `.online` forum domains are excluded.
- Logs/diagnostics/error UI and performance/backoff improvements are part of the Stable promotion set.
- Features explicitly designated Beta/Dev-only are excluded. See `STABLE-FEATURE-PARITY-v10000072.md`.
- Source-of-truth Stable version metadata is `src/com/harleytg/forum/BuildInfo.java` and `AndroidManifest.xml`.

Signing
-------
Stable v10000072 continues the established Stable V2 signing line with public certificate SHA-256:

`77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`

Use the private `HCF-Stable-v2.jks` with alias `hcf-stable-v2`. The private JKS and password stay outside this public repository. Do not create a temporary release key in CI.

Build
-----
Use `build-release.sh` with Android SDK build-tools/platform 35 and the required signing environment variables:

- `ANDROID_SDK_ROOT`
- `HCF_KEYSTORE` — private Stable V2 JKS
- `HCF_KEY_PASSWORD_FILE`
- Optional: `HCF_KEY_ALIAS` (defaults to `hcf-stable-v2`)

The script compiles resources and Java source, runs D8, zipaligns, verifies the expected package/version, enforces the Stable V2 signer fingerprint, and writes `out/HCF-Stable-v10000072.apk` unless `HCF_OUTPUT_DIR` is set.
