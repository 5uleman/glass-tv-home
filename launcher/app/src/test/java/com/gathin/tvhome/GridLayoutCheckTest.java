package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The tile geometry at 1920x1080 with the two supported column counts. The grid's inner width is
 * the screen minus the 56dp side padding; a focused tile is scaled 1.07 and must still fit its cell.
 */
public class GridLayoutCheckTest {

    private static final float DENSITY = 2f;
    private static final int SCREEN_W = 1920;
    private static final int INNER = SCREEN_W - 2 * (int) (56 * DENSITY);

    @Test
    public void fourColumnsKeepsTheDesignTileSize() {
        int w = AppAdapter.tileWidthPx(INNER, 4, DENSITY);
        assertEquals(360, w);                       // 180dp
        assertEquals(202, AppAdapter.tileHeightPx(w));
        assertFitsCell(INNER / 4, w);
    }

    @Test
    public void fiveColumnsShrinksTheTileToFitFiveCells() {
        int w = AppAdapter.tileWidthPx(INNER, 5, DENSITY);
        assertEquals(283, w);                       // 141.5dp
        assertEquals(159, AppAdapter.tileHeightPx(w));
        assertFitsCell(INNER / 5, w);
        assertTrue("five columns make a smaller tile than four", w < 360);
        assertTrue("never below the 80dp floor", w >= 80 * DENSITY);
    }

    @Test
    public void theDockTrayStaysInsideTheContentWidth() {
        for (int columns : new int[]{4, 5}) {
            int cell = INNER / columns;
            int w = AppAdapter.tileWidthPx(INNER, columns, DENSITY);
            // Cards are centred in their cells; the tray is their union plus 14dp of glass.
            float cards = columns * cell - (cell - w);
            float tray = cards + 2 * 14 * DENSITY;
            assertTrue("columns=" + columns + " tray " + tray + " fits inner " + INNER,
                    tray <= INNER);
        }
    }

    private static void assertFitsCell(int cell, int tileWidth) {
        assertTrue("focused tile " + tileWidth + " in cell " + cell,
                tileWidth * AppAdapter.FOCUS_SCALE <= cell);
    }
}
