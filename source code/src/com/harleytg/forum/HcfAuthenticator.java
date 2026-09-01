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
import android.view.View;
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

/**
 * HCF Authenticator
 *
 * Native RFC 6238 TOTP authenticator for Harley's Clan Forum. The enrolled
 * secret is encrypted with an Android Keystore AES key and never needs to be
 * sent to Harley's services after enrollment. Codes are generated locally
 * from the secret + the device clock and work without network access.
 *
 * Enrollment inputs:
 *  - otpauth://totp/... links opened from the forum
 *  - manual Base32 setup keys copied from the forum
 */
public final class HcfAuthenticator {
    private HcfAuthenticator() {}

    public static final String OPEN_URI = "hcf-auth://open";

    private static final int BG = Color.rgb(13, 16, 20);
    private static final int PANEL = Color.rgb(17, 27, 34);
    private static final int PANEL_2 = Color.rgb(12, 21, 27);
    private static final int BORDER = Color.rgb(41, 64, 75);
    private static final int CYAN = Color.rgb(0, 184, 240);
    private static final int TEXT = Color.rgb(239, 247, 250);
    private static final int MUTED = Color.rgb(155, 174, 183);
    private static final int GOOD = Color.rgb(85, 225, 59);
    private static final int DANGER = Color.rgb(255, 77, 87);

    public static final class Activity extends android.app.Activity {
        private final Handler ticker = new Handler(Looper.getMainLooper());
        private final Runnable tickTask = new Runnable() {
            @Override public void run() {
                renderCode();
                ticker.postDelayed(this, 250L);
            }
        };

