package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The Apps per Row cycle and the four-to-six range it writes into wallpaper_prefs. */
public class GridColumnsTest {

    @Test
    public void theSettingCyclesFourFiveSixThenBackToFour() {
        assertEquals(5, GridColumns.next(4));
        assertEquals(6, GridColumns.next(5));
        assertEquals(4, GridColumns.next(6));
    }

    @Test
    public void storedValuesOutsideTheRangeAreBroughtBackIn() {
        assertEquals(4, GridColumns.clamp(0));
        assertEquals(4, GridColumns.clamp(1));
        assertEquals(4, GridColumns.clamp(3));
        assertEquals(4, GridColumns.clamp(4));
        assertEquals(5, GridColumns.clamp(5));
        assertEquals(6, GridColumns.clamp(6));
        assertEquals(6, GridColumns.clamp(7));
        assertEquals(6, GridColumns.clamp(8));
    }

    @Test
    public void aStoredOutOfRangeValueCyclesFromTheNearestEnd() {
        assertEquals(4, GridColumns.next(3));
        assertEquals(4, GridColumns.next(7));
        assertEquals(6, GridColumns.next(5));
    }

    @Test
    public void theSupportedRangeIsFourToSixWithADefaultOfFour() {
        assertEquals(4, GridColumns.MIN);
        assertEquals(6, GridColumns.MAX);
        assertEquals(4, GridColumns.DEFAULT);
    }
}
