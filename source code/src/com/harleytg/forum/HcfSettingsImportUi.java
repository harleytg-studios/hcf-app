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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Adds portable settings backup/import controls to the existing native HCF setup/settings UI. */
public final class HcfSettingsImportUi {
    private static final String SETUP_TAG = "hcf_setup_import_settings";
    private static final String SETTINGS_TAG = "hcf_settings_backup_transfer";
    private static final String REFRESH_PREF = "settings_transfer_refresh_ui";
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
                            injectSettingsTransfer(activity);
                        }
                    } catch (Throwable error) {
                        AppLogger.warn(activity, "settings_transfer_ui", error.getClass().getSimpleName());
                    }
                }

                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                @Override public void onActivityDestroyed(Activity activity) {}
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
                    intent.putExtra(Intent.EXTRA_TITLE, "HCF-Settings-Backup.json");
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

    private static void injectSettingsTransfer(Activity activity) {
        ViewGroup content = findScrollContent(activity);
        if (content == null || findTagged(content, SETTINGS_TAG) != null) return;

        LinearLayout card = card(activity, SETTINGS_TAG);
        addTitle(activity, card, "Settings Backup & Transfer", "Move portable app settings between HCF Stable and Dev.");

        Button exportButton = actionButton(activity, "Export Settings   ›");
        exportButton.setOnClickListener(v -> TransferActivity.startExport(activity));
        card.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(activity, 44)));

        Button importButton = actionButton(activity, "Import Settings   ›");
        importButton.setOnClickListener(v -> TransferActivity.startImport(activity, false));
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(activity, 44));
        importLp.topMargin = dp(activity, 8);
        card.addView(importButton, importLp);

        TextView note = text(activity,
                "App identity, Stable/Dev update channel, Android permissions, account/session data and runtime history are not transferred.",
                10,
                activity.getColor(R.color.hcf_hint));
        note.setPadding(0, dp(activity, 9), 0, 0);
        card.addView(note);

        content.addView(card);
        AppLogger.info(activity, "settings_transfer_ui", "settings_control_added");
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
