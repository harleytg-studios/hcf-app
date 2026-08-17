package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.os.Build;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Privacy-scoped telemetry and diagnostics.
 *
 * Telemetry is OFF by default. Basic mode sends only coarse app-health events.
 * Diagnostics mode can send crash/error diagnostics after the user enables it.
 * Forum identity is a separate opt-in and is never implied by enabling telemetry.
 */
final class TelemetryService {
    static final String LEVEL_BASIC = "basic";
    static final String LEVEL_DIAGNOSTICS = "diagnostics";

    private static final long HEARTBEAT_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_BREADCRUMBS = 25;
    private static final int MAX_HISTORY = 20;
    private static final String KEY_SALT = "HCF-Telemetry-Discord-Relay-v1";
    private static final String AAD = "HCF_TELEMETRY";
    private static final String PENDING_CRASH_FILE = "pending-crash.json";

    // AES-GCM ciphertext of the configured HTTPS Discord webhook. The key is
    // derived at runtime from package name + the installed signing certificate.
    private static final String WEBHOOK_IV_B64 = "uoO2HMaYrR1Qjq+6";
    private static final String WEBHOOK_CIPHERTEXT_B64 =
            "VwSJKWKfeSuD5qbdEnHBEyUmADOfG8Fz61tvPWfIaqjA3c20QXuN1hgob3q5Oe26CKskpawsGgAHQm0uWQCveFBGHJbo/Ux6GT400Emw1NDCMp7dGDrMhEqDMUElQpRD7mE6WyOPeEJRQzWYavxeceU6bo3aWvdfNy9UZuAy1UuNMU/cUIrDqfA=";

    static boolean isEnabled(Context context) {
        if (context == null) return false;
        return safeBoolean(prefs(context), AppPrefs.TELEMETRY_ENABLED, false);
    }

    static String level(Context context) {
        String value = safeString(prefs(context), AppPrefs.TELEMETRY_LEVEL, LEVEL_BASIC);
        return LEVEL_DIAGNOSTICS.equals(value) ? LEVEL_DIAGNOSTICS : LEVEL_BASIC;
    }

    static boolean isDiagnostics(Context context) {
        return isEnabled(context) && LEVEL_DIAGNOSTICS.equals(level(context));
    }

    static String levelLabel(Context context) {
        return LEVEL_DIAGNOSTICS.equals(level(context)) ? "Diagnostics" : "Basic";
    }

    static String status(Context context) {
        if (!isEnabled(context)) return "Telemetry: Off • no telemetry is sent";
        String last = safeString(prefs(context), AppPrefs.TELEMETRY_LAST_RESULT, "Ready");
        return "Telemetry: On • " + levelLabel(context) + " • "
                + (last == null || last.trim().isEmpty() ? "Ready" : last.trim());
    }

    static void heartbeat(Context context) {
        if (context == null || !isEnabled(context)) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        long now = System.currentTimeMillis();
        long last = safeLong(prefs, AppPrefs.TELEMETRY_LAST_HEARTBEAT, 0L);
        if (last > 0L && now - last < HEARTBEAT_INTERVAL_MS) return;
        prefs.edit().putLong(AppPrefs.TELEMETRY_LAST_HEARTBEAT, now).apply();
        sendEvent(app, "app_heartbeat", "foreground");
    }

    static void noteRoute(Context context, String rawUrl) {
        if (context == null) return;
        String route = sanitizedRoute(rawUrl, false);
        prefs(context).edit().putString(AppPrefs.TELEMETRY_LAST_ROUTE, route).apply();
    }

