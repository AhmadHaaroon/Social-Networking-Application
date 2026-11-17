package com.ahmadhaaroon.i221295.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/** Drains persisted actions in order. Repeated requests keep the same UUID. */
public class SyncWorker extends Worker {

    private static final Object RUN_LOCK = new Object();

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void schedule(Context context) {
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    public static void retryNow(Context context) {
        enqueue(context, ExistingWorkPolicy.REPLACE);
    }

    private static void enqueue(Context context, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("kinnect_sync", policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        synchronized (RUN_LOCK) {
            String token = "";
            try {
                for (String[] row : LocalDb.get().revocations()) {
                    if (isStopped()) return Result.retry();
                    Api.call("auth/logout.php", new JSONObject(), SecureStore.decrypt(row[1]));
                    LocalDb.get().revoked(row[0]);
                }
                String uid;
                synchronized (Profiles.LOCK) {
                    uid = WebSession.uid();
                    token = WebSession.token();
                }
                if (uid == null) return Result.success();
                while (!isStopped()) {
                    JSONObject action;
                    synchronized (Profiles.LOCK) {
                        if (!token.equals(WebSession.token())) return Result.success();
                        action = LocalDb.get().next(uid);
                    }
                    if (action == null) break;
                    String id = action.getString("actionId");
                    try {
                        JSONObject response = Api.call("profile/update.php", action, token);
                        synchronized (Profiles.LOCK) {
                            if (!token.equals(WebSession.token())) return Result.success();
                            LocalDb.get().complete(id, response.getJSONObject("user"));
                        }
                    } catch (Api.Failure e) {
                        if (e.retryable()) return Result.retry();
                        synchronized (Profiles.LOCK) {
                            if (!token.equals(WebSession.token())) return Result.success();
                            if (e.status == 401) {
                                WebSession.clear();
                                LocalDb.changed();
                                return Result.failure();
                            }
                            LocalDb.get().reject(id, uid, e.getMessage());
                        }
                    }
                }
                if (isStopped()) return Result.retry();
                JSONObject response = Api.call("profile/me.php", null, token);
                synchronized (Profiles.LOCK) {
                    if (!token.equals(WebSession.token())) return Result.success();
                    LocalDb.get().accept(response.getJSONObject("user"));
                    LocalDb.changed();
                }
                return Result.success();
            } catch (Api.Failure e) {
                synchronized (Profiles.LOCK) {
                    if (e.status == 401 && !token.isEmpty() && token.equals(WebSession.token())) {
                        WebSession.clear();
                        LocalDb.changed();
                    }
                }
                return e.retryable() ? Result.retry() : Result.failure();
            } catch (Exception e) {
                return Result.retry();
            }
        }
    }
}
