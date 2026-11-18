package com.ahmadhaaroon.i221295.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.ahmadhaaroon.i221295.MyProfileActivity;
import com.ahmadhaaroon.i221295.ProfileActivity;
import com.ahmadhaaroon.i221295.models.Post;

/** Reactions, sharing and deleting posts; shared by Home, both profiles and post screens. */
public final class PostActions {

    private PostActions() {
    }

    /**
     * One reaction per user per post: picking a reaction replaces the old one, picking the same
     * reaction again (or tapping Like while reacted) removes it.
     */
    public static void react(Post post, String key, String current) {
        Content.action("posts.react", Content.value(post.id, "key", key == null || key.equals(current) ? "" : key));
    }

    public static void toggleLike(Post post, String current) {
        react(post, current == null ? "like" : null, current);
    }

    /** Sends the post into a friend's chat as a "post" message and bumps the share counter. */
    public static void share(Activity a, Post post) {
        com.ahmadhaaroon.i221295.ui.FriendPicker.show(
                a,
                "Share post",
                uid -> {
                    com.ahmadhaaroon.i221295.models.Message m =
                            Chats.text(post.text == null ? "Shared a post" : post.text);
                    m.type = com.ahmadhaaroon.i221295.models.Message.POST;
                    m.postId = post.id;
                    Chats.send(uid, m);
                });
    }

    public static void delete(Post post) {
        Content.action("posts.delete", Content.data(post.id));
    }

    public static void openProfile(Context c, String uid) {
        if (uid == null) return;
        if (uid.equals(Db.uid())) c.startActivity(new Intent(c, MyProfileActivity.class));
        else c.startActivity(ProfileActivity.intent(c, uid));
    }

    /** "1.2k" style counts used under posts. */
    public static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "k";
        return trim(n / 1_000_000.0) + "m";
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
