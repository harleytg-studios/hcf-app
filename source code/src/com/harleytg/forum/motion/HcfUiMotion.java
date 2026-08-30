package com.harleytg.forum.dev;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Advanced native motion engine for Harley's Clan Forum.
 *
 * Core rules:
 * - App Settings is intentionally excluded. HcfSettingsStableUi owns Settings so
 *   category/subsetting rendering cannot fight the global animation layer.
 * - The forum WebView and every ancestor containing it are excluded from entrance motion.
 * - Visibility transitions are observed rather than owned; navigation/business state
 *   always remains with the feature that created the view.
 * - Press feedback uses StateListAnimator when possible, so no touch/click listener is
 *   replaced by the motion system.
 * - Layout callbacks are coalesced to one motion pass per display frame.
 * - Motion density adapts to Android animator settings, HCF performance profile,
 *   low-RAM devices and battery saver.
 */
public final class HcfUiMotion {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";
    private static final String MAIN_ACTIVITY =
            "com.harleytg.forum.dev.HcfForum$MainActivity";
    private static final String STARTUP_ACTIVITY =
            "com.harleytg.forum.dev.HcfUI$StartupActivity";
    private static final String STARTUP_MAIN_ACTIVITY =
            "com.harleytg.forum.dev.HcfUI$StartupMainActivity";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>
            OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> PASS_PENDING = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> ACTIVITY_ENTERED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, MotionProfile> PROFILES = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Integer> TREE_SIGNATURES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PRESS_ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> LAST_VISIBLE = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> LAST_TEXT_HASH = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SEEN_ROOT_CHILDREN = new WeakHashMap<>();

    private static boolean registered;

    private HcfUiMotion() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context appContext = context.getApplicationContext();
            if (registered || !(appContext instanceof Application)) return true;

