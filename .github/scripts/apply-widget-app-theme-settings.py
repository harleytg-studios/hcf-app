#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.')
CORE = ROOT / 'source code/src/com/harleytg/forum/HcfCore.java'
UI = ROOT / 'source code/src/com/harleytg/forum/HcfUI.java'
WIDGET = ROOT / 'source code/src/com/harleytg/forum/HcfWidget.java'
VERIFY = ROOT / '.github/scripts/verify-release-readiness.py'
DRAWABLE = ROOT / 'source code/res/drawable'


def read(path):
    return path.read_text(encoding='utf-8')


def write(path, text):
    path.write_text(text, encoding='utf-8')


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# AppPrefs + settings backup/profile ownership
# -----------------------------------------------------------------------------
core = read(CORE)
core = replace_once(
    core,
    '    static final String UI_REVAMP_VERSION = "ui_revamp_version";\n',
    '    static final String UI_REVAMP_VERSION = "ui_revamp_version";\n'
    '    static final String WIDGET_FOLLOW_APP_THEME = "widget_follow_app_theme";\n',
    'AppPrefs widget theme key',
)
core = replace_once(
    core,
    '"show_bottom_nav", "show_startup_screen", "live_forum_updates"',
    '"show_bottom_nav", "show_startup_screen", "widget_follow_app_theme", "live_forum_updates"',
    'UiPreferences widget boolean sanitizer',
)
core = replace_once(
    core,
    '            AppPrefs.SHOW_STARTUP_SCREEN,\n            AppPrefs.SHOW_URL_BAR,',
    '            AppPrefs.SHOW_STARTUP_SCREEN,\n            AppPrefs.WIDGET_FOLLOW_APP_THEME,\n            AppPrefs.SHOW_URL_BAR,',
    'settings transfer widget key',
)
write(CORE, core)

