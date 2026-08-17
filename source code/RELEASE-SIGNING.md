# Stable release signing

Package: `com.harleytg.forum`  
Signing generation: `HCF Release v1`  
First release: `0.4.0`  
VersionCode: `4009999`

Every release from v0.4.0 forward must use the same persistent HCF Release v1 private key. Never commit the keystore or its password to a public repository. Losing this key prevents future in-place updates for the package.

The v0.3.2 debug-signing private key was not preserved, so Android requires one clean reinstall when moving from v0.3.2 to v0.4.0. This is a signing boundary, not a package-ID change.

Certificate SHA-256: `D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

## Version-code transition at v0.4.14
v0.4.14 uses versionCode `4140000`, which is greater than v0.4.13's `4139999` and begins the cleaner monotonic stable-code convention. The signing identity is unchanged.
