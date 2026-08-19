# APK install/signing fix

DEV package: `com.harleytg.forum.dev`

The permanent DEV signing line is locked to certificate SHA-256:
`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

`build-release.sh` verifies the keystore before build and verifies the output APK afterward.
This prevents a DEV release from being accidentally signed by another key.

The original/older DEV private signing keys are not retained. Android cannot perform an
in-place update across signing identities unless the old private key (or a valid signing lineage
created with that key) is available.
