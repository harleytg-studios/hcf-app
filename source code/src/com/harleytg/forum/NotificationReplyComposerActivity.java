package com.harleytg.forum.dev;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Safe fallback for notification RemoteInput when a verified native authenticated
 * write path is unavailable. Opens the real Messenger conversation and restores the
 * typed reply into its composer; it never reports the draft as sent.
 */
public final class NotificationReplyComposerActivity extends ThemedActivity {
    private static final int[] INJECTION_DELAYS_MS = {150, 450, 900, 1600, 2600};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private TextView status;
    private Uri destination;
    private String conversationId;
    private String draft;
    private int injectionAttempt;
    private boolean injected;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setTitle("Messenger Reply");

        Intent intent = getIntent();
        destination = intent == null ? null : intent.getData();
        conversationId = intent == null ? "" : safe(intent.getStringExtra(NotificationCenter.EXTRA_CONVERSATION_ID));
        draft = intent == null ? "" : safe(intent.getStringExtra(NotificationCenter.REMOTE_INPUT_KEY));
        if (draft.isEmpty() && !conversationId.isEmpty()) draft = AppSettings.replyDraft(this, conversationId);

        if (!validRequest()) {
            showFailure("The notification reply could not be opened safely.");
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        status = new TextView(this);
        status.setText("Opening Messenger • your reply has not been sent yet");
        status.setTextColor(getColor(R.color.hcf_text));
        status.setTextSize(12f);
        status.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        status.setPadding(pad, dp(9), pad, dp(9));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setUserAgentString(BuildInfo.userAgent(settings.getUserAgentString()));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                return !LinkRouter.isInternal(uri);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri;
                try { uri = Uri.parse(url == null ? "" : url); }
                catch (Throwable ignored) { return true; }
                return !LinkRouter.isInternal(uri);
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri loaded;
                try { loaded = Uri.parse(url == null ? "" : url); }
                catch (Throwable ignored) { return; }
                if (!LinkRouter.isInternal(loaded)) return;
                if (!conversationId.equals(LinkRouter.conversationId(loaded))) return;
                scheduleInjection();
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        webView.loadUrl(destination.toString());
        AppLogger.info(this, "notification_reply_composer", "open | conversation=" + conversationId);
    }

    private boolean validRequest() {
        if (destination == null || !LinkRouter.isInternal(destination)) return false;
        if (conversationId.isEmpty() || !conversationId.matches("[0-9]+")) return false;
        if (!conversationId.equals(LinkRouter.conversationId(destination))) return false;
        return draft != null && !draft.trim().isEmpty();
    }

    private void scheduleInjection() {
        injectionAttempt = 0;
        injected = false;
        scheduleNextInjection();
    }

    private void scheduleNextInjection() {
        if (webView == null || injected || injectionAttempt >= INJECTION_DELAYS_MS.length) {
            if (!injected && status != null) {
                status.setText("Reply saved locally • Messenger loaded, but the composer was not ready. Your text is still preserved in Notification History.");
            }
            return;
        }
        int delay = INJECTION_DELAYS_MS[injectionAttempt++];
        mainHandler.postDelayed(this::injectDraft, delay);
    }

    private void injectDraft() {
        if (webView == null || injected || isFinishing() || isDestroyed()) return;
        String quoted = JSONObject.quote(draft == null ? "" : draft);
        String script = "(function(){try{" +
                "var e=document.querySelector('#MessageTextArea,.chat-message textarea,textarea[name=message],textarea');" +
                "if(!e)return 'missing';" +
                "var p=window.HTMLTextAreaElement&&HTMLTextAreaElement.prototype;" +
                "var d=p&&Object.getOwnPropertyDescriptor(p,'value');" +
                "if(d&&d.set)d.set.call(e," + quoted + ");else e.value=" + quoted + ";" +
                "e.dispatchEvent(new Event('input',{bubbles:true}));" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));" +
                "e.focus();" +
                "return 'ok';" +
                "}catch(x){return 'error';}})();";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("ok")) {
                injected = true;
                if (status != null) status.setText("Reply restored • review it, then tap Send in Messenger");
                AppLogger.info(NotificationReplyComposerActivity.this, "notification_reply_composer",
                        "draft_restored | conversation=" + conversationId);
            } else {
                scheduleNextInjection();
            }
        });
    }

    private void showFailure(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(getColor(R.color.hcf_text));
        view.setTextSize(15f);
        view.setGravity(Gravity.CENTER);
        int pad = dp(24);
        view.setPadding(pad, pad, pad, pad);
        view.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        setContentView(view);
        AppLogger.warn(this, "notification_reply_composer", "invalid request");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            try { webView.stopLoading(); webView.destroy(); } catch (Throwable ignored) {}
            webView = null;
        }
        super.onDestroy();
    }
}
