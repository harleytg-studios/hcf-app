from pathlib import Path
import re, runpy, sys, tempfile

COMPAT_MARKERS = r'''logs=extract(STROOT/'src/com/harleytg/forum/LogsActivity.java',r'^\s*private void renderLogs\(\)').replace('private void renderLogs()','public void renderLogs()',1)
stable_res=STROOT/'res'; logo=stable_res/'drawable-nodpi/htg_app_logo.png' '''

base = Path(__file__).with_name('temp-promote-full-dev-to-stable-base.py')
text = base.read_text(encoding='utf-8')
old = (
    "logs=extract(STROOT/'src/com/harleytg/forum/LogsActivity.java',"
    + r"r'^\s*private void renderLogs\(\)'"
    + ").replace('private void renderLogs()','public void renderLogs()',1)"
)
new = (
    "logs=extract(STROOT/'src/com/harleytg/forum/LogsActivity.java',r'^\\s*(?:private|public) void renderLogs\\(\\)')\n"
    "logs=re.sub(r'^\\s*(?:private|public) void renderLogs\\(\\)', '    public void renderLogs()', logs, count=1, flags=re.M)"
)
text = text.replace(old, new)
old_logo = "stable_res=STROOT/'res'; logo=stable_res/" + "'drawable-nodpi/htg_app_logo.png'"
new_logo = old_logo + "\nif not logo.exists(): logo=stable_res/'drawable-nodpi-v4/htg_app_logo.png'"
text = text.replace(old_logo, new_logo)

with tempfile.NamedTemporaryFile('w', suffix='.py', delete=False, encoding='utf-8') as f:
    f.write(text)
    temp_path = f.name
runpy.run_path(temp_path, run_name='__main__')

out = Path(sys.argv[2])

# Deduplicate the recovered LogsActivity method annotation.
p = out/'src/com/harleytg/forum/LogsActivity.java'
s = p.read_text(encoding='utf-8')
needle = 'protected void onActivityResult('
idx = s.find(needle)
if idx < 0:
    raise SystemExit('LogsActivity onActivityResult not found after promotion')
block_start = s.rfind('}', 0, idx) + 1
segment = s[block_start:idx]
overrides = list(re.finditer(r'@Override', segment))
if len(overrides) > 1:
    for match in reversed(overrides[:-1]):
        a = block_start + match.start()
        b = block_start + match.end()
        s = s[:a] + s[b:]
p.write_text(s, encoding='utf-8')

# Replace the small JADX-damaged JobService outright. This preserves its behavior,
# catches sync failures, and always completes the asynchronous JobScheduler job.
p = out/'src/com/harleytg/forum/NotificationSyncJobService.java'
p.write_text(r'''package com.harleytg.forum;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

public final class NotificationSyncJobService extends JobService {
    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                try {
                    syncNow();
                } catch (Throwable t) {
                    AppLogger.warn(NotificationSyncJobService.this, "background_notification_sync",
                            "job-failed | " + t.getClass().getSimpleName());
                } finally {
                    try { jobFinished(params, false); } catch (Throwable ignored) {}
                }
            }
        });
        return true;
    }

    private void syncNow() throws Exception {
        SharedPreferences prefs = getSharedPreferences("hcf_app", 0);
        if (!prefs.getBoolean("background_notification_sync", true)) return;
        String userId = prefs.getString("session_user_id", "");
        if (userId == null || userId.trim().isEmpty()) {
            AppLogger.info(this, "background_notification_sync", "no-session");
            return;
        }
        String host = prefs.getString("active_host", "forum.harleytg.com");
        ForumNotificationSync.perform(this,
                ForumUrlRouter.isForumHost(host) ? host : "forum.harleytg.com",
                userId.trim(), "fallback-job");
    }
}
''', encoding='utf-8')

# The recovered notification sync method leaves the delivery count undefined if
# detailed alert delivery throws and the generic fallback is used. A zero default
# accurately means no detailed alerts were delivered while preserving the fallback.
p = out/'src/com/harleytg/forum/ForumNotificationSync.java'
s = p.read_text(encoding='utf-8')
s2, count = re.subn(r'(?m)^(\s*)int i;\s*$', r'\1int i = 0;', s, count=1)
if count != 1:
    raise SystemExit('ForumNotificationSync delivery count declaration not found')
p.write_text(s2, encoding='utf-8')

# Repair the remaining JADX-only MainActivity compiler issues without changing UI.
p = out/'src/com/harleytg/forum/MainActivity.java'
s = p.read_text(encoding='utf-8')

old = '''    private void showChecking(final String str) {\n        boolean z = true;\n        final int i = this.connectionUiGeneration + 1;\n        this.connectionUiGeneration = i;\n        if (str == null || str.trim().isEmpty()) {\n            str = "forum.harleytg.com";\n        }'''
new = '''    private void showChecking(final String requestedHost) {\n        boolean z = true;\n        final int i = this.connectionUiGeneration + 1;\n        this.connectionUiGeneration = i;\n        final String str = (requestedHost == null || requestedHost.trim().isEmpty())\n                ? "forum.harleytg.com" : requestedHost;'''
if old not in s:
    raise SystemExit('MainActivity showChecking recovery block not found')
s = s.replace(old, new, 1)

old = '''        String url;\n        if (i == this.connectionUiGeneration && this.startupProgress.getVisibility() == 0) {'''
new = '''        String url = null;\n        if (i == this.connectionUiGeneration && this.startupProgress.getVisibility() == 0) {'''
if old not in s:
    raise SystemExit('MainActivity timeout URL declaration not found')
s = s.replace(old, new, 1)

old = '''    /* synthetic */ void m77lambda$loadIdentityAvatar$71$comharleytgforumdevMainActivity(final String str) {\n        HttpsURLConnection httpsURLConnection;\n        HttpsURLConnection httpsURLConnection2 = null;'''
new = '''    /* synthetic */ void m77lambda$loadIdentityAvatar$71$comharleytgforumdevMainActivity(final String str) {\n        HttpsURLConnection httpsURLConnection = null;\n        HttpsURLConnection httpsURLConnection2 = null;'''
if old not in s:
    raise SystemExit('MainActivity avatar connection declaration not found')
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
