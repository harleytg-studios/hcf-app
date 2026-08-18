Harley's Clan Forum Android v1.0 — Stable Build Notes
=====================================================

Version: 1.0  
Version code: 10000032  
Internal build: 77  
Package: com.harleytg.forum  
Channel: Stable

Current Stable baseline
-----------------------
- Stable-only update channel; Development/Beta prereleases are ignored.
- Android update detection uses the numeric APK `versionCode` and does not depend on the release filename.
- Follow phone / Day / Night theme support is included across native app surfaces.
- Performance profiles are included with Auto as the default.
- Notification-count badge, simplified sharing, account/identity UI fixes, diagnostics/log cleanup, update/install fixes, and profile-avatar fitting are included in the current Stable source.
- The current source-of-truth version metadata is `src/com/harleytg/forum/BuildInfo.java`.

Build
-----
Use `build-release.sh` with Android SDK build-tools/platform 35 and the required signing environment variables:

- `ANDROID_SDK_ROOT`
- `HCF_KEYSTORE`
- `HCF_KEY_PASSWORD_FILE`
- Optional: `HCF_KEY_ALIAS` (defaults to `hcf-release`)

The script compiles resources and Java source, runs D8, zipaligns, signs, verifies, and writes `out/HarleysClanForum-1.0.apk` unless `HCF_OUTPUT_DIR` is set.

Repository cleanup
------------------
Historical one-off patch-note and verification files were removed from the active source folder to keep Stable maintainable. Their contents remain recoverable from Git history. Current release metadata is maintained at the branch root in `README.md`, `STABLE-RELEASE.md`, and `STABLE-LATEST.json`.
