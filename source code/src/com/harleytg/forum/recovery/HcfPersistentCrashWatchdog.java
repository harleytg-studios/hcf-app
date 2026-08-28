package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Process-wide crash context watchdog for HCF Dev/Beta.
 *
 * This is intentionally not a foreground service. It lives only for the current Android
 * process and complements HcfSafeMode's uncaught-exception recovery handler. Because the
 * provider is created before Application.onCreate(), HcfCore's normal crash logger wraps
 * this handler and keeps the chain active during startup, onboarding, normal forum use,
 * Settings, and while the process remains alive in the background.
 */
public final class HcfPersistentCrashWatchdog {
    private static final String PREF_FILE = "hcf_app";
    private static final String KEY_STAGE = "crash_watchdog_stage";
    private static final String KEY_ACTIVITY = "crash_watchdog_activity";
    private static final String KEY_VISIBILITY = "crash_watchdog_visibility";
    private static final String KEY_STAGE_AT = "crash_watchdog_stage_at";
    private static final String KEY_HEARTBEAT_AT = "crash_watchdog_heartbeat_at";
    private static final String KEY_PROCESS_STARTED_AT = "crash_watchdog_process_started_at";
    private static final String KEY_LAST_CRASH_AT = "crash_watchdog_last_crash_at";
    private static final String KEY_LAST_CRASH_STAGE = "crash_watchdog_last_crash_stage";
    private static final String KEY_LAST_CRASH_ACTIVITY = "crash_watchdog_last_crash_activity";
    private static final String KEY_LAST_CRASH_VISIBILITY = "crash_watchdog_last_crash_visibility";
    private static final String KEY_LAST_CRASH_THREAD = "crash_watchdog_last_crash_thread";
    private static final String RECOVERY_DIR = "hcf-recovery";
    private static final String CONTEXT_FILE = "runtime-context.txt";
    private static final long HEARTBEAT_MS = 60_000L;

    private static final Object LOCK = new Object();
    private static int startedActivities;
    private static volatile boolean heartbeatRunning;
    private static volatile Context applicationContext;

    private HcfPersistentCrashWatchdog() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context raw = getContext();
            if (raw == null) return true;
            Context app = raw.getApplicationContext();
            if (app == null) app = raw;
            applicationContext = app;

            initializeProcessState(app);
            installContextHandler(app);
            startHeartbeat(app);

