package com.ahmadhaaroon.i221295.models;


/** Signalling record written to calls/{calleeUid} and copied to callLogs/{uid}/{callId}. */
public class CallInfo {
    public static final String RINGING = "ringing";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";
    public static final String ENDED = "ended";
    public static final String MISSED = "missed";

    public String callId;
    public String callerUid;
    public String calleeUid;
    public String channel;
    public boolean video;
    public String status;
    public long timestamp;
    public long duration;

    public CallInfo() {
    }
}
