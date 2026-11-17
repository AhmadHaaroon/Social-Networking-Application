package com.ahmadhaaroon.i221295;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ahmadhaaroon.i221295.util.Badges;
import com.ahmadhaaroon.i221295.util.Subs;

public class MarketplaceActivity extends AppCompatActivity {

    private final Subs subs = new Subs();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marketplace);
        NavHelper.setup(this, NavHelper.MARKET, "Marketplace");
        Badges.messages(subs, findViewById(R.id.msgBadge));
        Badges.notifications(subs, findViewById(R.id.notifBadge));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }
}
