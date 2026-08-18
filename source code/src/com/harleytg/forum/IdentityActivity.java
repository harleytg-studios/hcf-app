package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;
import java.text.DateFormat;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public final class IdentityActivity extends ThemedActivity {
    private LinearLayout content;
    private ImageView identityAvatar;
    private String identityAvatarRequestedUrl = "";
    private String identityAvatarLoadedUrl = "";
    private Bitmap identityAvatarBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? android.graphics.Color.BLACK : getColor(R.color.hcf_bg));
        getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? android.graphics.Color.BLACK : getColor(R.color.hcf_bg));
        setContentView(buildUi());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeManager.isAmoled(this) ? android.graphics.Color.BLACK : getColor(R.color.hcf_bg));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(compact() ? 3 : 7), dp(10), dp(compact() ? 3 : 7));
        header.setBackgroundColor(ThemeManager.isAmoled(this) ? android.graphics.Color.BLACK : getColor(R.color.hcf_app_bar));
        header.setMinimumHeight(dp(compact() ? 46 : 56));

        ImageButton back = UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, compact() ? 9 : 11, "Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley\'s Clan Forum");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        logoParams.leftMargin = dp(8);
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        header.addView(titles, tp);

        TextView title = text("Account & Identity", 18, getColor(R.color.hcf_text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        titles.addView(title);
        TextView subtitle = text("Identity data stays scoped to the current signed-in forum user", 10, getColor(R.color.hcf_meta));
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(subtitle);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        identityAvatar = null;

        ForumIdentity.Snapshot identity = ForumIdentity.load(this);
        ForumSecurity.Snapshot security = ForumSecurity.load(this);

        content.addView(profileCard(identity));
        content.addView(linkedAccountsCard(identity, security));
        content.addView(securityCard(identity, security));
        content.addView(activityCard(identity));
        content.addView(quickActionsCard(identity));
        content.addView(sessionCard(identity, security));

        loadIdentityAvatar(identity);
    }

    private View profileCard(ForumIdentity.Snapshot s) {
        LinearLayout card = card();
        card.addView(sectionTitle("Account details", "Your current Harley's Clan Forum session"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        identityAvatar = new ImageView(this);
        identityAvatar.setImageResource(R.drawable.htg_app_logo);
        identityAvatar.setBackgroundResource(R.drawable.identity_avatar_background);
        identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        identityAvatar.setAdjustViewBounds(false);
        identityAvatar.setCropToPadding(false);
        identityAvatar.setPadding(dp(3), dp(3), dp(3), dp(3));
        identityAvatar.setClipToOutline(true);
        identityAvatar.setContentDescription("Current forum identity avatar placeholder");
        row.addView(identityAvatar, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(12);
        row.addView(labels, lp);

        TextView primary = text(s.loggedIn ? s.identityLabel() : "Guest_Protocol", 18, getColor(R.color.hcf_accent_text));
        primary.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(primary);
        labels.addView(text(s.loggedIn
                ? (s.username.isEmpty() ? "Identity sync" : "@" + s.username)
                : "Not signed in", 12, getColor(R.color.hcf_meta)));
        labels.addView(text(s.loggedIn
                ? "Only self-visible details exposed by your current forum session"
                : "Sign in inside the forum and this page updates automatically.", 10, getColor(R.color.hcf_muted)));
        card.addView(row);

        if (s.loggedIn) {
            addRow(card, "Display name", s.identityLabel());
            addRow(card, "Username", s.username.isEmpty() ? "—" : "@" + s.username);
            addRow(card, "Groups / roles", s.groups.isEmpty() ? (s.admin ? "Administrator" : "Member") : s.groups);
            addRow(card, "Email status", s.email.isEmpty() ? "Not exposed" : (s.emailConfirmed ? "Verified" : "Not verified"));
        } else {
            addRow(card, "Forum session", "Guest_Protocol");
        }
        return card;
    }


    private View linkedAccountsCard(ForumIdentity.Snapshot s, ForumSecurity.Snapshot security) {
        LinearLayout card = card();
        card.addView(sectionTitle("Linked accounts", "Sign-in providers detected for this forum account"));

        if (!s.loggedIn) {
            card.addView(text("Sign in to the forum to view linked accounts.", 11, getColor(R.color.hcf_muted)));
            return card;
        }

        String connected = ForumSecurity.mergeLabels(s.connections, security.seen ? security.providers : "");
        boolean emailConnected = !s.email.isEmpty() || containsProvider(connected, "email");
        boolean discordConnected = containsProvider(connected, "discord");
        boolean googleConnected = containsProvider(connected, "google");

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        if (emailConnected) chips.addView(providerChip("Email", R.drawable.ic_provider_email, s.emailConfirmed ? "Verified" : "Linked"));
        if (discordConnected) chips.addView(providerChip("Discord", R.drawable.ic_provider_discord, "Linked"));
        if (googleConnected) chips.addView(providerChip("Google", R.drawable.ic_provider_google, "Linked"));

        if (chips.getChildCount() > 0) {
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setFillViewport(false);
            scroll.addView(chips, new android.widget.HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(scroll);
            addRow(card, "Detected providers", connected.isEmpty() ? (emailConnected ? "Email" : "Linked") : connected);
        } else {
            card.addView(text(security.seen
                    ? "No linked sign-in providers were detected for this account."
                    : "Open Account Security once to sync linked sign-in providers.",
                    11, getColor(R.color.hcf_muted)));
        }

        card.addView(actionRow("Manage Linked Accounts", "View and manage your forum sign-in methods",
                R.drawable.fa_shield, v -> openAccountSecurity(s)));
        return card;
    }

    private TextView providerChip(String label, int iconRes, String state) {
        TextView chip = text(label + (state == null || state.isEmpty() ? "" : "  •  " + state), 10, getColor(R.color.hcf_text));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.provider_chip_background);
        chip.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        chip.setCompoundDrawablePadding(dp(5));
        chip.setPadding(dp(9), dp(7), dp(9), dp(7));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(6);
        chip.setLayoutParams(p);
        return chip;
    }

    private static boolean containsProvider(String labels, String provider) {
        if (labels == null || provider == null) return false;
        return labels.toLowerCase(java.util.Locale.US).contains(provider.toLowerCase(java.util.Locale.US));
    }

    private View securityCard(ForumIdentity.Snapshot s, ForumSecurity.Snapshot security) {
        LinearLayout card = card();
        card.addView(sectionTitle("Sign-in & security", "A safe summary from your forum security page"));

        String connected = ForumSecurity.mergeLabels(s.connections, security.seen ? security.providers : "");
        if (connected.isEmpty()) connected = s.connectionLabel();
        addRow(card, "Connected sign-in methods", connected.isEmpty() ? "None detected" : connected);
        addRow(card, "Email status", s.email.isEmpty() ? "Not exposed" : (s.emailConfirmed ? "Verified" : "Not verified"));
        addRow(card, "Security sync", security.seen ? security.sessionLabel() : "Open Account Security once to sync");

        String controls;
        if (!security.seen) controls = "No controls detected";
        else {
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            if (security.passwordControls) list.add("Password");
            if (security.emailControls) list.add("Email");
            if (security.twoFactorControls) list.add("2FA");
            controls = list.isEmpty() ? "No controls detected" : android.text.TextUtils.join(" • ", list);
        }
        addRow(card, "Available controls", controls);
        return card;
    }

    private View activityCard(ForumIdentity.Snapshot s) {
        LinearLayout card = card();
        card.addView(sectionTitle("Forum activity", "A quick view of your current account activity"));
        if (s.loggedIn) {
            addRow(card, "Discussions", Integer.toString(s.discussionCount));
            addRow(card, "Comments", Integer.toString(s.commentCount));
            addRow(card, "Unread notifications", Integer.toString(s.unreadNotifications));
            addRow(card, "New notifications", Integer.toString(s.newNotifications));
            addRow(card, "Joined", displayDate(s.joinTime));
            addRow(card, "Last seen", displayDate(s.lastSeenAt));
        } else {
            card.addView(text("Your account details appear here automatically after the forum session is signed in.", 11, getColor(R.color.hcf_muted)));
        }
        return card;
    }

    private View quickActionsCard(ForumIdentity.Snapshot s) {
        LinearLayout card = card();
        card.addView(sectionTitle("Account shortcuts", "Profile and security actions for this signed-in account"));
        if (!s.loggedIn) {
            card.addView(actionRow("Open Forum Sign In", "Sign in to sync your forum identity",
                    R.drawable.fa_user, v -> openForumPath("/login")));
            return card;
        }
        card.addView(actionRow("Open My Forum Profile", "View your profile on Harley\'s Clan Forum",
                R.drawable.fa_user, v -> openProfile(s)));
        card.addView(actionRow("Open Account Security", "Manage password, email and available security controls",
                R.drawable.fa_shield, v -> openAccountSecurity(s)));
        return card;
    }

    private View sessionCard(ForumIdentity.Snapshot s, ForumSecurity.Snapshot security) {
        LinearLayout card = card();
        card.addView(sectionTitle("Session & privacy", "Identity sync"));
        addRow(card, "Forum host", s.host.isEmpty() ? ForumConfig.PRIMARY_HOST : s.host);
        addRow(card, "Identity sync", s.syncedAt <= 0L ? "Not synced yet" : DateFormat.getDateTimeInstance().format(new Date(s.syncedAt)));
        addRow(card, "Security sync", security.syncedAt <= 0L ? "Not synced yet" : DateFormat.getDateTimeInstance().format(new Date(security.syncedAt)));
        TextView privacy = text("The app stores only the current user's self-visible profile summary and safe security capability/status fields. It does not store passwords, recovery codes, access/session token values, or cookie values.", 10, getColor(R.color.hcf_muted));
        privacy.setPadding(0, dp(10), 0, 0);
        card.addView(privacy);
        return card;
    }

    private void openProfile(ForumIdentity.Snapshot s) {
        if (s == null || !s.loggedIn) return;
        String handle = !s.slug.isEmpty() ? s.slug : s.username;
        if (handle == null || handle.trim().isEmpty()) return;
        openForumPath("/u/" + Uri.encode(handle.trim()));
    }

    private void openAccountSecurity(ForumIdentity.Snapshot s) {
        if (s == null || !s.loggedIn) {
            Toast.makeText(this, "Sign in to the forum first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String handle = !s.slug.isEmpty() ? s.slug : s.username;
        if (handle == null || handle.trim().isEmpty()) {
            Toast.makeText(this, "Unable to determine your forum profile route.", Toast.LENGTH_SHORT).show();
            return;
        }
        openForumPath("/u/" + Uri.encode(handle.trim()) + "/security");
    }

    private void openForumPath(String path) {
        ForumIdentity.Snapshot s = ForumIdentity.load(this);
        String host = ForumUrlRouter.isForumHost(s.host) ? s.host : ForumConfig.PRIMARY_HOST;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setData(Uri.parse("https://" + host + path));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadIdentityAvatar(ForumIdentity.Snapshot snapshot) {
        if (identityAvatar == null) return;
        final boolean hasAvatar = snapshot != null && snapshot.loggedIn
                && snapshot.avatarUrl != null && !snapshot.avatarUrl.trim().isEmpty();
        if (!hasAvatar) {
            identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int placeholderInset = dp(3);
            identityAvatar.setPadding(placeholderInset, placeholderInset, placeholderInset, placeholderInset);
            identityAvatar.setImageResource(R.drawable.htg_app_logo);
            identityAvatarRequestedUrl = "";
            identityAvatarLoadedUrl = "";
            identityAvatarBitmap = null;
            return;
        }
        final String requested = snapshot.avatarUrl.trim();
        Uri uri;
        try { uri = Uri.parse(requested); } catch (Throwable ignored) { return; }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ForumUrlRouter.isForumHost(uri.getHost())) return;

        if (requested.equals(identityAvatarLoadedUrl) && identityAvatarBitmap != null) {
            identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int avatarInset = dp(2);
            identityAvatar.setPadding(avatarInset, avatarInset, avatarInset, avatarInset);
            identityAvatar.setImageBitmap(identityAvatarBitmap);
            identityAvatar.setTag(requested);
            return;
        }
        if (requested.equals(identityAvatarRequestedUrl)) return;
        identityAvatarRequestedUrl = requested;
        identityAvatar.setTag(requested);

        AppExecutors.network().execute(() -> {
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL(requested).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setUseCaches(true);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
                if (connection.getResponseCode() != 200) return;
                final Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap == null) return;
                runOnUiThread(() -> {
                    if (identityAvatar != null && requested.equals(identityAvatar.getTag())) {
                        identityAvatarBitmap = bitmap;
                        identityAvatarLoadedUrl = requested;
                        identityAvatarRequestedUrl = "";
                        identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        int avatarInset = dp(2);
                        identityAvatar.setPadding(avatarInset, avatarInset, avatarInset, avatarInset);
                        identityAvatar.setImageBitmap(bitmap);
                        identityAvatar.setContentDescription("Current forum identity avatar");
                    }
                });
            } catch (Throwable ignored) {
                runOnUiThread(() -> {
                    if (requested.equals(identityAvatarRequestedUrl)) identityAvatarRequestedUrl = "";
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        int pad = dp(compact() ? 12 : 16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(compact() ? 8 : 12);
        card.setLayoutParams(p);
        return card;
    }

    private View sectionTitle(String titleValue, String subtitleValue) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(titleValue, 14, getColor(R.color.hcf_text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        if (subtitleValue != null && !subtitleValue.isEmpty()) box.addView(text(subtitleValue, 10, getColor(R.color.hcf_muted)));
        box.setPadding(0, 0, 0, dp(9));
        return box;
    }

    private void addRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView key = text(label, 10, getColor(R.color.hcf_muted));
        key.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(key);
        TextView val = text(value == null || value.trim().isEmpty() ? "—" : value, 12, getColor(R.color.hcf_text));
        val.setTextIsSelectable(true);
        row.addView(val);
        card.addView(row);
    }

    private String displayDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        return raw;
    }

    private View actionRow(String label, String subtitle, int iconRes, View.OnClickListener listener) {
        // Use a real centered icon + label group. Android Button compound drawables can
        // position the icon against the start padding while centering the text separately
        // on some vendor builds, which made these actions look misaligned.
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.button_background);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(listener);
        button.setContentDescription(label);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setContentDescription(null);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.rightMargin = dp(10);
        button.addView(icon, iconParams);

        TextView actionLabel = text(label, 14, getColor(R.color.hcf_cyan_bright));
        actionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        actionLabel.setIncludeFontPadding(false);
        actionLabel.setGravity(Gravity.CENTER_VERTICAL);
        actionLabel.setMaxLines(1);
        button.addView(actionLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact() ? 46 : 50));
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private boolean compact() {
        return getResources().getConfiguration().screenHeightDp <= 720;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
