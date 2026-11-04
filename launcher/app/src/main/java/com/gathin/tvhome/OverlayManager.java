package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Every launcher overlay that can sit over Home registers a dismiss action while it is open, and
 * unregisters when it closes itself. A Home press can then close all of them in one place, the way
 * Back closes the top one. Main thread only: the actions touch views and dialogs.
 *
 * <p>Kept free of Android types so the register/dismiss bookkeeping is unit tested on the JVM.
 */
final class OverlayManager {

    private static final class Entry {
        final Runnable dismiss;

        Entry(Runnable dismiss) {
            this.dismiss = dismiss;
        }
    }

    private static final List<Entry> OPEN = new ArrayList<>();

    private OverlayManager() {
    }

    /** Registers an open overlay; the returned token is passed to {@link #unregister}. */
    static synchronized Object register(Runnable dismiss) {
        Entry entry = new Entry(dismiss);
        OPEN.add(entry);
        return entry;
    }

    /** Forgets an overlay that closed by itself (Back, Cancel, a row click). */
    static synchronized void unregister(Object token) {
        OPEN.remove(token);
    }

    static synchronized boolean anyOpen() {
        return !OPEN.isEmpty();
    }

    /**
     * Closes everything that is open, newest first. The list is cleared before the actions run, so
     * an action that unregisters itself (or opens another overlay) cannot loop or be dismissed twice.
     *
     * @return true when there was at least one open overlay
     */
    static synchronized boolean dismissAll() {
        if (OPEN.isEmpty()) return false;
        List<Entry> entries = new ArrayList<>(OPEN);
        OPEN.clear();
        for (int i = entries.size() - 1; i >= 0; i--) {
            try {
                entries.get(i).dismiss.run();
            } catch (RuntimeException ignored) {
                // One broken overlay must not keep the others on screen.
            }
        }
        return true;
    }
}
