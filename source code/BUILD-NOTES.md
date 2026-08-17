Harley's Clan Forum Android v1.0 — System-wide Day Theme Fix
============================================================

Version: 1.0  
Version code: 10000017  
Internal build: 73  
Package: com.harleytg.forum  
Channel: Stable

Changes
-------
- Share forum page now uses a curated sharing-app list instead of every ACTION_SEND handler.
- Browser, Bluetooth, Download/Documents and similar non-sharing destinations are excluded from this dialog.
- Multiple share surfaces from the same package are collapsed into one app row.
- Messaging/email apps are prioritized, followed by social and other compatible share apps.
- Share forum media uses the same curated behavior.
- Open in Browser keeps its separate full external-app chooser.
- Auto remains the default performance profile.
- Contact Support and What's New button alignment fixes are preserved.
- Blue notification count badge is preserved.

- v1.0 internal revision: 10000015 / build 71. Simplified the HCF share panel to Copy Link, Share with…, and Cancel; destination selection now uses Android Sharesheet.

- v1.0 internal revision: 10000016 / build 72. Fixed Forum Identity profile-photo fitting: real avatars now fill and clip cleanly inside the rounded cyan frame while the HTG placeholder remains uncropped.


- v1.0 internal revision: 10000017 / build 73. Day theme now propagates system-wide across native app surfaces and refreshes the main shell when changed in Settings.
