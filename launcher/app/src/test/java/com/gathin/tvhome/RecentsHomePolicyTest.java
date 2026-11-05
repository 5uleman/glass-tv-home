package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecentsHomePolicyTest {

    private static final long NOW = 10_000L;

    private static boolean decide(boolean enabled, boolean deckOpen, boolean foreground,
                                  boolean alreadyFired, int apps, long last) {
        return RecentsHomePolicy.shouldGoHome(
                enabled, deckOpen, foreground, alreadyFired, apps, NOW, last);
    }

    @Test
    public void aRealClearOnTheOpenDeckGoesHome() {
        assertTrue(decide(true, true, true, false, 2, 0));
    }

    @Test
    public void backingOutWithoutClearingDoesNothing() {
        assertFalse("deck already closed", decide(true, false, true, false, 2, 0));
    }

    @Test
    public void anotherAppInTheForegroundDoesNothing() {
        assertFalse(decide(true, true, false, false, 2, 0));
    }

    @Test
    public void nothingActuallyClosedStaysOnTheDeck() {
        assertFalse(decide(true, true, true, false, 0, 0));
    }

    @Test
    public void theFeatureSwitchDisablesEverything() {
        assertFalse(decide(false, true, true, false, 2, 0));
    }

    @Test
    public void itNeverFiresTwiceInTheSameOpening() {
        assertFalse(decide(true, true, true, true, 2, 0));
    }

    @Test
    public void aSecondClearInsideTheDebounceWindowIsIgnored() {
        assertFalse(decide(true, true, true, false, 2, NOW - 1));
        assertFalse(decide(true, true, true, false, 2, NOW - RecentsHomePolicy.DEBOUNCE_MS + 1));
    }

    @Test
    public void aLaterClearIsAllowedAgain() {
        assertTrue(decide(true, true, true, false, 2, NOW - RecentsHomePolicy.DEBOUNCE_MS));
        assertTrue(decide(true, true, true, false, 2, NOW - RecentsHomePolicy.DEBOUNCE_MS - 5000));
    }
}
