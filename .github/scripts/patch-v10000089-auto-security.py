from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-v10000089-auto-security.py <source-code-root>")

root = Path(sys.argv[1])
main = root / "src/com/harleytg/forum/MainActivity.java"
identity = root / "src/com/harleytg/forum/IdentityActivity.java"
security = root / "src/com/harleytg/forum/ForumSecurity.java"
settings = root / "src/com/harleytg/forum/SettingsActivity.java"

# ---------------------------------------------------------------------------
# MainActivity: make the Beta footer build-driven and auto-sync the signed-in
# user's /security data in an off-screen same-origin iframe. The visible WebView
# never navigates away from the user's current page.
# ---------------------------------------------------------------------------
text = main.read_text(encoding="utf-8")
text = text.replace(
    'textView.setText("Harley\'s Clan Forum v1.0 [Development Build / Beta]");',
    'textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);',
)
text = text.replace(
    'textView.setText("Harley\'s Clan Forum v1.0 [Stable]");',
    'textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);',
)

if "var autoSecurity=function()" not in text:
    old_vars = "var lastIdentity='',lastSecurity='',lastRoute='';"
    new_vars = "var lastIdentity='',lastSecurity='',lastRoute='',lastAutoSecurityAt=0,lastAutoSecurityKey='',autoSecurityBusy=false;"
    if old_vars not in text:
        raise SystemExit("MainActivity bridge state marker not found")
    text = text.replace(old_vars, new_vars, 1)

    marker = "var reportRoute=function(){"
    if marker not in text:
        raise SystemExit("MainActivity reportRoute marker not found")

    auto_security = (
        "var autoSecurity=function(){try{"
        "if(autoSecurityBusy||!window.app||!app.session||!app.session.user||!document.body)return;"
        "var cp=String(location.pathname||'');if(cp.indexOf('/u/')===0&&cp.indexOf('/security')===cp.length-9)return;"
        "var u=app.session.user,slug=String(val(u,'slug','')||val(u,'username','')||'').trim();if(!slug)return;"
        "var key=String(val(u,'id','')||slug)+'@'+String(location.host||'');var now=Date.now();"
        "if(key===lastAutoSecurityKey&&now-lastAutoSecurityAt<600000)return;"
        "lastAutoSecurityKey=key;lastAutoSecurityAt=now;autoSecurityBusy=true;"
        "var f=document.createElement('iframe');f.setAttribute('aria-hidden','true');f.tabIndex=-1;"
        "f.style.cssText='position:fixed!important;left:-10000px!important;top:-10000px!important;width:1px!important;height:1px!important;border:0!important;opacity:0!important;pointer-events:none!important;';"
        "f.src='/u/'+encodeURIComponent(slug)+'/security?hcf_native_sync=1';"
        "var done=false,tries=0;"
        "var finish=function(ok){if(done)return;done=true;autoSecurityBusy=false;if(!ok)lastAutoSecurityAt=Date.now()-540000;try{if(f.parentNode)f.parentNode.removeChild(f);}catch(e){}};"
        "var read=function(){try{tries++;var d=f.contentDocument||(f.contentWindow&&f.contentWindow.document);"
        "if(!d||!d.body){if(tries<24)setTimeout(read,400);else finish(false);return;}"
        "var page=d.querySelector('.UserSecurityPage');if(!page||tries<6){if(tries<24)setTimeout(read,400);else finish(false);return;}"
        "var bodyText=String(d.body.innerText||'').toLowerCase();var providers=[];"
        "var addp=function(x){x=String(x||'').trim();if(x&&providers.indexOf(x)<0)providers.push(x);};"
        "var nodes=d.querySelectorAll('button,a,li,.Form-group,.Setting,.LoginProvider');"
        "var pm={discord:'Discord',google:'Google',github:'GitHub',microsoft:'Microsoft',apple:'Apple',facebook:'Facebook',twitter:'Twitter',steam:'Steam'};"
        "for(var ni=0;ni<nodes.length;ni++){var nt=String(nodes[ni].innerText||nodes[ni].textContent||'').toLowerCase();"
        "if(!(nt.indexOf('disconnect')>=0||nt.indexOf('connected')>=0||nt.indexOf('unlink')>=0||nt.indexOf('linked')>=0))continue;"
        "for(var pk in pm){if(nt.indexOf(pk)>=0)addp(pm[pk]);}}"
        "var sessions=d.querySelectorAll('.AccessTokensList-item').length;"
        "var activeSessions=d.querySelectorAll('.AccessTokensList-item--active').length;"
        "var data={seen:true,path:'/u/'+slug+'/security',sessionCount:Number(sessions||0),activeSessionCount:Number(activeSessions||0),providers:providers,"
        "passwordControls:(bodyText.indexOf('password')>=0),emailControls:(bodyText.indexOf('email')>=0),"
        "twoFactorControls:(bodyText.indexOf('two-factor')>=0||bodyText.indexOf('two factor')>=0||bodyText.indexOf('2fa')>=0||bodyText.indexOf('authenticator')>=0)};"
        "HCFNative.updateSecuritySummary(JSON.stringify(data),String(location.host||''));finish(true);"
        "}catch(e){if(tries<24)setTimeout(read,400);else finish(false);}};"
        "f.onload=function(){setTimeout(read,500);};document.body.appendChild(f);"
        "setTimeout(function(){if(!done)read();},1500);setTimeout(function(){if(!done)finish(false);},14000);"
        "}catch(e){autoSecurityBusy=false;}};"
    )
    text = text.replace(marker, auto_security + marker, 1)

    old_sync = "var sync=function(){reportRoute();syncIdentity();syncSecurity();fixSecurityLabels();};"
    new_sync = "var sync=function(){reportRoute();syncIdentity();syncSecurity();autoSecurity();fixSecurityLabels();};"
    if old_sync not in text:
        raise SystemExit("MainActivity bridge sync marker not found")
    text = text.replace(old_sync, new_sync, 1)

