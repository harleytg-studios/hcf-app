# Android installer versionCode and DEV signing-line fix

This DEV source uses:

- Public version: `1.0`
- Android versionCode: `10000021`
- Internal build: `76`
- DEV package: `com.harleytg.forum.dev`
- Permanent DEV signer SHA-256: `AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

The release build script rejects any different keystore before compiling and verifies the
finished APK certificate again after signing. This prevents future DEV update package conflicts
caused by accidental signing-key changes.

Older installed DEV packages signed by a different certificate cannot be replaced in-place
without the exact old private signing key. That is an Android cryptographic restriction.
