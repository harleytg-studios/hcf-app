#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: build-beta-v10000080-repair.py <source-code-root>")

root = Path(sys.argv[1])

# Release notes: ensure user-visible build identity matches this build.
release = root / 'src/com/harleytg/forum/ReleaseNotes.java'
if release.exists():
    s = release.read_text(encoding='utf-8')
    for old in ('10000072', '10000077', '10000078', '10000079'):
        s = s.replace(old, '10000080')
    release.write_text(s, encoding='utf-8')

# LogsActivity: restore document-provider export flow lost in decompilation.
logs = root / 'src/com/harleytg/forum/LogsActivity.java'
s = logs.read_text(encoding='utf-8')
marker = '    @Override // android.app.Activity\n    protected void onActivityResult'
if marker in s:
    start = s.index(marker)
    end = s.index('    private View buildRecoveryView', start)
    fixed = '''    @Override\n    protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n        super.onActivityResult(requestCode, resultCode, data);\n        if (requestCode != EXPORT_TEXT || resultCode != RESULT_OK || data == null || data.getData() == null) return;\n        pendingExportUri = data.getData();\n        String text = visiblePlainText == null ? "" : visiblePlainText;\n        try (OutputStream out = getContentResolver().openOutputStream(pendingExportUri, "w")) {\n            if (out == null) throw new IllegalStateException("No output stream");\n            out.write(text.getBytes(StandardCharsets.UTF_8));\n            out.flush();\n            AppLogger.info(this, MODE_DIAGNOSTICS.equals(currentMode) ? "diagnostics_exported" : "logs_exported", "document-provider");\n            Toast.makeText(this, "Export complete.", Toast.LENGTH_SHORT).show();\n        } catch (Throwable t) {\n            AppLogger.error(this, "logs_export_failed", t.getClass().getSimpleName());\n            Toast.makeText(this, "Could not export this content.", Toast.LENGTH_LONG).show();\n        } finally {\n            pendingExportUri = null;\n        }\n    }\n\n'''
    logs.write_text(s[:start] + fixed + s[end:], encoding='utf-8')

# FirebaseConfigLoader: restore checked-exception handling and callback delivery.
firebase = root / 'src/com/harleytg/forum/FirebaseConfigLoader.java'
s = firebase.read_text(encoding='utf-8')
marker = '    static /* synthetic */ void lambda$refresh$1'
if marker in s:
    start = s.index(marker)
    end = s.index('    static void clearRemoteCache', start)
    fixed = '''    static /* synthetic */ void lambda$refresh$1(String urlText, SharedPreferences sharedPreferences, Context context, final Callback callback) {\n        Config result;\n        String message;\n        HttpURLConnection connection = null;\n        try {\n            connection = (HttpURLConnection) new URL(urlText).openConnection();\n            connection.setConnectTimeout(8000);\n            connection.setReadTimeout(8000);\n            connection.setInstanceFollowRedirects(true);\n            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 FirebaseConfig");\n            int code = connection.getResponseCode();\n            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);\n            String raw = readAll(connection.getInputStream());\n            Config parsed = parse(raw, "HTTPS config");\n            if (parsed == null || !parsed.isValid()) throw new IllegalStateException("Invalid Firebase config");\n            sharedPreferences.edit().putString("firebase_config_cache", raw).putString("firebase_config_source", "HTTPS config").apply();\n            result = parsed;\n            message = "Firebase config refreshed from HTTPS.";\n        } catch (Throwable t) {\n            result = load(context);\n            message = "Remote refresh failed; kept " + (result == null ? "no config" : result.source) + ".";\n            AppLogger.error(context, "firebase_config_refresh", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));\n        } finally {\n            if (connection != null) connection.disconnect();\n        }\n        final Config callbackConfig = result;\n        final String callbackMessage = message;\n        new Handler(Looper.getMainLooper()).post(new Runnable() {\n            @Override public void run() { callback.onResult(callbackConfig, callbackMessage); }\n        });\n    }\n\n'''
    firebase.write_text(s[:start] + fixed + s[end:], encoding='utf-8')

# MainActivity: restore locals/final values mangled by decompilation.
main = root / 'src/com/harleytg/forum/MainActivity.java'
s = main.read_text(encoding='utf-8')
s = s.replace('this.drawerSwipeStartX < r0 - max', 'this.drawerSwipeStartX < getResources().getDisplayMetrics().widthPixels - max')
old = '''    private void showChecking(final String str) {\n        boolean z = true;\n        final int i = this.connectionUiGeneration + 1;\n        this.connectionUiGeneration = i;\n        if (str == null || str.trim().isEmpty()) {\n            str = "forum.harleytg.com";\n        }'''
new = '''    private void showChecking(final String requestedHost) {\n        boolean z = true;\n        final int i = this.connectionUiGeneration + 1;\n        this.connectionUiGeneration = i;\n        final String str = (requestedHost == null || requestedHost.trim().isEmpty())\n                ? "forum.harleytg.com" : requestedHost;'''
if old not in s:
    raise SystemExit('showChecking reconstruction marker not found')
