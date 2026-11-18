package com.ahmadhaaroon.i221295;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.ahmadhaaroon.i221295.ui.PhotoCropView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/** Profile and cover photos share one crop editor and keep the original screen's teal controls. */
public class PhotoAdjustActivity extends AppCompatActivity {

    private PhotoCropView crop;
    private boolean saving;
    private CustomTarget<Bitmap> target;

    public static Intent intent(Context context, Uri uri, boolean cover) {
        return new Intent(context, PhotoAdjustActivity.class).putExtra("uri", uri.toString()).putExtra("cover", cover);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean cover = getIntent().getBooleanExtra("cover", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Cancel", this::finish));
        TextView title = new TextView(this);
        title.setText(cover ? "Adjust cover photo" : "Adjust profile photo");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF0B5F63);
        title.setTextSize(17);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Use photo", this::save));
        root.addView(header);
        crop = new PhotoCropView(this, !cover);
        root.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = new TextView(this);
        hint.setText("Drag to position · Pinch to zoom");
        hint.setTextColor(0xFF0B5F63);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(16, 16, 16, 16);
        root.addView(hint);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(button("Rotate", () -> crop.rotate()));
        controls.addView(button("Flip", () -> crop.flip()));
        controls.addView(button("Reset", () -> crop.reset()));
        root.addView(controls);
        setContentView(root);
        target = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                crop.setBitmap(bitmap);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }

            @Override
            public void onLoadFailed(@Nullable Drawable drawable) {
                Toast.makeText(PhotoAdjustActivity.this, "Could not open this photo", Toast.LENGTH_LONG).show();
                finish();
            }
        };
        Glide.with(this).asBitmap().load(getIntent().getStringExtra("uri")).override(2048, 2048).fitCenter().into(target);
    }

    private TextView button(String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTextColor(0xFF0B5F63);
        int padding = (int) (14 * getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void save() {
        if (saving || !crop.ready()) return;
        saving = true;
        Bitmap result = crop.export();
        new Thread(() -> {
            try {
                File file = new File(getCacheDir(), "crop-" + UUID.randomUUID() + ".jpg");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    result.compress(Bitmap.CompressFormat.JPEG, 92, out);
                    out.getFD().sync();
                }
                runOnUiThread(() -> {
                    setResult(RESULT_OK, new Intent().setData(Uri.fromFile(file)));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false;
                    Toast.makeText(this, "Could not save photo", Toast.LENGTH_LONG).show();
                });
            } finally {
                result.recycle();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (target != null) Glide.with(getApplicationContext()).clear(target);
    }
}
