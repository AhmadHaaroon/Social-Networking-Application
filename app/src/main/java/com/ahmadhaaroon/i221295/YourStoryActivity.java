package com.ahmadhaaroon.i221295;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ahmadhaaroon.i221295.models.Story;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.ui.StoryPlayer;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.Content;
import com.ahmadhaaroon.i221295.util.TimeFmt;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Your own story (screen 12) with who has seen it. Expired stories (older than 24h) are removed
 * here and on Home (only the owner may delete them); everyone else simply stops showing them.
 */
public class YourStoryActivity extends AppCompatActivity implements StoryPlayer.Listener {

    private StoryPlayer player;
    private final Runnable changed = () -> {
        if (player.current() != null) onShow(player.current(), player.index());
    };
    private final List<String> viewers = new ArrayList<>();
    private boolean loaded;

    public static Intent intent(Context c) {
        return new Intent(c, YourStoryActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.your_story_screen);
        player = new StoryPlayer(this, this, R.id.saturday_label);
        ((AvatarView) findViewById(R.id.story_pfp)).bind(Db.uid());

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.action_create, () -> {
            startActivity(CameraActivity.forStory(this));
            finish();
        });
        NavHelper.click(this, R.id.action_seen, this::showViewers);
        NavHelper.click(this, R.id.action_send, this::sendToFriend);
        NavHelper.click(this, R.id.action_highlight, () ->
                Toast.makeText(this, "Highlights are coming soon", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.action_more, this::moreOptions);
        load();
    }

    private void load() {
        List<Story> list = Content.stories(Db.uid());
        if (list.isEmpty()) {
            Toast.makeText(this, "You have no active story", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loaded = true;
        player.setStories(list, list.size() - 1);
    }

    @Override
    public void onShow(Story story, int index) {
        String audience = "close".equals(story.audience) ? "Close friends" : "Friends";
        ((TextView) findViewById(R.id.story_time)).setText(TimeFmt.ago(story.timestamp) + " • " + audience + (Content.status(story.id).isEmpty() ? "" : " · " + Content.status(story.id)));
        watchViews(story);
    }

    /** Live "Seen by N" with the first three viewers' avatars. */
    private void watchViews(Story story) {
        viewers.clear();
        viewers.addAll(Content.viewers(story.id));
        ((TextView) findViewById(R.id.seenText)).setText(getString(R.string.seen_by_n, viewers.size()));
        int[] ids = {R.id.seen1, R.id.seen2, R.id.seen3};
        for (int i = 0; i < ids.length; i++) {
            AvatarView av = findViewById(ids[i]);
            av.setVisibility(i < viewers.size() ? View.VISIBLE : View.GONE);
            if (i < viewers.size()) av.bind(viewers.get(i));
        }
    }

    private void showViewers() {
        if (viewers.isEmpty()) {
            Toast.makeText(this, "No one has seen this yet", Toast.LENGTH_SHORT).show();
            return;
        }
        player.pause();
        List<String> names = new ArrayList<>();
        for (String uid : viewers) {
            UserCache.get(uid, u -> {
                names.add(u.displayName());
                if (names.size() == viewers.size()) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.seen_by_n, viewers.size()))
                            .setItems(names.toArray(new String[0]), null)
                            .setOnDismissListener(d -> player.resume())
                            .show();
                }
            });
        }
    }

    private void sendToFriend() {
        NavHelper.pendingFeature(this);
    }

    private void moreOptions() {
        Story s = player.current();
        if (s == null) return;
        player.pause();
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Delete this story"}, (d, w) -> {
                    Content.action("stories.delete", Content.data(s.id));
                    Toast.makeText(this, "Story deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setOnDismissListener(d -> player.resume())
                .show();
    }

    @Override
    public void onFinished() {
        finish();
    }

    @Override
    protected void onPause() {
        com.ahmadhaaroon.i221295.util.LocalDb.removeObserver(changed);
        super.onPause();
        if (loaded) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.ahmadhaaroon.i221295.util.LocalDb.observe(changed);
        com.ahmadhaaroon.i221295.util.Profiles.refresh(this);
        if (loaded) player.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }
}
