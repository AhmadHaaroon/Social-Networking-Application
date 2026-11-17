package com.ahmadhaaroon.i221295.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ahmadhaaroon.i221295.EditProfileActivity;
import com.ahmadhaaroon.i221295.LoginActivity;
import com.ahmadhaaroon.i221295.StartSocialMediaActivity;
import com.ahmadhaaroon.i221295.models.User;

/** What happens right after a user signs in, and on log out. */
public final class AuthFlow {

    private AuthFlow() {
    }

    public static void afterSignIn(Activity a) {
        User u = LocalDb.get().user(WebSession.uid());
        Intent next;
        if (!WebSession.valid() || u == null) {
            next = new Intent(a, LoginActivity.class);
        } else {
            Session.saveRecentLogin(a, u);
            next = u.profileComplete ? new Intent(a, StartSocialMediaActivity.class) : EditProfileActivity.setup(a);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(next);
        a.finish();
    }

    /** Pending content is discarded only after the user explicitly chooses to log out. */
    public static void logout(Activity a) {
        if (!LocalDb.get().status(WebSession.uid()).isEmpty()) {
            new AlertDialog.Builder(a)
                    .setTitle("Unsynced changes")
                    .setMessage("Logging out clears this device's saved content and discards changes that haven't synced.")
                    .setNegativeButton("Stay", null)
                    .setPositiveButton("Discard and log out", (d, w) -> finishLogout(a))
                    .show();
        } else {
            finishLogout(a);
        }
    }

    private static void finishLogout(Activity a) {
        Profiles.logout(a, () -> {
            Intent i = new Intent(a, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            a.startActivity(i);
            a.finishAffinity();
        }, e -> Toast.makeText(a, "Could not log out: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
    }
}
