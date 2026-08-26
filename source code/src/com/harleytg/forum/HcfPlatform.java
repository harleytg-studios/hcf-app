package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;


// ---- Consolidated from HcfDesktopMode.java ----
/**
 * Adaptive HCF window mode for phones, tablets, Samsung DeX and Android desktop/freeform.
 *
 * This intentionally uses window width instead of Samsung-specific SDKs. A DeX window can
 * be resized from phone-sized to full screen, so HCF follows the actual usable window:
 *   Phone   < 600dp
 *   Tablet  600-839dp
 *   Desktop >= 840dp
 *
 * Desktop mode replaces the phone hamburger with a persistent HCF navigation rail, keeps
 * the WebView in the remaining workspace, expands native chrome for pointer/keyboard use,
 * and switches live as the window is resized without needing a third-party dependency.
 */
public final class HcfPlatform {
    static final int TABLET_MIN_DP = 600;
    static final int DESKTOP_MIN_DP = 840;
    private static final int DESKTOP_RAIL_DP = 220;
    private static final String RAIL_TAG = "hcf_desktop_nav_rail";
    private static final Map<HcfForum.MainActivity, Controller> CONTROLLERS =
            new WeakHashMap<>();
    private static boolean installed;

    private HcfPlatform() {}

    enum Mode {
        PHONE("Phone"),
        TABLET("Tablet"),
        DESKTOP("Desktop / DeX");

        final String label;
        Mode(String label) { this.label = label; }
    }

