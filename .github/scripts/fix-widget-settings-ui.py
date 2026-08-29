#!/usr/bin/env python3
from pathlib import Path

ui_path = Path("source code/src/com/harleytg/forum/HcfUI.java")
widget_path = Path("source code/src/com/harleytg/forum/HcfWidget.java")
verify_path = Path(".github/scripts/verify-release-readiness.py")
readme_path = Path("README.md")
note_path = Path("source code/ci/widget-settings-subscreen.txt")

ui = ui_path.read_text()

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

# Give widget PendingIntents a supported deep-link into the real App Settings screen.
target_marker = '        private static final String TARGET_TAG_PREFIX = "hcf_setting:";\n'
if 'EXTRA_SETTINGS_SECTION = "hcf_settings_section"' not in ui:
    require(target_marker in ui, "Settings target marker not found")
    ui = ui.replace(
        target_marker,
        target_marker + '        public static final String EXTRA_SETTINGS_SECTION = "hcf_settings_section";\n',
        1,
    )

# Open a requested settings category after the normal Settings UI has been built.
create_marker = '                setContentView(buildUi());\n                handleInstallIntent(getIntent());\n'
if 'openRequestedSettingsSection(getIntent());' not in ui:
    require(create_marker in ui, "Settings onCreate marker not found")
    ui = ui.replace(
        create_marker,
        create_marker + '                openRequestedSettingsSection(getIntent());\n',
        1,
    )

settings_class = ui.index('    public static final class SettingsActivity extends ThemedActivity {')
on_resume = ui.index('        @Override\n        protected void onResume() {', settings_class)
if 'private void openRequestedSettingsSection(Intent intent)' not in ui[settings_class:on_resume]:
    routing = '''        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            handleInstallIntent(intent);
            openRequestedSettingsSection(intent);
        }

        private void openRequestedSettingsSection(Intent intent) {
            if (intent == null) return;
            String requested = intent.getStringExtra(EXTRA_SETTINGS_SECTION);
            if ("widget".equals(requested)) showSettingsSection("widget");
        }

'''
    ui = ui[:on_resume] + routing + ui[on_resume:]

# Remove the regression that diverted Widget to its own standalone Activity.
detour = '''            if ("widget".equals(section)) {
      startActivity(new Intent(this, HcfWidget.SettingsActivity.class));
      return;
  }
  currentSettingsSection = section;
'''
if detour in ui:
    ui = ui.replace(detour, '            currentSettingsSection = section;\n', 1)
require('startActivity(new Intent(this, HcfWidget.SettingsActivity.class))' not in ui,
        "Standalone Widget Settings detour still present")

# Use the same connectedSettingsPanel framework used by the other subsettings categories.
old_case = '''                case "widget":
                    settingsContent.addView(connectedSettingsPanel("Widget Appearance", "Theme source and home-screen widget controls", widgetCard(), shouldExpand("widget_appearance", true)));
                    break;
'''
new_case = '''                case "widget":
                    settingsContent.addView(connectedSettingsPanel("Widget Appearance", "Theme, identity, layout, opacity and text size", widgetCard(), shouldExpand("widget_appearance", true)));
                    settingsContent.addView(connectedSettingsPanel("Notification Preview", "Widget preview and notification-history shortcuts", widgetPreviewCard(), shouldExpand("widget_preview", false)));
                    settingsContent.addView(connectedSettingsPanel("Automatic Refresh", "Refresh schedule and current sync status", widgetRefreshCard(), shouldExpand("widget_refresh", false)));
                    settingsContent.addView(connectedSettingsPanel("Tap Behavior", "Default action when the widget body is tapped", widgetTapCard(), shouldExpand("widget_tap", false)));
                    break;
'''
if old_case in ui:
    ui = ui.replace(old_case, new_case, 1)
require(new_case in ui, "Standard Widget subsettings panels not installed")

