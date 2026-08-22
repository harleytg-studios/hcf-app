package com.harleytg.forum.dev;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: classes.dex */
public final class LogsActivity extends ThemedActivity {
    private static final int EXPORT_TEXT = 611;
    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_CRASH = "CRASH";
    private static final String FILTER_ERROR = "ERROR";
    private static final String FILTER_INFO = "INFO";
    private static final String FILTER_NETWORK = "NETWORK";
    private static final String FILTER_WARN = "WARN";
    private static final String FILTER_WEBVIEW = "WEBVIEW";
    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\S+) \\[(INFO|WARN|ERROR|CRASH)] ([^|]+?)(?: \\| (.*))?$");
    private static final int MAX_DISPLAY_CHARS = 180000;
    private static final String MODE_DIAGNOSTICS = "diagnostics";
    private static final String MODE_LOGS = "logs";
    private LinearLayout chipRow;
    private Button clearButton;
    private ScrollView contentScroll;
    private TextView contentText;
    private Button diagnosticsTab;
    private LinearLayout logsControls;
    private Button logsTab;
    private Uri pendingExportUri;
    private EditText searchInput;
    private TextView statusLine;
    private TextView viewerMeta;
    private TextView viewerSubtitle;
    private TextView viewerTitle;
    private String currentMode = MODE_LOGS;
    private String activeFilter = FILTER_ALL;
    private boolean groupRepeats = true;
    private String rawLogs = "";
    private String visiblePlainText = "";

    @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
    public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        super.onSharedPreferenceChanged(sharedPreferences, str);
    }

    @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            ThemeManager.apply(this);
            int color = ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(color);
            getWindow().setNavigationBarColor(color);
            setContentView(buildView());
            AppLogger.info(this, "logs_open", "system-ui-v3");
            refreshData();
        } catch (Throwable th) {
            try {
                AppLogger.error(this, "logs_screen_recovery", th.getClass().getSimpleName());
            } catch (Throwable unused) {
            }
            setContentView(buildRecoveryView(th));
        }
    }

    private View buildView() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        linearLayout.addView(buildHeader());
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setPadding(dp(14), dp(12), dp(14), dp(14));
        linearLayout2.addView(buildTabs(), new LinearLayout.LayoutParams(-1, dp(44)));
        linearLayout2.addView(buildStatusCard(), marginParams(-1, -2, 0, 10, 0, 0));
        linearLayout2.addView(buildActionRow(), marginParams(-1, dp(44), 0, 10, 0, 0));
        LinearLayout buildLogsControls = buildLogsControls();
        this.logsControls = buildLogsControls;
        linearLayout2.addView(buildLogsControls, marginParams(-1, -2, 0, 10, 0, 0));
        LinearLayout linearLayout3 = new LinearLayout(this);
        linearLayout3.setOrientation(1);
        if (ThemeManager.isAmoled(this)) {
            linearLayout3.setBackgroundColor(Color.rgb(3, 5, 7));
        } else {
            linearLayout3.setBackgroundResource(R.drawable.card_background);
        }
        linearLayout3.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout linearLayout4 = new LinearLayout(this);
        linearLayout4.setOrientation(0);
        linearLayout4.setGravity(16);
        TextView label = label("App Logs", 16.0f, R.color.hcf_accent_text, true);
        this.viewerTitle = label;
        linearLayout4.addView(label, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView label2 = label("", 10.0f, R.color.hcf_muted, false);
        this.viewerMeta = label2;
        label2.setGravity(8388613);
        linearLayout4.addView(this.viewerMeta, new LinearLayout.LayoutParams(-2, -2));
        linearLayout3.addView(linearLayout4);
        TextView label3 = label("Local troubleshooting history from this app", 11.0f, R.color.hcf_muted, false);
        this.viewerSubtitle = label3;
        label3.setPadding(0, dp(2), 0, dp(8));
        linearLayout3.addView(this.viewerSubtitle);
        View view = new View(this);
        view.setBackgroundColor(getColor(R.color.hcf_divider));
        linearLayout3.addView(view, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scrollView = new ScrollView(this);
        this.contentScroll = scrollView;
        scrollView.setFillViewport(true);
        this.contentScroll.setClipToPadding(false);
        TextView textView = new TextView(this);
        this.contentText = textView;
        textView.setTextColor(getColor(R.color.hcf_text));
        this.contentText.setTextSize(10.5f);
        this.contentText.setTextIsSelectable(true);
        this.contentText.setTypeface(Typeface.MONOSPACE);
        this.contentText.setIncludeFontPadding(false);
        this.contentText.setLineSpacing(dp(1), 1.05f);
        this.contentText.setPadding(0, dp(10), 0, dp(8));
        this.contentText.setHorizontallyScrolling(false);
        this.contentText.setBreakStrategy(0);
        this.contentText.setHyphenationFrequency(0);
        this.contentScroll.addView(this.contentText, new FrameLayout.LayoutParams(-1, -2));
        linearLayout3.addView(this.contentScroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        linearLayout2.addView(linearLayout3, marginParams(-1, 0, 1.0f, 0, 10, 0, 0));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return linearLayout;
    }

    private View buildHeader() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(dp(8), dp(5), dp(8), dp(5));
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
        linearLayout.setMinimumHeight(dp(56));
        ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back to App Settings");
        iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m26lambda$buildHeader$0$comharleytgforumdevLogsActivity(view);
            }
        });
        linearLayout.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        layoutParams.leftMargin = dp(4);
        linearLayout.addView(imageView, layoutParams);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams2.leftMargin = dp(10);
        linearLayout.addView(linearLayout2, layoutParams2);
        linearLayout2.addView(label("Logs & Diagnostics", 18.0f, R.color.hcf_text, true));
        TextView label = label("v1.0 • Local troubleshooting", 10.0f, R.color.hcf_meta, true);
        label.setPadding(0, dp(2), 0, 0);
        linearLayout2.addView(label);
        return linearLayout;
    }

    /* renamed from: lambda$buildHeader$0$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m26lambda$buildHeader$0$comharleytgforumdevLogsActivity(View view) {
        finish();
    }

    private View buildTabs() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        this.logsTab = segmentButton("Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m29lambda$buildTabs$1$comharleytgforumdevLogsActivity(view);
            }
        });
        this.diagnosticsTab = segmentButton("Diagnostics", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m30lambda$buildTabs$2$comharleytgforumdevLogsActivity(view);
            }
        });
        linearLayout.addView(this.logsTab, weightedTabParams(false));
        linearLayout.addView(this.diagnosticsTab, weightedTabParams(true));
        updateTabStyles();
        return linearLayout;
    }

    /* renamed from: lambda$buildTabs$1$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m29lambda$buildTabs$1$comharleytgforumdevLogsActivity(View view) {
        switchMode(MODE_LOGS);
    }

    /* renamed from: lambda$buildTabs$2$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m30lambda$buildTabs$2$comharleytgforumdevLogsActivity(View view) {
        switchMode(MODE_DIAGNOSTICS);
    }

    private LinearLayout.LayoutParams weightedTabParams(boolean z) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        if (z) {
            layoutParams.leftMargin = dp(8);
        }
        return layoutParams;
    }

    private View buildStatusCard() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        if (ThemeManager.isAmoled(this)) {
            linearLayout.setBackgroundColor(Color.rgb(3, 5, 7));
        } else {
            linearLayout.setBackgroundResource(R.drawable.card_background);
        }
        linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
        linearLayout.addView(label("Device status", 16.0f, R.color.hcf_accent_text, true));
        TextView label = label("Current app and forum health at a glance", 11.0f, R.color.hcf_muted, false);
        label.setPadding(0, dp(2), 0, dp(7));
        linearLayout.addView(label);
        TextView label2 = label("Checking app status…", 11.0f, R.color.hcf_text, false);
        this.statusLine = label2;
        label2.setLineSpacing(0.0f, 1.12f);
        linearLayout.addView(this.statusLine);
        return linearLayout;
    }

    private View buildActionRow() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.addView(actionButton("Refresh", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m22lambda$buildActionRow$3$comharleytgforumdevLogsActivity(view);
            }
        }), weightedActionParams(0, 0));
        linearLayout.addView(actionButton("Copy", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m23lambda$buildActionRow$4$comharleytgforumdevLogsActivity(view);
            }
        }), weightedActionParams(6, 0));
        linearLayout.addView(actionButton("Export", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m24lambda$buildActionRow$5$comharleytgforumdevLogsActivity(view);
            }
        }), weightedActionParams(6, 0));
        Button actionButton = actionButton("Clear", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m25lambda$buildActionRow$6$comharleytgforumdevLogsActivity(view);
            }
        });
        this.clearButton = actionButton;
        linearLayout.addView(actionButton, weightedActionParams(6, 0));
        return linearLayout;
    }

    /* renamed from: lambda$buildActionRow$3$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m22lambda$buildActionRow$3$comharleytgforumdevLogsActivity(View view) {
        refreshData();
    }

    /* renamed from: lambda$buildActionRow$4$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m23lambda$buildActionRow$4$comharleytgforumdevLogsActivity(View view) {
        copyVisible();
    }

    /* renamed from: lambda$buildActionRow$5$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m24lambda$buildActionRow$5$comharleytgforumdevLogsActivity(View view) {
        exportVisible();
    }

    /* renamed from: lambda$buildActionRow$6$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m25lambda$buildActionRow$6$comharleytgforumdevLogsActivity(View view) {
        confirmClearLogs();
    }

    private LinearLayout buildLogsControls() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        EditText editText = new EditText(this);
        this.searchInput = editText;
        editText.setSingleLine(true);
        this.searchInput.setTextColor(getColor(R.color.hcf_text));
        this.searchInput.setHintTextColor(getColor(R.color.hcf_hint));
        this.searchInput.setHint("Search app logs…");
        this.searchInput.setTextSize(13.0f);
        this.searchInput.setIncludeFontPadding(false);
        this.searchInput.setImeOptions(3);
        this.searchInput.setBackgroundResource(R.drawable.quick_action_background);
        this.searchInput.setPadding(dp(14), 0, dp(14), 0);
        linearLayout.addView(this.searchInput, new LinearLayout.LayoutParams(-1, dp(44)));
        this.searchInput.addTextChangedListener(new TextWatcher() { // from class: com.harleytg.forum.dev.LogsActivity.1
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                LogsActivity.this.renderLogs();
            }
        });
        TextView label = label("Filter logs", 10.0f, R.color.hcf_cyan, true);
        label.setPadding(dp(4), dp(8), 0, dp(6));
        linearLayout.addView(label);
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.chipRow = linearLayout2;
        linearLayout2.setOrientation(1);
        this.chipRow.setClipChildren(false);
        this.chipRow.setClipToPadding(false);
        linearLayout.addView(this.chipRow, new LinearLayout.LayoutParams(-1, dp(70)));
        rebuildFilterChips();
        return linearLayout;
    }

    private Button segmentButton(String str, View.OnClickListener onClickListener) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(str);
        button.setTextSize(12.0f);
        button.setGravity(17);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(onClickListener);
        return button;
    }

    private Button actionButton(String str, View.OnClickListener onClickListener) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(str);
        button.setTextSize(11.0f);
        button.setTextColor(getColor(R.color.hcf_accent_text));
        button.setTypeface(null, 1);
        button.setBackgroundResource(R.drawable.quick_action_background);
        button.setGravity(17);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setOnClickListener(onClickListener);
        return button;
    }

    private LinearLayout.LayoutParams weightedActionParams(int i, int i2) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        layoutParams.leftMargin = dp(i);
        layoutParams.rightMargin = dp(i2);
        return layoutParams;
    }

    private void rebuildFilterChips() {
        LinearLayout linearLayout = this.chipRow;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        LinearLayout filterChipLine = filterChipLine();
        addFilterChip(filterChipLine, "All", FILTER_ALL);
        addFilterChip(filterChipLine, "Info", FILTER_INFO);
        addFilterChip(filterChipLine, "Warning", FILTER_WARN);
        addFilterChip(filterChipLine, "Error", FILTER_ERROR);
        this.chipRow.addView(filterChipLine, new LinearLayout.LayoutParams(-1, dp(32)));
        LinearLayout filterChipLine2 = filterChipLine();
        addFilterChip(filterChipLine2, "Crash", FILTER_CRASH);
        addFilterChip(filterChipLine2, "WebView", FILTER_WEBVIEW);
        addFilterChip(filterChipLine2, "Network", FILTER_NETWORK);
        boolean z = this.groupRepeats;
        Button chipButton = chipButton(z ? "Group ✓" : "Group", z);
        chipButton.setContentDescription(this.groupRepeats ? "Grouping repeated events on" : "Grouping repeated events off");
        chipButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m32lambda$rebuildFilterChips$7$comharleytgforumdevLogsActivity(view);
            }
        });
        filterChipLine2.addView(chipButton, gridChipParams(filterChipLine2.getChildCount() > 0));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(32));
        layoutParams.topMargin = dp(6);
        this.chipRow.addView(filterChipLine2, layoutParams);
    }

    /* renamed from: lambda$rebuildFilterChips$7$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m32lambda$rebuildFilterChips$7$comharleytgforumdevLogsActivity(View view) {
        this.groupRepeats = !this.groupRepeats;
        rebuildFilterChips();
        renderLogs();
    }

    private LinearLayout filterChipLine() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        return linearLayout;
    }

    private void addFilterChip(LinearLayout linearLayout, String str, final String str2) {
        Button chipButton = chipButton(str, str2.equals(this.activeFilter));
        chipButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m21lambda$addFilterChip$8$comharleytgforumdevLogsActivity(str2, view);
            }
        });
        linearLayout.addView(chipButton, gridChipParams(linearLayout.getChildCount() > 0));
    }

    /* renamed from: lambda$addFilterChip$8$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m21lambda$addFilterChip$8$comharleytgforumdevLogsActivity(String str, View view) {
        this.activeFilter = str;
        rebuildFilterChips();
        renderLogs();
    }

    private Button chipButton(String str, boolean z) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(str);
        button.setTextSize(10.2f);
        button.setSingleLine(true);
        button.setGravity(17);
        button.setTypeface(null, z ? 1 : 0);
        button.setTextColor(getColor(z ? R.color.hcf_accent_text : R.color.hcf_muted));
        button.setBackgroundResource(z ? R.drawable.status_chip_background : R.drawable.quick_action_background);
        button.setPadding(dp(5), 0, dp(5), 0);
        return button;
    }

    private LinearLayout.LayoutParams gridChipParams(boolean z) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -1, 1.0f);
        if (z) {
            layoutParams.leftMargin = dp(6);
        }
        return layoutParams;
    }

    private void switchMode(String str) {
        View.OnClickListener onClickListener;
        String str2 = MODE_DIAGNOSTICS;
        if (!MODE_DIAGNOSTICS.equals(str)) {
            str2 = MODE_LOGS;
        }
        this.currentMode = str2;
        updateTabStyles();
        LinearLayout linearLayout = this.logsControls;
        if (linearLayout != null) {
            linearLayout.setVisibility(MODE_LOGS.equals(this.currentMode) ? 0 : 8);
        }
        Button button = this.clearButton;
        if (button != null) {
            button.setText(MODE_LOGS.equals(this.currentMode) ? "Clear" : "Logs");
            Button button2 = this.clearButton;
            if (MODE_LOGS.equals(this.currentMode)) {
                onClickListener = new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda1
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        LogsActivity.this.m36lambda$switchMode$9$comharleytgforumdevLogsActivity(view);
                    }
                };
            } else {
                onClickListener = new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda2
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        LogsActivity.this.m35lambda$switchMode$10$comharleytgforumdevLogsActivity(view);
                    }
                };
            }
            button2.setOnClickListener(onClickListener);
        }
        renderCurrentMode();
    }

    /* renamed from: lambda$switchMode$9$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m36lambda$switchMode$9$comharleytgforumdevLogsActivity(View view) {
        confirmClearLogs();
    }

    /* renamed from: lambda$switchMode$10$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m35lambda$switchMode$10$comharleytgforumdevLogsActivity(View view) {
        switchMode(MODE_LOGS);
    }

    private void updateTabStyles() {
        if (this.logsTab == null || this.diagnosticsTab == null) {
            return;
        }
        boolean equals = MODE_LOGS.equals(this.currentMode);
        styleSegment(this.logsTab, equals);
        styleSegment(this.diagnosticsTab, !equals);
    }

    private void styleSegment(Button button, boolean z) {
        button.setBackgroundResource(z ? R.drawable.status_chip_background : R.drawable.quick_action_background);
        button.setTextColor(getColor(z ? R.color.hcf_accent_text : R.color.hcf_muted));
        button.setTypeface(null, z ? 1 : 0);
    }

    private void refreshData() {
        this.rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
        updateStatusLine();
        renderCurrentMode();
    }

    private void renderCurrentMode() {
        if (MODE_DIAGNOSTICS.equals(this.currentMode)) {
            renderDiagnostics();
        } else {
            renderLogs();
        }
    }

    public void renderLogs() {
        if (contentText == null || !MODE_LOGS.equals(currentMode)) return;
        List<LogEntry> entries = parseLogs(rawLogs);
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.US);
        List<LogEntry> visible = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (!matchesFilter(entry)) continue;
            if (!query.isEmpty() && !entry.searchable().contains(query)) continue;
            visible.add(entry);
        }

        if (groupRepeats) visible = groupEntries(visible);
        Collections.reverse(visible);
        SpannableStringBuilder styled = new SpannableStringBuilder();
        StringBuilder plain = new StringBuilder();
        int errors = 0;
        int warnings = 0;
        for (LogEntry entry : visible) {
            if ("ERROR".equals(entry.level) || "CRASH".equals(entry.level)) errors += entry.count;
            if ("WARN".equals(entry.level)) warnings += entry.count;
            appendStyledEntry(styled, plain, entry);
        }
        if (visible.isEmpty()) {
            String empty = query.isEmpty() && FILTER_ALL.equals(activeFilter)
                    ? "No app logs yet."
                    : "No log entries match the current filters.";
            styled.append(empty);
            plain.append(empty);
        }
        visiblePlainText = plain.toString();
        contentText.setText(styled);
        viewerTitle.setText("App Logs");
        if (viewerSubtitle != null) viewerSubtitle.setText("Local troubleshooting history from this app");
        viewerMeta.setText(visible.size() + " shown" + (errors > 0 ? " • " + errors + " errors" : warnings > 0 ? " • " + warnings + " warnings" : ""));
        if (contentScroll != null) contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_UP));
    }

    /* renamed from: lambda$renderLogs$11$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m34lambda$renderLogs$11$comharleytgforumdevLogsActivity() {
        this.contentScroll.fullScroll(33);
    }

    private void renderDiagnostics() {
        if (this.contentText == null) {
            return;
        }
        String buildDiagnosticReport = buildDiagnosticReport();
        this.visiblePlainText = buildDiagnosticReport;
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(buildDiagnosticReport);
        colorDiagnosticLabels(spannableStringBuilder);
        this.contentText.setText(spannableStringBuilder);
        this.viewerTitle.setText("Diagnostics");
        TextView textView = this.viewerSubtitle;
        if (textView != null) {
            textView.setText("Sanitized device and app information");
        }
        this.viewerMeta.setText("No cookies, tokens or passwords");
        ScrollView scrollView = this.contentScroll;
        if (scrollView != null) {
            scrollView.post(new Runnable() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda15
                @Override // java.lang.Runnable
                public final void run() {
                    LogsActivity.this.m33lambda$renderDiagnostics$12$comharleytgforumdevLogsActivity();
                }
            });
        }
    }

    /* renamed from: lambda$renderDiagnostics$12$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m33lambda$renderDiagnostics$12$comharleytgforumdevLogsActivity() {
        this.contentScroll.fullScroll(33);
    }

    private List<LogEntry> parseLogs(String str) {
        ArrayList arrayList = new ArrayList();
        if (str != null && !str.trim().isEmpty() && !"No app logs yet.".equals(str.trim())) {
            String[] split = str.split("\\n");
            int length = split.length;
            for (int i = 0; i < length; i++) {
                String str2 = split[i];
                String trim = str2 == null ? "" : str2.trim();
                if (!trim.isEmpty()) {
                    Matcher matcher = LOG_PATTERN.matcher(trim);
                    if (matcher.matches()) {
                        arrayList.add(new LogEntry(matcher.group(1), matcher.group(2), safe(matcher.group(3)).trim(), safe(matcher.group(4)).trim()));
                    } else if (!trim.startsWith("Older log entries omitted")) {
                        arrayList.add(new LogEntry("", FILTER_INFO, "log_message", trim));
                    }
                }
            }
        }
        return arrayList;
    }

    private List<LogEntry> groupEntries(List<LogEntry> list) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        for (LogEntry logEntry : list) {
            String str = logEntry.level + "\n" + logEntry.event + "\n" + logEntry.detail;
            LogEntry logEntry2 = (LogEntry) linkedHashMap.get(str);
            if (logEntry2 == null) {
                linkedHashMap.put(str, logEntry.copy());
            } else {
                logEntry2.count += logEntry.count;
                logEntry2.lastTimestamp = logEntry.timestamp;
                linkedHashMap.remove(str);
                linkedHashMap.put(str, logEntry2);
            }
        }
        return new ArrayList(linkedHashMap.values());
    }

    private boolean matchesFilter(LogEntry logEntry) {
        if (logEntry == null) {
            return false;
        }
        if (FILTER_ALL.equals(this.activeFilter)) {
            return true;
        }
        if (FILTER_INFO.equals(this.activeFilter) || FILTER_WARN.equals(this.activeFilter) || FILTER_ERROR.equals(this.activeFilter) || FILTER_CRASH.equals(this.activeFilter)) {
            return this.activeFilter.equals(logEntry.level);
        }
        String lowerCase = (logEntry.event + " " + logEntry.detail).toLowerCase(Locale.US);
        if (FILTER_WEBVIEW.equals(this.activeFilter)) {
            return lowerCase.contains("webview") || lowerCase.contains("web_bridge") || lowerCase.contains("page_finished") || lowerCase.contains("renderer") || lowerCase.contains("javascript");
        }
        if (FILTER_NETWORK.equals(this.activeFilter)) {
            return lowerCase.contains("network") || lowerCase.contains("http") || lowerCase.contains("https") || lowerCase.contains("host") || lowerCase.contains("failover") || lowerCase.contains("ssl") || lowerCase.contains("download") || lowerCase.contains("online") || lowerCase.contains("offline");
        }
        return true;
    }

    private void appendStyledEntry(SpannableStringBuilder spannableStringBuilder, StringBuilder sb, LogEntry logEntry) {
        if (spannableStringBuilder.length() > 0) {
            spannableStringBuilder.append('\n');
            sb.append('\n');
        }
        String compactTimestamp = compactTimestamp(logEntry.timestamp);
        int length = spannableStringBuilder.length();
        spannableStringBuilder.append((CharSequence) compactTimestamp);
        spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), length, spannableStringBuilder.length(), 33);
        sb.append(compactTimestamp);
        spannableStringBuilder.append((CharSequence) "  ");
        sb.append("  ");
        String str = "[" + logEntry.level + "]";
        int length2 = spannableStringBuilder.length();
        spannableStringBuilder.append((CharSequence) str);
        spannableStringBuilder.setSpan(new ForegroundColorSpan(levelColor(logEntry.level)), length2, spannableStringBuilder.length(), 33);
        sb.append(str);
        spannableStringBuilder.append((CharSequence) "  ");
        sb.append("  ");
        int length3 = spannableStringBuilder.length();
        spannableStringBuilder.append((CharSequence) logEntry.event);
        spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_text)), length3, spannableStringBuilder.length(), 33);
        sb.append(logEntry.event);
        if (logEntry.count > 1) {
            String str2 = "  ×" + logEntry.count;
            int length4 = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) str2);
            spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), length4, spannableStringBuilder.length(), 33);
            sb.append(str2);
        }
        if (!logEntry.detail.isEmpty()) {
            spannableStringBuilder.append((CharSequence) "\n  ");
            sb.append("\n  ");
            int length5 = spannableStringBuilder.length();
            spannableStringBuilder.append((CharSequence) displayDetail(logEntry.detail));
            spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_detail)), length5, spannableStringBuilder.length(), 33);
            sb.append(logEntry.detail);
        }
        if (logEntry.count <= 1 || logEntry.lastTimestamp == null || logEntry.lastTimestamp.equals(logEntry.timestamp)) {
            return;
        }
        String str3 = "\n  latest " + compactTimestamp(logEntry.lastTimestamp);
        int length6 = spannableStringBuilder.length();
        spannableStringBuilder.append((CharSequence) str3);
        spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), length6, spannableStringBuilder.length(), 33);
        sb.append(str3);
    }

    private String displayDetail(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() + 24);
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            sb.append(charAt);
            if (charAt == '/' || charAt == '?' || charAt == '&' || charAt == '=' || charAt == '-' || charAt == '_') {
                sb.append((char) 8203);
            }
        }
        return sb.toString();
    }

    private int levelColor(String str) {
        if (FILTER_CRASH.equals(str) || FILTER_ERROR.equals(str)) {
            return getColor(R.color.hcf_error);
        }
        return FILTER_WARN.equals(str) ? getColor(R.color.hcf_warning) : getColor(R.color.hcf_info);
    }

    private String compactTimestamp(String str) {
        int i;
        if (str == null || str.isEmpty()) {
            return "--:--:--";
        }
        try {
            int indexOf = str.indexOf(84);
            if (indexOf >= 0 && str.length() >= (i = indexOf + 9)) {
                return str.substring(indexOf + 1, i);
            }
        } catch (Throwable unused) {
        }
        return str.length() > 19 ? str.substring(0, 19) : str;
    }

    private void colorDiagnosticLabels(SpannableStringBuilder spannableStringBuilder) {
        String[] strArr = {"App:", "Package:", "Android:", "Device:", "Network:", "Forum host:", "Theme:", "Performance profile:", "Runtime reason:", "Notification mode:", "Notification poll:", "Live page poll:", "FCM:", "Battery Saver:", "API failures:", "Notifications:", "Live sync:", "Auto failover:", "Telemetry:", "WebView:", "Renderer recovery:", "Last count change:", "Last route:", "Privacy:"};
        String spannableStringBuilder2 = spannableStringBuilder.toString();
        for (int i = 0; i < 24; i++) {
            String str = strArr[i];
            int i2 = 0;
            while (true) {
                int indexOf = spannableStringBuilder2.indexOf(str, i2);
                if (indexOf < 0) {
                    break;
                }
                spannableStringBuilder.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), indexOf, str.length() + indexOf, 33);
                i2 = indexOf + str.length();
            }
        }
    }

    private String buildDiagnosticReport() {
        SharedPreferences sharedPreferences = getSharedPreferences("hcf_app", 0);
        String string = sharedPreferences.getString("active_host", "forum.harleytg.com");
        String str = ForumUrlRouter.isForumHost(string) ? string : "forum.harleytg.com";
        String string2 = sharedPreferences.getString("notification_last_sync_status", "Not synced yet");
        long j = sharedPreferences.getLong("notification_last_sync_latency_ms", 0L);
        String str2 = "Unknown";
        try {
            PackageInfo currentWebViewPackage = WebView.getCurrentWebViewPackage();
            if (currentWebViewPackage != null) {
                str2 = currentWebViewPackage.packageName + " • " + currentWebViewPackage.versionName;
            }
        } catch (Throwable unused) {
        }
        String notificationPermissionLabel = notificationPermissionLabel();
        String str3 = "";
        String safeUrl = AppLogger.safeUrl(sharedPreferences.getString("last_recoverable_url", ""));
        if (safeUrl.trim().isEmpty()) {
            safeUrl = "Not recorded yet";
        }
        StringBuilder sb = new StringBuilder("Harley's Clan Forum • Sanitized Diagnostic Report\n\nApp: " + BuildInfo.installedVersionName() + "\nPackage: ");
        sb.append(getPackageName());
        sb.append("\nAndroid: SDK ");
        sb.append(Build.VERSION.SDK_INT);
        sb.append("\nDevice: ");
        sb.append(safe(Build.MANUFACTURER));
        sb.append(" ");
        sb.append(safe(Build.MODEL));
        sb.append("\nNetwork: ");
        sb.append(isNetworkAvailable() ? "Online" : "Offline");
        sb.append("\nForum host: ");
        sb.append(str);
        sb.append("\nTheme: ");
        sb.append(ThemeManager.label(this));
        sb.append("\nPerformance profile: ");
        sb.append(PerformanceProfile.settingLabel(this, sharedPreferences));
        sb.append("\nRuntime reason: ");
        sb.append(RuntimeDiagnostics.profileReason());
        sb.append("\nNotification mode: ");
        sb.append(RuntimeDiagnostics.notificationMode());
        sb.append("\nNotification poll: ");
        sb.append(formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs()));
        sb.append("\nLive page poll: ");
        sb.append(formatRuntimeInterval(RuntimeDiagnostics.livePollMs()));
        sb.append("\nFCM: ");
        sb.append(RuntimeDiagnostics.fcmState());
        sb.append("\nBattery Saver: ");
        sb.append(PerformanceProfile.isBatterySaver(this) ? "On" : "Off");
        sb.append("\nAPI failures: ");
        sb.append(RuntimeDiagnostics.failures());
        sb.append("\nNotifications: ");
        sb.append(notificationPermissionLabel);
        sb.append(" • ");
        sb.append(NotificationHelper.status(this));
        sb.append("\nLive sync: ");
        sb.append(safe(string2));
        if (j > 0) {
            str3 = " • " + j + " ms";
        }
        sb.append(str3);
        sb.append("\nAuto failover: ");
        sb.append(sharedPreferences.getBoolean("auto_failover", true) ? "On" : "Off");
        sb.append("\nTelemetry: ");
        sb.append(TelemetryService.status(this));
        sb.append("\nWebView: ");
        sb.append(str2);
        sb.append("\nRenderer recovery: Enabled (HCF-WV-001) • count ");
        sb.append(sharedPreferences.getInt("renderer_recovery_count", 0));
        sb.append("\nLast count change: ");
        sb.append(formatDiagnosticAge(sharedPreferences.getLong("notification_last_count_change_at", 0L)));
        sb.append("\nLast route: ");
        sb.append(safeUrl);
        sb.append("\nPrivacy: Cookies, session tokens, passwords and email are not included.");
        return sb.toString();
    }

    private String formatRuntimeInterval(long j) {
        if (j <= 0) {
            return "idle";
        }
        if (j < 1000) {
            return j + " ms";
        }
        if (j % 1000 != 0) {
            return String.format(Locale.US, "%.2f s", Double.valueOf(j / 1000.0d));
        }
        return (j / 1000) + " s";
    }

    private String formatDiagnosticAge(long j) {
        if (j <= 0) {
            return "not recorded yet";
        }
        long max = Math.max(0L, (System.currentTimeMillis() - j) / 1000);
        if (max < 60) {
            return max + " s ago";
        }
        long j2 = max / 60;
        if (j2 < 60) {
            return j2 + " min ago";
        }
        return (j2 / 60) + " h ago";
    }

    private String notificationPermissionLabel() {
        if (Build.VERSION.SDK_INT < 33) {
            return "System permission not required";
        }
        try {
            return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0 ? "Permission granted" : "Permission denied";
        } catch (Throwable unused) {
            return "Permission status unavailable";
        }
    }

    private void updateStatusLine() {
        if (this.statusLine == null) {
            return;
        }
        String string = getSharedPreferences("hcf_app", 0).getString("active_host", "forum.harleytg.com");
        if (!ForumUrlRouter.isForumHost(string)) {
            string = "forum.harleytg.com";
        }
        String str = "forum.harleytg.com".equalsIgnoreCase(string) ? "Primary" : "Backup";
        LogCounts countLogs = countLogs(this.rawLogs);
        TextView textView = this.statusLine;
        StringBuilder sb = new StringBuilder();
        sb.append(isNetworkAvailable() ? "Online" : "Offline");
        sb.append("  •  ");
        sb.append(str);
        sb.append(" forum  •  ");
        sb.append(countLogs.total);
        sb.append(" entries  •  ");
        sb.append(countLogs.errors);
        sb.append(" errors  •  ");
        sb.append(countLogs.warnings);
        sb.append(" warnings");
        textView.setText(sb.toString());
    }

    private LogCounts countLogs(String str) {
        LogCounts logCounts = new LogCounts();
        for (LogEntry logEntry : parseLogs(str)) {
            logCounts.total++;
            if (FILTER_WARN.equals(logEntry.level)) {
                logCounts.warnings++;
            }
            if (FILTER_ERROR.equals(logEntry.level) || FILTER_CRASH.equals(logEntry.level)) {
                logCounts.errors++;
            }
        }
        return logCounts;
    }

    private boolean isNetworkAvailable() {
        Network activeNetwork;
        NetworkCapabilities networkCapabilities;
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
            if (connectivityManager == null || (activeNetwork = connectivityManager.getActiveNetwork()) == null || (networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)) == null) {
                return false;
            }
            return networkCapabilities.hasCapability(12);
        } catch (Throwable unused) {
            return false;
        }
    }

    private void copyVisible() {
        String str = this.visiblePlainText;
        String trim = str == null ? "" : str.trim();
        if (trim.isEmpty()) {
            Toast.makeText(this, "Nothing to copy.", 0).show();
            return;
        }
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
            if (clipboardManager == null) {
                throw new IllegalStateException("Clipboard unavailable");
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText(MODE_DIAGNOSTICS.equals(this.currentMode) ? "HCF diagnostic report" : "HCF app logs", trim));
            Toast.makeText(this, MODE_DIAGNOSTICS.equals(this.currentMode) ? "Diagnostic report copied." : "Visible logs copied.", 0).show();
        } catch (Throwable unused) {
            Toast.makeText(this, "Could not copy this content.", 0).show();
        }
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this).setTitle("Clear App Logs?").setMessage("This removes only the local diagnostic log files. Forum cookies, account sessions and settings are not affected.").setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Clear Logs", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                LogsActivity.this.m31lambda$confirmClearLogs$13$comharleytgforumdevLogsActivity(dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$confirmClearLogs$13$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m31lambda$confirmClearLogs$13$comharleytgforumdevLogsActivity(DialogInterface dialogInterface, int i) {
        clearLogs();
    }

    private void clearLogs() {
        try {
            AppLogger.clear(this);
            AppLogger.info(this, "logs_cleared", "manual");
            this.rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
            updateStatusLine();
            renderCurrentMode();
            Toast.makeText(this, "Local logs cleared.", 0).show();
        } catch (Throwable unused) {
            Toast.makeText(this, "Could not clear local logs.", 0).show();
        }
    }

    private void exportVisible() {
        String str;
        try {
            Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("text/plain");
            if (MODE_DIAGNOSTICS.equals(this.currentMode)) {
                str = "harleys-clan-forum-diagnostics.txt";
            } else {
                str = "harleys-clan-forum-app-log.txt";
            }
            intent.putExtra("android.intent.extra.TITLE", str);
            startActivityForResult(intent, EXPORT_TEXT);
        } catch (Throwable th) {
            Toast.makeText(this, "No compatible document provider is available.", 1).show();
            try {
                AppLogger.error(this, "logs_export_picker_failed", th.getClass().getSimpleName());
            } catch (Throwable unused) {
            }
        }
    }

     // android.app.Activity

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_TEXT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        pendingExportUri = data.getData();
        String text = visiblePlainText == null ? "" : visiblePlainText;
        try (OutputStream out = getContentResolver().openOutputStream(pendingExportUri, "w")) {
            if (out == null) throw new IllegalStateException("No output stream");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            AppLogger.info(this, MODE_DIAGNOSTICS.equals(currentMode) ? "diagnostics_exported" : "logs_exported", "document-provider");
            Toast.makeText(this, "Export complete.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            AppLogger.error(this, "logs_export_failed", t.getClass().getSimpleName());
            Toast.makeText(this, "Could not export this content.", Toast.LENGTH_LONG).show();
        } finally {
            pendingExportUri = null;
        }
    }

    private View buildRecoveryView(Throwable th) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        linearLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
        linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        if (ThemeManager.isAmoled(this)) {
            linearLayout2.setBackgroundColor(Color.rgb(3, 5, 7));
        } else {
            linearLayout2.setBackgroundResource(R.drawable.card_background);
        }
        linearLayout2.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView label = label("Logs & Diagnostics Recovery", 18.0f, R.color.hcf_accent_text, true);
        label.setGravity(17);
        linearLayout2.addView(label);
        StringBuilder sb = new StringBuilder("The diagnostics viewer recovered safely from ");
        sb.append(th == null ? "an unexpected problem" : th.getClass().getSimpleName());
        sb.append(". You can clear the local log files or return to App Settings.");
        TextView label2 = label(sb.toString(), 12.0f, R.color.hcf_text, false);
        label2.setGravity(17);
        label2.setPadding(0, dp(10), 0, dp(12));
        linearLayout2.addView(label2);
        linearLayout2.addView(actionButton("Clear Local Logs", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m27lambda$buildRecoveryView$14$comharleytgforumdevLogsActivity(view);
            }
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        Button actionButton = actionButton("Back to App Settings", new View.OnClickListener() { // from class: com.harleytg.forum.dev.LogsActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogsActivity.this.m28lambda$buildRecoveryView$15$comharleytgforumdevLogsActivity(view);
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(48));
        layoutParams.topMargin = dp(8);
        linearLayout2.addView(actionButton, layoutParams);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        return linearLayout;
    }

    /* renamed from: lambda$buildRecoveryView$14$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m27lambda$buildRecoveryView$14$comharleytgforumdevLogsActivity(View view) {
        try {
            AppLogger.clear(this);
        } catch (Throwable unused) {
        }
        Toast.makeText(this, "Local logs cleared.", 0).show();
        recreate();
    }

    /* renamed from: lambda$buildRecoveryView$15$com-harleytg-forum-dev-LogsActivity, reason: not valid java name */
    /* synthetic */ void m28lambda$buildRecoveryView$15$comharleytgforumdevLogsActivity(View view) {
        finish();
    }

    private TextView label(String str, float f, int i, boolean z) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(f);
        textView.setTextColor(getColor(i));
        textView.setIncludeFontPadding(false);
        if (z) {
            textView.setTypeface(null, 1);
        }
        return textView;
    }

    private LinearLayout.LayoutParams marginParams(int i, int i2, int i3, int i4, int i5, int i6) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, i2);
        layoutParams.setMargins(dp(i3), dp(i4), dp(i5), dp(i6));
        return layoutParams;
    }

    private LinearLayout.LayoutParams marginParams(int i, int i2, float f, int i3, int i4, int i5, int i6) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(i, i2, f);
        layoutParams.setMargins(dp(i3), dp(i4), dp(i5), dp(i6));
        return layoutParams;
    }

    private String safe(String str) {
        return str == null ? "" : str;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private static final class LogEntry {
        int count = 1;
        final String detail;
        final String event;
        String lastTimestamp;
        final String level;
        final String timestamp;

        LogEntry(String str, String str2, String str3, String str4) {
            str = str == null ? "" : str;
            this.timestamp = str;
            this.level = str2 == null ? LogsActivity.FILTER_INFO : str2;
            this.event = str3 == null ? "event" : str3;
            this.detail = str4 == null ? "" : str4;
            this.lastTimestamp = str;
        }

        LogEntry copy() {
            LogEntry logEntry = new LogEntry(this.timestamp, this.level, this.event, this.detail);
            logEntry.count = this.count;
            logEntry.lastTimestamp = this.lastTimestamp;
            return logEntry;
        }

        String searchable() {
            return (this.timestamp + " " + this.level + " " + this.event + " " + this.detail).toLowerCase(Locale.US);
        }
    }

    private static final class LogCounts {
        int errors;
        int total;
        int warnings;

        private LogCounts() {
        }
    }
}
