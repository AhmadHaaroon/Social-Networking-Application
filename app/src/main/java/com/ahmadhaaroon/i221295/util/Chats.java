package com.ahmadhaaroon.i221295.util;

import com.ahmadhaaroon.i221295.models.Message;
import com.ahmadhaaroon.i221295.models.ChatSummary;
import org.json.JSONObject;
import java.util.*;

/** SQLite conversations and ordered, idempotent web-service actions. */
public final class Chats {
    private Chats() {}

    public static Message text(String text) {
        Message m = new Message();
        m.text = text;
        return m;
    }

    public static void send(String other, Message m) {
        send(other, m, () -> {});
    }

    public static void send(String other, Message m, Runnable done) {
        m.id = UUID.randomUUID().toString();
        m.senderId = Db.uid();
        m.recipientId = other;
        m.timestamp = System.currentTimeMillis();
        Content.action(
                "messages.create",
                ContentJson.write(m),
                done,
                e ->
                        android.widget.Toast.makeText(
                                        Realtime.context(),
                                        e.getMessage(),
                                        android.widget.Toast.LENGTH_LONG)
                                .show());
    }

    public static void edit(String other, Message m, String text) {
        Content.action("messages.edit", Content.value(m.id, "text", text));
    }

    public static void delete(String other, Message m) {
        Content.action("messages.delete", Content.data(m.id));
    }

    public static void toggleHeart(String other, Message m) {
        Content.action(
                "messages.heart",
                Content.value(m.id, "liked", !Boolean.TRUE.equals(m.hearts.get(Db.uid()))));
    }

    public static List<Message> messages(String other) {
        List<Message> out = new ArrayList<>();
        String me = Db.uid();
        for (JSONObject r : LocalDb.get().content("messages")) {
            Message m = ContentJson.read(r, Message.class);
            if ((Objects.equals(me, m.senderId) && Objects.equals(other, m.recipientId))
                    || (Objects.equals(other, m.senderId) && Objects.equals(me, m.recipientId)))
                out.add(m);
        }
        out.sort((a, b) -> {
            int t = Long.compare(a.timestamp, b.timestamp);
            return t == 0 ? a.id.compareTo(b.id) : t;
        });
        return out;
    }

    public static JSONObject read(String owner, String other) {
        for (JSONObject r : LocalDb.get().content("chat_reads"))
            if (owner.equals(r.optString("owner")) && other.equals(r.optString("other"))) return r;
        return new JSONObject();
    }

    public static void markRead(String other) {
        if (Db.uid() == null || other == null) return;
        long through = 0;
        for (Message m : messages(other))
            if (other.equals(m.senderId) && !m.pending) through = Math.max(through, m.timestamp);
        if (through > read(Db.uid(), other).optLong("readAt"))
            actionRead(other, "chat.read", through);
    }

    public static void hide(String other) {
        long through = 0;
        for (Message m : messages(other)) through = Math.max(through, m.timestamp);
        actionRead(other, "chat.hide", through);
    }

    private static void actionRead(String other, String op, long through) {
        try {
            Content.action(
                    op,
                    Content.value(UUID.randomUUID().toString(), "other", other)
                            .put("through", through));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<ChatSummary> summaries() {
        Map<String, ChatSummary> map = new HashMap<>();
        String me = Db.uid();
        if (me == null) return new ArrayList<>();
        for (JSONObject r : LocalDb.get().content("messages")) {
            Message m = ContentJson.read(r, Message.class);
            String other = me.equals(m.senderId) ? m.recipientId : m.senderId;
            JSONObject read = read(me, other);
            if (m.timestamp <= read.optLong("hiddenAt")) continue;
            ChatSummary c = map.get(other);
            if (c == null) {
                c = new ChatSummary();
                c.otherUid = other;
                c.chatId = Db.chatId(me, other);
                map.put(other, c);
            }
            if (!me.equals(m.senderId) && m.timestamp > read.optLong("readAt")) c.unread++;
            if (m.timestamp >= c.timestamp) {
                c.timestamp = m.timestamp;
                c.lastMessage = preview(m);
                c.lastSenderId = m.senderId;
            }
        }
        List<ChatSummary> out = new ArrayList<>(map.values());
        out.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return out;
    }

    public static String preview(Message m) {
        if (m.deleted) return "Message deleted";
        switch (m.type) {
            case Message.IMAGE:
                return "Sent a photo";
            case Message.VIDEO:
                return "Sent a video";
            case Message.FILE:
                return "Sent a file";
            case Message.POST:
                return "Shared a post";
            default:
                return m.text == null ? "" : m.text;
        }
    }
}
