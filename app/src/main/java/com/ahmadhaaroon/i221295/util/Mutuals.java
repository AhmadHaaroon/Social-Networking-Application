package com.ahmadhaaroon.i221295.util;

import java.util.ArrayList;
import java.util.List;

/** Mutual friends between the signed-in user and someone else. */
public final class Mutuals {

    private Mutuals() {
    }

    public static void count(String other, Callback<Integer> cb) {
        list(other, l -> cb.onResult(l.size()));
    }

    public static void list(String other, Callback<List<String>> cb) {
        String me = Db.uid();
        if (me == null || other == null || other.equals(me)) {
            cb.onResult(new ArrayList<>());
            return;
        }
        List<String> mutual = new ArrayList<>(People.friends(me));
        mutual.retainAll(People.friends(other));
        cb.onResult(mutual);
    }
}
