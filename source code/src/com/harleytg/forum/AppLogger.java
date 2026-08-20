package com.harleytg.forum.dev;

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

/* loaded from: classes.dex */
final class AppLogger {
    private static final Object LOCK = new Object();
    private static final String LOG_DIR = "app-logs";
    private static final String LOG_FILE = "hcf-app.log";
    private static final long MAX_LOG_BYTES = 524288;
    private static final String OLD_LOG_FILE = "hcf-app.previous.log";

    static void info(Context context, String str, String str2) {
        write(context, "INFO", str, str2);
    }

    static void warn(Context context, String str, String str2) {
        write(context, "WARN", str, str2);
    }

    static void error(Context context, String str, String str2) {
        write(context, "ERROR", str, str2);
    }

    static void crash(Context context, Throwable th) {
        StringWriter stringWriter = new StringWriter();
        th.printStackTrace(new PrintWriter(stringWriter));
        write(context, "CRASH", "uncaught_exception", stringWriter.toString());
    }

    static String safeUrl(String str) {
        String str2 = "";
        if (str == null || str.isEmpty()) {
            return "";
        }
        try {
            Uri parse = Uri.parse(str);
            String scheme = parse.getScheme() == null ? "" : parse.getScheme();
            if (parse.getHost() != null) {
                str2 = parse.getHost();
            }
            return scheme + "://" + str2 + (parse.getPath() == null ? "/" : parse.getPath());
        } catch (Throwable unused) {
            return "[unparseable-url]";
        }
    }

    static String readAll(Context context) {
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            appendFile(sb, oldFile(context));
            appendFile(sb, logFile(context));
            if (sb.length() == 0) {
                return "No app logs yet.";
            }
            return sb.toString();
        }
    }

    static String readRecent(Context context, int i) {
        int max = Math.max(4096, i);
        synchronized (LOCK) {
            try {
                File oldFile = oldFile(context);
                File logFile = logFile(context);
                long length = (oldFile.exists() ? oldFile.length() : 0L) + (logFile.exists() ? logFile.length() : 0L);
                StringBuilder sb = new StringBuilder(Math.min(max + 256, 200000));
                String readTail = readTail(logFile, Math.min(max, Math.max(4096, (max * 3) / 4)));
                int max2 = Math.max(0, max - readTail.length());
                String readTail2 = max2 > 0 ? readTail(oldFile, max2) : "";
                if (length > max) {
                    sb.append("Older log entries omitted from the on-screen viewer. Export logs to save the complete local history.\n\n");
                }
                if (!readTail2.isEmpty()) {
                    sb.append(readTail2);
                }
                if (!readTail.isEmpty()) {
                    sb.append(readTail);
                }
                if (sb.length() == 0) {
                    return "No app logs yet.";
                }
                int i2 = max + 220;
                if (sb.length() > i2) {
                    return sb.substring(sb.length() - i2);
                }
                return sb.toString();
            } catch (Throwable unused) {
                return "App logs are temporarily unavailable.";
            }
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            try {
                oldFile(context).delete();
            } catch (Throwable unused) {
            }
            try {
                logFile(context).delete();
            } catch (Throwable unused2) {
            }
        }
    }

    private static void write(Context context, String str, String str2, String str3) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                File logFile = logFile(context);
                if (logFile.length() >= MAX_LOG_BYTES) {
                    rotate(context);
                }
                String format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String clean = clean(str2, 120);
                String clean2 = clean(str3, 12000);
                TelemetryService.recordBreadcrumb(context, clean, clean2);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8);
                outputStreamWriter.write(format + " [" + str + "] " + clean);
                if (!clean2.isEmpty()) {
                    outputStreamWriter.write(" | " + clean2);
                }
                outputStreamWriter.write("\n");
                outputStreamWriter.flush();
                outputStreamWriter.close();
            } catch (Throwable unused) {
            }
        }
    }

    private static void rotate(Context context) {
        File logFile = logFile(context);
        File oldFile = oldFile(context);
        try {
            oldFile.delete();
        } catch (Throwable unused) {
        }
        if (logFile.exists()) {
            logFile.renameTo(oldFile);
        }
    }

    private static File logFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, LOG_FILE);
    }

    private static File oldFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return new File(file, OLD_LOG_FILE);
    }

    private static void appendFile(StringBuilder sb, File file) {
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            while (true) {
                String readLine = bufferedReader.readLine();
                if (readLine == null) {
                    bufferedReader.close();
                    return;
                } else {
                    sb.append(readLine);
                    sb.append('\n');
                }
            }
        } catch (Throwable unused) {
        }
    }

    private static String readTail(File file, int i) {
        int indexOf;
        int i2;
        if (file != null && file.exists() && i > 0) {
            RandomAccessFile randomAccessFile = null;
            try {
                RandomAccessFile randomAccessFile2 = new RandomAccessFile(file, "r");
                try {
                    long length = randomAccessFile2.length();
                    long min = Math.min(512000, Math.max(8192, i * 2));
                    long max = Math.max(0L, length - min);
                    randomAccessFile2.seek(max);
                    byte[] bArr = new byte[(int) Math.min(min, length - max)];
                    randomAccessFile2.readFully(bArr);
                    String str = new String(bArr, StandardCharsets.UTF_8);
                    if (max > 0 && (indexOf = str.indexOf(10)) >= 0 && (i2 = indexOf + 1) < str.length()) {
                        str = str.substring(i2);
                    }
                    if (str.length() > i) {
                        str = str.substring(str.length() - i);
                    }
                    try {
                        randomAccessFile2.close();
                    } catch (Throwable unused) {
                    }
                    return str;
                } catch (Throwable unused2) {
                    randomAccessFile = randomAccessFile2;
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (Throwable unused3) {
                        }
                    }
                    return "";
                }
            } catch (Throwable unused4) {
            }
        }
        return "";
    }

    private static String clean(String str, int i) {
        if (str == null) {
            return "";
        }
        String replace = str.replace((char) 0, ' ').replace("\r", "");
        if (replace.length() <= i) {
            return replace;
        }
        return replace.substring(0, i) + "…";
    }

    private AppLogger() {
    }
}
