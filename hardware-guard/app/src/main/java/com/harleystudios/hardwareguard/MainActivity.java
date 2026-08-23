package com.harleystudios.hardwareguard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(13, 17, 23);
    private static final int CARD = Color.rgb(20, 27, 34);
    private static final int CYAN = Color.rgb(0, 184, 240);
    private static final int TEXT = Color.rgb(238, 246, 250);
    private static final int MUTED = Color.rgb(155, 173, 183);
    private static final int GOOD = Color.rgb(64, 210, 139);
    private static final int WARN = Color.rgb(255, 180, 84);

    private PackageManager pm;
    private Spinner appSpinner;
    private TextView packageView;
    private TextView cameraView;
    private TextView micView;
    private TextView summaryView;
    private Button manageButton;
    private List<AppEntry> apps = new ArrayList<AppEntry>();
    private AppEntry selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selected != null) {
            updateStatus();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView studio = text("HARLEY'S STUDIOS", 13, CYAN, true);
        root.addView(studio);

        TextView title = text("Hardware Guard", 30, TEXT, true);
        LinearLayout.LayoutParams titleLp = lpMatchWrap();
        titleLp.topMargin = dp(4);
        root.addView(title, titleLp);

        TextView subtitle = text("No-root camera & microphone privacy controls", 15, MUTED, false);
        LinearLayout.LayoutParams subLp = lpMatchWrap();
        subLp.topMargin = dp(5);
        subLp.bottomMargin = dp(18);
        root.addView(subtitle, subLp);

        TextView badge = text("NO ROOT  •  NO SHIZUKU  •  ON-DEVICE", 12, CYAN, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(10), dp(12), dp(10));
        badge.setBackground(rounded(CARD, CYAN, 1, 14));
        LinearLayout.LayoutParams badgeLp = lpMatchWrap();
        badgeLp.bottomMargin = dp(18);
        root.addView(badge, badgeLp);

        LinearLayout selectorCard = card();
        root.addView(selectorCard, cardLp());
        selectorCard.addView(text("Choose an app", 14, TEXT, true));

        appSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerLp = lpMatchWrap();
        spinnerLp.topMargin = dp(10);
        selectorCard.addView(appSpinner, spinnerLp);

        packageView = text("", 12, MUTED, false);
        LinearLayout.LayoutParams packageLp = lpMatchWrap();
        packageLp.topMargin = dp(8);
        selectorCard.addView(packageView, packageLp);

        LinearLayout statusCard = card();
        root.addView(statusCard, cardLp());
        statusCard.addView(text("Hardware access", 18, TEXT, true));

        LinearLayout cameraRow = statusRow("CAMERA");
        cameraView = (TextView) cameraRow.getChildAt(1);
        LinearLayout.LayoutParams rowLp = lpMatchWrap();
        rowLp.topMargin = dp(14);
        statusCard.addView(cameraRow, rowLp);

        LinearLayout micRow = statusRow("MICROPHONE");
        micView = (TextView) micRow.getChildAt(1);
        LinearLayout.LayoutParams micLp = lpMatchWrap();
        micLp.topMargin = dp(12);
        statusCard.addView(micRow, micLp);

        summaryView = text("", 13, MUTED, false);
        summaryView.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams summaryLp = lpMatchWrap();
        summaryLp.topMargin = dp(14);
        statusCard.addView(summaryView, summaryLp);

        manageButton = button("Manage camera & microphone");
        manageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPermissionControls();
            }
        });
        LinearLayout.LayoutParams manageLp = lpMatchWrap();
        manageLp.topMargin = dp(18);
        root.addView(manageButton, manageLp);

        Button refresh = button("Refresh protection status");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus();
            }
        });
        LinearLayout.LayoutParams refreshLp = lpMatchWrap();
        refreshLp.topMargin = dp(10);
        root.addView(refresh, refreshLp);

        Button privacy = button("Open Android privacy controls");
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPrivacySettings();
            }
        });
        LinearLayout.LayoutParams privacyLp = lpMatchWrap();
        privacyLp.topMargin = dp(10);
        root.addView(privacy, privacyLp);

        TextView explanation = text(
                "How blocking works\n\nAndroid does not allow a normal third-party app to silently revoke another app's protected permissions. Hardware Guard takes you to Android's own permission screen, where you choose Don't allow. Android then enforces the block.\n\nFor a device-wide emergency block on Android 12+, use Android privacy controls to disable Camera access or Microphone access for all apps.",
                13, MUTED, false);
        explanation.setLineSpacing(0, 1.18f);
        explanation.setPadding(dp(14), dp(14), dp(14), dp(14));
        explanation.setBackground(rounded(CARD, Color.rgb(45, 59, 70), 1, 14));
        LinearLayout.LayoutParams explanationLp = lpMatchWrap();
        explanationLp.topMargin = dp(20);
        root.addView(explanation, explanationLp);

        TextView footer = text("Harley's Studios  •  Hardware Guard v0.1.0", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = lpMatchWrap();
        footerLp.topMargin = dp(24);
        root.addView(footer, footerLp);

        setContentView(scroll);
    }

    private void reloadApps() {
        apps.clear();
        final Map<String, AppEntry> unique = new LinkedHashMap<String, AppEntry>();

        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            CharSequence labelCs = info.loadLabel(pm);
            String label = labelCs == null ? pkg : labelCs.toString();
            unique.put(pkg, new AppEntry(label, pkg));
        }

        try {
            pm.getApplicationInfo("com.snapchat.android", 0);
            if (!unique.containsKey("com.snapchat.android")) {
                unique.put("com.snapchat.android", new AppEntry("Snapchat", "com.snapchat.android"));
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        apps.addAll(unique.values());
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.toLowerCase(Locale.US).compareTo(b.label.toLowerCase(Locale.US));
            }
        });

        if (apps.isEmpty()) {
            apps.add(new AppEntry("No visible apps", ""));
        }

        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<AppEntry>(
                this, android.R.layout.simple_spinner_item, apps);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appSpinner.setAdapter(adapter);

        int defaultIndex = 0;
        for (int i = 0; i < apps.size(); i++) {
            if ("com.snapchat.android".equals(apps.get(i).packageName)) {
                defaultIndex = i;
                break;
            }
        }

        appSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selected = apps.get(position);
                updateStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selected = null;
            }
        });
        appSpinner.setSelection(defaultIndex);
    }

    private void updateStatus() {
        if (selected == null || selected.packageName.length() == 0) {
            packageView.setText("No app selected");
            cameraView.setText("Unavailable");
            micView.setText("Unavailable");
            summaryView.setText("Hardware Guard could not find a visible launchable app.");
            manageButton.setEnabled(false);
            return;
        }

        manageButton.setEnabled(true);
        packageView.setText(selected.packageName);
        PermissionState camera = permissionState(selected.packageName, Manifest.permission.CAMERA);
        PermissionState mic = permissionState(selected.packageName, Manifest.permission.RECORD_AUDIO);
        applyState(cameraView, camera);
        applyState(micView, mic);

        if (camera == PermissionState.BLOCKED && mic == PermissionState.BLOCKED) {
            summaryView.setText("Protected: this app is currently denied both camera and microphone permission by Android.");
            summaryView.setTextColor(GOOD);
        } else if (camera == PermissionState.NOT_REQUESTED && mic == PermissionState.NOT_REQUESTED) {
            summaryView.setText("This app does not declare camera or microphone permission.");
            summaryView.setTextColor(MUTED);
        } else {
            summaryView.setText("Review any item marked ALLOWED if you want Android to block that hardware for this app.");
            summaryView.setTextColor(WARN);
        }
    }

    private PermissionState permissionState(String pkg, String permission) {
        try {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            boolean requested = false;
            if (info.requestedPermissions != null) {
                for (String p : info.requestedPermissions) {
                    if (permission.equals(p)) {
                        requested = true;
                        break;
                    }
                }
            }
            if (!requested) return PermissionState.NOT_REQUESTED;
            return pm.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
                    ? PermissionState.ALLOWED : PermissionState.BLOCKED;
        } catch (PackageManager.NameNotFoundException e) {
            return PermissionState.UNKNOWN;
        }
    }

    private void applyState(TextView view, PermissionState state) {
        if (state == PermissionState.BLOCKED) {
            view.setText("BLOCKED  •  PROTECTED");
            view.setTextColor(GOOD);
        } else if (state == PermissionState.ALLOWED) {
            view.setText("ALLOWED  •  APP CAN USE");
            view.setTextColor(WARN);
        } else if (state == PermissionState.NOT_REQUESTED) {
            view.setText("NOT REQUESTED");
            view.setTextColor(MUTED);
        } else {
            view.setText("UNKNOWN");
            view.setTextColor(MUTED);
        }
    }

    private void openPermissionControls() {
        if (selected == null || selected.packageName.length() == 0) return;

        Intent direct = new Intent("android.intent.action.MANAGE_APP_PERMISSIONS");
        direct.putExtra(Intent.EXTRA_PACKAGE_NAME, selected.packageName);
        if (direct.resolveActivity(pm) != null) {
            try {
                startActivity(direct);
                return;
            } catch (Exception ignored) {
            }
        }

        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + selected.packageName));
        try {
            startActivity(details);
            Toast.makeText(this, "Open Permissions, then Camera or Microphone, and choose Don't allow.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Android settings could not be opened on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private void openPrivacySettings() {
        Intent intent = new Intent(Settings.ACTION_PRIVACY_SETTINGS);
        if (intent.resolveActivity(pm) == null) {
            intent = new Intent(Settings.ACTION_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Android privacy settings could not be opened.", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout statusRow(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(rounded(BG, Color.rgb(45, 59, 70), 1, 12));
        row.addView(text(name, 12, MUTED, true));
        TextView status = text("Checking…", 14, TEXT, true);
        LinearLayout.LayoutParams statusLp = lpMatchWrap();
        statusLp.topMargin = dp(4);
        row.addView(status, statusLp);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(CARD, Color.rgb(45, 59, 70), 1, 16));
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = lpMatchWrap();
        lp.bottomMargin = dp(14);
        return lp;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(BG);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackground(rounded(CYAN, CYAN, 0, 12));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidthDp, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) shape.setStroke(dp(strokeWidthDp), stroke);
        return shape;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum PermissionState {
        ALLOWED,
        BLOCKED,
        NOT_REQUESTED,
        UNKNOWN
    }

    private static class AppEntry {
        final String label;
        final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
