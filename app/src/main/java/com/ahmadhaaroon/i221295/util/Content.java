package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.ahmadhaaroon.i221295.models.Post;
import com.ahmadhaaroon.i221295.models.Comment;
import com.ahmadhaaroon.i221295.models.Story;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Content screens always read SQLite; mutations are durably queued before returning. */
public final class Content {

    private static Context app;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Content() {
    }

    public static void init(Context context) {
        app = context.getApplicationContext();
    }

    public static void action(String operation, JSONObject data) {
        action(operation, data, () -> {}, e -> Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    public static JSONObject data(String id) {
        try {
            return new JSONObject().put("id", id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static JSONObject value(String id, String key, Object value) {
        try {
            return data(id).put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void action(String operation, JSONObject data, Runnable done, Callback<Exception> failed) {
        String token = WebSession.token();
        IO.execute(() -> {
            try {
                synchronized (Profiles.LOCK) {
                    if (WebSession.uid() == null || !token.equals(WebSession.token())) throw new IllegalStateException("Please log in again.");
                    JSONObject p = new JSONObject().put("actionId", UUID.randomUUID().toString())
                            .put("operation", operation).put("data", data);
                    LocalDb.get().enqueueContent(WebSession.uid(), p);
                }
                SyncWorker.retryNow(app);
                MAIN.post(done);
            } catch (Exception e) {
                MAIN.post(() -> failed.onResult(e));
            }
        });
    }

    public static List<Post> posts(String owner) {
        List<Post> list = new ArrayList<>();
        for (JSONObject row : LocalDb.get().content("posts")) {
            Post p = ContentJson.read(row, Post.class);
            if (owner == null || owner.equals(p.uid)) list.add(p);
        }
        list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list;
    }

    public static Post post(String id) {
        for (Post p : posts(null)) if (p.id.equals(id)) return p;
        return null;
    }

    public static List<Comment> comments(String postId) {
        List<Comment> list = new ArrayList<>();
        for (JSONObject row : LocalDb.get().content("comments")) {
            if (postId.equals(row.optString("postId"))) list.add(ContentJson.read(row, Comment.class));
        }
        list.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        return list;
    }

    public static Map<String, String> reactions(String postId) {
        Map<String, String> map = new LinkedHashMap<>();
        for (JSONObject row : LocalDb.get().content("reactions")) {
            if (postId.equals(row.optString("postId"))) map.put(row.optString("uid"), row.optString("key"));
        }
        return map;
    }

    public static Map<String, Map<String, Boolean>> likes() {
        Map<String, Map<String, Boolean>> map = new LinkedHashMap<>();
        for (JSONObject row : LocalDb.get().content("comment_likes")) {
            String id = row.optString("commentId");
            if (!map.containsKey(id)) map.put(id, new LinkedHashMap<>());
            map.get(id).put(row.optString("uid"), true);
        }
        return map;
    }

    public static List<Story> stories(String owner) {
        List<Story> list = new ArrayList<>();
        for (JSONObject row : LocalDb.get().content("stories")) {
            Story story = ContentJson.read(row, Story.class);
            if ((owner == null || owner.equals(story.uid)) && story.isActive(System.currentTimeMillis())) list.add(story);
        }
        list.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        return list;
    }

    public static List<String> viewers(String story) {
        List<String> out = new ArrayList<>();
        for (JSONObject row : LocalDb.get().content("story_views")) if (story.equals(row.optString("storyId"))) out.add(row.optString("uid"));
        return out;
    }

    public static String status(String id) {
        return LocalDb.get().contentStatus(id);
    }
}
