package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The small weather line under the clock (weather_lat / weather_lon): the temperature and sky from
 * Open-Meteo, and an AQI from Open-Meteo or, within about 80 km of one, the official figure from the
 * nearest Jabatan Alam Sekitar station. The last line is kept per location, so Home shows it at once
 * and only refreshes it behind.
 */
final class HomeWeather {
    private static final String TAG = "TvHome.Weather";
    static final long REFRESH_MS = 15 * 60_000L;
    private static final String DOE = "https://eqms.doe.gov.my/api3/publicportalapims/mapcaqm";
    /** Malaysian API bands, with DOE's colours: blue, green, yellow, orange, red. */
    private static final int[] API_TOP = {50, 100, 200, 300, 9999};
    static final int[] API_COLOR = {0xFF3D8BFF, 0xFF34C759, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A};
    private static final String[] API_NAME = {"Good", "Moderate", "Unhealthy", "Very unhealthy", "Hazardous"};

    /** What the line says: the weather part, the API part and its colour; either part may be empty. */
    static final class Line {
        final String weather, api;
        final int apiColor, icon;
        /** WMO code, visibility in metres, and the AQI used in the label; -1 when unknown. */
        final int weatherCode, visibilityM, aqi;
        /** The place this line belongs to, so a slow answer for the old city is never shown. */
        final String locationKey;

        Line(String weather, String api, int apiColor, int icon,
             int weatherCode, int visibilityM, int aqi, String locationKey) {
            this.weather = weather;
            this.api = api;
            this.apiColor = apiColor;
            this.icon = icon;
            this.weatherCode = weatherCode;
            this.visibilityM = visibilityM;
            this.aqi = aqi;
            this.locationKey = locationKey;
        }
    }

    private HomeWeather() {}

    /** The cache key of the place the app is set to right now. */
    static String locationKey(Context ctx) {
        return WeatherPlaces.cacheKey(AppLocationManager.getLatitude(ctx),
                AppLocationManager.getLongitude(ctx));
    }

    /** The cache key of the coordinates a request was made for. */
    static String locationKey(double lat, double lon) {
        return WeatherPlaces.cacheKey(lat, lon);
    }

    private static String field(String locationKey, String name) {
        return locationKey + ":" + name;
    }

