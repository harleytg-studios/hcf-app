# Harley's Clan Forum 1.0 DEV

Development-channel build for Harley's Clan Forum Android.

## Build identity

- Application ID: `com.harleytg.forum.dev`
- Public version: `1.0`
- Version code: `10000021`
- Channel: `dev`
- GitHub prerelease tag: `v1.0-dev`
- Target SDK: `34`

## Included update set

This DEV build carries the current 1.0 app work, including system-wide Day Theme changes, performance profiles with Auto as the default, notification controls/count badge work, simplified share chooser behavior, account/identity fixes, diagnostics/log UI changes, update/install handling, profile-avatar fit correction, and reduced animation load on lower-end devices.

## Permanent DEV signing line

The current DEV APK uses the permanent development certificate:

`CN=Harley's Clan Forum Development, O=HTG, C=US`

Certificate SHA-256:

`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

The release build verifies with APK Signature Scheme v2 and v3. The DEV build tooling now guards this expected signer before and after signing.

## Release artifacts

- `HarleysClanForum-1.0-dev-PERMANENT-SIGNING-LINE-v10000021.apk`
  - SHA-256: `3f5f175d1c1adab46208efda389e8a47f86a3d1f3959edbbecf9724e0356512d`
- `HarleysClanForum-1.0-dev-PERMANENT-SIGNING-LINE-v10000021-source.zip`
  - SHA-256: `b519ab1c063e80e077a46a2e2dddd5f2cf761d4ab090fe6c423472c4222c5809`
- `HarleysClanForum-1.0-dev-VERIFICATION.txt`

## Upgrade compatibility

Older `com.harleytg.forum.dev` installations may have been signed by a different DEV private key. Android requires the installed app and replacement APK to have a compatible signing identity, so those legacy installations cannot update in place to the permanent DEV signing line. A one-time uninstall/reinstall is required unless the exact older private key is recovered.

All future DEV releases should remain on the permanent DEV certificate above.
