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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ahmadhaaroon.i221295.util.Api;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.WebSession;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Run against the PHP test backend. Test accounts use unique addresses. */
@RunWith(AndroidJUnit4.class)
public class WebAuthFlowTest {

    @Rule
    public GrantPermissionRule notificationPermission = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS);

    private String suffix;
    private static final String PASSWORD = "KinnectTest42!";

    @Before
    public void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        synchronized (Profiles.LOCK) {
            LocalDb.get().revokeAndClear(WebSession.token());
            WebSession.clear();
        }
    }

    @After
    public void tearDown() throws Exception {
        synchronized (Profiles.LOCK) {
            LocalDb.get().revokeAndClear(WebSession.token());
            WebSession.clear();
        }
    }

    @Test
    public void signup_profileSetup_thenHome() throws Exception {
        try (ActivityScenario<CreateAccountActivity> ignored = ActivityScenario.launch(CreateAccountActivity.class)) {
            TestSystemUi.dismissFullscreenHint();
            onView(withId(R.id.etFirstName)).perform(replaceText("Espresso"));
            onView(withId(R.id.etLastName)).perform(replaceText("Tester"));
            onView(withId(R.id.etEmail)).perform(replaceText("signup" + suffix + "@example.com"));
            onView(withId(R.id.textView3)).perform(replaceText(PASSWORD));
            closeSoftKeyboard();
            onView(withId(R.id.button2)).perform(click());
            waitFor(R.id.btn_save);
            assertTrue(WebSession.valid());
            assertFalse(LocalDb.get().user(WebSession.uid()).profileComplete);
            onView(withId(R.id.field_username)).perform(scrollTo(), replaceText("test" + suffix));
            onView(withId(R.id.field_bio)).perform(scrollTo(), replaceText("Testing PHP and SQLite"));
            closeSoftKeyboard();
            onView(withId(R.id.btn_save)).perform(scrollTo(), click());
            waitFor(R.id.feed);
            assertTrue(LocalDb.get().user(WebSession.uid()).profileComplete);
            JSONObject me = Api.call("profile/me.php", null, WebSession.token()).getJSONObject("user");
            assertEquals("test" + suffix, me.getString("username"));
            assertEquals("", LocalDb.get().status(WebSession.uid()));
        }
    }

    @Test
    public void login_existingAccount_thenLogoutClearsSessionAndCache() throws Exception {
        JSONObject signup = new JSONObject();
        String email = "login" + suffix + "@example.com";
        signup.put("email", email);
        signup.put("password", PASSWORD);
        signup.put("firstName", "Login");
        signup.put("lastName", "Tester");
        signup.put("gender", "Custom");
        signup.put("birthday", "2004-03-14");
        JSONObject registered = Api.call("auth/signup.php", signup, null);
        String token = registered.getString("token");
        JSONObject profile = new JSONObject();
        profile.put("actionId", UUID.randomUUID().toString());
        profile.put("name", "Login Tester");
        profile.put("username", "login" + suffix);
        profile.put("bio", "Existing account");
        profile.put("gender", "Custom");
        Api.call("profile/update.php", profile, token);
        Api.call("auth/logout.php", new JSONObject(), token);
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            TestSystemUi.dismissFullscreenHint();
            onView(withId(R.id.textView2)).perform(replaceText(email));
            onView(withId(R.id.textView3)).perform(replaceText(PASSWORD));
            closeSoftKeyboard();
            onView(withId(R.id.button)).perform(click());
            waitFor(R.id.feed);
            String uid = WebSession.uid();
            onView(withId(R.id.tabMore)).perform(click());
            waitFor(R.id.btnLogout);
            onView(withId(R.id.btnLogout)).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).perform(click());
            waitFor(R.id.textView2);
            assertFalse(WebSession.valid());
            assertTrue(LocalDb.get().user(uid) == null);
        }
    }

    private static void waitFor(int id) {
        long end = SystemClock.elapsedRealtime() + 25000;
        AssertionError last = null;
        while (SystemClock.elapsedRealtime() < end) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                onView(withId(id)).check(matches(isDisplayed()));
                return;
            } catch (RuntimeException | AssertionError e) {
                last = new AssertionError(e);
                SystemClock.sleep(150);
            }
        }
        throw last == null ? new AssertionError("Screen did not appear") : last;
    }
}
