package com.harleytg.forum.dev;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Receives Android inline-reply text without ever claiming an unsent message succeeded. */
public final class NotificationReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !NotificationCenter.ACTION_REPLY.equals(intent.getAction())) return;
        try {
            Bundle input = RemoteInput.getResultsFromIntent(intent);
            CharSequence value = input == null ? null : input.getCharSequence(NotificationCenter.REMOTE_INPUT_KEY);
            String reply = value == null ? "" : value.toString().trim();
            String conversationId = intent.getStringExtra(NotificationCenter.EXTRA_CONVERSATION_ID);
            String rawDestination = intent.getStringExtra(NotificationCenter.EXTRA_DESTINATION);
            int notificationId = intent.getIntExtra(NotificationCenter.EXTRA_NOTIFICATION_ID, 41074);
            Uri destination = Uri.parse(rawDestination == null ? "" : rawDestination);

            if (reply.isEmpty() || conversationId == null || !conversationId.matches("[0-9]+")
                    || !LinkRouter.isInternal(destination)
                    || !conversationId.equals(LinkRouter.conversationId(destination))) {
                AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_LAST_ERROR,
                        "Inline reply rejected: invalid reply metadata").apply();
                AppLogger.warn(context, "notification_reply", "invalid metadata");
                return;
            }

            // The current authenticated notification client is read-only and the installed
            // Messenger extension does not expose a verified write endpoint to this native
            // layer. Preserve the user's reply locally and explicitly require completion in
            // Messenger rather than falsely reporting that a network send succeeded.
            NotificationCenter.showReplyFallback(context, conversationId, reply, destination, notificationId);
            AppLogger.info(context, "notification_reply", "draft_preserved | conversation=" + conversationId);
        } catch (Throwable error) {
            AppSettings.prefs(context).edit().putString(AppPrefs.NOTIFICATION_LAST_ERROR,
                    "Inline reply failed: " + error.getClass().getSimpleName()).apply();
            AppLogger.error(context, "notification_reply", error.getClass().getSimpleName());
        }
    }
}
