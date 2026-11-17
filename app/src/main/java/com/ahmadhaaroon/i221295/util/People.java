package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.ahmadhaaroon.i221295.models.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cached users and friend actions shared by Search and the two profile screens. */
public final class People {

    private static Context app;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private People() {
    }

    public static void init(Context context) {
        app = context.getApplicationContext();
    }

    public static void action(String other, String kind) {
        String uid = Db.uid();
        IO.execute(() -> {
            try {
                synchronized (Profiles.LOCK) {
                    if (uid == null || !uid.equals(Db.uid())) return;
                    JSONObject p = new JSONObject().put("actionId", UUID.randomUUID().toString())
                            .put("other", other).put("kind", kind);
                    LocalDb.get().enqueueFriend(uid, p);
                }
                SyncWorker.retryNow(app);
            } catch (Exception e) {
                MAIN.post(() -> Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    public static List<String> friends(String uid) {
        List<String> out = new ArrayList<>();
        if (uid == null) return out;
        for (JSONObject row : LocalDb.get().relations()) {
            if (!"accepted".equals(row.optString("state"))) continue;
            if (uid.equals(row.optString("a"))) out.add(row.optString("b"));
            else if (uid.equals(row.optString("b"))) out.add(row.optString("a"));
        }
        return out;
    }

    public static JSONObject relation(String other) {
        String me = Db.uid();
        if (me == null || other == null) return null;
        for (JSONObject row : LocalDb.get().relations()) {
            if ((me.equals(row.optString("a")) && other.equals(row.optString("b")))
                    || (other.equals(row.optString("a")) && me.equals(row.optString("b")))) return row;
        }
        return null;
    }

    public static int state(String other) {
        if (other != null && other.equals(Db.uid())) return Friends.SELF;
        JSONObject row = relation(other);
        if (row == null) return Friends.NONE;
        if ("accepted".equals(row.optString("state"))) return Friends.FRIENDS;
        return row.optString("requester").equals(Db.uid()) ? Friends.REQUESTED : Friends.INCOMING;
    }

    public static List<String> cachedSearch(String q, String filter) {
        List<String> result = new ArrayList<>();
        String term = q.toLowerCase(Locale.ROOT);
        for (User user : LocalDb.get().users()) {
            if (user.uid.equals(Db.uid()) || !user.profileComplete) continue;
            int state = state(user.uid);
            if ("friends".equals(filter) && state != Friends.FRIENDS) continue;
            if ("requests".equals(filter) && state != Friends.REQUESTED && state != Friends.INCOMING) continue;
            if (user.displayName().toLowerCase(Locale.ROOT).contains(term)
                    || (user.username != null && user.username.toLowerCase(Locale.ROOT).contains(term))) result.add(user.uid);
        }
        result.sort((a, b) -> LocalDb.get().user(a).displayName().compareToIgnoreCase(LocalDb.get().user(b).displayName()));
        return result;
    }

    public static void search(String q, String filter, Callback<List<String>> done) {
        String token = WebSession.token();
        IO.execute(() -> {
            try {
                JSONObject response = Api.call("users/search.php?q=" + URLEncoder.encode(q, "UTF-8")
                        + "&filter=" + filter, null, token);
                synchronized (Profiles.LOCK) {
                    if (!token.equals(WebSession.token())) return;
                    JSONArray users = response.getJSONArray("users");
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        LocalDb.get().accept(u);
                    }
                }
                MAIN.post(() -> {
                    if (token.equals(WebSession.token())) done.onResult(cachedSearch(q, filter));
                });
            } catch (Exception ignored) {
                // The already displayed SQLite results remain available offline.
            }
        });
    }
}
