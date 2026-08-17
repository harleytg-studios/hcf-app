package com.harleytg.forum;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.TextView;

import java.util.Locale;

/**
 * Font Awesome Solid-style native icon mapping.
 *
 * The Android shell uses vector drawables instead of bundling an icon font so
 * icons stay crisp, themeable, and available offline without shipping font files.
 */
final class FaIcons {
    private FaIcons() {}

    static int forLabel(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.US);
        if (s.contains("account security") || s.contains("security") || s.contains("safe link")) return R.drawable.fa_shield;
        if (s.contains("home")) return R.drawable.fa_house;
        if (s.contains("backup") || s.contains("primary") || s.contains("switch host") || s.contains("failover")) return R.drawable.fa_right_left;
        if (s.contains("browser") || s.contains("externally") || s.contains("open link")) return R.drawable.fa_arrow_up_right_from_square;
        if (s.contains("share")) return R.drawable.fa_share_nodes;
        if (s.contains("notification") || s.contains("alert") || s.contains("mention") || s.contains("reply")) return R.drawable.fa_bell;
        if (s.contains("setting") || s.contains("theme") || s.contains("appearance") || s.contains("permission")) return R.drawable.fa_gear;
        if (s.contains("log") || s.contains("diagnostic") || s.contains("report") || s.contains("history") || s.contains("details")) return R.drawable.fa_list;
        if (s.contains("support") || s.contains("email") || s.contains("contact")) return R.drawable.fa_envelope;
        if (s.contains("copy")) return R.drawable.fa_copy;
        if (s.contains("retry") || s.contains("refresh") || s.contains("sync") || s.contains("check for updates")) return R.drawable.fa_rotate_right;
        if (s.contains("back")) return R.drawable.fa_arrow_left;
        if (s.contains("create") || s.contains("post") || s.contains("new discussion")) return R.drawable.fa_plus;
        if (s.contains("identity") || s.contains("profile") || s.contains("account")) return R.drawable.fa_user;
        if (s.contains("connection") || s.contains("server") || s.contains("forum") || s.contains("cookie") || s.contains("site data") || s.contains("link")) return R.drawable.fa_globe;
        if (s.contains("install") || s.contains("download") || s.contains("update")) return R.drawable.fa_download;
        if (s.contains("error") || s.contains("recovery") || s.contains("crash")) return R.drawable.fa_bug;
        if (s.contains("about") || s.contains("what's new") || s.contains("release")) return R.drawable.fa_circle_info;
        return R.drawable.fa_circle_info;
    }

    static void applyStart(TextView view, String label) {
        applyStart(view, forLabel(label));
    }

    static void applyStart(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 18), dp(view.getContext(), 18));
        view.setCompoundDrawablesRelative(d, null, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 10));
        tint(view);
    }

    static void applyTop(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 19), dp(view.getContext(), 19));
        view.setCompoundDrawables(null, d, null, null);
        view.setCompoundDrawablePadding(dp(view.getContext(), 2));
        tint(view);
        view.setGravity(Gravity.CENTER);
    }

    static void applyOnly(TextView view, int drawableRes) {
        if (view == null || drawableRes == 0) return;
        Drawable d = view.getContext().getDrawable(drawableRes);
        if (d == null) return;
        d.setBounds(0, 0, dp(view.getContext(), 20), dp(view.getContext(), 20));
        view.setCompoundDrawables(null, null, null, null);
        view.setBackground(view.getBackground());
        view.setCompoundDrawablesRelative(d, null, null, null);
        view.setCompoundDrawablePadding(0);
        tint(view);
        view.setGravity(Gravity.CENTER);
    }

    private static void tint(TextView view) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                view.setCompoundDrawableTintList(ColorStateList.valueOf(view.getCurrentTextColor()));
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
