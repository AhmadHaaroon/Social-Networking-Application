package com.ahmadhaaroon.i221295.models;


/** One row of userChats/{uid}/{otherUid}. */
public class ChatSummary {
    public String chatId;
    public String otherUid;
    public String lastMessage;
    public String lastSenderId;
    public long timestamp;
    public int unread;

    public ChatSummary() {
    }
}
