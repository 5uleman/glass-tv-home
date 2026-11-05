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
        assertEquals(21, monuments.size());
        assertEquals(10, nature.size());
        assertEquals(31, legacy.size());
        for (String name : nature) assertFalse(monuments.contains(name));
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
