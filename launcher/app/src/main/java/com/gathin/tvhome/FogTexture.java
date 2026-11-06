package com.gathin.tvhome;

import java.util.Random;

/**
 * The fog veil's procedural texture: seamless fractal value noise, white with only the alpha
 * varying. Pure Java, so the renderer, the tests and the preview renderer build identical pixels.
 * White RGB with an alpha ramp means filtered edges never pick up a dark or coloured fringe.
 */
final class FogTexture {

    static final int WIDTH = 1024;
    static final int HEIGHT = 256;

    /** Lattice cells per octave. Narrow in x and tall in y, so the noise stretches into wisps. */
    private static final int[][] OCTAVE_CELLS = {
            {3, 6}, {6, 12}, {12, 24}, {24, 48}, {48, 96}
    };
    private static final float[] OCTAVE_AMPLITUDES = {1f, 0.55f, 0.30f, 0.16f, 0.08f};
    private static final float TOTAL_AMPLITUDE = 2.09f;
    private static final long SEED = 0x5EEDF06L;

    private FogTexture() {
    }

    /** ARGB pixels: white, alpha 0 at the top edge growing to full at the bottom. */
    static int[] pixels(int width, int height) {
        float[][] lattices = new float[OCTAVE_CELLS.length][];
        Random random = new Random(SEED);
        for (int o = 0; o < OCTAVE_CELLS.length; o++) {
            int cells = OCTAVE_CELLS[o][0] * OCTAVE_CELLS[o][1];
            lattices[o] = new float[cells];
            for (int i = 0; i < cells; i++) lattices[o][i] = random.nextFloat();
        }

        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            float t = y / (float) Math.max(1, height - 1);
            // Weighted to the lower part of the veil: clear at the top, strongest at the bottom.
            float envelope = t * t * (0.55f + 0.45f * t);
            for (int x = 0; x < width; x++) {
                float sum = 0f;
                for (int o = 0; o < OCTAVE_CELLS.length; o++) {
                    sum += OCTAVE_AMPLITUDES[o] * sample(lattices[o], OCTAVE_CELLS[o][0],
                            OCTAVE_CELLS[o][1], x, y, width, height);
                }
                float n = sum / TOTAL_AMPLITUDE;
                // A soft threshold leaves clear gaps between the wisps instead of a flat wash.
                n = clamp01((n - 0.30f) / 0.70f);
                n = n * n * (3f - 2f * n);
                int alpha = Math.round(255f * n * envelope);
                out[y * width + x] = (alpha << 24) | 0x00FFFFFF;
            }
        }
        return out;
    }

    private static float sample(float[] lattice, int cellsX, int cellsY,
                                int x, int y, int width, int height) {
        float fx = x * cellsX / (float) width;
        float fy = y * cellsY / (float) height;
        int x0 = (int) fx, y0 = (int) fy;
        float tx = fx - x0, ty = fy - y0;
        tx = tx * tx * (3f - 2f * tx);
        ty = ty * ty * (3f - 2f * ty);
        int xa = x0 % cellsX, ya = y0 % cellsY;
        int x1 = (xa + 1) % cellsX, y1 = (ya + 1) % cellsY;
        float v00 = lattice[ya * cellsX + xa];
        float v10 = lattice[ya * cellsX + x1];
        float v01 = lattice[y1 * cellsX + xa];
        float v11 = lattice[y1 * cellsX + x1];
        float top = v00 + (v10 - v00) * tx;
        float bottom = v01 + (v11 - v01) * tx;
        return top + (bottom - top) * ty;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
