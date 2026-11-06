package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperQualityTest {

    private static final String PEXELS =
            "https://images.pexels.com/photos/130576/pexels-photo-130576.jpeg?w=2560&h=1440&fit=crop";
    private static final String PEXELS_UHD =
            "https://images.pexels.com/photos/130576/pexels-photo-130576.jpeg?w=3840&h=2160&fit=crop";
    private static final String UNSPLASH =
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=2560&h=1440&fit=crop&crop=entropy&q=85";
    private static final String UNSPLASH_UHD =
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=3840&h=2160&fit=crop&crop=entropy&q=85";
    private static final String COMMONS =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Tooba_Masjid.jpg/1920px-Tooba_Masjid.jpg";
    private static final String COMMONS_UHD =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Tooba_Masjid.jpg/3840px-Tooba_Masjid.jpg";
    private static final String COMMONS_SMALL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Some_Old_Photo.jpg/1920px-Some_Old_Photo.jpg";

    @Test
    public void a1080pPanelKeepsTodaysTierAndUrls() {
        WallpaperQuality.Choice choice = WallpaperQuality.choose(1920, 1080, 512, false);
        assertFalse(choice.uhd);
        assertEquals(2560, choice.sourceWidth);
        assertEquals(1440, choice.sourceHeight);
        assertEquals(PEXELS, WallpaperQuality.tierUrl(PEXELS, choice.uhd));
        assertEquals(UNSPLASH, WallpaperQuality.tierUrl(UNSPLASH, choice.uhd));
        assertEquals(COMMONS, WallpaperQuality.tierUrl(COMMONS, choice.uhd));
        assertArrayEquals(new int[]{1920, 1080},
                WallpaperQuality.decodeSize(2560, 1440, 1920, 1080, choice.uhd));
    }

    @Test
    public void a720pPanelKeepsTodaysTier() {
        WallpaperQuality.Choice choice = WallpaperQuality.choose(1280, 720, 256, false);
        assertFalse(choice.uhd);
        assertEquals(PEXELS, WallpaperQuality.tierUrl(PEXELS, choice.uhd));
        assertArrayEquals(new int[]{1280, 720},
                WallpaperQuality.decodeSize(2560, 1440, 1280, 720, choice.uhd));
    }

    @Test
    public void a4kPanelWithEnoughHeapAsksFor3840() {
        WallpaperQuality.Choice choice = WallpaperQuality.choose(3840, 2160, 512, false);
        assertTrue(choice.uhd);
        assertEquals(3840, choice.sourceWidth);
        assertEquals(2160, choice.sourceHeight);
        assertEquals(PEXELS_UHD, WallpaperQuality.tierUrl(PEXELS, choice.uhd));
        assertEquals(UNSPLASH_UHD, WallpaperQuality.tierUrl(UNSPLASH, choice.uhd));
        assertEquals(COMMONS_UHD, WallpaperQuality.tierUrl(COMMONS, choice.uhd));
        assertArrayEquals(new int[]{3840, 2160},
                WallpaperQuality.decodeSize(3840, 2160, 3840, 2160, choice.uhd));
    }

    @Test
    public void a4kPanelOnALowRamTvFallsBackToStandard() {
        assertFalse(WallpaperQuality.choose(3840, 2160, 512, true).uhd);
        assertEquals(PEXELS, WallpaperQuality.tierUrl(PEXELS, false));
    }

    @Test
    public void a4kPanelWithoutHeapForTheCrossfadeFallsBack() {
        // 83 MB of crossfade must fit in 45% of the app heap: 128 MB gives only 58 MB.
        assertFalse(WallpaperQuality.choose(3840, 2160, 128, false).uhd);
        assertTrue(WallpaperQuality.choose(3840, 2160, 256, false).uhd);
        assertTrue(WallpaperQuality.choose(3840, 2160, 512, false).uhd);
    }

    @Test
    public void oddPanelSizesPickTheConservativeTier() {
        assertFalse(WallpaperQuality.choose(2560, 1440, 512, false).uhd); // 1440p, not 4K
        assertFalse(WallpaperQuality.choose(3840, 1600, 512, false).uhd); // short 4K-ish
        assertFalse(WallpaperQuality.choose(4000, 2000, 512, false).uhd); // under 2160 high
        assertFalse(WallpaperQuality.choose(720, 480, 512, false).uhd);
        assertFalse(WallpaperQuality.choose(0, 0, 512, false).uhd);
        assertTrue(WallpaperQuality.choose(4096, 2160, 512, false).uhd);
    }

    @Test
    public void peakCrossfadeIsTwoBitmapsPlusTwoBlurs() {
        long expected = 2L * (3840L * 2160L * 4L) + 2L * (1920L * 1080L * 4L);
        assertEquals(expected, WallpaperQuality.peakCrossfadeBytes(3840, 2160));
        assertTrue(expected < 84L * 1024 * 1024);
    }

    @Test
    public void uhdNeverUpscalesASmallerOriginal() {
        assertArrayEquals(new int[]{1920, 1280},
                WallpaperQuality.decodeSize(1920, 1280, 3840, 2160, true));
        assertArrayEquals(new int[]{2560, 1440},
                WallpaperQuality.decodeSize(2560, 1440, 3840, 2160, true));
        assertArrayEquals(new int[]{3840, 2160},
                WallpaperQuality.decodeSize(3840, 2160, 3840, 2160, true));
        // A 1920x1280 photo on a 1080p panel still fills it, as it always did.
        assertArrayEquals(new int[]{1920, 1280},
                WallpaperQuality.decodeSize(1920, 1280, 1920, 1080, false));
    }

    @Test
    public void commonsStaysAt1920WhenNo3840ThumbExists() {
        assertEquals(COMMONS_SMALL, WallpaperQuality.tierUrl(COMMONS_SMALL, true));
        assertEquals(COMMONS_UHD, WallpaperQuality.tierUrl(COMMONS, true));
    }

    @Test
    public void unknownHostsAreNeverRewritten() {
        String feed = "https://example.com/my-wallpaper.jpg";
        assertEquals(feed, WallpaperQuality.tierUrl(feed, true));
        assertNull(WallpaperQuality.tierUrl(null, true));
    }

    @Test
    public void eachTierGetsItsOwnCacheName() {
        assertNotEquals(OnlineWallpapers.hashFilename(PEXELS),
                OnlineWallpapers.hashFilename(WallpaperQuality.tierUrl(PEXELS, true)));
        assertNotEquals(OnlineWallpapers.hashFilename(UNSPLASH),
                OnlineWallpapers.hashFilename(WallpaperQuality.tierUrl(UNSPLASH, true)));
        assertNotEquals(OnlineWallpapers.hashFilename(COMMONS),
                OnlineWallpapers.hashFilename(WallpaperQuality.tierUrl(COMMONS, true)));
        assertEquals(OnlineWallpapers.hashFilename(PEXELS),
                OnlineWallpapers.hashFilename(WallpaperQuality.tierUrl(PEXELS, false)));
    }

    @Test
    public void uhdCacheCapIsNinetySixMegabytes() {
        assertEquals(96L * 1024 * 1024, OnlineWallpapers.MAX_UHD_CACHE_BYTES);
    }
}