# -----------------------------------------------------------------------------
# Root App Settings category + dedicated widget settings UI
# -----------------------------------------------------------------------------
ui = read(UI)
ui = replace_once(
    ui,
    '            addSettingsCategory(list, "Appearance & Performance", "Theme, interface density and performance", "appearance");\n'
    '            addSettingsCategory(list, "Forum & Site Data", "Server routing, links, cookies and local site data", "forum_data");',
    '            addSettingsCategory(list, "Appearance & Performance", "Theme, interface density and performance", "appearance");\n'
    '            addSettingsCategory(list, "Home-screen Widget", "Theme source and widget appearance", "widget");\n'
    '            addSettingsCategory(list, "Forum & Site Data", "Server routing, links, cookies and local site data", "forum_data");',
    'root widget settings category',
)
ui = replace_once(
    ui,
    '                new SettingTarget("verbose_startup_loader", "Verbose startup loader", "startup loading detailed checks progress completed verbose compact", "appearance", "appearance_performance"),\n'
    '                new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),',
    '                new SettingTarget("verbose_startup_loader", "Verbose startup loader", "startup loading detailed checks progress completed verbose compact", "appearance", "appearance_performance"),\n'
    '                new SettingTarget("widget_follow_app_theme", "Follow HCF app theme", "widget home screen theme app phone system light dark amoled", "widget", "widget_appearance"),\n'
    '                new SettingTarget("refresh_widget_now", "Refresh Home-screen Widget", "widget refresh reload home screen", "widget", "widget_appearance"),\n'
    '                new SettingTarget("auto_failover", "Automatically use backup if primary fails", "server backup failover routing", "forum_data", "connection_routing"),',
    'widget settings search entries',
)
ui = replace_once(
    ui,
    '                case "forum_data":\n'
    '                    settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));',
    '                case "widget":\n'
    '                    settingsContent.addView(connectedSettingsPanel("Widget Appearance", "Theme source and home-screen widget controls", widgetCard(), shouldExpand("widget_appearance", true)));\n'
    '                    break;\n'
    '                case "forum_data":\n'
    '                    settingsContent.addView(connectedSettingsPanel("Connection & Routing", "Primary/backup forum routing and link handling", connectionCard(), shouldExpand("connection_routing", true)));',
    'widget settings section switch',
)
ui = replace_once(
    ui,
    '            if ("appearance".equals(key)) return "Appearance & Performance";\n'
    '            if ("forum_data".equals(key)) return "Forum & Site Data";',
    '            if ("appearance".equals(key)) return "Appearance & Performance";\n'
    '            if ("widget".equals(key)) return "Home-screen Widget";\n'
    '            if ("forum_data".equals(key)) return "Forum & Site Data";',
    'widget section name',
)
ui = replace_once(
    ui,
    '            if ("appearance_performance".equals(key)) return "Appearance & Performance";\n'
    '            if ("connection_routing".equals(key)) return "Connection & Routing";',
    '            if ("appearance_performance".equals(key)) return "Appearance & Performance";\n'
    '            if ("widget_appearance".equals(key)) return "Widget Appearance";\n'
    '            if ("connection_routing".equals(key)) return "Connection & Routing";',
    'widget subsection display name',
)
ui = replace_once(
    ui,
    '        private View interfaceCard() {\n',
    '''        private View widgetCard() {\n            LinearLayout card = card();\n            final boolean followAppTheme = prefs.getBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, true);\n            final String themeState = followAppTheme\n                    ? "Following HCF app theme • " + ThemeManager.autoSourceLabel(this)\n                    : "Following Android phone theme";\n\n            card.addView(settingsInfoCard(\n                    "Widget theme source",\n                    themeState,\n                    R.drawable.fa_gear));\n\n            Switch follow = target(toggle("Follow HCF app theme", followAppTheme), "widget_follow_app_theme");\n            follow.setOnCheckedChangeListener((button, checked) -> {\n                prefs.edit().putBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, checked).apply();\n                HcfWidget.refreshAll(this);\n                AppLogger.info(this, "setting_widget_theme_source", checked ? "app" : "phone");\n                Toast.makeText(this,\n                        checked\n                                ? "Home-screen widget now follows the HCF app theme."\n                                : "Home-screen widget now follows the Android phone theme.",\n                        Toast.LENGTH_SHORT).show();\n                showSettingsSection("widget");\n            });\n            card.addView(follow);\n\n            card.addView(text(\n                    "When on, the widget uses HCF's selected Light, Dark, AMOLED, or resolved Auto theme even when the phone/launcher uses the opposite theme. Turn it off only if you want the widget to follow Android's phone theme instead.",\n                    10, getColor(R.color.hcf_muted)));\n\n            card.addView(target(actionButton("Refresh Home-screen Widget", v -> {\n                HcfWidget.refreshAll(this);\n                AppLogger.info(this, "widget_refresh", "settings");\n                Toast.makeText(this, "Home-screen widget refreshed.", Toast.LENGTH_SHORT).show();\n            }), "refresh_widget_now"));\n            return card;\n        }\n\n        private View interfaceCard() {\n''',
    'widget settings card',
)
ui = replace_once(
    ui,
    '            if ("appearance".equals(key)) return R.drawable.fa_gear;\n'
    '            if ("forum_data".equals(key)) return R.drawable.fa_globe;',
    '            if ("appearance".equals(key)) return R.drawable.fa_gear;\n'
    '            if ("widget".equals(key)) return R.drawable.fa_gear;\n'
    '            if ("forum_data".equals(key)) return R.drawable.fa_globe;',
    'widget root icon',
)
ui = replace_once(
    ui,
    '            if (lower.contains("appearance") || lower.contains("performance") || lower.contains("runtime")) return R.drawable.fa_gear;\n',
    '            if (lower.contains("appearance") || lower.contains("performance") || lower.contains("runtime") || lower.contains("widget")) return R.drawable.fa_gear;\n',
    'widget panel icon',
)
# Per-user settings profiles should retain the widget preference too.
needle = '                AppPrefs.SHOW_STARTUP_SCREEN,\n                AppPrefs.SHOW_URL_BAR,'
replacement = '                AppPrefs.SHOW_STARTUP_SCREEN,\n                AppPrefs.WIDGET_FOLLOW_APP_THEME,\n                AppPrefs.SHOW_URL_BAR,'
count = ui.count(needle)
if count != 1:
    raise SystemExit(f'user profile widget key: expected one match, found {count}')
