package com.gathin.tvhome;

/**
 * The size maths for normalising a user's wallpaper to the screen. Kept free of Android classes so
 * the orientation swap, the low-resolution rule, the absurd-size limits, the decode sample and the
 * cache key are unit tested.
 *
 * <p>Normalisation turns any source (phone portrait, 4:3, panorama, PNG) into one opaque 16:9 picture
 * at the screen's size, centre-cropped, the way the catalog's 16:9 photos already arrive.
 */
final class UploadSizing {

    /** Below this, the picture is not upscaled to the screen: its own crop is the output. */
    static final int LOW_RES_WIDTH = 1280;
    static final int LOW_RES_HEIGHT = 720;

    /** Beyond these, the source is treated as absurd: one more sample step, same cover fit. */
    static final long MAX_PIXELS = 100_000_000L;
    static final float MAX_RATIO = 4f;

    /** The API 26-27 fallback decodes with BitmapFactory; keep that transient bitmap this small. */
    static final long FALLBACK_MAX_PIXELS = 8_000_000L;

    static final int MAX_SAMPLE = 64;

    private UploadSizing() {
    }

    /** EXIF orientations 5-8 turn the picture a quarter turn, so width and height swap. */
    static boolean swapsAxes(int exifOrientation) {
        return exifOrientation >= 5 && exifOrientation <= 8;
    }

    static int[] orientedSize(int width, int height, int exifOrientation) {
        if (width <= 0 || height <= 0) return new int[]{1, 1};
        return swapsAxes(exifOrientation) ? new int[]{height, width} : new int[]{width, height};
    }

    static boolean isLowRes(int width, int height) {
        return width < LOW_RES_WIDTH || height < LOW_RES_HEIGHT;
    }

    /** More than 100 MP, or a longer side over four times the shorter one. */
    static boolean isExtreme(int width, int height) {
        if ((long) width * height > MAX_PIXELS) return true;
        int shorter = Math.min(width, height), longer = Math.max(width, height);
        return shorter > 0 && longer / (float) shorter > MAX_RATIO;
    }

    /**
     * The final 16:9 size. A source that already has enough pixels becomes exactly the UI size; a
     * low-resolution source is cropped at its own scale instead of being enlarged for no detail.
     */
    static int[] outputSize(int srcW, int srcH, int uiW, int uiH) {
        if (srcW <= 0 || srcH <= 0 || uiW <= 0 || uiH <= 0) {
            return new int[]{Math.max(1, uiW), Math.max(1, uiH)};
        }
        if (!isLowRes(srcW, srcH)) return new int[]{uiW, uiH};
        if ((long) srcW * 9L > (long) srcH * 16L) {
            // Wider than 16:9: keep the height and crop the sides.
            return new int[]{Math.max(1, Math.round(srcH * 16f / 9f)), srcH};
        }
        // Taller than 16:9: keep the width and crop top and bottom.
        return new int[]{srcW, Math.max(1, Math.round(srcW * 9f / 16f))};
    }

    /**
     * The size the decoder aims at before the centre crop: the output for a normal picture, the
     * source itself for a low-resolution one (no upscaling), and the cover size when the source is
     * wider or taller than the screen.
     */
    static int[] coverSize(int srcW, int srcH, int outW, int outH) {
        if (srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) {
            return new int[]{Math.max(1, outW), Math.max(1, outH)};
        }
        if (isLowRes(srcW, srcH)) return new int[]{srcW, srcH};
        double scale = Math.max(outW / (double) srcW, outH / (double) srcH);
        return new int[]{
                Math.max(1, (int) Math.round(srcW * scale)),
                Math.max(1, (int) Math.round(srcH * scale))
        };
    }

    /**
     * The power-of-two sample for BitmapFactory: the largest that still covers {@code need}. An
     * absurd source takes one step further, and the decoded bitmap is kept under the fallback
     * budget, which is what stops a 50 MP phone photo from allocating 200 MB.
     */
    static int sampleSize(int srcW, int srcH, int needW, int needH, boolean extreme) {
        if (srcW <= 0 || srcH <= 0 || needW <= 0 || needH <= 0) return 1;
        int sample = 1;
        while (sample < MAX_SAMPLE
                && srcW / (sample * 2) >= needW
                && srcH / (sample * 2) >= needH) {
            sample *= 2;
        }
        if (extreme && sample < MAX_SAMPLE) sample *= 2;
        while (sample < MAX_SAMPLE
                && (long) (srcW / sample) * (srcH / sample) > FALLBACK_MAX_PIXELS) {
            sample *= 2;
        }
        return sample;
    }

    /** A stable identity for one uploaded file: its name, byte size and modification time. */
    static String identity(String name, long length, long modified) {
        String basis = name + "|" + length + "|" + modified;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            final char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                int b = digest[i] & 0xFF;
                hex[i * 2] = digits[b >>> 4];
                hex[i * 2 + 1] = digits[b & 0x0F];
            }
            return new String(hex);
        } catch (Exception e) {
            return Integer.toHexString(basis.hashCode());
        }
    }

    /** The normalised copy's file name: identity plus both output dimensions. */
    static String cacheName(String name, long length, long modified, int outW, int outH) {
        return identity(name, length, modified) + "_" + outW + "x" + outH + ".jpg";
    }
}
