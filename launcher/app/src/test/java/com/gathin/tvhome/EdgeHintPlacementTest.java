package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EdgeHintPlacementTest {

    private static final float DP = 1.5f;
    private static final int SCREEN_W = 1920;
    private static final float TRAY_TOP = 830f;
    private static final float TOAST_W = 300f;
    private static final float TOAST_H = 57f;

    @Test
    public void centredAboveTheDockWhenTheHeroDoesNotReach() {
        // "YouTube" is short: it ends well before the centred toast.
        EdgeHintPlacement.Bounds hero = new EdgeHintPlacement.Bounds(82f, 620f, 480f, 755f);
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, hero, TOAST_W, TOAST_H, DP);
        assertEquals((SCREEN_W - TOAST_W) / 2f, xy[0], 0.01f);
        assertEquals(TRAY_TOP - EdgeHintPlacement.DOCK_GAP_DP * DP, xy[1] + TOAST_H, 0.01f);
    }

    @Test
    public void liftsAboveTheHeroWhenTheirRectanglesWouldTouch() {
        EdgeHintPlacement.Bounds hero = new EdgeHintPlacement.Bounds(82f, 620f, 1100f, 755f);
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, hero, TOAST_W, TOAST_H, DP);
        assertEquals((SCREEN_W - TOAST_W) / 2f, xy[0], 0.01f);
        assertEquals(hero.top - EdgeHintPlacement.HERO_GAP_DP * DP, xy[1] + TOAST_H, 0.01f);
    }

    @Test
    public void liftsForADescriptionThatRunsIntoTheToast() {
        // The description is narrower than the name but can still reach the toast's left edge.
        EdgeHintPlacement.Bounds hero = new EdgeHintPlacement.Bounds(82f, 620f, 900f, 780f);
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, hero, TOAST_W, TOAST_H, DP);
        assertEquals(hero.top - EdgeHintPlacement.HERO_GAP_DP * DP, xy[1] + TOAST_H, 0.01f);
    }

    @Test
    public void noLiftWhenTheHeroEndsJustShortOfTheToast() {
        float toastLeft = (SCREEN_W - TOAST_W) / 2f;
        float heroRight = toastLeft - EdgeHintPlacement.HERO_GAP_DP * DP - 1f;
        EdgeHintPlacement.Bounds hero = new EdgeHintPlacement.Bounds(82f, 620f, heroRight, 755f);
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, hero, TOAST_W, TOAST_H, DP);
        assertEquals(TRAY_TOP - EdgeHintPlacement.DOCK_GAP_DP * DP, xy[1] + TOAST_H, 0.01f);
    }

    @Test
    public void staysInsideTheScreenWhenThereIsNoHero() {
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, null, TOAST_W, TOAST_H, DP);
        assertEquals((SCREEN_W - TOAST_W) / 2f, xy[0], 0.01f);
        assertEquals(TRAY_TOP - EdgeHintPlacement.DOCK_GAP_DP * DP, xy[1] + TOAST_H, 0.01f);
    }

    @Test
    public void clampsAWideToastToTheSideMargin() {
        float wide = SCREEN_W + 100f;
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, null, wide, TOAST_H, DP);
        assertEquals(EdgeHintPlacement.SIDE_MARGIN_DP * DP, xy[0], 0.01f);
    }

    @Test
    public void neverGoesAboveTheTopMargin() {
        EdgeHintPlacement.Bounds hero = new EdgeHintPlacement.Bounds(0f, 40f, 1920f, 200f);
        float[] xy = EdgeHintPlacement.place(SCREEN_W, TRAY_TOP, hero, TOAST_W, TOAST_H, DP);
        assertTrue(xy[1] >= EdgeHintPlacement.SIDE_MARGIN_DP * DP);
    }
}
