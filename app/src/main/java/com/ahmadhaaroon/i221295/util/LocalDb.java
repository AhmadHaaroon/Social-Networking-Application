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
        super(context, "kinnect.db", null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (uid TEXT PRIMARY KEY, json TEXT NOT NULL, server_json TEXT NOT NULL)");
        db.execSQL("CREATE TABLE pending_actions (sequence INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "action_id TEXT NOT NULL UNIQUE, user_id TEXT NOT NULL, operation TEXT NOT NULL, "
                + "payload TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending', error TEXT)");
        db.execSQL("CREATE TABLE revocations (id INTEGER PRIMARY KEY AUTOINCREMENT, token TEXT NOT NULL)");
        // Feature stages add their query columns when their cache becomes active.
        for (String table : new String[]{"messages", "posts", "stories", "friends"}) {
            db.execSQL("CREATE TABLE " + table + " (id TEXT PRIMARY KEY, json TEXT NOT NULL)");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations must preserve pending actions rather than dropping the database.
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT uid FROM users WHERE uid != ? LIMIT 1", new String[]{uid})) {
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
        try (Cursor c = db.rawQuery("SELECT payload FROM pending_actions WHERE user_id = ? AND state = 'pending' ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
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
            db.delete("pending_actions", "user_id = ? AND state = 'failed'", new String[]{uid});
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
        // Initial setup is complete only after the server has accepted the unique username.
    }

    public synchronized JSONObject next(String uid) throws Exception {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM pending_actions WHERE user_id = ? AND state = 'pending' ORDER BY sequence LIMIT 1", new String[]{uid})) {
            return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM pending_actions WHERE user_id = ? AND state = 'failed' ORDER BY sequence DESC LIMIT 1", new String[]{uid})) {
            return c.moveToFirst() ? new JSONObject(c.getString(0)) : null;
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
            for (String table : new String[]{"users", "pending_actions", "messages", "posts", "stories", "friends"}) {
                db.delete(table, null, null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
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
}
