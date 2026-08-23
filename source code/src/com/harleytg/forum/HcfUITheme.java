package com.harleytg.forum.dev;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public final class HcfUITheme {
    private HcfUITheme() {}
}

// ---- ThemeManager.java ----
/**
 * App theme controller: light / dark / AMOLED / auto_forum / auto_phone.
 *
 * Cold-start: applyToApplication(Application) updates the process
 * Configuration so the first windowBackground and night resources match the
 * last known preference (including persisted forum_auto_theme).
 */
final class ThemeManager {
    static final String AMOLED = "amoled";
    static final String AUTO_FORUM = "auto_forum";
    static final String AUTO_PHONE = "auto_phone";
    static final String DARK = "dark";
    private static final String FORUM_AUTO = "auto";
    private static final String FORUM_DARK = "dark";
    private static final String FORUM_LIGHT = "light";
    private static final String LEGACY_SYSTEM = "system";
    static final String LIGHT = "light";
    static final String SYSTEM = "auto_forum";

    /** UI_MODE_NIGHT_YES */
    private static final int NIGHT_YES = 0x20;
    /** UI_MODE_NIGHT_NO */
    private static final int NIGHT_NO = 0x10;
    private static final int UI_MODE_NIGHT_MASK = 0x30;

    /**
     * Apply resolved night mode to the Application resources as early as
     * possible (call from HcfApplication.onCreate() before activities).
     */
    static void applyToApplication(Application application) {
        if (application == null) {
            return;
        }
        try {
            Context base = application.getBaseContext() != null ? application.getBaseContext() : application;
            String mode = mode(base);
            Configuration current = application.getResources().getConfiguration();
            int night = resolvedNightMode(base, current, mode);
            if ((current.uiMode & UI_MODE_NIGHT_MASK) == night) {
                return;
            }
            Configuration updated = new Configuration(current);
            updated.uiMode = night | (updated.uiMode & ~UI_MODE_NIGHT_MASK);
            Resources resources = application.getResources();
            //noinspection deprecation
            resources.updateConfiguration(updated, resources.getDisplayMetrics());
        } catch (Throwable unused) {
        }
    }

    static Context wrap(Context context) {
        if (context == null) {
            return null;
        }
        try {
            String mode = mode(context);
            Configuration configuration = context.getResources().getConfiguration();
            int resolvedNightMode = resolvedNightMode(context, configuration, mode);
            if ((configuration.uiMode & UI_MODE_NIGHT_MASK) == resolvedNightMode) {
                return context;
            }
            Configuration configuration2 = new Configuration(configuration);
            configuration2.uiMode = resolvedNightMode | (configuration2.uiMode & ~UI_MODE_NIGHT_MASK);
            return context.createConfigurationContext(configuration2);
        } catch (Throwable unused) {
            return context;
        }
    }

    static void prepare(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
    }

    static void apply(Activity activity) {
        activity.setTheme(R.style.Theme_HCF);
        applySystemBars(activity);
    }

    static int resolvedNightMode(Context context) {
        return resolvedNightMode(context, context.getResources().getConfiguration(), mode(context));
    }

    private static int resolvedNightMode(Context context, Configuration configuration, String str) {
        if (DARK.equals(str) || AMOLED.equals(str)) {
            return NIGHT_YES;
        }
        if (LIGHT.equals(str)) {
            return NIGHT_NO;
        }
        if (AUTO_PHONE.equals(str)) {
            return phoneNightMode(configuration);
        }
        // auto_forum: prefer last known forum day/night so cold start matches
        // the previous session instead of flashing phone light first.
        String forumAutoTheme = forumAutoTheme(context);
        if (FORUM_DARK.equals(forumAutoTheme)) {
            return NIGHT_YES;
        }
        if (FORUM_LIGHT.equals(forumAutoTheme)) {
            return NIGHT_NO;
        }
        return phoneNightMode(configuration);
    }

