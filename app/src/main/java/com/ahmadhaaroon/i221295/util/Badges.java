package com.ahmadhaaroon.i221295.util;
import android.view.View;
import android.widget.TextView;

/** Counters share the same local snapshots as their screens. */
public final class Badges {
    private Badges() {}

    private static void watch(
            Subs subs, TextView badge, java.util.concurrent.Callable<Integer> count) {
        if (badge == null) return;
        Runnable changed =
                () -> {
                    try {
                        int n = count.call();
                        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
                        badge.setText(n > 9 ? "9+" : String.valueOf(n));
                    } catch (Exception ignored) {
                    }
                };
        LocalDb.observe(changed);
        subs.add(() -> LocalDb.removeObserver(changed));
        changed.run();
    }

    public static void messages(Subs subs, TextView badge) {
        watch(
                subs,
                badge,
                () -> {
                    int n = 0;
                    for (com.ahmadhaaroon.i221295.models.ChatSummary c : Chats.summaries())
                        if (c.unread > 0) n++;
                    return n;
                });
    }

    public static void notifications(Subs subs, TextView badge) {
        watch(
                subs,
                badge,
                () -> {
                    int n = 0;
                    for (org.json.JSONObject r : LocalDb.get().content("notifications"))
                        if (!r.optBoolean("read")) n++;
                    return n;
                });
    }

    public static void requests(Subs subs, TextView badge) {
        watch(
                subs,
                badge,
                () -> {
                    int n = 0;
                    for (org.json.JSONObject r : LocalDb.get().relations())
                        if ("pending".equals(r.optString("state"))
                                && !Db.uid().equals(r.optString("requester"))
                                && (Db.uid().equals(r.optString("a"))
                                        || Db.uid().equals(r.optString("b")))) n++;
                    return n;
                });
    }
}
