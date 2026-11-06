package com.gathin.tvhome;

/**
 * The "Weather Effect Preview" choice in the wallpaper menu: Auto, or one effect held for ten
 * minutes. Pure and in memory: a launcher restart starts at Auto, and nothing is written to disk.
 * The chosen mode feeds synthetic weather values through the same classifier and renderer as the
 * real forecast, so what the preview paints is what real weather would paint.
 */
final class WeatherEffectPreview {

    /** How long a chosen effect stays before the real weather returns. */
    static final long TIMEOUT_MS = 10 * 60_000L;

    /** Weather values that make {@link WeatherEffects} choose each effect. */
    enum Mode {
        AUTO("Auto", WeatherEffects.UNKNOWN, WeatherEffects.UNKNOWN),
        DRIZZLE("Drizzle", 53, WeatherEffects.UNKNOWN),
        RAIN("Rain", 63, WeatherEffects.UNKNOWN),
        THUNDERSTORM("Thunderstorm", 95, WeatherEffects.UNKNOWN),
        /** Just under the fog trigger: the lightest veil the real classifier draws. */
        LIGHT_FOG("Light fog", WeatherEffects.FOG_CODE_LOW, 990),
        HEAVY_FOG("Heavy fog", WeatherEffects.FOG_CODE_LOW, 150);

        final String label;
        final int weatherCode;
        final int visibilityM;

        Mode(String label, int weatherCode, int visibilityM) {
            this.label = label;
            this.weatherCode = weatherCode;
            this.visibilityM = visibilityM;
        }
    }

    private Mode mode = Mode.AUTO;
    private long setAt;

    /** The stored choice, without considering expiry (for tests and labels). */
    Mode mode() {
        return mode;
    }

    /** The next choice in the cycle, which restarts the ten-minute window. */
    Mode next(long now) {
        Mode[] all = Mode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
        setAt = now;
        return mode;
    }

    /** The choice in force at {@code now}; an expired preview reports Auto and stays there. */
    Mode modeAt(long now) {
        if (mode != Mode.AUTO && now - setAt >= TIMEOUT_MS) mode = Mode.AUTO;
        return mode;
    }

    /** Milliseconds until the real weather returns; 0 when nothing is being previewed. */
    long remainingMs(long now) {
        if (modeAt(now) == Mode.AUTO) return 0;
        return Math.max(0, TIMEOUT_MS - (now - setAt));
    }

    void reset() {
        mode = Mode.AUTO;
        setAt = 0;
    }
}