    private static int phoneNightMode(Configuration configuration) {
        return (configuration.uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES ? NIGHT_YES : NIGHT_NO;
    }

    static String signature(Context context) {
        return mode(context) + ":" + resolvedNightMode(context) + (isAmoled(context) ? ":amoled" : "");
    }

    static boolean changedSince(Context context, String str) {
        try {
            return str == null || !str.equals(signature(context));
        } catch (Throwable unused) {
            return false;
        }
    }

    static String webColorScheme(Context context) {
        return resolvedNightMode(context) == NIGHT_YES ? "dark" : "light";
    }

    static void applySystemBars(Activity activity) {
        try {
            boolean isDark = isDark(activity);
            int color = isAmoled(activity) ? 0xFF000000 : activity.getColor(R.color.hcf_bg);
            activity.getWindow().setStatusBarColor(color);
            activity.getWindow().setNavigationBarColor(color);
            int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
            int i = !isDark ? systemUiVisibility | 8192 : systemUiVisibility & ~8192;
            activity.getWindow().getDecorView().setSystemUiVisibility(!isDark ? i | 16 : i & ~16);
        } catch (Throwable unused) {
        }
    }

    static boolean isDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK) == NIGHT_YES;
    }

    static boolean isAmoled(Context context) {
        return AMOLED.equals(mode(context));
    }

    static boolean isAutoForum(Context context) {
        return AUTO_FORUM.equals(mode(context));
    }

    static boolean isAutoPhone(Context context) {
        return AUTO_PHONE.equals(mode(context));
    }

    static String mode(Context context) {
        if (context == null) {
            return DARK;
        }
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("app_theme", DARK);
            if (!LEGACY_SYSTEM.equals(string)) {
                return (AUTO_FORUM.equals(string) || AUTO_PHONE.equals(string) || LIGHT.equals(string)
                        || DARK.equals(string) || AMOLED.equals(string)) ? string : DARK;
            }
            sharedPreferences.edit().putString("app_theme", AUTO_FORUM).apply();
            return AUTO_FORUM;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try {
                    sharedPreferences.edit().remove("app_theme").apply();
                } catch (Throwable unused2) {
                }
            }
            return DARK;
        }
    }

    static String label(Context context) {
        String mode = mode(context);
        return AUTO_PHONE.equals(mode) ? "Auto • Phone"
                : LIGHT.equals(mode) ? "Day (Light)"
                : DARK.equals(mode) ? "Night (Dark)"
                : AMOLED.equals(mode) ? "AMOLED Black"
                : "Auto • Forum";
    }

    static boolean updateForumAutoTheme(Context context, String str) {
        if (context == null || !AUTO_FORUM.equals(mode(context))) {
            return false;
        }
        String lowerCase = str == null ? "" : str.trim().toLowerCase();
        String str2 = FORUM_DARK;
        if (!"dark".equals(lowerCase) && !"night".equals(lowerCase) && !"2".equals(lowerCase)) {
            str2 = FORUM_LIGHT;
            if (!"light".equals(lowerCase) && !"day".equals(lowerCase) && !"1".equals(lowerCase)) {
                if (!FORUM_AUTO.equals(lowerCase) && !LEGACY_SYSTEM.equals(lowerCase)
                        && !"phone".equals(lowerCase) && !"0".equals(lowerCase)) {
                    return false;
                }
                str2 = FORUM_AUTO;
            }
        }
        if (str2.equals(forumAutoTheme(context))) {
            return false;
        }
        try {
            context.getSharedPreferences("hcf_app", 0).edit()
                    .putString("forum_auto_theme", str2)
                    .putLong("forum_auto_theme_updated_at", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Throwable unused) {
            return false;
        }
    }

    static String forumAutoTheme(Context context) {
        if (context == null) {
            return FORUM_AUTO;
        }
        SharedPreferences sharedPreferences = null;
        try {
            sharedPreferences = context.getSharedPreferences("hcf_app", 0);
            String string = sharedPreferences.getString("forum_auto_theme", FORUM_AUTO);
            return (FORUM_LIGHT.equals(string) || FORUM_DARK.equals(string)) ? string : FORUM_AUTO;
        } catch (Throwable unused) {
            if (sharedPreferences != null) {
                try {
                    sharedPreferences.edit().remove("forum_auto_theme").apply();
                } catch (Throwable unused2) {
                }
            }
            return FORUM_AUTO;
        }
    }

    static String autoSourceLabel(Context context) {
        String mode = mode(context);
        if (AUTO_PHONE.equals(mode)) {
            return resolvedNightMode(context) == NIGHT_YES ? "Auto • Phone Dark" : "Auto • Phone Light";
        }
        if (!AUTO_FORUM.equals(mode)) {
            return label(context);
        }
        String forumAutoTheme = forumAutoTheme(context);
        return FORUM_DARK.equals(forumAutoTheme) ? "Auto • Forum Dark"
                : FORUM_LIGHT.equals(forumAutoTheme) ? "Auto • Forum Light"
                : resolvedNightMode(context) == NIGHT_YES
                ? "Auto • Forum Auto → Phone Dark"
                : "Auto • Forum Auto → Phone Light";
    }

    static String next(String str) {
        return (AUTO_FORUM.equals(str) || LEGACY_SYSTEM.equals(str)) ? AUTO_PHONE
                : AUTO_PHONE.equals(str) ? LIGHT
                : LIGHT.equals(str) ? DARK
                : DARK.equals(str) ? AMOLED
                : AUTO_FORUM;
    }

    private ThemeManager() {
    }
}


