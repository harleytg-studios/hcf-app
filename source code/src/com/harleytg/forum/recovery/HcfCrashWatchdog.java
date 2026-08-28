package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Process-wide crash context watchdog for HCF Dev/Beta.
 *
 * This is deliberately not a Service and does not create a notification. It lives with
 * the app process, follows Activity lifecycle state before/during/after startup, keeps a
 * light main-thread heartbeat, and wraps the process uncaught-exception chain so worker,
 * service/job, WebView-host, and UI-thread crashes can preserve useful last-known context
 * before the existing HcfSafeMode crash handler performs recovery bookkeeping.
 */
public final class HcfCrashWatchdog {
    private static final String PREF_FILE = "hcf_app";
    private static final String RECOVERY_DIR = "hcf-recovery";
    private static final String CONTEXT_FILE = "watchdog-context.txt";
    private static final long HEARTBEAT_MS = 5000L;
    private static final int CONTEXT_LIMIT = 16 * 1024;

    private static final String KEY_ACTIVE = "crash_watchdog_active";
    private static final String KEY_PID = "crash_watchdog_pid";
    private static final String KEY_PROCESS_STARTED_AT = "crash_watchdog_process_started_at";
    private static final String KEY_PROCESS_STARTED_ELAPSED = "crash_watchdog_process_started_elapsed";
    private static final String KEY_FOREGROUND = "crash_watchdog_foreground";
    private static final String KEY_STAGE = "crash_watchdog_stage";
    private static final String KEY_ACTIVITY = "crash_watchdog_activity";
    private static final String KEY_LIFECYCLE = "crash_watchdog_lifecycle";
    private static final String KEY_TRANSITION_AT = "crash_watchdog_transition_at";
    private static final String KEY_HEARTBEAT_AT = "crash_watchdog_heartbeat_at";
    private static final String KEY_HEARTBEAT_ELAPSED = "crash_watchdog_heartbeat_elapsed";
    private static final String KEY_HEARTBEAT_SEQ = "crash_watchdog_heartbeat_seq";
    private static final String KEY_LAST_CRASH_AT = "crash_watchdog_last_crash_at";
    private static final String KEY_LAST_CRASH_THREAD = "crash_watchdog_last_crash_thread";
    private static final String KEY_LAST_CRASH_STAGE = "crash_watchdog_last_crash_stage";
    private static final String KEY_LAST_CRASH_ACTIVITY = "crash_watchdog_last_crash_activity";
    private static final String KEY_LAST_CRASH_FOREGROUND = "crash_watchdog_last_crash_foreground";
    private static final String KEY_LAST_CRASH_SUMMARY = "crash_watchdog_last_crash_summary";
    private static final String KEY_LAST_CRASH_HEARTBEAT_AGE = "crash_watchdog_last_crash_heartbeat_age_ms";
    private static final String KEY_LAST_CRASH_UPTIME = "crash_watchdog_last_crash_process_uptime_ms";

    private static boolean installed;
    private static int startedActivities;
    private static Handler heartbeatHandler;
    private static Runnable heartbeatRunnable;

    private HcfCrashWatchdog() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context app = context.getApplicationContext();
            if (app == null) app = context;

            initializeProcessState(app);
            installContextHandler(app);
            startHeartbeat(app);

            if (app instanceof Application) {
                registerLifecycle((Application) app);
            }
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static synchronized void installContextHandler(Context context) {
        if (installed) return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (!(previous instanceof ContextHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new ContextHandler(context, previous));
        }
        installed = true;
    }

    private static final class ContextHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler previous;
        private boolean handling;

        ContextHandler(Context context, Thread.UncaughtExceptionHandler previous) {
            Context app = context == null ? null : context.getApplicationContext();
            this.context = app == null ? context : app;
            this.previous = previous;
        }

