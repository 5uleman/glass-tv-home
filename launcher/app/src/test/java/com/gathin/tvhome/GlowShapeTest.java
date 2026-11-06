package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlowShapeTest {

    @Test
    public void glowIsStrongestAtTheTopEdge() {
        assertEquals(GlowShape.PEAK_ALPHA, GlowShape.alphaAt(0f), 0.0001f);
        assertTrue(GlowShape.PEAK_ALPHA >= 0.45f);
        assertTrue(GlowShape.PEAK_ALPHA <= 0.54f);
        assertTrue(GlowShape.alphaAt(0.01f) > 0f);
    }

    @Test
    public void strengthIsAboutThreeTimesTheOldValue() {
        assertTrue(GlowShape.PEAK_ALPHA / 0.18f >= 2.5f);
        assertTrue(GlowShape.PEAK_ALPHA / 0.18f <= 3.0f);
    }

    @Test
    public void glowReachesZeroAtTwoFifthsOfTheScreen() {
        assertEquals(0.40f, GlowShape.HEIGHT_FRACTION, 0.0001f);
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
        assertEquals(0f, GlowShape.alphaAt(0.41f), 0.0001f);
        assertEquals(0f, GlowShape.alphaAt(0.6f), 0.0001f);
    }

    @Test
    public void brightPictureCapsTheStrength() {
        assertEquals(GlowShape.PEAK_ALPHA, GlowShape.peakFor(0f), 0.0001f);
        assertEquals(GlowShape.PEAK_ALPHA, GlowShape.peakFor(GlowShape.DIM_LUMA), 0.0001f);
        assertEquals(GlowShape.BRIGHT_CAP, GlowShape.peakFor(GlowShape.BRIGHT_LUMA), 0.0001f);
        assertEquals(GlowShape.BRIGHT_CAP, GlowShape.peakFor(1f), 0.0001f);
        float middle = GlowShape.peakFor((GlowShape.DIM_LUMA + GlowShape.BRIGHT_LUMA) / 2f);
        assertTrue(middle < GlowShape.PEAK_ALPHA && middle > GlowShape.BRIGHT_CAP);
    }

    @Test
    public void cappedGlowIsStillVisible() {
        assertTrue(GlowShape.BRIGHT_CAP < GlowShape.PEAK_ALPHA);
        assertEquals(GlowShape.BRIGHT_CAP, GlowShape.alphaAt(0f, GlowShape.BRIGHT_CAP), 0.0001f);
        assertTrue(GlowShape.BRIGHT_CAP >= 0.25f);
    }

    @Test
    public void columnIsWhiteWithOnlyAlphaVarying() {
        int[] column = GlowShape.column(1080);
        assertEquals(1080, column.length);
        for (int pixel : column) assertEquals(0x00FFFFFF, pixel & 0x00FFFFFF);
        assertEquals(128, column[0] >>> 24);
        assertEquals(0, column[1079] >>> 24);
    }

    @Test
    public void columnUsesTheGivenPeak() {
        int[] column = GlowShape.column(1080, GlowShape.BRIGHT_CAP);
        assertEquals(Math.round(255f * GlowShape.BRIGHT_CAP), column[0] >>> 24);
    }

    @Test
    public void columnFadesSmoothlyWithoutAHardEnd() {
        int[] column = GlowShape.column(1080);
        int end = (int) (1080 * GlowShape.HEIGHT_FRACTION);
        for (int y = end - 20; y < end; y++) {
            assertTrue("tail still visible at " + y, (column[y] >>> 24) <= 2);
        }
    }

    @Test
    public void veryDarkColourGainsValueAndKeepsItsHue() {
        int lit = GlowShape.visibleColor(0x330000);
        assertEquals(0x6B0000, lit);
        assertEquals(0, (lit >> 8) & 0xFF);
        assertEquals(0, lit & 0xFF);
    }

    @Test
    public void visibleColourKeepsItsSaturation() {
        int lit = GlowShape.visibleColor(0x102030);
        int r = (lit >> 16) & 0xFF, g = (lit >> 8) & 0xFF, b = lit & 0xFF;
        float before = (48f - 16f) / 48f;
        float after = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)))
                / (float) Math.max(r, Math.max(g, b));
        assertEquals(before, after, 0.02f);
    }

    @Test
    public void brightColourIsUnchangedAndBlackStaysBlack() {
        assertEquals(0xE50914, GlowShape.visibleColor(0xFFE50914));
        assertEquals(0x000000, GlowShape.visibleColor(0));
    }
}
