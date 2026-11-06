package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The edge of a glass shape: a thin prismatic rim with a white glint, an accent shimmer and a pale
 * pearl along the top-left, a clear waist, and an accent-white return on the bottom-right, plus an
 * overhead window-light glint on the top edge. The accent is {@link GlareColor}: the classic blue,
 * or a soft tint sampled from the wallpaper.
 *
 * <p>Ported from {@code GlassRim.java} in upstream glass-tv-home (MIT, Copyright (c) 2026 Gathin).
 * The gradient is built once in a unit square and stretched onto each shape; it is rebuilt only
 * when the wallpaper accent changes, so drawing allocates nothing.
 */
final class GlassRim {

    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windowLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Matrix windowLightMatrix = new Matrix();
    private final float alphaFactor;
    private final int clearWaist;
    private final float reach;
    private int builtTint = Integer.MIN_VALUE;

    GlassRim(float strokePx, int shine, int faint) {
        this(strokePx, shine, faint, 1f);
    }

    /**
     * @param reach how far each shine runs towards the faint corners: 1 fades it out right at
     *              them, 0.5 only halfway. Long shapes such as the dock tray use the shorter reach,
     *              or the shine runs most of the length of their long edges.
     */
    GlassRim(float strokePx, int shine, int faint, float reach) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokePx);
        windowLightPaint.setStyle(Paint.Style.STROKE);
        windowLightPaint.setStrokeWidth(Math.max(1f, strokePx * 0.9f));
        alphaFactor = ((shine >>> 24) & 0xFF) / 255f;
        clearWaist = faint != 0 ? faint : 0x22FFFFFF;
        this.reach = reach;
        buildShader(GlareColor.current());
    }

    private void buildShader(int accent) {
        builtTint = accent;
        boolean classic = accent == GlareColor.CLASSIC;

        int cWhiteGlint = modulateAlpha(0xFFFFFFFF, alphaFactor);
        int cCyanShimmer;
        int cVioletSoap;
        int cGoldSheen;
        int cTwilightLilac;
        int cSkyCyan;
        int windowLightAccent;
        if (classic) {
            cCyanShimmer = modulateAlpha(0xCC67E8F9, alphaFactor);
            cVioletSoap = modulateAlpha(0xB3C084FC, alphaFactor);
            cGoldSheen = modulateAlpha(0x80FDE047, alphaFactor);
            cTwilightLilac = modulateAlpha(0x59818CF8, alphaFactor);
            cSkyCyan = modulateAlpha(0x8038BDF8, alphaFactor);
            windowLightAccent = 0x8067E8F9;
        } else {
            cCyanShimmer = modulateAlpha(GlareColor.withAlpha(GlareColor.lighten(accent, 0.30f), 0xCC), alphaFactor);
            cVioletSoap = modulateAlpha(GlareColor.withAlpha(GlareColor.lighten(accent, 0.12f), 0xB3), alphaFactor);
            cGoldSheen = modulateAlpha(GlareColor.withAlpha(GlareColor.lighten(accent, 0.55f), 0x80), alphaFactor);
            cTwilightLilac = modulateAlpha(GlareColor.withAlpha(accent, 0x59), alphaFactor);
            cSkyCyan = modulateAlpha(GlareColor.withAlpha(GlareColor.darken(accent, 0.12f), 0x80), alphaFactor);
            windowLightAccent = GlareColor.withAlpha(GlareColor.lighten(accent, 0.25f), 0x80);
        }
        int cClearWaist = modulateAlpha(clearWaist, alphaFactor);
        int cWhiteRim = modulateAlpha(0xD9FFFFFF, alphaFactor);

        int[] colors = new int[]{
                cWhiteGlint,
                cCyanShimmer,
                cVioletSoap,
                cGoldSheen,
                cClearWaist,
                cClearWaist,
                cTwilightLilac,
                cSkyCyan,
                cWhiteRim
        };
        float[] stops = new float[]{
                0.00f,
                0.08f * reach,
                0.18f * reach,
                0.30f * reach,
                0.46f,
                0.54f,
                1.0f - (0.30f * reach),
                1.0f - (0.15f * reach),
                1.00f
        };
        paint.setShader(new LinearGradient(0f, 0f, 1f, 1f, colors, stops, Shader.TileMode.CLAMP));

        int[] wlColors = new int[]{
                0x00FFFFFF,
                0x00FFFFFF,
                0xD9FFFFFF,
                windowLightAccent,
                0x00FFFFFF,
                0x00FFFFFF
        };
        float[] wlStops = new float[]{
                0.00f,
                0.15f,
                0.32f,
                0.46f,
                0.68f,
                1.00f
        };
        windowLightPaint.setShader(new LinearGradient(0f, 0f, 1f, 0f, wlColors, wlStops,
                Shader.TileMode.CLAMP));
    }

    private static int modulateAlpha(int color, float factor) {
        int a = Math.round(((color >>> 24) & 0xFF) * factor);
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** Lines the shine up with {@code rect}: brightest at its top-left and bottom-right. */
    void fit(RectF rect) {
        int tint = GlareColor.current();
        if (tint != builtTint) buildShader(tint);
        matrix.setScale(rect.width(), rect.height());
        matrix.postTranslate(rect.left, rect.top);
        paint.getShader().setLocalMatrix(matrix);
    }

    /** Draws the overhead window-light glint along the top curve of the glass. */
    void drawWindowLight(Canvas canvas, RectF rect, float cornerRadius, float alpha) {
        if (alpha <= 0.05f) return;
        windowLightMatrix.setScale(rect.width(), rect.height());
        windowLightMatrix.postTranslate(rect.left, rect.top);
        windowLightPaint.getShader().setLocalMatrix(windowLightMatrix);
        windowLightPaint.setAlpha(Math.round(200 * alpha));
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, windowLightPaint);
    }
}
