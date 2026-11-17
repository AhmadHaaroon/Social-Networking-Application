package com.ahmadhaaroon.i221295.util;

import com.ahmadhaaroon.i221295.models.User;

import org.json.JSONException;
import org.json.JSONObject;

/** Converts the profile shared by PHP and SQLite to the existing screen model. */
public final class UserJson {

    private UserJson() {
    }

    public static User read(JSONObject json) {
        User u = new User();
        u.uid = json.optString("uid", "");
        u.name = json.optString("name", "");
        u.firstName = json.optString("firstName", "");
        u.lastName = json.optString("lastName", "");
        u.email = json.optString("email", "");
        u.username = json.optString("username", "");
        u.usernameLower = json.optString("usernameLower", "");
        u.nameLower = json.optString("nameLower", "");
        u.bio = json.optString("bio", "");
        u.website = json.optString("website", "");
        u.phone = json.optString("phone", "");
        u.gender = json.optString("gender", "");
        u.birthday = json.optString("birthday", "");
        u.photoUrl = json.optString("photoUrl", "");
        u.coverUrl = json.optString("coverUrl", "");
        u.work = json.optString("work", "");
        u.school = json.optString("school", "");
        u.location = json.optString("location", "");
        u.fcmToken = json.optString("fcmToken", "");
        u.online = json.optBoolean("online");
        u.profileComplete = json.optBoolean("profileComplete");
        u.lastSeen = json.optLong("lastSeen");
        u.createdAt = json.optLong("createdAt");
        return u;
    }

    public static JSONObject write(User u) {
        JSONObject json = new JSONObject();
        try {
            json.put("uid", u.uid);
            json.put("name", u.name);
            json.put("firstName", u.firstName);
            json.put("lastName", u.lastName);
            json.put("email", u.email);
            json.put("username", u.username);
            json.put("usernameLower", u.usernameLower);
            json.put("nameLower", u.nameLower);
            json.put("bio", u.bio);
            json.put("website", u.website);
            json.put("phone", u.phone);
            json.put("gender", u.gender);
            json.put("birthday", u.birthday);
            json.put("photoUrl", u.photoUrl);
            json.put("coverUrl", u.coverUrl);
            json.put("work", u.work);
            json.put("school", u.school);
            json.put("location", u.location);
            json.put("fcmToken", u.fcmToken);
            json.put("online", u.online);
            json.put("profileComplete", u.profileComplete);
            json.put("lastSeen", u.lastSeen);
            json.put("createdAt", u.createdAt);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return json;
    }
}
