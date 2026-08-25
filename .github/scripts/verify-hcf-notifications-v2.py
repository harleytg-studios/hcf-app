#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('source code')
engine = (root / 'src/com/harleytg/forum/HcfNotificationEngine.java').read_text(encoding='utf-8')
ui = (root / 'src/com/harleytg/forum/HcfSubActivities.java').read_text(encoding='utf-8')

required_engine = [
    'CHANNEL_GROUP_ID = "hcf_notification_channels_v2"',
    'CHANNEL_ID = "hcf_alerts_v2"',
    'SILENT_CHANNEL_ID = "hcf_silent_alerts_v2"',
    'TEST_CHANNEL_ID = "hcf_test_alerts_v2"',
    'FORUM_GROUP_KEY = "hcf_alerts_group_v2"',
    'SILENT_STATUS_GROUP_KEY = "hcf_silent_status_group_v2"',
    'FORUM_SUMMARY_ID = 41072',
    'SERVICE_NOTIFICATION_ID = 41070',
    'private static final int JOB_ID = 41071',
    'Audible HCF messages, mentions, replies, forum activity and important app alerts. Controlled in Android notification settings. App silent controls never affect this channel.',
    'Always-silent live-service and status channel. The in-app toggle can hide the ongoing live-service notification and optional status alerts. Real forum alerts use HCF Alerts.',
    'Development and Beta notification tests only. Never carries real forum alerts or the live-service notification.',
]
for item in required_engine:
    if item not in engine: raise SystemExit('Missing Notifications v2 fragment: ' + item)

for legacy in [
    'hcf_alerts_v1','hcf_silent_alerts_v1','hcf_test_alerts_v1','forum_messages_heads_up_v2',
    'forum_messages','app_updates_v1','hcf_background_v2','instant_notification_service_v1',
    'hcf_passive_silent_v1','hcf_messages','hcf_forum_activity','hcf_live_service_v1']:
    if legacy not in engine: raise SystemExit('Legacy cleanup missing: ' + legacy)

for stale in [
    'live_service_preserved', 'live service preserved',
    'required live-service notification stays on',
    'optional quiet status notifications only; it never controls the foreground service',
    'The toggle hides optional silent status alerts only']:
    if stale in engine or stale in ui: raise SystemExit('Outdated silence behavior remains: ' + stale)

cancel_start = engine.index('static void cancelOptionalSilentAlerts(Context context) {')
cancel_end = engine.index('static boolean postNotificationServiceTest', cancel_start)
cancel = engine[cancel_start:cancel_end]
if 'SERVICE_NOTIFICATION_ID)' not in cancel: raise SystemExit('Silent cancel path does not explicitly cancel 41070')
if '!= HcfNotificationEngine.InstantNotificationService.SERVICE_NOTIFICATION_ID' in cancel:
    raise SystemExit('Silent cancel path still preserves 41070')

service_start = engine.index('public static final class InstantNotificationService extends Service {')
service_end = engine.index('// ---- NotificationSyncJobService.java ----', service_start)
service = engine[service_start:service_end]
if service.count('NotificationHelper.silencePassiveEnabled(') < 6:
    raise SystemExit('InstantNotificationService silence gates are incomplete')

scheduler_start = engine.index('final class NotificationSyncScheduler {')
scheduler_end = engine.index('// ---- HcfNotificationActions.java ----', scheduler_start)
scheduler = engine[scheduler_start:scheduler_end]
guard = scheduler.find('NotificationHelper.silencePassiveEnabled(context)')
start = scheduler.find('InstantNotificationService.start(context)')
if guard < 0 or (start >= 0 and guard > start):
    raise SystemExit('Scheduler can restart FGS before silence guard')
if 'schedule(context);' not in scheduler[guard:start if start >= 0 else len(scheduler)]:
    raise SystemExit('Silenced scheduler path does not retain JobScheduler fallback')

summary_start = engine.index('private static void postGroupSummary(')
summary_end = engine.index('private static void broadcastEvent(', summary_start)
summary = engine[summary_start:summary_end]
if 'new Notification.Builder(context, CHANNEL_ID)' not in summary or '.setGroup(FORUM_GROUP_KEY)' not in summary:
    raise SystemExit('Forum summary is not on HCF Alerts/FORUM_GROUP_KEY')
if 'SILENT_CHANNEL_ID' in summary or 'canPostOptionalSilentAlert' in summary:
    raise SystemExit('Forum summary is still tied to Silent Alerts')

service_notification_start = engine.index('static Notification buildInstantServiceNotification(')
service_notification_end = engine.index('static synchronized int recordForumNotificationCount(', service_notification_start)
service_notification = engine[service_notification_start:service_notification_end]
if 'new Notification.Builder(context, SILENT_CHANNEL_ID)' not in service_notification:
    raise SystemExit('FGS 41070 is not built on HCF Silent Alerts')
if '.setGroup(' in service_notification:
    raise SystemExit('FGS notification must be ungrouped')

main_start = ui.index('private View mainAlertsCard() {')
main_end = ui.index('private String hcfAlertSyncAge(', main_start)
main = ui[main_start:main_end]
if 'Silence HCF' in main or 'silence_hcf_silent_alerts' in main:
    raise SystemExit('HCF Alerts panel contains a silence toggle')

for fragment in [
    'toggle("Silence HCF Silent Alerts"',
    'getBoolean(AppPrefs.SILENCE_BACKGROUND_SERVICE_NOTIFICATION, false)',
    'HCF Silent Alerts silenced. Live-service notification hidden; background delivery may be delayed. Real HCF Alerts stay on.',
    'HCF Silent Alerts enabled. Live-service notification can run when background sync is on.',
    'Real HCF Alerts (messages, mentions, replies, updates) are never affected.',
    'Real forum alerts (messages, mentions, replies, updates) use HCF Alerts. Controlled only in Android notification settings. App silent controls never affect this channel.',
]:
    if fragment not in ui: raise SystemExit('Settings UI missing: ' + fragment)

if ui.count('"silence_hcf_silent_alerts"') != 2:
    raise SystemExit('Expected exactly one Silent toggle target plus one search entry')
if '"hcf_alerts_v1"' in ui or '"hcf_silent_alerts_v1"' in ui or '"hcf_test_alerts_v1"' in ui:
    raise SystemExit('Settings UI still references v1 notification channels')

print('HCF Notifications v2 verification: PASS')
