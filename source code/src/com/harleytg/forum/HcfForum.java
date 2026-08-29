package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONObject;


// ---- Consolidated from HcfMainActivities.java ----
public final class HcfForum {
    public static final class IdentityAvatarView extends ImageView {
        public IdentityAvatarView(Context context) { super(context); init(); }
        public IdentityAvatarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
        public IdentityAvatarView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(); }

        private void init() {
            setAdjustViewBounds(false);
            setCropToPadding(false);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    float radius = 9f * getResources().getDisplayMetrics().density;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            setClipToOutline(true);
        }

        @Override public void setScaleType(ScaleType scaleType) {
            super.setScaleType(scaleType == ScaleType.FIT_XY ? ScaleType.CENTER_CROP : scaleType);
        }

        static FrameLayout frame(Context context, ImageView avatar) {
            FrameLayout frame = new FrameLayout(context);
            frame.setBackgroundResource(R.drawable.identity_avatar_background);
            FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            int inset = Math.max(1, Math.round(2f * context.getResources().getDisplayMetrics().density));
            inner.setMargins(inset, inset, inset, inset);
            frame.addView(avatar, inner);
            return frame;
        }
    }

    private HcfForum() {}

    // ---- MainActivity.java ----
    /* loaded from: classes.dex */
    public static class MainActivity extends ThemedActivity {
        private static final int FILE_CHOOSER_REQUEST = 1407;
        private static final int NOTIFICATION_PERMISSION_REQUEST = 1408;
        private static final int UPDATE_INSTALL_PERMISSION_REQUEST = 1409;
        private String activeHost;
        private HcfBrandedLoader brandedLoader;
        private Button alternateButton;
        private ImageView appHeaderLogo;
        private TextView appHeaderSubtitle;
        private TextView appHeaderTitle;
        private TextView bottomAlerts;
        private TextView bottomBrowse;
        private TextView bottomCreate;
        private TextView bottomHome;
        private View bottomNav;
        private TextView bottomProfile;
        private ImageButton copyUrlButton;
        private EditText currentUrlText;
        private ImageButton drawerButton;
        private Button drawerHome;
        private TextView drawerHostText;
        private ImageView drawerIdentityAvatar;
        private View drawerIdentityCard;
        private TextView drawerIdentityMeta;
        private TextView drawerIdentityText;
        private TextView drawerIdentityUsername;
        private View drawerLinkedAccounts;
        private Button drawerLogs;
        private TextView drawerNotificationCountBadge;
        private Button drawerNotifications;
        private Button drawerOpenExternal;
        private View drawerPanel;
        private TextView drawerProviderDiscord;
        private TextView drawerProviderEmail;
        private View drawerScrim;
        private ImageButton drawerSecurity;
        private Button drawerSettings;
        private Button drawerShare;
        private Button drawerSupport;
        private boolean drawerSwipeCandidate;
        private long drawerSwipeStartAt;
        private float drawerSwipeStartX;
        private float drawerSwipeStartY;
        private Button drawerSwitchHost;
        private TextView drawerVersionText;
        private LinearLayout errorActions;
        private TextView errorCodeText;
        private Button errorDetailsButton;
        private TextView errorHeroText;
        private TextView errorMessageText;
        private View errorStateScroll;
        private TextView errorStatusText;
        private Button errorSupportButton;
        private TextView errorTechnicalText;
        private TextView errorTitleText;
        private ValueCallback<Uri[]> filePathCallback;
        private TextView headerNotificationCountBadge;
        private ImageButton headerNotificationsButton;
        private TextView hostBadge;
        private Bitmap identityAvatarBitmap;
        private ErrorSystem.AppError lastAppError;
        private long lastBridgeNotificationAt;
        private Uri lastErrorUri;
        private long lastPullRefreshAt;
        private boolean launchFailed;
        private boolean liveReloadInProgress;
        private TextView liveStatusBadge;
        private LiveForumUpdater liveUpdater;
        private boolean mainFrameLoadFailed;
        private AlertDialog nativeUpdateDialog;
        private boolean nativeUpdateFlowActive;
        private ConnectivityManager.NetworkCallback networkCallback;
        private boolean networkCallbackRegistered;
        private boolean notificationReceiverRegistered;
        private ProgressBar pageProgress;
        private SharedPreferences prefs;
        private boolean pullFromTop;
        private float pullStartY;
        private ImageButton reloadButton;
        private ImageButton urlBackButton;
        private boolean rendererRecoveryPending;
        private Button retryButton;
        private TextView secureForumLabel;
        private ProgressBar startupProgress;
        private View startupStateContainer;
        private View statusOverlay;
        private TextView statusSubtitle;
        private TextView statusTitle;
        private boolean switchingHosts;
        private View topAppBar;
        private View urlBar;
        private View urlBarInner;
        private ImageButton urlHomeButton;
        private WebView webView;
        private boolean welcomeBackPending;
        private TextView welcomeBanner;
        private int connectionUiGeneration = 0;
        private int pendingLiveScrollY = -1;
        private String identityAvatarRequestedUrl = "";
        private String identityAvatarLoadedUrl = "";
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private long nativeUpdateDownloadId = -1;
        private String liveState = "SYNCING";
        private String lastRecoverableUrl = "";
        private String appliedThemeSignature = "";
        private final BroadcastReceiver notificationEventReceiver = new BroadcastReceiver() { // from class: com.harleytg.forum.dev.MainActivity.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !"com.harleytg.forum.dev.NOTIFICATION_EVENT".equals(intent.getAction())) {
                    return;
                }
                int intExtra = intent.getIntExtra("event_count", -1);
                if (intExtra >= 0) {
                    MainActivity.this.updateNotificationChrome(intExtra);
                }
                String stringExtra = intent.getStringExtra("event_title");
                String stringExtra2 = intent.getStringExtra("event_body");
                if (stringExtra == null || stringExtra.trim().isEmpty()) {
                    return;
                }
                String trim = stringExtra.trim();
                if (stringExtra2 != null && !stringExtra2.trim().isEmpty()) {
                    trim = trim + " • " + stringExtra2.trim();
                }
                MainActivity.this.showTransientBanner(trim);
            }
        };

        static /* synthetic */ void lambda$showNativeUpdateDownload$58(DialogInterface dialogInterface) {
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            String home;
            super.onCreate(bundle);
            ThemeManager.apply(this);
            this.appliedThemeSignature = ThemeManager.signature(this);
            boolean z = false;
            SharedPreferences sharedPreferences = getSharedPreferences("hcf_app", 0);
            this.prefs = sharedPreferences;
            boolean z2 = sharedPreferences.getBoolean("app_has_launched", false);
            if (z2 && bundle == null) {
                z = true;
            }
            this.welcomeBackPending = z;
            this.prefs.edit().putBoolean("app_has_launched", true).apply();
            try {
                setContentView(R.layout.activity_main);
                bindViews();
                View brandedLoaderRoot = findViewById(R.id.rootFrame);
                if (brandedLoaderRoot instanceof FrameLayout) {
                    this.brandedLoader = new HcfBrandedLoader(this);
                    this.brandedLoader.attach((FrameLayout) brandedLoaderRoot);
                }
                applySolidIconSystem();
                applyAmoledSurfaces();
                validateCriticalViews();
                updateIdentityChrome(ForumIdentity.load(this));
                configureWebView();
                configureActions();
                this.liveUpdater = new LiveForumUpdater(this, new LiveForumUpdater.Listener() { // from class: com.harleytg.forum.dev.MainActivity.2
                    @Override // com.harleytg.forum.dev.LiveForumUpdater.Listener
                    public String currentUrl() {
                        return MainActivity.this.currentUrlString();
                    }

                    @Override // com.harleytg.forum.dev.LiveForumUpdater.Listener
                    public void onChangeCandidate(String str, String str2) {
                        MainActivity.this.handleLiveChangeCandidate(str, str2);
                    }

                    @Override // com.harleytg.forum.dev.LiveForumUpdater.Listener
                    public void onStateChanged(String str) {
                        MainActivity.this.updateLiveState(str);
                    }
                });
                applyChromePreferences();
                scheduleFirstRunPermissionSetup();
                scheduleWhatsNew(z2);
                AppLogger.info(this, "main_create", BuildInfo.VERSION_BUILD_LINE + " | UA=" + BuildInfo.USER_AGENT_MARKER);
                if (bundle != null) {
                    this.webView.restoreState(bundle);
                    Uri parse = Uri.parse(this.webView.getUrl() == null ? "" : this.webView.getUrl());
                    this.activeHost = ForumUrlRouter.isForumUrl(parse) ? parse.getHost() : chooseInitialHost();
                    hideStatus();
                    handleNotificationIntent(getIntent());
                    scheduleDeferredNativeSetup();
                    return;
                }
                Uri data = getIntent() == null ? null : getIntent().getData();
                if (ForumUrlRouter.isForumUrl(data)) {
                    this.activeHost = data.getHost();
                    this.prefs.edit().putString("active_host", this.activeHost).apply();
                } else {
                    this.activeHost = chooseInitialHost();
                    this.prefs.edit().putString("active_host", this.activeHost).apply();
                }
                if (ForumUrlRouter.isForumUrl(data)) {
                    home = ForumUrlRouter.equivalentOnHost(data, data.getHost());
                } else {
                    home = ForumUrlRouter.home(this.activeHost);
                }
                updateUrlChrome(home);
                boolean startupHandoff = getIntent() != null
                        && getIntent().getBooleanExtra("hcf_startup_handoff", false);
                if (startupHandoff) {
                    // The native startup loader already completed. Do not flash the old
                    // content-only checking overlay before the forum's own loader appears.
                    hideStatus();
                } else {
                    showChecking(this.activeHost);
                }
                AppLogger.info(this, "initial_navigation", AppLogger.safeUrl(home));
                this.webView.loadUrl(home);
                scheduleDeferredNativeSetup();
            } catch (Throwable th) {
                this.launchFailed = true;
                AppLogger.crash(this, th);
                showCrashSafeScreen(th);
            }
        }

        private void applySolidIconSystem() {
            try {
                configureCenteredHeaderIcon(this.drawerButton, R.drawable.fa_bars, R.color.hcf_cyan_bright);
                configureCenteredHeaderIcon(this.headerNotificationsButton, R.drawable.fa_bell, R.color.hcf_cyan_bright);
                configureCenteredHeaderIcon(this.urlBackButton, R.drawable.fa_arrow_left, R.color.hcf_cyan_bright);
                configureCenteredHeaderIcon(this.reloadButton, R.drawable.fa_rotate_right, R.color.hcf_cyan_bright);
                configureCenteredHeaderIcon(this.copyUrlButton, R.drawable.fa_copy, R.color.hcf_cyan_bright);
                configureCenteredHeaderIcon(this.urlHomeButton, R.drawable.fa_house, R.color.hcf_cyan_bright);
                if (this.urlBackButton != null) {
                    this.urlBackButton.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            MainActivity.this.onBackPressed();
                        }
                    });
                }
                configureErrorActionButton(this.retryButton, true, R.drawable.fa_rotate_right);
                configureErrorActionButton(this.alternateButton, false, R.drawable.fa_right_left);
                configureErrorActionButton(this.errorDetailsButton, false, R.drawable.fa_list);
                configureErrorActionButton(this.errorSupportButton, false, R.drawable.fa_envelope);
                configureDrawerSecurityIcon();
                FaIcons.applyStart(this.drawerHome, R.drawable.fa_house);
                FaIcons.applyStart(this.drawerSwitchHost, R.drawable.fa_right_left);
                FaIcons.applyStart(this.drawerOpenExternal, R.drawable.fa_arrow_up_right_from_square);
                FaIcons.applyStart(this.drawerShare, R.drawable.fa_share_nodes);
                FaIcons.applyStart(this.drawerNotifications, R.drawable.fa_bell);
                FaIcons.applyStart(this.drawerSettings, R.drawable.fa_gear);
                FaIcons.applyStart(this.drawerLogs, R.drawable.fa_list);
                FaIcons.applyStart(this.drawerSupport, R.drawable.fa_envelope);
                FaIcons.applyTop(this.bottomHome, R.drawable.fa_house);
                FaIcons.applyTop(this.bottomBrowse, R.drawable.fa_list);
                FaIcons.applyTop(this.bottomCreate, R.drawable.fa_plus);
                FaIcons.applyTop(this.bottomAlerts, R.drawable.fa_bell);
                FaIcons.applyTop(this.bottomProfile, R.drawable.fa_user);
            } catch (Throwable th) {
                AppLogger.warn(this, "solid_icons", th.getClass().getSimpleName());
            }
        }

        private void configureCenteredHeaderIcon(ImageButton imageButton, int i, int i2) {
            if (imageButton == null) {
                return;
            }
            imageButton.setImageResource(i);
            imageButton.setBackgroundResource(R.drawable.nav_button_background);
            imageButton.setScaleType(ImageView.ScaleType.CENTER);
            imageButton.setPadding(0, 0, 0, 0);
            imageButton.setMinimumWidth(0);
            imageButton.setMinimumHeight(0);
            imageButton.setStateListAnimator(null);
            imageButton.setImageTintList(ColorStateList.valueOf(getColor(i2)));
        }

        private void configureErrorActionButton(Button button, boolean z, int i) {
            if (button == null) {
                return;
            }
            UiButtons.normalizeText(button);
            button.setGravity(17);
            button.setTextAlignment(4);
            button.setTextSize(2, 13.0f);
            button.setPadding(dp(10), 0, dp(10), 0);
            button.setEnabled(true);
            button.setAlpha(1.0f);
            button.setTextColor(getColor(z ? R.color.hcf_on_accent : R.color.hcf_cyan_bright));
            button.setBackgroundResource(z ? R.drawable.error_primary_button_background : R.drawable.error_secondary_button_background);
            FaIcons.applyStart(button, i);
            button.setCompoundDrawablePadding(dp(7));
            button.setGravity(17);
        }

        private void configureDrawerSecurityIcon() {
            ImageButton imageButton = this.drawerSecurity;
            if (imageButton == null) {
                return;
            }
            imageButton.setImageResource(R.drawable.fa_shield);
            this.drawerSecurity.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            this.drawerSecurity.setPadding(dp(7), dp(7), dp(7), dp(7));
            this.drawerSecurity.setMinimumWidth(0);
            this.drawerSecurity.setMinimumHeight(0);
            this.drawerSecurity.setStateListAnimator(null);
            this.drawerSecurity.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan_bright)));
        }

        private void applyAmoledSurfaces() {
            if (ThemeManager.isAmoled(this)) {
                try {
                    View findViewById = findViewById(R.id.rootFrame);
                    if (findViewById != null) {
                        findViewById.setBackgroundColor(-16777216);
                    }
                    View view = this.statusOverlay;
                    if (view != null) {
                        view.setBackgroundColor(-16777216);
                    }
                    WebView webView = this.webView;
                    if (webView != null) {
                        webView.setBackgroundColor(-16777216);
                    }
                    View view2 = this.topAppBar;
                    if (view2 != null) {
                        view2.setBackgroundColor(-16777216);
                    }
                    View view3 = this.urlBar;
                    if (view3 != null) {
                        view3.setBackgroundColor(-16777216);
                    }
                    View view4 = this.drawerPanel;
                    if (view4 != null) {
                        view4.setBackgroundColor(Color.rgb(3, 5, 7));
                    }
                } catch (Throwable unused) {
                }
            }
        }

        private void validateCriticalViews() {
            if (this.webView == null || this.pageProgress == null || this.statusOverlay == null || this.startupStateContainer == null || this.errorStateScroll == null || this.statusTitle == null || this.statusSubtitle == null || this.errorActions == null || this.retryButton == null || this.alternateButton == null || this.errorCodeText == null || this.errorDetailsButton == null || this.errorSupportButton == null || this.errorStatusText == null || this.errorHeroText == null || this.errorTitleText == null || this.errorMessageText == null || this.errorTechnicalText == null) {
                throw new IllegalStateException("Main native layout is incomplete");
            }
        }

        private void scheduleDeferredNativeSetup() {
            WebView webView = this.webView;
            if (webView == null) {
                return;
            }
            webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda5
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m84xcc15c2f();
                }
            }, 2500L);
        }

        /* renamed from: lambda$scheduleDeferredNativeSetup$1$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m84xcc15c2f() {
            try {
                NotificationHelper.createChannel(this);
                NotificationSyncScheduler.apply(this);
                UpdateScheduler.apply(this);
                UpdateAutomation.maybeCheck(this, false, new UpdateAutomation.Listener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda65
                    @Override // com.harleytg.forum.dev.UpdateAutomation.Listener
                    public final void onFinished(UpdateChecker.Release release, boolean z, String str) {
                        MainActivity.this.m83xf91988ae(release, z, str);
                    }
                });
                TelemetryService.handlePendingCrash(this);
                AppLogger.info(this, "deferred_native_setup", "ok");
            } catch (Throwable th) {
                AppLogger.error(this, "deferred_native_setup", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            }
        }

        /* renamed from: lambda$scheduleDeferredNativeSetup$0$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m83xf91988ae(UpdateChecker.Release release, boolean z, String str) {
            if (!z || release == null || isFinishing() || isDestroyed()) {
                return;
            }
            showBetaUpdateAvailableDialog(release);
        }

        private void showCrashSafeScreen(Throwable th) {
            try {
                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(1);
                linearLayout.setGravity(17);
                linearLayout.setPadding(dp(24), dp(24), dp(24), dp(24));
                linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
                TextView textView = new TextView(this);
                textView.setText("Harley's Clan Forum • Recovery Mode");
                textView.setTextColor(getColor(R.color.hcf_cyan_bright));
                textView.setTextSize(20.0f);
                textView.setGravity(17);
                linearLayout.addView(textView);
                TextView textView2 = new TextView(this);
                textView2.setText("1.0 • Development / Beta\nThe native shell hit a startup problem, so the app stayed open instead of crashing.\n\n" + th.getClass().getSimpleName());
                textView2.setTextColor(getColor(R.color.hcf_text));
                textView2.setTextSize(13.0f);
                textView2.setGravity(17);
                textView2.setPadding(0, dp(14), 0, dp(18));
                linearLayout.addView(textView2);
                Button button = new Button(this);
                UiButtons.normalizeText(button);
                button.setTextColor(getColor(R.color.hcf_cyan_bright));
                button.setBackgroundResource(R.drawable.button_background);
                button.setText("Retry App Start");
                button.setAllCaps(false);
                button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda70
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m90lambda$showCrashSafeScreen$2$comharleytgforumdevMainActivity(view);
                    }
                });
                linearLayout.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
                Button button2 = new Button(this);
                UiButtons.normalizeText(button2);
                button2.setTextColor(getColor(R.color.hcf_cyan_bright));
                button2.setBackgroundResource(R.drawable.button_background);
                button2.setText("Open App Settings");
                button2.setAllCaps(false);
                button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda71
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m91lambda$showCrashSafeScreen$3$comharleytgforumdevMainActivity(view);
                    }
                });
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(52));
                layoutParams.topMargin = dp(10);
                linearLayout.addView(button2, layoutParams);
                Button button3 = new Button(this);
                UiButtons.normalizeText(button3);
                button3.setTextColor(getColor(R.color.hcf_cyan_bright));
                button3.setBackgroundResource(R.drawable.button_background);
                button3.setText("Clear Web Cache");
                button3.setAllCaps(false);
                button3.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda72
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m92lambda$showCrashSafeScreen$4$comharleytgforumdevMainActivity(view);
                    }
                });
                LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, dp(52));
                layoutParams2.topMargin = dp(8);
                linearLayout.addView(button3, layoutParams2);
                Button button4 = new Button(this);
                UiButtons.normalizeText(button4);
                button4.setTextColor(getColor(R.color.hcf_cyan_bright));
                button4.setBackgroundResource(R.drawable.button_background);
                button4.setText("Open Diagnostics & Logs");
                button4.setAllCaps(false);
                button4.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda73
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m93lambda$showCrashSafeScreen$5$comharleytgforumdevMainActivity(view);
                    }
                });
                LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-1, dp(52));
                layoutParams3.topMargin = dp(8);
                linearLayout.addView(button4, layoutParams3);
                Button button5 = new Button(this);
                UiButtons.normalizeText(button5);
                button5.setTextColor(getColor(R.color.hcf_cyan_bright));
                button5.setBackgroundResource(R.drawable.button_background);
                button5.setText("Reset App UI Only");
                button5.setAllCaps(false);
                button5.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda74
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m94lambda$showCrashSafeScreen$6$comharleytgforumdevMainActivity(view);
                    }
                });
                LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(-1, dp(52));
                layoutParams4.topMargin = dp(8);
                linearLayout.addView(button5, layoutParams4);
                setContentView(linearLayout);
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$showCrashSafeScreen$2$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m90lambda$showCrashSafeScreen$2$comharleytgforumdevMainActivity(View view) {
            recreate();
        }

        /* renamed from: lambda$showCrashSafeScreen$3$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m91lambda$showCrashSafeScreen$3$comharleytgforumdevMainActivity(View view) {
            try {
                startActivity(new Intent(this, (Class<?>) HcfSubActivities.SettingsActivity.class));
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$showCrashSafeScreen$4$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m92lambda$showCrashSafeScreen$4$comharleytgforumdevMainActivity(View view) {
            try {
                WebView webView = new WebView(this);
                webView.clearCache(true);
                webView.clearHistory();
                webView.destroy();
                Toast.makeText(this, "Web cache cleared.", 0).show();
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$showCrashSafeScreen$5$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m93lambda$showCrashSafeScreen$5$comharleytgforumdevMainActivity(View view) {
            try {
                startActivity(new Intent(this, (Class<?>) HcfSubActivities.LogsActivity.class));
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$showCrashSafeScreen$6$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m94lambda$showCrashSafeScreen$6$comharleytgforumdevMainActivity(View view) {
            try {
                this.prefs.edit().remove("show_url_bar").remove("compact_header").remove("show_bottom_nav").remove("app_theme").remove("performance_mode").remove("performance_profile").remove("native_accent").remove("ui_revamp_version").apply();
                UiPreferences.migrate(this);
                Toast.makeText(this, "App UI reset. Forum login and cookies were kept.", 1).show();
                recreate();
            } catch (Throwable unused) {
            }
        }

        private void bindViews() {
            this.webView = (WebView) findViewById(R.id.webView);
            this.pageProgress = (ProgressBar) findViewById(R.id.pageProgress);
            this.statusOverlay = findViewById(R.id.statusOverlay);
            this.startupStateContainer = findViewById(R.id.startupStateContainer);
            this.errorStateScroll = findViewById(R.id.errorStateScroll);
            this.statusTitle = (TextView) findViewById(R.id.statusTitle);
            this.statusSubtitle = (TextView) findViewById(R.id.statusSubtitle);
            this.errorStatusText = (TextView) findViewById(R.id.errorStatusText);
            this.errorHeroText = (TextView) findViewById(R.id.errorHeroText);
            this.errorTitleText = (TextView) findViewById(R.id.errorTitleText);
            this.errorMessageText = (TextView) findViewById(R.id.errorMessageText);
            this.errorTechnicalText = (TextView) findViewById(R.id.errorTechnicalText);
            this.errorActions = (LinearLayout) findViewById(R.id.errorActions);
            this.retryButton = (Button) findViewById(R.id.retryButton);
            this.alternateButton = (Button) findViewById(R.id.alternateButton);
            this.errorCodeText = (TextView) findViewById(R.id.errorCodeText);
            this.errorDetailsButton = (Button) findViewById(R.id.errorDetailsButton);
            this.errorSupportButton = (Button) findViewById(R.id.errorSupportButton);
            this.drawerButton = (ImageButton) findViewById(R.id.drawerButton);
            this.reloadButton = (ImageButton) findViewById(R.id.reloadButton);
            this.urlBackButton = (ImageButton) findViewById(R.id.urlBackButton);
            this.urlHomeButton = (ImageButton) findViewById(R.id.urlHomeButton);
            this.copyUrlButton = (ImageButton) findViewById(R.id.copyUrlButton);
            this.topAppBar = findViewById(R.id.topAppBar);
            this.appHeaderLogo = (ImageView) findViewById(R.id.appHeaderLogo);
            this.appHeaderTitle = (TextView) findViewById(R.id.appHeaderTitle);
            this.appHeaderSubtitle = (TextView) findViewById(R.id.appHeaderSubtitle);
            this.liveStatusBadge = (TextView) findViewById(R.id.liveStatusBadge);
            this.headerNotificationsButton = (ImageButton) findViewById(R.id.headerNotificationsButton);
            this.headerNotificationCountBadge = (TextView) findViewById(R.id.headerNotificationCountBadge);
            this.urlBar = findViewById(R.id.urlBar);
            this.urlBarInner = findViewById(R.id.urlBarInner);
            this.secureForumLabel = (TextView) findViewById(R.id.secureForumLabel);
            this.currentUrlText = (EditText) findViewById(R.id.currentUrlText);
            this.hostBadge = (TextView) findViewById(R.id.hostBadge);
            this.drawerScrim = findViewById(R.id.drawerScrim);
            this.drawerPanel = findViewById(R.id.drawerPanel);
            this.drawerHostText = (TextView) findViewById(R.id.drawerHostText);
            this.drawerIdentityCard = findViewById(R.id.drawerIdentityCard);
            this.drawerIdentityAvatar = (ImageView) findViewById(R.id.drawerIdentityAvatar);
            this.drawerIdentityText = (TextView) findViewById(R.id.drawerIdentityText);
            this.drawerIdentityUsername = (TextView) findViewById(R.id.drawerIdentityUsername);
            this.drawerIdentityMeta = (TextView) findViewById(R.id.drawerIdentityMeta);
            this.drawerLinkedAccounts = findViewById(R.id.drawerLinkedAccounts);
            this.drawerProviderEmail = (TextView) findViewById(R.id.drawerProviderEmail);
            this.drawerProviderDiscord = (TextView) findViewById(R.id.drawerProviderDiscord);
            this.drawerSecurity = (ImageButton) findViewById(R.id.drawerSecurity);
            this.welcomeBanner = (TextView) findViewById(R.id.welcomeBanner);
            this.bottomNav = findViewById(R.id.bottomNav);
            this.bottomHome = (TextView) findViewById(R.id.bottomHome);
            this.bottomBrowse = (TextView) findViewById(R.id.bottomBrowse);
            this.bottomCreate = (TextView) findViewById(R.id.bottomCreate);
            this.bottomAlerts = (TextView) findViewById(R.id.bottomAlerts);
            this.bottomProfile = (TextView) findViewById(R.id.bottomProfile);
            this.drawerHome = (Button) findViewById(R.id.drawerHome);
            this.drawerSwitchHost = (Button) findViewById(R.id.drawerSwitchHost);
            this.drawerOpenExternal = (Button) findViewById(R.id.drawerOpenExternal);
            this.drawerShare = (Button) findViewById(R.id.drawerShare);
            this.drawerNotifications = (Button) findViewById(R.id.drawerNotifications);
            this.drawerNotificationCountBadge = (TextView) findViewById(R.id.drawerNotificationCountBadge);
            this.drawerSettings = (Button) findViewById(R.id.drawerSettings);
            this.drawerLogs = (Button) findViewById(R.id.drawerLogs);
            this.drawerSupport = (Button) findViewById(R.id.drawerSupport);
            TextView textView = (TextView) findViewById(R.id.drawerVersionText);
            this.drawerVersionText = textView;
            if (textView != null) {
                textView.setText(BuildInfo.DEVELOPMENT_BUILD_LABEL);
            }
            this.startupProgress = (ProgressBar) findViewById(R.id.startupProgress);
        }

        private void showPageActionsDialog() {
            final String home;
            String currentUrlString = currentUrlString();
            if (currentUrlString == null || currentUrlString.trim().isEmpty()) {
                String str = this.activeHost;
                if (str == null) {
                    str = chooseInitialHost();
                }
                home = ForumUrlRouter.home(str);
            } else {
                home = currentUrlString.trim();
            }
            try {
                new AlertDialog.Builder(this).setTitle("Page Actions").setItems(new String[]{"Open in browser", "Share page", "Copy link"}, new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda23
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.m100x5fbf765e(home, dialogInterface, i);
                    }
                }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
            } catch (Throwable th) {
                AppLogger.warn(this, "page_actions", th.getClass().getSimpleName());
                openCurrentPageExternally();
            }
        }

        /* renamed from: lambda$showPageActionsDialog$7$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m100x5fbf765e(String str, DialogInterface dialogInterface, int i) {
            if (i == 0) {
                openCurrentPageExternally();
                return;
            }
            if (i == 1) {
                shareCurrentPage();
                return;
            }
            if (i == 2) {
                try {
                    ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
                    if (clipboardManager != null) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Harley's Clan Forum link", str));
                        Toast.makeText(this, "Forum link copied.", 0).show();
                    }
                } catch (Throwable unused) {
                    Toast.makeText(this, "Could not copy this link.", 0).show();
                }
            }
        }

        private void openSupportEmail() {
            try {
                startActivity(new Intent(this, (Class<?>) HcfSubActivities.SupportContactActivity.class));
                AppLogger.info(this, "support_contact_open", "drawer");
            } catch (Throwable th) {
                Toast.makeText(this, "Unable to open Contact Support.", 1).show();
                AppLogger.error(this, "support_contact_open", th.getClass().getSimpleName());
            }
        }

        private void configureWebView() {
            WebSettings settings = this.webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setCacheMode(-1);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(true);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setGeolocationEnabled(false);
            settings.setSaveFormData(false);
            settings.setMixedContentMode(1);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(false);
            settings.setDefaultTextEncodingName("UTF-8");
            settings.setUserAgentString(BuildInfo.userAgent(settings.getUserAgentString()));
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    settings.setForceDark(0);
                } catch (Throwable unused) {
                }
            }
            this.webView.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            WebView.setWebContentsDebuggingEnabled(false);
            this.webView.removeJavascriptInterface("searchBoxJavaBridge_");
            this.webView.removeJavascriptInterface("accessibility");
            this.webView.removeJavascriptInterface("accessibilityTraversal");
            if (Build.VERSION.SDK_INT >= 27) {
                try {
                    WebView.startSafeBrowsing(this, new ValueCallback() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda30
                        @Override // android.webkit.ValueCallback
                        public final void onReceiveValue(Object obj) {
                            MainActivity.this.m70lambda$configureWebView$8$comharleytgforumdevMainActivity((Boolean) obj);
                        }
                    });
                } catch (Throwable th) {
                    AppLogger.warn(this, "safe_browsing", th.getClass().getSimpleName());
                }
            }
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(this.webView, false);
            this.webView.addJavascriptInterface(new AppMessageBridge(), "HCFNative");
            this.webView.setWebViewClient(new HcfWebViewClient());
            this.webView.setWebChromeClient(new HcfChromeClient());
            try {
                this.webView.setRendererPriorityPolicy(2, false);
            } catch (Throwable unused2) {
            }
            this.webView.setDownloadListener(new DownloadListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda31
                @Override // android.webkit.DownloadListener
                public final void onDownloadStart(String str, String str2, String str3, String str4, long j) {
                    MainActivity.this.m71lambda$configureWebView$9$comharleytgforumdevMainActivity(str, str2, str3, str4, j);
                }
            });
            configurePullToRefresh();
            applyPerformanceMode();
            applySavedAccent();
        }

        /* renamed from: lambda$configureWebView$8$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m70lambda$configureWebView$8$comharleytgforumdevMainActivity(Boolean bool) {
            AppLogger.info(this, "safe_browsing", Boolean.TRUE.equals(bool) ? "ready" : "unavailable");
        }

        private void configureActions() {
            this.drawerButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda38
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m39lambda$configureActions$10$comharleytgforumdevMainActivity(view);
                }
            });
            this.drawerScrim.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda49
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m40lambda$configureActions$11$comharleytgforumdevMainActivity(view);
                }
            });
            this.reloadButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda56
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m41lambda$configureActions$12$comharleytgforumdevMainActivity(view);
                }
            });
            ImageButton imageButton = this.urlHomeButton;
            if (imageButton != null) {
                imageButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda57
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m42lambda$configureActions$13$comharleytgforumdevMainActivity(view);
                    }
                });
            }
            this.copyUrlButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda58
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m43lambda$configureActions$14$comharleytgforumdevMainActivity(view);
                }
            });
            configureEditableAddressBar();
            this.headerNotificationsButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda59
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m44lambda$configureActions$15$comharleytgforumdevMainActivity(view);
                }
            });
            this.liveStatusBadge.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda60
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m45lambda$configureActions$16$comharleytgforumdevMainActivity(view);
                }
            });
            this.bottomHome.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda61
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m46lambda$configureActions$17$comharleytgforumdevMainActivity(view);
                }
            });
            this.bottomBrowse.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda62
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m47lambda$configureActions$18$comharleytgforumdevMainActivity(view);
                }
            });
            this.bottomCreate.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda63
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m48lambda$configureActions$19$comharleytgforumdevMainActivity(view);
                }
            });
            this.bottomAlerts.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda39
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m49lambda$configureActions$20$comharleytgforumdevMainActivity(view);
                }
            });
            this.bottomProfile.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda40
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m50lambda$configureActions$21$comharleytgforumdevMainActivity(view);
                }
            });
            this.drawerHome.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda41
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m51lambda$configureActions$22$comharleytgforumdevMainActivity(view);
                }
            });
            this.drawerSwitchHost.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda42
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m52lambda$configureActions$23$comharleytgforumdevMainActivity(view);
                }
            });
            this.drawerOpenExternal.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda43
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m53lambda$configureActions$24$comharleytgforumdevMainActivity(view);
                }
            });
            Button button = this.drawerShare;
            if (button != null) {
                button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda44
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m54lambda$configureActions$25$comharleytgforumdevMainActivity(view);
                    }
                });
            }
            View view = this.drawerIdentityCard;
            if (view != null) {
                view.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda45
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view2) {
                        MainActivity.this.m55lambda$configureActions$26$comharleytgforumdevMainActivity(view2);
                    }
                });
            }
            ImageButton imageButton2 = this.drawerSecurity;
            if (imageButton2 != null) {
                imageButton2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda46
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view2) {
                        MainActivity.this.m56lambda$configureActions$27$comharleytgforumdevMainActivity(view2);
                    }
                });
            }
            this.drawerNotifications.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda47
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m57lambda$configureActions$28$comharleytgforumdevMainActivity(view2);
                }
            });
            this.drawerSettings.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda48
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m58lambda$configureActions$29$comharleytgforumdevMainActivity(view2);
                }
            });
            this.drawerLogs.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda50
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m59lambda$configureActions$30$comharleytgforumdevMainActivity(view2);
                }
            });
            Button button2 = this.drawerSupport;
            if (button2 != null) {
                button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda51
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view2) {
                        MainActivity.this.m60lambda$configureActions$31$comharleytgforumdevMainActivity(view2);
                    }
                });
            }
            this.retryButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda52
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m61lambda$configureActions$32$comharleytgforumdevMainActivity(view2);
                }
            });
            this.alternateButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda53
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m62lambda$configureActions$33$comharleytgforumdevMainActivity(view2);
                }
            });
            this.errorDetailsButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda54
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m63lambda$configureActions$34$comharleytgforumdevMainActivity(view2);
                }
            });
            this.errorSupportButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda55
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.this.m64lambda$configureActions$35$comharleytgforumdevMainActivity(view2);
                }
            });
        }

        /* renamed from: lambda$configureActions$10$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m39lambda$configureActions$10$comharleytgforumdevMainActivity(View view) {
            openDrawer();
        }

        /* renamed from: lambda$configureActions$11$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m40lambda$configureActions$11$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
        }

        /* renamed from: lambda$configureActions$12$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m41lambda$configureActions$12$comharleytgforumdevMainActivity(View view) {
            reloadCurrentPage();
        }

        /* renamed from: lambda$configureActions$13$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m42lambda$configureActions$13$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/", "url_home");
        }

        /* renamed from: lambda$configureActions$14$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m43lambda$configureActions$14$comharleytgforumdevMainActivity(View view) {
            copyCurrentUrl();
        }

        /* renamed from: lambda$configureActions$15$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m44lambda$configureActions$15$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/notifications", "header_alerts");
        }

        /* renamed from: lambda$configureActions$16$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m45lambda$configureActions$16$comharleytgforumdevMainActivity(View view) {
            HcfNotifications.InstantNotificationService.requestImmediateSync(this);
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.poke();
            }
            updateLiveState("SYNCING");
            Toast.makeText(this, "Syncing forum now…", 0).show();
        }

        /* renamed from: lambda$configureActions$17$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m46lambda$configureActions$17$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/", "bottom_home");
        }

        /* renamed from: lambda$configureActions$18$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m47lambda$configureActions$18$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/all", "bottom_browse");
        }

        /* renamed from: lambda$configureActions$19$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m48lambda$configureActions$19$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/compose", "bottom_create");
        }

        /* renamed from: lambda$configureActions$20$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m49lambda$configureActions$20$comharleytgforumdevMainActivity(View view) {
            navigateForumPath("/notifications", "bottom_alerts");
        }

        /* renamed from: lambda$configureActions$21$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m50lambda$configureActions$21$comharleytgforumdevMainActivity(View view) {
            openCurrentProfile();
        }

        /* renamed from: lambda$configureActions$22$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m51lambda$configureActions$22$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            String home = ForumUrlRouter.home(str);
            updateUrlChrome(home);
            this.webView.loadUrl(home);
            AppLogger.info(this, "drawer_home", AppLogger.safeUrl(home));
        }

        /* renamed from: lambda$configureActions$23$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m52lambda$configureActions$23$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            String str = "forum.harleytg.com".equals(this.activeHost) ? "harleysclan.freeflarum.com" : "forum.harleytg.com";
            if ("forum.harleytg.com".equals(str)) {
                this.prefs.edit().remove("fallback_until").apply();
            }
            switchHost(str, currentForumUri());
        }

        /* renamed from: lambda$configureActions$24$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m53lambda$configureActions$24$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            showPageActionsDialog();
        }

        /* renamed from: lambda$configureActions$25$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m54lambda$configureActions$25$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            shareCurrentPage();
        }

        /* renamed from: lambda$configureActions$26$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m55lambda$configureActions$26$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            startActivity(new Intent(this, (Class<?>) HcfSubActivities.IdentityActivity.class));
            AppLogger.info(this, "drawer_identity", ForumIdentity.load(this).loggedIn ? "member" : "guest");
        }

        /* renamed from: lambda$configureActions$27$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m56lambda$configureActions$27$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            openAccountSecurity();
        }

        /* renamed from: lambda$configureActions$28$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m57lambda$configureActions$28$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            StringBuilder sb = new StringBuilder("https://");
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            sb.append(str);
            sb.append("/notifications");
            String sb2 = sb.toString();
            updateUrlChrome(sb2);
            this.webView.loadUrl(sb2);
            AppLogger.info(this, "drawer", "forum_notifications");
        }

        /* renamed from: lambda$configureActions$29$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m58lambda$configureActions$29$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            openAppSettings("drawer");
        }

        /* renamed from: lambda$configureActions$30$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m59lambda$configureActions$30$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            AppLogger.info(this, "drawer_logs", "tap");
            startActivity(new Intent(this, (Class<?>) HcfSubActivities.LogsActivity.class));
        }

        /* renamed from: lambda$configureActions$31$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m60lambda$configureActions$31$comharleytgforumdevMainActivity(View view) {
            closeDrawer();
            openSupportEmail();
        }

        /* renamed from: lambda$configureActions$32$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m61lambda$configureActions$32$comharleytgforumdevMainActivity(View view) {
            String home;
            Uri uri = this.lastErrorUri;
            if (uri != null && ForumUrlRouter.isForumUrl(uri)) {
                String str = this.activeHost;
                if (str == null) {
                    str = chooseInitialHost();
                }
                home = ForumUrlRouter.equivalentOnHost(uri, str);
            } else {
                WebView webView = this.webView;
                String url = webView == null ? null : webView.getUrl();
                Uri parse = url != null ? Uri.parse(url) : null;
                if (parse != null && ForumUrlRouter.isForumUrl(parse)) {
                    String str2 = this.activeHost;
                    if (str2 == null) {
                        str2 = chooseInitialHost();
                    }
                    home = ForumUrlRouter.equivalentOnHost(parse, str2);
                } else {
                    String str3 = this.activeHost;
                    if (str3 == null) {
                        str3 = chooseInitialHost();
                    }
                    home = ForumUrlRouter.home(str3);
                }
            }
            clearErrorStateForNavigation("manual_retry");
            updateUrlChrome(home);
            AppLogger.info(this, "manual_retry", AppLogger.safeUrl(home));
            this.webView.loadUrl(home);
        }

        /* renamed from: lambda$configureActions$33$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m62lambda$configureActions$33$comharleytgforumdevMainActivity(View view) {
            String str = "forum.harleytg.com".equals(this.activeHost) ? "harleysclan.freeflarum.com" : "forum.harleytg.com";
            if ("forum.harleytg.com".equals(str)) {
                this.prefs.edit().remove("fallback_until").apply();
            }
            AppLogger.info(this, "manual_host_switch", this.activeHost + " -> " + str);
            switchHost(str, currentForumUri());
        }

        /* renamed from: lambda$configureActions$34$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m63lambda$configureActions$34$comharleytgforumdevMainActivity(View view) {
            showLastErrorDetails();
        }

        /* renamed from: lambda$configureActions$35$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m64lambda$configureActions$35$comharleytgforumdevMainActivity(View view) {
            openSupportEmail();
        }

        private void navigateForumPath(String str, String str2) {
            if (this.webView == null) {
                return;
            }
            String str3 = "/";
            String trim = str == null ? "/" : str.trim();
            if (trim.startsWith("/") && !trim.startsWith("//")) {
                str3 = trim;
            }
            String str4 = this.activeHost;
            if (str4 == null) {
                str4 = chooseInitialHost();
            }
            String str5 = "https://" + str4 + str3;
            closeDrawer();
            updateUrlChrome(str5);
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.reset();
            }
            if (str3.startsWith("/notifications")) {
                HcfNotifications.InstantNotificationService.requestImmediateSync(this);
            }
            this.webView.loadUrl(str5);
            AppLogger.info(this, "quick_navigation", str2 + " | " + AppLogger.safeUrl(str5));
        }

        private void openCurrentProfile() {
            ForumIdentity.Snapshot load = ForumIdentity.load(this);
            if (!load.loggedIn) {
                navigateForumPath("/login", "bottom_profile_sign_in");
                return;
            }
            String str = !load.slug.isEmpty() ? load.slug : load.username;
            if (str == null || str.trim().isEmpty()) {
                Toast.makeText(this, "Your profile is still syncing.", 0).show();
                HcfNotifications.InstantNotificationService.requestImmediateSync(this);
            } else {
                navigateForumPath("/u/" + Uri.encode(str.trim()), "bottom_profile");
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void updateLiveState(String str) {
            if (str == null) {
                str = "SYNCING";
            }
            this.liveState = str;
            if (this.liveStatusBadge == null) {
                return;
            }
            if ("LIVE".equals(str)) {
                this.liveStatusBadge.setText("Live");
                this.liveStatusBadge.setTextColor(getColor(R.color.hcf_cyan_bright));
            } else if ("WAITING".equals(this.liveState)) {
                this.liveStatusBadge.setText("Retry");
                this.liveStatusBadge.setTextColor(getColor(R.color.hcf_yellow));
            } else if ("PAUSED".equals(this.liveState)) {
                this.liveStatusBadge.setText("Ready");
                this.liveStatusBadge.setTextColor(getColor(R.color.hcf_muted));
            } else if ("OFFLINE".equals(this.liveState)) {
                this.liveStatusBadge.setText("Off");
                this.liveStatusBadge.setTextColor(getColor(R.color.hcf_yellow));
            } else {
                this.liveStatusBadge.setText("Sync");
                this.liveStatusBadge.setTextColor(getColor(R.color.hcf_meta));
            }
            updateHeaderSubtitle();
        }

        private void updateHeaderSubtitle() {
            String str;
            if (this.appHeaderSubtitle == null) {
                return;
            }
            String str2 = this.activeHost;
            if (str2 == null) {
                str2 = chooseInitialHost();
            }
            String str3 = "forum.harleytg.com".equalsIgnoreCase(str2) ? "Primary" : "Backup";
            if ("OFFLINE".equals(this.liveState)) {
                str = "Offline";
            } else if ("WAITING".equals(this.liveState)) {
                str = "Reconnecting";
            } else {
                str = "SYNCING".equals(this.liveState) ? "Syncing" : "Live forum";
            }
            this.appHeaderSubtitle.setText(str + " • " + str3);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void updateNotificationChrome(int i) {
            String str;
            String str2;
            String str3;
            int max = Math.max(0, i);
            String valueOf = max > 999 ? "999+" : String.valueOf(max);
            ImageButton imageButton = this.headerNotificationsButton;
            if (imageButton != null) {
                if (max > 0) {
                    str3 = max + " new forum notifications";
                } else {
                    str3 = "Forum notifications";
                }
                imageButton.setContentDescription(str3);
            }
            TextView textView = this.headerNotificationCountBadge;
            if (textView != null) {
                textView.setText(valueOf);
                this.headerNotificationCountBadge.setVisibility(max > 0 ? 0 : 8);
                TextView textView2 = this.headerNotificationCountBadge;
                if (max > 0) {
                    str2 = max + " unread forum notifications";
                } else {
                    str2 = "No unread forum notifications";
                }
                textView2.setContentDescription(str2);
            }
            TextView textView3 = this.bottomAlerts;
            if (textView3 != null) {
                if (max > 0) {
                    str = valueOf + "\nAlerts";
                } else {
                    str = "Alerts";
                }
                textView3.setText(str);
                this.bottomAlerts.setTextColor(getColor(max > 0 ? R.color.hcf_cyan_bright : R.color.hcf_muted));
            }
            Button button = this.drawerNotifications;
            if (button != null) {
                String str4 = "Notifications";
                button.setText("Notifications");
                Button button2 = this.drawerNotifications;
                if (max > 0) {
                    str4 = "Notifications, " + valueOf + " unread";
                }
                button2.setContentDescription(str4);
            }
            TextView textView4 = this.drawerNotificationCountBadge;
            if (textView4 != null) {
                textView4.setText(valueOf);
                this.drawerNotificationCountBadge.setVisibility(max <= 0 ? 8 : 0);
            }
        }

        private void registerNotificationEvents() {
            if (this.notificationReceiverRegistered) {
                return;
            }
            try {
                IntentFilter intentFilter = new IntentFilter("com.harleytg.forum.dev.NOTIFICATION_EVENT");
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(this.notificationEventReceiver, intentFilter, 4);
                } else {
                    registerReceiver(this.notificationEventReceiver, intentFilter);
                }
                this.notificationReceiverRegistered = true;
            } catch (Throwable th) {
                AppLogger.warn(this, "notification_event_receiver", th.getClass().getSimpleName());
            }
        }

        private void unregisterNotificationEvents() {
            if (this.notificationReceiverRegistered) {
                try {
                    unregisterReceiver(this.notificationEventReceiver);
                } catch (Throwable unused) {
                }
                this.notificationReceiverRegistered = false;
            }
        }

        private void openAppSettings(String str) {
            AppLogger.info(this, "settings_button", str);
            startActivity(new Intent(this, (Class<?>) HcfSubActivities.SettingsActivity.class));
        }

        private void applyChromePreferences() {
            applyHeaderDensity(true);
            TextView textView = this.liveStatusBadge;
            if (textView != null) {
                textView.setVisibility(8);
            }
            ImageButton imageButton = this.headerNotificationsButton;
            if (imageButton != null) {
                imageButton.setVisibility(0);
            }
            if (this.urlBar != null) {
                this.urlBar.setVisibility(this.prefs.getBoolean("show_url_bar", true) ? 0 : 8);
            }
            View view = this.bottomNav;
            if (view != null) {
                view.setVisibility(8);
            }
        }

        private void applyHeaderDensity(boolean z) {
            ViewGroup.LayoutParams layoutParams;
            float f;
            int i = z ? R.dimen.compact_app_header_height : R.dimen.app_header_height;
            int i2 = z ? R.dimen.compact_app_header_button : R.dimen.app_header_button;
            int i3 = z ? R.dimen.compact_app_header_logo : R.dimen.app_header_logo;
            int i4 = z ? R.dimen.compact_app_header_title_text : R.dimen.app_header_title_text;
            int i5 = z ? R.dimen.compact_url_bar_height : R.dimen.url_bar_height;
            int i6 = z ? R.dimen.compact_url_bar_inner_height : R.dimen.url_bar_inner_height;
            int i7 = z ? R.dimen.compact_url_copy_button : R.dimen.url_copy_button;
            int i8 = z ? R.dimen.compact_url_reload_button : R.dimen.url_reload_button;
            setViewHeight(this.topAppBar, i);
            setSquareSize(this.drawerButton, i2);
            setSquareSize(this.appHeaderLogo, i3);
            setSquareSize(this.headerNotificationsButton, i2);
            setTextSizeFromDimen(this.appHeaderTitle, i4);
            setViewHeight(this.urlBar, i5);
            setViewHeight(this.urlBarInner, i6);
            setSquareSize(this.urlBackButton, i8);
            setSquareSize(this.reloadButton, i8);
            setSquareSize(this.copyUrlButton, i7);
            setSquareSize(this.urlHomeButton, i8);
            boolean z2 = getResources().getConfiguration().orientation == 2;
            if (this.topAppBar != null) {
                int dp = dp(z ? 6 : 8);
                this.topAppBar.setPadding(dp, 0, dp, 0);
            }
            if (this.urlBar != null) {
                int dp2 = dp(z ? 6 : 8);
                int dp3 = dp(z ? 2 : z2 ? 3 : 4);
                this.urlBar.setPadding(dp2, dp3, dp2, dp3);
            }
            TextView textView = this.secureForumLabel;
            if (textView != null) {
                textView.setVisibility(z ? 8 : 0);
            }
            TextView textView2 = this.hostBadge;
            if (textView2 != null) {
                textView2.setTextSize(2, z ? 7.0f : 8.0f);
            }
            EditText editText = this.currentUrlText;
            if (editText != null) {
                if (z) {
                    f = z2 ? 9 : 10;
                } else {
                    f = 11.0f;
                }
                editText.setTextSize(2, f);
            }
            TextView textView3 = this.appHeaderSubtitle;
            if (textView3 != null) {
                textView3.setTextSize(2, 11.0f);
            }
            TextView textView4 = this.liveStatusBadge;
            if (textView4 == null || (layoutParams = textView4.getLayoutParams()) == null) {
                return;
            }
            layoutParams.height = dp(z ? 24 : 30);
            this.liveStatusBadge.setLayoutParams(layoutParams);
        }

        private void setViewHeight(View view, int i) {
            if (view == null || view.getLayoutParams() == null) {
                return;
            }
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.height = getResources().getDimensionPixelSize(i);
            view.setLayoutParams(layoutParams);
        }

        private void setSquareSize(View view, int i) {
            if (view == null || view.getLayoutParams() == null) {
                return;
            }
            int dimensionPixelSize = getResources().getDimensionPixelSize(i);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.width = dimensionPixelSize;
            layoutParams.height = dimensionPixelSize;
            view.setLayoutParams(layoutParams);
        }

        private void setTextSizeFromDimen(TextView textView, int i) {
            if (textView == null) {
                return;
            }
            textView.setTextSize(0, getResources().getDimension(i));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void reloadCurrentPage() {
            WebView webView = this.webView;
            if (webView == null) {
                return;
            }
            String url = webView.getUrl();
            Uri uri = this.lastErrorUri;
            if (uri != null && ForumUrlRouter.isForumUrl(uri)) {
                url = uri.toString();
            }
            if (url == null || url.trim().isEmpty() || !ForumUrlRouter.isForumUrl(Uri.parse(url))) {
                String str = this.activeHost;
                if (str == null) {
                    str = chooseInitialHost();
                }
                url = ForumUrlRouter.home(str);
            }
            AppLogger.info(this, "reload", AppLogger.safeUrl(url));
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.reset();
            }
            clearErrorStateForNavigation("reload");
            updateUrlChrome(url);
            this.webView.loadUrl(url);
        }

        private void applyLiveUpdatePreference() {
            if (this.liveUpdater == null) {
                return;
            }
            if (this.prefs.getBoolean("live_forum_updates", true)) {
                this.liveUpdater.start();
            } else {
                this.liveUpdater.stop();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleLiveChangeCandidate(final String str, final String str2) {
            if (this.webView == null || this.liveUpdater == null || !this.prefs.getBoolean("live_forum_updates", true)) {
                return;
            }
            View view = this.drawerPanel;
            if (view == null || view.getVisibility() != 0) {
                View view2 = this.statusOverlay;
                if (view2 == null || view2.getVisibility() != 0) {
                    try {
                        this.webView.evaluateJavascript("(function(){if(document.hidden)return 'busy';var a=document.activeElement;if(a&&((a.tagName==='INPUT')||(a.tagName==='TEXTAREA')||(a.tagName==='SELECT')||a.isContentEditable))return 'busy';var c=document.querySelector('.Composer');if(c&&c.offsetParent!==null)return 'busy';var m=document.querySelector('.ModalManager .Modal, .ModalManager .Modal-backdrop');if(m&&m.offsetParent!==null)return 'busy';return 'safe';})()", new ValueCallback() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda22
                            @Override // android.webkit.ValueCallback
                            public final void onReceiveValue(Object obj) {
                                MainActivity.this.m74xaae24114(str, str2, (String) obj);
                            }
                        });
                    } catch (Throwable th) {
                        AppLogger.warn(this, "live_update_safe_check", th.getClass().getSimpleName());
                    }
                }
            }
        }

        /* renamed from: lambda$handleLiveChangeCandidate$36$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m74xaae24114(String str, String str2, String str3) {
            if (str3 == null || !str3.contains("safe")) {
                return;
            }
            softSyncCurrentPage(str, str2);
        }

        private void softSyncCurrentPage(final String str, final String str2) {
            if (this.webView == null || this.liveUpdater == null) {
                return;
            }
            try {
                final String softSyncEndpoint = softSyncEndpoint(Uri.parse(currentUrlString()));
                if (softSyncEndpoint == null || softSyncEndpoint.isEmpty()) {
                    fallbackLiveReload(str, str2, "no soft-sync endpoint");
                    return;
                }
                try {
                    this.webView.evaluateJavascript("(async function(){try{var a=window.app;if(!a||!a.request||!a.store)return 'unsupported';var p=await a.request({method:'GET',url:'" + softSyncEndpoint.replace("\\", "\\\\").replace("'", "\\'") + "'});if(a.store.pushPayload)a.store.pushPayload(p);if(window.m&&m.redraw)m.redraw();return 'ok';}catch(e){return 'error';}})()", new ValueCallback() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda29
                        @Override // android.webkit.ValueCallback
                        public final void onReceiveValue(Object obj) {
                            MainActivity.this.m108x6628bf31(str, str2, softSyncEndpoint, (String) obj);
                        }
                    });
                } catch (Throwable th) {
                    AppLogger.warn(this, "live_update_soft_sync", th.getClass().getSimpleName());
                    fallbackLiveReload(str, str2, "soft-sync exception");
                }
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$softSyncCurrentPage$37$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m108x6628bf31(String str, String str2, String str3, String str4) {
            if (str4 == null || !str4.contains("ok")) {
                AppLogger.warn(this, "live_update", "soft-sync unavailable; safe reload fallback");
                fallbackLiveReload(str, str2, "soft-sync unavailable");
            } else {
                this.liveUpdater.acknowledge(str, str2);
                AppLogger.info(this, "live_update", "soft-sync " + AppLogger.safeUrl(str3));
            }
        }

        private void fallbackLiveReload(String str, String str2, String str3) {
            WebView webView = this.webView;
            if (webView == null || this.liveUpdater == null || this.liveReloadInProgress) {
                return;
            }
            this.liveReloadInProgress = true;
            this.pendingLiveScrollY = Math.max(0, webView.getScrollY());
            this.liveUpdater.acknowledge(str, str2);
            AppLogger.info(this, "live_update", "reload-fallback | " + str3);
            this.webView.reload();
        }

        private String softSyncEndpoint(Uri uri) {
            if (uri == null || !ForumUrlRouter.isForumUrl(uri)) {
                return null;
            }
            String host = uri.getHost();
            String path = uri.getPath() == null ? "/" : uri.getPath();
            String lowerCase = path.toLowerCase(Locale.US);
            String str = "https://" + host;
            if (lowerCase.startsWith("/d/")) {
                String substring = path.substring(3);
                int indexOf = substring.indexOf(47);
                if (indexOf >= 0) {
                    substring = substring.substring(0, indexOf);
                }
                int indexOf2 = substring.indexOf(45);
                if (indexOf2 >= 0) {
                    substring = substring.substring(0, indexOf2);
                }
                if (substring.matches("[0-9]+")) {
                    return str + "/api/discussions/" + substring + "?include=posts,posts.user";
                }
            }
            if (lowerCase.startsWith("/notifications")) {
                return str + "/api/notifications?page%5Blimit%5D=20";
            }
            if (!"/".equals(lowerCase) && !lowerCase.startsWith("/all") && !lowerCase.startsWith("/following") && !lowerCase.startsWith("/tags") && !lowerCase.startsWith("/t/")) {
                return null;
            }
            return str + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=20&include=user,lastPostedUser,tags";
        }

        private int parseJavascriptInt(String str) {
            if (str == null) {
                return -1;
            }
            try {
                return Integer.parseInt(str.replace("\"", "").trim());
            } catch (Throwable unused) {
                return -1;
            }
        }

        private void finishLiveRefresh(final WebView webView) {
            if (this.liveReloadInProgress) {
                final int i = this.pendingLiveScrollY;
                this.liveReloadInProgress = false;
                this.pendingLiveScrollY = -1;
                if (i <= 0) {
                    return;
                }
                webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda37
                    @Override // java.lang.Runnable
                    public final void run() {
                        webView.evaluateJavascript("window.scrollTo(0," + i + ");void(0);", null);
                    }
                }, 350L);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public String currentUrlString() {
            WebView webView = this.webView;
            String url = webView == null ? null : webView.getUrl();
            if (url != null && !url.trim().isEmpty()) {
                return url;
            }
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            return ForumUrlRouter.home(str);
        }

        private void copyCurrentUrl() {
            String currentUrlString = currentUrlString();
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Harley's Clan Forum URL", currentUrlString));
                Toast.makeText(this, "URL copied.", 0).show();
                AppLogger.info(this, "url_copy", AppLogger.safeUrl(currentUrlString));
            }
        }

        private void openCurrentPageExternally() {
            try {
                Uri parse = Uri.parse(currentUrlString());
                HcfIntentChooser.show(this, new Intent("android.intent.action.VIEW", parse), "Open in browser", "Choose an app for this forum page. Harley's Clan Forum is excluded to prevent a loop.", true);
                AppLogger.info(this, "url_open_external", AppLogger.safeUrl(parse.toString()));
            } catch (Throwable th) {
                Toast.makeText(this, "Unable to open this page externally.", 0).show();
                AppLogger.error(this, "url_open_external", th.getClass().getSimpleName());
            }
        }

        private void shareCurrentPage() {
            try {
                String currentUrlString = currentUrlString();
                Intent intent = new Intent("android.intent.action.SEND");
                intent.setType("text/plain");
                intent.putExtra("android.intent.extra.SUBJECT", "Harley's Clan Forum");
                intent.putExtra("android.intent.extra.TEXT", currentUrlString);
                HcfIntentChooser.showShare(this, intent, "Share forum page", "Copy the link or share it with another app.");
                AppLogger.info(this, "url_share", AppLogger.safeUrl(currentUrlString));
            } catch (Throwable th) {
                Toast.makeText(this, "Unable to share this page.", 0).show();
                AppLogger.error(this, "url_share", th.getClass().getSimpleName());
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        /* renamed from: enqueueForumDownload, reason: merged with bridge method [inline-methods] */
        public void m71lambda$configureWebView$9$comharleytgforumdevMainActivity(String str, String str2, String str3, String str4, long j) {
            Uri uri;
            try {
                uri = Uri.parse(str == null ? "" : str);
            } catch (Throwable unused) {
                uri = null;
            }
            if (uri == null || uri.getScheme() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                Toast.makeText(this, "This download link is not supported.", 0).show();
                return;
            }
            try {
                DownloadManager downloadManager = (DownloadManager) getSystemService("download");
                if (downloadManager == null) {
                    throw new IllegalStateException("DownloadManager unavailable");
                }
                String guessFileName = URLUtil.guessFileName(str, str3, str4);
                DownloadManager.Request allowedOverRoaming = new DownloadManager.Request(uri).setTitle(guessFileName).setDescription("Harley's Clan Forum download").setNotificationVisibility(1).setAllowedOverMetered(true).setAllowedOverRoaming(true);
                if (str4 != null && !str4.trim().isEmpty()) {
                    allowedOverRoaming.setMimeType(str4);
                }
                if (str2 != null && !str2.trim().isEmpty()) {
                    allowedOverRoaming.addRequestHeader("User-Agent", str2);
                }
                if (ForumUrlRouter.isForumUrl(uri)) {
                    String cookie = CookieManager.getInstance().getCookie("https://" + uri.getHost() + "/");
                    if (cookie != null && !cookie.trim().isEmpty()) {
                        allowedOverRoaming.addRequestHeader("Cookie", cookie);
                    }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        allowedOverRoaming.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessFileName);
                    } catch (Throwable unused2) {
                    }
                }
                long enqueue = downloadManager.enqueue(allowedOverRoaming);
                Toast.makeText(this, "Downloading " + guessFileName, 0).show();
                AppLogger.info(this, "forum_download", "id=" + enqueue + " | " + AppLogger.safeUrl(str));
            } catch (Throwable th) {
                AppLogger.error(this, "forum_download", th.getClass().getSimpleName());
                openExternal(uri);
            }
        }

        private void configurePullToRefresh() {
            if (this.webView == null) {
                return;
            }
            final float dp = dp(92);
            this.webView.setOnTouchListener(new View.OnTouchListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda64
                @Override // android.view.View.OnTouchListener
                public final boolean onTouch(View view, MotionEvent motionEvent) {
                    return MainActivity.this.m69x19894d87(dp, view, motionEvent);
                }
            });
        }

        /* renamed from: lambda$configurePullToRefresh$39$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ boolean m69x19894d87(float f, View view, MotionEvent motionEvent) {
            if (motionEvent == null) {
                return false;
            }
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.pullStartY = motionEvent.getY();
                this.pullFromTop = this.webView.getScrollY() <= 0;
            } else if (actionMasked == 1) {
                if (this.pullFromTop && this.webView.getScrollY() <= 0 && motionEvent.getY() - this.pullStartY >= f && System.currentTimeMillis() - this.lastPullRefreshAt > 1200) {
                    this.lastPullRefreshAt = System.currentTimeMillis();
                    showTransientBanner("Refreshing forum…");
                    HcfNotifications.InstantNotificationService.requestImmediateSync(this);
                    LiveForumUpdater liveForumUpdater = this.liveUpdater;
                    if (liveForumUpdater != null) {
                        liveForumUpdater.poke();
                    }
                    reloadCurrentPage();
                    AppLogger.info(this, "pull_to_refresh", AppLogger.safeUrl(this.webView.getUrl()));
                }
                this.pullFromTop = false;
            } else if (actionMasked == 3) {
                this.pullFromTop = false;
            }
            return false;
        }

        private void applyPerformanceMode() {
            if (this.webView == null) {
                return;
            }
            boolean equals = "performance".equals(PerformanceProfile.resolve(this, this.prefs));
            try {
                this.webView.setLayerType(2, null);
                this.webView.setOverScrollMode(equals ? 2 : 1);
                this.webView.setRendererPriorityPolicy(2, equals);
            } catch (Throwable unused) {
            }
        }

        private long motionDuration(long j) {
            return PerformanceProfile.motionDuration(this, this.prefs, j);
        }

        private void installPerformanceCss() {
            String str;
            String str2;
            if (this.webView == null || !isTrustedForumPage()) {
                return;
            }
            String resolve = PerformanceProfile.resolve(this, this.prefs);
            if ("performance".equals(resolve)) {
                str = "*{animation-duration:.001ms!important;animation-delay:0ms!important;transition-duration:.001ms!important;scroll-behavior:auto!important}.Modal,.Dropdown-menu,.Composer,.App-header{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}";
            } else if (!"balanced".equals(resolve)) {
                str = "";
            } else {
                str = ".Modal,.Dropdown-menu,.Composer,.App-header{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}html{scroll-behavior:auto!important}";
            }
            String replace = str.replace("\\", "\\\\").replace("'", "\\'");
            StringBuilder sb = new StringBuilder("(function(){try{var id='hcf-native-performance-style',x=document.getElementById(id);");
            if (str.isEmpty()) {
                str2 = "if(x&&x.parentNode)x.parentNode.removeChild(x);";
            } else {
                str2 = "if(!x){x=document.createElement('style');x.id=id;document.head&&document.head.appendChild(x);}if(x)x.textContent='" + replace + "';";
            }
            sb.append(str2);
            sb.append("}catch(e){}})();");
            try {
                this.webView.evaluateJavascript(sb.toString(), null);
            } catch (Throwable unused) {
            }
        }

        private void applySavedAccent() {
            int parseAccent;
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences == null || (parseAccent = parseAccent(sharedPreferences.getString("native_accent", ""))) == 0) {
                return;
            }
            applyAccentColor(parseAccent);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public int parseAccent(String str) {
            if (str == null) {
                return 0;
            }
            String trim = str.trim();
            try {
                if (trim.matches("#[0-9a-fA-F]{3}")) {
                    char charAt = trim.charAt(1);
                    char charAt2 = trim.charAt(2);
                    char charAt3 = trim.charAt(3);
                    trim = "#" + charAt + charAt + charAt2 + charAt2 + charAt3 + charAt3;
                }
                if (trim.matches("#[0-9a-fA-F]{6}")) {
                    return Color.parseColor(trim);
                }
            } catch (Throwable unused) {
            }
            return 0;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void applyAccentColor(int i) {
            try {
                ProgressBar progressBar = this.pageProgress;
                if (progressBar != null) {
                    progressBar.setProgressTintList(ColorStateList.valueOf(i));
                }
                if (this.drawerButton != null) {
                    this.drawerButton.setImageTintList(ColorStateList.valueOf(i));
                }
                if (this.reloadButton != null) {
                    this.reloadButton.setImageTintList(ColorStateList.valueOf(i));
                }
                TextView textView = this.hostBadge;
                if (textView != null) {
                    textView.setTextColor(i);
                }
            } catch (Throwable unused) {
            }
        }

        private void registerNetworkState() {
            if (this.networkCallbackRegistered) {
                return;
            }
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
                if (connectivityManager == null) {
                    return;
                }
                AnonymousClass3 anonymousClass3 = new AnonymousClass3();
                this.networkCallback = anonymousClass3;
                connectivityManager.registerDefaultNetworkCallback(anonymousClass3);
                this.networkCallbackRegistered = true;
                if (isNetworkAvailable()) {
                    return;
                }
                updateLiveState("OFFLINE");
            } catch (Throwable th) {
                AppLogger.warn(this, "network_callback", th.getClass().getSimpleName());
            }
        }

        /* renamed from: com.harleytg.forum.dev.MainActivity$3, reason: invalid class name */
        class AnonymousClass3 extends ConnectivityManager.NetworkCallback {
            AnonymousClass3() {
            }

            @Override // android.net.ConnectivityManager.NetworkCallback
            public void onAvailable(Network network) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$3$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AnonymousClass3.this.m112lambda$onAvailable$1$comharleytgforumdevMainActivity$3();
                    }
                });
            }

            /* renamed from: lambda$onAvailable$1$com-harleytg-forum-dev-MainActivity$3, reason: not valid java name */
            /* synthetic */ void m112lambda$onAvailable$1$comharleytgforumdevMainActivity$3() {
                String str = MainActivity.this.liveState;
                MainActivity.this.updateLiveState("SYNCING");
                HcfNotifications.InstantNotificationService.requestImmediateSync(MainActivity.this);
                if (MainActivity.this.liveUpdater != null) {
                    MainActivity.this.liveUpdater.poke();
                }
                if ("OFFLINE".equals(str)) {
                    boolean legacyReconnectVisible = MainActivity.this.statusOverlay != null
                            && MainActivity.this.statusOverlay.getVisibility() == 0;
                    String reconnectHost = MainActivity.this.activeHost == null
                            ? MainActivity.this.chooseInitialHost() : MainActivity.this.activeHost;
                    if (MainActivity.this.brandedLoader != null) {
                        MainActivity.this.brandedLoader.showReconnecting(reconnectHost);
                        if (MainActivity.this.statusOverlay != null) {
                            MainActivity.this.statusOverlay.setVisibility(8);
                        }
                        if (MainActivity.this.startupStateContainer != null) {
                            MainActivity.this.startupStateContainer.setVisibility(8);
                        }
                    }
                    MainActivity.this.showTransientBanner("Connection restored • syncing forum…");
                    if (MainActivity.this.webView == null
                            || (MainActivity.this.brandedLoader == null && !legacyReconnectVisible)) {
                        return;
                    }
                    WebView webView = MainActivity.this.webView;
                    final MainActivity mainActivity = MainActivity.this;
                    webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$3$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.reloadCurrentPage();
                        }
                    }, 250L);
                }
            }

            @Override // android.net.ConnectivityManager.NetworkCallback
            public void onLost(Network network) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$3$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AnonymousClass3.this.m113lambda$onLost$2$comharleytgforumdevMainActivity$3();
                    }
                });
            }

            /* renamed from: lambda$onLost$2$com-harleytg-forum-dev-MainActivity$3, reason: not valid java name */
            /* synthetic */ void m113lambda$onLost$2$comharleytgforumdevMainActivity$3() {
                if (MainActivity.this.isNetworkAvailable()) {
                    return;
                }
                MainActivity.this.updateLiveState("OFFLINE");
                MainActivity.this.showTransientBanner("Offline • forum will reconnect automatically");
            }
        }

        private void unregisterNetworkState() {
            ConnectivityManager.NetworkCallback networkCallback;
            if (this.networkCallbackRegistered) {
                try {
                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
                    if (connectivityManager != null && (networkCallback = this.networkCallback) != null) {
                        connectivityManager.unregisterNetworkCallback(networkCallback);
                    }
                } catch (Throwable unused) {
                }
                this.networkCallback = null;
                this.networkCallbackRegistered = false;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isNetworkAvailable() {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService("connectivity");
                if (connectivityManager == null) {
                    return true;
                }
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    return false;
                }
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasCapability(12)) {
                        return true;
                    }
                }
                return false;
            } catch (Throwable unused) {
                return true;
            }
        }

        private void openNotificationSystemSettings() {
            NotificationHelper.createChannel(this);
            NotificationHelper.openChannelSettings(this);
            AppLogger.info(this, "notification_settings", "drawer | " + NotificationHelper.status(this));
        }

        private void configureEditableAddressBar() {
            EditText editText = this.currentUrlText;
            if (editText == null) {
                return;
            }
            editText.setSelectAllOnFocus(false);
            this.currentUrlText.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda8
                @Override // android.view.View.OnFocusChangeListener
                public final void onFocusChange(View view, boolean z) {
                    MainActivity.this.m66x39c5c504(view, z);
                }
            });
            this.currentUrlText.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda9
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m67x4d6d9885(view);
                }
            });
            this.currentUrlText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda10
                @Override // android.widget.TextView.OnEditorActionListener
                public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    return MainActivity.this.m68x61156c06(textView, i, keyEvent);
                }
            });
        }

        /* renamed from: lambda$configureEditableAddressBar$41$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m66x39c5c504(View view, boolean z) {
            String url;
            if (z) {
                try {
                    EditText editText = this.currentUrlText;
                    editText.setSelection(editText.getText().length());
                } catch (Throwable unused) {
                }
                this.currentUrlText.post(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda66
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.m65x261df183();
                    }
                });
            } else {
                WebView webView = this.webView;
                if (webView == null || (url = webView.getUrl()) == null || url.trim().isEmpty()) {
                    return;
                }
                this.currentUrlText.setText(url);
            }
        }

        /* renamed from: lambda$configureEditableAddressBar$40$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m65x261df183() {
            try {
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
                if (inputMethodManager != null) {
                    inputMethodManager.showSoftInput(this.currentUrlText, 1);
                }
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$configureEditableAddressBar$42$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m67x4d6d9885(View view) {
            this.currentUrlText.requestFocus();
            try {
                EditText editText = this.currentUrlText;
                editText.setSelection(editText.getText().length());
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$configureEditableAddressBar$43$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ boolean m68x61156c06(TextView textView, int i, KeyEvent keyEvent) {
            if (i != 2 && (keyEvent == null || keyEvent.getAction() != 0 || keyEvent.getKeyCode() != 66)) {
                return false;
            }
            submitAddressBar();
            return true;
        }

        private void submitAddressBar() {
            Uri uri;
            EditText editText = this.currentUrlText;
            if (editText == null || this.webView == null) {
                return;
            }
            String trim = editText.getText() == null ? "" : this.currentUrlText.getText().toString().trim();
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            if (trim.isEmpty()) {
                trim = ForumUrlRouter.home(str);
            }
            if (trim.startsWith("/") && !trim.startsWith("//")) {
                trim = "https://" + str + trim;
            }
            if (!trim.contains("://")) {
                String lowerCase = trim.toLowerCase(Locale.US);
                if (lowerCase.startsWith("forum.harleytg.com".toLowerCase(Locale.US)) || lowerCase.startsWith("harleysclan.freeflarum.com".toLowerCase(Locale.US))) {
                    trim = "https://" + trim;
                } else if (!trim.contains(".")) {
                    if (!trim.startsWith("/")) {
                        trim = "/" + trim;
                    }
                    trim = "https://" + str + trim;
                }
            }
            try {
                uri = Uri.parse(trim);
            } catch (Throwable unused) {
                uri = null;
            }
            if (uri == null || !ForumUrlRouter.isForumUrl(uri)) {
                if (uri != null) {
                    trim = uri.toString();
                }
                ErrorSystem.AppError externalBlocked = ErrorSystem.externalBlocked(trim);
                hideKeyboardAndReleaseAddressBar();
                showUnavailable(externalBlocked, uri);
                return;
            }
            if (uri.getHost() != null) {
                str = uri.getHost();
            }
            String equivalentOnHost = ForumUrlRouter.equivalentOnHost(uri, str);
            this.activeHost = str;
            this.prefs.edit().putString("active_host", this.activeHost).apply();
            hideKeyboardAndReleaseAddressBar();
            clearErrorStateForNavigation("address_go");
            updateHostChrome(this.activeHost);
            updateUrlChrome(equivalentOnHost);
            this.webView.loadUrl(equivalentOnHost);
            AppLogger.info(this, "address_go", AppLogger.safeUrl(equivalentOnHost));
        }

        private void hideKeyboardAndReleaseAddressBar() {
            EditText editText;
            try {
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService("input_method");
                if (inputMethodManager != null && (editText = this.currentUrlText) != null) {
                    inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
            } catch (Throwable unused) {
            }
            EditText editText2 = this.currentUrlText;
            if (editText2 != null) {
                editText2.clearFocus();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void updateUrlChrome(String str) {
            if (this.currentUrlText == null) {
                return;
            }
            if (str == null || str.trim().isEmpty()) {
                str = currentUrlString();
            }
            if (!this.currentUrlText.hasFocus()) {
                this.currentUrlText.setText(str);
            }
            try {
                Uri parse = Uri.parse(str);
                if (ForumUrlRouter.isForumUrl(parse)) {
                    updateHostChrome(parse.getHost());
                    updateDrawerActiveState(parse);
                }
            } catch (Throwable unused) {
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleSpaRouteChange(String str) {
            if (str == null) {
                str = "";
            }
            try {
                Uri parse = Uri.parse(str);
                if (ForumUrlRouter.isForumUrl(parse)) {
                    updateUrlChrome(parse.toString());
                    TelemetryService.noteRoute(this, parse.toString());
                    LiveForumUpdater liveForumUpdater = this.liveUpdater;
                    if (liveForumUpdater != null) {
                        liveForumUpdater.reset();
                        this.liveUpdater.poke();
                    }
                    AppLogger.info(this, "spa_route", AppLogger.safeUrl(parse.toString()));
                }
            } catch (Throwable unused) {
            }
        }

        private void updateDrawerActiveState(Uri uri) {
            boolean z;
            if (this.drawerHome == null) {
                return;
            }
            boolean z2 = false;
            if (uri == null || !ForumUrlRouter.isForumUrl(uri)) {
                z = false;
            } else {
                String path = uri.getPath();
                boolean z3 = path == null || path.isEmpty() || "/".equals(path);
                if (path != null && ("/notifications".equals(path) || path.startsWith("/notifications/"))) {
                    z2 = true;
                }
                z = z2;
                z2 = z3;
            }
            Button button = this.drawerHome;
            int i = R.drawable.drawer_item_active_background;
            button.setBackgroundResource(z2 ? R.drawable.drawer_item_active_background : R.drawable.drawer_item_background);
            Button button2 = this.drawerHome;
            int i2 = R.color.hcf_cyan_bright;
            button2.setTextColor(getColor(z2 ? R.color.hcf_cyan_bright : R.color.hcf_text));
            FaIcons.applyStart(this.drawerHome, R.drawable.fa_house);
            Button button3 = this.drawerNotifications;
            if (button3 != null) {
                if (!z) {
                    i = R.drawable.drawer_item_background;
                }
                button3.setBackgroundResource(i);
                Button button4 = this.drawerNotifications;
                if (!z) {
                    i2 = R.color.hcf_text;
                }
                button4.setTextColor(getColor(i2));
                FaIcons.applyStart(this.drawerNotifications, R.drawable.fa_bell);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void updateHostChrome(String str) {
            if (str == null) {
                return;
            }
            boolean equalsIgnoreCase = "forum.harleytg.com".equalsIgnoreCase(str);
            TextView textView = this.hostBadge;
            int i = R.color.hcf_yellow;
            if (textView != null) {
                textView.setText(equalsIgnoreCase ? "Primary" : "Backup");
                this.hostBadge.setTextColor(getColor(equalsIgnoreCase ? R.color.hcf_cyan_bright : R.color.hcf_yellow));
            }
            TextView textView2 = this.drawerHostText;
            if (textView2 != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(equalsIgnoreCase ? "Primary • " : "Backup • ");
                sb.append(str);
                textView2.setText(sb.toString());
                TextView textView3 = this.drawerHostText;
                if (equalsIgnoreCase) {
                    i = R.color.hcf_cyan;
                }
                textView3.setTextColor(getColor(i));
            }
            Button button = this.drawerSwitchHost;
            if (button != null) {
                button.setText(equalsIgnoreCase ? "Primary server" : "Backup server");
                this.drawerSwitchHost.setContentDescription(equalsIgnoreCase ? "Switch to backup forum" : "Switch to primary forum");
                FaIcons.applyStart(this.drawerSwitchHost, R.drawable.fa_right_left);
            }
            updateHeaderSubtitle();
        }

        private void openDrawer() {
            if (this.prefs != null && !this.prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) {
                try {
                    SetupCenter.installDrawerEntry(this);
                } catch (Throwable error) {
                    AppLogger.warn(this, "app_setup_drawer", "open_restore_" + error.getClass().getSimpleName());
                }
            }
            if (this.drawerPanel.getVisibility() == 0) {
                return;
            }
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            updateHostChrome(str);
            updateIdentityChrome(ForumIdentity.load(this));
            this.drawerScrim.setAlpha(0.0f);
            this.drawerScrim.setVisibility(0);
            this.drawerPanel.setTranslationX(-dp(320));
            this.drawerPanel.setVisibility(0);
            long motionDuration = motionDuration(170L);
            long motionDuration2 = motionDuration(190L);
            if (motionDuration2 <= 0) {
                this.drawerScrim.setAlpha(1.0f);
                this.drawerPanel.setTranslationX(0.0f);
            } else {
                this.drawerScrim.animate().alpha(1.0f).setDuration(motionDuration).start();
                this.drawerPanel.animate().translationX(0.0f).setDuration(motionDuration2).start();
            }
            AppLogger.info(this, "drawer", "open");
        }

        private void closeDrawer() {
            if (this.drawerPanel.getVisibility() != 0) {
                return;
            }
            long motionDuration = motionDuration(150L);
            long motionDuration2 = motionDuration(170L);
            if (motionDuration2 <= 0) {
                this.drawerScrim.setAlpha(0.0f);
                this.drawerScrim.setVisibility(8);
                this.drawerPanel.setTranslationX(-dp(320));
                this.drawerPanel.setVisibility(8);
            } else {
                this.drawerScrim.animate().alpha(0.0f).setDuration(motionDuration).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda18
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.m37lambda$closeDrawer$44$comharleytgforumdevMainActivity();
                    }
                }).start();
                this.drawerPanel.animate().translationX(-dp(320)).setDuration(motionDuration2).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda19
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.m38lambda$closeDrawer$45$comharleytgforumdevMainActivity();
                    }
                }).start();
            }
            AppLogger.info(this, "drawer", "close");
        }

        /* renamed from: lambda$closeDrawer$44$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m37lambda$closeDrawer$44$comharleytgforumdevMainActivity() {
            this.drawerScrim.setVisibility(8);
        }

        /* renamed from: lambda$closeDrawer$45$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m38lambda$closeDrawer$45$comharleytgforumdevMainActivity() {
            this.drawerPanel.setVisibility(8);
        }

        private int dp(int i) {
            return Math.round(i * getResources().getDisplayMetrics().density);
        }

        private void scheduleWhatsNew(boolean z) {
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences == null) {
                return;
            }
            if (!z) {
                ReleaseNotes.seedForFreshInstall(sharedPreferences);
                return;
            }
            WebView webView = this.webView;
            if (webView == null) {
                return;
            }
            webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda20
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m85lambda$scheduleWhatsNew$46$comharleytgforumdevMainActivity();
                }
            }, 1500L);
        }

        /* renamed from: lambda$scheduleWhatsNew$46$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m85lambda$scheduleWhatsNew$46$comharleytgforumdevMainActivity() {
            try {
                if (hasWindowFocus() && ReleaseNotes.shouldNotify(this.prefs)) {
                    showWhatsNewNotification();
                }
            } catch (Throwable th) {
                AppLogger.error(this, "whats_new", th.getClass().getSimpleName());
            }
        }

        private void showWhatsNewNotification() {
            if (this.welcomeBanner == null || this.prefs == null || isFinishing() || isDestroyed()) {
                return;
            }
            ReleaseNotes.markSeen(this.prefs);
            this.welcomeBanner.animate().cancel();
            this.welcomeBanner.setText("✨  What's New • v" + BuildInfo.VERSION
                    + "
" + BuildInfo.VERSION_CODE + " • " + BuildInfo.BUILD_TAG + "  •  Tap to view");
            this.welcomeBanner.setContentDescription("What's new in v" + BuildInfo.VERSION
                    + " build " + BuildInfo.VERSION_CODE + ". Tap to view release notes.");
            this.welcomeBanner.setClickable(true);
            this.welcomeBanner.setFocusable(true);
            this.welcomeBanner.setAlpha(0.0f);
            this.welcomeBanner.setVisibility(0);
            this.welcomeBanner.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m104xaa334a0(view);
                }
            });
            this.welcomeBanner.animate().alpha(1.0f).setDuration(motionDuration(180L)).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda4
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m107xe25f08b8();
                }
            }).start();
            AppLogger.info(this, "whats_new_notification", "1.0");
        }

        /* renamed from: lambda$showWhatsNewNotification$47$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m104xaa334a0(View view) {
            this.welcomeBanner.animate().cancel();
            this.welcomeBanner.setVisibility(8);
            this.welcomeBanner.setClickable(false);
            this.welcomeBanner.setFocusable(false);
            this.welcomeBanner.setOnClickListener(null);
            ReleaseNotes.showCustom(this, this.prefs, false);
        }

        /* renamed from: lambda$showWhatsNewNotification$50$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m107xe25f08b8() {
            this.welcomeBanner.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda69
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m106x31f2dba2();
                }
            }, 7000L);
        }

        /* renamed from: lambda$showWhatsNewNotification$49$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m106x31f2dba2() {
            TextView textView = this.welcomeBanner;
            if (textView == null || textView.getVisibility() != 0) {
                return;
            }
            this.welcomeBanner.animate().alpha(0.0f).setDuration(motionDuration(220L)).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda28
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m105x1e4b0821();
                }
            }).start();
        }

        /* renamed from: lambda$showWhatsNewNotification$48$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m105x1e4b0821() {
            this.welcomeBanner.setVisibility(8);
            this.welcomeBanner.setClickable(false);
            this.welcomeBanner.setFocusable(false);
            this.welcomeBanner.setOnClickListener(null);
        }

        private void scheduleFirstRunPermissionSetup() {
            WebView webView;
            if (this.prefs.getBoolean("permission_onboarding_done", false) || (webView = this.webView) == null) {
                return;
            }
            webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda13
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.showFirstRunPermissionSetup();
                }
            }, 700L);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void showFirstRunPermissionSetup() {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            boolean z = Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0;
            boolean z2 = !AppSecurity.canInstallUpdates(this);
            if (!z && !z2) {
                this.prefs.edit().putBoolean("permission_onboarding_done", true).apply();
                return;
            }
            StringBuilder sb = new StringBuilder("Harley's Clan Forum only asks for permissions it actually uses.\n\n");
            if (z) {
                sb.append("• Notifications — forum alerts and update status.\n");
            }
            if (z2) {
                sb.append("• Install app updates — lets Android install verified HCF update APKs after you confirm.\n");
            }
            sb.append("\nThe app does not request location, contacts, microphone, or camera access.");
            new AlertDialog.Builder(this).setTitle("Set up app permissions").setMessage(sb.toString()).setPositiveButton("Continue", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda75
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m97x29893c96(dialogInterface, i);
                }
            }).setNegativeButton("Not now", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda76
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m98x3d311017(dialogInterface, i);
                }
            }).show();
        }

        /* renamed from: lambda$showFirstRunPermissionSetup$51$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m97x29893c96(DialogInterface dialogInterface, int i) {
            requestFirstRunPermissions();
        }

        /* renamed from: lambda$showFirstRunPermissionSetup$52$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m98x3d311017(DialogInterface dialogInterface, int i) {
            this.prefs.edit().putBoolean("permission_onboarding_done", true).apply();
            AppLogger.info(this, "permission_onboarding", "skipped");
        }

        private void requestFirstRunPermissions() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                this.prefs.edit().putBoolean("notification_permission_asked", true).apply();
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_PERMISSION_REQUEST);
            } else {
                requestUpdateInstallPermissionIfNeeded();
            }
        }

        private void requestUpdateInstallPermissionIfNeeded() {
            if (AppSecurity.canInstallUpdates(this)) {
                this.prefs.edit().putBoolean("permission_onboarding_done", true).apply();
            } else {
                new AlertDialog.Builder(this).setTitle("Allow secure app updates").setMessage("Android requires special approval before this app can open downloaded update APKs. Updates are limited to the trusted HCF release source and are verified for package name, newer version, and matching signing certificate before installation.").setPositiveButton("Open Android settings", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda6
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.m81xaecf68f0(dialogInterface, i);
                    }
                }).setNegativeButton("Later", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda7
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.m82xc2773c71(dialogInterface, i);
                    }
                }).show();
            }
        }

        /* renamed from: lambda$requestUpdateInstallPermissionIfNeeded$53$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m81xaecf68f0(DialogInterface dialogInterface, int i) {
            this.prefs.edit().putBoolean("install_permission_prompted", true).putBoolean("permission_onboarding_done", true).apply();
            try {
                startActivityForResult(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            } catch (Throwable th) {
                AppLogger.error(this, "install_permission", th.getClass().getSimpleName());
            }
        }

        /* renamed from: lambda$requestUpdateInstallPermissionIfNeeded$54$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m82xc2773c71(DialogInterface dialogInterface, int i) {
            this.prefs.edit().putBoolean("permission_onboarding_done", true).apply();
        }

        private void requestNotificationPermissionOnFirstRun() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0 && this.prefs.getInt("notification_permission_prompt_version", 0) < BuildInfo.VERSION_CODE) {
                this.prefs.edit().putBoolean("notification_permission_asked", true).putInt("notification_permission_prompt_version", BuildInfo.VERSION_CODE).apply();
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_PERMISSION_REQUEST);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isNativeInstallRoute(Uri uri) {
            String path;
            if (uri == null || !ForumUrlRouter.isForumUrl(uri) || (path = uri.getPath()) == null) {
                return false;
            }
            String lowerCase = path.trim().toLowerCase(Locale.US);
            return "/install".equals(lowerCase) || lowerCase.startsWith("/install/");
        }

        private void showBetaUpdateAvailableDialog(UpdateChecker.Release release) {
            if (release == null || isFinishing() || isDestroyed()) {
                return;
            }
            new AlertDialog.Builder(this).setTitle("Beta Update Available").setMessage("An updated Harley's Clan Forum development APK is ready.\n\nInstalled: v1.0 (" + BuildInfo.VERSION_CODE + ")\nAvailable: v" + UpdateChecker.displayVersion(release) + " (" + (release.versionCode > 0 ? Long.toString(release.versionCode) : "Checking build code") + ")\nReason: " + UpdateChecker.updateReason(release) + "\n\nChannel: Development / Beta\nUpdate when you're ready to test the latest build.").setNegativeButton("Later", (DialogInterface.OnClickListener) null).setPositiveButton("Update Now", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda12
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m86xf46525d5(dialogInterface, i);
                }
            }).show();
        }

        /* renamed from: lambda$showBetaUpdateAvailableDialog$55$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m86xf46525d5(DialogInterface dialogInterface, int i) {
            startNativeUpdateFlow();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void startNativeUpdateFlow() {
            if (this.nativeUpdateFlowActive || isFinishing() || isDestroyed()) {
                return;
            }
            this.nativeUpdateFlowActive = true;
            long downloadedId = AppUpdateDownloader.downloadedId(this);
            if (downloadedId > 0) {
                this.nativeUpdateDownloadId = downloadedId;
                AppSecurity.ApkVerification verifyDownloadedUpdate = AppSecurity.verifyDownloadedUpdate(this, downloadedId);
                if (verifyDownloadedUpdate.ok) {
                    continueInstallAfterVerification(downloadedId, verifyDownloadedUpdate.message);
                    return;
                }
            }
            final TextView textView = new TextView(this);
            textView.setText("Checking Development / Beta updates…");
            textView.setTextColor(getColor(R.color.hcf_text));
            textView.setTextSize(14.0f);
            int dp = dp(20);
            textView.setPadding(dp, dp, dp, dp);
            AlertDialog create = new AlertDialog.Builder(this).setTitle("Harley's Clan Forum Update").setView(textView).setNegativeButton("Cancel", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda1
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m109x6d485c01(dialogInterface, i);
                }
            }).create();
            this.nativeUpdateDialog = create;
            create.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda2
                @Override // android.content.DialogInterface.OnDismissListener
                public final void onDismiss(DialogInterface dialogInterface) {
                    MainActivity.this.m110x80f02f82(dialogInterface);
                }
            });
            this.nativeUpdateDialog.show();
            UpdateChecker.check(this, "dev", new UpdateChecker.Callback() { // from class: com.harleytg.forum.dev.MainActivity.4
                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onResult(UpdateChecker.Release release, boolean z) {
                    String str;
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) {
                        return;
                    }
                    if (!z) {
                        if (UpdateChecker.compareReleaseToInstalled(release) < 0) {
                            TextView textView2 = textView;
                            StringBuilder sb = new StringBuilder("Installed build 1.0 (" + BuildInfo.VERSION_CODE + ") is newer than the Development / Beta feed");
                            if (release.versionCode > 0) {
                                str = " (" + release.versionCode + ")";
                            } else {
                                str = "";
                            }
                            sb.append(str);
                            sb.append(". No downgrade will be installed.");
                            textView2.setText(sb.toString());
                        } else {
                            textView.setText("You're on the newest Development / Beta build.\n\nInstalled: v1.0 (" + BuildInfo.VERSION_CODE + ")");
                        }
                        MainActivity.this.nativeUpdateFlowActive = false;
                        return;
                    }
                    if (release.apkUrl == null || release.apkUrl.trim().isEmpty()) {
                        textView.setText("A newer Development / Beta build is published, but the release does not contain an installable APK.");
                        MainActivity.this.nativeUpdateFlowActive = false;
                        return;
                    }
                    long enqueue = AppUpdateDownloader.enqueue(MainActivity.this, release, true);
                    if (enqueue > 0) {
                        MainActivity.this.nativeUpdateDownloadId = enqueue;
                        MainActivity.this.showNativeUpdateDownload(release, enqueue);
                    } else {
                        textView.setText("The update could not be downloaded. Check your connection and try again.");
                        MainActivity.this.nativeUpdateFlowActive = false;
                    }
                }

                @Override // com.harleytg.forum.dev.UpdateChecker.Callback
                public void onError(String str) {
                    TextView textView2 = textView;
                    if (textView2 != null) {
                        textView2.setText("Unable to check for updates.\n\n" + str);
                    }
                    MainActivity.this.nativeUpdateFlowActive = false;
                }
            });
        }

        /* renamed from: lambda$startNativeUpdateFlow$56$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m109x6d485c01(DialogInterface dialogInterface, int i) {
            this.nativeUpdateFlowActive = false;
        }

        /* renamed from: lambda$startNativeUpdateFlow$57$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m110x80f02f82(DialogInterface dialogInterface) {
            if (this.nativeUpdateDownloadId <= 0) {
                this.nativeUpdateFlowActive = false;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void showNativeUpdateDownload(UpdateChecker.Release release, long j) {
            String str;
            AlertDialog alertDialog = this.nativeUpdateDialog;
            if (alertDialog != null) {
                try {
                    alertDialog.dismiss();
                } catch (Throwable unused) {
                }
            }
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            int dp = dp(20);
            linearLayout.setPadding(dp, dp(12), dp, dp(8));
            TextView textView = new TextView(this);
            StringBuilder sb = new StringBuilder("Installed: v1.0 (" + BuildInfo.VERSION_CODE + ")\nAvailable: v");
            sb.append(UpdateChecker.displayVersion(release));
            if (release.versionCode > 0) {
                str = " (" + release.versionCode + ")";
            } else {
                str = "";
            }
            sb.append(str);
            textView.setText(sb.toString());
            textView.setTextColor(getColor(R.color.hcf_text));
            textView.setTextSize(13.0f);
            linearLayout.addView(textView, new LinearLayout.LayoutParams(-1, -2));
            ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setIndeterminate(true);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(18));
            layoutParams.topMargin = dp(14);
            linearLayout.addView(progressBar, layoutParams);
            TextView textView2 = new TextView(this);
            textView2.setText("Starting download…");
            textView2.setTextColor(getColor(R.color.hcf_muted));
            textView2.setTextSize(12.0f);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
            layoutParams2.topMargin = dp(8);
            linearLayout.addView(textView2, layoutParams2);
            AlertDialog create = new AlertDialog.Builder(this).setTitle("Downloading update").setView(linearLayout).setNegativeButton("Hide", (DialogInterface.OnClickListener) null).create();
            this.nativeUpdateDialog = create;
            create.setCanceledOnTouchOutside(false);
            this.nativeUpdateDialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda0
                @Override // android.content.DialogInterface.OnDismissListener
                public final void onDismiss(DialogInterface dialogInterface) {
                    MainActivity.lambda$showNativeUpdateDownload$58(dialogInterface);
                }
            });
            this.nativeUpdateDialog.show();
            pollNativeUpdateDownload(release, j, progressBar, textView2);
        }

        private void pollNativeUpdateDownload(final UpdateChecker.Release release, final long j, final ProgressBar progressBar, final TextView textView) {
            String str;
            if (j <= 0) {
                return;
            }
            AppUpdateDownloader.ProgressSnapshot progress = AppUpdateDownloader.progress(this, j);
            int percent = progress.percent();
            if (percent >= 0) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(percent);
            } else {
                progressBar.setIndeterminate(true);
            }
            String formatUpdateBytes = formatUpdateBytes(progress.downloadedBytes);
            String formatUpdateBytes2 = progress.totalBytes > 0 ? formatUpdateBytes(progress.totalBytes) : "unknown size";
            if (progress.status == 8) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                textView.setText("Download complete • verifying APK…");
                AppSecurity.ApkVerification verifyDownloadedUpdate = AppSecurity.verifyDownloadedUpdate(this, j);
                if (!verifyDownloadedUpdate.ok) {
                    ErrorSystem.AppError updateVerification = ErrorSystem.updateVerification(verifyDownloadedUpdate.message);
                    textView.setText(updateVerification.code + " • " + updateVerification.title + "\n" + updateVerification.message + "\n\n" + verifyDownloadedUpdate.message);
                    StringBuilder sb = new StringBuilder();
                    sb.append(updateVerification.code);
                    sb.append(" | ");
                    sb.append(verifyDownloadedUpdate.message);
                    AppLogger.error(this, "update_verification", sb.toString());
                    this.nativeUpdateFlowActive = false;
                    this.nativeUpdateDownloadId = -1L;
                    return;
                }
                textView.setText("Verified • opening Android installer…");
                continueInstallAfterVerification(j, verifyDownloadedUpdate.message);
                return;
            }
            if (progress.status == 16) {
                ErrorSystem.AppError updateDownloadFailure = ErrorSystem.updateDownloadFailure(progress.reason);
                textView.setText(updateDownloadFailure.code + " • " + updateDownloadFailure.title + "\n" + updateDownloadFailure.message + "\n\nOpen /install to retry.");
                StringBuilder sb2 = new StringBuilder();
                sb2.append(updateDownloadFailure.code);
                sb2.append(" | ");
                sb2.append(updateDownloadFailure.technical);
                AppLogger.error(this, "update_download_failed", sb2.toString());
                this.nativeUpdateFlowActive = false;
                this.nativeUpdateDownloadId = -1L;
                return;
            }
            if (progress.status == 4) {
                textView.setText("Download paused • " + formatUpdateBytes + " / " + formatUpdateBytes2 + "\nAndroid will resume it automatically when possible.");
            } else {
                if (percent >= 0) {
                    str = percent + "% • ";
                } else {
                    str = "Downloading • ";
                }
                textView.setText(str + formatUpdateBytes + " / " + formatUpdateBytes2);
            }
            this.mainHandler.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda67
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m79xb37470d3(j, release, progressBar, textView);
                }
            }, 350L);
        }

        /* renamed from: lambda$pollNativeUpdateDownload$59$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m79xb37470d3(long j, UpdateChecker.Release release, ProgressBar progressBar, TextView textView) {
            if (isFinishing() || isDestroyed() || this.nativeUpdateDownloadId != j) {
                return;
            }
            pollNativeUpdateDownload(release, j, progressBar, textView);
        }

        private void continueInstallAfterVerification(long j, String str) {
            AlertDialog alertDialog;
            this.nativeUpdateDownloadId = j;
            if (!AppSecurity.canInstallUpdates(this)) {
                this.prefs.edit().putBoolean("update_resume_after_permission", true).apply();
                new AlertDialog.Builder(this).setTitle("Allow Harley's Clan Forum to install updates").setMessage("The APK is fully downloaded and verified. Android needs permission for this app to open its update installer. The update will not be downloaded again.\n\n" + str).setPositiveButton("Open Android settings", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda24
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.m72x9fdda4ad(dialogInterface, i);
                    }
                }).setNegativeButton("Later", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda25
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.m73xb385782e(dialogInterface, i);
                    }
                }).show();
                return;
            }
            boolean openInstaller = AppUpdateDownloader.openInstaller(this, j);
            AppLogger.info(this, "native_install_route", openInstaller ? "installer-opened" : "installer-open-failed");
            if (!openInstaller) {
                ErrorSystem.AppError installerOpenFailure = ErrorSystem.installerOpenFailure("Package installer activity was unavailable or rejected the install intent.");
                new AlertDialog.Builder(this).setTitle(installerOpenFailure.code + " • " + installerOpenFailure.title).setMessage(installerOpenFailure.message).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
                AppLogger.error(this, "native_install_route", installerOpenFailure.code + " | " + installerOpenFailure.technical);
            }
            this.nativeUpdateFlowActive = false;
            if (!openInstaller || (alertDialog = this.nativeUpdateDialog) == null) {
                return;
            }
            try {
                alertDialog.dismiss();
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$continueInstallAfterVerification$60$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m72x9fdda4ad(DialogInterface dialogInterface, int i) {
            try {
                startActivityForResult(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + getPackageName())), UPDATE_INSTALL_PERMISSION_REQUEST);
            } catch (Throwable th) {
                this.prefs.edit().remove("update_resume_after_permission").apply();
                this.nativeUpdateFlowActive = false;
                AppLogger.error(this, "install_permission", th.getClass().getSimpleName());
            }
        }

        /* renamed from: lambda$continueInstallAfterVerification$61$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m73xb385782e(DialogInterface dialogInterface, int i) {
            this.nativeUpdateFlowActive = false;
        }

        private String formatUpdateBytes(long j) {
            if (j < 1024) {
                return j + " B";
            }
            double d = j / 1024.0d;
            if (d < 1024.0d) {
                return String.format(Locale.US, "%.1f KB", Double.valueOf(d));
            }
            return String.format(Locale.US, "%.1f MB", Double.valueOf(d / 1024.0d));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public String chooseInitialHost() {
            if (this.prefs.getBoolean("auto_failover", true)) {
                return System.currentTimeMillis() < this.prefs.getLong("fallback_until", 0L) ? "harleysclan.freeflarum.com" : "forum.harleytg.com";
            }
            return "forum.harleytg.com";
        }

        private Uri currentForumUri() {
            String url = this.webView.getUrl();
            if (url != null) {
                Uri parse = Uri.parse(url);
                if (ForumUrlRouter.isForumUrl(parse)) {
                    return parse;
                }
            }
            return Uri.parse(ForumUrlRouter.home(this.activeHost));
        }

        private void failOverFromPrimary(Uri uri, String str) {
            failOverFromPrimary(uri, ErrorSystem.generic(str));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void failOverFromPrimary(Uri uri, ErrorSystem.AppError appError) {
            if (!"forum.harleytg.com".equals(this.activeHost) || this.switchingHosts) {
                return;
            }
            if (appError == null) {
                appError = ErrorSystem.generic("Primary forum connection failed.");
            }
            if (!this.prefs.getBoolean("auto_failover", true)) {
                AppLogger.warn(this, "primary_failure_no_failover", appError.code + " | " + appError.technical);
                showUnavailable(new ErrorSystem.AppError(appError.code, appError.title, appError.message + " Automatic backup is disabled in App Settings.", appError.technical), uri);
                return;
            }
            this.prefs.edit().putLong("fallback_until", System.currentTimeMillis() + 21600000).apply();
            AppLogger.warn(this, "primary_failover", appError.code + " | route=" + AppLogger.safeUrl(uri.toString()));
            this.lastAppError = appError;
            this.lastErrorUri = uri;
            this.statusTitle.setText(R.string.status_switching);
            this.statusSubtitle.setText(appError.message + "\nTrying harleysclan.freeflarum.com…");
            TextView textView = this.errorCodeText;
            if (textView != null) {
                textView.setText(appError.code);
                this.errorCodeText.setVisibility(0);
            }
            Button button = this.errorDetailsButton;
            if (button != null) {
                button.setVisibility(0);
            }
            this.statusOverlay.setVisibility(0);
            this.errorActions.setVisibility(8);
            switchHost("harleysclan.freeflarum.com", uri);
        }

        private void switchHost(String str, Uri uri) {
            this.switchingHosts = true;
            String str2 = this.activeHost;
            this.activeHost = str;
            this.prefs.edit().putString("active_host", str).apply();
            showChecking(str);
            this.webView.stopLoading();
            String equivalentOnHost = ForumUrlRouter.equivalentOnHost(uri, str);
            updateHostChrome(str);
            updateUrlChrome(equivalentOnHost);
            AppLogger.info(this, "host_switch", str2 + " -> " + str + " | " + AppLogger.safeUrl(equivalentOnHost));
            this.webView.loadUrl(equivalentOnHost);
        }

        private void showChecking(final String requestedHost) {
            boolean z = true;
            final int i = this.connectionUiGeneration + 1;
            this.connectionUiGeneration = i;
            final String str = (requestedHost == null || requestedHost.trim().isEmpty())
                    ? "forum.harleytg.com" : requestedHost;
            this.mainFrameLoadFailed = false;
            this.lastAppError = null;
            this.lastErrorUri = null;
            View view = this.startupStateContainer;
            if (view != null) {
                view.setVisibility(0);
            }
            View view2 = this.errorStateScroll;
            if (view2 != null) {
                view2.setVisibility(8);
            }
            this.statusTitle.setText("Connecting to Harley's Clan Forum…");
            this.statusSubtitle.setText(str);
            this.startupProgress.setVisibility(0);
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences != null && !sharedPreferences.getBoolean("show_startup_screen", true)) {
                z = false;
            }
            if (this.brandedLoader != null) {
                if (z) {
                    this.brandedLoader.showConnecting(str);
                } else {
                    this.brandedLoader.hide(false);
                }
                this.statusOverlay.setVisibility(8);
                if (this.startupStateContainer != null) {
                    this.startupStateContainer.setVisibility(8);
                }
            } else {
                this.statusOverlay.setVisibility(z ? 0 : 8);
            }
            ImageButton imageButton = this.reloadButton;
            if (imageButton != null) {
                imageButton.setEnabled(false);
                this.reloadButton.setAlpha(0.45f);
            }
            updateHostChrome(str);
            this.mainHandler.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda14
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m87lambda$showChecking$62$comharleytgforumdevMainActivity(i, str);
                }
            }, 650L);
            this.mainHandler.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda15
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m88lambda$showChecking$63$comharleytgforumdevMainActivity(i, str);
                }
            }, 1550L);
            this.mainHandler.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda16
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m89lambda$showChecking$64$comharleytgforumdevMainActivity(i, str);
                }
            }, 18000L);
        }

        /* renamed from: lambda$showChecking$62$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m87lambda$showChecking$62$comharleytgforumdevMainActivity(int i, String str) {
            if (i == this.connectionUiGeneration && this.startupProgress.getVisibility() == 0) {
                this.statusTitle.setText("Securing connection…");
                this.statusSubtitle.setText("Verifying " + str);
                if (this.brandedLoader != null && this.brandedLoader.isVisible()) {
                    this.brandedLoader.setTitle("Securing connection…");
                    this.brandedLoader.setStatus("Securing connection…");
                    this.brandedLoader.setDetail("Verifying " + str);
                }
            }
        }

        /* renamed from: lambda$showChecking$63$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m88lambda$showChecking$63$comharleytgforumdevMainActivity(int i, String str) {
            if (i == this.connectionUiGeneration && this.startupProgress.getVisibility() == 0) {
                this.statusTitle.setText("Loading forum…");
                this.statusSubtitle.setText(str);
                if (this.brandedLoader != null && this.brandedLoader.isVisible()) {
                    this.brandedLoader.setTitle("Loading forum…");
                    this.brandedLoader.setStatus("Secure connection ready");
                    this.brandedLoader.setDetail(str);
                }
            }
        }

        /* renamed from: lambda$showChecking$64$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m89lambda$showChecking$64$comharleytgforumdevMainActivity(int i, String str) {
            ErrorSystem.AppError connectionTimeout;
            SharedPreferences sharedPreferences;
            String url = null;
            if (i == this.connectionUiGeneration && this.startupProgress.getVisibility() == 0) {
                Uri uri = null;
                try {
                    WebView webView = this.webView;
                    url = webView == null ? null : webView.getUrl();
                } catch (Throwable unused) {
                }
                if (url != null) {
                    if (url.trim().isEmpty()) {
                    }
                    uri = Uri.parse(url);
                    connectionTimeout = ErrorSystem.connectionTimeout(str);
                    if ("forum.harleytg.com".equalsIgnoreCase(str) || (sharedPreferences = this.prefs) == null || !sharedPreferences.getBoolean("auto_failover", true)) {
                        showUnavailable(connectionTimeout, uri);
                    }
                    if (uri == null) {
                        uri = Uri.parse(ForumUrlRouter.home(str));
                    }
                    failOverFromPrimary(uri, connectionTimeout);
                    return;
                }
                url = ForumUrlRouter.home(str);
                uri = Uri.parse(url);
                connectionTimeout = ErrorSystem.connectionTimeout(str);
                if ("forum.harleytg.com".equalsIgnoreCase(str)) {
                }
                showUnavailable(connectionTimeout, uri);
            }
        }

        private void showUnavailable(String str) {
            showUnavailable(ErrorSystem.generic(str), currentForumUri());
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void showUnavailable(ErrorSystem.AppError appError, Uri uri) {
            this.connectionUiGeneration++;
            if (this.brandedLoader != null) {
                this.brandedLoader.hide(false);
            }
            this.switchingHosts = false;
            this.mainFrameLoadFailed = true;
            ImageButton imageButton = this.reloadButton;
            if (imageButton != null) {
                imageButton.setEnabled(true);
                this.reloadButton.setAlpha(1.0f);
            }
            boolean isNetworkAvailable = true ^ isNetworkAvailable();
            if (isNetworkAvailable && (appError == null || !appError.code.startsWith("HCF-SEC-"))) {
                appError = ErrorSystem.offline();
            } else if (appError == null) {
                appError = ErrorSystem.generic("The forum could not load.");
            }
            this.lastAppError = appError;
            this.lastErrorUri = uri;
            View view = this.startupStateContainer;
            if (view != null) {
                view.setVisibility(8);
            }
            View view2 = this.errorStateScroll;
            if (view2 != null) {
                view2.setVisibility(0);
            }
            TextView textView = this.errorStatusText;
            if (textView != null) {
                textView.setText("●  " + errorStatusLabel(appError));
                this.errorStatusText.setTextColor(errorAccentColor(appError));
            }
            TextView textView2 = this.errorHeroText;
            if (textView2 != null) {
                textView2.setText(errorHeroLabel(appError));
                this.errorHeroText.setTextColor(errorAccentColor(appError));
            }
            TextView textView3 = this.errorTitleText;
            if (textView3 != null) {
                textView3.setText(appError.title);
            }
            TextView textView4 = this.errorMessageText;
            if (textView4 != null) {
                textView4.setText(appError.message);
            }
            TextView textView5 = this.errorCodeText;
            if (textView5 != null) {
                textView5.setText(appError.code);
                this.errorCodeText.setTextColor(errorAccentColor(appError));
                this.errorCodeText.setVisibility(0);
            }
            TextView textView6 = this.errorTechnicalText;
            if (textView6 != null) {
                textView6.setText((appError.technical == null || appError.technical.trim().isEmpty()) ? "No additional technical detail is available." : appError.technical.trim());
            }
            Button button = this.errorDetailsButton;
            if (button != null) {
                button.setVisibility(0);
            }
            Button button2 = this.errorSupportButton;
            if (button2 != null) {
                button2.setVisibility(0);
            }
            if (isNetworkAvailable) {
                updateLiveState("OFFLINE");
            }
            this.errorActions.setVisibility(0);
            this.alternateButton.setText("forum.harleytg.com".equals(this.activeHost) ? R.string.use_backup : R.string.try_primary);
            this.statusOverlay.setVisibility(0);
            this.pageProgress.setVisibility(8);
            this.startupProgress.setVisibility(8);
            updateHostChrome(this.activeHost);
            StringBuilder sb = new StringBuilder();
            sb.append(appError.code);
            sb.append(" | ");
            sb.append(appError.title);
            sb.append(" | ");
            sb.append(AppLogger.safeUrl(uri == null ? "" : uri.toString()));
            AppLogger.warn(this, "native_error_state", sb.toString());
        }

        private int errorAccentColor(ErrorSystem.AppError appError) {
            String str = (appError == null || appError.code == null) ? "" : appError.code;
            if (str.startsWith("HCF-SEC-") || str.contains("403")) {
                return getColor(R.color.hcf_error);
            }
            if (str.contains("500") || str.contains("502") || str.startsWith("HCF-SSL")) {
                return getColor(R.color.hcf_error);
            }
            if (str.contains("429") || str.contains("503") || str.contains("504")) {
                return getColor(R.color.hcf_warning);
            }
            return getColor(R.color.hcf_cyan_bright);
        }

        private String errorStatusLabel(ErrorSystem.AppError appError) {
            String str = (appError == null || appError.code == null) ? "" : appError.code;
            return "HCF-NET-001".equals(str) ? "CONNECTION UNAVAILABLE" : str.startsWith("HCF-SEC-") ? "EXTERNAL SITE BLOCKED" : str.contains("403") ? "ACCESS DENIED" : str.contains("404") ? "ROUTE UNAVAILABLE" : str.contains("429") ? "RATE LIMITED" : str.contains("503") ? "TEMPORARILY OFFLINE" : str.startsWith("HCF-SSL") ? "SECURE CONNECTION BLOCKED" : str.startsWith("HCF-WV") ? "WEBVIEW RECOVERY" : str.startsWith("HCF-UPD") ? "UPDATE RECOVERY" : str.startsWith("HCF-WEB") ? "SERVER ERROR" : "APP RECOVERY";
        }

        private String errorHeroLabel(ErrorSystem.AppError appError) {
            String str = (appError == null || appError.code == null) ? "" : appError.code;
            if ("HCF-NET-001".equals(str)) {
                return "OFFLINE";
            }
            if (str.startsWith("HCF-SEC-")) {
                return "BLOCKED";
            }
            if (str.contains("403")) {
                return "403";
            }
            if (str.contains("404")) {
                return "404";
            }
            if (str.contains("429")) {
                return "429";
            }
            if (str.contains("500")) {
                return "500";
            }
            if (str.contains("502")) {
                return "502";
            }
            if (str.contains("503")) {
                return "503";
            }
            if (str.contains("504")) {
                return "504";
            }
            return str.startsWith("HCF-SSL") ? "SSL" : str.startsWith("HCF-WV") ? "WEBVIEW" : str.startsWith("HCF-UPD") ? "UPDATE" : "ERROR";
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void clearErrorStateForNavigation(String str) {
            View view;
            boolean z = this.mainFrameLoadFailed || this.lastAppError != null || ((view = this.errorStateScroll) != null && view.getVisibility() == 0);
            this.connectionUiGeneration++;
            this.mainFrameLoadFailed = false;
            this.lastAppError = null;
            this.lastErrorUri = null;
            this.switchingHosts = false;
            View view2 = this.errorStateScroll;
            if (view2 != null) {
                view2.setVisibility(8);
            }
            View view3 = this.startupStateContainer;
            if (view3 != null) {
                view3.setVisibility(8);
            }
            View view4 = this.statusOverlay;
            if (view4 != null) {
                view4.setVisibility(8);
            }
            ProgressBar progressBar = this.startupProgress;
            if (progressBar != null) {
                progressBar.setVisibility(8);
            }
            LinearLayout linearLayout = this.errorActions;
            if (linearLayout != null) {
                linearLayout.setVisibility(8);
            }
            TextView textView = this.errorCodeText;
            if (textView != null) {
                textView.setVisibility(8);
            }
            Button button = this.errorDetailsButton;
            if (button != null) {
                button.setVisibility(8);
            }
            Button button2 = this.errorSupportButton;
            if (button2 != null) {
                button2.setVisibility(8);
            }
            ImageButton imageButton = this.reloadButton;
            if (imageButton != null) {
                imageButton.setEnabled(true);
                this.reloadButton.setAlpha(1.0f);
            }
            if (z) {
                if (str == null) {
                    str = "navigation";
                }
                AppLogger.info(this, "error_state_cleared", str);
            }
        }

        private void showLastErrorDetails() {
            ErrorSystem.AppError appError = this.lastAppError;
            if (appError == null) {
                return;
            }
            Uri uri = this.lastErrorUri;
            String safeUrl = uri == null ? "" : AppLogger.safeUrl(uri.toString());
            String str = this.activeHost;
            if (str == null) {
                str = chooseInitialHost();
            }
            StringBuilder sb = new StringBuilder("Harley's Clan Forum Error Report\nError: ");
            sb.append(appError.code);
            sb.append("\nTitle: ");
            sb.append(appError.title);
            sb.append("\nApp: 1.0 (" + BuildInfo.VERSION_CODE + ")\nServer: ");
            sb.append(str);
            sb.append("\nNetwork: ");
            sb.append(isNetworkAvailable() ? "connected" : "offline");
            sb.append("\nRoute: ");
            if (safeUrl.isEmpty()) {
                safeUrl = "not available";
            }
            sb.append(safeUrl);
            sb.append("\nTechnical: ");
            sb.append(appError.technical.isEmpty() ? "not available" : appError.technical);
            final String sb2 = sb.toString();
            new AlertDialog.Builder(this).setTitle(appError.code + " • Technical details").setMessage(sb2).setPositiveButton("Copy report", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda77
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m99x28d83b08(sb2, dialogInterface, i);
                }
            }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
        }

        /* renamed from: lambda$showLastErrorDetails$65$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m99x28d83b08(String str, DialogInterface dialogInterface, int i) {
            copyErrorReport(str);
        }

        private void copyErrorReport(String str) {
            try {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService("clipboard");
                if (clipboardManager == null) {
                    throw new IllegalStateException("Clipboard unavailable");
                }
                clipboardManager.setPrimaryClip(ClipData.newPlainText("HCF error report", str));
                Toast.makeText(this, "Error report copied.", 0).show();
            } catch (Throwable unused) {
                Toast.makeText(this, "Could not copy the error report.", 0).show();
            }
        }

        private void hideStatus() {
            this.connectionUiGeneration++;
            if (this.brandedLoader != null) {
                this.brandedLoader.hide(true);
            }
            this.switchingHosts = false;
            this.mainFrameLoadFailed = false;
            this.lastAppError = null;
            this.lastErrorUri = null;
            ImageButton imageButton = this.reloadButton;
            if (imageButton != null) {
                imageButton.setEnabled(true);
                this.reloadButton.setAlpha(1.0f);
            }
            this.statusOverlay.setVisibility(8);
            View view = this.startupStateContainer;
            if (view != null) {
                view.setVisibility(8);
            }
            View view2 = this.errorStateScroll;
            if (view2 != null) {
                view2.setVisibility(8);
            }
            this.startupProgress.setVisibility(8);
            TextView textView = this.errorCodeText;
            if (textView != null) {
                textView.setVisibility(8);
            }
            Button button = this.errorDetailsButton;
            if (button != null) {
                button.setVisibility(8);
            }
            Button button2 = this.errorSupportButton;
            if (button2 != null) {
                button2.setVisibility(8);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean recoverWebViewRenderer(WebView webView, final String str, ErrorSystem.AppError appError) {
            try {
                Uri parse = Uri.parse(str);
                this.rendererRecoveryPending = true;
                showUnavailable(appError, parse);
                this.errorActions.setVisibility(8);
                this.startupProgress.setVisibility(0);
                if (webView != null) {
                    try {
                        webView.removeJavascriptInterface("HCFNative");
                        webView.stopLoading();
                        ViewParentCompat.removeFromParent(webView);
                        webView.destroy();
                    } catch (Throwable unused) {
                    }
                }
                ViewGroup viewGroup = (ViewGroup) findViewById(R.id.contentFrame);
                if (viewGroup == null) {
                    throw new IllegalStateException("Content frame unavailable");
                }
                final WebView webView2 = new WebView(this);
                webView2.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                if (ThemeManager.isAmoled(this)) {
                    webView2.setBackgroundColor(-16777216);
                }
                viewGroup.addView(webView2, 0);
                this.webView = webView2;
                configureWebView();
                updateUrlChrome(str);
                this.mainHandler.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda34
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.m80x8a6a235e(webView2, str);
                    }
                }, 250L);
                return true;
            } catch (Throwable th) {
                AppLogger.error(this, "webview_renderer_recovery_failed", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
                showCrashSafeScreen(th);
                return true;
            }
        }

        /* renamed from: lambda$recoverWebViewRenderer$66$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m80x8a6a235e(WebView webView, String str) {
            if (this.webView != webView || isFinishing() || isDestroyed()) {
                return;
            }
            String str2 = this.activeHost;
            if (str2 == null) {
                str2 = chooseInitialHost();
            }
            showChecking(str2);
            webView.loadUrl(str);
        }

        private static final class ViewParentCompat {
            private ViewParentCompat() {
            }

            static void removeFromParent(View view) {
                if (view == null) {
                    return;
                }
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(view);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void openExternal(Uri uri) {
            if (uri == null) {
                return;
            }
            if (!this.prefs.getBoolean("external_links", true)) {
                AppLogger.warn(this, "external_link_blocked", AppLogger.safeUrl(uri.toString()));
                Toast.makeText(this, "External links are disabled in App Settings.", 0).show();
                return;
            }
            String lowerCase = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            if ("mailto".equals(lowerCase) || "tel".equals(lowerCase)) {
                launchExternalIntent(uri);
                return;
            }
            LinkSafety.Result classify = LinkSafety.classify(uri);
            if (classify.status == LinkSafety.Status.BLOCKED) {
                showBlockedLink(classify);
                return;
            }
            if (classify.status == LinkSafety.Status.OFFICIAL) {
                launchExternalIntent(uri);
                return;
            }
            boolean z = !hasSeenExternalDomain(classify.host);
            if (classify.status == LinkSafety.Status.SUSPICIOUS || z) {
                showExternalLinkConfirmation(uri, classify, z);
            } else {
                launchExternalIntent(uri);
            }
        }

        private void showExternalLinkConfirmation(final Uri uri, final LinkSafety.Result result, boolean z) {
            String str = (result.host == null || result.host.isEmpty()) ? "Unknown destination" : result.host;
            StringBuilder sb = new StringBuilder("You’re leaving Harley’s Clan Forum.\n\n");
            sb.append(str);
            if (result.reason != null && !result.reason.isEmpty()) {
                sb.append("\n\n");
                sb.append(result.reason);
                sb.append('.');
            }
            if (result.status == LinkSafety.Status.SUSPICIOUS) {
                sb.append("\n\nOnly continue if you trust this destination.");
            } else if (z) {
                sb.append("\n\nThis is the first time this domain has been opened from the app.");
            }
            new AlertDialog.Builder(this).setTitle(result.status.label).setIcon(linkSafetyIcon(result.status)).setMessage(sb.toString()).setPositiveButton("Open externally", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda26
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m95x4c9c1260(result, uri, dialogInterface, i);
                }
            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda27
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m96x6043e5e1(result, dialogInterface, i);
                }
            }).show();
        }

        /* renamed from: lambda$showExternalLinkConfirmation$67$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m95x4c9c1260(LinkSafety.Result result, Uri uri, DialogInterface dialogInterface, int i) {
            if (result.status == LinkSafety.Status.EXTERNAL) {
                markExternalDomainSeen(result.host);
            }
            AppLogger.info(this, "safe_link_open", result.status.label + " | " + result.host);
            launchExternalIntent(uri);
        }

        /* renamed from: lambda$showExternalLinkConfirmation$68$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m96x6043e5e1(LinkSafety.Result result, DialogInterface dialogInterface, int i) {
            AppLogger.info(this, "safe_link_cancel", result.status.label + " | " + result.host);
        }

        private int linkSafetyIcon(LinkSafety.Status status) {
            if (status == LinkSafety.Status.OFFICIAL) {
                return R.drawable.fa_shield;
            }
            if (status == LinkSafety.Status.EXTERNAL) {
                return R.drawable.fa_arrow_up_right_from_square;
            }
            LinkSafety.Status status2 = LinkSafety.Status.SUSPICIOUS;
            return R.drawable.fa_triangle_exclamation;
        }

        private void showBlockedLink(LinkSafety.Result result) {
            String str = (result.host == null || result.host.isEmpty()) ? "This destination" : result.host;
            new AlertDialog.Builder(this).setTitle(LinkSafety.Status.BLOCKED.label).setIcon(linkSafetyIcon(LinkSafety.Status.BLOCKED)).setMessage(str + " was blocked by Harley’s Clan Forum Safe Links.\n\n" + result.reason).setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
            AppLogger.warn(this, "safe_link_blocked", result.reason + " | " + result.host);
        }

        private void launchExternalIntent(Uri uri) {
            try {
                HcfIntentChooser.show(this, new Intent("android.intent.action.VIEW", uri), "Open with", "Choose an app to continue outside Harley's Clan Forum.", true);
            } catch (Throwable unused) {
                AppLogger.error(this, "external_link_failed", AppLogger.safeUrl(uri.toString()));
                Toast.makeText(this, "No app can open this link.", 0).show();
            }
        }

        private boolean hasSeenExternalDomain(String str) {
            Set<String> stringSet;
            String canonicalHost = LinkSafety.canonicalHost(str);
            return (canonicalHost.isEmpty() || (stringSet = this.prefs.getStringSet("safe_links_seen_domains", Collections.emptySet())) == null || !stringSet.contains(canonicalHost)) ? false : true;
        }

        private void markExternalDomainSeen(String str) {
            String canonicalHost = LinkSafety.canonicalHost(str);
            if (canonicalHost.isEmpty()) {
                return;
            }
            Set<String> stringSet = this.prefs.getStringSet("safe_links_seen_domains", Collections.emptySet());
            HashSet<String> hashSet = new HashSet<>();
            if (stringSet != null) {
                hashSet.addAll(stringSet);
            }
            if (hashSet.add(canonicalHost)) {
                this.prefs.edit().putStringSet("safe_links_seen_domains", hashSet).apply();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isTrustedForumPage() {
            try {
                String url = this.webView.getUrl();
                if (url != null) {
                    return ForumUrlRouter.isForumUrl(Uri.parse(url));
                }
                return false;
            } catch (Throwable unused) {
                return false;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static boolean isUnsupportedPwaPushMessage(String str) {
            if (str == null) {
                return false;
            }
            String lowerCase = str.trim().toLowerCase(Locale.US);
            return lowerCase.contains("this browser does not support push notifications for progressive web apps") || lowerCase.contains("browser does not support push notifications") || lowerCase.contains("push notifications are not supported") || lowerCase.contains("push notification is not supported");
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleNativePushRequest() {
            AppLogger.info(this, "pwa_push_redirect", "native-notifications");
            if (!this.prefs.getBoolean("notifications_enabled", true)) {
                this.prefs.edit().putBoolean("notifications_enabled", true).apply();
                NotificationSyncScheduler.apply(this);
            }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_PERMISSION_REQUEST);
            } else {
                Toast.makeText(this, "Notifications are handled by the Harley's Clan Forum app.", 1).show();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void installForumBridge() {
            if (isTrustedForumPage()) {
                this.webView.evaluateJavascript("(function(){if(window.__HCF_NATIVE_V040__){try{window.__HCF_NATIVE_V040__.sync();}catch(e){}return;}var VERSION='1.0';var WARNING='This browser does not support push notifications for progressive web apps.';var REPLACEMENT=\"Push notifications are handled by the Harley's Clan Forum Android app. Use App Settings to manage notifications.\";var lastIdentity='',lastSecurity='',lastRoute='',lastAutoSecurityAt=0,lastAutoSecurityKey='',autoSecurityBusy=false;try{if(!window.__HCF_ORIGINAL_ALERT__)window.__HCF_ORIGINAL_ALERT__=window.alert.bind(window);window.alert=function(m){var x=String(m||'');var l=x.toLowerCase();if(l.indexOf('browser does not support push notifications')>=0||l.indexOf('push notifications are not supported')>=0){try{HCFNative.requestNotificationPermission();}catch(e){}return;}return window.__HCF_ORIGINAL_ALERT__(m);};}catch(e){}var send=function(t,b,u){try{HCFNative.notify(String(t||\"Harley's Clan Forum\"),String(b||''),String(u||location.href));}catch(e){}};window.HCFApp={isNative:true,platform:'android',version:VERSION,notify:send,openSettings:function(){try{HCFNative.openSettings();}catch(e){}}};try{var NativeNotification=function(title,opts){opts=opts||{};send(title,opts.body||'',(opts.data&&opts.data.url)||location.href);};Object.defineProperty(NativeNotification,'permission',{get:function(){try{return HCFNative.notificationsEnabled()?'granted':'default';}catch(e){return 'default';}}});NativeNotification.requestPermission=function(){try{HCFNative.requestNotificationPermission();}catch(e){} return Promise.resolve('default');};window.Notification=NativeNotification;}catch(e){}var iso=function(v){try{if(!v)return '';if(typeof v.toISOString==='function')return v.toISOString();if(v.$d&&typeof v.$d.toISOString==='function')return v.$d.toISOString();return String(v);}catch(e){return '';}};var val=function(o,n,d){try{var x=o&&o[n];return typeof x==='function'?x.call(o):(x===undefined?d:x);}catch(e){return d;}};var syncIdentity=function(){try{if(!window.app||!app.session){return;}var u=app.session.user;if(!u){var guest=JSON.stringify({loggedIn:false});if(guest!==lastIdentity){lastIdentity=guest;HCFNative.updateIdentity(guest,String(location.host||''));}return;}var gs=[];try{var groups=val(u,'groups',[])||[];for(var gi=0;gi<groups.length;gi++){var g=groups[gi];var gn=val(g,'nameSingular','');if(gn)gs.push(String(gn));}}catch(e){}var cs=[];var addc=function(x){x=String(x||'').trim();if(x&&cs.indexOf(x)<0)cs.push(x);};var email=String(val(u,'email','')||'');if(email)addc(!!val(u,'isEmailConfirmed',false)?'Email (verified)':'Email');try{var attrs=(u.data&&u.data.attributes)||{};var ks=Object.keys(attrs);var pm={discord:'Discord',google:'Google'};for(var ki=0;ki<ks.length;ki++){var lk=String(ks[ki]||'').toLowerCase();var av=attrs[ks[ki]];if(av===null||av===undefined||av===false||av==='')continue;for(var pk in pm){if(lk.indexOf(pk)>=0)addc(pm[pk]);}}}catch(e){}try{var rel=u.data&&u.data.relationships&&u.data.relationships.loginProviders&&u.data.relationships.loginProviders.data;if(rel&&rel.length){for(var ri=0;ri<rel.length;ri++){var pid=String((rel[ri]&&rel[ri].id)||'').toLowerCase();if(pid.indexOf('discord')>=0)addc('Discord');if(pid.indexOf('google')>=0)addc('Google');}}}catch(e){}var data={loggedIn:true,id:String(val(u,'id','')||''),username:String(val(u,'username','')||''),slug:String(val(u,'slug','')||''),displayName:String(val(u,'displayName','')||''),email:email,emailConfirmed:!!val(u,'isEmailConfirmed',false),avatarUrl:String(val(u,'avatarUrl','')||''),groups:gs,connections:cs,isAdmin:!!val(u,'isAdmin',false),joinTime:iso(val(u,'joinTime',null)),lastSeenAt:iso(val(u,'lastSeenAt',null)),unreadNotificationCount:Number(val(u,'unreadNotificationCount',0)||0),newNotificationCount:Number(val(u,'newNotificationCount',0)||0),discussionCount:Number(val(u,'discussionCount',0)||0),commentCount:Number(val(u,'commentCount',0)||0)};var payload=JSON.stringify(data);if(payload!==lastIdentity){lastIdentity=payload;HCFNative.updateIdentity(payload,String(location.host||''));}}catch(e){}};var syncSecurity=function(){try{if(!window.app||!app.session||!app.session.user||!document.body)return;var path=String(location.pathname||'').replace(/\\/+$/,'');if(path.indexOf('/u/')!==0||path.slice(-9)!='/security')return;var bodyText=String(document.body.innerText||'').toLowerCase();var providers=[];var addp=function(x){x=String(x||'').trim();if(x&&providers.indexOf(x)<0)providers.push(x);};var nodes=document.querySelectorAll('button,a,li,.Form-group,.Setting,.LoginProvider');var pm={discord:'Discord',google:'Google'};for(var ni=0;ni<nodes.length;ni++){var nt=String(nodes[ni].innerText||nodes[ni].textContent||'').toLowerCase();if(!(nt.indexOf('disconnect')>=0||nt.indexOf('connected')>=0||nt.indexOf('unlink')>=0||nt.indexOf('linked')>=0))continue;for(var pk in pm){if(nt.indexOf(pk)>=0)addp(pm[pk]);}}var sessions=document.querySelectorAll('.AccessTokensList-item').length;var activeSessions=document.querySelectorAll('.AccessTokensList-item--active').length;var data={seen:true,path:path,sessionCount:Number(sessions||0),activeSessionCount:Number(activeSessions||0),providers:providers,passwordControls:(bodyText.indexOf('password')>=0),emailControls:(bodyText.indexOf('email')>=0),twoFactorControls:(bodyText.indexOf('two-factor')>=0||bodyText.indexOf('two factor')>=0||bodyText.indexOf('2fa')>=0||bodyText.indexOf('authenticator')>=0)};var payload=JSON.stringify(data);if(payload!==lastSecurity){lastSecurity=payload;HCFNative.updateSecuritySummary(payload,String(location.host||''));}}catch(e){}};var autoSecurity=function(){try{if(autoSecurityBusy||!window.app||!app.session||!app.session.user||!document.body)return;var cp=String(location.pathname||'');if(cp.indexOf('/u/')===0&&cp.indexOf('/security')===cp.length-9)return;var u=app.session.user,slug=String(val(u,'slug','')||val(u,'username','')||'').trim();if(!slug)return;var key=String(val(u,'id','')||slug)+'@'+String(location.host||'');var now=Date.now();if(key===lastAutoSecurityKey&&now-lastAutoSecurityAt<600000)return;lastAutoSecurityKey=key;lastAutoSecurityAt=now;autoSecurityBusy=true;var f=document.createElement('iframe');f.setAttribute('aria-hidden','true');f.tabIndex=-1;f.style.cssText='position:fixed!important;left:-10000px!important;top:-10000px!important;width:1px!important;height:1px!important;border:0!important;opacity:0!important;pointer-events:none!important;';f.src='/u/'+encodeURIComponent(slug)+'/security?hcf_native_sync=1';var done=false,tries=0;var finish=function(ok){if(done)return;done=true;autoSecurityBusy=false;if(!ok)lastAutoSecurityAt=Date.now()-540000;try{if(f.parentNode)f.parentNode.removeChild(f);}catch(e){}};var read=function(){try{tries++;var d=f.contentDocument||(f.contentWindow&&f.contentWindow.document);if(!d||!d.body){if(tries<24)setTimeout(read,400);else finish(false);return;}var page=d.querySelector('.UserSecurityPage');if(!page||tries<6){if(tries<24)setTimeout(read,400);else finish(false);return;}var bodyText=String(d.body.innerText||'').toLowerCase();var providers=[];var addp=function(x){x=String(x||'').trim();if(x&&providers.indexOf(x)<0)providers.push(x);};var nodes=d.querySelectorAll('button,a,li,.Form-group,.Setting,.LoginProvider');var pm={discord:'Discord',google:'Google',github:'GitHub',microsoft:'Microsoft',apple:'Apple',facebook:'Facebook',twitter:'Twitter',steam:'Steam'};for(var ni=0;ni<nodes.length;ni++){var nt=String(nodes[ni].innerText||nodes[ni].textContent||'').toLowerCase();if(!(nt.indexOf('disconnect')>=0||nt.indexOf('connected')>=0||nt.indexOf('unlink')>=0||nt.indexOf('linked')>=0))continue;for(var pk in pm){if(nt.indexOf(pk)>=0)addp(pm[pk]);}}var sessions=d.querySelectorAll('.AccessTokensList-item').length;var activeSessions=d.querySelectorAll('.AccessTokensList-item--active').length;var data={seen:true,path:'/u/'+slug+'/security',sessionCount:Number(sessions||0),activeSessionCount:Number(activeSessions||0),providers:providers,passwordControls:(bodyText.indexOf('password')>=0),emailControls:(bodyText.indexOf('email')>=0),twoFactorControls:(bodyText.indexOf('two-factor')>=0||bodyText.indexOf('two factor')>=0||bodyText.indexOf('2fa')>=0||bodyText.indexOf('authenticator')>=0)};HCFNative.updateSecuritySummary(JSON.stringify(data),String(location.host||''));finish(true);}catch(e){if(tries<24)setTimeout(read,400);else finish(false);}};f.onload=function(){setTimeout(read,500);};document.body.appendChild(f);setTimeout(function(){if(!done)read();},1500);setTimeout(function(){if(!done)finish(false);},14000);}catch(e){autoSecurityBusy=false;}};var reportRoute=function(){try{var u=String(location.href||'');if(u!==lastRoute){lastRoute=u;HCFNative.routeChanged(u);}}catch(e){}};try{if(!window.__HCF_MUTATION_HOOK__&&window.XMLHttpRequest){window.__HCF_MUTATION_HOOK__=true;var XO=XMLHttpRequest.prototype.open,XS=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__hcfM=String(m||'GET').toUpperCase();this.__hcfU=String(u||'');return XO.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){try{if((this.__hcfM==='POST'||this.__hcfM==='PATCH'||this.__hcfM==='DELETE')&&this.__hcfU.indexOf('/api/')>=0){this.addEventListener('load',function(){try{if(this.status>=200&&this.status<300)HCFNative.forumMutation(String(this.__hcfU||''));}catch(e){}});}}catch(e){}return XS.apply(this,arguments);};}}catch(e){}var fixText=function(root){try{if(!root)return;if(root.nodeType===3){var v=root.nodeValue||'';if(v.indexOf(WARNING)>=0)root.nodeValue=v.split(WARNING).join(REPLACEMENT);return;}var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);var n,c=0;while((n=w.nextNode())&&c++<500){var v=n.nodeValue||'';if(v.indexOf(WARNING)>=0)n.nodeValue=v.split(WARNING).join(REPLACEMENT);}}catch(e){}};var fixSecurityLabels=function(){try{if(String(location.pathname||'').indexOf('/security')<0)return;var active=document.querySelectorAll('.AccessTokensList-item--active .AccessTokensList-item-title-main');for(var i=0;i<active.length;i++){var t=(active[i].textContent||'').trim();if(t&&t.indexOf(\"Harley's Clan Forum App\")<0)active[i].textContent=\"Harley's Clan Forum App on AndroidOS\";}}catch(e){}};var sync=function(){reportRoute();syncIdentity();syncSecurity();autoSecurity();fixSecurityLabels();};try{if(!window.__HCF_ROUTE_HOOK__){window.__HCF_ROUTE_HOOK__=true;['pushState','replaceState'].forEach(function(k){var old=history[k];if(typeof old!=='function')return;history[k]=function(){var r=old.apply(this,arguments);setTimeout(reportRoute,0);return r;};});window.addEventListener('popstate',reportRoute);window.addEventListener('hashchange',reportRoute);}}catch(e){}if(document.body){fixText(document.body);window.__HCF_NATIVE_OBSERVER__=new MutationObserver(function(ms){for(var i=0;i<ms.length;i++){var added=ms[i].addedNodes||[];for(var j=0;j<added.length;j++)fixText(added[j]);}try{clearTimeout(window.__HCF_SYNC_TIMER__);}catch(e){}window.__HCF_SYNC_TIMER__=setTimeout(sync,75);});window.__HCF_NATIVE_OBSERVER__.observe(document.body,{childList:true,subtree:true});}window.__HCF_NATIVE_V040__={sync:sync};sync();if(!window.__HCF_NATIVE_TIMER__){window.__HCF_NATIVE_TIMER__=setInterval(sync,1000);}try{window.dispatchEvent(new CustomEvent('hcf-app-ready',{detail:{platform:'android',version:VERSION}}));}catch(e){}})();", null);
                installPerformanceCss();
                installWebThemeBridge();
                installNativeMediaAndAccentHooks();
                installExternalLinkHook();
                AppLogger.info(this, "web_bridge_installed", AppLogger.safeUrl(this.webView.getUrl()));
            }
        }


        private void installExternalLinkHook() {
            if (this.webView == null || !isTrustedForumPage()) {
                return;
            }
            try {
                this.webView.evaluateJavascript("(function(){try{if(window.__HCF_NATIVE_EXTERNAL_LINKS__)return;window.__HCF_NATIVE_EXTERNAL_LINKS__=true;document.addEventListener('click',function(ev){try{var t=ev.target;if(!t||!t.closest)return;var a=t.closest('a[href]');if(!a)return;var raw=String(a.getAttribute('href')||'').trim();if(!raw||raw.charAt(0)==='#'||/^javascript:/i.test(raw))return;var u=new URL(a.href,location.href),s=String(u.protocol||'').toLowerCase(),h=String(u.hostname||'').toLowerCase();if((s==='http:'||s==='https:')&&(h==='forum.harleytg.com'||h==='harleysclan.freeflarum.com'))return;if(s!=='http:'&&s!=='https:'&&s!=='mailto:'&&s!=='tel:')return;ev.preventDefault();ev.stopPropagation();HCFNative.openExternalLink(String(u.href));}catch(e){}},true);}catch(e){}})();", null);
            } catch (Throwable th) {
                AppLogger.warn(this, "external_link_hook", th.getClass().getSimpleName());
            }
        }

        private void installWebThemeBridge() {
            if (this.webView == null || !isTrustedForumPage()) {
                return;
            }
            String mode = ThemeManager.mode(this);
            if ("auto_forum".equals(mode)) {
                try {
                    this.webView.evaluateJavascript("(function(){try{var last='';var cookie=function(k){try{var a=String(document.cookie||'').split(';');for(var i=0;i<a.length;i++){var p=a[i].trim(),x=p.indexOf('=');if(x<0)continue;if(decodeURIComponent(p.substring(0,x))===k)return decodeURIComponent(p.substring(x+1));}}catch(e){}return '';};var num=function(v,d){var n=Number(v);return isFinite(n)?n:d;};var fof=function(){try{var def=0;try{if(window.app&&app.forum&&app.forum.attribute)def=num(app.forum.attribute('fof-nightmode.default_theme'),0);}catch(e){}if(def!==0&&def!==1&&def!==2)def=0;var u=null,p=null;try{u=window.app&&app.session?app.session.user:null;p=u&&typeof u.preferences==='function'?u.preferences():null;}catch(e){}var per=!u||!!(p&&p.fofNightMode_perDevice),v;if(per){var c=cookie('flarum_nightmode');v=(c===''?def:num(c,def));}else{v=num(p&&p.fofNightMode,def);if(v===-1)v=def;}if(v===1)return 'light';if(v===2)return 'dark';return 'auto';}catch(e){return '';}};var rgb=function(v){var m=String(v||'').match(/rgba?\\s*\\(\\s*(\\d+)[, ]+\\s*(\\d+)[, ]+\\s*(\\d+)(?:[, \\/]+\\s*([0-9.]+))?/i);if(!m)return null;return [Number(m[1]),Number(m[2]),Number(m[3]),m[4]===undefined?1:Number(m[4])];};var lum=function(c){return c?(0.2126*c[0]+0.7152*c[1]+0.0722*c[2]):128;};var fallback=function(){try{var d=document.documentElement,b=document.body,raw='';if(d)raw+=' '+String(d.getAttribute('data-theme')||'')+' '+String(d.className||'');if(b)raw+=' '+String(b.className||'');raw=raw.toLowerCase();if(/(^|[ _-])(dark|night|nightmode)([ _-]|$)/.test(raw))return 'dark';if(/(^|[ _-])(light|day)([ _-]|$)/.test(raw))return 'light';var n=b||d,c=n?rgb(getComputedStyle(n).backgroundColor):null;if(c&&c[3]>.15)return lum(c)<128?'dark':'light';return 'auto';}catch(e){return 'auto';}};var report=function(){try{var t=fof()||fallback();if(t&&t!==last){last=t;HCFNative.updateForumTheme(t);}}catch(e){}};report();setTimeout(report,120);setTimeout(report,500);try{document.addEventListener('fofnightmodechange',function(){setTimeout(report,0);});}catch(e){}try{if(!window.__HCF_FORUM_THEME_OBSERVER__){window.__HCF_FORUM_THEME_OBSERVER__=new MutationObserver(function(){clearTimeout(window.__HCF_FORUM_THEME_WAIT__);window.__HCF_FORUM_THEME_WAIT__=setTimeout(report,60);});window.__HCF_FORUM_THEME_OBSERVER__.observe(document.documentElement,{attributes:true,attributeFilter:['class','style','data-theme','data-color-scheme'],childList:true,subtree:true});}}catch(e){}try{var mq=matchMedia('(prefers-color-scheme: dark)');if(mq&&!window.__HCF_FORUM_THEME_MQ__){window.__HCF_FORUM_THEME_MQ__=true;var f=function(){setTimeout(report,0);};if(mq.addEventListener)mq.addEventListener('change',f);else if(mq.addListener)mq.addListener(f);}}catch(e){}if(!window.__HCF_FORUM_THEME_TIMER__)window.__HCF_FORUM_THEME_TIMER__=setInterval(report,900);}catch(e){}})();", null);
                    return;
                } catch (Throwable unused) {
                    return;
                }
            }
            if ("auto_phone".equals(mode)) {
                return;
            }
            String webColorScheme = ThemeManager.webColorScheme(this);
            try {
                this.webView.evaluateJavascript("(function(){try{var m='" + webColorScheme + "',ft='" + ("dark".equals(webColorScheme) ? "night" : "day") + "';window.__HCF_APP_THEME__=m;var apply=function(){try{var changed=false;var d=document.documentElement;if(d){d.setAttribute('data-hcf-app-theme',m);d.style.colorScheme=m;}var b=document.body;if(b)b.setAttribute('data-hcf-app-theme',m);var meta=document.querySelector('meta[name=\\\"color-scheme\\\"]');if(!meta){meta=document.createElement('meta');meta.name='color-scheme';document.head&&document.head.appendChild(meta);}if(meta&&meta.content!==m){meta.content=m;changed=true;}var u='';try{if(window.app&&app.data)u=String(app.data['fof-nightmode.assets.'+ft]||'');}catch(e){}if(u&&document.head){var own=document.getElementById('hcf-app-theme-stylesheet');if(!own){own=document.createElement('link');own.id='hcf-app-theme-stylesheet';own.rel='stylesheet';document.head.appendChild(own);changed=true;}if(own.href!==u){own.href=u;changed=true;}var links=document.querySelectorAll('link.nightmode,link.nightmode-light,link.nightmode-dark');for(var i=0;i<links.length;i++){var l=links[i];if(l===own)continue;if(!l.disabled){l.disabled=true;changed=true;}if(l.media!=='not all'){l.media='not all';changed=true;}}}if(changed||window.__HCF_APP_FORCED_FOF__!==ft){window.__HCF_APP_FORCED_FOF__=ft;try{document.dispatchEvent(new CustomEvent('fofnightmodechange',{detail:ft}));}catch(e){}}}catch(e){}};apply();try{if(!window.__HCF_APP_THEME_OBSERVER__&&document.head){window.__HCF_APP_THEME_OBSERVER__=new MutationObserver(function(){setTimeout(apply,0);});window.__HCF_APP_THEME_OBSERVER__.observe(document.head,{childList:true,subtree:true,attributes:true,attributeFilter:['href','class','media','disabled']});}}catch(e){}if(!window.__HCF_APP_THEME_TIMER__){window.__HCF_APP_THEME_TIMER__=setInterval(apply,1000);}try{window.dispatchEvent(new CustomEvent('hcf-app-theme-change',{detail:{theme:m}}));}catch(e){}}catch(e){}})();", null);
            } catch (Throwable unused2) {
            }
        }

        private void installNativeMediaAndAccentHooks() {
            if (this.webView == null || !isTrustedForumPage()) {
                return;
            }
            try {
                this.webView.evaluateJavascript("(function(){try{var r=getComputedStyle(document.documentElement),c='';var keys=['--primary-color','--hcf-cyan','--cyan','--hc','--hc2'];for(var i=0;i<keys.length&&!c;i++)c=String(r.getPropertyValue(keys[i])||'').trim();if(!c){var m=document.querySelector('meta[name=theme-color]');if(m)c=String(m.content||'').trim();}if(/^#[0-9a-f]{3}([0-9a-f]{3})?$/i.test(c)){try{HCFNative.updateAccent(c);}catch(e){}}if(!window.__HCF_NATIVE_MEDIA__){window.__HCF_NATIVE_MEDIA__=true;document.addEventListener('click',function(ev){try{var t=ev.target;if(!t||!t.closest)return;var body=t.closest('.Post-body');if(!body)return;var el=t.closest('img,video');if(!el)return;var k=String(el.tagName||'').toLowerCase();var u='';if(k==='img')u=String(el.currentSrc||el.src||'');else{u=String(el.currentSrc||el.src||'');if(!u){var src=el.querySelector('source');if(src)u=String(src.src||'');}}if(!/^https:\\/\\//i.test(u))return;ev.preventDefault();ev.stopPropagation();HCFNative.openMedia(u,k);}catch(e){}},true);}}catch(e){}})();", null);
            } catch (Throwable unused) {
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void recordNotificationCount(String str, int i, String str2) {
            if (str == null || str.trim().isEmpty()) {
                return;
            }
            if (!ForumUrlRouter.isForumHost(str2) && (str2 = this.activeHost) == null) {
                str2 = "forum.harleytg.com";
            }
            String string = this.prefs.getString("session_user_id", "");
            boolean z = string == null || !str.equals(string);
            SharedPreferences.Editor putString = this.prefs.edit().putString("session_user_id", str).putString("active_host", str2);
            if (z) {
                putString.remove("last_notification_count").remove("delivered_notification_ids");
            }
            putString.apply();
            int recordForumNotificationCount = NotificationHelper.recordForumNotificationCount(this, i, str2, "foreground");
            updateNotificationChrome(i);
            if (recordForumNotificationCount > 0) {
                ForumNotificationSync.deliverObservedCountAsync(this, str2, recordForumNotificationCount, "foreground");
            }
            if (z) {
                NotificationSyncScheduler.apply(this);
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleIdentityUpdate(String str, String str2) {
            if (!ForumUrlRouter.isForumHost(str2) && (str2 = this.activeHost) == null) {
                str2 = "forum.harleytg.com";
            }
            try {
                ForumIdentity.Snapshot load = ForumIdentity.load(this);
                ForumIdentity.Snapshot fromBridgeJson = ForumIdentity.fromBridgeJson(str, str2);
                if (!fromBridgeJson.loggedIn || (load.loggedIn && !load.userId.isEmpty() && !load.userId.equals(fromBridgeJson.userId))) {
                    ForumSecurity.clear(this);
                }
                ForumIdentity.save(this, fromBridgeJson);
                updateIdentityChrome(fromBridgeJson);
                if (fromBridgeJson.loggedIn && !fromBridgeJson.userId.isEmpty()) {
                    recordNotificationCount(fromBridgeJson.userId, Math.max(fromBridgeJson.unreadNotifications, fromBridgeJson.newNotifications), str2);
                } else {
                    this.prefs.edit().remove("session_user_id").remove("last_notification_count").remove("delivered_notification_ids").apply();
                    updateNotificationChrome(0);
                    NotificationSyncScheduler.apply(this);
                }
                if (this.welcomeBackPending) {
                    this.welcomeBackPending = false;
                    showWelcomeBanner(fromBridgeJson);
                }
            } catch (Throwable th) {
                AppLogger.warn(this, "identity_sync", th.getClass().getSimpleName());
            }
        }

        private void updateIdentityChrome(ForumIdentity.Snapshot snapshot) {
            ImageView.ScaleType scaleType;
            if (snapshot == null) {
                String str = this.activeHost;
                if (str == null) {
                    str = "forum.harleytg.com";
                }
                snapshot = ForumIdentity.guest(str);
            }
            ForumSecurity.Snapshot load = ForumSecurity.load(this);
            TextView textView = this.drawerIdentityText;
            if (textView != null) {
                textView.setText(snapshot.identityLabel());
            }
            TextView textView2 = this.drawerIdentityUsername;
            if (textView2 != null) {
                textView2.setText(snapshot.usernameDisplay());
            }
            TextView textView3 = this.drawerIdentityMeta;
            if (textView3 != null) {
                textView3.setText(snapshot.identityMetaLabel());
            }
            updateNotificationChrome(effectiveNotificationCount(snapshot));
            String mergeLabels = ForumSecurity.mergeLabels(snapshot.connections, load.seen ? load.providers : "");
            String str2 = mergeLabels != null ? mergeLabels : "";
            boolean z = snapshot.loggedIn && (!snapshot.email.isEmpty() || containsProvider(str2, "email"));
            boolean z2 = snapshot.loggedIn && containsProvider(str2, "discord");
            View view = this.drawerLinkedAccounts;
            if (view != null) {
                view.setVisibility(snapshot.loggedIn ? 0 : 8);
            }
            updateProviderChip(this.drawerProviderEmail, "Email", z, snapshot.loggedIn);
            updateProviderChip(this.drawerProviderDiscord, "Discord", z2, snapshot.loggedIn);
            ImageButton imageButton = this.drawerSecurity;
            if (imageButton != null) {
                imageButton.setEnabled(snapshot.loggedIn);
                this.drawerSecurity.setAlpha(snapshot.loggedIn ? 1.0f : 0.42f);
                this.drawerSecurity.setContentDescription(snapshot.loggedIn ? "Account Security" : "Account Security • Sign in required");
                configureDrawerSecurityIcon();
            }
            if (this.drawerIdentityAvatar != null) {
                boolean z3 = (!snapshot.loggedIn || snapshot.avatarUrl == null || snapshot.avatarUrl.trim().isEmpty()) ? false : true;
                ImageView imageView = this.drawerIdentityAvatar;
                if (z3) {
                    scaleType = ImageView.ScaleType.FIT_XY;
                } else {
                    scaleType = ImageView.ScaleType.FIT_CENTER;
                }
                imageView.setScaleType(scaleType);
                this.drawerIdentityAvatar.setAdjustViewBounds(false);
                this.drawerIdentityAvatar.setCropToPadding(z3);
                int dp = dp(z3 ? 1 : 6);
                this.drawerIdentityAvatar.setPadding(dp, dp, dp, dp);
                this.drawerIdentityAvatar.setClipToOutline(true);
            }
            loadIdentityAvatar(snapshot);
        }

        private int effectiveNotificationCount(ForumIdentity.Snapshot snapshot) {
            if (snapshot == null || !snapshot.loggedIn) {
                return 0;
            }
            int max = Math.max(snapshot.unreadNotifications, snapshot.newNotifications);
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences == null || !sharedPreferences.contains("last_notification_count")) {
                return max;
            }
            String string = this.prefs.getString("session_user_id", "");
            return (string == null || string.trim().isEmpty() || snapshot.userId == null || snapshot.userId.trim().isEmpty() || string.equals(snapshot.userId)) ? Math.max(0, this.prefs.getInt("last_notification_count", max)) : max;
        }

        private static boolean containsProvider(String str, String str2) {
            if (str == null || str2 == null) {
                return false;
            }
            return str.toLowerCase(Locale.US).contains(str2.toLowerCase(Locale.US));
        }

        private void updateProviderChip(TextView textView, String str, boolean z, boolean z2) {
            if (textView == null) {
                return;
            }
            textView.setText(str);
            textView.setAlpha(z ? 1.0f : z2 ? 0.58f : 0.42f);
            StringBuilder sb = new StringBuilder();
            sb.append(str);
            sb.append(z ? " connected" : z2 ? " not linked" : " unavailable while signed out");
            textView.setContentDescription(sb.toString());
        }

        private void loadIdentityAvatar(ForumIdentity.Snapshot snapshot) {
            if (this.drawerIdentityAvatar == null) {
                return;
            }
            if (snapshot == null || !snapshot.loggedIn || snapshot.avatarUrl == null || snapshot.avatarUrl.trim().isEmpty()) {
                this.drawerIdentityAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int dp = dp(6);
                this.drawerIdentityAvatar.setPadding(dp, dp, dp, dp);
                if (!"__hcf_default__".equals(this.drawerIdentityAvatar.getTag())) {
                    this.drawerIdentityAvatar.setImageResource(R.drawable.htg_app_logo);
                    this.drawerIdentityAvatar.setTag("__hcf_default__");
                }
                this.identityAvatarRequestedUrl = "";
                this.identityAvatarLoadedUrl = "";
                this.identityAvatarBitmap = null;
                return;
            }
            final String trim = snapshot.avatarUrl.trim();
            try {
                Uri parse = Uri.parse(trim);
                if ("https".equalsIgnoreCase(parse.getScheme()) && ForumUrlRouter.isForumHost(parse.getHost())) {
                    if (trim.equals(this.identityAvatarLoadedUrl) && this.identityAvatarBitmap != null) {
                        this.drawerIdentityAvatar.setScaleType(ImageView.ScaleType.FIT_XY);
                        int dp2 = dp(1);
                        this.drawerIdentityAvatar.setPadding(dp2, dp2, dp2, dp2);
                        if (trim.equals(this.drawerIdentityAvatar.getTag())) {
                            return;
                        }
                        this.drawerIdentityAvatar.setImageBitmap(this.identityAvatarBitmap);
                        this.drawerIdentityAvatar.setTag(trim);
                        return;
                    }
                    if (trim.equals(this.identityAvatarRequestedUrl)) {
                        return;
                    }
                    this.identityAvatarRequestedUrl = trim;
                    this.drawerIdentityAvatar.setTag(trim);
                    AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda68
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.m77lambda$loadIdentityAvatar$71$comharleytgforumdevMainActivity(trim);
                        }
                    });
                }
            } catch (Throwable unused) {
            }
        }

        /* renamed from: lambda$loadIdentityAvatar$71$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m77lambda$loadIdentityAvatar$71$comharleytgforumdevMainActivity(final String str) {
            HttpsURLConnection httpsURLConnection = null;
            HttpsURLConnection httpsURLConnection2 = null;
            try {
                httpsURLConnection = (HttpsURLConnection) new URL(str).openConnection();
            } catch (Throwable unused) {
            }
            try {
                httpsURLConnection.setConnectTimeout(6000);
                httpsURLConnection.setReadTimeout(6000);
                httpsURLConnection.setUseCaches(true);
                httpsURLConnection.setInstanceFollowRedirects(false);
                httpsURLConnection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER);
                if (httpsURLConnection.getResponseCode() != 200) {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                        return;
                    }
                    return;
                }
                final Bitmap decodeStream = BitmapFactory.decodeStream(httpsURLConnection.getInputStream());
                if (decodeStream == null) {
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                } else {
                    runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda32
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.m75lambda$loadIdentityAvatar$69$comharleytgforumdevMainActivity(str, decodeStream);
                        }
                    });
                    if (httpsURLConnection != null) {
                        httpsURLConnection.disconnect();
                    }
                }
            } catch (Throwable unused2) {
                httpsURLConnection2 = httpsURLConnection;
                try {
                    runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda33
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.m76lambda$loadIdentityAvatar$70$comharleytgforumdevMainActivity(str);
                        }
                    });
                } finally {
                    if (httpsURLConnection2 != null) {
                        httpsURLConnection2.disconnect();
                    }
                }
            }
        }

        /* renamed from: lambda$loadIdentityAvatar$69$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m75lambda$loadIdentityAvatar$69$comharleytgforumdevMainActivity(String str, Bitmap bitmap) {
            ImageView imageView = this.drawerIdentityAvatar;
            if (imageView == null || !str.equals(imageView.getTag())) {
                return;
            }
            this.identityAvatarBitmap = bitmap;
            this.identityAvatarLoadedUrl = str;
            this.identityAvatarRequestedUrl = "";
            this.drawerIdentityAvatar.setScaleType(ImageView.ScaleType.FIT_XY);
            int dp = dp(1);
            this.drawerIdentityAvatar.setPadding(dp, dp, dp, dp);
            this.drawerIdentityAvatar.setImageBitmap(bitmap);
        }

        /* renamed from: lambda$loadIdentityAvatar$70$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m76lambda$loadIdentityAvatar$70$comharleytgforumdevMainActivity(String str) {
            if (str.equals(this.identityAvatarRequestedUrl)) {
                this.identityAvatarRequestedUrl = "";
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void handleSecuritySummaryUpdate(String str, String str2) {
            if (!ForumUrlRouter.isForumHost(str2) && (str2 = this.activeHost) == null) {
                str2 = "forum.harleytg.com";
            }
            try {
                ForumIdentity.Snapshot load = ForumIdentity.load(this);
                if (!load.loggedIn) {
                    ForumSecurity.clear(this);
                    return;
                }
                ForumSecurity.Snapshot fromBridgeJson = ForumSecurity.fromBridgeJson(str, str2);
                ForumSecurity.save(this, fromBridgeJson);
                updateIdentityChrome(load);
                AppLogger.info(this, "security_identity_sync", "sessions=" + fromBridgeJson.sessionCount + " active=" + fromBridgeJson.activeSessionCount);
            } catch (Throwable th) {
                AppLogger.warn(this, "security_identity_sync", th.getClass().getSimpleName());
            }
        }

        private String accountSecurityUrl(ForumIdentity.Snapshot snapshot) {
            if (snapshot != null && snapshot.loggedIn) {
                String str = !snapshot.slug.isEmpty() ? snapshot.slug : snapshot.username;
                if (str != null && !str.trim().isEmpty()) {
                    String str2 = this.activeHost;
                    if (!ForumUrlRouter.isForumHost(str2)) {
                        str2 = snapshot.host;
                    }
                    if (!ForumUrlRouter.isForumHost(str2)) {
                        str2 = chooseInitialHost();
                    }
                    return "https://" + str2 + "/u/" + Uri.encode(str.trim()) + "/security";
                }
            }
            return "";
        }

        private void openAccountSecurity() {
            String accountSecurityUrl = accountSecurityUrl(ForumIdentity.load(this));
            if (accountSecurityUrl.isEmpty()) {
                Toast.makeText(this, "Sign in to the forum to open Account Security.", 0).show();
                return;
            }
            updateUrlChrome(accountSecurityUrl);
            WebView webView = this.webView;
            if (webView != null) {
                webView.loadUrl(accountSecurityUrl);
            }
            AppLogger.info(this, "account_security_open", AppLogger.safeUrl(accountSecurityUrl));
        }

        private void showWelcomeBanner(ForumIdentity.Snapshot snapshot) {
            String str;
            if (this.welcomeBanner == null || snapshot == null) {
                return;
            }
            if (snapshot.loggedIn) {
                str = "Welcome back @" + snapshot.identityLabel();
            } else {
                str = "Welcome back • Guest_Protocol";
            }
            showTransientBanner(str);
            AppLogger.info(this, "welcome_identity", snapshot.loggedIn ? snapshot.userId : "guest");
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void showTransientBanner(String str) {
            if (this.welcomeBanner == null || str == null || str.trim().isEmpty()) {
                return;
            }
            this.welcomeBanner.setOnClickListener(null);
            this.welcomeBanner.setClickable(false);
            this.welcomeBanner.setFocusable(false);
            this.welcomeBanner.setContentDescription(str);
            this.welcomeBanner.setText(str);
            this.welcomeBanner.animate().cancel();
            this.welcomeBanner.setAlpha(0.0f);
            this.welcomeBanner.setVisibility(0);
            this.welcomeBanner.animate().alpha(1.0f).setDuration(motionDuration(180L)).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda21
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m103xf4e13154();
                }
            }).start();
        }

        /* renamed from: lambda$showTransientBanner$74$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m103xf4e13154() {
            this.welcomeBanner.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda36
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m102xe1395dd3();
                }
            }, 3200L);
        }

        /* renamed from: lambda$showTransientBanner$73$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m102xe1395dd3() {
            TextView textView = this.welcomeBanner;
            if (textView == null) {
                return;
            }
            textView.animate().alpha(0.0f).setDuration(motionDuration(220L)).withEndAction(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda17
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m101xcd918a52();
                }
            }).start();
        }

        /* renamed from: lambda$showTransientBanner$72$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m101xcd918a52() {
            this.welcomeBanner.setVisibility(8);
        }

        private void handleNotificationIntent(Intent intent) {
            if (intent == null || intent.getData() == null) {
                return;
            }
            Uri data = intent.getData();
            if (ForumUrlRouter.isForumUrl(data)) {
                String host = data.getHost();
                if (ForumUrlRouter.isForumHost(host)) {
                    this.activeHost = host;
                    SharedPreferences sharedPreferences = this.prefs;
                    if (sharedPreferences != null) {
                        sharedPreferences.edit().putString("active_host", this.activeHost).apply();
                    }
                    String equivalentOnHost = ForumUrlRouter.equivalentOnHost(data, host);
                    updateHostChrome(host);
                    updateUrlChrome(equivalentOnHost);
                    AppLogger.info(this, "forum_link_open", AppLogger.safeUrl(equivalentOnHost));
                    WebView webView = this.webView;
                    if (webView != null) {
                        webView.loadUrl(equivalentOnHost);
                    }
                }
            }
        }

        @Override // android.app.Activity
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            setIntent(intent);
            handleNotificationIntent(intent);
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onResume() {
            WebView webView;
            super.onResume();
            HcfNotifications.InstantNotificationService.requestImmediateSync(this);
            if (this.liveUpdater != null) {
                this.liveUpdater.poke();
            }
            this.appliedThemeSignature = ThemeManager.signature(this);
            resumeUpdateInstallPermissionIfNeeded();
            // SetupActivity may temporarily cover MainActivity during an upgrade.
            // Re-schedule without marking seen so What's New appears after setup returns.
            scheduleWhatsNew(true);
            if (this.launchFailed || (webView = this.webView) == null) {
                return;
            }
            try {
                webView.resumeTimers();
                this.webView.setRendererPriorityPolicy(2, false);
            } catch (Throwable unused) {
            }
            long j = this.prefs.getLong("last_main_paused_at", 0L);
            if (j > 0 && System.currentTimeMillis() - j >= 20000) {
                this.welcomeBackPending = true;
            }
            try {
                registerNotificationEvents();
                registerNetworkState();
                applyChromePreferences();
                applyPerformanceMode();
                applySavedAccent();
                String str = this.activeHost;
                if (str == null) {
                    str = chooseInitialHost();
                }
                updateHostChrome(str);
                updateUrlChrome(this.webView.getUrl());
                updateIdentityChrome(ForumIdentity.load(this));
                installForumBridge();
                applyLiveUpdatePreference();
                LiveForumUpdater liveForumUpdater = this.liveUpdater;
                if (liveForumUpdater != null) {
                    liveForumUpdater.poke();
                }
                NotificationSyncScheduler.apply(this);
                String string = this.prefs.getString("session_user_id", "");
                if (string != null && !string.trim().isEmpty()) {
                    HcfNotifications.InstantNotificationService.requestImmediateSync(this);
                }
                String str2 = this.activeHost;
                if (str2 == null) {
                    str2 = "starting";
                }
                AppLogger.info(this, "main_resume", str2);
            } catch (Throwable th) {
                AppLogger.error(this, "main_resume", th.getClass().getSimpleName() + ": " + String.valueOf(th.getMessage()));
            }
        }

        private void resumeUpdateInstallPermissionIfNeeded() {
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences != null && sharedPreferences.getBoolean("update_resume_after_permission", false) && AppSecurity.canInstallUpdates(this)) {
                this.prefs.edit().remove("update_resume_after_permission").apply();
                long j = this.nativeUpdateDownloadId;
                if (j <= 0) {
                    j = AppUpdateDownloader.downloadedId(this);
                }
                if (j > 0) {
                    continueInstallAfterVerification(j, "Install permission enabled • downloaded update remains verified.");
                }
            }
        }

        @Override // android.app.Activity
        protected void onPause() {
            String str = this.activeHost;
            if (str == null) {
                str = "";
            }
            AppLogger.info(this, "main_pause", str);
            SharedPreferences sharedPreferences = this.prefs;
            if (sharedPreferences != null) {
                sharedPreferences.edit().putLong("last_main_paused_at", System.currentTimeMillis()).apply();
            }
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.stop();
            }
            WebView webView = this.webView;
            if (webView != null) {
                try {
                    webView.pauseTimers();
                    this.webView.setRendererPriorityPolicy(1, true);
                } catch (Throwable unused) {
                }
            }
            unregisterNotificationEvents();
            unregisterNetworkState();
            super.onPause();
        }

        @Override // android.app.Activity, android.view.Window.Callback
        public boolean dispatchTouchEvent(MotionEvent motionEvent) {
            try {
                if (!this.launchFailed && motionEvent != null && this.drawerPanel != null) {
                    int actionMasked = motionEvent.getActionMasked();
                    boolean z = true;
                    if (actionMasked == 0) {
                        RuntimeState.noteUserInteraction();
                        LiveForumUpdater liveForumUpdater = this.liveUpdater;
                        if (liveForumUpdater != null) {
                            liveForumUpdater.noteUserInteraction();
                        }
                        this.drawerSwipeStartX = motionEvent.getX();
                        this.drawerSwipeStartY = motionEvent.getY();
                        this.drawerSwipeStartAt = System.currentTimeMillis();
                        int max = Math.max(dp(64), Math.round(getResources().getDisplayMetrics().widthPixels * 0.16f));
                        if (this.drawerPanel.getVisibility() == 0 || this.drawerSwipeStartX < getResources().getDisplayMetrics().widthPixels - max) {
                            z = false;
                        }
                        this.drawerSwipeCandidate = z;
                    } else if (actionMasked == 1) {
                        if (this.drawerSwipeCandidate) {
                            float x = motionEvent.getX() - this.drawerSwipeStartX;
                            float y = motionEvent.getY() - this.drawerSwipeStartY;
                            long currentTimeMillis = System.currentTimeMillis() - this.drawerSwipeStartAt;
                            if (Math.abs(x) < Math.max(dp(72), Math.abs(y) * 1.35f)) {
                                z = false;
                            }
                            if (x <= (-dp(72)) && z && currentTimeMillis <= 1000) {
                                this.drawerSwipeCandidate = false;
                                openDrawer();
                                AppLogger.info(this, "drawer_swipe", "right-to-left");
                            }
                        }
                        this.drawerSwipeCandidate = false;
                    } else if (actionMasked == 3) {
                        this.drawerSwipeCandidate = false;
                    }
                }
            } catch (Throwable unused) {
            }
            return super.dispatchTouchEvent(motionEvent);
        }

        @Override // android.app.Activity
        public void onBackPressed() {
            if (this.launchFailed || this.webView == null) {
                super.onBackPressed();
                return;
            }
            View view = this.drawerPanel;
            if (view != null && view.getVisibility() == 0) {
                closeDrawer();
                return;
            }
            if (this.statusOverlay.getVisibility() == 0 && this.webView.getUrl() != null) {
                hideStatus();
                return;
            }
            try {
                this.webView.evaluateJavascript("(function(){try{var vis=function(e){return !!(e&&e.offsetParent!==null);};var m=document.querySelector('.ModalManager .Modal,.Modal');if(vis(m)){var c=m.querySelector('.Modal-close,button[aria-label=\\\"Close\\\"],button[title=\\\"Close\\\"]');if(c)c.click();else document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));return 'handled-modal';}var p=document.querySelector('.Composer');if(vis(p)){var b=p.querySelector('.Composer-controls .item-minimize button,.Composer-controls .item-close button,button[aria-label=\\\"Minimize\\\"],button[aria-label=\\\"Close\\\"]');if(b)b.click();else document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));return 'handled-composer';}return 'navigate';}catch(e){return 'navigate';}})();", new ValueCallback() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda35
                    @Override // android.webkit.ValueCallback
                    public final void onReceiveValue(Object obj) {
                        MainActivity.this.m78lambda$onBackPressed$75$comharleytgforumdevMainActivity((String) obj);
                    }
                });
            } catch (Throwable unused) {
                if (this.webView.canGoBack()) {
                    this.webView.goBack();
                } else {
                    super.onBackPressed();
                }
            }
        }

        /* renamed from: lambda$onBackPressed$75$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m78lambda$onBackPressed$75$comharleytgforumdevMainActivity(String str) {
            if (str != null && str.contains("handled")) {
                AppLogger.info(this, "back_consumed", str.replace("\\\"", ""));
            } else if (this.webView.canGoBack()) {
                this.webView.goBack();
            } else {
                super.onBackPressed();
            }
        }

        @Override // android.app.Activity
        protected void onSaveInstanceState(Bundle bundle) {
            WebView webView;
            if (!this.launchFailed && (webView = this.webView) != null) {
                webView.saveState(bundle);
            }
            super.onSaveInstanceState(bundle);
        }

        @Override // android.app.Activity
        protected void onDestroy() {
            WebView webView;
            String str = this.activeHost;
            if (str == null) {
                str = "";
            }
            AppLogger.info(this, "main_destroy", str);
            this.mainHandler.removeCallbacksAndMessages(null);
            AlertDialog alertDialog = this.nativeUpdateDialog;
            if (alertDialog != null) {
                try {
                    alertDialog.dismiss();
                } catch (Throwable unused) {
                }
                this.nativeUpdateDialog = null;
            }
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.destroy();
            }
            unregisterNotificationEvents();
            unregisterNetworkState();
            if (!this.launchFailed && (webView = this.webView) != null) {
                webView.removeJavascriptInterface("HCFNative");
                this.webView.stopLoading();
                this.webView.destroy();
            }
            super.onDestroy();
        }

        @Override // android.app.Activity
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == UPDATE_INSTALL_PERMISSION_REQUEST) {
                boolean allowed = AppSecurity.canInstallUpdates(this);
                AppLogger.info(this, "install_permission", allowed ? "allowed" : "not-allowed");
                prefs.edit().remove(AppPrefs.UPDATE_RESUME_AFTER_PERMISSION).apply();
                Toast.makeText(this, allowed ? "Secure app updates are enabled." : "Update installation permission was not enabled.", Toast.LENGTH_SHORT).show();
                if (allowed) {
                    long ready = nativeUpdateDownloadId > 0L ? nativeUpdateDownloadId : AppUpdateDownloader.downloadedId(this);
                    if (ready > 0L) continueInstallAfterVerification(ready, "Previously downloaded update verified.");
                } else {
                    nativeUpdateFlowActive = false;
                }
                return;
            }
            if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
            AppLogger.info(this, "file_chooser_result", result == null ? "cancelled" : "selected=" + result.length);
        }

        @Override // android.app.Activity
        public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
            String str;
            super.onRequestPermissionsResult(i, strArr, iArr);
            if (i == NOTIFICATION_PERMISSION_REQUEST) {
                boolean z = iArr.length > 0 && iArr[0] == 0;
                if (z) {
                    str = "granted";
                } else {
                    str = "denied | " + NotificationHelper.status(this);
                }
                AppLogger.info(this, "notification_permission", str);
                if (z) {
                    String str2 = this.activeHost;
                    if (str2 == null) {
                        str2 = "forum.harleytg.com";
                    }
                    NotificationHelper.post(this, "Harley's Clan Forum", "Heads-up notifications are enabled. This is a test alert.", ForumUrlRouter.home(str2));
                }
                if (this.prefs.getBoolean("permission_onboarding_done", false)) {
                    return;
                }
                requestUpdateInstallPermissionIfNeeded();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        final class AppMessageBridge {
            private AppMessageBridge() {
            }

            @JavascriptInterface
            public void notify(final String str, final String str2, final String str3) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda3
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m115x9b5b3ad2(str, str2, str3);
                    }
                });
            }

            /* renamed from: lambda$notify$0$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m115x9b5b3ad2(String str, String str2, String str3) {
                if (!MainActivity.this.isTrustedForumPage()) {
                    AppLogger.warn(MainActivity.this, "bridge_notification_blocked", "untrusted-main-frame");
                    return;
                }
                long currentTimeMillis = System.currentTimeMillis();
                if (currentTimeMillis - MainActivity.this.lastBridgeNotificationAt < 0) {
                    return;
                }
                MainActivity.this.lastBridgeNotificationAt = currentTimeMillis;
                NotificationHelper.post(MainActivity.this, str, str2, str3);
            }

            @JavascriptInterface
            public void forumMutation(final String str) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m114x357c7f8a(str);
                    }
                });
            }

            /* renamed from: lambda$forumMutation$1$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m114x357c7f8a(String str) {
                RuntimeState.noteUserInteraction();
                HcfNotifications.InstantNotificationService.requestImmediateSync(MainActivity.this);
                if (MainActivity.this.liveUpdater != null) {
                    MainActivity.this.liveUpdater.poke();
                }
                AppLogger.info(MainActivity.this, "forum_mutation", AppLogger.safeUrl(str));
            }

            /* renamed from: lambda$openSettings$2$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m117x8e07200c() {
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) HcfSubActivities.SettingsActivity.class));
            }

            @JavascriptInterface
            public void openSettings() {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m117x8e07200c();
                    }
                });
            }

            @JavascriptInterface
            public boolean notificationsEnabled() {
                return NotificationHelper.canPost(MainActivity.this);
            }

            @JavascriptInterface
            public void requestNotificationPermission() {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda6
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m118xd9a4d449();
                    }
                });
            }

            /* renamed from: lambda$requestNotificationPermission$3$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m118xd9a4d449() {
                if (Build.VERSION.SDK_INT < 33 || MainActivity.this.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
                    return;
                }
                MainActivity.this.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, MainActivity.NOTIFICATION_PERMISSION_REQUEST);
            }

            @JavascriptInterface
            public void updateIdentity(final String str, final String str2) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda10
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m122xe67bd330(str, str2);
                    }
                });
            }

            /* renamed from: lambda$updateIdentity$4$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m122xe67bd330(String str, String str2) {
                if (MainActivity.this.isTrustedForumPage()) {
                    MainActivity.this.handleIdentityUpdate(str, str2);
                }
            }

            @JavascriptInterface
            public void updateSecuritySummary(final String str, final String str2) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda8
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m123xa4d2a53(str, str2);
                    }
                });
            }

            /* renamed from: lambda$updateSecuritySummary$5$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m123xa4d2a53(String str, String str2) {
                if (MainActivity.this.isTrustedForumPage()) {
                    MainActivity.this.handleSecuritySummaryUpdate(str, str2);
                }
            }

            @JavascriptInterface
            public void updateSession(final String str, final int i, final String str2) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda2
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m124xe52532a2(str, i, str2);
                    }
                });
            }

            /* renamed from: lambda$updateSession$6$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m124xe52532a2(String str, int i, String str2) {
                if (MainActivity.this.isTrustedForumPage()) {
                    MainActivity.this.recordNotificationCount(str, i, str2);
                }
            }

            @JavascriptInterface
            public void openMedia(final String str, final String str2) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda9
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m116x40f24ef4(str, str2);
                    }
                });
            }


            @JavascriptInterface
            public void openExternalLink(final String str) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!MainActivity.this.isTrustedForumPage()) {
                            AppLogger.warn(MainActivity.this, "bridge_external_link_blocked", "untrusted-main-frame");
                            return;
                        }
                        try {
                            Uri uri = Uri.parse(str == null ? "" : str.trim());
                            String scheme = uri.getScheme();
                            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                                    || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                                MainActivity.this.openExternal(uri);
                            } else {
                                AppLogger.warn(MainActivity.this, "bridge_external_link_blocked", "unsupported-scheme");
                            }
                        } catch (Throwable th) {
                            AppLogger.warn(MainActivity.this, "bridge_external_link_failed", th.getClass().getSimpleName());
                        }
                    }
                });
            }

            /* renamed from: lambda$openMedia$7$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m116x40f24ef4(String str, String str2) {
                String trim;
                if (MainActivity.this.isTrustedForumPage()) {
                    if (str == null) {
                        trim = "";
                    } else {
                        try {
                            trim = str.trim();
                        } catch (Throwable th) {
                            AppLogger.warn(MainActivity.this, "media_viewer", th.getClass().getSimpleName());
                            return;
                        }
                    }
                    Uri parse = Uri.parse(trim);
                    if ("https".equalsIgnoreCase(parse.getScheme()) && parse.getHost() != null) {
                        Intent intent = new Intent(MainActivity.this, (Class<?>) HcfSubActivities.MediaViewerActivity.class);
                        intent.putExtra("media_url", parse.toString());
                        if (str2 == null) {
                            str2 = "image";
                        }
                        intent.putExtra("media_kind", str2);
                        MainActivity.this.startActivity(intent);
                    }
                }
            }

            @JavascriptInterface
            public void updateForumTheme(final String str) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m121x1b13bb82(str);
                    }
                });
            }

            /* renamed from: lambda$updateForumTheme$8$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m121x1b13bb82(String str) {
                if (MainActivity.this.isTrustedForumPage() && "auto_forum".equals(ThemeManager.mode(MainActivity.this))) {
                    int resolvedNightMode = ThemeManager.resolvedNightMode(MainActivity.this);
                    boolean updateForumAutoTheme = ThemeManager.updateForumAutoTheme(MainActivity.this, str);
                    int resolvedNightMode2 = ThemeManager.resolvedNightMode(MainActivity.this);
                    if (updateForumAutoTheme) {
                        AppLogger.info(MainActivity.this, "forum_theme_auto", String.valueOf(str) + " | " + ThemeManager.autoSourceLabel(MainActivity.this));
                        if (resolvedNightMode != resolvedNightMode2) {
                            return;
                        }
                        ThemeManager.applySystemBars(MainActivity.this);
                        MainActivity mainActivity = MainActivity.this;
                        mainActivity.appliedThemeSignature = ThemeManager.signature(mainActivity);
                    }
                }
            }

            @JavascriptInterface
            public void updateAccent(final String str) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda4
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m120x3c1e593f(str);
                    }
                });
            }

            /* renamed from: lambda$updateAccent$9$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m120x3c1e593f(String str) {
                int parseAccent;
                if (MainActivity.this.isTrustedForumPage() && (parseAccent = MainActivity.this.parseAccent(str)) != 0) {
                    MainActivity.this.prefs.edit().putString("native_accent", str.trim()).apply();
                    MainActivity.this.applyAccentColor(parseAccent);
                }
            }

            @JavascriptInterface
            public void routeChanged(final String str) {
                MainActivity.this.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$AppMessageBridge$$ExternalSyntheticLambda7
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.AppMessageBridge.this.m119xf047a5e9(str);
                    }
                });
            }

            /* renamed from: lambda$routeChanged$10$com-harleytg-forum-dev-MainActivity$AppMessageBridge, reason: not valid java name */
            /* synthetic */ void m119xf047a5e9(String str) {
                if (MainActivity.this.isTrustedForumPage()) {
                    MainActivity.this.handleSpaRouteChange(str);
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        final class HcfChromeClient extends WebChromeClient {
            private HcfChromeClient() {
            }

            @Override // android.webkit.WebChromeClient
            public boolean onJsAlert(WebView webView, String str, String str2, JsResult jsResult) {
                if (ForumUrlRouter.isForumUrl(Uri.parse(str == null ? "" : str)) && MainActivity.isUnsupportedPwaPushMessage(str2)) {
                    jsResult.confirm();
                    final MainActivity mainActivity = MainActivity.this;
                    mainActivity.runOnUiThread(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$HcfChromeClient$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.handleNativePushRequest();
                        }
                    });
                    return true;
                }
                return super.onJsAlert(webView, str, str2, jsResult);
            }

            @Override // android.webkit.WebChromeClient
            public void onProgressChanged(WebView webView, int i) {
                MainActivity.this.pageProgress.setProgress(i);
                MainActivity.this.pageProgress.setVisibility(i >= 100 ? 8 : 0);
            }

            @Override // android.webkit.WebChromeClient
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> valueCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = valueCallback;
                Intent intent = new Intent("android.intent.action.GET_CONTENT");
                intent.addCategory("android.intent.category.OPENABLE");
                intent.setType("*/*");
                String[] acceptTypes = fileChooserParams == null ? null : fileChooserParams.getAcceptTypes();
                if (acceptTypes != null) {
                    ArrayList arrayList = new ArrayList();
                    for (String str : acceptTypes) {
                        if (str != null && !str.trim().isEmpty() && str.contains("/")) {
                            arrayList.add(str.trim());
                        }
                    }
                    if (arrayList.size() == 1) {
                        intent.setType((String) arrayList.get(0));
                    } else if (arrayList.size() > 1) {
                        intent.putExtra("android.intent.extra.MIME_TYPES", (String[]) arrayList.toArray(new String[0]));
                    }
                }
                boolean z = fileChooserParams != null && fileChooserParams.getMode() == 1;
                intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", z);
                try {
                    MainActivity.this.startActivityForResult(Intent.createChooser(intent, z ? "Choose files to upload" : "Choose photo, video, or file"), MainActivity.FILE_CHOOSER_REQUEST);
                    AppLogger.info(MainActivity.this, "file_chooser_open", z ? "multi-picker" : "content-picker");
                    return true;
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.filePathCallback = null;
                    AppLogger.error(MainActivity.this, "file_chooser_failed", e.getClass().getSimpleName());
                    Toast.makeText(MainActivity.this, "No photo/file picker is available.", 0).show();
                    return false;
                }
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void verifyRenderedForumPage(final WebView webView, final String str) {
            if (webView == null || this.mainFrameLoadFailed) {
                return;
            }
            try {
                webView.evaluateJavascript("(function(){try{var t=((document.title||'')+' '+((document.body&&document.body.innerText)||'')).toLowerCase();return (t.indexOf('network connection error')>=0||t.indexOf('cannot be used offline')>=0)?'hcf-native-error':'ok';}catch(e){return 'ok';}})();", new ValueCallback() { // from class: com.harleytg.forum.dev.MainActivity$$ExternalSyntheticLambda11
                    @Override // android.webkit.ValueCallback
                    public final void onReceiveValue(Object obj) {
                        MainActivity.this.m111xe973ecad(str, webView, (String) obj);
                    }
                });
            } catch (Throwable unused) {
                completeSuccessfulForumLoad(webView);
            }
        }

        /* renamed from: lambda$verifyRenderedForumPage$76$com-harleytg-forum-dev-MainActivity, reason: not valid java name */
        /* synthetic */ void m111xe973ecad(String str, WebView webView, String str2) {
            Uri currentForumUri;
            if (this.mainFrameLoadFailed) {
                return;
            }
            if (str2 != null && str2.contains("hcf-native-error")) {
                try {
                    currentForumUri = Uri.parse(str);
                } catch (Throwable unused) {
                    currentForumUri = currentForumUri();
                }
                showUnavailable(ErrorSystem.offline(), currentForumUri);
                return;
            }
            completeSuccessfulForumLoad(webView);
        }

        private void completeSuccessfulForumLoad(WebView webView) {
            if (this.mainFrameLoadFailed) {
                return;
            }
            hideStatus();
            if (this.rendererRecoveryPending) {
                this.rendererRecoveryPending = false;
                showTransientBanner("Forum viewer restarted • page restored");
            }
            installForumBridge();
            LiveForumUpdater liveForumUpdater = this.liveUpdater;
            if (liveForumUpdater != null) {
                liveForumUpdater.reset();
                this.liveUpdater.poke();
            }
            finishLiveRefresh(webView);
        }

        /* JADX INFO: Access modifiers changed from: private */
        final class HcfWebViewClient extends WebViewClient {
            private HcfWebViewClient() {
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
                return handleNavigation(webView, webResourceRequest == null ? null : webResourceRequest.getUrl());
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return handleNavigation(webView, url == null ? null : Uri.parse(url));
            }

            private boolean handleNavigation(WebView webView, Uri url) {
                if (url == null) {
                    AppLogger.warn(MainActivity.this, "webview_navigation_blocked", "missing-url");
                    return true;
                }
                if (MainActivity.this.isNativeInstallRoute(url)) {
                    MainActivity.this.startNativeUpdateFlow();
                    return true;
                }
                if (ForumUrlRouter.isForumUrl(url)) {
                    String host = url.getHost();
                    if (host != null && (MainActivity.this.activeHost == null || !MainActivity.this.activeHost.equalsIgnoreCase(host))) {
                        MainActivity.this.activeHost = host;
                        MainActivity.this.prefs.edit().putString("active_host", MainActivity.this.activeHost).apply();
                        MainActivity mainActivity = MainActivity.this;
                        mainActivity.updateHostChrome(mainActivity.activeHost);
                        String equivalentOnHost = ForumUrlRouter.equivalentOnHost(url, MainActivity.this.activeHost);
                        webView.loadUrl(equivalentOnHost);
                        AppLogger.info(MainActivity.this, "forum_host_link", AppLogger.safeUrl(equivalentOnHost));
                        return true;
                    }
                    if (!"http".equalsIgnoreCase(url.getScheme())) {
                        return false;
                    }
                    webView.loadUrl(ForumUrlRouter.equivalentOnHost(url, host));
                    return true;
                }
                if ("hcf-app".equalsIgnoreCase(url.getScheme()) && "settings".equalsIgnoreCase(url.getHost())) {
                    MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) HcfSubActivities.SettingsActivity.class));
                    return true;
                }
                String scheme = url.getScheme();
                if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    MainActivity.this.openExternal(url);
                    return true;
                }
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    MainActivity.this.openExternal(url);
                    AppLogger.info(MainActivity.this, "webview_external_routed", AppLogger.safeUrl(url.toString()));
                    return true;
                }
                MainActivity.this.showUnavailable(ErrorSystem.externalBlocked(url.toString()), url);
                MainActivity mainActivity2 = MainActivity.this;
                if (scheme == null) {
                    scheme = "null";
                }
                AppLogger.warn(mainActivity2, "unknown_scheme_blocked", scheme);
                return true;
            }

            @Override // android.webkit.WebViewClient
            public void onPageStarted(WebView webView, String str, Bitmap bitmap) {
                Uri parse = Uri.parse(str);
                if (!MainActivity.this.isNativeInstallRoute(parse)) {
                    if ((MainActivity.this.mainFrameLoadFailed || MainActivity.this.lastAppError != null) && ForumUrlRouter.isForumUrl(parse)) {
                        MainActivity.this.clearErrorStateForNavigation("page_started");
                    }
                    MainActivity.this.updateUrlChrome(str);
                    if (ForumUrlRouter.isForumUrl(parse)) {
                        MainActivity.this.lastRecoverableUrl = str;
                        MainActivity.this.prefs.edit().putString("last_recoverable_url", str).apply();
                        MainActivity.this.statusSubtitle.setText(parse.getHost());
                        MainActivity.this.updateHostChrome(parse.getHost());
                        final MainActivity mainActivity = MainActivity.this;
                        webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$HcfWebViewClient$$ExternalSyntheticLambda0
                            @Override // java.lang.Runnable
                            public final void run() {
                                MainActivity.this.installForumBridge();
                            }
                        }, 350L);
                        final MainActivity mainActivity2 = MainActivity.this;
                        webView.postDelayed(new Runnable() { // from class: com.harleytg.forum.dev.MainActivity$HcfWebViewClient$$ExternalSyntheticLambda1
                            @Override // java.lang.Runnable
                            public final void run() {
                                MainActivity.this.installForumBridge();
                            }
                        }, 1200L);
                    }
                    TelemetryService.noteRoute(MainActivity.this, str);
                    AppLogger.info(MainActivity.this, "page_started", AppLogger.safeUrl(str));
                    return;
                }
                try {
                    webView.stopLoading();
                } catch (Throwable unused) {
                }
                MainActivity.this.startNativeUpdateFlow();
            }

            @Override // android.webkit.WebViewClient
            public void onPageCommitVisible(WebView webView, String str) {
                MainActivity.this.updateUrlChrome(str);
                MainActivity.this.installForumBridge();
            }

            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView, String str) {
                Uri parse = Uri.parse(str);
                if (ForumUrlRouter.isForumUrl(parse)) {
                    MainActivity.this.lastRecoverableUrl = str;
                    MainActivity.this.prefs.edit().putString("last_recoverable_url", str).apply();
                }
                MainActivity.this.updateUrlChrome(str);
                TelemetryService.noteRoute(MainActivity.this, str);
                AppLogger.info(MainActivity.this, "page_finished", AppLogger.safeUrl(str));
                if (ForumUrlRouter.isForumUrl(parse) && MainActivity.this.activeHost.equalsIgnoreCase(parse.getHost())) {
                    if (!MainActivity.this.mainFrameLoadFailed) {
                        MainActivity.this.verifyRenderedForumPage(webView, str);
                    } else {
                        AppLogger.info(MainActivity.this, "page_finished_error_retained", AppLogger.safeUrl(str));
                    }
                }
            }

            @Override // android.webkit.WebViewClient
            public void onReceivedError(WebView webView, WebResourceRequest webResourceRequest, WebResourceError webResourceError) {
                if (webResourceRequest.isForMainFrame()) {
                    Uri url = webResourceRequest.getUrl();
                    ErrorSystem.AppError fromWebView = ErrorSystem.fromWebView(webResourceError.getErrorCode(), webResourceError.getDescription() == null ? "" : webResourceError.getDescription().toString(), !MainActivity.this.isNetworkAvailable());
                    AppLogger.warn(MainActivity.this, "web_error", fromWebView.code + " | " + fromWebView.technical + " | " + AppLogger.safeUrl(url.toString()));
                    MainActivity mainActivity = MainActivity.this;
                    StringBuilder sb = new StringBuilder();
                    sb.append(fromWebView.code);
                    sb.append(" | ");
                    sb.append(fromWebView.technical);
                    TelemetryService.sendDiagnosticEvent(mainActivity, "webview_connection_error", sb.toString());
                    if ("forum.harleytg.com".equalsIgnoreCase(url.getHost())) {
                        MainActivity.this.failOverFromPrimary(url, fromWebView);
                    } else {
                        MainActivity.this.showUnavailable(fromWebView, url);
                    }
                }
            }

            @Override // android.webkit.WebViewClient
            public void onReceivedHttpError(WebView webView, WebResourceRequest webResourceRequest, WebResourceResponse webResourceResponse) {
                if (webResourceRequest.isForMainFrame()) {
                    int statusCode = webResourceResponse.getStatusCode();
                    AppLogger.warn(MainActivity.this, "http_error", statusCode + " | " + AppLogger.safeUrl(webResourceRequest.getUrl().toString()));
                    if (statusCode >= 400) {
                        TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_http_error", "HTTP " + statusCode);
                    }
                    if (statusCode == 403 || statusCode == 404 || statusCode == 429 || (statusCode >= 500 && statusCode <= 599)) {
                        Uri url = webResourceRequest.getUrl();
                        ErrorSystem.AppError fromHttp = ErrorSystem.fromHttp(statusCode, url.getHost());
                        if ("forum.harleytg.com".equalsIgnoreCase(url.getHost())) {
                            MainActivity.this.failOverFromPrimary(url, fromHttp);
                        } else {
                            MainActivity.this.showUnavailable(fromHttp, url);
                        }
                    }
                }
            }

            @Override // android.webkit.WebViewClient
            public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
                sslErrorHandler.cancel();
                Uri parse = Uri.parse(sslError.getUrl());
                AppLogger.error(MainActivity.this, "ssl_error", AppLogger.safeUrl(sslError.getUrl()));
                TelemetryService.sendDiagnosticEvent(MainActivity.this, "webview_ssl_error", "SSL connection blocked");
                StringBuilder sb = new StringBuilder("SSL validation failed for ");
                sb.append(parse.getHost() == null ? "forum host" : parse.getHost());
                ErrorSystem.AppError ssl = ErrorSystem.ssl(sb.toString());
                if ("forum.harleytg.com".equalsIgnoreCase(parse.getHost())) {
                    MainActivity.this.failOverFromPrimary(parse, ssl);
                } else {
                    MainActivity.this.showUnavailable(ssl, parse);
                }
            }

            @Override // android.webkit.WebViewClient
            public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail renderProcessGoneDetail) {
                ErrorSystem.AppError renderer = ErrorSystem.renderer(renderProcessGoneDetail != null && renderProcessGoneDetail.didCrash());
                String str = MainActivity.this.lastRecoverableUrl;
                if (str == null || str.trim().isEmpty()) {
                    str = MainActivity.this.prefs.getString("last_recoverable_url", "");
                }
                if (str == null || str.trim().isEmpty()) {
                    str = ForumUrlRouter.home(MainActivity.this.activeHost == null ? MainActivity.this.chooseInitialHost() : MainActivity.this.activeHost);
                }
                int i = MainActivity.this.prefs.getInt("renderer_recovery_count", 0) + 1;
                MainActivity.this.prefs.edit().putInt("renderer_recovery_count", i).apply();
                AppLogger.error(MainActivity.this, "webview_renderer_gone", renderer.code + " | " + renderer.technical + " | recoveries=" + i + " | " + AppLogger.safeUrl(str));
                MainActivity mainActivity = MainActivity.this;
                StringBuilder sb = new StringBuilder();
                sb.append(renderer.code);
                sb.append(" | ");
                sb.append(renderer.technical);
                TelemetryService.sendDiagnosticEvent(mainActivity, "webview_renderer_gone", sb.toString());
                return MainActivity.this.recoverWebViewRenderer(webView, str, renderer);
            }
        }
    }

    // ---- WelcomeActivity.java ----
    /** First-run welcome gate shown before the optional App Setup Center. */
    public static final class WelcomeActivity extends ThemedActivity {
        private SharedPreferences prefs;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            ThemeManager.apply(this);
            prefs = getSharedPreferences(AppPrefs.FILE, 0);

            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi());

            boolean automatic = getIntent() != null
                    && getIntent().getBooleanExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, false);
            AppLogger.info(this, "app_welcome_open", "v" + SetupCenter.CURRENT_WELCOME_VERSION
                    + (automatic ? " | first-run" : " | manual"));
        }

        @Override
        public void onBackPressed() {
            continueWithoutSetup("back");
        }

        private View buildUi() {
            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setBackgroundColor(bg);

            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER_HORIZONTAL);
            page.setPadding(dp(18), dp(28), dp(18), dp(28));
            scroll.addView(page, new ScrollView.LayoutParams(-1, -1));

            LinearLayout hero = new LinearLayout(this);
            hero.setOrientation(LinearLayout.VERTICAL);
            hero.setGravity(Gravity.CENTER_HORIZONTAL);
            hero.setBackgroundResource(R.drawable.settings_hero_background);
            hero.setPadding(dp(22), dp(24), dp(22), dp(24));
            LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
            heroLp.topMargin = dp(12);
            page.addView(hero, heroLp);

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("Harley's Clan Forum logo");
            hero.addView(logo, new LinearLayout.LayoutParams(dp(92), dp(92)));

            TextView eyebrow = text("HARLEY'S STUDIOS", 10, getColor(R.color.hcf_meta));
            eyebrow.setGravity(Gravity.CENTER);
            eyebrow.setTypeface(null, 1);
            LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-1, -2);
            eyebrowLp.topMargin = dp(10);
            hero.addView(eyebrow, eyebrowLp);

            TextView title = text("", 23, getColor(R.color.hcf_text));
            title.setGravity(Gravity.CENTER);
            title.setTypeface(null, 1);
            title.setText(welcomeTitle());
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
            titleLp.topMargin = dp(4);
            hero.addView(title, titleLp);

            if (isDevelopmentBuild()) {
                TextView badge = text("Development Build / Beta", 10, Color.rgb(10, 14, 18));
                badge.setGravity(Gravity.CENTER);
                badge.setTypeface(null, 1);
                badge.setBackgroundResource(R.drawable.welcome_dev_badge_background);
                badge.setPadding(dp(12), dp(5), dp(12), dp(5));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
                badgeLp.topMargin = dp(10);
                hero.addView(badge, badgeLp);
            }

            TextView welcome = text(
                    "Thanks for using the Harley's Forum app. App Setup can help get Android features ready for this device, but it is completely optional.",
                    13, getColor(R.color.hcf_muted));
            welcome.setGravity(Gravity.CENTER);
            welcome.setLineSpacing(0.0f, 1.14f);
            LinearLayout.LayoutParams welcomeLp = new LinearLayout.LayoutParams(-1, -2);
            welcomeLp.topMargin = dp(16);
            hero.addView(welcome, welcomeLp);

            View featureGrid = buildFeatureGrid();
            LinearLayout.LayoutParams featureGridLp = new LinearLayout.LayoutParams(-1, -2);
            featureGridLp.topMargin = dp(16);
            page.addView(featureGrid, featureGridLp);

            View pawNote = buildPawNoteCard();
            LinearLayout.LayoutParams pawNoteLp = new LinearLayout.LayoutParams(-1, -2);
            pawNoteLp.topMargin = dp(14);
            page.addView(pawNote, pawNoteLp);

            Button startSetup = primaryButton("Start App Setup");
            startSetup.setOnClickListener(v -> startAppSetup());
            LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(52));
            startLp.topMargin = dp(16);
            page.addView(startSetup, startLp);

            Button continueButton = secondaryButton("Continue Without App Setup");
            continueButton.setOnClickListener(v -> continueWithoutSetup("button"));
            LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(-1, dp(50));
            continueLp.topMargin = dp(9);
            page.addView(continueButton, continueLp);

            TextView footer = text(BuildInfo.VERSION_BUILD_LINE, 9, getColor(R.color.hcf_hint));
            footer.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
            footerLp.topMargin = dp(20);
            page.addView(footer, footerLp);

            return scroll;
        }

        /** Four-column overview of the optional Android integrations App Setup can configure. */
        private View buildFeatureGrid() {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackground(cyanOutlineCardBackground());
            container.setPadding(dp(16), dp(16), dp(16), dp(18));

            TextView header = text("What App Setup helps with", 13, getColor(R.color.hcf_cyan));
            header.setTypeface(null, 1);
            header.setGravity(Gravity.CENTER);
            container.addView(header, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            grid.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
            gridLp.topMargin = dp(16);
            container.addView(grid, gridLp);

            grid.addView(buildFeatureItem(R.drawable.fa_bell, "Forum\nNotifications"));
            grid.addView(buildFeatureItem(R.drawable.fa_globe, "Open Forum\nLinks in App"));
            grid.addView(buildFeatureItem(R.drawable.fa_shield, "Secure App\nUpdate Permission"));
            grid.addView(buildFeatureItem(R.drawable.fa_circle_info, "Background\nAlert Health"));

            return container;
        }

        private View buildFeatureItem(int iconRes, String label) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(3), 0, dp(3), 0);

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            icon.setBackground(circleCyanBorderBackground());
            icon.setPadding(dp(12), dp(12), dp(12), dp(12));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            item.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

            TextView tv = text(label, 10, getColor(R.color.hcf_text));
            tv.setGravity(Gravity.CENTER);
            tv.setLineSpacing(0.0f, 1.05f);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
            textLp.topMargin = dp(8);
            item.addView(tv, textLp);

            item.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
            return item;
        }

        /** Reminder that Setup Center stays available after the welcome screen. */
        private View buildPawNoteCard() {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(cyanOutlineCardBackground());
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            ImageView paw = new ImageView(this);
            paw.setImageResource(R.drawable.ic_notification_paw);
            paw.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            paw.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(paw, new LinearLayout.LayoutParams(dp(28), dp(28)));

            TextView note = text(
                    "No worries — you can always open App Setup later from the app drawer → App Setup.",
                    11,
                    getColor(R.color.hcf_muted)
            );
            note.setLineSpacing(0.0f, 1.1f);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            noteLp.leftMargin = dp(12);
            card.addView(note, noteLp);

            return card;
        }

        private GradientDrawable cyanOutlineCardBackground() {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(
                    ThemeManager.isAmoled(this) ? Color.rgb(3, 5, 7) : getColor(R.color.hcf_card_bg)
            );
            background.setStroke(dp(1), getColor(R.color.hcf_cyan));
            background.setCornerRadius(dp(14));
            return background;
        }

        private GradientDrawable circleCyanBorderBackground() {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(getColor(R.color.hcf_quick_bg));
            background.setStroke(dp(1), getColor(R.color.hcf_cyan));
            return background;
        }

        private CharSequence welcomeTitle() {
            String value = "Welcome To\nHarley's Clan Forum";
            SpannableString styled = new SpannableString(value);
            String forum = "Harley's Clan Forum";
            int start = value.indexOf(forum);
            if (start >= 0) {
                styled.setSpan(
                        new ForegroundColorSpan(getColor(R.color.hcf_cyan)),
                        start,
                        start + forum.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            return styled;
        }

        private void startAppSetup() {
            SetupCenter.markWelcomeSeen(this);
            AppLogger.info(this, "app_welcome", "start_setup_v" + SetupCenter.CURRENT_WELCOME_VERSION);
            setResult(RESULT_OK);
            finish();
        }

        private void continueWithoutSetup(String source) {
            SetupCenter.markWelcomeSeen(this);

            // Only create a skipped Setup Center state on a truly fresh install.
            // Existing beta users who already completed setup keep that completion.
            if (!prefs.getBoolean(AppPrefs.SETUP_SEEN, false)) {
                SetupCenter.markSkipped(this);
            }

            AppLogger.info(this, "app_welcome", "continue_without_setup_" + source
                    + "_v" + SetupCenter.CURRENT_WELCOME_VERSION);
            setResult(RESULT_OK);
            finish();
        }

        private boolean isDevelopmentBuild() {
            return "dev".equalsIgnoreCase(BuildInfo.DEFAULT_UPDATE_CHANNEL)
                    || "Dev".equalsIgnoreCase(BuildInfo.CHANNEL);
        }

        private TextView text(String value, int sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextSize(sp);
            view.setTextColor(color);
            return view;
        }

        private Button primaryButton(String label) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextSize(13.0f);
            button.setTextColor(getColor(R.color.hcf_on_accent));
            button.setBackgroundResource(R.drawable.error_primary_button_background);
            button.setGravity(Gravity.CENTER);
            button.setStateListAnimator(null);
            return button;
        }

        private Button secondaryButton(String label) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextSize(12.0f);
            button.setTextColor(getColor(R.color.hcf_cyan_bright));
            button.setBackgroundResource(R.drawable.error_secondary_button_background);
            button.setGravity(Gravity.CENTER);
            button.setStateListAnimator(null);
            return button;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    // ---- SetupActivity.java ----
    /** Native first-run and troubleshooting setup center for HCF's Android integration. */
    public static final class SetupActivity extends ThemedActivity {
        private static final int REQUEST_NOTIFICATIONS = 1901;
        private static final int REQUEST_FORUM_LINKS = 1902;
        private static final int REQUEST_INSTALL_SOURCE = 1903;

        private SharedPreferences prefs;

        private TextView themeValue;
        private TextView performanceValue;

        private TextView notificationStatus;
        private TextView notificationDetail;
        private Button notificationAction;

        private TextView linksStatus;
        private TextView linksDetail;
        private Button linksAction;

        private TextView updateStatus;
        private TextView updateDetail;
        private Button updateAction;

        private TextView healthStatus;
        private TextView healthDetail;
        private Button healthAction;

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            ThemeManager.apply(this);
            prefs = getSharedPreferences(AppPrefs.FILE, 0);
            SetupCenter.markSeen(this);
            try {
                NotificationHelper.refreshChannels(this);
            } catch (Throwable ignored) {
            }

            int bg = ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
            setContentView(buildUi());

            AppLogger.info(this, "app_setup_open", "v" + SetupCenter.CURRENT_SETUP_VERSION
                    + (getIntent() != null && getIntent().getBooleanExtra(SetupCenter.EXTRA_AUTO_LAUNCHED, false)
                    ? " | first-run" : " | manual"));
        }

        @Override
        protected void onResume() {
            super.onResume();
            refreshChoiceLabels();
            refreshStatuses();
        }

        @Override
        public void onBackPressed() {
            if (!prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) {
                SetupCenter.markSkipped(this);
            }
            AppLogger.info(this, "app_setup", "back");
            super.onBackPressed();
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode != REQUEST_NOTIFICATIONS) return;

            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == 0;
            AppLogger.info(this, "notification_permission",
                    granted ? "granted_setup_center" : "denied_setup_center");
            try {
                NotificationSyncScheduler.apply(this);
            } catch (Throwable ignored) {
            }

            Toast.makeText(
                    this,
                    granted ? "Notifications allowed." : "Notifications were not allowed.",
                    Toast.LENGTH_SHORT
            ).show();
            refreshStatuses();
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_FORUM_LINKS || requestCode == REQUEST_INSTALL_SOURCE) {
                refreshStatuses();
            }
        }

        private View buildUi() {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_bg));
            root.addView(buildHeader());

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(12), dp(16), dp(24));
            scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

            content.addView(setupIntroCard());
            content.addView(appearanceAndPerformanceCard());
            content.addView(androidIntegrationCard());
            content.addView(backgroundHealthCard());
            content.addView(securityAndPrivacyCard());

            Button finish = primaryButton("Finish Setup   ›");
            finish.setOnClickListener(v -> {
                SetupCenter.markCompleted(this);
                AppLogger.info(this, "app_setup", "completed_v" + SetupCenter.CURRENT_SETUP_VERSION);
                finish();
            });
            LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(-1, dp(52));
            finishLp.topMargin = dp(8);
            content.addView(finish, finishLp);

            Button continueNow = secondaryButton("Continue for now   ›");
            continueNow.setOnClickListener(v -> {
                if (!prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) {
                    SetupCenter.markSkipped(this);
                }
                AppLogger.info(this, "app_setup",
                        "continue_for_now_v" + SetupCenter.CURRENT_SETUP_VERSION);
                finish();
            });
            LinearLayout.LayoutParams continueLp = new LinearLayout.LayoutParams(-1, dp(48));
            continueLp.topMargin = dp(10);
            content.addView(continueNow, continueLp);

            TextView footer = text(
                    "Setup Center v" + SetupCenter.CURRENT_SETUP_VERSION
                            + " • " + BuildInfo.VERSION_BUILD_LINE,
                    10,
                    getColor(R.color.hcf_hint)
            );
            footer.setGravity(Gravity.CENTER);
            footer.setPadding(0, dp(16), 0, 0);
            content.addView(footer);

            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            return root;
        }

        private View buildHeader() {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setMinimumHeight(dp(56));
            row.setBackgroundColor(
                    ThemeManager.isAmoled(this) ? Color.BLACK : getColor(R.color.hcf_app_bar)
            );

            ImageButton back = new ImageButton(this);
            back.setImageResource(R.drawable.fa_arrow_left);
            back.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            back.setBackgroundResource(R.drawable.nav_button_background);
            back.setScaleType(ImageView.ScaleType.CENTER);
            back.setContentDescription("Back");
            back.setOnClickListener(v -> onBackPressed());
            row.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.htg_app_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            logoLp.leftMargin = dp(8);
            row.addView(logo, logoLp);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            labelsLp.leftMargin = dp(12);
            row.addView(labels, labelsLp);

            TextView title = text("App Setup Center", 17, getColor(R.color.hcf_text));
            title.setTypeface(null, 1);
            labels.addView(title);

            TextView subtitle = text(
                    "Appearance, performance & Android features",
                    11,
                    getColor(R.color.hcf_cyan)
            );
            labels.addView(subtitle);

            return row;
        }

        private View setupIntroCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Set up this device",
                    "Choose how HCF looks and performs, then connect optional Android features."
            ));

            TextView intro = text(
                    "Night (Dark) is default, and Auto performance is recommended. "
                            + "You can change choices now or later in Settings. Permissions stay optional and never block forum access.",
                    12,
                    getColor(R.color.hcf_muted)
            );
            intro.setLineSpacing(0.0f, 1.12f);
            card.addView(intro);
            return card;
        }

        private View appearanceAndPerformanceCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Appearance & Performance",
                    "Same controls used by App Settings"
            ));

            card.addView(settingRowHeader(R.drawable.fa_circle_info, "Theme"));

            themeValue = settingValue("");
            card.addView(themeValue);

            Button themeButton = actionButton("Choose Theme   ›");
            themeButton.setOnClickListener(v -> showThemeDialog());
            card.addView(withTopMargin(themeButton, 8, 44));

            TextView themeHelp = text(
                    "Night is default. AMOLED uses a true-black shell. Phone Auto follows Android; Forum Auto follows forum night mode.",
                    11,
                    getColor(R.color.hcf_muted)
            );
            themeHelp.setPadding(0, dp(6), 0, dp(14));
            card.addView(themeHelp);

            card.addView(settingRowHeader(R.drawable.fa_gear, "Performance Profile"));

            performanceValue = settingValue("");
            card.addView(performanceValue);

            Button performanceButton = actionButton("Choose Performance Profile   ›");
            performanceButton.setOnClickListener(v -> showPerformanceDialog());
            card.addView(withTopMargin(performanceButton, 8, 44));

            TextView performanceHelp = text(
                    "Auto is recommended and adapts to memory, CPU, battery, thermal and network conditions.",
                    11,
                    getColor(R.color.hcf_muted)
            );
            performanceHelp.setPadding(0, dp(6), 0, dp(4));
            card.addView(performanceHelp);

            refreshChoiceLabels();
            return card;
        }

        private View androidIntegrationCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Android Integration",
                    "Recommended setup items for alerts, links and verified app updates"
            ));

            notificationStatus = statusBadge();
            notificationDetail = detailText();
            notificationAction = actionButton("Allow Notifications   ›");
            notificationAction.setOnClickListener(v -> handleNotificationAction());
            card.addView(integrationBlock(
                    "Notifications",
                    R.drawable.fa_bell,
                    notificationStatus,
                    notificationDetail,
                    notificationAction
            ));

            linksStatus = statusBadge();
            linksDetail = detailText();
            linksAction = actionButton("Configure Forum Links   ›");
            linksAction.setOnClickListener(
                    v -> SetupCenter.openForumLinkSettings(this, REQUEST_FORUM_LINKS)
            );
            card.addView(integrationBlock(
                    "Open Forum Links",
                    R.drawable.fa_globe,
                    linksStatus,
                    linksDetail,
                    linksAction
            ));

            updateStatus = statusBadge();
            updateDetail = detailText();
            updateAction = actionButton("Allow Secure Updates   ›");
            updateAction.setOnClickListener(
                    v -> SetupCenter.openInstallSourceSettings(this, REQUEST_INSTALL_SOURCE)
            );
            card.addView(integrationBlock(
                    "Secure App Updates",
                    R.drawable.fa_shield,
                    updateStatus,
                    updateDetail,
                    updateAction
            ));

            return card;
        }

        private View backgroundHealthCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Background Alerts",
                    "Confirm that HCF Alerts can keep working outside the foreground app"
            ));

            healthStatus = statusBadge();
            healthDetail = detailText();
            healthAction = actionButton("Notification Settings   ›");
            healthAction.setOnClickListener(v -> NotificationHelper.openAppNotificationSettings(this));

            card.addView(integrationBlock(
                    "Notification / Background Health",
                    R.drawable.fa_circle_info,
                    healthStatus,
                    healthDetail,
                    healthAction
            ));
            return card;
        }

        private View securityAndPrivacyCard() {
            LinearLayout card = settingsCard();
            card.addView(sectionTitle(
                    "Security & Privacy",
                    "Only the Android access HCF actually needs for these features"
            ));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView paw = text("🐾", 16, getColor(R.color.hcf_cyan));
            row.addView(paw);

            TextView securityNote = text(
                    "HCF does not request location, contacts, microphone, camera, or broad storage access. "
                            + "Update APKs remain restricted to the trusted HCF release source and are verified before installation.",
                    11,
                    getColor(R.color.hcf_muted)
            );
            securityNote.setLineSpacing(0.0f, 1.1f);
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            noteLp.leftMargin = dp(8);
            row.addView(securityNote, noteLp);

            card.addView(row);
            return card;
        }

        private LinearLayout settingsCard() {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_background);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(12);
            card.setLayoutParams(lp);
            return card;
        }

        private View sectionTitle(String title, String subtitle) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, 0, 0, dp(10));

            TextView titleView = text(title, 15, getColor(R.color.hcf_cyan_bright));
            titleView.setTypeface(null, 1);
            block.addView(titleView);

            if (subtitle != null && !subtitle.trim().isEmpty()) {
                TextView subtitleView = text(subtitle, 11, getColor(R.color.hcf_muted));
                subtitleView.setPadding(0, dp(2), 0, 0);
                block.addView(subtitleView);
            }
            return block;
        }

        private View settingRowHeader(int iconRes, String titleText) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(2));

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan)));
            icon.setBackgroundResource(R.drawable.quick_action_background);
            icon.setPadding(dp(6), dp(6), dp(6), dp(6));
            row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

            TextView heading = settingTitle(titleText);
            LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
            headingLp.leftMargin = dp(8);
            row.addView(heading, headingLp);

            return row;
        }

        private View integrationBlock(
                String title,
                int iconRes,
                TextView status,
                TextView detail,
                Button action
        ) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setBackgroundResource(R.drawable.quick_action_background);
            block.setPadding(dp(12), dp(12), dp(12), dp(12));

            LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(-1, -2);
            blockLp.bottomMargin = dp(10);
            block.setLayoutParams(blockLp);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.hcf_cyan_bright)));
            top.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

            TextView heading = text(title, 13, getColor(R.color.hcf_text));
            heading.setTypeface(null, 1);
            LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            headingLp.leftMargin = dp(8);
            top.addView(heading, headingLp);
            top.addView(status, new LinearLayout.LayoutParams(-2, -2));
            block.addView(top);

            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(6);
            block.addView(detail, detailLp);

            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(-1, dp(42));
            actionLp.topMargin = dp(10);
            block.addView(action, actionLp);

            return block;
        }

        private void showThemeDialog() {
            final String[] labels = new String[]{
                    "Night (Dark) — Recommended",
                    "AMOLED Black",
                    "Day (Light)",
                    "Auto • Phone",
                    "Auto • Forum"
            };
            final String[] values = new String[]{
                    ThemeManager.DARK,
                    ThemeManager.AMOLED,
                    ThemeManager.LIGHT,
                    ThemeManager.AUTO_PHONE,
                    ThemeManager.AUTO_FORUM
            };

            String current = ThemeManager.mode(this);
            int checked = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    checked = i;
                    break;
                }
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Theme")
                    .setSingleChoiceItems(labels, checked, null)
                    .setPositiveButton("Apply", null)
                    .setNegativeButton("Cancel", null)
                    .create();

            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        int selected = dialog.getListView().getCheckedItemPosition();
                        if (selected < 0 || selected >= values.length) return;

                        String value = values[selected];
                        prefs.edit().putString("app_theme", value).apply();
                        AppLogger.info(this, "setup_theme", value);
                        refreshChoiceLabels();
                        Toast.makeText(
                                this,
                                "Theme: " + ThemeManager.label(this),
                                Toast.LENGTH_SHORT
                        ).show();
                        dialog.dismiss();
                    }));
            dialog.show();
        }

        private void showPerformanceDialog() {
            final String[] values = new String[]{
                    PerformanceProfile.AUTO,
                    PerformanceProfile.PERFORMANCE,
                    PerformanceProfile.BALANCED,
                    PerformanceProfile.QUALITY
            };
            final String[] labels = new String[]{
                    "Auto — Recommended",
                    "Performance",
                    "Balanced",
                    "High Performance"
            };

            String current = PerformanceProfile.saved(prefs);
            int checked = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    checked = i;
                    break;
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle("Performance Profile")
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        if (which < 0 || which >= values.length) return;

                        PerformanceProfile.save(prefs, values[which]);
                        String label = PerformanceProfile.settingLabel(this, prefs);
                        refreshChoiceLabels();
                        AppLogger.info(
                                this,
                                "setup_performance_profile",
                                values[which] + " -> " + PerformanceProfile.resolve(this, prefs)
                        );
                        Toast.makeText(
                                this,
                                label + " • " + PerformanceProfile.detail(this, prefs),
                                Toast.LENGTH_SHORT
                        ).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void refreshChoiceLabels() {
            if (prefs == null) return;

            if (themeValue != null) {
                themeValue.setText(
                        ThemeManager.label(this)
                                + (ThemeManager.DARK.equals(ThemeManager.mode(this))
                                ? " • Default" : "")
                );
            }

            if (performanceValue != null) {
                performanceValue.setText(
                        PerformanceProfile.settingLabel(this, prefs)
                                + " • " + PerformanceProfile.detail(this, prefs)
                );
            }
        }

        private void refreshStatuses() {
            if (notificationStatus == null) return;

            try {
                NotificationHelper.refreshChannels(this);
            } catch (Throwable ignored) {
            }

            boolean runtimeAllowed = NotificationHelper.hasRuntimePermission(this);
            boolean appNotificationsAllowed = NotificationHelper.areAppNotificationsEnabled(this);
            int alertImportance = NotificationHelper.channelImportance(this);
            boolean asked = prefs.getBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, false);

            if (runtimeAllowed && appNotificationsAllowed && alertImportance != 0) {
                setStatus(notificationStatus, "✓ Allowed", true);
                notificationDetail.setText(
                        "Forum alerts and app/update status can post notifications. "
                                + NotificationHelper.status(this)
                );
                notificationAction.setVisibility(View.GONE);
            } else if (!runtimeAllowed) {
                boolean hardBlocked = Build.VERSION.SDK_INT >= 33
                        && asked
                        && !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS");
                setStatus(
                        notificationStatus,
                        hardBlocked ? "! Blocked by Android" : (asked ? "! Not allowed" : "! Needs setup"),
                        false
                );
                notificationDetail.setText(
                        asked
                                ? "Notification permission is off. Android notification settings can restore it "
                                + "without repeatedly showing the system permission prompt."
                                : "Notifications are used for forum alerts and app/update status. Android 13+ "
                                + "asks for notification permission when you choose Allow Notifications."
                );
                notificationAction.setText(
                        asked ? "Open Notification Settings" : "Allow Notifications"
                );
                notificationAction.setVisibility(View.VISIBLE);
            } else {
                setStatus(notificationStatus, "! Blocked by Android", false);
                notificationDetail.setText(
                        "The Android notification permission is present, but app or HCF Alerts channel "
                                + "settings are currently blocking notifications. "
                                + NotificationHelper.status(this)
                );
                notificationAction.setText("Open Notification Settings");
                notificationAction.setVisibility(View.VISIBLE);
            }

            SetupCenter.ForumLinksState linkState = SetupCenter.forumLinksState(this);
            if (!linkState.inspectable) {
                setStatus(linksStatus, "Optional", true);
            } else {
                setStatus(linksStatus, linkState.ready ? "✓ Ready" : "! Needs setup", linkState.ready);
            }
            linksDetail.setText(
                    linkState.detail
                            + " Supported domains: "
                            + SetupCenter.PRIMARY_FORUM_HOST
                            + " and "
                            + SetupCenter.BACKUP_FORUM_HOST
                            + "."
            );
            linksAction.setVisibility(View.VISIBLE);

            boolean canInstall = AppSecurity.canInstallUpdates(this);
            setStatus(
                    updateStatus,
                    canInstall ? "✓ Allowed" : "! Permission required",
                    canInstall
            );
            updateDetail.setText(
                    canInstall
                            ? "Android allows HCF to hand verified update APKs to the package installer. "
                            + "HCF's package, version/revision and signing-certificate checks remain enforced."
                            : "Android requires per-app approval before HCF can open a verified update APK. "
                            + "This does not weaken HCF's trusted-source, package, version/revision or "
                            + "signing-certificate verification."
            );
            updateAction.setVisibility(canInstall ? View.GONE : View.VISIBLE);

            boolean backgroundSync = prefs.getBoolean(AppPrefs.BACKGROUND_NOTIFICATION_SYNC, true);
            String lastSync = prefs.getString(AppPrefs.NOTIFICATION_LAST_SYNC_STATUS, "");
            if (lastSync == null || lastSync.trim().isEmpty()) {
                lastSync = "Waiting for first sync";
            }

            boolean healthReady = runtimeAllowed
                    && appNotificationsAllowed
                    && alertImportance != 0
                    && backgroundSync;
            setStatus(
                    healthStatus,
                    healthReady ? "✓ Ready" : "! Needs attention",
                    healthReady
            );
            healthDetail.setText(
                    "Android notification permission: " + (runtimeAllowed ? "Allowed" : "Not allowed")
                            + "\nApp notifications: " + (appNotificationsAllowed ? "Enabled" : "Blocked")
                            + "\nHCF Alerts channel: "
                            + NotificationHelper.channelStatus(this, NotificationHelper.CHANNEL_ID)
                            + "\nHCF Silent Alerts: "
                            + NotificationHelper.channelStatus(this, NotificationHelper.SILENT_CHANNEL_ID)
                            + "\nBackground notification sync: "
                            + (backgroundSync ? "Enabled" : "Paused")
                            + "\nLast sync status: " + lastSync
            );
            healthAction.setVisibility(healthReady ? View.GONE : View.VISIBLE);
        }

        private void handleNotificationAction() {
            if (Build.VERSION.SDK_INT < 33
                    || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
                NotificationHelper.openAppNotificationSettings(this);
                return;
            }

            boolean asked = prefs.getBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, false);
            if (asked) {
                NotificationHelper.openAppNotificationSettings(this);
                return;
            }

            prefs.edit()
                    .putBoolean(AppPrefs.NOTIFICATION_PERMISSION_ASKED, true)
                    .putInt(AppPrefs.NOTIFICATION_PERMISSION_PROMPT_VERSION, BuildInfo.VERSION_CODE)
                    .apply();

            requestPermissions(
                    new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQUEST_NOTIFICATIONS
            );
        }

        private void setStatus(TextView view, String value, boolean ready) {
            view.setText(value);
            view.setTextColor(getColor(ready ? R.color.hcf_cyan_bright : R.color.hcf_error));
        }

        private TextView statusBadge() {
            TextView view = text("Checking…", 10, getColor(R.color.hcf_cyan_bright));
            view.setTypeface(null, 1);
            view.setGravity(Gravity.CENTER);
            view.setBackgroundResource(R.drawable.status_chip_background);
            view.setPadding(dp(8), dp(4), dp(8), dp(4));
            return view;
        }

        private TextView detailText() {
            return text("Checking Android status…", 11, getColor(R.color.hcf_muted));
        }

        private TextView settingTitle(String value) {
            TextView view = text(value, 13, getColor(R.color.hcf_text));
            view.setTypeface(null, 1);
            view.setPadding(0, dp(3), 0, 0);
            return view;
        }

        private TextView settingValue(String value) {
            TextView view = text(value, 11, getColor(R.color.hcf_cyan_bright));
            view.setPadding(0, dp(4), 0, 0);
            return view;
        }

        private TextView text(String value, int sp, int color) {
            TextView view = new TextView(this);
            view.setText(value);
            view.setTextSize(sp);
            view.setTextColor(color);
            view.setLineSpacing(0.0f, 1.08f);
            return view;
        }

        private Button primaryButton(String label) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextSize(13.0f);
            button.setTextColor(getColor(R.color.hcf_on_accent));
            button.setBackgroundResource(R.drawable.error_primary_button_background);
            button.setGravity(Gravity.CENTER);
            button.setStateListAnimator(null);
            return button;
        }

        private Button secondaryButton(String label) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextSize(12.0f);
            button.setTextColor(getColor(R.color.hcf_cyan_bright));
            button.setBackgroundResource(R.drawable.error_secondary_button_background);
            button.setGravity(Gravity.CENTER);
            button.setStateListAnimator(null);
            return button;
        }

        private Button actionButton(String label) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(label);
            button.setTextSize(12.0f);
            button.setTextColor(getColor(R.color.hcf_accent_text));
            button.setBackgroundResource(R.drawable.quick_action_background);
            button.setGravity(Gravity.CENTER);
            button.setStateListAnimator(null);
            return button;
        }

        private View withTopMargin(View view, int topDp, int heightDp) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(heightDp));
            lp.topMargin = dp(topDp);
            view.setLayoutParams(lp);
            return view;
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    // ---- CookieManagerActivity.java ----
    /* loaded from: classes.dex */
    public static final class CookieManagerActivity extends ThemedActivity {
        private LinearLayout cookieList;
        private final Set<String> revealed = new HashSet();
        private TextView totalState;

        @Override // com.harleytg.forum.dev.ThemedActivity, android.content.SharedPreferences.OnSharedPreferenceChangeListener
        public /* bridge */ /* synthetic */ void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
            super.onSharedPreferenceChanged(sharedPreferences, str);
        }

        /* JADX INFO: Access modifiers changed from: private */
        static final class CookieEntry {
            final String host;
            final String name;
            final String value;

            CookieEntry(String str, String str2, String str3) {
                this.host = str;
                this.name = str2;
                this.value = str3;
            }

            String key() {
                return this.host + "|" + this.name;
            }
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            ThemeManager.apply(this);
            getWindow().setStatusBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            getWindow().setNavigationBarColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            setContentView(buildUi());
            refresh();
        }

        @Override // com.harleytg.forum.dev.ThemedActivity, android.app.Activity
        protected void onResume() {
            super.onResume();
            refresh();
        }

        private View buildUi() {
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_bg));
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setGravity(16);
            linearLayout2.setPadding(dp(10), dp(8), dp(10), dp(8));
            linearLayout2.setBackgroundColor(ThemeManager.isAmoled(this) ? -16777216 : getColor(R.color.hcf_app_bar));
            ImageButton iconButton = UiButtons.iconButton(this, R.drawable.fa_arrow_left, R.drawable.chrome_button_background, 11, "Back");
            iconButton.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CookieManagerActivity.this.m0lambda$buildUi$0$comharleytgforumdevCookieManagerActivity(view);
                }
            });
            linearLayout2.addView(iconButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView text = text("Cookie Data", 18, getColor(R.color.hcf_text));
            text.setTypeface(null, 1);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.leftMargin = dp(10);
            linearLayout2.addView(text, layoutParams);
            linearLayout.addView(linearLayout2);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout linearLayout3 = new LinearLayout(this);
            linearLayout3.setOrientation(1);
            linearLayout3.setPadding(dp(16), dp(16), dp(16), dp(28));
            scrollView.addView(linearLayout3);
            linearLayout3.addView(text("See each cookie the Android WebView currently sends to the forum. Values are masked until you choose Show. Cookie values stay on this phone and are not sent anywhere by this screen.", 12, getColor(R.color.hcf_muted)));
            TextView text2 = text("Checking cookie data…", 13, getColor(R.color.hcf_meta));
            this.totalState = text2;
            text2.setTypeface(null, 1);
            this.totalState.setPadding(0, dp(16), 0, dp(8));
            linearLayout3.addView(this.totalState);
            LinearLayout linearLayout4 = new LinearLayout(this);
            this.cookieList = linearLayout4;
            linearLayout4.setOrientation(1);
            linearLayout3.addView(this.cookieList);
            linearLayout3.addView(action("Refresh cookie data", new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CookieManagerActivity.this.m1lambda$buildUi$1$comharleytgforumdevCookieManagerActivity(view);
                }
            }));
            linearLayout3.addView(action("Clear all cookies + Web storage", new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda2
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CookieManagerActivity.this.m2lambda$buildUi$2$comharleytgforumdevCookieManagerActivity(view);
                }
            }));
            TextView text3 = text("Android WebView exposes cookie names and values for a URL, but not every stored attribute. Expiration, Path, SameSite, Secure and HttpOnly may not be available here. The Use label is a best-effort description based on the cookie name.", 11, getColor(R.color.hcf_muted));
            text3.setPadding(0, dp(16), 0, 0);
            linearLayout3.addView(text3);
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            return linearLayout;
        }

        /* renamed from: lambda$buildUi$0$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m0lambda$buildUi$0$comharleytgforumdevCookieManagerActivity(View view) {
            finish();
        }

        /* renamed from: lambda$buildUi$1$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m1lambda$buildUi$1$comharleytgforumdevCookieManagerActivity(View view) {
            refresh();
        }

        /* renamed from: lambda$buildUi$2$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m2lambda$buildUi$2$comharleytgforumdevCookieManagerActivity(View view) {
            clearAll();
        }

        private void refresh() {
            LinearLayout linearLayout = this.cookieList;
            if (linearLayout == null) {
                return;
            }
            linearLayout.removeAllViews();
            List<CookieEntry> readCookies = readCookies("forum.harleytg.com");
            List<CookieEntry> readCookies2 = readCookies("harleysclan.freeflarum.com");
            int size = readCookies.size() + readCookies2.size();
            this.totalState.setText("Visible forum cookies: " + size + " • Primary " + readCookies.size() + " • Backup " + readCookies2.size());
            addHostSection("Primary", "forum.harleytg.com", readCookies);
            addHostSection("Backup", "harleysclan.freeflarum.com", readCookies2);
            if (size == 0) {
                TextView text = text("No visible forum cookies are currently stored.", 12, getColor(R.color.hcf_muted));
                text.setPadding(0, dp(8), 0, dp(8));
                this.cookieList.addView(text);
            }
        }

        private void addHostSection(String str, String str2, List<CookieEntry> list) {
            TextView text = text(str + " • " + str2 + " • " + list.size(), 12, getColor(R.color.hcf_accent_text));
            text.setTypeface(null, 1);
            text.setPadding(0, dp(14), 0, dp(4));
            this.cookieList.addView(text);
            if (list.isEmpty()) {
                TextView text2 = text("No visible cookies for this host.", 11, getColor(R.color.hcf_muted));
                text2.setPadding(0, 0, 0, dp(6));
                this.cookieList.addView(text2);
            } else {
                Iterator<CookieEntry> it = list.iterator();
                while (it.hasNext()) {
                    this.cookieList.addView(cookieCard(it.next()));
                }
            }
        }

        private View cookieCard(final CookieEntry cookieEntry) {
            StringBuilder sb;
            String mask;
            LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(1);
            linearLayout.setPadding(dp(14), dp(12), dp(14), dp(12));
            linearLayout.setBackgroundResource(R.drawable.card_background);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.topMargin = dp(8);
            linearLayout.setLayoutParams(layoutParams);
            TextView text = text(cookieEntry.name, 14, getColor(R.color.hcf_text));
            text.setTypeface(null, 1);
            text.setTextIsSelectable(true);
            linearLayout.addView(text);
            TextView text2 = text("Use: " + describeUse(cookieEntry.name), 11, getColor(R.color.hcf_meta));
            text2.setPadding(0, dp(3), 0, 0);
            linearLayout.addView(text2);
            int length = cookieEntry.value.getBytes(StandardCharsets.UTF_8).length;
            StringBuilder sb2 = new StringBuilder("Host: ");
            sb2.append(cookieEntry.host);
            sb2.append(" • Value size: ");
            sb2.append(length);
            sb2.append(" byte");
            sb2.append(length == 1 ? "" : "s");
            TextView text3 = text(sb2.toString(), 10, getColor(R.color.hcf_muted));
            text3.setPadding(0, dp(3), 0, 0);
            linearLayout.addView(text3);
            boolean contains = this.revealed.contains(cookieEntry.key());
            if (contains) {
                sb = new StringBuilder("Value: ");
                mask = cookieEntry.value;
            } else {
                sb = new StringBuilder("Value: ");
                mask = mask(cookieEntry.value);
            }
            sb.append(mask);
            TextView text4 = text(sb.toString(), 11, getColor(R.color.hcf_text));
            text4.setTextIsSelectable(contains);
            text4.setPadding(0, dp(8), 0, 0);
            linearLayout.addView(text4);
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(0);
            linearLayout2.setPadding(0, dp(10), 0, 0);
            Button button = button(contains ? "Hide value" : "Show value");
            button.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CookieManagerActivity.this.m4lambda$cookieCard$3$comharleytgforumdevCookieManagerActivity(cookieEntry, view);
                }
            });
            linearLayout2.addView(button, new LinearLayout.LayoutParams(0, dp(46), 1.0f));
            Button button2 = button("Clear cookie");
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda4
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    CookieManagerActivity.this.m5lambda$cookieCard$4$comharleytgforumdevCookieManagerActivity(cookieEntry, view);
                }
            });
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, dp(46), 1.0f);
            layoutParams2.leftMargin = dp(8);
            linearLayout2.addView(button2, layoutParams2);
            linearLayout.addView(linearLayout2);
            return linearLayout;
        }

        /* renamed from: lambda$cookieCard$3$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m4lambda$cookieCard$3$comharleytgforumdevCookieManagerActivity(CookieEntry cookieEntry, View view) {
            if (this.revealed.contains(cookieEntry.key())) {
                this.revealed.remove(cookieEntry.key());
            } else {
                this.revealed.add(cookieEntry.key());
            }
            refresh();
        }

        /* renamed from: lambda$cookieCard$4$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m5lambda$cookieCard$4$comharleytgforumdevCookieManagerActivity(CookieEntry cookieEntry, View view) {
            clearCookie(cookieEntry);
        }

        private List<CookieEntry> readCookies(String str) {
            String cookie = CookieManager.getInstance().getCookie("https://" + str + "/");
            ArrayList arrayList = new ArrayList();
            if (TextUtils.isEmpty(cookie)) {
                return arrayList;
            }
            for (String str2 : cookie.split(";")) {
                String trim = str2.trim();
                if (!trim.isEmpty()) {
                    int indexOf = trim.indexOf(61);
                    String trim2 = indexOf >= 0 ? trim.substring(0, indexOf).trim() : trim;
                    String substring = indexOf >= 0 ? trim.substring(indexOf + 1) : "";
                    if (!trim2.isEmpty()) {
                        arrayList.add(new CookieEntry(str, trim2, substring));
                    }
                }
            }
            return arrayList;
        }

        private String describeUse(String str) {
            String lowerCase = str == null ? "" : str.toLowerCase(Locale.US);
            if (lowerCase.contains("csrf") || lowerCase.contains("xsrf")) {
                return "Request/security protection";
            }
            if (lowerCase.contains("session") || lowerCase.contains("auth") || lowerCase.contains("token")) {
                return "Sign-in or session state";
            }
            if (lowerCase.contains("remember") || lowerCase.contains("login")) {
                return "Remembered sign-in";
            }
            if (lowerCase.contains("consent") || lowerCase.contains("cookie")) {
                return "Cookie/consent preference";
            }
            if (lowerCase.contains("theme") || lowerCase.contains("dark") || lowerCase.contains("light")) {
                return "Appearance preference";
            }
            if (lowerCase.contains("lang") || lowerCase.contains("locale")) {
                return "Language/locale preference";
            }
            if (lowerCase.contains("notification") || lowerCase.contains("alert")) {
                return "Notification/site state";
            }
            return lowerCase.contains("flarum") ? "Flarum forum state" : "Site data (exact purpose is not exposed by WebView)";
        }

        private String mask(String str) {
            if (str == null || str.isEmpty()) {
                return "(empty)";
            }
            int min = Math.min(24, Math.max(8, str.length()));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < min; i++) {
                sb.append((char) 8226);
            }
            if (str.length() > min) {
                sb.append((char) 8230);
            }
            return sb.toString();
        }

        private void clearCookie(CookieEntry cookieEntry) {
            String str = "https://" + cookieEntry.host + "/";
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setCookie(str, cookieEntry.name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=Lax");
            cookieManager.flush();
            this.revealed.remove(cookieEntry.key());
            AppLogger.info(this, "cookie_clear_one", cookieEntry.host + " | " + cookieEntry.name);
            StringBuilder sb = new StringBuilder("Cleared ");
            sb.append(cookieEntry.name);
            Toast.makeText(this, sb.toString(), 0).show();
            refresh();
        }

        private void clearAll() {
            final CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(new ValueCallback() { // from class: com.harleytg.forum.dev.CookieManagerActivity$$ExternalSyntheticLambda5
                @Override // android.webkit.ValueCallback
                public final void onReceiveValue(Object obj) {
                    CookieManagerActivity.this.m3lambda$clearAll$5$comharleytgforumdevCookieManagerActivity(cookieManager, (Boolean) obj);
                }
            });
        }

        /* renamed from: lambda$clearAll$5$com-harleytg-forum-dev-CookieManagerActivity, reason: not valid java name */
        /* synthetic */ void m3lambda$clearAll$5$comharleytgforumdevCookieManagerActivity(CookieManager cookieManager, Boolean bool) {
            cookieManager.flush();
            WebStorage.getInstance().deleteAllData();
            ForumIdentity.clear(this);
            this.revealed.clear();
            AppLogger.info(this, "cookie_all_clear", "cookie_manager");
            Toast.makeText(this, "All forum cookies and Web storage cleared.", 1).show();
            refresh();
        }

        private Button action(String str, View.OnClickListener onClickListener) {
            Button button = button(str);
            button.setGravity(8388627);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setOnClickListener(onClickListener);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, dp(compact() ? 44 : 52));
            layoutParams.topMargin = dp(8);
            button.setLayoutParams(layoutParams);
            return button;
        }

        private Button button(String str) {
            Button button = new Button(this);
            UiButtons.normalizeText(button);
            button.setText(str != null ? str.replaceFirst("^[^A-Za-z0-9]+", "").trim() : "");
            button.setTextColor(getColor(R.color.hcf_accent_text));
            button.setBackgroundResource(R.drawable.quick_action_background);
            button.setAllCaps(false);
            FaIcons.applyStart(button, str);
            return button;
        }

        private TextView text(String str, int i, int i2) {
            TextView textView = new TextView(this);
            textView.setText(str);
            textView.setTextSize(i);
            textView.setTextColor(i2);
            textView.setLineSpacing(0.0f, 1.12f);
            return textView;
        }

        private boolean compact() {
            return getResources().getConfiguration().orientation == 2;
        }

        private int dp(int i) {
            return Math.round(i * getResources().getDisplayMetrics().density);
        }
    }
}

// ---- SetupCenter.java ----
/** Shared state and Android integration helpers for the versioned App Setup Center. */
final class SetupCenter {
    static final int CURRENT_SETUP_VERSION = 2;
    static final int CURRENT_WELCOME_VERSION = 2;
    static final String EXTRA_AUTO_LAUNCHED = "hcf_setup_auto_launched";
    static final String PRIMARY_FORUM_HOST = "forum.harleytg.com";
    static final String BACKUP_FORUM_HOST = "harleysclan.freeflarum.com";

    private static final String DRAWER_TAG = "hcf_app_setup_drawer";

    static final class ForumLinksState {
        final boolean inspectable;
        final boolean ready;
        final String detail;

        ForumLinksState(boolean inspectable, boolean ready, String detail) {
            this.inspectable = inspectable;
            this.ready = ready;
            this.detail = detail;
        }
    }

    static boolean shouldShowWelcome(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int storedVersion = prefs.getInt(AppPrefs.WELCOME_VERSION, 0);
        boolean seen = prefs.getBoolean(AppPrefs.WELCOME_SEEN, false);
        return storedVersion < CURRENT_WELCOME_VERSION || !seen;
    }

    static void markWelcomeSeen(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.WELCOME_VERSION, CURRENT_WELCOME_VERSION)
                .putBoolean(AppPrefs.WELCOME_SEEN, true)
                .apply();
    }

    static boolean shouldAutoLaunch(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int storedVersion = prefs.getInt(AppPrefs.SETUP_VERSION, 0);
        boolean seen = prefs.getBoolean(AppPrefs.SETUP_SEEN, false);
        return storedVersion < CURRENT_SETUP_VERSION || !seen;
    }

    static void markSeen(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.FILE, 0);
        int oldVersion = prefs.getInt(AppPrefs.SETUP_VERSION, 0);
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true);
        if (oldVersion < CURRENT_SETUP_VERSION) {
            editor.putBoolean(AppPrefs.SETUP_COMPLETED, false);
        }
        editor.apply();
    }

    static void markCompleted(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true)
                .putBoolean(AppPrefs.SETUP_COMPLETED, true)
                .apply();
    }

    static void markSkipped(Context context) {
        if (context == null) return;
        context.getSharedPreferences(AppPrefs.FILE, 0).edit()
                .putInt(AppPrefs.SETUP_VERSION, CURRENT_SETUP_VERSION)
                .putBoolean(AppPrefs.SETUP_SEEN, true)
                .putBoolean(AppPrefs.SETUP_COMPLETED, false)
                .apply();
    }

    static void maybeLaunchForMainActivity(HcfForum.MainActivity activity, android.os.Bundle savedInstanceState) {
        if (activity == null) return;
        installDrawerEntry(activity);
        if (savedInstanceState != null) return;

        if (shouldShowWelcome(activity)) {
            Intent welcome = new Intent(activity, HcfForum.WelcomeActivity.class);
            welcome.putExtra(EXTRA_AUTO_LAUNCHED, true);
            activity.startActivity(welcome);
            AppLogger.info(activity, "app_welcome", "auto_launch_v" + CURRENT_WELCOME_VERSION);
            return;
        }

        if (!shouldAutoLaunch(activity)) return;
        markSeen(activity);
        Intent intent = new Intent(activity, HcfForum.SetupActivity.class);
        intent.putExtra(EXTRA_AUTO_LAUNCHED, true);
        activity.startActivity(intent);
        AppLogger.info(activity, "app_setup", "auto_launch_v" + CURRENT_SETUP_VERSION);
    }

    static void installDrawerEntry(final HcfForum.MainActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.FILE, 0);
        if (prefs.getBoolean(AppPrefs.SETUP_COMPLETED, false)) return;

        try {
            View drawerRoot = activity.findViewById(R.id.drawerPanel);
            if (containsSetupEntry(drawerRoot)) return;

            View anchor = activity.findViewById(R.id.drawerSettings);
            if (anchor == null) anchor = activity.findViewById(R.id.drawerSupport);
            if (anchor == null) anchor = activity.findViewById(R.id.drawerLogs);

            DrawerPlacement placement = placementBefore(anchor);
            if (placement == null && drawerRoot instanceof ViewGroup) {
                placement = placementAfterAppHeader((ViewGroup) drawerRoot);
            }
            if (placement == null || containsSetupEntry(placement.parent)) return;

            Button setup = new Button(activity);
            setup.setTag(DRAWER_TAG);
            setup.setText("App Setup");
            setup.setContentDescription("Open App Setup Center");
            setup.setAllCaps(false);
            setup.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            setup.setTextColor(activity.getColor(R.color.hcf_text));
            setup.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    activity.getResources().getDimension(R.dimen.drawer_item_text));
            setup.setIncludeFontPadding(false);
            setup.setMinWidth(0);
            setup.setMinHeight(0);
            setup.setMinimumWidth(0);
            setup.setMinimumHeight(0);
            setup.setPadding(dp(activity, 14), 0, dp(activity, 12), 0);
            setup.setBackgroundResource(R.drawable.drawer_item_background);
            setup.setStateListAnimator(null);
            FaIcons.applyStart(setup, R.drawable.fa_shield);
            setup.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    try {
                        activity.startActivity(new Intent(activity, HcfForum.SetupActivity.class));
                        AppLogger.info(activity, "app_setup", "drawer_open");
                    } catch (Throwable error) {
                        AppLogger.error(activity, "app_setup_drawer", error.getClass().getSimpleName());
                    }
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    activity.getResources().getDimensionPixelSize(R.dimen.drawer_item_height));
            lp.bottomMargin = dp(activity, 5);
            int index = Math.max(0, Math.min(placement.index, placement.parent.getChildCount()));
            placement.parent.addView(setup, index, lp);
            AppLogger.info(activity, "app_setup_drawer", "installed | index=" + index);
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_setup_drawer", error.getClass().getSimpleName());
        }
    }

    private static final class DrawerPlacement {
        final LinearLayout parent;
        final int index;

        DrawerPlacement(LinearLayout parent, int index) {
            this.parent = parent;
            this.index = index;
        }
    }

    private static DrawerPlacement placementBefore(View anchor) {
        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) return null;
        LinearLayout parent = (LinearLayout) anchor.getParent();
        return new DrawerPlacement(parent, Math.max(0, parent.indexOfChild(anchor)));
    }

    private static DrawerPlacement placementAfterAppHeader(ViewGroup root) {
        if (root == null) return null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && !(child instanceof Button)) {
                CharSequence label = ((TextView) child).getText();
                if (label != null && "App".equalsIgnoreCase(label.toString().trim())
                        && root instanceof LinearLayout) {
                    return new DrawerPlacement((LinearLayout) root, i + 1);
                }
            }
            if (child instanceof ViewGroup) {
                DrawerPlacement nested = placementAfterAppHeader((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean containsSetupEntry(View view) {
        if (view == null) return false;
        if (DRAWER_TAG.equals(view.getTag())) return true;
        if (view instanceof TextView) {
            CharSequence label = ((TextView) view).getText();
            if (label != null && "App Setup".equalsIgnoreCase(label.toString().trim())) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsSetupEntry(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    static ForumLinksState forumLinksState(Context context) {
        if (context == null) return new ForumLinksState(false, false, "Unable to inspect Android link settings.");
        if (Build.VERSION.SDK_INT < 31) {
            return new ForumLinksState(false, false,
                    "Android manages supported links on this version. Open Forum Link Settings to verify both HCF domains.");
        }
        try {
            DomainVerificationManager manager = context.getSystemService(DomainVerificationManager.class);
            if (manager == null) {
                return new ForumLinksState(true, false, "Android's supported-link service is unavailable.");
            }
            DomainVerificationUserState state = manager.getDomainVerificationUserState(context.getPackageName());
            if (state == null) {
                return new ForumLinksState(true, false, "No supported-link state is available yet.");
            }
            Map<String, Integer> hosts = state.getHostToStateMap();
            boolean primaryReady = hostReady(hosts, PRIMARY_FORUM_HOST);
            boolean backupReady = hostReady(hosts, BACKUP_FORUM_HOST);
            boolean handlingAllowed = state.isLinkHandlingAllowed();
            boolean ready = handlingAllowed && primaryReady && backupReady;
            String detail = ready
                    ? "Both supported forum domains are enabled to open directly in HCF."
                    : "Android controls this setting. Enable HCF for forum.harleytg.com and harleysclan.freeflarum.com.";
            return new ForumLinksState(true, ready, detail);
        } catch (Throwable error) {
            return new ForumLinksState(true, false,
                    "Android link status could not be read: " + error.getClass().getSimpleName());
        }
    }

    private static boolean hostReady(Map<String, Integer> states, String host) {
        if (states == null || host == null) return false;
        Integer value = states.get(host);
        if (value == null) return false;
        int state = value.intValue();
        return state == DomainVerificationUserState.DOMAIN_STATE_SELECTED
                || state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED;
    }

    static void openForumLinkSettings(Activity activity, int requestCode) {
        if (activity == null) return;
        Intent primary = new Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS",
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivityForResult(primary, requestCode);
            AppLogger.info(activity, "forum_link_settings", "open-by-default");
            return;
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(fallback, requestCode);
                AppLogger.warn(activity, "forum_link_settings", "fallback-app-details");
                return;
            } catch (Throwable second) {
                AppLogger.error(activity, "forum_link_settings", second.getClass().getSimpleName());
            }
        }
    }

    static void openInstallSourceSettings(Activity activity, int requestCode) {
        if (activity == null) return;
        try {
            Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, requestCode);
            AppLogger.info(activity, "install_permission", "setup_center_open");
        } catch (Throwable first) {
            try {
                Intent fallback = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(fallback, requestCode);
            } catch (Throwable second) {
                AppLogger.error(activity, "install_permission", second.getClass().getSimpleName());
            }
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private SetupCenter() {}
}


// ---- HcfIntentChooser.java ----
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

// ---- Consolidated from HcfForumEngine.java ----
final class HcfForumEngine {
    private HcfForumEngine() {}
}

// ---- ForumConfig.java ----
/** Runtime forum-domain registry with safe built-in fallbacks. */
final class ForumConfig {
    static final String HTTPS = "https";
    static final long PRIMARY_RETRY_COOLDOWN_MS = 21600000L;

    static final String BUILTIN_PRIMARY_HOST = "forum.harleytg.com";
    static final String BUILTIN_BACKUP_HOST = "harleysclan.freeflarum.com";

    static volatile String PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
    static volatile String BACKUP_HOST = BUILTIN_BACKUP_HOST;
    static volatile Set<String> FORUM_HOSTS = builtInHosts();

    static synchronized void applyRemote(String primary, Collection<String> backups) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (primary != null && !primary.isEmpty()) hosts.add(primary);
        if (backups != null) {
            for (String host : backups) {
                if (host != null && !host.isEmpty()) hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            resetBuiltIn();
            return;
        }

        PRIMARY_HOST = hosts.iterator().next();
        List<String> ordered = new ArrayList<>(hosts);
        BACKUP_HOST = ordered.size() > 1 ? ordered.get(1) : BUILTIN_BACKUP_HOST;
        if (ordered.size() == 1 && !BACKUP_HOST.equals(PRIMARY_HOST)) hosts.add(BACKUP_HOST);
        FORUM_HOSTS = Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
    }

    static synchronized void resetBuiltIn() {
        PRIMARY_HOST = BUILTIN_PRIMARY_HOST;
        BACKUP_HOST = BUILTIN_BACKUP_HOST;
        FORUM_HOSTS = builtInHosts();
    }

    private static Set<String> builtInHosts() {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add(BUILTIN_PRIMARY_HOST);
        hosts.add(BUILTIN_BACKUP_HOST);
        return Collections.unmodifiableSet(hosts);
    }

    private ForumConfig() {}
}


// ---- FirebaseConfigLoader.java ----
/* loaded from: classes.dex */
final class FirebaseConfigLoader {

    interface Callback {
        void onResult(Config config, String str);
    }

    static final class Config {
        final String apiKey;
        final String appId;
        final String authDomain;
        final String measurementId;
        final String messagingSenderId;
        final String projectId;
        final String source;
        final String storageBucket;

        Config(String str, String str2, String str3, String str4, String str5, String str6, String str7, String str8) {
            this.apiKey = str;
            this.authDomain = str2;
            this.projectId = str3;
            this.storageBucket = str4;
            this.messagingSenderId = str5;
            this.appId = str6;
            this.measurementId = str7;
            this.source = str8;
        }

        boolean isValid() {
            return FirebaseConfigLoader.notBlank(this.apiKey) && FirebaseConfigLoader.notBlank(this.projectId) && FirebaseConfigLoader.notBlank(this.messagingSenderId) && FirebaseConfigLoader.notBlank(this.appId);
        }

        String safeSummary() {
            if (!isValid()) {
                return "Configuration unavailable";
            }
            return "Loaded • project " + this.projectId + " • " + this.source;
        }
    }

    static Config load(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        Config parse = parse(sharedPreferences.getString("firebase_config_cache", ""), sharedPreferences.getString("firebase_config_source", "Cached HTTPS config"));
        if (parse != null && parse.isValid()) {
            return parse;
        }
        try {
            Config parse2 = parse(readAll(context.getAssets().open("firebase-config.js")), "Bundled firebase-config.js");
            if (parse2 == null) {
                return null;
            }
            if (parse2.isValid()) {
                return parse2;
            }
            return null;
        } catch (Throwable unused) {
            return null;
        }
    }

    static void refresh(Context context, final Callback callback) {
        final Context applicationContext = context.getApplicationContext();
        final SharedPreferences sharedPreferences = applicationContext.getSharedPreferences("hcf_app", 0);
        final String trim = sharedPreferences.getString("firebase_config_url", "").trim();
        if (trim.isEmpty()) {
            Config load = load(applicationContext);
            callback.onResult(load, load == null ? "Bundled Firebase config could not be read." : "Using bundled Firebase config.");
        } else if (!trim.startsWith("https://")) {
            callback.onResult(load(applicationContext), "Firebase config URL must use HTTPS.");
        } else {
            AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.FirebaseConfigLoader$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    FirebaseConfigLoader.lambda$refresh$1(trim, sharedPreferences, applicationContext, callback);
                }
            });
        }
    }

    static /* synthetic */ void lambda$refresh$1(String urlText, SharedPreferences sharedPreferences, Context context, final Callback callback) {
        Config result;
        String message;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 FirebaseConfig");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            String raw = readAll(connection.getInputStream());
            Config parsed = parse(raw, "HTTPS config");
            if (parsed == null || !parsed.isValid()) throw new IllegalStateException("Invalid Firebase config");
            sharedPreferences.edit().putString("firebase_config_cache", raw).putString("firebase_config_source", "HTTPS config").apply();
            result = parsed;
            message = "Firebase config refreshed from HTTPS.";
        } catch (Throwable t) {
            result = load(context);
            message = "Remote refresh failed; kept " + (result == null ? "no config" : result.source) + ".";
            AppLogger.error(context, "firebase_config_refresh", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        } finally {
            if (connection != null) connection.disconnect();
        }
        final Config callbackConfig = result;
        final String callbackMessage = message;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { callback.onResult(callbackConfig, callbackMessage); }
        });
    }

    static void clearRemoteCache(Context context) {
        context.getSharedPreferences("hcf_app", 0).edit().remove("firebase_config_cache").remove("firebase_config_source").apply();
    }

    private static Config parse(String str, String str2) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        Config config = new Config(value(str, "apiKey"), value(str, "authDomain"), value(str, "projectId"), value(str, "storageBucket"), value(str, "messagingSenderId"), value(str, "appId"), value(str, "measurementId"), str2);
        if (config.isValid()) {
            return config;
        }
        return null;
    }

    private static String value(String str, String str2) {
        Matcher matcher = Pattern.compile("(?:\\\"?" + Pattern.quote(str2) + "\\\"?)\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']").matcher(str);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean notBlank(String str) {
        return (str == null || str.trim().isEmpty()) ? false : true;
    }

    private static String readAll(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine == null) {
                bufferedReader.close();
                return sb.toString();
            }
            sb.append(readLine);
            sb.append('\n');
        }
    }

    private FirebaseConfigLoader() {
    }
}


// ---- ForumIdentity.java ----
/* loaded from: classes.dex */
final class ForumIdentity {

    static final class Snapshot {
        final boolean admin;
        final String avatarUrl;
        final int commentCount;
        final String connections;
        final int discussionCount;
        final String displayName;
        final String email;
        final boolean emailConfirmed;
        final String groups;
        final String host;
        final String joinTime;
        final String lastSeenAt;
        final boolean loggedIn;
        final int newNotifications;
        final String slug;
        final long syncedAt;
        final int unreadNotifications;
        final String userId;
        final String username;

        Snapshot(boolean z, String str, String str2, String str3, String str4, String str5, boolean z2, String str6, String str7, String str8, boolean z3, String str9, String str10, int i, int i2, int i3, int i4, String str11, long j) {
            this.loggedIn = z;
            this.userId = ForumIdentity.safe(str);
            this.username = ForumIdentity.safe(str2);
            this.slug = ForumIdentity.safe(str3);
            this.displayName = ForumIdentity.safe(str4);
            this.email = ForumIdentity.safe(str5);
            this.emailConfirmed = z2;
            this.avatarUrl = ForumIdentity.safe(str6);
            this.groups = ForumIdentity.safe(str7);
            this.connections = ForumIdentity.safe(str8);
            this.admin = z3;
            this.joinTime = ForumIdentity.safe(str9);
            this.lastSeenAt = ForumIdentity.safe(str10);
            this.unreadNotifications = Math.max(0, i);
            this.newNotifications = Math.max(0, i2);
            this.discussionCount = Math.max(0, i3);
            this.commentCount = Math.max(0, i4);
            this.host = ForumIdentity.safe(str11);
            this.syncedAt = j;
        }

        String identityLabel() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            String str = !this.displayName.isEmpty() ? this.displayName : this.username;
            if (!this.username.isEmpty()) {
                if (str.equalsIgnoreCase("@" + this.username)) {
                    str = this.username;
                }
            }
            return str.isEmpty() ? "Member" : str;
        }

        String usernameDisplay() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            if (this.username.isEmpty()) {
                return "Signed-in forum member";
            }
            return "@" + this.username;
        }

        String identityMetaLabel() {
            return !this.loggedIn ? "Guest forum identity" : this.admin ? "Administrator" : !this.groups.isEmpty() ? this.groups : "Forum member";
        }

        String connectionLabel() {
            if (!this.loggedIn) {
                return "Guest_Protocol";
            }
            if (!this.connections.isEmpty()) {
                return this.connections;
            }
            if (this.email.isEmpty()) {
                return "Forum session";
            }
            return this.emailConfirmed ? "Email (verified)" : "Email";
        }
    }

    static Snapshot fromBridgeJson(String str, String str2) throws Exception {
        JSONObject jSONObject = new JSONObject(str == null ? "{}" : str);
        if (!jSONObject.optBoolean("loggedIn", false)) {
            return guest(str2);
        }
        return new Snapshot(true, jSONObject.optString("id", ""), jSONObject.optString("username", ""), jSONObject.optString("slug", ""), jSONObject.optString("displayName", ""), jSONObject.optString("email", ""), jSONObject.optBoolean("emailConfirmed", false), jSONObject.optString("avatarUrl", ""), joinUnique(jSONObject.optJSONArray("groups")), joinUnique(jSONObject.optJSONArray("connections")), jSONObject.optBoolean("isAdmin", false), jSONObject.optString("joinTime", ""), jSONObject.optString("lastSeenAt", ""), jSONObject.optInt("unreadNotificationCount", 0), jSONObject.optInt("newNotificationCount", 0), jSONObject.optInt("discussionCount", 0), jSONObject.optInt("commentCount", 0), str2, System.currentTimeMillis());
    }

    static Snapshot guest(String str) {
        return new Snapshot(false, "", "", "", "Guest_Protocol", "", false, "", "", "Guest_Protocol", false, "", "", 0, 0, 0, 0, str, System.currentTimeMillis());
    }

    static void save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null) {
            return;
        }
        SharedPreferences.Editor edit = context.getSharedPreferences("hcf_app", 0).edit();
        edit.putBoolean("identity_logged_in", snapshot.loggedIn).putString("identity_user_id", snapshot.userId).putString("identity_username", snapshot.username).putString("identity_slug", snapshot.slug).putString("identity_display_name", snapshot.displayName).putString("identity_email", snapshot.email).putBoolean("identity_email_confirmed", snapshot.emailConfirmed).putString("identity_avatar_url", snapshot.avatarUrl).putString("identity_groups", snapshot.groups).putString("identity_connections", snapshot.connections).putBoolean("identity_admin", snapshot.admin).putString("identity_join_time", snapshot.joinTime).putString("identity_last_seen_at", snapshot.lastSeenAt).putInt("identity_unread_notifications", snapshot.unreadNotifications).putInt("identity_new_notifications", snapshot.newNotifications).putInt("identity_discussion_count", snapshot.discussionCount).putInt("identity_comment_count", snapshot.commentCount).putString("identity_host", snapshot.host).putLong("identity_synced_at", snapshot.syncedAt);
        if (!snapshot.loggedIn || snapshot.userId.isEmpty()) {
            edit.remove("session_user_id").remove("last_notification_count");
        } else {
            edit.putString("session_user_id", snapshot.userId);
        }
        edit.apply();
    }

    static Snapshot load(Context context) {
        if (context == null) {
            return guest("forum.harleytg.com");
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        return new Snapshot(sharedPreferences.getBoolean("identity_logged_in", false), sharedPreferences.getString("identity_user_id", ""), sharedPreferences.getString("identity_username", ""), sharedPreferences.getString("identity_slug", ""), sharedPreferences.getString("identity_display_name", "Guest_Protocol"), sharedPreferences.getString("identity_email", ""), sharedPreferences.getBoolean("identity_email_confirmed", false), sharedPreferences.getString("identity_avatar_url", ""), sharedPreferences.getString("identity_groups", ""), sharedPreferences.getString("identity_connections", ""), sharedPreferences.getBoolean("identity_admin", false), sharedPreferences.getString("identity_join_time", ""), sharedPreferences.getString("identity_last_seen_at", ""), sharedPreferences.getInt("identity_unread_notifications", 0), sharedPreferences.getInt("identity_new_notifications", 0), sharedPreferences.getInt("identity_discussion_count", 0), sharedPreferences.getInt("identity_comment_count", 0), sharedPreferences.getString("identity_host", "forum.harleytg.com"), sharedPreferences.getLong("identity_synced_at", 0L));
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().remove("identity_logged_in").remove("identity_user_id").remove("identity_username").remove("identity_slug").remove("identity_display_name").remove("identity_email").remove("identity_email_confirmed").remove("identity_avatar_url").remove("identity_groups").remove("identity_connections").remove("identity_admin").remove("identity_join_time").remove("identity_last_seen_at").remove("identity_unread_notifications").remove("identity_new_notifications").remove("identity_discussion_count").remove("identity_comment_count").remove("identity_host").remove("identity_synced_at").remove("session_user_id").remove("last_notification_count").apply();
        ForumSecurity.clear(context);
    }

    private static String joinUnique(JSONArray jSONArray) {
        ArrayList<String> arrayList = new ArrayList();
        if (jSONArray != null) {
            for (int i = 0; i < jSONArray.length(); i++) {
                String trim = jSONArray.optString(i, "").trim();
                if (!trim.isEmpty() && !arrayList.contains(trim)) {
                    arrayList.add(trim);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String str : arrayList) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(str);
        }
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String safe(String str) {
        return str == null ? "" : str.trim();
    }

    private ForumIdentity() {
    }
}


// ---- ForumNotificationClient.java ----
/* loaded from: classes.dex */
final class ForumNotificationClient {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int MAX_BODY_CHARS = 700000;
    private static final int READ_TIMEOUT_MS = 4500;

    static final class HttpStatusException extends IOException {
        final int statusCode;
        final long retryAfterMs;

        HttpStatusException(int statusCode, long retryAfterMs) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.retryAfterMs = Math.max(0L, retryAfterMs);
        }
    }

    static final class Alert {
        final String body;
        final String id;
        final String title;
        final String url;
        final String conversationId;
        final String discussionId;
        final String senderAvatarUrl;
        final boolean replyCapable;

        Alert(String id, String title, String body, String url, String conversationId,
              String discussionId, String senderAvatarUrl, boolean replyCapable) {
            this.id = ForumNotificationClient.clean(id, 120);
            this.title = ForumNotificationClient.clean(title, 120);
            this.body = ForumNotificationClient.clean(body, 500);
            this.url = url == null ? "" : url;
            this.conversationId = ForumNotificationClient.clean(conversationId, 120);
            this.discussionId = ForumNotificationClient.clean(discussionId, 120);
            this.senderAvatarUrl = senderAvatarUrl == null ? "" : senderAvatarUrl;
            this.replyCapable = replyCapable;
        }
    }

    static int fetchNewCount(Context context, String str, String str2) throws Exception {
        JSONObject jSONObject = new JSONObject(get(context, trustedBase(str), "api/users/" + str2 + "?fields%5Busers%5D=unreadNotificationCount%2CnewNotificationCount", "Count")).getJSONObject("data").getJSONObject("attributes");
        return Math.max(Math.max(0, jSONObject.optInt("unreadNotificationCount", 0)), Math.max(0, jSONObject.optInt("newNotificationCount", 0)));
    }

    static List<Alert> fetchLatest(Context context, String str, int i) throws Exception {
        String str2;
        String trustedBase = trustedBase(str);
        int max = Math.max(1, Math.min(20, i));
        try {
            str2 = get(context, trustedBase, "api/notifications?include=fromUser,subject&page%5Blimit%5D=" + max, "Details");
        } catch (Throwable error) {
            if (error instanceof HttpStatusException) {
                throw (HttpStatusException) error;
            }
            str2 = get(context, trustedBase, "api/notifications?page%5Blimit%5D=" + max, "DetailsFallback");
        }
        return parseAlerts(new JSONObject(str2), trustedBase, max);
    }

    private static List<Alert> parseAlerts(JSONObject jSONObject, String str, int i) {
        Alert parseAlert;
        JSONArray optJSONArray = jSONObject.optJSONArray("data");
        if (optJSONArray == null || optJSONArray.length() == 0) {
            return Collections.emptyList();
        }
        HashMap hashMap = new HashMap();
        JSONArray optJSONArray2 = jSONObject.optJSONArray("included");
        if (optJSONArray2 != null) {
            for (int i2 = 0; i2 < optJSONArray2.length(); i2++) {
                JSONObject optJSONObject = optJSONArray2.optJSONObject(i2);
                if (optJSONObject != null) {
                    hashMap.put(key(optJSONObject.optString("type"), optJSONObject.optString("id")), optJSONObject);
                }
            }
        }
        ArrayList arrayList = new ArrayList();
        for (int i3 = 0; i3 < optJSONArray.length() && arrayList.size() < i; i3++) {
            JSONObject optJSONObject2 = optJSONArray.optJSONObject(i3);
            if (optJSONObject2 != null && (parseAlert = parseAlert(optJSONObject2, hashMap, str)) != null && !parseAlert.id.isEmpty()) {
                arrayList.add(parseAlert);
            }
        }
        return arrayList;
    }

    private static Alert parseAlert(JSONObject jSONObject, Map<String, JSONObject> map, String str) {
        String str2;
        String str3;
        String readableType;
        String optString = jSONObject.optString("id", "");
        JSONObject optJSONObject = jSONObject.optJSONObject("attributes");
        if (optJSONObject == null) {
            optJSONObject = new JSONObject();
        }
        String optString2 = optJSONObject.optString("type", "notification");
        JSONObject optJSONObject2 = jSONObject.optJSONObject("relationships");
        JSONObject relationData = relationData(optJSONObject2, "fromUser");
        JSONObject relationData2 = relationData(optJSONObject2, "subject");
        JSONObject jSONObject2 = relationData == null ? null : map.get(key(relationData.optString("type"), relationData.optString("id")));
        JSONObject jSONObject3 = relationData2 == null ? null : map.get(key(relationData2.optString("type"), relationData2.optString("id")));
        JSONObject contentObject = contentObject(optJSONObject.opt("content"));
        String userLabel = userLabel(jSONObject2);
        if (userLabel.isEmpty()) {
            userLabel = firstDeep(contentObject, "displayName", "display_name", "username", "user_name", "senderName", "sender_name");
        }
        if (userLabel.isEmpty()) {
            userLabel = "Someone";
        }
        String firstDeep = firstDeep(contentObject, "conversationId", "conversation_id");
        String firstDeep2 = firstDeep(contentObject, "discussionId", "discussion_id");
        String firstDeep3 = firstDeep(contentObject, "discussionTitle", "discussion_title", "title");
        String str4 = userLabel;
        String firstDeep4 = firstDeep(contentObject, "postNumber", "post_number", "number");
        String firstDeep5 = firstDeep(contentObject, "userSlug", "user_slug", "slug", "username");
        String prefer = prefer(attribute(jSONObject2, "slug"), attribute(jSONObject2, "username"));
        String optString3 = relationData2 != null ? relationData2.optString("type", "") : "";
        if ("discussions".equals(optString3)) {
            firstDeep2 = relationData2.optString("id", firstDeep2);
            firstDeep3 = prefer(firstDeep3, attribute(jSONObject3, "title"));
            str2 = firstDeep4;
        } else if ("posts".equals(optString3)) {
            JSONObject relationData3 = relationData(jSONObject3 == null ? null : jSONObject3.optJSONObject("relationships"), "discussion");
            if (relationData3 != null) {
                firstDeep2 = relationData3.optString("id", firstDeep2);
            }
            str2 = prefer(firstDeep4, attribute(jSONObject3, "number"));
        } else {
            if ("users".equals(optString3)) {
                firstDeep5 = prefer(attribute(jSONObject3, "slug"), attribute(jSONObject3, "username"));
            }
            str2 = firstDeep4;
        }
        String lowerCase = optString2.toLowerCase(Locale.US);
        boolean z = !firstDeep.isEmpty() || lowerCase.contains("privatediscussion") || lowerCase.contains("private_message") || lowerCase.contains("privatemessage") || lowerCase.contains("conversationmessage") || lowerCase.contains("conversation_message") || lowerCase.contains("messenger");
        if (z) {
            str3 = "New message from " + str4;
        } else if (lowerCase.contains("postmentioned") || lowerCase.contains("usermentioned")) {
            str3 = str4 + " mentioned you";
        } else if (lowerCase.contains("liked") || lowerCase.contains("postliked")) {
            str3 = str4 + " liked your post";
        } else if (lowerCase.contains("newpost") || lowerCase.contains("reply")) {
            str3 = "New reply from " + str4;
        } else if (lowerCase.contains("follow")) {
            str3 = str4 + " followed you";
        } else {
            str3 = "New forum alert from " + str4;
        }
        String firstDeep6 = firstDeep(contentObject, "message", "body", "text", "excerpt", "preview", "content");
        if (z || firstDeep3.isEmpty()) {
            firstDeep3 = firstDeep6;
        }
        String cleanNotificationBody = cleanNotificationBody(firstDeep3, z);
        if (cleanNotificationBody.isEmpty()) {
            if (z) {
                readableType = "You have a new private message.";
            } else {
                readableType = readableType(optString2);
            }
            cleanNotificationBody = readableType;
        }
        String str5 = str + "notifications";
        if (z && !firstDeep.isEmpty() && firstDeep.matches("[0-9]+")) {
            str5 = str + "conversations/" + firstDeep;
        } else if (lowerCase.contains("follow") && !prefer.isEmpty()) {
            str5 = str + "u/" + Uri.encode(prefer);
        } else if (!firstDeep5.isEmpty() && "users".equals(optString3)) {
            str5 = str + "u/" + Uri.encode(firstDeep5);
        } else if (!firstDeep2.isEmpty() && firstDeep2.matches("[0-9]+")) {
            str5 = str + "d/" + firstDeep2;
            if (!str2.isEmpty() && str2.matches("[0-9]+")) {
                str5 = str5 + "/" + str2;
            }
        }
        String senderAvatarUrl = absoluteHttpsUrl(str, attribute(jSONObject2, "avatarUrl"));
        boolean privateMessageType = lowerCase.contains("privatediscussion")
                || lowerCase.contains("private_message")
                || lowerCase.contains("privatemessage")
                || lowerCase.contains("conversationmessage")
                || lowerCase.contains("conversation_message")
                || lowerCase.contains("messenger");
        boolean replyCapable = firstDeep.matches("[0-9]+")
                && (privateMessageType || "messages".equals(optString3));
        return new Alert(optString, str3, cleanNotificationBody, str5, firstDeep, firstDeep2, senderAvatarUrl, replyCapable);
    }

    private static String absoluteHttpsUrl(String base, String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            Uri uri = Uri.parse(raw.trim());
            if (uri.isAbsolute()) return "https".equalsIgnoreCase(uri.getScheme()) ? uri.toString() : "";
            Uri resolved = Uri.parse(base).buildUpon().encodedPath(raw.startsWith("/") ? raw : "/" + raw).build();
            return "https".equalsIgnoreCase(resolved.getScheme()) ? resolved.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static JSONObject relationData(JSONObject jSONObject, String str) {
        JSONObject optJSONObject;
        if (jSONObject == null || (optJSONObject = jSONObject.optJSONObject(str)) == null) {
            return null;
        }
        Object opt = optJSONObject.opt("data");
        if (opt instanceof JSONObject) {
            return (JSONObject) opt;
        }
        return null;
    }

    private static JSONObject contentObject(Object obj) {
        if (obj instanceof JSONObject) {
            return (JSONObject) obj;
        }
        if (obj instanceof String) {
            String trim = ((String) obj).trim();
            if (trim.startsWith("{") && trim.endsWith("}")) {
                try {
                    return new JSONObject(trim);
                } catch (Throwable unused) {
                }
            }
        }
        return new JSONObject();
    }

    private static String userLabel(JSONObject jSONObject) {
        JSONObject optJSONObject;
        if (jSONObject != null && (optJSONObject = jSONObject.optJSONObject("attributes")) != null) {
            String clean = clean(optJSONObject.optString("displayName", ""), 80);
            return !clean.isEmpty() ? clean : clean(optJSONObject.optString("username", ""), 80);
        }
        return "";
    }

    private static String attribute(JSONObject jSONObject, String str) {
        JSONObject optJSONObject;
        if (jSONObject == null || (optJSONObject = jSONObject.optJSONObject("attributes")) == null) {
            return "";
        }
        return clean(optJSONObject.optString(str, ""), 300);
    }

    private static String firstDeep(JSONObject jSONObject, String... strArr) {
        return firstDeepObject(jSONObject, 0, strArr);
    }

    private static String firstDeepObject(JSONObject jSONObject, int i, String... strArr) {
        if (jSONObject != null && i <= 5) {
            for (String str : strArr) {
                if (jSONObject.has(str)) {
                    String readableValue = readableValue(jSONObject.opt(str), i + 1, strArr);
                    if (!readableValue.isEmpty()) {
                        return readableValue;
                    }
                }
            }
            JSONArray names = jSONObject.names();
            if (names != null) {
                for (int i2 = 0; i2 < names.length(); i2++) {
                    Object opt = jSONObject.opt(names.optString(i2, ""));
                    if ((opt instanceof JSONObject) || (opt instanceof JSONArray)) {
                        String readableValue2 = readableValue(opt, i + 1, strArr);
                        if (!readableValue2.isEmpty()) {
                            return readableValue2;
                        }
                    }
                }
            }
        }
        return "";
    }

    private static String readableValue(Object obj, int i, String... strArr) {
        if (obj == null || obj == JSONObject.NULL || i > 6) {
            return "";
        }
        if (obj instanceof JSONObject) {
            return firstDeepObject((JSONObject) obj, i, strArr);
        }
        int i2 = 0;
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            while (i2 < jSONArray.length()) {
                String readableValue = readableValue(jSONArray.opt(i2), i + 1, strArr);
                if (!readableValue.isEmpty()) {
                    return readableValue;
                }
                i2++;
            }
            return "";
        }
        String clean = clean(String.valueOf(obj), 2000);
        if (clean.isEmpty()) {
            return "";
        }
        String trim = clean.trim();
        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            try {
                if (trim.startsWith("{")) {
                    return firstDeepObject(new JSONObject(trim), i + 1, strArr);
                }
                JSONArray jSONArray2 = new JSONArray(trim);
                while (i2 < jSONArray2.length()) {
                    String readableValue2 = readableValue(jSONArray2.opt(i2), i + 1, strArr);
                    if (!readableValue2.isEmpty()) {
                        return readableValue2;
                    }
                    i2++;
                }
                return "";
            } catch (Throwable ignored) {
                return clean(trim, 500);
            }
        }
        return clean(trim, 500);
    }

    private static String cleanNotificationBody(String str, boolean z) {
        String readableValue;
        String stripMarkup = stripMarkup(str);
        if (stripMarkup.isEmpty()) {
            return "";
        }
        String trim = stripMarkup.trim();
        if ((trim.startsWith("{") && trim.endsWith("}")) || (trim.startsWith("[") && trim.endsWith("]"))) {
            try {
                if (trim.startsWith("{")) {
                    readableValue = firstDeepObject(new JSONObject(trim), 0, "message", "body", "text", "excerpt", "preview", "content");
                } else {
                    readableValue = readableValue(new JSONArray(trim), 0, "message", "body", "text", "excerpt", "preview", "content");
                }
                stripMarkup = stripMarkup(readableValue);
            } catch (Throwable unused) {
                return "";
            }
        }
        return (stripMarkup.isEmpty() && z) ? "You have a new private message." : clean(stripMarkup, 500);
    }

    private static String prefer(String str, String str2) {
        return (str == null || str.trim().isEmpty()) ? clean(str2, 500) : str;
    }

    private static String readableType(String str) {
        if (str == null) {
            str = "Notification";
        }
        String trim = str.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return trim.isEmpty() ? "You have a new forum notification." : trim;
    }

    private static String stripMarkup(String str) {
        if (str == null) {
            return "";
        }
        return clean(str.replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " "), 500);
    }

    private static String trustedBase(String str) {
        if (!ForumUrlRouter.isForumHost(str)) {
            str = "forum.harleytg.com";
        }
        return ForumUrlRouter.home(str);
    }

    private static String get(Context context, String str, String str2, String str3) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str + str2).openConnection();
        try {
            httpURLConnection.setConnectTimeout(4500);
            httpURLConnection.setReadTimeout(4500);
            httpURLConnection.setUseCaches(true);
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            httpURLConnection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            httpURLConnection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 Notification" + str3);
            String cookie = CookieManager.getInstance().getCookie(str);
            if (cookie != null && !cookie.trim().isEmpty()) {
                httpURLConnection.setRequestProperty("Cookie", cookie);
            }
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new HttpStatusException(responseCode, retryAfterMillis(httpURLConnection));
            }
            return read(httpURLConnection.getInputStream());
        } finally {
            httpURLConnection.disconnect();
        }
    }

    static int markNotificationRead(Context context, String host, String notificationId) throws Exception {
        if (context == null || notificationId == null || !notificationId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid notification id");
        }
        String base = trustedBase(host);
        JSONObject attributes = new JSONObject().put("isRead", true);
        JSONObject data = new JSONObject()
                .put("type", "notifications")
                .put("id", notificationId)
                .put("attributes", attributes);
        JSONObject body = new JSONObject().put("data", data);
        return mutate(context, base, "api/notifications/" + notificationId, body, true);
    }

    static int sendConversationReply(Context context, String host, String conversationId, String replyText) throws Exception {
        if (context == null || conversationId == null || !conversationId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid conversation id");
        }
        String reply = replyText == null ? "" : replyText.trim();
        if (reply.isEmpty() || reply.length() > 10000) throw new IllegalArgumentException("Invalid reply");
        String base = trustedBase(host);
        JSONObject attributes = new JSONObject()
                .put("messageContents", reply)
                .put("conversationId", conversationId);
        JSONObject data = new JSONObject()
                .put("type", "messages")
                .put("attributes", attributes);
        return mutate(context, base, "api/neoncube-private-messages/messages", new JSONObject().put("data", data), false);
    }

    private static int mutate(Context context, String base, String path, JSONObject body, boolean patch) throws Exception {
        String cookie = CookieManager.getInstance().getCookie(base);
        if (cookie == null || cookie.trim().isEmpty()) throw new HttpStatusException(401, 0L);
        String csrf = currentCsrfToken(context, base, cookie);
        if (csrf.isEmpty()) throw new HttpStatusException(401, 0L);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            if (patch) connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Content-Type", "application/vnd.api+json");
            connection.setRequestProperty("X-CSRF-Token", csrf);
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 NotificationAction");
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream output = connection.getOutputStream();
            output.write(bytes);
            output.flush();
            output.close();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status, retryAfterMillis(connection));
            return status;
        } finally {
            connection.disconnect();
        }
    }

    private static String currentCsrfToken(Context context, String base, String cookie) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("Cookie", cookie);
            connection.setRequestProperty("User-Agent", "HarleysClanForumApp/1.0 NotificationSession");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status, retryAfterMillis(connection));
            String html = readLimited(connection.getInputStream(), 1500000);
            int marker = html.indexOf("flarum-json-payload");
            if (marker < 0) marker = html.indexOf("id='flarum-json-payload'");
            if (marker < 0) return "";
            int start = html.indexOf('>', marker);
            int end = start < 0 ? -1 : html.indexOf("</script>", start + 1);
            if (start < 0 || end <= start) return "";
            JSONObject payload = new JSONObject(html.substring(start + 1, end).trim());
            JSONObject session = payload.optJSONObject("session");
            if (session == null || session.optInt("userId", 0) <= 0) return "";
            return clean(session.optString("csrfToken", ""), 1000);
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream inputStream, int maxChars) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(Math.min(maxChars, 131072));
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1 && out.length() < maxChars) {
            out.append(buffer, 0, Math.min(count, maxChars - out.length()));
        }
        reader.close();
        return out.toString();
    }

    private static long retryAfterMillis(HttpURLConnection connection) {
        if (connection == null) return 0L;
        String value = connection.getHeaderField("Retry-After");
        if (value == null || value.trim().isEmpty()) return 0L;
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(300000L, Math.max(0L, seconds * 1000L));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String read(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] cArr = new char[8192];
        while (true) {
            int read = bufferedReader.read(cArr);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(cArr, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        bufferedReader.close();
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String clean(String str, int i) {
        String trim = str == null ? "" : str.trim();
        if (trim.length() <= i) {
            return trim;
        }
        return trim.substring(0, i) + "…";
    }

    private static String key(String str, String str2) {
        StringBuilder sb = new StringBuilder();
        if (str == null) {
            str = "";
        }
        sb.append(str);
        sb.append(":");
        if (str2 == null) {
            str2 = "";
        }
        sb.append(str2);
        return sb.toString();
    }

    private ForumNotificationClient() {
    }
}


// ---- ForumNotificationSync.java ----
/* loaded from: classes.dex */
final class ForumNotificationSync {
    private static final long STATUS_WRITE_INTERVAL_MS = 15000;

    static final class Outcome {
        final int count;
        final int delivered;
        final long latencyMs;

        Outcome(int i, int i2, long j) {
            this.count = i;
            this.delivered = i2;
            this.latencyMs = j;
        }
    }

    static Outcome perform(Context context, String str, String str2, String str3) throws Exception {
        int i = 0;
        long currentTimeMillis = System.currentTimeMillis();
        try {
            int fetchNewCount = ForumNotificationClient.fetchNewCount(context, str, str2);
            int recordForumNotificationCount = NotificationHelper.recordForumNotificationCount(context, fetchNewCount, str, str3);
            if (recordForumNotificationCount > 0) {
                try {
                    i = NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(recordForumNotificationCount + 4, 8)), recordForumNotificationCount, str, str3);
                } catch (Throwable th) {
                    if (th instanceof ForumNotificationClient.HttpStatusException) {
                        throw (ForumNotificationClient.HttpStatusException) th;
                    }
                    NotificationHelper.postGenericDelta(context, recordForumNotificationCount, str);
                    AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName() + " | generic-fallback");
                }
                long max = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
                RuntimeDiagnostics.syncSucceeded(max);
                recordStatus(context, "Live • synced", max, false);
                return new Outcome(fetchNewCount, i, max);
            }
            i = 0;
            long max2 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncSucceeded(max2);
            recordStatus(context, "Live • synced", max2, false);
            return new Outcome(fetchNewCount, i, max2);
        } catch (Exception e) {
            long max3 = Math.max(0L, System.currentTimeMillis() - currentTimeMillis);
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Waiting for connection", max3, true);
            throw e;
        }
    }

    static void deliverObservedCountAsync(Context context, final String str, final int i, final String str2) {
        if (context == null || i <= 0) {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        AppExecutors.network().execute(new Runnable() { // from class: com.harleytg.forum.dev.ForumNotificationSync$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                ForumNotificationSync.lambda$deliverObservedCountAsync$0(applicationContext, str, i, str2);
            }
        });
    }

    static /* synthetic */ void lambda$deliverObservedCountAsync$0(Context context, String str, int i, String str2) {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            NotificationHelper.deliverDetailedAlerts(context, ForumNotificationClient.fetchLatest(context, str, Math.max(i + 4, 8)), i, str, str2);
            long currentTimeMillis2 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncSucceeded(currentTimeMillis2);
            recordStatus(context, "Live • synced", currentTimeMillis2, false);
        } catch (Throwable th) {
            NotificationHelper.postGenericDelta(context, i, str);
            long currentTimeMillis3 = System.currentTimeMillis() - currentTimeMillis;
            RuntimeDiagnostics.syncFailed();
            recordStatus(context, "Live • detail unavailable", currentTimeMillis3, true);
            AppLogger.warn(context, "notification_detail", th.getClass().getSimpleName());
        }
    }

    private static void recordStatus(Context context, String str, long j, boolean z) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        long currentTimeMillis = System.currentTimeMillis();
        String string = sharedPreferences.getString("notification_last_sync_status", "");
        long j2 = sharedPreferences.getLong("notification_last_sync_at", 0L);
        boolean z2 = !str.equals(string);
        if (z || z2 || currentTimeMillis - j2 >= STATUS_WRITE_INTERVAL_MS) {
            sharedPreferences.edit().putLong("notification_last_sync_at", currentTimeMillis).putString("notification_last_sync_status", str).putLong("notification_last_sync_latency_ms", Math.max(0L, j)).apply();
        }
    }

    private ForumNotificationSync() {
    }
}


// ---- ForumSecurity.java ----
/* loaded from: classes.dex */
final class ForumSecurity {

    static final class Snapshot {
        final int activeSessionCount;
        final boolean emailControls;
        final String host;
        final boolean passwordControls;
        final String path;
        final String providers;
        final boolean seen;
        final int sessionCount;
        final long syncedAt;
        final boolean twoFactorControls;

        Snapshot(boolean z, int i, int i2, String str, boolean z2, boolean z3, boolean z4, String str2, String str3, long j) {
            this.seen = z;
            this.sessionCount = Math.max(0, i);
            this.activeSessionCount = Math.max(0, i2);
            this.providers = ForumSecurity.safe(str);
            this.passwordControls = z2;
            this.emailControls = z3;
            this.twoFactorControls = z4;
            this.host = ForumSecurity.safe(str2);
            this.path = ForumSecurity.safe(str3);
            this.syncedAt = j;
        }

        String sessionLabel() {
            if (!this.seen) {
                return "Syncing automatically";
            }
            int i = this.sessionCount;
            return i <= 0 ? "No session list detected" : Integer.toString(i);
        }

        String currentSessionLabel() {
            return !this.seen ? "Waiting for automatic sync" : this.activeSessionCount > 0 ? "Current app/browser session detected" : "Not identified";
        }
    }

    static Snapshot fromBridgeJson(String str, String str2) throws Exception {
        if (str == null) {
            str = "{}";
        }
        JSONObject jSONObject = new JSONObject(str);
        return new Snapshot(jSONObject.optBoolean("seen", true), jSONObject.optInt("sessionCount", 0), jSONObject.optInt("activeSessionCount", 0), joinUnique(jSONObject.optJSONArray("providers")), jSONObject.optBoolean("passwordControls", false), jSONObject.optBoolean("emailControls", false), jSONObject.optBoolean("twoFactorControls", false), str2, jSONObject.optString("path", ""), System.currentTimeMillis());
    }

    static void save(Context context, Snapshot snapshot) {
        if (context == null || snapshot == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().putBoolean("identity_security_seen", snapshot.seen).putInt("identity_security_session_count", snapshot.sessionCount).putInt("identity_security_active_session_count", snapshot.activeSessionCount).putString("identity_security_providers", snapshot.providers).putBoolean("identity_security_password_controls", snapshot.passwordControls).putBoolean("identity_security_email_controls", snapshot.emailControls).putBoolean("identity_security_two_factor_controls", snapshot.twoFactorControls).putString("identity_security_host", snapshot.host).putString("identity_security_path", snapshot.path).putLong("identity_security_synced_at", snapshot.syncedAt).apply();
    }

    static Snapshot load(Context context) {
        if (context == null) {
            return empty("forum.harleytg.com");
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("hcf_app", 0);
        return new Snapshot(sharedPreferences.getBoolean("identity_security_seen", false), sharedPreferences.getInt("identity_security_session_count", 0), sharedPreferences.getInt("identity_security_active_session_count", 0), sharedPreferences.getString("identity_security_providers", ""), sharedPreferences.getBoolean("identity_security_password_controls", false), sharedPreferences.getBoolean("identity_security_email_controls", false), sharedPreferences.getBoolean("identity_security_two_factor_controls", false), sharedPreferences.getString("identity_security_host", "forum.harleytg.com"), sharedPreferences.getString("identity_security_path", ""), sharedPreferences.getLong("identity_security_synced_at", 0L));
    }

    static Snapshot empty(String str) {
        return new Snapshot(false, 0, 0, "", false, false, false, str, "", 0L);
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences("hcf_app", 0).edit().remove("identity_security_seen").remove("identity_security_session_count").remove("identity_security_active_session_count").remove("identity_security_providers").remove("identity_security_password_controls").remove("identity_security_email_controls").remove("identity_security_two_factor_controls").remove("identity_security_host").remove("identity_security_path").remove("identity_security_synced_at").apply();
    }

    static String mergeLabels(String str, String str2) {
        ArrayList<String> arrayList = new ArrayList();
        addCsv(arrayList, str);
        addCsv(arrayList, str2);
        StringBuilder sb = new StringBuilder();
        for (String str3 : arrayList) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(str3);
        }
        return sb.toString();
    }

    private static void addCsv(List<String> list, String str) {
        if (str == null) {
            return;
        }
        String[] split = str.split(",");
        int length = split.length;
        for (int i = 0; i < length; i++) {
            String str2 = split[i];
            String trim = str2 == null ? "" : str2.trim();
            if (!trim.isEmpty() && !list.contains(trim)) {
                list.add(trim);
            }
        }
    }

    private static String joinUnique(JSONArray jSONArray) {
        ArrayList<String> arrayList = new ArrayList();
        if (jSONArray != null) {
            for (int i = 0; i < jSONArray.length(); i++) {
                String trim = jSONArray.optString(i, "").trim();
                if (!trim.isEmpty() && !arrayList.contains(trim)) {
                    arrayList.add(trim);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String str : arrayList) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(str);
        }
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String safe(String str) {
        return str == null ? "" : str.trim();
    }

    private ForumSecurity() {
    }
}


// ---- ForumUrlRouter.java ----
/* loaded from: classes.dex */
final class ForumUrlRouter {
    private ForumUrlRouter() {
    }

    static boolean isForumHost(String str) {
        return str != null && ForumConfig.FORUM_HOSTS.contains(str.toLowerCase(Locale.US));
    }

    static boolean isForumUrl(Uri uri) {
        if (uri == null || !isForumHost(uri.getHost())) {
            return false;
        }
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    static String equivalentOnHost(Uri uri, String str) {
        Uri.Builder encodedPath = new Uri.Builder().scheme("https").authority(str).encodedPath(emptyToSlash(uri.getEncodedPath()));
        if (uri.getEncodedQuery() != null) {
            encodedPath.encodedQuery(uri.getEncodedQuery());
        }
        if (uri.getEncodedFragment() != null) {
            encodedPath.encodedFragment(uri.getEncodedFragment());
        }
        return encodedPath.build().toString();
    }

    static String home(String str) {
        return "https://" + str + "/";
    }

    private static String emptyToSlash(String str) {
        return (str == null || str.isEmpty()) ? "/" : str;
    }
}


// ---- LiveForumUpdater.java ----
/* loaded from: classes.dex */
final class LiveForumUpdater {
    private static final long AUTH_CHECK_MS = 15000L;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final long FIRST_POLL_MS = 100L;
    private static final long IDLE_ROUTE_POLL_MS = 1500L;
    private static final int MAX_BODY_CHARS = 180000;
    private static final int MAX_WS_PAYLOAD_BYTES = 524288;
    private static final int PUSH_READ_TIMEOUT_MS = 60000;
    private static final long RECONNECT_MAX_MS = 8000L;
    private static final long RECONNECT_MIN_MS = 300L;
    private static final int READ_TIMEOUT_MS = 4500;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Context app;
    private String baselineFingerprint;
    private String baselineKey;
    private final Map<String, CacheEntry> cache = new HashMap();
    private int failures;
    private boolean forceQueuedRefresh;
    private boolean inFlight;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private boolean queuedRefresh;
    private int reconnectAttempts;
    private volatile boolean running;
    private volatile PushConnection socket;
    private volatile boolean socketConnected;
    private boolean socketConnecting;
    private volatile int socketGeneration;
    private String socketIdentity = "";
    private String lastState = "";
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.poll();
        }
    };
    private final Runnable reconnect = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.connectSocket();
        }
    };
    private final Runnable authCheck = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.checkRealtimeIdentity();
        }
    };
    private final Runnable pushedRefresh = new Runnable() {
        @Override
        public void run() {
            LiveForumUpdater.this.refreshCurrentRoute(false, true);
        }
    };

    interface Listener {
        String currentUrl();

        void onChangeCandidate(String str, String str2);

        void onStateChanged(String str);
    }

    LiveForumUpdater(Context context, Listener listener) {
        Context applicationContext = context.getApplicationContext();
        this.app = applicationContext;
        this.listener = listener;
        this.prefs = applicationContext.getSharedPreferences("hcf_app", 0);
    }

    void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.failures = 0;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        state("SYNCING");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
        scheduleAuthCheck();
    }

    void stop() {
        this.running = false;
        this.socketGeneration++;
        this.main.removeCallbacks(this.tick);
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.authCheck);
        this.main.removeCallbacks(this.pushedRefresh);
        this.socketConnecting = false;
        this.socketConnected = false;
        closeSocket();
        state("PAUSED");
    }

    void destroy() {
        stop();
    }

    void reset() {
        this.baselineKey = null;
        this.baselineFingerprint = null;
        if (this.running) {
            poke();
        }
    }

    void poke() {
        if (!this.running) {
            return;
        }
        if (!realtimeIdentity().equals(this.socketIdentity)) {
            restartSocket("auth-or-host-changed");
        } else if (!this.socketConnected) {
            scheduleReconnect(0L);
        }
        if (this.socketConnected) {
            schedulePushedRefresh(0L);
        } else {
            scheduleFallback(0L);
        }
    }

    void noteUserInteraction() {
        RuntimeState.noteUserInteraction();
        if (!this.running || this.socketConnected) {
            return;
        }
        scheduleFallback(Math.min(FIRST_POLL_MS, PerformanceProfile.livePollInterval(this.app, this.prefs)));
    }

    void acknowledge(String str, String str2) {
        if (str == null || str2 == null) return;
        this.baselineKey = str;
        this.baselineFingerprint = str2;
        saveBaseline(str, str2);
    }

    private void scheduleAuthCheck() {
        if (!this.running) {
            return;
        }
        this.main.removeCallbacks(this.authCheck);
        this.main.postDelayed(this.authCheck, AUTH_CHECK_MS);
    }

    private void checkRealtimeIdentity() {
        if (!this.running) {
            return;
        }
        String realtimeIdentity = realtimeIdentity();
        if (!realtimeIdentity.equals(this.socketIdentity)) {
            restartSocket("auth-or-cookie-changed");
        } else if (!this.socketConnected && !this.socketConnecting && RuntimeState.networkAvailable(this.app)) {
            scheduleReconnect(0L);
        }
        scheduleAuthCheck();
    }

    private String realtimeIdentity() {
        String str = "";
        try {
            Uri parse = Uri.parse(this.listener.currentUrl());
            if (ForumUrlRouter.isForumUrl(parse) && parse.getHost() != null) {
                str = parse.getHost().toLowerCase(Locale.US);
            }
        } catch (Throwable unused) {
        }
        String string = this.prefs.getString("session_user_id", "");
        if (string == null) {
            string = "";
        }
        String cookie = cookieForHost(str);
        return str + "|" + string.trim() + "|" + Integer.toHexString(cookie.hashCode());
    }

    private String cookieForHost(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "";
        }
        try {
            String cookie = CookieManager.getInstance().getCookie("https://" + str + "/");
            return cookie == null ? "" : cookie;
        } catch (Throwable unused) {
            return "";
        }
    }

    private void restartSocket(String str) {
        this.socketGeneration++;
        this.socketConnected = false;
        this.socketConnecting = false;
        this.reconnectAttempts = 0;
        this.socketIdentity = realtimeIdentity();
        closeSocket();
        AppLogger.info(this.app, "live_update_socket", "restart | " + str);
        state(RuntimeState.networkAvailable(this.app) ? "SYNCING" : "OFFLINE");
        scheduleFallback(FIRST_POLL_MS);
        scheduleReconnect(0L);
    }

    private void closeSocket() {
        PushConnection pushConnection = this.socket;
        this.socket = null;
        if (pushConnection != null) {
            pushConnection.closeQuietly();
        }
    }

    private void scheduleReconnect(long j) {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        long max = Math.max(0L, j);
        this.main.removeCallbacks(this.reconnect);
        this.main.postDelayed(this.reconnect, max);
    }

    private long reconnectDelay() {
        int min = Math.min(this.reconnectAttempts, 5);
        long min2 = Math.min(RECONNECT_MAX_MS, RECONNECT_MIN_MS << min);
        return Math.min(RECONNECT_MAX_MS, min2 + RANDOM.nextInt(350));
    }

    private void connectSocket() {
        if (!this.running || this.socketConnected || this.socketConnecting) {
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final Uri parse;
        try {
            parse = Uri.parse(this.listener.currentUrl());
        } catch (Throwable unused) {
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        if (!ForumUrlRouter.isForumUrl(parse) || parse.getHost() == null) {
            state("PAUSED");
            scheduleFallback(IDLE_ROUTE_POLL_MS);
            return;
        }
        final String lowerCase = parse.getHost().toLowerCase(Locale.US);
        final int i = this.socketGeneration;
        this.socketIdentity = realtimeIdentity();
        this.socketConnecting = true;
        state("SYNCING");
        AppExecutors.network().execute(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.runSocketSession(i, lowerCase);
            }
        });
    }

    private void runSocketSession(final int i, String str) {
        PushConnection pushConnection = null;
        String str2 = "closed";
        try {
            RealtimeConfig realtimeConfig = fetchRealtimeConfig(str);
            if (!this.running || i != this.socketGeneration) {
                return;
            }
            if (realtimeConfig.key.isEmpty()) {
                throw new IOException("Flarum Pusher extension is not configured");
            }
            pushConnection = new PushConnection(realtimeConfig, str);
            pushConnection.open();
            if (!this.running || i != this.socketGeneration) {
                pushConnection.closeQuietly();
                return;
            }
            this.socket = pushConnection;
            String socketId = awaitConnectionEstablished(pushConnection);
            subscribe(pushConnection, "public", "");
            if (!realtimeConfig.userId.isEmpty() && !realtimeConfig.csrfToken.isEmpty()) {
                try {
                    String auth = authorizePrivateChannel(realtimeConfig, socketId);
                    if (!auth.isEmpty()) {
                        subscribe(pushConnection, "private-user" + realtimeConfig.userId, auth);
                    }
                } catch (Throwable th) {
                    AppLogger.warn(this.app, "live_update_socket_auth", th.getClass().getSimpleName());
                }
            }
            final PushConnection connected = pushConnection;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketConnected(i, connected);
                }
            });
            int heartbeatTimeouts = 0;
            while (this.running && i == this.socketGeneration) {
                try {
                    String text = pushConnection.readText();
                    heartbeatTimeouts = 0;
                    handlePusherMessage(pushConnection, text);
                } catch (SocketTimeoutException unused) {
                    heartbeatTimeouts++;
                    if (heartbeatTimeouts > 1) {
                        throw new IOException("Pusher heartbeat timeout");
                    }
                    pushConnection.sendPusherEvent("pusher:ping", new JSONObject());
                }
            }
        } catch (Throwable th2) {
            str2 = th2.getClass().getSimpleName() + ": " + String.valueOf(th2.getMessage());
        } finally {
            if (pushConnection != null) {
                pushConnection.closeQuietly();
            }
            final String reason = str2;
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleSocketDisconnected(i, reason);
                }
            });
        }
    }

    private String awaitConnectionEstablished(PushConnection pushConnection) throws Exception {
        int i = 0;
        while (i < 8) {
            i++;
            String readText = pushConnection.readText();
            JSONObject message = new JSONObject(readText);
            String event = message.optString("event", "");
            if ("pusher:connection_established".equals(event)) {
                JSONObject data = jsonData(message);
                String socketId = data.optString("socket_id", "");
                int activityTimeout = data.optInt("activity_timeout", 60);
                pushConnection.setReadTimeout(Math.max(20000, Math.min(PUSH_READ_TIMEOUT_MS, activityTimeout * 1000)));
                if (socketId.isEmpty()) {
                    throw new IOException("Pusher socket id missing");
                }
                return socketId;
            }
            if ("pusher:error".equals(event)) {
                throw new IOException("Pusher connection error");
            }
        }
        throw new IOException("Pusher connection handshake timed out");
    }

    private void subscribe(PushConnection pushConnection, String channel, String auth) throws Exception {
        JSONObject data = new JSONObject();
        data.put("channel", channel);
        if (auth != null && !auth.isEmpty()) {
            data.put("auth", auth);
        }
        pushConnection.sendPusherEvent("pusher:subscribe", data);
    }

    private String authorizePrivateChannel(RealtimeConfig realtimeConfig, String socketId) throws Exception {
        String channel = "private-user" + realtimeConfig.userId;
        String body = "socket_id=" + URLEncoder.encode(socketId, "UTF-8") + "&channel_name=" + URLEncoder.encode(channel, "UTF-8");
        byte[] bytes = body.getBytes("UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + realtimeConfig.forumHost + "/api/pusher/auth").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("X-CSRF-Token", realtimeConfig.csrfToken);
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " Realtime");
            String cookie = cookieForHost(realtimeConfig.forumHost);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Pusher auth HTTP " + code);
            }
            return new JSONObject(readLimited(connection.getInputStream())).optString("auth", "");
        } finally {
            connection.disconnect();
        }
    }

    private void handleSocketConnected(int generation, PushConnection pushConnection) {
        if (!this.running || generation != this.socketGeneration || this.socket != pushConnection) {
            pushConnection.closeQuietly();
            return;
        }
        this.socketConnecting = false;
        this.socketConnected = true;
        this.reconnectAttempts = 0;
        this.main.removeCallbacks(this.reconnect);
        this.main.removeCallbacks(this.tick);
        state("LIVE");
        AppLogger.info(this.app, "live_update_socket", "connected • push primary");
        HcfNotifications.InstantNotificationService.requestImmediateSync(this.app);
        schedulePushedRefresh(0L);
    }

    private void handleSocketDisconnected(int generation, String reason) {
        if (generation != this.socketGeneration) {
            return;
        }
        this.socket = null;
        this.socketConnecting = false;
        this.socketConnected = false;
        if (!this.running) {
            return;
        }
        this.reconnectAttempts = Math.min(this.reconnectAttempts + 1, 6);
        state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
        if (this.reconnectAttempts == 1 || this.reconnectAttempts == 3) {
            AppLogger.warn(this.app, "live_update_socket", "fallback polling | " + reason);
        }
        scheduleFallback(0L);
        scheduleReconnect(reconnectDelay());
    }

    private void handlePusherMessage(PushConnection pushConnection, String text) throws Exception {
        JSONObject message = new JSONObject(text);
        String event = message.optString("event", "");
        if ("pusher:ping".equals(event)) {
            pushConnection.sendPusherEvent("pusher:pong", new JSONObject());
            return;
        }
        if ("pusher:error".equals(event)) {
            throw new IOException("Pusher protocol error");
        }
        if (!"newPost".equals(event) && !"notification".equals(event)) {
            return;
        }
        final String pushEvent = event;
        final JSONObject data = jsonData(message);
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handlePushEvent(pushEvent, data);
            }
        });
    }

    private JSONObject jsonData(JSONObject message) {
        Object data = message.opt("data");
        if (data instanceof JSONObject) {
            return (JSONObject) data;
        }
        if (data instanceof String) {
            try {
                return new JSONObject((String) data);
            } catch (Throwable unused) {
            }
        }
        return new JSONObject();
    }

    private void handlePushEvent(String event, JSONObject data) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        if ("notification".equals(event)) {
            NotificationHelper.postFromPushPayload(this.app, data);
            HcfNotifications.InstantNotificationService.requestImmediateSync(this.app);
        }
        if (eventAffectsCurrentRoute(event, data)) {
            schedulePushedRefresh(0L);
        }
    }

    private boolean eventAffectsCurrentRoute(String event, JSONObject data) {
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                return false;
            }
            String path = uri.getPath() == null ? "/" : uri.getPath();
            String lower = path.toLowerCase(Locale.US);
            if (lower.startsWith("/notifications")) {
                return "notification".equals(event);
            }
            if (!"newPost".equals(event)) {
                return false;
            }
            if (lower.startsWith("/d/")) {
                String discussionId = discussionIdFromPath(path);
                String eventDiscussionId = data.optString("discussionId", "");
                return discussionId.isEmpty() || eventDiscussionId.isEmpty() || discussionId.equals(eventDiscussionId);
            }
            return "/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/");
        } catch (Throwable unused) {
            return false;
        }
    }

    private String discussionIdFromPath(String path) {
        if (path == null || path.length() < 4) {
            return "";
        }
        String value = path.substring(3);
        int slash = value.indexOf(47);
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int dash = value.indexOf(45);
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        return value.matches("[0-9]+") ? value : "";
    }

    private void schedulePushedRefresh(long delayMs) {
        if (!this.running || !this.socketConnected) {
            return;
        }
        this.main.removeCallbacks(this.pushedRefresh);
        this.main.postDelayed(this.pushedRefresh, Math.max(0L, delayMs));
    }

    private RealtimeConfig fetchRealtimeConfig(String host) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + host + "/api").openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " RealtimeBootstrap");
            String cookie = cookieForHost(host);
            if (!cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Flarum realtime bootstrap HTTP " + code);
            }
            JSONObject root = new JSONObject(readLimited(connection.getInputStream()));
            JSONObject data = root.optJSONObject("data");
            JSONObject attributes = data == null ? null : data.optJSONObject("attributes");
            RealtimeConfig config = new RealtimeConfig();
            config.forumHost = host;
            config.key = attributes == null ? "" : attributes.optString("pusherKey", "").trim();
            config.cluster = attributes == null ? "" : attributes.optString("pusherCluster", "").trim().toLowerCase(Locale.US);
            if (config.cluster.isEmpty()) {
                config.cluster = "mt1";
            }
            if (!config.cluster.matches("[a-z0-9-]+")) {
                throw new IOException("Invalid Pusher cluster");
            }
            config.csrfToken = header(connection, "X-CSRF-Token");
            String userId = this.prefs.getString("session_user_id", "");
            config.userId = userId == null ? "" : userId.trim();
            return config;
        } finally {
            connection.disconnect();
        }
    }

    private String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value == null ? "" : value.trim();
    }

    private void scheduleFallback(long delayMs) {
        if (!this.running || this.socketConnected) {
            return;
        }
        long delay = Math.max(0L, delayMs);
        RuntimeDiagnostics.livePoll(delay);
        this.main.removeCallbacks(this.tick);
        this.main.postDelayed(this.tick, delay);
    }

    public void poll() {
        refreshCurrentRoute(true, false);
    }

    private void refreshCurrentRoute(final boolean fallbackPoll, final boolean forceRefresh) {
        if (!this.running || (fallbackPoll && this.socketConnected)) {
            return;
        }
        if (this.inFlight) {
            this.queuedRefresh = true;
            this.forceQueuedRefresh |= forceRefresh;
            return;
        }
        if (!RuntimeState.networkAvailable(this.app)) {
            state("OFFLINE");
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
            return;
        }
        try {
            Uri uri = Uri.parse(this.listener.currentUrl());
            if (!ForumUrlRouter.isForumUrl(uri)) {
                state("PAUSED");
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            final String endpoint = endpointFor(uri);
            final String key = pageKey(uri, endpoint);
            if (key == null || endpoint == null) {
                if (this.socketConnected) {
                    state("LIVE");
                }
                if (fallbackPoll) {
                    scheduleFallback(IDLE_ROUTE_POLL_MS);
                }
                return;
            }
            this.inFlight = true;
            AppExecutors.network().execute(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.fetchRouteFingerprint(endpoint, key, fallbackPoll, forceRefresh);
                }
            });
        } catch (Throwable unused) {
            if (fallbackPoll) {
                scheduleFallback(IDLE_ROUTE_POLL_MS);
            }
        }
    }

    private void fetchRouteFingerprint(String endpoint, final String key, final boolean fallbackPoll, boolean forceRefresh) {
        final String fingerprint;
        try {
            fingerprint = fetchFingerprint(endpoint, forceRefresh);
        } catch (Throwable th) {
            if (this.failures == 0 || this.failures == 2) {
                AppLogger.warn(this.app, fallbackPoll ? "live_update_poll" : "live_update_push_refresh", th.getClass().getSimpleName());
            }
            this.main.post(new Runnable() {
                @Override
                public void run() {
                    LiveForumUpdater.this.handleFingerprintResult(key, null, fallbackPoll);
                }
            });
            return;
        }
        this.main.post(new Runnable() {
            @Override
            public void run() {
                LiveForumUpdater.this.handleFingerprintResult(key, fingerprint, fallbackPoll);
            }
        });
    }

    private void handleFingerprintResult(String key, String fingerprint, boolean fallbackPoll) {
        String previous;
        this.inFlight = false;
        if (!this.running) {
            return;
        }
        if (fingerprint == null || fingerprint.isEmpty()) {
            this.failures = Math.min(this.failures + 1, 5);
            if (!this.socketConnected) {
                state(RuntimeState.networkAvailable(this.app) ? "WAITING" : "OFFLINE");
                if (fallbackPoll) {
                    scheduleFallback(Math.min(4000L, 500L << Math.min(Math.max(0, this.failures - 1), 3)));
                }
            }
        } else {
            this.failures = 0;
            state("LIVE");
            if (!key.equals(this.baselineKey) || (previous = this.baselineFingerprint) == null) {
                previous = loadBaseline(key);
                this.baselineKey = key;
                this.baselineFingerprint = previous == null ? fingerprint : previous;
                if (previous == null) saveBaseline(key, fingerprint);
            }
            previous = this.baselineFingerprint;
            if (previous != null && !fingerprint.equals(previous)) {
                this.listener.onChangeCandidate(key, fingerprint);
            }
            if (fallbackPoll && !this.socketConnected) {
                scheduleFallback(PerformanceProfile.livePollInterval(this.app, this.prefs));
            }
        }
        if (this.queuedRefresh) {
            boolean force = this.forceQueuedRefresh;
            this.queuedRefresh = false;
            this.forceQueuedRefresh = false;
            refreshCurrentRoute(!this.socketConnected, force);
        }
    }

    private void state(String state) {
        if (state.equals(this.lastState)) return;
        String previous = this.lastState == null || this.lastState.isEmpty() ? "NONE" : this.lastState;
        this.lastState = state;
        AppLogger.info(this.app, "live_update_state", previous + " -> " + state);
        this.listener.onStateChanged(state);
    }

    private String baselinePrefKey(String key) {
        if (key == null) return "";
        return "live_fp_" + Integer.toHexString(key.hashCode());
    }

    private String loadBaseline(String key) {
        try {
            String value = this.prefs.getString(baselinePrefKey(key), null);
            return value == null || value.isEmpty() ? null : value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void saveBaseline(String key, String fingerprint) {
        if (key == null || fingerprint == null || fingerprint.isEmpty()) return;
        try {
            this.prefs.edit().putString(baselinePrefKey(key), fingerprint).apply();
        } catch (Throwable ignored) {
        }
    }

    private String endpointFor(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        String lower = path.toLowerCase(Locale.US);
        String root = "https://" + host;
        if (lower.startsWith("/d/")) {
            String discussionId = discussionIdFromPath(path);
            if (!discussionId.isEmpty()) {
                return root + "/api/discussions/" + discussionId + "?fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
            }
        }
        if ("/".equals(lower) || lower.startsWith("/all") || lower.startsWith("/following") || lower.startsWith("/tags") || lower.startsWith("/t/")) {
            return root + "/api/discussions?sort=-lastPostedAt&page%5Blimit%5D=1&fields%5Bdiscussions%5D=lastPostedAt%2ClastPostNumber%2CcommentCount";
        }
        if (!lower.startsWith("/notifications")) {
            return null;
        }
        return root + "/api/notifications?page%5Blimit%5D=1";
    }

    private String pageKey(Uri uri, String endpoint) {
        if (endpoint == null) {
            return null;
        }
        String path = uri.getPath() == null ? "/" : uri.getPath();
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost() == null ? "" : uri.getHost());
        sb.append("|");
        sb.append(path);
        sb.append("|");
        sb.append(endpoint);
        return sb.toString();
    }

    private String fetchFingerprint(String endpoint, boolean forceRefresh) throws Exception {
        CacheEntry cacheEntry = this.cache.get(endpoint);
        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.api+json, application/json");
            connection.setRequestProperty("Cache-Control", "no-cache, max-age=0");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " LiveUpdate");
            if (!forceRefresh && cacheEntry != null) {
                if (!cacheEntry.etag.isEmpty()) {
                    connection.setRequestProperty("If-None-Match", cacheEntry.etag);
                }
                if (cacheEntry.lastModified > 0) {
                    connection.setIfModifiedSince(cacheEntry.lastModified);
                }
            }
            String cookie = CookieManager.getInstance().getCookie(url.getProtocol() + "://" + url.getHost() + "/");
            if (cookie != null && !cookie.trim().isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int code = connection.getResponseCode();
            if (code == 304 && cacheEntry != null) {
                return cacheEntry.signature;
            }
            if (code < 200 || code >= 300) {
                return null;
            }
            String signature = compactSignature(readLimited(connection.getInputStream()));
            if (signature != null && !signature.isEmpty()) {
                CacheEntry next = new CacheEntry();
                String etag = connection.getHeaderField("ETag");
                next.etag = etag == null ? "" : etag;
                next.lastModified = connection.getLastModified();
                next.signature = signature;
                this.cache.put(endpoint, next);
                return signature;
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private String compactSignature(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            Object data = new JSONObject(body).opt("data");
            StringBuilder sb = new StringBuilder(160);
            if (data instanceof JSONObject) {
                appendResourceSignature(sb, (JSONObject) data);
            } else if (data instanceof JSONArray) {
                JSONArray array = (JSONArray) data;
                for (int i = 0; i < array.length() && i < 2; i++) {
                    JSONObject resource = array.optJSONObject(i);
                    if (resource != null) {
                        appendResourceSignature(sb, resource);
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        } catch (Throwable unused) {
        }
        return "fallback|" + body.length() + "|" + Integer.toHexString(body.substring(0, Math.min(body.length(), 32768)).hashCode());
    }

    private void appendResourceSignature(StringBuilder sb, JSONObject resource) {
        sb.append(resource.optString("type", ""));
        sb.append(':');
        sb.append(resource.optString("id", ""));
        JSONObject attributes = resource.optJSONObject("attributes");
        if (attributes != null) {
            appendAttr(sb, attributes, "lastPostNumber");
            appendAttr(sb, attributes, "lastPostedAt");
            appendAttr(sb, attributes, "commentCount");
            appendAttr(sb, attributes, "newNotificationCount");
            appendAttr(sb, attributes, "unreadNotificationCount");
            appendAttr(sb, attributes, "time");
            appendAttr(sb, attributes, "createdAt");
            appendAttr(sb, attributes, "isRead");
            appendAttr(sb, attributes, "type");
        }
        sb.append('|');
    }

    private void appendAttr(StringBuilder sb, JSONObject attributes, String name) {
        Object value;
        if (!attributes.has(name) || (value = attributes.opt(name)) == null || value == JSONObject.NULL) {
            return;
        }
        sb.append(';');
        sb.append(name);
        sb.append('=');
        sb.append(String.valueOf(value));
    }

    private String readLimited(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        while (true) {
            int read = reader.read(buffer);
            if (read == -1 || sb.length() >= MAX_BODY_CHARS) {
                break;
            }
            sb.append(buffer, 0, Math.min(read, MAX_BODY_CHARS - sb.length()));
        }
        reader.close();
        return sb.toString();
    }

    private static final class RealtimeConfig {
        String cluster = "mt1";
        String csrfToken = "";
        String forumHost = "";
        String key = "";
        String userId = "";
    }

    private static final class PushConnection {
        private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private final RealtimeConfig config;
        private final String originHost;
        private InputStream input;
        private OutputStream output;
        private SSLSocket socket;

        PushConnection(RealtimeConfig realtimeConfig, String originHost) {
            this.config = realtimeConfig;
            this.originHost = originHost;
        }

        void open() throws Exception {
            String wsHost = "ws-" + this.config.cluster + ".pusher.com";
            String path = "/app/" + this.config.key + "?protocol=7&client=android-hcf&version=1.0&flash=false";
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(wsHost, 443), CONNECT_TIMEOUT_MS);
            SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(rawSocket, wsHost, 443, true);
            this.socket = sslSocket;
            sslSocket.setSoTimeout(PUSH_READ_TIMEOUT_MS);
            this.socket.startHandshake();
            this.input = this.socket.getInputStream();
            this.output = this.socket.getOutputStream();
            byte[] nonce = new byte[16];
            RANDOM.nextBytes(nonce);
            String webSocketKey = Base64.encodeToString(nonce, Base64.NO_WRAP);
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(wsHost).append("\r\n");
            request.append("Upgrade: websocket\r\n");
            request.append("Connection: Upgrade\r\n");
            request.append("Sec-WebSocket-Key: ").append(webSocketKey).append("\r\n");
            request.append("Sec-WebSocket-Version: 13\r\n");
            request.append("Origin: https://").append(this.originHost).append("\r\n");
            request.append("User-Agent: HarleysClanForumApp/1.0 Realtime\r\n\r\n");
            this.output.write(request.toString().getBytes("UTF-8"));
            this.output.flush();
            String status = readHttpLine(this.input);
            if (status == null || status.indexOf(" 101 ") < 0) {
                throw new IOException("WebSocket upgrade failed: " + status);
            }
            String accept = "";
            while (true) {
                String header = readHttpLine(this.input);
                if (header == null || header.isEmpty()) {
                    break;
                }
                int colon = header.indexOf(58);
                if (colon > 0 && "sec-websocket-accept".equals(header.substring(0, colon).trim().toLowerCase(Locale.US))) {
                    accept = header.substring(colon + 1).trim();
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String expected = Base64.encodeToString(digest.digest((webSocketKey + WS_GUID).getBytes("UTF-8")), Base64.NO_WRAP);
            if (!expected.equals(accept)) {
                throw new IOException("WebSocket accept mismatch");
            }
        }

        void setReadTimeout(int timeoutMs) throws Exception {
            SSLSocket sslSocket = this.socket;
            if (sslSocket != null) {
                sslSocket.setSoTimeout(timeoutMs);
            }
        }

        synchronized void sendPusherEvent(String event, JSONObject data) throws Exception {
            JSONObject message = new JSONObject();
            message.put("event", event);
            message.put("data", data);
            sendText(message.toString());
        }

        synchronized void sendText(String text) throws Exception {
            sendFrame(1, text.getBytes("UTF-8"));
        }

        private synchronized void sendControl(int opcode, byte[] payload) throws Exception {
            sendFrame(opcode, payload);
        }

        private void sendFrame(int opcode, byte[] payload) throws Exception {
            OutputStream outputStream = this.output;
            if (outputStream == null) {
                throw new EOFException("WebSocket closed");
            }
            if (payload == null) {
                payload = new byte[0];
            }
            if (payload.length > MAX_WS_PAYLOAD_BYTES) {
                throw new IOException("WebSocket payload too large");
            }
            outputStream.write(128 | (opcode & 15));
            int length = payload.length;
            if (length < 126) {
                outputStream.write(128 | length);
            } else if (length <= 65535) {
                outputStream.write(254);
                outputStream.write((length >>> 8) & 255);
                outputStream.write(length & 255);
            } else {
                outputStream.write(255);
                long frameLength = length;
                for (int i = 7; i >= 0; i--) {
                    outputStream.write((int) ((frameLength >>> (i * 8)) & 255));
                }
            }
            byte[] mask = new byte[4];
            RANDOM.nextBytes(mask);
            outputStream.write(mask);
            for (int i = 0; i < payload.length; i++) {
                outputStream.write(payload[i] ^ mask[i & 3]);
            }
            outputStream.flush();
        }

        String readText() throws Exception {
            ByteArrayOutputStream fragments = null;
            int fragmentedOpcode = 0;
            while (true) {
                int first = readRequired(this.input);
                int second = readRequired(this.input);
                boolean fin = (first & 128) != 0;
                int opcode = first & 15;
                boolean masked = (second & 128) != 0;
                long length = second & 127;
                if (length == 126) {
                    length = (readRequired(this.input) << 8) | readRequired(this.input);
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        length = (length << 8) | readRequired(this.input);
                    }
                }
                if (length < 0 || length > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket payload exceeds limit");
                }
                byte[] mask = null;
                if (masked) {
                    mask = new byte[4];
                    readFully(this.input, mask);
                }
                byte[] payload = new byte[(int) length];
                readFully(this.input, payload);
                if (mask != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i & 3]);
                    }
                }
                if (opcode == 8) {
                    throw new EOFException("WebSocket closed by peer");
                }
                if (opcode == 9) {
                    sendControl(10, payload);
                    continue;
                }
                if (opcode == 10) {
                    continue;
                }
                if (opcode == 1) {
                    fragments = new ByteArrayOutputStream(Math.max(64, payload.length));
                    fragments.write(payload);
                    fragmentedOpcode = 1;
                } else if (opcode == 0 && fragments != null && fragmentedOpcode == 1) {
                    fragments.write(payload);
                } else {
                    continue;
                }
                if (fragments.size() > MAX_WS_PAYLOAD_BYTES) {
                    throw new IOException("WebSocket fragmented payload exceeds limit");
                }
                if (fin) {
                    return new String(fragments.toByteArray(), "UTF-8");
                }
            }
        }

        void closeQuietly() {
            try {
                SSLSocket sslSocket = this.socket;
                if (sslSocket != null) {
                    sslSocket.close();
                }
            } catch (Throwable unused) {
            }
            this.socket = null;
            this.input = null;
            this.output = null;
        }

        private static int readRequired(InputStream inputStream) throws Exception {
            if (inputStream == null) {
                throw new EOFException("WebSocket input closed");
            }
            int value = inputStream.read();
            if (value < 0) {
                throw new EOFException("WebSocket EOF");
            }
            return value;
        }

        private static void readFully(InputStream inputStream, byte[] bytes) throws Exception {
            int offset = 0;
            while (offset < bytes.length) {
                int read = inputStream.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new EOFException("WebSocket EOF");
                }
                offset += read;
            }
        }

        private static String readHttpLine(InputStream inputStream) throws Exception {
            StringBuilder sb = new StringBuilder();
            int previous = -1;
            while (sb.length() < 8192) {
                int value = inputStream.read();
                if (value < 0) {
                    return sb.length() == 0 ? null : sb.toString();
                }
                if (previous == 13 && value == 10) {
                    sb.setLength(Math.max(0, sb.length() - 1));
                    return sb.toString();
                }
                sb.append((char) value);
                previous = value;
            }
            throw new IOException("WebSocket HTTP header line too long");
        }
    }

    private static final class CacheEntry {
        String etag = "";
        long lastModified;
        String signature = "";
    }
}


// ---- RemoteDomainConfig.java ----
/** Pulls the trusted forum-domain registry from the hcf-app main branch. */
final class RemoteDomainConfig {
    static final String CONFIG_URL = BuildInfo.REMOTE_DOMAIN_CONFIG;

    private static final String PREFS = "hcf_remote_domains";
    private static final String KEY_RAW = "raw";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final long REFRESH_MS = 60L * 60L * 1000L;
    private static final int MAX_BYTES = 16 * 1024;

    static void initialize(Context context) {
        final Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = prefs.getString(KEY_RAW, null);
        if (cached != null) {
            Parsed parsed = parse(cached);
            if (parsed != null) ForumConfig.applyRemote(parsed.primary, parsed.backups);
        }

        long age = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0L);
        if (age >= 0L && age < REFRESH_MS) return;

        AppExecutors.network().execute(new Runnable() {
            @Override public void run() {
                refresh(app);
            }
        });
    }

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/plain, text/*;q=0.9, */*;q=0.1");
            connection.setRequestProperty("User-Agent", BuildInfo.USER_AGENT_MARKER + " DomainConfig/" + BuildInfo.VERSION_CODE);
            String etag = prefs.getString(KEY_ETAG, null);
            if (etag != null && !etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                prefs.edit().putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply();
                return;
            }
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + status);

            String raw = readLimited(connection.getInputStream());
            Parsed parsed = parse(raw);
            if (parsed == null) throw new IllegalArgumentException("Invalid domains.config");

            ForumConfig.applyRemote(parsed.primary, parsed.backups);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(KEY_RAW, raw)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis());
            String newEtag = connection.getHeaderField("ETag");
            if (newEtag != null && !newEtag.isEmpty()) edit.putString(KEY_ETAG, newEtag);
            edit.apply();
            AppLogger.info(app, "domain_config", "github-main | primary=" + parsed.primary + " | hosts=" + ForumConfig.FORUM_HOSTS.size());
        } catch (Throwable error) {
            AppLogger.warn(app, "domain_config", "fallback | " + error.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Parsed parse(String raw) {
        if (raw == null || raw.length() > MAX_BYTES) return null;
        String section = "";
        String primary = null;
        Set<String> backups = new LinkedHashSet<>();
        Set<String> all = new LinkedHashSet<>();

        try {
            BufferedReader reader = new BufferedReader(new java.io.StringReader(raw));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.US);
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) continue;
                String key = line.substring(0, equals).trim().toLowerCase(Locale.US);
                String value = line.substring(equals + 1).trim();
                if ("primary".equals(section) && "domain".equals(key)) {
                    primary = normalizeHost(value);
                    if (primary == null) return null;
                    all.add(primary);
                } else if ("backups".equals(section) && key.startsWith("domain")) {
                    String host = normalizeHost(value);
                    if (host == null) return null;
                    if (all.add(host)) backups.add(host);
                }
            }
        } catch (Throwable ignored) {
            return null;
        }

        if (primary == null || primary.isEmpty()) return null;
        return new Parsed(primary, new ArrayList<>(backups));
    }

    private static String normalizeHost(String value) {
        if (value == null) return null;
        String host = value.trim().toLowerCase(Locale.US);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > 253) return null;
        if (host.contains("://") || host.contains("/") || host.contains("\\") || host.contains(":") || host.contains("@") || host.contains("?" ) || host.contains("#")) return null;
        try {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return null;
        }
        if (!(host.equals("harleytg.com") || host.endsWith(".harleytg.com") || host.equals("freeflarum.com") || host.endsWith(".freeflarum.com"))) return null;
        return host;
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new IllegalArgumentException("domains.config too large");
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Parsed {
        final String primary;
        final List<String> backups;
        Parsed(String primary, List<String> backups) {
            this.primary = primary;
            this.backups = backups;
        }
    }

    private RemoteDomainConfig() {}
}


// ---- AppDomainRouter.java ----
/**
 * Local in-app URL namespace for Harley's Clan Forum.
 *
 * Format:
 *   app.forum.harleytg.com/<item>
 *
 * These addresses are intercepted by the Android app. They are not treated as
 * real forum hosts and are never sent to DNS/the network.
 */
final class AppDomainRouter {
    public static final String APP_HOST = "app.forum.harleytg.com";

    private AppDomainRouter() {
    }

    /**
     * Installs a very small inflater hook before MainActivity inflates its UI.
     * Only R.id.currentUrlText is replaced; every other view is left to the
     * normal Android inflater.
     */
    static void installAddressBarInflater(final Activity activity) {
        if (activity == null) {
            return;
        }

        try {
            final LayoutInflater inflater = activity.getLayoutInflater();
            if (inflater == null || inflater.getFactory2() != null || inflater.getFactory() != null) {
                return;
            }

            inflater.setFactory2(new LayoutInflater.Factory2() {
                @Override
                public View onCreateView(View parent, String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }

                @Override
                public View onCreateView(String name, android.content.Context context, AttributeSet attrs) {
                    return createAddressBarIfNeeded(name, context, attrs);
                }
            });
        } catch (Throwable error) {
            try {
                AppLogger.warn(activity, "app_domain_inflater", error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    private static View createAddressBarIfNeeded(
            String name,
            android.content.Context context,
            AttributeSet attrs
    ) {
        if (context == null || attrs == null || name == null) {
            return null;
        }

        int id = attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res/android", "id", 0);
        if (id == R.id.currentUrlText && ("EditText".equals(name) || "android.widget.EditText".equals(name)))
            return new AppDomainEditText(context, attrs);
        if (id == R.id.urlBackButton && ("ImageButton".equals(name) || "android.widget.ImageButton".equals(name)))
            return new UrlBackButton(context, attrs);
        return null;
    }

    public static boolean isAppUrl(String rawInput) {
        Uri uri = parse(rawInput);
        return uri != null
                && uri.getHost() != null
                && APP_HOST.equalsIgnoreCase(uri.getHost());
    }

    /**
     * Handles app.forum.harleytg.com/<item>.
     *
     * @return true when this was an app-domain URL and normal URL handling
     * should stop.
     */
    public static boolean handle(Activity activity, String rawInput) {
        if (activity == null || !isAppUrl(rawInput)) {
            return false;
        }

        Uri uri = parse(rawInput);
        String item = firstPathItem(uri);

        try {
            switch (item) {
                case "":
                case "home":
                case "menu":
                    showMenu(activity);
                    break;

                case "settings":
                case "preferences":
                case "appearance":
                case "updates":
                case "advanced":
                case "about":
                    open(activity, HcfSubActivities.SettingsActivity.class, item.isEmpty() ? "settings" : item);
                    break;

                case "identity":
                case "account":
                    open(activity, HcfSubActivities.IdentityActivity.class, "identity");
                    break;

                case "setup":
                case "app-setup":
                    open(activity, HcfForum.SetupActivity.class, "setup");
                    break;

                case "welcome":
                    open(activity, HcfForum.WelcomeActivity.class, "welcome");
                    break;

                case "logs":
                case "diagnostics":
                case "crash-logs":
                    open(activity, HcfSubActivities.LogsActivity.class, "logs");
                    break;

                case "support":
                case "contact":
                case "contact-support":
                    open(activity, HcfSubActivities.SupportContactActivity.class, "support");
                    break;

                case "cookies":
                case "cookie-manager":
                    open(activity, HcfForum.CookieManagerActivity.class, "cookies");
                    break;

                case "notification-settings":
                case "alert-settings":
                    openNotificationSettings(activity);
                    break;

                case "permissions":
                case "app-info":
                case "android-settings":
                    openAndroidAppInfo(activity);
                    break;

                case "forum":
                    loadForumPath(activity, "/", "forum_home");
                    break;

                case "browse":
                case "all":
                    loadForumPath(activity, "/all", "browse");
                    break;

                case "notifications":
                case "alerts":
                    loadForumPath(activity, "/notifications", "notifications");
                    try {
                        HcfNotifications.InstantNotificationService.requestImmediateSync(activity);
                    } catch (Throwable ignored) {
                    }
                    break;

                case "profile":
                case "my-profile":
                    openForumProfile(activity);
                    break;

                case "compose":
                case "new":
                case "new-discussion":
                    loadForumPath(activity, "/compose", "compose");
                    break;

                case "downloads":
                    loadForumPath(activity, "/p/14-downloads", "downloads");
                    break;

                case "forum-support":
                case "support-page":
                    loadForumPath(activity, "/p/17-support", "forum_support");
                    break;

                default:
                    showUnknownItem(activity, item);
                    break;
            }

            return true;
        } catch (Throwable error) {
            try {
                AppLogger.error(activity, "app_domain", item + " | " + error.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }

            Toast.makeText(
                    activity,
                    "Unable to open app item: " + item,
                    Toast.LENGTH_SHORT
            ).show();
            return true;
        }
    }

    private static void showMenu(final Activity activity) {
        final String[] labels = {
                "App Settings",
                "Account & Identity",
                "App Setup",
                "Welcome Screen",
                "Logs & Diagnostics",
                "Contact Support",
                "Cookie Manager",
                "Notification Settings",
                "Android App Info",
                "Forum Home",
                "Browse Discussions",
                "Notifications",
                "My Profile",
                "New Discussion",
                "Downloads"
        };

        final String[] items = {
                "settings",
                "identity",
                "setup",
                "welcome",
                "logs",
                "support",
                "cookies",
                "notification-settings",
                "app-info",
                "forum",
                "browse",
                "notifications",
                "profile",
                "compose",
                "downloads"
        };

        new AlertDialog.Builder(activity)
                .setTitle("Harley's Clan Forum App")
                .setMessage(APP_HOST)
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < items.length) {
                        handle(activity, APP_HOST + "/" + items[which]);
                    }
                })
                .setNegativeButton("Close", null)
                .show();

        AppLogger.info(activity, "app_domain", "menu");
    }

    private static void open(Activity activity, Class<?> target, String destination) {
        activity.startActivity(new Intent(activity, target));
        AppLogger.info(activity, "app_domain", destination);
    }

    private static void openNotificationSettings(Activity activity) {
        try {
            NotificationHelper.createChannel(activity);
            NotificationHelper.openChannelSettings(activity);
            AppLogger.info(activity, "app_domain", "notification_settings");
        } catch (Throwable error) {
            AppLogger.warn(activity, "app_domain_notification_settings", error.getClass().getSimpleName());
            openAndroidAppInfo(activity);
        }
    }

    private static void openAndroidAppInfo(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
        AppLogger.info(activity, "app_domain", "android_app_info");
    }

    private static void openForumProfile(Activity activity) {
        ForumIdentity.Snapshot snapshot = ForumIdentity.load(activity);

        if (snapshot == null || !snapshot.loggedIn) {
            loadForumPath(activity, "/login", "profile_login");
            return;
        }

        String slug = snapshot.slug;
        if (slug == null || slug.trim().isEmpty()) {
            slug = snapshot.username;
        }

        if (slug == null || slug.trim().isEmpty()) {
            Toast.makeText(activity, "Your profile is still syncing.", Toast.LENGTH_SHORT).show();
            try {
                HcfNotifications.InstantNotificationService.requestImmediateSync(activity);
            } catch (Throwable ignored) {
            }
            return;
        }

        loadForumPath(activity, "/u/" + Uri.encode(slug.trim()), "profile");
    }

    private static void loadForumPath(Activity activity, String path, String destination) {
        WebView webView = activity.findViewById(R.id.webView);
        if (webView == null) {
            Toast.makeText(activity, "Forum view is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = activeForumHost(activity);
        String safePath = path == null || path.trim().isEmpty() ? "/" : path.trim();
        if (!safePath.startsWith("/") || safePath.startsWith("//")) {
            safePath = "/";
        }

        String url = "https://" + host + safePath;
        webView.loadUrl(url);
        AppLogger.info(activity, "app_domain", destination + " | " + AppLogger.safeUrl(url));
    }

    private static String activeForumHost(Activity activity) {
        String host = "";
        try {
            SharedPreferences prefs = activity.getSharedPreferences("hcf_app", 0);
            host = prefs.getString("active_host", "");
        } catch (Throwable ignored) {
        }

        if (host != null) {
            host = host.trim().toLowerCase(Locale.US);
            if (ForumConfig.FORUM_HOSTS.contains(host)) {
                return host;
            }
        }

        String primary = ForumConfig.PRIMARY_HOST;
        if (primary == null || primary.trim().isEmpty()) {
            primary = ForumConfig.BUILTIN_PRIMARY_HOST;
        }
        return primary.toLowerCase(Locale.US);
    }

    private static Uri parse(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String value = rawInput.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            if (!value.contains("://")) {
                value = "https://" + value;
            }
            return Uri.parse(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Only the first item after app.forum.harleytg.com/ is the command. */
    private static String firstPathItem(Uri uri) {
        if (uri == null) {
            return "";
        }

        String path = uri.getPath();
        if (path == null || path.trim().isEmpty() || "/".equals(path)) {
            return "";
        }

        String value = path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }

        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }

        return value.trim().toLowerCase(Locale.US).replace('_', '-');
    }

    private static void showUnknownItem(Activity activity, String item) {
        new AlertDialog.Builder(activity)
                .setTitle("Unknown app item")
                .setMessage(
                        APP_HOST + "/" + item
                                + "\n\nUse " + APP_HOST
                                + " to see available in-app items."
                )
                .setPositiveButton("OK", null)
                .show();

        AppLogger.warn(activity, "app_domain", "unknown | " + item);
    }
}

// ---- Consolidated from HcfSessionPersistence.java ----
/**
 * Keeps the forum WebView cookie store synchronized with persistent storage and
 * owns the single visible native startup loader used by the Dev/Beta app.
 *
 * Cookie values are never read or exported here. Android WebView's CookieManager
 * is only asked to persist its existing first-party session state.
 */
final class HcfSessionPersistence {
    private static final long FOREGROUND_FLUSH_MS = 15000L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger RESUMED_ACTIVITIES = new AtomicInteger(0);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HcfSessionPersistence() {}

    private static final Runnable FOREGROUND_FLUSH = new Runnable() {
        @Override public void run() {
            if (RESUMED_ACTIVITIES.get() <= 0) return;
            flushCookies();
            MAIN.postDelayed(this, FOREGROUND_FLUSH_MS);
        }
    };

    static void flushCookies() {
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            manager.flush();
        } catch (Throwable ignored) {
        }
    }

    private static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        flushCookies();

        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) return;
        Application application = (Application) appContext;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}

            @Override public void onActivityResumed(Activity activity) {
                int resumed = RESUMED_ACTIVITIES.incrementAndGet();
                flushCookies();
                if (resumed == 1) {
                    MAIN.removeCallbacks(FOREGROUND_FLUSH);
                    MAIN.postDelayed(FOREGROUND_FLUSH, FOREGROUND_FLUSH_MS);
                }
            }

            @Override public void onActivityPaused(Activity activity) {
                flushCookies();
                int remaining = Math.max(0, RESUMED_ACTIVITIES.decrementAndGet());
                RESUMED_ACTIVITIES.set(remaining);
                if (remaining == 0) MAIN.removeCallbacks(FOREGROUND_FLUSH);
            }

            @Override public void onActivityStopped(Activity activity) {
                flushCookies();
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                flushCookies();
            }

            @Override public void onActivityDestroyed(Activity activity) {
                flushCookies();
            }
        });
    }

    /** Installs persistence before the first Activity/WebView is created. */
    public static final class BootstrapProvider extends ContentProvider {
        @Override public boolean onCreate() {
            install(getContext());
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override public String getType(Uri uri) {
            return null;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            return 0;
        }
    }
}
