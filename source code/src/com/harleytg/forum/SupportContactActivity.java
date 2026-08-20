package com.harleytg.forum;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Contact Support v2.
 *
 * Stable v10000072 intentionally starts every support section collapsed on each
 * new Activity opening. Expanded/collapsed UI state is never persisted.
 */
public final class SupportContactActivity extends ThemedActivity {
    private static final String SUPPORT_EMAIL = "harleytg.hq@gmail.com";

    private ForumIdentity.Snapshot identity;
    private EditText nameField;
    private EditText replyEmailField;
    private Spinner categoryField;
    private EditText subjectField;
    private EditText messageField;
    private CheckBox includeIdentity;
    private CheckBox includeDiagnostics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        identity = ForumIdentity.load(this);
        setTitle("Contact Support");
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.hcf_bg));
        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(24));

        content.addView(collapsibleCard(
                "Your account",
                identity.loggedIn ? "Current forum identity" : "Guest support is available",
                buildAccountSection()));
        content.addView(collapsibleCard(
                "Support request",
                "Describe what you need help with",
                buildRequestSection()));
        content.addView(collapsibleCard(
                "Report context",
                "Choose optional account/device details",
                buildContextSection()));
        content.addView(collapsibleCard(
                "Privacy & send",
                "Review what leaves the app",
                buildPrivacySection()));

        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackgroundColor(getColor(R.color.hcf_app_bar));

        ImageButton back = UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        logoParams.leftMargin = dp(4);
        header.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        TextView title = label("Contact Support", 19, R.color.hcf_text, true);
        TextView meta = label("Harley's Clan Forum • " + BuildInfo.VERSION_TAG,
                10, R.color.hcf_cyan_bright, true);
        titles.addView(title);
        titles.addView(meta);
        header.addView(titles, titleParams);
        return header;
    }

    private View collapsibleCard(String titleText, String subtitleText, View sectionBody) {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        header.setFocusable(true);
        header.setBackgroundResource(R.drawable.quick_action_background);
        header.setPadding(dp(12), dp(10), dp(10), dp(10));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(titleText, 14, R.color.hcf_text, true);
        TextView subtitle = label(subtitleText, 10, R.color.hcf_muted, false);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = label("+", 22, R.color.hcf_cyan_bright, true);
        state.setGravity(Gravity.CENTER);
        state.setContentDescription(titleText + " collapsed");
        header.addView(state, new LinearLayout.LayoutParams(dp(36), dp(36)));

        // Required v10000072 default: CLOSED every time Contact Support opens.
        sectionBody.setVisibility(View.GONE);
        header.setContentDescription(titleText + ", collapsed. Tap to expand.");
        header.setOnClickListener(v -> {
            boolean opening = sectionBody.getVisibility() != View.VISIBLE;
            sectionBody.setVisibility(opening ? View.VISIBLE : View.GONE);
            state.setText(opening ? "−" : "+");
            state.setContentDescription(titleText + (opening ? " expanded" : " collapsed"));
            header.setContentDescription(titleText + (opening
                    ? ", expanded. Tap to collapse."
                    : ", collapsed. Tap to expand."));
        });

        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(10);
        card.addView(sectionBody, bodyParams);
        return card;
    }

    private View buildAccountSection() {
        LinearLayout body = sectionBody();
        if (identity.loggedIn) {
            addRow(body, "Display name", nonEmpty(identity.displayName, "Not exposed"));
            addRow(body, "Username", identity.username.isEmpty() ? "Not exposed" : "@" + identity.username);
            addRow(body, "Forum email", identity.email.isEmpty()
                    ? "Not exposed"
                    : identity.email + (identity.emailConfirmed ? " • verified" : ""));
            addRow(body, "Role", identity.identityMetaLabel());
            addRow(body, "Forum host", nonEmpty(identity.host, ForumConfig.PRIMARY_HOST));
        } else {
            TextView guest = label(
                    "You can contact support as a guest. Sign in first if you want the form to include your current forum identity.",
                    11, R.color.hcf_muted, false);
            guest.setLineSpacing(0f, 1.08f);
            body.addView(guest);
        }
        return body;
    }

    private View buildRequestSection() {
        LinearLayout body = sectionBody();
        TextView destination = label("To: " + SUPPORT_EMAIL, 11, R.color.hcf_cyan_bright, true);
        destination.setPadding(0, 0, 0, dp(10));
        body.addView(destination);

        nameField = input("Name or display name", InputType.TYPE_CLASS_TEXT, false);
        String pulledName = !identity.displayName.isEmpty() ? identity.displayName : identity.username;
        if (identity.loggedIn && !pulledName.isEmpty()) nameField.setText(pulledName);
        addField(body, "Name", nameField);

        replyEmailField = input("Email support can reply to",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
        if (identity.loggedIn && !identity.email.isEmpty()) replyEmailField.setText(identity.email);
        addField(body, "Reply email", replyEmailField);

        body.addView(fieldLabel("Category"));
        categoryField = new Spinner(this);
        String[] categories = new String[]{
                "General Support",
                "Account / Sign In",
                "Notifications",
                "Update / Install",
                "Forum / WebView",
                "Privacy / Security",
                "Other"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, categories);
        categoryField.setAdapter(adapter);
        categoryField.setBackgroundResource(R.drawable.identity_card_background);
        categoryField.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        spinnerParams.bottomMargin = dp(11);
        body.addView(categoryField, spinnerParams);

        subjectField = input("Short description of the issue",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
        addField(body, "Subject", subjectField);

        messageField = input(
                "Tell us what happened, what you expected, and any error message you saw.",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                true);
        messageField.setMinLines(6);
        messageField.setGravity(Gravity.TOP | Gravity.START);
        addField(body, "Message", messageField);
        return body;
    }

    private View buildContextSection() {
        LinearLayout body = sectionBody();
        includeIdentity = new CheckBox(this);
        includeIdentity.setText("Include Forum Identity in support message");
        includeIdentity.setTextColor(getColor(R.color.hcf_text));
        includeIdentity.setTextSize(12f);
        includeIdentity.setChecked(identity.loggedIn);
        includeIdentity.setEnabled(identity.loggedIn);
        body.addView(includeIdentity);

        includeDiagnostics = new CheckBox(this);
        includeDiagnostics.setText("Include basic app/device diagnostics");
        includeDiagnostics.setTextColor(getColor(R.color.hcf_text));
        includeDiagnostics.setTextSize(12f);
        includeDiagnostics.setChecked(false);
        body.addView(includeDiagnostics);

        TextView hint = label(
                "Diagnostics are optional and off by default. They include only app version, Android version, device model and forum host — not logs, cookies, passwords or tokens.",
                10, R.color.hcf_muted, false);
        hint.setPadding(dp(4), dp(4), dp(4), 0);
        hint.setLineSpacing(0f, 1.08f);
        body.addView(hint);
        return body;
    }

    private View buildPrivacySection() {
        LinearLayout body = sectionBody();
        TextView copy = label(
                "The app does not submit this form directly. Continue to Email opens your installed mail app with the request prefilled so you can review, edit or cancel it before sending to "
                        + SUPPORT_EMAIL + ".",
                11, R.color.hcf_muted, false);
        copy.setLineSpacing(0f, 1.08f);
        body.addView(copy);

        Button continueButton = new Button(this);
        UiButtons.normalizeText(continueButton);
        continueButton.setText("Continue to Email");
        continueButton.setCompoundDrawablesRelative(null, null, null, null);
        continueButton.setCompoundDrawablePadding(0);
        continueButton.setAllCaps(false);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        continueButton.setPadding(dp(18), 0, dp(18), 0);
        continueButton.setTextColor(getColor(R.color.hcf_cyan_bright));
        continueButton.setTextSize(14f);
        continueButton.setTypeface(null, Typeface.BOLD);
        continueButton.setBackgroundResource(R.drawable.button_background);
        continueButton.setOnClickListener(v -> composeEmail());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.topMargin = dp(14);
        body.addView(continueButton, p);
        return body;
    }

    private void composeEmail() {
        String name = clean(nameField == null ? "" : nameField.getText().toString());
        String reply = clean(replyEmailField == null ? "" : replyEmailField.getText().toString());
        String category = categoryField == null || categoryField.getSelectedItem() == null
                ? "General Support" : categoryField.getSelectedItem().toString();
        String subject = clean(subjectField == null ? "" : subjectField.getText().toString());
        String message = clean(messageField == null ? "" : messageField.getText().toString());

        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message for support.", Toast.LENGTH_SHORT).show();
            if (messageField != null) messageField.requestFocus();
            return;
        }
        if (subject.isEmpty()) subject = category;

        String mailSubject = "HCF Support • " + category + " • " + BuildInfo.VERSION_TAG + " • " + subject;
        StringBuilder body = new StringBuilder();
        body.append("Hello Harley's Clan Forum Support,\n\n");
        body.append(message).append("\n\n");
        body.append("--- Contact Request ---\n");
        body.append("Category: ").append(category).append('\n');
        body.append("Name: ").append(name.isEmpty() ? "Not provided" : name).append('\n');
        body.append("Reply email: ").append(reply.isEmpty() ? "Not provided" : reply).append('\n');

        if (includeIdentity != null && includeIdentity.isChecked() && identity.loggedIn) {
            body.append("\n--- Forum Identity ---\n");
            body.append("Status: Signed in\n");
            body.append("Display name: ").append(nonEmpty(identity.displayName, "Not exposed")).append('\n');
            body.append("Username: ").append(identity.username.isEmpty()
                    ? "Not exposed" : "@" + identity.username).append('\n');
            if (!identity.email.isEmpty()) {
                body.append("Forum email: ").append(identity.email)
                        .append(identity.emailConfirmed ? " (verified)" : "").append('\n');
            }
            body.append("Role: ").append(identity.identityMetaLabel()).append('\n');
            body.append("Forum host: ").append(nonEmpty(identity.host, ForumConfig.PRIMARY_HOST)).append('\n');
        }

        if (includeDiagnostics != null && includeDiagnostics.isChecked()) {
            body.append("\n--- Basic Diagnostics ---\n");
            body.append("App: Harley's Clan Forum ").append(BuildInfo.VERSION_TAG).append('\n');
            body.append("Version code: ").append(BuildInfo.VERSION_CODE).append('\n');
            body.append("Android: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            body.append("Device: ").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append('\n');
            body.append("Forum host: ").append(nonEmpty(identity.host, ForumConfig.PRIMARY_HOST)).append('\n');
        }

        body.append("\nSent from the Harley's Clan Forum in-app Contact Support form.");

        try {
            String mailto = "mailto:" + SUPPORT_EMAIL
                    + "?subject=" + Uri.encode(mailSubject)
                    + "&body=" + Uri.encode(body.toString());
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse(mailto));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{SUPPORT_EMAIL});
            intent.putExtra(Intent.EXTRA_SUBJECT, mailSubject);
            intent.putExtra(Intent.EXTRA_TEXT, body.toString());
            startActivity(Intent.createChooser(intent, "Send support email"));
            AppLogger.info(this, "support_contact",
                    "mailto recipient=" + SUPPORT_EMAIL
                            + " identity=" + (includeIdentity != null && includeIdentity.isChecked() && identity.loggedIn)
                            + " diagnostics=" + (includeDiagnostics != null && includeDiagnostics.isChecked()));
        } catch (Throwable t) {
            Toast.makeText(this, "No email app is available. Email " + SUPPORT_EMAIL,
                    Toast.LENGTH_LONG).show();
            AppLogger.error(this, "support_contact", t.getClass().getSimpleName());
        }
    }

    private LinearLayout sectionBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(2), dp(2), dp(2), dp(2));
        return body;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        card.setLayoutParams(p);
        return card;
    }

    private void addRow(LinearLayout parent, String labelText, String valueText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(7));
        TextView label = label(labelText, 9, R.color.hcf_meta, true);
        TextView value = label(valueText, 12, R.color.hcf_text, false);
        value.setPadding(0, dp(1), 0, 0);
        row.addView(label);
        row.addView(value);
        parent.addView(row);
    }

    private void addField(LinearLayout parent, String labelText, EditText field) {
        parent.addView(fieldLabel(labelText));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(11);
        parent.addView(field, p);
    }

    private TextView fieldLabel(String value) {
        TextView label = label(value, 10, R.color.hcf_meta, true);
        label.setPadding(dp(2), 0, 0, dp(4));
        return label;
    }

    private EditText input(String hint, int inputType, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(getColor(R.color.hcf_hint));
        input.setTextColor(getColor(R.color.hcf_text));
        input.setTextSize(13f);
        input.setInputType(inputType);
        input.setSingleLine(!multiline);
        if (!multiline) input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        input.setBackgroundResource(R.drawable.identity_card_background);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return input;
    }

    private TextView label(String value, int sizeSp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(colorRes));
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
