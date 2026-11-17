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
        findViewById(R.id.msgBadge).setVisibility(android.view.View.GONE);
        findViewById(R.id.notifBadge).setVisibility(android.view.View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subs.clear();
    }
}
