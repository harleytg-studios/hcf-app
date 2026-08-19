# Harley's Clan Forum [Beta] — v10000025 crash fix

- Rebuilt `classes.dex` from Java source with D8 instead of binary-patching DEX.
- `com.harleytg.forum.dev` remains the package ID.
- DEV/Beta updater is forced to the `dev` channel at `UpdateChecker.check()`.
- Settings, automatic checks, and `/install` all use DEV/Beta.
- Launcher/app name remains `Harley's Clan Forum [Beta]`.
- Side-nav footer is set at runtime to `[Development Build / Beta]`.
- Permanent DEV signer remains AC:6B:91:3E:E0:80:94:83:37:1F:66:A7:3C:C5:D0:BB:DA:1E:45:D4:91:E1:43:57:4D:40:46:74:B0:23:AB:CE.
