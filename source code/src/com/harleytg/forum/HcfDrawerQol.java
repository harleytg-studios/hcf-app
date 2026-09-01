package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small quality-of-life layer for the native HCF drawer.
 *
 * It deliberately reuses the existing MainActivity views/listeners so Home,
 * Notifications and HCF Events keep one source of truth for their behavior.
 */
public final class HcfDrawerQol {
    private static final String QUICK_ROW_TAG = "hcf_drawer_quick_row_v1";
    private static final String ADMIN_BUTTON_TAG = "hcf_drawer_admin_v1";
    private static final String AUTH_BADGE_WRAP_TAG = "hcf_drawer_auth_badge_wrap_v1";
    private static final String AUTH_BADGE_TAG = "hcf_drawer_auth_badge_v2";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static WeakReference<Activity> resumedMain = new WeakReference<>(null);

    private HcfDrawerQol() {}

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (activity instanceof HcfForum.MainActivity) {
                            resumedMain = new WeakReference<>(activity);
                            MAIN.removeCallbacks(POLL);
                            MAIN.post(POLL);
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {
                        Activity current = resumedMain.get();
                        if (current == activity) {
                            resumedMain.clear();
                            MAIN.removeCallbacks(POLL);
                        }
                    }

                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override public void onActivityDestroyed(Activity activity) {
                        Activity current = resumedMain.get();
                        if (current == activity) {
                            resumedMain.clear();
                            MAIN.removeCallbacks(POLL);
                        }
                    }
                });
    }

    private static final Runnable POLL = new Runnable() {
        @Override public void run() {
            Activity activity = resumedMain.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            try {
                apply(activity);
            } catch (Throwable error) {
                AppLogger.warn(activity, "drawer_qol", error.getClass().getSimpleName());
            }
            MAIN.postDelayed(this, 500L);
        }
    };

    private static void apply(Activity activity) {
        View drawer = activity.findViewById(R.id.drawerPanel);
        if (!(drawer instanceof ViewGroup)) return;
        ViewGroup drawerRoot = (ViewGroup) drawer;

        TextView forumLabel = findTextExact(drawerRoot, "Forum controls");
        if (forumLabel == null) return;
        ViewParent forumParent = forumLabel.getParent();
        if (!(forumParent instanceof LinearLayout)) return;
        LinearLayout menu = (LinearLayout) forumParent;

        ensureQuickRow(activity, menu, forumLabel);
        rehomeHcfEvents(menu);
        ensureAdminEntry(activity, menu);
        repairAuthNewBadge(activity, menu);
    }

    /**
     * Exposes the already-wired Home and Notifications buttons as a compact second row.
     * The existing notification count TextView is reused as an overlay badge.
     */
    private static void ensureQuickRow(Activity activity, LinearLayout menu, TextView forumLabel) {
        View existing = menu.findViewWithTag(QUICK_ROW_TAG);
        if (existing instanceof LinearLayout) return;

        Button home = activity.findViewById(R.id.drawerHome);
        Button notifications = activity.findViewById(R.id.drawerNotifications);
        TextView badge = activity.findViewById(R.id.drawerNotificationCountBadge);
        if (home == null || notifications == null) return;

        int forumIndex = menu.indexOfChild(forumLabel);
        if (forumIndex < 0 || forumIndex + 1 >= menu.getChildCount()) return;

        View firstControlRow = menu.getChildAt(forumIndex + 1);
        int insertAt = menu.indexOfChild(firstControlRow) + 1;

        removeFromParent(home);
        removeFromParent(notifications);
        if (badge != null) removeFromParent(badge);

        LinearLayout row = new LinearLayout(activity);
        row.setTag(QUICK_ROW_TAG);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
        rowLp.topMargin = dp(activity, 8);

        configureQuickButton(home, "Home");
        LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        homeLp.rightMargin = dp(activity, 5);
        row.addView(home, homeLp);

        FrameLayout notificationCell = new FrameLayout(activity);
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        cellLp.leftMargin = dp(activity, 5);
        row.addView(notificationCell, cellLp);

        configureQuickButton(notifications, "Notifications");
        notificationCell.addView(notifications, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (badge != null) {
            styleUnreadBadge(activity, badge);
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 20),
                    Gravity.TOP | Gravity.END);
            badgeLp.topMargin = dp(activity, 3);
            badgeLp.rightMargin = dp(activity, 4);
            notificationCell.addView(badge, badgeLp);
        }

        menu.addView(row, Math.min(insertAt, menu.getChildCount()), rowLp);
        AppLogger.info(activity, "drawer_qol", "forum_quick_row_ready");
    }

    private static void configureQuickButton(Button button, String label) {
        button.setVisibility(View.VISIBLE);
        button.setText(label);
        button.setMaxLines(1);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(button.getPaddingLeft(), 0, button.getPaddingRight(), 0);
    }

    private static void styleUnreadBadge(Activity activity, TextView badge) {
        badge.setTextSize(9f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(activity.getColor(R.color.hcf_on_accent));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(activity, 20));
        badge.setMinimumWidth(dp(activity, 20));
        badge.setPadding(dp(activity, 5), 0, dp(activity, 5), 0);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(activity.getColor(R.color.hcf_cyan));
        background.setCornerRadius(dp(activity, 10));
        background.setStroke(dp(activity, 1), activity.getColor(R.color.hcf_bg));
        badge.setBackground(background);
    }

    /**
     * HCF Events is forum-sourced functionality. Move the existing injected view above
     * the App heading so the same click listener, route and injector-owned tag stay intact.
     */
    private static void rehomeHcfEvents(LinearLayout menu) {
        TextView appLabel = findDirectText(menu, "App");
        TextView events = findTextExact(menu, "HCF Events");
        if (appLabel == null || events == null) return;

        if (events.getParent() == menu) {
            int eventsIndex = menu.indexOfChild(events);
            int appIndex = menu.indexOfChild(appLabel);
            if (eventsIndex >= 0 && appIndex >= 0 && eventsIndex < appIndex) return;
        }

        ViewParent parent = events.getParent();
        if (!(parent instanceof ViewGroup)) return;
        ViewGroup.LayoutParams original = events.getLayoutParams();
        ((ViewGroup) parent).removeView(events);

        int appIndex = menu.indexOfChild(appLabel);
        if (appIndex < 0) return;
        events.setVisibility(View.VISIBLE);

        LinearLayout.LayoutParams lp;
        if (original instanceof LinearLayout.LayoutParams) {
            lp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) original);
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        lp.topMargin = dp(menu.getContext(), 8);
        menu.addView(events, appIndex, lp);
    }

    /**
     * Adds a forum Admin shortcut only while the currently resolved forum identity is
     * both signed in and marked administrator by ForumIdentity. No username, email or
     * local allow-list is used, so visibility follows the same live identity shown in
     * the drawer identity card.
     */
    private static void ensureAdminEntry(Activity activity, LinearLayout menu) {
        ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
        boolean allowed = identity != null && identity.loggedIn && identity.admin;
        View existing = menu.findViewWithTag(ADMIN_BUTTON_TAG);

        if (!allowed) {
            if (existing != null) removeFromParent(existing);
            return;
        }

        if (existing instanceof Button) {
            normalizeAdminButton(activity, (Button) existing);
            return;
        }

        TextView appLabel = findDirectText(menu, "App");
        if (appLabel == null) return;

        Button admin = new Button(activity, null, 0, R.style.HcfDrawerItem);
        admin.setTag(ADMIN_BUTTON_TAG);
        admin.setText("Admin");
        admin.setAllCaps(false);
        admin.setMaxLines(1);
        admin.setContentDescription("Open forum administrator panel");
        normalizeAdminButton(activity, admin);
        try { FaIcons.applyStart(admin, R.drawable.fa_shield); } catch (Throwable ignored) {}

        admin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                ForumIdentity.Snapshot current = ForumIdentity.load(activity);
                if (current == null || !current.loggedIn || !current.admin) {
                    removeFromParent(admin);
                    AppLogger.warn(activity, "drawer_admin", "identity_no_longer_admin");
                    return;
                }

                String host = current.host;
                if (!ForumUrlRouter.isForumHost(host)) {
                    host = activity.getSharedPreferences("hcf_app", Context.MODE_PRIVATE)
                            .getString("active_host", "forum.harleytg.com");
                }
                if (!ForumUrlRouter.isForumHost(host)) host = "forum.harleytg.com";

                String adminUrl = "https://" + host + "/admin";
                View target = activity.findViewById(R.id.webView);
                if (!(target instanceof WebView)) {
                    AppLogger.warn(activity, "drawer_admin", "webview_missing");
                    return;
                }

                activity.onBackPressed();
                ((WebView) target).loadUrl(adminUrl);
                AppLogger.info(activity, "drawer_admin", AppLogger.safeUrl(adminUrl));
            }
        });

        int appIndex = menu.indexOfChild(appLabel);
        if (appIndex < 0) return;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, 6);
        menu.addView(admin, appIndex, lp);
        AppLogger.info(activity, "drawer_qol", "admin_identity_action_ready");
    }

    /** Keep Admin compact while preserving the standard HCF drawer-item styling. */
    private static void normalizeAdminButton(Activity activity, Button admin) {
        admin.setVisibility(View.VISIBLE);
        admin.setMinWidth(0);
        admin.setMinimumWidth(0);
        admin.setTextSize(14f);
        int cardHeight = dp(activity, 52);
        admin.setMinHeight(cardHeight);
        admin.setMinimumHeight(cardHeight);
        ViewGroup.LayoutParams params = admin.getLayoutParams();
        if (params != null && params.height > 0 && params.height != cardHeight) {
            params.height = cardHeight;
            admin.setLayoutParams(params);
        }
    }

    /**
     * Keeps exactly one NEW badge anchored to HCF Auth. The original drawer injector can
     * add its own NEW view after this wrapper is created, so every refresh also removes
     * any later standalone/duplicate NEW badges instead of returning early.
     */
    private static void repairAuthNewBadge(Activity activity, LinearLayout menu) {
        FrameLayout wrapper = null;
        View wrapped = menu.findViewWithTag(AUTH_BADGE_WRAP_TAG);
        if (wrapped instanceof FrameLayout) wrapper = (FrameLayout) wrapped;

        if (wrapper == null) {
            TextView auth = findTextExact(menu, "HCF Auth");
            if (auth == null) return;

            View authRoot = topLevelChild(menu, auth);
            if (authRoot == null) return;
            int authIndex = menu.indexOfChild(authRoot);
            if (authIndex < 0) return;
            ViewGroup.LayoutParams original = authRoot.getLayoutParams();

            removeFromParent(authRoot);

            wrapper = new FrameLayout(activity);
            wrapper.setTag(AUTH_BADGE_WRAP_TAG);
            wrapper.setMinimumHeight(dp(activity, 64));

            FrameLayout.LayoutParams authLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wrapper.addView(authRoot, authLp);

            LinearLayout.LayoutParams wrapperLp;
            if (original instanceof LinearLayout.LayoutParams) {
                wrapperLp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) original);
                wrapperLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                wrapperLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            menu.addView(wrapper, Math.min(authIndex, menu.getChildCount()), wrapperLp);
        }

        TextView anchored = null;
        View tagged = wrapper.findViewWithTag(AUTH_BADGE_TAG);
        if (tagged instanceof TextView) anchored = (TextView) tagged;
        if (anchored == null) {
            TextView existingInside = findTextExact(wrapper, "NEW");
            if (existingInside != null) {
                anchored = existingInside;
                anchored.setTag(AUTH_BADGE_TAG);
            }
        }
        if (anchored == null) {
            anchored = new TextView(activity);
            anchored.setTag(AUTH_BADGE_TAG);
            wrapper.addView(anchored);
        }

        styleNewBadge(activity, anchored);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 24),
                Gravity.TOP | Gravity.END);
        badgeLp.topMargin = dp(activity, 8);
        badgeLp.rightMargin = dp(activity, 10);
        anchored.setLayoutParams(badgeLp);
        anchored.bringToFront();

        removeStrayNewBadges(menu, wrapper, anchored);
    }

    private static void removeStrayNewBadges(LinearLayout menu, FrameLayout wrapper, TextView anchored) {
        java.util.ArrayList<TextView> badges = new java.util.ArrayList<TextView>();
        collectTextExact(menu, "NEW", badges);
        for (TextView badge : badges) {
            if (badge == anchored || isDescendantOf(badge, wrapper)) continue;

            View top = topLevelChild(menu, badge);
            removeFromParent(badge);
            if (top == null) continue;
            if (top == badge) {
                removeFromParent(top);
            } else if (top instanceof ViewGroup && ((ViewGroup) top).getChildCount() == 0) {
                removeFromParent(top);
            } else if (top.getParent() == menu) {
                // A source-owned badge container can retain fixed margins/height even after
                // its NEW TextView is removed. Collapse only containers that no longer
                // contain meaningful visible content.
                if (!hasVisibleText((ViewGroup) top)) removeFromParent(top);
            }
        }
    }

    private static boolean hasVisibleText(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && child.getVisibility() == View.VISIBLE) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && !text.toString().trim().isEmpty()) return true;
            }
            if (child instanceof ViewGroup && hasVisibleText((ViewGroup) child)) return true;
        }
        return false;
    }

    private static boolean isDescendantOf(View child, ViewGroup ancestor) {
        if (child == null || ancestor == null) return false;
        ViewParent parent = child.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private static void collectTextExact(ViewGroup root, String exact,
                                         java.util.ArrayList<TextView> out) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && exact.equals(text.toString().trim())) {
                    out.add((TextView) child);
                }
            }
            if (child instanceof ViewGroup) {
                collectTextExact((ViewGroup) child, exact, out);
            }
        }
    }

    private static void styleNewBadge(Activity activity, TextView badge) {
        badge.setText("NEW");
        badge.setTextSize(9f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(activity, 38));
        badge.setMinimumWidth(dp(activity, 38));
        badge.setPadding(dp(activity, 7), 0, dp(activity, 7), 0);
        badge.setClickable(false);
        badge.setFocusable(false);
        badge.setVisibility(View.VISIBLE);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(activity.getColor(R.color.hcf_bg));
        background.setCornerRadius(dp(activity, 12));
        background.setStroke(dp(activity, 1), activity.getColor(R.color.hcf_cyan));
        badge.setBackground(background);
    }

    private static View topLevelChild(LinearLayout menu, View descendant) {
        if (menu == null || descendant == null) return null;
        View current = descendant;
        ViewParent parent = current.getParent();
        while (parent instanceof View && parent != menu) {
            current = (View) parent;
            parent = current.getParent();
        }
        return parent == menu ? current : null;
    }

    private static TextView findDirectText(LinearLayout root, String exact) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            CharSequence text = ((TextView) child).getText();
            if (text != null && exact.equals(text.toString().trim())) return (TextView) child;
        }
        return null;
    }

    private static TextView findTextExact(ViewGroup root, String exact) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && exact.equals(text.toString().trim())) return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView nested = findTextExact((ViewGroup) child, exact);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void removeFromParent(View view) {
        ViewParent parent = view == null ? null : view.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Installs the drawer QoL layer before MainActivity is shown. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}