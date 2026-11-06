package com.gathin.tvhome;

/**
 * The focused app's ambient glow: a vertical gradient anchored to the top edge that reaches zero
 * at two fifths of the screen height. Pure, so the ambient view, the glass tint and the preview
 * all use the same numbers.
 *
 * <p>The strength is raised well above a hint so a dark app colour still lights the room, but it
 * is capped when the picture behind the top bar is bright: the white clock, date and weather pill
 * keep their shadow-backed contrast there. The middle of the wallpaper stays untouched.
 */
final class GlowShape {

    /** Opacity at the very top edge on a dark picture. */
    static final float PEAK_ALPHA = 0.50f;
    /** The glow is gone by this fraction of the screen height. */
    static final float HEIGHT_FRACTION = 0.40f;
    /** On a bright picture the glow is held to this, so the white top bar stays readable. */
    static final float BRIGHT_CAP = 0.30f;
    /** At or below this top-band brightness the full strength is used. */
    static final float DIM_LUMA = 0.30f;
    /** From this top-band brightness up the capped strength is used. */
    static final float BRIGHT_LUMA = 0.60f;
    /** Above one, so the colour thins out quickly instead of ending on a visible line. */
    private static final float FALLOFF = 1.6f;
    /** Very dark colours are lifted to this value, keeping their hue and saturation. */
    static final float MIN_COLOR_VALUE = 0.42f;

    private GlowShape() {
    }

    /**
     * The top-edge opacity for a picture whose top band averages this luma (0 black, 1 white):
     * full strength on a dark picture, fading down to {@link #BRIGHT_CAP} on a bright one.
     */
    static float peakFor(float topLuma) {
        if (topLuma <= DIM_LUMA) return PEAK_ALPHA;
        if (topLuma >= BRIGHT_LUMA) return BRIGHT_CAP;
        float t = (topLuma - DIM_LUMA) / (BRIGHT_LUMA - DIM_LUMA);
        return PEAK_ALPHA + (BRIGHT_CAP - PEAK_ALPHA) * t;
    }

    /** Opacity at screen fraction {@code t} (0 at the top edge, 1 at the bottom). */
    static float alphaAt(float t) {
        return alphaAt(t, PEAK_ALPHA);
    }

    /** Opacity of a glow that peaks at {@code peak} at screen fraction {@code t}. */
    static float alphaAt(float t, float peak) {
        if (t <= 0f) return peak;
        if (t >= HEIGHT_FRACTION) return 0f;
        float remaining = 1f - t / HEIGHT_FRACTION;
        return peak * (float) Math.pow(remaining, FALLOFF);
    }

    /** One column of the shape, white with only the alpha varying, for a screen this tall. */
    static int[] column(int height) {
        return column(height, PEAK_ALPHA);
    }

    /** One column of a glow that peaks at {@code peak}. */
    static int[] column(int height, float peak) {
        int[] column = new int[height];
        float last = Math.max(1, height - 1);
        for (int y = 0; y < height; y++) {
            int alpha = Math.round(255f * alphaAt(y / last, peak));
            column[y] = (alpha << 24) | 0x00FFFFFF;
        }
        return column;
    }

    /**
     * A sampled app colour, ready to tint the glow. A colour that is too dark to see is scaled up
     * as a whole, so it keeps its hue and saturation and only gains value; black stays black.
     */
    static int visibleColor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int floor = Math.round(MIN_COLOR_VALUE * 255f);
        if (max == 0 || max >= floor) return rgb & 0xFFFFFF;
        float scale = floor / (float) max;
        return (Math.min(255, Math.round(r * scale)) << 16)
                | (Math.min(255, Math.round(g * scale)) << 8)
                | Math.min(255, Math.round(b * scale));
    }
}
