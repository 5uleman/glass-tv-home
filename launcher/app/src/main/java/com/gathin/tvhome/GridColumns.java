package com.gathin.tvhome;

/**
 * How many apps each grid row shows, and how many slots the dock has. The wallpaper settings row
 * cycles 4 and 5; the value is stored in wallpaper_prefs and read at Home's start. Pure, so the
 * range, the cycle and the one-time migration from the old six-column choice are unit tested.
 */
final class GridColumns {

    static final int MIN = 4;
    static final int MAX = 5;
    static final int DEFAULT = 4;

    private GridColumns() {
    }

    /** Any stored or configured value brought into the supported four-to-five range. */
    static int clamp(int columns) {
        return Math.max(MIN, Math.min(MAX, columns));
    }

    /**
     * The value to store at start: an old six-column choice becomes five the first time it is read.
     * Running it again on the stored five changes nothing, so the write happens once.
     */
    static int migrate(int stored) {
        return clamp(stored);
    }

    /** The next choice in the cycle: 4, 5, then back to 4. Out of range starts at the nearest end. */
    static int next(int current) {
        if (current < MIN) return MIN;
        if (current >= MAX) return MIN;
        return current + 1;
    }
}
