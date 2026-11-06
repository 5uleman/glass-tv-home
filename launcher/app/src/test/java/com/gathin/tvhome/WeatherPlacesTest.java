package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WeatherPlacesTest {

    private static final String GEOCODING = "{"
            + "\"results\":["
            + "{\"id\":1172451,\"name\":\"Lahore\",\"latitude\":31.5497,\"longitude\":74.3436,"
            + "\"country\":\"Pakistan\",\"admin1\":\"Punjab\",\"timezone\":\"Asia/Karachi\"},"
            + "{\"name\":\"Lahore\",\"latitude\":30.0,\"longitude\":70.0,"
            + "\"country\":\"Pakistan\",\"admin1\":\"\",\"timezone\":\"Asia/Karachi\"},"
            + "{\"name\":\"\",\"latitude\":1.0,\"longitude\":2.0},"
            + "{\"name\":\"Nowhere\",\"latitude\":null,\"longitude\":4.0}"
            + "]}";

    private static final String DETECTED = "{"
            + "\"ip\":\"203.0.113.9\",\"success\":true,\"city\":\"Lahore\",\"region\":\"Punjab\","
            + "\"country\":\"Pakistan\",\"latitude\":31.5204,\"longitude\":74.3587,"
            + "\"timezone\":{\"id\":\"Asia/Karachi\",\"abbr\":\"PKT\",\"is_dst\":false}}";

    @Test
    public void geocodingResultsKeepTheCityRegionAndCountry() {
        List<WeatherPlaces.Place> places = WeatherPlaces.parseGeocoding(GEOCODING);
        assertEquals(2, places.size());
        WeatherPlaces.Place first = places.get(0);
        assertEquals("Lahore", first.name);
        assertEquals("Punjab", first.region);
        assertEquals("Pakistan", first.country);
        assertEquals("Asia/Karachi", first.timezone);
        assertEquals("Lahore, Punjab", first.title());
        assertEquals("Lahore, Punjab, Pakistan", first.label());
        assertTrue(first.subtitle().contains("Pakistan"));
        assertTrue(first.subtitle().contains("Asia/Karachi"));
        assertEquals("Lahore", places.get(1).title());
    }

    @Test
    public void malformedOrEmptyGeocodingIsAnEmptyList() {
        assertTrue(WeatherPlaces.parseGeocoding(null).isEmpty());
        assertTrue(WeatherPlaces.parseGeocoding("not json").isEmpty());
        assertTrue(WeatherPlaces.parseGeocoding("{}").isEmpty());
        assertTrue(WeatherPlaces.parseGeocoding("{\"results\":[]}").isEmpty());
    }

    @Test
    public void geocodingSkipsRowsWithoutNameOrCoordinates() {
        String json = "{\"results\":[{\"name\":\"\",\"latitude\":1,\"longitude\":2},"
                + "{\"name\":\"Nowhere\",\"latitude\":null,\"longitude\":4}]}";
        assertTrue(WeatherPlaces.parseGeocoding(json).isEmpty());
    }

    @Test
    public void detectedCityCarriesItsTimezone() {
        WeatherPlaces.Place place = WeatherPlaces.parseDetected(DETECTED);
        assertNotNull(place);
        assertEquals("Lahore", place.name);
        assertEquals("Punjab", place.region);
        assertEquals("Pakistan", place.country);
        assertEquals("Asia/Karachi", place.timezone);
        assertEquals(31.5204, place.lat, 0.00001);
        assertEquals(74.3587, place.lon, 0.00001);
    }

    @Test
    public void aFailedOrPartialDetectionSavesNothing() {
        assertNull(WeatherPlaces.parseDetected(null));
        assertNull(WeatherPlaces.parseDetected("not json"));
        assertNull(WeatherPlaces.parseDetected("{\"success\":false,\"message\":\"reserved range\"}"));
        assertNull(WeatherPlaces.parseDetected("{\"success\":true,\"latitude\":31.5,\"longitude\":74.3}"));
        assertNull(WeatherPlaces.parseDetected("{\"success\":true,\"city\":\"Lahore\"}"));
    }

    @Test
    public void cacheKeysSeparateCitiesAndSurviveRounding() {
        String lahore = WeatherPlaces.cacheKey(31.5204, 74.3587);
        String islamabad = WeatherPlaces.cacheKey(33.6844, 73.0479);
        assertEquals("31.5204,74.3587", lahore);
        assertFalse(lahore.equals(islamabad));
        assertEquals(lahore, WeatherPlaces.cacheKey(31.52040, 74.35870));
        assertEquals(lahore, WeatherPlaces.cacheKey(31.52044, 74.35866));
    }

    @Test
    public void distanceGateMatchesTheOldEightyKilometreRule() {
        // Putrajaya and Cheras are about 21 km apart; Lahore is thousands of kilometres away.
        double nearby = WeatherPlaces.distanceSq(2.9158, 101.6909, 3.1063, 101.7179);
        double far = WeatherPlaces.distanceSq(31.5204, 74.3587, 2.9158, 101.6909);
        assertTrue(nearby < 0.5);
        assertTrue(far > 0.5);
        assertTrue(WeatherPlaces.isWithin(2.9158, 101.6909, 3.1063, 101.7179, 0.5));
        assertFalse(WeatherPlaces.isWithin(31.5204, 74.3587, 2.9158, 101.6909, 0.5));
    }

    @Test
    public void defaultIsLahoreAndMatchesTheStoredDefaults() {
        assertEquals("Lahore", WeatherPlaces.DEFAULT.name);
        assertEquals("Pakistan", WeatherPlaces.DEFAULT.country);
        assertEquals("Asia/Karachi", WeatherPlaces.DEFAULT.timezone);
        assertEquals(AppLocationManager.DEFAULT_LATITUDE, WeatherPlaces.DEFAULT.lat, 0.00001);
        assertEquals(AppLocationManager.DEFAULT_LONGITUDE, WeatherPlaces.DEFAULT.lon, 0.00001);
        assertEquals(AppLocationManager.DEFAULT_LOCATION_NAME, WeatherPlaces.DEFAULT.name);
        assertEquals(AppLocationManager.DEFAULT_TIMEZONE, WeatherPlaces.DEFAULT.timezone);
        assertEquals("31.5204,74.3587",
                WeatherPlaces.cacheKey(AppLocationManager.DEFAULT_LATITUDE,
                        AppLocationManager.DEFAULT_LONGITUDE));
    }

    @Test
    public void savedPlaceTrimsBlanksAndLabelsCleanly() {
        WeatherPlaces.Place place = new WeatherPlaces.Place("  Lahore  ", null, " ", 1, 2, null);
        assertEquals("Lahore", place.name);
        assertEquals("", place.region);
        assertEquals("", place.country);
        assertEquals("", place.timezone);
        assertEquals("Lahore", place.label());
        assertEquals("Lahore", place.title());
        assertTrue(place.subtitle().contains("Country unknown"));
    }

    @Test
    public void geocodeUrlEncodesTheQuery() {
        String url = WeatherPlaces.geocodeUrl("New York");
        assertTrue(url.contains("name=New+York"));
        assertTrue(url.contains("count=8"));
        assertTrue(url.contains("format=json"));
        assertNull(WeatherPlaces.geocodeUrl("   "));
        assertNull(WeatherPlaces.geocodeUrl(null));
    }
}
