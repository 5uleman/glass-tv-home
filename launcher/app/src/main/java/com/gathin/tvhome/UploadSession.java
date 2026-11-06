package com.gathin.tvhome;

/**
 * Lifecycle clock for the phone upload server. Any request resets the idle deadline, and a photo
 * upload is just another request, so sending a wallpaper can never close the server by itself.
 * Closing the QR dialog closes this session; otherwise ten silent minutes do. Pure so the timing
 * rule is unit tested without Android.
 */
final class UploadSession {
    static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000L;
    static final long IDLE_CHECK_MS = 5 * 1000L;

    private long lastRequestAt;
    private boolean closed;

    UploadSession(long now) {
        lastRequestAt = now;
    }

    synchronized void touch(long now) {
        if (!closed) lastRequestAt = now;
    }

    synchronized void close() {
        closed = true;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized long lastRequestAt() {
        return lastRequestAt;
    }

    /** True only for an open session that has heard nothing for a whole ten minutes. */
    synchronized boolean idleExpired(long now) {
        return !closed && now - lastRequestAt >= IDLE_TIMEOUT_MS;
    }
}
