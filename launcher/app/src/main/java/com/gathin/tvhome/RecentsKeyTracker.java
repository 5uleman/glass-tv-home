package com.gathin.tvhome;

/**
 * Tells a double Home press from two separate presses. Kept away from Android classes so the timing
 * rule is unit tested on the JVM: two short presses no further apart than {@link #DOUBLE_PRESS_MS}
 * open the recent-apps deck, and the pair is then forgotten so a third press starts a new one.
 */
final class RecentsKeyTracker {

    static final long DOUBLE_PRESS_MS = 400;

    private long lastPressUpMs;

    /** True when this press completes a double press. Call once per finished (short) press. */
    boolean isDoublePress(long uptimeMs) {
        boolean doubled = lastPressUpMs != 0 && uptimeMs - lastPressUpMs <= DOUBLE_PRESS_MS;
        lastPressUpMs = doubled ? 0 : uptimeMs;
        return doubled;
    }

    /** Forget the pending press, for instance when the key was a long press or was cancelled. */
    void reset() {
        lastPressUpMs = 0;
    }
}