            registered = true;
            ((Application) appContext).registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity activity, Bundle state) {
                            install(activity);
                        }
                        @Override public void onActivityStarted(Activity activity) {}
                        @Override public void onActivityResumed(Activity activity) {
                            install(activity);
                        }
                        @Override public void onActivityPaused(Activity activity) {
                            remove(activity, false);
                        }
                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                        @Override public void onActivityDestroyed(Activity activity) {
                            remove(activity, true);
                        }
                    });
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

    private static void install(final Activity activity) {
        if (activity == null || activity.isFinishing() || isSettings(activity)) return;
        schedule(activity);

        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            final View root = activity.getWindow() == null
                    ? null : activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            schedule(activity);
                        }
                    };
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    /** Coalesce layout-heavy UI changes into one animation inspection per frame. */
    private static void schedule(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.getWindow() == null
                || isSettings(activity)) return;
        synchronized (PASS_PENDING) {
            if (Boolean.TRUE.equals(PASS_PENDING.get(activity))) return;
            PASS_PENDING.put(activity, Boolean.TRUE);
        }

        final View root = activity.getWindow().getDecorView();
        if (root == null) {
            synchronized (PASS_PENDING) { PASS_PENDING.remove(activity); }
            return;
        }
        root.postOnAnimation(new Runnable() {
            @Override public void run() {
                synchronized (PASS_PENDING) { PASS_PENDING.remove(activity); }
                if (activity.isFinishing() || activity.isDestroyed() || isSettings(activity)) return;
                apply(activity);
            }
        });
    }

    private static void remove(Activity activity, boolean destroyed) {
        if (activity == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (OBSERVERS) { listener = OBSERVERS.remove(activity); }
        if (listener != null && activity.getWindow() != null) {
            View root = activity.getWindow().getDecorView();
            if (root != null) {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            }
        }
        synchronized (PASS_PENDING) { PASS_PENDING.remove(activity); }
        if (destroyed) {
            synchronized (ACTIVITY_ENTERED) { ACTIVITY_ENTERED.remove(activity); }
            synchronized (PROFILES) { PROFILES.remove(activity); }
        }
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.getWindow() == null || isSettings(activity)) return;
        MotionProfile profile = profile(activity);

        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot != null) attachInteractiveTree(contentRoot, profile);
        attachChrome(activity, profile);

        boolean firstEntrance;
        synchronized (ACTIVITY_ENTERED) {
            firstEntrance = !Boolean.TRUE.equals(ACTIVITY_ENTERED.get(activity));
            if (firstEntrance) ACTIVITY_ENTERED.put(activity, Boolean.TRUE);
        }

        if (firstEntrance) animateActivityEntrance(activity, contentRoot, profile);
        animateNewNativeRootChildren(contentRoot, firstEntrance, profile);
        animateNamedSurfaces(activity, profile);
        animateDrawerContents(activity, profile);
        animateStatusTextChanges(activity, profile);
    }

    private static void animateActivityEntrance(
            Activity activity, View contentRoot, MotionProfile profile) {
        if (!profile.enabled || isStartup(activity)) return;

        if (MAIN_ACTIVITY.equals(activity.getClass().getName())) {
            View topBar = findNamedView(activity, "topAppBar");
            View urlBar = findNamedView(activity, "urlBar");
            if (isEffectivelyVisible(topBar)) {
                UiMotion.enterView(topBar, -profile.dpDistance(4),
                        profile.duration(210L), 0L);
            }
            if (isEffectivelyVisible(urlBar)) {
                UiMotion.enterView(urlBar, -profile.dpDistance(3),
                        profile.duration(225L), profile.duration(28L));
            }
            return;
        }

        if (!(contentRoot instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) contentRoot;
        if (root.getChildCount() == 0) return;
        View page = root.getChildAt(0);
        if (!isEffectivelyVisible(page) || containsWebView(page)) return;
        UiMotion.enterView(page, profile.dpDistance(7),
                profile.duration(235L), 0L);
    }

    /** Animate native pages/overlays inserted after Activity creation exactly once. */
    private static void animateNewNativeRootChildren(
            View contentRoot, boolean firstPass, MotionProfile profile) {
        if (!(contentRoot instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) contentRoot;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == null) continue;
            boolean seen;
            synchronized (SEEN_ROOT_CHILDREN) {
                seen = Boolean.TRUE.equals(SEEN_ROOT_CHILDREN.put(child, Boolean.TRUE));
            }
            if (seen || firstPass || !profile.enabled || !isEffectivelyVisible(child)
                    || child instanceof WebView || containsWebView(child)) continue;
            UiMotion.enterView(child, profile.dpDistance(5),
                    profile.duration(205L), 0L);
        }
    }

    private static void attachChrome(Activity activity, MotionProfile profile) {
        attachPressState(activity.findViewById(R.id.drawerButton), 0.972f, profile);
        attachPressState(activity.findViewById(R.id.headerNotificationsButton), 0.972f, profile);
        attachPressState(activity.findViewById(R.id.urlBackButton), 0.972f, profile);
        attachPressState(activity.findViewById(R.id.reloadButton), 0.972f, profile);
        attachPressState(activity.findViewById(R.id.copyUrlButton), 0.972f, profile);
        attachPressState(activity.findViewById(R.id.urlHomeButton), 0.972f, profile);

        View logo = activity.findViewById(R.id.appHeaderLogo);
        if (logo != null && logo.isClickable()) attachPressState(logo, 0.986f, profile);
    }

    private static void animateNamedSurfaces(Activity activity, MotionProfile profile) {
        reveal(activity, "welcomeBanner", Effect.SLIDE_FROM_TOP, 190L, 4, profile);
        reveal(activity, "connectionBanner", Effect.SLIDE_FROM_TOP, 190L, 4, profile);
        reveal(activity, "safeModeBanner", Effect.SLIDE_FROM_TOP, 195L, 4, profile);
        reveal(activity, "errorShell", Effect.RISE, 235L, 8, profile);
        reveal(activity, "statusOverlay", Effect.FADE, 180L, 0, profile);
        reveal(activity, "bottomNav", Effect.RISE, 205L, 5, profile);
        reveal(activity, "pageProgress", Effect.FADE, 135L, 0, profile);
        reveal(activity, "liveStatusBadge", Effect.POP, 180L, 0, profile);
        reveal(activity, "headerNotificationCountBadge", Effect.POP, 175L, 0, profile);
        reveal(activity, "hostBadge", Effect.POP, 165L, 0, profile);
    }

    private static void reveal(Activity activity, String idName, Effect effect,
                               long baseDurationMs, int distanceDp, MotionProfile profile) {
        View view = findNamedView(activity, idName);
        if (view == null) return;
        boolean shown = isEffectivelyVisible(view);
        boolean justShown = transitionedToShown(view, shown);
        if (!justShown || !profile.enabled) return;

        long duration = profile.duration(baseDurationMs);
        switch (effect) {
            case FADE:
                UiMotion.fadeIn(view, duration, 0L);
                break;
            case POP:
                if (!view.isClickable()) UiMotion.popIn(view, duration);
                else UiMotion.fadeIn(view, duration, 0L);
                break;
            case SLIDE_FROM_TOP:
                UiMotion.enterView(view, -profile.dpDistance(distanceDp), duration, 0L);
                break;
            case RISE:
            default:
                UiMotion.enterView(view, profile.dpDistance(distanceDp), duration, 0L);
                break;
        }
    }

    private static void animateDrawerContents(Activity activity, MotionProfile profile) {
        View drawer = findNamedView(activity, "drawerPanel");
        if (!(drawer instanceof ViewGroup)) return;
        boolean shown = isEffectivelyVisible(drawer);
        boolean justShown = transitionedToShown(drawer, shown);
        if (!justShown || !profile.enabled) return;

        UiMotion.staggerChildren((ViewGroup) drawer,
                profile.dpDistance(3), profile.duration(185L),
                profile.duration(13L), profile.maxStagger(95L));
    }

    private static void animateStatusTextChanges(Activity activity, MotionProfile profile) {
        emphasizeTextChange(activity, "headerNotificationCountBadge", true, profile);
        emphasizeTextChange(activity, "liveStatusBadge", true, profile);
        emphasizeTextChange(activity, "hostBadge", true, profile);

        if (!isStartup(activity)) {
            emphasizeTextChange(activity, "statusTitle", false, profile);
            emphasizeTextChange(activity, "statusSubtitle", false, profile);
            emphasizeTextChange(activity, "errorStatusText", false, profile);
        }
    }

    private static void emphasizeTextChange(
            Activity activity, String idName, boolean pop, MotionProfile profile) {
        View view = findNamedView(activity, idName);
        if (!(view instanceof TextView) || !isEffectivelyVisible(view)) return;
        TextView textView = (TextView) view;
        CharSequence value = textView.getText();
        int hash = value == null ? 0 : value.toString().hashCode();
        Integer previous;
        synchronized (LAST_TEXT_HASH) {
            previous = LAST_TEXT_HASH.put(view, Integer.valueOf(hash));
        }
        if (previous == null || previous.intValue() == hash || !profile.enabled) return;

        if (pop && !view.isClickable()) UiMotion.pulse(view, profile.duration(220L));
        else UiMotion.softEmphasis(view, profile.duration(185L));
    }

    /**
     * Add non-invasive press feedback to native controls. Existing StateListAnimator
     * is respected so themed controls keep their own elevation/pressed behavior.
     */
    private static void attachInteractiveTree(View view, MotionProfile profile) {
        if (view == null || view instanceof WebView) return;

        if (view instanceof ImageButton) {
            attachPressState(view, 0.972f, profile);
        } else if (view instanceof CompoundButton) {
            attachPressState(view, 0.988f, profile);
        } else if (view instanceof Button) {
            attachPressState(view, 0.985f, profile);
        } else if (!(view instanceof EditText)
                && view instanceof TextView && view.isClickable()) {
            attachPressState(view, 0.992f, profile);
        } else if (view instanceof LinearLayout && view.isClickable()) {
            attachPressState(view, 0.994f, profile);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        int signature = childSignature(group);
        synchronized (TREE_SIGNATURES) {
            Integer previous = TREE_SIGNATURES.get(group);
            if (previous != null && previous.intValue() == signature) return;
            TREE_SIGNATURES.put(group, Integer.valueOf(signature));
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            attachInteractiveTree(group.getChildAt(i), profile);
        }
    }

    private static void attachPressState(View view, float fullScale, MotionProfile profile) {
        if (view == null || !view.isClickable()) return;
        synchronized (PRESS_ATTACHED) {
            if (PRESS_ATTACHED.containsKey(view)) return;
            PRESS_ATTACHED.put(view, Boolean.TRUE);
        }
        if (!profile.enabled) return;
        if (view.getStateListAnimator() != null) return;
        UiMotion.attachPressStateAnimator(view, profile.pressScale(fullScale),
                profile.duration(78L), profile.duration(145L));
    }

    private static int childSignature(ViewGroup group) {
        if (group == null) return 0;
        int result = 17;
        int count = group.getChildCount();
        result = 31 * result + count;
        for (int i = 0; i < count; i++) {
            result = 31 * result + System.identityHashCode(group.getChildAt(i));
        }
        return result;
    }

    private static boolean transitionedToShown(View view, boolean shown) {
        if (view == null) return false;
        Boolean previous;
        synchronized (LAST_VISIBLE) {
            previous = LAST_VISIBLE.put(view, Boolean.valueOf(shown));
        }
        return shown && !Boolean.TRUE.equals(previous);
    }

    /** Temporary animation alpha does not change logical visibility. */
    private static boolean isEffectivelyVisible(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        View current = view;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE) return false;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    private static boolean containsWebView(View view) {
        if (view == null) return false;
        if (view instanceof WebView) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsWebView(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static View findNamedView(Activity activity, String idName) {
        if (activity == null || idName == null || idName.isEmpty()) return null;
        int id = activity.getResources().getIdentifier(idName, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private static boolean isSettings(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean isStartup(Activity activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        return STARTUP_ACTIVITY.equals(name) || STARTUP_MAIN_ACTIVITY.equals(name);
    }

    private static MotionProfile profile(Activity activity) {
        synchronized (PROFILES) {
            MotionProfile cached = PROFILES.get(activity);
            if (cached != null && !cached.shouldRefresh()) return cached;
        }
        MotionProfile created = MotionProfile.resolve(activity);
        synchronized (PROFILES) { PROFILES.put(activity, created); }
        return created;
    }

    private enum Effect { FADE, RISE, SLIDE_FROM_TOP, POP }

    /** Motion token set resolved per Activity and refreshed periodically. */
    private static final class MotionProfile {
        final boolean enabled;
        final float durationScale;
        final float distanceScale;
        final float pressScaleStrength;
        final long resolvedAt;

        MotionProfile(boolean enabled, float durationScale,
                      float distanceScale, float pressScaleStrength) {
            this.enabled = enabled;
            this.durationScale = durationScale;
            this.distanceScale = distanceScale;
            this.pressScaleStrength = pressScaleStrength;
            this.resolvedAt = android.os.SystemClock.uptimeMillis();
        }

        static MotionProfile resolve(Activity activity) {
            if (!UiMotion.animationsEnabled()) {
                return new MotionProfile(false, 0.0f, 0.0f, 0.0f);
            }

            boolean lowRam = false;
            boolean powerSaver = false;
            try {
                ActivityManager manager = (ActivityManager)
                        activity.getSystemService(Context.ACTIVITY_SERVICE);
                lowRam = manager != null && manager.isLowRamDevice();
            } catch (Throwable ignored) {}
            try {
                PowerManager power = (PowerManager)
                        activity.getSystemService(Context.POWER_SERVICE);
                powerSaver = power != null && power.isPowerSaveMode();
            } catch (Throwable ignored) {}

            String saved = "";
            try {
                saved = activity.getSharedPreferences("hcf_app", 0)
                        .getString("performance_profile", "");
            } catch (Throwable ignored) {}

            float duration = 1.0f;
            float distance = 1.0f;
            float press = 1.0f;
            if ("balanced".equals(saved)) {
                duration = 0.90f;
                distance = 0.85f;
                press = 0.85f;
            } else if ("performance".equals(saved)) {
                duration = 0.76f;
                distance = 0.64f;
                press = 0.70f;
            }

            if (lowRam || powerSaver) {
                duration = Math.min(duration, 0.68f);
                distance = Math.min(distance, 0.55f);
                press = Math.min(press, 0.62f);
            }
            return new MotionProfile(true, duration, distance, press);
        }

        boolean shouldRefresh() {
            return android.os.SystemClock.uptimeMillis() - resolvedAt > 4000L;
        }

        long duration(long base) {
            if (!enabled) return 0L;
            return Math.max(1L, Math.round(base * durationScale));
        }

        int dpDistance(int dp) {
            if (!enabled || dp == 0) return 0;
            int sign = dp < 0 ? -1 : 1;
            int absolute = Math.abs(dp);
            return sign * Math.max(1, Math.round(absolute * distanceScale));
        }

        long maxStagger(long base) {
            return Math.max(1L, Math.round(base * durationScale));
        }

        float pressScale(float requested) {
            float delta = 1.0f - requested;
            return 1.0f - (delta * pressScaleStrength);
        }
    }
}

/** Shared property-animation primitives retained for compatibility with runtime helpers. */
final class UiMotion {
    private static final TimeInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f);
    private static final TimeInterpolator FAST_OUT =
            new PathInterpolator(0.40f, 0.0f, 1.0f, 1.0f);
    private static final TimeInterpolator STANDARD =
            new PathInterpolator(0.20f, 0.0f, 0.20f, 1.0f);

    private static final WeakHashMap<View, WeakReference<Animator>> RUNNING =
            new WeakHashMap<>();

    private UiMotion() {}

    static boolean animationsEnabled() {
        try { return ValueAnimator.areAnimatorsEnabled(); }
        catch (Throwable ignored) { return true; }
    }

    static void attachPressScale(final View view, final float scale,
                                 final long pressInMs, final long releaseMs) {
        if (view == null) return;
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                if (v == null || event == null || !v.isEnabled() || !animationsEnabled()) return false;
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().cancel();
                    v.animate().scaleX(scale).scaleY(scale)
                            .setDuration(Math.max(1L, pressInMs))
                            .setInterpolator(FAST_OUT).start();
                } else if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL
                        || action == android.view.MotionEvent.ACTION_OUTSIDE) {
                    v.animate().cancel();
                    v.animate().scaleX(1.0f).scaleY(1.0f)
                            .setDuration(Math.max(1L, releaseMs))
                            .setInterpolator(EMPHASIZED_DECELERATE).start();
                }
                return false;
            }
        });
    }

    static void attachPressStateAnimator(View view, float pressedScale,
                                         long pressMs, long releaseMs) {
        if (view == null || !animationsEnabled()) return;
        float safeScale = Math.max(0.94f, Math.min(1.0f, pressedScale));

        AnimatorSet pressed = new AnimatorSet();
        pressed.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, safeScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, safeScale));
        pressed.setDuration(Math.max(1L, pressMs));
        pressed.setInterpolator(FAST_OUT);

        AnimatorSet released = new AnimatorSet();
        released.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f));
        released.setDuration(Math.max(1L, releaseMs));
        released.setInterpolator(EMPHASIZED_DECELERATE);

        StateListAnimator states = new StateListAnimator();
        states.addState(new int[] { android.R.attr.state_enabled, android.R.attr.state_pressed }, pressed);
        states.addState(new int[] {}, released);
        view.setStateListAnimator(states);
    }

    static void enterView(View view, int offsetDp, long durationMs, long delayMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            normalize(view);
            return;
        }
        cancelEntrance(view);

        int offset = dp(view.getContext(), offsetDp);
        view.setAlpha(0.0f);
        view.setTranslationY(offset);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, offset, 0.0f));
        set.setStartDelay(Math.max(0L, delayMs));
        set.setDuration(Math.max(1L, durationMs));
        set.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, set, true);
    }

    static void fadeIn(View view, long durationMs, long delayMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            view.setAlpha(1.0f);
            return;
        }
        cancelEntrance(view);
        view.setTranslationY(0.0f);
        view.setAlpha(0.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
        alpha.setStartDelay(Math.max(0L, delayMs));
        alpha.setDuration(Math.max(1L, durationMs));
        alpha.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, alpha, true);
    }

    static void popIn(View view) {
        popIn(view, 165L);
    }

    static void popIn(View view, long durationMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            normalize(view);
            return;
        }
        cancelEntrance(view);
        view.setAlpha(0.0f);
        view.setScaleX(0.955f);
        view.setScaleY(0.955f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.955f, 1.0f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.955f, 1.0f));
        set.setDuration(Math.max(1L, durationMs));
        set.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, set, true);
    }

    static void pulse(View view, long durationMs) {
        if (view == null || !animationsEnabled()) return;
        cancelEntrance(view);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1.0f, 1.055f, 1.0f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.0f, 1.055f, 1.0f));
        set.setDuration(Math.max(1L, durationMs));
        set.setInterpolator(STANDARD);
        rememberAndStart(view, set, false);
    }

    static void softEmphasis(View view, long durationMs) {
        if (view == null || !animationsEnabled()) return;
        cancelEntrance(view);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.72f, 1.0f);
        alpha.setDuration(Math.max(1L, durationMs));
        alpha.setInterpolator(STANDARD);
        rememberAndStart(view, alpha, false);
    }

    static void staggerChildren(ViewGroup parent, int riseDp, long durationMs,
                                long staggerMs, long maxDelayMs) {
        if (parent == null || !animationsEnabled()) return;
        int animatedIndex = 0;
        int rise = dp(parent.getContext(), riseDp);
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE
                    || child instanceof WebView || containsWebViewChild(child)) continue;

            long delay = Math.min(maxDelayMs, Math.max(0L, staggerMs) * animatedIndex);
            cancelEntrance(child);
            child.setAlpha(0.0f);
            child.setTranslationY(rise);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(child, View.ALPHA, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, rise, 0.0f));
            set.setStartDelay(delay);
            set.setDuration(Math.max(1L, durationMs));
            set.setInterpolator(EMPHASIZED_DECELERATE);
            rememberAndStart(child, set, true);
            animatedIndex++;
        }
    }

    static void fadeInChildren(ViewGroup parent, long staggerMs) {
        staggerChildren(parent, 3, 180L, staggerMs, 90L);
    }

    static void fadeInChildren(ViewGroup parent, long staggerMs, long durationMs, int riseDp) {
        staggerChildren(parent, riseDp, durationMs, staggerMs, 90L);
    }

    static void fadeInChildrenAlpha(ViewGroup parent, long staggerMs, long durationMs) {
        if (parent == null) return;
        int index = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE
                    || child instanceof WebView || containsWebViewChild(child)) continue;
            fadeIn(child, durationMs, Math.min(90L, Math.max(0L, staggerMs) * index));
            index++;
        }
    }

    static void cancelEntrance(View view) {
        if (view == null) return;
        WeakReference<Animator> reference;
        synchronized (RUNNING) { reference = RUNNING.remove(view); }
        Animator animator = reference == null ? null : reference.get();
        if (animator != null) animator.cancel();
    }

    private static void rememberAndStart(final View view, final Animator animator,
                                         boolean useHardwareLayer) {
        if (view == null || animator == null) return;
        synchronized (RUNNING) { RUNNING.put(view, new WeakReference<>(animator)); }

        final int oldLayerType = view.getLayerType();
        final boolean promoted = useHardwareLayer && view.isAttachedToWindow()
                && oldLayerType == View.LAYER_TYPE_NONE;
        if (promoted) {
            try { view.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
            catch (Throwable ignored) {}
        }

        animator.addListener(new AnimatorListenerAdapter() {
            private boolean finished;
            private void finish() {
                if (finished) return;
                finished = true;
                synchronized (RUNNING) {
                    WeakReference<Animator> ref = RUNNING.get(view);
                    if (ref != null && ref.get() == animator) RUNNING.remove(view);
                }
                if (promoted && view.getLayerType() == View.LAYER_TYPE_HARDWARE) {
                    try { view.setLayerType(oldLayerType, null); }
                    catch (Throwable ignored) {}
                }
            }
            @Override public void onAnimationEnd(Animator animation) { finish(); }
            @Override public void onAnimationCancel(Animator animation) { finish(); }
        });
        animator.start();
    }

    private static void normalize(View view) {
        if (view == null) return;
        cancelEntrance(view);
        view.setAlpha(1.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
    }

    private static boolean containsWebViewChild(View view) {
        if (view instanceof WebView) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsWebViewChild(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
