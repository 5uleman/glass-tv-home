package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Places for the weather: Open-Meteo geocoding results, the ipwho.is answer for this connection,
 * the cache key that keeps one city's weather away from another's, and the small distance gate the
 * Malaysian DOE station override uses. Pure: no Android, no network, no clock.
 */
final class WeatherPlaces {

    static final String GEOCODE_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search";
    /** Free, key-less HTTPS IP lookup; ipwhois.io documents 1,000 requests per day per client IP. */
    static final String DETECT_ENDPOINT = "https://ipwho.is/";

    /** The fresh-install location, and what "Use default" saves. */
    static final Place DEFAULT = new Place("Lahore", "Punjab", "Pakistan",
            31.5204, 74.3587, "Asia/Karachi");

    static final class Place {
        final String name, region, country, timezone;
        final double lat, lon;

        Place(String name, String region, String country, double lat, double lon, String timezone) {
            this.name = name == null ? "" : name.trim();
            this.region = region == null ? "" : region.trim();
            this.country = country == null ? "" : country.trim();
            this.lat = lat;
            this.lon = lon;
            this.timezone = timezone == null ? "" : timezone.trim();
        }

        /** "Lahore, Punjab" or just the city when the service gives no region. */
        String title() {
            return region.isEmpty() ? name : name + ", " + region;
        }

        /** "Pakistan · 31.52°N, 74.36°E · Asia/Karachi", always showing the country. */
        String subtitle() {
            String coords = String.format(Locale.ROOT, "%.2f°%s, %.2f°%s",
                    Math.abs(lat), lat < 0 ? "S" : "N", Math.abs(lon), lon < 0 ? "W" : "E");
            String countryPart = country.isEmpty() ? "Country unknown" : country;
            return timezone.isEmpty() ? countryPart + " · " + coords
                    : countryPart + " · " + coords + " · " + timezone;
        }

        /** "Lahore, Punjab, Pakistan" for headings and stored display names. */
        String label() {
            if (region.isEmpty()) return country.isEmpty() ? name : name + ", " + country;
            if (country.isEmpty()) return name + ", " + region;
            return name + ", " + region + ", " + country;
        }
    }

    private WeatherPlaces() {
    }

    /**
     * Reads Open-Meteo's {@code results} array. Rows missing a name or coordinates are skipped;
     * malformed JSON and the "no results" answer both give an empty list rather than throwing.
     */
    static List<Place> parseGeocoding(String json) {
        List<Place> places = new ArrayList<>();
        if (json == null) return places;
        try {
            JSONArray results = new JSONObject(json).optJSONArray("results");
            if (results == null) return places;
            for (int i = 0; i < results.length() && places.size() < 10; i++) {
                JSONObject row = results.optJSONObject(i);
                if (row == null) continue;
                String name = row.optString("name", "").trim();
                double lat = row.optDouble("latitude", Double.NaN);
                double lon = row.optDouble("longitude", Double.NaN);
                if (name.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon)) continue;
                places.add(new Place(name, row.optString("admin1", ""),
                        row.optString("country", ""), lat, lon, row.optString("timezone", "")));
            }
        } catch (Exception ignored) {
        }
        return places;
    }

    /**
     * Reads the ipwho.is answer. A failed lookup ({@code success:false}), a missing city or missing
     * coordinates gives null, so nothing is ever saved from a partial answer.
     */
    static Place parseDetected(String json) {
        if (json == null) return null;
        try {
            JSONObject row = new JSONObject(json);
            if (!row.optBoolean("success", false)) return null;
            String city = row.optString("city", "").trim();
            double lat = row.optDouble("latitude", Double.NaN);
            double lon = row.optDouble("longitude", Double.NaN);
            if (city.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon)) return null;
            JSONObject tz = row.optJSONObject("timezone");
            String timezone = tz == null ? "" : tz.optString("id", "");
            return new Place(city, row.optString("region", ""), row.optString("country", ""),
                    lat, lon, timezone);
        } catch (Exception e) {
            return null;
        }
    }

    /** The key one city's weather is cached under; four decimals is about eleven metres. */
    static String cacheKey(double lat, double lon) {
        return String.format(Locale.ROOT, "%.4f,%.4f", lat, lon);
    }

    /** Squared degrees, the same cheap measure the DOE gate has always used. */
    static double distanceSq(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat1 - lat2, dLon = lon1 - lon2;
        return dLat * dLat + dLon * dLon;
    }

    /** Whether two points are within the same squared-degree radius (0.5 is about 80 km). */
    static boolean isWithin(double lat1, double lon1, double lat2, double lon2, double maxSquaredDegrees) {
        return distanceSq(lat1, lon1, lat2, lon2) < maxSquaredDegrees;
    }

    /** Open-Meteo geocoding for a typed city name; null when the query is blank. */
    static String geocodeUrl(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        try {
            return GEOCODE_ENDPOINT + "?name=" + URLEncoder.encode(query.trim(), "UTF-8")
                    + "&count=8&language=en&format=json";
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
}
