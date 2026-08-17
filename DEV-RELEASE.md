# Harley's Clan Forum 1.0 DEV

Development-channel build for Harley's Clan Forum Android.

## Build identity

- App name: **Harley's Clan Forum [Beta]**
- Application ID: `com.harleytg.forum.dev`
- Public version: `1.0`
- Version code: `10000032`
- Channel: `dev`
- GitHub prerelease tag: `v1.0-dev`
- Target SDK: `34`

## Included update set

This DEV build carries the current 1.0 app work, including system-wide Day Theme changes, performance profiles with Auto as the default, notification controls/count badge work, simplified share chooser behavior, Account & Identity fixes, diagnostics/log UI changes, app-link handling, safe external-link handling, update/install improvements, profile-avatar fit correction, lower-end-device animation reductions, and forum-message notification cleanup.

## Update-system behavior

- DEV builds check only the DEV/prerelease channel.
- Update availability is determined from the downloaded APK's embedded Android `versionCode`.
- The APK filename does not need to include the versionCode.
- Downloaded APKs are parsed and validated before install handoff.
- Package identity and expected DEV signing identity are checked before installation.
- After a valid download completes, HCF automatically opens Android's package installer when allowed.
- If Android requires **Allow from this source**, HCF can open the correct settings flow and resume installation afterward.
- If Android blocks the installer from opening while HCF is backgrounded, HCF shows an install-ready notification as a fallback.

## Forum message notification fix

Messenger/private-message notification payloads are recursively unwrapped before display. User-visible message candidates include `message`, `body`, `text`, `excerpt`, `preview`, and `content`. Raw JSON containers are never used as the visible notification body.

When `conversation_id` or `conversationId` is available:

- Notification title: `New message from <sender>`
- Notification body: readable message text
- Notification tap route: `/conversations/<conversation_id>`
- Safe fallback body: `You have a new private message.`

Example target behavior:

- Sender: `Darsoul`
- Message: `Is this right`
- Conversation ID: `23`

Visible Android notification:

`New message from Darsoul`

`Is this right`

Tap route: `/conversations/23`

## Permanent DEV signing line

The current DEV APK uses the permanent development certificate:

`CN=Harley's Clan Forum Development, O=HTG, C=US`

Certificate SHA-256:

`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

The release build verifies with APK Signature Scheme v2 and v3.

## Release artifacts

- `HCF-Beta-v10000032.apk`
  - SHA-256: `199e72d451ead4dde14fb1a09ea5fd1bbd101d1d674a216455f19cf3bf92e3e1`
- `HCF-Beta-v10000032-source.zip`
  - SHA-256: `1153b925a7d461fec40b8ec2d773efc324fd588c85afe3b7e83c83814635bf31`
- `HarleysClanForum-1.0-dev-VERIFICATION.txt`

## Upgrade compatibility

Older `com.harleytg.forum.dev` installations may have been signed by a different DEV private key. Android requires the installed app and replacement APK to have a compatible signing identity, so those legacy installations cannot update in place to the permanent DEV signing line. A one-time uninstall/reinstall is required unless the exact older private key is recovered.

All future DEV releases should remain on the permanent DEV certificate above.
