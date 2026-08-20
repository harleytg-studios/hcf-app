from pathlib import Path
import runpy, sys, tempfile

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
p = out/'src/com/harleytg/forum/LogsActivity.java'
s = p.read_text(encoding='utf-8')
s = s.replace(
    '    @Override\n    @Override\n    protected void onActivityResult(',
    '    @Override\n    protected void onActivityResult('
)
p.write_text(s, encoding='utf-8')
