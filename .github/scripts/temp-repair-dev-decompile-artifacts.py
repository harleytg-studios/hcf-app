from pathlib import Path
import re, sys

if len(sys.argv) != 3:
    raise SystemExit('usage: repair.py <promoted-source-code> <stable-backup-source-code>')
OUT = Path(sys.argv[1])
STABLE = Path(sys.argv[2])
SRC = OUT / 'src/com/harleytg/forum'
SSRC = STABLE / 'src/com/harleytg/forum'

def method_span(text, pattern):
    m = re.search(pattern, text, re.M)
    if not m:
        raise RuntimeError('method not found: ' + pattern)
    start = m.start()
    brace = text.find('{', m.end() - 1)
    if brace < 0:
        raise RuntimeError('method brace not found: ' + pattern)
    depth = 0
    i = brace
    in_string = False
    quote = ''
    escape = False
    line_comment = False
    block_comment = False
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ''
        if line_comment:
            if c == '\n': line_comment = False
        elif block_comment:
            if c == '*' and n == '/': block_comment = False; i += 1
        elif in_string:
            if escape: escape = False
            elif c == '\\': escape = True
            elif c == quote: in_string = False
        else:
            if c == '/' and n == '/': line_comment = True; i += 1
            elif c == '/' and n == '*': block_comment = True; i += 1
            elif c in ('\"', "'"): in_string = True; quote = c
            elif c == '{': depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return start, i + 1
        i += 1
    raise RuntimeError('unclosed method: ' + pattern)

def extract(path, pattern):
    text = path.read_text(encoding='utf-8')
    a, b = method_span(text, pattern)
    return text[a:b]

def replace_method(path, pattern, body):
    text = path.read_text(encoding='utf-8')
    a, b = method_span(text, pattern)
    path.write_text(text[:a] + body + text[b:], encoding='utf-8')

# JADX emitted malformed try/finally around the document-provider stream.
replace_method(
    SRC / 'LogsActivity.java',
    r'^\s*protected void onActivityResult\(int i, int i2, Intent intent\)',
    extract(SSRC / 'LogsActivity.java', r'^\s*protected void onActivityResult\(int requestCode, int resultCode, Intent data\)')
)

# Preserve Dev Firebase loader class, replacing only JADX's broken async refresh body.
replace_method(
    SRC / 'FirebaseConfigLoader.java',
    r'^\s*static void refresh\(final Context context, final Callback callback\)',
    extract(SSRC / 'FirebaseConfigLoader.java', r'^\s*static void refresh\(Context context, Callback callback\)')
)

# Preserve Dev telemetry feature surface; replace only the malformed network-send method.
replace_method(
    SRC / 'TelemetryService.java',
    r'^\s*private static boolean postReport\(Context context, JSONObject jSONObject\)',
    extract(SSRC / 'TelemetryService.java', r'^\s*private static boolean postReport\(Context context, JSONObject report\)')
)
telemetry = (SRC / 'TelemetryService.java').read_text(encoding='utf-8')
old = 'TelemetryService.showTextDialog(r0, "Crash report preview", TelemetryService.previewPendingReport(activity, editText.getText().toString(), r10.isChecked()));'
new = 'TelemetryService.showTextDialog(activity, "Crash report preview", TelemetryService.previewPendingReport(activity, editText.getText().toString(), r10.isChecked()));'
if old not in telemetry:
    raise RuntimeError('Telemetry preview JADX artifact not found')
(SRC / 'TelemetryService.java').write_text(telemetry.replace(old, new, 1), encoding='utf-8')

# JADX lost the display-width temporary used by the right-edge drawer gesture.
main = (SRC / 'MainActivity.java').read_text(encoding='utf-8')
old = '''                    int max = Math.max(dp(64), Math.round(getResources().getDisplayMetrics().widthPixels * 0.16f));
                    if (this.drawerPanel.getVisibility() == 0 || this.drawerSwipeStartX < r0 - max) {'''
new = '''                    int width = getResources().getDisplayMetrics().widthPixels;
                    int max = Math.max(dp(64), Math.round(width * 0.16f));
                    if (this.drawerPanel.getVisibility() == 0 || this.drawerSwipeStartX < width - max) {'''
if old not in main:
    raise RuntimeError('MainActivity drawer-width JADX artifact not found')
(SRC / 'MainActivity.java').write_text(main.replace(old, new, 1), encoding='utf-8')

# Guard against the exact compile artifacts this repair is responsible for.
joined = '\n'.join((SRC / n).read_text(encoding='utf-8', errors='ignore') for n in [
    'LogsActivity.java', 'TelemetryService.java', 'FirebaseConfigLoader.java', 'MainActivity.java'])
for bad in ['TelemetryService.showTextDialog(r0,', 'drawerSwipeStartX < r0 - max', 'FirebaseConfigLoader.Callback.this.onResult']:
    if bad in joined:
        raise RuntimeError('unrepaired JADX artifact remains: ' + bad)
print('Targeted Java decompile repairs applied.')
