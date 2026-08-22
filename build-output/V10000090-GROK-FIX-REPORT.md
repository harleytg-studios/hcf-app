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

## External infrastructure still required
- `assetlinks.json` must be served from each forum host at `/.well-known/assetlinks.json` for Android App Links verification.
- Native FCM remains disabled until a Firebase Android app config, trusted server sender, token lifecycle, and native receiver are deployed. Web Firebase config alone is not native FCM.

## Architectural follow-up
- MainActivity can be split incrementally in a later refactor. It is intentionally not mixed into this release-stability build.
