package com.harleytg.forum.dev;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared full-screen Harley's Clan Forum loading surface. */
public final class HcfBrandedLoader {
    public enum Mode { STARTUP, CONNECT, RECONNECT, LOADING }

    private final Context context;
    private final FrameLayout overlay;
    private final ImageView logo;
    private final TextView welcomeSubtitle;
    private final TextView title;
    private final TextView status;
    private final TextView detail;
    private final TextView step;
    private final TextView percent;
    private final TextView ticker;
    private final TextView build;
    private final ProgressBar spinner;
    private final ProgressBar progress;
    private final LinearLayout startupProgressGroup;
    private final Button retry;
    private FrameLayout parent;
    private ObjectAnimator logoPulse;
    private ValueAnimator progressAnimator;
    private Mode mode = Mode.LOADING;

    public HcfBrandedLoader(Context context) {
        this.context = context;
        int background = ThemeManager.isAmoled(context) ? Color.BLACK : color(R.color.hcf_bg);
        int cyan = color(R.color.hcf_cyan_bright);

        overlay = new FrameLayout(context);
        overlay.setBackgroundColor(background);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setVisibility(View.GONE);
        overlay.setAlpha(1.0f);
        overlay.setElevation(dp(48));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        overlay.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        logo = new ImageView(context);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum");
        content.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView brand = label("HARLEY'S STUDIOS", 11.0f, cyan, true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams brandLp = wrap();
        brandLp.topMargin = dp(12);
        content.addView(brand, brandLp);

        welcomeSubtitle = label("", 11.0f, color(R.color.hcf_meta), false);
        welcomeSubtitle.setGravity(Gravity.CENTER);
        welcomeSubtitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams welcomeLp = matchWrap();
        welcomeLp.topMargin = dp(7);
        content.addView(welcomeSubtitle, welcomeLp);

        title = label(defaultTitle(Mode.LOADING), 21.0f, color(R.color.hcf_text), true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(18);
        content.addView(title, titleLp);

        status = label("Preparing forum…", 13.0f, cyan, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(8);
        content.addView(status, statusLp);

        detail = label("", 11.0f, color(R.color.hcf_muted), false);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(0.0f, 1.12f);
        LinearLayout.LayoutParams detailLp = matchWrap();
        detailLp.topMargin = dp(6);
        content.addView(detail, detailLp);

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(cyan));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        spinnerLp.topMargin = dp(22);
        content.addView(spinner, spinnerLp);

        startupProgressGroup = new LinearLayout(context);
        startupProgressGroup.setOrientation(LinearLayout.VERTICAL);
        startupProgressGroup.setVisibility(View.GONE);
        LinearLayout.LayoutParams startupLp = matchWrap();
        startupLp.topMargin = dp(20);
        content.addView(startupProgressGroup, startupLp);

        LinearLayout stepRow = new LinearLayout(context);
        stepRow.setOrientation(LinearLayout.HORIZONTAL);
        stepRow.setGravity(Gravity.CENTER_VERTICAL);
        startupProgressGroup.addView(stepRow, matchWrap());

        step = label("Step 0 of 0", 11.0f, color(R.color.hcf_meta), true);
        stepRow.addView(step, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        percent = label("0%", 11.0f, cyan, true);
        percent.setGravity(Gravity.END);
        stepRow.addView(percent, wrap());

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(cyan));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.hcf_app_bar)));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(8);
        startupProgressGroup.addView(progress, progressLp);

        ticker = label("", 10.0f, color(R.color.hcf_muted), false);
        ticker.setLineSpacing(0.0f, 1.15f);
        ticker.setVisibility(View.GONE);
        LinearLayout.LayoutParams tickerLp = matchWrap();
        tickerLp.topMargin = dp(11);
        startupProgressGroup.addView(ticker, tickerLp);

        retry = new Button(context);
        UiButtons.normalizeText(retry);
        retry.setText("Retry");
        retry.setAllCaps(false);
        retry.setTextColor(cyan);
        retry.setTypeface(null, Typeface.BOLD);
        retry.setBackgroundResource(R.drawable.button_background);
        retry.setVisibility(View.GONE);
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        retryLp.topMargin = dp(22);
        content.addView(retry, retryLp);

