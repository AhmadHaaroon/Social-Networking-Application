package com.ahmadhaaroon.i221295.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import com.ahmadhaaroon.i221295.models.User;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** SQLite holds accepted profiles, visible local edits and the ordered action queue. */
public final class LocalDb extends SQLiteOpenHelper {

    private static LocalDb instance;
    private static final List<Runnable> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static synchronized void init(Context context) {
        instance = new LocalDb(context.getApplicationContext());
    }

    public static LocalDb get() {
        return instance;
    }

    private LocalDb(Context context) {
        super(context, "kinnect.db", null, 3);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (uid TEXT PRIMARY KEY, json TEXT NOT NULL, server_json TEXT NOT NULL)");
        db.execSQL("CREATE TABLE pending_actions (sequence INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "action_id TEXT NOT NULL UNIQUE, user_id TEXT NOT NULL, operation TEXT NOT NULL, "
                + "payload TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending', error TEXT)");
        db.execSQL("CREATE TABLE revocations (id INTEGER PRIMARY KEY AUTOINCREMENT, token TEXT NOT NULL)");
        db.execSQL("CREATE TABLE server_friends (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
        db.execSQL("CREATE TABLE cache_meta (name TEXT PRIMARY KEY, value TEXT)");
        createContent(db);
        // Feature stages add their query columns when their cache becomes active.
        for (String table : new String[]{"messages", "posts", "stories", "friends"}) {
            db.execSQL("CREATE TABLE " + table + " (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE server_friends (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
            db.execSQL("CREATE TABLE cache_meta (name TEXT PRIMARY KEY, value TEXT)");
            db.execSQL("INSERT INTO cache_meta SELECT 'owner', uid FROM users LIMIT 1");
        }
        if (oldVersion < 3) createContent(db);
    }

    private void createContent(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE server_content (kind TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(kind, id))");
        db.execSQL("CREATE TABLE content_cache (kind TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(kind, id))");
    }

    public static void observe(Runnable observer) {
        OBSERVERS.add(observer);
    }

    public static void removeObserver(Runnable observer) {
        OBSERVERS.remove(observer);
    }

    public static void changed() {
        MAIN.post(() -> {
            for (Runnable observer : OBSERVERS) observer.run();
        });
    }

    public synchronized boolean containsOnly(String uid) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM cache_meta WHERE name = 'owner' AND value != ?", new String[]{uid})) {
            return !c.moveToFirst();
        }
    }