    static void recordBreadcrumb(Context context, String event, String detail) {
        if (context == null || !isDiagnostics(context)) return;
        try {
            SharedPreferences prefs = prefs(context);
            JSONArray current;
            try { current = new JSONArray(safeString(prefs, AppPrefs.TELEMETRY_BREADCRUMBS, "[]")); }
            catch (Throwable ignored) { current = new JSONArray(); }
            JSONArray next = new JSONArray();
            int start = Math.max(0, current.length() - (MAX_BREADCRUMBS - 1));
            for (int i = start; i < current.length(); i++) next.put(current.opt(i));
            JSONObject item = new JSONObject();
            item.put("time", isoNow());
            item.put("event", safeToken(event, "event"));
            item.put("detail", safeDetail(detail, 180));
            next.put(item);
            prefs.edit().putString(AppPrefs.TELEMETRY_BREADCRUMBS, next.toString()).apply();
        } catch (Throwable ignored) {}
    }

    static void sendEvent(Context context, String event, String detail) {
        if (context == null || !isEnabled(context)) return;
        final Context app = context.getApplicationContext();
        final String safeEvent = safeToken(event, "app_event");
        final String safeDetail = safeDetail(detail, 240);
        new Thread(() -> {
            try {
                JSONObject report = buildBaseReport(app, "event", safeEvent, safeDetail, null);
                postReport(app, report);
            } catch (Throwable t) {
                saveResult(app, "Last send failed");
            }
        }, "hcf-telemetry-event").start();
    }

