package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.ahmadhaaroon.i221295.BuildConfig;
import com.ahmadhaaroon.i221295.KinnectApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;

/**
 * One authenticated server-event stream while the app is visible; SQLite remains authoritative for
 * screens.
 */
public final class Realtime {
    private static Context app;
    private static volatile boolean foreground;
    private static volatile int generation;
    private static volatile HttpURLConnection connection;
    private static String activeToken = "", registeredToken = "";
    private static final java.util.concurrent.ExecutorService IO =
            Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Realtime() {}

    public static Context context() {
        return app;
    }

    public static void init(Context c) {
        app = c.getApplicationContext();
        LocalDb.observe(Realtime::check);
    }

    public static void foreground(boolean value) {
        foreground = value;
        if (!value && WebSession.valid())
            request("realtime/status.php", Content.value("", "online", false), null, null);
        check();
    }

    private static synchronized void check() {
        String token = foreground && WebSession.valid() ? WebSession.token() : "";
        if (token.equals(activeToken)) return;
        activeToken = token;
        int run = ++generation;
        HttpURLConnection old = connection;
        if (old != null) old.disconnect();
        if (token.isEmpty()) return;
        registerDevice();
        new Thread(() -> stream(run, token), "Kinnect events").start();
    }

    private static void stream(int run, String token) {
        while (run == generation) {
            HttpURLConnection c = null;
            try {
                c =
                        (HttpURLConnection)
                                new URL(BuildConfig.API_BASE_URL + "realtime/stream.php")
                                        .openConnection();
                connection = c;
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "text/event-stream");
                c.setConnectTimeout(10000);
                c.setReadTimeout(35000);
                if (c.getResponseCode() == 401) {
                    synchronized (Profiles.LOCK) {
                        if (token.equals(WebSession.token())) {
                            WebSession.clear();
                            LocalDb.changed();
                        }
                    }
                    return;
                }
                if (c.getResponseCode() != 200) throw new IOException("Stream unavailable");
                registerDevice();
                try (BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        c.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while (run == generation && (line = in.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        JSONObject state = new JSONObject(line.substring(6));
                        synchronized (Profiles.LOCK) {
                            if (run != generation || !token.equals(WebSession.token())) return;
                            LocalDb.get().acceptSocial(state);
                            LocalDb.changed();
                        }
                    }
                }
            } catch (Exception ignored) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }

    public static void request(
            String path, JSONObject data, Callback<JSONObject> done, Callback<Exception> failed) {
        String token = WebSession.token();
        IO.execute(() -> {
            try {
                JSONObject result = Api.call(path, data, token);
                MAIN.post(() -> {
                    if (token.equals(WebSession.token()) && done != null) done.onResult(result);
                });
            } catch (Exception e) {
                if (failed != null) MAIN.post(() -> failed.onResult(e));
            }
        });
    }

    public static void typing(String other, boolean typing) {
        try {
            request(
                    "realtime/status.php",
                    new JSONObject().put("other", other).put("typing", typing),
                    null,
                    null);
        } catch (Exception ignored) {
        }
    }

    public static void registerDevice() {
        if (!WebSession.valid() || !KinnectApp.firebaseReady(app)) return;
        FirebaseMessaging.getInstance()
                .getToken()
                .addOnSuccessListener(token -> {
                    String key = WebSession.token() + token;
                    if (key.equals(registeredToken)) return;
                    try {
                        request(
                                "realtime/device.php",
                                new JSONObject().put("token", token),
                                r -> registeredToken = key,
                                null);
                    } catch (Exception ignored) {
                    }
                });
    }
}
