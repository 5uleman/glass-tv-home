package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Turns a user's own picture — any size, shape or orientation — into the same thing the catalog
 * delivers: one opaque 16:9 bitmap at the screen's size, centre-cropped, decoded once off the main
 * thread and cached as a JPEG. The original file is never modified.
 *
 * <p>Everything here is best effort: when a picture cannot be normalised the caller falls back to
 * the old decode path, so a bad file can only cost its own picture, never the home screen.
 */
final class UploadWallpapers {

    private static final String CACHE_DIR = "normalized_wallpapers";
    private static final int JPEG_QUALITY = 90;
    static final int THUMBNAIL_MAX = 480;

    private UploadWallpapers() {
    }

    static boolean isUpload(File file) {
        return file != null && file.getAbsolutePath().contains("custom_wallpapers");
    }

    /**
     * The normalised bitmap for this screen, made and cached on first use. Runs off the main
     * thread (the wallpaper decoder); returns null to let the old path try.
     */
    static Bitmap decode(Context ctx, File original, int uiW, int uiH) {
        if (original == null || !original.isFile() || uiW <= 0 || uiH <= 0) return null;
        File cache = cacheFile(ctx, original, uiW, uiH);
        Bitmap bitmap = null;
        if (cache.isFile()) bitmap = BitmapFactory.decodeFile(cache.getPath());
        if (bitmap == null) {
            if (cache.isFile()) cache.delete();
            if (!normalise(original, cache, uiW, uiH)) return null;
            bitmap = BitmapFactory.decodeFile(cache.getPath());
        }
        if (bitmap != null) bitmap.setHasAlpha(false);
        return bitmap;
    }

    /** A small picture for the upload dialog, sampled so it costs almost nothing. */
    static Bitmap thumbnail(File file, int maxSize) {
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxSize
                && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Drops cached copies whose upload is gone or has changed. Called at start and after every
     * delete, so the folder cannot grow a picture at a time.
     */
    static void prune(Context ctx, File uploadsDir) {
        File dir = cacheDir(ctx);
        File[] cached = dir.listFiles();
        if (cached == null || cached.length == 0) return;
        Set<String> live = new HashSet<>();
        File[] uploads = uploadsDir == null ? null : uploadsDir.listFiles(
                (d, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        if (uploads != null) {
            for (File f : uploads) {
                live.add(UploadSizing.identity(f.getName(), f.length(), f.lastModified()) + "_");
            }
        }
        for (File cachedFile : cached) {
            String name = cachedFile.getName();
            boolean keep = false;
            for (String prefix : live) {
                if (name.startsWith(prefix)) {
                    keep = true;
                    break;
                }
            }
            if (!keep) cachedFile.delete();
        }
    }

    private static File cacheDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), CACHE_DIR);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private static File cacheFile(Context ctx, File original, int uiW, int uiH) {
        int outW = uiW, outH = uiH;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(original.getPath(), bounds);
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            int[] oriented = UploadSizing.orientedSize(
                    bounds.outWidth, bounds.outHeight, exifOrientation(original));
            int[] out = UploadSizing.outputSize(oriented[0], oriented[1], uiW, uiH);
            outW = out[0];
            outH = out[1];
        }
        return new File(cacheDir(ctx), UploadSizing.cacheName(
                original.getName(), original.length(), original.lastModified(), outW, outH));
    }

    private static boolean normalise(File original, File cache, int uiW, int uiH) {
        Bitmap decoded = null;
        Bitmap result = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(original.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

            int orientation = exifOrientation(original);
            int[] oriented = UploadSizing.orientedSize(
                    bounds.outWidth, bounds.outHeight, orientation);
            int[] out = UploadSizing.outputSize(oriented[0], oriented[1], uiW, uiH);
            int[] cover = UploadSizing.coverSize(oriented[0], oriented[1], out[0], out[1]);
            boolean extreme = UploadSizing.isExtreme(oriented[0], oriented[1]);
            int sample = UploadSizing.sampleSize(
                    oriented[0], oriented[1], cover[0], cover[1], extreme);

            decoded = decodeSampled(original, sample);
            if (decoded == null) return false;

            result = Bitmap.createBitmap(out[0], out[1], Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            // Black under the picture: a PNG's transparent parts become opaque, and the view can
            // drop its background passes.
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(decoded,
                    matrix(decoded.getWidth(), decoded.getHeight(), orientation, out[0], out[1]),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            result.setHasAlpha(false);

            File tmp = new File(cache.getPath() + ".tmp");
            boolean written;
            try (FileOutputStream outStream = new FileOutputStream(tmp)) {
                written = result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream);
            }
            if (!written) {
                tmp.delete();
                return false;
            }
            if (cache.exists()) cache.delete();
            return tmp.renameTo(cache);
        } catch (OutOfMemoryError | Exception e) {
            return false;
        } finally {
            if (decoded != null) decoded.recycle();
            if (result != null) result.recycle();
        }
    }

    private static Bitmap decodeSampled(File file, int sample) {
        int size = sample;
        while (size <= UploadSizing.MAX_SAMPLE) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = size;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
                if (bitmap != null) return bitmap;
            } catch (OutOfMemoryError e) {
                // Try a coarser sample; the picture still shows.
            }
            if (size == UploadSizing.MAX_SAMPLE) break;
            size *= 2;
        }
        return null;
    }

    private static Matrix matrix(int rawW, int rawH, int orientation, int outW, int outH) {
        Matrix m = new Matrix();
        float cx = rawW / 2f, cy = rawH / 2f;
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.setScale(-1f, 1f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.setRotate(180f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.setScale(1f, -1f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                m.setRotate(90f, cx, cy);
                m.postScale(-1f, 1f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.setRotate(90f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                m.setRotate(270f, cx, cy);
                m.postScale(-1f, 1f, cx, cy);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.setRotate(270f, cx, cy);
                break;
            default:
                break;
        }
        float orientedW = UploadSizing.swapsAxes(orientation) ? rawH : rawW;
        float orientedH = UploadSizing.swapsAxes(orientation) ? rawW : rawH;
        float scale = Math.max(outW / orientedW, outH / orientedH);
        m.postScale(scale, scale, cx, cy);
        // Move the picture's centre to the output's centre; the canvas crops the rest.
        m.preTranslate(outW / 2f - cx, outH / 2f - cy);
        return m;
    }

    private static int exifOrientation(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getPath());
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception | Error e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }
}
