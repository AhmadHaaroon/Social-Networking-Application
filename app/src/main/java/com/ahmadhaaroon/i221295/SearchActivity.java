package com.ahmadhaaroon.i221295;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ahmadhaaroon.i221295.adapters.PersonAdapter;
import com.ahmadhaaroon.i221295.adapters.PostAdapter;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Content;
import com.ahmadhaaroon.i221295.util.People;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Search (screen 13). Cached people appear first, then PHP refreshes matches by name or username.
 */
public class SearchActivity extends AppCompatActivity {

    private static final int LIMIT = 20;
    private static final int MAX_RECENT = 8;

    private EditText query;
    private RecyclerView results;
    private PersonAdapter people;
    private PostAdapter posts;
    private boolean postMode;
    private int requestToken;
    private String filter = "everyone";
    private final Runnable changed = this::showCached;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runSearch = this::search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_screen);
        query = findViewById(R.id.tv_query);
        results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        people = new PersonAdapter(this);
        posts = new PostAdapter(this, null);
        results.setAdapter(people);

        NavHelper.click(this, R.id.backarrow, this::finish);
        NavHelper.click(this, R.id.btn_clear, () -> query.setText(""));
        ((TextView) findViewById(R.id.chip_all)).setText("Everyone");
        NavHelper.click(this, R.id.chip_all, () -> {
            filter = "everyone";
            setPostMode(false);
        });
        NavHelper.click(this, R.id.chip_people, () -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Find people")
                .setItems(new String[]{"Everyone", "Friends", "Requests"}, (d, which) -> {
                    filter = new String[]{"everyone", "friends", "requests"}[which];
                    ((TextView) findViewById(R.id.chip_people)).setText(which == 0 ? "People" : which == 1 ? "Friends" : "Requests");
                    setPostMode(false);
                }).show());
        NavHelper.click(this, R.id.chip_posts, () -> setPostMode(true));
        NavHelper.click(this, R.id.chip_groups, () -> Toast.makeText(this, "Groups are coming soon", Toast.LENGTH_SHORT).show());
        NavHelper.click(this, R.id.chip_marketplace, () -> startActivity(new Intent(this, MarketplaceActivity.class)));
        NavHelper.click(this, R.id.clear_recent, () -> {
            Session.setRecentSearches(this, "");
            showRecent();
        });

        people.setOnPick(uid -> {
            remember(query.getText().toString().trim());
            com.ahmadhaaroon.i221295.util.PostActions.openProfile(this, uid);
        });

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                requestToken++;
                handler.removeCallbacks(runSearch);
                handler.postDelayed(runSearch, 250);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        query.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                remember(query.getText().toString().trim());
                search();
                return true;
            }
            return false;
        });
        query.requestFocus();
        showRecent();
    }

    private void setPostMode(boolean on) {
        postMode = on;
        int[] chips = {R.id.chip_all, R.id.chip_people, R.id.chip_posts};
        for (int id : chips) {
            boolean selected = on ? id == R.id.chip_posts : id == ("everyone".equals(filter) ? R.id.chip_all : R.id.chip_people);
            TextView chip = findViewById(id);
            chip.setBackgroundResource(selected ? R.drawable.lightseagreen_chip : R.drawable.beige_chip);
            chip.setTextColor(getColor(selected ? R.color.SeaGreen : R.color.black));
        }
        results.setAdapter(on ? posts : people);
        ((TextView) findViewById(R.id.people_heading)).setText(on ? R.string.chip_posts : R.string.people);
        search();
    }

    private void search() {
        String q = query.getText().toString().trim().toLowerCase(Locale.ROOT);
        int token = ++requestToken;
        boolean empty = TextUtils.isEmpty(q) && "everyone".equals(filter);
        findViewById(R.id.recent_section).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.people_heading).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            people.setUsers(new ArrayList<>());
            posts.setPosts(new ArrayList<>());
            findViewById(R.id.noResults).setVisibility(View.GONE);
            showRecent();
            return;
        }
        if (postMode) searchPosts(q, token);
        else searchPeople(q, token);
    }

    private void searchPeople(String q, int token) {
        showPeople(People.cachedSearch(q, filter));
        People.search(q, filter, ids -> {
            if (!isDestroyed() && token == requestToken) showPeople(ids);
        });
    }

    private void showPeople(List<String> uids) {
        people.setUsers(uids);
        findViewById(R.id.noResults).setVisibility(uids.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCached() {
        if (postMode) { searchPosts(query.getText().toString().trim().toLowerCase(Locale.ROOT), requestToken); return; }
        String q = query.getText().toString().trim();
        if (!q.isEmpty() || !"everyone".equals(filter)) showPeople(People.cachedSearch(q, filter));
    }

    private void searchPosts(String q, int token) {
        List<com.ahmadhaaroon.i221295.models.Post> matches = new ArrayList<>();
        for (com.ahmadhaaroon.i221295.models.Post p : Content.posts(null)) {
            if (p.text != null && p.text.toLowerCase(Locale.ROOT).contains(q)) matches.add(p);
        }
        posts.setPosts(matches);
        findViewById(R.id.noResults).setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDb.observe(changed);
        showCached();
        Profiles.refresh(this);
    }

    @Override
    protected void onPause() {
        LocalDb.removeObserver(changed);
        super.onPause();
    }

    private List<String> recents() {
        String raw = Session.recentSearches(this);
        List<String> out = new ArrayList<>();
        if (!raw.isEmpty()) out.addAll(Arrays.asList(raw.split("\n")));
        return out;
    }

    private void remember(String q) {
        if (q.isEmpty()) return;
        List<String> list = recents();
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_RECENT) list.remove(list.size() - 1);
        Session.setRecentSearches(this, TextUtils.join("\n", list));
    }

    private void showRecent() {
        LinearLayout box = findViewById(R.id.recentList);
        box.removeAllViews();
        List<String> list = recents();
        findViewById(R.id.recent_section).setVisibility(list.isEmpty() ? View.GONE
                : (query.getText().length() == 0 ? View.VISIBLE : View.GONE));
        for (String r : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_recent_search, box, false);
            ((TextView) row.findViewById(R.id.recentText)).setText(r);
            row.setOnClickListener(v -> {
                query.setText(r);
                query.setSelection(r.length());
            });
            row.findViewById(R.id.recentRemove).setOnClickListener(v -> {
                List<String> l = recents();
                l.remove(r);
                Session.setRecentSearches(this, TextUtils.join("\n", l));
                showRecent();
            });
            box.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        posts.release();
    }
}