// ---- ThemedActivity.java ----
/* loaded from: classes.dex */
abstract class ThemedActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String appliedThemeSignature;
    private SharedPreferences themePrefs;
    private boolean themeRecreatePending;

    ThemedActivity() {
    }

    @Override // android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    protected void attachBaseContext(Context context) {
        Context wrapped = context;
        try {
            Context candidate = ThemeManager.wrap(context);
            if (candidate != null) wrapped = candidate;
        } catch (Throwable unused) {
        }
        super.attachBaseContext(wrapped);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        try {
            ThemeManager.prepare(this);
        } catch (Throwable unused) {
        }
        super.onCreate(bundle);
        try {
            AppDomainRouter.installAddressBarInflater(this);
        } catch (Throwable unused2) {
        }
        try {
            ThemeManager.applySystemBars(this);
        } catch (Throwable unused3) {
        }
        try {
            this.appliedThemeSignature = ThemeManager.signature(this);
        } catch (Throwable unused4) {
            this.appliedThemeSignature = "auto_forum";
        }
        try {
            this.themePrefs = getSharedPreferences("hcf_app", 0);
        } catch (Throwable unused5) {
            this.themePrefs = null;
        }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        try {
            WelcomeScreenFitter.apply(this, view);
        } catch (Throwable ignored) {
        }
    }

    @Override // android.app.Activity
    protected void onStart() {
        super.onStart();
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try {
                sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            } catch (Throwable unused) {
            }
        }
        recreateForThemeIfNeeded();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        recreateForThemeIfNeeded();
    }

    @Override // android.app.Activity
    protected void onStop() {
        SharedPreferences sharedPreferences = this.themePrefs;
        if (sharedPreferences != null) {
            try {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
            } catch (Throwable unused) {
            }
        }
        super.onStop();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String str) {
        if ("app_theme".equals(str) || "forum_auto_theme".equals(str)) {
            recreateForThemeIfNeeded();
        }
    }

    private void recreateForThemeIfNeeded() {
        try {
            if (this.themeRecreatePending || isFinishing() || isDestroyed() || !ThemeManager.changedSince(this, this.appliedThemeSignature)) {
                return;
            }
            this.themeRecreatePending = true;
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public final void run() {
                    ThemedActivity.this.m210xc87fab7b();
                }
            }, 90L);
        } catch (Throwable unused) {
        }
    }

    /* synthetic */ void m210xc87fab7b() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            recreate();
        } catch (Throwable unused) {
            this.themeRecreatePending = false;
        }
    }
}

/** Welcome-only layout fitter. Keeps the first-run screen on one phone viewport. */
final class WelcomeScreenFitter {
    private WelcomeScreenFitter() {}

