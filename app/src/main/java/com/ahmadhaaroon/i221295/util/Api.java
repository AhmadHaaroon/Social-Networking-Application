package com.ahmadhaaroon.i221295.util;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.ahmadhaaroon.i221295.BuildConfig;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** One Volley queue; SQLite owns caching and the worker owns retries. */
public final class Api {

    private static RequestQueue queue;

    private Api() {
    }

    public static void init(Context context) {
        queue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public static final class Failure extends Exception {
        public final int status;

        Failure(String message, int status) {
            super(message);
            this.status = status;
        }

        public boolean retryable() {
            return status == 0 || status == 408 || status == 429 || status >= 500;
        }
    }

    /** Run on a background thread. Never log bodies or authorization headers. */
    public static JSONObject call(String path, JSONObject body, String token) throws Exception {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        String base = BuildConfig.API_BASE_URL;
        if (!base.endsWith("/")) base += "/";
        JsonObjectRequest request = new JsonObjectRequest(body == null ? Request.Method.GET : Request.Method.POST,
                base + path, body, future, future) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                if (token != null && !token.isEmpty()) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(12_000, 0, 1f));
        queue.add(request);
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            int status = 0;
            String message = "Can't reach Kinnect. Check Wi-Fi and that XAMPP is running.";
            if (cause instanceof VolleyError) {
                VolleyError error = (VolleyError) cause;
                if (error.networkResponse != null) {
                    status = error.networkResponse.statusCode;
                    message = "Server error (" + status + "). Check the Kinnect backend setup.";
                    try {
                        message = new JSONObject(new String(error.networkResponse.data, StandardCharsets.UTF_8))
                                .optString("error", message);
                    } catch (Exception ignored) {
                    }
                }
            }
            throw new Failure(message, status);
        } catch (java.util.concurrent.TimeoutException e) {
            request.cancel();
            throw new Failure("The server did not respond. Check your connection and try again.", 0);
        } catch (InterruptedException e) {
            request.cancel();
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
