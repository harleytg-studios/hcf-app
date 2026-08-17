package com.harleytg.forum;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HCF-styled chooser for app-initiated external intents. */
final class HcfIntentChooser {
    private HcfIntentChooser() {}

    static boolean show(Activity activity, Intent baseIntent, String title, String detail, boolean excludeSelf) {
        return showInternal(activity, baseIntent, title, detail, excludeSelf, false);
    }

    static boolean showShare(Activity activity, Intent baseIntent, String title, String detail) {
        return showSimpleShare(activity, baseIntent, title);
    }

    /**
     * Sharing is intentionally kept simple: the HCF dialog offers Copy Link or Share with…,
     * then Android's normal Sharesheet handles the actual destination app selection.
     */
    private static boolean showSimpleShare(Activity activity, Intent baseIntent, String title) {
        if (activity == null || baseIntent == null || activity.isFinishing() || activity.isDestroyed()) return false;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundResource(R.drawable.card_background);
        int pad = dp(activity, 18);
        shell.setPadding(pad, pad, pad, pad);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(logo, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));

        LinearLayout headings = new LinearLayout(activity);
        headings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hp.leftMargin = dp(activity, 12);
        headings.addView(text(activity, "Harley's Clan Forum • Share", 9, R.color.hcf_meta, true));
        headings.addView(text(activity, title == null ? "Share" : title, 20, R.color.hcf_text, true));
        TextView sub = text(activity, "Copy the link or share it with another app.", 11, R.color.hcf_muted, false);
        sub.setPadding(0, dp(activity, 3), 0, 0);
        headings.addView(sub);
        hero.addView(headings, hp);
        shell.addView(hero);

