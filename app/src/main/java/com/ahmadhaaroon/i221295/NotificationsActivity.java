package com.ahmadhaaroon.i221295;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ahmadhaaroon.i221295.models.AppNotification;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Badges;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.Friends;
import com.ahmadhaaroon.i221295.util.PostActions;
import com.ahmadhaaroon.i221295.util.Subs;
import com.ahmadhaaroon.i221295.util.TimeFmt;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notifications (screen 18): reactions, comments, replies, friend requests (with Confirm /
 * Delete), story reactions and screenshot alerts. Unread items appear under "New".
 */
public class NotificationsActivity extends AppCompatActivity {

    private final Subs subs = new Subs();
    private final List<AppNotification> items = new ArrayList<>();
    private final Set<String> pendingRequests = new HashSet<>();
    private final NotifAdapter adapter = new NotifAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        NavHelper.setup(this, NavHelper.NOTIF, "Notifications");
        Badges.messages(subs, findViewById(R.id.msgBadge));
        Badges.notifications(subs, findViewById(R.id.notifBadge));

        RecyclerView list = findViewById(R.id.notifList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        Runnable changed =
                () -> {
                    items.clear();
                    for (org.json.JSONObject row :
                            com.ahmadhaaroon.i221295.util.LocalDb.get().content("notifications"))
                        items.add(
                                com.ahmadhaaroon.i221295.util.ContentJson.read(
                                        row, AppNotification.class));
                    items.sort((a, b) ->
                            a.read != b.read
                                    ? (a.read ? 1 : -1)
                                    : Long.compare(b.timestamp, a.timestamp));
                    pendingRequests.clear();
                    for (org.json.JSONObject row :
                            com.ahmadhaaroon.i221295.util.LocalDb.get().relations()) {
                        String other =
                                row.optString("a").equals(Db.uid())
                                        ? row.optString("b")
                                        : row.optString("a");
                        if (com.ahmadhaaroon.i221295.util.People.state(other) == Friends.INCOMING)
                            pendingRequests.add(other);
                    }
                    adapter.notifyDataSetChanged();
                    findViewById(R.id.emptyNotifs)
                            .setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                };
        com.ahmadhaaroon.i221295.util.LocalDb.observe(changed);
        subs.add(() -> com.ahmadhaaroon.i221295.util.LocalDb.removeObserver(changed));
        changed.run();
        com.ahmadhaaroon.i221295.util.Profiles.refresh(this);
    }

    /** Everything on screen counts as seen once the user leaves the screen. */
    @Override
    protected void onPause() {
        super.onPause();
        for (AppNotification n : items)
            if (!n.read)
                com.ahmadhaaroon.i221295.util.Content.action(
                        "notifications.read", com.ahmadhaaroon.i221295.util.Content.data(n.id));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }

    private void open(AppNotification n) {
        com.ahmadhaaroon.i221295.util.Content.action(
                "notifications.read", com.ahmadhaaroon.i221295.util.Content.data(n.id));
        if (n.postId != null) startActivity(CommentsActivity.intent(this, n.postId));
        else if ((AppNotification.SCREENSHOT.equals(n.type)
                || "message".equals(n.type)
                || "call".equals(n.type))) startActivity(ChatActivity.intent(this, n.fromUid));
        else PostActions.openProfile(this, n.fromUid);
    }

    private static int iconFor(String type) {
        if (type == null) return R.drawable.ic_bell;
        switch (type) {
            case AppNotification.REACTION:
            case AppNotification.STORY_REACTION:
                return R.drawable.ic_heart;
            case AppNotification.COMMENT:
            case AppNotification.REPLY:
                return R.drawable.ic_comment;
            case AppNotification.COMMENT_LIKE:
                return R.drawable.ic_thumb_fill;
            case AppNotification.FRIEND_REQUEST:
            case AppNotification.FRIEND_ACCEPT:
                return R.drawable.ic_person;
            case AppNotification.SCREENSHOT:
                return R.drawable.ic_camera;
            default:
                return R.drawable.ic_bell;
        }
    }

    private static int colorFor(String type) {
        if (AppNotification.REACTION.equals(type) || AppNotification.STORY_REACTION.equals(type)) return 0xFFD8344F;
        if (AppNotification.FRIEND_REQUEST.equals(type) || AppNotification.FRIEND_ACCEPT.equals(type)) return 0xFF3F6E8C;
        if (AppNotification.SCREENSHOT.equals(type)) return 0xFFBC4A28;
        return 0xFF0B5F61;
    }

    private class NotifAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            AppNotification n = items.get(position);
            View v = h.itemView;
            boolean firstOfSection = position == 0 || items.get(position - 1).read != n.read;
            TextView section = v.findViewById(R.id.sectionTitle);
            section.setVisibility(firstOfSection ? View.VISIBLE : View.GONE);
            section.setText(n.read ? R.string.earlier : R.string.new_section);
            v.findViewById(R.id.row).setBackgroundColor(getColor(n.read ? R.color.white : R.color.teal_light));

            ((AvatarView) v.findViewById(R.id.avatar)).bind(n.fromUid);
            ImageView badge = v.findViewById(R.id.badge);
            badge.setImageResource(iconFor(n.type));
            badge.setBackgroundTintList(ColorStateList.valueOf(colorFor(n.type)));

            TextView text = v.findViewById(R.id.text);
            text.setText(n.text);
            UserCache.get(n.fromUid, u -> {
                if (h.getBindingAdapterPosition() != position) return;
                SpannableStringBuilder sb = new SpannableStringBuilder(u.displayName());
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(' ').append(n.text == null ? "" : n.text);
                text.setText(sb);
            });
            TextView time = v.findViewById(R.id.time);
            time.setText(TimeFmt.ago(n.timestamp));
            time.setTextColor(getColor(n.read ? R.color.ink2 : R.color.teal));
            time.setTypeface(null, n.read ? Typeface.NORMAL : Typeface.BOLD);

            boolean actionable = AppNotification.FRIEND_REQUEST.equals(n.type) && pendingRequests.contains(n.fromUid);
            v.findViewById(R.id.actions).setVisibility(actionable ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.btnConfirm).setOnClickListener(x -> Friends.accept(n.fromUid));
            v.findViewById(R.id.btnDelete).setOnClickListener(x -> Friends.decline(n.fromUid));
            v.findViewById(R.id.row).setOnClickListener(x -> open(n));
            v.findViewById(R.id.more)
                    .setOnClickListener(x ->
                            new AlertDialog.Builder(NotificationsActivity.this)
                                    .setItems(
                                            new String[] {"Remove this notification"},
                                            (d, w) ->
                                                    com.ahmadhaaroon.i221295.util.Content.action(
                                                            "notifications.delete",
                                                            com.ahmadhaaroon.i221295.util.Content
                                                                    .data(n.id)))
                                    .show());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
