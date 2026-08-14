# Harley's Clan Forum 0.2.0

First full stable Android release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.0`
- Android versionCode: `2009999`
- GitHub tag: `v0.2.0`
- Channel: **Stable / GitHub Latest**

## Changes
- Production package no longer uses `.dev`, so Stable and Dev can be installed side-by-side.
- Uses the HTG App Icon black/yellow caution branding.
- Stable updater is locked to normal GitHub Latest releases in both foreground and scheduled background checks; it will not pull Dev prereleases.
- Automatic update checks and automatic APK downloads remain supported.
- After Android successfully replaces the stable package, the app removes the downloaded installer APK and its DownloadManager entry.
- A startup fallback also cleans the installer if the installed version matches the downloaded update tag.
- Live forum page updates, cookie inspection, simplified settings, and the prior 0.2.0-dev.1 improvements are included.

## Integrity
- APK SHA-256: `03df0b552daeee68888435162574fe3e7befe50aa24c35ab01fbe72ab3a2634e`
- Source ZIP SHA-256: `c88d592a93e78753bcf8ba160636d3b7e5916f6958350566bc34296f09be8375`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
