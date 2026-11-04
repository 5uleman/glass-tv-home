package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OverlayManagerTest {

    @After
    public void cleanUp() {
        OverlayManager.dismissAll();
    }

    @Test
    public void emptyManagerReportsNothingToDismiss() {
        assertFalse(OverlayManager.anyOpen());
        assertFalse(OverlayManager.dismissAll());
    }

    @Test
    public void dismissAllClosesNewestFirstAndOnlyOnce() {
        List<String> closed = new ArrayList<>();
        OverlayManager.register(() -> closed.add("menu"));
        OverlayManager.register(() -> closed.add("dialog"));
        assertTrue(OverlayManager.anyOpen());

        assertTrue(OverlayManager.dismissAll());
        assertEquals(Arrays.asList("dialog", "menu"), closed);
        assertFalse(OverlayManager.anyOpen());
        assertFalse("nothing is dismissed twice", OverlayManager.dismissAll());
    }

    @Test
    public void unregisterKeepsAClosedOverlayOutOfDismissAll() {
        List<String> closed = new ArrayList<>();
        Object token = OverlayManager.register(() -> closed.add("deck"));
        OverlayManager.unregister(token);

        assertFalse(OverlayManager.dismissAll());
        assertTrue(closed.isEmpty());
    }

    @Test
    public void anOverlayThatUnregistersItselfDoesNotBreakTheSweep() {
        List<String> closed = new ArrayList<>();
        Object[] token = new Object[1];
        token[0] = OverlayManager.register(() -> {
            OverlayManager.unregister(token[0]);
            closed.add("self");
        });
        OverlayManager.register(() -> closed.add("other"));

        assertTrue(OverlayManager.dismissAll());
        assertEquals(Arrays.asList("other", "self"), closed);
    }

    @Test
    public void aThrowingOverlayDoesNotKeepTheOthersOpen() {
        List<String> closed = new ArrayList<>();
        OverlayManager.register(() -> {
            throw new IllegalStateException("broken overlay");
        });
        OverlayManager.register(() -> closed.add("still closed"));

        assertTrue(OverlayManager.dismissAll());
        assertEquals(Arrays.asList("still closed"), closed);
        assertFalse(OverlayManager.anyOpen());
    }
}
