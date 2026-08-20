package com.harleytg.forum.dev;

import android.content.Context;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

/* loaded from: classes.dex */
final class UiButtons {
    static void normalizeText(Button button) {
        if (button == null) {
            return;
        }
        button.setAllCaps(false);
        button.setGravity(17);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
    }

    static ImageButton iconButton(Context context, int i, int i2, int i3, String str) {
        ImageButton imageButton = new ImageButton(context);
        imageButton.setImageResource(i);
        imageButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (i2 != 0) {
            imageButton.setBackgroundResource(i2);
        } else {
            imageButton.setBackgroundColor(0);
        }
        int dp = dp(context, i3);
        imageButton.setPadding(dp, dp, dp, dp);
        imageButton.setMinimumWidth(0);
        imageButton.setMinimumHeight(0);
        imageButton.setAdjustViewBounds(false);
        if (str == null || str.trim().isEmpty()) {
            str = "Button";
        }
        imageButton.setContentDescription(str);
        return imageButton;
    }

    private static int dp(Context context, int i) {
        return Math.round(i * context.getResources().getDisplayMetrics().density);
    }

    private UiButtons() {
    }
}
