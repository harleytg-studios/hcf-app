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
- Contact Support v2/default-collapsed behavior and the normal account/identity/settings/theme improvements are part of the Stable promotion set.
- Normal notification history/routing/adaptive-polling improvements are included in the Stable promotion policy.
- Primary/backup HCF routing uses `forum.harleytg.com` and `harleysclan.freeflarum.com`; retired `.online` forum domains are excluded.
- Logs/diagnostics/error UI and performance/backoff improvements are part of the Stable promotion set.
- Features explicitly designated Beta/Dev-only are excluded from the Stable feature list. See `STABLE-FEATURE-PARITY-v10000072.md`.
- Source-of-truth Stable version metadata is `src/com/harleytg/forum/BuildInfo.java` and `AndroidManifest.xml`.

Signing
-------
Stable v10000072 starts the retained local Stable signing line with public certificate SHA-256:

`9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`

The private `.p12` and password stay outside this public repository. Do not create a temporary release key in CI. Future Stable APKs that must update v10000072 in place must use the same local key.

Build
-----
Use `build-release.sh` with Android SDK build-tools/platform 35 and the required signing environment variables:

- `ANDROID_SDK_ROOT`
- `HCF_KEYSTORE` — private v10000072 Stable signing key
- `HCF_KEY_PASSWORD_FILE`
- Optional: `HCF_KEY_ALIAS` (defaults to `hcf-stable-v10000072`)

The script compiles resources and Java source, runs D8, zipaligns, verifies the expected package/version, enforces the v10000072 signer fingerprint, and writes `out/HCF-Stable-v10000072.apk` unless `HCF_OUTPUT_DIR` is set.