s = s.replace(old, new, 1)
s = s.replace('        String url;\n        if (i == this.connectionUiGeneration', '        String url = null;\n        if (i == this.connectionUiGeneration', 1)
s = s.replace('        HttpsURLConnection httpsURLConnection;\n        HttpsURLConnection httpsURLConnection2 = null;', '        HttpsURLConnection httpsURLConnection = null;\n        HttpsURLConnection httpsURLConnection2 = null;', 1)
main.write_text(s, encoding='utf-8')

# IdentityActivity: initialize the avatar HTTPS connection before guarded creation.
identity = root / 'src/com/harleytg/forum/IdentityActivity.java'
s = identity.read_text(encoding='utf-8')
s = s.replace('        HttpsURLConnection httpsURLConnection;\n        HttpsURLConnection httpsURLConnection2 = null;', '        HttpsURLConnection httpsURLConnection = null;\n        HttpsURLConnection httpsURLConnection2 = null;', 1)
identity.write_text(s, encoding='utf-8')

# LiveForumUpdater: restore effectively-final fingerprint callback local.
live = root / 'src/com/harleytg/forum/LiveForumUpdater.java'
s = live.read_text(encoding='utf-8')
old = '''    /* synthetic */ void m20lambda$poll$1$comharleytgforumdevLiveForumUpdater(String str, final String str2) {\n        final String str3;\n        try {\n            str3 = fetchFingerprint(str);\n        } catch (Throwable th) {\n            if (this.failures == 0 || this.failures == 2) {\n                AppLogger.warn(this.app, "live_update_poll", th.getClass().getSimpleName());\n            }\n            str3 = null;\n        }\n        this.main.post(new Runnable() {'''
new = '''    /* synthetic */ void m20lambda$poll$1$comharleytgforumdevLiveForumUpdater(String str, final String str2) {\n        String fingerprint;\n        try {\n            fingerprint = fetchFingerprint(str);\n        } catch (Throwable th) {\n            if (this.failures == 0 || this.failures == 2) {\n                AppLogger.warn(this.app, "live_update_poll", th.getClass().getSimpleName());\n            }\n            fingerprint = null;\n        }\n        final String str3 = fingerprint;\n        this.main.post(new Runnable() {'''
if old not in s:
    raise SystemExit('LiveForumUpdater reconstruction marker not found')
live.write_text(s.replace(old, new, 1), encoding='utf-8')

# ForumNotificationClient: restore JSONException handling used by Stable.
client = root / 'src/com/harleytg/forum/ForumNotificationClient.java'
s = client.read_text(encoding='utf-8')
old = '''        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {\n            if (trim.startsWith("{")) {\n                return firstDeepObject(new JSONObject(trim), i + 1, strArr);\n            }\n            JSONArray jSONArray2 = new JSONArray(trim);\n            while (i2 < jSONArray2.length()) {\n                String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);\n                if (!readableValue2.isEmpty()) {\n                    return readableValue2;\n                }\n                i2++;\n            }\n            return "";\n        }'''
new = '''        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {\n            try {\n                if (trim.startsWith("{")) return firstDeepObject(new JSONObject(trim), i + 1, strArr);\n                JSONArray jSONArray2 = new JSONArray(trim);\n                while (i2 < jSONArray2.length()) {\n                    String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);\n                    if (!readableValue2.isEmpty()) return readableValue2;\n                    i2++;\n                }\n                return "";\n            } catch (org.json.JSONException ignored) { return clean(trim, 500); }\n        }'''
if old not in s:
    raise SystemExit('ForumNotificationClient reconstruction marker not found')
client.write_text(s.replace(old, new, 1), encoding='utf-8')

# HcfIntentChooser: initialize the ResolveInfo label before the guarded lookup.
chooser = root / 'src/com/harleytg/forum/HcfIntentChooser.java'
s = chooser.read_text(encoding='utf-8')
old = '''    private static String resolveLabel(PackageManager packageManager, ResolveInfo resolveInfo) {\n        CharSequence loadLabel;\n        if (resolveInfo == null) {\n            loadLabel = null;\n        } else {\n            try {\n                loadLabel = resolveInfo.loadLabel(packageManager);\n            } catch (Throwable unused) {\n            }\n        }'''
new = '''    private static String resolveLabel(PackageManager packageManager, ResolveInfo resolveInfo) {\n        CharSequence loadLabel = null;\n        if (resolveInfo != null) {\n            try { loadLabel = resolveInfo.loadLabel(packageManager); }\n            catch (Throwable unused) { loadLabel = null; }\n        }'''
if old not in s:
    raise SystemExit('HcfIntentChooser reconstruction marker not found')
chooser.write_text(s.replace(old, new, 1), encoding='utf-8')

# The decompiled public.xml exports stale resource IDs; programmatic UI does not need it.
public_xml = root / 'res/values/public.xml'
if public_xml.exists():
    public_xml.unlink()

manifest = (root / 'AndroidManifest.xml').read_text(encoding='utf-8')
assert 'android:versionCode="10000080"' in manifest
assert 'android:versionName="1.0 (10000080)"' in manifest
assert 'package="com.harleytg.forum.dev"' in manifest
print('Beta v10000080 repair pass complete')
