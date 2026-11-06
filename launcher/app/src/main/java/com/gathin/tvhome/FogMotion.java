package com.gathin.tvhome;

/**
 * How the fog moves: named per-layer numbers and the pure offsets they produce. Speeds are px/s
 * and sway is in px on a 1920x1080 panel; the renderer scales both to the real screen. The drift is
 * a function of elapsed time, never of a frame count, so it is the same at any frame rate, and the
 * sway and breathing are sines with different phases so the layers never move as one sheet.
 */
final class FogMotion {

    /** The panel the speeds and sway amplitudes are written for. */
    static final float REFERENCE_W = 1920f;
    static final float REFERENCE_H = 1080f;

    /** One layer's motion. Speed is signed: positive drifts right, negative left. */
    static final class Layer {
        final float speedPx;
        final float swayPx;
        final float swaySeconds;
        final float swayPhase;
        final float breath;
        final float breathSeconds;
        final float breathPhase;

        Layer(float speedPx, float swayPx, float swaySeconds, float swayPhase,
              float breath, float breathSeconds, float breathPhase) {
            this.speedPx = speedPx;
            this.swayPx = swayPx;
            this.swaySeconds = swaySeconds;
            this.swayPhase = swayPhase;
            this.breath = breath;
            this.breathSeconds = breathSeconds;
            this.breathPhase = breathPhase;
        }
    }

    /**
     * Light fog, far to near: a slow wide layer and a quick close one crossing in opposite
     * directions, so the wisps open and close.
     */
    static final Layer[] LIGHT = {
            new Layer(+10f, 14f, 13.0f, 0.0f, 0.10f, 8.5f, 0.0f),
            new Layer(-24f, 16f, 9.5f, 2.1f, 0.10f, 7.2f, 1.4f),
    };

    /** Heavy fog adds a middle layer running against both, and is a little livelier overall. */
    static final Layer[] HEAVY = {
            new Layer(+12f, 16f, 12.0f, 0.6f, 0.11f, 8.0f, 0.3f),
            new Layer(-18f, 17f, 10.0f, 2.6f, 0.11f, 7.0f, 2.2f),
            new Layer(+27f, 18f, 9.0f, 4.2f, 0.11f, 7.6f, 4.0f),
    };

    private FogMotion() {
    }

    /** The layer set for a layer count: two for light fog, three for heavy. */
    static Layer[] layers(int count) {
        return count == FogLook.LAYERS_HEAVY ? HEAVY : LIGHT;
    }

    /** Signed horizontal travel after {@code timeSec}; the renderer scales it by screen width. */
    static float driftPx(Layer layer, double timeSec) {
        return (float) (layer.speedPx * timeSec);
    }

    /** Wraps {@code value} into [0, period): the shader repeats, so the wrap is invisible. */
    static float wrap(float value, float period) {
        if (period <= 0f) return 0f;
        float r = value % period;
        return r < 0f ? r + period : r;
    }

    /** Vertical sway, +-swayPx, for the layer's own period and phase. */
    static float swayPx(Layer layer, double timeSec) {
        return (float) (layer.swayPx
                * Math.sin(2 * Math.PI * timeSec / layer.swaySeconds + layer.swayPhase));
    }

    /** Multiplier around 1 for the layer alpha, +-breath, for the layer's own period and phase. */
    static float breathScale(Layer layer, double timeSec) {
        return (float) (1.0 + layer.breath
                * Math.sin(2 * Math.PI * timeSec / layer.breathSeconds + layer.breathPhase));
    }
}