            if (app instanceof Application) {
                final Application application = (Application) app;
                application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle state) {
                        noteActivity(activity, "created");
                        if (activity instanceof HcfSafeMode.SafeModeActivity) {
                            View root = activity.getWindow().getDecorView();
                            if (root != null) root.post(() -> decorateRecoveryRuntime(activity));
                        }
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        synchronized (LOCK) { startedActivities++; }
                        noteVisibility(activity, "foreground");
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        noteActivity(activity, "resumed");
                        noteVisibility(activity, "foreground");
                        if (activity instanceof HcfSafeMode.SafeModeActivity) {
                            View root = activity.getWindow().getDecorView();
                            if (root != null) root.post(() -> decorateRecoveryRuntime(activity));
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {}

                    @Override
                    public void onActivityStopped(Activity activity) {
                        boolean background;
                        synchronized (LOCK) {
                            startedActivities = Math.max(0, startedActivities - 1);
                            background = startedActivities == 0;
                        }
                        if (background) noteVisibility(activity, "background");
                    }

                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity activity) {}
                });

                application.registerComponentCallbacks(new ComponentCallbacks2() {
                    @Override public void onConfigurationChanged(Configuration newConfig) {}
                    @Override public void onLowMemory() { noteProcessSignal(application, "low_memory"); }
                    @Override public void onTrimMemory(int level) {
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                            noteVisibility(null, "background");
                        }
                        noteProcessSignal(application, "trim_memory_" + level);
                    }
                });
            }
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void initializeProcessState(Context context) {
        long now = System.currentTimeMillis();
        prefs(context).edit()
                .putString(KEY_STAGE, "Process startup")
                .putString(KEY_ACTIVITY, "BootstrapProvider")
                .putString(KEY_VISIBILITY, "starting")
                .putLong(KEY_STAGE_AT, now)
                .putLong(KEY_HEARTBEAT_AT, now)
                .putLong(KEY_PROCESS_STARTED_AT, now)
                .apply();
    }

    private static void installContextHandler(Context context) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof RuntimeContextHandler) return;
        Thread.setDefaultUncaughtExceptionHandler(new RuntimeContextHandler(context, previous));
    }

    private static final class RuntimeContextHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler previous;
        private boolean handling;

        RuntimeContextHandler(Context context, Thread.UncaughtExceptionHandler previous) {
            Context app = context == null ? null : context.getApplicationContext();
            this.context = app == null ? context : app;
            this.previous = previous;
        }

        @Override
        public synchronized void uncaughtException(Thread thread, Throwable error) {
            if (!handling) {
                handling = true;
                try { captureCrashContext(context, thread, error); }
                catch (Throwable ignored) {}
            }

            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }

    private static void captureCrashContext(Context context, Thread thread, Throwable error) {
        if (context == null) return;
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        String stage = p.getString(KEY_STAGE, "Unknown");
        String activity = p.getString(KEY_ACTIVITY, "Unknown");
        String visibility = p.getString(KEY_VISIBILITY, "unknown");
        String threadName = thread == null ? "unknown" : thread.getName();

        p.edit()
                .putLong(KEY_LAST_CRASH_AT, now)
                .putString(KEY_LAST_CRASH_STAGE, stage)
                .putString(KEY_LAST_CRASH_ACTIVITY, activity)
                .putString(KEY_LAST_CRASH_VISIBILITY, visibility)
                .putString(KEY_LAST_CRASH_THREAD, threadName)
                .commit();

        File dir = new File(context.getFilesDir(), RECOVERY_DIR);
        if (!dir.exists() && !dir.mkdirs()) return;
        File file = new File(dir, CONTEXT_FILE);
        StringBuilder out = new StringBuilder();
        out.append("HCF persistent crash watchdog context\n");
        out.append("Captured: ").append(formatTime(now)).append('\n');
        out.append("Runtime stage: ").append(stage).append('\n');
        out.append("Last activity: ").append(activity).append('\n');
        out.append("App visibility: ").append(visibility).append('\n');
        out.append("Thread: ").append(threadName).append('\n');
        out.append("Process started: ").append(formatTime(p.getLong(KEY_PROCESS_STARTED_AT, 0L))).append('\n');
        out.append("Last heartbeat: ").append(formatTime(p.getLong(KEY_HEARTBEAT_AT, 0L))).append('\n');
        if (error != null) {
            out.append("Exception: ").append(error.getClass().getName());
            String message = error.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                out.append(": ").append(clean(message, 240));
            }
            out.append('\n');
        }

        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(bytes);
            stream.flush();
        } catch (Throwable ignored) {}
    }

    private static void noteActivity(Activity activity, String event) {
        if (activity == null) return;
        Context context = activity.getApplicationContext();
        if (context == null) context = activity;
        long now = System.currentTimeMillis();
        String stage = stageFor(activity);
        SharedPreferences p = prefs(context);
        String previousStage = p.getString(KEY_STAGE, "");
        p.edit()
                .putString(KEY_STAGE, stage)
                .putString(KEY_ACTIVITY, activity.getClass().getSimpleName())
                .putLong(KEY_STAGE_AT, now)
                .putLong(KEY_HEARTBEAT_AT, now)
                .apply();
        if (!stage.equals(previousStage)) {
            AppLogger.info(context, "crash_watchdog_stage", stage + " | " + event);
        }
    }

    private static void noteVisibility(Activity activity, String visibility) {
        Context context = activity == null ? applicationContext : activity.getApplicationContext();
        if (context == null && activity != null) context = activity;
        if (context == null) return;
        prefs(context).edit()
                .putString(KEY_VISIBILITY, visibility)
                .putLong(KEY_HEARTBEAT_AT, System.currentTimeMillis())
                .apply();
    }

    private static void noteProcessSignal(Context context, String signal) {
        if (context == null) return;
        prefs(context).edit()
                .putString("crash_watchdog_last_process_signal", signal)
                .putLong(KEY_HEARTBEAT_AT, System.currentTimeMillis())
                .apply();
    }

    private static String stageFor(Activity activity) {
        if (activity instanceof HcfSafeMode.EntryActivity) return "Launcher / pre-startup";
        if (activity instanceof HcfUI.StartupActivity || activity instanceof HcfUI.StartupMainActivity) return "Startup / loading";
        if (activity instanceof HcfForum.WelcomeActivity) return "Welcome";
        if (activity instanceof HcfForum.SetupActivity) return "Onboarding / app setup";
        if (activity instanceof HcfForum.MainActivity) return "Forum runtime";
        if (activity instanceof HcfSubActivities.SettingsActivity) return "App Settings";
        if (activity instanceof HcfSubActivities.LogsActivity) return "Logs & Diagnostics";
        if (activity instanceof HcfSubActivities.IdentityActivity) return "Identity";
        if (activity instanceof HcfSubActivities.SupportContactActivity) return "Support";
        if (activity instanceof HcfForum.CookieManagerActivity) return "Cookie manager";
        if (activity instanceof HcfSafeMode.SafeModeActivity) return "Recovery & Safe Mode";
        String name = activity.getClass().getSimpleName();
        return name == null || name.isEmpty() ? "App runtime" : name;
    }

    private static void startHeartbeat(final Context context) {
        synchronized (LOCK) {
            if (heartbeatRunning) return;
            heartbeatRunning = true;
        }
        final Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try { Thread.sleep(HEARTBEAT_MS); }
                catch (InterruptedException ignored) { return; }
                try {
                    prefs(app).edit().putLong(KEY_HEARTBEAT_AT, System.currentTimeMillis()).apply();
                } catch (Throwable ignored) {}
            }
        }, "hcf-crash-watchdog");
        heartbeat.setDaemon(true);
        heartbeat.setPriority(Thread.MIN_PRIORITY);
        heartbeat.start();
    }

    private static void decorateRecoveryRuntime(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences p = prefs(activity);
        long crashAt = p.getLong(KEY_LAST_CRASH_AT, 0L);
        if (crashAt <= 0L) return;
        View root = activity.findViewById(android.R.id.content);
        TextView target = findTextContaining(root, "Crashes in 10-minute window:");
        if (target == null) return;
        CharSequence current = target.getText();
        String existing = current == null ? "" : current.toString();
        if (existing.contains("Runtime stage:")) return;

        String stage = p.getString(KEY_LAST_CRASH_STAGE, "Unknown");
        String lastActivity = p.getString(KEY_LAST_CRASH_ACTIVITY, "Unknown");
        String visibility = p.getString(KEY_LAST_CRASH_VISIBILITY, "unknown");
        String thread = p.getString(KEY_LAST_CRASH_THREAD, "unknown");
        target.setText(existing
                + "\nRuntime stage: " + stage
                + "\nLast screen: " + lastActivity
                + "\nApp state: " + visibility
                + "\nCrash thread: " + thread);
    }

    private static TextView findTextContaining(View view, String needle) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().contains(needle)) return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findTextContaining(group.getChildAt(i), needle);
            if (found != null) return found;
        }
        return null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private static String formatTime(long when) {
        if (when <= 0L) return "Unknown";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date(when));
    }
}
