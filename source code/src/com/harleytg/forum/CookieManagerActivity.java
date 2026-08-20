package com.harleytg.forum.dev;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/* loaded from: classes.dex */
public final class CookieManagerActivity extends ThemedActivity {
    private LinearLayout cookieList;
    private final Set<String> revealed = new HashSet();
    private TextView totalState;

    @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    static final class CookieEntry {
        final String host;
        final String name;
        final String value;

        CookieEntry(String str, String str2, String str3) {
            this.host = str;
            this.name = str2;
            this.value = str3;
        }

        String key() {
            return this.host + "|" + this.name;
        }
    }

    @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        setContentView(buildUi());
        refresh();
    }

    @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(10), dp(8), dp(10), dp(8));
        linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
        iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CookieManagerActivity.this.m0lambda$buildUi$0$comharleytgforumdevCookieManagerActivity(view);
            }
        });
        linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView text = text("Cookie Data", 18, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(10);
        linearLayout2.addView(text, layoutParams);
        linearLayout.addView(linearLayout2);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        linearLayout3.setPadding(dp(16), dp(16), dp(16), dp(28));
        scrollView.addView(linearLayout3);
        linearLayout3.addView(text("See each cookie the Android WebView currently sends to the forum. Values are masked until you choose Show. Cookie values stay on this phone and are not sent anywhere by this screen.", 12, getColor(R.color.hcf_muted)));
        TextView text2 = text("Checking cookie data…", 13, getColor(R.color.hcf_meta));
        this.totalState = text2;
        text2.setTypeface(null, 1);
        this.totalState.setPadding(0, dp(16), 0, dp(8));
        linearLayout3.addView(this.totalState);
        LinearLayout linearLayout4 = new LinearLayout(this);
        this.cookieList = linearLayout4;
        linearLayout4.setOrientation(1);
        linearLayout3.addView(this.cookieList);
        linearLayout3.addView(action("Refresh cookie data", new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CookieManagerActivity.this.m1lambda$buildUi$1$comharleytgforumdevCookieManagerActivity(view);
            }
        }));
        linearLayout3.addView(action("Clear all cookies + Web storage", new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CookieManagerActivity.this.m2lambda$buildUi$2$comharleytgforumdevCookieManagerActivity(view);
            }
        }));
        TextView text3 = text("Android WebView exposes cookie names and values for a URL, but not every stored attribute. Expiration, Path, SameSite, Secure and HttpOnly may not be available here. The Use label is a best-effort description based on the cookie name.", 11, getColor(R.color.hcf_muted));
        text3.setPadding(0, dp(16), 0, 0);
        linearLayout3.addView(text3);
        linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return linearLayout;
    }

    /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m0lambda$buildUi$0$comharleytgforumdevCookieManagerActivity(View view) {
        finish();
    }

    /* renamed from: lambda$buildUi$1$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m1lambda$buildUi$1$comharleytgforumdevCookieManagerActivity(View view) {
        refresh();
    }

    /* renamed from: lambda$buildUi$2$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m2lambda$buildUi$2$comharleytgforumdevCookieManagerActivity(View view) {
        clearAll();
    }

    private void refresh() {
        LinearLayout linearLayout = this.cookieList;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        List<CookieEntry> readCookies = readCookies("forum.harleytg.com");
        List<CookieEntry> readCookies2 = readCookies("harleysclan.freeflarum.com");
        int size = readCookies.size() + readCookies2.size();
        this.totalState.setText("Visible forum cookies: " + size + " • Primary " + readCookies.size() + " • Backup " + readCookies2.size());
        addHostSection("Primary", "forum.harleytg.com", readCookies);
        addHostSection("Backup", "harleysclan.freeflarum.com", readCookies2);
        if (size == 0) {
            TextView text = text("No visible forum cookies are currently stored.", 12, getColor(R.color.hcf_muted));
            text.setPadding(0, dp(8), 0, dp(8));
            this.cookieList.addView(text);
        }
    }

    private void addHostSection(String str, String str2, List<CookieEntry> list) {
        TextView text = text(str + " • " + str2 + " • " + list.size(), 12, getColor(R.color.hcf_accent_text));
        text.setTypeface(null, 1);
        text.setPadding(0, dp(14), 0, dp(4));
        this.cookieList.addView(text);
        if (list.isEmpty()) {
            TextView text2 = text("No visible cookies for this host.", 11, getColor(R.color.hcf_muted));
            text2.setPadding(0, 0, 0, dp(6));
            this.cookieList.addView(text2);
        } else {
            Iterator<CookieEntry> it = list.iterator();
            while (it.hasNext()) {
                this.cookieList.addView(cookieCard(it.next()));
            }
        }
    }

    private View cookieCard(final CookieEntry cookieEntry) {
        StringBuilder sb;
        String mask;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        linearLayout.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(8);
        linearLayout.setLayoutParams(layoutParams);
        TextView text = text(cookieEntry.name, 14, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        text.setTextIsSelectable(true);
        linearLayout.addView(text);
        TextView text2 = text("Use: " + describeUse(cookieEntry.name), 11, getColor(R.color.hcf_meta));
        text2.setPadding(0, dp(3), 0, 0);
        linearLayout.addView(text2);
        int length = cookieEntry.value.getBytes(StandardCharsets.UTF_8).length;
        StringBuilder sb2 = new StringBuilder("Host: ");
        sb2.append(cookieEntry.host);
        sb2.append(" • Value size: ");
        sb2.append(length);
        sb2.append(" byte");
        sb2.append(length == 1 ? "" : "s");
        TextView text3 = text(sb2.toString(), 10, getColor(R.color.hcf_muted));
        text3.setPadding(0, dp(3), 0, 0);
        linearLayout.addView(text3);
        boolean contains = this.revealed.contains(cookieEntry.key());
        if (contains) {
            sb = new StringBuilder("Value: ");
            mask = cookieEntry.value;
        } else {
            sb = new StringBuilder("Value: ");
            mask = mask(cookieEntry.value);
        }
        sb.append(mask);
        TextView text4 = text(sb.toString(), 11, getColor(R.color.hcf_text));
        text4.setTextIsSelectable(contains);
        text4.setPadding(0, dp(8), 0, 0);
        linearLayout.addView(text4);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setPadding(0, dp(10), 0, 0);
        Button button = button(contains ? "Hide value" : "Show value");
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CookieManagerActivity.this.m4lambda$cookieCard$3$comharleytgforumdevCookieManagerActivity(cookieEntry, view);
            }
        });
        linearLayout2.addView(button, new LinearLayout.LayoutParams(0, dp(46), 1.0f));
        Button button2 = button("Clear cookie");
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                CookieManagerActivity.this.m5lambda$cookieCard$4$comharleytgforumdevCookieManagerActivity(cookieEntry, view);
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, dp(46), 1.0f);
        layoutParams2.leftMargin = dp(8);
        linearLayout2.addView(button2, layoutParams2);
        linearLayout.addView(linearLayout2);
        return linearLayout;
    }

    /* renamed from: lambda$cookieCard$3$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m4lambda$cookieCard$3$comharleytgforumdevCookieManagerActivity(CookieEntry cookieEntry, View view) {
        if (this.revealed.contains(cookieEntry.key())) {
            this.revealed.remove(cookieEntry.key());
        } else {
            this.revealed.add(cookieEntry.key());
        }
        refresh();
    }

    /* renamed from: lambda$cookieCard$4$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m5lambda$cookieCard$4$comharleytgforumdevCookieManagerActivity(CookieEntry cookieEntry, View view) {
        clearCookie(cookieEntry);
    }

    private List<CookieEntry> readCookies(String str) {
        String cookie = CookieManager.getInstance().getCookie("https://" + str + "/");
        ArrayList arrayList = new ArrayList();
        if (TextUtils.isEmpty(cookie)) {
            return arrayList;
        }
        for (String str2 : cookie.split(";")) {
            String trim = str2.trim();
            if (!trim.isEmpty()) {
                int indexOf = trim.indexOf(61);
                String trim2 = indexOf >= 0 ? trim.substring(0, indexOf).trim() : trim;
                String substring = indexOf >= 0 ? trim.substring(indexOf + 1) : "";
                if (!trim2.isEmpty()) {
                    arrayList.add(new CookieEntry(str, trim2, substring));
                }
            }
        }
        return arrayList;
    }

    private String describeUse(String str) {
        String lowerCase = str == null ? "" : str.toLowerCase(Locale.US);
        if (lowerCase.contains("csrf") || lowerCase.contains("xsrf")) {
            return "Request/security protection";
        }
        if (lowerCase.contains("session") || lowerCase.contains("auth") || lowerCase.contains("token")) {
            return "Sign-in or session state";
        }
        if (lowerCase.contains("remember") || lowerCase.contains("login")) {
            return "Remembered sign-in";
        }
        if (lowerCase.contains("consent") || lowerCase.contains("cookie")) {
            return "Cookie/consent preference";
        }
        if (lowerCase.contains("theme") || lowerCase.contains("dark") || lowerCase.contains("light")) {
            return "Appearance preference";
        }
        if (lowerCase.contains("lang") || lowerCase.contains("locale")) {
            return "Language/locale preference";
        }
        if (lowerCase.contains("notification") || lowerCase.contains("alert")) {
            return "Notification/site state";
        }
        return lowerCase.contains("flarum") ? "Flarum forum state" : "Site data (exact purpose is not exposed by WebView)";
    }

    private String mask(String str) {
        if (str == null || str.isEmpty()) {
            return "(empty)";
        }
        int min = Math.min(24, Math.max(8, str.length()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < min; i++) {
            sb.append((char) 8226);
        }
        if (str.length() > min) {
            sb.append((char) 8230);
        }
        return sb.toString();
    }

    private void clearCookie(CookieEntry cookieEntry) {
        String str = "https://" + cookieEntry.host + "/";
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setCookie(str, cookieEntry.name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=Lax");
        cookieManager.flush();
        this.revealed.remove(cookieEntry.key());
        AppLogger.info(this, "cookie_clear_one", cookieEntry.host + " | " + cookieEntry.name);
        StringBuilder sb = new StringBuilder("Cleared ");
        sb.append(cookieEntry.name);
        Toast.makeText(this, sb.toString(), 0).show();
        refresh();
    }

    private void clearAll() {
        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(new ValueCallback() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda5
            @Override // android.webkit.ValueCallback
            public final void onReceiveValue(Object obj) {
                CookieManagerActivity.this.m3lambda$clearAll$5$comharleytgforumdevCookieManagerActivity(cookieManager, (Boolean) obj);
            }
        });
    }

    /* renamed from: lambda$clearAll$5$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
    /* synthetic */ void m3lambda$clearAll$5$comharleytgforumdevCookieManagerActivity(CookieManager cookieManager, Boolean bool) {
        cookieManager.flush();
        WebStorage.getInstance().deleteAllData();
        ForumIdentity.clear(this);
        this.revealed.clear();
        AppLogger.info(this, "cookie_all_clear", "cookie_manager");
        Toast.makeText(this, "All forum cookies and Web storage cleared.", 1).show();
        refresh();
    }

    private Button action(String str, View.OnClickListener onClickListener) {
        Button button = button(str);
        button.setGravity(8388627);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
        layoutParams.topMargin = dp(8);
        button.setLayoutParams(layoutParams);
        return button;
    }

    private Button button(String str) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(str != null ? str.replaceFirst("^[^A-Za-z0-9]+", "").trim() : "");
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setAllCaps(false);
        FaIcons.applyStart(button, str);
        return button;
    }

    private TextView text(String str, int i, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        textView.setLineSpacing(0.0f, 1.12f);
        return textView;
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == 2;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
