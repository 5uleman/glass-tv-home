package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HomeKeyDecisionTest {

    @Test
    public void aPressThatClosesAnOverlayNeverOpensTheDeck() {
        assertEquals(HomeKeyDecision.DISMISS_OVERLAY,
                HomeKeyDecision.decide(true, false));
        assertEquals("even when the timer says double, the overlay wins",
                HomeKeyDecision.DISMISS_OVERLAY, HomeKeyDecision.decide(true, true));
    }

    @Test
    public void doublePressOnPlainHomeOpensTheDeck() {
        assertEquals(HomeKeyDecision.OPEN_RECENTS, HomeKeyDecision.decide(false, true));
    }

    @Test
    public void aSinglePressOnPlainHomeJustGoesHome() {
        assertEquals(HomeKeyDecision.GO_HOME, HomeKeyDecision.decide(false, false));
    }
}
