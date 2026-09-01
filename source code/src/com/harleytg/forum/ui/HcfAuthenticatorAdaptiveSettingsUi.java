package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * Keeps the embedded HCF Authenticator setup UI adaptive.
 *
 * Before enrollment, Settings shows the full Nearata QR / Setup-key workflow.
 * Once a working local TOTP secret exists, the setup/activation walkthrough is
 * collapsed away and the user is left with the live passcode UI plus a compact
 * remove/reset control. Clearing the local secret restores the setup workflow.
 */
public final class HcfAuthenticatorAdaptiveSettingsUi {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> OBSERVERS = new WeakHashMap<>();
    private static boolean installed;

    private HcfAuthenticatorAdaptiveSettingsUi() {}

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
                if (isSettings(activity)) {
                    observe(activity);
                    schedule(activity);
                }
            }

            @Override public void onActivityResumed(Activity activity) {
                if (isSettings(activity)) {
                    observe(activity);
                    schedule(activity);
                }
            }

            @Override public void onActivityDestroyed(Activity activity) { remove(activity); }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    private static boolean isSettings(Activity activity) {
        return activity != null
                && "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity"
                .equals(activity.getClass().getName());
    }

    private static void observe(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> apply(activity);
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    private static void remove(Activity activity) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) {
            listener = OBSERVERS.remove(activity);
        }
        if (listener == null) return;
        try {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(listener);
            }
        } catch (Throwable ignored) {}
    }

    private static void schedule(Activity activity) {
        MAIN.postDelayed(() -> apply(activity), 90L);
        MAIN.postDelayed(() -> apply(activity), 240L);
        MAIN.postDelayed(() -> apply(activity), 520L);
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        // Do nothing until the embedded authenticator subsetting has actually rendered.
        TextView codeTitle = findText(root, "CURRENT 6-DIGIT PASSCODE");
        if (codeTitle == null) return;

        boolean configured = isConfigured(activity);

        View heading = findText(root, "HCF AUTHENTICATOR • FULL SETTINGS");
        View nearataStatus = parentOf(findText(root, "Nearata 2FA controls"));
        View setupPanel = parentOf(findText(root, "SET UP HCF AUTHENTICATOR"));
        View finishPanel = parentOf(findText(root, "FINISH IN FORUM USER SETTINGS"));
        View managePanel = parentOf(findText(root, "LOCAL AUTHENTICATOR STORAGE"));

        if (configured) {
            setVisible(heading, false);
            setVisible(nearataStatus, false);
            setVisible(setupPanel, false);
            setVisible(finishPanel, false);
            setVisible(managePanel, true);
            compactManagePanel(managePanel, activity);
        } else {
            setVisible(heading, true);
            setVisible(nearataStatus, true);
            setVisible(setupPanel, true);
            setVisible(finishPanel, true);
            // There is nothing to remove/reset before enrollment.
            setVisible(managePanel, false);
        }
    }

    private static void compactManagePanel(View managePanel, Activity activity) {
        if (!(managePanel instanceof ViewGroup)) return;
        ViewGroup panel = (ViewGroup) managePanel;
        TextView label = findText(panel, "LOCAL AUTHENTICATOR STORAGE");
        TextView detail = findTextStarting(panel, "Only the TOTP setup secret is stored by HCF");
        TextView warning = findTextStarting(panel, "Removing the local key does not disable Nearata 2FA");
        setVisible(label, false);
        setVisible(detail, false);
        setVisible(warning, false);
        if (panel instanceof LinearLayout) {
            int pad = dp(activity, 6);
            panel.setPadding(pad, pad, pad, pad);
        }
    }

    private static boolean isConfigured(Context context) {
        try {
            HcfAuthenticator.Config config = HcfAuthenticator.Vault.load(context);
            return config != null && config.secret != null && !config.secret.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static View parentOf(View view) {
        return view != null && view.getParent() instanceof View ? (View) view.getParent() : null;
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
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

    private static TextView findTextStarting(View view, String prefix) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().trim().startsWith(prefix)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextStarting(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
