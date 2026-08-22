package com.harleytg.forum.dev;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** First-run welcome gate shown before the optional App Setup Center. */
public final class WelcomeActivity extends ThemedActivity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.apply(this);
        prefs = getSharedPreferences(AppPrefs.FILE, 0);

        int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(buildUi());

        boolean automatic = getIntent() != null
                && getIntent().getBooleanExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, false);
        AppLogger.info(this, "app_welcome_open", "v" + SetupCenter.CURRENT_WELCOME_VERSION
                + (automatic ? " | first-run" : " | manual"));
    }

    @Override
    public void onBackPressed() {
        continueWithoutSetup("back");
    }

    private View buildUi() {
        int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(18), dp(28), dp(18), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -1));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackgroundResource(R.drawable.settings_hero_background);
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.topMargin = dp(12);
        page.addView(hero, heroLp);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Harley's Clan Forum logo");
        hero.addView(logo, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView eyebrow = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setTypeface(null, 1);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-1, -2);
        eyebrowLp.topMargin = dp(10);
        hero.addView(eyebrow, eyebrowLp);

        TextView title = text("Welcome to Harley's Clan Forum", 23, getColor(R.color.hcf_text));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(4);
        hero.addView(title, titleLp);

        if (isDevelopmentBuild()) {
            TextView badge = text("Development Build / Beta", 10, getColor(R.color.hcf_on_accent));
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(null, 1);
            badge.setBackgroundResource(R.drawable.dev_badge_background);
            badge.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
            badgeLp.topMargin = dp(10);
            hero.addView(badge, badgeLp);
        }

        TextView welcome = text(
                "Thanks for using the Harley's Forum app. App Setup can help get Android features ready for this device, but it is completely optional.",
                13, getColor(R.color.hcf_muted));
        welcome.setGravity(Gravity.CENTER);
        welcome.setLineSpacing(0.0f, 1.14f);
        LinearLayout.LayoutParams welcomeLp = new LinearLayout.LayoutParams(-1, -2);
        welcomeLp.topMargin = dp(16);
        hero.addView(welcome, welcomeLp);

        LinearLayout setupInfo = new LinearLayout(this);
        setupInfo.setOrientation(LinearLayout.VERTICAL);
        setupInfo.setBackgroundResource(R.drawable.quick_action_background);
        setupInfo.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(16);
        page.addView(setupInfo, infoLp);

        TextView setupTitle = text("What App Setup helps with", 14, getColor(R.color.hcf_text));
        setupTitle.setTypeface(null, 1);
        setupInfo.addView(setupTitle);

        TextView setupBody = text(
                "• Forum notifications\n• Opening supported forum links in the app\n• Secure app update permission\n• Background alert health",
                12, getColor(R.color.hcf_muted));
        setupBody.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = dp(8);
        setupInfo.addView(setupBody, bodyLp);

        Button startSetup = primaryButton("Start App Setup");
        startSetup.setOnClickListener(v -> startAppSetup());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(52));
        startLp.topMargin = dp(18);
        page.addView(startSetup, startLp);

        Button continueButton = secondaryButton("Continue Without App Setup");
        continueButton.setOnClickListener(v -> continueWithoutSetup("button"));
        LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(-1, dp(50));
        continueLp.topMargin = dp(9);
        page.addView(continueButton, continueLp);

        TextView note = text(
                "🐾 No worries — you can always open App Setup later from the app drawer → App Setup.",
                11, getColor(R.color.hcf_hint));
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0.0f, 1.1f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(14);
        page.addView(note, noteLp);

        TextView footer = text(BuildInfo.VERSION_BUILD_LINE, 9, getColor(R.color.hcf_hint));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(20);
        page.addView(footer, footerLp);

        return scroll;
    }

    private void startAppSetup() {
        SetupCenter.markWelcomeSeen(this);
        AppLogger.info(this, "app_welcome", "start_setup_v" + SetupCenter.CURRENT_WELCOME_VERSION);
        Intent intent = new Intent(this, SetupActivity.class);
        intent.putExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, true);
        startActivity(intent);
        finish();
    }

    private void continueWithoutSetup(String source) {
        SetupCenter.markWelcomeSeen(this);

        // Only create a skipped Setup Center state on a truly fresh install.
        // Existing beta users who already completed setup keep that completion.
        if (!prefs.getBoolean(AppPrefs.SETUP_SEEN, false)) {
            SetupCenter.markSkipped(this);
        }

        AppLogger.info(this, "app_welcome", "continue_without_setup_" + source
                + "_v" + SetupCenter.CURRENT_WELCOME_VERSION);
        finish();
    }

    private boolean isDevelopmentBuild() {
        return "dev".equalsIgnoreCase(BuildInfo.DEFAULT_UPDATE_CHANNEL)
                || "Dev".equalsIgnoreCase(BuildInfo.CHANNEL);
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setTextColor(getColor(R.color.hcf_on_accent));
        button.setBackgroundResource(R.drawable.error_primary_button_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextSize(12.0f);
        button.setTextColor(getColor(R.color.hcf_cyan_bright));
        button.setBackgroundResource(R.drawable.error_secondary_button_background);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
