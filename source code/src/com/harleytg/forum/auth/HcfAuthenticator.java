package com.harleytg.forum.dev;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
        private static final int REQUEST_QR_SCANNER = 8240;

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

        @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_QR_SCANNER && resultCode == RESULT_OK && data != null) {
                String raw = data.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE);
                if ((raw == null || raw.trim().isEmpty()) && data.getData() != null) raw = data.getData().toString();
                importOtpAuth(raw, "QR code");
            }
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
            setup.addView(text("Scan the forum's Two-Factor Authentication QR code, import a QR screenshot, paste an otpauth:// setup link, or enter the Base32 setup key manually.", 11, MUTED));

            Button scan = button("Scan or import QR code", true);
            scan.setOnClickListener(v -> startActivityForResult(new Intent(this, QrScannerActivity.class), REQUEST_QR_SCANNER));
            LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(-1, dp(50));
            scanLp.topMargin = dp(12);
            setup.addView(scan, scanLp);

            Button paste = button("Paste authenticator setup link", false);
            paste.setOnClickListener(v -> pasteSetupLink());
            LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(-1, dp(48));
            pasteLp.topMargin = dp(8);
            setup.addView(paste, pasteLp);

            TextView manualLabel = text("Manual setup key", 10, CYAN);
            manualLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            manualLabel.setPadding(0, dp(14), 0, 0);
            setup.addView(manualLabel);

            accountInput = input("Forum account name (optional)");
            accountInput.setInputType(InputType.TYPE_CLASS_TEXT);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, dp(50));
            fieldLp.topMargin = dp(9);
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

            TextView footer = text("RFC 6238 TOTP • Android Keystore • QR setup stays on-device • No cloud sync", 9, MUTED);
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
            importOtpAuth(uri.toString(), "setup link");
        }

        private void pasteSetupLink() {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            CharSequence value = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(this);
            if (value == null || value.toString().trim().isEmpty()) {
                Toast.makeText(this, "Clipboard does not contain an authenticator setup link.", Toast.LENGTH_LONG).show();
                return;
            }
            importOtpAuth(value.toString(), "clipboard");
        }

        private void importOtpAuth(String raw, String source) {
            if (raw == null || raw.trim().isEmpty()) return;
            try {
                Uri uri = Uri.parse(raw.trim());
                Config incoming = Config.fromOtpAuth(uri);
                confirmSave(incoming, config == null ? "Add" : "Replace", source);
            } catch (Throwable error) {
                Toast.makeText(this, "The " + source + " is not a supported TOTP authenticator setup.", Toast.LENGTH_LONG).show();
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
                if (config == null) save(incoming); else confirmSave(incoming, "Replace", "manual setup key");
            } catch (Throwable error) {
                secretInput.setError("Invalid Base32 setup key");
            }
        }

        private void confirmSave(Config incoming, String action, String source) {
            new AlertDialog.Builder(this)
                    .setTitle(action + " HCF authenticator?")
                    .setMessage("Account: " + incoming.displayLabel()
                            + "\nSource: " + source
                            + "\n\nThe setup secret will be encrypted with Android Keystore and stored only on this device.")
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

    /**
     * Local QR scanner. The HTML is embedded in the APK, blocks network loads, and uses
     * the Android WebView platform BarcodeDetector. QR contents are returned to Activity
     * and never uploaded to HCF or another service.
     */
    public static final class QrScannerActivity extends android.app.Activity {
        public static final String EXTRA_QR_VALUE = "hcf_auth_qr_value";

        private static final int REQUEST_CAMERA = 8241;
        private static final int REQUEST_IMAGE = 8242;

        private WebView scanner;
        private ValueCallback<Uri[]> pendingFiles;
        private TextView nativeStatus;
        private boolean cameraAllowed;

        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            buildUi();

            cameraAllowed = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            if (cameraAllowed) {
                loadScanner(true);
            } else {
                nativeStatus.setText("Camera permission is needed for live scanning. You can still import a QR image.");
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
        }

        @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode != REQUEST_CAMERA) return;
            cameraAllowed = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            loadScanner(cameraAllowed);
        }

        @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_IMAGE) {
                ValueCallback<Uri[]> callback = pendingFiles;
                pendingFiles = null;
                if (callback != null) callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            }
        }

        @Override protected void onDestroy() {
            if (pendingFiles != null) {
                pendingFiles.onReceiveValue(null);
                pendingFiles = null;
            }
            if (scanner != null) {
                try {
                    scanner.loadUrl("about:blank");
                    scanner.removeJavascriptInterface("HcfQrBridge");
                    scanner.stopLoading();
                    scanner.destroy();
                } catch (Throwable ignored) {}
                scanner = null;
            }
            super.onDestroy();
        }

        private void buildUi() {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(10), dp(7), dp(10), dp(7));
            Button close = button("‹", false);
            close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            close.setOnClickListener(v -> finish());
            header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(44)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("Scan authenticator QR", 18, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            labels.addView(title);
            labels.addView(text("Live camera or QR screenshot • processed on this device", 9, MUTED));
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            labelsLp.leftMargin = dp(8);
            header.addView(labels, labelsLp);
            root.addView(header, new LinearLayout.LayoutParams(-1, -2));

            nativeStatus = text("Preparing secure QR scanner…", 10, MUTED);
            nativeStatus.setGravity(Gravity.CENTER);
            nativeStatus.setPadding(dp(12), dp(5), dp(12), dp(7));
            root.addView(nativeStatus, new LinearLayout.LayoutParams(-1, -2));

            scanner = new WebView(this);
            scanner.setBackgroundColor(BG);
            configureScannerWebView();
            root.addView(scanner, new LinearLayout.LayoutParams(-1, 0, 1f));

            setContentView(root);
        }

        @SuppressWarnings("SetJavaScriptEnabled")
        private void configureScannerWebView() {
            WebSettings settings = scanner.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setDatabaseEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setBlockNetworkLoads(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSafeBrowsingEnabled(true);

            scanner.addJavascriptInterface(new QrBridge(), "HcfQrBridge");
            scanner.setWebViewClient(new WebViewClient());
            scanner.setWebChromeClient(new WebChromeClient() {
                @Override public void onPermissionRequest(PermissionRequest request) {
                    runOnUiThread(() -> {
                        if (request == null) return;
                        boolean localOrigin = request.getOrigin() != null
                                && "https".equalsIgnoreCase(request.getOrigin().getScheme())
                                && "hcf-auth.local".equalsIgnoreCase(request.getOrigin().getHost());
                        boolean video = false;
                        for (String resource : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                                video = true;
                                break;
                            }
                        }
                        if (localOrigin && video
                                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                        } else {
                            request.deny();
                        }
                    });
                }

                @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                                           FileChooserParams fileChooserParams) {
                    if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                    pendingFiles = filePathCallback;
                    Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    choose.addCategory(Intent.CATEGORY_OPENABLE);
                    choose.setType("image/*");
                    try {
                        startActivityForResult(choose, REQUEST_IMAGE);
                    } catch (Throwable error) {
                        pendingFiles = null;
                        filePathCallback.onReceiveValue(null);
                        Toast.makeText(QrScannerActivity.this, "No image picker is available.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
            });
        }

        private void loadScanner(boolean allowCamera) {
            nativeStatus.setText(allowCamera
                    ? "Point the rear camera at the forum QR code."
                    : "Live camera is off. Use Import QR image, or grant camera access and retry.");
            scanner.loadDataWithBaseURL(
                    "https://hcf-auth.local/scanner/",
                    scannerHtml(allowCamera),
                    "text/html",
                    "UTF-8",
                    null);
        }

        private void requestCameraFromPage() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraAllowed = true;
                    loadScanner(true);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                }
            });
        }

        private final class QrBridge {
            @JavascriptInterface public void onResult(String value) {
                runOnUiThread(() -> {
                    if (value == null || !value.trim().toLowerCase(Locale.US).startsWith("otpauth://totp/")) {
                        Toast.makeText(QrScannerActivity.this, "That QR code is not a TOTP authenticator setup.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Intent result = new Intent();
                    result.putExtra(EXTRA_QR_VALUE, value.trim());
                    result.setData(Uri.parse(value.trim()));
                    setResult(RESULT_OK, result);
                    finish();
                });
            }

            @JavascriptInterface public void requestCamera() {
                requestCameraFromPage();
            }

            @JavascriptInterface public void scannerStatus(String message) {
                runOnUiThread(() -> {
                    if (message != null && !message.trim().isEmpty()) nativeStatus.setText(message.trim());
                });
            }
        }

        private String scannerHtml(boolean allowCamera) {
            String cameraFlag = allowCamera ? "true" : "false";
            return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                    + "<style>"
                    + "*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;background:#0d1014;color:#eff7fa;font-family:system-ui,-apple-system,sans-serif}"
                    + "body{display:flex;flex-direction:column;overflow:hidden}.stage{position:relative;flex:1;min-height:260px;background:#080b0e;overflow:hidden}"
                    + "video{width:100%;height:100%;object-fit:cover;background:#080b0e}.shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.22),transparent 28%,transparent 72%,rgba(0,0,0,.30));pointer-events:none}"
                    + ".frame{position:absolute;left:50%;top:50%;width:min(72vw,320px);height:min(72vw,320px);transform:translate(-50%,-50%);border:2px solid #00b8f0;border-radius:22px;box-shadow:0 0 0 9999px rgba(0,0,0,.26),0 0 22px rgba(0,184,240,.35);pointer-events:none}"
                    + ".frame:before,.frame:after{content:'';position:absolute;inset:16px;border-top:2px solid rgba(255,255,255,.22);border-bottom:2px solid rgba(255,255,255,.22)}"
                    + ".panel{padding:14px 14px calc(14px + env(safe-area-inset-bottom));background:#111b22;border-top:1px solid #29404b}"
                    + "#status{font-size:12px;color:#9baeb7;text-align:center;min-height:34px;line-height:1.35}.ok{color:#55e13b!important}.bad{color:#ff4d57!important}"
                    + ".actions{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:8px}button,.pick{height:48px;border-radius:12px;border:1px solid #29404b;background:#0c151b;color:#eff7fa;font-weight:700;font-size:13px;display:flex;align-items:center;justify-content:center;text-align:center;padding:0 10px}"
                    + ".primary{background:#00b8f0;border-color:#00b8f0;color:#061014}.pick input{display:none}.hint{margin-top:10px;font-size:10px;line-height:1.4;color:#78909a;text-align:center}"
                    + "</style></head><body>"
                    + "<div class='stage'><video id='video' autoplay playsinline muted></video><div class='shade'></div><div class='frame'></div></div>"
                    + "<div class='panel'><div id='status'>Preparing QR detector…</div><div class='actions'>"
                    + "<button id='camera' class='primary'>Retry camera</button>"
                    + "<label class='pick'>Import QR image<input id='file' type='file' accept='image/*'></label>"
                    + "</div><div class='hint'>Only TOTP authenticator QR codes are accepted. QR data is processed locally and is not uploaded.</div></div>"
                    + "<script>"
                    + "const cameraAllowed=" + cameraFlag + ";const statusEl=document.getElementById('status');const video=document.getElementById('video');"
                    + "let detector=null,stream=null,stopped=false,busy=false,lastMessage='';"
                    + "function status(m,c){statusEl.textContent=m;statusEl.className=c||'';try{HcfQrBridge.scannerStatus(m)}catch(e){}}"
                    + "async function getDetector(){if(!('BarcodeDetector' in window))throw new Error('Platform QR detector unavailable');"
                    + "const formats=BarcodeDetector.getSupportedFormats?await BarcodeDetector.getSupportedFormats():['qr_code'];"
                    + "if(formats.indexOf('qr_code')<0)throw new Error('QR detection is unavailable on this device');"
                    + "return new BarcodeDetector({formats:['qr_code']});}"
                    + "function stopCamera(){if(stream){stream.getTracks().forEach(t=>t.stop());stream=null;}video.srcObject=null;}"
                    + "function consume(v){const raw=(v||'').trim();if(/^otpauth:\\/\\/totp\\//i.test(raw)){stopped=true;stopCamera();status('Authenticator QR detected.','ok');HcfQrBridge.onResult(raw);return true;}"
                    + "const now=Date.now();if(now-lastMessage>1300){lastMessage=now;status('QR detected, but it is not a TOTP authenticator setup.','bad');}return false;}"
                    + "async function detectSource(source){if(!detector)detector=await getDetector();const codes=await detector.detect(source);"
                    + "if(codes&&codes.length){for(const c of codes){if(consume(c.rawValue))return true;}}return false;}"
                    + "async function loop(){if(stopped||!stream)return;if(!busy&&video.readyState>=2){busy=true;try{await detectSource(video);}catch(e){}finally{busy=false;}}setTimeout(loop,180);}"
                    + "async function startCamera(){stopped=false;stopCamera();try{if(!cameraAllowed){status('Camera permission is not enabled. Tap Retry camera to request it, or import a QR image.','bad');return;}"
                    + "if(!detector)detector=await getDetector();stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}}});"
                    + "video.srcObject=stream;await video.play();status('Point the camera at the forum authenticator QR code.','');loop();}"
                    + "catch(e){status('Live QR scanning is unavailable. You can still import a QR screenshot.','bad');}}"
                    + "document.getElementById('camera').onclick=()=>{if(!cameraAllowed){try{HcfQrBridge.requestCamera()}catch(e){}return;}startCamera();};"
                    + "document.getElementById('file').addEventListener('change',async e=>{const f=e.target.files&&e.target.files[0];if(!f)return;"
                    + "try{status('Reading QR image…','');const bmp=await createImageBitmap(f);const found=await detectSource(bmp);bmp.close&&bmp.close();if(!found)status('No TOTP authenticator QR code was found in that image.','bad');}"
                    + "catch(err){status('This device could not read that QR image. Use the manual setup key instead.','bad');}e.target.value='';});"
                    + "window.addEventListener('pagehide',stopCamera);window.addEventListener('beforeunload',stopCamera);"
                    + "(async()=>{try{detector=await getDetector();if(cameraAllowed)startCamera();else status('Camera permission is off. Import a QR image or tap Retry camera.','bad');}"
                    + "catch(e){status('Platform QR detection is unavailable. Use the manual Base32 setup key.','bad');}})();"
                    + "</script></body></html>";
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
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(primary ? CYAN : PANEL_DARK);
            drawable.setCornerRadius(dp(12));
            drawable.setStroke(dp(1), primary ? CYAN : BORDER);
            button.setBackground(drawable);
            button.setStateListAnimator(null);
            return button;
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
