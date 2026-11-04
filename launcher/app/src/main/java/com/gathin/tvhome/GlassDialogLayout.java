package com.gathin.tvhome;

/**
 * The pure arithmetic behind the glass dialog: how tall the panel may be on the real screen, how
 * much of it the fixed header and footer leave for the scrolling list, and where to scroll so a
 * focused row and its ring are fully visible. Kept free of Android classes so the invariants are
 * unit tested on the JVM.
 */
final class GlassDialogLayout {

    private GlassDialogLayout() {
    }

    /** Screen height minus the top and bottom margin the dialog must leave. */
    static int maxPanelHeight(int screenHeightPx, float density, int marginDp) {
        return Math.max(0, screenHeightPx - Math.round(2 * marginDp * density));
    }

    /** The panel is as tall as its content, never taller than the screen allows. */
    static int panelHeight(int desiredContentPx, int maxPanelPx) {
        return Math.max(0, Math.min(desiredContentPx, maxPanelPx));
    }

    /** Height left for the scrolling list between the fixed header and footer. */
    static int listViewport(int panelPx, int headerPx, int listTopGapPx,
                            int footerPx, int footerTopGapPx) {
        return Math.max(0, panelPx - headerPx - listTopGapPx - footerPx - footerTopGapPx);
    }

    /**
     * Scroll offset that keeps {@code [rowTop, rowBottom]} plus {@code ringPx} on both sides inside
     * the viewport. Returns the current offset when the row already fits.
     */
    static int scrollTarget(int rowTop, int rowBottom, int scrollY, int viewportPx,
                            int ringPx, int contentPx) {
        int target = scrollY;
        if (rowTop - ringPx < scrollY) {
            target = rowTop - ringPx;
        } else if (rowBottom + ringPx > scrollY + viewportPx) {
            target = rowBottom + ringPx - viewportPx;
        }
        int maxScroll = Math.max(0, contentPx - viewportPx);
        return Math.max(0, Math.min(target, maxScroll));
    }

    /** True when the list viewport touches the header or the footer. */
    static boolean overlaps(int listTop, int listBottom, int headerBottom, int footerTop) {
        return listTop < headerBottom || listBottom > footerTop;
    }
}