# Replace the older one-card implementation with standard-card Widget controls.
method_start = ui.index('        private View widgetCard() {', settings_class)
method_end = ui.index('        private View interfaceCard() {', method_start)
widget_methods = r'''        private View widgetCard() {
            LinearLayout card = card();
            final boolean followAppTheme = prefs.getBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, true);
            final String themeState = followAppTheme
                    ? "Following HCF app theme • " + ThemeManager.autoSourceLabel(this)
                    : "Following Android phone theme";

            card.addView(settingsInfoCard(
                    "Widget theme source",
                    themeState,
                    R.drawable.fa_gear));

            Switch follow = target(toggle("Follow HCF app theme", followAppTheme), "widget_follow_app_theme");
            follow.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_theme_source", checked ? "app" : "phone");
            });
            card.addView(follow);
            card.addView(text(
                    "Uses HCF Light, Dark, AMOLED, or resolved Auto theme. Turn this off only if you want the widget to follow Android's phone theme instead.",
                    10, getColor(R.color.hcf_muted)));

            Switch connectedUsername = target(toggle("Show connected @username",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, true)), "widget_show_connected_username");
            connectedUsername.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_CONNECTED_USERNAME, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_connected_username", checked ? "shown" : "hidden");
            });
            card.addView(connectedUsername);

            Switch showUnread = target(toggle("Show unread count",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, true)), "widget_show_unread_count");
            showUnread.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_UNREAD_COUNT, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_unread_count", checked ? "shown" : "hidden");
            });
            card.addView(showUnread);

            Switch compactWidget = target(toggle("Compact widget mode",
                    prefs.getBoolean(HcfWidget.PREF_COMPACT_MODE, false)), "widget_compact_mode");
            compactWidget.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_COMPACT_MODE, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_compact_mode", checked ? "on" : "off");
            });
            card.addView(compactWidget);
            card.addView(text(
                    "Compact mode hides the large widget logo and title so identity and notification status get more room.",
                    10, getColor(R.color.hcf_muted)));

            Switch showUpdated = target(toggle("Show last updated time",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, false)), "widget_show_last_updated");
            showUpdated.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_LAST_UPDATED, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_last_updated", checked ? "shown" : "hidden");
            });
            card.addView(showUpdated);

            int opacity = Math.max(20, Math.min(100, prefs.getInt(HcfWidget.PREF_BACKGROUND_ALPHA, 96)));
            Button opacityButton = target(actionButton("Background opacity: " + opacity + "%", null), "widget_background_opacity");
            opacityButton.setOnClickListener(v -> showWidgetOpacityDialog(opacityButton));
            card.addView(opacityButton);

            int textSize = Math.max(10, Math.min(18, prefs.getInt(HcfWidget.PREF_TEXT_SIZE_SP, 12)));
            Button textSizeButton = target(actionButton("Widget text size: " + textSize + " sp", null), "widget_text_size");
            textSizeButton.setOnClickListener(v -> showWidgetTextSizeDialog(textSizeButton));
            card.addView(textSizeButton);
            return card;
        }

        private View widgetPreviewCard() {
            LinearLayout card = card();
            Switch preview = target(toggle("Show last notification preview",
                    prefs.getBoolean(HcfWidget.PREF_SHOW_LAST_NOTIFICATION_PREVIEW, true)), "widget_notification_preview");
            preview.setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(HcfWidget.PREF_SHOW_LAST_NOTIFICATION_PREVIEW, checked).apply();
                HcfWidget.refreshAll(this);
                AppLogger.info(this, "setting_widget_notification_preview", checked ? "shown" : "hidden");
            });
            card.addView(preview);
            card.addView(text(
                    "The widget preview is separate from the app's local notification-history privacy setting.",
                    10, getColor(R.color.hcf_muted)));
            card.addView(actionButton("Notification History Settings", v -> showSettingsSection("notifications")));
            card.addView(actionButton("Open Notification History", v ->
                    startActivity(new Intent(this, HcfWidget.NotificationHistoryActivity.class))));
            return card;
        }

        private View widgetRefreshCard() {
            LinearLayout card = card();
            TextView status = target(text(widgetRefreshSummary(), 11, getColor(R.color.hcf_meta)), "widget_refresh_status");
            status.setBackgroundResource(R.drawable.quick_action_background);
            status.setPadding(dp(14), dp(11), dp(14), dp(11));
            card.addView(status);

            int minutes = Math.max(0, prefs.getInt(HcfWidget.PREF_REFRESH_INTERVAL_MIN, 30));
            Button interval = target(actionButton("Automatic refresh: " + widgetRefreshLabel(minutes), null), "widget_auto_refresh");
            interval.setOnClickListener(v -> showWidgetRefreshDialog(interval, status));
            card.addView(interval);
            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {
                HcfWidget.refreshAll(this);
                try { HcfNotifications.InstantNotificationService.requestImmediateSync(this); }
                catch (Throwable ignored) {}
                status.setText(widgetRefreshSummary());
                AppLogger.info(this, "widget_refresh", "settings");
                Toast.makeText(this, "Home-screen widget refreshed.", Toast.LENGTH_SHORT).show();
            }), "refresh_widget_now"));
            return card;
        }

        private View widgetTapCard() {
            LinearLayout card = card();
            String currentTap = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);
            Button defaultTap = target(actionButton("Default widget tap: " + widgetTapLabel(currentTap), null),
                    "widget_default_tap_action");
            defaultTap.setOnClickListener(v -> {
                final String[] labels = {"Forum home", "Notifications", "Latest Discussions", "Profile", "Widget settings"};
                final String[] values = {HcfWidget.TAP_FORUM, HcfWidget.TAP_NOTIFICATIONS, HcfWidget.TAP_LATEST, HcfWidget.TAP_PROFILE, HcfWidget.TAP_SETTINGS};
                String saved = prefs.getString(HcfWidget.PREF_DEFAULT_TAP_ACTION, HcfWidget.TAP_FORUM);
                int selected = 0;
                for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) selected = i;
                new AlertDialog.Builder(this)
                        .setTitle("Default widget tap")
                        .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                            prefs.edit().putString(HcfWidget.PREF_DEFAULT_TAP_ACTION, values[which]).apply();
                            HcfWidget.refreshAll(this);
                            defaultTap.setText("Default widget tap: " + labels[which]);
                            AppLogger.info(this, "setting_widget_default_tap", values[which]);
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            card.addView(defaultTap);
            card.addView(text(
                    "This controls tapping the main widget body. The visible quick-action buttons keep their own destinations.",
                    10, getColor(R.color.hcf_muted)));
            return card;
        }

        private void showWidgetOpacityDialog(final Button button) {
            final int current = Math.max(20, Math.min(100, prefs.getInt(HcfWidget.PREF_BACKGROUND_ALPHA, 96)));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(22), dp(8), dp(22), 0);
            TextView value = text(current + "% background opacity", 12, getColor(R.color.hcf_muted));
            box.addView(value);
            android.widget.SeekBar seek = new android.widget.SeekBar(this);
            seek.setMax(80);
            seek.setProgress(current - 20);
            box.addView(seek, new LinearLayout.LayoutParams(-1, -2));
            final int[] selected = {current};
            seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                    selected[0] = progress + 20;
                    value.setText(selected[0] + "% background opacity");
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {}
            });
            new AlertDialog.Builder(this)
                    .setTitle("Widget background opacity")
                    .setView(box)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        prefs.edit().putInt(HcfWidget.PREF_BACKGROUND_ALPHA, selected[0]).apply();
                        HcfWidget.refreshAll(this);
                        button.setText("Background opacity: " + selected[0] + "%");
                        AppLogger.info(this, "setting_widget_background_opacity", Integer.toString(selected[0]));
                    })
                    .show();
        }

        private void showWidgetTextSizeDialog(final Button button) {
            final int current = Math.max(10, Math.min(18, prefs.getInt(HcfWidget.PREF_TEXT_SIZE_SP, 12)));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(22), dp(8), dp(22), 0);
            TextView value = text(current + " sp base text size", 12, getColor(R.color.hcf_muted));
            box.addView(value);
            android.widget.SeekBar seek = new android.widget.SeekBar(this);
            seek.setMax(8);
            seek.setProgress(current - 10);
            box.addView(seek, new LinearLayout.LayoutParams(-1, -2));
            final int[] selected = {current};
            seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                    selected[0] = progress + 10;
                    value.setText(selected[0] + " sp base text size");
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {}
            });
            new AlertDialog.Builder(this)
                    .setTitle("Widget text size")
                    .setView(box)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        prefs.edit().putInt(HcfWidget.PREF_TEXT_SIZE_SP, selected[0]).apply();
                        HcfWidget.refreshAll(this);
                        button.setText("Widget text size: " + selected[0] + " sp");
                        AppLogger.info(this, "setting_widget_text_size", Integer.toString(selected[0]));
                    })
                    .show();
        }

        private void showWidgetRefreshDialog(final Button button, final TextView status) {
            final int[] values = {0, 15, 30, 60, 120};
            final String[] labels = {"Off", "Every 15 minutes", "Every 30 minutes", "Every hour", "Every 2 hours"};
            int saved = Math.max(0, prefs.getInt(HcfWidget.PREF_REFRESH_INTERVAL_MIN, 30));
            int selected = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == saved) selected = i;
            new AlertDialog.Builder(this)
                    .setTitle("Automatic widget refresh")
                    .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                        prefs.edit().putInt(HcfWidget.PREF_REFRESH_INTERVAL_MIN, values[which]).apply();
                        HcfWidget.scheduleAutomaticRefresh(this);
                        HcfWidget.refreshAll(this);
                        button.setText("Automatic refresh: " + widgetRefreshLabel(values[which]));
                        status.setText(widgetRefreshSummary());
                        AppLogger.info(this, "setting_widget_refresh_interval", Integer.toString(values[which]));
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private String widgetRefreshLabel(int minutes) {
            if (minutes <= 0) return "Off";
            if (minutes == 60) return "Every hour";
            if (minutes == 120) return "Every 2 hours";
            return "Every " + minutes + " minutes";
        }

        private String widgetRefreshSummary() {
            int minutes = Math.max(0, prefs.getInt(HcfWidget.PREF_REFRESH_INTERVAL_MIN, 30));
            long last = Math.max(0L, prefs.getLong(HcfWidget.PREF_LAST_REALTIME_SYNC_MS, 0L));
            String interval = minutes <= 0 ? "Auto refresh off" : "Auto refresh every " + minutes + " min";
            if (last <= 0L) return interval + " • waiting for first sync";
            return interval + " • last sync " + android.text.format.DateFormat.getTimeFormat(this).format(new Date(last));
        }

        private String widgetTapLabel(String value) {
            if (HcfWidget.TAP_NOTIFICATIONS.equals(value)) return "Notifications";
            if (HcfWidget.TAP_LATEST.equals(value)) return "Latest Discussions";
            if (HcfWidget.TAP_PROFILE.equals(value)) return "Profile";
            if (HcfWidget.TAP_SETTINGS.equals(value)) return "Widget settings";
            return "Forum home";
        }

'''
ui = ui[:method_start] + widget_methods + ui[method_end:]

