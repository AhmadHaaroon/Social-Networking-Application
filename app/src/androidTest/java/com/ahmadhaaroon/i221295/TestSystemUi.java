package com.ahmadhaaroon.i221295;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;

/** Accepts Android's first-use fullscreen explanation before Espresso looks for app controls. */
final class TestSystemUi {

    private TestSystemUi() {
    }

    static void dismissFullscreenHint() {
        long end = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < end) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Got it")) {
                    if (node.isClickable() || node.getParent() != null) {
                        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK) && node.getParent() != null) {
                            node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        }
                        SystemClock.sleep(200);
                        return;
                    }
                }
            }
            SystemClock.sleep(100);
        }
    }
}
