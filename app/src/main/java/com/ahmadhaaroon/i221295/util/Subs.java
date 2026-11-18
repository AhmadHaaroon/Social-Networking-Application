package com.ahmadhaaroon.i221295.util;
import java.util.ArrayList;
import java.util.List;

/** Collects screen observers so they can be removed together in onDestroy. */
public class Subs {
    private final List<Runnable> removers = new ArrayList<>();

    public void add(Runnable remover) {
        removers.add(remover);
    }

    public void clear() {
        for (Runnable r : removers) r.run();
        removers.clear();
    }
}
