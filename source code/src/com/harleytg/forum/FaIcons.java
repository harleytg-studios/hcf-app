package com.harleytg.forum.dev;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import java.util.Locale;

/* loaded from: classes.dex */
final class FaIcons {
    private FaIcons() {
    }

    static int forLabel(String str) {
        String lowerCase = str == null ? "" : str.toLowerCase(Locale.US);
        if (lowerCase.contains("account security") || lowerCase.contains("security") || lowerCase.contains("safe link")) {
            return R.drawable.fa_shield;
        }
        if (lowerCase.contains("home")) {
            return R.drawable.fa_house;
        }
        if (lowerCase.contains("backup") || lowerCase.contains("primary") || lowerCase.contains("switch host") || lowerCase.contains("failover")) {
            return R.drawable.fa_right_left;
        }
        if (lowerCase.contains("browser") || lowerCase.contains("externally") || lowerCase.contains("open link")) {
            return R.drawable.fa_arrow_up_right_from_square;
        }
        if (lowerCase.contains("share")) {
            return R.drawable.fa_share_nodes;
        }
        if (lowerCase.contains("notification") || lowerCase.contains("alert") || lowerCase.contains("mention") || lowerCase.contains("reply")) {
            return R.drawable.fa_bell;
        }
        if (lowerCase.contains("setting") || lowerCase.contains("theme") || lowerCase.contains("appearance") || lowerCase.contains("permission")) {
            return R.drawable.fa_gear;
        }
        if (lowerCase.contains("log") || lowerCase.contains("diagnostic") || lowerCase.contains("report") || lowerCase.contains("history") || lowerCase.contains("details")) {
            return R.drawable.fa_list;
        }
        if (lowerCase.contains("support") || lowerCase.contains("email") || lowerCase.contains("contact")) {
            return R.drawable.fa_envelope;
        }
        if (lowerCase.contains("copy")) {
            return R.drawable.fa_copy;
        }
        if (lowerCase.contains("retry") || lowerCase.contains("refresh") || lowerCase.contains("sync") || lowerCase.contains("check for updates")) {
            return R.drawable.fa_rotate_right;
        }
        if (lowerCase.contains("back")) {
            return R.drawable.fa_arrow_left;
        }
        if (lowerCase.contains("create") || lowerCase.contains("post") || lowerCase.contains("new discussion")) {
            return R.drawable.fa_plus;
        }
        if (lowerCase.contains("identity") || lowerCase.contains("profile") || lowerCase.contains("account")) {
            return R.drawable.fa_user;
        }
        if (lowerCase.contains("connection") || lowerCase.contains("server") || lowerCase.contains("forum") || lowerCase.contains("cookie") || lowerCase.contains("site data") || lowerCase.contains("link")) {
            return R.drawable.fa_globe;
        }
        if (lowerCase.contains("install") || lowerCase.contains("download") || lowerCase.contains("update")) {
            return R.drawable.fa_download;
        }
        if (lowerCase.contains("error") || lowerCase.contains("recovery") || lowerCase.contains("crash")) {
            return R.drawable.fa_bug;
        }
        if (lowerCase.contains("about") || lowerCase.contains("what's new")) {
            return R.drawable.fa_circle_info;
        }
        lowerCase.contains("release");
        return R.drawable.fa_circle_info;
    }

    static void applyStart(TextView textView, String str) {
        applyStart(textView, forLabel(str));
    }

    static void applyStart(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) {
            return;
        }
        drawable.setBounds(0, 0, dp(textView.getContext(), 18), dp(textView.getContext(), 18));
        textView.setCompoundDrawablesRelative(drawable, null, null, null);
        textView.setCompoundDrawablePadding(dp(textView.getContext(), 10));
        tint(textView);
    }

    static void applyTop(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) {
            return;
        }
        drawable.setBounds(0, 0, dp(textView.getContext(), 19), dp(textView.getContext(), 19));
        textView.setCompoundDrawables(null, drawable, null, null);
        textView.setCompoundDrawablePadding(dp(textView.getContext(), 2));
        tint(textView);
        textView.setGravity(17);
    }

    static void applyOnly(TextView textView, int i) {
        Drawable drawable;
        if (textView == null || i == 0 || (drawable = textView.getContext().getDrawable(i)) == null) {
            return;
        }
        drawable.setBounds(0, 0, dp(textView.getContext(), 20), dp(textView.getContext(), 20));
        textView.setCompoundDrawables(null, null, null, null);
        textView.setBackground(textView.getBackground());
        textView.setCompoundDrawablesRelative(drawable, null, null, null);
        textView.setCompoundDrawablePadding(0);
        tint(textView);
        textView.setGravity(17);
    }

    private static void tint(TextView textView) {
        try {
            textView.setCompoundDrawableTintList(ColorStateList.valueOf(textView.getCurrentTextColor()));
        } catch (Throwable unused) {
        }
    }

    private static int dp(Context context, int i) {
        return Math.round(i * context.getResources().getDisplayMetrics().density);
    }
}
