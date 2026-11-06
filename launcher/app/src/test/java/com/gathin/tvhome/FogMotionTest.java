package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The fog's motion design and the maths the renderer uses, without a screen or a clock. */
public class FogMotionTest {

    private static final float W = FogMotion.REFERENCE_W;
    private static final double EPS = 1e-3;

    @Test
    public void lightFogHasTwoLayersCrossingAtTheAgreedSpeeds() {
        FogMotion.Layer[] light = FogMotion.layers(FogLook.LAYERS_LIGHT);
        assertEquals(2, light.length);
        assertTrue(Math.abs(light[0].speedPx) >= 8f && Math.abs(light[0].speedPx) <= 12f);
        assertTrue(Math.abs(light[1].speedPx) >= 20f && Math.abs(light[1].speedPx) <= 28f);
        assertTrue("the two light layers must cross", light[0].speedPx * light[1].speedPx < 0f);
    }

    @Test
    public void heavyFogHasThreeLayersAndIsLivelierThanLight() {
        FogMotion.Layer[] light = FogMotion.layers(FogLook.LAYERS_LIGHT);
        FogMotion.Layer[] heavy = FogMotion.layers(FogLook.LAYERS_HEAVY);
        assertEquals(3, heavy.length);
        assertTrue(heavy[0].speedPx * heavy[1].speedPx < 0f);
        assertTrue(heavy[1].speedPx * heavy[2].speedPx < 0f);
        assertTrue(Math.abs(heavy[0].speedPx) >= Math.abs(light[0].speedPx));
        assertTrue(Math.abs(heavy[2].speedPx) >= Math.abs(light[1].speedPx));
        assertTrue(heavy[2].swayPx >= light[1].swayPx);
        assertTrue(heavy[2].breath >= light[1].breath);
    }

    @Test
    public void everyLayerSwaysTwelveToEighteenPixelsOnPeriodsOfNineToFifteenSeconds() {
        for (FogMotion.Layer[] set : new FogMotion.Layer[][]{FogMotion.LIGHT, FogMotion.HEAVY}) {
            for (FogMotion.Layer layer : set) {
                assertTrue("sway " + layer.swayPx, layer.swayPx >= 12f && layer.swayPx <= 18f);
                assertTrue("sway period " + layer.swaySeconds,
                        layer.swaySeconds >= 9f && layer.swaySeconds <= 15f);
            }
            for (int a = 0; a < set.length; a++) {
                for (int b = a + 1; b < set.length; b++) {
                    assertTrue("sway phases must differ",
                            Math.abs(set[a].swayPhase - set[b].swayPhase) > 0.5f);
                }
            }
        }
    }

    @Test
    public void everyLayerBreathesTenPercentOnPeriodsOfSevenToElevenSeconds() {
        for (FogMotion.Layer[] set : new FogMotion.Layer[][]{FogMotion.LIGHT, FogMotion.HEAVY}) {
            for (FogMotion.Layer layer : set) {
                assertTrue("breath " + layer.breath, layer.breath >= 0.09f && layer.breath <= 0.12f);
                assertTrue("breath period " + layer.breathSeconds,
                        layer.breathSeconds >= 7f && layer.breathSeconds <= 11f);
            }
            for (int a = 0; a < set.length; a++) {
                for (int b = a + 1; b < set.length; b++) {
                    assertTrue("breath phases must differ",
                            Math.abs(set[a].breathPhase - set[b].breathPhase) > 0.5f);
                }
            }
        }
    }

    @Test
    public void theLayerOffsetsDifferAtFourAndTenSeconds() {
        for (int count : new int[]{FogLook.LAYERS_LIGHT, FogLook.LAYERS_HEAVY}) {
            FogMotion.Layer[] layers = FogMotion.layers(count);
            float[] widths = FogLook.widths(count);
            for (int i = 0; i < count; i++) {
                float width = W * widths[i];
                float at4 = FogMotion.wrap(FogMotion.driftPx(layers[i], 4), width);
                float at10 = FogMotion.wrap(FogMotion.driftPx(layers[i], 10), width);
                assertTrue("layer " + i + " does not move by 4 s", Math.abs(at4) > 1f);
                assertTrue("layer " + i + " is static between 4 s and 10 s",
                        Math.abs(at4 - at10) > 1f);
            }
        }
        // Exact drift after 4 s: light far 40 px right, near 96 px left; heavy 48/72/108.
        FogMotion.Layer[] light = FogMotion.layers(FogLook.LAYERS_LIGHT);
        assertEquals(10f, light[0].speedPx, 0.001f);
        assertEquals(-24f, light[1].speedPx, 0.001f);
        assertEquals(40f, FogMotion.driftPx(light[0], 4), 0.001f);
        assertEquals(-96f, FogMotion.driftPx(light[1], 4), 0.001f);
        FogMotion.Layer[] heavy = FogMotion.layers(FogLook.LAYERS_HEAVY);
        assertEquals(12f, heavy[0].speedPx, 0.001f);
        assertEquals(-18f, heavy[1].speedPx, 0.001f);
        assertEquals(27f, heavy[2].speedPx, 0.001f);
    }

