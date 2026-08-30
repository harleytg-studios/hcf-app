package com.harleytg.forum.dev;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/**
 * Settings-only stability layer.
 *
 * HcfUI historically builds some first subsettings expanded and then the shared
 * motion helper closes them after layout. That can expose an open frame before the
 * closed state wins. The native accordion also scales its whole body while its
 * layout changes, which can look like a flash/jump on dense Settings pages.
 *
 * This coordinator runs after HcfUiMotion, keeps Settings out of visual entrance
 * effects, hides Settings content before category rebuilds, establishes the final
 * collapsed state before content is shown, and owns accordion interaction using a
 * measured height animation only. No alpha/scale transform is used on accordion
 * bodies, so text and controls never blink or squash during open/close.
 */
public final class HcfSettingsStableUi {
    private static final String SETTINGS_ACTIVITY =
            "com.harleytg.forum.dev.HcfSubActivities$SettingsActivity";

    private static final WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>
            OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> PASS_PENDING = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Integer> CONTENT_SIGNATURE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PANEL_HEADERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NAV_HIDE_ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<View, ValueAnimator> HEIGHT_ANIMATORS = new WeakHashMap<>();

    private static final android.animation.TimeInterpolator EASE =
            new PathInterpolator(0.20f, 0.0f, 0.0f, 1.0f);

    private static boolean registered;

    private HcfSettingsStableUi() {}