ui = ui.replace(needle, replacement, 1)
write(UI, ui)

# -----------------------------------------------------------------------------
# Widget renderer: explicit palettes chosen from HCF app theme rather than host
# launcher resource-night resolution.
# -----------------------------------------------------------------------------
widget = read(WIDGET)
widget = replace_once(
    widget,
    '                if (AppPrefs.LAST_NOTIFICATION_COUNT.equals(key)\n'
    '                        || AppPrefs.SESSION_USER_ID.equals(key)) {',
    '                if (AppPrefs.LAST_NOTIFICATION_COUNT.equals(key)\n'
    '                        || AppPrefs.SESSION_USER_ID.equals(key)\n'
    '                        || AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)\n'
    '                        || AppPrefs.APP_THEME.equals(key)\n'
    '                        || AppPrefs.FORUM_AUTO_THEME.equals(key)) {',
    'widget preference refresh keys',
)
widget = replace_once(
    widget,
    '        views.setTextViewText(R.id.widget_hcf_title, context.getString(R.string.widget_hcf_title));\n'
    '        views.setTextViewText(R.id.widget_hcf_status, status);\n',
    '        views.setTextViewText(R.id.widget_hcf_title, context.getString(R.string.widget_hcf_title));\n'
    '        views.setTextViewText(R.id.widget_hcf_status, status);\n'
    '        applyWidgetTheme(context, prefs, views);\n',
    'widget theme application call',
)
widget = replace_once(
    widget,
    '    private static PendingIntent startupPendingIntent(',
    '''    private static void applyWidgetTheme(Context context, SharedPreferences prefs, RemoteViews views) {\n        if (context == null || prefs == null || views == null) return;\n\n        boolean followAppTheme = prefs.getBoolean(AppPrefs.WIDGET_FOLLOW_APP_THEME, true);\n        boolean amoled = followAppTheme && ThemeManager.isAmoled(context);\n        boolean dark = followAppTheme\n                ? "dark".equals(ThemeManager.webColorScheme(context))\n                : systemPhoneDark();\n\n        int rootBackground = amoled\n                ? R.drawable.widget_hcf_background_amoled\n                : dark ? R.drawable.widget_hcf_background_dark\n                : R.drawable.widget_hcf_background_light;\n        int actionBackground = amoled\n                ? R.drawable.widget_hcf_action_background_amoled\n                : dark ? R.drawable.widget_hcf_action_background_dark\n                : R.drawable.widget_hcf_action_background_light;\n        int titleColor = (amoled || dark) ? 0xFFE8F8FF : 0xFF10232B;\n        int mutedColor = (amoled || dark) ? 0xFFAEBBC2 : 0xFF53666F;\n        int accentColor = 0xFF00B8F0;\n\n        views.setInt(R.id.widget_hcf_root, "setBackgroundResource", rootBackground);\n        views.setTextColor(R.id.widget_hcf_title, titleColor);\n        views.setTextColor(R.id.widget_hcf_status, mutedColor);\n\n        int[] actions = {\n                R.id.widget_hcf_forum,\n                R.id.widget_hcf_notifications,\n                R.id.widget_hcf_reload,\n                R.id.widget_hcf_settings\n        };\n        for (int action : actions) {\n            views.setInt(action, "setBackgroundResource", actionBackground);\n            views.setTextColor(action, accentColor);\n        }\n    }\n\n    private static boolean systemPhoneDark() {\n        try {\n            int uiMode = android.content.res.Resources.getSystem().getConfiguration().uiMode;\n            return (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)\n                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;\n        } catch (Throwable ignored) {\n            return false;\n        }\n    }\n\n    private static PendingIntent startupPendingIntent(''',
    'widget explicit theme renderer',
)
write(WIDGET, widget)

