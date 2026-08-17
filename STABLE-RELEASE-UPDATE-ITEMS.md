# Harley's Clan Forum — Stable Release Update Items

## Stable-only update channel

- Use the official Stable GitHub release channel only.
- Package remains `com.harleytg.forum`.
- Update channel is fixed and cannot be changed by the user.

## Real Android versionCode update detection

- Compare the installed app's numeric Android `versionCode` against the downloaded APK's embedded `versionCode`.
- Do not depend on the GitHub release tag, APK filename, or visible `versionName`.
- Example: `10000020 → 10000021`.
- Updates continue working when both builds display `v1.0`.

## APK filename-independent update detection

The updater must not require the versionCode in the APK filename. Files such as these should all work:

- `HCF-1.0.apk`
- `HCF-Stable.apk`
- `HarleysClanForum.apk`
- `HCF.apk`

Read package and version information directly from the APK.

## Improved update-available message

Display:

**Stable Update Available**

A newer Harley's Clan Forum release is ready.

- **Installed:** v1.0 (`versionCode`)
- **Available:** v1.0 (`versionCode`)
- **Channel:** Stable

Buttons:

- **Later**
- **Update Now**

## Automatic installer handoff

- After the update APK finishes downloading and verification succeeds, automatically open Android's package installer.
- The user only needs to confirm installation through Android.

## Unknown-app installation handling

- Detect whether HCF has permission to install downloaded APKs.
- If permission is required, automatically open the correct **Allow from this source** settings page.
- Resume the pending update installer when the user returns.

## Install-ready fallback notification

- If Android prevents the installer from opening while HCF is in the background, display an **Update ready to install** notification.
- Tapping the notification immediately opens the verified installer APK.

## Pre-install APK validation

Validate the downloaded update before Android's installer opens. Confirm:

- Package ID is exactly `com.harleytg.forum`.
- Downloaded `versionCode` is newer than the installed version.
- APK uses the expected Harley's Clan Forum Stable signing certificate.
- Android can successfully parse the APK.
- Download completed successfully.

## Safer failed-download handling

- Never attempt to install an incomplete, corrupt, or unverified APK.
- Delete failed temporary APK files automatically.
- Remove failed DownloadManager entries where appropriate.
- Display a useful error with a **Retry** option.

## Cleaner update progress

Display clear update stages:

- Checking for updates…
- Update available
- Downloading update…
- Verifying update…
- Update ready
- Opening installer…
- Waiting for installation…
- Update complete

## App Updates settings improvements

- **Automatic update checks**
- **Automatically download new APKs**
- **Open installer automatically after download**
- Display **Channel: Stable** clearly.
- Stable channel cannot be changed.

## Post-update cleanup

After a successful in-place update:

- Remove the temporary installer APK.
- Remove its DownloadManager entry.
- Clear stale pending-update state.
- Refresh the installed version/versionCode shown in App Settings.

## Forum message notification cleanup

- Never display raw JSON/API data in Android notifications.
- Extract readable fields such as `message`, `body`, `text`, or `excerpt`.
- Private-message notifications should display:
  - **New message from Username**
  - Actual message text
- Hide internal fields such as:
  - `user_id`
  - `conversation_id`
  - API IDs
  - timestamps
  - raw JSON
- If a `conversation_id` is available, tapping the notification should open the matching conversation.
- Group multiple messages from the same conversation cleanly.
- Fall back to **You have a new forum notification** when readable text is unavailable.

## Notification count and badge improvements

- Preserve unread notification counts.
- Display counts from `1–999`.
- Display `999+` above the limit.
- Keep the header/menu notification badge synchronized with the side-navigation count.
- Avoid duplicate count indicators.

## Notification reliability

- Keep foreground notifications responsive without requiring a manual page refresh.
- Continue background notification checks where Android permits.
- Avoid duplicate notifications for the same forum event.
- Preserve notification state after reopening the app.

## Stable branding

- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Settings/header label: **Harley's Clan Forum v1.0**
- App Updates channel label: **Stable**
- Use the normal Harley's Clan Forum launcher icon and branding.

## Preserve existing Stable features

- Notification counts and badges.
- Performance Profiles with **Auto** as the default.
- Contact Support.
- Forum Identity.
- Linked Accounts.
- App Links.
- Safe external-link handling.
- Diagnostics and Logs.
- Crash handling.
- Day/light theme fixes.
- Dark-theme support.
- Current share-sheet cleanup.
- Compact app header.
- Swipe-to-open side navigation.
- Forum primary and backup URL handling.
- Account and Identity UI.
- Update/install system.