# Add the newer Widget controls to Settings search.
search_anchor = '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n'
if 'new SettingTarget("widget_background_opacity"' not in ui:
    require(search_anchor in ui, "Widget settings search anchor not found")
    ui = ui.replace(
        search_anchor,
        search_anchor
        + '                new SettingTarget("widget_background_opacity", "Background opacity", "widget transparency opacity appearance", "widget", "widget_appearance"),\n'
        + '                new SettingTarget("widget_text_size", "Widget text size", "widget text font size appearance", "widget", "widget_appearance"),\n'
        + '                new SettingTarget("widget_notification_preview", "Show last notification preview", "widget notification preview message", "widget", "widget_preview"),\n'
        + '                new SettingTarget("widget_auto_refresh", "Automatic widget refresh", "widget refresh interval schedule", "widget", "widget_refresh"),\n',
        1,
    )

ui_path.write_text(ui)

# The widget gear button now opens App Settings directly at Home-screen Widget.
widget = widget_path.read_text()
old_pending = '''    private static PendingIntent settingsPendingIntent(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.setAction("com.harleytg.forum.dev.action.HCF_WIDGET_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, REQUEST_SETTINGS, intent, PENDING_INTENT_FLAGS);
    }
'''
new_pending = '''    private static PendingIntent settingsPendingIntent(Context context) {
        Intent intent = new Intent(context, HcfSubActivities.SettingsActivity.class);
        intent.setAction("com.harleytg.forum.dev.action.HCF_WIDGET_SETTINGS");
        intent.putExtra(HcfSubActivities.SettingsActivity.EXTRA_SETTINGS_SECTION, "widget");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, REQUEST_SETTINGS, intent, PENDING_INTENT_FLAGS);
    }
'''
if old_pending in widget:
    widget = widget.replace(old_pending, new_pending, 1)
