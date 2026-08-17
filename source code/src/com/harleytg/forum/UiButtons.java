package com.harleytg.forum;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Consistent native button geometry across all HCF app surfaces. */
final class UiButtons {
    static void normalizeText(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
    }

    static ImageButton iconButton(Context context, int iconRes, int backgroundRes, int paddingDp, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (backgroundRes != 0) button.setBackgroundResource(backgroundRes);
        else button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        int padding = dp(context, paddingDp);
        button.setPadding(padding, padding, padding, padding);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setAdjustViewBounds(false);
        button.setContentDescription(description == null || description.trim().isEmpty() ? "Button" : description);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private UiButtons() {}
}