main.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Account & Identity copy: automatic sync is now the default. Opening Account
# Security is still available for management, not a prerequisite for detection.
# ---------------------------------------------------------------------------
text = identity.read_text(encoding="utf-8")
text = text.replace(
    "Open Account Security once to sync linked sign-in providers.",
    "Linked sign-in providers sync automatically while the forum is signed in.",
)
text = text.replace(
    'sectionTitle("Sign-in & security", "A safe summary from your forum security page")',
    'sectionTitle("Sign-in & security", "A safe summary synced from your forum account")',
)
text = text.replace(
    'snapshot2.seen ? snapshot2.sessionLabel() : "Open Account Security once to sync"',
    'snapshot2.seen ? snapshot2.sessionLabel() : "Syncing automatically"',
)
identity.write_text(text, encoding="utf-8")

text = security.read_text(encoding="utf-8")
text = text.replace('return "Open Account Security to sync";', 'return "Syncing automatically";')
text = text.replace('return !this.seen ? "Not synced yet"', 'return !this.seen ? "Waiting for automatic sync"')
security.write_text(text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Notifications settings redesign.
# - HCF Alerts owns Background notification sync.
# - HCF Silent Alerts is described only as the silent service-status channel.
# - HCF Test Alerts is clearly developer-only.
# - The expanded HCF Alerts panel becomes a compact, phone-friendly status UI.
# ---------------------------------------------------------------------------
text = settings.read_text(encoding="utf-8")
text = text.replace(
    'new SettingTarget("background_notification_sync", "Background notification sync", "notification sync background silent alerts", "notifications", "silent_alerts")',
    'new SettingTarget("background_notification_sync", "Background notification sync", "notification sync background HCF Alerts real forum alerts outside app closed app", "notifications", "hcf_alerts")',
)
text = text.replace(
    'new SettingTarget("silence_hcf_silent_alerts", "Silence HCF Silent Alerts", "silent background notification alerts", "notifications", "silent_alerts")',
    'new SettingTarget("silence_hcf_silent_alerts", "Disable HCF Silent Alerts", "silent service status background notification", "notifications", "silent_alerts")',
)
text = text.replace(
    'settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Required main alerts • messages, mentions, replies and important activity", mainAlertsCard(), shouldExpand("hcf_alerts", true)));',
    'settingsContent.addView(connectedSettingsPanel("HCF Alerts", "Real forum notifications • background delivery", mainAlertsCard(), shouldExpand("hcf_alerts", true)));',
)
text = text.replace(
    'settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Background sync, service status and passive notifications", silentAlertsCard(), shouldExpand("silent_alerts", false)));',
    'settingsContent.addView(connectedSettingsPanel("HCF Silent Alerts", "Silent service-status channel only", silentAlertsCard(), shouldExpand("silent_alerts", false)));',
)
text = text.replace(
    'settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", channelDisplayName(effectiveUpdateChannel()) + " test channel • controls live in Developer Tools", testAlertsInfoCard(), shouldExpand("test_alerts", false)));',
    'settingsContent.addView(connectedSettingsPanel("HCF Test Alerts", "Dev/Beta diagnostics only", testAlertsInfoCard(), shouldExpand("test_alerts", false)));',
)

if "Real forum alerts" not in text:
    start = text.index('    private View mainAlertsCard() {')
    end = text.index('    private View themeModeSelector()', start)
    replacement = r'''    private View mainAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        boolean permissionAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0;
        boolean channelAvailable = NotificationHelper.channelImportance(this, "hcf_alerts_v1") != 0;
        boolean ready = permissionAllowed && channelAvailable;

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(16);
        hero.setBackgroundResource(R.drawable.quick_action_background);
        hero.setPadding(dp(13), dp(11), dp(11), dp(11));
        ImageView heroIcon = settingsSectionIcon(R.drawable.fa_bell);
        LinearLayout.LayoutParams heroIconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        heroIconLp.rightMargin = dp(11);
        hero.addView(heroIcon, heroIconLp);
        LinearLayout heroLabels = new LinearLayout(this);
        heroLabels.setOrientation(LinearLayout.VERTICAL);
        TextView heroTitle = text("Real forum alerts", 14, getColor(R.color.hcf_text));
        heroTitle.setTypeface(null, 1);
        heroLabels.addView(heroTitle);
        heroLabels.addView(text("Messages • mentions • replies • important activity", 10, getColor(R.color.hcf_muted)));
        hero.addView(heroLabels, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView readyChip = text(ready ? "READY" : "CHECK", 9, getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
        readyChip.setTypeface(null, 1);
        readyChip.setGravity(17);
        readyChip.setPadding(dp(8), dp(4), dp(8), dp(4));
        readyChip.setBackgroundResource(R.drawable.status_chip_background);
        hero.addView(readyChip);
        card.addView(hero);

        notificationStatus = text("Checking HCF Alerts status…", 11, getColor(R.color.hcf_muted));
        notificationStatus.setPadding(dp(2), dp(7), dp(2), dp(5));
        card.addView(notificationStatus);

        card.addView(settingsSubsectionHeader("Background delivery", "Keep real HCF Alerts checking while the app is not open", R.drawable.fa_bell));
        Switch sync = target(toggle("Background notification sync", prefs.getBoolean("background_notification_sync", true)), "background_notification_sync");
        sync.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("background_notification_sync", checked).apply();
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_background_sync", Boolean.toString(checked));
            Toast.makeText(this, checked ? "Background HCF Alerts enabled." : "Background checking paused. HCF Alerts channel stays available.", Toast.LENGTH_SHORT).show();
            refreshStatusLabels();
        });
        card.addView(sync);
        card.addView(text("Recommended: keep this ON so new forum alerts can be discovered when HCF is in the background.", 10, getColor(R.color.hcf_muted)));

        card.addView(settingsSubsectionHeader("Android access", "Permission and channel status", R.drawable.fa_shield));
        if (!permissionAllowed) {
            card.addView(target(actionButton("Allow Notification Permission", v -> requestNotificationPermissionIfNeeded()), "allow_android_notification_permission"));
        } else {
            TextView granted = target(text("✓ Android notification permission allowed", 11, getColor(R.color.hcf_accent_text)), "allow_android_notification_permission");
            granted.setTypeface(null, 1);
            granted.setPadding(dp(2), dp(6), dp(2), dp(6));
            card.addView(granted);
        }
        card.addView(notificationChannelStatusRow("HCF Alerts", "Required real-alert channel • never controlled by HCF silence settings", "hcf_alerts_v1"));
        card.addView(actionButton("Open HCF Alerts Android Settings", v -> NotificationHelper.openChannelSettings(this)));
        return card;
    }

    private View silentAlertsCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(settingsInfoCard("Service-status channel",
                "HCF Silent Alerts only carries quiet background-service status. It never carries direct messages, mentions or replies.",
                R.drawable.fa_bell));
        Switch silence = target(toggle("Disable HCF Silent Alerts", prefs.getBoolean("silence_background_service_notification", false)), "silence_hcf_silent_alerts");
        silence.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("silence_background_service_notification", checked).apply();
            NotificationHelper.refreshChannels(this);
            NotificationSyncScheduler.apply(this);
            AppLogger.info(this, "setting_silence_passive_notifications", Boolean.toString(checked));
            Toast.makeText(this, checked ? "HCF Silent Alerts disabled." : "HCF Silent Alerts enabled • silent.", Toast.LENGTH_LONG).show();
        });
        card.addView(silence);
        card.addView(text("This affects the silent service-status channel only. Android may limit continuous background checking when the service status is disabled.", 10, getColor(R.color.hcf_muted)));
        card.addView(notificationChannelRow("HCF Silent Alerts", "Silent • service status only", "hcf_silent_alerts_v1"));
        return card;
    }

    private View testAlertsInfoCard() {
        LinearLayout card = card();
        NotificationHelper.createChannel(this);
        card.addView(settingsInfoCard("Developer test channel",
                "Use this only to test notification delivery. It never carries real forum alerts or background-service status.",
                R.drawable.fa_bug));
        card.addView(notificationChannelRow("HCF Test Alerts", "Dev/Beta notification tests", "hcf_test_alerts_v1"));
        card.addView(target(actionButton("Open Developer Notification Tools", v -> navigateToSettingKey("notification_test_console")), "open_developer_tools"));
        return card;
    }

'''
    text = text[:start] + replacement + text[end:]

old_refresh = '''        if (notificationStatus != null) {
            NotificationHelper.createChannel(this);
            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.headsUpChannelReady(this);
            notificationStatus.setText("Status: " + NotificationHelper.status(this) + " • channel importance=" + NotificationHelper.channelImportance(this));
            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
        }'''
new_refresh = '''        if (notificationStatus != null) {
            NotificationHelper.createChannel(this);
            boolean ready = NotificationHelper.canPost(this) && NotificationHelper.channelImportance(this) != 0;
            boolean background = prefs.getBoolean("background_notification_sync", true);
            String delivery = background ? "Background delivery ON" : "Background delivery paused";
            notificationStatus.setText((ready ? "HCF Alerts ready" : NotificationHelper.status(this)) + " • " + delivery);
            notificationStatus.setTextColor(getColor(ready ? R.color.hcf_accent_text : R.color.hcf_warning));
        }'''
if old_refresh in text:
    text = text.replace(old_refresh, new_refresh, 1)

settings.write_text(text, encoding="utf-8")

# Validation so a build fails instead of silently shipping old behavior.
main_text = main.read_text(encoding="utf-8")
identity_text = identity.read_text(encoding="utf-8")
security_text = security.read_text(encoding="utf-8")
settings_text = settings.read_text(encoding="utf-8")
checks = [
    ("textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);" in main_text, "build-driven drawer footer"),
    ("var autoSecurity=function()" in main_text, "automatic security sync function"),
    ("hcf_native_sync=1" in main_text, "off-screen security route"),
    ("autoSecurity();fixSecurityLabels();" in main_text, "automatic security sync wiring"),
    ("Linked sign-in providers sync automatically" in identity_text, "automatic linked-account copy"),
    ("Syncing automatically" in identity_text, "automatic security status copy"),
    ("Open Account Security once to sync" not in identity_text, "manual-sync instruction removed"),
    ("Syncing automatically" in security_text, "ForumSecurity automatic status"),
    ('"notifications", "hcf_alerts")' in settings_text, "background sync search route moved to HCF Alerts"),
    ("Real forum alerts" in settings_text, "user-friendly HCF Alerts hero"),
    ("Background delivery" in settings_text, "HCF Alerts background delivery section"),
    ("Service-status channel" in settings_text, "HCF Silent Alerts separation"),
    ("Developer test channel" in settings_text, "HCF Test Alerts separation"),
    ("Open HCF Alerts Android Settings" in settings_text, "HCF Alerts Android settings shortcut"),
]
for passed, label in checks:
    if not passed:
        raise SystemExit("validation failed: " + label)

print("v10000089 automatic security sync + Notifications UI patch applied")
