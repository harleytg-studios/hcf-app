# HCF Beta v10000090 — Grok Guide Repair Verification

## Completed in source
- Direct `dev` source compilation: PASS
- `Method not decompiled` runtime stubs: 0
- decompiler `UnsupportedOperationException` runtime stubs: 0
- Update progress/status/cleanup methods restored: PASS
- Notification delta/badge implementation restored: PASS
- Foreground notification-service sync implementation restored: PASS
- File chooser + install-permission result flow restored: PASS
- BuildInfo/manifest identity unified at versionCode 10000090: PASS
- README current build identity updated: PASS
- HCF Alerts remains app-required; Android owns permission/channel blocking: PASS
- Background notification sync belongs to HCF Alerts UI: PASS
- Last background sync age is shown in Notifications UI: PASS
- Silent Alerts service-status behavior remains separate: PASS
- Update package/version/signing verification preserved and signer-lineage policy added: PASS
- Remote domain host validation preserved: PASS
- Live updater state logging + persistent route fingerprint baseline: PASS
- App Links assetlinks deployment payload generated for Dev and Stable: PASS
- Stale decompiler `public.xml` removed: PASS
- build-release.sh rejects version drift/stubs and pins Beta signer: PASS
- Direct-source APK package: com.harleytg.forum.dev

## External deployment still required
- `assetlinks.json` must be served from both forum hosts at `/.well-known/assetlinks.json`.
- Native FCM remains disabled until a Firebase Android app config, trusted server sender, token lifecycle, and native receiver are deployed. Web Firebase config alone is not native FCM.

## Architectural follow-up
Grok recommends incrementally splitting MainActivity. The repaired source is now suitable for that refactor, but a large class split is intentionally not mixed into this runtime-stability build because it would add unrelated regression risk.
