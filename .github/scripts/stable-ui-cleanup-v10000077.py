from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: stable-ui-cleanup-v10000077.py <promoted-source-code>')

root = Path(sys.argv[1])
settings = root / 'src/com/harleytg/forum/SettingsActivity.java'
if not settings.exists():
    raise SystemExit('SettingsActivity.java not found')

s = settings.read_text(encoding='utf-8')
s = s.replace('Required alerts, silent background alerts and Beta test alerts',
              'Required alerts, silent background alerts and notification tools')
s = s.replace('Beta test alerts', 'notification tools')
s = s.replace('Beta Test Alerts', 'Notification Tools')
s = s.replace('Stable • Preview Releases', 'Stable • Official Releases')
s = s.replace('Preview Releases', 'Official Releases')
s = s.replace('Preview releases', 'Official releases')
settings.write_text(s, encoding='utf-8')

# Catch the same public wording if it appears in another promoted source/resource.
for p in root.rglob('*'):
    if not p.is_file() or p.suffix.lower() not in {'.java', '.xml', '.txt', '.md', '.json'}:
        continue
    try:
        text = p.read_text(encoding='utf-8')
    except Exception:
        continue
    text = text.replace('Beta test alerts', 'notification tools')
    text = text.replace('Beta Test Alerts', 'Notification Tools')
    text = text.replace('Preview Releases', 'Official Releases')
    text = text.replace('Preview releases', 'Official releases')
    p.write_text(text, encoding='utf-8')

print('Stable public UI wording cleanup applied')
