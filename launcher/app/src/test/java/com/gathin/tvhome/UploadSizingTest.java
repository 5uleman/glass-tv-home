package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The upload sizing rules: orientation, low resolution, absurd sizes, sampling and cache keys. */
public class UploadSizingTest {

    private static final int UI_W = 1920, UI_H = 1080;

    @Test
    public void quarterTurnOrientationsSwapTheAxes() {
        assertArrayEquals(new int[]{3000, 4000}, UploadSizing.orientedSize(4000, 3000, 6));
        assertArrayEquals(new int[]{3000, 4000}, UploadSizing.orientedSize(4000, 3000, 8));
        assertArrayEquals(new int[]{3000, 4000}, UploadSizing.orientedSize(4000, 3000, 5));
        assertArrayEquals(new int[]{3000, 4000}, UploadSizing.orientedSize(4000, 3000, 7));
        for (int kept : new int[]{1, 2, 3, 4}) {
            assertArrayEquals(new int[]{4000, 3000}, UploadSizing.orientedSize(4000, 3000, kept));
            assertFalse(UploadSizing.swapsAxes(kept));
        }
    }

    @Test
    public void lowResolutionIsBelow1280By720() {
        assertTrue(UploadSizing.isLowRes(640, 480));
        assertTrue(UploadSizing.isLowRes(800, 1200));
        assertTrue(UploadSizing.isLowRes(1279, 720));
        assertFalse(UploadSizing.isLowRes(1280, 720));
        assertFalse(UploadSizing.isLowRes(1920, 1080));
    }

    @Test
    public void aNormalSourceBecomesExactlyTheUiSize() {
        assertArrayEquals(new int[]{UI_W, UI_H}, UploadSizing.outputSize(4000, 3000, UI_W, UI_H));
        assertArrayEquals(new int[]{UI_W, UI_H}, UploadSizing.outputSize(3000, 4000, UI_W, UI_H));
        assertArrayEquals(new int[]{UI_W, UI_H}, UploadSizing.outputSize(8000, 2000, UI_W, UI_H));
        assertArrayEquals(new int[]{3840, 2160}, UploadSizing.outputSize(6000, 4000, 3840, 2160));
    }

    @Test
    public void aLowResolutionSourceKeepsItsOwnCropAndIsNotUpscaled() {
        assertArrayEquals(new int[]{640, 360}, UploadSizing.outputSize(640, 480, UI_W, UI_H));
        assertArrayEquals(new int[]{800, 450}, UploadSizing.outputSize(800, 1200, UI_W, UI_H));
        assertArrayEquals(new int[]{1279, 719}, UploadSizing.outputSize(1279, 720, UI_W, UI_H));
    }

    @Test
    public void theCoverSizeFillsTheOutputWithTheSourcesShape() {
        assertArrayEquals(new int[]{1920, 1440}, UploadSizing.coverSize(4000, 3000, UI_W, UI_H));
        assertArrayEquals(new int[]{1920, 2560}, UploadSizing.coverSize(3000, 4000, UI_W, UI_H));
        assertArrayEquals(new int[]{4320, 1080}, UploadSizing.coverSize(8000, 2000, UI_W, UI_H));
        assertArrayEquals(new int[]{1920, 1280}, UploadSizing.coverSize(6000, 4000, UI_W, UI_H));
        assertArrayEquals(new int[]{640, 480}, UploadSizing.coverSize(640, 480, UI_W, UI_H));
    }

    @Test
    public void absurdSizesAreOver100MegapixelsOrA4To1Ratio() {
        assertTrue(UploadSizing.isExtreme(12000, 9000));     // 108 MP
        assertTrue(UploadSizing.isExtreme(8001, 2000));      // just over 4:1
        assertTrue(UploadSizing.isExtreme(20000, 15000));
        assertFalse(UploadSizing.isExtreme(8000, 2000));     // exactly 4:1
        assertFalse(UploadSizing.isExtreme(10000, 10000));   // exactly 100 MP
        assertTrue(UploadSizing.isExtreme(10001, 10000));    // just over 100 MP
        assertFalse(UploadSizing.isExtreme(6000, 4000));
    }

    @Test
    public void theSampleCoversTheDecodeTargetWithoutBlowingUpMemory() {
        // 12 MP 4:3: 2000x1500 still covers 1920x1440.
        assertEquals(2, UploadSizing.sampleSize(4000, 3000, 1920, 1440, false));
        // 24 MP 3:2.
        assertEquals(2, UploadSizing.sampleSize(6000, 4000, 1920, 1280, false));
        // A 4:1 panorama: the strict sample would decode all 16 MP; the fallback budget halves it.
        assertEquals(2, UploadSizing.sampleSize(8000, 2000, 4320, 1080, false));
        // 108 MP and over 100 MP: one step further than the cover needs.
        assertEquals(8, UploadSizing.sampleSize(12000, 9000, 1920, 1080, true));
        // A tiny source is decoded as is.
        assertEquals(1, UploadSizing.sampleSize(640, 480, 640, 480, false));
        // 300 MP: the sample keeps the fallback decode at a sane size.
        int sample = UploadSizing.sampleSize(20000, 15000, 1920, 1080, true);
        assertTrue("sample " + sample, sample >= 16);
        assertTrue((20000L / sample) * (15000L / sample)
                <= UploadSizing.FALLBACK_MAX_PIXELS);
    }

    @Test
    public void theCacheKeyFollowsTheFileAndTheTier() {
        String a = UploadSizing.identity("photo.jpg", 4_000_000, 1_700_000_000_000L);
        String again = UploadSizing.identity("photo.jpg", 4_000_000, 1_700_000_000_000L);
        assertEquals(a, again);
        assertEquals(40, a.length());
        assertNotEquals(a, UploadSizing.identity("photo2.jpg", 4_000_000, 1_700_000_000_000L));
        assertNotEquals(a, UploadSizing.identity("photo.jpg", 4_000_001, 1_700_000_000_000L));
        assertNotEquals(a, UploadSizing.identity("photo.jpg", 4_000_000, 1_700_000_000_001L));

        assertEquals(a + "_1920x1080.jpg",
                UploadSizing.cacheName("photo.jpg", 4_000_000, 1_700_000_000_000L, 1920, 1080));
        assertNotEquals(
                UploadSizing.cacheName("photo.jpg", 4_000_000, 1, 1920, 1080),
                UploadSizing.cacheName("photo.jpg", 4_000_000, 1, 3840, 2160));
    }
}
