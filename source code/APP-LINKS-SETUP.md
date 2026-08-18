# Harley's Clan Forum — Android App Links

v0.4.14 registers every HTTP/HTTPS path on both forum hosts:

- `forum.harleytg.com`
- `harleysclan.freeflarum.com`

The app will appear as a handler for both hosts. For Android to open a host directly in the app without the system "Open with" chooser, that host must publish the matching Digital Asset Links file at:

`https://HOST/.well-known/assetlinks.json`

Ready-to-deploy copies are included under `app-links/` for both hosts. The fingerprint is the HCF Release v1 signing certificate used by v0.4.x.

If a hosting provider does not permit `.well-known/assetlinks.json`, Android may continue to use its system default-app / supported-links behavior for that host. The app includes **App Settings → Connection → Open Forum Link Settings** so users can review Android's handling for the app.
