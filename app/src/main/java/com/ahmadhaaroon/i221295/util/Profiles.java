package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serializes session changes and sync so a late response cannot restore a logged-out account. */
public final class Profiles {

    public static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Profiles() {
    }

    public static void signIn(Context context, String endpoint, JSONObject input,
                              Runnable done, Callback<Exception> failed) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                synchronized (LOCK) {
                    JSONObject result = Api.call("auth/" + endpoint + ".php", input, null);
                    String nextUid = result.getJSONObject("user").getString("uid");
                    if (!LocalDb.get().containsOnly(nextUid)) LocalDb.get().revokeAndClear("");
                    LocalDb.get().accept(result.getJSONObject("user"));
                    WebSession.save(result);
                }
                SyncWorker.retryNow(app);
                MAIN.post(done);
            } catch (Exception e) {
                MAIN.post(() -> failed.onResult(e));
            }
        });
    }

    public static void save(Context context, JSONObject profile, Runnable queued, Callback<Exception> failed) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                synchronized (LOCK) {
                    String uid = WebSession.uid();
                    if (uid == null) throw new IllegalStateException("Your session expired. Please log in again.");
                    profile.put("actionId", UUID.randomUUID().toString());
                    LocalDb.get().enqueue(uid, profile);
                }
                SyncWorker.retryNow(app);
                MAIN.post(queued);
            } catch (Exception e) {
                MAIN.post(() -> failed.onResult(e));
            }
        });
    }

    public static void refresh(Context context) {
        SyncWorker.retryNow(context.getApplicationContext());
    }

    public static void logout(Context context, Runnable done, Callback<Exception> failed) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                synchronized (LOCK) {
                    LocalDb.get().revokeAndClear(WebSession.token());
                    WebSession.clear();
                    UserCache.clear();
                }
                SyncWorker.retryNow(app);
                MAIN.post(done);
            } catch (Exception e) {
                MAIN.post(() -> failed.onResult(e));
            }
        });
    }
}
