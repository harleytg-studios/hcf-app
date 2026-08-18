Harley's Clan Forum Android v1.0 — Development/Beta v10000033
================================================================

Version: 1.0  
Version code: 10000033  
Internal build: 86  
Package: com.harleytg.forum.dev  
Channel: Development / Beta

Build focus
-----------
- Hardware-aware Auto • Real-Time adaptive performance engine.
- Adaptive foreground/background notification polling with immediate wake events.
- Network-aware live forum freshness checks with smaller signatures and HTTP validators.
- Shared executors instead of repeated one-off background threads.
- Reduced notification preference writes and successful-poll log spam.
- WebView foreground/background timer and renderer-priority management.
- Renderer recovery diagnostics and expanded optimization diagnostics.
- Deferred noncritical startup work.
- Development/Beta updater stays locked to the dev release channel and numeric APK versionCode comparison.

Signing
-------
- Package remains com.harleytg.forum.dev.
- build-release.sh retains the permanent Development/Beta signer fingerprint guard.
- Use the existing private Development/Beta keystore to preserve in-place update compatibility.

See V10000033-DEV-IMPLEMENTATION.md for the detailed implementation list.
