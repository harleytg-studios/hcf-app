package com.harleytg.forum.dev;

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
import android.view.animation.OvershootInterpolator;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Installs lightweight native-only motion without touching forum WebView content. */
public final class HcfUiMotion {
    private static final String SETTINGS_ACTIVITY = "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener> SETTINGS_OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, String> LAST_SETTINGS_SECTION = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MOTION_ATTACHED = new WeakHashMap<>();

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
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityResumed(Activity activity) {
                    install(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    removeSettingsObserver(activity, false);
                }

                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                @Override
                public void onActivityDestroyed(Activity activity) {
                    removeSettingsObserver(activity, true);
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

            final View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (root == null) return;

            ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
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

    private static void removeSettingsObserver(Activity activity, boolean destroyed) {
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

        if (destroyed) {
            synchronized (LAST_SETTINGS_SECTION) {
                LAST_SETTINGS_SECTION.remove(activity);
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
            attachPressScale(logo, 0.97f, 65L, 145L);
        }
    }

    private static void applySettingsMotion(final Activity activity) {
        final ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null) return;

        attachInteractiveTree(content);

        final String section = safe(readStringField(activity, "currentSettingsSection"));
        boolean changed;
        synchronized (LAST_SETTINGS_SECTION) {
            String previous = LAST_SETTINGS_SECTION.get(activity);
            changed = previous == null || !previous.equals(section);
            if (changed) LAST_SETTINGS_SECTION.put(activity, section);
        }
        if (!changed) return;

        content.post(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (section.isEmpty()) {
                    ViewGroup categories = findSettingsCategoryList(content);
                    if (categories != null) {
                        UiMotion.softReveal(categories);
                        UiMotion.fadeInChildren(categories, 28L);
                    }
                } else {
                    UiMotion.softReveal(content);
                    UiMotion.fadeInChildren(content, 30L);
                }
            }
        });
    }

    private static void attachInteractiveTree(View view) {
        if (view == null || view instanceof WebView) return;

        if (view instanceof ImageButton) {
            attachPressScale(view, 0.96f, 70L, 145L);
        } else if (view instanceof Switch) {
            attachPressScale(view, 0.955f, 60L, 165L);
        } else if (view instanceof Button) {
            attachPressScale(view, 0.972f, 70L, 150L);
        } else if (!(view instanceof EditText) && view.isClickable()) {
            attachPressScale(view, 0.982f, 65L, 140L);
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
        attachPressScale(view, UiMotion.DEFAULT_PRESS_SCALE, UiMotion.DEFAULT_PRESS_IN_MS, UiMotion.DEFAULT_RELEASE_MS);
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
}

final class UiMotion {
    static final float DEFAULT_PRESS_SCALE = 0.96f;
    static final long DEFAULT_PRESS_IN_MS = 70L;
    static final long DEFAULT_RELEASE_MS = 145L;

    private static final long CHILD_ENTER_MS = 195L;
    private static final long SOFT_REVEAL_MS = 165L;
    private static final long MAX_STAGGER_DELAY_MS = 220L;
    private static final int CHILD_RISE_DP = 6;
    private static final int SOFT_REVEAL_RISE_DP = 4;
    private static final int INDICATOR_SHIFT_DP = 2;
    private static final float CHILD_START_SCALE = 0.992f;
    private static final float CONTAINER_START_SCALE = 0.996f;

    private UiMotion() {}

    static void attachPressScale(View view) {
        attachPressScale(view, DEFAULT_PRESS_SCALE, DEFAULT_PRESS_IN_MS, DEFAULT_RELEASE_MS);
    }

    static void attachPressScale(final View view, float scale, long pressInMs, long releaseMs) {
        if (view == null) return;

        final float pressedScale = Math.max(0.85f, Math.min(1.0f, scale));
        final long pressDuration = Math.max(0L, pressInMs);
        final long releaseDuration = Math.max(0L, releaseMs);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (touched == null || event == null || !touched.isEnabled()) return false;

                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touched.animate().cancel();
                    touched.setAlpha(1.0f);
                    touched.setTranslationY(0.0f);
                    animateTrailingIndicator(touched, true);
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
                    animateTrailingIndicator(touched, false);
                    touched.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setStartDelay(0L)
                            .setDuration(releaseDuration)
                            .setInterpolator(new OvershootInterpolator(0.75f))
                            .start();
                }
                return false;
            }
        });
    }

    static void softReveal(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        int rise = dp(view.getContext(), SOFT_REVEAL_RISE_DP);
        view.animate().cancel();
        view.setAlpha(0.94f);
        view.setTranslationY(rise);
        view.setScaleX(CONTAINER_START_SCALE);
        view.setScaleY(CONTAINER_START_SCALE);
        view.animate()
                .alpha(1.0f)
                .translationY(0.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setStartDelay(0L)
                .setDuration(SOFT_REVEAL_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
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
            child.animate().cancel();
            child.setAlpha(0.0f);
            child.setTranslationY(rise);
            child.setScaleX(CHILD_START_SCALE);
            child.setScaleY(CHILD_START_SCALE);
            child.animate()
                    .alpha(1.0f)
                    .translationY(0.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setStartDelay(delay)
                    .setDuration(CHILD_ENTER_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            animatedIndex++;
        }
    }

    private static void animateTrailingIndicator(View root, boolean pressed) {
        View indicator = trailingIndicator(root);
        if (indicator == null) return;

        indicator.animate().cancel();
        if (pressed) {
            indicator.animate()
                    .translationX(dp(root.getContext(), INDICATOR_SHIFT_DP))
                    .alpha(0.82f)
                    .setStartDelay(0L)
                    .setDuration(75L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            indicator.animate()
                    .translationX(0.0f)
                    .alpha(1.0f)
                    .setStartDelay(0L)
                    .setDuration(145L)
                    .setInterpolator(new OvershootInterpolator(0.65f))
                    .start();
        }
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

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
