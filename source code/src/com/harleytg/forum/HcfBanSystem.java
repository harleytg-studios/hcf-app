package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** Native HCF IP/username observation gate plus the dedicated Access Restricted screen. */
public final class HcfBanSystem {
    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/ban-system.config";
    private static final String PREF_CONFIG_CACHE = "ban_system_config_cache";
    private static final String PREF_CONFIG_FETCHED_AT = "ban_system_config_fetched_at";
    private static final long CONFIG_CACHE_MS = 6L * 60L * 60L * 1000L;

    private HcfBanSystem() {}

    private static class BaseActivity extends Activity {
        int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        TextView text(String value, int sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextSize(sp);
            view.setTextColor(color);
            return view;
        }

        GradientDrawable panelDrawable(int fill, int stroke, int radiusDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radiusDp));
            drawable.setStroke(dp(1), stroke);
            return drawable;
        }
    }

    static final class RuntimeConfig {
        final boolean enabled;
        final String observeUrl;
        final String ipPrimary;
        final String ipFallback;

        RuntimeConfig(boolean enabled, String observeUrl, String ipPrimary, String ipFallback) {
            this.enabled = enabled;
            this.observeUrl = safe(observeUrl);
            this.ipPrimary = safe(ipPrimary);
            this.ipFallback = safe(ipFallback);
        }

        boolean ready() {
            return enabled && observeUrl.startsWith("https://");
        }
    }

    static final class PublicIp {
        final String address;
        final String source;

        PublicIp(String address, String source) {
            this.address = normalizeIp(address);
            this.source = safe(source);
        }

        boolean available() {
            return !address.isEmpty();
        }
    }

    static final class CheckResult {
        final boolean banned;
        final String banId;
        final String reason;
        final String expiresAt;
        final String scope;
        final String username;
        final String maskedIp;
        final boolean appealAllowed;

        CheckResult(boolean banned, String banId, String reason, String expiresAt,
                    String scope, String username, String maskedIp, boolean appealAllowed) {
            this.banned = banned;
            this.banId = safe(banId);
            this.reason = safe(reason);
            this.expiresAt = safe(expiresAt);
            this.scope = safe(scope);
            this.username = safe(username);
            this.maskedIp = safe(maskedIp);
            this.appealAllowed = appealAllowed;
        }
    }

    /** Launcher gate. All normal launches and HCF App Links should enter here first. */
    public static final class GateActivity extends BaseActivity {
        private final Handler main = new Handler(Looper.getMainLooper());
        private TextView status;
        private TextView detail;
        private ProgressBar progress;
        private volatile boolean destroyed;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            try { ThemeManager.apply(this); } catch (Throwable ignored) {}
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi(bg));

            Thread worker = new Thread(new Runnable() {
                @Override public void run() { runGate(); }
            }, "hcf-ban-gate");
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.start();
        }

        @Override
        protected void onDestroy() {
            destroyed = true;
            main.removeCallbacksAndMessages(null);
            super.onDestroy();
        }

        private View buildUi(int bg) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(34), dp(24), dp(28));
            root.setBackgroundColor(bg);

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            root.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));

            TextView brand = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(14);
            root.addView(brand, brandLp);

            TextView title = text("Starting Harley's Clan Forum", 21, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(6);
            root.addView(title, titleLp);

            status = text("Checking access status", 13, getColor(R.color.hcf_cyan_bright));
            status.setTypeface(null, 1);
            status.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
            statusLp.topMargin = dp(22);
            root.addView(status, statusLp);

            detail = text("Preparing the HCF security check before the forum opens.", 11,
                    getColor(R.color.hcf_muted));
            detail.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(6);
            root.addView(detail, detailLp);

            progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(4);
            progress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
            progressLp.topMargin = dp(24);
            root.addView(progress, progressLp);

            TextView privacy = text("Public IP is used for HCF security and abuse-prevention checks.",
                    9, getColor(R.color.hcf_hint));
            privacy.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
            privacyLp.topMargin = dp(16);
            root.addView(privacy, privacyLp);
            return root;
        }

        private void runGate() {
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            String username = identity != null && identity.loggedIn ? safe(identity.username) : "";
            stage(18, "Checking forum identity",
                    username.isEmpty() ? "Guest session detected." : "Signed-in user: @" + username);

            RuntimeConfig config;
            try {
                config = loadRuntimeConfig(this);
            } catch (Throwable error) {
                AppLogger.warn(this, "ban_gate_config", error.getClass().getSimpleName());
                stage(100, "Access check unavailable", "Ban service configuration could not be loaded; startup will continue.");
                continueStartupSoon();
                return;
            }

            if (!config.ready()) {
                stage(100, "Access system not enabled", "The ban-service endpoint is not active yet; startup will continue.");
                continueStartupSoon();
                return;
            }

            stage(36, "Checking public network address", "Using ipify with IPinfo as the fallback lookup.");
            PublicIp publicIp = resolvePublicIp(config);
            stage(55, "Network identity ready", publicIp.available()
                    ? "Public IP resolved through " + publicIp.source + "."
                    : "IP lookup unavailable; the access service will use the connection address.");

            try {
                stage(72, "Checking HCF access record", "Looking for an active username or IP ban.");
                CheckResult result = observeAndCheck(this, config, identity, publicIp);
                if (result.banned) {
                    AppLogger.warn(this, "ban_gate", "blocked | scope=" + result.scope + " | id=" + result.banId);
                    openBanScreen(result);
                    return;
                }
                stage(100, "Access allowed", "No active HCF ban was found.");
                continueStartupSoon();
            } catch (Throwable error) {
                // Fail open: a Worker/GitHub/IP-provider outage must not lock out every HCF user.
                AppLogger.warn(this, "ban_gate", "fail-open | " + error.getClass().getSimpleName());
                stage(100, "Access check unavailable", "Security service could not be reached; startup will continue.");
                continueStartupSoon();
            }
        }

        private void stage(final int value, final String statusText, final String detailText) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed()) return;
                    if (status != null) status.setText(statusText);
                    if (detail != null) detail.setText(detailText);
                    if (progress != null) progress.setProgress(Math.max(0, Math.min(100, value)), true);
                }
            });
        }

        private void continueStartupSoon() {
            main.postDelayed(new Runnable() {
                @Override public void run() { continueStartup(); }
            }, 220L);
        }

        private void continueStartup() {
            if (destroyed || isFinishing() || isDestroyed()) return;
            Intent target = new Intent(this, HcfUITheme.StartupActivity.class);
            Intent source = getIntent();
            if (source != null) {
                if (source.getData() != null) target.setData(source.getData());
                if (!TextUtils.isEmpty(source.getAction()) && !Intent.ACTION_MAIN.equals(source.getAction())) {
                    target.setAction(source.getAction());
                }
            }
            startActivity(target);
            overridePendingTransition(0, 0);
            finish();
        }

        private void openBanScreen(final CheckResult result) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed()) return;
                    Intent intent = new Intent(GateActivity.this, BanActivity.class);
                    intent.putExtra("ban_id", result.banId);
                    intent.putExtra("reason", result.reason);
                    intent.putExtra("expires_at", result.expiresAt);
                    intent.putExtra("scope", result.scope);
                    intent.putExtra("username", result.username);
                    intent.putExtra("masked_ip", result.maskedIp);
                    intent.putExtra("appeal_allowed", result.appealAllowed);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }
            });
        }
    }

    /** Dedicated native ban screen. */
    public static final class BanActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            try { ThemeManager.apply(this); } catch (Throwable ignored) {}
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi(bg));
        }

        @Override
        public void onBackPressed() {
            finishAffinity();
        }

        private View buildUi(int bg) {
            Intent data = getIntent();
            final String banId = safe(data == null ? "" : data.getStringExtra("ban_id"));
            String reason = safe(data == null ? "" : data.getStringExtra("reason"));
            String expiresAt = safe(data == null ? "" : data.getStringExtra("expires_at"));
            String scope = safe(data == null ? "" : data.getStringExtra("scope"));
            String username = safe(data == null ? "" : data.getStringExtra("username"));
            String maskedIp = safe(data == null ? "" : data.getStringExtra("masked_ip"));
            boolean appealAllowed = data == null || data.getBooleanExtra("appeal_allowed", true);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(bg);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(30), dp(24), dp(30));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            root.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

            TextView brand = text("HARLEY'S CLAN FORUM", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(12);
            root.addView(brand, brandLp);

            TextView title = text("Access Restricted", 24, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(6);
            root.addView(title, titleLp);

            TextView subtitle = text("This account or network currently has an active HCF access ban.",
                    12, getColor(R.color.hcf_muted));
            subtitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(8);
            root.addView(subtitle, subLp);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(panelDrawable(getColor(R.color.hcf_surface), getColor(R.color.hcf_cyan), 12));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.topMargin = dp(22);
            root.addView(card, cardLp);

            card.addView(info("Ban ID", banId.isEmpty() ? "HCF-BAN" : banId));
            card.addView(info("Scope", "user".equalsIgnoreCase(scope) ? "Forum account" : "Network / IP"));
            if (!username.isEmpty()) card.addView(info("Username", "@" + username));
            if (!maskedIp.isEmpty()) card.addView(info("Network", maskedIp));
            card.addView(info("Expires", expiresAt.isEmpty() ? "Permanent / until removed" : expiresAt));

            TextView reasonTitle = text("Reason", 11, getColor(R.color.hcf_meta));
            reasonTitle.setTypeface(null, 1);
            LinearLayout.LayoutParams reasonTitleLp = new LinearLayout.LayoutParams(-1, -2);
            reasonTitleLp.topMargin = dp(14);
            card.addView(reasonTitle, reasonTitleLp);
            TextView reasonText = text(reason.isEmpty() ? "Access restricted by an administrator." : reason,
                    14, getColor(R.color.hcf_text));
            card.addView(reasonText, new LinearLayout.LayoutParams(-1, -2));

            Button retry = button("Check Again", true);
            retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    startActivity(new Intent(BanActivity.this, GateActivity.class));
                    finish();
                }
            });
            addButton(root, retry, 20);

            if (appealAllowed) {
                Button appeal = button("Appeal Ban", false);
                appeal.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { openAppeal(banId); }
                });
                addButton(root, appeal, 10);
            }

            Button close = button("Close App", false);
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { finishAffinity(); }
            });
            addButton(root, close, 10);

            TextView privacy = text("IP information is processed for forum security and abuse prevention.",
                    9, getColor(R.color.hcf_hint));
            privacy.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
            privacyLp.topMargin = dp(18);
            root.addView(privacy, privacyLp);
            return scroll;
        }

        private TextView info(String label, String value) {
            TextView line = text(label + ": " + value, 12, getColor(R.color.hcf_text));
            line.setTextIsSelectable(true);
            line.setPadding(0, dp(3), 0, dp(3));
            return line;
        }

        private Button button(String label, boolean primary) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextColor(primary ? getColor(R.color.hcf_on_accent) : getColor(R.color.hcf_text));
            button.setBackground(panelDrawable(
                    primary ? getColor(R.color.hcf_cyan) : getColor(R.color.hcf_surface),
                    getColor(R.color.hcf_cyan), 10));
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(0);
            return button;
        }

        private void addButton(LinearLayout root, Button button, int topMargin) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
            lp.topMargin = dp(topMargin);
            root.addView(button, lp);
        }

        private void openAppeal(String banId) {
            try {
                String subject = "HCF Ban Appeal" + (banId.isEmpty() ? "" : " - " + banId);
                Uri uri = Uri.parse("mailto:harleytg.hq@gmail.com?subject=" + Uri.encode(subject));
                startActivity(new Intent(Intent.ACTION_SENDTO, uri));
            } catch (Throwable error) {
                AppLogger.warn(this, "ban_appeal", error.getClass().getSimpleName());
            }
        }
    }

    private static RuntimeConfig loadRuntimeConfig(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        long now = System.currentTimeMillis();
        long fetchedAt = prefs.getLong(PREF_CONFIG_FETCHED_AT, 0L);
        String cached = safe(prefs.getString(PREF_CONFIG_CACHE, ""));
        if (!cached.isEmpty() && fetchedAt > 0L && now - fetchedAt < CONFIG_CACHE_MS) {
            return parseConfig(cached);
        }

        try {
            String remote = getText(CONFIG_URL, 4000, 4000);
            RuntimeConfig parsed = parseConfig(remote);
            prefs.edit().putString(PREF_CONFIG_CACHE, remote)
                    .putLong(PREF_CONFIG_FETCHED_AT, now).apply();
            return parsed;
        } catch (Throwable error) {
            if (!cached.isEmpty()) return parseConfig(cached);
            throw error;
        }
    }

    private static RuntimeConfig parseConfig(String raw) {
        boolean enabled = false;
        String observe = "";
        String primary = "https://api.ipify.org?format=json";
        String fallback = "https://ipinfo.io/json";
        String section = "";

        for (String source : (raw == null ? "" : raw).split("\\r?\\n")) {
            String line = source == null ? "" : source.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.US);
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) continue;
            String key = line.substring(0, split).trim().toLowerCase(Locale.US);
            String value = line.substring(split + 1).trim();
            if ("config".equals(section) && "enabled".equals(key)) {
                enabled = "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
            } else if ("endpoint".equals(section) && "observe_url".equals(key)) {
                observe = value;
            } else if ("ip_lookup".equals(section) && "primary".equals(key)) {
                primary = value;
            } else if ("ip_lookup".equals(section) && "fallback".equals(key)) {
                fallback = value;
            }
        }
        return new RuntimeConfig(enabled, observe, primary, fallback);
    }

    private static PublicIp resolvePublicIp(RuntimeConfig config) {
        PublicIp primary = lookupIp(config.ipPrimary, "ipify");
        if (primary.available()) return primary;
        PublicIp fallback = lookupIp(config.ipFallback, "IPinfo");
        return fallback.available() ? fallback : new PublicIp("", "unavailable");
    }

    private static PublicIp lookupIp(String urlText, String source) {
        if (TextUtils.isEmpty(urlText) || !urlText.startsWith("https://")) return new PublicIp("", source);
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/plain;q=0.9");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanIpLookup");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return new PublicIp("", source);
            String body = readAll(connection.getInputStream());
            try {
                return new PublicIp(new JSONObject(body).optString("ip", ""), source);
            } catch (Throwable ignored) {
                return new PublicIp(body.trim(), source);
            }
        } catch (Throwable ignored) {
            return new PublicIp("", source);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static CheckResult observeAndCheck(Context context, RuntimeConfig config,
                                               ForumIdentity.Snapshot identity, PublicIp publicIp) throws Exception {
        boolean loggedIn = identity != null && identity.loggedIn && !safe(identity.username).isEmpty();
        String username = loggedIn ? safe(identity.username) : "";

        JSONObject payload = new JSONObject();
        payload.put("logged_in", loggedIn);
        payload.put("username", username);
        payload.put("reported_ip", publicIp == null ? "" : publicIp.address);
        payload.put("reported_ip_source", publicIp == null ? "" : publicIp.source);
        payload.put("app_version", BuildInfo.installedVersionName());
        payload.put("version_code", BuildInfo.VERSION_CODE);
        payload.put("package_name", context.getPackageName());

        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(config.observeUrl).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-HCF-Client", "android");
            connection.setRequestProperty("X-HCF-App-Package", context.getPackageName());
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanSystem/1");
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(payload.toString());
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (code < 200 || code >= 300) throw new IllegalStateException("Ban service HTTP " + code);

            JSONObject response = new JSONObject(body);
            boolean banned = response.optBoolean("banned", false)
                    || "banned".equalsIgnoreCase(response.optString("status", ""));
            String maskedIp = response.optString("masked_ip", "");
            String scope = response.optString("scope", "");
            String responseUsername = response.optString("username", username);
            if (!banned) return new CheckResult(false, "", "", "", scope, responseUsername, maskedIp, true);

            JSONObject ban = response.optJSONObject("ban");
            return new CheckResult(true,
                    ban == null ? "" : ban.optString("ban_id", ""),
                    ban == null ? "" : ban.optString("reason", ""),
                    ban == null ? "" : ban.optString("expires_at", ""),
                    scope, responseUsername, maskedIp,
                    ban == null || ban.optBoolean("appeal_allowed", true));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String getText(String urlText, int connectTimeout, int readTimeout) throws Exception {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanConfig/1");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            return readAll(connection.getInputStream());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                if (out.length() > 65536) throw new IllegalStateException("Response too large");
            }
            return out.toString();
        }
    }

    private static String normalizeIp(String value) {
        String raw = safe(value);
        if (raw.isEmpty() || raw.length() > 64) return "";
        if (raw.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            String[] parts = raw.split("\\.");
            for (String part : parts) {
                try {
                    int number = Integer.parseInt(part);
                    if (number < 0 || number > 255) return "";
                } catch (Throwable ignored) { return ""; }
            }
            return raw;
        }
        if (raw.indexOf(':') >= 0 && raw.matches("^[0-9a-fA-F:]+$")) return raw.toLowerCase(Locale.US);
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace((char) 0, ' ').trim();
    }
}
