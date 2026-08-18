package com.harleytg.forum.dev;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogsActivity extends ThemedActivity {
    private static final int EXPORT_TEXT = 611;
    private static final int MAX_DISPLAY_CHARS = 180000;
    private static final String MODE_LOGS = "logs";
    private static final String MODE_DIAGNOSTICS = "diagnostics";
    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_INFO = "INFO";
    private static final String FILTER_WARN = "WARN";
    private static final String FILTER_ERROR = "ERROR";
    private static final String FILTER_CRASH = "CRASH";
    private static final String FILTER_WEBVIEW = "WEBVIEW";
    private static final String FILTER_NETWORK = "NETWORK";
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\[(INFO|WARN|ERROR|CRASH)] ([^|]+?)(?: \\| (.*))?$");

    private TextView contentText;
    private TextView viewerTitle;
    private TextView viewerMeta;
    private TextView viewerSubtitle;
    private TextView statusLine;
    private EditText searchInput;
    private LinearLayout logsControls;
    private LinearLayout chipRow;
    private Button logsTab;
    private Button diagnosticsTab;
    private Button clearButton;
    private ScrollView contentScroll;

    private String currentMode = MODE_LOGS;
    private String activeFilter = FILTER_ALL;
    private boolean groupRepeats = true;
    private String rawLogs = "";
    private String visiblePlainText = "";
    private Uri pendingExportUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.apply(this);
            int systemChrome = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(systemChrome);
            getWindow().setNavigationBarColor(systemChrome);
            setContentView(buildView());
            AppLogger.info(this, "logs_open", "system-ui-v3");
            refreshData();
        } catch (Throwable t) {
            try { AppLogger.error(this, "logs_screen_recovery", t.getClass().getSimpleName()); }
            catch (Throwable ignored) {}
            setContentView(buildRecoveryView(t));
        }
    }


    private View buildView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        page.addView(buildHeader());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(14));

        content.addView(buildTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        content.addView(buildStatusCard(), marginParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
        content.addView(buildActionRow(), marginParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44), 0, 10, 0, 0));

        logsControls = buildLogsControls();
        content.addView(logsControls, marginParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));

        LinearLayout viewer = new LinearLayout(this);
        viewer.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) viewer.setBackgroundColor(Color.rgb(3, 5, 7));
        else viewer.setBackgroundResource(R.drawable.card_background);
        viewer.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout viewerHeader = new LinearLayout(this);
        viewerHeader.setOrientation(LinearLayout.HORIZONTAL);
        viewerHeader.setGravity(Gravity.CENTER_VERTICAL);

        viewerTitle = label("App Logs", 16, R.color.hcf_accent_text, true);
        viewerHeader.addView(viewerTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        viewerMeta = label("", 10, R.color.hcf_muted, false);
        viewerMeta.setGravity(Gravity.END);
        viewerHeader.addView(viewerMeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        viewer.addView(viewerHeader);

        viewerSubtitle = label("Local troubleshooting history from this app", 11, R.color.hcf_muted, false);
        viewerSubtitle.setPadding(0, dp(2), 0, dp(8));
        viewer.addView(viewerSubtitle);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.hcf_divider));
        viewer.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        contentScroll.setClipToPadding(false);

        contentText = new TextView(this);
        contentText.setTextColor(getColor(R.color.hcf_text));
        contentText.setTextSize(10.5f);
        contentText.setTextIsSelectable(true);
        contentText.setTypeface(Typeface.MONOSPACE);
        contentText.setIncludeFontPadding(false);
        contentText.setLineSpacing(dp(1), 1.05f);
        contentText.setPadding(0, dp(10), 0, dp(8));
        contentText.setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= 23) {
            contentText.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
            contentText.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        }
        contentScroll.addView(contentText, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        viewer.addView(contentScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(viewer, marginParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, 0, 10, 0, 0));

        page.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }


    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(5), dp(8), dp(5));
        bar.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar));
        bar.setMinimumHeight(dp(56));

        ImageButton back = UiButtons.iconButton(
                this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back to App Settings");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        logoParams.leftMargin = dp(4);
        bar.addView(logo, logoParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        bar.addView(titles, tp);

        TextView title = label("Logs & Diagnostics", 18, R.color.hcf_text, true);
        titles.addView(title);

        TextView subtitle = label(BuildInfo.VERSION_TAG + " • Local troubleshooting", 10, R.color.hcf_meta, true);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle);
        return bar;
    }


    private View buildTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);

        logsTab = segmentButton("Logs", v -> switchMode(MODE_LOGS));
        diagnosticsTab = segmentButton("Diagnostics", v -> switchMode(MODE_DIAGNOSTICS));

        tabs.addView(logsTab, weightedTabParams(false));
        tabs.addView(diagnosticsTab, weightedTabParams(true));
        updateTabStyles();
        return tabs;
    }

    private LinearLayout.LayoutParams weightedTabParams(boolean hasLeft) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (hasLeft) p.leftMargin = dp(8);
        return p;
    }


    private View buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView title = label("Device status", 16, R.color.hcf_accent_text, true);
        card.addView(title);
        TextView subtitle = label("Current app and forum health at a glance", 11, R.color.hcf_muted, false);
        subtitle.setPadding(0, dp(2), 0, dp(7));
        card.addView(subtitle);

        statusLine = label("Checking app status…", 11, R.color.hcf_text, false);
        statusLine.setLineSpacing(0, 1.12f);
        card.addView(statusLine);
        return card;
    }


    private View buildActionRow() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        actions.addView(actionButton("Refresh", v -> refreshData()), weightedActionParams(0, 0));
        actions.addView(actionButton("Copy", v -> copyVisible()), weightedActionParams(6, 0));
        actions.addView(actionButton("Export", v -> exportVisible()), weightedActionParams(6, 0));
        clearButton = actionButton("Clear", v -> confirmClearLogs());
        actions.addView(clearButton, weightedActionParams(6, 0));
        return actions;
    }


    private LinearLayout buildLogsControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(getColor(R.color.hcf_text));
        searchInput.setHintTextColor(getColor(R.color.hcf_hint));
        searchInput.setHint("Search app logs…");
        searchInput.setTextSize(13);
        searchInput.setIncludeFontPadding(false);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackgroundResource(R.drawable.quick_action_background);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        controls.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderLogs(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        TextView filterLabel = label("Filter logs", 10, R.color.hcf_cyan, true);
        filterLabel.setPadding(dp(4), dp(8), 0, dp(6));
        controls.addView(filterLabel);

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.VERTICAL);
        chipRow.setClipChildren(false);
        chipRow.setClipToPadding(false);
        controls.addView(chipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
        rebuildFilterChips();
        return controls;
    }


    private Button segmentButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label);
        b.setTextSize(12f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setOnClickListener(listener);
        return b;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label);
        b.setTextSize(11f);
        b.setTextColor(getColor(R.color.hcf_accent_text));
        b.setTypeface(null, Typeface.BOLD);
        b.setBackgroundResource(R.drawable.quick_action_background);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams weightedActionParams(int leftDp, int rightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        p.leftMargin = dp(leftDp);
        p.rightMargin = dp(rightDp);
        return p;
    }

    private void rebuildFilterChips() {
        if (chipRow == null) return;
        chipRow.removeAllViews();

        LinearLayout top = filterChipLine();
        addFilterChip(top, "All", FILTER_ALL);
        addFilterChip(top, "Info", FILTER_INFO);
        addFilterChip(top, "Warning", FILTER_WARN);
        addFilterChip(top, "Error", FILTER_ERROR);
        chipRow.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        LinearLayout bottom = filterChipLine();
        addFilterChip(bottom, "Crash", FILTER_CRASH);
        addFilterChip(bottom, "WebView", FILTER_WEBVIEW);
        addFilterChip(bottom, "Network", FILTER_NETWORK);
        Button grouped = chipButton(groupRepeats ? "Group ✓" : "Group", groupRepeats);
        grouped.setContentDescription(groupRepeats ? "Grouping repeated events on" : "Grouping repeated events off");
        grouped.setOnClickListener(v -> {
            groupRepeats = !groupRepeats;
            rebuildFilterChips();
            renderLogs();
        });
        bottom.addView(grouped, gridChipParams(bottom.getChildCount() > 0));
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        bottomParams.topMargin = dp(6);
        chipRow.addView(bottom, bottomParams);
    }

    private LinearLayout filterChipLine() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private void addFilterChip(LinearLayout row, String label, String filter) {
        boolean selected = filter.equals(activeFilter);
        Button chip = chipButton(label, selected);
        chip.setOnClickListener(v -> {
            activeFilter = filter;
            rebuildFilterChips();
            renderLogs();
        });
        row.addView(chip, gridChipParams(row.getChildCount() > 0));
    }


    private Button chipButton(String label, boolean selected) {
        Button b = new Button(this);
        UiButtons.normalizeText(b);
        b.setText(label);
        b.setTextSize(10.2f);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        b.setTextColor(getColor(selected ? R.color.hcf_accent_text : R.color.hcf_muted));
        b.setBackgroundResource(selected ? R.drawable.status_chip_background : R.drawable.quick_action_background);
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private LinearLayout.LayoutParams gridChipParams(boolean hasLeft) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (hasLeft) p.leftMargin = dp(6);
        return p;
    }

    private void switchMode(String mode) {
        currentMode = MODE_DIAGNOSTICS.equals(mode) ? MODE_DIAGNOSTICS : MODE_LOGS;
        updateTabStyles();
        if (logsControls != null) logsControls.setVisibility(MODE_LOGS.equals(currentMode) ? View.VISIBLE : View.GONE);
        if (clearButton != null) {
            clearButton.setText(MODE_LOGS.equals(currentMode) ? "Clear" : "Logs");
            clearButton.setOnClickListener(MODE_LOGS.equals(currentMode)
                    ? v -> confirmClearLogs()
                    : v -> switchMode(MODE_LOGS));
        }
        renderCurrentMode();
    }

    private void updateTabStyles() {
        if (logsTab == null || diagnosticsTab == null) return;
        boolean logs = MODE_LOGS.equals(currentMode);
        styleSegment(logsTab, logs);
        styleSegment(diagnosticsTab, !logs);
    }


    private void styleSegment(Button button, boolean selected) {
        button.setBackgroundResource(selected ? R.drawable.status_chip_background : R.drawable.quick_action_background);
        button.setTextColor(getColor(selected ? R.color.hcf_accent_text : R.color.hcf_muted));
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void refreshData() {
        rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
        updateStatusLine();
        renderCurrentMode();
    }

    private void renderCurrentMode() {
        if (MODE_DIAGNOSTICS.equals(currentMode)) renderDiagnostics();
        else renderLogs();
    }

    private void renderLogs() {
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

    private void renderDiagnostics() {
        if (contentText == null) return;
        String report = buildDiagnosticReport();
        visiblePlainText = report;
        SpannableStringBuilder styled = new SpannableStringBuilder(report);
        colorDiagnosticLabels(styled);
        contentText.setText(styled);
        viewerTitle.setText("Diagnostics");
        if (viewerSubtitle != null) viewerSubtitle.setText("Sanitized device and app information");
        viewerMeta.setText("No cookies, tokens or passwords");
        if (contentScroll != null) contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_UP));
    }

    private List<LogEntry> parseLogs(String value) {
        List<LogEntry> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty() || "No app logs yet.".equals(value.trim())) return out;
        String[] lines = value.split("\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = LOG_PATTERN.matcher(trimmed);
            if (m.matches()) {
                out.add(new LogEntry(m.group(1), m.group(2), safe(m.group(3)).trim(), safe(m.group(4)).trim()));
            } else if (!trimmed.startsWith("Older log entries omitted")) {
                out.add(new LogEntry("", "INFO", "log_message", trimmed));
            }
        }
        return out;
    }

    private List<LogEntry> groupEntries(List<LogEntry> input) {
        LinkedHashMap<String, LogEntry> grouped = new LinkedHashMap<>();
        for (LogEntry entry : input) {
            String key = entry.level + "\n" + entry.event + "\n" + entry.detail;
            LogEntry existing = grouped.get(key);
            if (existing == null) grouped.put(key, entry.copy());
            else {
                existing.count += entry.count;
                existing.lastTimestamp = entry.timestamp;
                // Reinsert so grouped entries retain the position of their newest occurrence.
                grouped.remove(key);
                grouped.put(key, existing);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    private boolean matchesFilter(LogEntry entry) {
        if (entry == null) return false;
        if (FILTER_ALL.equals(activeFilter)) return true;
        if (FILTER_INFO.equals(activeFilter) || FILTER_WARN.equals(activeFilter)
                || FILTER_ERROR.equals(activeFilter) || FILTER_CRASH.equals(activeFilter)) {
            return activeFilter.equals(entry.level);
        }
        String hay = (entry.event + " " + entry.detail).toLowerCase(Locale.US);
        if (FILTER_WEBVIEW.equals(activeFilter)) {
            return hay.contains("webview") || hay.contains("web_bridge") || hay.contains("page_finished")
                    || hay.contains("renderer") || hay.contains("javascript");
        }
        if (FILTER_NETWORK.equals(activeFilter)) {
            return hay.contains("network") || hay.contains("http") || hay.contains("https")
                    || hay.contains("host") || hay.contains("failover") || hay.contains("ssl")
                    || hay.contains("download") || hay.contains("online") || hay.contains("offline");
        }
        return true;
    }

    private void appendStyledEntry(SpannableStringBuilder styled, StringBuilder plain, LogEntry entry) {
        if (styled.length() > 0) {
            styled.append('\n');
            plain.append('\n');
        }
        String time = compactTimestamp(entry.timestamp);
        int timeStart = styled.length();
        styled.append(time);
        styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), timeStart,
                styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        plain.append(time);

        styled.append("  ");
        plain.append("  ");
        String levelText = "[" + entry.level + "]";
        int levelStart = styled.length();
        styled.append(levelText);
        styled.setSpan(new ForegroundColorSpan(levelColor(entry.level)), levelStart,
                styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        plain.append(levelText);

        styled.append("  ");
        plain.append("  ");
        int eventStart = styled.length();
        styled.append(entry.event);
        styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_text)), eventStart,
                styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        plain.append(entry.event);

        if (entry.count > 1) {
            String repeat = "  ×" + entry.count;
            int repeatStart = styled.length();
            styled.append(repeat);
            styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), repeatStart,
                    styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            plain.append(repeat);
        }
        if (!entry.detail.isEmpty()) {
            styled.append("\n  ");
            plain.append("\n  ");
            int detailStart = styled.length();
            styled.append(displayDetail(entry.detail));
            styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_detail)), detailStart,
                    styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            plain.append(entry.detail);
        }
        if (entry.count > 1 && entry.lastTimestamp != null && !entry.lastTimestamp.equals(entry.timestamp)) {
            String latest = "\n  latest " + compactTimestamp(entry.lastTimestamp);
            int latestStart = styled.length();
            styled.append(latest);
            styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_log_timestamp)), latestStart,
                    styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            plain.append(latest);
        }
    }

    private String displayDetail(String detail) {
        if (detail == null || detail.isEmpty()) return "";
        // Add invisible break opportunities after URL/path separators. This keeps long
        // routes readable on narrow phones without modifying copied/exported log text.
        StringBuilder out = new StringBuilder(detail.length() + 24);
        for (int i = 0; i < detail.length(); i++) {
            char c = detail.charAt(i);
            out.append(c);
            if (c == '/' || c == '?' || c == '&' || c == '=' || c == '-' || c == '_') {
                out.append('\u200B');
            }
        }
        return out.toString();
    }

    private int levelColor(String level) {
        if ("CRASH".equals(level) || "ERROR".equals(level)) return getColor(R.color.hcf_error);
        if ("WARN".equals(level)) return getColor(R.color.hcf_warning);
        return getColor(R.color.hcf_info);
    }

    private String compactTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "--:--:--";
        try {
            int t = timestamp.indexOf('T');
            if (t >= 0 && timestamp.length() >= t + 9) return timestamp.substring(t + 1, t + 9);
        } catch (Throwable ignored) {}
        return timestamp.length() > 19 ? timestamp.substring(0, 19) : timestamp;
    }

    private void colorDiagnosticLabels(SpannableStringBuilder styled) {
        String[] labels = {"App:", "Package:", "Android:", "Device:", "Network:", "Forum host:",
                "Theme:", "Performance profile:", "Runtime reason:", "Notification mode:", "Notification poll:",
                "Live page poll:", "FCM:", "Battery Saver:", "API failures:", "Notifications:", "Live sync:",
                "Auto failover:", "Telemetry:", "WebView:", "Renderer recovery:", "Last count change:", "Last route:", "Privacy:"};
        String value = styled.toString();
        for (String label : labels) {
            int from = 0;
            while (true) {
                int at = value.indexOf(label, from);
                if (at < 0) break;
                styled.setSpan(new ForegroundColorSpan(getColor(R.color.hcf_cyan_bright)), at,
                        at + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = at + label.length();
            }
        }
    }

    private String buildDiagnosticReport() {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        String sync = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "Not synced yet");
        long latency = prefs.getLong(AppPrefs.NOTIFICATION_LAST_SYNC_LATENCY_MS, 0L);
        String webViewProvider = "Unknown";
        try {
            android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info != null) webViewProvider = info.packageName + " • " + info.versionName;
        } catch (Throwable ignored) {}
        String notificationPermission = notificationPermissionLabel();
        String lastRoute = AppLogger.safeUrl(prefs.getString(AppPrefs.LAST_RECOVERABLE_URL, ""));
        if (lastRoute.trim().isEmpty()) lastRoute = "Not recorded yet";

        return "Harley's Clan Forum • Sanitized Diagnostic Report\n\n"
                + "App: " + BuildInfo.VERSION + " (" + BuildInfo.VERSION_CODE + ")\n"
                + "Package: " + getPackageName() + "\n"
                + "Android: SDK " + Build.VERSION.SDK_INT + "\n"
                + "Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + "\n"
                + "Network: " + (isNetworkAvailable() ? "Online" : "Offline") + "\n"
                + "Forum host: " + host + "\n"
                + "Theme: " + ThemeManager.label(this) + "\n"
                + "Performance profile: " + PerformanceProfile.settingLabel(this, prefs) + "\n"
                + "Runtime reason: " + RuntimeDiagnostics.profileReason() + "\n"
                + "Notification mode: " + RuntimeDiagnostics.notificationMode() + "\n"
                + "Notification poll: " + formatRuntimeInterval(RuntimeDiagnostics.notificationPollMs()) + "\n"
                + "Live page poll: " + formatRuntimeInterval(RuntimeDiagnostics.livePollMs()) + "\n"
                + "FCM: " + RuntimeDiagnostics.fcmState() + "\n"
                + "Battery Saver: " + (PerformanceProfile.isBatterySaver(this) ? "On" : "Off") + "\n"
                + "API failures: " + RuntimeDiagnostics.failures() + "\n"
                + "Notifications: " + notificationPermission + " • " + NotificationHelper.status(this) + "\n"
                + "Live sync: " + safe(sync) + (latency > 0L ? " • " + latency + " ms" : "") + "\n"
                + "Auto failover: " + (prefs.getBoolean(AppPrefs.AUTO_FAILOVER, true) ? "On" : "Off") + "\n"
                + "Telemetry: " + TelemetryService.status(this) + "\n"
                + "WebView: " + webViewProvider + "\n"
                + "Renderer recovery: Enabled (HCF-WV-001) • count " + prefs.getInt(AppPrefs.RENDERER_RECOVERY_COUNT, 0) + "\n"
                + "Last count change: " + formatDiagnosticAge(prefs.getLong(AppPrefs.NOTIFICATION_LAST_COUNT_CHANGE_AT, 0L)) + "\n"
                + "Last route: " + lastRoute + "\n"
                + "Privacy: Cookies, session tokens, passwords and email are not included.";
    }

    private String formatRuntimeInterval(long ms) {
        if (ms <= 0L) return "idle";
        if (ms < 1000L) return ms + " ms";
        if (ms % 1000L == 0L) return (ms / 1000L) + " s";
        return String.format(java.util.Locale.US, "%.2f s", ms / 1000.0d);
    }

    private String formatDiagnosticAge(long at) {
        if (at <= 0L) return "not recorded yet";
        long seconds = Math.max(0L, (System.currentTimeMillis() - at) / 1000L);
        if (seconds < 60L) return seconds + " s ago";
        long minutes = seconds / 60L;
        if (minutes < 60L) return minutes + " min ago";
        return (minutes / 60L) + " h ago";
    }

    private String notificationPermissionLabel() {
        if (Build.VERSION.SDK_INT < 33) return "System permission not required";
        try {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ? "Permission granted" : "Permission denied";
        } catch (Throwable ignored) {
            return "Permission status unavailable";
        }
    }

    private void updateStatusLine() {
        if (statusLine == null) return;
        SharedPreferences prefs = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE);
        String host = prefs.getString(AppPrefs.ACTIVE_HOST, ForumConfig.PRIMARY_HOST);
        if (!ForumUrlRouter.isForumHost(host)) host = ForumConfig.PRIMARY_HOST;
        String server = ForumConfig.PRIMARY_HOST.equalsIgnoreCase(host) ? "Primary" : "Backup";
        LogCounts counts = countLogs(rawLogs);
        statusLine.setText((isNetworkAvailable() ? "Online" : "Offline") + "  •  " + server + " forum  •  "
                + counts.total + " entries  •  " + counts.errors + " errors  •  " + counts.warnings + " warnings");
    }

    private LogCounts countLogs(String text) {
        LogCounts counts = new LogCounts();
        for (LogEntry entry : parseLogs(text)) {
            counts.total++;
            if ("WARN".equals(entry.level)) counts.warnings++;
            if ("ERROR".equals(entry.level) || "CRASH".equals(entry.level)) counts.errors++;
        }
        return counts;
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void copyVisible() {
        String text = visiblePlainText == null ? "" : visiblePlainText.trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            String label = MODE_DIAGNOSTICS.equals(currentMode) ? "HCF diagnostic report" : "HCF app logs";
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, MODE_DIAGNOSTICS.equals(currentMode)
                    ? "Diagnostic report copied." : "Visible logs copied.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Could not copy this content.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setTitle("Clear App Logs?")
                .setMessage("This removes only the local diagnostic log files. Forum cookies, account sessions and settings are not affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear Logs", (dialog, which) -> clearLogs())
                .show();
    }

    private void clearLogs() {
        try {
            AppLogger.clear(this);
            AppLogger.info(this, "logs_cleared", "manual");
            rawLogs = AppLogger.readRecent(this, MAX_DISPLAY_CHARS);
            updateStatusLine();
            renderCurrentMode();
            Toast.makeText(this, "Local logs cleared.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Could not clear local logs.", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportVisible() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            String file = MODE_DIAGNOSTICS.equals(currentMode)
                    ? "harleys-clan-forum-diagnostics.txt"
                    : "harleys-clan-forum-app-log.txt";
            intent.putExtra(Intent.EXTRA_TITLE, file);
            startActivityForResult(intent, EXPORT_TEXT);
        } catch (Throwable t) {
            Toast.makeText(this, "No compatible document provider is available.", Toast.LENGTH_LONG).show();
            try { AppLogger.error(this, "logs_export_picker_failed", t.getClass().getSimpleName()); }
            catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_TEXT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        pendingExportUri = data.getData();
        String exportText = visiblePlainText == null ? "" : visiblePlainText;
        try (OutputStream out = getContentResolver().openOutputStream(pendingExportUri, "w")) {
            if (out == null) throw new IllegalStateException("No output stream");
            out.write(exportText.getBytes(StandardCharsets.UTF_8));
            out.flush();
            AppLogger.info(this, MODE_DIAGNOSTICS.equals(currentMode) ? "diagnostics_exported" : "logs_exported", "document-provider");
            Toast.makeText(this, "Export complete.", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            AppLogger.error(this, "logs_export_failed", e.getClass().getSimpleName());
            Toast.makeText(this, "Could not export this content.", Toast.LENGTH_SHORT).show();
        } finally {
            pendingExportUri = null;
        }
    }


    private View buildRecoveryView(Throwable failure) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (ThemeManager.isAmoled(this)) card.setBackgroundColor(Color.rgb(3, 5, 7));
        else card.setBackgroundResource(R.drawable.card_background);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = label("Logs & Diagnostics Recovery", 18, R.color.hcf_accent_text, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView detail = label("The diagnostics viewer recovered safely from "
                + (failure == null ? "an unexpected problem" : failure.getClass().getSimpleName())
                + ". You can clear the local log files or return to App Settings.",
                12, R.color.hcf_text, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(10), 0, dp(12));
        card.addView(detail);

        Button clear = actionButton("Clear Local Logs", v -> {
            try { AppLogger.clear(this); } catch (Throwable ignored) {}
            Toast.makeText(this, "Local logs cleared.", Toast.LENGTH_SHORT).show();
            recreate();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        card.addView(clear, clearParams);

        Button close = actionButton("Back to App Settings", v -> finish());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        cp.topMargin = dp(8);
        card.addView(close, cp);

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private TextView label(String text, float size, int colorRes, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(getColor(colorRes));
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height,
                                                    int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, float weight,
                                                    int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height, weight);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LogEntry {
        final String timestamp;
        final String level;
        final String event;
        final String detail;
        String lastTimestamp;
        int count = 1;

        LogEntry(String timestamp, String level, String event, String detail) {
            this.timestamp = timestamp == null ? "" : timestamp;
            this.level = level == null ? "INFO" : level;
            this.event = event == null ? "event" : event;
            this.detail = detail == null ? "" : detail;
            this.lastTimestamp = this.timestamp;
        }

        LogEntry copy() {
            LogEntry value = new LogEntry(timestamp, level, event, detail);
            value.count = count;
            value.lastTimestamp = lastTimestamp;
            return value;
        }

        String searchable() {
            return (timestamp + " " + level + " " + event + " " + detail).toLowerCase(Locale.US);
        }
    }

    private static final class LogCounts {
        int total;
        int warnings;
        int errors;
    }
}
