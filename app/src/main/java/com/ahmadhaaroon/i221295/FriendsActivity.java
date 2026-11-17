package com.ahmadhaaroon.i221295;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ahmadhaaroon.i221295.adapters.PersonAdapter;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.People;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.Friends;
import com.ahmadhaaroon.i221295.util.Mutuals;
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
 * Friends (screen 14): live pending-request count with Confirm / Delete, "Your friends", and
 * "People you may know" ranked by mutual friends.
 */
public class FriendsActivity extends AppCompatActivity {

    private static final int COLLAPSED_REQUESTS = 4;

    private final Subs subs = new Subs();
    private final List<String> requestUids = new ArrayList<>();
    private final Map<String, Long> requestTimes = new HashMap<>();
    private final Set<String> friends = new HashSet<>();
    private final RequestAdapter requestAdapter = new RequestAdapter();
    private PersonAdapter people;
    private boolean showAllRequests;
    private boolean showingFriends;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.friends_screen);

        NavHelper.click(this, R.id.btn_search, () -> startActivity(new Intent(this, SearchActivity.class)));
        NavHelper.click(this, R.id.messageIcon, () -> NavHelper.open(this, ChatsActivity.class));
        NavHelper.click(this, R.id.tab_home, () -> NavHelper.goHome(this));
        NavHelper.click(this, R.id.tab_shop, () -> NavHelper.go(this, MarketplaceActivity.class));
        NavHelper.click(this, R.id.bell_icon, () -> NavHelper.go(this, NotificationsActivity.class));
        NavHelper.click(this, R.id.tab_more, () -> NavHelper.go(this, MenuActivity.class));
        NavHelper.click(this, R.id.suggestions_chip, () -> showFriends(false));
        NavHelper.click(this, R.id.your_friends_chip, () -> showFriends(true));
        NavHelper.click(this, R.id.see_all_requests, () -> {
            showAllRequests = !showAllRequests;
            ((TextView) findViewById(R.id.see_all_requests)).setText(showAllRequests ? R.string.show_less : R.string.see_all);
            requestAdapter.notifyDataSetChanged();
        });

        RecyclerView requests = findViewById(R.id.requestList);
        requests.setLayoutManager(new LinearLayoutManager(this));
        requests.setAdapter(requestAdapter);
        people = new PersonAdapter(this);
        RecyclerView peopleList = findViewById(R.id.peopleList);
        peopleList.setLayoutManager(new LinearLayoutManager(this));
        peopleList.setAdapter(people);

        findViewById(R.id.msgBadge).setVisibility(View.GONE);
        findViewById(R.id.notifBadge).setVisibility(View.GONE);
        listen();
        showFriends(false);
    }

    private void listen() {
        Runnable changed = this::refreshPeople;
        LocalDb.observe(changed);
        subs.add(() -> LocalDb.removeObserver(changed));
        refreshPeople();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Profiles.refresh(this);
    }

    private void refreshPeople() {
        requestUids.clear();
        requestTimes.clear();
        for (org.json.JSONObject row : LocalDb.get().relations()) {
            if (!row.optString("a").equals(Db.uid()) && !row.optString("b").equals(Db.uid())) continue;
            String other = row.optString("a").equals(Db.uid()) ? row.optString("b") : row.optString("a");
            if (People.state(other) != Friends.INCOMING || requestUids.contains(other)) continue;
            requestUids.add(other);
            requestTimes.put(other, row.optLong("timestamp"));
        }
        requestUids.sort((a, b) -> Long.compare(requestTimes.get(b), requestTimes.get(a)));
        ((TextView) findViewById(R.id.requests_count)).setText(String.valueOf(requestUids.size()));
        findViewById(R.id.noRequests).setVisibility(requestUids.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.see_all_requests).setVisibility(requestUids.size() > COLLAPSED_REQUESTS ? View.VISIBLE : View.GONE);
        requestAdapter.notifyDataSetChanged();
        friends.clear();
        friends.addAll(People.friends(Db.uid()));
        if (showingFriends) showFriendList();
        else loadSuggestions();
    }

    private void showFriends(boolean friendsTab) {
        showingFriends = friendsTab;
        TextView sug = findViewById(R.id.suggestions_chip);
        TextView yours = findViewById(R.id.your_friends_chip);
        sug.setBackgroundResource(friendsTab ? R.drawable.beige_chip : R.drawable.lightseagreen_chip);
        sug.setTextColor(getColor(friendsTab ? R.color.black : R.color.SeaGreen));
        yours.setBackgroundResource(friendsTab ? R.drawable.lightseagreen_chip : R.drawable.beige_chip);
        yours.setTextColor(getColor(friendsTab ? R.color.SeaGreen : R.color.black));
        findViewById(R.id.requestsSection).setVisibility(friendsTab ? View.GONE : View.VISIBLE);
        if (friendsTab) showFriendList();
        else loadSuggestions();
    }

    private void showFriendList() {
        List<String> list = new ArrayList<>(friends);
        ((TextView) findViewById(R.id.listHeading)).setText(getString(R.string.your_friends_count, list.size()));
        people.setUsers(list);
        TextView empty = findViewById(R.id.emptyPeople);
        empty.setText(R.string.no_friends_yet);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Friends of friends first (ranked by how many mutual friends), then other Kinnect users. */
    private void loadSuggestions() {
        String me = Db.uid();
        ((TextView) findViewById(R.id.listHeading)).setText(R.string.people_you_may_know);
        Set<String> exclude = new HashSet<>(friends);
        exclude.add(me);
        exclude.addAll(requestUids);
        List<String> list = People.cachedSearch("", "everyone");
        list.removeAll(exclude);
        list.sort((a, b) -> Integer.compare(mutualCount(b), mutualCount(a)));
        if (list.size() > 30) list = new ArrayList<>(list.subList(0, 30));
        people.setUsers(list);
        TextView empty = findViewById(R.id.emptyPeople);
        empty.setText(R.string.no_suggestions);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int mutualCount(String uid) {
        List<String> mutual = People.friends(uid);
        mutual.retainAll(friends);
        return mutual.size();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }

    private class RequestAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_request, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            String uid = requestUids.get(position);
            View v = h.itemView;
            ((AvatarView) v.findViewById(R.id.requestAvatar)).bind(uid);
            TextView name = v.findViewById(R.id.requestName);
            name.setText("");
            UserCache.get(uid, u -> name.setText(u.displayName()));
            Long t = requestTimes.get(uid);
            ((TextView) v.findViewById(R.id.requestTime)).setText(t == null ? "" : TimeFmt.ago(t));

            AvatarView m1 = v.findViewById(R.id.mutual1);
            AvatarView m2 = v.findViewById(R.id.mutual2);
            TextView mText = v.findViewById(R.id.mutualText);
            m1.setVisibility(View.GONE);
            m2.setVisibility(View.GONE);
            mText.setText("");
            Mutuals.list(uid, list -> {
                if (h.getBindingAdapterPosition() != position) return;
                if (list.size() > 0) {
                    m1.setVisibility(View.VISIBLE);
                    m1.bind(list.get(0));
                }
                if (list.size() > 1) {
                    m2.setVisibility(View.VISIBLE);
                    m2.bind(list.get(1));
                }
                mText.setText(list.isEmpty() ? "" : getResources().getQuantityString(R.plurals.mutual_friends, list.size(), list.size()));
            });

            v.findViewById(R.id.btnConfirm).setOnClickListener(x -> Friends.accept(uid));
            v.findViewById(R.id.btnDelete).setOnClickListener(x -> Friends.decline(uid));
            v.setOnClickListener(x -> PostActions.openProfile(FriendsActivity.this, uid));
        }

        @Override
        public int getItemCount() {
            return showAllRequests ? requestUids.size() : Math.min(COLLAPSED_REQUESTS, requestUids.size());
        }
    }
}
