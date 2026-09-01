package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Camera / image QR importer used by the embedded HCF Authenticator in App Settings.
 * A successful Nearata/otpauth TOTP QR is saved directly into HcfAuthenticator.Vault.
 */
public final class HcfAuthenticatorSettingsQrActivity extends Activity {
    private static final int REQUEST_CAMERA = 8341;
    private static final int REQUEST_IMAGE = 8342;
    private static final int BG = Color.rgb(13, 16, 20);
    private static final int PANEL = Color.rgb(17, 27, 34);
    private static final int BORDER = Color.rgb(41, 64, 75);
    private static final int CYAN = Color.rgb(0, 184, 240);
    private static final int TEXT = Color.rgb(239, 247, 250);
    private static final int MUTED = Color.rgb(155, 174, 183);

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
            if (callback != null) {
                callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            }
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
                scanner.removeJavascriptInterface("HcfSettingsQrBridge");
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
        TextView title = text("Scan Nearata 2FA QR", 18, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        labels.addView(text("Saves directly to HCF Authenticator • processed on this device", 9, MUTED));
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

        scanner.addJavascriptInterface(new QrBridge(), "HcfSettingsQrBridge");
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

            @Override public boolean onShowFileChooser(WebView webView,
                                                       ValueCallback<Uri[]> filePathCallback,
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
                    Toast.makeText(HcfAuthenticatorSettingsQrActivity.this,
                            "No image picker is available.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
    }

    private void loadScanner(boolean allowCamera) {
        nativeStatus.setText(allowCamera
                ? "Point the rear camera at the Nearata QR code."
                : "Live camera is off. Import a QR image or grant camera access and retry.");
        scanner.loadDataWithBaseURL(
                "https://hcf-auth.local/settings-scanner/",
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

    private void saveQr(String value) {
        if (value == null || !value.trim().toLowerCase(Locale.US).startsWith("otpauth://totp/")) {
            Toast.makeText(this, "That QR code is not a TOTP authenticator setup.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Config.fromOtpAuth(Uri.parse(value.trim()));
            HcfAuthenticator.Vault.save(this, config);
            Toast.makeText(this, "Nearata QR saved to HCF Authenticator.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Throwable error) {
            Toast.makeText(this, "Could not save this authenticator QR code.", Toast.LENGTH_LONG).show();
        }
    }

    private final class QrBridge {
        @JavascriptInterface public void onResult(String value) {
            runOnUiThread(() -> saveQr(value));
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
                + ".panel{padding:14px 14px calc(14px + env(safe-area-inset-bottom));background:#111b22;border-top:1px solid #29404b}"
                + "#status{font-size:12px;color:#9baeb7;text-align:center;min-height:34px;line-height:1.35}.ok{color:#55e13b!important}.bad{color:#ff4d57!important}"
                + ".actions{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:8px}button,.pick{height:48px;border-radius:12px;border:1px solid #29404b;background:#0c151b;color:#eff7fa;font-weight:700;font-size:13px;display:flex;align-items:center;justify-content:center;text-align:center;padding:0 10px}"
                + ".primary{background:#00b8f0;border-color:#00b8f0;color:#061014}.pick input{display:none}.hint{margin-top:10px;font-size:10px;line-height:1.4;color:#78909a;text-align:center}"
                + "</style></head><body>"
                + "<div class='stage'><video id='video' autoplay playsinline muted></video><div class='shade'></div><div class='frame'></div></div>"
                + "<div class='panel'><div id='status'>Preparing QR detector…</div><div class='actions'>"
                + "<button id='camera' class='primary'>Retry camera</button>"
                + "<label class='pick'>Import QR image<input id='file' type='file' accept='image/*'></label>"
                + "</div><div class='hint'>Only TOTP authenticator QR codes are accepted. QR data stays on this device.</div></div>"
                + "<script>"
                + "const cameraAllowed=" + cameraFlag + ";const statusEl=document.getElementById('status');const video=document.getElementById('video');"
                + "let detector=null,stream=null,stopped=false,busy=false,lastMessage=0;"
                + "function status(m,c){statusEl.textContent=m;statusEl.className=c||'';try{HcfSettingsQrBridge.scannerStatus(m)}catch(e){}}"
                + "async function getDetector(){if(!('BarcodeDetector' in window))throw new Error('Platform QR detector unavailable');const formats=BarcodeDetector.getSupportedFormats?await BarcodeDetector.getSupportedFormats():['qr_code'];if(formats.indexOf('qr_code')<0)throw new Error('QR detection unavailable');return new BarcodeDetector({formats:['qr_code']});}"
                + "function stopCamera(){if(stream){stream.getTracks().forEach(t=>t.stop());stream=null;}video.srcObject=null;}"
                + "function consume(v){const raw=(v||'').trim();if(/^otpauth:\\/\\/totp\\//i.test(raw)){stopped=true;stopCamera();status('Nearata authenticator QR detected.','ok');HcfSettingsQrBridge.onResult(raw);return true;}const now=Date.now();if(now-lastMessage>1300){lastMessage=now;status('QR detected, but it is not a TOTP authenticator setup.','bad');}return false;}"
                + "async function detectSource(source){if(!detector)detector=await getDetector();const codes=await detector.detect(source);if(codes&&codes.length){for(const c of codes){if(consume(c.rawValue))return true;}}return false;}"
                + "async function loop(){if(stopped||!stream)return;if(!busy&&video.readyState>=2){busy=true;try{await detectSource(video);}catch(e){}finally{busy=false;}}setTimeout(loop,180);}"
                + "async function startCamera(){stopped=false;stopCamera();try{if(!cameraAllowed){status('Camera permission is off. Tap Retry camera or import a QR image.','bad');return;}if(!detector)detector=await getDetector();stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}}});video.srcObject=stream;await video.play();status('Point the camera at the Nearata 2FA QR code.','');loop();}catch(e){status('Live QR scanning is unavailable. Import a QR screenshot instead.','bad');}}"
                + "document.getElementById('camera').onclick=()=>{if(!cameraAllowed){try{HcfSettingsQrBridge.requestCamera()}catch(e){}return;}startCamera();};"
                + "document.getElementById('file').addEventListener('change',async e=>{const f=e.target.files&&e.target.files[0];if(!f)return;try{status('Reading QR image…','');const bmp=await createImageBitmap(f);const found=await detectSource(bmp);bmp.close&&bmp.close();if(!found)status('No TOTP authenticator QR code was found in that image.','bad');}catch(err){status('This device could not read that QR image. Use the Setup key instead.','bad');}e.target.value='';});"
                + "window.addEventListener('pagehide',stopCamera);window.addEventListener('beforeunload',stopCamera);"
                + "(async()=>{try{detector=await getDetector();if(cameraAllowed)startCamera();else status('Camera permission is off. Import a QR image or tap Retry camera.','bad');}catch(e){status('Platform QR detection is unavailable. Use the Setup key instead.','bad');}})();"
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
        drawable.setColor(primary ? CYAN : PANEL);
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
