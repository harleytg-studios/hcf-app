package com.harleytg.forum;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Base Activity that applies the user-selected HCF theme before Android resolves
 * page resources and recreates native screens when the theme preference changes.
 */
abstract class ThemedActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String appliedThemeSignature;
    private SharedPreferences themePrefs;
    private boolean themeRecreatePending;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.prepare(this);
        super.onCreate(savedInstanceState);
        ThemeManager.applySystemBars(this);
        appliedThemeSignature = ThemeManager.signature(this);
        themePrefs = getSharedPreferences("hcf_app", 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (themePrefs != null) {
            themePrefs.registerOnSharedPreferenceChangeListener(this);
        }
        recreateForThemeIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        recreateForThemeIfNeeded();
    }

    @Override
    protected void onStop() {
        if (themePrefs != null) {
            themePrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        super.onStop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("app_theme".equals(key) || "forum_auto_theme".equals(key)) {
            recreateForThemeIfNeeded();
        }
    }

    private void recreateForThemeIfNeeded() {
        if (themeRecreatePending || isFinishing() || isDestroyed()
                || !ThemeManager.changedSince(this, appliedThemeSignature)) {
            return;
        }
        themeRecreatePending = true;
        getWindow().getDecorView().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
        }, 90L);
    }
}
