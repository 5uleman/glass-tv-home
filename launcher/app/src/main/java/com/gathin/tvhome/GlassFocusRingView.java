package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The focus rim on an app tile: a cyan bloom, a white-to-cyan gradient edge, and a top specular
 * glint. Static on purpose: it draws only when focus changes, with the shaders cached on resize.
 *
 * <p>The drawing is ported from {@code DockBreathingBorderView.java} in upstream glass-tv-home
 * (MIT, Copyright (c) 2026 Gathin), held at its resting breath with the animator removed.
 */
public class GlassFocusRingView extends View {

    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bounds = new RectF();
    private final RectF glowBounds = new RectF();
    private final RectF sheenBounds = new RectF();

    private float density = 1f;
    private float cornerRadius = 16f;
    private LinearGradient rimShader;
    private LinearGradient sheenShader;
    private int builtTint = Integer.MIN_VALUE;
    private int glowColor = 0xFF38BDF8;

    public GlassFocusRingView(Context context) {
        super(context);
        init(context);
    }

    public GlassFocusRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GlassFocusRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /** Matches the card's outline radius, which shrinks with the tile in the five-column layout. */
    void setCornerRadiusDp(float radiusDp) {
        float px = Math.max(0f, radiusDp) * density;
        if (Math.abs(px - cornerRadius) < 0.5f) return;
        cornerRadius = px;
        updateBounds();
        invalidate();
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 16f * density;
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
        rimPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        sheenPaint.setStyle(Paint.Style.STROKE);
        sheenPaint.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBounds();
    }

    private void updateBounds() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float stroke = 2f * density;
        float half = stroke / 2f;
        bounds.set(half, half, w - half, h - half);
        float glowInset = 1f * density;
        glowBounds.set(bounds.left - glowInset, bounds.top - glowInset,
                bounds.right + glowInset, bounds.bottom + glowInset);
        sheenBounds.set(bounds.left + 2 * density, bounds.top + density,
                bounds.right - 2 * density, bounds.top + 16 * density);

        sheenShader = new LinearGradient(sheenBounds.left, sheenBounds.top,
                sheenBounds.right, sheenBounds.top,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        sheenPaint.setShader(sheenShader);
        buildRimShader();
    }

    private void buildRimShader() {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        builtTint = GlareColor.current();
        int accent = builtTint == GlareColor.CLASSIC ? 0xFF38BDF8 : builtTint;
        glowColor = accent;
        rimShader = new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[]{0xFFFFFFFF, accent}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        rimPaint.setShader(rimShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (rimShader == null) updateBounds();
        if (GlareColor.current() != builtTint) buildRimShader();

        // The upstream ring at its resting breath: no animator, so nothing runs between focus changes.
        float alphaFactor = 0.9025f;
        glowPaint.setStrokeWidth(6.55f * density);
        glowPaint.setColor(GlareColor.withAlpha(glowColor, Math.round(65 * alphaFactor)));
        canvas.drawRoundRect(glowBounds, cornerRadius + 2f * density, cornerRadius + 2f * density,
                glowPaint);

        rimPaint.setStrokeWidth(2.45f * density);
        rimPaint.setAlpha(Math.round(225 * alphaFactor));
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, rimPaint);

        sheenPaint.setStrokeWidth(1.2f * density);
        sheenPaint.setAlpha(Math.round(190 * (0.4f + 0.6f * 0.85f)));
        canvas.drawLine(bounds.left + cornerRadius, bounds.top + 0.5f * density,
                bounds.right - cornerRadius, bounds.top + 0.5f * density, sheenPaint);
    }
}
