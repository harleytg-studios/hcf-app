# Harley's Clan Forum v0.1.7/dev • Build 16

Dev prerelease notes.

## Changes
- Removed the extra Native App Mode strip below the header.
- Removed the native bottom navigation bar.
- Simplified the startup/connection screen to the square HTG logo, connection status, and a horizontal progress bar.
- Simplified App Settings and removed redundant controls.
- Firebase controls remain removed from Settings.
- Cookie counts remain visible directly in Settings with the full Cookie Manager available separately.
- Added live foreground forum updates, enabled by default.
- Live updates check for discussion/notification activity about every 12 seconds while the app is visible.
- Discussion pages watch their current discussion so unrelated posts do not force a refresh.
- Index/tag pages watch the latest discussion activity.
- Auto-refresh waits while the user is typing, using the composer, or has a modal open.
- Live refresh preserves the current scroll position.
- Dev updates continue to use GitHub Pre-releases.

Package: `com.harleytg.forum.dev`

APK SHA-256: `c5660c0c5fe109a86a09fa8c196a6dad564df5de6f35d879ab80e0cbd1f856d9`

Source ZIP SHA-256: `9c4ac04bd3c0d2b1c09c7497177e1193ef32caf250087d1efba0a78874e0edf1`
