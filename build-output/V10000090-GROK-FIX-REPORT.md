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
- Background state reports live / delayed / waiting / off accurately: PASS
- HCF Silent Alerts remains a separate service-status channel: PASS
- Last background sync age/status is shown in Notifications UI: PASS
- Update package/version/signing verification and signer-lineage policy: PASS
- Realtime Pusher transport and live-update diagnostics: PASS
- Remote domain host validation: PASS
- Cold-start theme application and remembered Forum Auto state: PASS
- Separate AMOLED appearance mode and live theme status: PASS
- App Links `assetlinks.json` deployment payload generated: PASS
- Stale decompiler `public.xml` removed: PASS
- `build-release.sh` rejects version drift/stubs and pins Beta signer: PASS

## HCF Alerts final expanded UI migration
- Dedicated source marker: `HCF_ALERTS_RENDER_FINAL_V10000090`: PASS
- Keeps outer expandable `HCF Alerts` / `Real forum notifications • background delivery` panel: PASS
- Three equal top status tiles: Alerts / Background / Last sync: PASS
- Status values and light colors are state-driven rather than screenshot-hardcoded: PASS
- Alerts tile reads runtime permission, app notification state, HCF Alerts channel importance, and postability: PASS
- Background tile reads the existing background-sync preference, signed-in session, HCF Silent service state, and current sync state: PASS
- Last-sync tile reads `NOTIFICATION_LAST_SYNC_AT` and `NOTIFICATION_LAST_SYNC_STATUS`: PASS
- Failed/waiting/stale sync cannot render green `Synced`: PASS
- Background notification sync toggle preserves the existing preference and `NotificationSyncScheduler.apply(...)` behavior: PASS
- Battery recommendation is a distinct inset row and visually emphasizes `Unrestricted`: PASS
- Android Access permission row reports permission denied / Android blocking / permission allowed from real state: PASS
- Android Access channel row reports blocked/off, low, normal, or high priority from actual channel importance: PASS
- Full-width `Open Android settings` action uses existing `NotificationHelper.openChannelSettings(...)`: PASS
- Informational footer separates HCF Alerts from HCF Silent Alerts: PASS
- HCF Silent Alerts panel was not redesigned: PASS
- Obsolete inner `Real forum alerts` card: not rendered
- Obsolete uppercase `READY` pill/badge: not rendered
- Obsolete combined `HCF Alerts ready • Background delivery ... • Last sync ...` sentence: removed from the status refresh path
- Obsolete inner HCF Alerts `REQUIRED` channel card: not rendered
- Obsolete `Open HCF Alerts Android Settings` presentation: absent from final DEX
- Responsive weighted layouts are used; no absolute/screenshot pixel positioning: PASS

## Final V10000090 build
- Source commit: `bf05a5e5c2cd8d463d9609beb4855fd7b0e67832`
- Workflow run: `32554233161`: PASS
- Unsigned artifact: `HCF-Beta-v10000090-HCF-Alerts-Final-Unsigned` (artifact `9470957400`)
- Package: `com.harleytg.forum.dev`
- Version code: `10000090`
- Version name: `1.0 (10000090)`
- Direct Java compile: PASS
- D8/DEX package build: PASS
- `zipalign` verification: PASS
- Final DEX contains Background notification sync, Unrestricted battery guidance, Permission allowed/denied, channel priority states, Open Android settings, and HCF Alerts/Silent Alerts footer: PASS
- Final DEX does not contain `Open HCF Alerts Android Settings`: PASS
- Final DEX does not contain the old combined `HCF Alerts ready • Background delivery` status string: PASS
- Final DEX contains no `Method not decompiled:` runtime marker: PASS

## Signed APK verification
- APK: `HCF-Beta-v10000090.apk`
- SHA-256: `746859521ed20841450ccee3b785ad5f63760a7bf90e72c31be2753e5e02ddc0`
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
