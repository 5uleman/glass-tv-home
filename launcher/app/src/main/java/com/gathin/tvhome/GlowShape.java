package com.gathin.tvhome;

/**
 * The focused app's ambient glow: a vertical gradient anchored to the top edge that reaches zero
 * at a third of the screen height. Pure, so the ambient view, the glass tint and the preview all
 * use the same numbers.
 *
 * <p>Kept deliberately low: at the top edge it is a fifth of the way to white, so the white clock
 * and the glass pills stay readable over a bright picture, and the middle of the wallpaper is
 * untouched.
 */
final class GlowShape {

    /** The glow is gone by this fraction of the screen height. */
    static final float HEIGHT_FRACTION = 1f / 3f;
    /** Opacity at the very top edge. */
    static final float PEAK_ALPHA = 0.18f;
    /** Above one, so the colour thins out quickly instead of ending on a visible line. */
    private static final float FALLOFF = 1.6f;

    private GlowShape() {
    }

    /** Opacity at screen fraction {@code t} (0 at the top edge, 1 at the bottom). */
    static float alphaAt(float t) {
        if (t <= 0f) return PEAK_ALPHA;
        if (t >= HEIGHT_FRACTION) return 0f;
        float remaining = 1f - t / HEIGHT_FRACTION;
        return PEAK_ALPHA * (float) Math.pow(remaining, FALLOFF);
    }

    /** One column of the shape, white with only the alpha varying, for a screen this tall. */
    static int[] column(int height) {
        int[] column = new int[height];
        float last = Math.max(1, height - 1);
        for (int y = 0; y < height; y++) {
            int alpha = Math.round(255f * alphaAt(y / last));
            column[y] = (alpha << 24) | 0x00FFFFFF;
        }
        return column;
    }
}
