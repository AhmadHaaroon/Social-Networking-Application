package com.ahmadhaaroon.i221295.services;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.ahmadhaaroon.i221295.ChatActivity;
import com.ahmadhaaroon.i221295.KinnectApp;
import com.ahmadhaaroon.i221295.models.CallInfo;
import com.ahmadhaaroon.i221295.util.Db;

import java.util.Map;

/**
 * Receives FCM data messages sent by the PHP backend: type=message a new chat message (title =
 * sender name, body = text) type=screenshot the other person screenshotted your chat type=call an
 * incoming Agora call type=friend_request Data-only messages are used so the app controls how the
 * notification looks in both the foreground and the background.
 */
public class KinnectMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        getSharedPreferences("kinnect_device", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        com.ahmadhaaroon.i221295.util.Realtime.registerDevice();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> d = message.getData();
        if (!com.ahmadhaaroon.i221295.util.WebSession.valid() || !Db.uid().equals(d.get("toUid")))
            return;
        com.ahmadhaaroon.i221295.util.SyncWorker.retryNow(this);
        String type = d.get("type");
        if (type == null) return;
        String event = d.get("notificationId");
        if (event != null) {
            android.content.SharedPreferences seen =
                    getSharedPreferences("received_push", MODE_PRIVATE);
            if (seen.contains(event)) return;
            seen.edit().putLong(event, System.currentTimeMillis()).apply();
        }
        String from = d.get("fromUid");
        String title = d.get("title") == null ? "Kinnect" : d.get("title");
        String body = d.get("body") == null ? "" : d.get("body");

        switch (type) {
            case "message":
            case "screenshot":
                // No banner for the conversation that is already open on screen.
                if (from == null) return;
                if (KinnectApp.isInForeground() && from.equals(ChatActivity.openChatUid)) return;
                PushNotifier.showMessage(this, from, title, body);
                break;
            case "call":
                if (KinnectApp.isInForeground()) return; // CallWatcher already shows the call screen
                CallInfo call = new CallInfo();
                call.callId = d.get("callId");
                call.callerUid = from;
                call.calleeUid = Db.uid();
                call.channel = d.get("channel");
                call.video = "true".equals(d.get("video"));
                call.status = CallInfo.RINGING;
                try {
                    call.timestamp = Long.parseLong(d.get("timestamp"));
                } catch (Exception e) {
                    call.timestamp = System.currentTimeMillis();
                }
                if (call.callId != null) PushNotifier.showCall(this, call, title);
                break;
            default:
                PushNotifier.showSocial(this, type, title, body);
        }
    }
}