    @Test
    public void theDriftRepeatsAfterOneLayerWidth() {
        for (int count : new int[]{FogLook.LAYERS_LIGHT, FogLook.LAYERS_HEAVY}) {
            FogMotion.Layer[] layers = FogMotion.layers(count);
            float[] widths = FogLook.widths(count);
            for (int i = 0; i < count; i++) {
                float width = W * widths[i];
                float periodSec = width / Math.abs(layers[i].speedPx);
                for (double t : new double[]{0, 3.3, 41.7}) {
                    float now = FogMotion.wrap(FogMotion.driftPx(layers[i], t), width);
                    float later = FogMotion.wrap(FogMotion.driftPx(layers[i], t + periodSec), width);
                    assertEquals("layer " + i + " drift period", now, later, 0.01f);
                }
            }
        }
    }

    @Test
    public void theWrapIsSeamlessAcrossTheBoundary() {
        float period = 1600f;
        float before = FogMotion.wrap(period - 0.05f, period);
        float after = FogMotion.wrap(period + 0.05f, period);
        assertTrue(before > period - 0.1f);
        assertTrue(after < 0.1f);
        // One full repeat past the boundary is the same texture phase, just wrapped.
        assertEquals(0.05f, FogMotion.wrap(period + 0.05f, period), 0.001f);
        // Negative travel wraps into the same range without a jump in the visible position.
        assertEquals(period - 0.05f, FogMotion.wrap(-0.05f, period), 0.01f);
        assertEquals(FogMotion.wrap(7.5f, period), FogMotion.wrap(7.5f + 3 * period, period), 0.01f);
    }

    @Test
    public void swayAndBreathingHaveTheirStatedPeriods() {
        for (FogMotion.Layer[] set : new FogMotion.Layer[][]{FogMotion.LIGHT, FogMotion.HEAVY}) {
            for (FogMotion.Layer layer : set) {
                for (double t : new double[]{0, 2.7, 33.1}) {
                    float sway = FogMotion.swayPx(layer, t);
                    float later = FogMotion.swayPx(layer, t + layer.swaySeconds);
                    assertEquals("sway period", sway, later, 0.01f);
                    float breath = FogMotion.breathScale(layer, t);
                    float breathLater = FogMotion.breathScale(layer, t + layer.breathSeconds);
                    assertEquals("breath period", breath, breathLater, 0.0001f);
                }
            }
        }
    }

    @Test
    public void theAmplitudesStayInsideTheAgreedEnvelope() {
        for (FogMotion.Layer[] set : new FogMotion.Layer[][]{FogMotion.LIGHT, FogMotion.HEAVY}) {
            for (FogMotion.Layer layer : set) {
                float peakSway = 0f, minBreath = 2f, maxBreath = 0f;
                for (int i = 0; i <= 4000; i++) {
                    double t = i * 0.05;
                    peakSway = Math.max(peakSway, Math.abs(FogMotion.swayPx(layer, t)));
                    minBreath = Math.min(minBreath, FogMotion.breathScale(layer, t));
                    maxBreath = Math.max(maxBreath, FogMotion.breathScale(layer, t));
                }
                assertTrue(peakSway <= layer.swayPx + 0.01f);
                assertTrue("sway does not reach its amplitude", peakSway >= layer.swayPx - 0.5f);
                assertEquals(1 - layer.breath, minBreath, 0.01f);
                assertEquals(1 + layer.breath, maxBreath, 0.01f);
            }
        }
    }

    @Test
    public void motionIsContinuousAtThirtyFramesPerSecond() {
        double step = 1 / 30.0;
        for (FogMotion.Layer[] set : new FogMotion.Layer[][]{FogMotion.LIGHT, FogMotion.HEAVY}) {
            for (FogMotion.Layer layer : set) {
                for (double t : new double[]{0.5, 9.9, 77.3}) {
                    float dSway = FogMotion.swayPx(layer, t + step) - FogMotion.swayPx(layer, t);
                    float dBreath = FogMotion.breathScale(layer, t + step) - FogMotion.breathScale(layer, t);
                    assertTrue("sway steps", Math.abs(dSway) < 0.5f);
                    assertTrue("breathing steps", Math.abs(dBreath) < 0.01f);
                }
            }
        }
    }
}
