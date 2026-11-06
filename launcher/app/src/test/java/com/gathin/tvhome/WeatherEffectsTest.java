package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherEffectsTest {

    private static final int CLEAR = 0;
    private static final int RAIN = 61;
    private static final int DRIZZLE = 51;
    private static final int SHOWERS = 80;
    private static final int STORM = 95;
    private static final int FOG_CODE = 45;
    private static final int FOG_CODE_LOW = 48;
    private static final int UNKNOWN = WeatherEffects.UNKNOWN;

    @Test
    public void toggleOffAlwaysMeansNoEffect() {
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(false, STORM, 10));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(false, FOG_CODE, 10));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(false, CLEAR, 10_000));
    }

    @Test
    public void everyRainCodeKeepsItsOwnEffect() {
        assertEquals(WeatherEffects.Effect.DRIZZLE, WeatherEffects.classify(true, DRIZZLE, 10_000));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, 10_000));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, SHOWERS, 10_000));
        assertEquals(WeatherEffects.Effect.THUNDERSTORM, WeatherEffects.classify(true, STORM, 10_000));
        assertTrue(WeatherEffects.isRain(57));
        assertFalse(WeatherEffects.isRain(58));
        assertTrue(WeatherEffects.isRain(82));
        assertFalse(WeatherEffects.isRain(83));
        assertTrue(WeatherEffects.isRain(99));
        assertFalse(WeatherEffects.isRain(100));
    }

    @Test
    public void rainWinsOverFog() {
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, 100));
        assertEquals(WeatherEffects.Effect.DRIZZLE, WeatherEffects.classify(true, DRIZZLE, 100));
        assertEquals(WeatherEffects.Effect.THUNDERSTORM, WeatherEffects.classify(true, STORM, 0));
    }

    @Test
    public void fogCodesShowFog() {
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, FOG_CODE, 10_000));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, FOG_CODE_LOW, 10_000));
    }

    @Test
    public void visibilityBoundaryIsOneThousandMetres() {
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, CLEAR, 999));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, 1_000));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, 10_000));
    }

    @Test
    public void codeOnlyFogIsMedium() {
        assertEquals(WeatherEffects.FOG_CODE_ONLY_DENSITY,
                WeatherEffects.fogDensity(FOG_CODE, UNKNOWN), 0.0001f);
        assertEquals(WeatherEffects.FOG_CODE_ONLY_DENSITY,
                WeatherEffects.fogDensity(FOG_CODE_LOW, 10_000), 0.0001f);
        assertEquals(WeatherEffects.FOG_CODE_ONLY_DENSITY,
                WeatherEffects.fogDensity(FOG_CODE_LOW, 1_000), 0.0001f);
    }

    @Test
    public void fogGrowsAsVisibilityDropsToTwoHundredMetres() {
        assertEquals(WeatherEffects.FOG_LIGHT_DENSITY,
                WeatherEffects.fogDensity(CLEAR, 999), 0.001f);
        assertTrue(WeatherEffects.fogDensity(CLEAR, 600)
                > WeatherEffects.fogDensity(CLEAR, 800));
        assertEquals(0.675f, WeatherEffects.fogDensity(CLEAR, 600), 0.001f);
        assertEquals(WeatherEffects.FOG_HEAVY_DENSITY,
                WeatherEffects.fogDensity(CLEAR, 200), 0.0001f);
        assertEquals(WeatherEffects.FOG_HEAVY_DENSITY,
                WeatherEffects.fogDensity(CLEAR, 50), 0.0001f);
    }

    @Test
    public void fogDensityIsZeroWhenThereIsNoFog() {
        assertEquals(0f, WeatherEffects.fogDensity(CLEAR, 1_000), 0.0001f);
        assertEquals(0f, WeatherEffects.fogDensity(CLEAR, 20_000), 0.0001f);
        assertEquals(0f, WeatherEffects.fogDensity(UNKNOWN, UNKNOWN), 0.0001f);
        assertEquals(0f, WeatherEffects.fogDensity(RAIN, 100), 0.0001f);
    }

    @Test
    public void missingValuesMeanNoEffectWhenNothingIsKnown() {
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, UNKNOWN, UNKNOWN));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, UNKNOWN));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, 3, 20_000));
    }

    @Test
    public void partialDataStillClassifies() {
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, UNKNOWN));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, UNKNOWN, 800));
    }

    @Test
    public void badAirAloneNeverPaintsTheWallpaper() {
        // AQI is no longer an input: the same clear, long-visibility sky is always NONE.
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, 20_000));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, 3, 20_000));
    }

    @Test
    public void namedThresholdsKeepTheirDefaults() {
        assertEquals(1_000, WeatherEffects.FOG_VISIBILITY_M);
        assertEquals(200, WeatherEffects.FOG_HEAVY_VISIBILITY_M);
        assertEquals(0.35f, WeatherEffects.FOG_LIGHT_DENSITY, 0.0001f);
        assertEquals(1f, WeatherEffects.FOG_HEAVY_DENSITY, 0.0001f);
        assertEquals(0.55f, WeatherEffects.FOG_CODE_ONLY_DENSITY, 0.0001f);
    }
}
