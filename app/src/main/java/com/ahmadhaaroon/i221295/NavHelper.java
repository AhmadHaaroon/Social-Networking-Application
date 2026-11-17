package com.ahmadhaaroon.i221295;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

public class
NavHelper {

    public static final int HOME = 0, FRIENDS = 1, MARKET = 2, NOTIF = 3, MENU = 4;

    public static void setup(final Activity a, int selected, String title) {
        int[] tabs = {R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4};
        for (int i = 0; i < 5; i++) {
            final int index = i;
            final int sel = selected;
            click(a, tabs[i], () -> {
                if (index == sel) return;
                switch (index) {
                    case HOME: goHome(a); break;
                    case FRIENDS: go(a, FriendsActivity.class); break;
                    case MARKET: go(a, MarketplaceActivity.class); break;
                    case NOTIF: go(a, NotificationsActivity.class); break;
                    case MENU: go(a, MenuActivity.class); break;
                }
            });
        }

        click(a, R.id.btnMessenger, () -> open(a, ChatsActivity.class));
        click(a, R.id.btnSearch, () -> open(a, SearchActivity.class));
    }

    public static void go(Activity a, Class<?> target) {
        if (open(a, target)) a.finish();
    }

    /** Temporary boundary while each existing screen is migrated to PHP. */
    public static boolean open(Activity a, Class<?> target) {
        if (target != StartSocialMediaActivity.class && target != MenuActivity.class
                && target != MyProfileActivity.class && target != EditProfileActivity.class
                && target != MarketplaceActivity.class) {
            pendingFeature(a);
            return false;
        }
        a.startActivity(new Intent(a, target));
        return true;
    }

    public static void pendingFeature(Activity a) {
        Toast.makeText(a, "This feature will be connected in a later migration stage.", Toast.LENGTH_SHORT).show();
    }

    public static void goHome(Activity a) {
        Intent i = new Intent(a, StartSocialMediaActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(i);
        a.finish();
    }

    public static void click(Activity a, int id, Runnable action) {
        View v = a.findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}
