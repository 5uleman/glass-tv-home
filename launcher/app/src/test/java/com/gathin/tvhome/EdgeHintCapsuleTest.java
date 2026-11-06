package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The side capsule's placement: correct edge, clear of the hero text and the dock border. */
public class EdgeHintCapsuleTest {

    private static final float D = 2f;             // 320 dpi
    private static final float SCREEN = 1920f;
    private static final float TILE_LEFT = 110f;
    private static final float TILE_RIGHT = 1810f;
    private static final float TILE_TOP = 800f;
    private static final float TRAY_TOP = TILE_TOP - DockDecoration.TRAY_PAD_DP * D;
    /** The hero block's bottom sits 50 dp above the first dock tile (MainActivity.HERO_GAP_DP). */
    private static final float HERO_BOTTOM = TILE_TOP - 50f * D;
    private static final float W = 300f;
    private static final float H = 54f;            // a 27 dp capsule

    private static float[] place(boolean next, boolean rtl, boolean hero, float heroBottom) {
        return EdgeHintCapsule.place(SCREEN, TILE_LEFT, TILE_RIGHT, TRAY_TOP, heroBottom,
                hero, W, H, next, rtl, D);
    }

    @Test
    public void nextSitsOnTheLastTilesOuterEdgeAndPreviousOnTheFirsts() {
        assertEquals(TILE_RIGHT - W, place(true, false, true, HERO_BOTTOM)[0], 0.01f);
        assertEquals(TILE_LEFT, place(false, false, true, HERO_BOTTOM)[0], 0.01f);
        // In a right-to-left dock the physical edges mirror.
        assertEquals(TILE_LEFT, place(true, true, true, HERO_BOTTOM)[0], 0.01f);
        assertEquals(TILE_RIGHT - W, place(false, true, true, HERO_BOTTOM)[0], 0.01f);
    }

    @Test
    public void theCapsuleNeverTouchesTheDockTrayBorder() {
        for (boolean next : new boolean[]{false, true}) {
            float y = place(next, false, true, HERO_BOTTOM)[1];
            assertTrue("capsule bottom touches the tray", y + H <= TRAY_TOP - DOCK_GAP_PX() + 0.01f);
        }
    }

    @Test
    public void theCapsuleStaysClearOfTheHeroText() {
        float y = place(true, false, true, HERO_BOTTOM)[1];
        assertTrue("capsule top touches the hero text",
                y >= HERO_BOTTOM + EdgeHintCapsule.HERO_CLEAR_DP * D - 0.01f);
        assertTrue("there is a visible gap", y - HERO_BOTTOM >= 3 * D);
    }

    @Test
    public void withoutHeroTextTheCapsuleKeepsTheSameDockGap() {
        float withHero = place(true, false, true, HERO_BOTTOM)[1];
        float withoutHero = place(true, false, false, 0)[1];
        assertEquals(withHero, withoutHero, 0.01f);
    }

    @Test
    public void aTightBandNeverPushesTheCapsuleIntoTheTray() {
        float heroTooLow = TRAY_TOP - H;
        float y = place(true, false, true, heroTooLow)[1];
        assertTrue(y + H <= TRAY_TOP - DOCK_GAP_PX() + 0.01f);
    }

    @Test
    public void theCapsuleKeepsTheScreenMarginWhenATileEdgeIsOutsideIt() {
        float[] xy = EdgeHintCapsule.place(SCREEN, 4f, 304f, TRAY_TOP, HERO_BOTTOM, true,
                300f, H, false, false, D);
        assertTrue(xy[0] >= EdgeHintCapsule.SIDE_MARGIN_DP * D - 0.01f);
        float[] rightEdge = EdgeHintCapsule.place(SCREEN, SCREEN - 304f, SCREEN - 4f, TRAY_TOP,
                HERO_BOTTOM, true, 300f, H, true, false, D);
        assertTrue(rightEdge[0] + 300f <= SCREEN - EdgeHintCapsule.SIDE_MARGIN_DP * D + 0.01f);
    }

    private static float DOCK_GAP_PX() {
        return EdgeHintCapsule.DOCK_GAP_DP * D;
    }
}
