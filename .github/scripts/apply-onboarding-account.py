#!/usr/bin/env python3
"""Apply the HCF App Setup forum-account onboarding patch to an isolated build source tree.

The checked-in recovered Java source is intentionally kept stable. The release build copies
that source into a temporary directory and this script applies validated, deterministic
changes before javac runs.
"""

from pathlib import Path
import sys

MAIN_MARKER = "HCF_ONBOARDING_FORUM_ACCOUNT_V1"
APP_MARKER = "HCF_ONBOARDING_ACCOUNT_FLOW_GUARD_V1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Onboarding patch anchor {label!r} expected once, found {count}")
    return text.replace(old, new, 1)


def patch_main(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if MAIN_MARKER in text:
        print(f"Onboarding account patch already present: {path}")
        return

    text = replace_once(
        text,
        '''            TextView welcome = text(
                    "Thanks for using the Harley's Forum app. App Setup can help get Android features ready for this device, but it is completely optional.",
                    13, getColor(R.color.hcf_muted));''',
        '''            TextView welcome = text(
                    "Set up your forum account and Android features in one guided flow. Sign in, create a forum account, or continue as a guest. Everything remains optional and can be changed later.",
                    13, getColor(R.color.hcf_muted));''',
        "welcome-copy",
    )

    text = replace_once(
        text,
        '''            grid.addView(buildFeatureItem(R.drawable.fa_bell, "Forum\\nNotifications"));
            grid.addView(buildFeatureItem(R.drawable.fa_globe, "Open Forum\\nLinks in App"));
            grid.addView(buildFeatureItem(R.drawable.fa_shield, "Secure App\\nUpdate Permission"));
            grid.addView(buildFeatureItem(R.drawable.fa_circle_info, "Background\\nAlert Health"));''',
        '''            grid.addView(buildFeatureItem(R.drawable.fa_user, "Forum Account\\nSign in, Sign up or Guest"));
            grid.addView(buildFeatureItem(R.drawable.fa_bell, "Forum\\nNotifications"));
            grid.addView(buildFeatureItem(R.drawable.fa_globe, "Open Forum\\nLinks in App"));
            grid.addView(buildFeatureItem(R.drawable.fa_shield, "Secure App\\nUpdates"));''',
        "welcome-feature-grid",
    )

    text = replace_once(
        text,
        '''        private TextView themeValue;
        private TextView performanceValue;

        private TextView notificationStatus;''',
        '''        private TextView themeValue;
        private TextView performanceValue;

        // HCF_ONBOARDING_FORUM_ACCOUNT_V1
        private TextView forumAccountStatus;
        private TextView forumAccountDetail;
        private Button forumSignInAction;
        private Button forumSignUpAction;
        private Button forumGuestAction;

        private TextView notificationStatus;''',
        "setup-account-fields",
    )

    text = replace_once(
        text,
        '''            content.addView(setupIntroCard());
            content.addView(appearanceAndPerformanceCard());''',
        '''            content.addView(setupIntroCard());
            content.addView(forumAccountCard());
            content.addView(appearanceAndPerformanceCard());''',
        "setup-card-order",
    )

    text = replace_once(
        text,
        '''            TextView subtitle = text(
                    "Appearance, performance & Android features",
                    11,
                    getColor(R.color.hcf_cyan)
            );''',
        '''            TextView subtitle = text(
                    "Forum account, appearance & Android features",
                    11,
                    getColor(R.color.hcf_cyan)
            );''',
        "setup-header-subtitle",
    )

    text = replace_once(
        text,
        '''            card.addView(sectionTitle(
                    "Set up this device",
                    "Choose how HCF looks and performs, then connect optional Android features."
            ));''',
        '''            card.addView(sectionTitle(
                    "Set up this device",
                    "Connect a forum account or continue as a guest, choose how HCF looks and performs, then configure optional Android features."
            ));''',
        "setup-intro-title",
    )

    text = replace_once(
        text,
        '''        private View appearanceAndPerformanceCard() {''',
        '''        private View forumAccountCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Forum Account",
                    "Sign in, create an account, or continue without signing in"
            ));

            forumAccountStatus = statusBadge();
            forumAccountDetail = detailText();
            forumSignInAction = actionButton("Sign In to Forum   ›");
            forumSignInAction.setOnClickListener(v -> handleForumAccountPrimaryAction());

            card.addView(integrationBlock(
                    "Forum Identity",
                    R.drawable.fa_user,
                    forumAccountStatus,
                    forumAccountDetail,
                    forumSignInAction
            ));

            forumSignUpAction = actionButton("Create Forum Account   ›");
            forumSignUpAction.setOnClickListener(v -> openForumAccountRoute("/signup", "sign_up"));
            card.addView(withTopMargin(forumSignUpAction, 0, 44));

            forumGuestAction = actionButton("Continue as Guest   ›");
            forumGuestAction.setOnClickListener(v -> continueAsGuest());
            card.addView(withTopMargin(forumGuestAction, 0, 44));

            TextView privacy = text(
                    "Sign-in and sign-up happen on the Harley's Clan Forum page inside HCF. App Setup does not store or re-submit your forum password. Guest mode does not require an account.",
                    10,
                    getColor(R.color.hcf_hint)
            );
            privacy.setLineSpacing(0.0f, 1.08f);
            privacy.setPadding(0, dp(8), 0, dp(2));
            card.addView(privacy);

            refreshForumAccountStatus();
            return card;
        }

        private void refreshForumAccountStatus() {
            if (forumAccountStatus == null || forumAccountDetail == null
                    || forumSignInAction == null || forumSignUpAction == null
                    || forumGuestAction == null) {
                return;
            }

            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            boolean guestSelected = prefs != null
                    && prefs.getBoolean("setup_forum_guest_selected", false);

            if (identity != null && identity.loggedIn) {
                if (guestSelected && prefs != null) {
                    prefs.edit().remove("setup_forum_guest_selected").apply();
                }
                setStatus(forumAccountStatus, "✓ Signed in", true);
                String account = identity.usernameDisplay();
                if (account == null || account.trim().isEmpty()) {
                    account = identity.identityLabel();
                }
                forumAccountDetail.setText(
                        "Signed in as " + account + ". Your forum session is connected to this app and can be used for your profile, notifications and account features."
                );
                forumSignInAction.setText("Open My Forum Profile   ›");
                forumSignUpAction.setVisibility(View.GONE);
                forumGuestAction.setVisibility(View.GONE);
            } else if (guestSelected) {
                setStatus(forumAccountStatus, "✓ Guest selected", true);
                forumAccountDetail.setText(
                        "No forum account will be connected during App Setup. Continue with the remaining setup options below. You can sign in or create an account later from the forum profile area."
                );
                forumSignInAction.setText("Sign In Instead   ›");
                forumSignInAction.setVisibility(View.VISIBLE);
                forumSignUpAction.setVisibility(View.VISIBLE);
                forumGuestAction.setVisibility(View.GONE);
            } else {
                setStatus(forumAccountStatus, "Guest • Optional", true);
                forumAccountDetail.setText(
                        "Sign in to connect your existing forum profile, create a Harley's Clan Forum account, or choose Continue as Guest to use HCF without signing in."
                );
                forumSignInAction.setText("Sign In to Forum   ›");
                forumSignInAction.setVisibility(View.VISIBLE);
                forumSignUpAction.setVisibility(View.VISIBLE);
                forumGuestAction.setVisibility(View.VISIBLE);
            }
        }

        private void continueAsGuest() {
            if (prefs != null) {
                prefs.edit().putBoolean("setup_forum_guest_selected", true).apply();
            }
            AppLogger.info(this, "setup_forum_account", "continue_as_guest");
            refreshForumAccountStatus();
        }

        private void handleForumAccountPrimaryAction() {
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            if (identity != null && identity.loggedIn) {
                String slug = !identity.slug.isEmpty() ? identity.slug : identity.username;
                if (slug != null && !slug.trim().isEmpty()) {
                    openForumAccountRoute("/u/" + Uri.encode(slug.trim()), "profile");
                    return;
                }
                openForumAccountRoute("/", "profile_home");
                return;
            }
            openForumAccountRoute("/login", "sign_in");
        }

        private String setupForumHost() {
            String host = prefs == null ? "" : prefs.getString("active_host", "");
            if (!ForumUrlRouter.isForumHost(host)) {
                host = SetupCenter.PRIMARY_FORUM_HOST;
            }
            return host;
        }

        private void openForumAccountRoute(String path, String source) {
            String safePath = path == null || !path.startsWith("/") ? "/" : path;
            Uri target = Uri.parse("https://" + setupForumHost() + safePath);
            Intent intent = new Intent(this, HcfMainActivities.MainActivity.class);
            intent.setData(target);
            intent.putExtra("hcf_setup_account_flow", true);
            startActivity(intent);
            AppLogger.info(this, "setup_forum_account", source + " | " + AppLogger.safeUrl(target.toString()));
        }

        private View appearanceAndPerformanceCard() {''',
        "setup-account-card",
    )

    text = replace_once(
        text,
        '''        private void refreshStatuses() {
            if (notificationStatus == null) return;''',
        '''        private void refreshStatuses() {
            refreshForumAccountStatus();
            if (notificationStatus == null) return;''',
        "refresh-account-status",
    )

    text = replace_once(
        text,
        '''                if (this.welcomeBackPending) {
                    this.welcomeBackPending = false;
                    showWelcomeBanner(fromBridgeJson);
                }''',
        '''                if (fromBridgeJson.loggedIn && getIntent() != null
                        && getIntent().getBooleanExtra("hcf_setup_account_flow", false)) {
                    getIntent().putExtra("hcf_setup_account_flow", false);
                    AppLogger.info(this, "setup_forum_account", "authenticated_return");
                    showTransientBanner("Forum account connected • Returning to App Setup");
                    this.mainHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (!isFinishing() && !isDestroyed()) {
                                finish();
                            }
                        }
                    }, 450L);
                }
                if (this.welcomeBackPending) {
                    this.welcomeBackPending = false;
                    showWelcomeBanner(fromBridgeJson);
                }''',
        "authenticated-auto-return",
    )

    path.write_text(text, encoding="utf-8")
    print(f"Applied forum account onboarding patch: {path}")


def patch_application(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if APP_MARKER in text:
        print(f"Onboarding account-flow guard already present: {path}")
        return

    text = replace_once(
        text,
        '''                        try {
                            SetupCenter.maybeLaunchForMainActivity((HcfMainActivities.MainActivity) activity, state);
                        } catch (Throwable error) {''',
        '''                        try {
                            // HCF_ONBOARDING_ACCOUNT_FLOW_GUARD_V1
                            Intent launchIntent = activity.getIntent();
                            boolean setupAccountFlow = launchIntent != null
                                    && launchIntent.getBooleanExtra("hcf_setup_account_flow", false);
                            if (setupAccountFlow) {
                                AppLogger.info(App.this, "app_setup_account_flow", "skip_auto_setup_gate");
                            } else {
                                SetupCenter.maybeLaunchForMainActivity((HcfMainActivities.MainActivity) activity, state);
                            }
                        } catch (Throwable error) {''',
        "setup-flow-auto-gate",
    )

    path.write_text(text, encoding="utf-8")
    print(f"Applied forum account flow guard: {path}")


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("Usage: apply-onboarding-account.py <HcfMainActivities.java> <HcfApplication.java>")

    main_path = Path(sys.argv[1])
    app_path = Path(sys.argv[2])
    if not main_path.is_file() or not app_path.is_file():
        raise SystemExit("Onboarding patch source file is missing")

    patch_main(main_path)
    patch_application(app_path)


if __name__ == "__main__":
    main()
