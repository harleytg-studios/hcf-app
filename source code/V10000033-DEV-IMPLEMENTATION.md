# Harley's Clan Forum — Beta/Dev v10000033 Implementation

Package: `com.harleytg.forum.dev`
Version name: `1.0`
Version code: `10000033`

## Implemented in this coding pass

- Hardware-aware Auto runtime states:
  - Auto • Real-Time
  - Auto • Balanced
  - Auto • Performance
  - Auto • Extreme Saver
- Auto considers available/total RAM, CPU cores, Android low-RAM classification, Battery Saver, battery level/charging state, thermal state, app foreground/background state, network type/metering, recent notification request latency, WebView renderer recovery count, and memory-pressure callbacks.
- Capable foreground devices target ~1 second notification polling and ~1 second live forum freshness checks when push is unavailable.
- Adaptive background notification polling:
  - recently backgrounded: ~10 seconds
  - normal background idle: ~20–30 seconds
  - long idle: ~90 seconds to 3 minutes
  - screen off: ~3 minutes
  - Battery Saver: ~5 minutes
- Immediate notification/live freshness sync after app resume, restored connectivity, pull-to-refresh, notifications navigation, and successful forum API mutations detected through the WebView bridge.
- Central in-memory foreground/background/network/memory-pressure state.
- Shared network/disk/serial/scheduled executors replace one-off background threads throughout the source.
- Notification state is persisted only when count/host changes; routine successful count polls are no longer disk-logged.
- Sync status preference writes are rate-limited during unchanged successful polling.
- Notification count request uses JSON:API sparse fields to reduce payload size.
- LiveForumUpdater uses sparse discussion fields, compact change signatures, and in-memory ETag/Last-Modified validators instead of SHA-256 hashing large API responses every poll.
- Live updates stop with the activity and adapt between active/idle intervals while foregrounded.
- WebView timers resume in foreground and pause in background; renderer priority is Important in foreground and Bound/discardable in background.
- Renderer recovery count is tracked and surfaced in diagnostics.
- Noncritical application cleanup/telemetry heartbeat is deferred off the cold-start path.
- Settings/Logs diagnostics show runtime profile, runtime reason, notification mode/interval, live interval, network, Battery Saver, FCM state, API failures, renderer recovery count, and last notification-count change.
- Development/Beta version code updated from `10000032` to `10000033` in both `AndroidManifest.xml` and `BuildInfo.java`.

## FCM status

The provided source contains Firebase web configuration support but does not bundle the native Firebase Messaging Android SDK. v10000033 therefore identifies native FCM as unavailable and uses adaptive polling as the active transport. No fake FCM delivery path was added.

## Build/signing

`build-release.sh` still enforces the existing Development/Beta signer SHA-256 fingerprint. The signing key itself is not included in the uploaded source, so a same-signature in-place APK must be built with the existing private DEV keystore.
