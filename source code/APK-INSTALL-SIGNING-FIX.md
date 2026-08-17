# APK install/signing fix

This source revision keeps APK Signature Scheme v1 + v2 enabled and disables v3 in the release script for the compatibility build.
The APK must still be signed with the correct persistent private key for its package.

Stable package: `com.harleytg.forum`. Use the existing HCF Release v1 keystore (`hcf-release`) so upgrades retain the historical signing identity. The private key is intentionally not bundled in this source ZIP.
