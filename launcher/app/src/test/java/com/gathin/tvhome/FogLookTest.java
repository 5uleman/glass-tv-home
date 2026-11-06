package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FogLookTest {

    private static final float LIGHT = WeatherEffects.fogDensity(WeatherEffects.FOG_CODE_LOW, 990);
    private static final float HEAVY = 1f;

    private static float compositeOf(float density) {
        float clear = 1f;
        for (int i = 0; i < FogLook.LAYERS; i++) {
            clear *= 1f - FogLook.layerAlpha(density, i, true) / 255f;
        }
        return 1f - clear;
    }

    @Test
    public void lightFogUsesTheLightCapAndCoverage() {
        assertEquals(0.45f, FogLook.opacityCap(LIGHT), 0.01f);
        assertEquals(0.45f, FogLook.coverage(LIGHT), 0.01f);
    }

    @Test
    public void heavyFogUsesTheHeavyCapAndCoverage() {
        assertEquals(0.80f, FogLook.opacityCap(HEAVY), 0.0001f);
        assertEquals(0.65f, FogLook.coverage(HEAVY), 0.0001f);
    }

    @Test
    public void layerAlphasCompositeToTheSummedCap() {
        assertEquals(FogLook.opacityCap(LIGHT), compositeOf(LIGHT), 0.02f);
        assertEquals(FogLook.opacityCap(HEAVY), compositeOf(HEAVY), 0.02f);
    }

    @Test
    public void lightFogCoversLessOfTheScreenThanHeavyFog() {
        assertTrue(FogLook.bandTop(1080, LIGHT) > FogLook.bandTop(1080, HEAVY));
        assertEquals(1080f * 0.55f, FogLook.bandTop(1080, LIGHT), 3f);
        assertEquals(1080f * 0.35f, FogLook.bandTop(1080, HEAVY), 0.5f);
    }

    @Test
    public void everyLayerStaysInsideTheCoveredBand() {
        for (int i = 0; i < FogLook.LAYERS; i++) {
            float top = FogLook.layerTop(1080, HEAVY, i);
            assertTrue(top >= FogLook.bandTop(1080, HEAVY));
            assertTrue(top < 1080f);
        }
        assertEquals(FogLook.bandTop(1080, HEAVY), FogLook.layerTop(1080, HEAVY, 0), 0.001f);
        assertTrue(FogLook.layerTop(1080, HEAVY, 1) > FogLook.layerTop(1080, HEAVY, 0));
        assertTrue(FogLook.layerTop(1080, HEAVY, 2) > FogLook.layerTop(1080, HEAVY, 1));
    }

    @Test
    public void nightIsLighterThanDay() {
        for (int i = 0; i < FogLook.LAYERS; i++) {
            assertTrue(FogLook.layerAlpha(HEAVY, i, false) < FogLook.layerAlpha(HEAVY, i, true));
        }
    }

    @Test
    public void oddDensitiesClampToTheLightAndHeavyNumbers() {
        assertEquals(FogLook.LIGHT_CAP, FogLook.opacityCap(0f), 0.0001f);
        assertEquals(FogLook.LIGHT_COVERAGE, FogLook.coverage(0f), 0.0001f);
        assertEquals(FogLook.HEAVY_CAP, FogLook.opacityCap(2f), 0.0001f);
        assertEquals(FogLook.HEAVY_COVERAGE, FogLook.coverage(2f), 0.0001f);
    }

    @Test
    public void theThreeWeightsSumToOneAndNoLayerIsEmpty() {
        float sum = 0f;
        for (float weight : FogLook.WEIGHTS) {
            assertTrue(weight > 0f);
            sum += weight;
        }
        assertEquals(1f, sum, 0.0001f);
        assertEquals(3, FogLook.LAYERS);
    }
}
