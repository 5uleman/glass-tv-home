package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class OnlineWallpapersTest {

    @Test
    public void pakistanCollectionsMatchTheirOwnItems() {
        assertTrue(OnlineWallpapers.matchesCategory("Pakistan_Monuments", "Pakistan Monuments"));
        assertTrue(OnlineWallpapers.matchesCategory("Pakistan_Nature", "Pakistan Nature"));
        assertFalse(OnlineWallpapers.matchesCategory("Pakistan_Nature", "Pakistan Monuments"));
        assertFalse(OnlineWallpapers.matchesCategory("Pakistan_Monuments", "Pakistan Nature"));
    }

    @Test
    public void oldPakistanKeyStillMeansEveryPakistanPhoto() {
        assertTrue(OnlineWallpapers.matchesCategory("Pakistan_Monuments", "Pakistan"));
        assertTrue(OnlineWallpapers.matchesCategory("Pakistan_Nature", "Pakistan"));
        assertFalse(OnlineWallpapers.matchesCategory("Malaysia_Sabah", "Pakistan"));
    }

    @Test
    public void catalogSplitHasNoOverlapAndCoversEverything() {
        Set<String> monuments = OnlineWallpapers.currentNames("Pakistan Monuments");
        Set<String> nature = OnlineWallpapers.currentNames("Pakistan Nature");
        Set<String> legacy = OnlineWallpapers.currentNames("Pakistan");
        assertEquals(24, monuments.size());
        assertEquals(10, nature.size());
        assertEquals(34, legacy.size());
        for (String name : nature) assertFalse(monuments.contains(name));
    }

    @Test
    public void removedPhotosAreNoLongerReferenced() {
        String goldenMist = "https://images.unsplash.com/photo-1706511920650-b690cd6619e8?w=2560&h=1440&fit=crop&crop=entropy&q=85";
        String faisalDusk = "https://images.pexels.com/photos/16693762/pexels-photo-16693762.jpeg?w=2560&h=1440&fit=crop";
        String eiffel = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Eiffel_Tower_replica_in_Bahria_Town.jpg/1920px-Eiffel_Tower_replica_in_Bahria_Town.jpg";
        String oldAbbasi = "https://images.pexels.com/photos/4253018/pexels-photo-4253018.jpeg?w=2560&h=1440&fit=crop";
        Set<String> monuments = OnlineWallpapers.currentNames("Pakistan Monuments");
        assertFalse(monuments.contains("online_" + Math.abs(goldenMist.hashCode())));
        assertFalse(monuments.contains("online_" + Math.abs(faisalDusk.hashCode())));
        assertFalse(monuments.contains("online_" + Math.abs(eiffel.hashCode())));
        assertFalse(monuments.contains("online_" + Math.abs(oldAbbasi.hashCode())));
    }

    @Test
    public void removedPhotoIsPrunedFromTheCache() throws Exception {
        File dir = File.createTempFile("online_prune", "");
        dir.delete();
        dir.mkdirs();
        String keepUrl = "https://images.pexels.com/photos/5487986/pexels-photo-5487986.jpeg?w=2560&h=1440&fit=crop";
        String dropUrl = "https://images.unsplash.com/photo-1706511920650-b690cd6619e8?w=2560&h=1440&fit=crop&crop=entropy&q=85";
        File keep = new File(dir, "online_" + Math.abs(keepUrl.hashCode()) + ".jpg");
        keep.createNewFile();
        File drop = new File(dir, "online_" + Math.abs(dropUrl.hashCode()) + ".jpg");
        drop.createNewFile();
        File dropTxt = new File(dir, "online_" + Math.abs(dropUrl.hashCode()) + ".txt");
        try (FileOutputStream os = new FileOutputStream(dropTxt)) {
            os.write("Old\nOld\nPakistan_Monuments\n".getBytes(StandardCharsets.UTF_8));
        }

        try {
            OnlineWallpapers.cleanOutdatedCategoryWallpapers(dir, "Pakistan Monuments");
        } catch (RuntimeException ignored) {
            // android.util.Log is not mocked in plain JVM tests; the delete happens first.
        }

        assertTrue(keep.exists());
        assertFalse(drop.exists());
        assertFalse(dropTxt.exists());
        keep.delete();
        dir.delete();
    }

    @Test
    public void catalogCategoryReadsTheSplitFromAStaleCacheName() {
        String badshahi = "https://images.pexels.com/photos/5487986/pexels-photo-5487986.jpeg?w=2560&h=1440&fit=crop";
        String rawal = "https://images.pexels.com/photos/38459967/pexels-photo-38459967.jpeg?w=2560&h=1440&fit=crop";
        assertEquals("Pakistan_Monuments", OnlineWallpapers.catalogCategory(
                "online_" + Math.abs(badshahi.hashCode())));
        assertEquals("Pakistan_Nature", OnlineWallpapers.catalogCategory(
                "online_" + Math.abs(rawal.hashCode())));
        assertNull(OnlineWallpapers.catalogCategory("online_123"));
    }

    @Test
    public void anUpgradeShowsTheCurrentCatalogTitleNotTheCachedOne() throws Exception {
        String faisal = "https://images.pexels.com/photos/38383016/pexels-photo-38383016.jpeg?w=2560&h=1440&fit=crop";
        String base = "online_" + Math.abs(faisal.hashCode());
        File dir = File.createTempFile("online_names", "");
        dir.delete();
        dir.mkdirs();

        File jpg = new File(dir, base + ".jpg");
        jpg.createNewFile();
        File txt = new File(dir, base + ".txt");
        try (FileOutputStream os = new FileOutputStream(txt)) {
            os.write("Faisal Mosque at Blue Hour\nThe old description\nPakistan_Monuments\nIslamabad, Pakistan\n"
                    .getBytes(StandardCharsets.UTF_8));
        }

        String[] about = OnlineWallpapers.describe(jpg);
        assertNotNull(about);
        assertEquals("a stale .txt must not keep the old title", "Faisal Mosque", about[0]);
        assertEquals("Pakistan_Monuments", about[2]);
        assertEquals("Islamabad, Pakistan", about[3]);

        jpg.delete();
        txt.delete();
        dir.delete();
    }

    @Test
    public void bothGrandJamiaPhotosShareTheNewNameAndBahriaTownDescription() {
        String night = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Grand_Jamia_Masjid.jpg/1920px-Grand_Jamia_Masjid.jpg";
        String arcade = "https://images.pexels.com/photos/23961877/pexels-photo-23961877.jpeg?w=2560&h=1440&fit=crop";

        OnlineWallpapers.WallpaperItem a = OnlineWallpapers.catalogItem(
                "online_" + Math.abs(night.hashCode()));
        OnlineWallpapers.WallpaperItem b = OnlineWallpapers.catalogItem(
                "online_" + Math.abs(arcade.hashCode()));
        assertNotNull(a);
        assertNotNull(b);
        assertEquals("Grand Jamia Mosque", a.title);
        assertEquals("Grand Jamia Mosque", b.title);
        assertEquals("Lahore, Pakistan", a.place);
        assertEquals("Lahore, Pakistan", b.place);
        assertTrue(a.description.contains("Bahria Town, Lahore"));
        assertTrue(b.description.contains("Bahria Town, Lahore"));
    }

    @Test
    public void describeReadsCompanionTextFile() throws Exception {
        File dir = File.createTempFile("online_test", "");
        dir.delete();
        dir.mkdirs();

        File jpg = new File(dir, "photo1.jpg");
        jpg.createNewFile();

        File txt = new File(dir, "photo1.txt");
        try (FileOutputStream os = new FileOutputStream(txt)) {
            os.write("Moody Alpine Mountains\nDark 4K Landscape\nUnsplash\n".getBytes(StandardCharsets.UTF_8));
        }

        String[] about = OnlineWallpapers.describe(jpg);
        assertNotNull(about);
        assertEquals(4, about.length);
        assertEquals("Moody Alpine Mountains", about[0]);
        assertEquals("Dark 4K Landscape", about[1]);
        assertEquals("Unsplash", about[2]);
        assertEquals("", about[3]);

        jpg.delete();
        txt.delete();
        dir.delete();
    }
}
