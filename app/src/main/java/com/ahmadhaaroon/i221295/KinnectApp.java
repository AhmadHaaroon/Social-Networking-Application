package com.ahmadhaaroon.i221295;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.ahmadhaaroon.i221295.util.Api;
import com.ahmadhaaroon.i221295.util.People;
import com.ahmadhaaroon.i221295.util.MediaFiles;
import com.ahmadhaaroon.i221295.ui.ScreenSync;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.WebSession;
import com.ahmadhaaroon.i221295.util.SyncWorker;
import com.ahmadhaaroon.i221295.services.PushNotifier;

/**
 * Initializes local sessions, SQLite and networking, and tracks foreground activity.
 */
public class KinnectApp extends Application {

    private static int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        WebSession.init(this);
        LocalDb.init(this);
        Api.init(this);
        People.init(this);
        com.ahmadhaaroon.i221295.util.Content.init(this);
        MediaFiles.init(this);
        PushNotifier.createChannels(this);
        SyncWorker.schedule(this);
        com.ahmadhaaroon.i221295.util.Connection.watch(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                ScreenSync.start(activity);
                startedActivities++;
                if (startedActivities == 1) onForegroundChanged(true);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                ScreenSync.stop(activity);
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) onForegroundChanged(false);
            }

            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity a) {
                com.ahmadhaaroon.i221295.ui.ScreenChrome.apply(a);
            }

            @Override
            public void onActivityPaused(@NonNull Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity a) {
                ScreenSync.destroy(a);
            }
        });
    }

    private void onForegroundChanged(boolean fg) {
        if (fg) SyncWorker.schedule(this);
    }

    public static boolean isInForeground() {
        return startedActivities > 0;
    }

    /** False until app/google-services.json has been added from the Firebase console. */
    public static boolean firebaseReady(Context c) {
        return !FirebaseApp.getApps(c).isEmpty();
    }
}
