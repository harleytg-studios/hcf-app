# Android installer versionCode fix

The previous CLEAN-D8 build accidentally regressed the Android `versionCode` to `10000018`.
A prior Harley's Clan Forum v1.0 build had already reached `10000019`, so Android can reject
`10000018` as a downgrade when updating an installed package.

This source uses:

- Public version: `1.0`
- Android versionCode: `10000020`
- Internal build: `75`
- Stable package: `com.harleytg.forum`
- DEV package: `com.harleytg.forum.dev`
- APK signing: v1 + v2 + v3 (v4 disabled for standalone APK distribution)

Stable must be signed with the historical HCF Release v1 key. DEV uses the retained HCF Dev v2 key.
