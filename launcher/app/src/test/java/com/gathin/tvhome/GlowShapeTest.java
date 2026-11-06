package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlowShapeTest {

    @Test
    public void glowIsStrongestAtTheTopEdgeAndCapped() {
        assertEquals(GlowShape.PEAK_ALPHA, GlowShape.alphaAt(0f), 0.0001f);
        assertTrue(GlowShape.PEAK_ALPHA <= 0.2f);
        assertTrue(GlowShape.alphaAt(0.01f) > 0f);
    }

    @Test
    public void glowReachesZeroAtOneThirdOfTheScreen() {
        assertEquals(0f, GlowShape.alphaAt(GlowShape.HEIGHT_FRACTION), 0.0001f);
        assertEquals(0f, GlowShape.alphaAt(0.5f), 0.0001f);
        assertEquals(0f, GlowShape.alphaAt(1f), 0.0001f);
        assertTrue(GlowShape.alphaAt(0.2f) > 0f);
    }

    @Test
    public void glowNeverRisesAsItGoesDown() {
        float previous = GlowShape.alphaAt(0f);
        for (int i = 1; i <= 100; i++) {
            float value = GlowShape.alphaAt(i / 100f);
            assertTrue("rises at " + i + "%", value <= previous);
            previous = value;
        }
    }

    @Test
    public void middleOfTheScreenIsUntouched() {
        assertEquals(0f, GlowShape.alphaAt(0.34f), 0.0001f);
        assertEquals(0f, GlowShape.alphaAt(0.6f), 0.0001f);
    }

    @Test
    public void columnIsWhiteWithOnlyAlphaVarying() {
        int[] column = GlowShape.column(1080);
        assertEquals(1080, column.length);
        for (int pixel : column) assertEquals(0x00FFFFFF, pixel & 0x00FFFFFF);
        assertEquals(46, column[0] >>> 24);
        assertEquals(0, column[1079] >>> 24);
    }

    @Test
    public void columnFadesSmoothlyWithoutAHardEnd() {
        int[] column = GlowShape.column(1080);
        int end = (int) (1080 * GlowShape.HEIGHT_FRACTION);
        for (int y = end - 20; y < end; y++) {
            assertTrue("tail still visible at " + y, (column[y] >>> 24) <= 2);
        }
    }
}