    static void sendDiagnosticEvent(Context context, String event, String detail) {
        if (context == null || !isDiagnostics(context)) return;
        if (!safeBoolean(prefs(context), AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS, false)) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject report = buildBaseReport(app, "diagnostic_error", safeToken(event, "error"), safeDetail(detail, 420), null);
                report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {}
        }, "hcf-telemetry-error").start();
    }

    static void sendTest(Context context) {
        if (LEVEL_DIAGNOSTICS.equals(level(context))) {
            sendDiagnosticEventForced(context, "telemetry_test", "manual diagnostics test from App Settings");
        } else {
            sendEvent(context, "telemetry_test", "manual basic telemetry test from App Settings");
        }
    }

    private static void sendDiagnosticEventForced(Context context, String event, String detail) {
        if (context == null || !isEnabled(context)) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject report = buildBaseReport(app, "diagnostic_test", safeToken(event, "diagnostic_test"), safeDetail(detail, 420), null);
                report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {}
        }, "hcf-telemetry-test").start();
    }

    static void captureCrash(Context context, Thread thread, Throwable throwable) {
        if (context == null || throwable == null || !isDiagnostics(context)) return;
        try {
            // Keep identity out of the pending crash file. It is added only at send time
            // if the user explicitly enables the identity option globally or per report.
            JSONObject report = buildBaseReport(context, "crash", "uncaught_exception", "", false);
            report.put("thread", safeDetail(thread == null ? "unknown" : thread.getName(), 80));
            report.put("exception", throwable.getClass().getName());
            report.put("message", safeDetail(String.valueOf(throwable.getMessage()), 800));
            report.put("stackTrace", sanitizeStack(throwable));
            report.put("breadcrumbs", breadcrumbs(context));
            writeText(pendingCrashFile(context), report.toString());
            prefs(context).edit().putString(AppPrefs.TELEMETRY_PENDING_CRASH_ID, report.optString("reportId", "")).apply();
        } catch (Throwable ignored) {}
    }

    static boolean hasPendingCrash(Context context) {
        return context != null && pendingCrashFile(context).exists();
    }

    static void handlePendingCrash(Activity activity) {
        if (activity == null || activity.isFinishing() || !hasPendingCrash(activity)) return;
        if (!isDiagnostics(activity)) return;

        final SharedPreferences prefs = prefs(activity);
        boolean auto = safeBoolean(prefs, AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS, false);
        boolean ask = safeBoolean(prefs, AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT, true);
        if (auto && !ask) {
            sendPendingCrash(activity, "", safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false));
            return;
        }

        JSONObject pending = readPendingCrash(activity);
        if (pending == null) return;
        final String reportId = pending.optString("reportId", "HCF-REPORT");

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView intro = new TextView(activity);
        intro.setText("Harley's Clan Forum recovered from a problem.\nReport ID: " + reportId);
        intro.setTextSize(14);
        box.addView(intro);

        EditText feedback = new EditText(activity);
        feedback.setHint("What were you doing when this happened? (optional)");
        feedback.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        feedback.setMinLines(2);
        feedback.setMaxLines(5);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(activity, 10);
        box.addView(feedback, fp);

        Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false));
        box.addView(identity);

        TextView privacy = new TextView(activity);
        privacy.setText("Passwords, cookies, access tokens, recovery codes, posts and messages are never included.");
        privacy.setTextSize(11);
        box.addView(privacy);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Send crash report?")
                .setView(box)
                .setPositiveButton("Send Report", null)
                .setNeutralButton("Preview", null)
                .setNegativeButton("Don't Send", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                sendPendingCrash(activity, feedback.getText().toString(), identity.isChecked());
                Toast.makeText(activity, "Crash report queued: " + reportId, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showTextDialog(activity,
                    "Crash report preview", previewPendingReport(activity, feedback.getText().toString(), identity.isChecked())));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                deletePendingCrash(activity);
                addHistory(activity, reportId, "crash", "discarded");
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    static void showManualFeedbackDialog(Activity activity) {
        if (activity == null) return;
        if (!isEnabled(activity)) {
            Toast.makeText(activity, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText note = new EditText(activity);
        note.setHint("Describe the problem or feedback");
        note.setMinLines(3);
        note.setMaxLines(7);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(note);

        Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(prefs(activity).getBoolean(AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false));
        box.addView(identity);

        new AlertDialog.Builder(activity)
                .setTitle("Send diagnostic feedback")
                .setView(box)
                .setPositiveButton("Send", (d, w) -> {
                    String text = safeDetail(note.getText().toString(), 900);
                    sendManualFeedback(activity, text, identity.isChecked());
                    Toast.makeText(activity, "Diagnostic feedback queued.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void showPreview(Activity activity) {
        if (activity == null) return;
        showTextDialog(activity, "Telemetry report preview", previewReport(activity));
    }

    static void showHistory(Activity activity) {
        if (activity == null) return;
        showTextDialog(activity, "Telemetry report history", historyText(activity));
    }

    static void clearLocalReports(Context context) {
        if (context == null) return;
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        prefs(context).edit()
                .remove(AppPrefs.TELEMETRY_REPORT_HISTORY)
                .remove(AppPrefs.TELEMETRY_PENDING_CRASH_ID)
                .remove(AppPrefs.TELEMETRY_BREADCRUMBS)
                .apply();
    }

    static String previewReport(Context context) {
        try {
            JSONObject report = buildBaseReport(context, "preview", "settings_preview", "Example of the data this app would send with the current telemetry settings.", null);
            if (LEVEL_DIAGNOSTICS.equals(level(context))) report.put("breadcrumbs", breadcrumbs(context));
            return report.toString(2);
        } catch (Throwable t) {
            return "Preview unavailable: " + t.getClass().getSimpleName();
        }
    }

    private static String previewPendingReport(Context context, String note, boolean includeIdentity) {
        try {
            JSONObject pending = readPendingCrash(context);
            if (pending == null) return "No pending crash report.";
            JSONObject prepared = prepareReportForSend(context, pending, note, includeIdentity);
            return prepared.toString(2);
        } catch (Throwable t) {
            return "Preview unavailable: " + t.getClass().getSimpleName();
        }
    }

    private static void sendPendingCrash(Context context, String note, boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        final JSONObject pending = readPendingCrash(app);
        if (pending == null) return;
        new Thread(() -> {
            try {
                JSONObject prepared = prepareReportForSend(app, pending, note, includeIdentity);
                boolean success = postReport(app, prepared);
                if (success) deletePendingCrash(app);
            } catch (Throwable t) {
                saveResult(app, "Crash report send failed");
            }
        }, "hcf-crash-report").start();
    }

    private static void sendManualFeedback(Context context, String note, boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                JSONObject report = buildBaseReport(app, "feedback", "manual_feedback", note, includeIdentity);
                if (LEVEL_DIAGNOSTICS.equals(level(app))) report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {}
        }, "hcf-manual-feedback").start();
    }

    private static JSONObject prepareReportForSend(Context context, JSONObject original, String note, boolean includeIdentity) throws Exception {
        JSONObject report = new JSONObject(original.toString());
        if (note != null && !note.trim().isEmpty()) report.put("userFeedback", safeDetail(note, 900));
        applyOptionalPrivacyFields(context, report, includeIdentity);
        return report;
    }

    private static JSONObject buildBaseReport(Context context, String type, String event, String detail, Boolean includeIdentityOverride) throws Exception {
        JSONObject report = new JSONObject();
        report.put("reportId", newReportId());
        report.put("type", safeToken(type, "event"));
        report.put("event", safeToken(event, "app_event"));
        report.put("timestampUtc", isoNow());
        report.put("appVersion", BuildInfo.VERSION);
        report.put("versionCode", BuildInfo.VERSION_CODE);
        report.put("internalBuild", BuildInfo.INTERNAL_BUILD);
        report.put("channel", BuildInfo.CHANNEL);
        report.put("androidApi", Build.VERSION.SDK_INT);
        report.put("orientation", orientation(context));
        if (detail != null && !detail.trim().isEmpty()) report.put("detail", safeDetail(detail, 900));

        ForumIdentity.Snapshot identity = ForumIdentity.load(context);
        report.put("identityMode", identity.loggedIn ? "SIGNED_IN" : "Guest_Protocol");
        String host = identity.host == null || identity.host.trim().isEmpty()
                ? safeString(prefs(context), AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST)
                : identity.host;
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        report.put("forumHost", host);
        applyOptionalPrivacyFields(context, report, includeIdentityOverride);
        return report;
    }

    private static void applyOptionalPrivacyFields(Context context, JSONObject report, Boolean includeIdentityOverride) throws Exception {
        SharedPreferences prefs = prefs(context);
        boolean includeDevice = safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL, false);
        boolean includeRoute = safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_ROUTE, false);
        boolean includeIdentity = includeIdentityOverride != null
                ? includeIdentityOverride
                : safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_IDENTITY, false);
        boolean includeEmail = includeIdentity && safeBoolean(prefs, AppPrefs.TELEMETRY_INCLUDE_EMAIL, false);

        if (includeDevice) {
            report.put("device", safeDetail(Build.MANUFACTURER + " " + Build.MODEL, 120));
        } else {
            report.remove("device");
        }
        if (includeRoute) {
            String route = safeString(prefs, AppPrefs.TELEMETRY_LAST_ROUTE, "");
            if (route != null && !route.isEmpty()) report.put("route", sanitizedRoute(route, includeIdentity));
        } else {
            report.remove("route");
        }

        if (includeIdentity) {
            ForumIdentity.Snapshot identity = ForumIdentity.load(context);
            if (identity.loggedIn) {
                JSONObject account = new JSONObject();
                account.put("displayName", safeDetail(identity.displayName, 120));
                account.put("username", safeDetail(identity.username, 120));
                if (!identity.groups.isEmpty()) account.put("groups", safeDetail(identity.groups, 240));
                if (includeEmail && !identity.email.isEmpty()) account.put("email", safeDetail(identity.email, 180));
                report.put("forumIdentity", account);
            }
        } else {
            report.remove("forumIdentity");
        }
    }

    private static boolean postReport(Context context, JSONObject report) {
        HttpsURLConnection connection = null;
        String reportId = report == null ? "HCF-REPORT" : report.optString("reportId", "HCF-REPORT");
        String type = report == null ? "event" : report.optString("type", "event");
        try {
            String endpoint = decryptWebhook(context);
            if (endpoint == null || !endpoint.startsWith("https://discord.com/api/webhooks/")) {
                saveResult(context, "Blocked: endpoint verification failed");
                addHistory(context, reportId, type, "blocked");
                return false;
            }

            JSONObject root = discordPayload(report);
            byte[] body = root.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(endpoint);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "HarleysClanForumTelemetry/" + BuildInfo.VERSION);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
                output.flush();
            }
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                saveResult(context, "Last send succeeded • " + reportId);
                addHistory(context, reportId, type, "sent");
                AppLogger.info(context, "telemetry_sent", reportId + " | " + type);
                return true;
            }
            saveResult(context, "Last send failed (HTTP " + code + ")");
            addHistory(context, reportId, type, "HTTP " + code);
            AppLogger.warn(context, "telemetry_http", reportId + " | " + code);
        } catch (Throwable t) {
            saveResult(context, "Last send failed");
            addHistory(context, reportId, type, "failed");
            AppLogger.warn(context, "telemetry_failed", reportId + " | " + t.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return false;
    }

    private static JSONObject discordPayload(JSONObject report) throws Exception {
        JSONObject root = new JSONObject();
        root.put("username", "HCF Diagnostics");
        root.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));

        String type = report.optString("type", "event");
        int color = "crash".equals(type) ? 0xE53935
                : (type.contains("error") ? 0xF0A000 : 0x00B8F0);
        JSONObject embed = new JSONObject();
        embed.put("title", ("crash".equals(type) ? "🔴 Crash" : "HCF Diagnostics") + " • " + report.optString("event", type));
        embed.put("color", color);
        String detail = report.optString("detail", "");
        if (!detail.isEmpty()) embed.put("description", safeDetail(detail, 900));

        JSONArray fields = new JSONArray();
        fields.put(field("Report ID", report.optString("reportId", "—"), true));
        fields.put(field("App", report.optString("appVersion", BuildInfo.VERSION) + " • " + report.optString("channel", BuildInfo.CHANNEL), true));
        fields.put(field("Build", report.optInt("internalBuild", BuildInfo.INTERNAL_BUILD) + " / " + report.optInt("versionCode", BuildInfo.VERSION_CODE), true));
        fields.put(field("Android", "API " + report.optInt("androidApi", Build.VERSION.SDK_INT), true));
        fields.put(field("Orientation", report.optString("orientation", "unknown"), true));
        fields.put(field("Identity mode", report.optString("identityMode", "Guest_Protocol"), true));
        fields.put(field("Forum host", report.optString("forumHost", "—"), false));
        if (report.has("device")) fields.put(field("Device", report.optString("device", "—"), false));
        if (report.has("route")) fields.put(field("Route", report.optString("route", "—"), false));
        if (report.has("forumIdentity")) {
            JSONObject identity = report.optJSONObject("forumIdentity");
            if (identity != null) {
                StringBuilder id = new StringBuilder();
                if (!identity.optString("displayName", "").isEmpty()) id.append(identity.optString("displayName")).append('\n');
                if (!identity.optString("username", "").isEmpty()) id.append('@').append(identity.optString("username")).append('\n');
                if (!identity.optString("groups", "").isEmpty()) id.append("Groups: ").append(identity.optString("groups")).append('\n');
                if (!identity.optString("email", "").isEmpty()) id.append("Email: ").append(identity.optString("email"));
                fields.put(field("Forum identity (user opted in)", safeDetail(id.toString(), 900), false));
            }
        }
        if (report.has("exception")) {
            fields.put(field("Exception", report.optString("exception", "—") + (report.optString("message", "").isEmpty() ? "" : "\n" + report.optString("message")), false));
        }
        if (report.has("userFeedback")) fields.put(field("User feedback", report.optString("userFeedback", "—"), false));
        if (report.has("stackTrace")) fields.put(field("Stack trace", "```\n" + safeDetail(report.optString("stackTrace", ""), 900) + "\n```", false));
        JSONArray crumbs = report.optJSONArray("breadcrumbs");
        if (crumbs != null && crumbs.length() > 0) fields.put(field("Recent app events", breadcrumbText(crumbs), false));
        embed.put("fields", fields);
        embed.put("footer", new JSONObject().put("text", "HCF opt-in diagnostics • sensitive credentials and forum content excluded"));
        embed.put("timestamp", report.optString("timestampUtc", isoNow()));
        root.put("embeds", new JSONArray().put(embed));
        return root;
    }

    private static JSONObject field(String name, String value, boolean inline) throws Exception {
        String v = value == null || value.trim().isEmpty() ? "—" : value.trim();
        if (v.length() > 1000) v = v.substring(0, 1000) + "…";
        return new JSONObject().put("name", name).put("value", v).put("inline", inline);
    }

    private static JSONArray breadcrumbs(Context context) {
        try { return new JSONArray(safeString(prefs(context), AppPrefs.TELEMETRY_BREADCRUMBS, "[]")); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    private static String breadcrumbText(JSONArray array) {
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, array.length() - 12);
        for (int i = start; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(item.optString("event", "event"));
            String detail = item.optString("detail", "");
            if (!detail.isEmpty()) out.append(" • ").append(detail);
            if (out.length() > 880) break;
        }
        return safeDetail(out.toString(), 900);
    }

    private static String sanitizeStack(Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String value = sw.toString().replace('\u0000', ' ').replace("\r", "");
            // Do not allow URLs with query strings or obvious token-style values into reports.
            value = value.replaceAll("https?://[^\\s?]+\\?[^\\s]+", "[URL_QUERY_REDACTED]");
            value = value.replaceAll("(?i)(token|authorization|cookie|password)=?[^\\s,;]+", "$1=[REDACTED]");
            if (value.length() > 6000) value = value.substring(0, 6000) + "…";
            return value;
        } catch (Throwable ignored) {
            return throwable.getClass().getName();
        }
    }

    private static String sanitizedRoute(String raw, boolean identityAllowed) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            android.net.Uri uri = android.net.Uri.parse(raw);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = raw.startsWith("/") ? raw : "/";
            if (!identityAllowed) {
                path = path.replaceAll("(?i)^/u/[^/]+", "/u/[user]");
            }
            path = path.replaceAll("(?i)^/d/(\\d+)[^/]*", "/d/$1");
            if (path.length() > 240) path = path.substring(0, 240);
            return path;
        } catch (Throwable ignored) {
            return "[route-unavailable]";
        }
    }

    private static String orientation(Context context) {
        try {
            int o = context.getResources().getConfiguration().orientation;
            if (o == Configuration.ORIENTATION_LANDSCAPE) return "landscape";
            if (o == Configuration.ORIENTATION_PORTRAIT) return "portrait";
        } catch (Throwable ignored) {}
        return "unknown";
    }

    private static String newReportId() {
        String day;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            day = f.format(new Date());
        } catch (Throwable ignored) { day = "00000000"; }
        byte[] random = new byte[3];
        new SecureRandom().nextBytes(random);
        StringBuilder hex = new StringBuilder();
        for (byte b : random) hex.append(String.format(Locale.US, "%02X", b & 0xff));
        return "HCF-" + day + "-" + hex;
    }

    private static void addHistory(Context context, String reportId, String type, String result) {
        try {
            SharedPreferences prefs = prefs(context);
            JSONArray current;
            try { current = new JSONArray(safeString(prefs, AppPrefs.TELEMETRY_REPORT_HISTORY, "[]")); }
            catch (Throwable ignored) { current = new JSONArray(); }
            JSONArray next = new JSONArray();
            int start = Math.max(0, current.length() - (MAX_HISTORY - 1));
            for (int i = start; i < current.length(); i++) next.put(current.opt(i));
            next.put(new JSONObject()
                    .put("time", isoNow())
                    .put("reportId", reportId)
                    .put("type", type)
                    .put("result", result));
            prefs.edit().putString(AppPrefs.TELEMETRY_REPORT_HISTORY, next.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private static String historyText(Context context) {
        try {
            JSONArray history = new JSONArray(safeString(prefs(context), AppPrefs.TELEMETRY_REPORT_HISTORY, "[]"));
            if (history.length() == 0) return "No telemetry reports have been sent or discarded on this device.";
            StringBuilder out = new StringBuilder();
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                out.append(item.optString("time", "")).append('\n')
                        .append(item.optString("reportId", "HCF-REPORT")).append(" • ")
                        .append(item.optString("type", "event")).append(" • ")
                        .append(item.optString("result", "unknown")).append("\n\n");
            }
            return out.toString().trim();
        } catch (Throwable t) {
            return "History unavailable.";
        }
    }

    private static JSONObject readPendingCrash(Context context) {
        try {
            File file = pendingCrashFile(context);
            if (!file.exists()) return null;
            return new JSONObject(readText(file));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void deletePendingCrash(Context context) {
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        try { prefs(context).edit().remove(AppPrefs.TELEMETRY_PENDING_CRASH_ID).apply(); } catch (Throwable ignored) {}
    }

    private static File pendingCrashFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "telemetry");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, PENDING_CRASH_FILE);
    }

    private static void writeText(File file, String value) throws Exception {
        FileOutputStream fos = new FileOutputStream(file, false);
        OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        writer.write(value == null ? "" : value);
        writer.flush();
        writer.close();
    }

    private static String readText(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static String decryptWebhook(Context context) throws Exception {
        String certHash = signingCertificateSha256(context);
        if (certHash.isEmpty()) throw new IllegalStateException("signing certificate unavailable");
        String material = "HCF_TELEMETRY_V1|" + context.getPackageName() + "|" + certHash + "|" + KEY_SALT;
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = Base64.decode(WEBHOOK_IV_B64, Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(WEBHOOK_CIPHERTEXT_B64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static String signingCertificateSha256(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            @SuppressWarnings("deprecation")
            Signature[] legacy = info.signatures;
            signatures = legacy;
        }
        if (signatures == null || signatures.length == 0) return "";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        return hex.toString();
    }

    private static String isoNow() {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.format(new Date());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeToken(String value, String fallback) {
        String v = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (v.isEmpty()) v = fallback;
        return v.length() > 64 ? v.substring(0, 64) : v;
    }

    private static String safeDetail(String value, int max) {
        if (value == null) return "";
        String v = value.replace('\u0000', ' ').replace("\r", "").trim();
        v = v.replaceAll("(?i)(authorization|cookie|password|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        if (v.length() > max) v = v.substring(0, max) + "…";
        return v;
    }

    private static void saveResult(Context context, String value) {
        try { prefs(context).edit().putString(AppPrefs.TELEMETRY_LAST_RESULT, value).apply(); }
        catch (Throwable ignored) {}
    }

    private static boolean safeBoolean(SharedPreferences prefs, String key, boolean fallback) {
        try { return prefs.getBoolean(key, fallback); }
        catch (Throwable ignored) {
            try { prefs.edit().remove(key).apply(); } catch (Throwable ignoredAgain) {}
            return fallback;
        }
    }

    private static String safeString(SharedPreferences prefs, String key, String fallback) {
        try {
            String value = prefs.getString(key, fallback);
            return value == null ? fallback : value;
        } catch (Throwable ignored) {
            try { prefs.edit().remove(key).apply(); } catch (Throwable ignoredAgain) {}
            return fallback;
        }
    }

    private static long safeLong(SharedPreferences prefs, String key, long fallback) {
        try { return prefs.getLong(key, fallback); }
        catch (Throwable ignored) {
            try { prefs.edit().remove(key).apply(); } catch (Throwable ignoredAgain) {}
            return fallback;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AppPrefs.FILE, Context.MODE_PRIVATE);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void showTextDialog(Activity activity, String title, String body) {
        TextView text = new TextView(activity);
        text.setText(body == null ? "" : body);
        text.setTextSize(12);
        text.setTextIsSelectable(true);
        text.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private TelemetryService() {}
}
