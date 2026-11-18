package com.ahmadhaaroon.i221295;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ahmadhaaroon.i221295.models.CallInfo;
import com.ahmadhaaroon.i221295.models.Message;
import com.ahmadhaaroon.i221295.ui.AvatarView;
import com.ahmadhaaroon.i221295.util.Chats;
import com.ahmadhaaroon.i221295.util.Db;
import com.ahmadhaaroon.i221295.util.TimeFmt;
import com.ahmadhaaroon.i221295.util.UserCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;

/**
 * Voice and video calls (screen 22) with the Agora RTC SDK.
 *
 * <p>Signalling goes through authenticated PHP services: the caller creates a call with status
 * "ringing"; the callee's app (CallWatcher in the foreground, or the FCM push in the background)
 * opens this screen in incoming mode. Accepting sets status "accepted" and both sides join the
 * Agora channel using participant-bound tokens issued by PHP. Every call is logged by the server
 * and shown as a message in the chat.
 */
public class CallActivity extends AppCompatActivity {

    private static final String EXTRA_PEER = "peer";
    private static final String EXTRA_VIDEO = "video";
    private static final String EXTRA_INCOMING = "incoming";
    private static final String EXTRA_CALL_ID = "callId";
    private static final String EXTRA_CHANNEL = "channel";
    private static final String EXTRA_CALLER = "caller";
    private static final String EXTRA_TIMESTAMP = "timestamp";

    private static volatile String showingCallId;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String me, peer, callId, channel, calleeUid, callerUid;
    private boolean video, incoming, connected, ended, muted, speaker;
    private long connectedAt;
    private RtcEngine engine;
    private Runnable signalListener;
    private final Runnable heartbeat =
            new Runnable() {
                public void run() {
                    if (!ended) {
                        com.ahmadhaaroon.i221295.util.Calls.action(
                                callId, "heartbeat", peer, video, null, null);
                        handler.postDelayed(this, 15000);
                    }
                }
            };
    private TextView status;

