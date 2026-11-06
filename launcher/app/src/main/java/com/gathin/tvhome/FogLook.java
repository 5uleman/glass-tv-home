package com.gathin.tvhome;

/**
 * The fog's optical numbers, shared by the renderer, the tests and the off-device check images:
 * how many layers each fog thickness draws, how opaque they get, how much of the lower screen they
 * cover and how each layer is shaped. The layers are drawn one over the other with normal blending,
 * so their alphas add up to the summed cap at full texture density. Pure: no Android classes.
 */
final class FogLook {

    /** Neutral light grey-white: unlike pure white it still changes a bright picture. */
    static final int RGB = 0x00F2F2F2;

    /** The summed (composited) layer cap for the lightest and the heaviest fog. */
    static final float LIGHT_CAP = 0.45f;
    static final float HEAVY_CAP = 0.80f;
    /** Share of the screen height the fog reaches, bottom-up. */
    static final float LIGHT_COVERAGE = 0.45f;
    static final float HEAVY_COVERAGE = 0.65f;
    /** Fog is a little lighter at night, matching the other effects. */
    static final float NIGHT_SCALE = 0.82f;

    /** Light fog drifts as two layers, heavy fog as three. */
    static final int LAYERS_LIGHT = 2;
    static final int LAYERS_HEAVY = 3;
    /** From this density up the third, middle layer joins. */
    static final float HEAVY_LAYER_DENSITY = 0.70f;

    /** Share of the summed cap each layer gets, back to front; each set sums to one. */
    private static final float[] LIGHT_WEIGHTS = {0.62f, 0.38f};
    private static final float[] HEAVY_WEIGHTS = {0.45f, 0.33f, 0.22f};
    /** Layer width as a multiple of the screen; wider layers look further away. */
    private static final float[] LIGHT_WIDTHS = {1.65f, 1.00f};
    private static final float[] HEAVY_WIDTHS = {1.65f, 1.30f, 1.00f};
    /** Where each layer starts inside the covered band; each one reaches the bottom. */
    private static final float[] LIGHT_TOPS = {0f, 0.34f};
    private static final float[] HEAVY_TOPS = {0f, 0.18f, 0.42f};

    private FogLook() {
    }

    /** How many layers this density draws: two for light fog, three from {@link #HEAVY_LAYER_DENSITY}. */
    static int layerCount(float density) {
        return density >= HEAVY_LAYER_DENSITY ? LAYERS_HEAVY : LAYERS_LIGHT;
    }

    /** One of {@link #LIGHT_WEIGHTS} or {@link #HEAVY_WEIGHTS}. Never null for 2 or 3. */
    static float[] weights(int count) {
        return count == LAYERS_HEAVY ? HEAVY_WEIGHTS : LIGHT_WEIGHTS;
    }

    /** One of {@link #LIGHT_WIDTHS} or {@link #HEAVY_WIDTHS}. */
    static float[] widths(int count) {
        return count == LAYERS_HEAVY ? HEAVY_WIDTHS : LIGHT_WIDTHS;
    }

    /** One of {@link #LIGHT_TOPS} or {@link #HEAVY_TOPS}. */
    static float[] topShares(int count) {
        return count == LAYERS_HEAVY ? HEAVY_TOPS : LIGHT_TOPS;
    }

    /** Maps the classifier's fog density (0.35 at the lightest to 1.0 at 200 m) to the summed cap. */
    static float opacityCap(float density) {
        float t = densityT(density);
        return LIGHT_CAP + (HEAVY_CAP - LIGHT_CAP) * t;
    }

    /** The covered share of the screen: about 45% for light fog, 65% for heavy. */
    static float coverage(float density) {
        float t = densityT(density);
        return LIGHT_COVERAGE + (HEAVY_COVERAGE - LIGHT_COVERAGE) * t;
    }

    /** The screen y where the fog begins. */
    static float bandTop(float height, float density) {
        return height * (1f - coverage(density));
    }

    /** The screen y where one layer begins. */
    static float layerTop(float height, float density, int layer) {
        float bandTop = bandTop(height, density);
        float bandHeight = height - bandTop;
        return bandTop + bandHeight * topShares(layerCount(density))[layer];
    }

    /** The alpha of one layer at this fog density. */
    static int layerAlpha(float density, int layer, boolean daytime) {
        float target = opacityCap(density) * (daytime ? 1f : NIGHT_SCALE);
        return Math.round(255f * layerFraction(target, layerCount(density), layer));
    }

    /**
     * The share of one layer's alpha. Normal blending is not additive, so the layer alphas are
     * solved together: their composite reaches {@code target} when the texture is fully opaque.
     */
    static float layerFraction(float target, int count, int layer) {
        float lo = 0f, hi = 4f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) / 2f;
            if (composite(mid, count) < target) lo = mid;
            else hi = mid;
        }
        return ((lo + hi) / 2f) * weights(count)[layer];
    }

    /** What {@code count} layers scaled by {@code k} add up to with normal blending. */
    static float composite(float k, int count) {
        float clear = 1f;
        for (float weight : weights(count)) clear *= 1f - Math.min(1f, k * weight);
        return 1f - clear;
    }

    private static float densityT(float density) {
        float d = density;
        if (d <= WeatherEffects.FOG_LIGHT_DENSITY) return 0f;
        if (d >= 1f) return 1f;
        return (d - WeatherEffects.FOG_LIGHT_DENSITY) / (1f - WeatherEffects.FOG_LIGHT_DENSITY);
    }
}
