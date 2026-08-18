# DEV signing-line update fix

Package: `com.harleytg.forum.dev`
Version name: `1.0`
Version code: `10000021`

## Permanent DEV signer

Future DEV releases are locked to SHA-256 certificate:

`AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE`

`build-release.sh` now checks the selected keystore before compilation and checks the
finished APK again after signing. A wrong signing key stops the build instead of
publishing an APK that Android would reject as a conflicting package.

## Existing old DEV installs

Older DEV builds used earlier certificates. Android does not permit an APK signed by
a different private key to replace an already installed package with the same package
name. The old private DEV signing keys are not present in the retained source/build
files, so those old installs require one signing-line migration before this permanent
DEV line can be used. This restriction is enforced cryptographically by Android and
cannot be bypassed by changing DEX, versionCode, ZIP alignment, or manifest data.
