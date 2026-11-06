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
}
