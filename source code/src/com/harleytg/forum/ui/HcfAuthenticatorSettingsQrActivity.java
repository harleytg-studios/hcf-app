package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
 * HCF_AUTHENTICATOR_NATIVE_QR_SCANNER_V2
 *
 * Native camera/image QR importer used by the HCF Authenticator Settings panel.
 *
 * The old scanner depended on the BarcodeDetector Web API inside Android WebView.
 * BarcodeDetector is not consistently implemented by Android System WebView, so
 * some devices could show a camera preview but never decode a QR code. This
 * scanner uses Android Camera + bundled ZXing Core instead. No network access or
 * WebView QR APIs are required.
 *
 * A successful Nearata TOTP QR is validated and saved directly to the encrypted
 * HcfAuthenticator vault. Imported screenshots use the same local decoder.
 */
@SuppressWarnings("deprecation")
public final class HcfAuthenticatorSettingsQrActivity extends Activity
        implements SurfaceHolder.Callback {

    private static final int REQUEST_CAMERA = 8341;
    private static final int REQUEST_IMAGE = 8342;
    private static final int BG = Color.rgb(13, 16, 20);
    private static final int PANEL = Color.rgb(17, 27, 34);
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
            setStatus("Camera permission is needed for live scanning. You can still import a QR screenshot.", false);
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
            setStatus("Camera preview could not restart. Tap Retry camera or import a QR screenshot.", false);
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
            setStatus("Camera access is off. Import the QR screenshot instead, or tap Retry camera to grant access.", false);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri image = data.getData();
        setStatus("Reading QR screenshot on this device…", true);
        if (decodeHandler == null) return;
        decodeHandler.post(() -> {
            String value = decodeImage(image);
            main.post(() -> {
                if (value != null) {
                    handleDecodedValue(value);
                } else {
                    setStatus("No TOTP authenticator QR code was found in that image. Try a clearer screenshot or use the Setup key.", false);
                }
            });
        });
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
        TextView title = text("Scan 2FA QR Code", 18, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        labels.addView(text("Nearata User Settings • decoded locally with HCF", 9, MUTED));
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsLp.leftMargin = dp(8);
        header.addView(labels, labelsLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        status = text("Preparing native QR scanner…", 10, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(5), dp(12), dp(8));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.rgb(8, 11, 14));
        preview = new SurfaceView(this);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(this);
        stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(new ScannerOverlay(this), new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(14), dp(12), dp(14), dp(14));
        controls.setBackgroundColor(PANEL);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        retryCamera = button("Retry camera", true);
        retryCamera.setOnClickListener(v -> retryCamera());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actions.addView(retryCamera, actionLp);

        Button importImage = button("Import QR screenshot", false);
        importImage.setOnClickListener(v -> chooseImage());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        importLp.leftMargin = dp(9);
        actions.addView(importImage, importLp);
        controls.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = text("Point the rear camera at the QR code shown under User Settings → Two-Factor Authentication. Only TOTP setup QR codes are accepted.", 10, MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(10);
        controls.addView(hint, hintLp);

        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
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
            setStatus("The camera could not start. Import a QR screenshot or tap Retry camera.", false);
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
            setStatus("Camera frame capture failed. Tap Retry camera or import a QR screenshot.", false);
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
        button.setStateListAnimator(null);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundRect(primary ? CYAN : Color.rgb(12, 21, 27),
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
        private final float density;

        ScannerOverlay(Activity context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            shade.setColor(Color.argb(92, 0, 0, 0));
            frame.setColor(CYAN);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(2f * density);
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
        }
    }
}
