package com.gathin.tvhome;

/**
 * The bitmap size a wallpaper is decoded to: the smallest size that still covers the screen,
 * with the source aspect ratio kept so the centre-crop never stretches the picture. Kept free of
 * Android classes so the arithmetic is unit tested.
 */
final class WallpaperSizing {

    private WallpaperSizing() {
    }

    /**
     * Returns {width, height} that cover {@code targetW x targetH} at the source's aspect ratio.
     * A 2560x1440 source on a 1080p TV gives exactly 1920x1080; a 3:2 source gives 1920x1280, so
     * the extra height is cropped by the view instead of squeezing the picture.
     */
    static int[] coverSize(int srcW, int srcH, int targetW, int targetH) {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) {
            return new int[]{Math.max(1, targetW), Math.max(1, targetH)};
        }
        double scale = Math.max(targetW / (double) srcW, targetH / (double) srcH);
        return new int[]{
                Math.max(1, (int) Math.round(srcW * scale)),
                Math.max(1, (int) Math.round(srcH * scale))
        };
    }

    /**
     * The same cover size, but never larger than the source: the UHD tier keeps a smaller original
     * at its own resolution instead of blowing it up to 3840. A source that already covers the
     * screen is returned unchanged.
     */
    static int[] coverSizeNoUpscale(int srcW, int srcH, int targetW, int targetH) {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) {
            return new int[]{Math.max(1, targetW), Math.max(1, targetH)};
        }
        double cover = Math.max(targetW / (double) srcW, targetH / (double) srcH);
        double scale = Math.min(1.0, cover);
        return new int[]{
                Math.max(1, (int) Math.round(srcW * scale)),
                Math.max(1, (int) Math.round(srcH * scale))
        };
    }
}