require(new_pending in widget, "Widget gear PendingIntent was not redirected")
widget_path.write_text(widget)

# Update release validation so it protects the shared route, not the old detour.
verify = verify_path.read_text()
old_verify = 'require("widget App Settings route missing", \'startActivity(new Intent(this, HcfWidget.SettingsActivity.class))\' in ui_source)\n'
new_verify = (
    'require("widget shared App Settings route missing", \'case "widget":\' in ui_source and \'EXTRA_SETTINGS_SECTION = "hcf_settings_section"\' in ui_source and \'HcfSubActivities.SettingsActivity.class\' in widget_source)\n'
    'require("widget settings still detours to standalone UI", \'startActivity(new Intent(this, HcfWidget.SettingsActivity.class))\' not in ui_source)\n'
)
if old_verify in verify:
    verify = verify.replace(old_verify, new_verify, 1)
verify_path.write_text(verify)

# Keep docs/validation note accurate.
readme = readme_path.read_text()
old_sentence = "App Settings → Home-screen Widget now opens the complete native widget settings screen, including transparency, text size, notification preview/history privacy, retention, automatic refresh, and tap behavior. Back returns to App Settings."
new_sentence = "App Settings → Home-screen Widget now uses the same native HCF subsettings UI as the other App Settings categories. Widget Appearance, Notification Preview, Automatic Refresh, and Tap Behavior are standard expandable settings panels; the widget gear button deep-links to this same section."
if old_sentence in readme:
    readme = readme.replace(old_sentence, new_sentence, 1)
readme_path.write_text(readme)

if note_path.exists():
    note_path.write_text(
        "Widget settings UI: shared App Settings subsettings framework\n"
        "Route: App Settings > Home-screen Widget\n"
        "Panels: Widget Appearance; Notification Preview; Automatic Refresh; Tap Behavior\n"
        "Standalone widget settings Activity is no longer used by App Settings or widget gear actions.\n"
    )

print("Widget settings now use the shared App Settings subsettings UI.")
