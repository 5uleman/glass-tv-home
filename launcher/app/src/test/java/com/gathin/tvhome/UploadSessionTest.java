package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UploadSessionTest {

    private static final long T0 = 1_000_000L;

    @Test
    public void staysUpForTheWholeTenMinutes() {
        UploadSession session = new UploadSession(T0);
        assertFalse(session.idleExpired(T0 + UploadSession.IDLE_TIMEOUT_MS - 1));
        assertTrue(session.idleExpired(T0 + UploadSession.IDLE_TIMEOUT_MS));
    }

    @Test
    public void oneRequestResetsTheWholeDeadline() {
        UploadSession session = new UploadSession(T0);
        session.touch(T0 + 9 * 60 * 1000L);
        assertFalse(session.idleExpired(T0 + 10 * 60 * 1000L));
        assertTrue(session.idleExpired(T0 + 19 * 60 * 1000L));
    }

    @Test
    public void anUploadAndItsListRefreshKeepTheServerOpen() {
        UploadSession session = new UploadSession(T0);
        session.touch(T0 + 1_000);
        session.touch(T0 + 2_000);
        assertFalse(session.isClosed());
        assertFalse(session.idleExpired(T0 + 3_000));
        assertEquals(T0 + 2_000, session.lastRequestAt());
    }

    @Test
    public void closingTheDialogEndsTheSessionAtOnce() {
        UploadSession session = new UploadSession(T0);
        session.close();
        assertTrue(session.isClosed());
        assertFalse(session.idleExpired(T0 + UploadSession.IDLE_TIMEOUT_MS));
    }

    @Test
    public void aLateRequestCannotReviveAClosedSession() {
        UploadSession session = new UploadSession(T0);
        session.close();
        session.touch(T0 + 5_000);
        assertEquals(T0, session.lastRequestAt());
        assertFalse(session.idleExpired(T0 + 5_000));
    }
}
