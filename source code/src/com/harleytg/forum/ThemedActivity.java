package com.harleytg.forum;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

/**
 * Base Activity that applies the user-selected HCF theme before Android resolves
 * page resources. Every native screen must inherit from this class.
 */
abstract class ThemedActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.prepare(this);
        super.onCreate(savedInstanceState);
        ThemeManager.applySystemBars(this);
    }
}
