# Background push requirements

HCF Beta currently uses the foreground notification service plus JobScheduler fallback.
`BuildInfo.FCM_CONFIGURED` must stay `false` until all of the following exist:

1. A Firebase Android app registered for `com.harleytg.forum.dev`.
2. The matching Android Firebase configuration bundled into the native build.
3. A server-side trusted sender that emits a data message when Flarum creates a notification.
4. A native receiver/service that validates the payload and posts it only through `HCF Alerts`.
5. Token registration/revocation tied to the signed-in forum account.

Do not set the flag to true merely because the web Firebase config is present; web config alone is not native FCM delivery.
