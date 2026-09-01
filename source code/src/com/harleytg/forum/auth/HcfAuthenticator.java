package com.harleytg.forum.dev;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Native offline RFC 6238 authenticator for Harley's Clan Forum. */
public final class HcfAuthenticator {
    private HcfAuthenticator() {}

    public static final String OPEN_URI = "hcf-auth://open";

    private static final int BG = Color.rgb(13, 16, 20);
    private static final int PANEL = Color.rgb(17, 27, 34);
    private static final int PANEL_DARK = Color.rgb(12, 21, 27);
    private static final int BORDER = Color.rgb(41, 64, 75);
    private static final int CYAN = Color.rgb(0, 184, 240);
    private static final int TEXT = Color.rgb(239, 247, 250);
    private static final int MUTED = Color.rgb(155, 174, 183);
    private static final int GOOD = Color.rgb(85, 225, 59);
    private static final int DANGER = Color.rgb(255, 77, 87);

    public static final class Activity extends android.app.Activity {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Config config;
        private String lastCode = "";
        private TextView status;
        private TextView account;
        private TextView code;
        private TextView countdown;
        private ProgressBar progress;
        private EditText accountInput;
        private EditText secretInput;

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                renderCode();
                handler.postDelayed(this, 250L);
            }
        };

        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            buildUi();
            reload();
            handleIntent(getIntent());
        }

        @Override protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            handleIntent(intent);
        }

        @Override protected void onResume() {
            super.onResume();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }

        @Override protected void onPause() {
            handler.removeCallbacks(ticker);
            super.onPause();
        }

        private void buildUi() {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(BG);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(12), dp(18), dp(28));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            Button back = button("‹", false);
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            back.setOnClickListener(v -> finish());
            header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("HCF Authenticator", 21, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labels.addView(title);
            labels.addView(text("Two-factor authentication • Offline code generator", 10, MUTED));
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            labelsLp.leftMargin = dp(10);
            header.addView(labels, labelsLp);
            root.addView(header);

            LinearLayout local = panel();
            local.addView(section("LOCAL & ENCRYPTED"));
            local.addView(text("Codes are generated on this device from the encrypted setup key and current time. Internet is not required after setup.", 11, MUTED));
            TextView secure = text("Screenshots and screen recording are blocked here.", 10, CYAN);
            secure.setPadding(0, dp(7), 0, 0);
            local.addView(secure);
            root.addView(local, panelLp());

            LinearLayout state = panel();
            state.addView(section("AUTHENTICATOR STATUS"));
            status = text("Not configured", 16, DANGER);
            status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            state.addView(status);
            account = text("No forum account enrolled", 11, MUTED);
            account.setPadding(0, dp(4), 0, 0);
            state.addView(account);
            root.addView(state, panelLp());

            LinearLayout current = panel();
            current.addView(section("CURRENT ACCESS CODE"));
            code = text("--- ---", 38, TEXT);
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            code.setGravity(Gravity.CENTER);
            code.setLetterSpacing(0.08f);
            code.setPadding(0, dp(8), 0, dp(4));
            current.addView(code, new LinearLayout.LayoutParams(-1, -2));
            countdown = text("Configure the authenticator to generate a code.", 11, MUTED);
            countdown.setGravity(Gravity.CENTER);
            current.addView(countdown, new LinearLayout.LayoutParams(-1, -2));
            progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(30);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(5));
            progressLp.topMargin = dp(10);
            current.addView(progress, progressLp);
            Button copy = button("Copy current code", true);
            copy.setOnClickListener(v -> copyCode());
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-1, dp(48));
            copyLp.topMargin = dp(12);
            current.addView(copy, copyLp);
            root.addView(current, panelLp());

            LinearLayout setup = panel();
            setup.addView(section("SET UP FROM THE FORUM"));
            setup.addView(text("Copy the Base32 setup key from the forum's Two-Factor Authentication screen, or open an otpauth:// setup link in HCF.", 11, MUTED));
            accountInput = input("Forum account name (optional)");
            accountInput.setInputType(InputType.TYPE_CLASS_TEXT);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, dp(50));
            fieldLp.topMargin = dp(12);
            setup.addView(accountInput, fieldLp);
            secretInput = input("Base32 setup key");
            secretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            LinearLayout.LayoutParams secretLp = new LinearLayout.LayoutParams(-1, dp(50));
            secretLp.topMargin = dp(8);
            setup.addView(secretInput, secretLp);
            Button save = button("Save setup key", true);
            save.setOnClickListener(v -> saveManual());
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
            saveLp.topMargin = dp(10);
            setup.addView(save, saveLp);
            root.addView(setup, panelLp());

            LinearLayout manage = panel();
            manage.addView(section("MANAGE"));
            Button forum = button("Open Harley's Clan Forum", false);
            forum.setOnClickListener(v -> openForum());
            manage.addView(forum, new LinearLayout.LayoutParams(-1, dp(48)));
            Button remove = button("Remove authenticator from this device", false);
            remove.setTextColor(DANGER);
            remove.setOnClickListener(v -> remove());
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(-1, dp(48));
            removeLp.topMargin = dp(8);
            manage.addView(remove, removeLp);
            TextView warning = text("Removing the key here does not disable 2FA on the website. Keep your forum recovery codes somewhere safe.", 10, MUTED);
            warning.setPadding(0, dp(10), 0, 0);
            manage.addView(warning);
            root.addView(manage, panelLp());

            TextView footer = text("RFC 6238 TOTP • Android Keystore • No cloud sync", 9, MUTED);
            footer.setGravity(Gravity.CENTER);
            root.addView(footer);
            setContentView(scroll);
        }

        private void reload() {
            try {
                config = Vault.load(this);
            } catch (Throwable error) {
                Vault.clear(this);
                config = null;
                Toast.makeText(this, "Authenticator storage could not be unlocked. Set it up again.", Toast.LENGTH_LONG).show();
            }
            renderState();
        }

        private void renderState() {
            boolean ready = config != null && !config.secret.isEmpty();
            status.setText(ready ? "Configured" : "Not configured");
            status.setTextColor(ready ? GOOD : DANGER);
            if (ready) {
                account.setText(config.displayLabel() + " • " + config.digits + " digits • " + config.period + " sec");
            } else {
                account.setText("No forum account enrolled");
                code.setText("--- ---");
                countdown.setText("Configure the authenticator to generate a code.");
                progress.setMax(30);
                progress.setProgress(0);
                lastCode = "";
            }
        }

        private void renderCode() {
            if (config == null) return;
            try {
                long now = System.currentTimeMillis() / 1000L;
                lastCode = Totp.generate(config, now);
                code.setText(formatCode(lastCode));
                int elapsed = (int) (now % config.period);
                int remaining = config.period - elapsed;
                progress.setMax(config.period);
                progress.setProgress(elapsed);
                countdown.setText("New code in " + remaining + " second" + (remaining == 1 ? "" : "s") + " • works offline");
            } catch (Throwable error) {
                lastCode = "";
                code.setText("--- ---");
                countdown.setText("Unable to generate code. Check the setup key and device time.");
            }
        }

        private void handleIntent(Intent intent) {
            if (intent == null || intent.getData() == null) return;
            Uri uri = intent.getData();
            if (!"otpauth".equalsIgnoreCase(uri.getScheme())) return;
            try {
                Config incoming = Config.fromOtpAuth(uri);
                confirmSave(incoming, config == null ? "Add" : "Replace");
            } catch (Throwable error) {
                Toast.makeText(this, "This authenticator setup link is invalid or unsupported.", Toast.LENGTH_LONG).show();
            }
        }

        private void saveManual() {
            String raw = secretInput.getText().toString().trim();
            if (raw.isEmpty()) {
                secretInput.setError("Enter the setup key from the forum");
                return;
            }
            try {
                Config incoming = Config.manual(raw, accountInput.getText().toString().trim());
                Base32.decode(incoming.secret);
                if (config == null) save(incoming); else confirmSave(incoming, "Replace");
            } catch (Throwable error) {
                secretInput.setError("Invalid Base32 setup key");
            }
        }

        private void confirmSave(Config incoming, String action) {
            new AlertDialog.Builder(this)
                    .setTitle(action + " HCF authenticator?")
                    .setMessage("Account: " + incoming.displayLabel() + "\n\nThe setup secret will be encrypted with Android Keystore and stored only on this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(action, (dialog, which) -> save(incoming))
                    .show();
        }

        private void save(Config incoming) {
            try {
                Vault.save(this, incoming);
                config = incoming;
                secretInput.setText("");
                renderState();
                renderCode();
                Toast.makeText(this, "HCF Authenticator configured.", Toast.LENGTH_SHORT).show();
            } catch (Throwable error) {
                Toast.makeText(this, "Could not securely save this authenticator.", Toast.LENGTH_LONG).show();
            }
        }

        private void copyCode() {
            if (lastCode.isEmpty()) {
                Toast.makeText(this, "No authentication code is available yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("HCF authentication code", lastCode));
                Toast.makeText(this, "Authentication code copied.", Toast.LENGTH_SHORT).show();
            }
        }

        private void remove() {
            if (config == null) {
                Toast.makeText(this, "No authenticator is configured on this device.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Remove HCF Authenticator?")
                    .setMessage("This deletes the encrypted setup key from this device. It does not disable two-factor authentication on the website.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        Vault.clear(this);
                        config = null;
                        renderState();
                    })
                    .show();
        }

        private void openForum() {
            Intent forum = new Intent(this, HcfForum.MainActivity.class);
            forum.setData(Uri.parse("https://forum.harleytg.com/"));
            forum.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(forum);
        }

        private EditText input(String hint) {
            EditText input = new EditText(this);
            input.setHint(hint);
            input.setHintTextColor(MUTED);
            input.setTextColor(TEXT);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            input.setSingleLine(true);
            input.setPadding(dp(13), 0, dp(13), 0);
            input.setBackground(rounded(PANEL_DARK, BORDER, 12, 1));
            return input;
        }

        private LinearLayout panel() {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(15), dp(14), dp(15), dp(14));
            panel.setBackground(rounded(PANEL, BORDER, 16, 1));
            return panel;
        }

        private LinearLayout.LayoutParams panelLp() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            return lp;
        }

        private TextView section(String value) {
            TextView title = text(value, 10, CYAN);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setPadding(0, 0, 0, dp(8));
            return title;
        }

        private TextView text(String value, float sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextColor(color);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            return view;
        }

        private Button button(String value, boolean primary) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setText(value);
            button.setTextColor(primary ? Color.BLACK : TEXT);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setBackground(rounded(primary ? CYAN : PANEL_DARK, primary ? CYAN : BORDER, 12, 1));
            button.setStateListAnimator(null);
            return button;
        }

        private GradientDrawable rounded(int fill, int stroke, int radius, int strokeWidth) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radius));
            drawable.setStroke(dp(strokeWidth), stroke);
            return drawable;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    static final class Config {
        final String secret;
        final String label;
        final String issuer;
        final String algorithm;
        final int digits;
        final int period;

        Config(String secret, String label, String issuer, String algorithm, int digits, int period) {
            this.secret = normalizeSecret(secret);
            this.label = clean(label);
            this.issuer = clean(issuer);
            this.algorithm = normalizeAlgorithm(algorithm);
            this.digits = digits == 8 ? 8 : 6;
            this.period = period >= 15 && period <= 120 ? period : 30;
        }

        static Config manual(String secret, String label) {
            return new Config(secret, label, "Harley's Clan Forum", "SHA1", 6, 30);
        }

        static Config fromOtpAuth(Uri uri) {
            if (uri == null || !"otpauth".equalsIgnoreCase(uri.getScheme()) || !"totp".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException("Not TOTP");
            }
            String secret = uri.getQueryParameter("secret");
            if (secret == null || secret.trim().isEmpty()) throw new IllegalArgumentException("Missing secret");
            String path = uri.getPath();
            String label = path == null ? "" : Uri.decode(path.startsWith("/") ? path.substring(1) : path);
            Config config = new Config(secret, label, uri.getQueryParameter("issuer"), uri.getQueryParameter("algorithm"),
                    parseInt(uri.getQueryParameter("digits"), 6), parseInt(uri.getQueryParameter("period"), 30));
            Base32.decode(config.secret);
            return config;
        }

        String displayLabel() {
            if (!label.isEmpty()) return label;
            if (!issuer.isEmpty()) return issuer;
            return "Harley's Clan Forum";
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("secret", secret);
            json.put("label", label);
            json.put("issuer", issuer);
            json.put("algorithm", algorithm);
            json.put("digits", digits);
            json.put("period", period);
            return json;
        }

        static Config fromJson(String value) throws Exception {
            JSONObject json = new JSONObject(value);
            return new Config(json.getString("secret"), json.optString("label", ""),
                    json.optString("issuer", "Harley's Clan Forum"), json.optString("algorithm", "SHA1"),
                    json.optInt("digits", 6), json.optInt("period", 30));
        }

        private static String normalizeSecret(String value) {
            return value == null ? "" : value.toUpperCase(Locale.US).replace(" ", "").replace("-", "").replace("=", "").trim();
        }

        private static String normalizeAlgorithm(String value) {
            String normalized = value == null ? "SHA1" : value.toUpperCase(Locale.US).replace("-", "").trim();
            if ("SHA256".equals(normalized)) return "SHA256";
            if ("SHA512".equals(normalized)) return "SHA512";
            return "SHA1";
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
        private static int parseInt(String value, int fallback) {
            try { return Integer.parseInt(value); } catch (Throwable ignored) { return fallback; }
        }
    }

    static final class Totp {
        static String generate(Config config, long unixSeconds) throws Exception {
            byte[] key = Base32.decode(config.secret);
            byte[] counter = ByteBuffer.allocate(8).putLong(unixSeconds / config.period).array();
            String macName = "SHA256".equals(config.algorithm) ? "HmacSHA256" : "SHA512".equals(config.algorithm) ? "HmacSHA512" : "HmacSHA1";
            Mac mac = Mac.getInstance(macName);
            mac.init(new SecretKeySpec(key, macName));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int divisor = config.digits == 8 ? 100000000 : 1000000;
            return String.format(Locale.US, "%0" + config.digits + "d", binary % divisor);
        }
    }

    static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        static byte[] decode(String encoded) {
            String input = encoded == null ? "" : encoded.toUpperCase(Locale.US).replace(" ", "").replace("-", "").replace("=", "");
            if (input.isEmpty()) throw new IllegalArgumentException("Empty secret");
            byte[] output = new byte[(input.length() * 5) / 8 + 1];
            int buffer = 0, bits = 0, count = 0;
            for (int i = 0; i < input.length(); i++) {
                int value = ALPHABET.indexOf(input.charAt(i));
                if (value < 0) throw new IllegalArgumentException("Invalid Base32");
                buffer = (buffer << 5) | value;
                bits += 5;
                if (bits >= 8) {
                    bits -= 8;
                    output[count++] = (byte) ((buffer >> bits) & 0xff);
                }
            }
            if (count == 0) throw new IllegalArgumentException("Secret too short");
            return Arrays.copyOf(output, count);
        }
    }

    static final class Vault {
        private static final String PREFS = "hcf_authenticator_v1";
        private static final String VALUE = "encrypted_totp_config";
        private static final String ALIAS = "hcf_authenticator_master_key_v1";
        private static final byte[] AAD = "HCF-TOTP-V1".getBytes(StandardCharsets.UTF_8);

        static void save(Context context, Config config) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(config.toJson().toString().getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            packed.put((byte) iv.length).put(iv).put(encrypted);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(VALUE, Base64.encodeToString(packed.array(), Base64.NO_WRAP)).apply();
        }

        static Config load(Context context) throws Exception {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saved = prefs.getString(VALUE, "");
            if (saved == null || saved.isEmpty()) return null;
            ByteBuffer packed = ByteBuffer.wrap(Base64.decode(saved, Base64.NO_WRAP));
            int ivLength = packed.get() & 0xff;
            if (ivLength < 12 || ivLength > 32 || packed.remaining() <= ivLength) throw new IllegalStateException("Bad vault record");
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] encrypted = new byte[packed.remaining()];
            packed.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return Config.fromJson(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        }

        static void clear(Context context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        }

        private static SecretKey key() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            java.security.Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        }
    }

    private static String formatCode(String raw) {
        if (raw == null) return "--- ---";
        if (raw.length() == 6) return raw.substring(0, 3) + " " + raw.substring(3);
        if (raw.length() == 8) return raw.substring(0, 4) + " " + raw.substring(4);
        return raw;
    }
}