# -----------------------------------------------------------------------------
# Explicit widget palettes. These do not use resource-night overrides, so the
# launcher cannot substitute the phone theme after HCF chooses a palette.
# -----------------------------------------------------------------------------
DRAWABLE.mkdir(parents=True, exist_ok=True)
files = {
    'widget_hcf_background_light.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <solid android:color="#FFFFFFFF" />\n    <stroke android:width="1dp" android:color="#FFB9CBD3" />\n    <corners android:radius="18dp" />\n</shape>\n''',
    'widget_hcf_background_dark.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <solid android:color="#FF12171C" />\n    <stroke android:width="1dp" android:color="#FF29404D" />\n    <corners android:radius="18dp" />\n</shape>\n''',
    'widget_hcf_background_amoled.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <solid android:color="#FF000000" />\n    <stroke android:width="1dp" android:color="#FF29404D" />\n    <corners android:radius="18dp" />\n</shape>\n''',
    'widget_hcf_action_background_light.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_pressed="true"><shape android:shape="rectangle"><solid android:color="#FFDDF5FB"/><stroke android:width="1dp" android:color="#FF00B8F0"/><corners android:radius="11dp"/></shape></item>\n    <item><shape android:shape="rectangle"><solid android:color="#FFF7FBFC"/><stroke android:width="1dp" android:color="#FFB9CBD3"/><corners android:radius="11dp"/></shape></item>\n</selector>\n''',
    'widget_hcf_action_background_dark.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_pressed="true"><shape android:shape="rectangle"><solid android:color="#FF1B313B"/><stroke android:width="1dp" android:color="#FF00B8F0"/><corners android:radius="11dp"/></shape></item>\n    <item><shape android:shape="rectangle"><solid android:color="#FF131C22"/><stroke android:width="1dp" android:color="#FF29404D"/><corners android:radius="11dp"/></shape></item>\n</selector>\n''',
    'widget_hcf_action_background_amoled.xml': '''<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_pressed="true"><shape android:shape="rectangle"><solid android:color="#FF10232B"/><stroke android:width="1dp" android:color="#FF00B8F0"/><corners android:radius="11dp"/></shape></item>\n    <item><shape android:shape="rectangle"><solid android:color="#FF080D11"/><stroke android:width="1dp" android:color="#FF29404D"/><corners android:radius="11dp"/></shape></item>\n</selector>\n''',
}
for name, content in files.items():
    path = DRAWABLE / name
    if path.exists():
        raise SystemExit(f'refusing to overwrite existing {path}')
    write(path, content)

# -----------------------------------------------------------------------------
# Release gate assertions for this feature.
# -----------------------------------------------------------------------------
verify = read(VERIFY)
verify = replace_once(
    verify,
    'platform_source = text(java_source / "HcfPlatform.java")\n',
    'platform_source = text(java_source / "HcfPlatform.java")\nwidget_source = text(java_source / "HcfWidget.java")\n',
    'verifier widget source binding',
)
verify = replace_once(
    verify,
    'require("URL-bar back button missing", \'android:id="@+id/urlBackButton"\' in text(source / "res/layout/activity_main.xml"))\n',
    '''require("URL-bar back button missing", 'android:id="@+id/urlBackButton"' in text(source / "res/layout/activity_main.xml"))\nrequire("widget app-theme preference missing", 'WIDGET_FOLLOW_APP_THEME = "widget_follow_app_theme"' in app_prefs)\nrequire("widget root settings category missing", '"Home-screen Widget"' in ui_source and '"Follow HCF app theme"' in ui_source)\nrequire("widget app-theme renderer missing", 'ThemeManager.webColorScheme(context)' in widget_source and 'systemPhoneDark()' in widget_source)\nrequire("widget theme changes do not refresh widget", 'AppPrefs.APP_THEME.equals(key)' in widget_source and 'AppPrefs.WIDGET_FOLLOW_APP_THEME.equals(key)' in widget_source)\nfor palette in ("light", "dark", "amoled"):\n    require(f"widget {palette} background missing", (source / f"res/drawable/widget_hcf_background_{palette}.xml").is_file())\n    require(f"widget {palette} action background missing", (source / f"res/drawable/widget_hcf_action_background_{palette}.xml").is_file())\n''',
    'widget release gate assertions',
)
write(VERIFY, verify)

print('Widget app-theme settings patch applied successfully.')
