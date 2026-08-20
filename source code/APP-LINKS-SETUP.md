# Harley's Clan Forum — Android App Links

Stable v10000072 registers forum links for:

- `forum.harleytg.com`
- `harleysclan.freeflarum.com`

For Android to open these hosts directly in the app without the system chooser, each host must publish the matching Digital Asset Links file at:

`https://HOST/.well-known/assetlinks.json`

Ready-to-deploy copies are included under `app-links/` for both hosts.

During the v10000072 signing migration, the files contain **both** the new local Stable signer and the previous Stable signer so verified links can continue to work for both installation lines:

- v10000072 local Stable signer: `9D:46:75:EC:2A:CB:83:22:AB:14:FD:97:0D:A5:B0:61:F5:9E:42:FA:5E:8E:45:3B:67:15:57:B2:13:13:78:05`
- previous Stable signer: `D6:51:2E:54:63:52:C3:06:1D:E6:C1:D4:26:D3:C9:AD:A0:83:A5:0A:E8:14:77:1B:AF:D1:6F:B0:73:78:4E:1B`

Deploy the corresponding `assetlinks.json` to both live hosts. If a hosting provider does not permit `.well-known/assetlinks.json`, Android may continue to use its supported-links/default-app behavior for that host.
