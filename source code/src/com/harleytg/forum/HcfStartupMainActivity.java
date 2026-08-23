package com.harleytg.forum.dev;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

/**
 * MainActivity host used after the native startup loader has completed.
 *
 * The normal MainActivity still owns the forum engine, navigation, recovery,
 * identity, notifications, updates and all other app behavior. This subclass
 * only removes the old native "checking" overlay during the startup handoff so
 * the forum's own WebView loading animation can take over immediately.
 */
public final class HcfStartupMainActivity extends HcfMainActivities.MainActivity {
    public static final String EXTRA_STARTUP_HANDOFF = "hcf_startup_handoff";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        if (getIntent() == null || !getIntent().getBooleanExtra(EXTRA_STARTUP_HANDOFF, false)) {
            return;
        }

        try {
            View statusOverlay = findViewById(R.id.statusOverlay);
            if (statusOverlay != null) {
                statusOverlay.animate().cancel();
                statusOverlay.setAlpha(0.0f);
                statusOverlay.setVisibility(View.GONE);
            }

            View startupState = findViewById(R.id.startupStateContainer);
            if (startupState != null) {
                startupState.setVisibility(View.GONE);
            }

            WebView webView = findViewById(R.id.webView);
            if (webView != null) {
                webView.setAlpha(1.0f);
                webView.setVisibility(View.VISIBLE);
            }

            AppLogger.info(this, "startup_handoff", "native loader complete; forum WebView owns loading UI");
        } catch (Throwable error) {
            AppLogger.warn(this, "startup_handoff", error.getClass().getSimpleName());
        }
    }
}
