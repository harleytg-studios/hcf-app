package com.harleytg.forum;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.harleytg.forum.ForumIdentity;

/* loaded from: classes.dex */
public final class SupportContactActivity extends ThemedActivity {
    private static final String SUPPORT_EMAIL = "harleytg.hq@gmail.com";
    private Spinner categoryField;
    private EditText expectedField;
    private EditText guestNameField;
    private EditText guestReplyEmailField;
    private ForumIdentity.Snapshot identity;
    private CheckBox includeDiagnostics;
    private CheckBox includeIdentity;
    private CheckBox includeRoute;
    private EditText messageField;
    private SharedPreferences prefs;
    private EditText stepsField;
    private EditText subjectField;

    @Override // com.harleytg.forum.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override // com.harleytg.forum.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ThemeManager.apply(this);
        this.identity = ForumIdentity.load(this);
        this.prefs = getSharedPreferences("hcf_app", 0);
        setTitle("Contact Support");
        buildUi();
    }

    private void buildUi() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(getColor(R.color.hcf_bg));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(12), dp(10), dp(12), dp(10));
        linearLayout2.setBackgroundColor(getColor(R.color.hcf_app_bar));
        ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
        iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SupportContactActivity.this.m204lambda$buildUi$0$comharleytgforumdevSupportContactActivity(view);
            }
        });
        linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setContentDescription("Harley's Clan Forum");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        layoutParams.leftMargin = dp(4);
        linearLayout2.addView(imageView, layoutParams);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.leftMargin = dp(10);
        linearLayout3.addView(label("Contact Support", 19, R.color.hcf_text, true));
        linearLayout3.addView(label("Forum help, app support & report tools", 10, R.color.hcf_cyan_bright, true));
        linearLayout2.addView(linearLayout3, layoutParams2);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setPadding(dp(12), dp(12), dp(12), dp(24));
        linearLayout4.addView(supportPanel("Your account", "Locked forum identity and reply information", R.drawable.fa_user, accountBody(), false));
        linearLayout4.addView(supportPanel("Support request", "Tell us what happened and what you expected", R.drawable.fa_envelope, requestBody(), false));
        linearLayout4.addView(supportPanel("Report context", "Read-only app and device information", R.drawable.fa_circle_info, contextBody(), false));
        linearLayout4.addView(supportPanel("Privacy & send", "Choose what to include, preview, then send", R.drawable.fa_shield, privacyBody(), false));
        TextView label = label("Harley's Clan Forum • Contact Support v2 • v1.0", 9, R.color.hcf_hint, false);
        label.setGravity(17);
        label.setPadding(0, dp(6), 0, dp(4));
        linearLayout4.addView(label);
        scrollView.addView(linearLayout4, new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        setContentView(linearLayout);
    }

    /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m204lambda$buildUi$0$comharleytgforumdevSupportContactActivity(View view) {
        finish();
    }

    private View accountBody() {
        String str;
        LinearLayout bodyContainer = bodyContainer();
        if (this.identity.loggedIn) {
            String str2 = "Not exposed";
            addLockedRow(bodyContainer, "Display name", nonEmpty(this.identity.displayName, "Not exposed"));
            if (this.identity.username.isEmpty()) {
                str = "Not exposed";
            } else {
                str = "@" + this.identity.username;
            }
            addLockedRow(bodyContainer, "Username", str);
            if (!this.identity.email.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(this.identity.email);
                sb.append(this.identity.emailConfirmed ? " • verified" : " • unverified");
                str2 = sb.toString();
            }
            addLockedRow(bodyContainer, "Forum email", str2);
            addLockedRow(bodyContainer, "Role", this.identity.identityMetaLabel());
            addLockedRow(bodyContainer, "Forum host", nonEmpty(this.identity.host, "forum.harleytg.com"));
            TextView label = label("These identity fields are synced from the signed-in forum session and cannot be edited here.", 10, R.color.hcf_muted, false);
            label.setPadding(0, dp(4), 0, 0);
            bodyContainer.addView(label);
        } else {
            TextView label2 = label("No signed-in forum identity was detected. Enter a name and reply email for this support request.", 11, R.color.hcf_muted, false);
            label2.setPadding(0, 0, 0, dp(10));
            bodyContainer.addView(label2);
            EditText input = input("Name or display name", 1, false);
            this.guestNameField = input;
            addField(bodyContainer, "Name", input);
            EditText input2 = input("Email support can reply to", 33, false);
            this.guestReplyEmailField = input2;
            addField(bodyContainer, "Reply email", input2);
        }
        return bodyContainer;
    }

    private View requestBody() {
        LinearLayout bodyContainer = bodyContainer();
        addLockedRow(bodyContainer, "Support destination", SUPPORT_EMAIL);
        bodyContainer.addView(fieldLabel("Support type"));
        this.categoryField = new Spinner(this);
        this.categoryField.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"App issue", "Account issue", "Notifications", "Login / identity", "Bug report", "Feature request", "Update / install", "Forum / WebView", "Privacy / security", "Other"}));
        this.categoryField.setBackgroundResource(R.drawable.identity_card_background);
        this.categoryField.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(50));
        layoutParams.bottomMargin = dp(11);
        bodyContainer.addView(this.categoryField, layoutParams);
        EditText input = input("Short description of the issue", 16385, false);
        this.subjectField = input;
        addField(bodyContainer, "Subject", input);
        EditText input2 = input("What happened? Include any error message you saw.", 147457, true);
        this.messageField = input2;
        input2.setMinLines(5);
        this.messageField.setGravity(8388659);
        addField(bodyContainer, "What happened", this.messageField);
        EditText input3 = input("Optional: steps that reproduce the problem", 147457, true);
        this.stepsField = input3;
        input3.setMinLines(3);
        this.stepsField.setGravity(8388659);
        addField(bodyContainer, "Steps to reproduce", this.stepsField);
        EditText input4 = input("Optional: what should have happened instead", 147457, true);
        this.expectedField = input4;
        input4.setMinLines(3);
        this.expectedField.setGravity(8388659);
        addField(bodyContainer, "Expected behavior", this.expectedField);
        return bodyContainer;
    }

    private View contextBody() {
        LinearLayout bodyContainer = bodyContainer();
        String activeHost = activeHost();
        String currentRoute = currentRoute();
        addLockedRow(bodyContainer, "App", "Harley's Clan Forum v" + BuildInfo.VERSION + " • build " + BuildInfo.VERSION_CODE);
        addLockedRow(bodyContainer, "Package", getPackageName());
        addLockedRow(bodyContainer, "Android", Build.VERSION.RELEASE + " • API " + Build.VERSION.SDK_INT);
        addLockedRow(bodyContainer, "Device", Build.MANUFACTURER + " " + Build.MODEL);
        addLockedRow(bodyContainer, "Forum host", activeHost);
        addLockedRow(bodyContainer, "Current route", currentRoute);
        addLockedRow(bodyContainer, "Theme", this.prefs.getString("app_theme", "system"));
        TextView label = label("Context is shown here for transparency. Only the items selected under Privacy & send are added to the email.", 10, R.color.hcf_muted, false);
        label.setPadding(0, dp(4), 0, 0);
        bodyContainer.addView(label);
        return bodyContainer;
    }

    private View privacyBody() {
        LinearLayout bodyContainer = bodyContainer();
        CheckBox check = check("Include forum identity", this.identity.loggedIn, this.identity.loggedIn);
        this.includeIdentity = check;
        bodyContainer.addView(check);
        CheckBox check2 = check("Include sanitized app/device diagnostics", false, true);
        this.includeDiagnostics = check2;
        bodyContainer.addView(check2);
        CheckBox check3 = check("Include current forum route", false, true);
        this.includeRoute = check3;
        bodyContainer.addView(check3);
        TextView label = label("Passwords, cookies, authentication tokens and private message contents are never included. The app opens your email client so you can review or cancel before sending.", 10, R.color.hcf_muted, false);
        label.setPadding(dp(2), dp(2), dp(2), dp(10));
        bodyContainer.addView(label);
        bodyContainer.addView(actionButton("Preview Report", R.drawable.fa_list, new View.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SupportContactActivity.this.m206x8f236721(view);
            }
        }));
        View actionButton = actionButton("Continue to Email", R.drawable.fa_envelope, new View.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SupportContactActivity.this.m207x49d8d62(view);
            }
        });
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) actionButton.getLayoutParams();
        layoutParams.bottomMargin = 0;
        actionButton.setLayoutParams(layoutParams);
        bodyContainer.addView(actionButton);
        TextView label2 = label("Support email: harleytg.hq@gmail.com  •  tap to copy", 10, R.color.hcf_cyan_bright, true);
        label2.setGravity(17);
        label2.setPadding(0, dp(10), 0, 0);
        label2.setClickable(true);
        label2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SupportContactActivity.this.m208x7a17b3a3(view);
            }
        });
        bodyContainer.addView(label2);
        return bodyContainer;
    }

    /* renamed from: lambda$privacyBody$1$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m206x8f236721(View view) {
        previewReport();
    }

    /* renamed from: lambda$privacyBody$2$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m207x49d8d62(View view) {
        composeEmail();
    }

    /* renamed from: lambda$privacyBody$3$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m208x7a17b3a3(View view) {
        copySupportEmail();
    }

    private View supportPanel(String str, String str2, int i, View view, boolean z) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(12);
        linearLayout.setLayoutParams(layoutParams);
        final LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        linearLayout2.setClickable(true);
        linearLayout2.setFocusable(true);
        linearLayout2.setPadding(dp(15), dp(13), dp(12), dp(13));
        linearLayout2.setBackgroundResource(z ? R.drawable.settings_section_header_expanded : R.drawable.settings_section_header_collapsed);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(i);
        imageView.setColorFilter(getColor(R.color.hcf_cyan_bright));
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(dp(24), dp(24));
        layoutParams2.rightMargin = dp(11);
        linearLayout2.addView(imageView, layoutParams2);
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        linearLayout3.addView(label(str, 14, R.color.hcf_accent_text, true));
        linearLayout3.addView(label(str2, 10, R.color.hcf_muted, false));
        linearLayout2.addView(linearLayout3, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final TextView label = label("›", 22, R.color.hcf_cyan_bright, false);
        label.setGravity(17);
        label.setRotation(z ? 90.0f : 0.0f);
        linearLayout2.addView(label, new LinearLayout.LayoutParams(dp(28), -1));
        linearLayout.addView(linearLayout2);
        final LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(1);
        linearLayout4.setBackgroundResource(R.drawable.settings_section_body);
        linearLayout4.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (view != null) {
            linearLayout4.addView(view, new LinearLayout.LayoutParams(-1, -2));
        }
        linearLayout4.setVisibility(z ? 0 : 8);
        linearLayout4.setAlpha(z ? 1.0f : 0.0f);
        linearLayout4.setTranslationY(z ? 0.0f : -dp(6));
        linearLayout.addView(linearLayout4);
        final boolean[] zArr = {z};
        linearLayout2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                SupportContactActivity.this.m209xae9de76a(zArr, label, linearLayout4, linearLayout2, view2);
            }
        });
        return linearLayout;
    }

    /* renamed from: lambda$supportPanel$5$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m209xae9de76a(boolean[] zArr, TextView textView, final LinearLayout linearLayout, final LinearLayout linearLayout2, View view) {
        if (zArr[0]) {
            zArr[0] = false;
            textView.animate().rotation(0.0f).setDuration(150L).start();
            linearLayout.animate().alpha(0.0f).translationY(-dp(6)).setDuration(150L).withEndAction(new Runnable() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda5
                @Override // java.lang.Runnable
                public final void run() {
                    SupportContactActivity.lambda$supportPanel$4(linearLayout, linearLayout2);
                }
            }).start();
            return;
        }
        zArr[0] = true;
        linearLayout2.setBackgroundResource(R.drawable.settings_section_header_expanded);
        linearLayout.setVisibility(0);
        linearLayout.setAlpha(0.0f);
        linearLayout.setTranslationY(-dp(6));
        textView.animate().rotation(90.0f).setDuration(170L).start();
        linearLayout.animate().alpha(1.0f).translationY(0.0f).setDuration(180L).start();
    }

    static /* synthetic */ void lambda$supportPanel$4(LinearLayout linearLayout, LinearLayout linearLayout2) {
        linearLayout.setVisibility(8);
        linearLayout2.setBackgroundResource(R.drawable.settings_section_header_collapsed);
    }

    private LinearLayout bodyContainer() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(0);
        return linearLayout;
    }

    private void addLockedRow(LinearLayout linearLayout, String str, String str2) {
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setBackgroundResource(R.drawable.identity_card_background);
        linearLayout2.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(8);
        linearLayout2.setLayoutParams(layoutParams);
        TextView label = label(str, 9, R.color.hcf_meta, true);
        TextView label2 = label(str2, 12, R.color.hcf_text, false);
        label2.setPadding(0, dp(2), 0, 0);
        linearLayout2.addView(label);
        linearLayout2.addView(label2);
        linearLayout.addView(linearLayout2);
    }

    private void addField(LinearLayout linearLayout, String str, EditText editText) {
        linearLayout.addView(fieldLabel(str));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = dp(11);
        linearLayout.addView(editText, layoutParams);
    }

    private TextView fieldLabel(String str) {
        TextView label = label(str, 10, R.color.hcf_meta, true);
        label.setPadding(dp(2), 0, 0, dp(4));
        return label;
    }

    private EditText input(String str, int i, boolean z) {
        EditText editText = new EditText(this);
        editText.setHint(str);
        editText.setHintTextColor(getColor(R.color.hcf_hint));
        editText.setTextColor(getColor(R.color.hcf_text));
        editText.setTextSize(13.0f);
        editText.setInputType(i);
        editText.setSingleLine(!z);
        if (!z) {
            editText.setImeOptions(5);
        }
        editText.setBackgroundResource(R.drawable.identity_card_background);
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        return editText;
    }

    private CheckBox check(String str, boolean z, boolean z2) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(str);
        checkBox.setTextColor(getColor(R.color.hcf_text));
        checkBox.setTextSize(12.0f);
        checkBox.setChecked(z);
        checkBox.setEnabled(z2);
        checkBox.setPadding(0, dp(1), 0, dp(1));
        return checkBox;
    }

    private View actionButton(String str, int i, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(17);
        linearLayout.setBackgroundResource(R.drawable.button_background);
        linearLayout.setClickable(true);
        linearLayout.setFocusable(true);
        linearLayout.setPadding(dp(16), 0, dp(16), 0);
        linearLayout.setContentDescription(str);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(i);
        imageView.setColorFilter(getColor(R.color.hcf_cyan_bright));
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        layoutParams.rightMargin = dp(10);
        linearLayout.addView(imageView, layoutParams);
        TextView label = label(str, 13, R.color.hcf_cyan_bright, true);
        label.setGravity(17);
        label.setIncludeFontPadding(false);
        linearLayout.addView(label, new LinearLayout.LayoutParams(-2, -2));
        linearLayout.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(52));
        layoutParams2.bottomMargin = dp(9);
        linearLayout.setLayoutParams(layoutParams2);
        return linearLayout;
    }

    private void previewReport() {
        final String buildReportBody = buildReportBody();
        if (buildReportBody == null) {
            return;
        }
        new AlertDialog.Builder(this).setTitle("Support report preview").setMessage(buildReportBody).setNegativeButton("Close", (DialogInterface.OnClickListener) null).setPositiveButton("Continue to Email", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.SupportContactActivity$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                SupportContactActivity.this.m205xc9453f78(buildReportBody, dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$previewReport$6$com-harleytg-forum-dev-SupportContactActivity, reason: not valid java name */
    /* synthetic */ void m205xc9453f78(String str, DialogInterface dialogInterface, int i) {
        openEmail(str);
    }

    private void composeEmail() {
        String buildReportBody = buildReportBody();
        if (buildReportBody != null) {
            openEmail(buildReportBody);
        }
    }

    private String buildReportBody() {
        String clean;
        String clean2;
        String obj = this.categoryField.getSelectedItem() == null ? "App issue" : this.categoryField.getSelectedItem().toString();
        String clean3 = clean(this.subjectField.getText().toString());
        String clean4 = clean(this.messageField.getText().toString());
        String clean5 = clean(this.stepsField.getText().toString());
        String clean6 = clean(this.expectedField.getText().toString());
        if (clean4.isEmpty()) {
            Toast.makeText(this, "Please describe what happened.", 0).show();
            this.messageField.requestFocus();
            return null;
        }
        if (this.identity.loggedIn) {
            clean = nonEmpty(this.identity.displayName, this.identity.username);
        } else {
            EditText editText = this.guestNameField;
            clean = clean(editText == null ? "" : editText.getText().toString());
        }
        if (this.identity.loggedIn) {
            clean2 = this.identity.email;
        } else {
            EditText editText2 = this.guestReplyEmailField;
            clean2 = clean(editText2 == null ? "" : editText2.getText().toString());
        }
        StringBuilder sb = new StringBuilder("Hello Harley's Clan Forum Support,\n\nSupport type: ");
        sb.append(obj);
        sb.append("\nSubject: ");
        if (!clean3.isEmpty()) {
            obj = clean3;
        }
        sb.append(obj);
        sb.append("\n\n--- What happened ---\n");
        sb.append(clean4);
        sb.append("\n\n");
        if (!clean5.isEmpty()) {
            sb.append("--- Steps to reproduce ---\n");
            sb.append(clean5);
            sb.append("\n\n");
        }
        if (!clean6.isEmpty()) {
            sb.append("--- Expected behavior ---\n");
            sb.append(clean6);
            sb.append("\n\n");
        }
        sb.append("--- Contact ---\nName: ");
        if (clean.isEmpty()) {
            clean = "Not provided";
        }
        sb.append(clean);
        sb.append("\nReply email: ");
        if (clean2.isEmpty()) {
            clean2 = "Not provided";
        }
        sb.append(clean2);
        sb.append('\n');
        CheckBox checkBox = this.includeIdentity;
        if (checkBox != null && checkBox.isChecked() && this.identity.loggedIn) {
            sb.append("\n--- Forum Identity ---\nDisplay name: ");
            String str = "Not exposed";
            sb.append(nonEmpty(this.identity.displayName, "Not exposed"));
            sb.append("\nUsername: ");
            if (!this.identity.username.isEmpty()) {
                str = "@" + this.identity.username;
            }
            sb.append(str);
            sb.append('\n');
            if (!this.identity.email.isEmpty()) {
                sb.append("Forum email: ");
                sb.append(this.identity.email);
                sb.append(this.identity.emailConfirmed ? " (verified)" : "");
                sb.append('\n');
            }
            sb.append("Role: ");
            sb.append(this.identity.identityMetaLabel());
            sb.append('\n');
        }
        CheckBox checkBox2 = this.includeDiagnostics;
        if (checkBox2 != null && checkBox2.isChecked()) {
            sb.append("\n--- Sanitized Diagnostics ---\nApp: Harley's Clan Forum v" + BuildInfo.VERSION + "\nVersion code: " + BuildInfo.VERSION_CODE + "\nPackage: ");
            sb.append(getPackageName());
            sb.append("\nAndroid: ");
            sb.append(Build.VERSION.RELEASE);
            sb.append(" (API ");
            sb.append(Build.VERSION.SDK_INT);
            sb.append(")\nDevice: ");
            sb.append(Build.MANUFACTURER);
            sb.append(' ');
            sb.append(Build.MODEL);
            sb.append("\nForum host: ");
            sb.append(activeHost());
            sb.append("\nTheme: ");
            sb.append(this.prefs.getString("app_theme", "system"));
            sb.append("\nNotifications: ");
            sb.append(NotificationHelper.status(this));
            sb.append('\n');
        }
        CheckBox checkBox3 = this.includeRoute;
        if (checkBox3 != null && checkBox3.isChecked()) {
            sb.append("\n--- Current Route ---\n");
            sb.append(currentRoute());
            sb.append('\n');
        }
        sb.append("\nPrivacy: passwords, cookies, tokens and private-message content are not included.\nSent from Harley's Clan Forum Contact Support v2.");
        return sb.toString();
    }

    private void openEmail(String str) {
        String obj = this.categoryField.getSelectedItem() == null ? "App issue" : this.categoryField.getSelectedItem().toString();
        String clean = clean(this.subjectField.getText().toString());
        if (clean.isEmpty()) {
            clean = obj;
        }
        String str2 = "HCF Support • " + obj + " • v1.0 • " + clean;
        try {
            String str3 = "mailto:harleytg.hq@gmail.com?subject=" + Uri.encode(str2) + "&body=" + Uri.encode(str);
            Intent intent = new Intent("android.intent.action.SENDTO");
            intent.setData(Uri.parse(str3));
            intent.putExtra("android.intent.extra.EMAIL", new String[]{SUPPORT_EMAIL});
            intent.putExtra("android.intent.extra.SUBJECT", str2);
            intent.putExtra("android.intent.extra.TEXT", str);
            startActivity(Intent.createChooser(intent, "Send support email"));
            AppLogger.info(this, "support_contact_v2", "mailto recipient=harleytg.hq@gmail.com");
        } catch (Throwable th) {
            Toast.makeText(this, "No email app is available. Email harleytg.hq@gmail.com", 1).show();
            AppLogger.error(this, "support_contact_v2", th.getClass().getSimpleName());
        }
    }

    private void copySupportEmail() {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF support email", SUPPORT_EMAIL));
            }
            Toast.makeText(this, "Support email copied.", 0).show();
        } catch (Throwable unused) {
            Toast.makeText(this, SUPPORT_EMAIL, 1).show();
        }
    }

    private String activeHost() {
        String string = this.prefs.getString("active_host", "forum.harleytg.com");
        return ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
    }

    private String currentRoute() {
        String string = this.prefs.getString("last_recoverable_url", "");
        if (string == null || string.trim().isEmpty()) {
            return "https://" + activeHost() + "/";
        }
        return AppLogger.safeUrl(string);
    }

    private TextView label(String str, int i, int i2, boolean z) {
        TextView textView = new TextView(this);
        if (str == null) {
            str = "";
        }
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(getColor(i2));
        if (z) {
            textView.setTypeface(null, 1);
        }
        return textView;
    }

    private static String clean(String str) {
        return str == null ? "" : str.trim();
    }

    private static String nonEmpty(String str, String str2) {
        return (str == null || str.trim().isEmpty()) ? str2 : str.trim();
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
