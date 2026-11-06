package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.function.Predicate;

public class EdgeWallpaperSwitchTest {

    private static final int LEFT = -1, RIGHT = 1;
    private static final long T0 = 10_000;

    @Test
    public void firstRightAtLastArmsNext() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0));
        assertTrue(sw.isArmed());
    }

    @Test
    public void secondRightInsideWindowConfirmsNext() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        assertEquals(EdgeWallpaperSwitch.Action.CONFIRM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 1_999));
        assertFalse(sw.isArmed());
    }

    @Test
    public void secondRightAfterWindowArmsAgainInsteadOfConfirming() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 2_001));
        assertTrue(sw.isArmed());
        assertEquals(EdgeWallpaperSwitch.Action.CONFIRM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 2_100));
    }

    @Test
    public void repeatedKeyCancelsAndDoesNothing() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        assertEquals(EdgeWallpaperSwitch.Action.NONE,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, true, T0 + 100));
        assertFalse(sw.isArmed());
    }

    @Test
    public void otherKeyCancelsArm() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        assertEquals(EdgeWallpaperSwitch.Action.NONE, sw.onOtherInput());
        assertFalse(sw.isArmed());
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 100));
    }

    @Test
    public void focusMoveCancelsArm() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.FIRST, false, false, T0);
        sw.onOtherInput();
        assertFalse(sw.isArmed());
    }

    @Test
    public void leftAtFirstArmsAndConfirmsPrevious() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        assertEquals(EdgeWallpaperSwitch.Action.ARM_PREVIOUS,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.FIRST, false, false, T0));
        assertEquals(EdgeWallpaperSwitch.Action.CONFIRM_PREVIOUS,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.FIRST, false, false, T0 + 800));
    }

    @Test
    public void inwardKeyOnAnEdgeDoesNotArm() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        assertEquals(EdgeWallpaperSwitch.Action.NONE,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0));
        assertEquals(EdgeWallpaperSwitch.Action.NONE,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.FIRST, false, false, T0));
        assertFalse(sw.isArmed());
    }

    @Test
    public void aKeyThatOnlyMovesFocusOntoTheEndDoesNotArm() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        // Focus is on the item before the last: this Right moves focus, so nothing arms.
        assertEquals(EdgeWallpaperSwitch.Action.NONE,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.NONE, false, false, T0));
        assertFalse(sw.isArmed());
    }

    @Test
    public void singleItemIsBothEnds() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.BOTH, false, false, T0));
        sw.cancel();
        assertEquals(EdgeWallpaperSwitch.Action.ARM_PREVIOUS,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.BOTH, false, false, T0 + 100));
    }

    @Test
    public void rtlMapsPhysicalKeysToTheVisualEdges() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        // Right-to-left: the last logical item sits on the physical left, so Left advances.
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.LAST, true, false, T0));
        sw.cancel();
        assertEquals(EdgeWallpaperSwitch.Action.ARM_PREVIOUS,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.FIRST, true, false, T0 + 100));
    }

    @Test
    public void confirmingDoesNotFireTwice() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 100);
        // The next press must only arm again, never confirm from the cleared state.
        assertEquals(EdgeWallpaperSwitch.Action.ARM_NEXT,
                sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0 + 200));
    }

    @Test
    public void expireClearsOnlyAfterTheWindow() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.LAST, false, false, T0);
        assertFalse(sw.expire(T0 + EdgeWallpaperSwitch.WINDOW_MS));
        assertTrue(sw.expire(T0 + EdgeWallpaperSwitch.WINDOW_MS + 1));
        assertFalse(sw.isArmed());
    }

    @Test
    public void switchingDirectionReArmsTheOtherWay() {
        EdgeWallpaperSwitch sw = new EdgeWallpaperSwitch();
        sw.onKey(RIGHT, EdgeWallpaperSwitch.Edge.BOTH, false, false, T0);
        assertEquals(EdgeWallpaperSwitch.Action.ARM_PREVIOUS,
                sw.onKey(LEFT, EdgeWallpaperSwitch.Edge.BOTH, false, false, T0 + 100));
    }

    @Test
    public void historyWalksBackAndForward() {
        WallpaperHistory<File> history = new WallpaperHistory<>();
        File a = new File("a.jpg"), b = new File("b.jpg"), c = new File("c.jpg");
        history.record(a);
        history.record(b);
        history.record(c);
        assertEquals(b, history.previous(null));
        assertEquals(a, history.previous(null));
        assertEquals(b, history.next(null));
        assertEquals(c, history.next(null));
        assertNull(history.next(null));
    }

    @Test
    public void historyStartsEmpty() {
        WallpaperHistory<File> history = new WallpaperHistory<>();
        assertTrue(history.isEmpty());
        assertNull(history.previous(null));
        assertNull(history.next(null));
        history.record(new File("only.jpg"));
        assertNull(history.previous(null));
        assertNull(history.next(null));
    }

    @Test
    public void historyKeepsOnlyTheLastTen() {
        WallpaperHistory<Integer> history = new WallpaperHistory<>();
        for (int i = 0; i < 12; i++) history.record(i);
        assertEquals(10, history.size());
        assertEquals(Integer.valueOf(11), history.current());
        assertEquals(Integer.valueOf(10), history.previous(null));
        for (int i = 9; i >= 2; i--) assertEquals(Integer.valueOf(i), history.previous(null));
        assertNull(history.previous(null));
    }

    @Test
    public void recordingAfterGoingBackDropsForwardEntries() {
        WallpaperHistory<File> history = new WallpaperHistory<>();
        File a = new File("a.jpg"), b = new File("b.jpg"), c = new File("c.jpg"), d = new File("d.jpg");
        history.record(a);
        history.record(b);
        history.record(c);
        assertEquals(b, history.previous(null));
        history.record(d);
        assertEquals(3, history.size());
        assertEquals(d, history.current());
        assertEquals(b, history.previous(null));
        assertEquals(a, history.previous(null));
    }

    @Test
    public void filteredHistorySkipsPicturesOutsideTheCurrentPool() {
        WallpaperHistory<File> history = new WallpaperHistory<>();
        File day = new File("day.jpg"), night = new File("night.jpg"), both = new File("both.jpg");
        history.record(day);
        history.record(night);
        history.record(both);
        Predicate<File> nightAllowed = f -> !f.equals(day);
        assertEquals(night, history.previous(nightAllowed));
        assertNull(history.previous(nightAllowed));   // day was skipped, cursor stays on night
        assertEquals(both, history.next(nightAllowed));
    }

    @Test
    public void clearStartsOver() {
        WallpaperHistory<File> history = new WallpaperHistory<>();
        history.record(new File("a.jpg"));
        history.clear();
        assertTrue(history.isEmpty());
        assertNull(history.current());
    }
}
