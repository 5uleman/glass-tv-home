package com.gathin.tvhome;

/**
 * The edge-switch toast's allowance: each direction may be explained twice, counted separately,
 * and then the double-press keeps working in silence. Pure and in memory only; the caller resets it
 * on screen-off/on and a new process starts at zero.
 */
final class EdgeHintLimit {

    static final int MAX_SHOWS = 2;

    private int nextShown;
    private int previousShown;

    /** True while the next-wallpaper toast may still be shown; counts the show. */
    boolean allowNext() {
        if (nextShown >= MAX_SHOWS) return false;
        nextShown++;
        return true;
    }

    /** True while the previous-wallpaper toast may still be shown; counts the show. */
    boolean allowPrevious() {
        if (previousShown >= MAX_SHOWS) return false;
        previousShown++;
        return true;
    }

    void reset() {
        nextShown = 0;
        previousShown = 0;
    }

    int shownNext() {
        return nextShown;
    }

    int shownPrevious() {
        return previousShown;
    }
}
