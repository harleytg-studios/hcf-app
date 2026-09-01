package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

/**
 * Dev/Beta-only diagnostic UI for the app's native IP-ban enforcement path.
 *
 * Privacy rules for this diagnostic:
 * - never render or log a repository owner/name;
 * - never render or log the ban-list/config source URL;
 * - never render or log the device's raw public IP address;
 * - never render or log a username, ban id, reason, or ban-list entry;
 * - logs contain only coarse health state and exception class names.
 */
public final class HcfBanDevTools {
    private static final String VIEW_TAG = "hcf_ip_ban_devtools_v1";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static WeakReference<Activity> resumedSettings = new WeakReference<>(null);

    private HcfBanDevTools() {}

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (activity instanceof HcfSubActivities.SettingsActivity) {
                            resumedSettings = new WeakReference<>(activity);
                            MAIN.removeCallbacks(POLL);
                            MAIN.post(POLL);
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {
                        Activity current = resumedSettings.get();
                        if (current == activity) {
                            resumedSettings.clear();
                            MAIN.removeCallbacks(POLL);
                        }
                    }

                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override public void onActivityDestroyed(Activity activity) {
                        Activity current = resumedSettings.get();
                        if (current == activity) {
                            resumedSettings.clear();
                            MAIN.removeCallbacks(POLL);
                        }
                    }
                });
    }

    private static final Runnable POLL = new Runnable() {
        @Override public void run() {
            Activity activity = resumedSettings.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            try {
                injectIntoDeveloperTools(activity);
            } catch (Throwable error) {
                AppLogger.warn(activity, "ip_ban_devtools_ui", error.getClass().getSimpleName());
            }
            MAIN.postDelayed(this, 650L);
        }
    };

    private static void injectIntoDeveloperTools(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        if (decor.findViewWithTag(VIEW_TAG) != null) return;

        TextView title = findText((ViewGroup) decor, "Developer Tools");
        if (title == null) return;

        LinearLayout body = findPanelBody(title);
        if (body == null) return;

        LinearLayout host = body;
        if (body.getChildCount() > 0 && body.getChildAt(0) instanceof LinearLayout) {
            host = (LinearLayout) body.getChildAt(0);
        }
        if (host.findViewWithTag(VIEW_TAG) != null) return;

        final TextView status = new TextView(activity);
        final Button check = new Button(activity);
        LinearLayout block = buildBlock(activity, status, check);
        block.setTag(VIEW_TAG);
        host.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        check.setOnClickListener(v -> runCheck(activity, status, check));
    }

    private static LinearLayout buildBlock(Activity activity, TextView status, Button check) {
        int density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 12 * density, 0, 0);

        TextView heading = new TextView(activity);
        heading.setText("IP Ban System");
        heading.setTextSize(12f);
        heading.setTypeface(null, 1);
        heading.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        block.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = new TextView(activity);
        privacy.setText("Tests the app's live ban enforcement path without displaying source details or your public IP.");
        privacy.setTextSize(10f);
        privacy.setTextColor(activity.getColor(R.color.hcf_muted));
        privacy.setPadding(0, 4 * density, 0, 8 * density);
        block.addView(privacy, new LinearLayout.LayoutParams(-1, -2));

        status.setText("Status: Not checked yet\nSource details: Hidden");
        status.setTextSize(11f);
        status.setTextColor(activity.getColor(R.color.hcf_meta));
        status.setPadding(12 * density, 10 * density, 12 * density, 10 * density);
        status.setBackgroundResource(R.drawable.quick_action_background);
        block.addView(status, new LinearLayout.LayoutParams(-1, -2));

        try { UiButtons.normalizeText(check); } catch (Throwable ignored) {}
        check.setAllCaps(false);
        check.setText("Check IP Ban System");
        check.setTextSize(13f);
        check.setTextColor(activity.getColor(R.color.hcf_text));
        check.setGravity(Gravity.CENTER);
        check.setMinHeight(0);
        check.setMinimumHeight(0);
        check.setBackgroundResource(R.drawable.quick_action_background);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, 48 * density);
        buttonLp.topMargin = 8 * density;
        block.addView(check, buttonLp);

        return block;
    }

    private static void runCheck(Activity activity, TextView status, Button check) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        check.setEnabled(false);
        check.setText("Checking IP Ban System…");
        status.setText("Status: Checking…\nConfiguration: Checking\nBan list: Checking\nNetwork lookup: Checking\nSource details: Hidden");
        status.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        AppLogger.info(activity, "ip_ban_diagnostic", "started");

        Context app = activity.getApplicationContext();
        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                DiagnosticResult result = diagnose(app);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        status.setText(result.displayText());
                        status.setTextColor(activity.getColor(result.colorRes()));
                        check.setEnabled(true);
                        check.setText("Check IP Ban System Again");
                        AppLogger.info(activity, "ip_ban_diagnostic", result.logState);
                    }
                });
            }
        });
    }

    /**
     * Executes a true service diagnostic even when the signed-in developer account is on
     * the app's enforcement bypass list. This intentionally does not evaluate or disclose
     * whether the current account/network matches a ban entry.
     */
    private static DiagnosticResult diagnose(Context context) {
        try {
            HcfBanSystem.RuntimeConfig config = loadRuntimeConfigForDiagnostic(context);
            if (config == null) {
                return DiagnosticResult.unavailable("Configuration could not be loaded");
            }
            if (!config.ready()) {
                return DiagnosticResult.inactive();
            }

            JSONObject root = new JSONObject(downloadJson(config.banListUrl));
            if (root.optInt("schema_version", 0) != 1) {
                return DiagnosticResult.unavailable("Ban-list schema is invalid");
            }
            if (root.optJSONObject("users") == null || root.optJSONObject("ip_sha256") == null) {
                return DiagnosticResult.unavailable("Ban-list structure is incomplete");
            }

            boolean networkAvailable = lookupPublicIpAvailable(config.ipPrimary)
                    || lookupPublicIpAvailable(config.ipFallback);
            if (!networkAvailable) return DiagnosticResult.degraded();
            return DiagnosticResult.working();
        } catch (Throwable error) {
            Throwable clean = unwrap(error);
            String name = clean == null ? "UnknownError" : clean.getClass().getSimpleName();
            return DiagnosticResult.unavailable(name);
        }
    }

    private static HcfBanSystem.RuntimeConfig loadRuntimeConfigForDiagnostic(Context context) throws Exception {
        Method method = HcfBanSystem.class.getDeclaredMethod("loadRuntimeConfig", Context.class);
        method.setAccessible(true);
        Object value = method.invoke(null, context);
        return value instanceof HcfBanSystem.RuntimeConfig
                ? (HcfBanSystem.RuntimeConfig) value : null;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        return current;
    }

    private static String downloadJson(String source) throws Exception {
        if (source == null || !source.startsWith("https://")) {
            throw new IllegalStateException("InvalidSource");
        }
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanDiagnostic/1");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HttpError");
            return readAll(connection.getInputStream(), 131072);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean lookupPublicIpAvailable(String source) {
        if (source == null || !source.startsWith("https://")) return false;
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/plain;q=0.9");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanDiagnosticIp/1");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return false;
            String body = readAll(connection.getInputStream(), 8192).trim();
            String ip = body;
            try { ip = new JSONObject(body).optString("ip", ""); } catch (Throwable ignored) {}
            return looksLikeIp(ip);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean looksLikeIp(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("::ffff:")) raw = raw.substring(7);
        if (raw.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            String[] parts = raw.split("\\.");
            for (String part : parts) {
                try {
                    int number = Integer.parseInt(part);
                    if (number < 0 || number > 255) return false;
                } catch (Throwable ignored) { return false; }
            }
            return true;
        }
        return raw.indexOf(':') >= 0 && raw.length() <= 64
                && raw.matches("^[0-9a-fA-F:]+$");
    }

    private static String readAll(InputStream stream, int max) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                if (out.length() > max) throw new IllegalStateException("ResponseTooLarge");
            }
            return out.toString();
        }
    }

    private static TextView findText(ViewGroup root, String exact) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && exact.equals(text.toString().trim())) return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView nested = findText((ViewGroup) child, exact);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean containsText(View root, String exact) {
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && exact.equals(text.toString().trim())) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), exact)) return true;
            }
        }
        return false;
    }

    private static LinearLayout findPanelBody(TextView title) {
        View current = title;
        for (int depth = 0; depth < 7 && current != null; depth++) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) parent;
            if (group instanceof LinearLayout && group.getChildCount() >= 2) {
                View header = group.getChildAt(0);
                View body = group.getChildAt(1);
                if (body instanceof LinearLayout && containsText(header, "Developer Tools")) {
                    return (LinearLayout) body;
                }
            }
            current = (View) parent;
        }
        return null;
    }

    private static final class DiagnosticResult {
        final String state;
        final String config;
        final String list;
        final String network;
        final String logState;
        final int color;

        DiagnosticResult(String state, String config, String list, String network,
                         String logState, int color) {
            this.state = state;
            this.config = config;
            this.list = list;
            this.network = network;
            this.logState = logState;
            this.color = color;
        }

        static DiagnosticResult working() {
            return new DiagnosticResult("Working", "Active", "Reachable • schema v1",
                    "Available", "working", R.color.hcf_accent_text);
        }

        static DiagnosticResult degraded() {
            return new DiagnosticResult("Degraded", "Active", "Reachable • schema v1",
                    "Unavailable", "degraded_network_lookup", R.color.hcf_warning);
        }

        static DiagnosticResult inactive() {
            return new DiagnosticResult("Inactive", "Not enabled", "Not tested",
                    "Not tested", "inactive", R.color.hcf_warning);
        }

        static DiagnosticResult unavailable(String reason) {
            String safeReason = sanitizeReason(reason);
            return new DiagnosticResult("Unavailable", "Could not verify",
                    "Could not verify", "Not verified",
                    "unavailable_" + safeReason.toLowerCase(Locale.US), R.color.hcf_error);
        }

        String displayText() {
            String extra = "Degraded".equals(state)
                    ? "\nNote: account-ban data is reachable, but network/IP bans cannot be reliably evaluated until public-IP lookup recovers."
                    : "";
            return "Status: " + state
                    + "\nConfiguration: " + config
                    + "\nBan list: " + list
                    + "\nNetwork lookup: " + network
                    + "\nSource details: Hidden"
                    + extra;
        }

        int colorRes() { return color; }

        private static String sanitizeReason(String value) {
            String out = value == null ? "error" : value.replaceAll("[^A-Za-z0-9_]", "_");
            if (out.isEmpty()) out = "error";
            if (out.length() > 32) out = out.substring(0, 32);
            return out;
        }
    }

    /** Auto-installs the Dev Tools injector without exporting any component. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}
