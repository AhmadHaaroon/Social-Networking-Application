package com.ahmadhaaroon.i221295.services;
import android.content.Context;
import android.content.Intent;
import com.ahmadhaaroon.i221295.CallActivity;
import com.ahmadhaaroon.i221295.KinnectApp;
import com.ahmadhaaroon.i221295.models.CallInfo;
import com.ahmadhaaroon.i221295.util.*;

/** Incoming calls are delivered by the server stream, with FCM covering the background. */
public final class CallWatcher {
    public static final long RING_TIMEOUT_MS = 45000;
    private static Runnable observer;
    private static final java.util.Set<String> shown = new java.util.HashSet<>();

    private CallWatcher() {}

    public static synchronized void start(Context context) {
        if (observer != null) return;
        Context app = context.getApplicationContext();
        observer =
                () -> {
                    if (!KinnectApp.isInForeground() || Db.uid() == null) return;
                    for (org.json.JSONObject r : LocalDb.get().content("calls")) {
                        CallInfo c = ContentJson.read(r, CallInfo.class);
                        if (Db.uid().equals(c.calleeUid)
                                && CallInfo.RINGING.equals(c.status)
                                && System.currentTimeMillis() - c.timestamp < RING_TIMEOUT_MS
                                && !CallActivity.isShowing(c.callId)
                                && shown.add(c.callId))
                            app.startActivity(
                                    CallActivity.incoming(app, c)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                };
        LocalDb.observe(observer);
    }

    public static synchronized void stop() {
        if (observer != null) LocalDb.removeObserver(observer);
        observer = null;
        shown.clear();
    }
}
