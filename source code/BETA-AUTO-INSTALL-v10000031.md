# HCF Beta v10000031 — Automatic installer handoff

- Package: `com.harleytg.forum.dev`
- Public version: `1.0`
- Android versionCode: `10000031`
- Adds **Open installer automatically after download** under App Updates (default: ON).
- After DownloadManager completes, HCF verifies package name, newer versionCode, and signing certificate before opening Android Package Installer.
- While App Updates is open, the installer is launched immediately after verification.
- Background download completion also attempts the installer handoff and falls back to an install-ready notification if Android blocks background activity launch.
- Android still requires the user to confirm installation; silent installation is not possible for a normal sideloaded app.
