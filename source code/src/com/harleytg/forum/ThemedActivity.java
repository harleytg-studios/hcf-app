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
        super.attachBaseContext(ThemeManager.wrap(context));
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        ThemeManager.prepare(this);
        super.onCreate(bundle);
        ThemeManager.applySystemBars(this);
        this.appliedThemeSignature = ThemeManager.signature(this);
        this.themePrefs = getSharedPreferences("hcf_app", 0);
    }

    @Override // android.app.Activity
    protected void onStart() {
        super.onStart();
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
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
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onStop();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("app_theme".equals(str) || "forum_auto_theme".equals(str)) {
            recreateForThemeIfNeeded();
        }
    }

    private void recreateForThemeIfNeeded() {
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
    }

    /* renamed from: lambda$recreateForThemeIfNeeded$0$com-harleytg-forum-dev-ThemedActivity, reason: not valid java name */
    /* synthetic */ void m210xc87fab7b() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        recreate();
    }
}
