package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherEffectPreviewTest {

    private static final long T = 1_000_000L;

    private static WeatherEffectPreview.Mode mode(WeatherEffectPreview p, long now) {
        return p.modeAt(now);
    }

    @Test
    public void cyclesAutoDrizzleRainThunderstormLightFogHeavyFog() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        assertEquals(WeatherEffectPreview.Mode.AUTO, p.mode());
        assertEquals(WeatherEffectPreview.Mode.DRIZZLE, p.next(T));
        assertEquals(WeatherEffectPreview.Mode.RAIN, p.next(T));
        assertEquals(WeatherEffectPreview.Mode.THUNDERSTORM, p.next(T));
        assertEquals(WeatherEffectPreview.Mode.LIGHT_FOG, p.next(T));
        assertEquals(WeatherEffectPreview.Mode.HEAVY_FOG, p.next(T));
        assertEquals(WeatherEffectPreview.Mode.AUTO, p.next(T));
    }

    @Test
    public void everyModeHasTheLabelTheRowShows() {
        assertEquals("Auto", WeatherEffectPreview.Mode.AUTO.label);
        assertEquals("Drizzle", WeatherEffectPreview.Mode.DRIZZLE.label);
        assertEquals("Rain", WeatherEffectPreview.Mode.RAIN.label);
        assertEquals("Thunderstorm", WeatherEffectPreview.Mode.THUNDERSTORM.label);
        assertEquals("Light fog", WeatherEffectPreview.Mode.LIGHT_FOG.label);
        assertEquals("Heavy fog", WeatherEffectPreview.Mode.HEAVY_FOG.label);
    }

    @Test
    public void expiresTenMinutesAfterTheChoice() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        p.next(T);
        assertEquals(WeatherEffectPreview.Mode.DRIZZLE, mode(p, T + WeatherEffectPreview.TIMEOUT_MS - 1));
        assertEquals(WeatherEffectPreview.Mode.AUTO, mode(p, T + WeatherEffectPreview.TIMEOUT_MS));
        assertEquals(WeatherEffectPreview.Mode.AUTO, mode(p, T + WeatherEffectPreview.TIMEOUT_MS + 1));
        assertEquals(0, p.remainingMs(T + WeatherEffectPreview.TIMEOUT_MS));
    }

    @Test
    public void aNewChoiceRestartsTheWindow() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        p.next(T);
        assertEquals(WeatherEffectPreview.Mode.DRIZZLE, mode(p, T + 9 * 60_000L));
        p.next(T + 9 * 60_000L);
        assertEquals(WeatherEffectPreview.Mode.RAIN,
                mode(p, T + 9 * 60_000L + WeatherEffectPreview.TIMEOUT_MS - 1));
    }

    @Test
    public void settingAutoEndsThePreview() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        p.next(T);
        p.next(T);
        p.next(T);
        p.next(T);
        p.next(T);
        assertEquals(WeatherEffectPreview.Mode.AUTO, p.next(T));
        assertEquals(0, p.remainingMs(T));
        assertEquals(WeatherEffectPreview.Mode.AUTO, mode(p, T + 1));
    }

    @Test
    public void aRestartStartsAtAutoAndIsNeverPersisted() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        p.next(T);
        p.next(T);
        assertEquals(WeatherEffectPreview.Mode.RAIN, p.mode());

        WeatherEffectPreview restarted = new WeatherEffectPreview();
        assertEquals(WeatherEffectPreview.Mode.AUTO, restarted.mode());
        assertEquals(0, restarted.remainingMs(T));
        assertEquals(WeatherEffectPreview.Mode.AUTO, mode(restarted, T));
    }

    @Test
    public void toggleOffWinsOverEveryPreview() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        for (WeatherEffectPreview.Mode expected : new WeatherEffectPreview.Mode[]{
                WeatherEffectPreview.Mode.DRIZZLE, WeatherEffectPreview.Mode.RAIN,
                WeatherEffectPreview.Mode.THUNDERSTORM, WeatherEffectPreview.Mode.LIGHT_FOG,
                WeatherEffectPreview.Mode.HEAVY_FOG}) {
            p.next(T);
            assertEquals(expected, p.mode());
            assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(false,
                    p.mode().weatherCode, p.mode().visibilityM));
        }
    }

    @Test
    public void autoFallsBackToTheRealWeatherValues() {
        WeatherEffectPreview p = new WeatherEffectPreview();
        assertEquals(WeatherEffects.UNKNOWN, p.mode().weatherCode);
        assertEquals(WeatherEffects.UNKNOWN, p.mode().visibilityM);
        // With Auto the app passes the forecast's own code and visibility to the same classifier.
        assertEquals(WeatherEffects.Effect.RAIN, WeatherEffects.classify(true, 63, WeatherEffects.UNKNOWN));
        assertEquals(WeatherEffects.Effect.FOG, WeatherEffects.classify(true, 45, 900));
        assertEquals(WeatherEffects.Effect.NONE, WeatherEffects.classify(true, 0, 20_000));
    }

    @Test
    public void previewsUseTheSameClassifierAndRenderValuesAsRealWeather() {
        WeatherEffectPreview.Mode drizzle = WeatherEffectPreview.Mode.DRIZZLE;
        assertEquals(WeatherEffects.Effect.DRIZZLE,
                WeatherEffects.classify(true, drizzle.weatherCode, drizzle.visibilityM));
        WeatherEffectPreview.Mode rain = WeatherEffectPreview.Mode.RAIN;
        assertEquals(WeatherEffects.Effect.RAIN,
                WeatherEffects.classify(true, rain.weatherCode, rain.visibilityM));
        WeatherEffectPreview.Mode storm = WeatherEffectPreview.Mode.THUNDERSTORM;
        assertEquals(WeatherEffects.Effect.THUNDERSTORM,
                WeatherEffects.classify(true, storm.weatherCode, storm.visibilityM));

        WeatherEffectPreview.Mode light = WeatherEffectPreview.Mode.LIGHT_FOG;
        WeatherEffectPreview.Mode heavy = WeatherEffectPreview.Mode.HEAVY_FOG;
        assertEquals(WeatherEffects.Effect.FOG,
                WeatherEffects.classify(true, light.weatherCode, light.visibilityM));
        assertEquals(WeatherEffects.Effect.FOG,
                WeatherEffects.classify(true, heavy.weatherCode, heavy.visibilityM));
        float lightDensity = WeatherEffects.fogDensity(light.weatherCode, light.visibilityM);
        float heavyDensity = WeatherEffects.fogDensity(heavy.weatherCode, heavy.visibilityM);
        assertTrue(lightDensity > 0f && lightDensity < 0.4f);
        assertEquals(WeatherEffects.FOG_HEAVY_DENSITY, heavyDensity, 0.0001f);
        assertTrue(lightDensity < heavyDensity);
    }
}