    public synchronized User user(String uid) {
        if (uid == null) return null;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM users WHERE uid = ?", new String[]{uid})) {
            return c.moveToFirst() ? UserJson.read(new JSONObject(c.getString(0))) : null;
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void accept(JSONObject json) throws Exception {
        String uid = json.getString("uid");
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uid", uid);
        values.put("server_json", json.toString());
        try (Cursor c = db.rawQuery("SELECT payload FROM pending_actions WHERE user_id = ? AND operation = 'profile.update' AND state = 'pending' ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
            User visible = UserJson.read(json);
            if (c.moveToFirst()) applyProfile(visible, new JSONObject(c.getString(0)));
            values.put("json", UserJson.write(visible).toString());
        }
        db.insertWithOnConflict("users", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void enqueue(String uid, JSONObject payload) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            User visible = user(uid);
            if (visible == null) throw new IllegalStateException("Please log in again.");
            ContentValues action = new ContentValues();
            action.put("action_id", payload.getString("actionId"));
            action.put("user_id", uid);
            action.put("operation", "profile.update");
            action.put("payload", payload.toString());
            db.insertOrThrow("pending_actions", null, action);
            applyProfile(visible, payload);
            ContentValues row = new ContentValues();
            row.put("json", UserJson.write(visible).toString());
            db.update("users", row, "uid = ?", new String[]{uid});
            db.delete("pending_actions", "user_id = ? AND operation = 'profile.update' AND state = 'failed'", new String[]{uid});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    private static void applyProfile(User u, JSONObject p) {
        u.name = p.optString("name");
        String[] parts = u.name.split("\\s+", 2);
        u.firstName = parts[0];
        u.lastName = parts.length > 1 ? parts[1] : "";
        u.username = p.optString("username");
        u.bio = p.optString("bio");
        u.website = p.optString("website");
        u.phone = p.optString("phone");
        u.work = p.optString("work");
        u.school = p.optString("school");
        u.location = p.optString("location");
        u.gender = p.optString("gender");
        if (p.has("photoUrl")) u.photoUrl = p.optString("photoUrl");
        if (p.has("coverUrl")) u.coverUrl = p.optString("coverUrl");
        // Initial setup is complete only after the server has accepted the unique username.
    }

    public synchronized JSONObject next(String uid) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload, operation FROM pending_actions WHERE user_id = ? AND state = 'pending' ORDER BY sequence LIMIT 1", new String[]{uid})) {
            if (!c.moveToFirst()) return null;
            JSONObject p = new JSONObject(c.getString(0));
            if (c.getColumnCount() > 1) p.put("operation", c.getString(1));
            return p;
        }
    }

    public synchronized void complete(String id, JSONObject user) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("pending_actions", "action_id = ?", new String[]{id});
            accept(user);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void reject(String id, String uid, String message) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("state", "failed");
            row.put("error", message);
            db.update("pending_actions", row, "action_id = ?", new String[]{id});
            try (Cursor c = db.rawQuery("SELECT server_json FROM users WHERE uid = ?", new String[]{uid})) {
                if (c.moveToFirst()) accept(new JSONObject(c.getString(0)));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized String status(String uid) {
        if (uid == null) return "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT state, error FROM pending_actions WHERE user_id = ? ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
            if (!c.moveToFirst()) return "";
            return "failed".equals(c.getString(0)) ? "Couldn't sync: " + c.getString(1)
                    : "Pending — saved on this device. Waiting to sync.";
        }
    }

    public synchronized JSONObject failedProfile(String uid) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM pending_actions WHERE user_id = ? AND operation = 'profile.update' AND state = 'failed' ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
            if (!c.moveToFirst()) return null;
            JSONObject p = new JSONObject(c.getString(0));
            return p;
        }
    }

