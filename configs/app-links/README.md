# HCF Android App Links configuration

This directory is the shared repository source-of-truth for both Android packages:

- Dev/Beta: `com.harleytg.forum.dev`
- Stable: `com.harleytg.forum`

Canonical GitHub locations:

- Page: `https://github.com/markhitchk/hcf-app/blob/main/configs/app-links%2Fassetlinks.json`
- Raw: `https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/app-links/assetlinks.json`

Android does not verify App Links from a GitHub `blob` or `raw` URL. Deploy the
JSON file unchanged, with an HTTP 200 response and JSON content type, at both:

- `https://forum.harleytg.com/.well-known/assetlinks.json`
- `https://harleysclan.freeflarum.com/.well-known/assetlinks.json`

Do not redirect either `/.well-known/assetlinks.json` URL. After deployment,
verify on Android with:

`adb shell pm get-app-links com.harleytg.forum.dev`

`adb shell pm get-app-links com.harleytg.forum`