        Button copy = simpleAction(activity, "Copy Link");
        copy.setOnClickListener(v -> {
            CharSequence value = baseIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (value == null || value.toString().trim().isEmpty()) {
                Toast.makeText(activity, "No link is available to copy.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Harley's Clan Forum link", value));
                Toast.makeText(activity, "Link copied.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Throwable t) {
                Toast.makeText(activity, "Unable to copy this link.", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        ap.topMargin = dp(activity, 16);
        shell.addView(copy, ap);

        Button share = simpleAction(activity, "Share with…");
        share.setOnClickListener(v -> {
            try {
                Intent chooser = Intent.createChooser(new Intent(baseIntent), title == null ? "Share" : title);
                activity.startActivity(chooser);
                dialog.dismiss();
            } catch (Throwable t) {
                Toast.makeText(activity, "No compatible sharing app is available.", Toast.LENGTH_SHORT).show();
                AppLogger.error(activity, "system_share_chooser", t.getClass().getSimpleName());
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        sp.topMargin = dp(activity, 8);
        shell.addView(share, sp);

        Button cancel = new Button(activity);
        UiButtons.normalizeText(cancel);
        cancel.setText("Cancel");
        cancel.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackgroundResource(R.drawable.button_background);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46));
        cp.topMargin = dp(activity, 12);
        shell.addView(cancel, cp);

        dialog.setContentView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.62f;
            window.setAttributes(attrs);
            int width = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24);
            window.setLayout(Math.max(dp(activity, 280), width), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        return true;
    }

    private static Button simpleAction(Activity activity, String label) {
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText(label);
        button.setTextColor(activity.getColor(R.color.hcf_text));
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.quick_action_background);
        return button;
    }

    private static boolean showInternal(Activity activity, Intent baseIntent, String title, String detail,
                                        boolean excludeSelf, boolean curatedShare) {
        if (activity == null || baseIntent == null || activity.isFinishing() || activity.isDestroyed()) return false;
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> all;
        try {
            all = pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            all = new ArrayList<>();
        }
        List<ResolveInfo> choices = curatedShare
                ? curatedShareTargets(activity, pm, all, excludeSelf)
                : normalTargets(activity, all, excludeSelf);

        if (choices.size() == 1) {
            return launch(activity, baseIntent, choices.get(0));
        }
        if (choices.isEmpty()) {
            if (excludeSelf) {
                Toast.makeText(activity, "No compatible external app is available.", Toast.LENGTH_SHORT).show();
                return false;
            }
            try {
                activity.startActivity(baseIntent);
                return true;
            } catch (Throwable t) {
                Toast.makeText(activity, "No compatible app is available.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundResource(R.drawable.card_background);
        int pad = dp(activity, 18);
        shell.setPadding(pad, pad, pad, pad);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.htg_app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(logo, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));

        LinearLayout headings = new LinearLayout(activity);
        headings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hp.leftMargin = dp(activity, 12);

        TextView eyebrow = text(activity, "Harley's Clan Forum • App Chooser", 9, R.color.hcf_meta, true);
        headings.addView(eyebrow);
        TextView heading = text(activity, title == null ? "Open with" : title, 20, R.color.hcf_text, true);
        headings.addView(heading);
        if (detail != null && !detail.trim().isEmpty()) {
            TextView sub = text(activity, detail, 11, R.color.hcf_muted, false);
            sub.setPadding(0, dp(activity, 3), 0, 0);
            headings.addView(sub);
        }
        hero.addView(headings, hp);
        shell.addView(hero);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 12), 0, 0);

        for (ResolveInfo info : choices) {
            CharSequence labelCs;
            try { labelCs = info.loadLabel(pm); }
            catch (Throwable t) { labelCs = info.activityInfo.packageName; }
            String label = curatedShare
                    ? applicationLabel(pm, info)
                    : (labelCs == null ? info.activityInfo.packageName : labelCs.toString());

            Button row = new Button(activity);
            UiButtons.normalizeText(row);
            row.setText(label);
            row.setTextColor(activity.getColor(R.color.hcf_text));
            row.setTextSize(14f);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setBackgroundResource(R.drawable.quick_action_background);
            row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            try {
                Drawable icon = info.loadIcon(pm);
                if (icon != null) {
                    icon.setBounds(0, 0, dp(activity, 28), dp(activity, 28));
                    row.setCompoundDrawablesRelative(icon, null, null, null);
                    row.setCompoundDrawablePadding(dp(activity, 12));
                }
            } catch (Throwable ignored) {}
            row.setOnClickListener(v -> {
                if (launch(activity, baseIntent, info)) dialog.dismiss();
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 54));
            rp.bottomMargin = dp(activity, 8);
            list.addView(row, rp);
        }
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button cancel = new Button(activity);
        UiButtons.normalizeText(cancel);
        cancel.setText("Cancel");
        cancel.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        cancel.setBackgroundResource(R.drawable.button_background);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46));
        cp.topMargin = dp(activity, 4);
        shell.addView(cancel, cp);

        dialog.setContentView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.62f;
            window.setAttributes(attrs);
            int width = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24);
            int maxHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
            window.setLayout(Math.max(dp(activity, 280), width), Math.max(dp(activity, 360), maxHeight));
            window.setGravity(Gravity.CENTER);
        }
        return true;
    }


    private static List<ResolveInfo> normalTargets(Activity activity, List<ResolveInfo> all, boolean excludeSelf) {
        List<ResolveInfo> choices = new ArrayList<>();
        if (all == null) return choices;
        for (ResolveInfo info : all) {
            if (info == null || info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (excludeSelf && activity.getPackageName().equals(pkg)) continue;
            choices.add(info);
        }
        return choices;
    }

    /**
     * Share mode intentionally differs from a raw ACTION_SEND resolver list. Android may expose
     * browser, Bluetooth, download/document activities and several surfaces from the same social
     * app. For a simple forum-link share these are noisy, so show one sensible row per sharing app.
     */
    private static List<ResolveInfo> curatedShareTargets(Activity activity, PackageManager pm,
                                                          List<ResolveInfo> all, boolean excludeSelf) {
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        if (all != null) {
            for (ResolveInfo info : all) {
                if (info == null || info.activityInfo == null) continue;
                String pkg = safe(info.activityInfo.packageName);
                if (pkg.isEmpty()) continue;
                if (excludeSelf && activity.getPackageName().equals(pkg)) continue;
                if (isNoiseShareTarget(pm, info)) continue;

                ResolveInfo existing = byPackage.get(pkg);
                if (existing == null || targetScore(pm, info) > targetScore(pm, existing)) {
                    byPackage.put(pkg, info);
                }
            }
        }

        List<ResolveInfo> choices = new ArrayList<>(byPackage.values());
        Collections.sort(choices, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                int pa = sharePriority(pm, a);
                int pb = sharePriority(pm, b);
                if (pa != pb) return pa - pb;
                return applicationLabel(pm, a).compareToIgnoreCase(applicationLabel(pm, b));
            }
        });
        return choices;
    }

    private static boolean isNoiseShareTarget(PackageManager pm, ResolveInfo info) {
        String pkg = safe(info.activityInfo.packageName).toLowerCase(java.util.Locale.US);
        String activityName = safe(info.activityInfo.name).toLowerCase(java.util.Locale.US);
        String activityLabel = resolveLabel(pm, info).toLowerCase(java.util.Locale.US);
        String appLabel = applicationLabel(pm, info).toLowerCase(java.util.Locale.US);
        String combined = pkg + " " + activityName + " " + activityLabel + " " + appLabel;

        if (combined.contains("bluetooth")) return true;
        if (pkg.equals("com.android.chrome") || pkg.contains("chromium") || pkg.contains("browser")) return true;
        if (activityLabel.equals("chrome") || activityLabel.equals("browser") || activityLabel.contains("web browser")) return true;
        if (pkg.contains("documentsui") || pkg.contains("downloads") || pkg.contains("downloadprovider")) return true;
        if (activityLabel.equals("download") || activityLabel.equals("downloads") || activityLabel.equals("save to files")) return true;
        if (pkg.contains("packageinstaller") || pkg.contains("permissioncontroller") || pkg.contains("systemui")) return true;
        return false;
    }

    private static int targetScore(PackageManager pm, ResolveInfo info) {
        String activity = resolveLabel(pm, info).toLowerCase(java.util.Locale.US);
        String app = applicationLabel(pm, info).toLowerCase(java.util.Locale.US);
        int score = activity.equals(app) ? 100 : 0;
        if (activity.contains("share") || activity.contains("send") || activity.contains("message")) score += 30;
        if (activity.contains("feed") || activity.contains("post")) score += 15;
        if (activity.contains("story") || activity.contains("group")) score -= 10;
        return score;
    }

    private static int sharePriority(PackageManager pm, ResolveInfo info) {
        String s = (safe(info.activityInfo.packageName) + " " + applicationLabel(pm, info))
                .toLowerCase(java.util.Locale.US);
        if (s.contains("message") || s.contains("messenger") || s.contains("whatsapp")
                || s.contains("signal") || s.contains("telegram") || s.contains("discord")
                || s.contains("gmail") || s.contains("mail") || s.contains("email")) return 0;
        if (s.contains("facebook") || s.contains("instagram") || s.contains("twitter")
                || s.contains("reddit") || s.contains("slack") || s.contains("snapchat")
                || s.contains("tiktok")) return 1;
        if (s.contains("nearby") || s.contains("quick share")) return 2;
        return 3;
    }

    private static String applicationLabel(PackageManager pm, ResolveInfo info) {
        try {
            if (info != null && info.activityInfo != null && info.activityInfo.applicationInfo != null) {
                CharSequence label = info.activityInfo.applicationInfo.loadLabel(pm);
                if (label != null && !label.toString().trim().isEmpty()) return label.toString().trim();
            }
        } catch (Throwable ignored) {}
        return resolveLabel(pm, info);
    }

    private static String resolveLabel(PackageManager pm, ResolveInfo info) {
        try {
            CharSequence label = info == null ? null : info.loadLabel(pm);
            if (label != null && !label.toString().trim().isEmpty()) return label.toString().trim();
        } catch (Throwable ignored) {}
        return info != null && info.activityInfo != null ? safe(info.activityInfo.packageName) : "App";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean launch(Activity activity, Intent base, ResolveInfo info) {
        try {
            Intent explicit = new Intent(base);
            explicit.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            activity.startActivity(explicit);
            return true;
        } catch (Throwable t) {
            Toast.makeText(activity, "That app could not open this item.", Toast.LENGTH_SHORT).show();
            AppLogger.error(activity, "custom_intent_chooser", t.getClass().getSimpleName());
            return false;
        }
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(activity.getColor(color));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