    /** Forgets every place's line, so a new location is fetched instead of an old city's weather showing. */
    static void forget(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    static boolean due(Context ctx) {
        String key = locationKey(ctx);
        return Math.abs(System.currentTimeMillis() - prefs(ctx).getLong(field(key, "at"), 0)) >= REFRESH_MS;
    }

    /** The last line fetched for the chosen place, or null before the first. Reads storage only. */
    static Line last(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String key = locationKey(ctx);
        String w = p.getString(field(key, "weather"), null), a = p.getString(field(key, "api"), "");
        if (w != null && !w.isEmpty() && !Character.isDigit(w.charAt(0))) {
            // An older line, with an emoji: dropped, and fetched again at once.
            w = "";
            p.edit().putLong(field(key, "at"), 0).putString(field(key, "weather"), "").apply();
        }
        return w == null && a.isEmpty() ? null : new Line(w == null ? "" : w, a,
                p.getInt(field(key, "api_color"), 0),
                p.getInt(field(key, "icon_code"), -1) < 0 ? 0
                        : icon(p.getInt(field(key, "icon_code"), 3), p.getBoolean(field(key, "icon_day"), true)),
                p.getInt(field(key, "icon_code"), -1), p.getInt(field(key, "visibility_m"), -1),
                p.getInt(field(key, "aqi_value"), -1), key);
    }

    /** Fetches both parts. Network; never on the UI thread. Returns null when neither could be read. */
    static Line refresh(Context ctx, double lat, double lon) {
        String locationKey = locationKey(lat, lon);
        String weather = "", api = "";
        int color = 0, code = -1, visibility = -1;
        boolean day = true;
        boolean isMalay = !AppLanguage.LANG_EN.equals(AppLanguage.getLanguage(ctx));
        // US AQI from Open-Meteo covers the whole world, so it is the base reading; a genuinely nearby
        // Malaysian DOE station (below) overrides it with the official local figure when one exists.
        int aqiValue = -1;
        String tz = AppLocationManager.getTimezone(ctx).replace("/", "%2F");
        try {
            JSONObject now = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,is_day,us_aqi,visibility&timezone=" + tz)).getJSONObject("current");
            code = now.has("weather_code") && !now.isNull("weather_code") ? now.optInt("weather_code") : -1;
            day = now.optInt("is_day", 1) == 1;
            weather = Math.round(now.optDouble("temperature_2m")) + "°  " + describe(code);
            visibility = now.has("visibility") && !now.isNull("visibility") ? now.optInt("visibility", -1) : -1;
            aqiValue = now.has("us_aqi") && !now.isNull("us_aqi") ? now.optInt("us_aqi", -1) : -1;
        } catch (Exception e) {
            Log.w(TAG, "weather failed: " + e.getMessage());
        }
        // The DOE network covers Malaysia only: outside it the nearest station says nothing about the
        // air here, so the request is skipped and Open-Meteo's US AQI stands.
        if (AppLocationManager.isNearJasStation(lat, lon)) {
            try {
                JSONArray all = new JSONObject(get(DOE)).getJSONArray("data");
                JSONObject near = null;
                double best = Double.MAX_VALUE;
                for (int i = 0; i < all.length(); i++) {
                    JSONObject st = all.getJSONObject(i);
                    if (st.optInt("API", -1) < 0) continue;
                    double dx = st.optDouble("LONGITUDE") - lon, dy = st.optDouble("LATITUDE") - lat, d = dx * dx + dy * dy;
                    if (d < best) {
                        best = d;
                        near = st;
                    }
                }
                // Only a station near home (about 80 km): the network covers Malaysia, and outside it the
                // nearest one would be far away and say nothing about the air here.
                if (near != null && best < 0.5) {
                    aqiValue = near.getInt("API");
                }
            } catch (Exception e) {
                Log.w(TAG, "APIMS failed: " + e.getMessage());
            }
        }
        if (aqiValue >= 0) {
            // Standard US AQI categories and colors (shared with the weather dashboard), labelled AQI/IPU.
            api = (isMalay ? "IPU " : "AQI ") + aqiValue + " " + WeatherAirQuality.getAqiStatus(aqiValue, isMalay);
            color = WeatherAirQuality.getAqiColor(aqiValue);
        }
        // A failed try waits for the next round too, rather than asking again every minute.
        SharedPreferences.Editor ed = prefs(ctx).edit().putLong(field(locationKey, "at"), System.currentTimeMillis());
        if (weather.isEmpty() && api.isEmpty()) {
            ed.apply();
            return null;
        }
        ed.putString(field(locationKey, "weather"), weather)
                .putString(field(locationKey, "api"), api)
                .putInt(field(locationKey, "api_color"), color)
                .putInt(field(locationKey, "icon_code"), code)
                .putBoolean(field(locationKey, "icon_day"), day)
                .putInt(field(locationKey, "visibility_m"), visibility)
                .putInt(field(locationKey, "aqi_value"), aqiValue).apply();
        return new Line(weather, api, color, code < 0 ? 0 : icon(code, day), code, visibility, aqiValue,
                locationKey);
    }

    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Haze";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return code >= 65 ? "Heavy rain" : "Rain";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    /** The sky as a small vector symbol (not an emoji: the colour emoji font alone costs Home megabytes). */
    static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code == 1 || code == 2) return day ? R.drawable.wx_partly : R.drawable.wx_partly_night;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 51 && code <= 57) return R.drawable.wx_drizzle;
        if (code >= 61 && code <= 67) return R.drawable.wx_rain;
        if (code >= 80 && code <= 82) return R.drawable.wx_rain;
        if (code >= 95) return R.drawable.wx_storm;
        return R.drawable.wx_cloud;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "GlassTV/1.0 (Android TV home screen)");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > 512 * 1024) throw new IllegalStateException("response too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("home_weather", Context.MODE_PRIVATE);
    }
}
