# HCF Android App Links deployment

Serve `assetlinks.json` unchanged as HTTPS JSON with no redirect at both:

- `https://forum.harleytg.com/.well-known/assetlinks.json`
- `https://harleysclan.freeflarum.com/.well-known/assetlinks.json`

The file includes both the Beta/Dev package and Stable package signing fingerprints.
After deployment verify on Android with `adb shell pm get-app-links com.harleytg.forum.dev`.
The Android manifest already declares both HTTPS hosts with `android:autoVerify="true"`.
