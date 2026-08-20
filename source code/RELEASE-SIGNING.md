# Stable release signing

## Current Stable signing line — Stable V2

Stable v10000072 continues the established Stable V2 signing identity already used by Stable v10000034.

Keystore name used in private storage: `HCF-Stable-v2.jks`

Alias: `hcf-stable-v2`

Public certificate SHA-256:

`77:E0:E9:6C:11:77:84:2A:AA:31:1A:8F:C0:EB:EA:29:B9:2D:3C:D2:90:BB:81:5B:DB:86:AD:0E:0A:85:84:4F`

Keep the matching JKS and password in private storage. **Never commit them to GitHub.** Future Stable APKs intended to update Stable V2 builds in place must use this same private key.

## Legacy Stable signer

An older Stable line used:

`D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

Android does not allow an in-place update when the signing identity changes. Users still on that legacy signer require a one-time migration to the Stable V2 line.

## Build policy

- Package must remain `com.harleytg.forum`.
- Stable builds must keep the Stable update channel and must not consume Dev/Beta feeds.
- Release builds must use `hcf-stable-v2`; do not generate a temporary signing key.
- Do not commit signing passwords, private keys, PKCS#12 files, JKS files, or other private key material.
- Verify the signed APK certificate and package/version metadata before publishing.
