package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds HCF settings backup/import controls and account-scoped App Settings profiles.
 *
 * Every signed-in forum username keeps an independent set of user-facing App Settings.
 * Guest has a separate profile. Account/session data is never copied between profiles.
 */
public final class HcfSettingsImportUi {
    private static final String SETUP_TAG = "hcf_setup_import_settings";
    private static final String SETTINGS_TAG = "hcf_settings_backup_transfer";
    private static final String REFRESH_PREF = "settings_transfer_refresh_ui";
    private static final WeakHashMap<Activity, Boolean> SETTINGS_OBSERVERS = new WeakHashMap<>();
    private static boolean registered;

    private HcfSettingsImportUi() {}

    /** Starts the account-scoped settings system as soon as the application process starts. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            UserSettingsProfiles.install(appContext);
            if (registered || !(appContext instanceof Application)) return true;
            registered = true;
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityResumed(Activity activity) {
                    try {
                        boolean profileChanged = UserSettingsProfiles.ensureActiveProfile(activity);
                        if (profileChanged && activity instanceof HcfSubActivities.SettingsActivity) {
                            activity.recreate();
                            return;
                        }
                        if (activity instanceof HcfMainActivities.SetupActivity) {
                            if (consumeRefresh(activity)) {
                                activity.recreate();
                                return;
                            }
                            injectSetupImport(activity);
                        } else if (activity instanceof HcfSubActivities.SettingsActivity) {
                            if (consumeRefresh(activity)) {
                                activity.recreate();
                                return;
                            }
                            installSettingsObserver(activity);
                            injectAdvancedSettingsTransfer(activity);
                        }
                    } catch (Throwable error) {
                        AppLogger.warn(activity, "settings_transfer_ui", error.getClass().getSimpleName());
                    }
                }

                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                @Override
                public void onActivityDestroyed(Activity activity) {
                    synchronized (SETTINGS_OBSERVERS) {
                        SETTINGS_OBSERVERS.remove(activity);
                    }
                }
            });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    public static final class TransferActivity extends Activity {
        private static final String EXTRA_MODE = "mode";
        private static final String EXTRA_FROM_SETUP = "from_setup";
        private static final String MODE_IMPORT = "import";
        private static final String MODE_EXPORT = "export";
        private static final int REQUEST_IMPORT = 2911;
        private static final int REQUEST_EXPORT = 2912;
        private String mode;

        static void startImport(Activity activity, boolean fromSetup) {
            Intent intent = new Intent(activity, TransferActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_IMPORT);
            intent.putExtra(EXTRA_FROM_SETUP, fromSetup);
            activity.startActivity(intent);
        }

        static void startExport(Activity activity) {
            Intent intent = new Intent(activity, TransferActivity.class);
            intent.putExtra(EXTRA_MODE, MODE_EXPORT);
            activity.startActivity(intent);
        }

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            ThemeManager.apply(this);
            UserSettingsProfiles.ensureActiveProfile(this);
            mode = getIntent() == null ? MODE_IMPORT : getIntent().getStringExtra(EXTRA_MODE);
            if (!MODE_EXPORT.equals(mode)) mode = MODE_IMPORT;
            if (state == null) launchPicker();
        }

        private void launchPicker() {
            try {
                Intent intent;
                if (MODE_EXPORT.equals(mode)) {
                    intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, suggestedExportFileName());
                    startActivityForResult(intent, REQUEST_EXPORT);
                } else {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "text/json", "text/plain", "application/octet-stream"
                    });
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(intent, REQUEST_IMPORT);
                }
            } catch (Throwable error) {
                Toast.makeText(this, "No compatible file picker is available.", Toast.LENGTH_SHORT).show();
                AppLogger.warn(this, "settings_transfer_picker", error.getClass().getSimpleName());
                finish();
            }
        }

        private String suggestedExportFileName() {
            SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, 0);
            String username = readStringPreference(prefs, AppPrefs.IDENTITY_USERNAME);
            boolean signedIn = isSignedIn(prefs);
            String accountPart = signedIn && !username.isEmpty()
                    ? "@" + safeFilePart(username, "User")
                    : "Guest";

            String channel = BuildInfo.DEFAULT_UPDATE_CHANNEL == null
                    ? ""
                    : BuildInfo.DEFAULT_UPDATE_CHANNEL.trim();
            String channelPart = ("dev".equalsIgnoreCase(channel) || "beta".equalsIgnoreCase(channel))
                    ? "-Beta"
                    : "";
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());

            return "HCF-Settings-" + accountPart
                    + channelPart
                    + "-v" + shortVersionCode()
                    + "-" + stamp
                    + ".json";
        }

        private static boolean isSignedIn(SharedPreferences prefs) {
            try {
                if (prefs.getBoolean(AppPrefs.IDENTITY_LOGGED_IN, false)) return true;
            } catch (Throwable ignored) {}
            return !readStringPreference(prefs, AppPrefs.SESSION_USER_ID).isEmpty();
        }

        private static String readStringPreference(SharedPreferences prefs, String key) {
            try {
                String value = prefs.getString(key, "");
                return value == null ? "" : value.trim();
            } catch (Throwable ignored) {
                return "";
            }
        }

        private static String safeFilePart(String value, String fallback) {
            String part = value == null ? "" : value.trim();
            part = part.replaceAll("[^A-Za-z0-9._-]+", "-");
            part = part.replaceAll("^-+|-+$", "");
            return part.isEmpty() ? fallback : part;
        }

        private static long shortVersionCode() {
            long code = BuildInfo.VERSION_CODE;
            if (code >= 10000000L && code < 20000000L) {
                return 100L + (code - 10000000L);
            }
            return code;
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finish();
                return;
            }
            Uri uri = data.getData();
            if (requestCode == REQUEST_IMPORT) {
                HcfSettingsTransfer.Result result = HcfSettingsTransfer.importFromUri(this, uri);
                Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show();
                if (result.ok) {
                    UserSettingsProfiles.captureActiveProfile(this);
                    try {
                        NotificationSyncScheduler.apply(this);
                    } catch (Throwable ignored) {}
                    getSharedPreferences(AppPrefs.FILE, 0).edit().putBoolean(REFRESH_PREF, true).apply();
                    AppLogger.info(this, "settings_transfer", "import_ok | " + result.summary());
                } else {
                    AppLogger.warn(this, "settings_transfer", "import_failed | " + result.message);
                }
            } else if (requestCode == REQUEST_EXPORT) {
                try {
                    UserSettingsProfiles.captureActiveProfile(this);
                    HcfSettingsTransfer.exportToUri(this, uri);
                    Toast.makeText(this, "HCF settings backup exported.", Toast.LENGTH_SHORT).show();
                    AppLogger.info(this, "settings_transfer", "export_ok | " + UserSettingsProfiles.displayLabel(this));
                } catch (Throwable error) {
                    Toast.makeText(this, "HCF could not export the settings backup.", Toast.LENGTH_LONG).show();
                    AppLogger.warn(this, "settings_transfer", "export_failed | " + error.getClass().getSimpleName());
                }
            }
            finish();
        }
    }

    private static void injectSetupImport(Activity activity) {
        ViewGroup content = findScrollContent(activity);
        if (content == null || findTagged(content, SETUP_TAG) != null) return;

        LinearLayout card = card(activity, SETUP_TAG);
        addTitle(activity, card, "Import Settings", "Restore App Settings into the current account profile.");
        TextView detail = text(activity,
                "Current settings profile: " + UserSettingsProfiles.displayLabel(activity)
                        + "\nChoose an HCF settings backup to restore the user-configurable settings for this profile.",
                11,
                activity.getColor(R.color.hcf_muted));
        detail.setPadding(0, 0, 0, dp(activity, 10));
        card.addView(detail);

        Button importButton = actionButton(activity, "Import Settings   ›");
        importButton.setOnClickListener(v -> TransferActivity.startImport(activity, true));
        card.addView(importButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        int index = Math.min(1, content.getChildCount());
        content.addView(card, index);
        AppLogger.info(activity, "settings_transfer_ui", "setup_control_added");
    }

    private static void installSettingsObserver(final Activity activity) {
        synchronized (SETTINGS_OBSERVERS) {
            if (SETTINGS_OBSERVERS.containsKey(activity)) return;
            SETTINGS_OBSERVERS.put(activity, Boolean.TRUE);
        }
        final View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    injectAdvancedSettingsTransfer(activity);
                } catch (Throwable error) {
                    AppLogger.warn(activity, "settings_transfer_advanced", error.getClass().getSimpleName());
                }
            }
        });
    }

    /** Adds Backup & Transfer only inside Advanced & About using the native settings panel style. */
    private static void injectAdvancedSettingsTransfer(Activity activity) {
        if (!"advanced".equals(readStringField(activity, "currentSettingsSection"))) return;
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null || findTagged(content, SETTINGS_TAG) != null) return;

