package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
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
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional, opt-in HCF telemetry and diagnostic reporting. */
final class TelemetryService {
    private static final String AAD = "HCF_TELEMETRY";
    private static final long HEARTBEAT_INTERVAL_MS = 21600000L;
    private static final int MAX_BREADCRUMBS = 25;
    private static final int MAX_HISTORY = 20;
    private static final String PENDING_CRASH_FILE = "pending-crash.json";
    private static final String WEBHOOK_CIPHERTEXT_B64 = "VwSJKWKfeSuD5qbdEnHBEyUmADOfG8Fz61tvPWfIaqjA3c20QXuN1hgob3q5Oe26CKskpawsGgAHQm0uWQCveFBGHJbo/Ux6GT400Emw1NDCMp7dGDrMhEqDMUElQpRD7mE6WyOPeEJRQzWYavxeceU6bo3aWvdfNy9UZuAy1UuNMU/cUIrDqfA=";
    private static final String WEBHOOK_IV_B64 = "uoO2HMaYrR1Qjq+6";
    static final String LEVEL_BASIC = "basic";
    static final String LEVEL_DIAGNOSTICS = "diagnostics";

    static boolean isEnabled(Context context) {
        return context != null && prefs(context).getBoolean("telemetry_enabled", false);
    }

    static String level(Context context) {
        if (context == null) return LEVEL_BASIC;
        return LEVEL_DIAGNOSTICS.equals(prefs(context).getString("telemetry_level", LEVEL_BASIC)) ? LEVEL_DIAGNOSTICS : LEVEL_BASIC;
    }

    static boolean isDiagnostics(Context context) {
        return isEnabled(context) && LEVEL_DIAGNOSTICS.equals(level(context));
    }

    static String levelLabel(Context context) {
        return LEVEL_DIAGNOSTICS.equals(level(context)) ? "Diagnostics" : "Basic";
    }

    static String status(Context context) {
        if (!isEnabled(context)) return "Telemetry: Off • no telemetry is sent";
        String result = prefs(context).getString("telemetry_last_result", "Ready");
        if (result == null || result.trim().isEmpty()) result = "Ready";
        return "Telemetry: On • " + levelLabel(context) + " • " + result.trim();
    }

    static void heartbeat(Context context) {
        if (!isEnabled(context)) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        long last = p.getLong("telemetry_last_heartbeat", 0L);
        if (last <= 0L || now - last >= HEARTBEAT_INTERVAL_MS) {
            p.edit().putLong("telemetry_last_heartbeat", now).apply();
            sendEvent(app, "app_heartbeat", "foreground");
        }
    }

    static void noteRoute(Context context, String route) {
        if (context == null) return;
        prefs(context).edit().putString("telemetry_last_route", sanitizedRoute(route, false)).apply();
    }

