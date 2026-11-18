package com.ahmadhaaroon.i221295.ui;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ahmadhaaroon.i221295.LoginActivity;
import com.ahmadhaaroon.i221295.CreateAccountActivity;
import com.ahmadhaaroon.i221295.StartSocialMediaActivity;
import com.ahmadhaaroon.i221295.MyProfileActivity;
import com.ahmadhaaroon.i221295.EditProfileActivity;
import com.ahmadhaaroon.i221295.ProfileActivity;
import com.ahmadhaaroon.i221295.FriendsActivity;
import com.ahmadhaaroon.i221295.SearchActivity;
import com.ahmadhaaroon.i221295.MenuActivity;
import com.ahmadhaaroon.i221295.MarketplaceActivity;
import com.ahmadhaaroon.i221295.util.Connection;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.SyncStatus;
import com.ahmadhaaroon.i221295.util.WebSession;

import java.util.HashMap;
import java.util.Map;

/** Adds the same connection strip and refresh gesture to each migrated screen. */
public final class ScreenSync {

    private static final Map<Activity, Binding> BINDINGS = new HashMap<>();

    private ScreenSync() {
    }

    public static void start(Activity activity) {
        if (!(activity instanceof LoginActivity
                || activity instanceof CreateAccountActivity
                || activity instanceof StartSocialMediaActivity
                || activity instanceof MyProfileActivity
                || activity instanceof EditProfileActivity
                || activity instanceof ProfileActivity
                || activity instanceof FriendsActivity
                || activity instanceof SearchActivity
                || activity instanceof com.ahmadhaaroon.i221295.CommentsActivity
                || activity instanceof com.ahmadhaaroon.i221295.LikeActivity
                || activity instanceof com.ahmadhaaroon.i221295.ChatActivity
                || activity instanceof com.ahmadhaaroon.i221295.ChatsActivity
                || activity instanceof com.ahmadhaaroon.i221295.NotificationsActivity
                || activity instanceof MenuActivity
                || activity instanceof MarketplaceActivity)) return;
        Binding b = BINDINGS.get(activity);
        if (b == null) {
            b = new Binding(activity);
            BINDINGS.put(activity, b);
        }
        b.start();
    }

    public static void stop(Activity activity) {
        Binding b = BINDINGS.get(activity);
        if (b != null) b.stop();
    }

    public static void destroy(Activity activity) {
        stop(activity);
        BINDINGS.remove(activity);
    }

    private static boolean scrolled(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view.canScrollVertically(-1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (scrolled(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static class Binding {
        final Activity activity;
        final TextView bar;
        final SwipeRefreshLayout swipe;
        final ConnectivityManager manager;
        final Runnable changed = this::render;
        boolean started;
        boolean refreshing;
        boolean connected;
        long refreshGeneration;
        final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                activity.runOnUiThread(() -> {
                    if (!started) return;
                    boolean available = Connection.connected(activity);
                    if (available && !connected && WebSession.valid()) Profiles.refresh(activity);
                    connected = available;
                    render();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                activity.runOnUiThread(() -> {
                    connected = Connection.connected(activity);
                    render();
                });
            }

        };

        Binding(Activity activity) {
            this.activity = activity;
            ViewGroup content = activity.findViewById(android.R.id.content);
            View original = content.getChildAt(0);
            content.removeView(original);
            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            bar = new TextView(activity);
            bar.setGravity(Gravity.CENTER);
            bar.setTextSize(12);
            int pad = (int) (5 * activity.getResources().getDisplayMetrics().density);
            bar.setPadding(pad, pad, pad, pad);
            column.addView(bar, new LinearLayout.LayoutParams(-1, -2));
            swipe = new SwipeRefreshLayout(activity);
            swipe.setColorSchemeColors(0xFF0B5F63);
            swipe.addView(original, new ViewGroup.LayoutParams(-1, -1));
            swipe.setOnChildScrollUpCallback((parent, child) -> scrolled(original));
            column.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1));
            content.addView(column, new ViewGroup.LayoutParams(-1, -1));
            swipe.setOnRefreshListener(() -> {
                if (!Connection.internet(activity)) {
                    Toast.makeText(activity, "No internet connection. Showing saved content.", Toast.LENGTH_SHORT).show();
                }
                if (!WebSession.valid() || !Connection.connected(activity)) {
                    swipe.setRefreshing(false);
                    return;
                }
                refreshGeneration = SyncStatus.completed;
                refreshing = true;
                Profiles.refresh(activity);
            });
            bar.setOnClickListener(v -> {
                String status = LocalDb.get().status(WebSession.uid());
                String message = status.startsWith("Couldn't") ? status : SyncStatus.error;
                if (!message.isEmpty()) new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("Sync status").setMessage(message)
                        .setPositiveButton("OK", null).show();
            });
            manager = (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
        }

        void start() {
            if (started) return;
            started = true;
            connected = Connection.connected(activity);
            LocalDb.observe(changed);
            manager.registerDefaultNetworkCallback(network);
            render();
        }

        void stop() {
            if (!started) return;
            started = false;
            refreshing = false;
            swipe.setRefreshing(false);
            LocalDb.removeObserver(changed);
            manager.unregisterNetworkCallback(network);
        }

        void render() {
            if (!started || activity.isFinishing()) return;
            boolean internet = Connection.internet(activity);
            String text = internet ? "Internet connected" : "No internet connection";
            String pending = LocalDb.get().status(WebSession.uid());
            if (SyncStatus.running) text += " · Updating…";
            else if (!SyncStatus.error.isEmpty() && Connection.connected(activity)) text += " · Server unavailable";
            else if (!pending.isEmpty()) text += pending.startsWith("Couldn't") ? " · A change needs attention" : " · Changes pending";
            bar.setText(text);
            bar.setVisibility(internet ? View.GONE : View.VISIBLE);
            bar.setBackgroundColor(internet ? 0xFFE1F0EC : 0xFFF4E8D6);
            bar.setTextColor(internet ? 0xFF0B5F63 : 0xFF80531C);
            if (refreshing && ((!SyncStatus.running && SyncStatus.completed > refreshGeneration)
                    || !Connection.connected(activity))) {
                refreshing = false;
                swipe.setRefreshing(false);
                if (!SyncStatus.error.isEmpty()) Toast.makeText(activity, SyncStatus.error, Toast.LENGTH_LONG).show();
            }
        }
    }
}
