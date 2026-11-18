package com.ahmadhaaroon.i221295.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.ahmadhaaroon.i221295.R;

/** All cover displays use the same 2:1 crop as the photo editor. */
public final class ProfileLayout {

    private ProfileLayout() {
    }

    public static void own(Activity activity) {
        View cover = activity.findViewById(R.id.cover_section);
        cover.post(() -> {
            int height = cover.getWidth() / 2;
            setHeight(cover, height);
            View avatar = activity.findViewById(R.id.avatar_section);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) avatar.getLayoutParams();
            params.topMargin = height - (int) (80 * activity.getResources().getDisplayMetrics().density);
            avatar.setLayoutParams(params);
        });
    }

    public static void other(Activity activity) {
        View cover = activity.findViewById(R.id.cover);
        cover.post(() -> {
            int height = cover.getWidth() / 2;
            setHeight(cover, height);
            setHeight((View) cover.getParent(), height + (int) (78 * activity.getResources().getDisplayMetrics().density));
        });
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }
}