    static void recordBreadcrumb(Context context, String event, String detail) {
        if (!isDiagnostics(context)) return;
        try {
            SharedPreferences p = prefs(context);
            JSONArray old;
            try { old = new JSONArray(p.getString("telemetry_breadcrumbs", "[]")); }
            catch (Throwable ignored) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - (MAX_BREADCRUMBS - 1)); i < old.length(); i++) next.put(old.opt(i));
            JSONObject item = new JSONObject();
            item.put("time", isoNow());
            item.put("event", safeToken(event, "event"));
            item.put("detail", safeDetail(detail, 180));
            next.put(item);
            p.edit().putString("telemetry_breadcrumbs", next.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static void sendEvent(Context context, String event, String detail) {
        if (!isEnabled(context)) return;
        final Context app = context.getApplicationContext();
        final String safeEvent = safeToken(event, "app_event");
        final String safeText = safeDetail(detail, 240);
        AppExecutors.network().execute(() -> {
            try { postReport(app, buildBaseReport(app, "event", safeEvent, safeText, null)); }
            catch (Throwable error) { saveResult(app, "Last send failed"); }
        });
    }

    static void sendDiagnosticEvent(Context context, String event, String detail) {
        if (!isDiagnostics(context) || !prefs(context).getBoolean("telemetry_auto_error_reports", false)) return;
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                JSONObject report = buildBaseReport(app, "diagnostic_error", safeToken(event, "error"), safeDetail(detail, 420), null);
                report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {
            }
        });
    }

    static void sendTest(Context context) {
        if (!isEnabled(context)) return;
        if (LEVEL_DIAGNOSTICS.equals(level(context))) {
            final Context app = context.getApplicationContext();
            AppExecutors.network().execute(() -> {
                try {
                    JSONObject report = buildBaseReport(app, "diagnostic_test", "telemetry_test", "manual diagnostics test from App Settings", null);
                    report.put("breadcrumbs", breadcrumbs(app));
                    postReport(app, report);
                } catch (Throwable ignored) {
                }
            });
        } else {
            sendEvent(context, "telemetry_test", "manual basic telemetry test from App Settings");
        }
    }

    static void captureCrash(Context context, Thread thread, Throwable error) {
        if (context == null || error == null || !isDiagnostics(context)) return;
        try {
            JSONObject report = buildBaseReport(context, "crash", "uncaught_exception", "", false);
            report.put("thread", safeDetail(thread == null ? "unknown" : thread.getName(), 80));
            report.put("exception", error.getClass().getName());
            report.put("message", safeDetail(String.valueOf(error.getMessage()), 800));
            report.put("stackTrace", sanitizeStack(error));
            report.put("breadcrumbs", breadcrumbs(context));
            writeText(pendingCrashFile(context), report.toString());
            prefs(context).edit().putString("telemetry_pending_crash_id", report.optString("reportId", "")).apply();
        } catch (Throwable ignored) {
        }
    }

    static boolean hasPendingCrash(Context context) {
        return context != null && pendingCrashFile(context).exists();
    }

    static void handlePendingCrash(final Activity activity) {
        if (activity == null || activity.isFinishing() || !hasPendingCrash(activity) || !isDiagnostics(activity)) return;
        SharedPreferences p = prefs(activity);
        if (p.getBoolean("telemetry_auto_crash_reports", false) && !p.getBoolean("telemetry_ask_before_crash_report", true)) {
            sendPendingCrash(activity, "", p.getBoolean("telemetry_include_identity", false));
            return;
        }
        JSONObject pending = readPendingCrash(activity);
        if (pending == null) return;
        final String reportId = pending.optString("reportId", "HCF-REPORT");
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView intro = new TextView(activity);
        intro.setText("Harley's Clan Forum recovered from a problem.\nReport ID: " + reportId);
        intro.setTextSize(14.0f);
        content.addView(intro);
        final EditText notes = new EditText(activity);
        notes.setHint("What were you doing when this happened? (optional)");
        notes.setMinLines(2);
        notes.setMaxLines(5);
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(-1, -2);
        notesLp.topMargin = dp(activity, 10);
        content.addView(notes, notesLp);
        final Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(p.getBoolean("telemetry_include_identity", false));
        content.addView(identity);
        TextView privacy = new TextView(activity);
        privacy.setText("Passwords, cookies, access tokens, recovery codes, posts and messages are never included.");
        privacy.setTextSize(11.0f);
        content.addView(privacy);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Send crash report?")
                .setView(content)
                .setPositiveButton("Send Report", null)
                .setNeutralButton("Preview", null)
                .setNegativeButton("Don't Send", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                sendPendingCrash(activity, notes.getText().toString(), identity.isChecked());
                Toast.makeText(activity, "Crash report queued: " + reportId, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showTextDialog(activity, "Crash report preview", previewPendingReport(activity, notes.getText().toString(), identity.isChecked())));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                deletePendingCrash(activity);
                addHistory(activity, reportId, "crash", "discarded");
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    static void showManualFeedbackDialog(final Activity activity) {
        if (activity == null) return;
        if (!isEnabled(activity)) {
            Toast.makeText(activity, "Enable Telemetry Services first.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad / 2, pad, 0);
        final EditText details = new EditText(activity);
        details.setHint("Describe the problem or feedback");
        details.setMinLines(3);
        details.setMaxLines(7);
        content.addView(details);
        final Switch identity = new Switch(activity);
        identity.setText("Include my forum identity with this report");
        identity.setChecked(prefs(activity).getBoolean("telemetry_include_identity", false));
        content.addView(identity);
        new AlertDialog.Builder(activity).setTitle("Send diagnostic feedback").setView(content)
                .setPositiveButton("Send", (dialog, which) -> sendManualFeedback(activity, safeDetail(details.getText().toString(), 900), identity.isChecked()))
                .setNegativeButton("Cancel", null).show();
    }

    static void showPreview(Activity activity) {
        if (activity != null) showTextDialog(activity, "Telemetry report preview", previewReport(activity));
    }

    static void showHistory(Activity activity) {
        if (activity != null) showTextDialog(activity, "Telemetry report history", historyText(activity));
    }

    static void clearLocalReports(Context context) {
        if (context == null) return;
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        prefs(context).edit().remove("telemetry_report_history").remove("telemetry_pending_crash_id").remove("telemetry_breadcrumbs").apply();
    }

    static String previewReport(Context context) {
        try {
            JSONObject report = buildBaseReport(context, "preview", "settings_preview", "Example of the data this app would send with the current telemetry settings.", null);
            if (LEVEL_DIAGNOSTICS.equals(level(context))) report.put("breadcrumbs", breadcrumbs(context));
            return report.toString(2);
        } catch (Throwable error) {
            return "Preview unavailable: " + error.getClass().getSimpleName();
        }
    }

    private static void sendPendingCrash(Context context, final String notes, final boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        final JSONObject pending = readPendingCrash(app);
        if (pending == null) return;
        AppExecutors.network().execute(() -> {
            try {
                if (postReport(app, prepareReportForSend(app, pending, notes, includeIdentity))) deletePendingCrash(app);
            } catch (Throwable error) {
                saveResult(app, "Crash report send failed");
            }
        });
    }

    private static String previewPendingReport(Context context, String notes, boolean includeIdentity) {
        try {
            JSONObject pending = readPendingCrash(context);
            return pending == null ? "No pending crash report." : prepareReportForSend(context, pending, notes, includeIdentity).toString(2);
        } catch (Throwable error) {
            return "Preview unavailable: " + error.getClass().getSimpleName();
        }
    }

    private static void sendManualFeedback(Context context, final String details, final boolean includeIdentity) {
        final Context app = context.getApplicationContext();
        AppExecutors.network().execute(() -> {
            try {
                JSONObject report = buildBaseReport(app, "feedback", "manual_feedback", details, includeIdentity);
                if (LEVEL_DIAGNOSTICS.equals(level(app))) report.put("breadcrumbs", breadcrumbs(app));
                postReport(app, report);
            } catch (Throwable ignored) {
            }
        });
        Toast.makeText(context, "Diagnostic feedback queued.", Toast.LENGTH_SHORT).show();
    }

    private static JSONObject prepareReportForSend(Context context, JSONObject original, String notes, boolean includeIdentity) throws Exception {
        JSONObject report = new JSONObject(original.toString());
        if (notes != null && !notes.trim().isEmpty()) report.put("userFeedback", safeDetail(notes, 900));
        applyOptionalPrivacyFields(context, report, includeIdentity);
        return report;
    }

    private static JSONObject buildBaseReport(Context context, String type, String event, String detail, Boolean identityOverride) throws Exception {
        JSONObject report = new JSONObject();
        report.put("reportId", newReportId());
        report.put("type", safeToken(type, "event"));
        report.put("event", safeToken(event, "app_event"));
        report.put("timestampUtc", isoNow());
        report.put("appVersion", BuildInfo.VERSION);
        report.put("versionCode", BuildInfo.VERSION_CODE);
        report.put("internalBuild", BuildInfo.INTERNAL_BUILD);
        report.put("channel", BuildInfo.CHANNEL);
        report.put("package", context == null ? "" : context.getPackageName());
        report.put("androidApi", Build.VERSION.SDK_INT);
        report.put("orientation", orientation(context));
        if (detail != null && !detail.trim().isEmpty()) report.put("detail", safeDetail(detail, 900));
        ForumIdentity.Snapshot identity = ForumIdentity.load(context);
        report.put("identityMode", identity.loggedIn ? "SIGNED_IN" : "Guest_Protocol");
        String host = identity.host == null || identity.host.trim().isEmpty()
                ? prefs(context).getString("active_host", "forum.harleytg.com") : identity.host;
        report.put("forumHost", ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com");
        applyOptionalPrivacyFields(context, report, identityOverride);
        return report;
    }

    private static void applyOptionalPrivacyFields(Context context, JSONObject report, Boolean identityOverride) throws Exception {
        SharedPreferences p = prefs(context);
        boolean includeDevice = p.getBoolean("telemetry_include_device_model", false);
        boolean includeRoute = p.getBoolean("telemetry_include_route", false);
        boolean includeIdentity = identityOverride != null ? identityOverride.booleanValue() : p.getBoolean("telemetry_include_identity", false);
        boolean includeEmail = includeIdentity && p.getBoolean("telemetry_include_email", false);
        if (includeDevice) report.put("device", safeDetail(Build.MANUFACTURER + " " + Build.MODEL, 120)); else report.remove("device");
        if (includeRoute) {
            String route = p.getString("telemetry_last_route", "");
            if (route != null && !route.isEmpty()) report.put("route", sanitizedRoute(route, includeIdentity));
        } else report.remove("route");
        if (includeIdentity) {
            ForumIdentity.Snapshot identity = ForumIdentity.load(context);
            if (identity.loggedIn) {
                JSONObject forumIdentity = new JSONObject();
                forumIdentity.put("displayName", safeDetail(identity.displayName, 120));
                forumIdentity.put("username", safeDetail(identity.username, 120));
                if (!identity.groups.isEmpty()) forumIdentity.put("groups", safeDetail(identity.groups, 240));
                if (includeEmail && !identity.email.isEmpty()) forumIdentity.put("email", safeDetail(identity.email, 180));
                report.put("forumIdentity", forumIdentity);
            }
        } else report.remove("forumIdentity");
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
            byte[] body = discordPayload(report).toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpsURLConnection) new URL(endpoint).openConnection();
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
        } catch (Throwable error) {
            saveResult(context, "Last send failed");
            addHistory(context, reportId, type, "failed");
            AppLogger.warn(context, "telemetry_failed", reportId + " | " + error.getClass().getSimpleName());
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
        int color = "crash".equals(type) ? 15022389 : (type.contains("error") ? 15769600 : 47344);
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
        JSONObject forumIdentity = report.optJSONObject("forumIdentity");
        if (forumIdentity != null) {
            StringBuilder identity = new StringBuilder();
            if (!forumIdentity.optString("displayName", "").isEmpty()) identity.append(forumIdentity.optString("displayName")).append('\n');
            if (!forumIdentity.optString("username", "").isEmpty()) identity.append('@').append(forumIdentity.optString("username")).append('\n');
            if (!forumIdentity.optString("groups", "").isEmpty()) identity.append("Groups: ").append(forumIdentity.optString("groups")).append('\n');
            if (!forumIdentity.optString("email", "").isEmpty()) identity.append("Email: ").append(forumIdentity.optString("email"));
            fields.put(field("Forum identity (user opted in)", safeDetail(identity.toString(), 900), false));
        }
        if (report.has("exception")) fields.put(field("Exception", report.optString("exception", "—") + (report.optString("message", "").isEmpty() ? "" : "\n" + report.optString("message")), false));
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
        String safe = value == null || value.trim().isEmpty() ? "—" : value.trim();
        if (safe.length() > 1000) safe = safe.substring(0, 1000) + "…";
        return new JSONObject().put("name", name).put("value", safe).put("inline", inline);
    }

    private static JSONArray breadcrumbs(Context context) {
        try { return new JSONArray(prefs(context).getString("telemetry_breadcrumbs", "[]")); }
        catch (Throwable ignored) { return new JSONArray(); }
    }

    private static String breadcrumbText(JSONArray items) {
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, items.length() - 12); i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(item.optString("event", "event"));
            String detail = item.optString("detail", "");
            if (!detail.isEmpty()) out.append(" • ").append(detail);
            if (out.length() > 880) break;
        }
        return safeDetail(out.toString(), 900);
    }

    private static String sanitizeStack(Throwable error) {
        try {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            String safe = writer.toString().replace((char) 0, ' ').replace("\r", "")
                    .replaceAll("https?://[^\\s?]+\\?[^\\s]+", "[URL_QUERY_REDACTED]")
                    .replaceAll("(?i)(token|authorization|cookie|password)=?[^\\s,;]+", "$1=[REDACTED]");
            return safe.length() <= 6000 ? safe : safe.substring(0, 6000) + "…";
        } catch (Throwable ignored) {
            return error.getClass().getName();
        }
    }

    private static String sanitizedRoute(String value, boolean identityAllowed) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String path = Uri.parse(value).getPath();
            if (path == null || path.isEmpty()) path = value.startsWith("/") ? value : "/";
            if (!identityAllowed) path = path.replaceAll("(?i)^/u/[^/]+", "/u/[user]");
            path = path.replaceAll("(?i)^/d/(\\d+)[^/]*", "/d/$1");
            return path.length() > 240 ? path.substring(0, 240) : path;
        } catch (Throwable ignored) {
            return "[route-unavailable]";
        }
    }

    private static String orientation(Context context) {
        try {
            int orientation = context.getResources().getConfiguration().orientation;
            return orientation == 2 ? "landscape" : (orientation == 1 ? "portrait" : "unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String newReportId() {
        String date;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            date = format.format(new Date());
        } catch (Throwable ignored) {
            date = "00000000";
        }
        byte[] random = new byte[3];
        new SecureRandom().nextBytes(random);
        StringBuilder suffix = new StringBuilder();
        for (byte b : random) suffix.append(String.format(Locale.US, "%02X", b & 255));
        return "HCF-" + date + "-" + suffix;
    }

    private static void addHistory(Context context, String reportId, String type, String result) {
        try {
            SharedPreferences p = prefs(context);
            JSONArray old;
            try { old = new JSONArray(p.getString("telemetry_report_history", "[]")); }
            catch (Throwable ignored) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - (MAX_HISTORY - 1)); i < old.length(); i++) next.put(old.opt(i));
            next.put(new JSONObject().put("time", isoNow()).put("reportId", reportId).put("type", type).put("result", result));
            p.edit().putString("telemetry_report_history", next.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String historyText(Context context) {
        try {
            JSONArray history = new JSONArray(prefs(context).getString("telemetry_report_history", "[]"));
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
        } catch (Throwable ignored) {
            return "History unavailable.";
        }
    }

    private static JSONObject readPendingCrash(Context context) {
        try {
            File file = pendingCrashFile(context);
            return file.exists() ? new JSONObject(readText(file)) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void deletePendingCrash(Context context) {
        try { pendingCrashFile(context).delete(); } catch (Throwable ignored) {}
        try { prefs(context).edit().remove("telemetry_pending_crash_id").apply(); } catch (Throwable ignored) {}
    }

    private static File pendingCrashFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "telemetry");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, PENDING_CRASH_FILE);
    }

    private static void writeText(File file, String value) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(value == null ? "" : value);
            writer.flush();
        }
    }

    private static String readText(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }

    private static String decryptWebhook(Context context) throws Exception {
        String fingerprint = signingCertificateSha256(context);
        if (fingerprint.isEmpty()) throw new IllegalStateException("signing certificate unavailable");
        byte[] key = MessageDigest.getInstance("SHA-256").digest(("HCF_TELEMETRY_V1|" + context.getPackageName() + "|" + fingerprint + "|HCF-Telemetry-Discord-Relay-v1").getBytes(StandardCharsets.UTF_8));
        SecretKeySpec secret = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, Base64.decode(WEBHOOK_IV_B64, Base64.NO_WRAP)));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(WEBHOOK_CIPHERTEXT_B64, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private static String signingCertificateSha256(Context context) throws Exception {
        PackageManager manager = context.getPackageManager();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            signatures = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }

    private static String isoNow() {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(new Date());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeToken(String value, String fallback) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) safe = fallback;
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    private static String safeDetail(String value, int max) {
        if (value == null) return "";
        String safe = value.replace((char) 0, ' ').replace("\r", "").trim()
                .replaceAll("(?i)(authorization|cookie|password|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }

    private static void saveResult(Context context, String value) {
        try { prefs(context).edit().putString("telemetry_last_result", value).apply(); } catch (Throwable ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("hcf_app", 0);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void showTextDialog(Activity activity, String title, String body) {
        TextView text = new TextView(activity);
        text.setText(body == null ? "" : body);
        text.setTextSize(12.0f);
        text.setTextIsSelectable(true);
        text.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private TelemetryService() {}
}
