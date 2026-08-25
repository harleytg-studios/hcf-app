#!/usr/bin/env python3
"""Patch App Setup so Forum Account reflects the live WebView/Flarum session.

This runs after apply-onboarding-account.py against the isolated build source. It adds a
short-lived, invisible WebView probe that uses the app's existing first-party cookie store,
reads Flarum's live app.session.user, refreshes ForumIdentity, and then tears itself down.
"""

from pathlib import Path
import sys

MARKER = "HCF_SETUP_LIVE_SESSION_PROBE_V1"
ONBOARDING_MARKER = "HCF_ONBOARDING_FORUM_ACCOUNT_V1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Live-session patch anchor {label!r} expected once, found {count}")
    return text.replace(old, new, 1)


def patch_main(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"Live forum-session probe already present: {path}")
        return
    if ONBOARDING_MARKER not in text:
        raise SystemExit("Forum-account onboarding patch must run before live-session patch")

    text = replace_once(
        text,
        '''        private Button forumGuestAction;

        private TextView notificationStatus;''',
        '''        private Button forumGuestAction;

        // HCF_SETUP_LIVE_SESSION_PROBE_V1
        private final Handler forumIdentityProbeHandler = new Handler(Looper.getMainLooper());
        private final List<String> forumIdentityProbeHosts = new ArrayList<>();
        private WebView forumIdentityProbe;
        private int forumIdentityProbeGeneration;
        private int forumIdentityProbeIndex;
        private int forumIdentityProbeConfirmedGuests;
        private boolean forumIdentityProbeResolved;

        private TextView notificationStatus;''',
        "probe-fields",
    )

    text = replace_once(
        text,
        '''        @Override
        protected void onResume() {
            super.onResume();
            refreshChoiceLabels();
            refreshStatuses();
        }''',
        '''        @Override
        protected void onResume() {
            super.onResume();
            refreshChoiceLabels();
            refreshStatuses();
            refreshForumIdentityFromLiveSession();
        }

        @Override
        protected void onDestroy() {
            forumIdentityProbeGeneration++;
            destroyForumIdentityProbe();
            super.onDestroy();
        }''',
        "setup-on-resume",
    )

    text = replace_once(
        text,
        '''        private void refreshForumAccountStatus() {''',
        r'''        private void refreshForumIdentityFromLiveSession() {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            forumIdentityProbeGeneration++;
            int generation = forumIdentityProbeGeneration;
            forumIdentityProbeResolved = false;
            forumIdentityProbeIndex = 0;
            forumIdentityProbeConfirmedGuests = 0;
            destroyForumIdentityProbe();
            forumIdentityProbeHosts.clear();

            ForumIdentity.Snapshot cached = ForumIdentity.load(this);
            if (cached != null) {
                addForumIdentityProbeHost(cached.host);
            }
            addForumIdentityProbeHost(setupForumHost());
            addForumIdentityProbeHost(SetupCenter.PRIMARY_FORUM_HOST);
            addForumIdentityProbeHost(SetupCenter.BACKUP_FORUM_HOST);

            if (forumIdentityProbeHosts.isEmpty()) {
                return;
            }

            if (cached == null || !cached.loggedIn) {
                if (forumAccountStatus != null) {
                    setStatus(forumAccountStatus, "Checking session…", true);
                }
                if (forumAccountDetail != null) {
                    forumAccountDetail.setText(
                            "Checking the existing Harley's Clan Forum session stored in this app…"
                    );
                }
            }

            AppLogger.info(this, "setup_forum_identity_probe",
                    "start | hosts=" + forumIdentityProbeHosts.size());
            startNextForumIdentityProbe(generation);
        }

        private void addForumIdentityProbeHost(String host) {
            if (!ForumUrlRouter.isForumHost(host)) {
                return;
            }
            String normalized = host.trim().toLowerCase(Locale.US);
            if (!forumIdentityProbeHosts.contains(normalized)) {
                forumIdentityProbeHosts.add(normalized);
            }
        }

        private void startNextForumIdentityProbe(final int generation) {
            if (generation != forumIdentityProbeGeneration || forumIdentityProbeResolved
                    || isFinishing() || isDestroyed()) {
                return;
            }

            destroyForumIdentityProbe();

            if (forumIdentityProbeIndex >= forumIdentityProbeHosts.size()) {
                if (forumIdentityProbeConfirmedGuests == forumIdentityProbeHosts.size()) {
                    ForumIdentity.save(this, ForumIdentity.guest(setupForumHost()));
                    AppLogger.info(this, "setup_forum_identity_probe", "confirmed_guest_all_hosts");
                } else {
                    AppLogger.info(this, "setup_forum_identity_probe",
                            "finished_without_definitive_result | guest="
                                    + forumIdentityProbeConfirmedGuests
                                    + " | hosts=" + forumIdentityProbeHosts.size());
                }
                refreshForumAccountStatus();
                return;
            }

            final String expectedHost = forumIdentityProbeHosts.get(forumIdentityProbeIndex);
            final WebView probe = new WebView(this);
            forumIdentityProbe = probe;
            probe.setVisibility(View.INVISIBLE);
            probe.setAlpha(0.0f);
            probe.setFocusable(false);
            probe.setClickable(false);

            WebSettings settings = probe.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            try {
                CookieManager cookies = CookieManager.getInstance();
                cookies.setAcceptCookie(true);
                cookies.setAcceptThirdPartyCookies(probe, true);
                cookies.flush();
            } catch (Throwable ignored) {
            }

            probe.addJavascriptInterface(
                    new SetupIdentityProbeBridge(generation, expectedHost),
                    "HCFSetupIdentity"
            );
            probe.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (generation != forumIdentityProbeGeneration || view != forumIdentityProbe) {
                        return;
                    }
                    forumIdentityProbeHandler.postDelayed(
                            () -> requestForumIdentityProbe(view, generation, expectedHost, 0),
                            250L
                    );
                }

                @Override
                public void onReceivedError(
                        WebView view,
                        WebResourceRequest request,
                        WebResourceError error
                ) {
                    super.onReceivedError(view, request, error);
                    if (request != null && request.isForMainFrame()) {
                        failForumIdentityProbeHost(generation, expectedHost, "web_error");
                    }
                }
            });

            probe.loadUrl("https://" + expectedHost + "/?hcf_setup_identity_probe=1");
        }

        private void requestForumIdentityProbe(
                final WebView probe,
                final int generation,
                final String expectedHost,
                final int attempt
        ) {
            if (generation != forumIdentityProbeGeneration || forumIdentityProbeResolved
                    || probe != forumIdentityProbe || isFinishing() || isDestroyed()) {
                return;
            }
            if (attempt >= 24) {
                failForumIdentityProbeHost(generation, expectedHost, "session_not_ready");
                return;
            }

            String script = "(function(){try{"
                    + "if(!window.app||!app.session){return false;}"
                    + "var val=function(o,n,d){try{var x=o&&o[n];return typeof x==='function'?x.call(o):(x===undefined?d:x);}catch(e){return d;}};"
                    + "var iso=function(v){try{if(!v)return '';if(typeof v.toISOString==='function')return v.toISOString();if(v.$d&&typeof v.$d.toISOString==='function')return v.$d.toISOString();return String(v);}catch(e){return '';}};"
                    + "var u=app.session.user;"
                    + "if(!u){HCFSetupIdentity.report(JSON.stringify({loggedIn:false}),String(location.host||''));return true;}"
                    + "var gs=[];try{var groups=val(u,'groups',[])||[];for(var i=0;i<groups.length;i++){var g=groups[i];var n=val(g,'nameSingular','');if(n)gs.push(String(n));}}catch(e){}"
                    + "var email=String(val(u,'email','')||'');"
                    + "var data={loggedIn:true,id:String(val(u,'id','')||''),username:String(val(u,'username','')||''),slug:String(val(u,'slug','')||''),displayName:String(val(u,'displayName','')||''),email:email,emailConfirmed:!!val(u,'isEmailConfirmed',false),avatarUrl:String(val(u,'avatarUrl','')||''),groups:gs,connections:[],isAdmin:!!val(u,'isAdmin',false),joinTime:iso(val(u,'joinTime',null)),lastSeenAt:iso(val(u,'lastSeenAt',null)),unreadNotificationCount:Number(val(u,'unreadNotificationCount',0)||0),newNotificationCount:Number(val(u,'newNotificationCount',0)||0),discussionCount:Number(val(u,'discussionCount',0)||0),commentCount:Number(val(u,'commentCount',0)||0)};"
                    + "HCFSetupIdentity.report(JSON.stringify(data),String(location.host||''));return true;"
                    + "}catch(e){return false;}})();";

            try {
                probe.evaluateJavascript(script, value -> {
                    if (generation != forumIdentityProbeGeneration || forumIdentityProbeResolved
                            || probe != forumIdentityProbe) {
                        return;
                    }
                    if (!"true".equals(value)) {
                        forumIdentityProbeHandler.postDelayed(
                                () -> requestForumIdentityProbe(
                                        probe,
                                        generation,
                                        expectedHost,
                                        attempt + 1
                                ),
                                300L
                        );
                    }
                });
            } catch (Throwable error) {
                failForumIdentityProbeHost(
                        generation,
                        expectedHost,
                        "evaluate_" + error.getClass().getSimpleName()
                );
            }
        }

        private final class SetupIdentityProbeBridge {
            private final int generation;
            private final String expectedHost;

            SetupIdentityProbeBridge(int generation, String expectedHost) {
                this.generation = generation;
                this.expectedHost = expectedHost;
            }

            @JavascriptInterface
            public void report(final String json, final String host) {
                runOnUiThread(() -> handleForumIdentityProbeResult(
                        generation,
                        expectedHost,
                        json,
                        host
                ));
            }
        }

        private void handleForumIdentityProbeResult(
                int generation,
                String expectedHost,
                String json,
                String reportedHost
        ) {
            if (generation != forumIdentityProbeGeneration || forumIdentityProbeResolved
                    || isFinishing() || isDestroyed()) {
                return;
            }

            String host = ForumUrlRouter.isForumHost(reportedHost)
                    ? reportedHost
                    : expectedHost;
            if (!ForumUrlRouter.isForumHost(host)) {
                failForumIdentityProbeHost(generation, expectedHost, "untrusted_host");
                return;
            }

            try {
                ForumIdentity.Snapshot snapshot = ForumIdentity.fromBridgeJson(json, host);
                if (snapshot.loggedIn) {
                    ForumIdentity.save(this, snapshot);
                    if (prefs != null) {
                        prefs.edit().remove("setup_forum_guest_selected").apply();
                    }
                    forumIdentityProbeResolved = true;
                    AppLogger.info(this, "setup_forum_identity_probe",
                            "signed_in | " + snapshot.usernameDisplay());
                    destroyForumIdentityProbe();
                    refreshForumAccountStatus();
                    return;
                }

                forumIdentityProbeConfirmedGuests++;
                AppLogger.info(this, "setup_forum_identity_probe",
                        "guest | host=" + host);
                forumIdentityProbeIndex++;
                startNextForumIdentityProbe(generation);
            } catch (Throwable error) {
                failForumIdentityProbeHost(
                        generation,
                        expectedHost,
                        "parse_" + error.getClass().getSimpleName()
                );
            }
        }

        private void failForumIdentityProbeHost(
                int generation,
                String host,
                String reason
        ) {
            if (generation != forumIdentityProbeGeneration || forumIdentityProbeResolved) {
                return;
            }
            AppLogger.warn(this, "setup_forum_identity_probe",
                    reason + " | host=" + host);
            forumIdentityProbeIndex++;
            startNextForumIdentityProbe(generation);
        }

        private void destroyForumIdentityProbe() {
            forumIdentityProbeHandler.removeCallbacksAndMessages(null);
            WebView probe = forumIdentityProbe;
            forumIdentityProbe = null;
            if (probe == null) {
                return;
            }
            try {
                probe.stopLoading();
                probe.removeJavascriptInterface("HCFSetupIdentity");
                probe.loadUrl("about:blank");
                probe.clearHistory();
                probe.removeAllViews();
                probe.destroy();
            } catch (Throwable ignored) {
            }
        }

        private void refreshForumAccountStatus() {''',
        "live-session-methods",
    )

    path.write_text(text, encoding="utf-8")
    print(f"Applied live forum-session detection patch: {path}")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply-onboarding-live-session.py <HcfMainActivities.java>")

    path = Path(sys.argv[1])
    if not path.is_file():
        raise SystemExit("HcfMainActivities.java is missing")
    patch_main(path)


if __name__ == "__main__":
    main()
