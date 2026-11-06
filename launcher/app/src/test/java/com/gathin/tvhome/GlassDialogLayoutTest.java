package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Screen 1920x1080 at 320 dpi (density 2): a 56dp margin leaves 856px. The panel carries 20dp top
 * and 18dp bottom padding, a 60dp header, a 14dp gap, a 46dp footer and a 12dp gap. Rows carry the
 * longest two-line subtitle: 72dp plus a 5dp gap. The real chrome is smaller than this model, so
 * the numbers here are the conservative case.
 */
public class GlassDialogLayoutTest {

    private static final float DENSITY = 2f;       // 320 dpi
    private static final int SCREEN_PX = 1080;
    private static final int MARGIN_DP = 56;
    private static final int PAD_TOP_PX = 40;      // 20dp
    private static final int PAD_BOTTOM_PX = 36;   // 18dp
    private static final int PAD_PX = PAD_TOP_PX + PAD_BOTTOM_PX;
    private static final int HEADER_PX = 120;      // 60dp
    private static final int LIST_GAP_PX = 28;     // 14dp
    private static final int FOOTER_PX = 92;       // 46dp
    private static final int FOOTER_GAP_PX = 24;   // 12dp
    private static final int ROW_PX = 144;         // 72dp, two-line subtitle
    private static final int ROW_GAP_PX = 10;      // 5dp
    private static final int RING_PX = 4;          // 2dp ring, both sides

    private static int maxPanel() {
        return GlassDialogLayout.maxPanelHeight(SCREEN_PX, DENSITY, MARGIN_DP);
    }

    private static int content(int rows) {
        return PAD_PX + HEADER_PX + LIST_GAP_PX + rows * (ROW_PX + ROW_GAP_PX)
                + FOOTER_GAP_PX + FOOTER_PX;
    }

    private static int viewportFor(int panel) {
        return GlassDialogLayout.listViewport(panel, PAD_PX, HEADER_PX, LIST_GAP_PX,
                FOOTER_PX, FOOTER_GAP_PX);
    }

    private static int listTop() {
        return PAD_TOP_PX + HEADER_PX + LIST_GAP_PX;
    }

    private static int footerTop(int panel) {
        return panel - PAD_BOTTOM_PX - FOOTER_PX;
    }

    private static void assertNoOverlap(int panel, int rows) {
        int viewport = viewportFor(panel);
        int listBottom = listTop() + viewport;
        assertTrue("rows=" + rows + " the list region has height", viewport > 0);
        assertFalse("rows=" + rows + " list must not touch the header",
                GlassDialogLayout.overlaps(listTop(), listBottom,
                        PAD_TOP_PX + HEADER_PX, footerTop(panel)));
        assertTrue("rows=" + rows + " list bottom is above the footer",
                listBottom <= footerTop(panel));
        assertEquals("rows=" + rows + " the panel fits the screen",
                Math.min(content(rows), maxPanel()), panel);
    }

    @Test
    public void wallpaperDialogWithSeventeenRowsKeepsTheFooterReachable() {
        int rows = 17;
        int panel = GlassDialogLayout.panelHeight(content(rows), maxPanel());
        assertEquals(maxPanel(), panel);
        int viewport = viewportFor(panel);
        assertTrue(viewport > 0);
        int listBottom = listTop() + viewport;
        assertTrue("the list never covers the footer", listBottom <= footerTop(panel));

        int contentHeight = rows * (ROW_PX + ROW_GAP_PX);
        int lastTop = (rows - 1) * (ROW_PX + ROW_GAP_PX);
        int target = GlassDialogLayout.scrollTarget(lastTop, lastTop + ROW_PX, 0, viewport,
                RING_PX, contentHeight);
        assertTrue("the last row plus ring is reachable",
                lastTop - RING_PX >= target
                        && lastTop + ROW_PX + RING_PX <= target + viewport);
    }

    @Test
    public void wallpaperDialogWithTwentyRowsKeepsTheFooterAndNewRowsReachable() {
        int rows = 20;
        int panel = GlassDialogLayout.panelHeight(content(rows), maxPanel());
        assertEquals(maxPanel(), panel);
        int viewport = viewportFor(panel);
        assertTrue(viewport > 0);
        int listBottom = listTop() + viewport;
        assertTrue("the list never covers the footer", listBottom <= footerTop(panel));

        int contentHeight = rows * (ROW_PX + ROW_GAP_PX);
        int lastTop = (rows - 1) * (ROW_PX + ROW_GAP_PX);
        int target = GlassDialogLayout.scrollTarget(lastTop, lastTop + ROW_PX, 0, viewport,
                RING_PX, contentHeight);
        assertTrue("the last row plus ring is reachable",
                lastTop - RING_PX >= target
                        && lastTop + ROW_PX + RING_PX <= target + viewport);

        // App Color Glow is the fourteenth row, directly under Image Title & Info.
        int glowTop = 13 * (ROW_PX + ROW_GAP_PX);
        int glowTarget = GlassDialogLayout.scrollTarget(glowTop, glowTop + ROW_PX, 0,
                viewport, RING_PX, contentHeight);
        assertTrue("the App Color Glow row plus ring is reachable",
                glowTop - RING_PX >= glowTarget
                        && glowTop + ROW_PX + RING_PX <= glowTarget + viewport);

        // The preview row sits under Weather Location, one above the last three rows.
        int previewTop = 16 * (ROW_PX + ROW_GAP_PX);
        int previewTarget = GlassDialogLayout.scrollTarget(previewTop, previewTop + ROW_PX, 0,
                viewport, RING_PX, contentHeight);
        assertTrue("the preview row plus ring is reachable",
                previewTop - RING_PX >= previewTarget
                        && previewTop + ROW_PX + RING_PX <= previewTarget + viewport);
    }

