package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.util.Locale;

/* loaded from: classes.dex */
final class HcfIntentChooser {
    private HcfIntentChooser() {
    }

    static boolean show(Activity activity, Intent intent, String str, String str2, boolean z) {
        return showInternal(activity, intent, str, str2, z, false);
    }

    static boolean showShare(Activity activity, Intent intent, String str, String str2) {
        return showSimpleShare(activity, intent, str);
    }

    private static boolean showSimpleShare(final Activity activity, final Intent intent, final String str) {
        if (activity == null || intent == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        dialog.setCancelable(true);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundResource(R.drawable.card_background);
        int dp = dp(activity, 18);
        linearLayout.setPadding(dp, dp, dp, dp);
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        ImageView imageView = new ImageView(activity);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout2.addView(imageView, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));
        LinearLayout linearLayout3 = new LinearLayout(activity);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(activity, 12);
        linearLayout3.addView(text(activity, "Harley's Clan Forum • Share", 9, R.color.hcf_meta, true));
        linearLayout3.addView(text(activity, str == null ? "Share" : str, 20, R.color.hcf_text, true));
        TextView text = text(activity, "Copy the link or share it with another app.", 11, R.color.hcf_muted, false);
        text.setPadding(0, dp(activity, 3), 0, 0);
        linearLayout3.addView(text);
        linearLayout2.addView(linearLayout3, layoutParams);
        linearLayout.addView(linearLayout2);
        Button simpleAction = simpleAction(activity, "Copy Link");
        simpleAction.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.HcfIntentChooser$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                HcfIntentChooser.lambda$showSimpleShare$0(intent, activity, dialog, view);
            }
        });
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        layoutParams2.topMargin = dp(activity, 16);
        linearLayout.addView(simpleAction, layoutParams2);
        Button simpleAction2 = simpleAction(activity, "Share with…");
        simpleAction2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.HcfIntentChooser$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                HcfIntentChooser.lambda$showSimpleShare$1(intent, str, activity, dialog, view);
            }
        });
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        layoutParams3.topMargin = dp(activity, 8);
        linearLayout.addView(simpleAction2, layoutParams3);
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText("Cancel");
        button.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button.setGravity(17);
        button.setBackgroundResource(R.drawable.button_background);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.HcfIntentChooser$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        layoutParams4.topMargin = dp(activity, 12);
        linearLayout.addView(button, layoutParams4);
        dialog.setContentView(linearLayout);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(2);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
            window.setLayout(Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)), -2);
            window.setGravity(17);
        }
        return true;
    }

    static /* synthetic */ void lambda$showSimpleShare$0(Intent intent, Activity activity, Dialog dialog, View view) {
        CharSequence charSequenceExtra = intent.getCharSequenceExtra("android.intent.extra.TEXT");
        if (charSequenceExtra == null || charSequenceExtra.toString().trim().isEmpty()) {
            Toast.makeText(activity, "No link is available to copy.", 0).show();
            return;
        }
        try {
            ClipboardManager clipboardManager = (ClipboardManager) activity.getSystemService("clipboard");
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Harley's Clan Forum link", charSequenceExtra));
            }
            Toast.makeText(activity, "Link copied.", 0).show();
            dialog.dismiss();
        } catch (Throwable unused) {
            Toast.makeText(activity, "Unable to copy this link.", 0).show();
        }
    }

    static /* synthetic */ void lambda$showSimpleShare$1(Intent intent, String str, Activity activity, Dialog dialog, View view) {
        try {
            Intent intent2 = new Intent(intent);
            if (str == null) {
                str = "Share";
            }
            activity.startActivity(Intent.createChooser(intent2, str));
            dialog.dismiss();
        } catch (Throwable th) {
            Toast.makeText(activity, "No compatible sharing app is available.", 0).show();
            AppLogger.error(activity, "system_share_chooser", th.getClass().getSimpleName());
        }
    }

    private static Button simpleAction(Activity activity, String str) {
        Button button = new Button(activity);
        UiButtons.normalizeText(button);
        button.setText(str);
        button.setTextColor(activity.getColor(R.color.hcf_text));
        button.setTextSize(14.0f);
        button.setGravity(17);
        button.setBackgroundResource(R.drawable.quick_action_background);
        return button;
    }

    private static boolean showInternal(final Activity activity, final Intent intent, String str, String str2, boolean z, boolean z2) {
        List<ResolveInfo> arrayList;
        List<ResolveInfo> normalTargets;
        CharSequence charSequence;
        String charSequence2;
        if (activity == null || intent == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        PackageManager packageManager = activity.getPackageManager();
        try {
            arrayList = packageManager.queryIntentActivities(intent, 65536);
        } catch (Throwable unused) {
            arrayList = new ArrayList<>();
        }
        if (z2) {
            normalTargets = curatedShareTargets(activity, packageManager, arrayList, z);
        } else {
            normalTargets = normalTargets(activity, arrayList, z);
        }
        if (normalTargets.size() == 1) {
            return launch(activity, intent, normalTargets.get(0));
        }
        if (normalTargets.isEmpty()) {
            if (z) {
                Toast.makeText(activity, "No compatible external app is available.", 0).show();
                return false;
            }
            try {
                activity.startActivity(intent);
                return true;
            } catch (Throwable unused2) {
                Toast.makeText(activity, "No compatible app is available.", 0).show();
                return false;
            }
        }
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(1);
        dialog.setCancelable(true);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundResource(R.drawable.card_background);
        int dp = dp(activity, 18);
        linearLayout.setPadding(dp, dp, dp, dp);
        LinearLayout linearLayout2 = new LinearLayout(activity);
        linearLayout2.setOrientation(0);
        linearLayout2.setGravity(16);
        ImageView imageView = new ImageView(activity);
        imageView.setImageResource(R.drawable.htg_app_logo);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        linearLayout2.addView(imageView, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));
        LinearLayout linearLayout3 = new LinearLayout(activity);
        linearLayout3.setOrientation(1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = dp(activity, 12);
        linearLayout3.addView(text(activity, "Harley's Clan Forum • App Chooser", 9, R.color.hcf_meta, true));
        String str3 = str == null ? "Open with" : str;
        int i = R.color.hcf_text;
        linearLayout3.addView(text(activity, str3, 20, R.color.hcf_text, true));
        if (str2 != null && !str2.trim().isEmpty()) {
            TextView text = text(activity, str2, 11, R.color.hcf_muted, false);
            text.setPadding(0, dp(activity, 3), 0, 0);
            linearLayout3.addView(text);
        }
        linearLayout2.addView(linearLayout3, layoutParams);
        linearLayout.addView(linearLayout2);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        LinearLayout linearLayout4 = new LinearLayout(activity);
        linearLayout4.setOrientation(1);
        linearLayout4.setPadding(0, dp(activity, 12), 0, 0);
        for (final ResolveInfo resolveInfo : normalTargets) {
            try {
                charSequence = resolveInfo.loadLabel(packageManager);
            } catch (Throwable unused3) {
                charSequence = resolveInfo.activityInfo.packageName;
            }
            if (z2) {
                charSequence2 = applicationLabel(packageManager, resolveInfo);
            } else {
                charSequence2 = charSequence == null ? resolveInfo.activityInfo.packageName : charSequence.toString();
            }
            Button button = new Button(activity);
            UiButtons.normalizeText(button);
            button.setText(charSequence2);
            button.setTextColor(activity.getColor(i));
            button.setTextSize(14.0f);
            button.setGravity(8388627);
            button.setBackgroundResource(R.drawable.quick_action_background);
            button.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            try {
                Drawable loadIcon = resolveInfo.loadIcon(packageManager);
                if (loadIcon != null) {
                    loadIcon.setBounds(0, 0, dp(activity, 28), dp(activity, 28));
                    button.setCompoundDrawablesRelative(loadIcon, null, null, null);
                    button.setCompoundDrawablePadding(dp(activity, 12));
                }
            } catch (Throwable unused4) {
            }
            button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.HcfIntentChooser$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    HcfIntentChooser.lambda$showInternal$3(activity, intent, resolveInfo, dialog, view);
                }
            });
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(activity, 54));
            layoutParams2.bottomMargin = dp(activity, 8);
            linearLayout4.addView(button, layoutParams2);
            i = R.color.hcf_text;
        }
        scrollView.addView(linearLayout4, new FrameLayout.LayoutParams(-1, -2));
        linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        Button button2 = new Button(activity);
        UiButtons.normalizeText(button2);
        button2.setText("Cancel");
        button2.setTextColor(activity.getColor(R.color.hcf_cyan_bright));
        button2.setBackgroundResource(R.drawable.button_background);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.HcfIntentChooser$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, dp(activity, 46));
        layoutParams3.topMargin = dp(activity, 4);
        linearLayout.addView(button2, layoutParams3);
        dialog.setContentView(linearLayout);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return true;
        }
        window.setBackgroundDrawable(new ColorDrawable(0));
        window.addFlags(2);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.62f;
        window.setAttributes(attributes);
        window.setLayout(Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)), Math.max(dp(activity, 360), Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.78f)));
        window.setGravity(17);
        return true;
    }

    static /* synthetic */ void lambda$showInternal$3(Activity activity, Intent intent, ResolveInfo resolveInfo, Dialog dialog, View view) {
        if (launch(activity, intent, resolveInfo)) {
            dialog.dismiss();
        }
    }

    private static List<ResolveInfo> normalTargets(Activity activity, List<ResolveInfo> list, boolean z) {
        ArrayList arrayList = new ArrayList();
        if (list == null) {
            return arrayList;
        }
        for (ResolveInfo resolveInfo : list) {
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                String str = resolveInfo.activityInfo.packageName;
                if (!z || !activity.getPackageName().equals(str)) {
                    arrayList.add(resolveInfo);
                }
            }
        }
        return arrayList;
    }

    private static List<ResolveInfo> curatedShareTargets(Activity activity, final PackageManager packageManager, List<ResolveInfo> list, boolean z) {
        ResolveInfo resolveInfo;
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        if (list != null) {
            for (ResolveInfo resolveInfo2 : list) {
                if (resolveInfo2 != null && resolveInfo2.activityInfo != null) {
                    String safe = safe(resolveInfo2.activityInfo.packageName);
                    if (!safe.isEmpty() && (!z || !activity.getPackageName().equals(safe))) {
                        if (!isNoiseShareTarget(packageManager, resolveInfo2) && ((resolveInfo = (ResolveInfo) linkedHashMap.get(safe)) == null || targetScore(packageManager, resolveInfo2) > targetScore(packageManager, resolveInfo))) {
                            linkedHashMap.put(safe, resolveInfo2);
                        }
                    }
                }
            }
        }
        ArrayList arrayList = new ArrayList(linkedHashMap.values());
        Collections.sort(arrayList, new Comparator<ResolveInfo>() { // from class: com.harleytg.forum.dev.HcfIntentChooser.1
            @Override // java.util.Comparator
            public int compare(ResolveInfo resolveInfo3, ResolveInfo resolveInfo4) {
                int sharePriority = HcfIntentChooser.sharePriority(packageManager, resolveInfo3);
                int sharePriority2 = HcfIntentChooser.sharePriority(packageManager, resolveInfo4);
                return sharePriority != sharePriority2 ? sharePriority - sharePriority2 : HcfIntentChooser.applicationLabel(packageManager, resolveInfo3).compareToIgnoreCase(HcfIntentChooser.applicationLabel(packageManager, resolveInfo4));
            }
        });
        return arrayList;
    }

    private static boolean isNoiseShareTarget(PackageManager packageManager, ResolveInfo resolveInfo) {
        String lowerCase = safe(resolveInfo.activityInfo.packageName).toLowerCase(Locale.US);
        String lowerCase2 = safe(resolveInfo.activityInfo.name).toLowerCase(Locale.US);
        String lowerCase3 = resolveLabel(packageManager, resolveInfo).toLowerCase(Locale.US);
        String lowerCase4 = applicationLabel(packageManager, resolveInfo).toLowerCase(Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append(lowerCase);
        sb.append(" ");
        sb.append(lowerCase2);
        sb.append(" ");
        sb.append(lowerCase3);
        sb.append(" ");
        sb.append(lowerCase4);
        return sb.toString().contains("bluetooth") || lowerCase.equals("com.android.chrome") || lowerCase.contains("chromium") || lowerCase.contains("browser") || lowerCase3.equals("chrome") || lowerCase3.equals("browser") || lowerCase3.contains("web browser") || lowerCase.contains("documentsui") || lowerCase.contains("downloads") || lowerCase.contains("downloadprovider") || lowerCase3.equals("download") || lowerCase3.equals("downloads") || lowerCase3.equals("save to files") || lowerCase.contains("packageinstaller") || lowerCase.contains("permissioncontroller") || lowerCase.contains("systemui");
    }

    private static int targetScore(PackageManager packageManager, ResolveInfo resolveInfo) {
        String lowerCase = resolveLabel(packageManager, resolveInfo).toLowerCase(Locale.US);
        int i = lowerCase.equals(applicationLabel(packageManager, resolveInfo).toLowerCase(Locale.US)) ? 100 : 0;
        if (lowerCase.contains("share") || lowerCase.contains("send") || lowerCase.contains("message")) {
            i += 30;
        }
        if (lowerCase.contains("feed") || lowerCase.contains("post")) {
            i += 15;
        }
        return (lowerCase.contains("story") || lowerCase.contains("group")) ? i - 10 : i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int sharePriority(PackageManager packageManager, ResolveInfo resolveInfo) {
        String lowerCase = (safe(resolveInfo.activityInfo.packageName) + " " + applicationLabel(packageManager, resolveInfo)).toLowerCase(Locale.US);
        if (lowerCase.contains("message") || lowerCase.contains("messenger") || lowerCase.contains("whatsapp") || lowerCase.contains("signal") || lowerCase.contains("telegram") || lowerCase.contains("discord") || lowerCase.contains("gmail") || lowerCase.contains("mail") || lowerCase.contains("email")) {
            return 0;
        }
        if (lowerCase.contains("facebook") || lowerCase.contains("instagram") || lowerCase.contains("twitter") || lowerCase.contains("reddit") || lowerCase.contains("slack") || lowerCase.contains("snapchat") || lowerCase.contains("tiktok")) {
            return 1;
        }
        return (lowerCase.contains("nearby") || lowerCase.contains("quick share")) ? 2 : 3;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String applicationLabel(PackageManager packageManager, ResolveInfo resolveInfo) {
        CharSequence loadLabel;
        if (resolveInfo != null) {
            try {
                if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.applicationInfo != null && (loadLabel = resolveInfo.activityInfo.applicationInfo.loadLabel(packageManager)) != null && !loadLabel.toString().trim().isEmpty()) {
                    return loadLabel.toString().trim();
                }
            } catch (Throwable unused) {
            }
        }
        return resolveLabel(packageManager, resolveInfo);
    }

    private static String resolveLabel(PackageManager packageManager, ResolveInfo resolveInfo) {
        CharSequence loadLabel = null;
        if (resolveInfo == null) {
            loadLabel = null;
        } else {
            try {
                loadLabel = resolveInfo.loadLabel(packageManager);
            } catch (Throwable unused) {
            }
        }
        if (loadLabel != null && !loadLabel.toString().trim().isEmpty()) {
            return loadLabel.toString().trim();
        }
        return (resolveInfo == null || resolveInfo.activityInfo == null) ? "App" : safe(resolveInfo.activityInfo.packageName);
    }

    private static String safe(String str) {
        return str == null ? "" : str.trim();
    }

    private static boolean launch(Activity activity, Intent intent, ResolveInfo resolveInfo) {
        try {
            Intent intent2 = new Intent(intent);
            intent2.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            activity.startActivity(intent2);
            return true;
        } catch (Throwable th) {
            Toast.makeText(activity, "That app could not open this item.", 0).show();
            AppLogger.error(activity, "custom_intent_chooser", th.getClass().getSimpleName());
            return false;
        }
    }

    private static TextView text(Activity activity, String str, int i, int i2, boolean z) {
        TextView textView = new TextView(activity);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(activity.getColor(i2));
        if (z) {
            textView.setTypeface(null, 1);
        }
        return textView;
    }

    private static int dp(Activity activity, int i) {
        return Math.round(i * activity.getResources().getDisplayMetrics().density);
    }
}
