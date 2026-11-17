package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.HttpHeaderParser;
import com.ahmadhaaroon.i221295.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Durable image files survive process death while their profile action is waiting to sync. */
public final class MediaFiles {

    private static File directory;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MediaFiles() {
    }

    public static void init(Context context) {
        directory = new File(context.getFilesDir(), "media");
        directory.mkdirs();
    }

    public static boolean isRef(String ref) {
        return ref != null && ref.matches("media:[a-f0-9-]{36}");
    }

    public static File file(String ref) {
        return new File(directory, ref.substring(6));
    }

    public static String store(byte[] bytes) throws Exception {
        String ref = "media:" + UUID.randomUUID();
        write(file(ref), bytes);
        return ref;
    }

    private static void write(File file, byte[] bytes) throws Exception {
        File part = new File(file + ".part");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!part.renameTo(file)) throw new java.io.IOException("Could not save the image on this device.");
    }

    public static void resolve(String ref, Callback<File> done) {
        if (file(ref).isFile()) {
            done.onResult(file(ref));
            return;
        }
        String token = WebSession.token();
        IO.execute(() -> {
            File result = null;
            try {
                byte[] bytes = request("media/file.php?id=" + ref.substring(6), null, null, token);
                synchronized (Profiles.LOCK) {
                    if (!token.equals(WebSession.token())) return;
                    write(file(ref), bytes);
                    new File(file(ref) + ".sent").createNewFile();
                }
                result = file(ref);
            } catch (Exception ignored) {
            }
            File loaded = result;
            MAIN.post(() -> done.onResult(loaded));
        });
    }

    public static void uploadProfile(JSONObject p, String token) throws Exception {
        for (String key : new String[]{"photoUrl", "coverUrl"}) {
            String ref = p.optString(key);
            if (!isRef(ref) || !file(ref).exists() || new File(file(ref) + ".sent").exists()) continue;
            String boundary = "Kinnect" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"id\"\r\n\r\n"
                    + ref.substring(6) + "\r\n--" + boundary
                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try (java.io.FileInputStream in = new java.io.FileInputStream(file(ref))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) body.write(buffer, 0, n);
            }
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            request("media/upload.php", body.toByteArray(), "multipart/form-data; boundary=" + boundary, token);
            synchronized (Profiles.LOCK) {
                if (token.equals(WebSession.token()) && file(ref).exists()) new File(file(ref) + ".sent").createNewFile();
            }
        }
    }

    private static byte[] request(String path, byte[] body, String type, String token) throws Exception {
        RequestFuture<byte[]> future = RequestFuture.newFuture();
        String base = BuildConfig.API_BASE_URL.endsWith("/") ? BuildConfig.API_BASE_URL : BuildConfig.API_BASE_URL + "/";
        Request<byte[]> request = new Request<byte[]>(body == null ? Request.Method.GET : Request.Method.POST, base + path, future) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }

            @Override
            public byte[] getBody() {
                return body;
            }

            @Override
            public String getBodyContentType() {
                return type == null ? "application/octet-stream" : type;
            }

            @Override
            protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
                return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
            }

            @Override
            protected void deliverResponse(byte[] bytes) {
                future.onResponse(bytes);
            }
        };
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(20000, 0, 1f));
        Api.add(request);
        try {
            return future.get(25, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw Api.failure(e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            request.cancel();
            throw new Api.Failure("Image upload timed out. It will retry automatically.", 0);
        } catch (InterruptedException e) {
            request.cancel();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public static void clear() {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
    }
}