    @Test
    public void maximumPanelLeavesMarginsOnTheRealScreen() {
        assertEquals(856, maxPanel());
        assertEquals(SCREEN_PX - 2 * 112, maxPanel());
    }

    @Test
    public void oneThreeEightAndTwentyRowsNeverIntersectHeaderOrFooter() {
        for (int rows : new int[]{1, 3, 8, 20}) {
            int panel = GlassDialogLayout.panelHeight(content(rows), maxPanel());
            assertNoOverlap(panel, rows);
        }
    }

    @Test
    public void shortListsTakeTheirContentHeightAndLongListsAreCapped() {
        assertEquals(content(1), GlassDialogLayout.panelHeight(content(1), maxPanel()));
        assertEquals(content(3), GlassDialogLayout.panelHeight(content(3), maxPanel()));
        assertEquals(maxPanel(), GlassDialogLayout.panelHeight(content(8), maxPanel()));
        assertEquals(maxPanel(), GlassDialogLayout.panelHeight(content(20), maxPanel()));
    }

    @Test
    public void aCappedPanelLeavesLessThanTheFullListHeightToScroll() {
        int cappedViewport = viewportFor(maxPanel());
        int uncappedViewport = viewportFor(content(20));
        assertEquals(516, cappedViewport);
        assertTrue("the cap shortens the list viewport", cappedViewport < uncappedViewport);
        assertTrue(cappedViewport > 0);
        assertTrue("the viewport is still smaller than the list content",
                cappedViewport < 20 * (ROW_PX + ROW_GAP_PX));
    }

    @Test
    public void shortListsShowEveryRowWithoutScrolling() {
        assertEquals(154, viewportFor(GlassDialogLayout.panelHeight(content(1), maxPanel())));
        assertEquals(462, viewportFor(GlassDialogLayout.panelHeight(content(3), maxPanel())));
    }

    @Test
    public void focusedRowAtTheTopMiddleAndBottomStaysFullyInsideTheViewport() {
        int rows = 20;
        int viewport = viewportFor(maxPanel());
        int contentHeight = rows * (ROW_PX + ROW_GAP_PX);
        int maxScroll = contentHeight - viewport;

        int top = GlassDialogLayout.scrollTarget(0, ROW_PX, 0, viewport, RING_PX, contentHeight);
        assertEquals(0, top);

        int middleRowTop = 10 * (ROW_PX + ROW_GAP_PX);
        int middle = GlassDialogLayout.scrollTarget(middleRowTop, middleRowTop + ROW_PX,
                viewport / 2, viewport, RING_PX, contentHeight);
        assertTrue(middle >= 0 && middle <= maxScroll);
        assertTrue("middle row plus ring is inside the viewport",
                middleRowTop - RING_PX >= middle
                        && middleRowTop + ROW_PX + RING_PX <= middle + viewport);

        int lastTop = (rows - 1) * (ROW_PX + ROW_GAP_PX);
        int bottom = GlassDialogLayout.scrollTarget(lastTop, lastTop + ROW_PX, 0, viewport,
                RING_PX, contentHeight);
        assertTrue(bottom >= 0 && bottom <= maxScroll);
        assertTrue("last row plus ring is inside the viewport",
                lastTop - RING_PX >= bottom
                        && lastTop + ROW_PX + RING_PX <= bottom + viewport);
    }

    @Test
    public void aRowAlreadyVisibleDoesNotMoveTheScroll() {
        int viewport = 300;
        int contentHeight = 1000;
        int scrollY = 200;
        int rowTop = 260;
        assertEquals(scrollY, GlassDialogLayout.scrollTarget(rowTop, rowTop + 140, scrollY,
                viewport, RING_PX, contentHeight));
    }

    @Test
    public void aRowAboveTheViewportScrollsUpWithRingRoom() {
        int viewport = 300;
        int contentHeight = 1000;
        int scrollY = 400;
        int rowTop = 320;
        int target = GlassDialogLayout.scrollTarget(rowTop, rowTop + 140, scrollY, viewport,
                RING_PX, contentHeight);
        assertEquals(rowTop - RING_PX, target);
        assertTrue(rowTop - RING_PX >= target);
    }

    @Test
    public void theLastRowsBottomRingIsNotClippedAtTheViewportEdge() {
        int viewport = 300;
        int padTop = 4;   // scroll.setPadding(0, ringRoom, 0, ringRoom)
        int rows = 10;
        int contentHeight = rows * (ROW_PX + ROW_GAP_PX) + padTop;
        int lastTop = (rows - 1) * (ROW_PX + ROW_GAP_PX) + padTop;
        int target = GlassDialogLayout.scrollTarget(lastTop, lastTop + ROW_PX, 0, viewport,
                RING_PX, contentHeight);
        assertTrue("row top and ring are visible", lastTop - RING_PX >= target);
        assertTrue("row bottom and ring are visible",
                lastTop + ROW_PX + RING_PX <= target + viewport);
    }
}