    static void apply(Activity activity, View root) {
        if (activity == null || root == null || !(activity instanceof HcfMainActivities.WelcomeActivity)) {
            return;
        }

        int screenHeightDp = activity.getResources().getConfiguration().screenHeightDp;
        if (screenHeightDp > 860) {
            return;
        }

        final float layoutScale = screenHeightDp <= 680 ? 0.68f
                : screenHeightDp <= 780 ? 0.78f
                : 0.90f;
        final float textScale = screenHeightDp <= 680 ? 0.84f
                : screenHeightDp <= 780 ? 0.91f
                : 0.96f;

        if (root instanceof ScrollView) {
            ScrollView scroll = (ScrollView) root;
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        root.post(new Runnable() {
            @Override
            public void run() {
                compact(root, layoutScale, textScale);
                root.requestLayout();
            }
        });
    }

    private static void compact(View view, float layoutScale, float textScale) {
        if (view == null) {
            return;
        }

        view.setPadding(
                scaled(view.getPaddingLeft(), layoutScale),
                scaled(view.getPaddingTop(), layoutScale),
                scaled(view.getPaddingRight(), layoutScale),
                scaled(view.getPaddingBottom(), layoutScale)
        );

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params.width > 0) {
                params.width = scaled(params.width, layoutScale);
            }
            if (params.height > 0) {
                params.height = scaled(params.height, layoutScale);
            }
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.leftMargin = scaled(margins.leftMargin, layoutScale);
                margins.topMargin = scaled(margins.topMargin, layoutScale);
                margins.rightMargin = scaled(margins.rightMargin, layoutScale);
                margins.bottomMargin = scaled(margins.bottomMargin, layoutScale);
            }
            view.setLayoutParams(params);
        }

        int minWidth = view.getMinimumWidth();
        int minHeight = view.getMinimumHeight();
        if (minWidth > 0) {
            view.setMinimumWidth(scaled(minWidth, layoutScale));
        }
        if (minHeight > 0) {
            view.setMinimumHeight(scaled(minHeight, layoutScale));
        }

        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize() * textScale);
            text.setLineSpacing(text.getLineSpacingExtra() * textScale, text.getLineSpacingMultiplier());
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                compact(group.getChildAt(i), layoutScale, textScale);
            }
        }
    }

    private static int scaled(int value, float scale) {
        return value == 0 ? 0 : Math.max(1, Math.round(value * scale));
    }
}


// ---- FaIcons.java ----
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


// ---- UiButtons.java ----
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


// ---- AppDomainEditText.java ----
/**
 * Address-bar EditText that preserves MainActivity's existing listener while
 * intercepting the local app.forum.harleytg.com/<item> namespace first.
 */
final class AppDomainEditText extends EditText {

    public AppDomainEditText(Context context) {
        super(context);
    }

    public AppDomainEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppDomainEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnEditorActionListener(final TextView.OnEditorActionListener listener) {
        super.setOnEditorActionListener((view, actionId, event) -> {
            if (isSubmitAction(actionId, event)) {
                Activity activity = findActivity(getContext());
                String raw = getText() == null ? "" : getText().toString().trim();

                if (activity != null && AppDomainRouter.handle(activity, raw)) {
                    hideKeyboard(activity);
                    clearFocus();
                    restoreDisplayedForumUrl(activity);
                    return true;
                }
            }

            return listener != null && listener.onEditorAction(view, actionId, event);
        });
    }

    private boolean isSubmitAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            return true;
        }

        return event != null
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }

    private void hideKeyboard(Activity activity) {
        try {
            InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) {
                input.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void restoreDisplayedForumUrl(final Activity activity) {
        post(() -> {
            try {
                WebView webView = activity.findViewById(R.id.webView);
                if (webView == null) {
                    return;
                }

                String url = webView.getUrl();
                if (url != null && !url.trim().isEmpty()) {
                    setText(url);
                    setSelection(length());
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}


final class UrlBackButton extends android.widget.ImageButton {
    UrlBackButton(android.content.Context c){super(c);init();}
    UrlBackButton(android.content.Context c,android.util.AttributeSet a){super(c,a);init();}
    UrlBackButton(android.content.Context c,android.util.AttributeSet a,int d){super(c,a,d);init();}
    private void init(){setOnClickListener(v->back());}
    private void back(){android.app.Activity a=act(getContext());if(a==null)return;android.webkit.WebView w=a.findViewById(R.id.webView);if(w!=null&&w.canGoBack()){w.goBack();try{AppLogger.info(a,"url_back",AppLogger.safeUrl(w.getUrl()));}catch(Throwable ignored){}return;}android.widget.Toast.makeText(a,"No previous forum page.",android.widget.Toast.LENGTH_SHORT).show();}
    private android.app.Activity act(android.content.Context c){android.content.Context x=c;while(x instanceof android.content.ContextWrapper){if(x instanceof android.app.Activity)return(android.app.Activity)x;x=((android.content.ContextWrapper)x).getBaseContext();}return x instanceof android.app.Activity?(android.app.Activity)x:null;}
}
