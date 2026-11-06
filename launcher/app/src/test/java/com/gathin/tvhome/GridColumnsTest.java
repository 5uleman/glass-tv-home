package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The Apps per Row cycle, the four-to-five range, and the one-time move off six columns. */
public class GridColumnsTest {

    @Test
    public void theSettingCyclesFourFiveThenBackToFour() {
        assertEquals(5, GridColumns.next(4));
        assertEquals(4, GridColumns.next(5));
    }

    @Test
    public void storedValuesOutsideTheRangeAreBroughtBackIn() {
        assertEquals(4, GridColumns.clamp(0));
        assertEquals(4, GridColumns.clamp(3));
        assertEquals(4, GridColumns.clamp(4));
        assertEquals(5, GridColumns.clamp(5));
        assertEquals(5, GridColumns.clamp(6));
        assertEquals(5, GridColumns.clamp(7));
    }

    @Test
    public void aStoredSixIsMovedToFiveOnce() {
        assertEquals(5, GridColumns.migrate(6));
        // Reading the migrated value again writes nothing new: the migration is idempotent.
        assertEquals(5, GridColumns.migrate(GridColumns.migrate(6)));
        assertEquals(4, GridColumns.migrate(4));
        assertEquals(5, GridColumns.migrate(5));
    }

    @Test
    public void sixIsNoLongerPartOfTheCycle() {
        assertEquals(4, GridColumns.next(6));
        assertEquals(4, GridColumns.next(7));
        assertEquals(4, GridColumns.next(3));
    }

    @Test
    public void theSupportedRangeIsFourToFiveWithADefaultOfFour() {
        assertEquals(4, GridColumns.MIN);
        assertEquals(5, GridColumns.MAX);
        assertEquals(4, GridColumns.DEFAULT);
    }
}
