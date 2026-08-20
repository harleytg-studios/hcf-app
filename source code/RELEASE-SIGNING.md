# Stable release signing

## Current Stable signing line — v10000072+

Stable v10000072 is signed with the locally generated Stable signing key.

Public certificate SHA-256:

`9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`

Keep the matching `.p12` and password in private local storage. **Never commit them to GitHub.** Future Stable APKs intended to update v10000072 in place must be signed with this same private key.

## Previous Stable signer

The prior Stable signing certificate was:

`D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

Android does not allow an in-place update when the signing identity changes. Users on that signer must perform a one-time uninstall/reinstall before joining the v10000072 signing line.

## Build policy

- Package must remain `com.harleytg.forum`.
- Stable builds must keep the Stable update channel and must not consume Dev/Beta feeds.
- Do not generate a new temporary signing key in CI for a release APK.
- Do not commit signing passwords, private keys, PKCS#12 files, JKS files, or other private key material.
- Verify the signed APK certificate and package/version metadata before publishing.
