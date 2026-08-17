# Harley's Clan Forum 1.0

Stable 1.0 release metadata for the Harley's Clan Forum Android app.

## Release identity

- App name: **Harley's Clan Forum**
- Package: `com.harleytg.forum`
- Version: `1.0`
- Android versionCode: `10000020`
- GitHub tag: `v1.0`
- Channel: **Stable / GitHub Latest**
- Target SDK: `34`

## Included 1.0 update set

- System-wide Day Theme/native UI improvements.
- Performance profiles with **Auto** as the default.
- Notification controls and notification-count badge work.
- Simplified Android share chooser behavior.
- Account and Forum Identity button/alignment fixes.
- Profile-avatar fit correction.
- Diagnostics/log UI cleanup and crash handling improvements.
- Update/install handling improvements, including the versionCode 10000020 installer fix.
- Reduced animation load for lower-end devices while retaining richer effects on capable devices.

## Release artifacts

- `HarleysClanForum-1.0-VERSIONCODE20-INSTALL-FIX.apk`
  - SHA-256: `722c0702dcbabae60aa21eb2d7ff0d142c1f7e2601f8f66ca059f15e91b14ff3`
- `HarleysClanForum-1.0-VERSIONCODE20-INSTALL-FIX-source.zip`
  - SHA-256: `d1b813d86b051821c90bc4b6b9bb8fcf522097457c17d199b3d7dfed619b16c2`
- `HarleysClanForum-1.0-VERSIONCODE20-INSTALL-FIX-VERIFICATION.txt`

## Signing and upgrade compatibility

The 1.0 stable APK is signed with:

`D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

The currently published v0.3.0 APK used an older Android Debug certificate with SHA-256:

`6A:5E:1C:51:A0:5D:D9:7B:CF:E4:6D:DA:6D:D2:83:C3:68:CF:C4:3B:36:E4:CA:E9:86:D2:23:71:A1:18:E2:2A`

Because Android requires the same signing certificate for an in-place package update, installations from that old signer cannot update directly to the new permanent stable signing line. A one-time uninstall/reinstall migration is required unless the exact legacy private key is recovered.

Future `com.harleytg.forum` releases should stay on the current permanent stable signing certificate above.
