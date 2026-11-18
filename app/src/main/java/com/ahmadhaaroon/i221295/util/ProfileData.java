package com.ahmadhaaroon.i221295.util;

import com.ahmadhaaroon.i221295.models.Post;

import java.util.ArrayList;
import java.util.List;

/** Listeners shared by both profile screens. */
public final class ProfileData {

    private ProfileData() {
    }

    /** All posts by {@code uid}, newest first (uses the ".indexOn": "uid" rule). */
    public static void posts(Subs subs, String uid, Callback<List<Post>> cb) {
        Runnable changed = () -> cb.onResult(Content.posts(uid));
        LocalDb.observe(changed);
        subs.add(() -> LocalDb.removeObserver(changed));
        changed.run();
    }

    /** Friend uids of {@code uid}, most recently added first. */
    public static void friends(Subs subs, String uid, Callback<List<String>> cb) {
        Runnable changed = () -> cb.onResult(People.friends(uid));
        LocalDb.observe(changed);
        subs.add(() -> LocalDb.removeObserver(changed));
        changed.run();
    }

    /** Adds or removes someone from my Close friends list. */
    public static void setCloseFriend(String friendUid, boolean close) {
        People.action(friendUid, close ? "close" : "unclose");
    }

    public static List<String> allImages(List<Post> posts) {
        List<String> out = new ArrayList<>();
        for (Post p : posts) if (p.images != null) out.addAll(p.images);
        return out;
    }
}
