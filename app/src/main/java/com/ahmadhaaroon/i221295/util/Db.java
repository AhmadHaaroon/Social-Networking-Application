package com.ahmadhaaroon.i221295.util;

/** Shared account and conversation identifiers. */
public final class Db {
    private Db() {}

    public static String uid() {
        return WebSession.uid();
    }

    public static String chatId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}
