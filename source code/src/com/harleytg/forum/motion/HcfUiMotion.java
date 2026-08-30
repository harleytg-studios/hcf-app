package com.harleytg.forum.dev;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Lightweight native-only motion plus Settings accordion coordination.
 * Forum WebView content is intentionally excluded.
 */
public final class HcfUiMotion {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>
            SETTINGS_OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, String> LAST_SETTINGS_SECTION = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MOTION_ATTACHED = new WeakHashMap<>();
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
                            removeSettingsObserver(activity);
                        }

                        @Override public void onActivityStopped(Activity activity) {}
                        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            removeSettingsObserver(activity);
                        }
                    });
            return true;
        }

        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    private static void install(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        attachChrome(activity);
        if (!isSettingsActivity(activity)) return;

        applySettingsMotion(activity);

        synchronized (SETTINGS_OBSERVERS) {
            if (SETTINGS_OBSERVERS.containsKey(activity)) return;
            final View root = activity.getWindow() == null
                    ? null : activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (activity.isFinishing() || activity.isDestroyed()) return;
                            applySettingsMotion(activity);
                        }
                    };
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(listener);
            SETTINGS_OBSERVERS.put(activity, listener);
        }
    }

    private static void removeSettingsObserver(Activity activity) {
        if (activity == null) return;

        ViewTreeObserver.OnGlobalLayoutListener listener;
        synchronized (SETTINGS_OBSERVERS) {
            listener = SETTINGS_OBSERVERS.remove(activity);
        }
        if (listener != null && activity.getWindow() != null) {
            View root = activity.getWindow().getDecorView();
            if (root != null) {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
            }
        }

        // Returning to a Settings category must start with every subsection closed.
        synchronized (LAST_SETTINGS_SECTION) {
            LAST_SETTINGS_SECTION.remove(activity);
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
            attachPressScale(logo, 0.985f, 75L, 110L);
        }
    }

    private static void applySettingsMotion(Activity activity) {
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null) return;

        installAccordionBehavior(content);
        attachInteractiveTree(content);

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

        if (section.isEmpty()) {
            ViewGroup categories = findSettingsCategoryList(content);
            if (categories != null) UiMotion.fadeInChildren(categories, 18L);
        } else {
            UiMotion.fadeInChildren(content, 20L);
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

        // Wait one frame so the touched panel's native OnClick has completed. Once
        // it is visibly open, every other visible sibling is closed. No arrow-angle
        // heuristic is used: that heuristic was what allowed two panels to remain open.
        header.postDelayed(new Runnable() {
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
        }, 16L);
    }

    private static void collapseOtherConnectedPanels(ViewGroup content, ViewGroup keepOpen) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null || panel == keepOpen) continue;

            final View header = panel.getChildAt(0);
            final View body = panel.getChildAt(1);
            if (body.getVisibility() != View.VISIBLE) continue;

            // Let the native panel listener synchronize its private isExpanded state
            // and run the normal close animation.
            header.performClick();

            // Defensive visual cleanup only after the native close animation should
            // have completed. This prevents a stale VISIBLE body from leaving two
            // subsections open without sacrificing the normal smooth close motion.
            header.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (body.getVisibility() == View.VISIBLE) {
                        finalizeCollapsedPanel(header, body);
                    }
                }
            }, 190L);
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

    private static void attachInteractiveTree(View view) {
        if (view == null || view instanceof WebView) return;

        if (view instanceof ImageButton) {
            attachPressScale(view, 0.980f, 75L, 110L);
        } else if (view instanceof Switch) {
            attachPressScale(view, 0.985f, 75L, 115L);
        } else if (view instanceof Button) {
            attachPressScale(view, 0.988f, 75L, 115L);
        } else if (!(view instanceof EditText) && view.isClickable()) {
            attachPressScale(view, 0.992f, 75L, 115L);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            attachInteractiveTree(group.getChildAt(i));
        }
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
            if (!(child instanceof android.widget.TextView)) return null;
            CharSequence value = ((android.widget.TextView) child).getText();
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
    static final float DEFAULT_PRESS_SCALE = 0.980f;
    static final long DEFAULT_PRESS_IN_MS = 75L;
    static final long DEFAULT_RELEASE_MS = 110L;

    private static final long CHILD_ENTER_MS = 175L;
    private static final long MAX_STAGGER_DELAY_MS = 120L;
    private static final int CHILD_RISE_DP = 4;

    private static final WeakHashMap<View, WeakReference<AnimatorSet>> ENTER_ANIMATORS =
            new WeakHashMap<>();

    private UiMotion() {}

    static void attachPressScale(final View view, float scale, long pressInMs, long releaseMs) {
        if (view == null) return;

        final float pressedScale = Math.max(0.90f, Math.min(1.0f, scale));
        final long pressDuration = Math.max(0L, pressInMs);
        final long releaseDuration = Math.max(0L, releaseMs);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (touched == null || event == null || !touched.isEnabled()) return false;

                int action = event.getActionMasked();
                HcfUiMotion.handleAccordionTouch(touched, action);

                if (action == MotionEvent.ACTION_DOWN) {
                    touched.animate().cancel();
                    touched.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .setStartDelay(0L)
                            .setDuration(pressDuration)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_OUTSIDE) {
                    touched.animate().cancel();
                    touched.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setStartDelay(0L)
                            .setDuration(releaseDuration)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                return false;
            }
        });
    }

    static void fadeInChildren(ViewGroup parent, long staggerMs) {
        if (parent == null) return;

        long safeStagger = Math.max(0L, staggerMs);
        int animatedIndex = 0;
        int rise = dp(parent.getContext(), CHILD_RISE_DP);

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;

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
            set.setDuration(CHILD_ENTER_MS);
            set.setInterpolator(new DecelerateInterpolator());

            synchronized (ENTER_ANIMATORS) {
                ENTER_ANIMATORS.put(child, new WeakReference<>(set));
            }
            set.start();
            animatedIndex++;
        }
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

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
