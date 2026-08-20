package com.harleytg.forum;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.harleytg.forum.ForumIdentity;
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
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class TelemetryService {
    private static final String AAD = "HCF_TELEMETRY";
    private static final long HEARTBEAT_INTERVAL_MS = 21600000;
    private static final String KEY_SALT = "HCF-Telemetry-Discord-Relay-v1";
    static final String LEVEL_BASIC = "basic";
    static final String LEVEL_DIAGNOSTICS = "diagnostics";
    private static final int MAX_BREADCRUMBS = 25;
    private static final int MAX_HISTORY = 20;
    private static final String PENDING_CRASH_FILE = "pending-crash.json";
    private static final String WEBHOOK_CIPHERTEXT_B64 = "VwSJKWKfeSuD5qbdEnHBEyUmADOfG8Fz61tvPWfIaqjA3c20QXuN1hgob3q5Oe26CKskpawsGgAHQm0uWQCveFBGHJbo/Ux6GT400Emw1NDCMp7dGDrMhEqDMUElQpRD7mE6WyOPeEJRQzWYavxeceU6bo3aWvdfNy9UZuAy1UuNMU/cUIrDqfA=";
    private static final String WEBHOOK_IV_B64 = "uoO2HMaYrR1Qjq+6";

    static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return safeBoolean(prefs(context), "telemetry_enabled", false);
    }

    static String level(Context context) {
        return LEVEL_DIAGNOSTICS.equals(safeString(prefs(context), "telemetry_level", LEVEL_BASIC)) ? LEVEL_DIAGNOSTICS : LEVEL_BASIC;
    }

    static boolean isDiagnostics(Context context) {
        return isEnabled(context) && LEVEL_DIAGNOSTICS.equals(level(context));
    }

    static String levelLabel(Context context) {
        return LEVEL_DIAGNOSTICS.equals(level(context)) ? "Diagnostics" : "Basic";
    }

    static String status(Context context) {
        if (!isEnabled(context)) {
            return "Telemetry: Off • no telemetry is sent";
        }
        String str = "Ready";
        String safeString = safeString(prefs(context), "telemetry_last_result", "Ready");
        StringBuilder sb = new StringBuilder("Telemetry: On • ");
        sb.append(levelLabel(context));
        sb.append(" • ");
        if (safeString != null && !safeString.trim().isEmpty()) {
            str = safeString.trim();
        }
        sb.append(str);
        return sb.toString();
    }

    static void heartbeat(Context context) {
        if (context == null || !isEnabled(context)) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences prefs = prefs(applicationContext);
        long currentTimeMillis = System.currentTimeMillis();
        long safeLong = safeLong(prefs, "telemetry_last_heartbeat", 0L);
        if (safeLong <= 0 || currentTimeMillis - safeLong >= HEARTBEAT_INTERVAL_MS) {
            prefs.edit().putLong("telemetry_last_heartbeat", currentTimeMillis).apply();
            sendEvent(applicationContext, "app_heartbeat", "foreground");
        }
    }

    static void noteRoute(Context context, String str) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putString("telemetry_last_route", sanitizedRoute(str, false)).apply();
    }

    static void recordBreadcrumb(Context context, String str, String str2) {
        JSONArray jSONArray;
        if (context == null || !isDiagnostics(context)) {
            return;
        }
        try {
            SharedPreferences prefs = prefs(context);
            try {
                jSONArray = new JSONArray(safeString(prefs, "telemetry_breadcrumbs", "[]"));
            } catch (Throwable unused) {
                jSONArray = new JSONArray();
            }
            JSONArray jSONArray2 = new JSONArray();
            for (int max = Math.max(0, jSONArray.length() - 24); max < jSONArray.length(); max++) {
                jSONArray2.put(jSONArray.opt(max));
            }
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("time", isoNow());
            jSONObject.put("event", safeToken(str, "event"));
            jSONObject.put("detail", safeDetail(str2, 180));
            jSONArray2.put(jSONObject);
            prefs.edit().putString("telemetry_breadcrumbs", jSONArray2.toString()).apply();
        } catch (Throwable unused2) {
        }
    }

    static void sendEvent(Context context, String str, String str2) {
        if (context == null || !isEnabled(context)) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        final String safeToken = safeToken(str, "app_event");
        final String safeDetail = safeDetail(str2, 240);
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                TelemetryService.lambda$sendEvent$0(applicationContext, safeToken, safeDetail);
            }
        });
    }

    static /* synthetic */ void lambda$sendEvent$0(Context context, String str, String str2) {
        try {
            postReport(context, buildBaseReport(context, "event", str, str2, null));
        } catch (Throwable unused) {
            saveResult(context, "Last send failed");
        }
    }

    static void sendDiagnosticEvent(Context context, final String str, final String str2) {
        if (context != null && isDiagnostics(context) && safeBoolean(prefs(context), "telemetry_auto_error_reports", false)) {
            final Context applicationContext = context.getApplicationContext();
            AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda8
                @Override // java.lang.Runnable
                public final void run() {
                    TelemetryService.lambda$sendDiagnosticEvent$1(applicationContext, str, str2);
                }
            });
        }
    }

    static /* synthetic */ void lambda$sendDiagnosticEvent$1(Context context, String str, String str2) {
        try {
            JSONObject buildBaseReport = buildBaseReport(context, "diagnostic_error", safeToken(str, "error"), safeDetail(str2, 420), null);
            buildBaseReport.put("breadcrumbs", breadcrumbs(context));
            postReport(context, buildBaseReport);
        } catch (Throwable unused) {
        }
    }

    static void sendTest(Context context) {
        if (LEVEL_DIAGNOSTICS.equals(level(context))) {
            sendDiagnosticEventForced(context, "telemetry_test", "manual diagnostics test from App Settings");
        } else {
            sendEvent(context, "telemetry_test", "manual basic telemetry test from App Settings");
        }
    }

    private static void sendDiagnosticEventForced(Context context, final String str, final String str2) {
        if (context == null || !isEnabled(context)) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                TelemetryService.lambda$sendDiagnosticEventForced$2(applicationContext, str, str2);
            }
        });
    }

    static /* synthetic */ void lambda$sendDiagnosticEventForced$2(Context context, String str, String str2) {
        try {
            JSONObject buildBaseReport = buildBaseReport(context, "diagnostic_test", safeToken(str, "diagnostic_test"), safeDetail(str2, 420), null);
            buildBaseReport.put("breadcrumbs", breadcrumbs(context));
            postReport(context, buildBaseReport);
        } catch (Throwable unused) {
        }
    }

    static void captureCrash(Context context, Thread thread, Throwable th) {
        if (context == null || th == null || !isDiagnostics(context)) {
            return;
        }
        try {
            JSONObject buildBaseReport = buildBaseReport(context, "crash", "uncaught_exception", "", false);
            buildBaseReport.put("thread", safeDetail(thread == null ? "unknown" : thread.getName(), 80));
            buildBaseReport.put("exception", th.getClass().getName());
            buildBaseReport.put("message", safeDetail(String.valueOf(th.getMessage()), 800));
            buildBaseReport.put("stackTrace", sanitizeStack(th));
            buildBaseReport.put("breadcrumbs", breadcrumbs(context));
            writeText(pendingCrashFile(context), buildBaseReport.toString());
            prefs(context).edit().putString("telemetry_pending_crash_id", buildBaseReport.optString("reportId", "")).apply();
        } catch (Throwable unused) {
        }
    }

    static boolean hasPendingCrash(Context context) {
        return context != null && pendingCrashFile(context).exists();
    }

    static void handlePendingCrash(final Activity activity) {
        if (activity == null || activity.isFinishing() || !hasPendingCrash(activity) || !isDiagnostics(activity)) {
            return;
        }
        SharedPreferences prefs = prefs(activity);
        boolean safeBoolean = safeBoolean(prefs, "telemetry_auto_crash_reports", false);
        boolean safeBoolean2 = safeBoolean(prefs, "telemetry_ask_before_crash_report", true);
        if (safeBoolean && !safeBoolean2) {
            sendPendingCrash(activity, "", safeBoolean(prefs, "telemetry_include_identity", false));
            return;
        }
        JSONObject readPendingCrash = readPendingCrash(activity);
        if (readPendingCrash == null) {
            return;
        }
        final String optString = readPendingCrash.optString("reportId", "HCF-REPORT");
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        int dp = dp(activity, 16);
        linearLayout.setPadding(dp, dp / 2, dp, 0);
        TextView textView = new TextView(activity);
        textView.setText("Harley's Clan Forum recovered from a problem.\nReport ID: " + optString);
        textView.setTextSize(14.0f);
        linearLayout.addView(textView);
        final EditText editText = new EditText(activity);
        editText.setHint("What were you doing when this happened? (optional)");
        editText.setInputType(147457);
        editText.setMinLines(2);
        editText.setMaxLines(5);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(activity, 10);
        linearLayout.addView(editText, layoutParams);
        final Switch r11 = new Switch(activity);
        r11.setText("Include my forum identity with this report");
        r11.setChecked(safeBoolean(prefs, "telemetry_include_identity", false));
        linearLayout.addView(r11);
        TextView textView2 = new TextView(activity);
        textView2.setText("Passwords, cookies, access tokens, recovery codes, posts and messages are never included.");
        textView2.setTextSize(11.0f);
        linearLayout.addView(textView2);
        final AlertDialog create = new AlertDialog.Builder(activity).setTitle("Send crash report?").setView(linearLayout).setPositiveButton("Send Report", (DialogInterface.OnClickListener) null).setNeutralButton("Preview", (DialogInterface.OnClickListener) null).setNegativeButton("Don't Send", (DialogInterface.OnClickListener) null).create();
        create.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                TelemetryService.lambda$handlePendingCrash$6(create, activity, editText, r11, optString, dialogInterface);
            }
        });
        create.show();
    }

    static /* synthetic */ void lambda$handlePendingCrash$6(final AlertDialog alertDialog, final Activity activity, final EditText editText, final Switch r10, final String str, DialogInterface dialogInterface) {
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                TelemetryService.lambda$handlePendingCrash$3(activity, editText, r10, str, alertDialog, view);
            }
        });
        alertDialog.getButton(-3).setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                TelemetryService.showTextDialog(activity, "Crash report preview", TelemetryService.previewPendingReport(activity, editText.getText().toString(), r10.isChecked()));
            }
        });
        alertDialog.getButton(-2).setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                TelemetryService.lambda$handlePendingCrash$5(activity, str, alertDialog, view);
            }
        });
    }

    static /* synthetic */ void lambda$handlePendingCrash$3(Activity activity, EditText editText, Switch r2, String str, AlertDialog alertDialog, View view) {
        sendPendingCrash(activity, editText.getText().toString(), r2.isChecked());
        Toast.makeText(activity, "Crash report queued: " + str, 1).show();
        alertDialog.dismiss();
    }

    static /* synthetic */ void lambda$handlePendingCrash$5(Activity activity, String str, AlertDialog alertDialog, View view) {
        deletePendingCrash(activity);
        addHistory(activity, str, "crash", "discarded");
        alertDialog.dismiss();
    }

    static void showManualFeedbackDialog(final Activity activity) {
        if (activity == null) {
            return;
        }
        if (!isEnabled(activity)) {
            Toast.makeText(activity, "Enable Telemetry Services first.", 0).show();
            return;
        }
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        int dp = dp(activity, 16);
        linearLayout.setPadding(dp, dp / 2, dp, 0);
        final EditText editText = new EditText(activity);
        editText.setHint("Describe the problem or feedback");
        editText.setMinLines(3);
        editText.setMaxLines(7);
        editText.setInputType(147457);
        linearLayout.addView(editText);
        final Switch r3 = new Switch(activity);
        r3.setText("Include my forum identity with this report");
        r3.setChecked(prefs(activity).getBoolean("telemetry_include_identity", false));
        linearLayout.addView(r3);
        new AlertDialog.Builder(activity).setTitle("Send diagnostic feedback").setView(linearLayout).setPositiveButton("Send", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                TelemetryService.lambda$showManualFeedbackDialog$7(editText, activity, r3, dialogInterface, i);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    static /* synthetic */ void lambda$showManualFeedbackDialog$7(EditText editText, Activity activity, Switch r2, DialogInterface dialogInterface, int i) {
        sendManualFeedback(activity, safeDetail(editText.getText().toString(), 900), r2.isChecked());
        Toast.makeText(activity, "Diagnostic feedback queued.", 0).show();
    }

    static void showPreview(Activity activity) {
        if (activity == null) {
            return;
        }
        showTextDialog(activity, "Telemetry report preview", previewReport(activity));
    }

    static void showHistory(Activity activity) {
        if (activity == null) {
            return;
        }
        showTextDialog(activity, "Telemetry report history", historyText(activity));
    }

    static void clearLocalReports(Context context) {
        if (context == null) {
            return;
        }
        try {
            pendingCrashFile(context).delete();
        } catch (Throwable unused) {
        }
        prefs(context).edit().remove("telemetry_report_history").remove("telemetry_pending_crash_id").remove("telemetry_breadcrumbs").apply();
    }

    static String previewReport(Context context) {
        try {
            JSONObject buildBaseReport = buildBaseReport(context, "preview", "settings_preview", "Example of the data this app would send with the current telemetry settings.", null);
            if (LEVEL_DIAGNOSTICS.equals(level(context))) {
                buildBaseReport.put("breadcrumbs", breadcrumbs(context));
            }
            return buildBaseReport.toString(2);
        } catch (Throwable th) {
            return "Preview unavailable: " + th.getClass().getSimpleName();
        }
    }

    private static String previewPendingReport(Context context, String str, boolean z) {
        try {
            JSONObject readPendingCrash = readPendingCrash(context);
            if (readPendingCrash == null) {
                return "No pending crash report.";
            }
            return prepareReportForSend(context, readPendingCrash, str, z).toString(2);
        } catch (Throwable th) {
            return "Preview unavailable: " + th.getClass().getSimpleName();
        }
    }

    private static void sendPendingCrash(Context context, final String str, final boolean z) {
        final Context applicationContext = context.getApplicationContext();
        final JSONObject readPendingCrash = readPendingCrash(applicationContext);
        if (readPendingCrash == null) {
            return;
        }
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                TelemetryService.lambda$sendPendingCrash$8(applicationContext, readPendingCrash, str, z);
            }
        });
    }

    static /* synthetic */ void lambda$sendPendingCrash$8(Context context, JSONObject jSONObject, String str, boolean z) {
        try {
            if (postReport(context, prepareReportForSend(context, jSONObject, str, z))) {
                deletePendingCrash(context);
            }
        } catch (Throwable unused) {
            saveResult(context, "Crash report send failed");
        }
    }

    private static void sendManualFeedback(Context context, final String str, final boolean z) {
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.TelemetryService$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                TelemetryService.lambda$sendManualFeedback$9(applicationContext, str, z);
            }
        });
    }

    static /* synthetic */ void lambda$sendManualFeedback$9(Context context, String str, boolean z) {
        try {
            JSONObject buildBaseReport = buildBaseReport(context, "feedback", "manual_feedback", str, Boolean.valueOf(z));
            if (LEVEL_DIAGNOSTICS.equals(level(context))) {
                buildBaseReport.put("breadcrumbs", breadcrumbs(context));
            }
            postReport(context, buildBaseReport);
        } catch (Throwable unused) {
        }
    }

    private static JSONObject prepareReportForSend(Context context, JSONObject jSONObject, String str, boolean z) throws Exception {
        JSONObject jSONObject2 = new JSONObject(jSONObject.toString());
        if (str != null && !str.trim().isEmpty()) {
            jSONObject2.put("userFeedback", safeDetail(str, 900));
        }
        applyOptionalPrivacyFields(context, jSONObject2, Boolean.valueOf(z));
        return jSONObject2;
    }

    private static JSONObject buildBaseReport(Context context, String str, String str2, String str3, Boolean bool) throws Exception {
        String safeString;
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("reportId", newReportId());
        jSONObject.put("type", safeToken(str, "event"));
        jSONObject.put("event", safeToken(str2, "app_event"));
        jSONObject.put("timestampUtc", isoNow());
        jSONObject.put("appVersion", "1.0");
        jSONObject.put("versionCode", 10000072);
        jSONObject.put("internalBuild", 100);
        jSONObject.put("channel", "Dev");
        jSONObject.put("androidApi", Build.VERSION.SDK_INT);
        jSONObject.put("orientation", orientation(context));
        if (str3 != null && !str3.trim().isEmpty()) {
            jSONObject.put("detail", safeDetail(str3, 900));
        }
        ForumIdentity.Snapshot load = ForumIdentity.load(context);
        jSONObject.put("identityMode", load.loggedIn ? "SIGNED_IN" : "Guest_Protocol");
        if (load.host == null || load.host.trim().isEmpty()) {
            safeString = safeString(prefs(context), "active_host", "forum.harleytg.com");
        } else {
            safeString = load.host;
        }
        jSONObject.put("forumHost", ForumUrlRouter.isForumHost(safeString) ? safeString : "forum.harleytg.com");
        applyOptionalPrivacyFields(context, jSONObject, bool);
        return jSONObject;
    }

    private static void applyOptionalPrivacyFields(Context context, JSONObject jSONObject, Boolean bool) throws Exception {
        boolean safeBoolean;
        SharedPreferences prefs = prefs(context);
        boolean z = false;
        boolean safeBoolean2 = safeBoolean(prefs, "telemetry_include_device_model", false);
        boolean safeBoolean3 = safeBoolean(prefs, "telemetry_include_route", false);
        if (bool != null) {
            safeBoolean = bool.booleanValue();
        } else {
            safeBoolean = safeBoolean(prefs, "telemetry_include_identity", false);
        }
        if (safeBoolean && safeBoolean(prefs, "telemetry_include_email", false)) {
            z = true;
        }
        if (safeBoolean2) {
            jSONObject.put("device", safeDetail(Build.MANUFACTURER + " " + Build.MODEL, 120));
        } else {
            jSONObject.remove("device");
        }
        if (safeBoolean3) {
            String safeString = safeString(prefs, "telemetry_last_route", "");
            if (safeString != null && !safeString.isEmpty()) {
                jSONObject.put("route", sanitizedRoute(safeString, safeBoolean));
            }
        } else {
            jSONObject.remove("route");
        }
        if (safeBoolean) {
            ForumIdentity.Snapshot load = ForumIdentity.load(context);
            if (load.loggedIn) {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("displayName", safeDetail(load.displayName, 120));
                jSONObject2.put("username", safeDetail(load.username, 120));
                if (!load.groups.isEmpty()) {
                    jSONObject2.put("groups", safeDetail(load.groups, 240));
                }
                if (z && !load.email.isEmpty()) {
                    jSONObject2.put("email", safeDetail(load.email, 180));
                }
                jSONObject.put("forumIdentity", jSONObject2);
                return;
            }
            return;
        }
        jSONObject.remove("forumIdentity");
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
        } catch (Throwable t) {
            saveResult(context, "Last send failed");
            addHistory(context, reportId, type, "failed");
            AppLogger.warn(context, "telemetry_failed", reportId + " | " + t.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return false;
    }

    private static JSONObject discordPayload(JSONObject jSONObject) throws Exception {
        int i;
        String str;
        JSONObject optJSONObject;
        JSONObject jSONObject2 = new JSONObject();
        jSONObject2.put("username", "HCF Diagnostics");
        jSONObject2.put("allowed_mentions", new JSONObject().put("parse", new JSONArray()));
        String optString = jSONObject.optString("type", "event");
        if ("crash".equals(optString)) {
            i = 15022389;
        } else {
            i = optString.contains("error") ? 15769600 : 47344;
        }
        JSONObject jSONObject3 = new JSONObject();
        StringBuilder sb = new StringBuilder();
        sb.append("crash".equals(optString) ? "🔴 Crash" : "HCF Diagnostics");
        sb.append(" • ");
        sb.append(jSONObject.optString("event", optString));
        jSONObject3.put("title", sb.toString());
        jSONObject3.put("color", i);
        String optString2 = jSONObject.optString("detail", "");
        if (!optString2.isEmpty()) {
            jSONObject3.put("description", safeDetail(optString2, 900));
        }
        JSONArray jSONArray = new JSONArray();
        jSONArray.put(field("Report ID", jSONObject.optString("reportId", "—"), true));
        jSONArray.put(field("App", jSONObject.optString("appVersion", "1.0") + " • " + jSONObject.optString("channel", "Dev"), true));
        jSONArray.put(field("Build", jSONObject.optInt("internalBuild", 100) + " / " + jSONObject.optInt("versionCode", 10000072), true));
        StringBuilder sb2 = new StringBuilder("API ");
        sb2.append(jSONObject.optInt("androidApi", Build.VERSION.SDK_INT));
        jSONArray.put(field("Android", sb2.toString(), true));
        jSONArray.put(field("Orientation", jSONObject.optString("orientation", "unknown"), true));
        jSONArray.put(field("Identity mode", jSONObject.optString("identityMode", "Guest_Protocol"), true));
        jSONArray.put(field("Forum host", jSONObject.optString("forumHost", "—"), false));
        if (jSONObject.has("device")) {
            jSONArray.put(field("Device", jSONObject.optString("device", "—"), false));
        }
        if (jSONObject.has("route")) {
            jSONArray.put(field("Route", jSONObject.optString("route", "—"), false));
        }
        if (jSONObject.has("forumIdentity") && (optJSONObject = jSONObject.optJSONObject("forumIdentity")) != null) {
            StringBuilder sb3 = new StringBuilder();
            if (!optJSONObject.optString("displayName", "").isEmpty()) {
                sb3.append(optJSONObject.optString("displayName"));
                sb3.append('\n');
            }
            if (!optJSONObject.optString("username", "").isEmpty()) {
                sb3.append('@');
                sb3.append(optJSONObject.optString("username"));
                sb3.append('\n');
            }
            if (!optJSONObject.optString("groups", "").isEmpty()) {
                sb3.append("Groups: ");
                sb3.append(optJSONObject.optString("groups"));
                sb3.append('\n');
            }
            if (!optJSONObject.optString("email", "").isEmpty()) {
                sb3.append("Email: ");
                sb3.append(optJSONObject.optString("email"));
            }
            jSONArray.put(field("Forum identity (user opted in)", safeDetail(sb3.toString(), 900), false));
        }
        if (jSONObject.has("exception")) {
            StringBuilder sb4 = new StringBuilder();
            sb4.append(jSONObject.optString("exception", "—"));
            if (jSONObject.optString("message", "").isEmpty()) {
                str = "";
            } else {
                str = "\n" + jSONObject.optString("message");
            }
            sb4.append(str);
            jSONArray.put(field("Exception", sb4.toString(), false));
        }
        if (jSONObject.has("userFeedback")) {
            jSONArray.put(field("User feedback", jSONObject.optString("userFeedback", "—"), false));
        }
        if (jSONObject.has("stackTrace")) {
            jSONArray.put(field("Stack trace", "```\n" + safeDetail(jSONObject.optString("stackTrace", ""), 900) + "\n```", false));
        }
        JSONArray optJSONArray = jSONObject.optJSONArray("breadcrumbs");
        if (optJSONArray != null && optJSONArray.length() > 0) {
            jSONArray.put(field("Recent app events", breadcrumbText(optJSONArray), false));
        }
        jSONObject3.put("fields", jSONArray);
        jSONObject3.put("footer", new JSONObject().put("text", "HCF opt-in diagnostics • sensitive credentials and forum content excluded"));
        jSONObject3.put("timestamp", jSONObject.optString("timestampUtc", isoNow()));
        jSONObject2.put("embeds", new JSONArray().put(jSONObject3));
        return jSONObject2;
    }

    private static JSONObject field(String str, String str2, boolean z) throws Exception {
        String trim = (str2 == null || str2.trim().isEmpty()) ? "—" : str2.trim();
        if (trim.length() > 1000) {
            trim = trim.substring(0, 1000) + "…";
        }
        return new JSONObject().put("name", str).put("value", trim).put("inline", z);
    }

    private static JSONArray breadcrumbs(Context context) {
        try {
            return new JSONArray(safeString(prefs(context), "telemetry_breadcrumbs", "[]"));
        } catch (Throwable unused) {
            return new JSONArray();
        }
    }

    private static String breadcrumbText(JSONArray jSONArray) {
        StringBuilder sb = new StringBuilder();
        for (int max = Math.max(0, jSONArray.length() - 12); max < jSONArray.length(); max++) {
            JSONObject optJSONObject = jSONArray.optJSONObject(max);
            if (optJSONObject != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(optJSONObject.optString("event", "event"));
                String optString = optJSONObject.optString("detail", "");
                if (!optString.isEmpty()) {
                    sb.append(" • ");
                    sb.append(optString);
                }
                if (sb.length() > 880) {
                    break;
                }
            }
        }
        return safeDetail(sb.toString(), 900);
    }

    private static String sanitizeStack(Throwable th) {
        try {
            StringWriter stringWriter = new StringWriter();
            th.printStackTrace(new PrintWriter(stringWriter));
            String replaceAll = stringWriter.toString().replace((char) 0, ' ').replace("\r", "").replaceAll("https?://[^\\s?]+\\?[^\\s]+", "[URL_QUERY_REDACTED]").replaceAll("(?i)(token|authorization|cookie|password)=?[^\\s,;]+", "$1=[REDACTED]");
            if (replaceAll.length() <= 6000) {
                return replaceAll;
            }
            return replaceAll.substring(0, 6000) + "…";
        } catch (Throwable unused) {
            return th.getClass().getName();
        }
    }

    private static String sanitizedRoute(String str, boolean z) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        try {
            String path = Uri.parse(str).getPath();
            if (path == null || path.isEmpty()) {
                if (!str.startsWith("/")) {
                    str = "/";
                }
                path = str;
            }
            if (!z) {
                path = path.replaceAll("(?i)^/u/[^/]+", "/u/[user]");
            }
            String replaceAll = path.replaceAll("(?i)^/d/(\\d+)[^/]*", "/d/$1");
            return replaceAll.length() > 240 ? replaceAll.substring(0, 240) : replaceAll;
        } catch (Throwable unused) {
            return "[route-unavailable]";
        }
    }

    private static String orientation(Context context) {
        try {
            int i = context.getResources().getConfiguration().orientation;
            if (i == 2) {
                return "landscape";
            }
            if (i == 1) {
                return "portrait";
            }
            return "unknown";
        } catch (Throwable unused) {
            return "unknown";
        }
    }

    private static String newReportId() {
        String str;
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            str = simpleDateFormat.format(new Date());
        } catch (Throwable unused) {
            str = "00000000";
        }
        byte[] bArr = new byte[3];
        new SecureRandom().nextBytes(bArr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(String.format(Locale.US, "%02X", Integer.valueOf(bArr[i] & 255)));
        }
        return "HCF-" + str + "-" + ((Object) sb);
    }

    private static void addHistory(Context context, String str, String str2, String str3) {
        JSONArray jSONArray;
        try {
            SharedPreferences prefs = prefs(context);
            try {
                jSONArray = new JSONArray(safeString(prefs, "telemetry_report_history", "[]"));
            } catch (Throwable unused) {
                jSONArray = new JSONArray();
            }
            JSONArray jSONArray2 = new JSONArray();
            for (int max = Math.max(0, jSONArray.length() - 19); max < jSONArray.length(); max++) {
                jSONArray2.put(jSONArray.opt(max));
            }
            jSONArray2.put(new JSONObject().put("time", isoNow()).put("reportId", str).put("type", str2).put("result", str3));
            prefs.edit().putString("telemetry_report_history", jSONArray2.toString()).apply();
        } catch (Throwable unused2) {
        }
    }

    private static String historyText(Context context) {
        try {
            JSONArray jSONArray = new JSONArray(safeString(prefs(context), "telemetry_report_history", "[]"));
            if (jSONArray.length() == 0) {
                return "No telemetry reports have been sent or discarded on this device.";
            }
            StringBuilder sb = new StringBuilder();
            for (int length = jSONArray.length() - 1; length >= 0; length--) {
                JSONObject optJSONObject = jSONArray.optJSONObject(length);
                if (optJSONObject != null) {
                    sb.append(optJSONObject.optString("time", ""));
                    sb.append('\n');
                    sb.append(optJSONObject.optString("reportId", "HCF-REPORT"));
                    sb.append(" • ");
                    sb.append(optJSONObject.optString("type", "event"));
                    sb.append(" • ");
                    sb.append(optJSONObject.optString("result", "unknown"));
                    sb.append("\n\n");
                }
            }
            return sb.toString().trim();
        } catch (Throwable unused) {
            return "History unavailable.";
        }
    }

    private static JSONObject readPendingCrash(Context context) {
        try {
            File pendingCrashFile = pendingCrashFile(context);
            if (pendingCrashFile.exists()) {
                return new JSONObject(readText(pendingCrashFile));
            }
            return null;
        } catch (Throwable unused) {
            return null;
        }
    }

    private static void deletePendingCrash(Context context) {
        try {
            pendingCrashFile(context).delete();
        } catch (Throwable unused) {
        }
        try {
            prefs(context).edit().remove("telemetry_pending_crash_id").apply();
        } catch (Throwable unused2) {
        }
    }

    private static File pendingCrashFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), "telemetry");
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, PENDING_CRASH_FILE);
    }

    private static void writeText(File file, String str) throws Exception {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8);
        if (str == null) {
            str = "";
        }
        outputStreamWriter.write(str);
        outputStreamWriter.flush();
        outputStreamWriter.close();
    }

    private static String readText(File file) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                return sb.toString();
            }
            sb.append(readLine);
        }
    }

    private static String decryptWebhook(Context context) throws Exception {
        String signingCertificateSha256 = signingCertificateSha256(context);
        if (signingCertificateSha256.isEmpty()) {
            throw new IllegalStateException("signing certificate unavailable");
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("HCF_TELEMETRY_V1|" + context.getPackageName() + "|" + signingCertificateSha256 + "|HCF-Telemetry-Discord-Relay-v1").getBytes(StandardCharsets.UTF_8)), "AES");
        byte[] decode = Base64.decode(WEBHOOK_IV_B64, 2);
        byte[] decode2 = Base64.decode(WEBHOOK_CIPHERTEXT_B64, 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(2, secretKeySpec, new GCMParameterSpec(128, decode));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(decode2), StandardCharsets.UTF_8);
    }

    private static String signingCertificateSha256(Context context) throws Exception {
        Signature[] signatureArr;
        PackageManager packageManager = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 134217728);
            signatureArr = packageInfo.signingInfo == null ? null : packageInfo.signingInfo.getApkContentsSigners();
        } else {
            signatureArr = packageManager.getPackageInfo(context.getPackageName(), 64).signatures;
        }
        if (signatureArr == null || signatureArr.length == 0) {
            return "";
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatureArr[0].toByteArray());
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02x", Integer.valueOf(b & 255)));
        }
        return sb.toString();
    }

    private static String isoNow() {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return simpleDateFormat.format(new Date());
        } catch (Throwable unused) {
            return "";
        }
    }

    private static String safeToken(String str, String str2) {
        String replaceAll = str == null ? "" : str.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (!replaceAll.isEmpty()) {
            str2 = replaceAll;
        }
        return str2.length() > 64 ? str2.substring(0, 64) : str2;
    }

    private static String safeDetail(String str, int i) {
        if (str == null) {
            return "";
        }
        String replaceAll = str.replace((char) 0, ' ').replace("\r", "").trim().replaceAll("(?i)(authorization|cookie|password|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]");
        if (replaceAll.length() <= i) {
            return replaceAll;
        }
        return replaceAll.substring(0, i) + "…";
    }

    private static void saveResult(Context context, String str) {
        try {
            prefs(context).edit().putString("telemetry_last_result", str).apply();
        } catch (Throwable unused) {
        }
    }

    private static boolean safeBoolean(SharedPreferences sharedPreferences, String str, boolean z) {
        try {
            try {
                return sharedPreferences.getBoolean(str, z);
            } catch (Throwable unused) {
                return z;
            }
        } catch (Throwable unused2) {
            sharedPreferences.edit().remove(str).apply();
            return z;
        }
    }

    private static String safeString(SharedPreferences sharedPreferences, String str, String str2) {
        try {
            try {
                String string = sharedPreferences.getString(str, str2);
                return string == null ? str2 : string;
            } catch (Throwable unused) {
                return str2;
            }
        } catch (Throwable unused2) {
            sharedPreferences.edit().remove(str).apply();
            return str2;
        }
    }

    private static long safeLong(SharedPreferences sharedPreferences, String str, long j) {
        try {
            try {
                return sharedPreferences.getLong(str, j);
            } catch (Throwable unused) {
                return j;
            }
        } catch (Throwable unused2) {
            sharedPreferences.edit().remove(str).apply();
            return j;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("hcf_app", 0);
    }

    private static int dp(Context context, int i) {
        return Math.round(i * context.getResources().getDisplayMetrics().density);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void showTextDialog(Activity activity, String str, String str2) {
        TextView textView = new TextView(activity);
        if (str2 == null) {
            str2 = "";
        }
        textView.setText(str2);
        textView.setTextSize(12.0f);
        textView.setTextIsSelectable(true);
        textView.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(textView);
        new AlertDialog.Builder(activity).setTitle(str).setView(scrollView).setPositiveButton("Close", (DialogInterface.OnClickListener) null).show();
    }

    private TelemetryService() {
    }
}
