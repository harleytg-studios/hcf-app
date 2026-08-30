package com.harleytg.forum.dev;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Full-system native motion plus Settings accordion coordination.
 *
 * Design rules:
 * - never animate or traverse the forum WebView;
 * - entrance alpha/translation is kept separate from press-scale feedback;
 * - motion passes are frame-throttled so layout-heavy screens do not continuously
 *   rescan the whole native hierarchy;
 * - Android's disabled-animation preference is respected;
 * - Settings keeps one subsetting open at a time and starts normal navigation closed.
 */
public final class HcfUiMotion {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>
            ACTIVITY_OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, String> LAST_SETTINGS_SECTION = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> ACTIVITY_ENTERED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> MOTION_PASS_PENDING = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MOTION_ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> LAST_EFFECTIVE_VISIBILITY = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Integer> TREE_SIGNATURES = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewGroup> ACCORDION_HEADERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> ACCORDION_WILL_OPEN = new WeakHashMap<>();

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
                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {
                            install(activity);
                        }

                        @Override public void onActivityStarted(Activity activity) {}

                        @Override
                        public void onActivityResumed(Activity activity) {
                            install(activity);
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {
                            removeActivityObserver(activity, false);
                        }

                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            removeActivityObserver(activity, true);
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
        if (activity == null || activity.isFinishing()) return;

        attachChrome(activity);
        scheduleMotionPass(activity);

        synchronized (ACTIVITY_OBSERVERS) {
            if (ACTIVITY_OBSERVERS.containsKey(activity)) return;
            final View root = activity.getWindow() == null
                    ? null : activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            scheduleMotionPass(activity);
                        }
                    };
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(listener);
            ACTIVITY_OBSERVERS.put(activity, listener);
        }
    }

    /** Coalesces repeated global-layout callbacks into at most one motion pass per frame. */
    private static void scheduleMotionPass(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.getWindow() == null) return;
        synchronized (MOTION_PASS_PENDING) {
            if (Boolean.TRUE.equals(MOTION_PASS_PENDING.get(activity))) return;
            MOTION_PASS_PENDING.put(activity, Boolean.TRUE);
        }

        final View root = activity.getWindow().getDecorView();
        if (root == null) {
            synchronized (MOTION_PASS_PENDING) { MOTION_PASS_PENDING.remove(activity); }
            return;
        }
        root.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                synchronized (MOTION_PASS_PENDING) { MOTION_PASS_PENDING.remove(activity); }
                if (activity.isFinishing() || activity.isDestroyed()) return;
                attachChrome(activity);
                applySystemMotion(activity);
                if (isSettingsActivity(activity)) applySettingsMotion(activity);
            }
        });
    }

    private static void removeActivityObserver(Activity activity, boolean destroyed) {
        if (activity == null) return;

        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (ACTIVITY_OBSERVERS) {
            listener = ACTIVITY_OBSERVERS.remove(activity);
        }
        if (listener != null && activity.getWindow() != null) {
            View root = activity.getWindow().getDecorView();
            if (root != null) {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            }
        }

        synchronized (MOTION_PASS_PENDING) {
            MOTION_PASS_PENDING.remove(activity);
        }

        // Returning to a Settings category must start with every subsection closed.
        synchronized (LAST_SETTINGS_SECTION) {
            LAST_SETTINGS_SECTION.remove(activity);
        }

        if (destroyed) {
            synchronized (ACTIVITY_ENTERED) {
                ACTIVITY_ENTERED.remove(activity);
            }
        }
    }

    private static void attachChrome(Activity activity) {
        if (activity == null) return;
        attachPressScale(activity.findViewById(R.id.drawerButton));
        attachPressScale(activity.findViewById(R.id.headerNotificationsButton));
        attachPressScale(activity.findViewById(R.id.urlBackButton));
        attachPressScale(activity.findViewById(R.id.reloadButton));
        attachPressScale(activity.findViewById(R.id.copyUrlButton));
        attachPressScale(activity.findViewById(R.id.urlHomeButton));

        View logo = activity.findViewById(R.id.appHeaderLogo);
        if (logo != null && logo.isClickable()) {
            attachPressScale(logo, 0.990f, 70L, 120L);
        }
    }

    /** Applies low-conflict native motion throughout every app Activity. */
    private static void applySystemMotion(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;

        View contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot != null) attachInteractiveTree(contentRoot);

        boolean firstEntrance;
        synchronized (ACTIVITY_ENTERED) {
            firstEntrance = !Boolean.TRUE.equals(ACTIVITY_ENTERED.get(activity));
            if (firstEntrance) ACTIVITY_ENTERED.put(activity, Boolean.TRUE);
        }

        if (firstEntrance && UiMotion.animationsEnabled()) {
            if (isSettingsActivity(activity)) {
                animateSettingsHeaderEntrance(activity);
            } else {
                View topBar = findNamedView(activity, "topAppBar");
                View urlBar = findNamedView(activity, "urlBar");
                if (topBar != null || urlBar != null) {
                    if (topBar != null) UiMotion.enterView(topBar, -3, 175L, 0L);
                    if (urlBar != null) UiMotion.enterView(urlBar, -3, 190L, 22L);

                    View startup = findNamedView(activity, "startupStateContainer");
                    if (startup != null && isEffectivelyVisible(startup)) {
                        UiMotion.enterView(startup, 4, 205L, 38L);
                    }
                } else if (contentRoot instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) contentRoot;
                    if (group.getChildCount() > 0) {
                        View page = group.getChildAt(0);
                        // Do not move an ancestor containing the forum WebView either.
                        if (!(page instanceof WebView) && !containsWebView(page)) {
                            UiMotion.enterView(page, 5, 205L, 0L);
                        }
                    }
                }
            }
        }

        // Animate only when these native surfaces transition hidden -> visible.
        animateDrawerIfShown(activity);
        animateTransientIfShown(activity, "welcomeBanner", -4, 185L);
        animateTransientIfShown(activity, "errorShell", 5, 205L);
        animateTransientIfShown(activity, "bottomNav", 4, 190L);
        animateTransientIfShown(activity, "statusOverlay", 4, 180L);
        animateTransientIfShown(activity, "safeModeBanner", -3, 185L);
        animateTransientIfShown(activity, "connectionBanner", -3, 185L);
        animateFadeIfShown(activity, "pageProgress", 140L);
        animatePopIfShown(activity, "liveStatusBadge");
        animatePopIfShown(activity, "headerNotificationCountBadge");
    }

    private static void animateSettingsHeaderEntrance(Activity activity) {
        View title = readViewField(activity, "headerTitleView");
        View subtitle = readViewField(activity, "headerSubtitleView");
        View back = readViewField(activity, "headerBackButton");
        if (back != null) UiMotion.enterView(back, -2, 160L, 0L);
        if (title != null) UiMotion.enterView(title, -2, 180L, 12L);
        if (subtitle != null) UiMotion.enterView(subtitle, -2, 190L, 28L);
    }

    private static void animateDrawerIfShown(Activity activity) {
        View drawer = findNamedView(activity, "drawerPanel");
        if (!(drawer instanceof ViewGroup)) return;
        boolean shown = isEffectivelyVisible(drawer);
        boolean justShown = visibilityTransitionedToShown(drawer, shown);
        if (!justShown) return;

        // Leave the drawer panel's own slide animation alone. Only its native
        // contents receive a short stagger so there is no translation conflict.
        UiMotion.fadeInChildren((ViewGroup) drawer, 12L, 175L, 3);
    }

    private static void animateTransientIfShown(
            Activity activity, String idName, int riseDp, long durationMs) {
        View view = findNamedView(activity, idName);
        if (view == null) return;
        boolean shown = isEffectivelyVisible(view);
        if (visibilityTransitionedToShown(view, shown)) {
            UiMotion.enterView(view, riseDp, durationMs, 0L);
        }
    }

    private static void animateFadeIfShown(Activity activity, String idName, long durationMs) {
        View view = findNamedView(activity, idName);
        if (view == null) return;
        boolean shown = isEffectivelyVisible(view);
        if (visibilityTransitionedToShown(view, shown)) UiMotion.fadeIn(view, durationMs, 0L);
    }

    private static void animatePopIfShown(Activity activity, String idName) {
        View view = findNamedView(activity, idName);
        if (view == null) return;
        boolean shown = isEffectivelyVisible(view);
        if (visibilityTransitionedToShown(view, shown)) UiMotion.popIn(view);
    }

    private static boolean visibilityTransitionedToShown(View view, boolean shown) {
        if (view == null) return false;
        Boolean previous;
        synchronized (LAST_EFFECTIVE_VISIBILITY) {
            previous = LAST_EFFECTIVE_VISIBILITY.put(view, shown);
        }
        return shown && !Boolean.TRUE.equals(previous);
    }

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
        int id = activity.getResources().getIdentifier(
                idName, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private static void applySettingsMotion(Activity activity) {
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null) return;

        installAccordionBehavior(content);
        attachInteractiveTree(content);
        animateOpenedAccordionContents(content);

        String section = safe(readStringField(activity, "currentSettingsSection"));
        String pendingSettingKey = safe(readStringField(activity, "pendingSettingKey"));
        boolean changed;
        synchronized (LAST_SETTINGS_SECTION) {
            String previous = LAST_SETTINGS_SECTION.get(activity);
            changed = previous == null || !previous.equals(section);
            if (changed) LAST_SETTINGS_SECTION.put(activity, section);
        }
        if (!changed) return;

        // Normal navigation starts fully collapsed. Search/deep-link navigation may
        // open the exact target panel so the existing scroll-to-setting behavior works.
        if (!section.isEmpty() && pendingSettingKey.isEmpty()) {
            collapseAllConnectedPanelsImmediate(content);
        }

        if (!UiMotion.animationsEnabled()) return;
        if (section.isEmpty()) {
            ViewGroup categories = findSettingsCategoryList(content);
            if (categories != null) UiMotion.fadeInChildren(categories, 14L, 190L, 4);
        } else {
            UiMotion.fadeInChildren(content, 15L, 185L, 4);
        }
    }

    /** Adds a subtle content reveal after each Settings accordion actually opens. */
    private static void animateOpenedAccordionContents(ViewGroup content) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null) continue;
            View body = panel.getChildAt(1);
            boolean shown = isEffectivelyVisible(body);
            if (visibilityTransitionedToShown(body, shown) && body instanceof ViewGroup) {
                UiMotion.fadeInChildren((ViewGroup) body, 7L, 150L, 2);
            }
        }
    }

    private static void installAccordionBehavior(ViewGroup content) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null) continue;
            View header = panel.getChildAt(0);
            synchronized (ACCORDION_HEADERS) {
                ACCORDION_HEADERS.put(header, content);
            }
        }
    }

    /**
     * Runs from the shared touch listener while allowing the panel's native click
     * listener to keep ownership of its expand/collapse animation and state.
     */
    static void handleAccordionTouch(final View header, int action) {
        if (header == null) return;

        final ViewGroup content;
        synchronized (ACCORDION_HEADERS) {
            content = ACCORDION_HEADERS.get(header);
        }
        if (content == null) return;

        View parent = header.getParent() instanceof View ? (View) header.getParent() : null;
        ViewGroup panel = parent instanceof ViewGroup ? connectedPanel(parent) : null;
        if (panel == null) return;
        View body = panel.getChildAt(1);

        if (action == MotionEvent.ACTION_DOWN) {
            synchronized (ACCORDION_WILL_OPEN) {
                ACCORDION_WILL_OPEN.put(header, body.getVisibility() != View.VISIBLE);
            }
            return;
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
            synchronized (ACCORDION_WILL_OPEN) {
                ACCORDION_WILL_OPEN.remove(header);
            }
            return;
        }

        if (action != MotionEvent.ACTION_UP) return;

        final boolean opening;
        synchronized (ACCORDION_WILL_OPEN) {
            opening = Boolean.TRUE.equals(ACCORDION_WILL_OPEN.remove(header));
        }
        if (!opening) return;

        // Run on the next display frame so the touched panel's native click has
        // completed before sibling state is evaluated.
        header.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                View parent = header.getParent() instanceof View
                        ? (View) header.getParent() : null;
                ViewGroup openedPanel = parent instanceof ViewGroup
                        ? connectedPanel(parent) : null;
                if (openedPanel == null) return;
                View openedBody = openedPanel.getChildAt(1);
                if (openedBody.getVisibility() != View.VISIBLE) return;
                collapseOtherConnectedPanels(content, openedPanel);
            }
        });
    }

    private static void collapseOtherConnectedPanels(ViewGroup content, ViewGroup keepOpen) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null || panel == keepOpen) continue;

            final View header = panel.getChildAt(0);
            final View body = panel.getChildAt(1);
            if (body.getVisibility() != View.VISIBLE) continue;

            header.performClick();
            header.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (body.getVisibility() == View.VISIBLE) {
                        finalizeCollapsedPanel(header, body);
                    }
                }
            }, 220L);
        }
    }

    private static void collapseAllConnectedPanelsImmediate(ViewGroup content) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null) continue;

            View header = panel.getChildAt(0);
            View body = panel.getChildAt(1);
            if (body.getVisibility() != View.VISIBLE) continue;

            header.performClick();
            finalizeCollapsedPanel(header, body);
        }
    }

    private static void finalizeCollapsedPanel(View header, View body) {
        if (header == null || body == null) return;
        header.animate().cancel();
        body.animate().cancel();
        body.setVisibility(View.GONE);
        body.setAlpha(0.0f);
        body.setScaleY(0.96f);
        header.setBackgroundResource(R.drawable.settings_section_header_collapsed);

        synchronized (LAST_EFFECTIVE_VISIBILITY) {
            LAST_EFFECTIVE_VISIBILITY.put(body, Boolean.FALSE);
        }

        View arrow = trailingIndicator(header);
        if (arrow != null) {
            arrow.animate().cancel();
            arrow.setRotation(0.0f);
            arrow.setTranslationX(0.0f);
            arrow.setAlpha(1.0f);
        }
    }

    private static ViewGroup connectedPanel(View candidate) {
        if (!(candidate instanceof ViewGroup)) return null;
        ViewGroup panel = (ViewGroup) candidate;
        if (panel.getChildCount() != 2) return null;

        View header = panel.getChildAt(0);
        View body = panel.getChildAt(1);
        if (!(header instanceof LinearLayout) || !header.isClickable()) return null;
        if (!(body instanceof LinearLayout)) return null;
        return panel;
    }

    /**
     * Adds consistent low-amplitude press feedback to native controls only.
     * Structural signatures keep repeated layout passes from recursively walking an
     * unchanged hierarchy. WebView subtrees are never traversed.
     */
    private static void attachInteractiveTree(View view) {
        if (view == null || view instanceof WebView) return;

        if (view instanceof ImageButton) {
            attachPressScale(view, 0.978f, 68L, 125L);
        } else if (view instanceof Switch) {
            attachPressScale(view, 0.990f, 70L, 130L);
        } else if (view instanceof Button) {
            attachPressScale(view, 0.992f, 70L, 125L);
        } else if (!(view instanceof EditText)
                && view instanceof TextView
                && view.isClickable()) {
            attachPressScale(view, 0.995f, 68L, 120L);
        } else if (view instanceof LinearLayout && view.isClickable()) {
            attachPressScale(view, 0.996f, 68L, 120L);
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
            attachInteractiveTree(group.getChildAt(i));
        }
    }

    private static int childSignature(ViewGroup group) {
        if (group == null) return 0;
        int result = 17;
        int count = group.getChildCount();
        result = 31 * result + count;
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            result = 31 * result + System.identityHashCode(child);
        }
        return result;
    }

    private static ViewGroup findSettingsCategoryList(ViewGroup content) {
        if (content == null) return null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;

            ViewGroup group = (ViewGroup) child;
            int clickable = 0;
            for (int j = 0; j < group.getChildCount(); j++) {
                View row = group.getChildAt(j);
                if (row != null && row.isClickable()) clickable++;
            }
            if (clickable >= 4) return group;
        }
        return null;
    }

    private static void attachPressScale(View view) {
        attachPressScale(view, UiMotion.DEFAULT_PRESS_SCALE,
                UiMotion.DEFAULT_PRESS_IN_MS, UiMotion.DEFAULT_RELEASE_MS);
    }

    private static void attachPressScale(View view, float scale, long pressInMs, long releaseMs) {
        if (view == null) return;
        synchronized (MOTION_ATTACHED) {
            if (MOTION_ATTACHED.containsKey(view)) return;
            MOTION_ATTACHED.put(view, Boolean.TRUE);
        }
        UiMotion.attachPressScale(view, scale, pressInMs, releaseMs);
    }

    private static boolean isSettingsActivity(Activity activity) {
        return activity != null && SETTINGS_ACTIVITY.equals(activity.getClass().getName());
    }

    private static View readViewField(Activity activity, String fieldName) {
        Object value = readField(activity, fieldName);
        return value instanceof View ? (View) value : null;
    }

    private static ViewGroup readViewGroupField(Activity activity, String fieldName) {
        Object value = readField(activity, fieldName);
        return value instanceof ViewGroup ? (ViewGroup) value : null;
    }

    private static String readStringField(Activity activity, String fieldName) {
        Object value = readField(activity, fieldName);
        return value instanceof String ? (String) value : "";
    }

    private static Object readField(Activity activity, String fieldName) {
        if (activity == null || fieldName == null || fieldName.isEmpty()) return null;
        Class<?> type = activity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(activity);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static View trailingIndicator(View root) {
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            if (!(child instanceof TextView)) return null;
            CharSequence value = ((TextView) child).getText();
            String text = value == null ? "" : value.toString().trim();
            if ("›".equals(text)
                    || ">".equals(text)
                    || "⌄".equals(text)
                    || "⌃".equals(text)
                    || "∨".equals(text)
                    || "∧".equals(text)) {
                return child;
            }
            return null;
        }
        return null;
    }
}

