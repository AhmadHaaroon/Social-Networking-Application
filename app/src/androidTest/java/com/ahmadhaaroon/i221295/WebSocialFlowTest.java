package com.ahmadhaaroon.i221295;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ahmadhaaroon.i221295.util.Api;
import com.ahmadhaaroon.i221295.util.Connection;
import com.ahmadhaaroon.i221295.util.Friends;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.MediaFiles;
import com.ahmadhaaroon.i221295.util.MediaUpload;
import com.ahmadhaaroon.i221295.util.UserJson;
import com.ahmadhaaroon.i221295.util.People;
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

import java.io.FileInputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Use an isolated test install: this suite temporarily disables Wi-Fi and mobile data during the offline test. */
@RunWith(AndroidJUnit4.class)
public class WebSocialFlowTest {

    @Rule
    public GrantPermissionRule notificationPermission = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS);

    private Context context;
    private JSONObject other;
    private String suffix;
    private boolean hadWifi;
    private boolean hadData;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        hadWifi = android.provider.Settings.Global.getInt(context.getContentResolver(), "wifi_on", 1) != 0;
        hadData = android.provider.Settings.Global.getInt(context.getContentResolver(), "mobile_data", 1) != 0;
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        synchronized (Profiles.LOCK) {
            LocalDb.get().revokeAndClear(WebSession.token());
            WebSession.clear();
        }
        JSONObject me = account("Owner");
        other = account("Friend");
        CountDownLatch signedIn = new CountDownLatch(1);
        Exception[] error = {null};
        Profiles.signIn(context, "login", new JSONObject().put("email", "Owner" + suffix + "@example.com")
                .put("password", "KinnectTest42!"), signedIn::countDown, e -> {
                    error[0] = e;
                    signedIn.countDown();
                });
        assertTrue(signedIn.await(25, TimeUnit.SECONDS));
        if (error[0] != null) throw error[0];
        Api.call("auth/logout.php", new JSONObject(), me.getString("token"));
        await(() -> LocalDb.get().user(other.getJSONObject("user").getString("uid")) != null);
    }

    private JSONObject account(String label) throws Exception {
        JSONObject user = Api.call("auth/signup.php", new JSONObject().put("email", label + suffix + "@example.com")
                .put("password", "KinnectTest42!").put("firstName", label).put("lastName", "Tester")
                .put("gender", "Custom").put("birthday", "2004-03-14"), null);
        Api.call("profile/update.php", new JSONObject().put("actionId", UUID.randomUUID().toString())
                .put("name", label + " Tester").put("username", label.toLowerCase() + suffix)
                .put("bio", "Social workflow test").put("gender", "Custom"), user.getString("token"));
        return user;
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
        SyncWorker.retryNow(context);
        if (other != null) Api.call("auth/logout.php", new JSONObject(), other.getString("token"));
    }

    @Test
    public void normalProfileSave_returnsToProfile_andSyncs() throws Exception {
        try (ActivityScenario<MyProfileActivity> ignored = ActivityScenario.launch(MyProfileActivity.class)) {
            TestSystemUi.dismissFullscreenHint();
            onView(withId(R.id.btn_edit_profile)).perform(scrollTo(), click());
            onView(withId(R.id.field_bio)).perform(scrollTo(), replaceText("Updated from own profile"));
            closeSoftKeyboard();
            onView(withId(R.id.btn_save)).perform(scrollTo(), click());
            await(() -> {
                try {
                    onView(withId(R.id.profile_name)).check(matches(isDisplayed()));
                    return true;
                } catch (Exception | AssertionError e) {
                    return false;
                }
            });
            await(() -> LocalDb.get().profileStatus(WebSession.uid()).isEmpty());
            assertEquals("Updated from own profile", Api.call("profile/me.php", null, WebSession.token())
                    .getJSONObject("user").getString("bio"));
        }
    }

    @Test
    public void offlineFriendRequest_isVisible_thenSyncsOnce() throws Exception {
        String uid = other.getJSONObject("user").getString("uid");
        try (ActivityScenario<SearchActivity> ignored = ActivityScenario.launch(SearchActivity.class)) {
            TestSystemUi.dismissFullscreenHint();
            onView(withId(R.id.tv_query)).perform(replaceText("friend" + suffix));
            closeSoftKeyboard();
            shell("svc data disable");
            shell("svc wifi disable");
            await(() -> !Connection.connected(context));
            People.action(uid, "send");
            await(() -> LocalDb.get().friendPending(uid));
            assertEquals(Friends.REQUESTED, People.state(uid));
            onView(withId(R.id.results)).check(matches(isDisplayed()));
            shell("svc wifi enable");
            await(() -> Connection.connected(context));
            Profiles.refresh(context);
            await(() -> !LocalDb.get().friendPending(uid));
            JSONArray rows = Api.call("social/state.php", null, other.getString("token")).getJSONArray("relations");
            int found = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (WebSession.uid().equals(row.optString("requester"))
                        && (uid.equals(row.optString("a")) || uid.equals(row.optString("b")))) found++;
            }
            assertEquals(1, found);
        }
    }

    @Test
    public void offlineProfilePhoto_uploadsAndDownloadsThroughVolley() throws Exception {
        shell("svc data disable");
        shell("svc wifi disable");
        await(() -> !Connection.connected(context));
        java.io.File source = new java.io.File(context.getCacheDir(), "test-avatar.png");
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFF0B5F63);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(source)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        CountDownLatch prepared = new CountDownLatch(1);
        String[] ref = {null};
        Exception[] error = {null};
        MediaUpload.upload(context, android.net.Uri.fromFile(source), "avatars", new MediaUpload.Listener() {
            @Override
            public void onSuccess(String value) {
                ref[0] = value;
                prepared.countDown();
            }

            @Override
            public void onError(Exception e) {
                error[0] = e;
                prepared.countDown();
            }
        });
        assertTrue(prepared.await(10, TimeUnit.SECONDS));
        if (error[0] != null) throw error[0];
        assertTrue(MediaFiles.file(ref[0]).isFile());
        CountDownLatch queued = new CountDownLatch(1);
        Profiles.save(context, UserJson.write(LocalDb.get().user(WebSession.uid())).put("photoUrl", ref[0]),
                queued::countDown, e -> {
                    error[0] = e;
                    queued.countDown();
                });
        assertTrue(queued.await(10, TimeUnit.SECONDS));
        if (error[0] != null) throw error[0];
        assertEquals(ref[0], LocalDb.get().user(WebSession.uid()).photoUrl);
        assertEquals("Pending", LocalDb.get().profileStatus(WebSession.uid()));
        shell("svc wifi enable");
        await(() -> Connection.connected(context));
        Profiles.refresh(context);
        await(() -> LocalDb.get().profileStatus(WebSession.uid()).isEmpty());
        assertEquals(ref[0], Api.call("profile/me.php", null, WebSession.token()).getJSONObject("user").getString("photoUrl"));
        assertTrue(MediaFiles.file(ref[0]).delete());
        CountDownLatch downloaded = new CountDownLatch(1);
        boolean[] found = {false};
        MediaFiles.resolve(ref[0], file -> {
            found[0] = file != null && file.length() > 0;
            downloaded.countDown();
        });
        assertTrue(downloaded.await(25, TimeUnit.SECONDS));
        assertTrue(found[0]);
        source.delete();
    }

    private interface Check {
        boolean ready() throws Exception;
    }

    private static void await(Check check) throws Exception {
        long end = SystemClock.elapsedRealtime() + 35000;
        while (SystemClock.elapsedRealtime() < end) {
            if (check.ready()) return;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Timed out waiting for the workflow");
    }

    private static void shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
            }
        }
    }
}
