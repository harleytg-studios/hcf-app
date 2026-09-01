package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.InvertedLuminanceSource;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HCF_AUTHENTICATOR_NATIVE_QR_SCANNER_V3_SYSTEM_UI
 *
 * Native camera/image QR importer used by the dedicated HCF Authenticator
 * subsettings panel. Camera decoding and screenshot decoding use bundled ZXing
 * Core, not Android WebView BarcodeDetector.
 *
 * The screen intentionally follows the same HCF Settings visual language:
 * compact back/logo header, cyan metadata, bordered cards, rounded controls and
 * the same dark surface hierarchy as Account & Security.
 */
@SuppressWarnings("deprecation")
public final class HcfAuthenticatorSettingsQrActivity extends Activity
        implements SurfaceHolder.Callback {

    private static final int REQUEST_CAMERA = 8341;
    private static final int REQUEST_IMAGE = 8342;
    private static final int BG = Color.rgb(8, 13, 17);
    private static final int PANEL = Color.rgb(18, 28, 35);
    private static final int SURFACE = Color.rgb(14, 22, 28);
    private static final int BORDER = Color.rgb(41, 64, 75);
    private static final int CYAN = Color.rgb(0, 184, 240);
    private static final int TEXT = Color.rgb(239, 247, 250);
    private static final int MUTED = Color.rgb(155, 174, 183);
    private static final int ERROR = Color.rgb(255, 77, 87);

    private static final Map<DecodeHintType, Object> QR_HINTS;
    static {
        EnumMap<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        QR_HINTS = Collections.unmodifiableMap(hints);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private SurfaceView preview;
    private SurfaceHolder previewHolder;
    private TextView status;
    private Button retryCamera;
    private Camera camera;
    private int cameraId = -1;
    private boolean surfaceReady;
    private boolean decodeBusy;
    private boolean resultHandled;
    private HandlerThread decodeThread;
    private Handler decodeHandler;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        decodeThread = new HandlerThread("HcfQrDecode");
        decodeThread.start();
        decodeHandler = new Handler(decodeThread.getLooper());

        buildUi();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Camera permission is needed for live scanning. You can still import a QR image.", false);
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (surfaceReady
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && camera == null
                && !resultHandled) {
            startCamera();
        }
    }

    @Override protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopCamera();
        if (decodeThread != null) {
            decodeThread.quitSafely();
            decodeThread = null;
            decodeHandler = null;
        }
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!surfaceReady || camera == null) return;
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            requestNextFrame();
        } catch (Throwable error) {
            setStatus("Camera preview could not restart. Tap Restart camera or import a QR image.", false);
        }
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopCamera();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA) return;
        boolean allowed = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (allowed) {
            setStatus("Camera ready. Point it at the QR code in forum User Settings.", true);
            if (surfaceReady) startCamera();
        } else {
            setStatus("Camera access is off. Import the QR image instead, or tap Restart camera to grant access.", false);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri image = data.getData();
        setStatus("Reading QR image on this device…", true);
        if (decodeHandler == null) return;
        decodeHandler.post(() -> {
            String value = decodeImage(image);
            main.post(() -> {
                if (value != null) {
                    handleDecodedValue(value);
                } else {
                    setStatus("No TOTP authenticator QR code was found. Try a clearer image or use the Setup key.", false);
                }
            });
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(9), dp(12), dp(12));

        // Same compact header language used by Account & Security.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = iconButton(R.drawable.fa_arrow_left, "Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(58), dp(58)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum logo");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        logoLp.leftMargin = dp(10);
        header.addView(logo, logoLp);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Scan 2FA QR", 20, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        TextView subtitle = text("HCF Authenticator Setup • Nearata", 10, CYAN);
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(3);
        labels.addView(subtitle, subtitleLp);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsLp.leftMargin = dp(12);
        header.addView(labels, labelsLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout scannerCard = new LinearLayout(this);
        scannerCard.setOrientation(LinearLayout.VERTICAL);
        scannerCard.setPadding(dp(10), dp(10), dp(10), dp(10));
        scannerCard.setBackground(roundRect(PANEL, BORDER, 15));
        LinearLayout.LayoutParams scannerCardLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scannerCardLp.topMargin = dp(13);
        root.addView(scannerCard, scannerCardLp);

        TextView scannerLabel = text("Camera scanner", 11, CYAN);
        scannerLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        scannerCard.addView(scannerLabel);

        TextView instructions = text(
                "Point the camera at the QR code shown in forum User Settings → Two-Factor Authentication.",
                10, MUTED);
        instructions.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams instructionsLp = new LinearLayout.LayoutParams(-1, -2);
        instructionsLp.topMargin = dp(4);
        scannerCard.addView(instructions, instructionsLp);

        FrameLayout stageShell = new FrameLayout(this);
        stageShell.setBackground(roundRect(Color.rgb(7, 11, 14), BORDER, 13));
        stageShell.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams stageShellLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        stageShellLp.topMargin = dp(10);
        scannerCard.addView(stageShell, stageShellLp);

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.rgb(7, 11, 14));
        preview = new SurfaceView(this);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(this);
        stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(new ScannerOverlay(this), new FrameLayout.LayoutParams(-1, -1));
        stageShell.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        status = text("Preparing native QR scanner…", 10, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0f, 1.06f);
        status.setPadding(dp(10), dp(8), dp(10), dp(8));
        status.setBackground(roundRect(SURFACE, BORDER, 10));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(9);
        scannerCard.addView(status, statusLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(11), dp(12), dp(12));
        controls.setBackground(roundRect(PANEL, BORDER, 15));
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.topMargin = dp(11);
        root.addView(controls, controlsLp);

        TextView optionsLabel = text("Scan options", 11, CYAN);
        optionsLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        controls.addView(optionsLabel);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(9);
        controls.addView(actions, actionsLp);

        retryCamera = button("Restart camera", true);
        retryCamera.setOnClickListener(v -> retryCamera());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actions.addView(retryCamera, actionLp);

        Button importImage = button("Import QR image", false);
        importImage.setOnClickListener(v -> chooseImage());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        importLp.leftMargin = dp(9);
        actions.addView(importImage, importLp);

        Button manual = button("Use Setup key instead", false);
        manual.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams manualLp = new LinearLayout.LayoutParams(-1, dp(48));
        manualLp.topMargin = dp(9);
        controls.addView(manual, manualLp);

        TextView privacy = text(
                "QR data is decoded locally on this device and saved only to the encrypted HCF Authenticator vault.",
                9, MUTED);
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.topMargin = dp(9);
        controls.addView(privacy, privacyLp);

        setContentView(root);
    }

    private void retryCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        stopCamera();
        if (surfaceReady) startCamera();
    }

    private void chooseImage() {
        Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        choose.setType("image/*");
        try {
            startActivityForResult(choose, REQUEST_IMAGE);
        } catch (Throwable error) {
            Toast.makeText(this, "No image picker is available.", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        if (!surfaceReady || resultHandled || camera != null) return;
        try {
            cameraId = findBackCamera();
            if (cameraId < 0) throw new IllegalStateException("No rear camera");
            Camera opened = Camera.open(cameraId);
            Camera.Parameters params = opened.getParameters();
            params.setPreviewFormat(ImageFormat.NV21);
            Camera.Size chosen = choosePreviewSize(params.getSupportedPreviewSizes());
            if (chosen != null) params.setPreviewSize(chosen.width, chosen.height);

            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }

            opened.setParameters(params);
            opened.setDisplayOrientation(cameraDisplayOrientation(cameraId));
            opened.setPreviewDisplay(previewHolder);
            opened.startPreview();
            camera = opened;
            setStatus("Scanning… hold the Nearata QR code inside the frame.", true);
            requestNextFrame();
        } catch (Throwable error) {
            stopCamera();
            setStatus("The camera could not start. Import a QR image or tap Restart camera.", false);
        }
    }

    private void stopCamera() {
        Camera active = camera;
        camera = null;
        decodeBusy = false;
        if (active == null) return;
        try { active.setOneShotPreviewCallback(null); } catch (Throwable ignored) {}
        try { active.setPreviewCallback(null); } catch (Throwable ignored) {}
        try { active.stopPreview(); } catch (Throwable ignored) {}
        try { active.release(); } catch (Throwable ignored) {}
    }

    private void requestNextFrame() {
        final Camera active = camera;
        if (active == null || decodeBusy || resultHandled || decodeHandler == null) return;
        try {
            active.setOneShotPreviewCallback((data, sourceCamera) -> {
                if (data == null || camera == null || resultHandled || decodeHandler == null) return;
                Camera.Size size;
                try {
                    size = sourceCamera.getParameters().getPreviewSize();
                } catch (Throwable error) {
                    scheduleNextFrame();
                    return;
                }
                if (size == null || size.width <= 0 || size.height <= 0) {
                    scheduleNextFrame();
                    return;
                }
                int yLength = size.width * size.height;
                if (data.length < yLength) {
                    scheduleNextFrame();
                    return;
                }

                byte[] luminance = Arrays.copyOf(data, yLength);
                int width = size.width;
                int height = size.height;
                decodeBusy = true;
                decodeHandler.post(() -> {
                    String value = decodeCameraFrame(luminance, width, height);
                    main.post(() -> {
                        decodeBusy = false;
                        if (value != null) handleDecodedValue(value);
                        else scheduleNextFrame();
                    });
                });
            });
        } catch (Throwable error) {
            decodeBusy = false;
            setStatus("Camera frame capture failed. Tap Restart camera or import a QR image.", false);
        }
    }

    private void scheduleNextFrame() {
        if (resultHandled || camera == null) return;
        main.postDelayed(this::requestNextFrame, 90L);
    }

    private String decodeCameraFrame(byte[] yPlane, int width, int height) {
        LumaFrame frame = new LumaFrame(yPlane, width, height);
        for (int rotation = 0; rotation < 4; rotation++) {
            String value = decodeLuma(frame.data, frame.width, frame.height);
            if (isTotp(value)) return value;
            frame = rotate90(frame);
        }
        return null;
    }

    private String decodeLuma(byte[] data, int width, int height) {
        if (data == null || width <= 0 || height <= 0 || data.length < width * height) return null;
        try {
            LuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false);
            String normal = decodeSource(source);
            if (normal != null) return normal;
            return decodeSource(new InvertedLuminanceSource(source));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String decodeImage(Uri uri) {
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }

            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / sample > 2048) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return null;

            Bitmap current = bitmap;
            for (int rotation = 0; rotation < 4; rotation++) {
                String value = decodeBitmap(current);
                if (isTotp(value)) return value;
                if (rotation < 3) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90f);
                    Bitmap next = Bitmap.createBitmap(current, 0, 0,
                            current.getWidth(), current.getHeight(), matrix, true);
                    if (current != bitmap && current != next) current.recycle();
                    current = next;
                }
            }
            if (current != bitmap) current.recycle();
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        return null;
    }

    private String decodeBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) return null;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        String normal = decodeSource(source);
        return normal != null ? normal : decodeSource(new InvertedLuminanceSource(source));
    }

    private String decodeSource(LuminanceSource source) {
        QRCodeReader reader = new QRCodeReader();
        try {
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), QR_HINTS);
            return result == null ? null : result.getText();
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { reader.reset(); } catch (Throwable ignored) {}
        }
    }

    private void handleDecodedValue(String value) {
        if (resultHandled) return;
        if (!isTotp(value)) {
            setStatus("A QR code was found, but it is not a TOTP authenticator setup.", false);
            scheduleNextFrame();
            return;
        }
        resultHandled = true;
        stopCamera();
        setStatus("Authenticator QR detected. Saving securely…", true);
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Config.fromOtpAuth(Uri.parse(value.trim()));
            HcfAuthenticator.Vault.save(this, config);
            Toast.makeText(this, "QR code saved to HCF Authenticator.", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Throwable error) {
            resultHandled = false;
            setStatus("The QR code was read, but HCF could not save that authenticator setup. Use the Setup key instead.", false);
            if (surfaceReady && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            }
        }
    }

    private boolean isTotp(String value) {
        return value != null && value.trim().toLowerCase(Locale.US).startsWith("otpauth://totp/");
    }

    private int findBackCamera() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return count > 0 ? 0 : -1;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        long targetArea = 1280L * 720L;
        long bestDelta = Long.MAX_VALUE;
        for (Camera.Size size : sizes) {
            if (size == null || size.width <= 0 || size.height <= 0) continue;
            long area = (long) size.width * size.height;
            if (area > 1920L * 1080L) continue;
            long delta = Math.abs(area - targetArea);
            if (delta < bestDelta) {
                best = size;
                bestDelta = delta;
            }
        }
        return best;
    }

    private int cameraDisplayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees;
        switch (rotation) {
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
            default: degrees = 0; break;
        }
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            int result = (info.orientation + degrees) % 360;
            return (360 - result) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private static LumaFrame rotate90(LumaFrame frame) {
        int width = frame.width;
        int height = frame.height;
        byte[] output = new byte[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                output[x * height + (height - 1 - y)] = frame.data[row + x];
            }
        }
        return new LumaFrame(output, height, width);
    }

    private void setStatus(String message, boolean positive) {
        if (status == null) return;
        status.setText(message);
        status.setTextColor(positive ? MUTED : ERROR);
        status.setBackground(roundRect(positive ? SURFACE : Color.rgb(35, 19, 23),
                positive ? BORDER : Color.rgb(104, 45, 53), 10));
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setImageTintList(ColorStateList.valueOf(CYAN));
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        button.setBackground(roundRect(SURFACE, BORDER, 15));
        button.setStateListAnimator(null);
        return button;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextColor(primary ? Color.BLACK : CYAN);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setStateListAnimator(null);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(roundRect(primary ? CYAN : SURFACE,
                primary ? CYAN : BORDER, 12));
        return button;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LumaFrame {
        final byte[] data;
        final int width;
        final int height;
        LumaFrame(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }
    }

    private static final class ScannerOverlay extends View {
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scan = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        ScannerOverlay(Activity context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            shade.setColor(Color.argb(88, 0, 0, 0));
            frame.setColor(CYAN);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(2f * density);
            scan.setColor(Color.argb(205, 0, 184, 240));
            scan.setStrokeWidth(2f * density);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth() * 0.72f, 320f * density);
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            RectF box = new RectF(left, top, left + size, top + size);
            canvas.drawRect(0, 0, getWidth(), top, shade);
            canvas.drawRect(0, top + size, getWidth(), getHeight(), shade);
            canvas.drawRect(0, top, left, top + size, shade);
            canvas.drawRect(left + size, top, getWidth(), top + size, shade);
            canvas.drawRoundRect(box, 22f * density, 22f * density, frame);

            long cycle = SystemClock.uptimeMillis() % 1800L;
            float phase = cycle / 1800f;
            float lineY = top + (size * phase);
            canvas.drawLine(left + 14f * density, lineY,
                    left + size - 14f * density, lineY, scan);
            postInvalidateDelayed(16L);
        }
    }
}
