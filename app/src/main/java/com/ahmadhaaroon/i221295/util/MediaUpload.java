package com.ahmadhaaroon.i221295.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Prepares durable local media; the pending action uploads it through PHP during sync. */
public final class MediaUpload {

    public interface Listener {
        void onSuccess(String ref);

        void onError(Exception e);
    }

    public static final long MAX_VIDEO_BYTES = 10L * 1024 * 1024;
    private static final int MAX_SIDE = 1280;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MediaUpload() {
    }

    public static boolean isVideo(Context c, Uri uri) {
        String type = c.getContentResolver().getType(uri);
        if (type != null) return type.startsWith("video");
        String path = uri.toString().toLowerCase();
        return path.endsWith(".mp4") || path.endsWith(".3gp") || path.endsWith(".webm") || path.endsWith(".mkv");
    }

    public static void upload(Context context, Uri uri, String folder, Listener l) {
        Context app = context.getApplicationContext();
        String owner = Db.uid();
        boolean video = isVideo(app, uri);
        IO.execute(() -> {
            try {
                byte[] bytes = video ? readVideo(app, uri) : compress(app, uri);
                String ref;
                synchronized (Profiles.LOCK) {
                    if (owner == null || !owner.equals(Db.uid())) throw new IOException("Please log in again.");
                    ref = MediaFiles.store(bytes, video ? "video/mp4" : "image/jpeg");
                }
                MAIN.post(() -> l.onSuccess(ref));
            } catch (Exception e) {
                MAIN.post(() -> l.onError(e));
            }
        });
    }

    /** Uploads the files one after another and returns their references in the same order. */
    public static void uploadAll(Context c, List<Uri> uris, String folder, Callback<List<String>> done, Callback<Exception> failed) {
        List<String> refs = new ArrayList<>();
        uploadNext(c, uris, 0, folder, refs, done, failed);
    }

    private static void uploadNext(Context c, List<Uri> uris, int i, String folder, List<String> refs,
                                   Callback<List<String>> done, Callback<Exception> failed) {
        if (i >= uris.size()) {
            done.onResult(refs);
            return;
        }
        upload(c, uris.get(i), folder, new Listener() {
            @Override
            public void onSuccess(String ref) {
                refs.add(ref);
                uploadNext(c, uris, i + 1, folder, refs, done, failed);
            }

            @Override
            public void onError(Exception e) {
                failed.onResult(e);
            }
        });
    }

    public static void delete(String ref) {
        // The server owns deletion once no saved content references the uploaded file.
    }

    private static byte[] readVideo(Context c, Uri uri) throws IOException {
        long size = sizeOf(c, uri);
        if (size > MAX_VIDEO_BYTES) throw new IOException("Video is too large (max 10 MB). Try a shorter clip.");
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open video");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_VIDEO_BYTES) throw new IOException("Video is too large (max 10 MB). Try a shorter clip.");
            }
            return out.toByteArray();
        }
    }

    private static long sizeOf(Context c, Uri uri) {
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new java.io.File(uri.getPath()).length();
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && !cur.isNull(0)) return cur.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static byte[] compress(Context c, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Unsupported image");

        int rotation = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (IOException ignored) {
        }

        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
        if (scale < 1f || rotation != 0) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 78, out);
        return out.toByteArray();
    }
}