        private LinearLayout content;
        private TextView statusValue;
        private TextView accountValue;
        private TextView codeValue;
        private TextView countdownValue;
        private ProgressBar countdownProgress;
        private EditText setupKeyInput;
        private EditText accountInput;
        private Config config;
        private String lastRawCode = "";

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            buildUi();
            reloadConfig();
            handleIntent(getIntent());
        }

        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            handleIntent(intent);
        }

        @Override
        protected void onResume() {
            super.onResume();
            ticker.removeCallbacks(tickTask);
            ticker.post(tickTask);
        }

        @Override
        protected void onPause() {
            ticker.removeCallbacks(tickTask);
            super.onPause();
        }

        private void buildUi() {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(BG);

            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(18), dp(12), dp(18), dp(28));
            scroll.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(0, dp(4), 0, dp(14));

            Button back = button("‹", false);
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            back.setContentDescription("Back");
            back.setOnClickListener(v -> finish());
            header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

            LinearLayout titles = new LinearLayout(this);
            titles.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("HCF Authenticator", 21, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            titles.addView(title);
            titles.addView(text("Two-factor authentication • Offline code generator", 10, MUTED));
            LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, -2, 1f);
            titlesLp.leftMargin = dp(10);
            header.addView(titles, titlesLp);
            content.addView(header);

            LinearLayout privacy = panel();
            privacy.addView(sectionTitle("LOCAL & ENCRYPTED"));
            privacy.addView(text("Authentication codes are generated on this device. A network connection is not required after setup.", 11, MUTED));
            TextView secureNote = text("Screenshots and screen recording are blocked on this screen.", 10, CYAN);
            secureNote.setPadding(0, dp(7), 0, 0);
            privacy.addView(secureNote);
            content.addView(privacy, panelParams());

            LinearLayout status = panel();
            status.addView(sectionTitle("AUTHENTICATOR STATUS"));
            statusValue = text("Not configured", 16, DANGER);
            statusValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            status.addView(statusValue);
            accountValue = text("No forum account enrolled", 11, MUTED);
            accountValue.setPadding(0, dp(4), 0, 0);
            status.addView(accountValue);
            content.addView(status, panelParams());

            LinearLayout codePanel = panel();
            codePanel.setTag("code_panel");
            codePanel.addView(sectionTitle("CURRENT ACCESS CODE"));
            codeValue = text("--- ---", 38, TEXT);
            codeValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            codeValue.setGravity(Gravity.CENTER);
            codeValue.setLetterSpacing(0.08f);
            codeValue.setPadding(0, dp(8), 0, dp(4));
            codePanel.addView(codeValue, new LinearLayout.LayoutParams(-1, -2));
            countdownValue = text("Configure the authenticator to generate a code.", 11, MUTED);
            countdownValue.setGravity(Gravity.CENTER);
            codePanel.addView(countdownValue, new LinearLayout.LayoutParams(-1, -2));
            countdownProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            countdownProgress.setMax(30);
            countdownProgress.setProgress(0);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(5));
            progressLp.topMargin = dp(10);
            codePanel.addView(countdownProgress, progressLp);

            Button copy = button("Copy current code", true);
            copy.setOnClickListener(v -> copyCode());
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-1, dp(48));
            copyLp.topMargin = dp(12);
            codePanel.addView(copy, copyLp);
            content.addView(codePanel, panelParams());

            LinearLayout setup = panel();
            setup.addView(sectionTitle("SET UP FROM THE FORUM"));
            setup.addView(text("Open Two-Factor Authentication on Harley's Clan Forum and copy the setup key, or tap an HCF/otpauth setup link when one is offered.", 11, MUTED));

            accountInput = input("Forum account name (optional)");
            accountInput.setInputType(InputType.TYPE_CLASS_TEXT);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, dp(50));
            fieldLp.topMargin = dp(12);
            setup.addView(accountInput, fieldLp);

            setupKeyInput = input("Base32 setup key");
            setupKeyInput.setSingleLine(true);
            setupKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, dp(50));
            keyLp.topMargin = dp(8);
            setup.addView(setupKeyInput, keyLp);

            Button save = button("Save setup key", true);
            save.setOnClickListener(v -> saveManual());
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
            saveLp.topMargin = dp(10);
            setup.addView(save, saveLp);
            content.addView(setup, panelParams());

            LinearLayout manage = panel();
            manage.addView(sectionTitle("MANAGE"));
            Button openForum = button("Open Harley's Clan Forum", false);
            openForum.setOnClickListener(v -> openForum());
            manage.addView(openForum, new LinearLayout.LayoutParams(-1, dp(48)));
            Button remove = button("Remove authenticator from this device", false);
            remove.setTextColor(DANGER);
            remove.setOnClickListener(v -> confirmRemove());
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(-1, dp(48));
            removeLp.topMargin = dp(8);
            manage.addView(remove, removeLp);
            TextView warning = text("Removing the key here does not disable two-factor authentication on the website. Keep your forum recovery codes in a safe place.", 10, MUTED);
            warning.setPadding(0, dp(10), 0, 0);
            manage.addView(warning);
            content.addView(manage, panelParams());

            TextView footer = text("RFC 6238 TOTP • Secret protected by Android Keystore • No cloud sync", 9, MUTED);
            footer.setGravity(Gravity.CENTER);
            footer.setPadding(dp(8), dp(6), dp(8), 0);
            content.addView(footer);

            setContentView(scroll);
        }

        private void reloadConfig() {
            try {
                config = Vault.load(this);
            } catch (Throwable error) {
                config = null;
                Vault.clear(this);
                Toast.makeText(this, "Authenticator storage could not be unlocked. Set it up again.", Toast.LENGTH_LONG).show();
            }
            renderState();
        }

        private void renderState() {
            boolean ready = config != null && config.secret != null && !config.secret.isEmpty();
            statusValue.setText(ready ? "Configured" : "Not configured");
            statusValue.setTextColor(ready ? GOOD : DANGER);
            if (ready) {
                String label = config.displayLabel();
                accountValue.setText(label + " • " + config.digits + " digits • " + config.period + " sec");
                if (accountInput != null && accountInput.getText().toString().trim().isEmpty()) {
                    accountInput.setText(config.label);
                }
            } else {
                accountValue.setText("No forum account enrolled");
                codeValue.setText("--- ---");
                countdownValue.setText("Configure the authenticator to generate a code.");
                countdownProgress.setMax(30);
                countdownProgress.setProgress(0);
                lastRawCode = "";
            }
        }

        private void renderCode() {
            if (config == null) return;
            try {
                long nowSeconds = System.currentTimeMillis() / 1000L;
                String raw = Totp.generate(config, nowSeconds);
                lastRawCode = raw;
                codeValue.setText(formatCode(raw));
                int elapsed = (int) (nowSeconds % config.period);
                int remaining = config.period - elapsed;
                countdownProgress.setMax(config.period);
                countdownProgress.setProgress(elapsed);
                countdownValue.setText("New code in " + remaining + " second" + (remaining == 1 ? "" : "s") + " • works offline");
            } catch (Throwable error) {
                lastRawCode = "";
                codeValue.setText("--- ---");
                countdownValue.setText("Unable to generate code. Check the setup key and device time.");
            }
        }

        private void handleIntent(Intent intent) {
            if (intent == null || intent.getData() == null) return;
            Uri uri = intent.getData();
            if (!"otpauth".equalsIgnoreCase(uri.getScheme())) return;
            try {
                final Config incoming = Config.fromOtpAuth(uri);
                String action = config == null ? "Add" : "Replace";
                new AlertDialog.Builder(this)
                        .setTitle(action + " HCF authenticator?")
                        .setMessage("Account: " + incoming.displayLabel() + "\n\nThe setup secret will be encrypted with Android Keystore and stored only on this device.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(action, (dialog, which) -> {
                            try {
                                Vault.save(this, incoming);
                                config = incoming;
                                renderState();
                                renderCode();
                                Toast.makeText(this, "HCF Authenticator configured.", Toast.LENGTH_SHORT).show();
                            } catch (Throwable error) {
                                Toast.makeText(this, "Could not securely save this authenticator.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .show();
            } catch (Throwable error) {
                Toast.makeText(this, "This authenticator setup link is invalid or unsupported.", Toast.LENGTH_LONG).show();
            }
        }

        private void saveManual() {
            String secret = setupKeyInput.getText().toString().trim();
            String label = accountInput.getText().toString().trim();
            if (secret.isEmpty()) {
                setupKeyInput.setError("Enter the setup key from the forum");
                return;
            }
            try {
                Config incoming = Config.manual(secret, label);
                // Decode once before saving so malformed Base32 never reaches the vault.
                Base32.decode(incoming.secret);
                Runnable save = () -> {
                    try {
                        Vault.save(this, incoming);
                        config = incoming;
                        setupKeyInput.setText("");
                        renderState();
                        renderCode();
                        Toast.makeText(this, "HCF Authenticator configured.", Toast.LENGTH_SHORT).show();
                    } catch (Throwable error) {
                        Toast.makeText(this, "Could not securely save this setup key.", Toast.LENGTH_LONG).show();
                    }
                };
                if (config == null) {
                    save.run();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Replace authenticator?")
                            .setMessage("This replaces the setup key currently stored on this device. It does not change the website until you configure the forum with the same key.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Replace", (dialog, which) -> save.run())
                            .show();
                }
            } catch (Throwable error) {
                setupKeyInput.setError("Invalid Base32 setup key");
            }
        }

        private void copyCode() {
            if (lastRawCode == null || lastRawCode.isEmpty()) {
                Toast.makeText(this, "No authentication code is available yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("HCF authentication code", lastRawCode));
                Toast.makeText(this, "Authentication code copied.", Toast.LENGTH_SHORT).show();
            }
        }

        private void confirmRemove() {
            if (config == null) {
                Toast.makeText(this, "No authenticator is configured on this device.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Remove HCF Authenticator?")
                    .setMessage("This deletes the encrypted setup key from this device. It does NOT disable two-factor authentication on the website. Make sure you can still sign in or have recovery codes first.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        Vault.clear(this);
                        config = null;
                        renderState();
                        Toast.makeText(this, "Authenticator removed from this device.", Toast.LENGTH_SHORT).show();
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
            input.setBackground(rounded(PANEL_2, BORDER, 12, 1));
            return input;
        }

        private LinearLayout panel() {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(15), dp(14), dp(15), dp(14));
            panel.setBackground(rounded(PANEL, BORDER, 16, 1));
            return panel;
        }

        private LinearLayout.LayoutParams panelParams() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            return lp;
        }

        private TextView sectionTitle(String value) {
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
            button.setBackground(rounded(primary ? CYAN : PANEL_2, primary ? CYAN : BORDER, 12, 1));
            button.setStateListAnimator(null);
            return button;
        }

        private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radiusDp));
            if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
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
                throw new IllegalArgumentException("Not a TOTP URI");
            }
            String secret = uri.getQueryParameter("secret");
            if (secret == null || secret.trim().isEmpty()) throw new IllegalArgumentException("Missing secret");
            String path = uri.getPath();
            String label = path == null ? "" : Uri.decode(path.startsWith("/") ? path.substring(1) : path);
            String issuer = uri.getQueryParameter("issuer");
            String algorithm = uri.getQueryParameter("algorithm");
            int digits = parseInt(uri.getQueryParameter("digits"), 6);
            int period = parseInt(uri.getQueryParameter("period"), 30);
            Config config = new Config(secret, label, issuer, algorithm, digits, period);
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
            return new Config(
                    json.getString("secret"),
                    json.optString("label", ""),
                    json.optString("issuer", "Harley's Clan Forum"),
                    json.optString("algorithm", "SHA1"),
                    json.optInt("digits", 6),
                    json.optInt("period", 30));
        }

        private static String normalizeSecret(String value) {
            if (value == null) return "";
            return value.toUpperCase(Locale.US).replace(" ", "").replace("-", "").replace("=", "").trim();
        }

        private static String normalizeAlgorithm(String value) {
            String normalized = value == null ? "SHA1" : value.toUpperCase(Locale.US).replace("-", "").trim();
            if ("SHA256".equals(normalized)) return "SHA256";
            if ("SHA512".equals(normalized)) return "SHA512";
            return "SHA1";
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }

        private static int parseInt(String value, int fallback) {
            try { return Integer.parseInt(value); } catch (Throwable ignored) { return fallback; }
        }
    }

    static final class Totp {
        private Totp() {}

        static String generate(Config config, long unixSeconds) throws Exception {
            byte[] key = Base32.decode(config.secret);
            long counter = unixSeconds / config.period;
            byte[] message = ByteBuffer.allocate(8).putLong(counter).array();
            String macName = "SHA256".equals(config.algorithm) ? "HmacSHA256"
                    : "SHA512".equals(config.algorithm) ? "HmacSHA512" : "HmacSHA1";
            Mac mac = Mac.getInstance(macName);
            mac.init(new SecretKeySpec(key, macName));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int divisor = config.digits == 8 ? 100000000 : 1000000;
            int otp = binary % divisor;
            return String.format(Locale.US, "%0" + config.digits + "d", otp);
        }
    }

    static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        private Base32() {}

        static byte[] decode(String encoded) {
            String input = encoded == null ? "" : encoded.toUpperCase(Locale.US)
                    .replace(" ", "").replace("-", "").replace("=", "");
            if (input.isEmpty()) throw new IllegalArgumentException("Empty Base32 secret");
            byte[] output = new byte[(input.length() * 5) / 8 + 1];
            int buffer = 0;
            int bitsLeft = 0;
            int count = 0;
            for (int i = 0; i < input.length(); i++) {
                int value = ALPHABET.indexOf(input.charAt(i));
                if (value < 0) throw new IllegalArgumentException("Invalid Base32 character");
                buffer = (buffer << 5) | value;
                bitsLeft += 5;
                if (bitsLeft >= 8) {
                    bitsLeft -= 8;
                    output[count++] = (byte) ((buffer >> bitsLeft) & 0xff);
                }
            }
            if (count == 0) throw new IllegalArgumentException("Base32 secret too short");
            return Arrays.copyOf(output, count);
        }
    }

    static final class Vault {
        private static final String PREFS = "hcf_authenticator_v1";
        private static final String VALUE = "encrypted_totp_config";
        private static final String KEY_ALIAS = "hcf_authenticator_master_key_v1";
        private static final byte[] AAD = "HCF-TOTP-V1".getBytes(StandardCharsets.UTF_8);

        private Vault() {}

        static void save(Context context, Config config) throws Exception {
            SecretKey key = key();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(AAD);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(config.toJson().toString().getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            packed.put((byte) iv.length);
            packed.put(iv);
            packed.put(encrypted);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(VALUE, Base64.encodeToString(packed.array(), Base64.NO_WRAP))
                    .apply();
        }

        static Config load(Context context) throws Exception {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saved = prefs.getString(VALUE, "");
            if (saved == null || saved.isEmpty()) return null;
            byte[] packed = Base64.decode(saved, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            int ivLength = buffer.get() & 0xff;
            if (ivLength < 12 || ivLength > 32 || buffer.remaining() <= ivLength) throw new IllegalStateException("Invalid vault record");
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(encrypted);
            return Config.fromJson(new String(plain, StandardCharsets.UTF_8));
        }

        static void clear(Context context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        }

        private static SecretKey key() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            java.security.Key existing = store.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;

            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
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
