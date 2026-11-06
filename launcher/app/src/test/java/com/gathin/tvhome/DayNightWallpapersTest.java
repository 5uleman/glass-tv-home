package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DayNightWallpapersTest {

    private static DayNightWallpapers.Photo photo(String name, double luma, DayNightWallpapers.Tone tone) {
        return new DayNightWallpapers.Photo(new File(name), luma, tone);
    }

    /** Three day, three night and two both: each period has exactly five allowed pictures. */
    private static List<DayNightWallpapers.Photo> mixedSet() {
        List<DayNightWallpapers.Photo> photos = new ArrayList<>();
        photos.add(photo("day1.jpg", 150, DayNightWallpapers.Tone.DAY));
        photos.add(photo("day2.jpg", 130, DayNightWallpapers.Tone.DAY));
        photos.add(photo("day3.jpg", 116, DayNightWallpapers.Tone.DAY));
        photos.add(photo("night1.jpg", 30, DayNightWallpapers.Tone.NIGHT));
        photos.add(photo("night2.jpg", 60, DayNightWallpapers.Tone.NIGHT));
        photos.add(photo("night3.jpg", 84, DayNightWallpapers.Tone.NIGHT));
        photos.add(photo("both1.jpg", 100, DayNightWallpapers.Tone.BOTH));
        photos.add(photo("both2.jpg", 95, DayNightWallpapers.Tone.BOTH));
        return photos;
    }

    private static List<String> names(List<DayNightWallpapers.Photo> photos) {
        List<String> out = new ArrayList<>();
        for (DayNightWallpapers.Photo p : photos) out.add(p.file.getName());
        return out;
    }

    @Test
    public void switchOffAllowsEveryPhoto() {
        List<DayNightWallpapers.Photo> all = mixedSet();
        assertEquals(8, DayNightWallpapers.allowed(false, LocalTime.of(12, 0), all).size());
        assertEquals(8, DayNightWallpapers.allowed(false, LocalTime.of(23, 0), all).size());
    }

    @Test
    public void dayStartsAtSixAndNightStartsAtEighteen() {
        assertEquals(DayNightWallpapers.Period.NIGHT, DayNightWallpapers.periodAt(LocalTime.of(5, 59)));
        assertEquals(DayNightWallpapers.Period.DAY, DayNightWallpapers.periodAt(LocalTime.of(6, 0)));
        assertEquals(DayNightWallpapers.Period.DAY, DayNightWallpapers.periodAt(LocalTime.of(17, 59)));
        assertEquals(DayNightWallpapers.Period.NIGHT, DayNightWallpapers.periodAt(LocalTime.of(18, 0)));
        assertEquals(DayNightWallpapers.Period.NIGHT, DayNightWallpapers.periodAt(LocalTime.of(0, 0)));
    }

    @Test
    public void atSixOnlyDayAndBothPhotosRotate() {
        List<String> day = names(DayNightWallpapers.allowed(true, LocalTime.of(6, 0), mixedSet()));
        assertEquals(5, day.size());
        assertTrue(day.contains("day1.jpg"));
        assertTrue(day.contains("day2.jpg"));
        assertTrue(day.contains("day3.jpg"));
        assertTrue(day.contains("both1.jpg"));
        assertTrue(day.contains("both2.jpg"));
        assertFalse(day.contains("night1.jpg"));
    }

    @Test
    public void atEighteenOnlyNightAndBothPhotosRotate() {
        List<String> night = names(DayNightWallpapers.allowed(true, LocalTime.of(18, 0), mixedSet()));
        assertEquals(5, night.size());
        assertTrue(night.contains("night1.jpg"));
        assertTrue(night.contains("night2.jpg"));
        assertTrue(night.contains("night3.jpg"));
        assertTrue(night.contains("both1.jpg"));
        assertTrue(night.contains("both2.jpg"));
        assertFalse(night.contains("day1.jpg"));
    }

    @Test
    public void bothToneAppearsInBothPeriods() {
        List<String> day = names(DayNightWallpapers.allowed(true, LocalTime.of(12, 0), mixedSet()));
        List<String> night = names(DayNightWallpapers.allowed(true, LocalTime.of(21, 0), mixedSet()));
        assertTrue(day.contains("both1.jpg"));
        assertTrue(day.contains("both2.jpg"));
        assertTrue(night.contains("both1.jpg"));
        assertTrue(night.contains("both2.jpg"));
    }

    @Test
    public void fallbackAddsTheClosestPhotosUntilFive() {
        List<DayNightWallpapers.Photo> photos = new ArrayList<>();
        photos.add(photo("day1.jpg", 120, DayNightWallpapers.Tone.DAY));
        photos.add(photo("day2.jpg", 130, DayNightWallpapers.Tone.DAY));
        photos.add(photo("day3.jpg", 140, DayNightWallpapers.Tone.DAY));
        photos.add(photo("night1.jpg", 80, DayNightWallpapers.Tone.NIGHT));
        photos.add(photo("night2.jpg", 60, DayNightWallpapers.Tone.NIGHT));
        photos.add(photo("night3.jpg", 40, DayNightWallpapers.Tone.NIGHT));

        List<String> day = names(DayNightWallpapers.allowed(true, LocalTime.of(9, 0), photos));
        assertEquals(5, day.size());
        assertTrue(day.contains("night1.jpg"));
        assertTrue(day.contains("night2.jpg"));
        assertFalse(day.contains("night3.jpg"));

        // The same six pictures at night have three matches, so the two brightest days are added.
        List<String> night = names(DayNightWallpapers.allowed(true, LocalTime.of(21, 0), photos));
        assertEquals(5, night.size());
        assertTrue(night.contains("day1.jpg"));
        assertTrue(night.contains("day2.jpg"));
        assertFalse(night.contains("day3.jpg"));
    }

    @Test
    public void emptyCollectionStaysEmpty() {
        assertTrue(DayNightWallpapers.allowed(true, LocalTime.of(12, 0),
                new ArrayList<>()).isEmpty());
    }

    @Test
    public void onePhotoCollectionUsesThatPhotoEvenOutOfSeason() {
        List<DayNightWallpapers.Photo> one = new ArrayList<>();
        one.add(photo("night.jpg", 20, DayNightWallpapers.Tone.NIGHT));
        List<DayNightWallpapers.Photo> allowed = DayNightWallpapers.allowed(
                true, LocalTime.of(12, 0), one);
        assertEquals(1, allowed.size());
        assertEquals("night.jpg", allowed.get(0).file.getName());
    }

    @Test
    public void lumaThresholdsMapToTheInitialTones() {
        assertEquals(DayNightWallpapers.Tone.DAY, DayNightWallpapers.toneFor(115.0));
        assertEquals(DayNightWallpapers.Tone.NIGHT, DayNightWallpapers.toneFor(85.0));
        assertEquals(DayNightWallpapers.Tone.BOTH, DayNightWallpapers.toneFor(114.9));
        assertEquals(DayNightWallpapers.Tone.BOTH, DayNightWallpapers.toneFor(85.1));
    }

    @Test
    public void everyCatalogPhotoHasExplicitLumaAndTag() {
        List<OnlineWallpapers.WallpaperItem> all = OnlineWallpapers.catalogItems(null);
        assertEquals(95, all.size());
        for (OnlineWallpapers.WallpaperItem item : all) {
            assertTrue(item.title + " has no luma", item.luma > 0);
            assertTrue(item.title + " has no tone", item.tone != null);
            assertTrue(item.title + " has no explicit tag", item.explicitTag);
        }
    }

    @Test
    public void eachCollectionOffersTheExpectedDayAndNightSets() {
        List<OnlineWallpapers.WallpaperItem> malaysia = OnlineWallpapers.catalogItems("Malaysia");
        List<OnlineWallpapers.WallpaperItem> landscapes = exactCategories(
                "Landscape", "Nature", "Forest", "Desert", "Mountains");
        List<OnlineWallpapers.WallpaperItem> cities = OnlineWallpapers.catalogItems("Skyscraper");
        List<OnlineWallpapers.WallpaperItem> space = OnlineWallpapers.catalogItems("Space");
        List<OnlineWallpapers.WallpaperItem> oceans = OnlineWallpapers.catalogItems("Ocean");
        List<OnlineWallpapers.WallpaperItem> dark = OnlineWallpapers.catalogItems("Minimalist");
        List<OnlineWallpapers.WallpaperItem> monuments = OnlineWallpapers.catalogItems("Pakistan Monuments");
        List<OnlineWallpapers.WallpaperItem> nature = OnlineWallpapers.catalogItems("Pakistan Nature");
        List<OnlineWallpapers.WallpaperItem> legacy = OnlineWallpapers.catalogItems("Pakistan");

        assertEquals(16, malaysia.size());
        assertEquals(10, landscapes.size());
        assertEquals(6, cities.size());
        assertEquals(4, space.size());
        assertEquals(6, oceans.size());
        assertEquals(19, dark.size());
        assertEquals(24, monuments.size());
        assertEquals(10, nature.size());
        assertEquals(34, legacy.size());
        assertEquals(95, OnlineWallpapers.catalogItems(null).size());

        // Tag sets before fallback: the values the owner assigned.
        assertEquals(13, matchingCount(malaysia, 12));
        assertEquals(6, matchingCount(malaysia, 21));
        assertEquals(6, matchingCount(landscapes, 12));
        assertEquals(4, matchingCount(landscapes, 21));
        assertEquals(4, matchingCount(cities, 12));
        assertEquals(2, matchingCount(cities, 21));
        assertEquals(0, matchingCount(space, 12));
        assertEquals(4, matchingCount(space, 21));
        assertEquals(6, matchingCount(oceans, 12));
        assertEquals(2, matchingCount(oceans, 21));
        assertEquals(4, matchingCount(dark, 12));
        assertEquals(15, matchingCount(dark, 21));
        assertEquals(17, matchingCount(monuments, 12));
        assertEquals(9, matchingCount(monuments, 21));
        assertEquals(8, matchingCount(nature, 12));
        assertEquals(8, matchingCount(nature, 21));
        assertEquals(25, matchingCount(legacy, 12));
        assertEquals(17, matchingCount(legacy, 21));

        // What the rotation actually offers after the 5-photo fallback and the under-5 rule.
        assertEquals(13, allowedCount(malaysia, 12));
        assertEquals(6, allowedCount(malaysia, 21));
        assertEquals(6, allowedCount(landscapes, 12));
        assertEquals(5, allowedCount(landscapes, 21));
        assertEquals(5, allowedCount(cities, 12));
        assertEquals(5, allowedCount(cities, 21));
        assertEquals(4, allowedCount(space, 12));
        assertEquals(4, allowedCount(space, 21));
        assertEquals(6, allowedCount(oceans, 12));
        assertEquals(5, allowedCount(oceans, 21));
        assertEquals(5, allowedCount(dark, 12));
        assertEquals(15, allowedCount(dark, 21));
        assertEquals(17, allowedCount(monuments, 12));
        assertEquals(9, allowedCount(monuments, 21));
        assertEquals(8, allowedCount(nature, 12));
        assertEquals(8, allowedCount(nature, 21));
        assertEquals(25, allowedCount(legacy, 12));
        assertEquals(17, allowedCount(legacy, 21));
    }

    private static List<OnlineWallpapers.WallpaperItem> exactCategories(String... categories) {
        List<String> wanted = Arrays.asList(categories);
        List<OnlineWallpapers.WallpaperItem> out = new ArrayList<>();
        for (OnlineWallpapers.WallpaperItem item : OnlineWallpapers.catalogItems(null)) {
            if (wanted.contains(item.category)) out.add(item);
        }
        return out;
    }

    private static int matchingCount(List<OnlineWallpapers.WallpaperItem> items, int hour) {
        DayNightWallpapers.Period period = DayNightWallpapers.periodAt(LocalTime.of(hour, 0));
        int count = 0;
        for (OnlineWallpapers.WallpaperItem item : items) {
            if (DayNightWallpapers.matches(item.tone, period)) count++;
        }
        return count;
    }

    private static int allowedCount(List<OnlineWallpapers.WallpaperItem> items, int hour) {
        List<DayNightWallpapers.Photo> photos = new ArrayList<>();
        int index = 0;
        for (OnlineWallpapers.WallpaperItem item : items) {
            photos.add(photo("photo" + (index++) + ".jpg", item.luma, item.tone));
        }
        return DayNightWallpapers.allowed(true, LocalTime.of(hour, 0), photos).size();
    }
}
