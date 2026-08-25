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
import java.util.Date;
import java.util.Locale;
import java.util.WeakHashMap;

/** Adds portable settings backup/import controls to the existing native HCF setup/settings UI. */
public final class HcfSettingsImportUi {
    private static final String SETUP_TAG = "hcf_setup_import_settings";
    private static final String SETTINGS_TAG = "hcf_settings_backup_transfer";
    private static final String REFRESH_PREF = "settings_transfer_refresh_ui";
    private static final WeakHashMap<Activity, Boolean> SETTINGS_OBSERVERS = new WeakHashMap<>();
    private static boolean registered;

    private HcfSettingsImportUi() {}

    /** Registers the UI hook without requiring changes to the large activity source bundle. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (registered || context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;
            registered = true;
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityResumed(Activity activity) {
                    try {
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
            String accountPart;
            if (username.isEmpty()) {
                accountPart = "Guest";
            } else {
                accountPart = "@" + safeFilePart(username, "User");
            }

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
                    try {
                        NotificationSyncScheduler.apply(this);
                    } catch (Throwable ignored) {
                    }
                    getSharedPreferences(AppPrefs.FILE, 0).edit().putBoolean(REFRESH_PREF, true).apply();
                    AppLogger.info(this, "settings_transfer", "import_ok | " + result.summary());
                } else {
                    AppLogger.warn(this, "settings_transfer", "import_failed | " + result.message);
                }
            } else if (requestCode == REQUEST_EXPORT) {
                try {
                    HcfSettingsTransfer.exportToUri(this, uri);
                    Toast.makeText(this, "HCF settings backup exported.", Toast.LENGTH_SHORT).show();
                    AppLogger.info(this, "settings_transfer", "export_ok");
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
        addTitle(activity, card, "Import Settings", "Restore compatible settings from HCF Stable or Dev.");
        TextView detail = text(activity,
                "Optional — choose an HCF settings backup and the setup controls will update automatically.",
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

    /**
     * Adds Backup & Transfer only inside Advanced & About. The outer shell and controls are
     * created through SettingsActivity's own builders so this section stays visually identical
     * to App Updates, Telemetry, Developer Tools and the other native sub-settings panels.
     */
    private static void injectAdvancedSettingsTransfer(Activity activity) {
        if (!"advanced".equals(readStringField(activity, "currentSettingsSection"))) return;
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null || findTagged(content, SETTINGS_TAG) != null) return;

        LinearLayout inner = nativeCard(activity);
        inner.setTag(SETTINGS_TAG + "_content");

        View nativeTitle = nativeSectionTitle(activity,
                "Backup & Transfer",
                "Move portable app settings between HCF Stable and Dev");
        if (nativeTitle != null) {
            inner.addView(nativeTitle);
        } else {
            addTitle(activity, inner, "Backup & Transfer", "Move portable app settings between HCF Stable and Dev.");
        }

        Button exportButton = nativeActionButton(activity, "Export Settings", v -> TransferActivity.startExport(activity));
        inner.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        Button importButton = nativeActionButton(activity, "Import Settings", v -> TransferActivity.startImport(activity, false));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        importLp.topMargin = dp(activity, 8);
        inner.addView(importButton, importLp);

        TextView note = text(activity,
                "Portable preferences only. App identity, Stable/Dev update channel, Android permissions, account/session data and runtime history stay with the current app.",
                10,
                activity.getColor(R.color.hcf_hint));
        note.setPadding(0, dp(activity, 9), 0, 0);
        inner.addView(note);

        View panel = nativeConnectedSettingsPanel(activity,
                "Backup & Transfer",
                "Export or import portable app settings between Stable and Dev",
                inner,
                false);
        panel.setTag(SETTINGS_TAG);

        int aboutIndex = directChildContainingText(content, "About Harley's Clan Forum");
        if (aboutIndex < 0) aboutIndex = content.getChildCount();
        content.addView(panel, aboutIndex);
        AppLogger.info(activity, "settings_transfer_ui", "advanced_control_added");
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
        } catch (Throwable ignored) {
        }
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
        } catch (Throwable ignored) {
        }
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
        } catch (Throwable ignored) {
        }
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
