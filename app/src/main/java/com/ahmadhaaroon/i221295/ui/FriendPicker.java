package com.ahmadhaaroon.i221295.ui;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ahmadhaaroon.i221295.util.Callback;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.People;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.List;

/** Uses the same cached friend list as the Friends screen. */
public final class FriendPicker {

    private FriendPicker() {
    }

    public static void show(Activity activity, String title, Callback<String> picked) {
        List<String> ids = People.friends(Db.uid());
        if (ids.isEmpty()) {
            Toast.makeText(activity, "Add a friend first", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) names[i] = UserCache.peek(ids.get(i)).displayName();
        new AlertDialog.Builder(activity).setTitle(title)
                .setItems(names, (d, which) -> picked.onResult(ids.get(which))).show();
    }
}
