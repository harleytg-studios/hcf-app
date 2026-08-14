# Harley's Clan Forum 0.2.3

Stable identity, branding-quality, and updater cleanup release.

## Release identity
- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `0.2.3`
- Android versionCode: `2039999`
- Internal build: `21`
- GitHub tag: `v0.2.3`
- Channel: **Stable / GitHub Latest**

## Changes
- Removed the top-right cog/gear button from the main app header; App Settings remain available from the side drawer.
- Replaced app-wide native logo resources with the exact supplied 1254×1254 HTG artwork and regenerated density launcher assets with high-quality resampling.
- Native logo ImageViews now use uncropped `fitCenter` rendering to keep the full cyan border/artwork sharp.
- Added native Flarum identity sync using the current `window.app.session.user` only.
- The drawer now shows the current signed-in identity or Guest protocol.
- Added **My Forum Identity** with the current user's self-visible display name, username, ID, slug, groups/roles, admin state, self-visible email state, join/last-seen timestamps, discussion/comment totals, notification counts and avatar URL.
- Passwords, access/session tokens, preference blobs, and private data for other users are never collected.
- On reopen the app waits for the current forum session and shows `Welcome back @display name`; unauthenticated sessions show `Welcome back • Guest protocol`.
- Returning after at least 20 seconds in the background also refreshes identity/welcome state.
- Updater-owned APKs are kept out of the general Downloads UI where supported and are removed after a successful package replacement.
- Completed obsolete updater APKs are removed before a newer release is downloaded.
- Manual APK downloads from browsers, ChatGPT, or other apps remain user-owned and are not silently deleted.
- Existing HTTPS-only WebView security, SSL blocking, Safe Browsing, matching-signature update verification, live forum updates, cookie inspection, and permission onboarding remain enabled.

## Integrity
- APK SHA-256: `6582462c03e673893335f66183991d2c3385885c3a7a7105a97c6cd59f631079`
- Source ZIP SHA-256: `a3729966e1eff64772cbc36c46eb96d2af1b003428c52983329e3620934361b2`
- Stable signing certificate SHA-256: `daa53ddbb505e066542d3e821f73cec26d33a7a73363159e3ee381d6256066a8`

The private stable signing key must never be committed to this public repository. Every future `com.harleytg.forum` APK must be signed with the same stable key for in-place updates to work.
