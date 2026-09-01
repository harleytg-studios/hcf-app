package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.WeakHashMap;

/**
 * HCF_SUBSETTINGS_UI_V4_FULL_AUTHENTICATOR
 *
 * Dedicated owner for App Settings > Account & Security > Account Controls.
 * The complete HCF Authenticator UI is embedded directly in the Two-Factor
 * Authentication subsetting. HcfAuthenticator remains the TOTP/Keystore engine
 * and standalone deep-link fallback only.
 */
public final class HcfSubsettingsUi {
    private static final String TAG = "hcf_account_controls_subsettings_ui_v4_full_auth";
    private static final String TAG_2FA_SUMMARY = TAG + ":2fa_summary";
    private static final String PREF_PREFIX = "account_controls_subsetting_";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, AuthenticatorPane> AUTH_PANES = new WeakHashMap<>();
    private static boolean installed;

    private HcfSubsettingsUi() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (appContext instanceof Application) install((Application) appContext);
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }

    private static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (isSettings(activity)) {
                    installObserver(activity);
                    scheduleRender(activity);
                }
            }

            @Override public void onActivityResumed(Activity activity) {
                if (!isSettings(activity)) return;
                installObserver(activity);
                scheduleRender(activity);
                AuthenticatorPane pane = AUTH_PANES.get(activity);
                if (pane != null) {
                    pane.reload();
                    pane.start();
                }
            }

            @Override public void onActivityPaused(Activity activity) {
                AuthenticatorPane pane = AUTH_PANES.get(activity);
                if (pane != null) pane.stop();
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {
                removeObserver(activity);
                AuthenticatorPane pane = AUTH_PANES.remove(activity);
                if (pane != null) pane.stop();
            }
        });
    }

    private static boolean isSettings(Activity activity) {
        return activity != null
                && "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity"
                .equals(activity.getClass().getName());
    }

    private static void installObserver(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;
            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (!activity.isFinishing()) render(activity);
                }
            };
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    private static void removeObserver(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) {
            listener = OBSERVERS.remove(activity);
        }
        if (listener == null) return;
        try {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void scheduleRender(Activity activity) {
        MAIN.postDelayed(() -> render(activity), 60L);
        MAIN.postDelayed(() -> render(activity), 180L);
        MAIN.postDelayed(() -> render(activity), 420L);
    }

    private static void render(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        View existing = root.findViewWithTag(TAG);
        if (existing != null) {
            AuthenticatorPane pane = AUTH_PANES.get(activity);
            if (pane != null) pane.refreshDisplay();
            return;
        }

        TextView profileText = findText(root, "Open My Forum Profile");
        TextView securityText = findText(root, "Open Account Security");
        if (profileText == null || securityText == null) {
            AuthenticatorPane old = AUTH_PANES.remove(activity);
            if (old != null) old.stop();
            return;
        }

        ViewGroup common = commonCardAncestor(profileText, securityText);
        if (!(common instanceof LinearLayout)) return;
        LinearLayout card = (LinearLayout) common;
        View profileAction = clickableAncestor(profileText, card);
        View securityAction = clickableAncestor(securityText, card);
        if (profileAction == null || securityAction == null || profileAction == securityAction) return;

        ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
        ForumSecurity.Snapshot security = ForumSecurity.load(activity);
        if (!identity.loggedIn) return;

        AuthenticatorPane old = AUTH_PANES.remove(activity);
        if (old != null) old.stop();

        detach(profileAction);
        detach(securityAction);
        card.removeAllViews();
        card.setTag(TAG);
        card.setPadding(0, 0, 0, 0);

        String handle = identity.username == null || identity.username.trim().isEmpty()
                ? identity.identityLabel()
                : "@" + identity.username.trim();

        card.addView(buildProfileSubsetting(activity, profileAction, handle),
                lp(activity, -1, -2, 0, 9));
        card.addView(buildSecuritySubsetting(activity, securityAction, security),
                lp(activity, -1, -2, 0, 9));

        AuthenticatorPane pane = new AuthenticatorPane(activity, identity, security);
        AUTH_PANES.put(activity, pane);
        View twoFactor = subsetting(activity,
                "two_factor",
                "Two-Factor Authentication",
                pane.summary(),
                pane.build(),
                true);
        TextView summary = findText(twoFactor, pane.summary());
        if (summary != null) summary.setTag(TAG_2FA_SUMMARY);
        card.addView(twoFactor, lp(activity, -1, -2, 0, 0));
        pane.start();
    }

    private static View buildProfileSubsetting(Activity activity, View profileAction, String handle) {
        LinearLayout body = body(activity);
        body.addView(detail(activity,
                "Open your public Harley's Clan Forum profile, activity and account identity."));
        styleExistingAction(activity, profileAction);
        body.addView(profileAction, lp(activity, -1, dp(activity, 52), 10, 0));
        return subsetting(activity, "profile", "Forum Profile", "Signed in as " + handle,
                body, true);
    }

    private static View buildSecuritySubsetting(Activity activity, View securityAction,
                                                ForumSecurity.Snapshot security) {
        LinearLayout body = body(activity);
        body.addView(securityStatusRow(activity, "Password",
                security.passwordControls ? "Available" : forumState(security),
                security.passwordControls));
        body.addView(securityStatusRow(activity, "Email",
                security.emailControls ? "Available" : forumState(security),
                security.emailControls), lp(activity, -1, -2, 7, 0));
        body.addView(securityStatusRow(activity, "Active sessions",
                security.sessionCount > 0 ? String.valueOf(security.sessionCount)
                        : (security.seen ? "None synced" : "Sync needed"),
                security.sessionCount > 0), lp(activity, -1, -2, 7, 0));
        if (!security.seen) {
            body.addView(detail(activity,
                    "Open Account Security once to sync the controls available for this forum account."),
                    lp(activity, -1, -2, 9, 0));
        }
        styleExistingAction(activity, securityAction);
        body.addView(securityAction, lp(activity, -1, dp(activity, 52), 11, 0));
        String summary = security.seen
                ? "Password, email and session controls"
                : "Open once to sync forum security controls";
        return subsetting(activity, "security", "Password, Email & Sessions", summary,
                body, false);
    }

    /** Full embedded HCF Authenticator. */
    private static final class AuthenticatorPane {
        private final Activity activity;
        private final ForumIdentity.Snapshot identity;
        private final ForumSecurity.Snapshot security;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private HcfAuthenticator.Config config;
        private String lastCode = "";
        private TextView localStatus;
        private TextView code;
        private TextView countdown;
        private ProgressBar progress;
        private EditText accountInput;
        private EditText secretInput;
        private Button removeButton;
        private boolean running;

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!running) return;
                renderCode();
                handler.postDelayed(this, 250L);
            }
        };

        AuthenticatorPane(Activity activity, ForumIdentity.Snapshot identity,
                          ForumSecurity.Snapshot security) {
            this.activity = activity;
            this.identity = identity;
            this.security = security;
            reload();
        }

        String summary() {
            return ready()
                    ? "HCF Authenticator ready • Nearata forum 2FA"
                    : (security.twoFactorControls
                            ? "HCF Authenticator • set up with Nearata"
                            : "HCF Authenticator • open forum 2FA setup");
        }

        View build() {
            LinearLayout body = body(activity);

            TextView heading = text(activity, "HCF AUTHENTICATOR • FULL SETTINGS", 9,
                    cyan(activity));
            heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            body.addView(heading);

            body.addView(securityStatusRow(activity,
                    "Nearata 2FA controls",
                    security.twoFactorControls ? "Detected" : "Open forum",
                    security.twoFactorControls), lp(activity, -1, -2, 7, 0));

            localStatus = authStatus(activity, ready());
            body.addView(localStatus, lp(activity, -1, -2, 7, 0));

            body.addView(currentCodePanel(), lp(activity, -1, -2, 10, 0));
            body.addView(setupPanel(), lp(activity, -1, -2, 10, 0));
            body.addView(nearataPanel(), lp(activity, -1, -2, 10, 0));
            body.addView(managePanel(), lp(activity, -1, -2, 10, 0));

            TextView footer = text(activity,
                    "RFC 6238 TOTP • Android Keystore • codes work offline after setup",
                    9, color(activity, R.color.hcf_muted, Color.GRAY));
            footer.setGravity(Gravity.CENTER);
            body.addView(footer, lp(activity, -1, -2, 8, 0));
            return body;
        }

        private View currentCodePanel() {
            LinearLayout panel = innerPanel(activity);
            panel.addView(sectionLabel(activity, "CURRENT 6-DIGIT PASSCODE"));

            code = text(activity, "--- ---", 32,
                    color(activity, R.color.hcf_text, Color.WHITE));
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            code.setGravity(Gravity.CENTER);
            code.setLetterSpacing(0.08f);
            panel.addView(code, lp(activity, -1, -2, 5, 0));

            countdown = text(activity,
                    "Add the Nearata QR code or Setup key to generate a passcode.",
                    10, color(activity, R.color.hcf_muted, Color.LTGRAY));
            countdown.setGravity(Gravity.CENTER);
            panel.addView(countdown, lp(activity, -1, -2, 3, 0));

            progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(30);
            panel.addView(progress, lp(activity, -1, dp(activity, 5), 9, 0));

            Button copy = actionButton(activity, "Copy current passcode", true);
            copy.setOnClickListener(v -> copyCode());
            panel.addView(copy, lp(activity, -1, dp(activity, 48), 10, 0));
            return panel;
        }

        private View setupPanel() {
            LinearLayout panel = innerPanel(activity);
            panel.addView(sectionLabel(activity, "SET UP HCF AUTHENTICATOR"));
            panel.addView(detail(activity,
                    "Use the QR code or Setup key shown by Nearata TwoFactor on the forum."));

            Button scan = actionButton(activity, "Scan or import Nearata QR code", true);
            scan.setOnClickListener(v -> {
                Intent intent = new Intent(activity, HcfAuthenticatorSettingsQrActivity.class);
                activity.startActivity(intent);
            });
            panel.addView(scan, lp(activity, -1, dp(activity, 50), 11, 0));

            Button paste = actionButton(activity, "Paste authenticator setup link", false);
            paste.setOnClickListener(v -> pasteSetupLink());
            panel.addView(paste, lp(activity, -1, dp(activity, 48), 8, 0));

            TextView manual = text(activity, "MANUAL SETUP KEY", 9, cyan(activity));
            manual.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            panel.addView(manual, lp(activity, -1, -2, 13, 0));

            accountInput = input(activity, "Forum account name (optional)");
            if (identity.username != null && !identity.username.trim().isEmpty()) {
                accountInput.setText(identity.username.trim());
            }
            panel.addView(accountInput, lp(activity, -1, dp(activity, 50), 8, 0));

            secretInput = input(activity, "Setup key");
            secretInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            panel.addView(secretInput, lp(activity, -1, dp(activity, 50), 8, 0));

            Button save = actionButton(activity, ready() ? "Replace Setup key" : "Save Setup key", true);
            save.setOnClickListener(v -> saveManual());
            panel.addView(save, lp(activity, -1, dp(activity, 48), 9, 0));
            return panel;
        }

        private View nearataPanel() {
            LinearLayout panel = innerPanel(activity);
            panel.addView(sectionLabel(activity, "FINISH ON NEARATA TWOFACTOR"));
            panel.addView(detail(activity,
                    "Nearata requires your forum password, the current 6-digit passcode, and the same setup secret before it enables 2FA."));

            panel.addView(stepRow(activity, "1", "Open Nearata's Two-Factor Authentication setup on the forum."),
                    lp(activity, -1, -2, 9, 0));
            panel.addView(stepRow(activity, "2", "Use the QR code or Setup key here, then copy the current HCF passcode."),
                    lp(activity, -1, -2, 7, 0));
            panel.addView(stepRow(activity, "3", "Enter your forum password + passcode in Nearata and press Enable."),
                    lp(activity, -1, -2, 7, 0));
            panel.addView(stepRow(activity, "4", "Save or copy the backup codes Nearata provides after activation."),
                    lp(activity, -1, -2, 7, 0));

            Button forum = actionButton(activity, "Open Nearata 2FA Setup", false);
            forum.setOnClickListener(v -> openForumSettings(activity));
            panel.addView(forum, lp(activity, -1, dp(activity, 48), 10, 0));
            return panel;
        }

        private View managePanel() {
            LinearLayout panel = innerPanel(activity);
            panel.addView(sectionLabel(activity, "LOCAL AUTHENTICATOR STORAGE"));
            panel.addView(detail(activity,
                    "Only the TOTP setup secret is stored by HCF, encrypted with Android Keystore. Your forum password is never stored here."));

            removeButton = actionButton(activity, "Remove authenticator from this device", false);
            removeButton.setTextColor(Color.rgb(255, 77, 87));
            removeButton.setEnabled(ready());
            removeButton.setOnClickListener(v -> remove());
            panel.addView(removeButton, lp(activity, -1, dp(activity, 48), 10, 0));

            TextView warning = text(activity,
                    "Removing the local key does not disable Nearata 2FA on the forum. Keep your Nearata backup codes safe.",
                    9, color(activity, R.color.hcf_muted, Color.GRAY));
            warning.setGravity(Gravity.CENTER);
            panel.addView(warning, lp(activity, -1, -2, 8, 0));
            return panel;
        }

        void reload() {
            try {
                config = HcfAuthenticator.Vault.load(activity);
            } catch (Throwable error) {
                config = null;
            }
            refreshDisplay();
        }

        void refreshDisplay() {
            if (localStatus != null) {
                boolean ready = ready();
                int stateColor = ready ? cyan(activity)
                        : color(activity, R.color.hcf_muted, Color.LTGRAY);
                localStatus.setText(ready
                        ? "HCF Authenticator configured on this device"
                        : "HCF Authenticator not configured on this device");
                localStatus.setTextColor(stateColor);
                localStatus.setBackground(roundRect(activity,
                        alpha(stateColor, 18), alpha(stateColor, 100), 10));
                if (removeButton != null) removeButton.setEnabled(ready);
            }
            View root = activity.findViewById(android.R.id.content);
            if (root != null) {
                View summary = root.findViewWithTag(TAG_2FA_SUMMARY);
                if (summary instanceof TextView) ((TextView) summary).setText(summary());
            }
            renderCode();
        }

        void start() {
            running = true;
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }

        void stop() {
            running = false;
            handler.removeCallbacks(ticker);
        }

        private boolean ready() {
            return config != null && config.secret != null && !config.secret.isEmpty();
        }

        private void renderCode() {
            if (code == null || countdown == null || progress == null) return;
            if (!ready()) {
                lastCode = "";
                code.setText("--- ---");
                countdown.setText("Add the Nearata QR code or Setup key to generate a passcode.");
                progress.setMax(30);
                progress.setProgress(0);
                return;
            }
            try {
                long now = System.currentTimeMillis() / 1000L;
                lastCode = HcfAuthenticator.Totp.generate(config, now);
                code.setText(formatCode(lastCode));
                int elapsed = (int) (now % config.period);
                int remaining = config.period - elapsed;
                progress.setMax(config.period);
                progress.setProgress(elapsed);
                countdown.setText("New passcode in " + remaining + " second"
                        + (remaining == 1 ? "" : "s") + " • works offline");
            } catch (Throwable error) {
                lastCode = "";
                code.setText("--- ---");
                countdown.setText("Unable to generate a passcode. Check the Setup key and device time.");
            }
        }

        private void copyCode() {
            renderCode();
            if (lastCode.isEmpty()) {
                Toast.makeText(activity, "No authentication passcode is available yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("HCF authentication passcode", lastCode));
                Toast.makeText(activity, "6-digit passcode copied.", Toast.LENGTH_SHORT).show();
            }
        }

        private void pasteSetupLink() {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(activity, "Clipboard is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            CharSequence value = clip == null || clip.getItemCount() == 0
                    ? null : clip.getItemAt(0).coerceToText(activity);
            if (value == null || value.toString().trim().isEmpty()) {
                Toast.makeText(activity, "Clipboard does not contain an authenticator setup link.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                HcfAuthenticator.Config incoming = HcfAuthenticator.Config.fromOtpAuth(
                        Uri.parse(value.toString().trim()));
                confirmSave(incoming, "clipboard setup link");
            } catch (Throwable error) {
                Toast.makeText(activity, "That is not a supported TOTP setup link.", Toast.LENGTH_LONG).show();
            }
        }

        private void saveManual() {
            if (secretInput == null) return;
            String raw = secretInput.getText().toString().trim();
            if (raw.isEmpty()) {
                secretInput.setError("Enter the Setup key shown by Nearata");
                return;
            }
            try {
                String label = accountInput == null ? "" : accountInput.getText().toString().trim();
                HcfAuthenticator.Config incoming = HcfAuthenticator.Config.manual(raw, label);
                HcfAuthenticator.Base32.decode(incoming.secret);
                confirmSave(incoming, "manual Setup key");
            } catch (Throwable error) {
                secretInput.setError("Invalid Setup key");
            }
        }

        private void confirmSave(HcfAuthenticator.Config incoming, String source) {
            new AlertDialog.Builder(activity)
                    .setTitle(ready() ? "Replace HCF Authenticator?" : "Set up HCF Authenticator?")
                    .setMessage("Source: " + source
                            + "\n\nThe setup secret will be encrypted with Android Keystore and stored only on this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(ready() ? "Replace" : "Save", (dialog, which) -> save(incoming))
                    .show();
        }

        private void save(HcfAuthenticator.Config incoming) {
            try {
                HcfAuthenticator.Vault.save(activity, incoming);
                config = incoming;
                if (secretInput != null) secretInput.setText("");
                refreshDisplay();
                Toast.makeText(activity, "HCF Authenticator configured.", Toast.LENGTH_SHORT).show();
            } catch (Throwable error) {
                Toast.makeText(activity, "Could not securely save this authenticator.", Toast.LENGTH_LONG).show();
            }
        }

        private void remove() {
            if (!ready()) return;
            new AlertDialog.Builder(activity)
                    .setTitle("Remove HCF Authenticator from this device?")
                    .setMessage("This deletes the encrypted Setup key from this device. It does not disable Nearata TwoFactor on the forum.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        HcfAuthenticator.Vault.clear(activity);
                        config = null;
                        lastCode = "";
                        refreshDisplay();
                    })
                    .show();
        }
    }

    private static View subsetting(Activity activity, String key, String title,
                                   String summary, View content, boolean defaultExpanded) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 13));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 11), dp(activity, 11));
        header.setClickable(true);
        header.setFocusable(true);
        header.setContentDescription(title + ". " + summary);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(activity, title, 13,
                color(activity, R.color.hcf_text, Color.WHITE));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(heading);
        TextView summaryView = text(activity, summary, 10,
                color(activity, R.color.hcf_muted, Color.LTGRAY));
        summaryView.setMaxLines(2);
        labels.addView(summaryView, lp(activity, -1, -2, 3, 0));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text(activity, "›", 22,
                color(activity, R.color.hcf_accent_text, Color.rgb(0, 184, 240)));
        arrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 36)));

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(activity, 14), 0, dp(activity, 14), dp(activity, 13));
        shell.addView(content);

        boolean expanded = activity.getSharedPreferences(AppPrefs.FILE, 0)
                .getBoolean(PREF_PREFIX + key, defaultExpanded);
        applyExpanded(shell, arrow, expanded);
        header.setOnClickListener(v -> {
            boolean next = shell.getVisibility() != View.VISIBLE;
            applyExpanded(shell, arrow, next);
            activity.getSharedPreferences(AppPrefs.FILE, 0).edit()
                    .putBoolean(PREF_PREFIX + key, next).apply();
        });

        panel.addView(header);
        panel.addView(shell);
        return panel;
    }

    private static void applyExpanded(View body, TextView arrow, boolean expanded) {
        body.animate().cancel();
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        body.setAlpha(1f);
        arrow.setText(expanded ? "⌄" : "›");
    }

    private static LinearLayout body(Activity activity) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        return body;
    }

    private static LinearLayout innerPanel(Activity activity) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11));
        panel.setBackground(roundRect(activity,
                alpha(color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)), 242),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 12));
        return panel;
    }

    private static TextView sectionLabel(Activity activity, String value) {
        TextView label = text(activity, value, 9, cyan(activity));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private static View securityStatusRow(Activity activity, String label, String value,
                                          boolean positive) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 11), dp(activity, 9), dp(activity, 11), dp(activity, 9));
        row.setBackground(roundRect(activity,
                alpha(color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)), 235),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 10));
        TextView name = text(activity, label, 11,
                color(activity, R.color.hcf_text, Color.WHITE));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        int valueColor = positive ? cyan(activity)
                : color(activity, R.color.hcf_muted, Color.LTGRAY);
        TextView state = text(activity, value, 10, valueColor);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(state);
        return row;
    }

    private static TextView authStatus(Activity activity, boolean ready) {
        int stateColor = ready ? cyan(activity)
                : color(activity, R.color.hcf_muted, Color.LTGRAY);
        TextView status = text(activity,
                ready ? "HCF Authenticator configured on this device"
                        : "HCF Authenticator not configured on this device",
                11, stateColor);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(dp(activity, 11), dp(activity, 8), dp(activity, 11), dp(activity, 8));
        status.setBackground(roundRect(activity,
                alpha(stateColor, 18), alpha(stateColor, 100), 10));
        return status;
    }

    private static View stepRow(Activity activity, String number, String message) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 9), dp(activity, 8), dp(activity, 9), dp(activity, 8));
        row.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 10));
        TextView badge = text(activity, number, 11, cyan(activity));
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(activity, alpha(cyan(activity), 22),
                alpha(cyan(activity), 110), 9));
        row.addView(badge, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30)));
        TextView copy = text(activity, message, 10,
                color(activity, R.color.hcf_text, Color.WHITE));
        copy.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1f);
        copyLp.leftMargin = dp(activity, 9);
        row.addView(copy, copyLp);
        return row;
    }

    private static EditText input(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setHintTextColor(color(activity, R.color.hcf_muted, Color.GRAY));
        input.setTextColor(color(activity, R.color.hcf_text, Color.WHITE));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        input.setSingleLine(true);
        input.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        input.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 11));
        return input;
    }

    private static Button actionButton(Activity activity, String label, boolean primary) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setStateListAnimator(null);
        if (primary) {
            button.setTextColor(Color.BLACK);
            button.setBackground(roundRect(activity, cyan(activity), cyan(activity), 12));
        } else {
            button.setTextColor(cyan(activity));
            button.setBackground(roundRect(activity,
                    color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                    color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 12));
        }
        return button;
    }

    private static void openForumSettings(Activity activity) {
        try {
            ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
            String host = ForumUrlRouter.isForumHost(identity.host)
                    ? identity.host : "forum.harleytg.com";
            Intent intent = new Intent(activity, HcfForum.MainActivity.class);
            intent.setData(Uri.parse("https://" + host + "/settings"));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static TextView detail(Activity activity, String value) {
        TextView detail = text(activity, value, 10,
                color(activity, R.color.hcf_hint, Color.LTGRAY));
        detail.setLineSpacing(0f, 1.08f);
        return detail;
    }

    private static void styleExistingAction(Activity activity, View action) {
        action.setMinimumHeight(dp(activity, 52));
        action.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        action.setBackground(roundRect(activity,
                color(activity, R.color.hcf_surface, Color.rgb(19, 28, 34)),
                color(activity, R.color.hcf_border, Color.rgb(41, 64, 75)), 11));
    }

    private static String forumState(ForumSecurity.Snapshot security) {
        return security.seen ? "Forum managed" : "Sync needed";
    }

    private static String formatCode(String raw) {
        if (raw == null) return "--- ---";
        if (raw.length() == 6) return raw.substring(0, 3) + " " + raw.substring(3);
        if (raw.length() == 8) return raw.substring(0, 4) + " " + raw.substring(4);
        return raw;
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private static View clickableAncestor(View child, ViewGroup stop) {
        View current = child;
        View candidate = child;
        while (current != null && current != stop) {
            if (current.isClickable()) candidate = current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return candidate == child && !child.isClickable() ? null : candidate;
    }

    private static ViewGroup commonCardAncestor(View first, View second) {
        View cursor = first;
        while (cursor != null && cursor.getParent() instanceof View) {
            cursor = (View) cursor.getParent();
            if (!(cursor instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) cursor;
            if (isDescendant(group, second) && group.getChildCount() >= 3) return group;
        }
        return null;
    }

    private static boolean isDescendant(ViewGroup parent, View target) {
        View cursor = target;
        while (cursor != null && cursor.getParent() instanceof View) {
            if (cursor.getParent() == parent) return true;
            cursor = (View) cursor.getParent();
        }
        return false;
    }

    private static TextView findText(View view, String exact) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && exact.equals(value.toString().trim())) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView text(Context context, String value, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout.LayoutParams lp(Context context, int width, int height,
                                                 int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.topMargin = dp(context, topDp);
        lp.bottomMargin = dp(context, bottomDp);
        return lp;
    }

    private static GradientDrawable roundRect(Context context, int fill, int stroke,
                                              int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int alpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int cyan(Context context) {
        return color(context, R.color.hcf_cyan_bright, Color.rgb(0, 184, 240));
    }

    private static int color(Context context, int resId, int fallback) {
        try { return context.getColor(resId); }
        catch (Throwable ignored) { return fallback; }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
