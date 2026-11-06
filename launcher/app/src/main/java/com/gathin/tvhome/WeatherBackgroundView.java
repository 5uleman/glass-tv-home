package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hardware-accelerated animated weather background matching Apple Weather's atmospheric canvas.
 * Renders dynamic sky gradients, sun lens flares, multi-layer rainfall, thunderstorm lightning flashes,
 * procedural drifting clouds, twinkling night stars, and slow fog/smog veils. As a wallpaper overlay
 * it is capped at 30 fps and draws only while Home is in front.
 */
public class WeatherBackgroundView extends View {

    public enum ConditionType {
        NONE,
        SUNNY,
        CLOUDY,
        DRIZZLE,
        RAIN,
        THUNDERSTORM,
        FOG,
        SMOG,
        NIGHT
    }

    private ConditionType currentCondition = ConditionType.CLOUDY;
    private boolean isAnimating = false;
    private long lastFrameTime = 0;
    private final Random random = new Random();

    // Paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Fog and smog are two prebuilt low-resolution textures, drawn slowly, never made per frame. */
    private final Paint hazePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix hazeMatrix = new Matrix();
    private Bitmap fogTexture, smogTexture;
    private boolean daytime = true;
    private float hazePhase;
    private static final int FOG_DAY_ALPHA = 120, FOG_NIGHT_ALPHA = 68;
    private static final int SMOG_DAY_ALPHA = 138, SMOG_NIGHT_ALPHA = 78;
    private static final float HAZE_SCALE = 1.18f;
    /** Overlay frames are capped at 30 fps; the rest of the app still runs at display rate. */
    private static final long OVERLAY_FRAME_MS = 33;

    // Gradients
    private LinearGradient bgGradient;
    private int cachedW = 0;
    private int cachedH = 0;

    // Rain particles
    private static class RainDrop {
        float x, y;
        float length;
        float speed;
        float width;
        int alpha;
        int layer; // 0: back, 1: mid, 2: front
    }
    private final List<RainDrop> rainDrops = new ArrayList<>();

    // Splash ripples
    private static class SplashRipple {
        float x, y;
        float radius;
        float maxRadius;
        int alpha;
    }
    private final List<SplashRipple> ripples = new ArrayList<>();

    // Stars
    private static class Star {
        float x, y;
        float size;
        float phase;
        float speed;
        int baseAlpha;
    }
    private final List<Star> stars = new ArrayList<>();

    // Procedural Clouds
    private static class CloudPuff {
        float x, y;
        float rx, ry;
        float speed;
        int alpha;
    }
    private final List<CloudPuff> cloudPuffs = new ArrayList<>();

    // Sun / Lens Flare State
    private float sunPulseTime = 0f;

    // Lightning State
    private long nextLightningTime = 0;
    private float lightningAlpha = 0f;
    private final Path lightningPath = new Path();
    private boolean hasActiveBolt = false;

    public WeatherBackgroundView(Context context) {
        this(context, null);
    }

