package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlareColorTest {

    @Test
    public void normaliseCapsSaturationAndRaisesBrightness() {
        float[] hsv = {210f, 1f, 0.2f};
        assertTrue(GlareColor.normalize(hsv));
        assertEquals(210f, hsv[0], 0.01f);
        assertEquals(0.55f, hsv[1], 0.001f);
        assertEquals(0.88f, hsv[2], 0.001f);
    }

    @Test
    public void normaliseLiftsVeryLowSaturationToAReadableTint() {
        float[] hsv = {30f, 0.19f, 0.6f};
        assertTrue(GlareColor.normalize(hsv));
        assertEquals(0.25f, hsv[1], 0.001f);
        assertEquals(0.88f, hsv[2], 0.001f);
    }

    @Test
    public void normaliseRejectsGray() {
        float[] hsv = {30f, 0.1f, 0.9f};
        assertFalse(GlareColor.normalize(hsv));
    }

    @Test
    public void withAlphaKeepsTheColour() {
        assertEquals(0x8038BDF8, GlareColor.withAlpha(0xFF38BDF8, 0x80));
        assertEquals(0x3D67E8F9, GlareColor.withAlpha(0xFF67E8F9, 0x3D));
    }

    @Test
    public void zeroFallsBackToClassic() {
        GlareColor.setActive(0);
        assertEquals(GlareColor.CLASSIC, GlareColor.current());
    }

    @Test
    public void neutralIsLightOnDarkArtAndMidOnBrightArt() {
        int dark = GlareColor.neutral(0f);
        int bright = GlareColor.neutral(1f);
        assertEquals(GlareColor.NEUTRAL_LIGHT, dark);
        assertEquals(GlareColor.NEUTRAL_MID, bright);
        assertTrue("dark art gets the lighter shade", luma(dark) > luma(bright) + 0.3);
        assertTrue("no blue cast on dark art", saturation(dark) < 0.10f);
        assertTrue("no blue cast on bright art", saturation(bright) < 0.12f);
    }

    @Test
    public void neutralStaysVisibleOnBothKindsOfArt() {
        assertTrue("reads on a black screen", contrast(GlareColor.neutral(0f), 0xFF000000) >= 10.0);
        assertTrue("reads on a white sky", contrast(GlareColor.neutral(1f), 0xFFFFFFFF) >= 2.4);
        assertTrue("reads on mid art", contrast(GlareColor.neutral(0.5f), 0xFF808080) >= 1.8);
    }

    private static float luma(int color) {
        return 0.2126f * linear((color >> 16) & 0xFF)
                + 0.7152f * linear((color >> 8) & 0xFF)
                + 0.0722f * linear(color & 0xFF);
    }

    private static float linear(int channel) {
        float c = channel / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float saturation(int color) {
        int max = Math.max((color >> 16) & 0xFF, Math.max((color >> 8) & 0xFF, color & 0xFF));
        int min = Math.min((color >> 16) & 0xFF, Math.min((color >> 8) & 0xFF, color & 0xFF));
        return max == 0 ? 0f : (max - min) / (float) max;
    }

    private static float contrast(int a, int b) {
        float la = luma(a);
        float lb = luma(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }
}