    private static synchronized void install(Context context) {
        if (installed || context == null) return;
        Context app = context.getApplicationContext();
        if (!(app instanceof Application)) return;
        installed = true;

        ((Application) app).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {
                        if (activity instanceof HcfForum.MainActivity) {
                            attach((HcfForum.MainActivity) activity);
                        }
                    }

                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (activity instanceof HcfForum.MainActivity) {
                            Controller controller = attach((HcfForum.MainActivity) activity);
                            controller.apply(true);
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {}
                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override public void onActivityDestroyed(Activity activity) {
                        if (!(activity instanceof HcfForum.MainActivity)) return;
                        Controller controller = CONTROLLERS.remove(activity);
                        if (controller != null) controller.detach();
                    }
                }
        );
    }

    private static synchronized Controller attach(HcfForum.MainActivity activity) {
        Controller existing = CONTROLLERS.get(activity);
        if (existing != null) return existing;
        Controller controller = new Controller(activity);
        CONTROLLERS.put(activity, controller);
        controller.attach();
        return controller;
    }

    private static final class Controller implements View.OnLayoutChangeListener, View.OnKeyListener {
        private final HcfForum.MainActivity activity;
        private final float density;
        private final SharedPreferences prefs;
        private FrameLayout root;
        private View mainShell;
        private LinearLayout rail;
        private WebView webView;
        private View drawerPanel;
        private View drawerScrim;
        private View drawerButtonFrame;
        private View topAppBar;
        private ImageView appHeaderLogo;
        private TextView appHeaderTitle;
        private TextView appHeaderSubtitle;
        private View notificationButtonFrame;
        private View urlBar;
        private View urlBarInner;
        private View urlBackButton;
        private View reloadButton;
        private View copyUrlButton;
        private View urlHomeButton;
        private TextView secureForumLabel;
        private EditText currentUrlText;
        private TextView hostBadge;
        private View bottomNav;
        private View errorShell;
        private Button railProfile;
        private TextView railMode;
        private Mode mode;
        private int lastWidthDp = -1;
        private boolean applying;

        Controller(HcfForum.MainActivity activity) {
            this.activity = activity;
            this.density = activity.getResources().getDisplayMetrics().density;
            this.prefs = activity.getSharedPreferences(AppPrefs.FILE, 0);
        }

        void attach() {
            View found = activity.findViewById(R.id.rootFrame);
            if (!(found instanceof FrameLayout)) return;
            root = (FrameLayout) found;
            mainShell = activity.findViewById(R.id.mainShell);
            webView = activity.findViewById(R.id.webView);
            drawerPanel = activity.findViewById(R.id.drawerPanel);
            drawerScrim = activity.findViewById(R.id.drawerScrim);
            drawerButtonFrame = activity.findViewById(R.id.drawerButtonFrame);
            topAppBar = activity.findViewById(R.id.topAppBar);
            appHeaderLogo = activity.findViewById(R.id.appHeaderLogo);
            appHeaderTitle = activity.findViewById(R.id.appHeaderTitle);
            appHeaderSubtitle = activity.findViewById(R.id.appHeaderSubtitle);
            notificationButtonFrame = activity.findViewById(R.id.notificationButtonFrame);
            urlBar = activity.findViewById(R.id.urlBar);
            urlBarInner = activity.findViewById(R.id.urlBarInner);
            urlBackButton = activity.findViewById(R.id.urlBackButton);
            reloadButton = activity.findViewById(R.id.reloadButton);
            copyUrlButton = activity.findViewById(R.id.copyUrlButton);
            urlHomeButton = activity.findViewById(R.id.urlHomeButton);
            secureForumLabel = activity.findViewById(R.id.secureForumLabel);
            currentUrlText = activity.findViewById(R.id.currentUrlText);
            hostBadge = activity.findViewById(R.id.hostBadge);
            bottomNav = activity.findViewById(R.id.bottomNav);
            errorShell = activity.findViewById(R.id.errorShell);

            root.addOnLayoutChangeListener(this);
            if (webView != null) {
                webView.setOnKeyListener(this);
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
            }
            root.post(new Runnable() {
                @Override public void run() { apply(true); }
            });
        }

        void detach() {
            if (root != null) root.removeOnLayoutChangeListener(this);
            if (webView != null) webView.setOnKeyListener(null);
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int widthDp = pxToDp(Math.max(0, right - left));
            if (widthDp != lastWidthDp) apply(false);
        }

        private Mode chooseMode(int widthDp) {
            if (widthDp >= DESKTOP_MIN_DP) return Mode.DESKTOP;
            if (widthDp >= TABLET_MIN_DP) return Mode.TABLET;
            return Mode.PHONE;
        }

        void apply(boolean force) {
            if (applying || activity.isFinishing() || activity.isDestroyed() || root == null) return;
            applying = true;
            try {
                int widthPx = root.getWidth();
                int widthDp = widthPx > 0
                        ? pxToDp(widthPx)
                        : activity.getResources().getConfiguration().screenWidthDp;
                if (widthDp <= 0) widthDp = 360;
                Mode next = chooseMode(widthDp);
                if (!force && next == mode && widthDp == lastWidthDp) return;

                Mode old = mode;
                mode = next;
                lastWidthDp = widthDp;

                if (next == Mode.DESKTOP) {
                    applyDesktop(widthDp);
                } else if (next == Mode.TABLET) {
                    applyTablet(widthDp);
                } else {
                    applyPhone(widthDp);
                }
                refreshRailIdentity();
                configureWebView(next);

                prefs.edit().putString("hcf_window_mode", next.label).apply();
                if (old != next) {
                    AppLogger.info(activity, "window_mode",
                            next.label + " | width_dp=" + widthDp);
                }
            } catch (Throwable error) {
                AppLogger.warn(activity, "desktop_mode",
                        error.getClass().getSimpleName());
            } finally {
                applying = false;
            }
        }

        private void applyDesktop(int widthDp) {
            ensureRail();
            if (rail != null) rail.setVisibility(View.VISIBLE);
            shiftMainShell(DESKTOP_RAIL_DP);
            setVisible(drawerButtonFrame, false);
            setVisible(drawerPanel, false);
            setVisible(drawerScrim, false);
            if (bottomNav != null) bottomNav.setVisibility(View.GONE);

            setHeight(topAppBar, 64);
            setSquare(appHeaderLogo, 48);
            setTextSp(appHeaderTitle, 18f);
            setTextSp(appHeaderSubtitle, 12f);
            setSquare(notificationButtonFrame, 46);

            setHeight(urlBar, 56);
            setHeight(urlBarInner, 46);
            setSquare(urlBackButton, 44);
            setSquare(reloadButton, 44);
            setSquare(copyUrlButton, 44);
            setSquare(urlHomeButton, 44);
            setTextSp(currentUrlText, 13f);
            setTextSp(secureForumLabel, 9f);
            setTextSp(hostBadge, 9f);

            setErrorShellWidth(Math.min(760, Math.max(520, widthDp - DESKTOP_RAIL_DP - 64)));
            setDrawerWidth(360);
            if (railMode != null) railMode.setText("DESKTOP / DEX  •  " + widthDp + "dp");
        }

        private void applyTablet(int widthDp) {
            removeRailFromWorkspace();
            setVisible(drawerButtonFrame, true);
            restoreBottomNavPreference();

            setHeight(topAppBar, 60);
            setSquare(appHeaderLogo, 46);
            setTextSp(appHeaderTitle, 17f);
            setTextSp(appHeaderSubtitle, 11f);
            setSquare(notificationButtonFrame, 44);

            setHeight(urlBar, 52);
            setHeight(urlBarInner, 42);
            setSquare(urlBackButton, 40);
            setSquare(reloadButton, 40);
            setSquare(copyUrlButton, 40);
            setSquare(urlHomeButton, 40);
            setTextSp(currentUrlText, 12f);
            setTextSp(secureForumLabel, 8f);
            setTextSp(hostBadge, 8f);

            setErrorShellWidth(-1);
            setDrawerWidth(Math.min(340, Math.max(292, widthDp - 36)));
        }

        private void applyPhone(int widthDp) {
            removeRailFromWorkspace();
            setVisible(drawerButtonFrame, true);
            restoreBottomNavPreference();

            setHeightPx(topAppBar, activity.getResources().getDimensionPixelSize(R.dimen.app_header_height));
            setSquarePx(appHeaderLogo, activity.getResources().getDimensionPixelSize(R.dimen.app_header_logo));
            setTextSp(appHeaderTitle, 16f);
            setTextSp(appHeaderSubtitle, 11f);
            setSquarePx(notificationButtonFrame,
                    activity.getResources().getDimensionPixelSize(R.dimen.app_header_button));

            setHeightPx(urlBar, activity.getResources().getDimensionPixelSize(R.dimen.url_bar_height));
            setHeightPx(urlBarInner,
                    activity.getResources().getDimensionPixelSize(R.dimen.url_bar_inner_height));
            int reloadSize = activity.getResources().getDimensionPixelSize(R.dimen.url_reload_button);
            setSquarePx(urlBackButton, reloadSize);
            setSquarePx(reloadButton, reloadSize);
            setSquarePx(urlHomeButton, reloadSize);
            setSquarePx(copyUrlButton,
                    activity.getResources().getDimensionPixelSize(R.dimen.url_copy_button));
            setTextSp(currentUrlText, 11f);
            setTextSp(secureForumLabel, 8f);
            setTextSp(hostBadge, 8f);

            setErrorShellWidth(-1);
            setDrawerWidth(Math.min(292, Math.max(248, widthDp - 24)));
        }

        private void ensureRail() {
            if (root == null) return;
            View existing = root.findViewWithTag(RAIL_TAG);
            if (existing instanceof LinearLayout) {
                rail = (LinearLayout) existing;
                return;
            }

            rail = new LinearLayout(activity);
            rail.setTag(RAIL_TAG);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setPadding(dp(10), dp(12), dp(10), dp(10));
            rail.setBackgroundResource(R.drawable.drawer_background);
            rail.setElevation(dp(18));

            LinearLayout brand = new LinearLayout(activity);
            brand.setOrientation(LinearLayout.HORIZONTAL);
            brand.setGravity(Gravity.CENTER_VERTICAL);
            brand.setPadding(dp(4), 0, dp(4), dp(10));

            ImageView logo = new ImageView(activity);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            brand.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout brandText = new LinearLayout(activity);
            brandText.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams brandTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
            brandTextLp.leftMargin = dp(9);
            brand.addView(brandText, brandTextLp);

            TextView title = railText("HCF Desktop", 15f, R.color.hcf_text, true);
            brandText.addView(title);
            TextView sub = railText("Harley's Clan Forum [Beta]", 9f, R.color.hcf_cyan, false);
            brandText.addView(sub);
            rail.addView(brand, new LinearLayout.LayoutParams(-1, -2));

            View divider = new View(activity);
            divider.setBackgroundColor(activity.getColor(R.color.hcf_divider));
            rail.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout nav = new LinearLayout(activity);
            nav.setOrientation(LinearLayout.VERTICAL);
            nav.setPadding(0, dp(10), 0, dp(10));
            scroll.addView(nav, new ScrollView.LayoutParams(-1, -2));
            rail.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            nav.addView(sectionLabel("FORUM"));
            nav.addView(railButton("Home", R.drawable.fa_house, new View.OnClickListener() {
                @Override public void onClick(View v) { loadForumPath("/"); }
            }));
            nav.addView(railButton("Browse", R.drawable.fa_list, new View.OnClickListener() {
                @Override public void onClick(View v) { loadForumPath("/all"); }
            }));
            nav.addView(railButton("New Post", R.drawable.fa_plus, new View.OnClickListener() {
                @Override public void onClick(View v) { loadForumPath("/compose"); }
            }));
            nav.addView(railButton("Alerts", R.drawable.fa_bell, new View.OnClickListener() {
                @Override public void onClick(View v) { loadForumPath("/notifications"); }
            }));
            railProfile = railButton("Profile", R.drawable.fa_user, new View.OnClickListener() {
                @Override public void onClick(View v) { openProfile(); }
            });
            nav.addView(railProfile);

            TextView appLabel = sectionLabel("APP");
            LinearLayout.LayoutParams appLabelLp = new LinearLayout.LayoutParams(-1, -2);
            appLabelLp.topMargin = dp(12);
            appLabel.setLayoutParams(appLabelLp);
            nav.addView(appLabel);
            nav.addView(railButton("App Settings", R.drawable.fa_gear, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    activity.startActivity(new Intent(activity, HcfSubActivities.SettingsActivity.class));
                }
            }));
            nav.addView(railButton("Contact Support", R.drawable.fa_envelope, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    activity.startActivity(new Intent(activity, HcfSubActivities.SupportContactActivity.class));
                }
            }));

            railMode = railText("DESKTOP / DEX", 9f, R.color.hcf_meta, true);
            railMode.setGravity(Gravity.CENTER);
            railMode.setPadding(dp(4), dp(8), dp(4), dp(4));
            rail.addView(railMode, new LinearLayout.LayoutParams(-1, -2));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(DESKTOP_RAIL_DP), -1);
            lp.gravity = Gravity.START;
            root.addView(rail, lp);
        }

        private TextView sectionLabel(String value) {
            TextView label = railText(value, 9f, R.color.hcf_muted, true);
            label.setPadding(dp(8), dp(5), dp(8), dp(5));
            return label;
        }

        private Button railButton(String label, int icon, View.OnClickListener listener) {
            Button button = new Button(activity);
            button.setText(label);
            button.setAllCaps(false);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            button.setTextColor(activity.getColor(R.color.hcf_text));
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setPadding(dp(13), 0, dp(10), 0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setBackgroundResource(R.drawable.drawer_item_background);
            button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            button.setCompoundDrawableTintList(ColorStateList.valueOf(
                    activity.getColor(R.color.hcf_cyan_bright)));
            button.setCompoundDrawablePadding(dp(10));
            button.setOnClickListener(listener);
            button.setTooltipText(label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
            lp.bottomMargin = dp(5);
            button.setLayoutParams(lp);
            return button;
        }

        private TextView railText(String value, float sp, int colorRes, boolean bold) {
            TextView text = new TextView(activity);
            text.setText(value);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            text.setTextColor(activity.getColor(colorRes));
            if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return text;
        }

        private void refreshRailIdentity() {
            if (railProfile == null) return;
            try {
                ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
                if (identity != null && identity.loggedIn) {
                    String name = identity.usernameDisplay();
                    railProfile.setText(name == null || name.trim().isEmpty()
                            ? "My Profile" : name);
                    railProfile.setTooltipText("Open my forum profile");
                } else {
                    railProfile.setText("Sign In");
                    railProfile.setTooltipText("Sign in to Harley's Clan Forum");
                }
            } catch (Throwable ignored) {
                railProfile.setText("Profile");
            }
        }

        private void openProfile() {
            try {
                ForumIdentity.Snapshot identity = ForumIdentity.load(activity);
                if (identity != null && identity.loggedIn) {
                    String slug = identity.slug != null && !identity.slug.trim().isEmpty()
                            ? identity.slug : identity.username;
                    if (slug != null && !slug.trim().isEmpty()) {
                        loadForumPath("/u/" + Uri.encode(slug.trim()));
                        return;
                    }
                }
            } catch (Throwable ignored) {}
            loadForumPath("/login");
        }

        private void loadForumPath(String path) {
            if (webView == null) return;
            String host = SetupCenter.PRIMARY_FORUM_HOST;
            try {
                String current = webView.getUrl();
                if (current != null) {
                    Uri parsed = Uri.parse(current);
                    if (ForumUrlRouter.isForumHost(parsed.getHost())) host = parsed.getHost();
                }
            } catch (Throwable ignored) {}
            webView.loadUrl("https://" + host + (path == null ? "/" : path));
        }

        private void configureWebView(Mode currentMode) {
            if (webView == null) return;
            try {
                WebSettings settings = webView.getSettings();
                settings.setUseWideViewPort(true);
                if (currentMode == Mode.DESKTOP) {
                    settings.setSupportZoom(true);
                    settings.setBuiltInZoomControls(true);
                    settings.setDisplayZoomControls(false);
                    webView.setVerticalScrollBarEnabled(true);
                    webView.setHorizontalScrollBarEnabled(false);
                    webView.setScrollbarFadingEnabled(false);
                } else {
                    settings.setBuiltInZoomControls(false);
                    webView.setScrollbarFadingEnabled(true);
                }
            } catch (Throwable error) {
                AppLogger.warn(activity, "desktop_webview", error.getClass().getSimpleName());
            }
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (mode != Mode.DESKTOP && mode != Mode.TABLET) return false;

            boolean ctrl = event.isCtrlPressed();
            boolean alt = event.isAltPressed();
            if (ctrl && keyCode == KeyEvent.KEYCODE_R) {
                if (webView != null) webView.reload();
                return true;
            }
            if (ctrl && keyCode == KeyEvent.KEYCODE_L) {
                if (currentUrlText != null) {
                    currentUrlText.requestFocus();
                    currentUrlText.setTextIsSelectable(true);
                    currentUrlText.setSelection(0, currentUrlText.length());
                }
                return true;
            }
            if (alt && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (webView != null && webView.canGoBack()) webView.goBack();
                return true;
            }
            if (alt && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (webView != null && webView.canGoForward()) webView.goForward();
                return true;
            }
            return false;
        }

        private void removeRailFromWorkspace() {
            shiftMainShell(0);
            if (rail != null) rail.setVisibility(View.GONE);
        }

        private void shiftMainShell(int leftDp) {
            if (mainShell == null) return;
            ViewGroup.LayoutParams raw = mainShell.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            int left = dp(leftDp);
            if (lp.leftMargin != left) {
                lp.leftMargin = left;
                mainShell.setLayoutParams(lp);
            }
        }

        private void restoreBottomNavPreference() {
            if (bottomNav == null) return;
            boolean show = prefs.getBoolean("show_bottom_nav", false);
            bottomNav.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        private void setDrawerWidth(int widthDp) {
            if (drawerPanel == null) return;
            ViewGroup.LayoutParams lp = drawerPanel.getLayoutParams();
            if (lp == null) return;
            lp.width = dp(widthDp);
            drawerPanel.setLayoutParams(lp);
        }

        private void setErrorShellWidth(int widthDp) {
            if (errorShell == null) return;
            ViewGroup.LayoutParams lp = errorShell.getLayoutParams();
            if (lp == null) return;
            lp.width = widthDp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(widthDp);
            errorShell.setLayoutParams(lp);
        }

        private void setVisible(View view, boolean visible) {
            if (view != null) view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        private void setHeight(View view, int heightDp) {
            setHeightPx(view, dp(heightDp));
        }

        private void setHeightPx(View view, int heightPx) {
            if (view == null || view.getLayoutParams() == null) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = heightPx;
            view.setLayoutParams(lp);
        }

        private void setSquare(View view, int sizeDp) {
            setSquarePx(view, dp(sizeDp));
        }

        private void setSquarePx(View view, int sizePx) {
            if (view == null || view.getLayoutParams() == null) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = sizePx;
            lp.height = sizePx;
            view.setLayoutParams(lp);
        }

        private void setTextSp(TextView view, float sp) {
            if (view != null) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }

        private int pxToDp(int px) {
            return Math.round(px / density);
        }

        private int dp(int value) {
            return Math.round(value * density);
        }
    }

    /** Starts the adaptive window controller before MainActivity is shown. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}

// ---- Consolidated from HcfAppLinksConfig.java ----
/**
 * Runtime mirror/validator for the shared HCF Digital Asset Links configuration.
 *
 * The repository main branch is the single source of truth for both Stable and
 * Dev/Beta package fingerprints. Android's OS-level App Links verifier still
 * checks each website's /.well-known/assetlinks.json; this class gives the HCF
 * app its own live view of the same canonical repository data.
 */
final class HcfAppLinksConfig {
    public static final String MASTER_PAGE_URL =
            "https://github.com/markhitchk/hcf-app/blob/main/configs%2Fapp-links%2Fassetlinks.json";
    public static final String MASTER_RAW_URL =
            "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/app-links/assetlinks.json";

    private static final String PREFS = "hcf_app";
    private static final String KEY_JSON = "app_links_master_json";
    private static final String KEY_VALID = "app_links_master_valid";
    private static final String KEY_LAST_CHECK = "app_links_master_last_check";
    private static final String KEY_SOURCE = "app_links_master_source";
    private static final String KEY_STATUS = "app_links_master_status";
    private static final String KEY_PACKAGE = "app_links_master_package";
    private static final long REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private HcfAppLinksConfig() {}

    /** Starts a non-blocking refresh when the cached copy is missing or stale. */
    public static void bootstrap(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, 0);
        long checked = prefs.getLong(KEY_LAST_CHECK, 0L);
        if (prefs.contains(KEY_JSON) && System.currentTimeMillis() - checked < REFRESH_INTERVAL_MS) {
            return;
        }
        refreshAsync(app);
    }

    /** Forces a background refresh from the main-branch raw GitHub URL. */
    public static void refreshAsync(Context context) {
        if (context == null || !REFRESHING.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    refreshNow(app);
                } finally {
                    REFRESHING.set(false);
                }
            }
        }, "hcf-app-links-config");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private static void refreshNow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        long now = System.currentTimeMillis();
        try {
            String json = download(MASTER_RAW_URL);
            Validation validation = validateForInstalledPackage(context, json);
            prefs.edit()
                    .putString(KEY_JSON, json)
                    .putBoolean(KEY_VALID, validation.valid)
                    .putLong(KEY_LAST_CHECK, now)
                    .putString(KEY_SOURCE, MASTER_RAW_URL)
                    .putString(KEY_PACKAGE, context.getPackageName())
                    .putString(KEY_STATUS, validation.status)
                    .apply();
            try {
                AppLogger.info(context, "app_links_master",
                        (validation.valid ? "verified" : "not_verified") + " | " + MASTER_RAW_URL);
            } catch (Throwable ignored) {
            }
        } catch (Throwable error) {
            prefs.edit()
                    .putLong(KEY_LAST_CHECK, now)
                    .putString(KEY_SOURCE, MASTER_RAW_URL)
                    .putString(KEY_PACKAGE, context.getPackageName())
                    .putString(KEY_STATUS, "Fetch failed: " + error.getClass().getSimpleName())
                    .apply();
            try {
                AppLogger.warn(context, "app_links_master", error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static String download(String source) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 AppLinksConfig");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        InputStream input = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line).append('\n');
            if (out.length() > 256 * 1024) {
                reader.close();
                connection.disconnect();
                throw new IllegalStateException("assetlinks.json too large");
            }
        }
        reader.close();
        connection.disconnect();
        return out.toString().trim();
    }

    private static Validation validateForInstalledPackage(Context context, String json) throws Exception {
        JSONArray statements = new JSONArray(json);
        String packageName = context.getPackageName();
        String signer = installedSignerSha256(context);
        if (signer.isEmpty()) return new Validation(false, "Installed signing certificate unavailable");

        for (int i = 0; i < statements.length(); i++) {
            JSONObject statement = statements.optJSONObject(i);
            if (statement == null || !hasHandleAllUrls(statement.optJSONArray("relation"))) continue;
            JSONObject target = statement.optJSONObject("target");
            if (target == null) continue;
            if (!"android_app".equals(target.optString("namespace"))) continue;
            if (!packageName.equals(target.optString("package_name"))) continue;

            JSONArray fingerprints = target.optJSONArray("sha256_cert_fingerprints");
            if (fingerprints == null) continue;
            for (int j = 0; j < fingerprints.length(); j++) {
                String expected = normalizeFingerprint(fingerprints.optString(j));
                if (signer.equals(expected)) {
                    return new Validation(true,
                            "Current package and signing certificate match the main assetlinks.json");
                }
            }
            return new Validation(false,
                    "Package is listed, but the installed signing certificate does not match");
        }
        return new Validation(false, "Current package is not listed in the main assetlinks.json");
    }

    private static boolean hasHandleAllUrls(JSONArray relations) {
        if (relations == null) return false;
        for (int i = 0; i < relations.length(); i++) {
            if ("delegate_permission/common.handle_all_urls".equals(relations.optString(i))) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static String installedSignerSha256(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo info;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) return "";
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(signatures[0].toByteArray()));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }

    private static String normalizeFingerprint(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").toUpperCase(Locale.US);
    }

    public static Snapshot snapshot(Context context) {
        if (context == null) return new Snapshot(false, 0L, MASTER_RAW_URL, "No context");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return new Snapshot(
                prefs.getBoolean(KEY_VALID, false),
                prefs.getLong(KEY_LAST_CHECK, 0L),
                prefs.getString(KEY_SOURCE, MASTER_RAW_URL),
                prefs.getString(KEY_STATUS, "Not checked yet"));
    }

    public static final class Snapshot {
        public final boolean valid;
        public final long lastCheckedAt;
        public final String source;
        public final String status;

        Snapshot(boolean valid, long lastCheckedAt, String source, String status) {
            this.valid = valid;
            this.lastCheckedAt = lastCheckedAt;
            this.source = source == null ? MASTER_RAW_URL : source;
            this.status = status == null ? "" : status;
        }
    }

    private static final class Validation {
        final boolean valid;
        final String status;

        Validation(boolean valid, String status) {
            this.valid = valid;
            this.status = status;
        }
    }

    /**
     * Auto-start hook. Android creates providers before Application.onCreate(), so
     * this makes the main-branch assetlinks source refresh without blocking launch.
     */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            Context context = getContext();
            if (context != null) HcfAppLinksConfig.bootstrap(context);
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}

// ---- Consolidated from HcfNativeRoutes.java ----
/**
 * Native URL bridge for HCF-only routes that should open Android UI instead of a forum page.
 *
 * https://forum.harleytg.com/app/settings and the backup-domain equivalent both open the
 * native App Settings screen. The existing hcf-app://settings route remains supported.
 */
final class HcfNativeRoutes {
    private static final String PRIMARY_HOST = "forum.harleytg.com";
    private static final String BACKUP_HOST = "harleysclan.freeflarum.com";
    private static final String SETTINGS_PATH = "/app/settings";
    private static final String EXTRA_SETTINGS_CONSUMED = "hcf_native_settings_route_consumed";
    private static final long ROUTE_POLL_MS = 300L;
    private static final long HOOK_REFRESH_MS = 1200L;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static long lastHookAt;
    private static boolean pollRunning;

    private HcfNativeRoutes() {}

    static boolean isNativeSettingsUri(Uri uri) {
        if (uri == null) return false;

        String scheme = safeLower(uri.getScheme());
        String host = safeLower(uri.getHost());
        if ("hcf-app".equals(scheme) && "settings".equals(host)) return true;
        if (!"https".equals(scheme)) return false;
        if (!PRIMARY_HOST.equals(host) && !BACKUP_HOST.equals(host)) return false;

        String path = uri.getPath();
        if (path == null) return false;
        String normalized = path.trim().toLowerCase(Locale.US);
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return SETTINGS_PATH.equals(normalized);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;

        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) {}
                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        resumedActivity = new WeakReference<>(activity);
                        if (activity instanceof HcfForum.MainActivity) {
                            guardMainActivity((HcfForum.MainActivity) activity);
                            startPolling();
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) resumedActivity.clear();
                    }

                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                    @Override public void onActivityDestroyed(Activity activity) {
                        Activity current = resumedActivity.get();
                        if (current == activity) resumedActivity.clear();
                    }
                });
    }

    private static void startPolling() {
        if (pollRunning) return;
        pollRunning = true;
        MAIN.post(ROUTE_POLL);
    }

    private static final Runnable ROUTE_POLL = new Runnable() {
        @Override public void run() {
            Activity activity = resumedActivity.get();
            if (!(activity instanceof HcfForum.MainActivity)
                    || activity.isFinishing() || activity.isDestroyed()) {
                pollRunning = false;
                return;
            }
            guardMainActivity((HcfForum.MainActivity) activity);
            MAIN.postDelayed(this, ROUTE_POLL_MS);
        }
    };

    private static void guardMainActivity(HcfForum.MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Intent intent = activity.getIntent();
        Uri incoming = intent == null ? null : intent.getData();
        if (isNativeSettingsUri(incoming)
                && (intent == null || !intent.getBooleanExtra(EXTRA_SETTINGS_CONSUMED, false))) {
            if (intent != null) intent.putExtra(EXTRA_SETTINGS_CONSUMED, true);
            openSettings(activity, "https_app_link");
            return;
        }

        WebView webView = activity.findViewById(R.id.webView);
        if (webView == null) return;

        Uri current = parse(webView.getUrl());
        if (isNativeSettingsUri(current)) {
            String host = current == null ? PRIMARY_HOST : safeLower(current.getHost());
            if (!PRIMARY_HOST.equals(host) && !BACKUP_HOST.equals(host)) host = PRIMARY_HOST;
            try {
                webView.stopLoading();
                webView.loadUrl("https://" + host + "/");
            } catch (Throwable ignored) {}
            openSettings(activity, "webview_url");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHookAt >= HOOK_REFRESH_MS) {
            lastHookAt = now;
            installWebRouteHook(webView);
        }
    }

    private static Uri parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Uri.parse(value.trim());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void openSettings(Activity activity, String source) {
        if (activity == null || activity instanceof HcfSubActivities.SettingsActivity
                || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            AppLogger.info(activity, "native_settings_route", source);
            activity.startActivity(new Intent(activity, HcfSubActivities.SettingsActivity.class));
        } catch (Throwable error) {
            AppLogger.error(activity, "native_settings_route", error.getClass().getSimpleName());
        }
    }

    /**
     * Stops Flarum SPA links/history changes before they turn /app/settings into a web page.
     * HCFNative.openSettings() is already exposed by MainActivity on trusted forum pages.
     */
    private static void installWebRouteHook(WebView webView) {
        if (webView == null) return;
        final String script =
                "(function(){try{" +
                "if(window.__HCF_NATIVE_SETTINGS_URL_V1__)return;" +
                "window.__HCF_NATIVE_SETTINGS_URL_V1__=true;" +
                "var hosts={'forum.harleytg.com':1,'harleysclan.freeflarum.com':1};" +
                "var isSettings=function(v){try{var u=new URL(String(v||''),location.href);" +
                "var p=String(u.pathname||'').replace(/\\/+$/,'').toLowerCase();" +
                "return u.protocol==='https:'&&!!hosts[String(u.hostname||'').toLowerCase()]&&p==='/app/settings';" +
                "}catch(e){return false;}};" +
                "var open=function(){try{if(window.HCFNative&&HCFNative.openSettings)HCFNative.openSettings();}catch(e){}};" +
                "document.addEventListener('click',function(e){try{var t=e.target&&e.target.closest?e.target.closest('a[href]'):null;" +
                "if(t&&isSettings(t.href)){e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation();open();}}catch(x){}},true);" +
                "['pushState','replaceState'].forEach(function(k){try{var old=history[k];if(typeof old!=='function')return;" +
                "history[k]=function(){try{var u=arguments.length>2?arguments[2]:'';if(u&&isSettings(u)){open();return null;}}catch(e){}" +
                "return old.apply(this,arguments);};}catch(e){}});" +
                "}catch(e){}})();";
        try {
            webView.evaluateJavascript(script, null);
        } catch (Throwable ignored) {}
    }

    /** Installs the native URL bridge before MainActivity is shown. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) { return 0; }
    }
}

// ---- Consolidated from HcfBanSystem.java ----
/**
 * Native HCF access gate backed by a public, sanitized GitHub JSON ban list.
 * Raw IP addresses are never published in the list; network bans use SHA-256 hashes.
 */