    public static final class BootstrapProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            Context context = getContext();
            if (context == null) return true;
            Context app = context.getApplicationContext();
            if (registered || !(app instanceof Application)) return true;
            registered = true;
            ((Application) app).registerActivityLifecycleCallbacks(
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
        if (!isSettings(activity) || activity.isFinishing()) return;
        schedule(activity);
        synchronized (OBSERVERS) {
            if (OBSERVERS.containsKey(activity)) return;
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (root == null) return;
            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            if (!activity.isFinishing() && !activity.isDestroyed()) schedule(activity);
                        }
                    };
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) return;
            observer.addOnGlobalLayoutListener(listener);
            OBSERVERS.put(activity, listener);
        }
    }

    /** Post after the shared motion pass so this Settings-only state wins before draw. */
    private static void schedule(final Activity activity) {
        if (!isSettings(activity) || activity.getWindow() == null) return;
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
                if (activity.isFinishing() || activity.isDestroyed()) return;
                stabilize(activity);
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
        if (destroyed) synchronized (CONTENT_SIGNATURE) { CONTENT_SIGNATURE.remove(activity); }
    }

    private static void stabilize(Activity activity) {
        ViewGroup content = readViewGroupField(activity, "settingsContent");
        if (content == null) return;

        int signature = signature(content);
        boolean changed;
        synchronized (CONTENT_SIGNATURE) {
            Integer previous = CONTENT_SIGNATURE.put(activity, Integer.valueOf(signature));
            changed = previous == null || previous.intValue() != signature;
        }

        // Cancel page/category alpha fades from the generic motion layer. Settings
        // content should already be in its final state when it becomes visible.
        content.animate().cancel();
        content.setAlpha(1.0f);
        content.setScaleX(1.0f);
        content.setScaleY(1.0f);
        content.setTranslationX(0.0f);
        content.setTranslationY(0.0f);

        String section = safe(readStringField(activity, "currentSettingsSection"));
        String pendingKey = safe(readStringField(activity, "pendingSettingKey"));

        if (section.isEmpty()) {
            configureHomeNavigation(content);
        } else {
            configurePanels(content, changed && pendingKey.isEmpty());
        }

        configureBackNavigation(activity, content);

        // Header fades can otherwise race the category rebuild and make the title
        // appear to blink independently of the content.
        normalize(readViewField(activity, "headerTitleView"));
        normalize(readViewField(activity, "headerSubtitleView"));
        normalize(readViewField(activity, "headerBackButton"));
    }

    /** Normal category navigation must reveal only after all subsettings are closed. */
    private static void configurePanels(final ViewGroup content, boolean forceClosed) {
        if (content == null) return;
        for (int i = 0; i < content.getChildCount(); i++) {
            final ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null) continue;
            final View header = panel.getChildAt(0);
            final View body = panel.getChildAt(1);

            cancelHeight(body);
            normalize(header);
            normalize(body);

            if (forceClosed) settleClosed(header, body);
            else settleCurrent(header, body);

            // Replace both the old scale press-listener and the native scaleY
            // accordion listener. State is derived from actual visibility, so it
            // cannot drift out of sync after another panel closes.
            header.setOnTouchListener(null);
            header.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (body.getVisibility() == View.VISIBLE) {
                        closePanel(header, body, null);
                    } else {
                        openExclusively(content, panel);
                    }
                }
            });
            synchronized (PANEL_HEADERS) { PANEL_HEADERS.put(header, Boolean.TRUE); }
        }
    }

    private static void openExclusively(final ViewGroup content, final ViewGroup target) {
        if (content == null || target == null) return;
        ViewGroup openSibling = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            ViewGroup panel = connectedPanel(content.getChildAt(i));
            if (panel == null || panel == target) continue;
            if (panel.getChildAt(1).getVisibility() == View.VISIBLE) {
                openSibling = panel;
                break;
            }
        }

        final View targetHeader = target.getChildAt(0);
        final View targetBody = target.getChildAt(1);
        if (openSibling == null) {
            openPanel(targetHeader, targetBody);
            return;
        }

        View siblingHeader = openSibling.getChildAt(0);
        View siblingBody = openSibling.getChildAt(1);
        closePanel(siblingHeader, siblingBody, new Runnable() {
            @Override public void run() { openPanel(targetHeader, targetBody); }
        });
    }

    /** Height-only expansion: no alpha blink and no scale/squash of child controls. */
    private static void openPanel(final View header, final View body) {
        if (header == null || body == null) return;
        cancelHeight(body);
        normalize(header);
        normalize(body);

        ViewGroup.LayoutParams raw = body.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams)) {
            body.setVisibility(View.VISIBLE);
            header.setBackgroundResource(R.drawable.settings_section_header_expanded);
            rotateArrow(header, 90.0f, 140L);
            return;
        }

        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        int parentWidth = body.getParent() instanceof View
                ? ((View) body.getParent()).getWidth() : 0;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                Math.max(1, parentWidth), View.MeasureSpec.AT_MOST);
        body.setVisibility(View.INVISIBLE);
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        body.setLayoutParams(lp);
        body.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetHeight = Math.max(1, body.getMeasuredHeight());

        lp.height = 0;
        body.setLayoutParams(lp);
        body.setVisibility(View.VISIBLE);
        header.setBackgroundResource(R.drawable.settings_section_header_expanded);
        rotateArrow(header, 90.0f, 140L);

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        rememberHeight(body, animator);
        animator.setDuration(165L);
        animator.setInterpolator(EASE);
        animator.addUpdateListener(a -> {
            lp.height = (Integer) a.getAnimatedValue();
            body.setLayoutParams(lp);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                forgetHeight(body, animator);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                body.setLayoutParams(lp);
            }
            @Override public void onAnimationCancel(Animator animation) {
                forgetHeight(body, animator);
            }
        });
        animator.start();
    }

    /** Sequential height collapse prevents two Settings panels from reflowing at once. */
    private static void closePanel(final View header, final View body, final Runnable endAction) {
        if (header == null || body == null) {
            if (endAction != null) endAction.run();
            return;
        }
        cancelHeight(body);
        normalize(header);
        normalize(body);

        ViewGroup.LayoutParams raw = body.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams) || body.getHeight() <= 0) {
            settleClosed(header, body);
            if (endAction != null) endAction.run();
            return;
        }

        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        final int startHeight = body.getHeight();
        rotateArrow(header, 0.0f, 120L);

        ValueAnimator animator = ValueAnimator.ofInt(startHeight, 0);
        rememberHeight(body, animator);
        animator.setDuration(135L);
        animator.setInterpolator(EASE);
        animator.addUpdateListener(a -> {
            lp.height = (Integer) a.getAnimatedValue();
            body.setLayoutParams(lp);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean finished;
            private void finish() {
                if (finished) return;
                finished = true;
                forgetHeight(body, animator);
                settleClosed(header, body);
                if (endAction != null) endAction.run();
            }
            @Override public void onAnimationEnd(Animator animation) { finish(); }
            @Override public void onAnimationCancel(Animator animation) { finish(); }
        });
        animator.start();
    }

    private static void settleCurrent(View header, View body) {
        if (body.getVisibility() == View.VISIBLE) settleOpen(header, body);
        else settleClosed(header, body);
    }

    private static void settleOpen(View header, View body) {
        if (header == null || body == null) return;
        normalize(header);
        normalize(body);
        body.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams lp = body.getLayoutParams();
        if (lp != null) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.setLayoutParams(lp);
        }
        header.setBackgroundResource(R.drawable.settings_section_header_expanded);
        setArrow(header, 90.0f);
    }

    private static void settleClosed(View header, View body) {
        if (header == null || body == null) return;
        cancelHeight(body);
        normalize(header);
        normalize(body);
        body.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = body.getLayoutParams();
        if (lp != null) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            body.setLayoutParams(lp);
        }
        header.setBackgroundResource(R.drawable.settings_section_header_collapsed);
        setArrow(header, 0.0f);
    }

    /** Hide Settings content before the existing category click rebuilds its children. */
    private static void configureHomeNavigation(final ViewGroup content) {
        if (content == null) return;
        attachPreHideToClickableDescendants(content, content, 0);
    }

    private static void configureBackNavigation(Activity activity, final ViewGroup content) {
        View back = readViewField(activity, "headerBackButton");
        if (back == null) return;
        synchronized (NAV_HIDE_ATTACHED) {
            if (NAV_HIDE_ATTACHED.containsKey(back)) return;
            NAV_HIDE_ATTACHED.put(back, Boolean.TRUE);
        }
        back.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event != null && event.getActionMasked() == MotionEvent.ACTION_UP) preHide(content);
                return false;
            }
        });
    }

    private static void attachPreHideToClickableDescendants(
            View view, final ViewGroup content, int depth) {
        if (view == null || depth > 4) return;
        if (view != content && view.isClickable()) {
            synchronized (NAV_HIDE_ATTACHED) {
                if (!NAV_HIDE_ATTACHED.containsKey(view)) {
                    NAV_HIDE_ATTACHED.put(view, Boolean.TRUE);
                    view.setOnTouchListener(new View.OnTouchListener() {
                        @Override public boolean onTouch(View v, MotionEvent event) {
                            if (event != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
                                preHide(content);
                            }
                            return false;
                        }
                    });
                }
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            attachPreHideToClickableDescendants(group.getChildAt(i), content, depth + 1);
        }
    }

    private static void preHide(ViewGroup content) {
        if (content == null) return;
        content.animate().cancel();
        content.setAlpha(0.0f);
    }

    private static void normalize(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
    }

    private static void rotateArrow(View header, float degrees, long durationMs) {
        View arrow = trailingIndicator(header);
        if (arrow == null) return;
        arrow.animate().cancel();
        arrow.animate().rotation(degrees).setDuration(durationMs).setInterpolator(EASE).start();
    }

    private static void setArrow(View header, float degrees) {
        View arrow = trailingIndicator(header);
        if (arrow == null) return;
        arrow.animate().cancel();
        arrow.setRotation(degrees);
        arrow.setAlpha(1.0f);
        arrow.setScaleX(1.0f);
        arrow.setScaleY(1.0f);
        arrow.setTranslationX(0.0f);
        arrow.setTranslationY(0.0f);
    }

    private static View trailingIndicator(View root) {
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            if (!(child instanceof TextView)) return null;
            String text = String.valueOf(((TextView) child).getText()).trim();
            if ("›".equals(text) || ">".equals(text) || "⌄".equals(text)
                    || "⌃".equals(text) || "∨".equals(text) || "∧".equals(text)) {
                return child;
            }
            return null;
        }
        return null;
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

    private static int signature(ViewGroup content) {
        int result = 17;
        result = 31 * result + content.getChildCount();
        for (int i = 0; i < content.getChildCount(); i++) {
            result = 31 * result + System.identityHashCode(content.getChildAt(i));
        }
        return result;
    }

    private static void rememberHeight(View body, ValueAnimator animator) {
        synchronized (HEIGHT_ANIMATORS) { HEIGHT_ANIMATORS.put(body, animator); }
    }

    private static void forgetHeight(View body, ValueAnimator animator) {
        synchronized (HEIGHT_ANIMATORS) {
            ValueAnimator current = HEIGHT_ANIMATORS.get(body);
            if (current == animator) HEIGHT_ANIMATORS.remove(body);
        }
    }

    private static void cancelHeight(View body) {
        if (body == null) return;
        ValueAnimator animator;
        synchronized (HEIGHT_ANIMATORS) { animator = HEIGHT_ANIMATORS.remove(body); }
        if (animator != null) animator.cancel();
    }

    private static boolean isSettings(Activity activity) {
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
        if (activity == null) return null;
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
