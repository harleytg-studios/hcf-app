# Harley's Clan Forum App — v10000033 Optimization Update

**Development/Beta Version Code:** `10000033`  
**Development/Beta Package:** `com.harleytg.forum.dev`  

## Release Strategy

v10000033 is a **Development/Beta feature and optimization release**.

The complete v10000033 optimization set is scoped to `com.harleytg.forum.dev`. This document covers only the Development/Beta package and its new optimization, real-time, notification, networking, WebView, and adaptive-performance behavior.

---

## Development/Beta v10000033 Optimization Set

## Hardware-Aware Real-Time Performance

Development/Beta v10000033 should prioritize **maximum responsiveness on hardware that can support it**.

Auto Performance mode must not unnecessarily throttle capable devices. If the phone has sufficient CPU, RAM, battery state, thermal headroom, and network quality, HCF should operate in an aggressive real-time mode with the lowest practical latency.

### Auto • Real-Time

Enable automatically when the device is considered capable.

Target behavior:

- Foreground notification checks: approximately `750–1250 ms` when push is unavailable
- Live forum page refresh checks: approximately `750–1500 ms` while the user is actively viewing the page
- Immediate refresh after:
  - Opening the app
  - Resuming the app
  - Returning from another Activity
  - Posting a reply
  - Starting a discussion
  - Sending a private message
  - Opening notifications
  - Pull-to-refresh
  - Regaining network connectivity
- No unnecessary artificial debounce delay for user-triggered actions
- Keep the WebView renderer at high priority while the app is foregrounded
- Keep UI animations full-quality when frame performance remains healthy
- Prefer real-time push delivery whenever FCM is available
- Run polling only as a fallback or supplemental freshness mechanism when push is unavailable or insufficient

The goal is for notifications, messages, discussion changes, and forum state to appear **as close to instantly as the network and forum server allow**.

### Hardware Capability Detection

Auto mode should consider:

- Available RAM, not only total RAM
- Total RAM
- CPU core count
- CPU capability/device class
- Android low-RAM flag
- Current thermal state
- Battery Saver state
- Battery level
- Foreground/background state
- Wi-Fi/cellular connection quality
- Recent request latency
- WebView renderer stability
- Frame/render performance

A capable device should remain in Real-Time mode unless current conditions require a temporary reduction.

### Suggested Auto Profiles

**Auto • Real-Time**
- Strong hardware
- Good network
- Battery Saver off
- No thermal pressure
- Healthy memory state
- Fastest safe foreground refresh
- Full UI effects

**Auto • Balanced**
- Average hardware or moderate load
- Approximately 2–5 second live checks when push is unavailable
- Normal UI effects

**Auto • Performance**
- Low-memory or slower devices
- Reduced visual effects
- Longer polling intervals
- Reduced background processing

**Auto • Extreme Saver**
- Experimental Development/Beta fallback
- Battery Saver / severe thermal or memory pressure
- Minimal polling and visual effects

### Dynamic Promotion and Demotion

Auto mode must be able to change profiles while the app is running.

Examples:

`Auto • Balanced → Auto • Real-Time`

when:

- Thermal state returns to normal
- Network improves
- Memory pressure clears
- Battery Saver is disabled

And:

`Auto • Real-Time → Auto • Performance`

when:

- Device begins thermal throttling
- Memory becomes critically low
- WebView becomes unstable
- Battery Saver is enabled
- Network requests repeatedly time out

When conditions recover, HCF should automatically return to the faster profile.

### Latency Principle

Do not slow a capable phone merely to match low-end hardware.

Use the fastest practical behavior the current device can sustain without causing:

- UI jank
- WebView instability
- excessive thermal throttling
- repeated failed requests
- severe battery drain
- Android background execution problems

Foreground responsiveness should have higher priority than battery optimization while the user is actively using HCF.

---

### 1. Adaptive Notification Sync

Replace the fixed ~1.25 second background notification polling loop with adaptive polling.

Recommended behavior:

- App actively in use: `2–3 seconds`
- Recently backgrounded: `10 seconds`
- Background idle: `20–30 seconds`
- Long idle: `1–3 minutes`
- Screen off: `3–5 minutes`
- Battery Saver active: `5–10 minutes`
- Manual refresh, app resume, or notification-page open: sync immediately
- Keep the existing scheduled fallback for recovery if the foreground notification service stops

The app should automatically speed syncing back up when the user returns.

### 2. Reduce Unnecessary Preference Writes

Do not write notification state to `SharedPreferences` every time a poll succeeds.

Only save values when they actually change, including:

- Notification count
- Active forum host
- Sync status
- Connection state
- Last meaningful notification event

Rate-limit diagnostic sync timestamps instead of saving them every request.

### 3. Reduce Log File Spam

Normal successful notification polls should no longer create repeated disk log entries.

Log important events only, such as:

- Notification count changed
- New notification received
- Connection lost
- Connection restored
- Authentication failure
- API failure
- Polling backoff enabled
- Notification service started/stopped
- Unusually slow request

### 4. Optimize Live Forum Updating

Reduce the amount of work performed by `LiveForumUpdater`.

Avoid hashing large full API responses on every refresh when a smaller change signature can be used.

Prefer lightweight values such as:

- Discussion ID
- Last post number
- Last posted timestamp
- Comment count
- New notification count
- Latest notification ID

Where supported, use HTTP cache validators such as `ETag`, `If-None-Match`, or `Last-Modified` so unchanged responses can return without downloading full content.

Suggested refresh behavior:

- Active interaction: `2–3 seconds`
- Page open but idle: `5 seconds`
- Longer inactivity: `10 seconds`
- App backgrounded: stop live page refresh entirely

