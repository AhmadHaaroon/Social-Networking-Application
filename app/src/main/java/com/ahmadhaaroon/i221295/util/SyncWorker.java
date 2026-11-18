package com.ahmadhaaroon.i221295.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("kinnect_sync", policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        synchronized (RUN_LOCK) {
            // XAMPP can be reachable on Wi-Fi even when Android cannot validate internet access.
            if (!Connection.connected(getApplicationContext())) return Result.retry();
            SyncStatus.begin();
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
                        boolean friendAction = "friends.action".equals(action.optString("operation"));
                        boolean contentAction = action.has("data");
                        if (contentAction) MediaFiles.uploadContent(action.getJSONObject("data"), token);
                        else if (!friendAction) MediaFiles.uploadProfile(action, token);
                        String operation = action.optString("operation");
                        boolean chatAction =
                                operation.startsWith("messages.")
                                        || operation.startsWith("chat.")
                                        || operation.startsWith("notifications.");
                        JSONObject response =
                                Api.call(
                                        contentAction
                                                ? (chatAction
                                                        ? "chat/action.php"
                                                        : "content/action.php")
                                                : friendAction
                                                        ? "friends/action.php"
                                                        : "profile/update.php",
                                        action,
                                        token);
                        if (friendAction || contentAction) response = Api.call("social/state.php", null, token);
                        synchronized (Profiles.LOCK) {
                            if (!token.equals(WebSession.token())) return Result.success();
                            if (contentAction) LocalDb.get().completeContent(id, response);
                            else if (friendAction) LocalDb.get().completeFriend(id, response);
                            else LocalDb.get().complete(id, response.getJSONObject("user"));
                        }
                    } catch (Api.Failure e) {
                        if (e.retryable()) {
                            SyncStatus.error = e.getMessage();
                            return Result.retry();
                        }
                        synchronized (Profiles.LOCK) {
                            if (!token.equals(WebSession.token())) return Result.success();
                            if (e.status == 401) {
                                WebSession.clear();
                                LocalDb.changed();
                                return Result.failure();
                            }
                            if (action.has("data")) LocalDb.get().rejectContent(id, e.getMessage());
                            else if ("friends.action".equals(action.optString("operation"))) LocalDb.get().rejectFriend(id, e.getMessage());
                            else LocalDb.get().reject(id, uid, e.getMessage());
                        }
                    }
                }
                if (isStopped()) return Result.retry();
                JSONObject response = Api.call("social/state.php", null, token);
                synchronized (Profiles.LOCK) {
                    if (!token.equals(WebSession.token())) return Result.success();
                    LocalDb.get().acceptSocial(response);
                    LocalDb.changed();
                }
                return Result.success();
            } catch (Api.Failure e) {
                SyncStatus.error = e.getMessage();
                synchronized (Profiles.LOCK) {
                    if (e.status == 401 && !token.isEmpty() && token.equals(WebSession.token())) {
                        WebSession.clear();
                        LocalDb.changed();
                    }
                }
                return e.retryable() ? Result.retry() : Result.failure();
            } catch (Exception e) {
                SyncStatus.error = "Could not finish updating. Your pending changes are saved.";
                return Result.retry();
            } finally {
                SyncStatus.end();
            }
        }
    }
}
