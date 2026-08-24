package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the forum WebView cookie store synchronized with persistent storage.
 *
 * This class never reads or exports cookie values. It only asks Android WebView's
 * CookieManager to flush its existing first-party session state to disk at safe
 * lifecycle points so a normal app close/reopen does not unnecessarily lose a
 * valid forum login.
 */
public final class HcfSessionPersistence {
    private static final long FOREGROUND_FLUSH_MS = 15000L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger RESUMED_ACTIVITIES = new AtomicInteger(0);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HcfSessionPersistence() {}

    private static final Runnable FOREGROUND_FLUSH = new Runnable() {
        @Override public void run() {
            if (RESUMED_ACTIVITIES.get() <= 0) return;
            flushCookies();
            MAIN.postDelayed(this, FOREGROUND_FLUSH_MS);
        }
    };

    static void flushCookies() {
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            manager.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        flushCookies();

        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        Application application = (Application) appContext;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override public void onActivityResumed(Activity activity) {
                int resumed = RESUMED_ACTIVITIES.incrementAndGet();
                flushCookies();
                if (resumed == 1) {
                    MAIN.removeCallbacks(FOREGROUND_FLUSH);
                    MAIN.postDelayed(FOREGROUND_FLUSH, FOREGROUND_FLUSH_MS);
                }
            }

            @Override public void onActivityPaused(Activity activity) {
                flushCookies();
                int remaining = Math.max(0, RESUMED_ACTIVITIES.decrementAndGet());
                RESUMED_ACTIVITIES.set(remaining);
                if (remaining == 0) MAIN.removeCallbacks(FOREGROUND_FLUSH);
            }

            @Override public void onActivityStopped(Activity activity) {
                flushCookies();
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                flushCookies();
            }

            @Override public void onActivityDestroyed(Activity activity) {
                flushCookies();
            }
        });
    }

    /** Installs persistence before the first Activity/WebView is created. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            return 0;
        }
    }
}
