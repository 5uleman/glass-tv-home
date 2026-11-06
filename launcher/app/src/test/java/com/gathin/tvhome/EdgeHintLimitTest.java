package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EdgeHintLimitTest {

    @Test
    public void showsTwiceThenStopsForEachSide() {
        EdgeHintLimit limit = new EdgeHintLimit();
        for (int i = 0; i < EdgeHintLimit.MAX_SHOWS; i++) {
            assertTrue("next show " + (i + 1), limit.allowNext());
            assertTrue("previous show " + (i + 1), limit.allowPrevious());
        }
        assertFalse(limit.allowNext());
        assertFalse(limit.allowPrevious());
        assertEquals(2, limit.shownNext());
        assertEquals(2, limit.shownPrevious());
    }

    @Test
    public void aRefusedShowIsNotCounted() {
        EdgeHintLimit limit = new EdgeHintLimit();
        for (int i = 0; i < 10; i++) limit.allowNext();
        assertEquals(EdgeHintLimit.MAX_SHOWS, limit.shownNext());
        assertFalse(limit.allowNext());
        assertEquals(EdgeHintLimit.MAX_SHOWS, limit.shownNext());
    }

    @Test
    public void theTwoDirectionsAreSeparate() {
        EdgeHintLimit limit = new EdgeHintLimit();
        for (int i = 0; i < EdgeHintLimit.MAX_SHOWS; i++) assertTrue(limit.allowNext());
        assertFalse(limit.allowNext());
        assertEquals(0, limit.shownPrevious());
        assertTrue(limit.allowPrevious());
        assertEquals(1, limit.shownPrevious());
    }

    @Test
    public void resetGivesBothSidesTwoMoreShows() {
        EdgeHintLimit limit = new EdgeHintLimit();
        for (int i = 0; i < EdgeHintLimit.MAX_SHOWS; i++) {
            limit.allowNext();
            limit.allowPrevious();
        }
        limit.reset();
        assertEquals(0, limit.shownNext());
        assertEquals(0, limit.shownPrevious());
        assertTrue(limit.allowNext());
        assertTrue(limit.allowPrevious());
    }

    @Test
    public void aRestartStartsWithAFullAllowance() {
        EdgeHintLimit before = new EdgeHintLimit();
        for (int i = 0; i < EdgeHintLimit.MAX_SHOWS; i++) {
            before.allowNext();
            before.allowPrevious();
        }
        EdgeHintLimit restarted = new EdgeHintLimit();
        assertEquals(0, restarted.shownNext());
        assertEquals(0, restarted.shownPrevious());
        assertTrue(restarted.allowNext());
        assertTrue(restarted.allowPrevious());
    }

    @Test
    public void theLimitIsTwo() {
        assertEquals(2, EdgeHintLimit.MAX_SHOWS);
    }
}
