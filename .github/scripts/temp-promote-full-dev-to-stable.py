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
