package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperSizingTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    @Test
    public void sixteenByNineHitsTheScreenExactly() {
        assertArrayEquals(new int[]{1920, 1080},
                WallpaperSizing.coverSize(2560, 1440, SCREEN_W, SCREEN_H));
        assertArrayEquals(new int[]{1920, 1080},
                WallpaperSizing.coverSize(1920, 1080, SCREEN_W, SCREEN_H));
    }

    @Test
    public void aThreeByTwoSourcePadsTheWidthAndCropsTheHeight() {
        assertArrayEquals(new int[]{1920, 1280},
                WallpaperSizing.coverSize(2000, 1333, SCREEN_W, SCREEN_H));
        assertArrayEquals(new int[]{1920, 1280},
                WallpaperSizing.coverSize(6000, 4000, SCREEN_W, SCREEN_H));
    }

    @Test
    public void aWiderThanSixteenByNineSourcePadsTheHeight() {
        assertArrayEquals(new int[]{1966, 1080},
                WallpaperSizing.coverSize(2500, 1373, SCREEN_W, SCREEN_H));
        assertArrayEquals(new int[]{1967, 1080},
                WallpaperSizing.coverSize(1920, 1054, SCREEN_W, SCREEN_H));
    }

    @Test
    public void everyResultCoversTheScreenWithoutStretching() {
        int[][] sources = {{2560, 1440}, {2000, 1333}, {2500, 1373}, {9908, 6606},
                {2738, 1825}, {1920, 1054}, {1000, 500}, {1080, 1920}};
        for (int[] s : sources) {
            int[] out = WallpaperSizing.coverSize(s[0], s[1], SCREEN_W, SCREEN_H);
            assertTrue("covers width", out[0] >= SCREEN_W);
            assertTrue("covers height", out[1] >= SCREEN_H);
            double srcAspect = s[0] / (double) s[1];
            double outAspect = out[0] / (double) out[1];
            assertTrue("aspect kept for " + s[0] + "x" + s[1],
                    Math.abs(srcAspect - outAspect) < 0.002);
        }
    }

    @Test
    public void aDegenerateSourceFallsBackToTheScreen() {
        assertArrayEquals(new int[]{1920, 1080},
                WallpaperSizing.coverSize(0, 0, SCREEN_W, SCREEN_H));
    }
}
