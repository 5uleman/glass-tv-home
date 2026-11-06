package com.gathin.tvhome;

/**
 * The dock's edge double-press: a first outward press at the first or last dock item arms a
 * wallpaper change and a second one inside {@link #WINDOW_MS} confirms it. Pure state: the caller
 * supplies the clock, the key direction, the edge and whether the key is a repeat.
 *
 * <p>The edge is the logical dock edge, while the direction is the physical D-pad key. In a
 * right-to-left layout the reading direction runs leftwards, so Left is the key that advances
 * towards the last item and Right the key that goes back to the first one.
 */
final class EdgeWallpaperSwitch {

    static final long WINDOW_MS = 2_000;

    /** Where the focused dock item sits: first, last, both (a lone dock item), or neither. */
    enum Edge { NONE, FIRST, LAST, BOTH }

    enum Action { NONE, ARM_NEXT, ARM_PREVIOUS, CONFIRM_NEXT, CONFIRM_PREVIOUS }

    private Action armed = Action.NONE;
    private long armedAt;

    /**
     * @param keyDirection -1 for a physical Left press, +1 for Right
     * @param edge         the edge of the focused dock position
     * @param rtl          whether the dock is laid out right to left
     * @param repeat       a held key's repeating ACTION_DOWN
     * @param now          milliseconds, monotonic
     */
    Action onKey(int keyDirection, Edge edge, boolean rtl, boolean repeat, long now) {
        if (repeat) {
            cancel();
            return Action.NONE;
        }
        int forward = rtl ? -1 : 1;
        Action wanted;
        if (keyDirection == forward && (edge == Edge.LAST || edge == Edge.BOTH)) {
            wanted = Action.ARM_NEXT;
        } else if (keyDirection == -forward && (edge == Edge.FIRST || edge == Edge.BOTH)) {
            wanted = Action.ARM_PREVIOUS;
        } else {
            wanted = Action.NONE;
        }
        if (wanted == Action.NONE) {
            cancel();
            return Action.NONE;
        }
        if (armed == wanted && now - armedAt <= WINDOW_MS) {
            cancel();
            return wanted == Action.ARM_NEXT ? Action.CONFIRM_NEXT : Action.CONFIRM_PREVIOUS;
        }
        // Also the stale-armed case: the window passed, so this press starts a new arm.
        armed = wanted;
        armedAt = now;
        return wanted;
    }

    /** Any other key or a focus move ends the arm without changing the wallpaper. */
    Action onOtherInput() {
        cancel();
        return Action.NONE;
    }

    /** The hint's timer: true when an arm was still waiting and has now run out. */
    boolean expire(long now) {
        if (armed == Action.NONE || now - armedAt <= WINDOW_MS) return false;
        cancel();
        return true;
    }

    void cancel() {
        armed = Action.NONE;
        armedAt = 0;
    }

    boolean isArmed() {
        return armed != Action.NONE;
    }

    Action armedAction() {
        return armed;
    }
}
