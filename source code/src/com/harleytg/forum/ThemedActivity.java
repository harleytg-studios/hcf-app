package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/* loaded from: classes.dex */
abstract class ThemedActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String appliedThemeSignature;
    private SharedPreferences themePrefs;
    private boolean themeRecreatePending;

    ThemedActivity() {
    }

    @Override // android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    protected void attachBaseContext(Context context) {
        Context wrapped = context;
        try {
            Context candidate = ThemeManager.wrap(context);
            if (candidate != null) wrapped = candidate;
        } catch (Throwable unused) {
        }
        super.attachBaseContext(wrapped);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
            ThemeManager.prepare(this);
        } catch (Throwable unused) {
        }
        super.onCreate(bundle);
        try {
            AppDomainRouter.installAddressBarInflater(this);
        } catch (Throwable unused2) {
        }
        try {
            ThemeManager.applySystemBars(this);
        } catch (Throwable unused3) {
        }
        try {
            this.appliedThemeSignature = ThemeManager.signature(this);
        } catch (Throwable unused4) {
            this.appliedThemeSignature = "auto_forum";
        }
        try {
            this.themePrefs = getSharedPreferences("hcf_app", 0);
        } catch (Throwable unused5) {
            this.themePrefs = null;
        }
    }

    @Override // android.app.Activity
    protected void onStart() {
        super.onStart();
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try {
                sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            } catch (Throwable unused) {
            }
        }
        recreateForThemeIfNeeded();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        recreateForThemeIfNeeded();
    }

    @Override // android.app.Activity
    protected void onStop() {
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
            } catch (Throwable unused) {
            }
        }
        super.onStop();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("app_theme".equals(str) || "forum_auto_theme".equals(str)) {
            recreateForThemeIfNeeded();
        }
    }

    private void recreateForThemeIfNeeded() {
        try {
            if (this.themeRecreatePending || isFinishing() || isDestroyed() || !ThemeManager.changedSince(this, this.appliedThemeSignature)) {
                return;
            }
            this.themeRecreatePending = true;
            getWindow().getDecorView().postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.ThemedActivity$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    ThemedActivity.this.m210xc87fab7b();
                }
            }, 90L);
        } catch (Throwable unused) {
        }
    }

    /* renamed from: lambda$recreateForThemeIfNeeded$0$com-harleytg-forum-dev-ThemedActivity, reason: not valid java name */
    /* synthetic */ void m210xc87fab7b() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            recreate();
        } catch (Throwable unused) {
            this.themeRecreatePending = false;
        }
    }
}
