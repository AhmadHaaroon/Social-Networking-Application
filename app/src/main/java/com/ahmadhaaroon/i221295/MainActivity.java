package com.ahmadhaaroon.i221295;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.ahmadhaaroon.i221295.util.WebSession;
import com.ahmadhaaroon.i221295.util.AuthFlow;

/** Splash (screen 01): visible for 5 seconds, then Home if already signed in, else Log in. */
public class MainActivity extends AppCompatActivity {

    static final long SPLASH_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = this::route;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(next, SPLASH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(next);
    }

    private void route() {
        if (WebSession.valid()) {
            AuthFlow.afterSignIn(this);
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}
