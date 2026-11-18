package com.ahmadhaaroon.i221295;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ahmadhaaroon.i221295.models.Story;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.ui.StoryPlayer;
import com.ahmadhaaroon.i221295.util.Content;
import com.ahmadhaaroon.i221295.util.PostActions;
import com.ahmadhaaroon.i221295.util.TimeFmt;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.List;

/**
 * Viewing a friend's cached story (screen 11). Views are queued for the web service.
 */
public class StoryViewerActivity extends AppCompatActivity implements StoryPlayer.Listener {

    private static final String EXTRA_UID = "uid";

    private String owner;
    private StoryPlayer player;
    private EditText reply;
    private boolean loaded;

    public static Intent intent(Context c, String uid) {
        return new Intent(c, StoryViewerActivity.class).putExtra(EXTRA_UID, uid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_viewer_screen);
        owner = getIntent().getStringExtra(EXTRA_UID);
        if (owner == null) {
            finish();
            return;
        }
        player = new StoryPlayer(this, this, R.id.karachi_label);
        reply = findViewById(R.id.reply_input);

        ((AvatarView) findViewById(R.id.story_pfp)).bind(owner);
        TextView name = findViewById(R.id.story_name);
        UserCache.get(owner, u -> name.setText(u.displayName()));

        NavHelper.click(this, R.id.close_button, this::finish);
        NavHelper.click(this, R.id.story_pfp, () -> PostActions.openProfile(this, owner));
        NavHelper.click(this, R.id.story_name, () -> PostActions.openProfile(this, owner));
        NavHelper.click(this, R.id.btn_more, () -> {
            player.pause();
            new AlertDialog.Builder(this)
                    .setItems(new String[]{"View profile"}, (d, w) -> PostActions.openProfile(this, owner))
                    .setOnDismissListener(d -> player.resume())
                    .show();
        });
        NavHelper.click(this, R.id.react_like, () -> react("👍"));
        NavHelper.click(this, R.id.react_love, () -> react("❤️"));
        NavHelper.click(this, R.id.react_haha, () -> react("😂"));

        reply.setOnFocusChangeListener((v, focused) -> {
            if (focused) player.pause();
            else player.resume();
        });
        reply.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply();
                return true;
            }
            return false;
        });

        load();
    }

    /** Loads the owner's active stories; close-friends stories only if I am on their list. */
    private void load() {
        List<Story> list = Content.stories(owner);
        if (list.isEmpty()) {
            Toast.makeText(this, "This story is no longer available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loaded = true;
        player.setStories(list, 0);
    }

    @Override
    public void onShow(Story story, int index) {
        ((TextView) findViewById(R.id.story_time)).setText(TimeFmt.ago(story.timestamp) + " •");
        Content.action("stories.view", Content.data(story.id));
    }

    @Override
    public void onFinished() {
        finish();
    }

    private void sendReply() {
        NavHelper.pendingFeature(this);
    }

    private void react(String emoji) {
        NavHelper.pendingFeature(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (loaded) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded && !reply.hasFocus()) player.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
