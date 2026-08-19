package com.harleytg.forum.dev;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local notification history, DND controls and notification diagnostics. */
public final class NotificationHistoryActivity extends ThemedActivity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setTitle("Notification Center");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        content.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Notification Center", 22, getColor(R.color.hcf_text));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text("Local history • DND • diagnostics", 12, getColor(R.color.hcf_muted));
        subtitle.setPadding(0, 0, 0, dp(14));
        content.addView(subtitle);

        addDndControls();
        addDiagnostics();
        addHistory();
        setContentView(scroll);
    }

    private void addDndControls() {
        TextView heading = text("Do Not Disturb", 16, getColor(R.color.hcf_cyan_bright));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);
        TextView state = text("Status: " + AppSettings.dndLabel(this), 12, getColor(R.color.hcf_text));
        state.setPadding(0, dp(4), 0, dp(8));
        content.addView(state);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button off = button("Off");
        Button on = button("On");
        Button scheduled = button("Scheduled");
        off.setOnClickListener(v -> { AppSettings.setDndMode(this, AppSettings.DND_OFF); render(); });
        on.setOnClickListener(v -> { AppSettings.setDndMode(this, AppSettings.DND_ON); render(); });
        scheduled.setOnClickListener(v -> { AppSettings.setDndMode(this, AppSettings.DND_SCHEDULED); render(); });
        row.addView(off, weight()); row.addView(on, weight()); row.addView(scheduled, weight());
        content.addView(row);

        LinearLayout scheduleRow = new LinearLayout(this);
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        Button start = button("Start " + AppSettings.timeLabel(AppSettings.dndStartMinute(this)));
        Button end = button("End " + AppSettings.timeLabel(AppSettings.dndEndMinute(this)));
        start.setOnClickListener(v -> pickTime(true));
        end.setOnClickListener(v -> pickTime(false));
        scheduleRow.addView(start, weight()); scheduleRow.addView(end, weight());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6); sp.bottomMargin = dp(16);
        content.addView(scheduleRow, sp);
    }

    private void addDiagnostics() {
        TextView heading = text("Diagnostics", 16, getColor(R.color.hcf_cyan_bright));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);
        TextView diagnostics = text(NotificationCenter.diagnostics(this), 11, getColor(R.color.hcf_text));
        diagnostics.setPadding(0, dp(5), 0, dp(8));
        content.addView(diagnostics);
        Button settings = button("Open Android notification settings");
        settings.setOnClickListener(v -> NotificationCenter.openAndroidSettings(this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(16);
        content.addView(settings, p);
    }

    private void addHistory() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("History", 16, getColor(R.color.hcf_cyan_bright));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clearRead = button("Clear read");
        Button clearAll = button("Clear history");
        clearRead.setOnClickListener(v -> { NotificationCenter.clearReadHistory(this); render(); });
        clearAll.setOnClickListener(v -> { NotificationCenter.clearHistory(this); render(); });
        actions.addView(clearRead, weight()); actions.addView(clearAll, weight());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(5); ap.bottomMargin = dp(10);
        content.addView(actions, ap);

        List<NotificationCenter.Entry> entries = NotificationCenter.history(this);
        if (entries.isEmpty()) {
            TextView empty = text("No local notification history yet.", 12, getColor(R.color.hcf_muted));
            empty.setPadding(0, dp(12), 0, dp(12));
            content.addView(empty);
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
        for (NotificationCenter.Entry entry : entries) {
            Button item = button((entry.opened ? "" : "• ") + entry.title + "\n" + entry.message
                    + "\n" + format.format(new Date(entry.timestamp)));
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setAllCaps(false);
            item.setOnClickListener(v -> openEntry(entry));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.bottomMargin = dp(7);
            content.addView(item, p);
        }
    }

    private void openEntry(NotificationCenter.Entry entry) {
        NotificationCenter.markHistoryOpened(this, entry.id);
        try {
            Uri uri = Uri.parse(entry.url == null ? "" : entry.url);
            if (!LinkRouter.isInternal(uri)) {
                Toast.makeText(this, "This history item does not contain a trusted forum link.", Toast.LENGTH_SHORT).show();
                render();
                return;
            }
            Intent open = new Intent(this, MainActivity.class)
                    .setAction("com.harleytg.forum.dev.OPEN_NOTIFICATION")
                    .setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (entry.conversationId != null && !entry.conversationId.isEmpty()) {
                String draft = AppSettings.replyDraft(this, entry.conversationId);
                if (draft != null && !draft.isEmpty()) open.putExtra("hcf_reply_draft", draft);
            }
            startActivity(open);
        } catch (Throwable error) {
            Toast.makeText(this, "Could not open this notification.", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickTime(boolean start) {
        int minute = start ? AppSettings.dndStartMinute(this) : AppSettings.dndEndMinute(this);
        new TimePickerDialog(this, (view, hour, min) -> {
            int chosen = hour * 60 + min;
            int oldStart = AppSettings.dndStartMinute(this);
            int oldEnd = AppSettings.dndEndMinute(this);
            AppSettings.setDndSchedule(this, start ? chosen : oldStart, start ? oldEnd : chosen);
            AppSettings.setDndMode(this, AppSettings.DND_SCHEDULED);
            render();
        }, minute / 60, minute % 60, false).show();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(label); button.setAllCaps(false);
        button.setTextColor(getColor(R.color.hcf_text));
        button.setBackgroundResource(R.drawable.button_background);
        return button;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(2); p.rightMargin = dp(2);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
