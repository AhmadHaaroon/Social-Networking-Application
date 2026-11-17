package com.ahmadhaaroon.i221295.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ahmadhaaroon.i221295.R;
import com.ahmadhaaroon.i221295.models.User;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Callback;
import com.ahmadhaaroon.i221295.util.Friends;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Mutuals;
import com.ahmadhaaroon.i221295.util.PostActions;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of people with a live Add friend button (Add friend -> Requested -> Friends). Used by
 * Search (13) and Friends (14).
 */
public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.VH> {

    private final Activity activity;
    private final List<String> uids = new ArrayList<>();
    private Callback<String> onPick;
    private boolean showActions = true;

    public PersonAdapter(Activity activity) {
        this.activity = activity;
    }

    public void setUsers(List<String> list) {
        uids.clear();
        uids.addAll(list);
        notifyDataSetChanged();
    }

    /** Overrides the default "open profile" tap, e.g. to start a chat. */
    public void setOnPick(Callback<String> onPick) {
        this.onPick = onPick;
    }

    public void setShowActions(boolean show) {
        this.showActions = show;
    }

    class VH extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView name, details, state;
        final View add, online;
        Friends.Handle handle;
        String uid;

        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.personAvatar);
            name = v.findViewById(R.id.personName);
            details = v.findViewById(R.id.personDetails);
            state = v.findViewById(R.id.personState);
            add = v.findViewById(R.id.personAdd);
            online = v.findViewById(R.id.personOnline);
        }

        void bind(String id) {
            uid = id;
            avatar.bind(id);
            name.setText("");
            details.setText("");
            UserCache.get(id, u -> {
                if (!id.equals(uid)) return;
                name.setText(u.displayName());
                online.setVisibility(u.online ? View.VISIBLE : View.GONE);
                showDetails(u, -1);
                Mutuals.count(id, n -> {
                    if (id.equals(uid)) showDetails(u, n);
                });
            });
            itemView.setOnClickListener(v -> {
                if (onPick != null) onPick.onResult(id);
                else PostActions.openProfile(activity, id);
            });

            if (handle != null) handle.remove();
            if (!showActions) {
                add.setVisibility(View.GONE);
                state.setVisibility(View.GONE);
                return;
            }
            handle = Friends.observe(id, this::showState);
        }

        private void showDetails(User u, int mutual) {
            StringBuilder sb = new StringBuilder();
            if (u.username != null && !u.username.isEmpty()) sb.append('@').append(u.username);
            if (mutual > 0) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(mutual).append(mutual == 1 ? " mutual friend" : " mutual friends");
            }
            details.setText(sb);
        }

        private void showState(int s) {
            add.setVisibility(s == Friends.NONE ? View.VISIBLE : View.GONE);
            state.setVisibility(s == Friends.NONE || s == Friends.SELF ? View.GONE : View.VISIBLE);
            state.setText(Friends.label(s) + (LocalDb.get().friendPending(uid) ? " · Pending" : ""));
            boolean confirm = s == Friends.INCOMING;
            state.setBackgroundResource(confirm ? R.drawable.seagreen_button_10 : R.drawable.beige_button_10);
            state.setTextColor(activity.getColor(confirm ? R.color.white : R.color.black));
            add.setOnClickListener(v -> Friends.sendRequest(uid));
            state.setOnClickListener(v -> {
                if (s == Friends.FRIENDS) PostActions.openProfile(activity, uid);
                else Friends.primaryAction(uid, s);
            });
        }

        void release() {
            if (handle != null) handle.remove();
            handle = null;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(uids.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull VH h) {
        h.release();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
            if (vh instanceof VH) ((VH) vh).release();
        }
    }

    @Override
    public int getItemCount() {
        return uids.size();
    }
}
