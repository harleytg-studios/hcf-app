package com.harleytg.forum;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.harleytg.forum.ForumIdentity;
import com.harleytg.forum.ForumSecurity;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/* loaded from: classes.dex */
public final class IdentityActivity extends ThemedActivity {
    private LinearLayout content;
    private ImageView identityAvatar;
    private Bitmap identityAvatarBitmap;
    private String identityAvatarRequestedUrl = "";
    private String identityAvatarLoadedUrl = "";

    @Override // com.harleytg.forum.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override // com.harleytg.forum.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        setContentView(buildUi());
        render();
    }

    @Override // com.harleytg.forum.ThemedActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        render();
    }

    private View buildUi() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(8), dp(compact() ? 3 : 7), dp(10), dp(compact() ? 3 : 7));
        linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        linearLayout2.setMinimumHeight(dp(compact() ? 46 : 56));
        ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, compact() ? 9 : 11, "Back");
        iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                IdentityActivity.this.m10lambda$buildUi$0$comharleytgforumdevIdentityActivity(view);
            }
        });
        linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(compact() ? 38 : 44), dp(compact() ? 38 : 44)));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setContentDescription("Harley's Clan Forum");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(compact() ? 34 : 40), dp(compact() ? 34 : 40));
        layoutParams.leftMargin = dp(8);
        linearLayout2.addView(imageView, layoutParams);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.leftMargin = dp(10);
        linearLayout2.addView(linearLayout3, layoutParams2);
        TextView text = text("Account & Identity", 18, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout3.addView(text);
        TextView text2 = text("Identity data stays scoped to the current signed-in forum user", 10, getColor(R.color.hcf_meta));
        text2.setMaxLines(1);
        text2.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout3.addView(text2);
        linearLayout.addView(linearLayout2);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout linearLayout4 = new LinearLayout(this);
        this.content = linearLayout4;
        linearLayout4.setOrientation(1);
        this.content.setPadding(dp(compact() ? 10 : 14), dp(compact() ? 8 : 14), dp(compact() ? 10 : 14), dp(compact() ? 18 : 28));
        scrollView.addView(this.content, new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return linearLayout;
    }

    /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m10lambda$buildUi$0$comharleytgforumdevIdentityActivity(View view) {
        finish();
    }

    private void render() {
        LinearLayout linearLayout = this.content;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        this.identityAvatar = null;
        ForumIdentity.Snapshot load = ForumIdentity.load(this);
        ForumSecurity.Snapshot load2 = ForumSecurity.load(this);
        this.content.addView(profileCard(load));
        this.content.addView(linkedAccountsCard(load, load2));
        this.content.addView(securityCard(load, load2));
        this.content.addView(activityCard(load));
        this.content.addView(quickActionsCard(load));
        this.content.addView(sessionCard(load, load2));
        loadIdentityAvatar(load);
    }

    private View profileCard(ForumIdentity.Snapshot snapshot) {
        String str;
        String str2;
        String str3;
        LinearLayout card = card();
        card.addView(sectionTitle("Account details", "Your current Harley's Clan Forum session"));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        ImageView imageView = new ImageView(this);
        this.identityAvatar = imageView;
        imageView.setImageResource(R.drawable.htg_app_logo);
        this.identityAvatar.setBackgroundResource(R.drawable.identity_avatar_background);
        this.identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        this.identityAvatar.setAdjustViewBounds(false);
        this.identityAvatar.setCropToPadding(false);
        this.identityAvatar.setPadding(dp(3), dp(3), dp(3), dp(3));
        this.identityAvatar.setClipToOutline(true);
        this.identityAvatar.setContentDescription("Current forum identity avatar placeholder");
        linearLayout.addView(this.identityAvatar, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(12);
        linearLayout.addView(linearLayout2, layoutParams);
        TextView text = text(snapshot.loggedIn ? snapshot.identityLabel() : "Guest_Protocol", 18, getColor(R.color.hcf_accent_text));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        if (snapshot.loggedIn) {
            if (snapshot.username.isEmpty()) {
                str = "Identity sync";
            } else {
                str = "@" + snapshot.username;
            }
        } else {
            str = "Not signed in";
        }
        linearLayout2.addView(text(str, 12, getColor(R.color.hcf_meta)));
        if (snapshot.loggedIn) {
            str2 = "Only self-visible details exposed by your current forum session";
        } else {
            str2 = "Sign in inside the forum and this page updates automatically.";
        }
        linearLayout2.addView(text(str2, 10, getColor(R.color.hcf_muted)));
        card.addView(linearLayout);
        if (snapshot.loggedIn) {
            addRow(card, "Display name", snapshot.identityLabel());
            if (snapshot.username.isEmpty()) {
                str3 = "—";
            } else {
                str3 = "@" + snapshot.username;
            }
            addRow(card, "Username", str3);
            addRow(card, "Groups / roles", snapshot.groups.isEmpty() ? snapshot.admin ? "Administrator" : "Member" : snapshot.groups);
            addRow(card, "Email status", snapshot.email.isEmpty() ? "Not exposed" : snapshot.emailConfirmed ? "Verified" : "Not verified");
        } else {
            addRow(card, "Forum session", "Guest_Protocol");
        }
        return card;
    }

    private View linkedAccountsCard(final ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
        String str;
        LinearLayout card = card();
        card.addView(sectionTitle("Linked accounts", "Sign-in providers detected for this forum account"));
        if (!snapshot.loggedIn) {
            card.addView(text("Sign in to the forum to view linked accounts.", 11, getColor(R.color.hcf_muted)));
            return card;
        }
        String mergeLabels = ForumSecurity.mergeLabels(snapshot.connections, snapshot2.seen ? snapshot2.providers : "");
        boolean z = !snapshot.email.isEmpty() || containsProvider(mergeLabels, "email");
        boolean containsProvider = containsProvider(mergeLabels, "discord");
        boolean containsProvider2 = containsProvider(mergeLabels, "google");
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        if (z) {
            linearLayout.addView(providerChip("Email", R.drawable.ic_provider_email, snapshot.emailConfirmed ? "Verified" : "Linked"));
        }
        if (containsProvider) {
            linearLayout.addView(providerChip("Discord", R.drawable.ic_provider_discord, "Linked"));
        }
        if (containsProvider2) {
            linearLayout.addView(providerChip("Google", R.drawable.ic_provider_google, "Linked"));
        }
        if (linearLayout.getChildCount() > 0) {
            HorizontalScrollView horizontalScrollView = new HorizontalScrollView(this);
            horizontalScrollView.setHorizontalScrollBarEnabled(false);
            horizontalScrollView.setFillViewport(false);
            horizontalScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-2, -2));
            card.addView(horizontalScrollView);
            if (mergeLabels.isEmpty()) {
                mergeLabels = z ? "Email" : "Linked";
            }
            addRow(card, "Detected providers", mergeLabels);
        } else {
            if (snapshot2.seen) {
                str = "No linked sign-in providers were detected for this account.";
            } else {
                str = "Open Account Security once to sync linked sign-in providers.";
            }
            card.addView(text(str, 11, getColor(R.color.hcf_muted)));
        }
        card.addView(actionRow("Manage Linked Accounts", "View and manage your forum sign-in methods", R.drawable.fa_shield, new View.OnClickListener() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                IdentityActivity.this.m11xe0d3a06d(snapshot, view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$linkedAccountsCard$1$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m11xe0d3a06d(ForumIdentity.Snapshot snapshot, View view) {
        openAccountSecurity(snapshot);
    }

    private TextView providerChip(String str, int i, String str2) {
        String str3;
        StringBuilder sb = new StringBuilder();
        sb.append(str);
        if (str2 == null || str2.isEmpty()) {
            str3 = "";
        } else {
            str3 = "  •  " + str2;
        }
        sb.append(str3);
        TextView text = text(sb.toString(), 10, getColor(R.color.hcf_text));
        text.setGravity(16);
        text.setBackgroundResource(R.drawable.provider_chip_background);
        text.setCompoundDrawablesWithIntrinsicBounds(i, 0, 0, 0);
        text.setCompoundDrawablePadding(dp(5));
        text.setPadding(dp(9), dp(7), dp(9), dp(7));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.rightMargin = dp(6);
        text.setLayoutParams(layoutParams);
        return text;
    }

    private static boolean containsProvider(String str, String str2) {
        if (str == null || str2 == null) {
            return false;
        }
        return str.toLowerCase(Locale.US).contains(str2.toLowerCase(Locale.US));
    }

    private View securityCard(ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
        LinearLayout card = card();
        card.addView(sectionTitle("Sign-in & security", "A safe summary from your forum security page"));
        String mergeLabels = ForumSecurity.mergeLabels(snapshot.connections, snapshot2.seen ? snapshot2.providers : "");
        if (mergeLabels.isEmpty()) {
            mergeLabels = snapshot.connectionLabel();
        }
        if (mergeLabels.isEmpty()) {
            mergeLabels = "None detected";
        }
        addRow(card, "Connected sign-in methods", mergeLabels);
        addRow(card, "Email status", snapshot.email.isEmpty() ? "Not exposed" : snapshot.emailConfirmed ? "Verified" : "Not verified");
        addRow(card, "Security sync", snapshot2.seen ? snapshot2.sessionLabel() : "Open Account Security once to sync");
        String str = "No controls detected";
        if (snapshot2.seen) {
            ArrayList arrayList = new ArrayList();
            if (snapshot2.passwordControls) {
                arrayList.add("Password");
            }
            if (snapshot2.emailControls) {
                arrayList.add("Email");
            }
            if (snapshot2.twoFactorControls) {
                arrayList.add("2FA");
            }
            if (!arrayList.isEmpty()) {
                str = TextUtils.join(" • ", arrayList);
            }
        }
        addRow(card, "Available controls", str);
        return card;
    }

    private View activityCard(ForumIdentity.Snapshot snapshot) {
        LinearLayout card = card();
        card.addView(sectionTitle("Forum activity", "A quick view of your current account activity"));
        if (snapshot.loggedIn) {
            addRow(card, "Discussions", Integer.toString(snapshot.discussionCount));
            addRow(card, "Comments", Integer.toString(snapshot.commentCount));
            addRow(card, "Unread notifications", Integer.toString(snapshot.unreadNotifications));
            addRow(card, "New notifications", Integer.toString(snapshot.newNotifications));
            addRow(card, "Joined", displayDate(snapshot.joinTime));
            addRow(card, "Last seen", displayDate(snapshot.lastSeenAt));
        } else {
            card.addView(text("Your account details appear here automatically after the forum session is signed in.", 11, getColor(R.color.hcf_muted)));
        }
        return card;
    }

    private View quickActionsCard(final ForumIdentity.Snapshot snapshot) {
        LinearLayout card = card();
        card.addView(sectionTitle("Account shortcuts", "Profile and security actions for this signed-in account"));
        if (!snapshot.loggedIn) {
            card.addView(actionRow("Open Forum Sign In", "Sign in to sync your forum identity", R.drawable.fa_user, new View.OnClickListener() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    IdentityActivity.this.m15xd192321f(view);
                }
            }));
            return card;
        }
        card.addView(actionRow("Open My Forum Profile", "View your profile on Harley's Clan Forum", R.drawable.fa_user, new View.OnClickListener() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                IdentityActivity.this.m16x5e325d20(snapshot, view);
            }
        }));
        card.addView(actionRow("Open Account Security", "Manage password, email and available security controls", R.drawable.fa_shield, new View.OnClickListener() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                IdentityActivity.this.m17xead28821(snapshot, view);
            }
        }));
        return card;
    }

    /* renamed from: lambda$quickActionsCard$2$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m15xd192321f(View view) {
        openForumPath("/login");
    }

    /* renamed from: lambda$quickActionsCard$3$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m16x5e325d20(ForumIdentity.Snapshot snapshot, View view) {
        openProfile(snapshot);
    }

    /* renamed from: lambda$quickActionsCard$4$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m17xead28821(ForumIdentity.Snapshot snapshot, View view) {
        openAccountSecurity(snapshot);
    }

    private View sessionCard(ForumIdentity.Snapshot snapshot, ForumSecurity.Snapshot snapshot2) {
        LinearLayout card = card();
        card.addView(sectionTitle("Session & privacy", "Identity sync"));
        addRow(card, "Forum host", snapshot.host.isEmpty() ? "forum.harleytg.com" : snapshot.host);
        addRow(card, "Identity sync", snapshot.syncedAt <= 0 ? "Not synced yet" : DateFormat.getDateTimeInstance().format(new Date(snapshot.syncedAt)));
        addRow(card, "Security sync", snapshot2.syncedAt > 0 ? DateFormat.getDateTimeInstance().format(new Date(snapshot2.syncedAt)) : "Not synced yet");
        TextView text = text("The app stores only the current user's self-visible profile summary and safe security capability/status fields. It does not store passwords, recovery codes, access/session token values, or cookie values.", 10, getColor(R.color.hcf_muted));
        text.setPadding(0, dp(10), 0, 0);
        card.addView(text);
        return card;
    }

    private void openProfile(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null || !snapshot.loggedIn) {
            return;
        }
        String str = !snapshot.slug.isEmpty() ? snapshot.slug : snapshot.username;
        if (str == null || str.trim().isEmpty()) {
            return;
        }
        openForumPath("/u/" + Uri.encode(str.trim()));
    }

    private void openAccountSecurity(ForumIdentity.Snapshot snapshot) {
        if (snapshot == null || !snapshot.loggedIn) {
            Toast.makeText(this, "Sign in to the forum first.", 0).show();
            return;
        }
        String str = !snapshot.slug.isEmpty() ? snapshot.slug : snapshot.username;
        if (str == null || str.trim().isEmpty()) {
            Toast.makeText(this, "Unable to determine your forum profile route.", 0).show();
            return;
        }
        openForumPath("/u/" + Uri.encode(str.trim()) + "/security");
    }

    private void openForumPath(String str) {
        ForumIdentity.Snapshot load = ForumIdentity.load(this);
        String str2 = ForumUrlRouter.isForumHost(load.host) ? load.host : "forum.harleytg.com";
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setData(Uri.parse("https://" + str2 + str));
        intent.addFlags(603979776);
        startActivity(intent);
        finish();
    }

    private void loadIdentityAvatar(ForumIdentity.Snapshot snapshot) {
        if (this.identityAvatar == null) {
            return;
        }
        if (snapshot == null || !snapshot.loggedIn || snapshot.avatarUrl == null || snapshot.avatarUrl.trim().isEmpty()) {
            this.identityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int dp = dp(3);
            this.identityAvatar.setPadding(dp, dp, dp, dp);
            this.identityAvatar.setImageResource(R.drawable.htg_app_logo);
            this.identityAvatarRequestedUrl = "";
            this.identityAvatarLoadedUrl = "";
            this.identityAvatarBitmap = null;
            return;
        }
        final String trim = snapshot.avatarUrl.trim();
        try {
            Uri parse = Uri.parse(trim);
            if ("https".equalsIgnoreCase(parse.getScheme()) && ForumUrlRouter.isForumHost(parse.getHost())) {
                if (trim.equals(this.identityAvatarLoadedUrl) && this.identityAvatarBitmap != null) {
                    this.identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    int dp2 = dp(2);
                    this.identityAvatar.setPadding(dp2, dp2, dp2, dp2);
                    this.identityAvatar.setImageBitmap(this.identityAvatarBitmap);
                    this.identityAvatar.setTag(trim);
                    return;
                }
                if (trim.equals(this.identityAvatarRequestedUrl)) {
                    return;
                }
                this.identityAvatarRequestedUrl = trim;
                this.identityAvatar.setTag(trim);
                AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda4
                    @Override // java.lang.Runnable
                    public final void run() {
                        IdentityActivity.this.m14x27753261(trim);
                    }
                });
            }
        } catch (Throwable unused) {
        }
    }

    /* renamed from: lambda$loadIdentityAvatar$7$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m14x27753261(final String str) {
        HttpsURLConnection httpsURLConnection;
        HttpsURLConnection httpsURLConnection2 = null;
        try {
            httpsURLConnection = (HttpsURLConnection) new URL(str).openConnection();
        } catch (Throwable unused) {
        }
        try {
            httpsURLConnection.setConnectTimeout(6000);
            httpsURLConnection.setReadTimeout(6000);
            httpsURLConnection.setUseCaches(true);
            httpsURLConnection.setInstanceFollowRedirects(false);
            httpsURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0");
            if (httpsURLConnection.getResponseCode() != 200) {
                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                    return;
                }
                return;
            }
            final Bitmap decodeStream = BitmapFactory.decodeStream(httpsURLConnection.getInputStream());
            if (decodeStream == null) {
                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                }
            } else {
                runOnUiThread(new Runnable() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda6
                    @Override // java.lang.Runnable
                    public final void run() {
                        IdentityActivity.this.m12xe34dc5f(str, decodeStream);
                    }
                });
                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                }
            }
        } catch (Throwable unused2) {
            httpsURLConnection2 = httpsURLConnection;
            try {
                runOnUiThread(new Runnable() { // from class: com.harleytg.forum.IdentityActivity$$ExternalSyntheticLambda7
                    @Override // java.lang.Runnable
                    public final void run() {
                        IdentityActivity.this.m13x9ad50760(str);
                    }
                });
            } finally {
                if (httpsURLConnection2 != null) {
                    httpsURLConnection2.disconnect();
                }
            }
        }
    }

    /* renamed from: lambda$loadIdentityAvatar$5$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m12xe34dc5f(String str, Bitmap bitmap) {
        ImageView imageView = this.identityAvatar;
        if (imageView == null || !str.equals(imageView.getTag())) {
            return;
        }
        this.identityAvatarBitmap = bitmap;
        this.identityAvatarLoadedUrl = str;
        this.identityAvatarRequestedUrl = "";
        this.identityAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int dp = dp(2);
        this.identityAvatar.setPadding(dp, dp, dp, dp);
        this.identityAvatar.setImageBitmap(bitmap);
        this.identityAvatar.setContentDescription("Current forum identity avatar");
    }

    /* renamed from: lambda$loadIdentityAvatar$6$com-harleytg-forum-dev-IdentityActivity, reason: not valid java name */
    /* synthetic */ void m13x9ad50760(String str) {
        if (str.equals(this.identityAvatarRequestedUrl)) {
            this.identityAvatarRequestedUrl = "";
        }
    }

    private LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundResource(R.drawable.card_background);
        int dp = dp(compact() ? 12 : 16);
        linearLayout.setPadding(dp, dp, dp, dp);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(compact() ? 8 : 12);
        linearLayout.setLayoutParams(layoutParams);
        return linearLayout;
    }

    private View sectionTitle(String str, String str2) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        TextView text = text(str, 14, getColor(R.color.hcf_text));
        text.setTypeface(null, 1);
        linearLayout.addView(text);
        if (str2 != null && !str2.isEmpty()) {
            linearLayout.addView(text(str2, 10, getColor(R.color.hcf_muted)));
        }
        linearLayout.setPadding(0, 0, 0, dp(9));
        return linearLayout;
    }

    private void addRow(LinearLayout linearLayout, String str, String str2) {
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setPadding(0, dp(6), 0, dp(6));
        TextView text = text(str, 10, getColor(R.color.hcf_muted));
        text.setTypeface(null, 1);
        linearLayout2.addView(text);
        if (str2 == null || str2.trim().isEmpty()) {
            str2 = "—";
        }
        TextView text2 = text(str2, 12, getColor(R.color.hcf_text));
        text2.setTextIsSelectable(true);
        linearLayout2.addView(text2);
        linearLayout.addView(linearLayout2);
    }

    private String displayDate(String str) {
        return (str == null || str.trim().isEmpty()) ? "—" : str;
    }

    private View actionRow(String str, String str2, int i, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(17);
        linearLayout.setBackgroundResource(R.drawable.button_background);
        linearLayout.setPadding(dp(14), 0, dp(14), 0);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setOnClickListener(onClickListener);
        linearLayout.setContentDescription(str);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(i);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setContentDescription(null);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        layoutParams.rightMargin = dp(10);
        linearLayout.addView(imageView, layoutParams);
        TextView text = text(str, 14, getColor(R.color.hcf_cyan_bright));
        text.setTypeface(null, 1);
        text.setIncludeFontPadding(false);
        text.setGravity(16);
        text.setMaxLines(1);
        linearLayout.addView(text, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(compact() ? 46 : 50));
        layoutParams2.topMargin = dp(8);
        linearLayout.setLayoutParams(layoutParams2);
        return linearLayout;
    }

    private TextView text(String str, int i, int i2) {
        TextView textView = new TextView(this);
        if (str == null) {
            str = "";
        }
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        textView.setLineSpacing(0.0f, 1.08f);
        return textView;
    }

    private boolean compact() {
        return getResources().getConfiguration().screenHeightDp <= 720;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
