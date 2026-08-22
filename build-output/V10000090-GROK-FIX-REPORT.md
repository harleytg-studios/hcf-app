# HCF Beta v10000090 — Final Release Verification

## Completed in Dev source
- Direct `dev` source compilation: PASS
- Package: `com.harleytg.forum.dev`
- BuildInfo/manifest identity: `1.0 (10000090)` / versionCode `10000090`: PASS
- `Method not decompiled` runtime stubs: 0
- decompiler `UnsupportedOperationException` runtime stubs: 0
- Update progress/status/cleanup methods restored: PASS
- Notification delta/badge implementation restored: PASS
- Foreground notification-service sync implementation restored: PASS
- File chooser + install-permission result flow restored: PASS
- HCF Alerts remains app-required; Android owns permission/channel blocking: PASS
- Background notification sync is under HCF Alerts: PASS
- HCF Alerts UI v2 status dashboard with green/yellow/red state lights: PASS
- HCF Alerts summary tiles include Ready / Delayed-or-Live / Synced-or-Stale states: PASS
- Background state reports live / delayed / waiting / paused accurately: PASS
- HCF Silent Alerts remains a separate service-status channel: PASS
- Last background sync age is shown in Notifications UI: PASS
- Update package/version/signing verification and signer-lineage policy: PASS
- Realtime Pusher transport and live-update diagnostics: PASS
- Remote domain host validation: PASS
- Cold-start theme application and remembered Forum Auto state: PASS
- Separate AMOLED appearance mode and live theme status: PASS
- App Links `assetlinks.json` deployment payload generated: PASS
- Stale decompiler `public.xml` removed: PASS
- `build-release.sh` rejects version drift/stubs and pins Beta signer: PASS

## Latest v10000090 rebuild
- HCF Alerts UI source commit: `a692f406b91e81a26c4dc15605adfcd16851e906`
- Final direct-source build run: `32552790283`: PASS
- Final unsigned artifact: `HCF-Beta-v10000090-Final-Unsigned` (artifact `9470540201`)
- Package: `com.harleytg.forum.dev`
- Version code: `10000090`
- Version name: `1.0 (10000090)`
- Final DEX contains HCF Alerts UI v2 markers: Ready, Delayed, Synced, Background notification sync, Permission allowed, High priority, Open Android settings: PASS
- Final DEX contains no `Method not decompiled`, `10000072`, `10000071`, or `v10000047`: PASS

## Signed APK verification
- APK: `HCF-Beta-v10000090.apk`
- SHA-256: `a6869e525c935d6f44a57fc2cb079a6c4d278d905e3e12440553fa622f4b5cc8`
- APK Signature Scheme v2: PASS
- APK Signature Scheme v3: PASS
- APK Signature Scheme v4 sidecar (`.idsig`): generated
- Signer: `CN=Harley's Clan Forum Beta v2, OU=Development Signing, O=Harley's Studios, C=US`
- Signer certificate SHA-256: `93d49bf9a877c7cfb1b37f9064bd955cd67bd7dd8db73a9e3f766b59c4bcce63`
- RSA key size: 4096 bits

## External infrastructure still required
- `assetlinks.json` must be served from each forum host at `/.well-known/assetlinks.json` for Android App Links verification.
- Native FCM remains disabled until a Firebase Android app config, trusted server sender, token lifecycle, and native receiver are deployed. Web Firebase config alone is not native FCM.

## Architectural follow-up
- MainActivity can be split incrementally in a later refactor. It is intentionally not mixed into this release-stability build.
