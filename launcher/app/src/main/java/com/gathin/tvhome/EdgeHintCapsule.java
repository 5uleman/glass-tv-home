package com.gathin.tvhome;

/**
 * Where the compact Next/Previous wallpaper capsule sits: at the physical outer edge of the last
 * dock tile (next) or the first (previous), in the band between the hero text above and the dock
 * tray below. Pure geometry, so the clearances it promises are unit tested.
 */
final class EdgeHintCapsule {

    /** Clear air between the capsule's bottom and the dock tray's top border. */
    static final float DOCK_GAP_DP = 4f;
    /** Clear air between the capsule's top and the hero text block's bottom. */
    static final float HERO_CLEAR_DP = 3f;
    /** The screen margin the capsule never crosses. */
    static final float SIDE_MARGIN_DP = 8f;

    private EdgeHintCapsule() {
    }

    /**
     * @param screenW    the root's width
     * @param tileLeft   the first/last dock tile's left edge in root coordinates
     * @param tileRight  the same tile's right edge
     * @param trayTop    the dock tray's top border
     * @param heroBottom the hero text block's bottom, ignored when {@code hasHero} is false
     * @param next       true for the last tile's outer edge, false for the first tile's
     * @param rtl        whether the dock runs right to left
     * @return {x, y} of the capsule's top-left corner in root coordinates
     */
    static float[] place(float screenW, float tileLeft, float tileRight, float trayTop,
                         float heroBottom, boolean hasHero, float w, float h,
                         boolean next, boolean rtl, float density) {
        float margin = SIDE_MARGIN_DP * density;
        float bottom = trayTop - DOCK_GAP_DP * density;
        float top = bottom - h;
        if (hasHero) top = Math.max(top, heroBottom + HERO_CLEAR_DP * density);
        // If the two never fit, the dock border is the one that is never crossed.
        top = Math.min(top, bottom - h);

        float x;
        if (next) x = rtl ? tileLeft : tileRight - w;
        else x = rtl ? tileRight - w : tileLeft;
        x = Math.max(margin, Math.min(x, screenW - w - margin));
        return new float[]{x, Math.max(margin, top)};
    }
}