    public synchronized void revokeAndClear(String token) throws Exception {
        String encrypted = token.isEmpty() ? null : SecureStore.encrypt(token);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (encrypted != null) {
                ContentValues row = new ContentValues();
                row.put("token", encrypted);
                db.insertOrThrow("revocations", null, row);
            }
            for (String table : new String[]{"users", "pending_actions", "messages", "posts", "stories", "friends", "server_friends", "cache_meta", "server_content", "content_cache"}) {
                db.delete(table, null, null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void setOwner(String uid) {
        ContentValues v = new ContentValues();
        v.put("name", "owner");
        v.put("value", uid);
        getWritableDatabase().insertWithOnConflict("cache_meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<User> users() {
        List<User> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM users", null)) {
            while (c.moveToNext()) list.add(UserJson.read(new JSONObject(c.getString(0))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    public synchronized List<JSONObject> relations() {
        List<JSONObject> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM friends", null)) {
            while (c.moveToNext()) list.add(new JSONObject(c.getString(0)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return list;
    }

    private void rebaseFriends() throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("friends", null, null);
        db.execSQL("INSERT INTO friends SELECT * FROM server_friends");
        try (Cursor c = db.rawQuery("SELECT payload, user_id FROM pending_actions WHERE operation = 'friends.action' AND state = 'pending' ORDER BY sequence", null)) {
            while (c.moveToNext()) {
                JSONObject p = new JSONObject(c.getString(0));
                String me = c.getString(1), other = p.getString("other");
                String a = me.compareTo(other) < 0 ? me : other;
                String b = me.equals(a) ? other : me;
                String id = a + ":" + b;
                JSONObject row = null;
                for (JSONObject r : relations()) if (id.equals(r.optString("id"))) row = r;
                String kind = p.getString("kind");
                if ("cancel".equals(kind) || "decline".equals(kind) || "unfriend".equals(kind)) {
                    db.delete("friends", "id = ?", new String[]{id});
                    continue;
                }
                if (row == null) row = new JSONObject().put("id", id).put("a", a).put("b", b)
                        .put("requester", me).put("state", "pending").put("timestamp", System.currentTimeMillis());
                if ("accept".equals(kind)) row.put("state", "accepted");
                if ("close".equals(kind) || "unclose".equals(kind)) row.put("close", "close".equals(kind));
                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("json", row.toString());
                db.insertWithOnConflict("friends", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

    public synchronized void acceptSocial(JSONObject response) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONArray users = response.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) accept(users.getJSONObject(i));
            db.delete("server_friends", null, null);
            JSONArray rows = response.getJSONArray("relations");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("id", r.getString("id"));
                v.put("json", r.toString());
                db.insertOrThrow("server_friends", null, v);
            }
            rebaseFriends();
            if (response.has("content")) acceptContent(response.getJSONObject("content"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void enqueueFriend(String uid, JSONObject p) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<String> oldFailures = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT action_id, payload FROM pending_actions WHERE operation = 'friends.action' AND state = 'failed'", null)) {
                while (c.moveToNext()) if (p.getString("other").equals(new JSONObject(c.getString(1)).optString("other"))) oldFailures.add(c.getString(0));
            }
            for (String id : oldFailures) db.delete("pending_actions", "action_id = ?", new String[]{id});
            ContentValues v = new ContentValues();
            v.put("action_id", p.getString("actionId"));
            v.put("user_id", uid);
            v.put("operation", "friends.action");
            v.put("payload", p.toString());
            db.insertOrThrow("pending_actions", null, v);
            rebaseFriends();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void completeFriend(String id, JSONObject response) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("pending_actions", "action_id = ?", new String[]{id});
            acceptSocial(response);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void rejectFriend(String id, String error) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("state", "failed");
            v.put("error", error);
            db.update("pending_actions", v, "action_id = ?", new String[]{id});
            rebaseFriends();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized boolean friendPending(String other) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM pending_actions WHERE operation = 'friends.action' AND state = 'pending'", null)) {
            while (c.moveToNext()) if (other.equals(new JSONObject(c.getString(0)).optString("other"))) return true;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return false;
    }

    public synchronized String profileStatus(String uid) {
        if (uid == null) return "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT state, error FROM pending_actions WHERE user_id = ? AND operation = 'profile.update' ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
            if (!c.moveToFirst()) return "";
            return "failed".equals(c.getString(0)) ? "Couldn't sync: " + c.getString(1) : "Pending";
        }
    }

    public synchronized List<String[]> revocations() {
        List<String[]> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, token FROM revocations ORDER BY id", null)) {
            while (c.moveToNext()) list.add(new String[]{c.getString(0), c.getString(1)});
        }
        return list;
    }

    public synchronized void revoked(String id) {
        getWritableDatabase().delete("revocations", "id = ?", new String[]{id});
    }
    public synchronized List<JSONObject> content(String kind) {
        purgeStories();
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT json FROM content_cache WHERE kind = ?", new String[]{kind})) {
            while (c.moveToNext()) out.add(new JSONObject(c.getString(0)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private void putContent(String table, String kind, JSONObject row) throws Exception {
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("id", row.getString("id"));
        v.put("json", row.toString());
        getWritableDatabase().insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void removeContent(String kind, String id) {
        getWritableDatabase().delete("content_cache", "kind = ? AND id = ?", new String[]{kind, id});
    }

    private void purgeStories() {
        SQLiteDatabase db = getWritableDatabase();
        for (String table : new String[]{"server_content", "content_cache"}) {
            List<String> expired = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT id, json FROM " + table + " WHERE kind = 'stories'", null)) {
                while (c.moveToNext()) if (new JSONObject(c.getString(1)).optLong("expiresAt") <= System.currentTimeMillis()) expired.add(c.getString(0));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            for (String id : expired) db.delete(table, "kind = 'stories' AND id = ?", new String[]{id});
        }
    }

    private void acceptContent(JSONObject state) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("server_content", null, null);
        String[] kinds = {"posts", "comments", "reactions", "comment_likes", "stories", "story_views"};
        for (String kind : kinds) {
            JSONArray rows = state.getJSONArray(kind);
            for (int i = 0; i < rows.length(); i++) putContent("server_content", kind, rows.getJSONObject(i));
        }
        rebaseContent();
    }

    private void rebaseContent() throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("content_cache", null, null);
        db.execSQL("INSERT INTO content_cache SELECT * FROM server_content");
        try (Cursor c = db.rawQuery("SELECT operation, payload, user_id, state, error FROM pending_actions WHERE operation LIKE 'posts.%' OR operation LIKE 'comments.%' OR operation LIKE 'stories.%' ORDER BY sequence", null)) {
            while (c.moveToNext()) {
                String op = c.getString(0), me = c.getString(2);
                JSONObject p = new JSONObject(c.getString(1)).getJSONObject("data");
                String id = p.getString("id");
                String kind = op.substring(0, op.indexOf('.'));
                boolean failed = "failed".equals(c.getString(3));
                if (op.endsWith(".create")) {
                    p.put("uid", me).put("pending", !failed);
                    if (failed) p.put("syncError", c.getString(4));
                    putContent("content_cache", kind, p);
                } else if (!failed && op.endsWith(".delete")) {
                    removeContent(kind, id);
                    if (kind.equals("posts")) {
                        for (JSONObject row : content("comments")) if (id.equals(row.optString("postId"))) removeContent("comments", row.getString("id"));
                    } else if (kind.equals("comments")) {
                        for (JSONObject row : content("comments")) if (id.equals(row.optString("parentId"))) removeContent("comments", row.getString("id"));
                    }
                } else if (!failed && op.equals("posts.react")) {
                    String key = p.optString("key");
                    if (key.isEmpty()) removeContent("reactions", id + ":" + me);
                    else putContent("content_cache", "reactions", new JSONObject().put("id", id + ":" + me).put("postId", id).put("uid", me).put("key", key));
                } else if (!failed && op.equals("comments.like")) {
                    if (!p.optBoolean("liked")) removeContent("comment_likes", id + ":" + me);
                    else putContent("content_cache", "comment_likes", new JSONObject().put("id", id + ":" + me).put("commentId", id).put("uid", me));
                }
            }
        }
        purgeStories();
    }

    public synchronized void enqueueContent(String uid, JSONObject payload) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("action_id", payload.getString("actionId"));
            row.put("user_id", uid);
            row.put("operation", payload.getString("operation"));
            row.put("payload", payload.toString());
            db.insertOrThrow("pending_actions", null, row);
            rebaseContent();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void completeContent(String id, JSONObject state) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("pending_actions", "action_id = ?", new String[]{id});
            acceptSocial(state);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized void rejectContent(String id, String error) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues row = new ContentValues();
            row.put("state", "failed"); row.put("error", error);
            db.update("pending_actions", row, "action_id = ?", new String[]{id});
            rebaseContent();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
    }

    public synchronized String contentStatus(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload, state, error FROM pending_actions WHERE operation LIKE 'posts.%' OR operation LIKE 'comments.%' OR operation LIKE 'stories.%' ORDER BY sequence DESC", null)) {
            while (c.moveToNext()) {
                JSONObject p = new JSONObject(c.getString(0)).optJSONObject("data");
                if (p != null && (id.equals(p.optString("id")) || id.equals(p.optString("postId")))) return "failed".equals(c.getString(1)) ? "Couldn't sync: " + c.getString(2) : "Pending";
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return "";
    }

}
