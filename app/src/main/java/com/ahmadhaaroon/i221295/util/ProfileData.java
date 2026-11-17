package com.ahmadhaaroon.i221295.util;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.ahmadhaaroon.i221295.models.Post;

import java.util.ArrayList;
import java.util.List;

/** Listeners shared by both profile screens. */
public final class ProfileData {

    private ProfileData() {
    }

    /** All posts by {@code uid}, newest first (uses the ".indexOn": "uid" rule). */
    public static void posts(Subs subs, String uid, Callback<List<Post>> cb) {
        subs.value(Db.posts().orderByChild("uid").equalTo(uid), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                List<Post> list = new ArrayList<>();
                for (DataSnapshot c : s.getChildren()) {
                    Post p = c.getValue(Post.class);
                    if (p == null) continue;
                    p.id = c.getKey();
                    list.add(p);
                }
                list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
                cb.onResult(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
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
