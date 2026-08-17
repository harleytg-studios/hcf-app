package com.harleytg.forum;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CookieManagerActivity extends ThemedActivity {
    private LinearLayout cookieList;
    private TextView totalState;
    private final Set<String> revealed = new HashSet<>();

    private static final class CookieEntry {
        final String host;
        final String name;
        final String value;
        CookieEntry(String host, String name, String value) {
            this.host = host;
            this.name = name;
            this.value = value;
        }
        String key() { return host + "|" + name; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));
        ImageButton back = UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text("Cookie Data", 18, getColor(R.color.hcf_text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleP.leftMargin = dp(10);
        header.addView(title, titleP);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(content);

        TextView intro = text(
                "See each cookie the Android WebView currently sends to the forum. Values are masked until you choose Show. Cookie values stay on this phone and are not sent anywhere by this screen.",
                12,
                getColor(R.color.hcf_muted));
        content.addView(intro);

        totalState = text("Checking cookie data…", 13, getColor(R.color.hcf_meta));
        totalState.setTypeface(null, android.graphics.Typeface.BOLD);
        totalState.setPadding(0, dp(16), 0, dp(8));
        content.addView(totalState);

        cookieList = new LinearLayout(this);
        cookieList.setOrientation(LinearLayout.VERTICAL);
        content.addView(cookieList);

        content.addView(action("Refresh cookie data", v -> refresh()));
        content.addView(action("Clear all cookies + Web storage", v -> clearAll()));

        TextView note = text(
                "Android WebView exposes cookie names and values for a URL, but not every stored attribute. Expiration, Path, SameSite, Secure and HttpOnly may not be available here. The Use label is a best-effort description based on the cookie name.",
                11,
                getColor(R.color.hcf_muted));
        note.setPadding(0, dp(16), 0, 0);
        content.addView(note);

        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void refresh() {
        if (cookieList == null) return;
        cookieList.removeAllViews();
        List<CookieEntry> primary = readCookies(ForumConfig.PRIMARY_HOST);
        List<CookieEntry> backup = readCookies(ForumConfig.BACKUP_HOST);
        int total = primary.size() + backup.size();
        totalState.setText("Visible forum cookies: " + total + " • Primary " + primary.size() + " • Backup " + backup.size());

        addHostSection("Primary", ForumConfig.PRIMARY_HOST, primary);
        addHostSection("Backup", ForumConfig.BACKUP_HOST, backup);
        if (total == 0) {
            TextView empty = text("No visible forum cookies are currently stored.", 12, getColor(R.color.hcf_muted));
            empty.setPadding(0, dp(8), 0, dp(8));
            cookieList.addView(empty);
        }
    }

    private void addHostSection(String label, String host, List<CookieEntry> entries) {
        TextView heading = text(label + " • " + host + " • " + entries.size(), 12, getColor(R.color.hcf_accent_text));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, dp(4));
        cookieList.addView(heading);

        if (entries.isEmpty()) {
            TextView empty = text("No visible cookies for this host.", 11, getColor(R.color.hcf_muted));
            empty.setPadding(0, 0, 0, dp(6));
            cookieList.addView(empty);
            return;
        }
        for (CookieEntry entry : entries) cookieList.addView(cookieCard(entry));
    }

    private View cookieCard(CookieEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.card_background);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(8);
        card.setLayoutParams(cp);

        TextView name = text(entry.name, 14, getColor(R.color.hcf_text));
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setTextIsSelectable(true);
        card.addView(name);

        TextView use = text("Use: " + describeUse(entry.name), 11, getColor(R.color.hcf_meta));
        use.setPadding(0, dp(3), 0, 0);
        card.addView(use);

        int bytes = entry.value.getBytes(StandardCharsets.UTF_8).length;
        TextView meta = text("Host: " + entry.host + " • Value size: " + bytes + " byte" + (bytes == 1 ? "" : "s"), 10, getColor(R.color.hcf_muted));
        meta.setPadding(0, dp(3), 0, 0);
        card.addView(meta);

        boolean isShown = revealed.contains(entry.key());
        TextView value = text(isShown ? "Value: " + entry.value : "Value: " + mask(entry.value), 11, getColor(R.color.hcf_text));
        value.setTextIsSelectable(isShown);
        value.setPadding(0, dp(8), 0, 0);
        card.addView(value);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);

        Button show = button(isShown ? "Hide value" : "Show value");
        show.setOnClickListener(v -> {
            if (revealed.contains(entry.key())) revealed.remove(entry.key());
            else revealed.add(entry.key());
            refresh();
        });
        actions.addView(show, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button clear = button("Clear cookie");
        clear.setOnClickListener(v -> clearCookie(entry));
        LinearLayout.LayoutParams clearP = new LinearLayout.LayoutParams(0, dp(46), 1f);
        clearP.leftMargin = dp(8);
        actions.addView(clear, clearP);
        card.addView(actions);
        return card;
    }

    private List<CookieEntry> readCookies(String host) {
        String raw = CookieManager.getInstance().getCookie("https://" + host + "/");
        List<CookieEntry> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return out;
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int equals = trimmed.indexOf('=');
            String name = equals >= 0 ? trimmed.substring(0, equals).trim() : trimmed;
            String value = equals >= 0 ? trimmed.substring(equals + 1) : "";
            if (!name.isEmpty()) out.add(new CookieEntry(host, name, value));
        }
        return out;
    }

    private String describeUse(String cookieName) {
        String n = cookieName == null ? "" : cookieName.toLowerCase(Locale.US);
        if (n.contains("csrf") || n.contains("xsrf")) return "Request/security protection";
        if (n.contains("session") || n.contains("auth") || n.contains("token")) return "Sign-in or session state";
        if (n.contains("remember") || n.contains("login")) return "Remembered sign-in";
        if (n.contains("consent") || n.contains("cookie")) return "Cookie/consent preference";
        if (n.contains("theme") || n.contains("dark") || n.contains("light")) return "Appearance preference";
        if (n.contains("lang") || n.contains("locale")) return "Language/locale preference";
        if (n.contains("notification") || n.contains("alert")) return "Notification/site state";
        if (n.contains("flarum")) return "Flarum forum state";
        return "Site data (exact purpose is not exposed by WebView)";
    }

    private String mask(String value) {
        if (value == null || value.isEmpty()) return "(empty)";
        int len = Math.min(24, Math.max(8, value.length()));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < len; i++) b.append('•');
        if (value.length() > len) b.append('…');
        return b.toString();
    }

    private void clearCookie(CookieEntry entry) {
        String url = "https://" + entry.host + "/";
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie(url, entry.name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=Lax");
        cm.flush();
        revealed.remove(entry.key());
        AppLogger.info(this, "cookie_clear_one", entry.host + " | " + entry.name);
        Toast.makeText(this, "Cleared " + entry.name, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void clearAll() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(value -> {
            cm.flush();
            WebStorage.getInstance().deleteAllData();
            ForumIdentity.clear(CookieManagerActivity.this);
            revealed.clear();
            AppLogger.info(CookieManagerActivity.this, "cookie_all_clear", "cookie_manager");
            Toast.makeText(CookieManagerActivity.this, "All forum cookies and Web storage cleared.", Toast.LENGTH_LONG).show();
            refresh();
        });
    }

    private Button action(String label, View.OnClickListener listener) {
        Button b = button(label);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 44 : 52));
        p.topMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private Button button(String label) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label == null ? "" : label.replaceFirst("^[^A-Za-z0-9]+", "").trim());
        b.setTextColor(getColor(R.color.hcf_accent_text));
        b.setBackgroundResource(R.drawable.quick_action_background);
        b.setAllCaps(false);
        FaIcons.applyStart(b, label);
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private boolean compact() {
        return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
