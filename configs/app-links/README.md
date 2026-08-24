# HCF Android App Links configuration

This directory is the shared repository source-of-truth for both Android packages:

- Dev/Beta: `com.harleytg.forum.dev`
- Stable: `com.harleytg.forum`

Canonical GitHub locations:

- Page: `https://github.com/markhitchk/hcf-app/blob/main/configs/app-links%2Fassetlinks.json`
- Raw: `https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/app-links/assetlinks.json`

## In-app source

HCF uses the `main` branch copy above as its runtime App Links configuration source.
The Android app downloads the **Raw** URL because the GitHub **Page** URL returns
HTML rather than the JSON payload. The Page URL remains the canonical human-viewable
location; the Raw URL is the machine-readable form of the same file.

The app caches the downloaded JSON, validates that its installed package name and
SHA-256 signing certificate are present, and periodically refreshes the cached copy.
Both the Primary and Backup HCF hosts remain registered by the app/manifest.

## Android OS verification

Android does not perform OS-level App Links verification from a GitHub `blob` or
`raw` URL. Deploy this JSON file unchanged, with an HTTP 200 response and JSON
content type, at both:

- `https://forum.harleytg.com/.well-known/assetlinks.json`
- `https://harleysclan.freeflarum.com/.well-known/assetlinks.json`

Do not redirect either `/.well-known/assetlinks.json` URL. After deployment,
verify on Android with:

`adb shell pm get-app-links com.harleytg.forum.dev`

`adb shell pm get-app-links com.harleytg.forum`
