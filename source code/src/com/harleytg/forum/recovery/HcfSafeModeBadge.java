package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Shows a compact Safe Mode badge as an overlay below the native HCF URL bar. */
public final class HcfSafeModeBadge {
    private static final String PREF_FILE = "hcf_app";
    private static final String KEY_ACTIVE = "safe_mode_active";
    private static final String KEY_SESSION_PID = "safe_mode_session_pid";
    private static final String BADGE_TAG = "hcf_safe_mode_header_badge";

    private HcfSafeModeBadge() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (!(appContext instanceof Application)) return true;

            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity activity, Bundle state) {
                            HcfSafeModeBadge.update(activity);
                        }

                        @Override public void onActivityResumed(Activity activity) {
                            HcfSafeModeBadge.update(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityPaused(Activity activity) {}
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                        @Override public void onActivityDestroyed(Activity activity) {}
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void update(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        View rootView = activity.findViewById(R.id.rootFrame);
        View urlBar = activity.findViewById(R.id.urlBar);
        if (!(rootView instanceof FrameLayout) || urlBar == null) return;

        FrameLayout root = (FrameLayout) rootView;
        View badge = findTaggedView(root, BADGE_TAG);
        boolean active = isSafeModeActive(activity);

        if (!active) {
            if (badge != null) badge.setVisibility(View.GONE);
            return;
        }

        if (badge == null) {
            badge = createBadge(activity);
            root.addView(badge);

            final View overlayBadge = badge;
            urlBar.addOnLayoutChangeListener((v, left, top, right, bottom,
                                              oldLeft, oldTop, oldRight, oldBottom) ->
                    positionBelowUrlBar(root, urlBar, overlayBadge));
        }

        final View overlayBadge = badge;
        badge.setVisibility(View.VISIBLE);
        badge.bringToFront();
        root.post(() -> positionBelowUrlBar(root, urlBar, overlayBadge));
        AppLogger.info(activity, "safe_mode_badge", "overlay_below_url_bar");
    }

    private static void positionBelowUrlBar(FrameLayout root, View urlBar, View badge) {
        if (root == null || urlBar == null || badge == null) return;

        int[] rootLocation = new int[2];
        int[] urlLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        urlBar.getLocationOnScreen(urlLocation);

        int topMargin = (urlLocation[1] - rootLocation[1]) + urlBar.getHeight() + dp(root.getContext(), 6);
        FrameLayout.LayoutParams lp;
        ViewGroup.LayoutParams current = badge.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams) {
            lp = (FrameLayout.LayoutParams) current;
        } else {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(root.getContext(), 26));
        }

        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = dp(root.getContext(), 26);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = Math.max(0, topMargin);
        lp.rightMargin = dp(root.getContext(), 10);
        badge.setLayoutParams(lp);
        badge.bringToFront();
    }

    private static boolean isSafeModeActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ACTIVE, false)
                && prefs.getInt(KEY_SESSION_PID, -1) == android.os.Process.myPid();
    }

    private static TextView createBadge(Context context) {
        TextView badge = new TextView(context);
        badge.setTag(BADGE_TAG);
        badge.setText("Safe Mode");
        badge.setTextSize(9);
        badge.setTextColor(context.getColor(R.color.hcf_cyan_bright));
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setIncludeFontPadding(false);
        badge.setContentDescription("Safe Mode active");
        badge.setBackgroundResource(R.drawable.status_chip_background);
        badge.setPadding(dp(context, 7), 0, dp(context, 7), 0);
        badge.setMinHeight(dp(context, 26));
        badge.setClickable(false);
        badge.setFocusable(false);
        badge.setElevation(dp(context, 8));

        try {
            Drawable shield = context.getDrawable(R.drawable.fa_shield);
            if (shield != null) {
                shield = shield.mutate();
                shield.setTintList(ColorStateList.valueOf(context.getColor(R.color.hcf_cyan_bright)));
                int size = dp(context, 11);
                shield.setBounds(0, 0, size, size);
                badge.setCompoundDrawablesRelative(shield, null, null, null);
                badge.setCompoundDrawablePadding(dp(context, 4));
            }
        } catch (Throwable ignored) {}

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 26), Gravity.TOP | Gravity.END);
        lp.rightMargin = dp(context, 10);
        badge.setLayoutParams(lp);
        return badge;
    }

    private static View findTaggedView(View view, String tag) {
        if (tag.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTaggedView(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
