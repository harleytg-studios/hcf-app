# HCF iOS Build & Release

## Version mapping

The Android Dev channel currently identifies itself as:

- HCF channel version: `1.1-hf2-a1`
- Android `versionCode`: `100000105`

Apple's bundle version fields should remain numeric, so the iOS target maps this to:

- `MARKETING_VERSION = 1.1.0`
- `CURRENT_PROJECT_VERSION = 100000105`
- In-app channel identity: `v1.1-hf2-a1 (100000105) • Beta / Development Build`

For every TestFlight upload, increment `CURRENT_PROJECT_VERSION` even when the human channel suffix changes only from `a1` to `a2`. Keep the Android/iOS build number aligned when the two builds represent the same HCF source baseline.

Suggested Dev progression:

| HCF display | Apple marketing | Build |
| --- | --- | ---: |
| `1.1-hf2-a1` | `1.1.0` | `100000105` |
| `1.1-hf2-a2` | `1.1.0` | `100000106` |
| `1.1-hf2-b1` | `1.1.0` | `100000107` |
| `1.1-hf2-rc1` | `1.1.0` | `100000108` |
| `1.1` stable | `1.1.0` | next unused build |

## Before the first signed build

1. Copy `Config/Local.xcconfig.example` to `Config/Local.xcconfig`.
2. Set `HCF_DEVELOPMENT_TEAM` to the Apple Developer Team ID.
3. Create the App ID `com.harleytg.forum.dev` in Apple Developer.
4. Create the Widget App ID `com.harleytg.forum.dev.widget`.
5. Create App Group `group.com.harleytg.forum.dev` and add both identifiers.
6. Enable Associated Domains and Push Notifications on the app identifier.
7. Host the AASA file on both trusted forum domains at either `/.well-known/apple-app-site-association` or `/apple-app-site-association`, with no redirect and a JSON content type.
8. Replace `YOUR_APPLE_TEAM_ID` in the hosted AASA file with the actual Team ID. Do not commit a guess.
9. If APNs real-time delivery is enabled, configure the server/provider described in `APNs/README.md`.
10. If Discord security observation is required for App Store/TestFlight, set `HCF_OBSERVATION_PROXY_URL` to the HCF-owned HTTPS proxy. Never place the Discord webhook in an App Store build.

## Local build

```bash
cd ios
xcodegen generate
open HarleysClanForum.xcodeproj
```

Use the **HCF Dev** scheme. Development builds use the APNs sandbox environment; Release archives use production APNs through `Config/Release.xcconfig` and the distribution provisioning profile.

## TestFlight

1. Select **Any iOS Device (arm64)**.
2. Product → Archive.
3. Run Validate App before upload.
4. Distribute App → App Store Connect → Upload.
5. In App Store Connect, add TestFlight notes that identify the HCF channel suffix and numeric build.
6. Test at minimum:
   - fresh install / guest browsing;
   - Flarum sign-in and relaunch session persistence;
   - primary → backup host switch preserving discussion paths;
   - Universal Link into a discussion/profile/notifications page;
   - notification permission denied and granted paths;
   - local Flarum sync badge updates;
   - APNs delivery if the provider is configured;
   - Reply and Mark as Read actions;
   - small/medium/large widgets and widget quick links;
   - settings export/import on a second clean install;
   - Light/Dark/AMOLED/System themes;
   - ban-list username/IP hash test entries in a non-production fixture;
   - external link confirmation;
   - VoiceOver, Dynamic Type and iPad split view.

## App Store

The GitHub Releases checker is informational on iOS. The App Store binary must never download or execute replacement app code or silently install an IPA. Public stable updates are delivered through the App Store; beta/dev updates are delivered through TestFlight.

Before public submission:

- use a stable display name without `[Beta]` if this is the public stable SKU;
- confirm App Privacy answers match the shipped configuration, especially forum account identifiers, forum user content, IP-based ban/moderation checks, and push identifiers;
- supply the privacy policy and support URL in App Store Connect;
- confirm the privacy manifest matches actual required-reason API use;
- verify camera/microphone/photo permissions appear only when the user invokes attachment UI;
- remove any private/internal `HCF_INTERNAL_DISTRIBUTION` compilation condition;
- verify no secrets exist in the archive using `strings`, source review, and repository secret scanning;
- use Organizer validation to catch entitlement, icon and signing issues.

## iOS-specific parity notes

- Android foreground services can poll continuously; iOS cannot. Foreground HCF sync runs while the app is active, APNs provides real background event delivery, and `BGAppRefreshTask` is best-effort fallback work scheduled by iOS.
- Android installs an APK from GitHub. iOS checks the same Dev prerelease channel for release awareness but opens TestFlight/App Store/GitHub release notes instead of self-installing an IPA.
- Android App Links use `assetlinks.json`. iOS Universal Links require `apple-app-site-association` plus the Associated Domains entitlement.
- WidgetKit controls refresh timing. The HCF widget consumes App Group cache and does not duplicate the forum network engine inside the extension.
