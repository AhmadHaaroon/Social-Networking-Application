package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** Internet validation and local-network access are separate when using XAMPP on Wi-Fi. */
public final class Connection {

    private Connection() {
    }

    public static void watch(Context context) {
        Context app = context.getApplicationContext();
        ConnectivityManager manager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            private boolean available;

            @Override
            public void onCapabilitiesChanged(android.net.Network network, NetworkCapabilities caps) {
                boolean connected = Connection.connected(app);
                if (connected && !available) SyncWorker.retryNow(app);
                available = connected;
            }

            @Override
            public void onLost(android.net.Network network) {
                available = false;
            }
        });
    }

    public static boolean connected(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public static boolean internet(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
