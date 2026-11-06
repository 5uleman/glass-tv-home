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
    public void everyPixelIsWhiteWithOnlyAlphaVarying() {
        for (int pixel : texture()) {
            assertEquals(0x00FFFFFF, pixel & 0x00FFFFFF);
        }
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
    public void lowerHalfIsWeightedMoreThanTheUpperHalf() {
        int[] pixels = texture();
        int w = FogTexture.WIDTH;
        long upper = 0, lower = 0;
        for (int y = 0; y < FogTexture.HEIGHT; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = pixels[y * w + x] >>> 24;
                if (y < FogTexture.HEIGHT / 2) upper += alpha;
                else lower += alpha;
            }
        }
        assertTrue(lower > upper * 3);
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