        LinearLayout inner = nativeCard(activity);
        inner.setTag(SETTINGS_TAG + "_content");

        View nativeTitle = nativeSectionTitle(activity,
                "Backup & Transfer",
                "Per-account App Settings backup and restore");
        if (nativeTitle != null) inner.addView(nativeTitle);
        else addTitle(activity, inner, "Backup & Transfer", "Per-account App Settings backup and restore.");

        TextView profile = text(activity,
                "Settings profile: " + UserSettingsProfiles.displayLabel(activity),
                11,
                activity.getColor(R.color.hcf_accent_text));
        profile.setTypeface(null, 1);
        profile.setPadding(0, 0, 0, dp(activity, 9));
        inner.addView(profile);

        Button exportButton = nativeActionButton(activity, "Export Settings", v -> TransferActivity.startExport(activity));
        inner.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        Button importButton = nativeActionButton(activity, "Import Settings", v -> TransferActivity.startImport(activity, false));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        importLp.topMargin = dp(activity, 8);
        inner.addView(importButton, importLp);

        TextView note = text(activity,
                "Each signed-in forum username has its own App Settings profile. Guest has a separate profile. Switching accounts automatically saves the previous profile and restores the new one. Login/session data is never transferred.",
                10,
                activity.getColor(R.color.hcf_hint));
        note.setPadding(0, dp(activity, 9), 0, 0);
        inner.addView(note);