final class UiMotion {
    static final float DEFAULT_PRESS_SCALE = 0.988f;
    static final long DEFAULT_PRESS_IN_MS = 70L;
    static final long DEFAULT_RELEASE_MS = 125L;

    private static final long CHILD_ENTER_MS = 185L;
    private static final long MAX_STAGGER_DELAY_MS = 100L;
    private static final int CHILD_RISE_DP = 4;

    private static final TimeInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f);
    private static final TimeInterpolator FAST_PRESS =
            new PathInterpolator(0.40f, 0.0f, 1.0f, 1.0f);

    private static final WeakHashMap<View, WeakReference<AnimatorSet>> ENTER_ANIMATORS =
            new WeakHashMap<>();

    private UiMotion() {}

    static boolean animationsEnabled() {
        try {
            return ValueAnimator.areAnimatorsEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void attachPressScale(final View view, float scale, long pressInMs, long releaseMs) {
        if (view == null) return;

        final float pressedScale = Math.max(0.94f, Math.min(1.0f, scale));
        final long pressDuration = Math.max(0L, pressInMs);
        final long releaseDuration = Math.max(0L, releaseMs);
        final int edgeTolerance = dp(view.getContext(), 8);

        view.setOnTouchListener(new View.OnTouchListener() {
            private boolean visuallyPressed;

            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (touched == null || event == null || !touched.isEnabled()) return false;

                int action = event.getActionMasked();
                HcfUiMotion.handleAccordionTouch(touched, action);

                if (!animationsEnabled()) {
                    if (visuallyPressed) {
                        visuallyPressed = false;
                        normalizeScale(touched);
                    }
                    return false;
                }

                if (action == MotionEvent.ACTION_DOWN) {
                    visuallyPressed = true;
                    animateScale(touched, pressedScale, pressDuration, FAST_PRESS);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float x = event.getX();
                    float y = event.getY();
                    boolean inside = x >= -edgeTolerance
                            && y >= -edgeTolerance
                            && x <= touched.getWidth() + edgeTolerance
                            && y <= touched.getHeight() + edgeTolerance;
                    if (!inside && visuallyPressed) {
                        visuallyPressed = false;
                        animateScale(touched, 1.0f, releaseDuration, EMPHASIZED_DECELERATE);
                    } else if (inside && !visuallyPressed) {
                        visuallyPressed = true;
                        animateScale(touched, pressedScale, pressDuration, FAST_PRESS);
                    }
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_OUTSIDE) {
                    visuallyPressed = false;
                    animateScale(touched, 1.0f, releaseDuration, EMPHASIZED_DECELERATE);
                }
                return false;
            }
        });
    }

    private static void animateScale(
            View view, float targetScale, long duration, TimeInterpolator interpolator) {
        if (view == null) return;
        view.animate().cancel();
        view.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setStartDelay(0L)
                .setDuration(Math.max(1L, duration))
                .setInterpolator(interpolator == null ? new DecelerateInterpolator() : interpolator)
                .start();
    }

    private static void normalizeScale(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
    }

    /** Alpha/translation entrance that never touches scale used by press feedback. */
    static void enterView(View view, int offsetDp, long durationMs, long delayMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            normalizeEntrance(view);
            return;
        }
        cancelEntrance(view);

        int offset = dp(view.getContext(), offsetDp);
        float startTranslation = offset;
        view.setAlpha(0.0f);
        view.setTranslationY(startTranslation);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
        ObjectAnimator translation = ObjectAnimator.ofFloat(
                view, View.TRANSLATION_Y, startTranslation, 0.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, translation);
        set.setStartDelay(Math.max(0L, delayMs));
        set.setDuration(Math.max(1L, durationMs));
        set.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, set);
    }

    static void fadeIn(View view, long durationMs, long delayMs) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            view.setAlpha(1.0f);
            return;
        }
        cancelEntrance(view);
        view.setAlpha(0.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha);
        set.setStartDelay(Math.max(0L, delayMs));
        set.setDuration(Math.max(1L, durationMs));
        set.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, set);
    }

    /** Small badge/status reveal. Reserved for non-clickable transient UI. */
    static void popIn(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (!animationsEnabled()) {
            view.setAlpha(1.0f);
            view.setScaleX(1.0f);
            view.setScaleY(1.0f);
            return;
        }
        cancelEntrance(view);

        view.setAlpha(0.0f);
        view.setScaleX(0.965f);
        view.setScaleY(0.965f);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.0f, 1.0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.965f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.965f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setDuration(165L);
        set.setInterpolator(EMPHASIZED_DECELERATE);
        rememberAndStart(view, set);
    }

    static void fadeInChildren(ViewGroup parent, long staggerMs) {
        fadeInChildren(parent, staggerMs, CHILD_ENTER_MS, CHILD_RISE_DP);
    }

    static void fadeInChildren(
            ViewGroup parent, long staggerMs, long durationMs, int riseDp) {
        if (parent == null) return;
        if (!animationsEnabled()) {
            normalizeVisibleChildren(parent);
            return;
        }

        long safeStagger = Math.max(0L, staggerMs);
        int animatedIndex = 0;
        int rise = dp(parent.getContext(), riseDp);

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null
                    || child.getVisibility() != View.VISIBLE
                    || child instanceof WebView
                    || containsWebViewChild(child)) {
                continue;
            }

            long delay = Math.min(MAX_STAGGER_DELAY_MS, safeStagger * animatedIndex);
            cancelEntrance(child);
            child.setAlpha(0.0f);
            child.setTranslationY(rise);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(child, View.ALPHA, 0.0f, 1.0f);
            ObjectAnimator translation = ObjectAnimator.ofFloat(
                    child, View.TRANSLATION_Y, rise, 0.0f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, translation);
            set.setStartDelay(delay);
            set.setDuration(Math.max(1L, durationMs));
            set.setInterpolator(EMPHASIZED_DECELERATE);
            rememberAndStart(child, set);
            animatedIndex++;
        }
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

    private static void normalizeVisibleChildren(ViewGroup parent) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            normalizeEntrance(child);
        }
    }

    private static void rememberAndStart(final View view, final AnimatorSet set) {
        if (view == null || set == null) return;
        synchronized (ENTER_ANIMATORS) {
            ENTER_ANIMATORS.put(view, new WeakReference<>(set));
        }
        final int oldLayerType = view.getLayerType();
        if (view.isAttachedToWindow() && oldLayerType == View.LAYER_TYPE_NONE) {
            try { view.setLayerType(View.LAYER_TYPE_HARDWARE, null); }
            catch (Throwable ignored) {}
        }
        set.addListener(new AnimatorListenerAdapter() {
            private void finish() {
                synchronized (ENTER_ANIMATORS) {
                    WeakReference<AnimatorSet> ref = ENTER_ANIMATORS.get(view);
                    if (ref != null && ref.get() == set) ENTER_ANIMATORS.remove(view);
                }
                if (view.getLayerType() == View.LAYER_TYPE_HARDWARE
                        && oldLayerType == View.LAYER_TYPE_NONE) {
                    try { view.setLayerType(View.LAYER_TYPE_NONE, null); }
                    catch (Throwable ignored) {}
                }
            }

            @Override public void onAnimationEnd(Animator animation) { finish(); }
            @Override public void onAnimationCancel(Animator animation) { finish(); }
        });
        set.start();
    }

    private static void cancelEntrance(View view) {
        if (view == null) return;
        WeakReference<AnimatorSet> reference;
        synchronized (ENTER_ANIMATORS) {
            reference = ENTER_ANIMATORS.remove(view);
        }
        AnimatorSet set = reference == null ? null : reference.get();
        if (set != null) set.cancel();
    }

    private static void normalizeEntrance(View view) {
        if (view == null) return;
        cancelEntrance(view);
        view.setAlpha(1.0f);
        view.setTranslationY(0.0f);
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
