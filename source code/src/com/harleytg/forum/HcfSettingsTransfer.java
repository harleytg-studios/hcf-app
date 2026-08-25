package com.harleytg.forum.dev;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Portable HCF app-settings backup/import helper.
 *
 * All user-configurable app preferences are exported automatically, including future
 * preferences added to the shared HCF settings store. Runtime state, package/channel
 * identity, Android permission/onboarding state, account/session data, notification
 * delivery history, telemetry history and downloaded-update state are never restored.
 */
final class HcfSettingsTransfer {
    static final String FORMAT = "hcf-settings";
    static final int SCHEMA_VERSION = 2;
    private static final int MAX_IMPORT_CHARS = 1024 * 1024;

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
            boolean simpleRootBackup = settings == null;
            if (settings == null) {
                // Keep compatibility with early/manual key/value HCF backups.
                settings = root;
            }
            JSONObject types = root.optJSONObject("settingTypes");

            SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
            Map<String, ?> current = prefs.getAll();
            SharedPreferences.Editor edit = prefs.edit();
            int imported = 0;
            int skipped = 0;

            java.util.Iterator<String> keys = settings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (simpleRootBackup && isMetadataKey(key)) continue;
                if (!isPortableSettingKey(key)) {
                    skipped++;
                    continue;
                }

