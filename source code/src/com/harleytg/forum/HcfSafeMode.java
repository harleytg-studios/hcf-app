package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Crash recovery and one-process Safe Mode for the HCF Dev/Beta app.
 *
 * Safe Mode intentionally does not clear cookies, account data, notification channels,
 * downloads, or other user content. It temporarily reduces aggressive runtime tuning
 * and automatic APK downloads, and the previous values are restored on the next process.
 */
public final class HcfSafeMode {
    private static final String PREF_FILE = "hcf_app";

    private static final String KEY_PENDING = "safe_mode_pending";
    private static final String KEY_ACTIVE = "safe_mode_active";
    private static final String KEY_SESSION_PID = "safe_mode_session_pid";
    private static final String KEY_CRASH_COUNT = "safe_mode_crash_count";
    private static final String KEY_LAST_CRASH_AT = "safe_mode_last_crash_at";
    private static final String KEY_LAST_CRASH_SUMMARY = "safe_mode_last_crash_summary";
    private static final String KEY_CRASHED_WHILE_SAFE = "safe_mode_crashed_while_active";

    private static final String KEY_PREV_AGGRESSIVE_PRESENT = "safe_mode_prev_aggressive_present";
    private static final String KEY_PREV_AGGRESSIVE = "safe_mode_prev_aggressive";
    private static final String KEY_PREV_PROFILE_PRESENT = "safe_mode_prev_profile_present";
    private static final String KEY_PREV_PROFILE = "safe_mode_prev_profile";
    private static final String KEY_PREV_PERFORMANCE_MODE_PRESENT = "safe_mode_prev_performance_mode_present";
    private static final String KEY_PREV_PERFORMANCE_MODE = "safe_mode_prev_performance_mode";
    private static final String KEY_PREV_AUTO_DOWNLOAD_PRESENT = "safe_mode_prev_auto_download_present";
    private static final String KEY_PREV_AUTO_DOWNLOAD = "safe_mode_prev_auto_download";

    private static final long CRASH_WINDOW_MS = 10L * 60L * 1000L;
    private static final int REPORT_LIMIT = 96 * 1024;
    private static final String RECOVERY_DIR = "hcf-recovery";
    private static final String LAST_CRASH_FILE = "last-crash.txt";

    private static boolean installed;

    private HcfSafeMode() {}

    /**
     * Early provider. Providers are created before Application.onCreate(), allowing this
     * handler to become the "previous" handler wrapped by HcfCore.App's existing logger.
     */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;

            Context appContext = context.getApplicationContext();
            if (appContext == null) appContext = context;

            restoreStaleSafeModeSession(appContext);
            installCrashHandler();

