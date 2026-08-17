package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class StablePromoInjector {
    private static final String PROMO_TAG = "hcf_stable_promo_v10000033";
    private static boolean installed;

    static synchronized void install(Application application) {
        if (installed || application == null) return;
        installed = true;

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof MainActivity)) return;
                View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (decor == null) return;
                decor.post(() -> attachToDrawer(activity));
            }

            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static void attachToDrawer(Activity activity) {
        try {
            View drawerView = activity.findViewById(R.id.drawerPanel);
            if (!(drawerView instanceof LinearLayout)) return;

            LinearLayout drawer = (LinearLayout) drawerView;
            if (drawer.findViewWithTag(PROMO_TAG) != null) return;

            LinearLayout promo = new LinearLayout(activity);
            promo.setTag(PROMO_TAG);
            promo.setOrientation(LinearLayout.HORIZONTAL);
            promo.setGravity(Gravity.CENTER_VERTICAL);
            promo.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 8), dp(activity, 8));
            promo.setBackgroundResource(R.drawable.quick_action_background);
            promo.setClickable(true);
            promo.setFocusable(true);
            promo.setContentDescription("Get Harley's Clan Forum Stable v10000033");

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(activity);
            title.setText(BuildInfo.STABLE_PROMO_LABEL);
            title.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            title.setTypeface(title.getTypeface(), Typeface.BOLD);
            title.setIncludeFontPadding(false);
            copy.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView subtitle = new TextView(activity);
            subtitle.setText("Daily-use build • fewer experiments");
            subtitle.setTextColor(activity.getColor(R.color.hcf_muted));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            subtitle.setIncludeFontPadding(false);
            subtitle.setPadding(0, dp(activity, 2), 0, 0);
            copy.addView(subtitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f);
            promo.addView(copy, copyParams);

            Button getStable = new Button(activity);
            UiButtons.normalizeText(getStable);
            getStable.setText("Get Stable");
            getStable.setAllCaps(false);
            getStable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            getStable.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
            getStable.setBackgroundResource(R.drawable.button_background);
            getStable.setMinWidth(0);
            getStable.setMinimumWidth(0);
            getStable.setMinHeight(0);
            getStable.setMinimumHeight(0);
            getStable.setPadding(dp(activity, 7), 0, dp(activity, 7), 0);

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    dp(activity, 82),
                    dp(activity, 38));
            buttonParams.setMarginStart(dp(activity, 8));
            promo.addView(getStable, buttonParams);

            View.OnClickListener openStable = v -> openStable(activity);
            promo.setOnClickListener(openStable);
            getStable.setOnClickListener(openStable);

            LinearLayout.LayoutParams promoParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            promoParams.topMargin = dp(activity, 6);
            promoParams.bottomMargin = dp(activity, 5);

            View versionText = activity.findViewById(R.id.drawerVersionText);
            int insertAt = versionText == null ? drawer.getChildCount() : drawer.indexOfChild(versionText);
            if (insertAt < 0) insertAt = drawer.getChildCount();
            drawer.addView(promo, insertAt, promoParams);

            AppLogger.info(activity, "stable_promo_attach",
                    BuildInfo.STABLE_PROMO_LABEL + " | target=" + BuildInfo.STABLE_PROMO_VERSION_CODE);
        } catch (Throwable t) {
            try { AppLogger.warn(activity, "stable_promo_attach", t.getClass().getSimpleName()); }
            catch (Throwable ignored) {}
        }
    }

    private static void openStable(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildInfo.STABLE_PROMO_URL));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(intent);
            AppLogger.info(activity, "stable_promo_open", BuildInfo.STABLE_PROMO_LABEL);
        } catch (Throwable t) {
            Toast.makeText(activity, "Unable to open the Stable download.", Toast.LENGTH_LONG).show();
            try { AppLogger.warn(activity, "stable_promo_open", t.getClass().getSimpleName()); }
            catch (Throwable ignored) {}
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics()));
    }

    private StablePromoInjector() {}
}
