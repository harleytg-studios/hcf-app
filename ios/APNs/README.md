# HCF APNs Integration

The iOS target registers with Apple Push Notification service (APNs) and stores the returned device token in the Keychain. A production notification provider still needs to associate that token with the authenticated HCF account and send APNs payloads.

This is intentionally not implemented as an embedded secret or a direct Discord/GitHub credential. APNs provider credentials (`.p8` key, Key ID, Team ID) belong on a server and must never ship inside the app.

## Recommended registration API

Expose an authenticated HCF-owned HTTPS endpoint, ideally on the forum's trusted origin or on an API origin controlled by Harley's Studios. The client registration request should use an existing server-verifiable forum authentication mechanism, not a static app secret.

Suggested request body:

```json
{
  "platform": "ios",
  "environment": "development",
  "device_token": "<APNS_DEVICE_TOKEN>",
  "bundle_id": "com.harleytg.forum.dev",
  "forum_user_id": "123",
  "app_version": "1.1-hf2-a1",
  "build": 100000105
}
```

The provider should replace older tokens for the same app installation/account, delete tokens after APNs reports them invalid, and keep development and production APNs environments separate.

## HCF notification payload

The client accepts normal APNs `aps` content plus these HCF fields when available:

```json
{
  "aps": {
    "alert": {
      "title": "New message from HarleyTG",
      "body": "Message preview"
    },
    "sound": "default",
    "badge": 2,
    "category": "HCF_FORUM_REPLY",
    "content-available": 1,
    "mutable-content": 1
  },
  "id": "987",
  "title": "New message from HarleyTG",
  "body": "Message preview",
  "url": "https://forum.harleytg.com/conversations/42",
  "conversationId": "42",
  "discussionId": "",
  "replyCapable": true
}
```

For non-reply-capable alerts, use `HCF_FORUM_READ` as the category. The app then uses the same Flarum endpoints as Android for **Reply** and **Mark as Read**, using the user's local first-party forum session and a fresh CSRF token.

## Background behavior

- APNs is the only reliable way to receive a remote event while iOS has suspended the app.
- `BGAppRefreshTask` is a best-effort fallback and iOS chooses when it executes; it is not a fixed polling timer.
- While HCF is foreground/active, the app performs its own authenticated Flarum notification sync.
- A silent push (`content-available: 1`) may ask HCF to refresh unread state, subject to iOS background execution limits.

## Security rules

- Never commit APNs `.p8` private keys, provider JWTs, Discord webhooks, GitHub tokens, or session cookies.
- Never place a static "API secret" in Swift or an `.xcconfig` that is committed to Git.
- Validate the user/token association server-side.
- Use TLS only.
- Treat APNs tokens as account/device identifiers and remove them on logout when your registration API is available.
