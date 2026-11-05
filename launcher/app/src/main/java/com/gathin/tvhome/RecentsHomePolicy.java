package com.gathin.tvhome;

/**
 * Whether a finished Clear all should also close the recent-apps deck, revealing Home. Kept apart
 * from Android so the guards are unit tested: only while the deck is still open on a foreground
 * Home, only when at least one app was really closed, once per opening, and never twice inside
 * the debounce window (so a repeated or racing clear cannot loop).
 */
final class RecentsHomePolicy {

    /** One switch for the whole feature: false leaves Clear all on the deck with its empty state. */
    static final boolean ENABLED = true;

    /** A second Clear all this soon after one that went Home is ignored. */
    static final long DEBOUNCE_MS = 1500;

    private RecentsHomePolicy() {
    }

    static boolean shouldGoHome(boolean enabled, boolean deckOpen, boolean homeForeground,
                                boolean alreadyFiredThisOpening, int appsClosed,
                                long nowUptimeMs, long lastHomeUptimeMs) {
        if (!enabled || !deckOpen || !homeForeground || alreadyFiredThisOpening) return false;
        if (appsClosed <= 0) return false;
        return lastHomeUptimeMs <= 0 || nowUptimeMs - lastHomeUptimeMs >= DEBOUNCE_MS;
    }
}