            if (appContext instanceof Application) {
                final Context finalContext = appContext;
                ((Application) appContext).registerActivityLifecycleCallbacks(
                        new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(Activity activity, Bundle state) {
                                if (activity instanceof SafeModeActivity || activity instanceof EntryActivity) return;

                                if (isCurrentProcessSafeMode(finalContext)) {
                                    applySafeOverrides(finalContext);
                                    return;
                                }

                                if (prefs(finalContext).getBoolean(KEY_PENDING, false)) {
                                    Intent recovery = new Intent(activity, SafeModeActivity.class);
                                    recovery.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    activity.startActivity(recovery);
                                    activity.finish();
                                }
                            }

                            @Override public void onActivityStarted(Activity activity) {}
                            @Override public void onActivityResumed(Activity activity) {}
                            @Override public void onActivityPaused(Activity activity) {}
                            @Override public void onActivityStopped(Activity activity) {}
                            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                            @Override public void onActivityDestroyed(Activity activity) {}
                        });
            }

            return true;
        }

        private void installCrashHandler() {
            synchronized (HcfSafeMode.class) {
                if (installed) return;
                Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                if (!(previous instanceof CrashHandler)) {
                    Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(getContext(), previous));
                }
                installed = true;
            }
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static final class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler previous;
        private boolean handling;

        CrashHandler(Context context, Thread.UncaughtExceptionHandler previous) {
            Context app = context == null ? null : context.getApplicationContext();
            this.context = app == null ? context : app;
            this.previous = previous;
        }

        @Override
        public synchronized void uncaughtException(Thread thread, Throwable error) {
            if (!handling) {
                handling = true;
                try {
                    recordCrash(context, thread, error);
                } catch (Throwable ignored) {
                }
            }

            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }

    private static void recordCrash(Context context, Thread thread, Throwable error) {
        if (context == null) return;

        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        long last = p.getLong(KEY_LAST_CRASH_AT, 0L);
        int count = now - last <= CRASH_WINDOW_MS ? p.getInt(KEY_CRASH_COUNT, 0) + 1 : 1;
        String summary = summarize(error);

        p.edit()
                .putBoolean(KEY_PENDING, true)
                .putInt(KEY_CRASH_COUNT, count)
                .putLong(KEY_LAST_CRASH_AT, now)
                .putString(KEY_LAST_CRASH_SUMMARY, summary)
                .putBoolean(KEY_CRASHED_WHILE_SAFE, isCurrentProcessSafeMode(context))
                .commit();

        File dir = new File(context.getFilesDir(), RECOVERY_DIR);
        if (!dir.exists() && !dir.mkdirs()) return;

        File report = new File(dir, LAST_CRASH_FILE);
        String text = buildCrashReport(context, thread, error, count, now);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, REPORT_LIMIT);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(report, false);
            out.write(bytes, 0, length);
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String buildCrashReport(Context context, Thread thread, Throwable error, int count, long when) {
        StringBuilder out = new StringBuilder();
        out.append("Harley's Clan Forum — Dev/Beta crash report\n");
        out.append("Generated: ").append(formatTime(when)).append('\n');
        out.append("Version: ").append(BuildInfo.VERSION).append(" (").append(BuildInfo.VERSION_CODE).append(")\n");
        out.append("Channel: ").append(BuildInfo.CHANNEL).append('\n');
        out.append("Crash count in 10 min window: ").append(count).append('\n');
        out.append("Safe Mode active: ").append(isCurrentProcessSafeMode(context)).append('\n');
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" / SDK ").append(Build.VERSION.SDK_INT).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("Thread: ").append(thread == null ? "unknown" : thread.getName()).append('\n');
        out.append("Exception: ").append(summarize(error)).append("\n\n");

        if (error != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            error.printStackTrace(pw);
            pw.flush();
            out.append(sw.toString());
        }

        return out.toString();
    }

    private static String summarize(Throwable error) {
        if (error == null) return "Unknown uncaught exception";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 220) message = message.substring(0, 220) + "…";
        return error.getClass().getName() + ": " + message;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    private static boolean isCurrentProcessSafeMode(Context context) {
        if (context == null) return false;
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ACTIVE, false)
                && p.getInt(KEY_SESSION_PID, -1) == android.os.Process.myPid();
    }

    private static void beginSafeMode(Context context) {
        SharedPreferences p = prefs(context);
        snapshotRuntimePrefs(p);

        p.edit()
                .putBoolean(KEY_PENDING, false)
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_SESSION_PID, android.os.Process.myPid())
                .commit();

        applySafeOverrides(context);
    }

    private static void applySafeOverrides(Context context) {
        SharedPreferences p = prefs(context);
        p.edit()
                .putBoolean("aggressive_realtime", false)
                .putString("performance_profile", PerformanceProfile.AUTO)
                .putBoolean("performance_mode", false)
                .putBoolean("update_auto_download", false)
                .commit();
    }

    private static void snapshotRuntimePrefs(SharedPreferences p) {
        if (p.getBoolean(KEY_ACTIVE, false)) return;

        SharedPreferences.Editor e = p.edit();

        e.putBoolean(KEY_PREV_AGGRESSIVE_PRESENT, p.contains("aggressive_realtime"));
        if (p.contains("aggressive_realtime")) {
            e.putBoolean(KEY_PREV_AGGRESSIVE, p.getBoolean("aggressive_realtime", true));
        }

        e.putBoolean(KEY_PREV_PROFILE_PRESENT, p.contains("performance_profile"));
        if (p.contains("performance_profile")) {
            e.putString(KEY_PREV_PROFILE, p.getString("performance_profile", PerformanceProfile.AUTO));
        }

        e.putBoolean(KEY_PREV_PERFORMANCE_MODE_PRESENT, p.contains("performance_mode"));
        if (p.contains("performance_mode")) {
            e.putBoolean(KEY_PREV_PERFORMANCE_MODE, p.getBoolean("performance_mode", false));
        }

        e.putBoolean(KEY_PREV_AUTO_DOWNLOAD_PRESENT, p.contains("update_auto_download"));
        if (p.contains("update_auto_download")) {
            e.putBoolean(KEY_PREV_AUTO_DOWNLOAD, p.getBoolean("update_auto_download", false));
        }

        e.commit();
    }

    private static void restoreStaleSafeModeSession(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACTIVE, false)) return;

        int savedPid = p.getInt(KEY_SESSION_PID, -1);
        if (savedPid == android.os.Process.myPid()) return;

        restoreRuntimePrefs(p);
    }

    private static void restoreRuntimePrefs(SharedPreferences p) {
        SharedPreferences.Editor e = p.edit();

        restoreBoolean(p, e, KEY_PREV_AGGRESSIVE_PRESENT, KEY_PREV_AGGRESSIVE, "aggressive_realtime");
        restoreString(p, e, KEY_PREV_PROFILE_PRESENT, KEY_PREV_PROFILE, "performance_profile");
        restoreBoolean(p, e, KEY_PREV_PERFORMANCE_MODE_PRESENT, KEY_PREV_PERFORMANCE_MODE, "performance_mode");
        restoreBoolean(p, e, KEY_PREV_AUTO_DOWNLOAD_PRESENT, KEY_PREV_AUTO_DOWNLOAD, "update_auto_download");

        e.putBoolean(KEY_ACTIVE, false);
        e.remove(KEY_SESSION_PID);
        e.remove(KEY_PREV_AGGRESSIVE_PRESENT).remove(KEY_PREV_AGGRESSIVE);
        e.remove(KEY_PREV_PROFILE_PRESENT).remove(KEY_PREV_PROFILE);
        e.remove(KEY_PREV_PERFORMANCE_MODE_PRESENT).remove(KEY_PREV_PERFORMANCE_MODE);
        e.remove(KEY_PREV_AUTO_DOWNLOAD_PRESENT).remove(KEY_PREV_AUTO_DOWNLOAD);
        e.commit();
    }

    private static void restoreBoolean(
            SharedPreferences p,
            SharedPreferences.Editor e,
            String presentKey,
            String valueKey,
            String targetKey) {
        if (p.getBoolean(presentKey, false)) {
            e.putBoolean(targetKey, p.getBoolean(valueKey, false));
        } else {
            e.remove(targetKey);
        }
    }

    private static void restoreString(
            SharedPreferences p,
            SharedPreferences.Editor e,
            String presentKey,
            String valueKey,
            String targetKey) {
        if (p.getBoolean(presentKey, false)) {
            e.putString(targetKey, p.getString(valueKey, PerformanceProfile.AUTO));
        } else {
            e.remove(targetKey);
        }
    }

    private static void normalStart(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_ACTIVE, false)) {
            restoreRuntimePrefs(p);
        }
        p.edit().putBoolean(KEY_PENDING, false).commit();
    }

    private static String readCrashReport(Context context) {
        File report = new File(new File(context.getFilesDir(), RECOVERY_DIR), LAST_CRASH_FILE);
        if (!report.isFile()) {
            SharedPreferences p = prefs(context);
            return "No saved crash report file.\n\nLast crash: "
                    + p.getString(KEY_LAST_CRASH_SUMMARY, "Unknown");
        }

        try {
            byte[] bytes = new byte[(int) Math.min(report.length(), REPORT_LIMIT)];
            java.io.FileInputStream in = new java.io.FileInputStream(report);
            int offset = 0;
            try {
                while (offset < bytes.length) {
                    int read = in.read(bytes, offset, bytes.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
            } finally {
                in.close();
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (Throwable error) {
            return "Crash report could not be read: " + error.getClass().getSimpleName();
        }
    }

    private static void clearCrashHistory(Context context) {
        SharedPreferences p = prefs(context);
        p.edit()
                .remove(KEY_CRASH_COUNT)
                .remove(KEY_LAST_CRASH_AT)
                .remove(KEY_LAST_CRASH_SUMMARY)
                .remove(KEY_CRASHED_WHILE_SAFE)
                .commit();

        File report = new File(new File(context.getFilesDir(), RECOVERY_DIR), LAST_CRASH_FILE);
        if (report.isFile()) {
            try { report.delete(); } catch (Throwable ignored) {}
        }
    }

    private static void resetRecoveryCounters(Context context) {
        prefs(context).edit()
                .remove("renderer_recovery_count")
                .remove("startup_last_good_at")
                .remove("startup_last_good_host")
                .remove("startup_loader_verbose")
                .commit();
    }

    private static int clearTemporaryCache(Context context) {
        File root = context.getCacheDir();
        if (root == null || !root.isDirectory()) return 0;
        int removed = 0;
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                removed += deleteRecursively(child);
            }
        }
        return removed;
    }

    private static int deleteRecursively(File file) {
        if (file == null || !file.exists()) return 0;
        int removed = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) removed += deleteRecursively(child);
            }
        }
        try {
            if (file.delete()) removed++;
        } catch (Throwable ignored) {
        }
        return removed;
    }

    private static String formatTime(long when) {
        if (when <= 0L) return "Unknown";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date(when));
    }

    /**
     * Tiny launcher/app-link router. This runs instead of the full startup activity so a
     * saved crash can reach recovery before WebView/startup UI is created.
     */
    public static final class EntryActivity extends Activity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);

            SharedPreferences p = prefs(this);
            if (p.getBoolean(KEY_PENDING, false)) {
                Intent recovery = new Intent(this, SafeModeActivity.class);
                recovery.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(recovery);
                finish();
                return;
            }

            if (isCurrentProcessSafeMode(this)) {
                applySafeOverrides(this);
            }

            Intent source = getIntent();
            Intent target = new Intent(this, HcfUI.StartupActivity.class);
            if (source != null) {
                target.setAction(source.getAction());
                target.setData(source.getData());
                Bundle extras = source.getExtras();
                if (extras != null) target.putExtras(extras);
            }
            startActivity(target);
            finish();
        }

        @Override
        public void onBackPressed() {
            finish();
        }
    }

    /** Native recovery UI shown after an uncaught crash. */
    public static final class SafeModeActivity extends Activity {
        private static final int BG = Color.rgb(13, 16, 20);
        private static final int PANEL = Color.rgb(20, 28, 34);
        private static final int CYAN = Color.rgb(0, 184, 240);
        private static final int TEXT = Color.rgb(235, 247, 251);
        private static final int MUTED = Color.rgb(157, 176, 186);
        private static final int WARNING = Color.rgb(255, 183, 77);
        private static final int BORDER = Color.rgb(47, 72, 84);

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            setTitle("HCF Safe Mode");
            setContentView(buildContent());
        }

        private View buildContent() {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(BG);

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(18), dp(24), dp(18), dp(28));
            scroll.addView(root, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView eyebrow = label("HARLEY'S CLAN FORUM • RECOVERY", 11, CYAN, true);
            root.addView(eyebrow);

            TextView title = label("Safe Mode", 28, TEXT, true);
            LinearLayout.LayoutParams titleLp = wrap();
            titleLp.topMargin = dp(4);
            root.addView(title, titleLp);

            TextView intro = label(
                    "The previous app run ended unexpectedly. Your forum account, cookies, "
                            + "notification channels, and downloaded files have not been cleared.",
                    14, MUTED, false);
            intro.setLineSpacing(0f, 1.12f);
            LinearLayout.LayoutParams introLp = wrap();
            introLp.topMargin = dp(8);
            root.addView(intro, introLp);

            root.addView(statusCard(), spaced(16));
            root.addView(primaryButton("Start in Safe Mode", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    beginSafeMode(SafeModeActivity.this);
                    toast("Safe Mode enabled for this app process");
                    launchForum();
                }
            }), spaced(16));

            root.addView(secondaryButton("Try Normal Start", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    normalStart(SafeModeActivity.this);
                    launchForum();
                }
            }), spaced(8));

            root.addView(sectionTitle("Crash report"), spaced(22));

            root.addView(secondaryButton("View Crash Details", new View.OnClickListener() {
                @Override public void onClick(View v) { showCrashDetails(); }
            }), spaced(8));

            root.addView(secondaryButton("Copy Crash Report", new View.OnClickListener() {
                @Override public void onClick(View v) { copyCrashReport(); }
            }), spaced(8));

            root.addView(secondaryButton("Share Crash Report", new View.OnClickListener() {
                @Override public void onClick(View v) { shareCrashReport(); }
            }), spaced(8));

            root.addView(sectionTitle("Recovery tools"), spaced(22));

            root.addView(secondaryButton("Clear Temporary Cache", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int count = clearTemporaryCache(SafeModeActivity.this);
                    toast("Removed " + count + " temporary cache item" + (count == 1 ? "" : "s"));
                }
            }), spaced(8));

            root.addView(secondaryButton("Reset Startup / Renderer Recovery State", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    resetRecoveryCounters(SafeModeActivity.this);
                    toast("Startup and renderer recovery counters reset");
                }
            }), spaced(8));

            root.addView(secondaryButton("Open Android App Settings", new View.OnClickListener() {
                @Override public void onClick(View v) { openAndroidAppSettings(); }
            }), spaced(8));

            root.addView(secondaryButton("Clear Crash History", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    clearCrashHistory(SafeModeActivity.this);
                    toast("Crash history cleared");
                    recreate();
                }
            }), spaced(8));

            if (BuildInfo.ENABLE_DEV_TEST_MENU) {
                root.addView(sectionTitle("Developer test"), spaced(22));
                root.addView(dangerButton("Test Crash Handler", new View.OnClickListener() {
                    @Override public void onClick(View v) { confirmCrashTest(); }
                }), spaced(8));
            }

            TextView foot = label(
                    "Safe Mode is intentionally temporary. It lowers realtime polling pressure, "
                            + "uses Auto performance mode, and pauses automatic APK downloads for "
                            + "this process. Original preferences are restored on the next process.",
                    12, MUTED, false);
            foot.setLineSpacing(0f, 1.12f);
            root.addView(foot, spaced(22));

            return scroll;
        }

        private View statusCard() {
            SharedPreferences p = prefs(this);
            int count = p.getInt(KEY_CRASH_COUNT, 0);
            long when = p.getLong(KEY_LAST_CRASH_AT, 0L);
            String summary = p.getString(KEY_LAST_CRASH_SUMMARY, "No crash summary available.");
            boolean safeCrash = p.getBoolean(KEY_CRASHED_WHILE_SAFE, false);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(rounded(PANEL, BORDER));

            TextView heading = label(count >= 2 ? "Repeated crash detected" : "Crash detected",
                    15, count >= 2 ? WARNING : TEXT, true);
            card.addView(heading);

            TextView meta = label(
                    "Crashes in 10-minute window: " + count + "\n"
                            + "Last crash: " + formatTime(when)
                            + (safeCrash ? "\nThe crash happened while Safe Mode was active." : ""),
                    12, MUTED, false);
            meta.setLineSpacing(0f, 1.15f);
            card.addView(meta, spaced(7));

            TextView detail = label(summary, 12, TEXT, false);
            detail.setLineSpacing(0f, 1.12f);
            card.addView(detail, spaced(10));

            return card;
        }

        private void launchForum() {
            Intent intent = new Intent(this, HcfUI.StartupActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        private void showCrashDetails() {
            TextView details = label(readCrashReport(this), 12, TEXT, false);
            details.setTextIsSelectable(true);
            details.setPadding(dp(8), dp(8), dp(8), dp(8));

            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(PANEL);
            scroll.addView(details);

            new AlertDialog.Builder(this)
                    .setTitle("HCF Crash Report")
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show();
        }

        private void copyCrashReport() {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                toast("Clipboard unavailable");
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("HCF crash report", readCrashReport(this)));
            toast("Crash report copied");
        }

        private void shareCrashReport() {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "HCF Dev/Beta crash report");
            share.putExtra(Intent.EXTRA_TEXT, readCrashReport(this));
            try {
                startActivity(Intent.createChooser(share, "Share crash report"));
            } catch (Throwable error) {
                toast("No compatible share app available");
            }
        }

        private void openAndroidAppSettings() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Throwable error) {
                toast("Android app settings unavailable");
            }
        }

        private void confirmCrashTest() {
            new AlertDialog.Builder(this)
                    .setTitle("Test crash recovery?")
                    .setMessage("This intentionally crashes the Dev/Beta app. Reopen HCF afterward to verify Safe Mode.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Crash App", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            throw new RuntimeException("HCF Safe Mode crash-handler test");
                        }
                    })
                    .show();
        }

        private Button primaryButton(String text, View.OnClickListener listener) {
            return button(text, CYAN, Color.rgb(2, 18, 24), CYAN, listener);
        }

        private Button secondaryButton(String text, View.OnClickListener listener) {
            return button(text, PANEL, TEXT, BORDER, listener);
        }

        private Button dangerButton(String text, View.OnClickListener listener) {
            return button(text, PANEL, WARNING, WARNING, listener);
        }

        private Button button(String text, int bg, int fg, int stroke, View.OnClickListener listener) {
            Button button = new Button(this);
            button.setText(text);
            button.setTextColor(fg);
            button.setTextSize(14);
            button.setAllCaps(false);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(48));
            button.setPadding(dp(14), dp(10), dp(14), dp(10));
            button.setBackground(rounded(bg, stroke));
            button.setOnClickListener(listener);
            return button;
        }

        private TextView sectionTitle(String text) {
            return label(text, 15, CYAN, true);
        }

        private TextView label(String text, int sp, int color, boolean bold) {
            TextView view = new TextView(this);
            view.setText(text);
            view.setTextColor(color);
            view.setTextSize(sp);
            if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return view;
        }

        private GradientDrawable rounded(int fill, int stroke) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(fill);
            shape.setCornerRadius(dp(10));
            shape.setStroke(dp(1), stroke);
            return shape;
        }

        private LinearLayout.LayoutParams wrap() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private LinearLayout.LayoutParams spaced(int topDp) {
            LinearLayout.LayoutParams lp = wrap();
            lp.topMargin = dp(topDp);
            return lp;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        private void toast(String text) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBackPressed() {
            moveTaskToBack(true);
        }
    }
}
