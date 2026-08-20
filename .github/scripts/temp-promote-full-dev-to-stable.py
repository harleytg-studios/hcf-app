from pathlib import Path
import re, runpy, sys, tempfile

# Text markers kept so the historical workflow's compatibility patch still succeeds.
# They are deliberately inert; the active replacement strings below are split so
# that the workflow cannot rewrite Python syntax inside them.
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
idx = s.find(needle)
block_start = s.rfind('}', 0, idx) + 1
if s[block_start:idx].count('@Override') > 1:
    raise SystemExit('duplicate Override remains before LogsActivity onActivityResult')
p.write_text(s, encoding='utf-8')

# Repair the JADX-damaged asynchronous JobService worker. The recovered source
# declared syncNow() throws Exception but did not catch it, and also lost the
# required jobFinished() call from its empty nested finally block.
p = out/'src/com/harleytg/forum/NotificationSyncJobService.java'
s = p.read_text(encoding='utf-8')
pattern = re.compile(
    r'(?ms)^\s*/\* renamed from: lambda\$onStartJob\$0\$.*?\*/\s*\n'
    r'\s*/\* synthetic \*/ void m128x38509368\(JobParameters jobParameters\) \{.*?^\s*\}',
)
replacement = r'''    /* synthetic */ void m128x38509368(JobParameters jobParameters) {
        try {
            syncNow();
        } catch (Throwable t) {
            AppLogger.warn(this, "background_notification_sync", "job-failed | " + t.getClass().getSimpleName());
        } finally {
            try {
                jobFinished(jobParameters, false);
            } catch (Throwable ignored) {
            }
        }
    }'''
s2, count = pattern.subn(replacement, s, count=1)
if count != 1:
    # Fallback anchored from the synthetic method declaration to syncNow().
    start = s.find('/* synthetic */ void m128x38509368(JobParameters jobParameters)')
    end = s.find('    private void syncNow()', start)
    if start < 0 or end < 0:
        raise SystemExit('NotificationSyncJobService worker not found after promotion')
    prefix_start = s.rfind('    /* renamed from:', 0, start)
    if prefix_start < 0:
        prefix_start = start
    s2 = s[:prefix_start] + replacement + '\n\n' + s[end:]
p.write_text(s2, encoding='utf-8')