    public WeatherBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        rainPaint.setStyle(Paint.Style.STROKE);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);

        lightningPaint.setStyle(Paint.Style.FILL);
        lightningPaint.setColor(0xE0FFFFFF);

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(1.5f);

        starPaint.setStyle(Paint.Style.FILL);
        cloudPaint.setStyle(Paint.Style.FILL);
        sunGlowPaint.setStyle(Paint.Style.FILL);
        flarePaint.setStyle(Paint.Style.FILL);

        // A wallpaper effect: never focusable, never a key or touch target.
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setCondition(ConditionType condition) {
        if (this.currentCondition != condition) {
            this.currentCondition = condition;
            if (condition == ConditionType.FOG || condition == ConditionType.SMOG) {
                ensureHazeTextures();
            }
            rebuildGradientsAndParticles();
            invalidate();
        }
    }

    /** Fog and smog are dimmer at night; the clock/dock stay above this view either way. */
    public void setDaytime(boolean daytime) {
        if (this.daytime != daytime) {
            this.daytime = daytime;
            invalidate();
        }
    }

    public ConditionType getCondition() {
        return currentCondition;
    }

    private boolean overlayMode = false;
    private boolean lowSpecMode = false;

    public void setOverlayMode(boolean overlayMode) {
        this.overlayMode = overlayMode;
        invalidate();
    }

    public boolean isOverlayMode() {
        return overlayMode;
    }

    public void setLowSpecMode(boolean lowSpecMode) {
        if (this.lowSpecMode == lowSpecMode) return;
        this.lowSpecMode = lowSpecMode;
        recycleHazeTextures();
        if (currentCondition == ConditionType.FOG || currentCondition == ConditionType.SMOG) {
            ensureHazeTextures();
        }
        rebuildGradientsAndParticles();
    }

    public boolean isLowSpecMode() {
        return lowSpecMode;
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    public void stopAnimation() {
        isAnimating = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cachedW = w;
        cachedH = h;
        rebuildGradientsAndParticles();
    }

    private void rebuildGradientsAndParticles() {
        if (cachedW <= 0 || cachedH <= 0) return;

        // 1. Sky Gradients based on Apple Weather color palettes
        switch (currentCondition) {
            case SUNNY:
                // Radiant day sky: vibrant azure blue blending into softer bright cerulean
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF1C68C5, 0xFF3180D8, 0xFF4A96E6},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case CLOUDY:
                // Muted overcast sky: soft slate blue-grey
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF243447, 0xFF35485E, 0xFF435B74},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case RAIN:
                // Rain sky: deep atmospheric oceanic slate
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF162334, 0xFF213247, 0xFF2F455F},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case THUNDERSTORM:
                // Dark storm sky: charcoal stormy blue
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF0E1624, 0xFF172232, 0xFF202C3D},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case FOG:
                // Cool grey-white veil.
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF6C7780, 0xFF8A949B, 0xFFAAB2B7},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case SMOG:
                // Warm grey-brown haze.
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF4E4438, 0xFF6A5B4A, 0xFF84745E},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case NIGHT:
            default:
                // Starry night: deep midnight sapphire
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF070D18, 0xFF0D1728, 0xFF14223A},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
        }
        bgPaint.setShader(bgGradient);

        // 2. Initialize Rain drops if drizzle, rain or storm
        rainDrops.clear();
        ripples.clear();
        if (currentCondition == ConditionType.DRIZZLE || currentCondition == ConditionType.RAIN || currentCondition == ConditionType.THUNDERSTORM) {
            int count;
            if (currentCondition == ConditionType.THUNDERSTORM) {
                count = lowSpecMode ? 70 : 140;
            } else if (currentCondition == ConditionType.RAIN) {
                count = lowSpecMode ? 50 : 100;
            } else { // DRIZZLE
                count = lowSpecMode ? 25 : 50;
            }

            for (int i = 0; i < count; i++) {
                RainDrop drop = new RainDrop();
                drop.x = random.nextFloat() * cachedW * 1.2f - (cachedW * 0.1f);
                drop.y = random.nextFloat() * cachedH;
                drop.layer = random.nextInt(3);
                if (currentCondition == ConditionType.DRIZZLE) {
                    // Delicate translucent rintik hujan
                    drop.length = 8f + random.nextFloat() * 8f;
                    drop.speed = 10f + random.nextFloat() * 6f;
                    drop.width = 1.1f;
                    drop.alpha = 40 + random.nextInt(45);
                } else if (drop.layer == 0) {
                    // Background drizzle
                    drop.length = 12f + random.nextFloat() * 10f;
                    drop.speed = 14f + random.nextFloat() * 6f;
                    drop.width = 1.2f;
                    drop.alpha = 50 + random.nextInt(50);
                } else if (drop.layer == 1) {
                    // Midground rain
                    drop.length = 22f + random.nextFloat() * 14f;
                    drop.speed = 22f + random.nextFloat() * 8f;
                    drop.width = 1.8f;
                    drop.alpha = 90 + random.nextInt(60);
                } else {
                    // Foreground heavy streak
                    drop.length = 34f + random.nextFloat() * 18f;
                    drop.speed = 32f + random.nextFloat() * 10f;
                    drop.width = 2.4f;
                    drop.alpha = 140 + random.nextInt(70);
                }
                rainDrops.add(drop);
            }
        }

        // 3. Initialize Stars if night
        stars.clear();
        if (currentCondition == ConditionType.NIGHT) {
            int starCount = 80;
            for (int i = 0; i < starCount; i++) {
                Star s = new Star();
                s.x = random.nextFloat() * cachedW;
                s.y = random.nextFloat() * (cachedH * 0.75f);
                s.size = 1f + random.nextFloat() * 2.2f;
                s.phase = random.nextFloat() * 6.28f;
                s.speed = 1.5f + random.nextFloat() * 2.5f;
                s.baseAlpha = 70 + random.nextInt(140);
                stars.add(s);
            }
        }

        // 4. Initialize Cloud Puffs if cloudy or partly cloudy
        cloudPuffs.clear();
        if (currentCondition == ConditionType.CLOUDY || currentCondition == ConditionType.RAIN) {
            int puffs = 8;
            for (int i = 0; i < puffs; i++) {
                CloudPuff p = new CloudPuff();
                p.x = random.nextFloat() * cachedW;
                p.y = (cachedH * 0.05f) + (random.nextFloat() * (cachedH * 0.45f));
                p.rx = (cachedW * 0.22f) + (random.nextFloat() * (cachedW * 0.25f));
                p.ry = (cachedH * 0.16f) + (random.nextFloat() * (cachedH * 0.18f));
                p.speed = 0.25f + (random.nextFloat() * 0.45f);
                p.alpha = 15 + random.nextInt(25);
                cloudPuffs.add(p);
            }
        }

        nextLightningTime = SystemClock.uptimeMillis() + 4000 + random.nextInt(5000);
        lightningAlpha = 0f;
        hasActiveBolt = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (cachedW <= 0 || cachedH <= 0 || currentCondition == ConditionType.NONE) return;

        long now = SystemClock.uptimeMillis();
        long interval = overlayMode ? OVERLAY_FRAME_MS : 0;
        if (interval > 0 && lastFrameTime > 0 && now - lastFrameTime < interval) {
            if (isAnimating) postInvalidateDelayed(Math.max(1, interval - (now - lastFrameTime)));
            return;
        }
        float dt = (lastFrameTime > 0) ? Math.min((now - lastFrameTime) / 1000f, 0.1f)
                : (overlayMode ? OVERLAY_FRAME_MS / 1000f : 0.016f);
        lastFrameTime = now;

        // 1. Draw base sky gradient (only when not in transparent wallpaper overlay mode)
        if (!overlayMode) {
            canvas.drawRect(0, 0, cachedW, cachedH, bgPaint);
        }

        // 2. Weather Specific Layers
        switch (currentCondition) {
            case SUNNY:
                if (!overlayMode) drawSunnyAtmosphere(canvas, dt);
                break;
            case CLOUDY:
                if (!overlayMode) drawClouds(canvas, dt);
                break;
            case DRIZZLE:
                if (!overlayMode) drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, false);
                break;
            case RAIN:
                if (!overlayMode) drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, false);
                break;
            case THUNDERSTORM:
                if (!overlayMode) drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, true);
                drawLightning(canvas, now, dt);
                break;
            case FOG:
                drawHaze(canvas, dt, false);
                break;
            case SMOG:
                drawHaze(canvas, dt, true);
                break;
            case NIGHT:
                if (!overlayMode) drawNightSky(canvas, dt);
                break;
        }

        if (isAnimating) {
            if (overlayMode) postInvalidateDelayed(OVERLAY_FRAME_MS);
            else postInvalidateOnAnimation();
        }
    }

    /** Two slowly drifting copies of one small texture; no gradients or bitmaps are made per frame. */
    private void drawHaze(Canvas canvas, float dt, boolean smog) {
        ensureHazeTextures();
        Bitmap texture = smog ? smogTexture : fogTexture;
        if (texture == null) return;
        hazePhase += dt * (smog ? 0.07f : 0.10f);
        int base = smog
                ? (daytime ? SMOG_DAY_ALPHA : SMOG_NIGHT_ALPHA)
                : (daytime ? FOG_DAY_ALPHA : FOG_NIGHT_ALPHA);
        drawHazeLayer(canvas, texture, HAZE_SCALE, hazePhase, base);
        drawHazeLayer(canvas, texture, HAZE_SCALE + 0.14f, hazePhase * 0.62f + 1.7f,
                Math.max(8, (int) (base * 0.55f)));
    }

    private void drawHazeLayer(Canvas canvas, Bitmap texture, float scale, float phase, int alpha) {
        float width = cachedW * scale, height = cachedH * scale;
        float dx = (float) Math.sin(phase) * cachedW * 0.045f;
        float dy = (float) Math.cos(phase * 0.6f) * cachedH * 0.025f;
        hazeMatrix.setScale(width / texture.getWidth(), height / texture.getHeight());
        hazeMatrix.postTranslate((cachedW - width) / 2f + dx, (cachedH - height) / 2f + dy);
        hazePaint.setAlpha(alpha);
        canvas.drawBitmap(texture, hazeMatrix, hazePaint);
    }

    private void ensureHazeTextures() {
        int width = lowSpecMode ? 192 : 384;
        int height = Math.max(1, Math.round(width * 9f / 16f));
        if (fogTexture != null && !fogTexture.isRecycled() && fogTexture.getWidth() == width) return;
        recycleHazeTextures();
        try {
            fogTexture = makeHazeTexture(width, height, false);
            smogTexture = makeHazeTexture(width, height, true);
        } catch (OutOfMemoryError e) {
            // A missing veil is better than taking Home down on a nearly full heap.
            recycleHazeTextures();
        }
    }

    /** Builds both haze textures ahead of a preview, so the first Fog phase does not hitch. */
    public void prepareHazeTextures() {
        ensureHazeTextures();
    }

    private void recycleHazeTextures() {
        if (fogTexture != null && !fogTexture.isRecycled()) fogTexture.recycle();
        if (smogTexture != null && !smogTexture.isRecycled()) smogTexture.recycle();
        fogTexture = null;
        smogTexture = null;
    }

    /** Built once on the UI thread when fog or smog first appears: about 0.66 MB for both textures. */
    private static Bitmap makeHazeTexture(int width, int height, boolean smog) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int top = smog ? 0x665A4A38 : 0x55E8F0F5;
        int middle = smog ? 0x3A40362C : 0x2EDCE7EE;
        int bottom = smog ? 0x66504435 : 0x55E8F0F5;
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{top, middle, bottom}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        Random random = new Random(smog ? 7L : 3L);
        int blob = smog ? 0x50B09878 : 0x5AEFF5F8;
        int clear = smog ? 0x00B09878 : 0x00EFF5F8;
        for (int i = 0; i < 16; i++) {
            float cx = random.nextFloat() * width;
            float cy = random.nextFloat() * height;
            float radius = width * (0.10f + random.nextFloat() * 0.22f);
            paint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{blob, clear}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
        }
        paint.setShader(null);
        return bitmap;
    }

    private void drawSunnyAtmosphere(Canvas canvas, float dt) {
        sunPulseTime += dt;
        float sunCenterX = cachedW * 0.35f;
        float sunCenterY = cachedH * 0.05f;

        // Animated sun core glow
        float pulse = (float) Math.sin(sunPulseTime * 1.2f) * 0.05f;
        float coreRadius = cachedW * (0.35f + pulse);
        RadialGradient sunRadial = new RadialGradient(
                sunCenterX, sunCenterY, coreRadius,
                new int[]{0xAAFFFFFF, 0x44FFE082, 0x15FFB300, 0x001E88E5},
                new float[]{0f, 0.25f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        );
        sunGlowPaint.setShader(sunRadial);
        canvas.drawCircle(sunCenterX, sunCenterY, coreRadius, sunGlowPaint);

        // Optical Lens Flare Rings & Disks (Apple Weather style)
        // Flare 1: warm cyan-gold ring
        float flare1X = sunCenterX + (cachedW * 0.12f) + (float) Math.sin(sunPulseTime * 0.8f) * 8f;
        float flare1Y = sunCenterY + (cachedH * 0.18f);
        flarePaint.setColor(0x2280D8FF);
        canvas.drawCircle(flare1X, flare1Y, cachedW * 0.08f, flarePaint);

        // Flare 2: soft warm amber disc
        float flare2X = sunCenterX + (cachedW * 0.24f);
        float flare2Y = sunCenterY + (cachedH * 0.36f);
        flarePaint.setColor(0x18FFE082);
        canvas.drawCircle(flare2X, flare2Y, cachedW * 0.05f, flarePaint);

        // Flare 3: subtle violet halo near bottom
        float flare3X = sunCenterX + (cachedW * 0.38f);
        float flare3Y = sunCenterY + (cachedH * 0.58f);
        flarePaint.setColor(0x14CE93D8);
        canvas.drawCircle(flare3X, flare3Y, cachedW * 0.14f, flarePaint);
    }

    private void drawNightSky(Canvas canvas, float dt) {
        // Moon radial glow at top right
        float moonX = cachedW * 0.85f;
        float moonY = cachedH * 0.14f;
        float moonRadius = cachedW * 0.15f;
        RadialGradient moonRadial = new RadialGradient(
                moonX, moonY, moonRadius,
                new int[]{0x35E0F2FE, 0x1590CAF9, 0x000D1728},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        sunGlowPaint.setShader(moonRadial);
        canvas.drawCircle(moonX, moonY, moonRadius, sunGlowPaint);

        // Twinkling Stars
        for (Star s : stars) {
            s.phase += s.speed * dt;
            float twinkle = (float) ((Math.sin(s.phase) + 1.0) / 2.0);
            int alpha = (int) (s.baseAlpha * (0.4f + 0.6f * twinkle));
            starPaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
    }

    private void drawClouds(Canvas canvas, float dt) {
        for (CloudPuff p : cloudPuffs) {
            p.x += p.speed * 60f * dt;
            if (p.x - p.rx > cachedW) {
                p.x = -p.rx;
            }

            RadialGradient cloudGrad = new RadialGradient(
                    p.x, p.y, Math.max(p.rx, p.ry),
                    new int[]{Color.argb(p.alpha, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            cloudPaint.setShader(cloudGrad);
            canvas.drawOval(p.x - p.rx, p.y - p.ry, p.x + p.rx, p.y + p.ry, cloudPaint);
        }
    }

    private void drawRainAndRipples(Canvas canvas, float dt, boolean heavy) {
        float angleTan = 0.16f; // ~9 degree wind slant

        for (int i = 0; i < rainDrops.size(); i++) {
            RainDrop drop = rainDrops.get(i);
            drop.y += drop.speed * 60f * dt;
            drop.x += (drop.speed * 60f * dt) * angleTan;

            // Draw streak
            rainPaint.setStrokeWidth(drop.width);
            rainPaint.setColor(Color.argb(drop.alpha, 220, 235, 255));
            canvas.drawLine(
                    drop.x,
                    drop.y,
                    drop.x + (drop.length * angleTan),
                    drop.y + drop.length,
                    rainPaint
            );

            // Check if drop hit bottom of screen
            if (drop.y > cachedH) {
                // Occasional splash ripple on screen bottom floor
                if (ripples.size() < 16 && random.nextFloat() < 0.25f) {
                    SplashRipple r = new SplashRipple();
                    r.x = drop.x;
                    r.y = cachedH - (random.nextFloat() * 30f);
                    r.radius = 1f;
                    r.maxRadius = 6f + random.nextFloat() * 10f;
                    r.alpha = 80;
                    ripples.add(r);
                }

                // Reset drop to top of sky
                drop.y = -drop.length - random.nextFloat() * 40f;
                drop.x = random.nextFloat() * cachedW * 1.2f - (cachedW * 0.1f);
            }
        }

        // Draw and update splash ripples
        for (int i = ripples.size() - 1; i >= 0; i--) {
            SplashRipple r = ripples.get(i);
            r.radius += 24f * dt;
            float progress = r.radius / r.maxRadius;
            if (progress >= 1.0f) {
                ripples.remove(i);
            } else {
                int currentAlpha = (int) (r.alpha * (1f - progress));
                ripplePaint.setColor(Color.argb(currentAlpha, 220, 240, 255));
                canvas.drawOval(
                        r.x - r.radius * 1.6f,
                        r.y - r.radius * 0.6f,
                        r.x + r.radius * 1.6f,
                        r.y + r.radius * 0.6f,
                        ripplePaint
                );
            }
        }
    }

    private void drawLightning(Canvas canvas, long now, float dt) {
        if (now >= nextLightningTime && lightningAlpha <= 0f) {
            // Trigger lightning flash
            lightningAlpha = 0.95f;
            nextLightningTime = now + 4000 + random.nextInt(6000);
            generateLightningPath();
            hasActiveBolt = true;
        }

        if (lightningAlpha > 0f) {
            // Screen flash wash
            int flashColor = Color.argb((int) (lightningAlpha * 140), 225, 240, 255);
            canvas.drawColor(flashColor);

            // Draw bolt
            if (hasActiveBolt && lightningAlpha > 0.4f) {
                lightningPaint.setStrokeWidth(2.5f);
                lightningPaint.setStyle(Paint.Style.STROKE);
                lightningPaint.setColor(Color.argb((int) (lightningAlpha * 230), 255, 255, 255));
                canvas.drawPath(lightningPath, lightningPaint);
            }

            // Rapid decay
            lightningAlpha -= dt * 4.5f;
            if (lightningAlpha <= 0f) {
                lightningAlpha = 0f;
                hasActiveBolt = false;
            }
        }
    }

    private void generateLightningPath() {
        lightningPath.reset();
        float startX = cachedW * (0.25f + random.nextFloat() * 0.5f);
        float startY = 0;
        lightningPath.moveTo(startX, startY);

        float curX = startX;
        float curY = startY;
        int segments = 10 + random.nextInt(6);
        float segHeight = (cachedH * 0.65f) / segments;

        for (int i = 0; i < segments; i++) {
            curY += segHeight + (random.nextFloat() * 8f - 4f);
            curX += (random.nextFloat() * 60f - 30f);
            lightningPath.lineTo(curX, curY);

            // Occasional fork branch
            if (random.nextFloat() < 0.35f) {
                float forkX = curX + (random.nextFloat() * 50f - 25f);
                float forkY = curY + (segHeight * 0.7f);
                lightningPath.moveTo(curX, curY);
                lightningPath.lineTo(forkX, forkY);
                lightningPath.moveTo(curX, curY);
            }
        }
    }
}
