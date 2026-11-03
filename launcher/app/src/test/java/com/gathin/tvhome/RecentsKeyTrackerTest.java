package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecentsKeyTrackerTest {

    @Test
    public void firstPressIsNotADoublePress() {
        RecentsKeyTracker tracker = new RecentsKeyTracker();
        assertFalse(tracker.isDoublePress(1_000));
    }

    @Test
    public void secondPressWithinWindowOpensOnce() {
        RecentsKeyTracker tracker = new RecentsKeyTracker();
        assertFalse(tracker.isDoublePress(1_000));
        assertTrue(tracker.isDoublePress(1_350));
        assertFalse("the pair is forgotten", tracker.isDoublePress(1_500));
    }

    @Test
    public void secondPressExactlyAtTheLimitCounts() {
        RecentsKeyTracker tracker = new RecentsKeyTracker();
        tracker.isDoublePress(1_000);
        assertTrue(tracker.isDoublePress(1_000 + RecentsKeyTracker.DOUBLE_PRESS_MS));
    }

    @Test
    public void slowSecondPressStartsANewPair() {
        RecentsKeyTracker tracker = new RecentsKeyTracker();
        tracker.isDoublePress(1_000);
        assertFalse(tracker.isDoublePress(1_000 + RecentsKeyTracker.DOUBLE_PRESS_MS + 1));
        assertTrue("the slow press became the first of a new pair", tracker.isDoublePress(1_700));
    }

    @Test
    public void resetForgetsThePendingPress() {
        RecentsKeyTracker tracker = new RecentsKeyTracker();
        tracker.isDoublePress(1_000);
        tracker.reset();
        assertFalse(tracker.isDoublePress(1_200));
    }
}
