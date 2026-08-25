package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/**
 * Portable HCF app-settings backup/import helper.
 *
 * The backup intentionally contains only user-controlled settings that can safely move
 * between Stable and Dev. Package identity, update channel, downloaded APK state,
 * permissions, session/identity data, notification history, telemetry history and other
 * runtime state are never transferred.
 */
final class HcfSettingsTransfer {
    static final String FORMAT = "hcf-settings";
    static final int SCHEMA_VERSION = 1;
    private static final int MAX_IMPORT_CHARS = 1024 * 1024;

    private static final Set<String> BOOLEAN_KEYS = new LinkedHashSet<>(Arrays.asList(
            AppPrefs.AUTO_FAILOVER,
            AppPrefs.BACKGROUND_NOTIFICATION_SYNC,
            AppPrefs.COMPACT_HEADER,
            AppPrefs.EXTERNAL_LINKS,
            AppPrefs.LIVE_FORUM_UPDATES,
            AppPrefs.NOTIFICATIONS_ENABLED,
            AppPrefs.PERFORMANCE_MODE,
            AppPrefs.SHOW_BOTTOM_NAV,
            AppPrefs.SHOW_STARTUP_SCREEN,
            AppPrefs.SHOW_URL_BAR,
            AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION,
            AppPrefs.TELEMETRY_ASK_BEFORE_CRASH_REPORT,
            AppPrefs.TELEMETRY_AUTO_CRASH_REPORTS,
            AppPrefs.TELEMETRY_AUTO_ERROR_REPORTS,
            AppPrefs.TELEMETRY_ENABLED,
            AppPrefs.TELEMETRY_INCLUDE_DEVICE_MODEL,
            AppPrefs.TELEMETRY_INCLUDE_EMAIL,
            AppPrefs.TELEMETRY_INCLUDE_IDENTITY,
            AppPrefs.TELEMETRY_INCLUDE_ROUTE,
            AppPrefs.UPDATE_AUTO_CHECK,
            AppPrefs.UPDATE_AUTO_DOWNLOAD,
            AppPrefs.UPDATE_AUTO_INSTALL
    ));

    private static final Set<String> STRING_KEYS = new LinkedHashSet<>(Arrays.asList(
            AppPrefs.APP_THEME,
            AppPrefs.NATIVE_ACCENT,
            AppPrefs.PERFORMANCE_PROFILE,
            AppPrefs.TELEMETRY_LEVEL,
            AppPrefs.FIREBASE_CONFIG_URL
    ));

    static final class Result {
        final boolean ok;
        final int imported;
        final int skipped;
        final String message;

        Result(boolean ok, int imported, int skipped, String message) {
            this.ok = ok;
            this.imported = imported;
            this.skipped = skipped;
            this.message = message == null ? "" : message;
        }

        String summary() {
            if (!ok) return message.isEmpty() ? "Settings import failed." : message;
            String base = "Imported " + imported + (imported == 1 ? " setting" : " settings");
            if (skipped > 0) base += " • skipped " + skipped;
            return base;
        }
    }

    private HcfSettingsTransfer() {}

    static Result importFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return new Result(false, 0, 0, "No settings backup was selected.");
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return new Result(false, 0, 0, "The selected settings backup could not be opened.");
            }
            return importJson(context, readUtf8(input));
        } catch (Throwable error) {
            AppLogger.warn(context, "settings_import", error.getClass().getSimpleName());
            return new Result(false, 0, 0, "This HCF settings backup could not be imported.");
        }
    }

    static Result importJson(Context context, String json) {
        if (context == null || json == null || json.trim().isEmpty()) {
            return new Result(false, 0, 0, "The selected settings backup is empty.");
        }
        try {
            JSONObject root = new JSONObject(json);
            String format = root.optString("format", "").trim();
            if (!format.isEmpty() && !FORMAT.equalsIgnoreCase(format)) {
                return new Result(false, 0, 0, "This file is not an HCF settings backup.");
            }
            int schema = root.optInt("schemaVersion", root.optInt("schema", 1));
            if (schema < 1 || schema > SCHEMA_VERSION) {
                return new Result(false, 0, 0, "This settings backup uses a newer unsupported format.");
            }

            JSONObject settings = root.optJSONObject("settings");
            if (settings == null) {
                // Accept a simple key/value object for early/manual HCF backups too.
                settings = root;
            }

            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
            SharedPreferences.Editor edit = prefs.edit();
            int imported = 0;
            int skipped = 0;

            java.util.Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = settings.opt(key);
                if (BOOLEAN_KEYS.contains(key)) {
                    Boolean parsed = asBoolean(value);
                    if (parsed == null) {
                        skipped++;
                    } else {
                        edit.putBoolean(key, parsed.booleanValue());
                        imported++;
                    }
                } else if (STRING_KEYS.contains(key)) {
                    if (value == null || value == JSONObject.NULL) {
                        skipped++;
                    } else {
                        edit.putString(key, String.valueOf(value));
                        imported++;
                    }
                } else if (!isMetadataKey(key)) {
                    skipped++;
                }
            }

            if (imported <= 0) {
                return new Result(false, 0, skipped, "No compatible HCF app settings were found in this backup.");
            }
            if (!edit.commit()) {
                return new Result(false, 0, skipped, "HCF could not save the imported settings.");
            }
            UiPreferences.migrate(context);
            AppLogger.info(context, "settings_import", "imported=" + imported + " skipped=" + skipped);
            return new Result(true, imported, skipped, "");
        } catch (Throwable error) {
            AppLogger.warn(context, "settings_import", error.getClass().getSimpleName());
            return new Result(false, 0, 0, "This HCF settings backup is invalid or damaged.");
        }
    }

    static String exportJson(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        Map<String, ?> all = prefs.getAll();
        JSONObject settings = new JSONObject();

        for (String key : BOOLEAN_KEYS) {
            Object value = all.get(key);
            if (value instanceof Boolean) settings.put(key, value);
        }
        for (String key : STRING_KEYS) {
            Object value = all.get(key);
            if (value instanceof String) settings.put(key, value);
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sourcePackage", context.getPackageName());
        root.put("sourceChannel", BuildInfo.DEFAULT_UPDATE_CHANNEL);
        root.put("sourceVersionCode", BuildInfo.VERSION_CODE);
        root.put("settings", settings);
        return root.toString(2);
    }

    static void exportToUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("Missing export destination");
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("Unable to open export destination");
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(exportJson(context));
                writer.flush();
            }
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) return Boolean.FALSE;
        }
        return null;
    }

    private static boolean isMetadataKey(String key) {
        return "format".equals(key)
                || "schemaVersion".equals(key)
                || "schema".equals(key)
                || "sourcePackage".equals(key)
                || "sourceChannel".equals(key)
                || "sourceVersionCode".equals(key);
    }

    private static String readUtf8(InputStream input) throws Exception {
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (out.length() + read > MAX_IMPORT_CHARS) {
                throw new IllegalArgumentException("Settings backup is too large");
            }
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
