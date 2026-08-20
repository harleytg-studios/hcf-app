package com.harleytg.forum;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/* loaded from: classes.dex */
public final class MediaViewerActivity extends ThemedActivity {
    static final String EXTRA_KIND = "media_kind";
    static final String EXTRA_URL = "media_url";
    private String mediaUrl = "";

    @Override // com.harleytg.forum.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override // com.harleytg.forum.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        getWindow().setFlags(1024, 1024);
        String safeHttps = getIntent() == null ? "" : safeHttps(getIntent().getStringExtra(EXTRA_URL));
        this.mediaUrl = safeHttps;
        if (safeHttps.isEmpty()) {
            Toast.makeText(this, "This media link could not be opened safely.", 1).show();
            finish();
        } else {
            setContentView(buildUi());
            AppLogger.info(this, "media_viewer_open", AppLogger.safeUrl(this.mediaUrl));
        }
    }

    private LinearLayout buildUi() {
        String str;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(6), dp(4), dp(6), dp(4));
        linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, 0, 12, "Close media viewer");
        iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.MediaViewerActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MediaViewerActivity.this.m125lambda$buildUi$0$comharleytgforumdevMediaViewerActivity(view);
            }
        });
        linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView textView = new TextView(this);
        textView.setText("Media Viewer");
        textView.setTextColor(getColor(R.color.hcf_text));
        textView.setTextSize(14.0f);
        textView.setTypeface(null, 1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(8);
        linearLayout2.addView(textView, layoutParams);
        Button button = button("Share");
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.MediaViewerActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MediaViewerActivity.this.m126lambda$buildUi$1$comharleytgforumdevMediaViewerActivity(view);
            }
        });
        linearLayout2.addView(button, new LinearLayout.LayoutParams(dp(72), dp(46)));
        Button button2 = button("Open");
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.MediaViewerActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MediaViewerActivity.this.m127lambda$buildUi$2$comharleytgforumdevMediaViewerActivity(view);
            }
        });
        linearLayout2.addView(button2, new LinearLayout.LayoutParams(dp(72), dp(46)));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, dp(54)));
        WebView webView = new WebView(this);
        webView.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        WebSettings settings = webView.getSettings();
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
        settings.setMixedContentMode(1);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient());
        String valueOf = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra(EXTRA_KIND));
        String html = html(this.mediaUrl);
        if (valueOf.toLowerCase(Locale.US).contains("video")) {
            str = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes'><style>html,body{margin:0;background:#000;width:100%;height:100%;display:flex;align-items:center;justify-content:center}video{width:100%;max-height:100vh;background:#000}</style></head><body><video controls autoplay playsinline src='" + html + "'></video></body></html>";
        } else {
            str = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=8,user-scalable=yes'><style>html,body{margin:0;background:#000;min-height:100%;display:flex;align-items:center;justify-content:center}img{max-width:100%;height:auto;object-fit:contain}</style></head><body><img src='" + html + "' alt='Forum media'></body></html>";
        }
        webView.loadDataWithBaseURL("https://" + Uri.parse(this.mediaUrl).getHost() + "/", str, "text/html", "UTF-8", null);
        linearLayout.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return linearLayout;
    }

    /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-MediaViewerActivity, reason: not valid java name */
    /* synthetic */ void m125lambda$buildUi$0$comharleytgforumdevMediaViewerActivity(View view) {
        finish();
    }

    /* renamed from: lambda$buildUi$1$com-harleytg-forum-dev-MediaViewerActivity, reason: not valid java name */
    /* synthetic */ void m126lambda$buildUi$1$comharleytgforumdevMediaViewerActivity(View view) {
        share();
    }

    /* renamed from: lambda$buildUi$2$com-harleytg-forum-dev-MediaViewerActivity, reason: not valid java name */
    /* synthetic */ void m127lambda$buildUi$2$comharleytgforumdevMediaViewerActivity(View view) {
        openExternal();
    }

    private Button button(String str) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(str != null ? str.replaceFirst("^[^A-Za-z0-9]+", "").trim() : "");
        button.setTextColor(getColor(R.color.hcf_cyan_bright));
        button.setTextSize(11.0f);
        button.setBackgroundColor(0);
        FaIcons.applyStart(button, str);
        return button;
    }

    private void share() {
        try {
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType("text/plain");
            intent.putExtra("android.intent.extra.TEXT", this.mediaUrl);
            HcfIntentChooser.showShare(this, intent, "Share forum media", "Copy the link or share it with another app.");
        } catch (Throwable unused) {
            Toast.makeText(this, "Unable to share this media.", 0).show();
        }
    }

    private void openExternal() {
        try {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse(this.mediaUrl)));
        } catch (ActivityNotFoundException unused) {
            Toast.makeText(this, "No app can open this media.", 0).show();
        }
    }

    private static String safeHttps(String str) {
        String trim;
        if (str == null) {
            trim = "";
        } else {
            try {
                trim = str.trim();
            } catch (Throwable unused) {
            }
        }
        Uri parse = Uri.parse(trim);
        if ("https".equalsIgnoreCase(parse.getScheme()) && parse.getHost() != null && !parse.getHost().trim().isEmpty()) {
            return parse.toString();
        }
        return "";
    }

    private static String html(String str) {
        return str.replace("&", "&amp;").replace("'", "&#39;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
