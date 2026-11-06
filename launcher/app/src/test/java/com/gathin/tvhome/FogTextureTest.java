package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FogTextureTest {

    private static int[] texture() {
        return FogTexture.pixels(FogTexture.WIDTH, FogTexture.HEIGHT);
    }

    @Test
    public void textureIsOneMegabyteAtTheStatedSize() {
        assertEquals(1024, FogTexture.WIDTH);
        assertEquals(256, FogTexture.HEIGHT);
        assertEquals(FogTexture.WIDTH * FogTexture.HEIGHT,
                texture().length);
    }

    @Test
    public void textureIsDeterministic() {
        assertArrayEquals(texture(), texture());
    }

    @Test
    public void everyPixelIsTheSameNeutralGreyWithOnlyAlphaVarying() {
        for (int pixel : texture()) {
            assertEquals(FogLook.RGB, pixel & 0x00FFFFFF);
        }
    }

    @Test
    public void textureHasClearGapsAndDenseCores() {
        int[] pixels = texture();
        int gaps = 0, cores = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha == 0) gaps++;
            if (alpha >= 200) cores++;
        }
        double gapShare = gaps / (double) pixels.length;
        double coreShare = cores / (double) pixels.length;
        assertTrue("no real gaps: " + gapShare, gapShare >= 0.08);
        assertTrue("no dense cores: " + coreShare, coreShare >= 0.20);
    }

    @Test
    public void textureIsDenseEnoughToBeVisibleButNotASolidWash() {
        long sum = 0;
        int[] pixels = texture();
        for (int pixel : pixels) sum += pixel >>> 24;
        double mean = sum / (double) pixels.length / 255.0;
        assertTrue("too thin: " + mean, mean >= 0.45);
        assertTrue("a solid wash: " + mean, mean <= 0.62);
    }

    @Test
    public void veilIsClearAtTheTopAndStrongestAtTheBottom() {
        int[] pixels = texture();
        int w = FogTexture.WIDTH;
        long top = 0, bottom = 0;
        for (int x = 0; x < w; x++) {
            top += pixels[x] >>> 24;
            bottom += pixels[(FogTexture.HEIGHT - 1) * w + x] >>> 24;
        }
        assertEquals(0, top);
        assertTrue(bottom > 0);
    }

    @Test
    public void bottomSliceIsMuchStrongerThanTheTopSlice() {
        int[] pixels = texture();
        int w = FogTexture.WIDTH;
        long top = 0, bottom = 0;
        int slice = FogTexture.HEIGHT / 10;
        for (int y = 0; y < slice; y++) {
            for (int x = 0; x < w; x++) top += pixels[y * w + x] >>> 24;
        }
        for (int y = FogTexture.HEIGHT - slice; y < FogTexture.HEIGHT; y++) {
            for (int x = 0; x < w; x++) bottom += pixels[y * w + x] >>> 24;
        }
        assertTrue(bottom > top * 3);
    }

    @Test
    public void leftAndRightEdgesJoinWithoutASeam() {
        int[] pixels = texture();
        int w = FogTexture.WIDTH;
        int worst = 0;
        long total = 0;
        for (int y = 0; y < FogTexture.HEIGHT; y++) {
            int left = pixels[y * w] >>> 24;
            int right = pixels[y * w + w - 1] >>> 24;
            int diff = Math.abs(left - right);
            worst = Math.max(worst, diff);
            total += diff;
        }
        assertTrue("edge join differs too much at some row: " + worst, worst <= 64);
        assertTrue("edge join is too visible on average", total / FogTexture.HEIGHT <= 24);
    }
}
