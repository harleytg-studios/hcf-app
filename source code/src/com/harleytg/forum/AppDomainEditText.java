package com.harleytg.forum.dev;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Address-bar EditText that preserves MainActivity's existing listener while
 * intercepting the local app.forum.harleytg.com/<item> namespace first.
 */
public final class AppDomainEditText extends EditText {

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
