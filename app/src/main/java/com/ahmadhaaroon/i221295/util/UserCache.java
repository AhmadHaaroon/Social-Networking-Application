package com.ahmadhaaroon.i221295.util;

import com.ahmadhaaroon.i221295.models.User;

/** Shared SQLite profile cache used by names and avatars across the existing screens. */
public final class UserCache {

    private UserCache() {
    }

    public static User peek(String uid) {
        return LocalDb.get().user(uid);
    }

    public static void get(String uid, Callback<User> cb) {
        if (uid == null) return;
        User user = peek(uid);
        if (user == null) {
            user = new User();
            user.uid = uid;
            user.name = "Kinnect user";
        }
        cb.onResult(user);
    }

    public static void put(User user) {
        if (user == null || user.uid == null) return;
        try {
            LocalDb.get().accept(UserJson.write(user));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void clear() {
        // Profiles.logout clears all user rows in the same transaction as pending content.
    }
}
