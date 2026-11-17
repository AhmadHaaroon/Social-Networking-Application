package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** The session is read locally, so opening the app never requires a network response. */
public final class WebSession {

    private static SharedPreferences prefs;
    private static JSONObject current = new JSONObject();

    private WebSession() {
    }

    public static synchronized void init(Context context) {
        prefs = context.getSharedPreferences("kinnect_secure_session", Context.MODE_PRIVATE);
        String stored = prefs.getString("session", null);
        if (stored == null) return;
        try {
            current = new JSONObject(SecureStore.decrypt(stored));
        } catch (Exception e) {
            current = new JSONObject();
            prefs.edit().remove("session").commit();
        }
    }

    public static synchronized void save(JSONObject result) throws Exception {
        JSONObject next = new JSONObject();
        next.put("token", result.getString("token"));
        next.put("uid", result.getJSONObject("user").getString("uid"));
        next.put("expiresAt", result.getLong("expiresAt"));
        if (!prefs.edit().putString("session", SecureStore.encrypt(next.toString())).commit()) {
            throw new IllegalStateException("Could not save your session on this device.");
        }
        current = next;
    }

    public static synchronized boolean valid() {
        return !current.optString("token").isEmpty() && current.optLong("expiresAt") > System.currentTimeMillis();
    }

    public static synchronized String uid() {
        return valid() ? current.optString("uid", null) : null;
    }

    public static synchronized String token() {
        return valid() ? current.optString("token", "") : "";
    }

    public static synchronized void clear() {
        current = new JSONObject();
        prefs.edit().remove("session").commit();
    }
}
