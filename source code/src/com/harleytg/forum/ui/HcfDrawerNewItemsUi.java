package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * HCF_DRAWER_NEW_SHORTCUTS_V2_STARTUP_HOST
 *
 * Adds two native hamburger-menu shortcuts using the existing HCF drawer style:
 *   HCF Auth   -> Account & Security > HCF Authenticator
 *   HCF Events -> https://forum.harleytg.com/events
 *
 * Both rows include a compact NEW badge while these features are newly exposed.
 * StartupMainActivity subclasses HcfForum.MainActivity, so the activity test is
 * intentionally instanceof-based rather than exact-class-name based.
 */
public final class HcfDrawerNewItemsUi {
    private static final String SETTINGS_ACTIVITY = "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";
    private static final String TAG_AUTH = "hcf_drawer_auth_new_v2";
    private static final String TAG_EVENTS = "hcf_drawer_events_new_v2";
    private static final String EXTRA_OPEN_AUTH = "hcf_open_authenticator_subsettings";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean installed;

    private HcfDrawerNewItemsUi() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (appContext instanceof Application) install((Application) appContext);
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

    private static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (isMain(activity)) scheduleDrawerInstall(activity);
                if (isSettings(activity)) scheduleRequestedAuth(activity);
            }

            @Override public void onActivityResumed(Activity activity) {
                if (isMain(activity)) scheduleDrawerInstall(activity);
                if (isSettings(activity)) scheduleRequestedAuth(activity);
            }

            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static boolean isMain(Activity activity) {
        return activity instanceof HcfForum.MainActivity;
    }

    private static boolean isSettings(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static void scheduleDrawerInstall(Activity activity) {
        MAIN.postDelayed(() -> installDrawerRows(activity), 60L);
        MAIN.postDelayed(() -> installDrawerRows(activity), 180L);
        MAIN.postDelayed(() -> installDrawerRows(activity), 420L);
        MAIN.postDelayed(() -> installDrawerRows(activity), 900L);
    }

    private static void installDrawerRows(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View drawer = findId(activity, "drawerPanel");
        View settings = findId(activity, "drawerSettings");
        if (!(drawer instanceof ViewGroup) || settings == null) return;
        if (drawer.findViewWithTag(TAG_AUTH) != null || drawer.findViewWithTag(TAG_EVENTS) != null) return;

        ViewGroup list = nearestLinearParent(settings, drawer);
        if (!(list instanceof LinearLayout)) return;

        View auth = shortcutRow(activity, "HCF Auth", R.drawable.fa_lock, TAG_AUTH,
                v -> openAuthenticatorSettings(activity));
        View events = shortcutRow(activity, "HCF Events", R.drawable.fa_calendar, TAG_EVENTS,
                v -> openEvents(activity));

        int settingsIndex = list.indexOfChild(settings);
        if (settingsIndex < 0) settingsIndex = list.getChildCount();
        list.addView(auth, settingsIndex);
        list.addView(events, Math.min(settingsIndex + 1, list.getChildCount()));
    }

    private static View shortcutRow(Activity activity, String title, int iconRes,
                                    String tag, View.OnClickListener listener) {
        FrameLayout shell = new FrameLayout(activity);
        shell.setTag(tag);

        Button button = new Button(activity, null, 0, R.style.HcfDrawerItem);
        button.setText(title);
        button.setAllCaps(false);
        button.setContentDescription(title + ", new feature");
        button.setOnClickListener(listener);
        try { FaIcons.applyStart(button, iconRes); }
        catch (Throwable ignored) {
            button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(activity, 10));
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                button.setCompoundDrawableTintList(ColorStateList.valueOf(cyan(activity)));
            }
        }
        shell.addView(button, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, drawerItemHeight(activity)));

        TextView badge = new TextView(activity);
        badge.setText("NEW");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(cyan(activity));
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setPadding(dp(activity, 7), 0, dp(activity, 7), 0);
        badge.setBackground(badgeBackground(activity));
        badge.setContentDescription("New");
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 20), Gravity.END | Gravity.CENTER_VERTICAL);
        badgeLp.rightMargin = dp(activity, 12);
        shell.addView(badge, badgeLp);

        LinearLayout.LayoutParams shellLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, drawerItemHeight(activity));
        shellLp.bottomMargin = dp(activity, 5);
        shell.setLayoutParams(shellLp);
        return shell;
    }

    private static void openAuthenticatorSettings(Activity activity) {
        hideDrawer(activity);
        try {
            Intent intent = new Intent(activity, HcfSubActivities.SettingsActivity.class);
            intent.putExtra(EXTRA_OPEN_AUTH, true);
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static void openEvents(Activity activity) {
        hideDrawer(activity);
        String url = "https://forum.harleytg.com/events";
        try {
            WebView webView = mainWebView(activity);
            if (webView != null) {
                webView.loadUrl(url);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Intent intent = new Intent(activity, HcfForum.MainActivity.class);
            intent.setData(Uri.parse(url));
            activity.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static void hideDrawer(Activity activity) {
        View drawer = findId(activity, "drawerPanel");
        View scrim = findId(activity, "drawerScrim");
        if (drawer != null) drawer.setVisibility(View.GONE);
        if (scrim != null) scrim.setVisibility(View.GONE);
    }

    private static WebView mainWebView(Activity activity) {
        try {
            Class<?> cursor = activity.getClass();
            while (cursor != null) {
                try {
                    Field field = cursor.getDeclaredField("webView");
                    field.setAccessible(true);
                    Object value = field.get(activity);
                    if (value instanceof WebView) return (WebView) value;
                } catch (NoSuchFieldException missing) {
                    cursor = cursor.getSuperclass();
                    continue;
                }
                break;
            }
            View view = findId(activity, "webView");
            return view instanceof WebView ? (WebView) view : null;
        } catch (Throwable ignored) {
            View view = findId(activity, "webView");
            return view instanceof WebView ? (WebView) view : null;
        }
    }

    private static void scheduleRequestedAuth(Activity activity) {
        if (activity == null || activity.getIntent() == null
                || !activity.getIntent().getBooleanExtra(EXTRA_OPEN_AUTH, false)) return;
        activity.getIntent().removeExtra(EXTRA_OPEN_AUTH);
        MAIN.postDelayed(() -> openAccountSecurity(activity), 70L);
        MAIN.postDelayed(() -> openAccountSecurity(activity), 210L);
        MAIN.postDelayed(() -> expandAuthenticatorPanel(activity), 520L);
        MAIN.postDelayed(() -> expandAuthenticatorPanel(activity), 850L);
    }

    private static void openAccountSecurity(Activity activity) {
        invokeOneString(activity, "showSettingsSection", "account_security");
    }

    private static void expandAuthenticatorPanel(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        TextView title = findText(root, "HCF Authenticator");
        if (title == null) return;
        View panel = connectedPanel(title);
        if (!(panel instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) panel;
        if (group.getChildCount() < 2) return;
        View body = group.getChildAt(1);
        if (body != null && body.getVisibility() != View.VISIBLE) {
            View header = group.getChildAt(0);
            if (header != null) header.performClick();
        }
    }

    private static boolean invokeOneString(Activity activity, String methodName, String argument) {
        Class<?> cursor = activity.getClass();
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(methodName, String.class);
                method.setAccessible(true);
                method.invoke(activity, argument);
                return true;
            } catch (NoSuchMethodException missing) {
                cursor = cursor.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static View connectedPanel(View title) {
        View current = title;
        while (current != null && current.getParent() instanceof View) {
            current = (View) current.getParent();
            if (!(current instanceof LinearLayout)) continue;
            LinearLayout layout = (LinearLayout) current;
            if (layout.getChildCount() == 2
                    && layout.getChildAt(0) instanceof LinearLayout
                    && layout.getChildAt(1) instanceof LinearLayout) return layout;
        }
        return null;
    }

    private static ViewGroup nearestLinearParent(View child, View stop) {
        View current = child;
        while (current != null && current != stop && current.getParent() instanceof View) {
            View parent = (View) current.getParent();
            if (parent instanceof LinearLayout) return (LinearLayout) parent;
            current = parent;
        }
        return stop instanceof ViewGroup ? (ViewGroup) stop : null;
    }

    private static View findId(Activity activity, String name) {
        if (activity == null) return null;
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private static TextView findText(View view, String exact) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && exact.equals(value.toString().trim())) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static GradientDrawable badgeBackground(Context context) {
        GradientDrawable bg = new GradientDrawable();
        int cyan = cyan(context);
        bg.setColor(Color.argb(24, Color.red(cyan), Color.green(cyan), Color.blue(cyan)));
        bg.setStroke(dp(context, 1), Color.argb(170, Color.red(cyan), Color.green(cyan), Color.blue(cyan)));
        bg.setCornerRadius(dp(context, 10));
        return bg;
    }

    private static int drawerItemHeight(Context context) {
        try { return context.getResources().getDimensionPixelSize(R.dimen.drawer_item_height); }
        catch (Throwable ignored) { return dp(context, 48); }
    }

    private static int cyan(Context context) {
        try { return context.getColor(R.color.hcf_cyan_bright); }
        catch (Throwable ignored) { return Color.rgb(0, 184, 240); }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
