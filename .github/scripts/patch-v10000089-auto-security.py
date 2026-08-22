from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-v10000089-auto-security.py <source-code-root>")

root = Path(sys.argv[1])
main = root / "src/com/harleytg/forum/MainActivity.java"
identity = root / "src/com/harleytg/forum/IdentityActivity.java"
security = root / "src/com/harleytg/forum/ForumSecurity.java"

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

# ForumSecurity fallback labels should no longer instruct a manual page visit.
text = security.read_text(encoding="utf-8")
text = text.replace('return "Open Account Security to sync";', 'return "Syncing automatically";')
text = text.replace('return !this.seen ? "Not synced yet"', 'return !this.seen ? "Waiting for automatic sync"')
security.write_text(text, encoding="utf-8")

# Validation so a build fails instead of silently shipping the old behavior.
main_text = main.read_text(encoding="utf-8")
identity_text = identity.read_text(encoding="utf-8")
security_text = security.read_text(encoding="utf-8")
checks = [
    ("textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);" in main_text, "build-driven drawer footer"),
    ("var autoSecurity=function()" in main_text, "automatic security sync function"),
    ("hcf_native_sync=1" in main_text, "off-screen security route"),
    ("autoSecurity();fixSecurityLabels();" in main_text, "automatic security sync wiring"),
    ("Linked sign-in providers sync automatically" in identity_text, "automatic linked-account copy"),
    ("Syncing automatically" in identity_text, "automatic security status copy"),
    ("Open Account Security once to sync" not in identity_text, "manual-sync instruction removed"),
    ("Syncing automatically" in security_text, "ForumSecurity automatic status"),
]
for passed, label in checks:
    if not passed:
        raise SystemExit("validation failed: " + label)

print("v10000089 automatic account-security sync patch applied")
