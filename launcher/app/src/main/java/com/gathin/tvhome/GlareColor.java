package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.LruCache;

/**
 * One soft accent sampled from the wallpaper for the glass edges. It mirrors the scoring the App
 * Color Glow uses on app artwork (saturated, not too dark, average as a fallback), but reads an
 * already-decoded bitmap instead of drawing a Drawable into a 12x12 stamp.
 *
 * <p>Sampling runs once per picture, then the colour is cached by file identity. A dark, gray or
 * unreadable picture gets a neutral shade picked from the picture's mean brightness instead of the
 * classic blue; the blue is only ever the tint in Classic mode.
 */
final class GlareColor {

    /** The fixed blue of the classic rim; GlassRim keeps its original palette when this is active. */
    static final int CLASSIC = 0xFF67E8F9;
    /** Auto is the default; a missing key (an upgrade) means Auto. */
    static final String AUTO_KEY = "glare_color_auto";

    private static LruCache<String, Integer> cache;
    private static volatile int active = CLASSIC;

    /** A picture with no usable hue gets this shade on dark art: soft light gray-white. */
    static final int NEUTRAL_LIGHT = 0xFFDDE3EA;
    /** ... and this one on bright art, where a light edge would vanish: soft mid gray. */
    static final int NEUTRAL_MID = 0xFF97A0AA;
    /** Mean luma (0 to 1) of the picture last sampled, which picks the neutral shade. */
    private static volatile float sampledLuma = 0.5f;

    private GlareColor() {}

    private static LruCache<String, Integer> cache() {
        if (cache == null) cache = new LruCache<>(24);
        return cache;
    }

    static int current() {
        return active;
    }

    static void setActive(int color) {
        active = color == 0 ? CLASSIC : color;
    }

    static boolean isClassic() {
        return active == CLASSIC;
    }

    /**
     * The neutral edge for a picture of this mean brightness (0 black to 1 white): the light shade
     * on dark art, the mid shade on bright art, blended between at 0.25 and 0.75.
     */
    static int neutral(float luma) {
        float t = (luma - 0.25f) / 0.5f;
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return rgb(Math.round(0xDD + (0x97 - 0xDD) * t),
                Math.round(0xE3 + (0xA0 - 0xE3) * t),
                Math.round(0xEA + (0xAA - 0xEA) * t));
    }

    /** Packs an opaque colour without the platform helper, so the fallback rule stays testable. */
    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** The accent for this picture, from the cache when it has been seen before. */
    static int accentFor(Bitmap bmp, String key) {
        if (bmp == null || bmp.isRecycled()) return NEUTRAL_LIGHT;   // no picture: the dark-screen shade
        if (key != null) {
            Integer hit = cache().get(key);
            if (hit != null) return hit;
        }
        int color = sample(bmp);
        if (color == 0) color = neutral(sampledLuma);   // gray or dark art: neutral, never blue
        if (key != null) cache().put(key, color);
        return color;
    }

    /** {@code 0} when the picture is too dark or too gray to give a legible tint. */
    private static int sample(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0) return 0;
        final int cols = 24;
        final int rows = 14;
        float[] hsv = new float[3];
        int best = 0;
        float bestScore = -1f;
        double sumValue = 0;
        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;
        sampledLuma = 0.5f;
        try {
            for (int row = 0; row < rows; row++) {
                int y = Math.min(h - 1, (int) ((row + 0.5f) * h / rows));
                for (int col = 0; col < cols; col++) {
                    int x = Math.min(w - 1, (int) ((col + 0.5f) * w / cols));
                    int p = bmp.getPixel(x, y);
                    Color.colorToHSV(p, hsv);
                    sumValue += hsv[2];
                    sumR += Color.red(p);
                    sumG += Color.green(p);
                    sumB += Color.blue(p);
                    count++;
                    float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                    if (score > bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        } catch (Exception | OutOfMemoryError ignored) {
            sampledLuma = 0.5f;
            return 0;   // a hardware bitmap or a decoder quirk: fall back to the neutral shade
        }
        if (count == 0) return 0;
        sampledLuma = (float) ((0.2126 * sumR + 0.7152 * sumG + 0.0722 * sumB) / count / 255.0);
        if (bestScore < 0.25f || sumValue / count < 0.15) return 0;
        Color.colorToHSV(best, hsv);
        if (!normalize(hsv)) return 0;
        return Color.HSVToColor(hsv);
    }

    /**
     * Bright and soft: readable over a night photo, still a tint over a white sky. {@code false}
     * when the colour is too gray to read as a tint.
     */
    static boolean normalize(float[] hsv) {
        if (hsv[1] < 0.18f) return false;
        hsv[1] = Math.max(0.25f, Math.min(0.55f, hsv[1]));
        hsv[2] = Math.max(0.88f, hsv[2]);
        return true;
    }

    static int lighten(int color, float amount) {
        return Color.rgb(
                Math.round(Color.red(color) + (255 - Color.red(color)) * amount),
                Math.round(Color.green(color) + (255 - Color.green(color)) * amount),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount));
    }

    static int darken(int color, float amount) {
        return Color.rgb(Math.round(Color.red(color) * (1f - amount)),
                Math.round(Color.green(color) * (1f - amount)),
                Math.round(Color.blue(color) * (1f - amount)));
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