final class HcfBanSystem {
    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/markhitchk/hcf-app/main/configs/ban-system.config";
    private static final String PREF_CONFIG_CACHE = "ban_system_config_cache";
    private static final String PREF_CONFIG_FETCHED_AT = "ban_system_config_fetched_at";
    private static final long CONFIG_CACHE_MS = 6L * 60L * 60L * 1000L;

    // Sanitized app-side mirror of the private ban-system bypass list.
    // Never place raw IP addresses here; network exclusions use SHA-256 only.
    private static final String[] BYPASS_USERNAMES = new String[] { "harleytg" };
    private static final String[] BYPASS_IP_SHA256 = new String[] {
            "09e5ca508de13a2a32d1346d43457b831006c036daeda23bbeb9ddceb0993ab8"
    };

    private HcfBanSystem() {}

    private static class BaseActivity extends Activity {
        int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        TextView text(String value, int sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextSize(sp);
            view.setTextColor(color);
            return view;
        }

        GradientDrawable panelDrawable(int fill, int stroke, int radiusDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radiusDp));
            drawable.setStroke(dp(1), stroke);
            return drawable;
        }
    }

    static final class RuntimeConfig {
        final boolean enabled;
        final String banListUrl;
        final String ipPrimary;
        final String ipFallback;

        RuntimeConfig(boolean enabled, String banListUrl, String ipPrimary, String ipFallback) {
            this.enabled = enabled;
            this.banListUrl = safe(banListUrl);
            this.ipPrimary = safe(ipPrimary);
            this.ipFallback = safe(ipFallback);
        }

        boolean ready() {
            return enabled && banListUrl.startsWith("https://");
        }
    }

    static final class PublicIp {
        final String address;
        final String source;

        PublicIp(String address, String source) {
            this.address = normalizeIp(address);
            this.source = safe(source);
        }

        boolean available() {
            return !address.isEmpty();
        }
    }

    static final class CheckResult {
        final boolean banned;
        final String banId;
        final String reason;
        final String expiresAt;
        final String scope;
        final String username;
        final String maskedIp;
        final boolean appealAllowed;

        CheckResult(boolean banned, String banId, String reason, String expiresAt,
                    String scope, String username, String maskedIp, boolean appealAllowed) {
            this.banned = banned;
            this.banId = safe(banId);
            this.reason = safe(reason);
            this.expiresAt = safe(expiresAt);
            this.scope = safe(scope);
            this.username = safe(username);
            this.maskedIp = safe(maskedIp);
            this.appealAllowed = appealAllowed;
        }
    }

    /** Launcher gate. All normal launches and HCF App Links enter here first. */
    public static final class GateActivity extends BaseActivity {
        private final Handler main = new Handler(Looper.getMainLooper());
        private TextView status;
        private TextView detail;
        private ProgressBar progress;
        private volatile boolean destroyed;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            try { ThemeManager.apply(this); } catch (Throwable ignored) {}
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi(bg));

            Thread worker = new Thread(new Runnable() {
                @Override public void run() { runGate(); }
            }, "hcf-ban-gate");
            worker.setPriority(Thread.NORM_PRIORITY);
            worker.start();
        }

        @Override
        protected void onDestroy() {
            destroyed = true;
            main.removeCallbacksAndMessages(null);
            super.onDestroy();
        }

        private View buildUi(int bg) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(34), dp(24), dp(28));
            root.setBackgroundColor(bg);

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            root.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));

            TextView brand = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(14);
            root.addView(brand, brandLp);

            TextView title = text("Starting Harley's Clan Forum", 21, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(6);
            root.addView(title, titleLp);

            status = text("Checking access status", 13, getColor(R.color.hcf_cyan_bright));
            status.setTypeface(null, 1);
            status.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
            statusLp.topMargin = dp(22);
            root.addView(status, statusLp);

            detail = text("Preparing the HCF security check before the forum opens.", 11,
                    getColor(R.color.hcf_muted));
            detail.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(6);
            root.addView(detail, detailLp);

            progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(100);
            progress.setProgress(4);
            progress.setProgressTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
            progressLp.topMargin = dp(24);
            root.addView(progress, progressLp);

            TextView privacy = text("Public IP is used only for HCF security and abuse-prevention checks.",
                    9, getColor(R.color.hcf_hint));
            privacy.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
            privacyLp.topMargin = dp(16);
            root.addView(privacy, privacyLp);
            return root;
        }

        private void runGate() {
            ForumIdentity.Snapshot identity = ForumIdentity.load(this);
            String username = identity != null && identity.loggedIn ? safe(identity.username) : "";
            stage(18, "Checking forum identity",
                    username.isEmpty() ? "Guest session detected." : "Signed-in user: @" + username);

            if (isBypassedUsername(username)) {
                AppLogger.info(this, "ban_gate", "bypass matched | scope=user");
                stage(100, "Access allowed", "This forum account is excluded from HCF ban enforcement.");
                continueStartupSoon();
                return;
            }

            RuntimeConfig config;
            try {
                config = loadRuntimeConfig(this);
            } catch (Throwable error) {
                AppLogger.warn(this, "ban_gate_config", error.getClass().getSimpleName());
                stage(100, "Access check unavailable", "Ban configuration could not be loaded; startup will continue.");
                continueStartupSoon();
                return;
            }

            if (!config.ready()) {
                stage(100, "Access system not enabled", "The HCF ban list is not active; startup will continue.");
                continueStartupSoon();
                return;
            }

            stage(38, "Checking public network address", "Using ipify with IPinfo as the fallback lookup.");
            PublicIp publicIp = resolvePublicIp(config);
            stage(58, "Checking HCF ban list", "Matching account and hashed network records.");

            try {
                CheckResult result = checkBanList(config, identity, publicIp);
                if (result.banned) {
                    AppLogger.warn(this, "ban_gate", "blocked | scope=" + result.scope + " | id=" + result.banId);
                    openBanScreen(result);
                    return;
                }
                stage(100, "Access allowed", "No active HCF ban was found.");
                continueStartupSoon();
            } catch (Throwable error) {
                // Fail open so a GitHub or IP-provider outage cannot lock out all users.
                AppLogger.warn(this, "ban_gate", "fail-open | " + error.getClass().getSimpleName());
                stage(100, "Access check unavailable", "The ban list could not be reached; startup will continue.");
                continueStartupSoon();
            }
        }

        private void stage(final int value, final String statusText, final String detailText) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed()) return;
                    if (status != null) status.setText(statusText);
                    if (detail != null) detail.setText(detailText);
                    if (progress != null) progress.setProgress(Math.max(0, Math.min(100, value)), true);
                }
            });
        }

        private void continueStartupSoon() {
            main.postDelayed(new Runnable() {
                @Override public void run() { continueStartup(); }
            }, 220L);
        }

        private void continueStartup() {
            if (destroyed || isFinishing() || isDestroyed()) return;
            Intent target = new Intent(this, HcfUI.StartupActivity.class);
            Intent source = getIntent();
            if (source != null) {
                if (source.getData() != null) target.setData(source.getData());
                if (!TextUtils.isEmpty(source.getAction()) && !Intent.ACTION_MAIN.equals(source.getAction())) {
                    target.setAction(source.getAction());
                }
            }
            startActivity(target);
            overridePendingTransition(0, 0);
            finish();
        }

        private void openBanScreen(final CheckResult result) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (destroyed || isFinishing() || isDestroyed()) return;
                    Intent intent = new Intent(GateActivity.this, BanActivity.class);
                    intent.putExtra("ban_id", result.banId);
                    intent.putExtra("reason", result.reason);
                    intent.putExtra("expires_at", result.expiresAt);
                    intent.putExtra("scope", result.scope);
                    intent.putExtra("username", result.username);
                    intent.putExtra("masked_ip", result.maskedIp);
                    intent.putExtra("appeal_allowed", result.appealAllowed);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }
            });
        }
    }

    /** Dedicated native ban screen. */
    public static final class BanActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            try { ThemeManager.apply(this); } catch (Throwable ignored) {}
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi(bg));
        }

        @Override
        public void onBackPressed() {
            finishAffinity();
        }

        private View buildUi(int bg) {
            Intent data = getIntent();
            final String banId = safe(data == null ? "" : data.getStringExtra("ban_id"));
            String reason = safe(data == null ? "" : data.getStringExtra("reason"));
            String expiresAt = safe(data == null ? "" : data.getStringExtra("expires_at"));
            String scope = safe(data == null ? "" : data.getStringExtra("scope"));
            String username = safe(data == null ? "" : data.getStringExtra("username"));
            String maskedIp = safe(data == null ? "" : data.getStringExtra("masked_ip"));
            boolean appealAllowed = data == null || data.getBooleanExtra("appeal_allowed", true);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(bg);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(30), dp(24), dp(30));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            root.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

            TextView brand = text("HARLEY'S CLAN FORUM", 10, getColor(R.color.hcf_meta));
            brand.setTypeface(null, 1);
            brand.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(-1, -2);
            brandLp.topMargin = dp(12);
            root.addView(brand, brandLp);

            TextView title = text("Access Restricted", 24, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(6);
            root.addView(title, titleLp);

            TextView subtitle = text("This account or network currently has an active HCF access ban.",
                    12, getColor(R.color.hcf_muted));
            subtitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
            subLp.topMargin = dp(8);
            root.addView(subtitle, subLp);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(panelDrawable(getColor(R.color.hcf_surface), getColor(R.color.hcf_cyan), 12));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.topMargin = dp(22);
            root.addView(card, cardLp);

            card.addView(info("Ban ID", banId.isEmpty() ? "HCF-BAN" : banId));
            card.addView(info("Scope", "user".equalsIgnoreCase(scope) ? "Forum account" : "Network / IP"));
            if (!username.isEmpty()) card.addView(info("Username", "@" + username));
            if (!maskedIp.isEmpty()) card.addView(info("Network", maskedIp));
            card.addView(info("Expires", expiresAt.isEmpty() ? "Permanent / until removed" : expiresAt));

            TextView reasonTitle = text("Reason", 11, getColor(R.color.hcf_meta));
            reasonTitle.setTypeface(null, 1);
            LinearLayout.LayoutParams reasonTitleLp = new LinearLayout.LayoutParams(-1, -2);
            reasonTitleLp.topMargin = dp(14);
            card.addView(reasonTitle, reasonTitleLp);
            TextView reasonText = text(reason.isEmpty() ? "Access restricted by an administrator." : reason,
                    14, getColor(R.color.hcf_text));
            card.addView(reasonText, new LinearLayout.LayoutParams(-1, -2));

            Button retry = button("Check Again", true);
            retry.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Intent retryIntent = new Intent(BanActivity.this, HcfUI.StartupActivity.class);
                    retryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(retryIntent);
                    finish();
                }
            });
            addButton(root, retry, 20);

            if (appealAllowed) {
                Button appeal = button("Appeal Ban", false);
                appeal.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { openAppeal(banId); }
                });
                addButton(root, appeal, 10);
            }

            Button close = button("Close App", false);
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { finishAffinity(); }
            });
            addButton(root, close, 10);

            TextView privacy = text("Raw IP addresses are not published in the HCF ban list.",
                    9, getColor(R.color.hcf_hint));
            privacy.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
            privacyLp.topMargin = dp(18);
            root.addView(privacy, privacyLp);
            return scroll;
        }

        private TextView info(String label, String value) {
            TextView line = text(label + ": " + value, 12, getColor(R.color.hcf_text));
            line.setTextIsSelectable(true);
            line.setPadding(0, dp(3), 0, dp(3));
            return line;
        }

        private Button button(String label, boolean primary) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextColor(primary ? getColor(R.color.hcf_on_accent) : getColor(R.color.hcf_text));
            button.setBackground(panelDrawable(
                    primary ? getColor(R.color.hcf_cyan) : getColor(R.color.hcf_surface),
                    getColor(R.color.hcf_cyan), 10));
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(0);
            return button;
        }

        private void addButton(LinearLayout root, Button button, int topMargin) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
            lp.topMargin = dp(topMargin);
            root.addView(button, lp);
        }

        private void openAppeal(String banId) {
            try {
                String subject = "HCF Ban Appeal" + (banId.isEmpty() ? "" : " - " + banId);
                Uri uri = Uri.parse("mailto:harleytg.hq@gmail.com?subject=" + Uri.encode(subject));
                startActivity(new Intent(Intent.ACTION_SENDTO, uri));
            } catch (Throwable error) {
                AppLogger.warn(this, "ban_appeal", error.getClass().getSimpleName());
            }
        }
    }

    /** Runs the ban lookup as a stage of the existing HCF startup loader. */
    static CheckResult checkCurrentAccess(Context context) throws Exception {
        ForumIdentity.Snapshot identity = ForumIdentity.load(context);
        String username = identity != null && identity.loggedIn ? safe(identity.username) : "";
        if (isBypassedUsername(username)) {
            return new CheckResult(false, "", "", "", "", username, "", true);
        }
        RuntimeConfig config = loadRuntimeConfig(context);
        if (!config.ready()) {
            return new CheckResult(false, "", "", "", "", username, "", true);
        }
        PublicIp publicIp = resolvePublicIp(config);
        return checkBanList(config, identity, publicIp);
    }

    private static RuntimeConfig loadRuntimeConfig(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        long now = System.currentTimeMillis();
        long fetchedAt = prefs.getLong(PREF_CONFIG_FETCHED_AT, 0L);
        String cached = safe(prefs.getString(PREF_CONFIG_CACHE, ""));

        if (!cached.isEmpty() && fetchedAt > 0L && now - fetchedAt < CONFIG_CACHE_MS) {
            RuntimeConfig parsed = parseConfig(cached);
            // Ignore an old Firebase-era cache and refresh immediately after app update.
            if (parsed.ready()) return parsed;
        }

        try {
            String remote = getText(CONFIG_URL, 4000, 4000, "text/plain");
            RuntimeConfig parsed = parseConfig(remote);
            prefs.edit().putString(PREF_CONFIG_CACHE, remote)
                    .putLong(PREF_CONFIG_FETCHED_AT, now).apply();
            return parsed;
        } catch (Throwable error) {
            if (!cached.isEmpty()) return parseConfig(cached);
            throw error;
        }
    }

    private static RuntimeConfig parseConfig(String raw) {
        boolean enabled = false;
        String banList = "";
        String primary = "https://api.ipify.org?format=json";
        String fallback = "https://ipinfo.io/json";
        String section = "";

        for (String source : (raw == null ? "" : raw).split("\\r?\\n")) {
            String line = source == null ? "" : source.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.US);
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) continue;
            String key = line.substring(0, split).trim().toLowerCase(Locale.US);
            String value = line.substring(split + 1).trim();
            if ("config".equals(section) && "enabled".equals(key)) {
                enabled = "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
            } else if ("ban_list".equals(section) && "url".equals(key)) {
                banList = value;
            } else if ("ip_lookup".equals(section) && "primary".equals(key)) {
                primary = value;
            } else if ("ip_lookup".equals(section) && "fallback".equals(key)) {
                fallback = value;
            }
        }
        return new RuntimeConfig(enabled, banList, primary, fallback);
    }

    private static PublicIp resolvePublicIp(RuntimeConfig config) {
        PublicIp primary = lookupIp(config.ipPrimary, "ipify");
        if (primary.available()) return primary;
        PublicIp fallback = lookupIp(config.ipFallback, "IPinfo");
        return fallback.available() ? fallback : new PublicIp("", "unavailable");
    }

    private static PublicIp lookupIp(String urlText, String source) {
        if (TextUtils.isEmpty(urlText) || !urlText.startsWith("https://")) return new PublicIp("", source);
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/plain;q=0.9");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanIpLookup/2");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return new PublicIp("", source);
            String body = readAll(connection.getInputStream(), 8192);
            try {
                return new PublicIp(new JSONObject(body).optString("ip", ""), source);
            } catch (Throwable ignored) {
                return new PublicIp(body.trim(), source);
            }
        } catch (Throwable ignored) {
            return new PublicIp("", source);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static CheckResult checkBanList(RuntimeConfig config,
                                             ForumIdentity.Snapshot identity,
                                             PublicIp publicIp) throws Exception {
        boolean loggedIn = identity != null && identity.loggedIn && !safe(identity.username).isEmpty();
        String username = loggedIn ? safe(identity.username) : "";
        String normalizedUser = normalizedUsername(username);
        String maskedIp = publicIp == null ? "" : maskIp(publicIp.address);
        String ipHash = publicIp != null && publicIp.available() ? sha256Hex(publicIp.address) : "";

        if (isBypassedUsername(username) || isBypassedIpHash(ipHash)) {
            return new CheckResult(false, "", "", "", "", username, maskedIp, true);
        }

        String body = getText(config.banListUrl, 4500, 5000, "application/json");
        JSONObject root = new JSONObject(body);
        if (root.optInt("schema_version", 0) != 1) {
            throw new IllegalStateException("Unsupported ban-list schema");
        }

        if (!normalizedUser.isEmpty()) {
            JSONObject users = root.optJSONObject("users");
            JSONObject entry = users == null ? null : users.optJSONObject(normalizedUser);
            if (isBanEntryActive(entry)) return fromEntry(entry, "user", username, maskedIp);
        }

        if (!ipHash.isEmpty()) {
            JSONObject networks = root.optJSONObject("ip_sha256");
            JSONObject entry = networks == null ? null : networks.optJSONObject(ipHash);
            if (isBanEntryActive(entry)) return fromEntry(entry, "ip", username, maskedIp);
        }

        return new CheckResult(false, "", "", "", "", username, maskedIp, true);
    }

    static boolean isBypassedUsername(String username) {
        String normalized = normalizedUsername(username);
        if (normalized.isEmpty()) return false;
        for (String bypass : BYPASS_USERNAMES) {
            if (normalized.equals(normalizedUsername(bypass))) return true;
        }
        return false;
    }

    static boolean isBypassedIpHash(String hash) {
        String normalized = safe(hash).toLowerCase(Locale.US);
        if (normalized.isEmpty()) return false;
        for (String bypass : BYPASS_IP_SHA256) {
            if (normalized.equals(safe(bypass).toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    private static CheckResult fromEntry(JSONObject entry, String scope, String username, String maskedIp) {
        return new CheckResult(true,
                entry == null ? "" : entry.optString("ban_id", ""),
                entry == null ? "" : entry.optString("reason", ""),
                nullableString(entry, "expires_at"),
                scope,
                username,
                maskedIp,
                entry == null || entry.optBoolean("appeal_allowed", true));
    }

    private static boolean isBanEntryActive(JSONObject entry) {
        if (entry == null || !entry.optBoolean("active", false)) return false;
        String expires = nullableString(entry, "expires_at");
        if (expires.isEmpty()) return true;
        Long parsed = parseUtc(expires);
        return parsed == null || parsed.longValue() > System.currentTimeMillis();
    }

    private static Long parseUtc(String value) {
        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "";
        return safe(object.optString(key, ""));
    }

    private static String normalizedUsername(String value) {
        String out = safe(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]", "-");
        while (out.contains("--")) out = out.replace("--", "-");
        if (out.length() > 80) out = out.substring(0, 80);
        return out;
    }

    static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) out.append(String.format(Locale.US, "%02x", item & 0xff));
        return out.toString();
    }

    private static String maskIp(String ip) {
        String value = normalizeIp(ip);
        if (value.isEmpty()) return "";
        if (value.indexOf(':') >= 0) {
            String[] parts = value.split(":");
            StringBuilder out = new StringBuilder();
            int shown = 0;
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (shown > 0) out.append(':');
                out.append(part);
                shown++;
                if (shown == 2) break;
            }
            return out.append(":…").toString();
        }
        String[] parts = value.split("\\.");
        return parts.length == 4 ? parts[0] + "." + parts[1] + ".*.*" : "Network";
    }

    private static String getText(String urlText, int connectTimeout, int readTimeout, String accept) throws Exception {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " BanList/2");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            return readAll(connection.getInputStream(), 131072);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream stream, int max) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
                if (out.length() > max) throw new IllegalStateException("Response too large");
            }
            return out.toString();
        }
    }

    private static String normalizeIp(String value) {
        String raw = safe(value);
        if (raw.isEmpty() || raw.length() > 64) return "";
        if (raw.startsWith("::ffff:")) raw = raw.substring(7);
        if (raw.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
            String[] parts = raw.split("\\.");
            for (String part : parts) {
                try {
                    int number = Integer.parseInt(part);
                    if (number < 0 || number > 255) return "";
                } catch (Throwable ignored) { return ""; }
            }
            return raw;
        }
        if (raw.indexOf(':') >= 0 && raw.matches("^[0-9a-fA-F:]+$")) return raw.toLowerCase(Locale.US);
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace((char) 0, ' ').trim();
    }
}
