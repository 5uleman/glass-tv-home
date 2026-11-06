package com.gathin.tvhome;

/**
 * The fog's optical numbers, shared by the renderer, the tests and the off-device check images:
 * how opaque the three layers get, how much of the lower screen they cover and how fast they drift.
 * The layers are drawn one over the other with normal blending, so their alphas add up to the
 * summed cap at full texture density. Pure: no Android classes.
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

    static final int LAYERS = 3;
    /** Share of the summed cap each layer gets, back to front; together they sum to one. */
    static final float[] WEIGHTS = {0.45f, 0.33f, 0.22f};
    /** Seconds for one screen width of drift; slow, but still visibly moving. */
    static final float[] SECONDS = {130f, 100f, 70f};
    /** Layer width as a multiple of the screen; wider layers look further away. */
    static final float[] WIDTHS = {1.65f, 1.30f, 1.00f};
    /** Where each layer starts inside the covered band; each one reaches the bottom. */
    static final float[] TOPS_IN_BAND = {0f, 0.18f, 0.42f};

    private FogLook() {
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
        return bandTop + bandHeight * TOPS_IN_BAND[layer];
    }

    /** The alpha of one layer at this fog density. */
    static int layerAlpha(float density, int layer, boolean daytime) {
        float target = opacityCap(density) * (daytime ? 1f : NIGHT_SCALE);
        return Math.round(255f * layerFraction(target, layer));
    }

    /**
     * Normal blending is not additive, so the three layer alphas are solved together: their
     * composite reaches {@code target} when the texture is fully opaque there.
     */
    static float layerFraction(float target, int layer) {
        float lo = 0f, hi = 4f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) / 2f;
            if (composite(mid) < target) lo = mid;
            else hi = mid;
        }
        return ((lo + hi) / 2f) * WEIGHTS[layer];
    }

    /** What three layers scaled by {@code k} add up to with normal blending. */
    static float composite(float k) {
        float clear = 1f;
        for (float weight : WEIGHTS) clear *= 1f - Math.min(1f, k * weight);
        return 1f - clear;
    }

    private static float densityT(float density) {
        float d = density;
        if (d <= WeatherEffects.FOG_LIGHT_DENSITY) return 0f;
        if (d >= 1f) return 1f;
        return (d - WeatherEffects.FOG_LIGHT_DENSITY) / (1f - WeatherEffects.FOG_LIGHT_DENSITY);
    }
}