        build = label(BuildInfo.VERSION_BUILD_LINE, 9.0f, color(R.color.hcf_hint), false);
        build.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildLp = matchWrap();
        buildLp.topMargin = dp(18);
        content.addView(build, buildLp);
    }

    public void attach(FrameLayout parent) {
        if (parent == null) return;
        if (this.parent == parent && overlay.getParent() == parent) return;
        detach();
        this.parent = parent;
        parent.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void detach() {
        stopAnimations();
        ViewGroup currentParent = (overlay.getParent() instanceof ViewGroup)
                ? (ViewGroup) overlay.getParent() : null;
        if (currentParent != null) currentParent.removeView(overlay);
        parent = null;
    }

    public boolean isVisible() {
        return overlay.getParent() != null && overlay.getVisibility() == View.VISIBLE;
    }

    public void show(Mode mode, String titleText, String detailText) {
        this.mode = mode == null ? Mode.LOADING : mode;
        setTitle(isBlank(titleText) ? defaultTitle(this.mode) : titleText);
        setStatus(defaultStatus(this.mode));
        setDetail(detailText);
        setRetryVisible(false);
        boolean startup = this.mode == Mode.STARTUP;
        startupProgressGroup.setVisibility(startup ? View.VISIBLE : View.GONE);
        spinner.setVisibility(startup ? View.GONE : View.VISIBLE);
        overlay.animate().cancel();
        overlay.setAlpha(1.0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        startLogoPulse();
    }

    public void showConnecting(String host) {
        show(Mode.CONNECT, defaultTitle(Mode.CONNECT), host);
    }

    public void showReconnecting(String host) {
        show(Mode.RECONNECT, defaultTitle(Mode.RECONNECT), host);
    }

    public void showLoading(String message) {
        show(Mode.LOADING, defaultTitle(Mode.LOADING), message);
    }

    public void showStartup(String titleText, String statusText, String detailText) {
        show(Mode.STARTUP, titleText, detailText);
        setStatus(statusText);
    }

    public void hide() {
        hide(false);
    }

    public void hide(boolean animate) {
        if (overlay.getVisibility() != View.VISIBLE) {
            stopAnimations();
            overlay.setVisibility(View.GONE);
            overlay.setAlpha(1.0f);
            return;
        }
        if (!animate) {
            stopAnimations();
            overlay.animate().cancel();
            overlay.setAlpha(1.0f);
            overlay.setVisibility(View.GONE);
            return;
        }
        stopLogoPulse();
        overlay.animate().cancel();
        overlay.animate().alpha(0.0f).setDuration(180L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        overlay.setVisibility(View.GONE);
                        overlay.setAlpha(1.0f);
                        overlay.animate().setListener(null);
                    }
                }).start();
    }

    public void setTitle(String value) {
        title.setText(safe(value));
    }

    public void setStatus(String value) {
        String text = safe(value);
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setDetail(String value) {
        String text = safe(value);
        detail.setText(text);
        detail.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setWelcomeSubtitle(String value) {
        String text = safe(value);
        welcomeSubtitle.setText(text);
        welcomeSubtitle.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setProgress(int thousandths) {
        int value = clampProgress(thousandths);
        if (progressAnimator != null) progressAnimator.cancel();
        progress.setProgress(value);
        percent.setText(Math.round(value / 10.0f) + "%");
    }

    public void animateProgressTo(int thousandths, long durationMs) {
        int target = clampProgress(thousandths);
        if (progressAnimator != null) progressAnimator.cancel();
        progressAnimator = ValueAnimator.ofInt(progress.getProgress(), target);
        progressAnimator.setDuration(Math.max(0L, durationMs));
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            int value = (Integer) animation.getAnimatedValue();
            progress.setProgress(value);
            percent.setText(Math.round(value / 10.0f) + "%");
        });
        progressAnimator.start();
    }

    public void setStep(int stepValue, int total) {
        int safeTotal = Math.max(0, total);
        int safeStep = safeTotal == 0 ? Math.max(0, stepValue)
                : Math.min(Math.max(0, stepValue), safeTotal);
        step.setText("Step " + safeStep + " of " + safeTotal);
    }

    public void setTicker(String value) {
        String text = safe(value);
        ticker.setText(text);
        ticker.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void setRetryVisible(boolean show) {
        retry.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void setRetryListener(View.OnClickListener listener) {
        retry.setOnClickListener(listener);
    }

    public void setRetryLabel(String label) {
        retry.setText(isBlank(label) ? "Retry" : label);
    }

    private void startLogoPulse() {
        stopLogoPulse();
        logoPulse = ObjectAnimator.ofFloat(logo, View.ALPHA, 1.0f, 0.72f, 1.0f);
        logoPulse.setDuration(1700L);
        logoPulse.setRepeatCount(ValueAnimator.INFINITE);
        logoPulse.setRepeatMode(ValueAnimator.RESTART);
        logoPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        logoPulse.start();
    }

    private void stopLogoPulse() {
        if (logoPulse != null) {
            logoPulse.cancel();
            logoPulse = null;
        }
        logo.setAlpha(1.0f);
    }

    private void stopAnimations() {
        stopLogoPulse();
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        overlay.animate().cancel();
    }

    private String defaultTitle(Mode value) {
        if (value == Mode.STARTUP) return "Starting Harley's Clan Forum";
        if (value == Mode.CONNECT) return "Connecting to Harley's Clan Forum…";
        if (value == Mode.RECONNECT) return "Reconnecting to Harley's Clan Forum…";
        return "Loading forum…";
    }

    private String defaultStatus(Mode value) {
        if (value == Mode.STARTUP) return "Preparing app…";
        if (value == Mode.CONNECT) return "Establishing secure connection…";
        if (value == Mode.RECONNECT) return "Restoring secure connection…";
        return "Preparing forum…";
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int color(int id) {
        return context.getColor(id);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int clampProgress(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
