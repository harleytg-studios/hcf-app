# Beta Update UI / versionCode Detection

- Baseline test build: Android versionCode 10000029.
- Foreground prompt title: **Beta Update Available**.
- Prompt shows installed and available visible version plus Android versionCode.
- DEV updater inspects the APK asset itself with Android PackageManager; APK filename does not need to contain a versionCode.
- Fixed `v1.0-dev` tag can be reused: GitHub asset ID/update timestamp identifies replacement APK assets.
- Update downloads are keyed by release asset identity instead of tag alone.
