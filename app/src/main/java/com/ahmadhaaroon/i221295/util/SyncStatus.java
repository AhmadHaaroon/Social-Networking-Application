package com.ahmadhaaroon.i221295.util;

public final class SyncStatus {

    public static volatile boolean running;
    public static volatile long completed;
    public static volatile String error = "";

    private SyncStatus() {
    }

    public static void begin() {
        running = true;
        error = "";
        LocalDb.changed();
    }

    public static void end() {
        completed++;
        running = false;
        LocalDb.changed();
    }
}
