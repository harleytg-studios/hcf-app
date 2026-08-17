package com.harleytg.forum;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppLogger {
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final String LOG_DIR = "app-logs";
    private static final String LOG_FILE = "hcf-app.log";
    private static final String OLD_LOG_FILE = "hcf-app.previous.log";
    private static final Object LOCK = new Object();

    static void info(Context context, String event, String detail) {
        write(context, "INFO", event, detail);
    }

    static void warn(Context context, String event, String detail) {
        write(context, "WARN", event, detail);
    }

    static void error(Context context, String event, String detail) {
        write(context, "ERROR", event, detail);
    }

    static void crash(Context context, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        write(context, "CRASH", "uncaught_exception", sw.toString());
    }

    static String safeUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "/" : uri.getPath();
            return scheme + "://" + host + path;
        } catch (Throwable ignored) {
            return "[unparseable-url]";
        }
    }

    static String readAll(Context context) {
        synchronized (LOCK) {
            StringBuilder out = new StringBuilder();
            appendFile(out, oldFile(context));
            appendFile(out, logFile(context));
            if (out.length() == 0) return "No app logs yet.";
            return out.toString();
        }
    }


    static String readRecent(Context context, int maxChars) {
        int limit = Math.max(4096, maxChars);
        synchronized (LOCK) {
            try {
                File old = oldFile(context);
                File current = logFile(context);
                long totalBytes = (old.exists() ? old.length() : 0L) + (current.exists() ? current.length() : 0L);
                StringBuilder out = new StringBuilder(Math.min(limit + 256, 200000));

                // Prioritize the active log, then use any remaining display budget for
                // the tail of the previous rotated log. This avoids building a huge
                // selectable TextView string on lower-memory devices.
                int currentBudget = Math.min(limit, Math.max(4096, limit * 3 / 4));
                String currentTail = readTail(current, currentBudget);
                int remaining = Math.max(0, limit - currentTail.length());
                String oldTail = remaining > 0 ? readTail(old, remaining) : "";

                if (totalBytes > limit) {
                    out.append("Older log entries omitted from the on-screen viewer. Export logs to save the complete local history.\n\n");
                }
                if (!oldTail.isEmpty()) out.append(oldTail);
                if (!currentTail.isEmpty()) out.append(currentTail);
                if (out.length() == 0) return "No app logs yet.";
                if (out.length() > limit + 220) return out.substring(out.length() - (limit + 220));
                return out.toString();
            } catch (Throwable ignored) {
                return "App logs are temporarily unavailable.";
            }
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            try { oldFile(context).delete(); } catch (Throwable ignored) {}
            try { logFile(context).delete(); } catch (Throwable ignored) {}
        }
    }

    private static void write(Context context, String level, String event, String detail) {
        if (context == null) return;
        synchronized (LOCK) {
            try {
                File file = logFile(context);
                if (file.length() >= MAX_LOG_BYTES) rotate(context);
                String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String cleanEvent = clean(event, 120);
                String cleanDetail = clean(detail, 12000);
                TelemetryService.recordBreadcrumb(context, cleanEvent, cleanDetail);
                FileOutputStream fos = new FileOutputStream(file, true);
                OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                writer.write(timestamp + " [" + level + "] " + cleanEvent);
                if (!cleanDetail.isEmpty()) writer.write(" | " + cleanDetail);
                writer.write("\n");
                writer.flush();
                writer.close();
            } catch (Throwable ignored) {
                // Logging must never crash the app.
            }
        }
    }

    private static void rotate(Context context) {
        File current = logFile(context);
        File old = oldFile(context);
        try { old.delete(); } catch (Throwable ignored) {}
        if (current.exists()) current.renameTo(old);
    }

    private static File logFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, LOG_FILE);
    }

    private static File oldFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, OLD_LOG_FILE);
    }

    private static void appendFile(StringBuilder out, File file) {
        if (!file.exists()) return;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            reader.close();
        } catch (Throwable ignored) {}
    }

    private static String readTail(File file, int maxChars) {
        if (file == null || !file.exists() || maxChars <= 0) return "";
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            int byteBudget = Math.min(512000, Math.max(8192, maxChars * 2));
            long start = Math.max(0L, length - byteBudget);
            raf.seek(start);
            byte[] data = new byte[(int) Math.min((long) byteBudget, length - start)];
            raf.readFully(data);
            String value = new String(data, StandardCharsets.UTF_8);
            if (start > 0L) {
                int newline = value.indexOf('\n');
                if (newline >= 0 && newline + 1 < value.length()) value = value.substring(newline + 1);
            }
            if (value.length() > maxChars) value = value.substring(value.length() - maxChars);
            return value;
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (raf != null) try { raf.close(); } catch (Throwable ignored) {}
        }
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String v = value.replace('\u0000', ' ').replace("\r", "");
        if (v.length() > max) v = v.substring(0, max) + "…";
        return v;
    }

    private AppLogger() {}
}
