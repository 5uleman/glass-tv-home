package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessibilityRepairTest {

    private static final String OURS =
            "com.suleman.glasstv/com.gathin.tvhome.HomeKeyService";
    private static final String TALKBACK =
            "com.google.android.marvin.talkback/.TalkBackService";
    private static final String SWITCH =
            "com.android.switchaccess/.SwitchAccessService";

    @Test
    public void repairsOnlyWithGrantListedAndUnbound() {
        assertTrue(AccessibilityRepair.shouldRepair(true, true, false));

        assertFalse("no grant", AccessibilityRepair.shouldRepair(false, true, false));
        assertFalse("not opted in", AccessibilityRepair.shouldRepair(true, false, false));
        assertFalse("already bound", AccessibilityRepair.shouldRepair(true, true, true));
        assertFalse("nothing true", AccessibilityRepair.shouldRepair(false, false, false));
        assertFalse(AccessibilityRepair.shouldRepair(false, true, true));
        assertFalse(AccessibilityRepair.shouldRepair(true, false, true));
    }

    @Test
    public void findsOurServiceInTheList() {
        assertTrue(AccessibilityRepair.isListed(OURS, OURS));
        assertTrue(AccessibilityRepair.isListed(TALKBACK + ":" + OURS, OURS));
        assertTrue(AccessibilityRepair.isListed(OURS + ":" + TALKBACK, OURS));
    }

    @Test
    public void doesNotMatchPartialOrMissingEntries() {
        assertFalse(AccessibilityRepair.isListed(null, OURS));
        assertFalse(AccessibilityRepair.isListed("", OURS));
        assertFalse(AccessibilityRepair.isListed(TALKBACK, OURS));
        assertFalse(AccessibilityRepair.isListed(OURS + "Extra", OURS));
        assertFalse(AccessibilityRepair.isListed(TALKBACK + ":" + SWITCH, OURS));
    }

    @Test
    public void removalKeepsEveryOtherServiceInOrder() {
        assertEquals(TALKBACK + ":" + SWITCH,
                AccessibilityRepair.withoutService(OURS + ":" + TALKBACK + ":" + SWITCH, OURS));
        assertEquals(TALKBACK,
                AccessibilityRepair.withoutService(TALKBACK + ":" + OURS, OURS));
        assertEquals("", AccessibilityRepair.withoutService(OURS, OURS));
        assertEquals("", AccessibilityRepair.withoutService("", OURS));
    }

    @Test
    public void rewriteKeepsOthersAndMovesOursToTheEnd() {
        assertEquals(TALKBACK + ":" + SWITCH + ":" + OURS,
                AccessibilityRepair.withServiceLast(
                        OURS + ":" + TALKBACK + ":" + SWITCH, OURS));
        assertEquals(TALKBACK + ":" + OURS,
                AccessibilityRepair.withServiceLast(TALKBACK + ":" + OURS, OURS));
        assertEquals(OURS, AccessibilityRepair.withServiceLast(OURS, OURS));
        assertEquals(OURS, AccessibilityRepair.withServiceLast("", OURS));
    }

    @Test
    public void rewriteNeverDuplicatesOurService() {
        String rebuilt = AccessibilityRepair.withServiceLast(
                OURS + ":" + TALKBACK + ":" + OURS, OURS);
        assertEquals(TALKBACK + ":" + OURS, rebuilt);
    }

    @Test
    public void blankEntriesAreDropped() {
        assertEquals(TALKBACK + ":" + OURS,
                AccessibilityRepair.withServiceLast(":" + TALKBACK + "::", OURS));
    }
}
