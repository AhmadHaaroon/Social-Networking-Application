package com.ahmadhaaroon.i221295.util;

/** Friend actions update SQLite immediately and sync through PHP in queue order. */
public final class Friends {

    public static final int NONE = 0;
    public static final int REQUESTED = 1;
    public static final int INCOMING = 2;
    public static final int FRIENDS = 3;
    public static final int SELF = 4;

    public interface Handle {
        void remove();
    }

    private Friends() {
    }

    public static void sendRequest(String other) {
        People.action(other, "send");
    }

    public static void cancelRequest(String other) {
        People.action(other, "cancel");
    }

    public static void accept(String other) {
        People.action(other, "accept");
    }

    public static void decline(String other) {
        People.action(other, "decline");
    }

    public static void unfriend(String other) {
        People.action(other, "unfriend");
    }

    /** Runs the right action for the Add friend button in its current state. */
    public static void primaryAction(String other, int state) {
        if (state == NONE) sendRequest(other);
        else if (state == REQUESTED) cancelRequest(other);
        else if (state == INCOMING) accept(other);
    }

    public static String label(int state) {
        switch (state) {
            case REQUESTED: return "Requested";
            case INCOMING: return "Confirm";
            case FRIENDS: return "Friends";
            default: return "Add friend";
        }
    }

    /** Live friendship state between the signed-in user and {@code other}. */
    public static Handle observe(String other, Callback<Integer> cb) {
        Runnable observer = () -> cb.onResult(People.state(other));
        LocalDb.observe(observer);
        observer.run();
        return () -> LocalDb.removeObserver(observer);
    }
}
