# Clean D8 rebuild verification

This DEV source is built using Android SDK Platform 35 and Build Tools 35.0.0.

The APK is created from source with `aapt`, Java 8 compilation, `d8 --min-api 26`,
`zipalign`, and official `apksigner`.

DEV package: `com.harleytg.forum.dev`  
Version: `1.0`  
Version code: `10000021`  
Internal build: `76`

The permanent HCF DEV v2 signing key is intentionally not included in this source archive.
