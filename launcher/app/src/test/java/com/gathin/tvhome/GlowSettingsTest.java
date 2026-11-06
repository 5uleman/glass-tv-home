package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The per-mode App Color Glow storage: separate keys, on by default, and the old stored Off. */
public class GlowSettingsTest {

    @Test
    public void theDefaultIsOn() {
        assertTrue(GlowSettings.DEFAULT);
        // A fresh install has neither key stored, so both modes read the default.
        assertEquals(true, GlowSettings.effective(false, GlowSettings.DEFAULT, GlowSettings.DEFAULT));
        assertEquals(true, GlowSettings.effective(true, GlowSettings.DEFAULT, GlowSettings.DEFAULT));
    }

    @Test
    public void eachModeHasItsOwnKey() {
        assertEquals("app_color_glow", GlowSettings.keyFor(false));
        assertEquals("app_color_glow_dark", GlowSettings.keyFor(true));
        assertNotEquals(GlowSettings.keyFor(false), GlowSettings.keyFor(true));
    }

    @Test
    public void switchingModesRestoresThatModesStoredChoice() {
        // The glow was turned off over a picture; the dark screen still has its own default of on.
        assertEquals(false, GlowSettings.effective(false, false, true));
        assertEquals(true, GlowSettings.effective(true, false, true));

        // Turned off on the dark screen only: the picture keeps its glow.
        assertEquals(true, GlowSettings.effective(false, true, false));
        assertEquals(false, GlowSettings.effective(true, true, false));
    }

    @Test
    public void someoneWhoAlreadyStoredTheOldSettingOffStillGetsTheDarkGlow() {
        // Before 4.0.0 there was one key, app_color_glow, and the dark screen shared it. With the
        // old key stored false and no dark key yet, the dark screen starts on rather than off.
        boolean oldStoredOff = false;
        boolean darkKeyAbsent = GlowSettings.DEFAULT;
        assertEquals(true, GlowSettings.effective(true, oldStoredOff, darkKeyAbsent));
        assertEquals(false, GlowSettings.effective(false, oldStoredOff, darkKeyAbsent));
    }
}
