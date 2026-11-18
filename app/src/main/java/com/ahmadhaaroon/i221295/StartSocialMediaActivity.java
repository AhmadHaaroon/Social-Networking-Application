package com.ahmadhaaroon.i221295;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ahmadhaaroon.i221295.adapters.PostAdapter;
import com.ahmadhaaroon.i221295.adapters.StoryRowAdapter;
import com.ahmadhaaroon.i221295.models.Post;
import com.ahmadhaaroon.i221295.models.Story;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.Content;
import com.ahmadhaaroon.i221295.util.Subs;
import com.ahmadhaaroon.i221295.util.UserCache;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.AuthFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Home (screen 04). Shows friends' stories (Create story first) and a real-time feed of posts
 * by the user and their friends, newest first.
 */
public class StartSocialMediaActivity extends AppCompatActivity {

    private static final int FEED_LIMIT = 300;

    private final Subs subs = new Subs();
    private final Set<String> friends = new HashSet<>();
    private final Set<String> closeFriendOf = new HashSet<>();
    private final List<Post> allPosts = new ArrayList<>();

    private PostAdapter feedAdapter;
    private StoryRowAdapter storyAdapter;
    private View header;
    private final android.os.Handler expiryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expire = new Runnable() {
        @Override
        public void run() {
            rebuildStories();
        }
    };
    private final Runnable changed = this::refreshLocalProfile;

    private final ActivityResultLauncher<String> notifPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_social_media_screen);
        String me = Db.uid();
        if (me == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        NavHelper.click(this, R.id.messageIcon, () -> open(ChatsActivity.class));
        NavHelper.click(this, R.id.btnSearch, () -> open(SearchActivity.class));
        NavHelper.click(this, R.id.tabPeople, () -> open(FriendsActivity.class));
        NavHelper.click(this, R.id.tabShop, () -> open(MarketplaceActivity.class));
        NavHelper.click(this, R.id.tabNotif, () -> open(NotificationsActivity.class));
        NavHelper.click(this, R.id.tabMore, () -> open(MenuActivity.class));

        storyAdapter = new StoryRowAdapter(this);
        feedAdapter = new PostAdapter(this, this::bindHeader);
        RecyclerView feed = findViewById(R.id.feed);
        feed.setLayoutManager(new LinearLayoutManager(this));
        feed.setAdapter(feedAdapter);

        findViewById(R.id.msgBadge).setVisibility(View.GONE);
        findViewById(R.id.notifBadge).setVisibility(View.GONE);
        askForNotificationPermission();
    }

    private void bindHeader(View h) {
        header = h;
        String me = Db.uid();
        ((AvatarView) h.findViewById(R.id.seagreenpfp)).bind(me);
        TextView prompt = h.findViewById(R.id.search_bar);
        UserCache.get(me, u -> prompt.setText(getString(R.string.whats_on_your_mind, u.firstName())));

        RecyclerView stories = h.findViewById(R.id.stories);
        stories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        stories.setAdapter(storyAdapter);

        h.findViewById(R.id.seagreenpfp).setOnClickListener(v -> open(MyProfileActivity.class));
        prompt.setOnClickListener(v -> open(CreatePostActivity.class));
        h.findViewById(R.id.btnImagePost).setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class).putExtra("openPicker", true)));
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (header == null) return;
        header.findViewById(R.id.emptyFeed).setVisibility(feedAdapter.postCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void rebuildStories() {
        java.util.Map<String, StoryRowAdapter.Group> groups = new java.util.LinkedHashMap<>();
        for (Story story : Content.stories(null)) {
            StoryRowAdapter.Group group = groups.get(story.uid);
            if (group == null) {
                group = new StoryRowAdapter.Group();
                group.uid = story.uid;
                groups.put(story.uid, group);
            }
            group.stories.add(story);
        }
        List<StoryRowAdapter.Group> list = new ArrayList<>(groups.values());
        list.sort((a, b) -> a.uid.equals(Db.uid()) ? -1 : b.uid.equals(Db.uid()) ? 1 : Long.compare(b.latest().timestamp, a.latest().timestamp));
        storyAdapter.setGroups(list);
        expiryHandler.removeCallbacks(expire);
        long nextExpiry = Long.MAX_VALUE;
        for (StoryRowAdapter.Group group : list) for (Story story : group.stories) nextExpiry = Math.min(nextExpiry, story.expiresAt);
        if (nextExpiry != Long.MAX_VALUE) expiryHandler.postDelayed(expire, Math.max(1, nextExpiry - System.currentTimeMillis() + 1));
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void open(Class<?> target) {
        NavHelper.open(this, target);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDb.observe(changed);
        refreshLocalProfile();
        com.ahmadhaaroon.i221295.util.Profiles.refresh(this);
    }

    @Override
    protected void onPause() {
        expiryHandler.removeCallbacks(expire);
        LocalDb.removeObserver(changed);
        super.onPause();
    }

    private void refreshLocalProfile() {
        if (Db.uid() == null) {
            AuthFlow.afterSignIn(this);
            return;
        }
        if (feedAdapter == null) return;
        feedAdapter.setPosts(Content.posts(null));
        rebuildStories();
        updateEmptyState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
        if (feedAdapter != null) feedAdapter.release();
    }
}