        @Override
        public synchronized void uncaughtException(Thread thread, Throwable error) {
            if (!handling) {
                handling = true;
                try {
                    recordCrashContext(context, thread, error);
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

    private static void initializeProcessState(Context context) {
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_PID, android.os.Process.myPid())
                .putLong(KEY_PROCESS_STARTED_AT, now)
                .putLong(KEY_PROCESS_STARTED_ELAPSED, elapsed)
                .putBoolean(KEY_FOREGROUND, false)
                .putString(KEY_STAGE, "process-start")
                .putString(KEY_ACTIVITY, "none")
                .putString(KEY_LIFECYCLE, "provider-created")
                .putLong(KEY_TRANSITION_AT, now)
                .putLong(KEY_HEARTBEAT_AT, now)
                .putLong(KEY_HEARTBEAT_ELAPSED, elapsed)
                .putInt(KEY_HEARTBEAT_SEQ, 0)
                .commit();
    }

    private static void registerLifecycle(Application app) {
        final Context context = app.getApplicationContext() == null ? app : app.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
                updateActivityState(context, activity, "created", false, null);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                updateActivityState(context, activity, "started", true, null);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                updateActivityState(context, activity, "resumed", true, classifyStage(activity));
            }

            @Override
            public void onActivityPaused(Activity activity) {
                updateActivityState(context, activity, "paused", startedActivities > 0, null);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                boolean foreground = startedActivities > 0;
                updateActivityState(context, activity, foreground ? "stopped-transition" : "background", foreground,
                        foreground ? null : "background");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                updateActivityState(context, activity, "destroyed", startedActivities > 0, null);
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    private static void updateActivityState(
            Context context,
            Activity activity,
            String lifecycle,
            boolean foreground,
            String explicitStage) {
        if (context == null) return;
        String activityName = activity == null ? "none" : activity.getClass().getName();
        SharedPreferences p = prefs(context);
        String stage = explicitStage == null ? p.getString(KEY_STAGE, "process-start") : explicitStage;
        p.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putInt(KEY_PID, android.os.Process.myPid())
                .putBoolean(KEY_FOREGROUND, foreground)
                .putString(KEY_STAGE, stage == null ? "unknown" : stage)
                .putString(KEY_ACTIVITY, activityName)
                .putString(KEY_LIFECYCLE, lifecycle)
                .putLong(KEY_TRANSITION_AT, System.currentTimeMillis())
                .apply();
    }

    private static String classifyStage(Activity activity) {
        if (activity == null) return "unknown";
        if (activity instanceof HcfForum.WelcomeActivity) return "welcome";
        if (activity instanceof HcfForum.SetupActivity) return "onboarding";
        if (activity instanceof HcfUI.StartupActivity || activity instanceof HcfUI.StartupMainActivity) return "startup-loading";
        if (activity instanceof HcfForum.MainActivity) return "forum-loaded";
        if (activity instanceof HcfSubActivities.SettingsActivity) return "settings";
        if (activity instanceof HcfSubActivities.LogsActivity) return "logs-diagnostics";
        if (activity instanceof HcfSafeMode.SafeModeActivity) return "recovery";
        if (activity instanceof HcfSafeMode.EntryActivity) return "launcher-route";
        return "activity:" + activity.getClass().getSimpleName();
    }

    private static synchronized void startHeartbeat(final Context context) {
        if (heartbeatHandler != null) return;
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override public void run() {
                try {
                    SharedPreferences p = prefs(context);
                    int seq = p.getInt(KEY_HEARTBEAT_SEQ, 0) + 1;
                    p.edit()
                            .putBoolean(KEY_ACTIVE, true)
                            .putInt(KEY_PID, android.os.Process.myPid())
                            .putLong(KEY_HEARTBEAT_AT, System.currentTimeMillis())
                            .putLong(KEY_HEARTBEAT_ELAPSED, SystemClock.elapsedRealtime())
                            .putInt(KEY_HEARTBEAT_SEQ, seq)
                            .apply();
                } catch (Throwable ignored) {
                }
                if (heartbeatHandler != null) heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
            }
        };
        heartbeatHandler.post(heartbeatRunnable);
    }

    private static void recordCrashContext(Context context, Thread thread, Throwable error) {
        if (context == null) return;
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        long processStarted = p.getLong(KEY_PROCESS_STARTED_ELAPSED, elapsed);
        long heartbeat = p.getLong(KEY_HEARTBEAT_ELAPSED, elapsed);
        long uptime = Math.max(0L, elapsed - processStarted);
        long heartbeatAge = Math.max(0L, elapsed - heartbeat);
        String stage = p.getString(KEY_STAGE, "unknown");
        String activity = p.getString(KEY_ACTIVITY, "none");
        boolean foreground = p.getBoolean(KEY_FOREGROUND, false);
        String summary = summarize(error);
        String threadName = thread == null ? "unknown" : thread.getName();

        p.edit()
                .putLong(KEY_LAST_CRASH_AT, now)
                .putString(KEY_LAST_CRASH_THREAD, threadName)
                .putString(KEY_LAST_CRASH_STAGE, stage)
                .putString(KEY_LAST_CRASH_ACTIVITY, activity)
                .putBoolean(KEY_LAST_CRASH_FOREGROUND, foreground)
                .putString(KEY_LAST_CRASH_SUMMARY, summary)
                .putLong(KEY_LAST_CRASH_HEARTBEAT_AGE, heartbeatAge)
                .putLong(KEY_LAST_CRASH_UPTIME, uptime)
                .commit();

        writeContextFile(context, now, stage, activity, foreground, threadName, summary, heartbeatAge, uptime,
                p.getString(KEY_LIFECYCLE, "unknown"));
    }

    private static void writeContextFile(
            Context context,
            long when,
            String stage,
            String activity,
            boolean foreground,
            String thread,
            String summary,
            long heartbeatAge,
            long uptime,
            String lifecycle) {
        File dir = new File(context.getFilesDir(), RECOVERY_DIR);
        if (!dir.exists() && !dir.mkdirs()) return;
        File file = new File(dir, CONTEXT_FILE);
        String text = "HCF Persistent Crash Watchdog\n"
                + "Captured: " + when + "\n"
                + "Process PID: " + android.os.Process.myPid() + "\n"
                + "Process uptime ms: " + uptime + "\n"
                + "App state: " + (foreground ? "foreground" : "background") + "\n"
                + "Stage: " + stage + "\n"
                + "Activity: " + activity + "\n"
                + "Lifecycle: " + lifecycle + "\n"
                + "Thread: " + thread + "\n"
                + "Main heartbeat age ms: " + heartbeatAge + "\n"
                + "Exception: " + summary + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, CONTEXT_LIMIT);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file, false);
            out.write(bytes, 0, length);
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) {}
            }
        }
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
}
