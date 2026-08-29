# Harley's Clan Forum — Native iOS Dev Port

Native Swift/SwiftUI iOS port of the current `dev` Android application.

## Baseline

- Android source baseline: `dev` commit `258c483606a03652ca1ea6f38f988333325c840c`
- HCF channel version: `v1.1-hf2-a1`
- Shared build number: `100000105`
- Product version: `1.1`
- iOS deployment target: iOS 17.0+
- App bundle identifier: `com.harleytg.forum.dev`
- Widget bundle identifier: `com.harleytg.forum.dev.widget`
- App Group: `group.com.harleytg.forum.dev`

Apple requires `CFBundleShortVersionString` to use a numeric dotted version. The iOS target therefore uses `1.1.0` for `MARKETING_VERSION`, while HCF displays `1.1-hf2-a1` inside the app and uses `100000105` for `CFBundleVersion` / `CURRENT_PROJECT_VERSION`. This keeps the Android dev-channel identity without violating Apple bundle-version formatting.

## Architecture

The Swift package mirrors the seven Android subsystems rather than copying Android Activity/Service classes:

- **HCFCore** — build identity, models, network client, preferences/App Group storage, Keychain, diagnostics, theme primitives.
- **HCFForum** — trusted-domain routing, Flarum API helpers, WKWebView bridge, session/cookie persistence, identity capture.
- **HCFUI** — SwiftUI application shell, startup gate, forum browser chrome, settings, identity, diagnostics, transfer and update UI.
- **HCFNotifications** — APNs/local notification coordinator, notification actions, foreground sync and BGTask refresh.
- **HCFUpdates** — GitHub prerelease checker for the Dev/Beta channel and TestFlight/release hand-off.
- **HCFPlatform** — window-size/adaptive-layout policy, connectivity and platform helpers.
- **HCFWidget** — WidgetKit timelines/views backed only by App Group cached state.

The application itself is in `HarleysClanForum/`; the WidgetKit extension is in `HCFWidgetExtension/`; reusable modules are in `Packages/HCFModules/`.

## Generate the Xcode project

The repository stores an XcodeGen specification instead of a hand-edited `.pbxproj` so target membership, build settings and entitlements remain reviewable text.

```bash
cd ios
brew install xcodegen
xcodegen generate
open HarleysClanForum.xcodeproj
```

XcodeGen is a build-time development tool only. The shipped app has no third-party runtime framework dependency.

## Required Apple configuration

1. Set your Apple Developer Team ID in `Config/Local.xcconfig` (copy from `Config/Local.xcconfig.example`).
2. Enable **App Groups**, **Associated Domains**, **Push Notifications**, and **Background Modes** for the app identifier.
3. Enable the same App Group for the Widget extension.
4. Host a valid `apple-app-site-association` file on both trusted forum hosts. Android `assetlinks.json` does not configure iOS Universal Links.
5. Configure APNs on a server/provider if true background real-time pushes are required. iOS does not permit an Android-style continuously running 2-second foreground service while the app is suspended.
6. For TestFlight/App Store builds, distribute updates through Apple. The GitHub updater remains a channel/version and release-notes checker; it does not download or install an IPA.

## Secrets and moderation observations

**Never commit a Discord webhook.** A secret embedded in an iOS app can be extracted just like an APK secret. Production builds use `HCF_OBSERVATION_PROXY_URL`, a server-side endpoint that owns the Discord credential. A direct webhook option exists only behind the `HCF_INTERNAL_DISTRIBUTION` compilation condition for private/internal testing and reads the value from the untracked `Config/Secrets.xcconfig`.

The ban gate itself remains backend-free and fail-open: it reads the public ban-system config and public `ban-list.json`, normalizes usernames, hashes the public IP with SHA-256, and never publishes raw IP addresses.

## Settings transfer

Exported settings contain only non-sensitive preferences. The transfer archive deliberately excludes WebKit cookies, Keychain values, APNs tokens, authentication/session secrets, notification message bodies when history privacy is disabled, and any moderation delivery secret.

## Widget behavior

The Widget extension performs no forum network request. It reads a cached `WidgetSnapshot` from the shared App Group. Forum/Notifications/Settings/Profile/Latest links open the app. Reload opens the app with `hcf://widget/reload`, where the normal notification sync pipeline updates the cache and calls `WidgetCenter.reloadAllTimelines()`.

## Release policy

- Dev display/tag: `v1.1-hf2-a1`
- Apple marketing version: `1.1.0`
- Apple build number: `100000105`
- Android versionCode parity: `100000105`
- Recommended next dev builds: increment the numeric build for every upload while keeping the human channel suffix (`a2`, `b1`, `rc1`, etc.) in `HCFBuildInfo.channelVersion`.

For TestFlight, every uploaded build must have a unique higher `CFBundleVersion`. App Store promotion should keep the public semantic product version numeric and may remove the Dev/Beta label from the display name.