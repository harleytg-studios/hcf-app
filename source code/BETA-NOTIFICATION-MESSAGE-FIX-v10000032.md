# Harley's Clan Forum Beta — Notification Message Fix (10000032)

- Private/conversation notification payloads are recursively unwrapped.
- Raw JSON objects are never used directly as notification text.
- Payloads containing `conversation_id` are treated as direct messages.
- Direct-message title format: `New message from <sender>`.
- Direct-message body uses the actual `message`/`body`/`text`/`excerpt` value.
- Tapping a conversation notification opens `/conversations/<conversation_id>`.
- If no readable text can be extracted, the safe fallback is `You have a new private message.`
- Android versionCode: 10000032.
