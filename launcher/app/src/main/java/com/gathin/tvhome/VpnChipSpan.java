package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;

/**
 * The VPN chip: white bold text on a filled, rounded dark-green pill. A span rather than a second
 * view, so the network label next to it keeps shrinking and ellipsizing on its own.
 */
final class VpnChipSpan extends ReplacementSpan {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float paddingH;
    private final float verticalInset;
    private final int fillColor;

    VpnChipSpan(float textSize, float paddingH, float verticalInset, int fillColor, Typeface typeface) {
        this.paddingH = paddingH;
        this.verticalInset = verticalInset;
        this.fillColor = fillColor;
        paint.setTypeface(typeface != null ? typeface : Typeface.DEFAULT_BOLD);
        paint.setTextSize(textSize);
        paint.setFontVariationSettings("'wght' 700");
    }

    /** Width the chip needs for this text at this size, both side paddings included. */
    static float widthFor(String text, float textSize, float paddingH, Typeface typeface) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(typeface != null ? typeface : Typeface.DEFAULT_BOLD);
        p.setTextSize(textSize);
        p.setFontVariationSettings("'wght' 700");
        return p.measureText(text) + 2 * paddingH;
    }

    private float widthOf(CharSequence text, int start, int end) {
        return paint.measureText(text, start, end) + 2 * paddingH;
    }

    @Override
    public int getSize(Paint basePaint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return (int) Math.ceil(widthOf(text, start, end));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                     int bottom, Paint basePaint) {
        float width = widthOf(text, start, end);
        RectF pill = new RectF(x, top + verticalInset, x + width, bottom - verticalInset);
        float radius = pill.height() / 2f;
        paint.setColor(fillColor);
        canvas.drawRoundRect(pill, radius, radius, paint);
        paint.setColor(Color.WHITE);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = pill.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, start, end, x + paddingH, baseline, paint);
    }
}
