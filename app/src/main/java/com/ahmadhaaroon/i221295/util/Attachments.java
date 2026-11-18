package com.ahmadhaaroon.i221295.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.ahmadhaaroon.i221295.models.Message;
import java.io.*;

/** Copies picked files into durable app storage before adding the message to SQLite. */
public final class Attachments {
    private Attachments() {}

    public static void send(Activity activity, Uri uri, String other) {
        String token = WebSession.token();
        new Thread(
                        () -> {
                            try {
                                String mime = activity.getContentResolver().getType(uri);
                                if (mime == null) mime = "application/octet-stream";
                                String name = "Attachment";
                                try (android.database.Cursor c =
                                        activity.getContentResolver()
                                                .query(
                                                        uri,
                                                        new String[] {
                                                            android.provider.OpenableColumns
                                                                    .DISPLAY_NAME
                                                        },
                                                        null,
                                                        null,
                                                        null)) {
                                    if (c != null && c.moveToFirst()) name = c.getString(0);
                                }
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                try (InputStream in =
                                        activity.getContentResolver().openInputStream(uri)) {
                                    if (in == null) throw new IOException("Cannot open attachment");
                                    byte[] b = new byte[8192];
                                    int n;
                                    while ((n = in.read(b)) != -1) {
                                        bytes.write(b, 0, n);
                                        if (bytes.size() > 10 * 1024 * 1024)
                                            throw new IOException(
                                                    "Attachments must be 10 MB or smaller.");
                                    }
                                }
                                Message m = new Message();
                                m.mime = mime;
                                m.fileName = name;
                                m.type = mime.startsWith("video/") ? Message.VIDEO : Message.FILE;
                                synchronized (Profiles.LOCK) {
                                    if (!token.equals(WebSession.token()))
                                        throw new IOException("Please log in again.");
                                    m.imageUrl = MediaFiles.store(bytes.toByteArray(), mime);
                                }
                                activity.runOnUiThread(() -> Chats.send(other, m));
                            } catch (Exception e) {
                                activity.runOnUiThread(() ->
                                        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG)
                                                .show());
                            }
                        },
                        "Save attachment")
                .start();
    }

    public static void open(Activity activity, Message message) {
        MediaFiles.resolve(
                message.imageUrl,
                file -> {
                    if (file == null) {
                        Toast.makeText(
                                        activity,
                                        "Connect to download this attachment first.",
                                        Toast.LENGTH_LONG)
                                .show();
                        return;
                    }
                    Uri uri =
                            androidx.core.content.FileProvider.getUriForFile(
                                    activity, activity.getPackageName() + ".attachments", file);
                    Intent intent =
                            new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(
                                            uri,
                                            message.mime == null
                                                    ? "application/octet-stream"
                                                    : message.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        activity.startActivity(Intent.createChooser(intent, "Open attachment"));
                    } catch (android.content.ActivityNotFoundException e) {
                        Toast.makeText(
                                        activity,
                                        "No app is installed to open this file.",
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }
}
