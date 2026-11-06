package com.gathin.tvhome;

/**
 * Where the edge-switch toast sits in the root: centred over the dock tray, 20 dp above its top
 * border. The hero text is 50 dp above the dock, so the toast cannot fit between them; when the
 * two rectangles would touch, the toast moves above the hero block instead. Pure geometry, so the
 * view code only has to read the rectangles and the arithmetic is unit tested.
 */
final class EdgeHintPlacement {

    /** Clear air between the toast and the dock tray's top border. */
    static final float DOCK_GAP_DP = 20f;
    /** Clear air between the toast and the hero text when the toast has to move above it. */
    static final float HERO_GAP_DP = 14f;
    /** The screen margin the toast never crosses, at the sides and at the top. */
    static final float SIDE_MARGIN_DP = 16f;

    /** A rectangle in root coordinates. Null stands for a hero block that is not showing. */
    static final class Bounds {
        final float left, top, right, bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private EdgeHintPlacement() {
    }

    /**
     * @param screenW the root's width
     * @param trayTop the dock tray's top border
     * @param hero    the title block, or null when it is not showing
     * @return {x, y} of the toast's top-left corner in root coordinates
     */
    static float[] place(int screenW, float trayTop, Bounds hero,
                         float toastW, float toastH, float density) {
        float margin = SIDE_MARGIN_DP * density;
        float bottom = trayTop - DOCK_GAP_DP * density;
        float top = bottom - toastH;

        float x = (screenW - toastW) / 2f;
        x = Math.max(margin, Math.min(x, screenW - toastW - margin));

        if (hero != null) {
            float clear = HERO_GAP_DP * density;
            boolean overlaps = top < hero.bottom + clear && bottom > hero.top - clear
                    && x < hero.right + clear && x + toastW > hero.left - clear;
            if (overlaps) top = hero.top - clear - toastH;
        }
        return new float[]{x, Math.max(margin, top)};
    }
}
