package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Full-screen in-app viewer for media explicitly opened from a trusted forum page. */
public final class MediaViewerActivity extends ThemedActivity {
    static final String EXTRA_URL = "media_url";
    static final String EXTRA_KIND = "media_kind";

    private String mediaUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mediaUrl = getIntent() == null ? "" : safeHttps(getIntent().getStringExtra(EXTRA_URL));
        if (mediaUrl.isEmpty()) {
            Toast.makeText(this, "This media link could not be opened safely.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildUi());
        AppLogger.info(this, "media_viewer_open", AppLogger.safeUrl(mediaUrl));
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(4), dp(6), dp(4));
        bar.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));

        ImageButton close = UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, 0, 12, "Close media viewer");
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = new TextView(this);
        title.setText("Media Viewer");
        title.setTextColor(getColor(R.color.hcf_text));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(8);
        bar.addView(title, tp);

        Button share = button("Share");
        share.setOnClickListener(v -> share());
        bar.addView(share, new LinearLayout.LayoutParams(dp(72), dp(46)));

        Button external = button("Open");
        external.setOnClickListener(v -> openExternal());
        bar.addView(external, new LinearLayout.LayoutParams(dp(72), dp(46)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        WebView media = new WebView(this);
        media.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        WebSettings settings = media.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        media.setWebViewClient(new WebViewClient());

        String kind = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra(EXTRA_KIND));
        String escaped = html(mediaUrl);
        String content;
        if (kind.toLowerCase(java.util.Locale.US).contains("video")) {
            content = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes'>"
                    + "<style>html,body{margin:0;background:#000;width:100%;height:100%;display:flex;align-items:center;justify-content:center}video{width:100%;max-height:100vh;background:#000}</style></head>"
                    + "<body><video controls autoplay playsinline src='" + escaped + "'></video></body></html>";
        } else {
            content = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes'>"
                    + "<style>html,body{margin:0;background:#000;min-height:100%;display:flex;align-items:center;justify-content:center}img{max-width:100%;height:auto;object-fit:contain}</style></head>"
                    + "<body><img src='" + escaped + "' alt='Forum media'></body></html>";
        }
        Uri uri = Uri.parse(mediaUrl);
        String base = "https://" + uri.getHost() + "/";
        media.loadDataWithBaseURL(base, content, "text/html", "UTF-8", null);
        root.addView(media, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private Button button(String label) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label == null ? "" : label.replaceFirst("^[^A-Za-z0-9]+", "").trim());
        b.setTextColor(getColor(R.color.hcf_cyan_bright));
        b.setTextSize(11);
        b.setBackgroundColor(Color.TRANSPARENT);
        FaIcons.applyStart(b, label);
        return b;
    }

    private void share() {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, mediaUrl);
            HcfIntentChooser.showShare(this, share, "Share forum media", "Copy the link or share it with another app.");
        } catch (Throwable t) {
            Toast.makeText(this, "Unable to share this media.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternal() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mediaUrl)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this media.", Toast.LENGTH_SHORT).show();
        }
    }

    private static String safeHttps(String raw) {
        try {
            Uri uri = Uri.parse(raw == null ? "" : raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().trim().isEmpty()) return "";
            return uri.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
