package com.ahmadhaaroon.i221295.util;

import com.google.firebase.database.DatabaseReference;
import com.ahmadhaaroon.i221295.models.AppNotification;

/** Writes in-app notifications shown on the Notifications screen (18). */
public final class Notifs {

    private Notifs() {
    }

    public static void send(String toUid, String type, String postId, String text) {
        String me = Db.uid();
        if (me == null || toUid == null || toUid.equals(me)) return;
        DatabaseReference ref = Db.notifications(toUid).push();
        AppNotification n = new AppNotification();
        n.id = ref.getKey();
        n.type = type;
        n.fromUid = me;
        n.postId = postId;
        n.text = text;
        n.timestamp = System.currentTimeMillis();
        ref.setValue(n);
    }
}