### 5. Network-Aware Syncing

Add central connectivity awareness.

Behavior:

- Wi-Fi / Ethernet: normal real-time behavior
- Good cellular connection: normal or balanced real-time behavior
- Metered or weak mobile connection: slower background polling
- No network: stop repeated HTTP attempts
- Network restored: perform an immediate notification sync and page freshness check

### 6. Shared Background Executors

Replace repeated `new Thread(...)` calls with shared executors for:

- Network operations
- Disk operations
- Serial background work
- Main-thread callbacks

This reduces unnecessary thread creation and makes background work easier to cancel and control.

### 7. WebView Memory Improvements

Improve WebView behavior under memory pressure.

Add:

- `onRenderProcessGone()` recovery
- WebView recreation after renderer failure
- Restore the last valid forum URL after recovery
- Foreground/background renderer priority handling
- Pause unnecessary WebView timers when safe while the app is backgrounded
- Resume WebView activity cleanly when returning to the app

### 8. Faster Startup

Keep only startup-critical work on the initial launch path.

Startup-critical:

- Theme
- Preferences required for UI
- MainActivity creation
- WebView initialization
- Initial forum load

Defer noncritical work until after the first screen is usable:

- Telemetry heartbeat
- Old update-file cleanup
- Update checks
- Log housekeeping
- Nonessential diagnostics
- Nonessential Firebase setup

### 9. Improved Auto Performance Mode

Keep `Auto` as the default performance mode.

Auto should dynamically consider:

- Device RAM
- Available memory
- CPU core count
- Android low-RAM classification
- Battery Saver
- App foreground/background state
- Network quality
- WebView renderer pressure
- Repeated network failures

Auto can dynamically select:

- `High Performance`
- `Balanced`
- `Performance`

The selected Auto state may be displayed in diagnostics, for example:

`Auto • Balanced`

or

`Auto • Performance — Battery Saver active`

### 10. Low-End Device Improvements

When the device is considered low-end or under pressure:

- Reduce nonessential animations
- Avoid expensive blur effects
- Slow idle refresh intervals
- Reduce repeated diagnostics updates
- Minimize background WebView activity
- Avoid unnecessary layout invalidation
- Prefer lightweight transitions

---

## Development/Beta v10000033

Development/Beta receives the full new feature and optimization set first.

Package:

`com.harleytg.forum.dev`

Version transition:

`10000032 → 10000033`

### FCM-First Notification Experiment

Where Firebase configuration is available, Development/Beta should test Firebase Cloud Messaging as the primary real-time notification transport.

Preferred flow:

`Forum event → notification bridge → FCM → Android notification`

Adaptive polling remains enabled as a fallback/recovery mechanism.

If FCM is unavailable, misconfigured, delayed, or disconnected, automatically fall back to adaptive polling without requiring user action.

### Experimental Adaptive Engine

Development/Beta may test a more advanced adaptive performance engine that reacts to:

- Battery Saver
- Thermal pressure
- RAM pressure
- Connection quality
- App activity
- WebView crashes
- Network timeout frequency
- Notification latency

Possible runtime modes:

- `Auto • High Performance`
- `Auto • Balanced`
- `Auto • Performance`
- `Auto • Extreme Saver` *(experimental)*

### Extreme Saver — Development/Beta Only

Optional experimental mode:

- Minimal native animations
- Reduced blur/transparency
- Longer idle live-refresh intervals
- Background notifications use push/fallback polling only
- Reduced diagnostic refresh frequency
- Reduced WebView background activity

### Development/Beta Validation Targets

Before considering the Development/Beta build complete, test:

- Cold start
- Warm start
- App resume
- App backgrounding
- Screen off/on
- Wi-Fi to cellular transitions
- Temporary loss of network
- Battery Saver
- Low-memory conditions
- WebView renderer termination/recovery
- Login/logout
- Notification count changes
- Foreground notifications
- Background notifications
- APK update download
- APK install flow
- Package/signature compatibility
- Reboot persistence

---

## Update System Requirements

Both packages must continue to detect updates using the installed and downloaded APK's numeric Android `versionCode`.

For this Development/Beta-first release:

**Development/Beta:** `10000032 → 10000033`  

Do not depend on:

- GitHub tag names
- APK filenames
- Visible `versionName`

APK filenames such as the following must continue to work:

- `HCF-1.0.apk`
- `HCF-Stable.apk`
- `HCF-Beta.apk`
- `HarleysClanForum.apk`

The updater should read the downloaded APK's package metadata directly.

Development/Beta must use its Development/Beta release channel according to its existing configuration.

---

## Diagnostics Additions

Add lightweight diagnostics for optimization troubleshooting:

- Current performance profile
- Auto-selected runtime profile
- Notification sync mode
- Current polling interval
- FCM state *(Beta where enabled)*
- Last successful sync
- Last notification count change
- Current network type
- Battery Saver state
- WebView renderer recovery count
- Consecutive API failure count

Diagnostics should not continuously write these values to disk unless necessary.

---

## Expected Results

Development/Beta v10000033 should provide:

- Lower battery drain
- Less unnecessary network traffic
- Fewer repeated disk writes
- Lower background CPU usage
- Better low-end device performance
- Better WebView crash recovery
- Faster app startup
- More reliable notification syncing
- Real-time behavior while the app is actively being used
- Better background efficiency without removing notification support

---

## Release Summary

**v10000033 is a Development/Beta-only feature set.** The new optimization, notification, networking, WebView, and adaptive-performance changes are implemented in `com.harleytg.forum.dev`. Stable remains on `10000032` until the new feature set has been tested and proven reliable.
