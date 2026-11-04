package com.gathin.tvhome;

/**
 * What a finished short Home press does. Kept apart from Android so the ordering rule is unit
 * tested: an overlay wins over the double-press timer, because the press that closes an overlay
 * must not also become the first press of a double Home.
 */
final class HomeKeyDecision {

    static final int DISMISS_OVERLAY = 0;
    static final int OPEN_RECENTS = 1;
    static final int GO_HOME = 2;

    private HomeKeyDecision() {
    }

    static int decide(boolean overlayOpen, boolean doublePress) {
        if (overlayOpen) return DISMISS_OVERLAY;
        return doublePress ? OPEN_RECENTS : GO_HOME;
    }
}
