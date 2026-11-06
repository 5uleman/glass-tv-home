package com.gathin.tvhome;

/**
 * Which weather effect the wallpaper should show, from the values the app already has. Pure:
 * no Android, no clock, no network. Missing values use {@link #UNKNOWN}.
 *
 * <p>Precedence is RAIN, then FOG, then SMOG. Rain is the most definite signal; fog beats smog
 * when both are present; smog only appears when the air is bad and the sky is not already
 * raining or foggy.
 */
final class WeatherEffects {

    static final int UNKNOWN = -1;

    /** Below one kilometre, the sky is treated as fog even when the code does not say so. */
    static final int FOG_VISIBILITY_M = 1_000;
    /** US AQI at which the warm haze effect starts. */
    static final int SMOG_AQI_MIN = 150;

    static final int FOG_CODE_LOW = 45;
    static final int FOG_CODE_HIGH = 48;

    private static final int DRIZZLE_FIRST = 51, DRIZZLE_LAST = 57;
    private static final int RAIN_FIRST = 61, RAIN_LAST = 67;
    private static final int SHOWER_FIRST = 80, SHOWER_LAST = 82;
    private static final int STORM_FIRST = 95, STORM_LAST = 99;

    enum Effect { NONE, RAIN, FOG, SMOG }

    private WeatherEffects() {
    }

    /**
     * @param enabled      the Dynamic Weather toggle
     * @param weatherCode  WMO weather code, or {@link #UNKNOWN}
     * @param visibilityM  visibility in metres, or {@link #UNKNOWN}
     * @param aqi          US AQI (or the official local figure), or {@link #UNKNOWN}
     */
    static Effect classify(boolean enabled, int weatherCode, int visibilityM, int aqi) {
        if (!enabled) return Effect.NONE;
        if (isRain(weatherCode)) return Effect.RAIN;
        boolean fog = weatherCode == FOG_CODE_LOW || weatherCode == FOG_CODE_HIGH
                || (visibilityM != UNKNOWN && visibilityM < FOG_VISIBILITY_M);
        if (fog) return Effect.FOG;
        if (aqi != UNKNOWN && aqi >= SMOG_AQI_MIN) return Effect.SMOG;
        return Effect.NONE;
    }

    static boolean isRain(int code) {
        return (code >= DRIZZLE_FIRST && code <= DRIZZLE_LAST)
                || (code >= RAIN_FIRST && code <= RAIN_LAST)
                || (code >= SHOWER_FIRST && code <= SHOWER_LAST)
                || (code >= STORM_FIRST && code <= STORM_LAST);
    }
}
