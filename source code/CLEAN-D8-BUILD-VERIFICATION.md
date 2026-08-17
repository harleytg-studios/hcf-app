# Clean D8 rebuild verification

This source was verified on 2026-08-17 using Android SDK Platform 35 and Build Tools 35.0.0.

The release APK was created from source with `aapt`, Java 8 compilation, `d8 --min-api 26`, `zipalign`, and official `apksigner`. No prior APK's `classes.dex`, compiled manifest, or `resources.arsc` was reused.

Stable package: `com.harleytg.forum`  
Version: `1.0`  
Version code: `10000020`

Signing keys are intentionally not included in this source archive.
