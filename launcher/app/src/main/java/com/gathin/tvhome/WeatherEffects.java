package com.gathin.tvhome;

/**
 * Which weather effect the wallpaper should show, from the values the app already has. Pure:
 * no Android, no clock, no network. Missing values use {@link #UNKNOWN}.
 *
 * <p>Rain wins over fog. Air quality is not an input: a bad AQI stays in the text pill and never
 * paints the wallpaper.
 */
final class WeatherEffects {

    static final int UNKNOWN = -1;

    /** Below one kilometre, the sky is treated as fog even when the code does not say so. */
    static final int FOG_VISIBILITY_M = 1_000;
    /** At or below this visibility the veil is at full strength. */
    static final int FOG_HEAVY_VISIBILITY_M = 200;

    static final int FOG_CODE_LOW = 45;
    static final int FOG_CODE_HIGH = 48;

    /** Visibility just under the trigger. */
    static final float FOG_LIGHT_DENSITY = 0.35f;
    /** Visibility at or below {@link #FOG_HEAVY_VISIBILITY_M}. */
    static final float FOG_HEAVY_DENSITY = 1f;
    /** The code says fog but no visibility came with it: a medium veil. */
    static final float FOG_CODE_ONLY_DENSITY = 0.55f;

    private static final int DRIZZLE_FIRST = 51, DRIZZLE_LAST = 57;
    private static final int RAIN_FIRST = 61, RAIN_LAST = 67;
    private static final int SHOWER_FIRST = 80, SHOWER_LAST = 82;
    private static final int STORM_FIRST = 95, STORM_LAST = 99;

    enum Effect { NONE, RAIN, FOG }

    private WeatherEffects() {
    }

    /**
     * @param enabled      the Dynamic Weather toggle
     * @param weatherCode  WMO weather code, or {@link #UNKNOWN}
     * @param visibilityM  visibility in metres, or {@link #UNKNOWN}
     */
    static Effect classify(boolean enabled, int weatherCode, int visibilityM) {
        if (!enabled) return Effect.NONE;
        if (isRain(weatherCode)) return Effect.RAIN;
        if (isFog(weatherCode, visibilityM)) return Effect.FOG;
        return Effect.NONE;
    }

    static boolean isFog(int weatherCode, int visibilityM) {
        return weatherCode == FOG_CODE_LOW || weatherCode == FOG_CODE_HIGH
                || (visibilityM != UNKNOWN && visibilityM < FOG_VISIBILITY_M);
    }

    /**
     * How thick the fog should look: 0 when there is no fog, {@link #FOG_LIGHT_DENSITY} just under
     * 1,000 m, rising to {@link #FOG_HEAVY_DENSITY} at 200 m. A code with no visibility is medium.
     */
    static float fogDensity(int weatherCode, int visibilityM) {
        if (isRain(weatherCode) || !isFog(weatherCode, visibilityM)) return 0f;
        if (visibilityM == UNKNOWN || visibilityM >= FOG_VISIBILITY_M) {
            return FOG_CODE_ONLY_DENSITY;
        }
        if (visibilityM <= FOG_HEAVY_VISIBILITY_M) return FOG_HEAVY_DENSITY;
        float t = (FOG_VISIBILITY_M - visibilityM)
                / (float) (FOG_VISIBILITY_M - FOG_HEAVY_VISIBILITY_M);
        return FOG_LIGHT_DENSITY + (FOG_HEAVY_DENSITY - FOG_LIGHT_DENSITY) * t;
    }

    static boolean isRain(int code) {
        return (code >= DRIZZLE_FIRST && code <= DRIZZLE_LAST)
                || (code >= RAIN_FIRST && code <= RAIN_LAST)
                || (code >= SHOWER_FIRST && code <= SHOWER_LAST)
                || (code >= STORM_FIRST && code <= STORM_LAST);
    }
}
