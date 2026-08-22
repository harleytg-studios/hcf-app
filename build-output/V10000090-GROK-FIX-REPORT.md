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

## HCF Alerts render-matched UI
- Dedicated HCF Alerts layout marker: `HCF_ALERTS_RENDER_V3`: PASS
- Selected second-render structure: PASS
- Three top status tiles with green/yellow/red lights: PASS
- Ready / Background / Last sync presentation: PASS
- Background delivery section no longer duplicates the generic subsection subtitle: PASS
- Background notification sync switch and battery reliability note: PASS
- Android access panel with Permission + Notification channel rows: PASS
- Status text uses neutral UI text with separate colored indicator lights: PASS
- Dedicated rounded `Open Android settings` action: PASS
- Compact HCF Alerts / HCF Silent Alerts relationship note: PASS
- Dashboard immediately rebuilds when background sync changes so status lights refresh: PASS
- Source file: `source code/src/com/harleytg/forum/SettingsActivity.java`

## Latest v10000090 rebuild
- Source compile workflow run: `32553611556`: PASS
- Final release-gate run: `32553668381`: PASS
- Final unsigned artifact: `HCF-Beta-v10000090-Final-Unsigned` (artifact `9470791113`)
- Package: `com.harleytg.forum.dev`
- Version code: `10000090`
- Version name: `1.0 (10000090)`
- Final DEX contains Background notification sync, battery guidance, Permission allowed, High priority, Open Android settings, and HCF Alerts relationship text: PASS
- Final DEX contains no `Method not decompiled` runtime marker: PASS

## Signed APK verification
- APK: `HCF-Beta-v10000090.apk`
- SHA-256: `c6268c0b7950e0285406584885ae2d1726a55baa658c7a19fd6ce1aa10911eec`
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
