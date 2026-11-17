package com.ahmadhaaroon.i221295;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.AuthFlow;
import com.ahmadhaaroon.i221295.util.Badges;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.Subs;
import com.ahmadhaaroon.i221295.util.UserCache;

/** Menu (screen 19). Log out signs out, marks the user offline and clears the back stack. */
public class MenuActivity extends AppCompatActivity {

    private final Subs subs = new Subs();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        NavHelper.setup(this, NavHelper.MENU, "Menu");
        Badges.messages(subs, findViewById(R.id.msgBadge));
        Badges.notifications(subs, findViewById(R.id.notifBadge));

        String me = Db.uid();
        ((AvatarView) findViewById(R.id.avJW)).bind(me);
        TextView name = findViewById(R.id.menuName);
        UserCache.get(me, u -> name.setText(u.displayName()));

        findViewById(R.id.cardProfile).setOnClickListener(v -> startActivity(new Intent(this, MyProfileActivity.class)));
        findViewById(R.id.cardMarket).setOnClickListener(v -> NavHelper.go(this, MarketplaceActivity.class));
        findViewById(R.id.cardFriends).setOnClickListener(v -> NavHelper.go(this, FriendsActivity.class));

        findViewById(R.id.btnLogout).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setPositiveButton("Log out", (d, w) -> AuthFlow.logout(this))
                .setNegativeButton("Cancel", null)
                .show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }
}