    private final Runnable ringTimeout = () -> {
        if (!connected) finishCall(CallInfo.MISSED);
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!connected) return;
            status.setText(TimeFmt.duration((System.currentTimeMillis() - connectedAt) / 1000));
            handler.postDelayed(this, 1000);
        }
    };

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (hasPermissions()) startAgora();
                else {
                    Toast.makeText(this, "Microphone permission is needed for calls", Toast.LENGTH_LONG).show();
                    finishCall(incoming ? CallInfo.DECLINED : CallInfo.ENDED);
                }
            });

    public static Intent outgoing(Context c, String peerUid, boolean video) {
        return new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER, peerUid)
                .putExtra(EXTRA_VIDEO, video);
    }

    public static Intent incoming(Context c, CallInfo call) {
        return new Intent(c, CallActivity.class)
                .putExtra(EXTRA_PEER, call.callerUid)
                .putExtra(EXTRA_CALLER, call.callerUid)
                .putExtra(EXTRA_VIDEO, call.video)
                .putExtra(EXTRA_INCOMING, true)
                .putExtra(EXTRA_CALL_ID, call.callId)
                .putExtra(EXTRA_CHANNEL, call.channel)
                .putExtra(EXTRA_TIMESTAMP, call.timestamp);
    }

    public static boolean isShowing(String callId) {
        return callId != null && callId.equals(showingCallId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_call);

        me = Db.uid();
        peer = getIntent().getStringExtra(EXTRA_PEER);
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        incoming = getIntent().getBooleanExtra(EXTRA_INCOMING, false);
        if (me == null || peer == null) {
            finish();
            return;
        }
        status = findViewById(R.id.tvTimer);
        ((AvatarView) findViewById(R.id.callAvatar)).bind(peer);
        TextView name = findViewById(R.id.callName);
        UserCache.get(peer, u -> name.setText(u.displayName()));

        ImageView notes = findViewById(R.id.btnNotes);
        if (video) notes.setImageResource(R.drawable.ic_camera);
        else notes.setVisibility(View.GONE);
        notes.setOnClickListener(v -> {
            if (engine != null) engine.switchCamera();
        });
        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());
        findViewById(R.id.btnEnd).setOnClickListener(v -> finishCall(connected ? CallInfo.ENDED : (incoming ? CallInfo.DECLINED : CallInfo.MISSED)));
        findViewById(R.id.btnAccept).setOnClickListener(v -> accept());
        findViewById(R.id.btnDecline).setOnClickListener(v -> finishCall(CallInfo.DECLINED));

        if (incoming) {
            callId = getIntent().getStringExtra(EXTRA_CALL_ID);
            channel = getIntent().getStringExtra(EXTRA_CHANNEL);
            callerUid = peer;
            calleeUid = me;
            status.setText(video ? R.string.incoming_video_call : R.string.incoming_voice_call);
            findViewById(R.id.incomingControls).setVisibility(View.VISIBLE);
            findViewById(R.id.callControls).setVisibility(View.GONE);
        } else {
            callerUid = me;
            calleeUid = peer;
            callId = java.util.UUID.randomUUID().toString();
            channel = callId;
            status.setText(R.string.calling);
            ring();
        }
        showingCallId = callId;
        watchSignal();
        handler.postDelayed(ringTimeout, com.ahmadhaaroon.i221295.services.CallWatcher.RING_TIMEOUT_MS);
        if (!incoming) requestPermissionsAndStart();
    }

    /** Caller: write the ringing record that the callee's app reacts to. */
    private void ring() {
        CallInfo info = new CallInfo();
        info.callId = callId;
        info.callerUid = me;
        info.calleeUid = peer;
        info.channel = channel;
        info.video = video;
        info.status = CallInfo.RINGING;
        info.timestamp = System.currentTimeMillis();
        com.ahmadhaaroon.i221295.util.Calls.action(
                callId,
                "ring",
                peer,
                video,
                null,
                e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finishCall(CallInfo.ENDED, false);
                });
    }

    private void watchSignal() {
        signalListener =
                () -> {
                    CallInfo info = com.ahmadhaaroon.i221295.util.Calls.get(callId);
                    if (info == null) return;
                    if (CallInfo.DECLINED.equals(info.status)
                            || CallInfo.ENDED.equals(info.status)
                            || CallInfo.MISSED.equals(info.status)) remoteEnded();
                    else if (CallInfo.ACCEPTED.equals(info.status) && !incoming && !connected)
                        status.setText(R.string.connecting);
                };
        com.ahmadhaaroon.i221295.util.LocalDb.observe(signalListener);
        signalListener.run();
        handler.postDelayed(heartbeat, 15000);
    }

    private void accept() {
        com.ahmadhaaroon.i221295.util.Calls.action(
                callId,
                "accept",
                peer,
                video,
                () -> {
                    findViewById(R.id.incomingControls).setVisibility(View.GONE);
                    findViewById(R.id.callControls).setVisibility(View.VISIBLE);
                    status.setText(R.string.connecting);
                    requestPermissionsAndStart();
                },
                e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finishCall(CallInfo.MISSED, false);
                });
    }

    private String[] neededPermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        if (video) p.add(Manifest.permission.CAMERA);
        return p.toArray(new String[0]);
    }

    private boolean hasPermissions() {
        for (String p : neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPermissionsAndStart() {
        if (hasPermissions()) startAgora();
        else permissions.launch(neededPermissions());
    }

    private String agoraAppId;
    private int agoraUid;

    private void startAgora() {
        fetchToken(false);
    }

    private void fetchToken(boolean renew) {
        com.ahmadhaaroon.i221295.util.Realtime.request(
                "calls/token.php",
                com.ahmadhaaroon.i221295.util.Content.data(callId),
                result -> {
                    if (ended) return;
                    agoraAppId = result.optString("appId");
                    agoraUid = result.optInt("uid");
                    if (renew && engine != null) engine.renewToken(result.optString("token"));
                    else joinChannel(result.optString("token"));
                },
                e -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finishCall(CallInfo.ENDED);
                });
    }

    private final IRtcEngineEventHandler events =
            new IRtcEngineEventHandler() {
                @Override
                public void onTokenPrivilegeWillExpire(String token) {
                    runOnUiThread(() -> fetchToken(true));
                }

                @Override
                public void onRequestToken() {
                    runOnUiThread(() -> fetchToken(true));
                }

                @Override
                public void onUserJoined(int uid, int elapsed) {
                    runOnUiThread(() -> onPeerJoined(uid));
                }

                @Override
                public void onUserOffline(int uid, int reason) {
                    runOnUiThread(() -> remoteEnded());
                }

                @Override
                public void onError(int err) {
                    runOnUiThread(() ->
                            Toast.makeText(
                                            CallActivity.this,
                                            "Call error " + err,
                                            Toast.LENGTH_SHORT)
                                    .show());
                }
            };

    private void joinChannel(String token) {
        if (ended) return;
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getApplicationContext();
            config.mAppId = agoraAppId;
            config.mEventHandler = events;
            engine = RtcEngine.create(config);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start call: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finishCall(CallInfo.ENDED);
            return;
        }
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.publishMicrophoneTrack = true;
        options.autoSubscribeAudio = true;
        if (video) {
            engine.enableVideo();
            FrameLayout local = findViewById(R.id.localVideo);
            SurfaceView surface = new SurfaceView(this);
            surface.setZOrderMediaOverlay(true);
            local.addView(surface);
            engine.setupLocalVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            engine.startPreview();
            findViewById(R.id.localVideoCard).setVisibility(View.VISIBLE);
            options.publishCameraTrack = true;
            options.autoSubscribeVideo = true;
            speaker = true;
        } else {
            engine.disableVideo();
        }
        engine.setDefaultAudioRoutetoSpeakerphone(speaker);
        engine.joinChannel(token, channel, agoraUid, options);
        updateSpeakerIcon();
    }

    private void onPeerJoined(int uid) {
        if (connected) return;
        connected = true;
        connectedAt = System.currentTimeMillis();
        handler.removeCallbacks(ringTimeout);
        handler.post(tick);
        if (video) {
            FrameLayout remote = findViewById(R.id.remoteVideo);
            remote.setVisibility(View.VISIBLE);
            SurfaceView surface = new SurfaceView(this);
            remote.addView(surface);
            engine.setupRemoteVideo(new VideoCanvas(surface, VideoCanvas.RENDER_MODE_HIDDEN, uid));
            findViewById(R.id.halo1).setVisibility(View.GONE);
            findViewById(R.id.halo2).setVisibility(View.GONE);
            findViewById(R.id.callAvatarRing).setVisibility(View.GONE);
        }
    }

    private void toggleMute() {
        muted = !muted;
        if (engine != null) engine.muteLocalAudioStream(muted);
        View b = findViewById(R.id.btnMute);
        b.setAlpha(muted ? 0.5f : 1f);
    }

    private void toggleSpeaker() {
        speaker = !speaker;
        if (engine != null) engine.setEnableSpeakerphone(speaker);
        updateSpeakerIcon();
    }

    private void updateSpeakerIcon() {
        findViewById(R.id.btnSpeaker).setAlpha(speaker ? 1f : 0.6f);
    }

    private void remoteEnded() {
        if (ended) return;
        finishCall(connected ? CallInfo.ENDED : (incoming ? CallInfo.MISSED : CallInfo.ENDED), false);
    }

    private void finishCall(String result) {
        finishCall(result, true);
    }

    /**
     * Leaves the Agora channel, clears the ringing record, updates both call logs and (from the
     * caller's side) adds a call message to the chat.
     */
    private void finishCall(String result, boolean notifyPeer) {
        if (ended) return;
        ended = true;
        handler.removeCallbacksAndMessages(null);
        long duration = connected ? (System.currentTimeMillis() - connectedAt) / 1000 : 0;

        if (signalListener != null)
            com.ahmadhaaroon.i221295.util.LocalDb.removeObserver(signalListener);
        if (notifyPeer)
            com.ahmadhaaroon.i221295.util.Calls.action(callId, result, peer, video, null, null);

        if (engine != null) {
            engine.leaveChannel();
            engine.stopPreview();
            RtcEngine.destroy();
            engine = null;
        }
        showingCallId = null;
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back does not hang up; use the red button.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!ended && callId != null) finishCall(connected ? CallInfo.ENDED : CallInfo.MISSED);
    }
}
