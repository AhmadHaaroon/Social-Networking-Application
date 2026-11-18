package com.ahmadhaaroon.i221295;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ahmadhaaroon.i221295.models.Comment;
import com.ahmadhaaroon.i221295.models.Post;
import com.ahmadhaaroon.i221295.models.Story;
import com.ahmadhaaroon.i221295.ui.PhotoCropView;
import com.ahmadhaaroon.i221295.util.Api;
import com.ahmadhaaroon.i221295.util.Connection;
import com.ahmadhaaroon.i221295.util.Content;
import com.ahmadhaaroon.i221295.util.ContentJson;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.SyncWorker;
import com.ahmadhaaroon.i221295.util.WebSession;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Disposable test accounts and a separate install keep demo data out of these workflows. */
@RunWith(AndroidJUnit4.class)
public class WebContentFlowTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.READ_MEDIA_IMAGES);

    private Context context;
    private boolean hadWifi, hadData;
    private String token;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        hadWifi = android.provider.Settings.Global.getInt(context.getContentResolver(), "wifi_on", 1) != 0;
        hadData = android.provider.Settings.Global.getInt(context.getContentResolver(), "mobile_data", 1) != 0;
        synchronized (Profiles.LOCK) {
            LocalDb.get().revokeAndClear(WebSession.token());
            WebSession.clear();
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JSONObject registered = Api.call("auth/signup.php", new JSONObject().put("email", suffix + "@example.com")
                .put("password", "ContentTest42!").put("firstName", "Content").put("lastName", "Tester")
                .put("birthday", "2004-01-01").put("gender", "Custom"), null);
        token = registered.getString("token");
        JSONObject profile = Api.call("profile/update.php", new JSONObject().put("actionId", UUID.randomUUID().toString())
                .put("username", suffix).put("name", "Content Tester").put("bio", "Testing content")
                .put("gender", "Custom"), token);
        registered.put("user", profile.getJSONObject("user"));
        synchronized (Profiles.LOCK) {
            WebSession.save(registered);
            LocalDb.get().setOwner(WebSession.uid());
            LocalDb.get().accept(profile.getJSONObject("user"));
            LocalDb.get().acceptSocial(Api.call("social/state.php", null, token));
        }
    }

    @After
    public void tearDown() throws Exception {
        shell("svc wifi " + (hadWifi ? "enable" : "disable"));
        shell("svc data " + (hadData ? "enable" : "disable"));
        await(() -> Connection.connected(context));
        synchronized (Profiles.LOCK) {
            LocalDb.get().revokeAndClear(WebSession.token());
            WebSession.clear();
        }
        if (token != null) Api.call("auth/logout.php", new JSONObject(), token);
        SyncWorker.retryNow(context);
    }

    @Test
    public void offlinePostCommentReactionAndStory_syncInOrder() throws Exception {
        shell("svc data disable");
        shell("svc wifi disable");
        await(() -> !Connection.connected(context));
        try (ActivityScenario<CreatePostActivity> scenario = ActivityScenario.launch(CreatePostActivity.class)) {
            TestSystemUi.dismissFullscreenHint();
            onView(withId(R.id.post_text)).perform(replaceText("Offline post from Espresso"));
            closeSoftKeyboard();
            onView(withId(R.id.btnPost)).perform(click());
            await(() -> !Content.posts(WebSession.uid()).isEmpty());
        }
        Post post = Content.posts(WebSession.uid()).get(0);
        assertTrue(post.pending);
        Comment comment = new Comment();
        comment.id = UUID.randomUUID().toString(); comment.postId = post.id; comment.uid = WebSession.uid();
        comment.text = "Queued reply target"; comment.timestamp = System.currentTimeMillis();
        queue("comments.create", ContentJson.write(comment));
        queue("posts.react", Content.value(post.id, "key", "love"));
        queue("comments.like", Content.value(comment.id, "liked", true));
        Story story = new Story();
        story.id = UUID.randomUUID().toString(); story.uid = WebSession.uid(); story.text = "Offline story";
        story.mediaType = "text"; story.audience = "public"; story.timestamp = System.currentTimeMillis();
        story.expiresAt = story.timestamp + Story.LIFETIME_MS;
        queue("stories.create", ContentJson.write(story));
        assertEquals("love", Content.reactions(post.id).get(WebSession.uid()));
        assertEquals(1, Content.comments(post.id).size());
        assertTrue(Content.likes().get(comment.id).containsKey(WebSession.uid()));
        LocalDb.get().close();
        LocalDb.init(context);
        assertTrue(Content.post(post.id).pending);
        shell("svc wifi enable");
        await(() -> Connection.connected(context));
        await(() -> LocalDb.get().status(WebSession.uid()).isEmpty());
        assertFalse(Content.post(post.id).pending);
        JSONObject state = Api.call("social/state.php", null, token).getJSONObject("content");
        assertEquals(1, count(state.getJSONArray("posts"), post.id));
        assertEquals(1, count(state.getJSONArray("comments"), comment.id));
        assertEquals(1, count(state.getJSONArray("stories"), story.id));
        assertEquals("love", Content.reactions(post.id).get(WebSession.uid()));
    }

    @Test
    public void circularCrop_exportsMatchingSquare_andCoverExportsTwoToOne() throws Exception {
        File file = new File(context.getCacheDir(), "crop-test.png");
        Bitmap source = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(source);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.RED); canvas.drawRect(0, 0, 100, 100, paint);
        paint.setColor(android.graphics.Color.GREEN); canvas.drawRect(100, 0, 200, 100, paint);
        paint.setColor(android.graphics.Color.BLUE); canvas.drawRect(200, 0, 300, 100, paint);
        try (FileOutputStream out = new FileOutputStream(file)) { source.compress(Bitmap.CompressFormat.PNG, 100, out); }
        for (boolean cover : new boolean[]{false, true}) {
            Intent intent = PhotoAdjustActivity.intent(context, Uri.fromFile(file), cover);
            try (ActivityScenario<PhotoAdjustActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
                TestSystemUi.dismissFullscreenHint();
                await(() -> {
                    boolean[] ready = {false};
                    scenario.onActivity(activity -> ready[0] = findCrop(activity.findViewById(android.R.id.content)).ready());
                    return ready[0];
                });
                SystemClock.sleep(600);
                int[] centerOnScreen = new int[2];
                scenario.onActivity(activity -> {
                    PhotoCropView view = findCrop(activity.findViewById(android.R.id.content));
                    view.getLocationOnScreen(centerOnScreen);
                    centerOnScreen[0] += view.getWidth() / 2;
                    centerOnScreen[1] += view.getHeight() / 2;
                });
                Bitmap preview = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertTrue(android.graphics.Color.green(preview.getPixel(centerOnScreen[0], centerOnScreen[1])) > 240);
                preview.recycle();
                shell("screencap -p /data/local/tmp/kinnect4-" + (cover ? "cover" : "avatar") + ".png");
                if (cover) {
                    android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                    scenario.onActivity(activity -> activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics));
                    int x = metrics.widthPixels / 2, h = metrics.heightPixels;
                    shell("input swipe " + x + " " + (h - 1) + " " + x + " " + (h - 250) + " 250");
                    SystemClock.sleep(400);
                    shell("screencap -p /data/local/tmp/kinnect4-bottom-edge.png");
                    SystemClock.sleep(3500);
                    shell("input swipe " + x + " 1 " + x + " 250 250");
                    SystemClock.sleep(400);
                    shell("screencap -p /data/local/tmp/kinnect4-top-edge.png");
                    SystemClock.sleep(3500);
                    onView(withText("Flip")).perform(click());
                }
                onView(withText("Use photo")).perform(click());
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
                Uri result = scenario.getResult().getResultData().getData();
                Bitmap crop = BitmapFactory.decodeFile(result.getPath());
                assertEquals(cover ? 2 : 1, crop.getWidth() / crop.getHeight());
                int center = crop.getPixel(crop.getWidth() / 2, crop.getHeight() / 2);
                assertTrue(android.graphics.Color.green(center) > 240);
                if (cover) assertTrue(android.graphics.Color.blue(crop.getPixel(crop.getWidth() / 8, crop.getHeight() / 2)) > 240);
                if (!cover) assertTrue(android.graphics.Color.green(crop.getPixel(crop.getWidth() / 4, crop.getHeight() / 2)) > 240);
                crop.recycle();
            }
        }
        source.recycle(); file.delete();
    }

    @Test
    public void offlineMessage_reconnectsWithoutDuplicates_andEditsThroughPhp() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JSONObject receiver =
                Api.call(
                        "auth/signup.php",
                        new JSONObject()
                                .put("email", suffix + "@example.com")
                                .put("password", "ContentTest42!")
                                .put("firstName", "Receiver")
                                .put("lastName", "Test")
                                .put("birthday", "2004-01-01")
                                .put("gender", "Custom"),
                        null);
        String other = receiver.getJSONObject("user").getString("uid"),
                otherToken = receiver.getString("token");
        try {
            Api.call(
                    "profile/update.php",
                    new JSONObject()
                            .put("actionId", UUID.randomUUID().toString())
                            .put("username", suffix)
                            .put("name", "Receiver Test")
                            .put("bio", "Chat test")
                            .put("gender", "Custom"),
                    otherToken);
            synchronized (Profiles.LOCK) {
                LocalDb.get().acceptSocial(Api.call("social/state.php", null, token));
            }
            shell("svc data disable");
            shell("svc wifi disable");
            await(() -> !Connection.connected(context));
            try (ActivityScenario<ChatActivity> scenario =
                    ActivityScenario.launch(ChatActivity.intent(context, other))) {
                TestSystemUi.dismissFullscreenHint();
                onView(withId(R.id.etMsg)).perform(replaceText("Offline chat message"));
                closeSoftKeyboard();
                onView(withId(R.id.btnSend)).perform(click());
                await(() -> !com.ahmadhaaroon.i221295.util.Chats.messages(other).isEmpty());
                assertTrue(com.ahmadhaaroon.i221295.util.Chats.messages(other).get(0).pending);
                shell("svc wifi enable");
                await(() -> Connection.connected(context));
                await(() -> LocalDb.get().status(WebSession.uid()).isEmpty());
                com.ahmadhaaroon.i221295.models.Message message =
                        com.ahmadhaaroon.i221295.util.Chats.messages(other).get(0);
                assertFalse(message.pending);
                JSONArray received =
                        Api.call("social/state.php", null, otherToken)
                                .getJSONObject("content")
                                .getJSONArray("messages");
                assertEquals(1, count(received, message.id));
                com.ahmadhaaroon.i221295.util.Chats.edit(other, message, "Edited chat message");
                await(() -> {
                    com.ahmadhaaroon.i221295.models.Message current =
                            com.ahmadhaaroon.i221295.util.Chats.messages(other).get(0);
                    return "Edited chat message".equals(current.text)
                            && current.edited
                            && !current.pending
                            && LocalDb.get().status(WebSession.uid()).isEmpty();
                });
                JSONObject edited =
                        Api.call("social/state.php", null, otherToken)
                                .getJSONObject("content")
                                .getJSONArray("messages")
                                .getJSONObject(0);
                assertEquals("Edited chat message", edited.getString("text"));
            }
        } finally {
            shell("svc wifi enable");
            await(() -> Connection.connected(context));
            Api.call("auth/logout.php", new JSONObject(), otherToken);
        }
    }

    private static PhotoCropView findCrop(android.view.View view) {
        if (view instanceof PhotoCropView) return (PhotoCropView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                PhotoCropView crop = findCrop(group.getChildAt(i));
                if (crop != null) return crop;
            }
        }
        return null;
    }

    private static int count(JSONArray rows, String id) throws Exception {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) if (id.equals(rows.getJSONObject(i).optString("id"))) count++;
        return count;
    }

    private static void queue(String operation, JSONObject data) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {null};
        Content.action(operation, data, latch::countDown, e -> { error[0] = e; latch.countDown(); });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error[0] != null) throw error[0];
    }

    private interface Check { boolean ready() throws Exception; }

    private static void await(Check check) throws Exception {
        long end = SystemClock.elapsedRealtime() + 40000;
        while (SystemClock.elapsedRealtime() < end) {
            if (check.ready()) return;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Timed out waiting for content workflow");
    }

    private static void shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            byte[] bytes = new byte[1024];
            while (in.read(bytes) != -1) { }
        }
    }
}
