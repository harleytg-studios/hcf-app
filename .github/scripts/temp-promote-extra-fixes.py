from pathlib import Path

# 'out' is supplied by temp-promote-full-dev-to-stable.py.
p = Path(out)/'src/com/harleytg/forum/IdentityActivity.java'
s = p.read_text(encoding='utf-8')
old = '''    /* synthetic */ void m14x27753261(final String str) {\n        HttpsURLConnection httpsURLConnection;\n        HttpsURLConnection httpsURLConnection2 = null;'''
new = '''    /* synthetic */ void m14x27753261(final String str) {\n        HttpsURLConnection httpsURLConnection = null;\n        HttpsURLConnection httpsURLConnection2 = null;'''
if old not in s:
    raise SystemExit('IdentityActivity avatar connection declaration not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
