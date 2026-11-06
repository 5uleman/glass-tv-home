package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * The two network calls the Weather Location dialog makes, both blocking and both meant for a
 * background thread: a city search on Open-Meteo geocoding and one IP lookup on ipwho.is. Nothing
 * runs unless the person presses the matching row. Responses are capped like HomeWeather's.
 */
final class WeatherLocationService {

    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private WeatherLocationService() {
    }

    static List<WeatherPlaces.Place> search(String query) {
        String url = WeatherPlaces.geocodeUrl(query);
        if (url == null) return Collections.emptyList();
        return WeatherPlaces.parseGeocoding(get(url));
    }

    static WeatherPlaces.Place detect() {
        return WeatherPlaces.parseDetected(get(WeatherPlaces.DETECT_ENDPOINT));
    }

    private static String get(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "GlassTV/1.0 (Android TV home screen)");
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_RESPONSE_BYTES) return null;
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