                Object value = settings.opt(key);
                String type = types == null ? "" : types.optString(key, "");
                Object existing = current.get(key);
                if (writePreference(edit, key, value, type, existing)) {
                    imported++;
                } else {
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
        JSONObject types = new JSONObject();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!isPortableSettingKey(key)) continue;
            putPreference(settings, types, key, entry.getValue());
        }

        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sourcePackage", context.getPackageName());
        root.put("sourceChannel", BuildInfo.DEFAULT_UPDATE_CHANNEL);
        root.put("sourceVersionCode", BuildInfo.VERSION_CODE);

        String username = safeStringPref(prefs, "identity_username");
        if (!username.isEmpty()) root.put("connectedUsername", username);

        root.put("settings", settings);
        root.put("settingTypes", types);
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

    private static void putPreference(JSONObject settings, JSONObject types, String key, Object value) throws Exception {
        if (value instanceof Boolean) {
            settings.put(key, value);
            types.put(key, "boolean");
        } else if (value instanceof String) {
            settings.put(key, value);
            types.put(key, "string");
        } else if (value instanceof Integer) {
            settings.put(key, value);
            types.put(key, "int");
        } else if (value instanceof Long) {
            settings.put(key, value);
            types.put(key, "long");
        } else if (value instanceof Float) {
            settings.put(key, ((Float) value).doubleValue());
            types.put(key, "float");
        } else if (value instanceof Set) {
            JSONArray array = new JSONArray();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) array.put(item);
            }
            settings.put(key, array);
            types.put(key, "string_set");
        }
    }

    private static boolean writePreference(SharedPreferences.Editor edit, String key, Object value, String declaredType, Object existing) {
        try {
            String type = declaredType == null ? "" : declaredType.trim().toLowerCase(java.util.Locale.US);
            if (type.isEmpty()) type = inferType(value, existing);

            if ("boolean".equals(type)) {
                Boolean parsed = asBoolean(value);
                if (parsed == null) return false;
                edit.putBoolean(key, parsed.booleanValue());
                return true;
            }
            if ("string".equals(type)) {
                if (value == null || value == JSONObject.NULL) return false;
                edit.putString(key, String.valueOf(value));
                return true;
            }
            if ("int".equals(type)) {
                Integer parsed = asInt(value);
                if (parsed == null) return false;
                edit.putInt(key, parsed.intValue());
                return true;
            }
            if ("long".equals(type)) {
                Long parsed = asLong(value);
                if (parsed == null) return false;
                edit.putLong(key, parsed.longValue());
                return true;
            }
            if ("float".equals(type)) {
                Float parsed = asFloat(value);
                if (parsed == null) return false;
                edit.putFloat(key, parsed.floatValue());
                return true;
            }
            if ("string_set".equals(type) && value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                LinkedHashSet<String> out = new LinkedHashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item != null && item != JSONObject.NULL) out.add(String.valueOf(item));
                }
                edit.putStringSet(key, out);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String inferType(Object value, Object existing) {
        if (existing instanceof Boolean) return "boolean";
        if (existing instanceof String) return "string";
        if (existing instanceof Integer) return "int";
        if (existing instanceof Long) return "long";
        if (existing instanceof Float) return "float";
        if (existing instanceof Set) return "string_set";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof String) return "string";
        if (value instanceof Integer) return "int";
        if (value instanceof Long) return "long";
        if (value instanceof Number) return "float";
        if (value instanceof JSONArray) return "string_set";
        return "";
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

    private static Integer asInt(Object value) {
        if (value instanceof Number) return Integer.valueOf(((Number) value).intValue());
        if (value instanceof String) {
            try { return Integer.valueOf(Integer.parseInt(((String) value).trim())); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) return Long.valueOf(((Number) value).longValue());
        if (value instanceof String) {
            try { return Long.valueOf(Long.parseLong(((String) value).trim())); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Float asFloat(Object value) {
        if (value instanceof Number) return Float.valueOf(((Number) value).floatValue());
        if (value instanceof String) {
            try { return Float.valueOf(Float.parseFloat(((String) value).trim())); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String safeStringPref(SharedPreferences prefs, String key) {
        try {
            String value = prefs.getString(key, "");
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Returns true only for portable user-configurable settings. */
    private static boolean isPortableSettingKey(String key) {
        if (key == null || key.trim().isEmpty()) return false;
        String k = key.trim().toLowerCase(java.util.Locale.US);

        // Account/session identity is shown as metadata only and is never restored.
        if (k.startsWith("identity_") || k.startsWith("session_")) return false;

        // Download/install/check results are runtime state; update preferences remain portable.
        if (k.startsWith("update_download_")) return false;
        if ("update_channel".equals(k)
                || "update_install_pending".equals(k)
                || "update_last_available_tag".equals(k)
                || "update_last_check".equals(k)
                || "update_resume_after_permission".equals(k)) return false;

        // Notification delivery state and Android permission prompts are device-local.
        if (k.startsWith("notification_last_")
                || "delivered_notification_ids".equals(k)
                || "last_notification_count".equals(k)
                || "notification_permission_asked".equals(k)
                || "notification_permission_prompt_version".equals(k)) return false;

        // Telemetry choices are portable; telemetry records/results are not.
        if (k.startsWith("telemetry_last_")
                || "telemetry_breadcrumbs".equals(k)
                || "telemetry_pending_crash_id".equals(k)
                || "telemetry_report_history".equals(k)) return false;

        // Routing/cache/recovery/onboarding state is not an app preference the user chose.
        if ("active_host".equals(k)
                || "app_has_launched".equals(k)
                || "fallback_until".equals(k)
                || "firebase_config_cache".equals(k)
                || "firebase_config_source".equals(k)
                || "forum_auto_theme_updated_at".equals(k)
                || "install_permission_prompted".equals(k)
                || "last_main_paused_at".equals(k)
                || "last_recoverable_url".equals(k)
                || "last_seen_whats_new_version".equals(k)
                || "permission_onboarding_done".equals(k)
                || "renderer_recovery_count".equals(k)
                || "safe_links_seen_domains".equals(k)
                || "setup_completed".equals(k)
                || "setup_seen".equals(k)
                || "setup_version".equals(k)
                || "ui_revamp_version".equals(k)
                || "welcome_seen".equals(k)
                || "welcome_version".equals(k)) return false;

        return true;
    }

    private static boolean isMetadataKey(String key) {
        return "format".equals(key)
                || "schemaVersion".equals(key)
                || "schema".equals(key)
                || "sourcePackage".equals(key)
                || "sourceChannel".equals(key)
                || "sourceVersionCode".equals(key)
                || "connectedUsername".equals(key)
                || "settingTypes".equals(key);
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