        View panel = nativeConnectedSettingsPanel(activity,
                "Backup & Transfer",
                "Per-user settings • " + UserSettingsProfiles.displayLabel(activity),
                inner,
                false);
        panel.setTag(SETTINGS_TAG);

        int aboutIndex = directChildContainingText(content, "About Harley's Clan Forum");
        if (aboutIndex < 0) aboutIndex = content.getChildCount();
        content.addView(panel, aboutIndex);
        AppLogger.info(activity, "settings_transfer_ui", "advanced_control_added | " + UserSettingsProfiles.displayLabel(activity));
    }

    /**
     * Keeps the existing global AppPrefs contract intact while making user-facing settings
     * account-scoped. This avoids changing every settings consumer in the app.
     */
    private static final class UserSettingsProfiles implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String ACTIVE_PROFILE_KEY = "settings_profile_active";
        private static final String PROFILE_FILE_PREFIX = "hcf_user_settings_profile_";
        private static final String PROFILE_INITIALIZED = "__initialized";
        private static final String PROFILE_LABEL = "__label";

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

        private static UserSettingsProfiles instance;
        private final Context appContext;
        private final SharedPreferences global;
        private boolean switching;

        private UserSettingsProfiles(Context context) {
            appContext = context.getApplicationContext();
            global = appContext.getSharedPreferences(AppPrefs.FILE, 0);
        }

        static synchronized void install(Context context) {
            if (context == null) return;
            if (instance == null) {
                instance = new UserSettingsProfiles(context);
                instance.global.registerOnSharedPreferenceChangeListener(instance);
            }
            instance.ensureProfile();
        }

        static boolean ensureActiveProfile(Context context) {
            install(context);
            return instance != null && instance.ensureProfile();
        }

        static void captureActiveProfile(Context context) {
            install(context);
            if (instance != null) instance.captureActive();
        }

        static String displayLabel(Context context) {
            install(context);
            if (instance == null) return "Guest";
            String username = readString(instance.global, AppPrefs.IDENTITY_USERNAME);
            if (instance.signedIn() && !username.isEmpty()) return "@" + username;
            return "Guest";
        }

        private synchronized boolean ensureProfile() {
            if (switching) return false;
            String target = desiredProfileKey();
            if (target.isEmpty()) return false; // Identity is currently syncing; keep the existing profile.
            String active = readString(global, ACTIVE_PROFILE_KEY);

            if (active.isEmpty()) {
                switching = true;
                try {
                    SharedPreferences targetPrefs = profilePrefs(target);
                    if (targetPrefs.getBoolean(PROFILE_INITIALIZED, false)) {
                        loadProfile(target);
                    } else {
                        saveGlobalToProfile(target);
                    }
                    global.edit().putString(ACTIVE_PROFILE_KEY, target).commit();
                    AppLogger.info(appContext, "settings_profile_init", displayLabelNoInstall());
                } finally {
                    switching = false;
                }
                return false;
            }

            if (active.equals(target)) return false;

            switching = true;
            try {
                saveGlobalToProfile(active);
                SharedPreferences targetPrefs = profilePrefs(target);
                if (targetPrefs.getBoolean(PROFILE_INITIALIZED, false)) {
                    loadProfile(target);
                } else {
                    clearGlobalUserSettings();
                    targetPrefs.edit()
                            .putBoolean(PROFILE_INITIALIZED, true)
                            .putString(PROFILE_LABEL, targetLabel())
                            .commit();
                }
                global.edit().putString(ACTIVE_PROFILE_KEY, target).commit();
                try {
                    NotificationSyncScheduler.apply(appContext);
                } catch (Throwable ignored) {}
                AppLogger.info(appContext, "settings_profile_switch", active + " -> " + target);
                return true;
            } finally {
                switching = false;
            }
        }

        private synchronized void captureActive() {
            if (switching) return;
            String active = readString(global, ACTIVE_PROFILE_KEY);
            if (active.isEmpty()) {
                ensureProfile();
                active = readString(global, ACTIVE_PROFILE_KEY);
            }
            if (!active.isEmpty()) saveGlobalToProfile(active);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (switching || key == null) return;
            if (AppPrefs.IDENTITY_USERNAME.equals(key)
                    || AppPrefs.IDENTITY_LOGGED_IN.equals(key)
                    || AppPrefs.SESSION_USER_ID.equals(key)) {
                ensureProfile();
                return;
            }
            if (!isUserSettingKey(key)) return;
            String active = readString(global, ACTIVE_PROFILE_KEY);
            if (active.isEmpty()) {
                ensureProfile();
                active = readString(global, ACTIVE_PROFILE_KEY);
            }
            if (!active.isEmpty()) saveSingleSetting(active, key);
        }

        private String desiredProfileKey() {
            boolean signedIn = signedIn();
            String username = readString(global, AppPrefs.IDENTITY_USERNAME);
            if (signedIn) {
                if (username.isEmpty()) return "";
                return "user:" + username.toLowerCase(Locale.US);
            }
            return "guest";
        }

        private boolean signedIn() {
            try {
                if (global.getBoolean(AppPrefs.IDENTITY_LOGGED_IN, false)) return true;
            } catch (Throwable ignored) {}
            return !readString(global, AppPrefs.SESSION_USER_ID).isEmpty();
        }

        private String targetLabel() {
            String username = readString(global, AppPrefs.IDENTITY_USERNAME);
            return signedIn() && !username.isEmpty() ? "@" + username : "Guest";
        }

        private String displayLabelNoInstall() {
            return targetLabel();
        }

        private SharedPreferences profilePrefs(String profileKey) {
            String safe = profileKey.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_");
            if (safe.isEmpty()) safe = "profile";
            safe = safe + "_" + Integer.toHexString(profileKey.hashCode());
            return appContext.getSharedPreferences(PROFILE_FILE_PREFIX + safe, 0);
        }

        private void saveGlobalToProfile(String profileKey) {
            if (profileKey == null || profileKey.isEmpty()) return;
            Map<String, ?> all = global.getAll();
            SharedPreferences.Editor out = profilePrefs(profileKey).edit().clear();
            out.putBoolean(PROFILE_INITIALIZED, true);
            out.putString(PROFILE_LABEL, "guest".equals(profileKey) ? "Guest" : profileKey.substring(profileKey.indexOf(':') + 1));
            for (String key : BOOLEAN_KEYS) {
                Object value = all.get(key);
                if (value instanceof Boolean) out.putBoolean(key, ((Boolean) value).booleanValue());
            }
            for (String key : STRING_KEYS) {
                Object value = all.get(key);
                if (value instanceof String) out.putString(key, (String) value);
            }
            out.commit();
        }

        private void saveSingleSetting(String profileKey, String key) {
            Object value = global.getAll().get(key);
            SharedPreferences.Editor out = profilePrefs(profileKey).edit();
            out.putBoolean(PROFILE_INITIALIZED, true);
            if (value instanceof Boolean) out.putBoolean(key, ((Boolean) value).booleanValue());
            else if (value instanceof String) out.putString(key, (String) value);
            else out.remove(key);
            out.apply();
        }

        private void loadProfile(String profileKey) {
            SharedPreferences source = profilePrefs(profileKey);
            Map<String, ?> saved = source.getAll();
            SharedPreferences.Editor edit = global.edit();
            for (String key : BOOLEAN_KEYS) edit.remove(key);
            for (String key : STRING_KEYS) edit.remove(key);
            for (String key : BOOLEAN_KEYS) {
                Object value = saved.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, ((Boolean) value).booleanValue());
            }
            for (String key : STRING_KEYS) {
                Object value = saved.get(key);
                if (value instanceof String) edit.putString(key, (String) value);
            }
            edit.commit();
        }

        private void clearGlobalUserSettings() {
            SharedPreferences.Editor edit = global.edit();
            for (String key : BOOLEAN_KEYS) edit.remove(key);
            for (String key : STRING_KEYS) edit.remove(key);
            edit.commit();
        }

        private static boolean isUserSettingKey(String key) {
            return BOOLEAN_KEYS.contains(key) || STRING_KEYS.contains(key);
        }

        private static String readString(SharedPreferences prefs, String key) {
            try {
                String value = prefs.getString(key, "");
                return value == null ? "" : value.trim();
            } catch (Throwable ignored) {
                return "";
            }
        }
    }

    private static String readStringField(Activity activity, String fieldName) {
        try {
            Field field = activity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static ViewGroup readViewGroupField(Activity activity, String fieldName) {
        try {
            Field field = activity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(activity);
            return value instanceof ViewGroup ? (ViewGroup) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LinearLayout nativeCard(Activity activity) {
        try {
            Method method = activity.getClass().getDeclaredMethod("card");
            method.setAccessible(true);
            Object value = method.invoke(activity);
            if (value instanceof LinearLayout) return (LinearLayout) value;
        } catch (Throwable ignored) {}
        return card(activity, SETTINGS_TAG + "_fallback");
    }

    private static View nativeSectionTitle(Activity activity, String title, String subtitle) {
        try {
            Method method = activity.getClass().getDeclaredMethod("sectionTitle", String.class, String.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle);
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Button nativeActionButton(Activity activity, String title, View.OnClickListener listener) {
        try {
            Method method = activity.getClass().getDeclaredMethod("actionButton", String.class, View.OnClickListener.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, listener);
            if (value instanceof Button) return (Button) value;
        } catch (Throwable ignored) {}
        Button fallback = actionButton(activity, title + "   ›");
        fallback.setOnClickListener(listener);
        return fallback;
    }

    private static View nativeConnectedSettingsPanel(Activity activity, String title, String subtitle, View inner, boolean expanded) {
        try {
            Method method = activity.getClass().getDeclaredMethod(
                    "connectedSettingsPanel", String.class, String.class, View.class, boolean.class);
            method.setAccessible(true);
            Object value = method.invoke(activity, title, subtitle, inner, expanded);
            if (value instanceof View) return (View) value;
        } catch (Throwable ignored) {}
        return inner;
    }

    private static int directChildContainingText(ViewGroup parent, String expected) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (containsText(parent.getChildAt(i), expected)) return i;
        }
        return -1;
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && expected.contentEquals(value)) return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }

    private static boolean consumeRefresh(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.FILE, 0);
        if (!prefs.getBoolean(REFRESH_PREF, false)) return false;
        prefs.edit().remove(REFRESH_PREF).apply();
        return true;
    }

    private static ViewGroup findScrollContent(Activity activity) {
        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        ScrollView scroll = findFirstScroll(root);
        if (scroll == null || scroll.getChildCount() == 0) return null;
        View child = scroll.getChildAt(0);
        return child instanceof ViewGroup ? (ViewGroup) child : null;
    }

    private static ScrollView findFirstScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findFirstScroll(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static View findTagged(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static LinearLayout card(Activity activity, String tag) {
        LinearLayout card = new LinearLayout(activity);
        card.setTag(tag);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(activity, 12);
        card.setLayoutParams(lp);
        return card;
    }

    private static void addTitle(Activity activity, LinearLayout card, String title, String subtitle) {
        TextView titleView = text(activity, title, 15, activity.getColor(R.color.hcf_cyan_bright));
        titleView.setTypeface(null, 1);
        card.addView(titleView);
        TextView subtitleView = text(activity, subtitle, 11, activity.getColor(R.color.hcf_muted));
        subtitleView.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        card.addView(subtitleView);
    }

    private static Button actionButton(Activity activity, String label) {
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(12.0f);
        button.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button.setBackgroundResource(R.drawable.error_secondary_button_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private static TextView text(Activity activity, String value, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
