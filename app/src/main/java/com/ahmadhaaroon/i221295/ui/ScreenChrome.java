package com.ahmadhaaroon.i221295.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.ahmadhaaroon.i221295.R;

/** Android owns edge gestures; page scrolling never has to imitate navigation gestures. */
public final class ScreenChrome {

    private ScreenChrome() {
    }

    public static void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), content);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        if (content.getTag(R.id.chromeInstalled) != null) return;
        content.setTag(R.id.chromeInstalled, true);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        content.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            if (focused && !activity.isFinishing()) controller.hide(WindowInsetsCompat.Type.systemBars());
        });
        ViewCompat.requestApplyInsets(content);
    }
}
