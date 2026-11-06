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
        assertEquals(WeatherEffects.Effect.NONE,
                WeatherEffects.classify(false, STORM, 10, 300));
        assertEquals(WeatherEffects.Effect.NONE,
                WeatherEffects.classify(false, FOG_CODE, 10, 300));
        assertEquals(WeatherEffects.Effect.NONE,
                WeatherEffects.classify(false, CLEAR, 10_000, 300));
    }

    @Test
    public void rainCodesAreRain() {
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, DRIZZLE, 10_000, 0));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, 10_000, 0));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, SHOWERS, 10_000, 0));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, STORM, 10_000, 0));
        assertTrue(WeatherEffects.isRain(57));
        assertFalse(WeatherEffects.isRain(58));
        assertTrue(WeatherEffects.isRain(82));
        assertFalse(WeatherEffects.isRain(83));
        assertTrue(WeatherEffects.isRain(99));
        assertFalse(WeatherEffects.isRain(100));
    }

    @Test
    public void rainWinsOverFogAndSmog() {
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, 100, 300));
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, STORM, 0, 999));
    }

    @Test
    public void fogCodesShowFog() {
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, FOG_CODE, 10_000, 0));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, FOG_CODE_LOW, 10_000, 0));
    }

    @Test
    public void visibilityBoundaryIsOneThousandMetres() {
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, CLEAR, 999, 0));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, 1_000, 0));
    }

    @Test
    public void fogWinsOverSmog() {
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, FOG_CODE, 10_000, 300));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, CLEAR, 500, 300));
    }

    @Test
    public void smogBoundaryIsOneHundredAndFifty() {
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, 10_000, 149));
        assertEquals(WeatherEffects.Effect.SMOG, WeatherEffects.classify(true, CLEAR, 10_000, 150));
        assertEquals(WeatherEffects.Effect.SMOG, WeatherEffects.classify(true, CLEAR, 10_000, 999));
    }

    @Test
    public void missingValuesMeanNoEffectWhenNothingIsKnown() {
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, UNKNOWN, UNKNOWN, UNKNOWN));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, CLEAR, UNKNOWN, UNKNOWN));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, UNKNOWN, 10_000, 100));
    }

    @Test
    public void partialDataStillClassifies() {
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, RAIN, UNKNOWN, UNKNOWN));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, UNKNOWN, 800, UNKNOWN));
        assertEquals(WeatherEffects.Effect.SMOG, WeatherEffects.classify(true, UNKNOWN, UNKNOWN, 150));
    }

    @Test
    public void ordinarySkyAndCleanAirMeanNoEffect() {
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, 3, 20_000, 100));
    }

    @Test
    public void namedThresholdsKeepTheirDefaults() {
        assertEquals(1_000, WeatherEffects.FOG_VISIBILITY_M);
        assertEquals(150, WeatherEffects.SMOG_AQI_MIN);
    }
}
