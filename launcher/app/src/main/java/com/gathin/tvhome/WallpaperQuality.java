package com.gathin.tvhome;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which source size a wallpaper is downloaded at, from the panel and the app heap. Pure: the caller
 * supplies the UI size, the ActivityManager memory class and whether the TV is low-RAM.
 *
 * <p>A 1080p or smaller panel, a low-RAM device, or a panel without enough heap keeps the original
 * 2560x1440 download and the existing decode. A 4K panel with enough heap for the crossfade asks the
 * catalog for 3840x2160 and decodes at up to that size, never upscaling a smaller original.
 */
final class WallpaperQuality {

    static final int STANDARD_WIDTH = 2560;
    static final int STANDARD_HEIGHT = 1440;
    static final int UHD_WIDTH = 3840;
    static final int UHD_HEIGHT = 2160;

    /** A tier is only used when its crossfade fits in this share of the app heap. */
    static final float SAFE_HEAP_SHARE = 0.45f;

    /**
     * Commons originals measured at 3840 wide on 2026-11-06. A 3840 thumbnail exists only for these;
     * the other Commons photos stay at their 1920 thumbnail.
     */
    private static final Set<String> COMMONS_4K = new HashSet<>(Arrays.asList(
            "Tooba_Masjid.jpg",
            "The_Scenic_Hazuri_Bagh_Baradari.jpg",
            "Mohatta_Palace_2016.jpg",
            "Grand_Jamia_Masjid.jpg",
            "Frere_Hall_Karachi_City.jpg",
            "Mazar-e-Quaid_Karachi.jpg"));

    static final class Choice {
        final boolean uhd;
        final int sourceWidth, sourceHeight;

        Choice(boolean uhd, int sourceWidth, int sourceHeight) {
            this.uhd = uhd;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
        }
    }

    private WallpaperQuality() {
    }

    /** The panel, memory and low-RAM flag decide the tier once, at startup. */
    static Choice choose(int uiWidth, int uiHeight, int memoryClassMb, boolean lowRam) {
        boolean fourKPanel = uiWidth >= UHD_WIDTH && uiHeight >= UHD_HEIGHT;
        boolean heapFits = memoryClassMb > 0
                && peakCrossfadeBytes(uiWidth, uiHeight)
                <= (long) (memoryClassMb * 1024L * 1024L * SAFE_HEAP_SHARE);
        boolean uhd = fourKPanel && !lowRam && heapFits;
        return uhd
                ? new Choice(true, UHD_WIDTH, UHD_HEIGHT)
                : new Choice(false, STANDARD_WIDTH, STANDARD_HEIGHT);
    }

    /**
     * Two full-screen bitmaps for the crossfade plus the two half-size blur copies Backdrop keeps.
     * On a 3840x2160 panel this is 2 x 33.2 MB + 2 x 8.3 MB = 83 MB.
     */
    static long peakCrossfadeBytes(int width, int height) {
        long bitmap = (long) width * height * 4L;
        long blur = (long) Math.max(1, width / 2) * Math.max(1, height / 2) * 4L;
        return 2L * bitmap + 2L * blur;
    }

    /**
     * The bitmap size for a source decoded for this panel. The UHD tier never upscales a smaller
     * original: a 1920-wide Commons photo stays 1920 wide instead of being blown up to 3840.
     */
    static int[] decodeSize(int srcW, int srcH, int targetW, int targetH, boolean noUpscale) {
        return noUpscale
                ? WallpaperSizing.coverSizeNoUpscale(srcW, srcH, targetW, targetH)
                : WallpaperSizing.coverSize(srcW, srcH, targetW, targetH);
    }

    /**
     * The catalog URL at the chosen tier. Pexels and Unsplash change only w/h and keep their crop
     * and quality parameters; Commons changes 1920px- to 3840px- only for an original measured at
     * 3840 wide. Anything else, and the standard tier, returns the URL unchanged.
     */
    static String tierUrl(String standardUrl, boolean uhd) {
        if (!uhd || standardUrl == null) return standardUrl;
        if (standardUrl.contains("images.pexels.com/") || standardUrl.contains("images.unsplash.com/")) {
            return standardUrl.replace("w=2560", "w=3840").replace("h=1440", "h=2160");
        }
        if (standardUrl.contains("/wikipedia/commons/thumb/")) {
            int thumb = standardUrl.lastIndexOf("/1920px-");
            if (thumb < 0) return standardUrl;
            String file = standardUrl.substring(thumb + "/1920px-".length());
            if (COMMONS_4K.contains(file)) {
                return standardUrl.substring(0, thumb + 1) + "3840px-" + file;
            }
        }
        return standardUrl;
    }
}
