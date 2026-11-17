package com.ahmadhaaroon.i221295;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.ahmadhaaroon.i221295.models.User;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.Images;
import com.ahmadhaaroon.i221295.util.Session;
import com.ahmadhaaroon.i221295.util.UserCache;
import com.ahmadhaaroon.i221295.util.LocalDb;
import com.ahmadhaaroon.i221295.util.Profiles;
import com.ahmadhaaroon.i221295.util.AuthFlow;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Edit profile (screen 16). Also used as the first-time profile setup step: new accounts must
 * pick a unique username and write a bio here before they reach Home.
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String EXTRA_SETUP = "setup";

    private EditText etName, etUsername, etWebsite, etBio, etPhone, etWork, etSchool, etLocation;
    private TextView tvEmail, tvGender;
    private AvatarView avatar;
    private ImageView cover;
    private View progress;
    private TextView syncStatus;
    private final Runnable changed = this::showSyncStatus;

    private boolean setupMode;
    private User current;
    private Uri newAvatar, newCover;

    private final ActivityResultLauncher<Intent> pickAvatar = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (uris.isEmpty()) return;
                newAvatar = uris.get(0);
                avatar.setLocalImage(newAvatar);
            });

    private final ActivityResultLauncher<Intent> pickCover = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                List<Uri> uris = PictureSendActivity.results(r.getData());
                if (uris.isEmpty()) return;
                newCover = uris.get(0);
                Glide.with(this).load(newCover).centerCrop().into(cover);
            });

    public static Intent setup(Context c) {
        return new Intent(c, EditProfileActivity.class).putExtra(EXTRA_SETUP, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_profile_screen);
        setupMode = getIntent().getBooleanExtra(EXTRA_SETUP, false);

        etName = findViewById(R.id.field_name);
        etUsername = findViewById(R.id.field_username);
        etWebsite = findViewById(R.id.field_website);
        etBio = findViewById(R.id.field_bio);
        etPhone = findViewById(R.id.field_phone);
        etWork = findViewById(R.id.field_work);
        etSchool = findViewById(R.id.field_school);
        etLocation = findViewById(R.id.field_location);
        tvEmail = findViewById(R.id.field_email);
        tvGender = findViewById(R.id.field_gender);
        avatar = findViewById(R.id.avatar);
        cover = findViewById(R.id.cover);
        progress = findViewById(R.id.progress);
        syncStatus = findViewById(R.id.syncStatus);
        syncStatus.setOnClickListener(v -> restoreAttempt());

        if (setupMode) {
            // First-time users cannot skip this step.
            findViewById(R.id.btn_cancel).setVisibility(View.INVISIBLE);
            findViewById(R.id.usernameHint).setVisibility(View.VISIBLE);
        }

        NavHelper.click(this, R.id.btn_cancel, this::finish);
        NavHelper.click(this, R.id.btn_save, this::save);
        NavHelper.click(this, R.id.btn_edit_picture, () -> NavHelper.pendingFeature(this));
        NavHelper.click(this, R.id.avatar, () -> NavHelper.pendingFeature(this));
        NavHelper.click(this, R.id.btn_edit_cover, () -> NavHelper.pendingFeature(this));
        NavHelper.click(this, R.id.btn_cover_camera, () -> NavHelper.pendingFeature(this));
        NavHelper.click(this, R.id.field_gender, this::pickGender);

        load();
    }

    @Override
    public void onBackPressed() {
        if (setupMode) {
            Toast.makeText(this, "Choose a username to finish setting up your profile", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void load() {
        String uid = Db.uid();
        if (uid == null) {
            finish();
            return;
        }
        current = LocalDb.get().user(uid);
        if (current == null) {
            AuthFlow.afterSignIn(this);
            return;
        }
        etName.setText(current.name);
        etUsername.setText(current.username);
        etWebsite.setText(current.website);
        etBio.setText(current.bio);
        etPhone.setText(current.phone);
        etWork.setText(current.work);
        etSchool.setText(current.school);
        etLocation.setText(current.location);
        tvEmail.setText(current.email);
        tvGender.setText(current.gender == null ? "Male" : current.gender);
        avatar.setUser(current);
        Images.load(cover, current.coverUrl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalDb.observe(changed);
        showSyncStatus();
        Profiles.refresh(this);
    }

    @Override
    protected void onPause() {
        LocalDb.removeObserver(changed);
        super.onPause();
    }

    private void showSyncStatus() {
        if (Db.uid() == null) {
            AuthFlow.afterSignIn(this);
            return;
        }
        String status = LocalDb.get().status(Db.uid());
        syncStatus.setText(status.isEmpty() ? "" : status + "\nTap to retry or restore your attempted changes.");
        syncStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_save).setEnabled(!status.startsWith("Pending"));
        User saved = LocalDb.get().user(Db.uid());
        if (saved != null && status.isEmpty()) Session.saveRecentLogin(this, saved);
        if (setupMode && saved != null && saved.profileComplete && status.isEmpty()) {
            AuthFlow.afterSignIn(this);
        }
    }

    private void restoreAttempt() {
        try {
            JSONObject p = LocalDb.get().failedProfile(Db.uid());
            if (p != null) {
                etName.setText(p.optString("name"));
                etUsername.setText(p.optString("username"));
                etBio.setText(p.optString("bio"));
                etWebsite.setText(p.optString("website"));
                etPhone.setText(p.optString("phone"));
                etWork.setText(p.optString("work"));
                etSchool.setText(p.optString("school"));
                etLocation.setText(p.optString("location"));
                tvGender.setText(p.optString("gender"));
            } else {
                Profiles.refresh(this);
            }
        } catch (Exception e) {
            failed(e);
        }
    }

    private void pickGender() {
        String[] options = {"Female", "Male", "Custom"};
        new AlertDialog.Builder(this)
                .setTitle("Gender")
                .setItems(options, (d, which) -> tvGender.setText(options[which]))
                .show();
    }

    static boolean validUsername(String u) {
        return u != null && u.matches("[a-z0-9._]{3,30}");
    }

    private void save() {
        if (current == null) return;
        String name = etName.getText().toString().trim();
        String username = etUsername.getText().toString().trim().toLowerCase(Locale.ROOT);
        String bio = etBio.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Required");
            return;
        }
        if (!validUsername(username)) {
            etUsername.setError("3-30 characters: letters, numbers, . and _");
            return;
        }
        if (setupMode && TextUtils.isEmpty(bio)) {
            etBio.setError("Tell people a little about yourself");
            return;
        }
        progress.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_save).setEnabled(false);
        try {
            JSONObject profile = new JSONObject();
            profile.put("name", name);
            profile.put("username", username);
            profile.put("bio", bio);
            profile.put("website", etWebsite.getText().toString().trim());
            profile.put("phone", etPhone.getText().toString().trim());
            profile.put("work", etWork.getText().toString().trim());
            profile.put("school", etSchool.getText().toString().trim());
            profile.put("location", etLocation.getText().toString().trim());
            profile.put("gender", tvGender.getText().toString());
            Profiles.save(this, profile, () -> {
                progress.setVisibility(View.GONE);
                showSyncStatus();
                if (!setupMode) {
                    Toast.makeText(this, "Saved on this device; syncing when connected", Toast.LENGTH_SHORT).show();
                }
            }, this::failed);
        } catch (Exception e) {
            failed(e);
        }
    }

    private void failed(Exception e) {
        progress.setVisibility(View.GONE);
        findViewById(R.id.btn_save).setEnabled(true);
        Toast.makeText(this, "Could not save: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
    }
}
