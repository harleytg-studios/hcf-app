package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Adds the existing Safe Mode recovery screen to Settings -> Developer Tools. */
public final class HcfSafeModeSettings {
    private static final String INJECTED_TAG = "hcf_safe_mode_developer_tools";
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>();

    private HcfSafeModeSettings() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;

            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {
                            if (activity instanceof HcfSubActivities.SettingsActivity) attach(activity);
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            if (activity instanceof HcfSubActivities.SettingsActivity) {
                                attach(activity);
                                View root = activity.getWindow().getDecorView();
                                if (root != null) root.post(() -> inject(activity, root));
                            }
                        }

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            detach(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void attach(final Activity activity) {
        synchronized (LISTENERS) {
            if (LISTENERS.containsKey(activity)) return;
            final View root = activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    inject(activity, root);
                }
            };
            LISTENERS.put(activity, listener);
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            root.post(() -> inject(activity, root));
        }
    }

    private static void detach(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (LISTENERS) {
            listener = LISTENERS.remove(activity);
        }
        if (listener == null) return;
        try {
            View root = activity.getWindow().getDecorView();
            ViewTreeObserver observer = root == null ? null : root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }

    private static void inject(final Activity activity, View root) {
        if (activity == null || activity.isFinishing() || root == null) return;
        View playgroundButton = findButton(root, "Open UI Playground");
        if (!(playgroundButton != null && playgroundButton.getParent() instanceof LinearLayout)) return;

        LinearLayout card = (LinearLayout) playgroundButton.getParent();
        for (int i = 0; i < card.getChildCount(); i++) {
            if (INJECTED_TAG.equals(card.getChildAt(i).getTag())) return;
        }

        int playgroundIndex = card.indexOfChild(playgroundButton);
        int insertionIndex = playgroundIndex;
        for (int i = playgroundIndex - 1; i >= 0; i--) {
            View candidate = card.getChildAt(i);
            if (containsText(candidate, "UI Playground")) {
                insertionIndex = i;
                break;
            }
        }

        LinearLayout section = buildSafeModeSection(activity);
        section.setTag(INJECTED_TAG);
        card.addView(section, insertionIndex);
        AppLogger.info(activity, "safe_mode_settings_added", "developer_tools");
    }

    private static LinearLayout buildSafeModeSection(final Activity activity) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 4), dp(activity, 14), dp(activity, 4), dp(activity, 5));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.fa_shield);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.hcf_cyan_bright)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20));
        iconLp.rightMargin = dp(activity, 10);
        header.addView(icon, iconLp);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Safe Mode & Recovery");
        title.setTextColor(activity.getColor(R.color.hcf_text));
        title.setTextSize(12);
        title.setTypeface(null, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        labels.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Open crash recovery, start HCF in Safe Mode, inspect crash details, and use recovery tools.");
        subtitle.setTextColor(activity.getColor(R.color.hcf_muted));
        subtitle.setTextSize(10);
        subtitle.setLineSpacing(0.0f, 1.08f);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        section.addView(header);

        Button open = new Button(activity);
        UiButtons.normalizeText(open);
        open.setText("Open Safe Mode & Recovery");
        open.setTextColor(activity.getColor(R.color.hcf_accent_text));
        open.setBackgroundResource(R.drawable.quick_action_background);
        open.setAllCaps(false);
        open.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        open.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        FaIcons.applyStart(open, R.drawable.fa_shield);
        open.setOnClickListener(v -> {
            AppLogger.info(activity, "safe_mode_tools_open", "developer_tools");
            activity.startActivity(new Intent(activity, HcfSafeMode.SafeModeActivity.class));
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52));
        buttonLp.topMargin = dp(activity, 7);
        section.addView(open, buttonLp);

        return section;
    }

    private static View findButton(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findButton(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(text)) return true;
        }
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
