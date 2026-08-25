package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

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
public final class HcfDesktopMode {
    static final int TABLET_MIN_DP = 600;
    static final int DESKTOP_MIN_DP = 840;
    private static final int DESKTOP_RAIL_DP = 220;
    private static final String RAIL_TAG = "hcf_desktop_nav_rail";
    private static final Map<HcfMainActivities.MainActivity, Controller> CONTROLLERS =
            new WeakHashMap<>();
    private static boolean installed;

    private HcfDesktopMode() {}

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
                        if (activity instanceof HcfMainActivities.MainActivity) {
                            attach((HcfMainActivities.MainActivity) activity);
                        }
                    }

                    @Override public void onActivityStarted(Activity activity) {}

                    @Override public void onActivityResumed(Activity activity) {
                        if (activity instanceof HcfMainActivities.MainActivity) {
                            Controller controller = attach((HcfMainActivities.MainActivity) activity);
                            controller.apply(true);
                        }
                    }

                    @Override public void onActivityPaused(Activity activity) {}
                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override public void onActivityDestroyed(Activity activity) {
                        if (!(activity instanceof HcfMainActivities.MainActivity)) return;
                        Controller controller = CONTROLLERS.remove(activity);
                        if (controller != null) controller.detach();
                    }
                }
        );
    }

    private static synchronized Controller attach(HcfMainActivities.MainActivity activity) {
        Controller existing = CONTROLLERS.get(activity);
        if (existing != null) return existing;
        Controller controller = new Controller(activity);
        CONTROLLERS.put(activity, controller);
        controller.attach();
        return controller;
    }

    private static final class Controller implements View.OnLayoutChangeListener, View.OnKeyListener {
        private final HcfMainActivities.MainActivity activity;
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
        private TextView currentUrlText;
        private TextView hostBadge;
        private View bottomNav;
        private View errorShell;
        private Button railProfile;
        private TextView railMode;
        private Mode mode;
        private int lastWidthDp = -1;
        private boolean applying;

        Controller(HcfMainActivities.MainActivity activity) {
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
