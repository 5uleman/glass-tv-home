package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wallpaper manager for the launcher's 1920x1080 screen. Catalog pictures are fetched at
 * 2560x1440 from Pexels and Unsplash and decoded down to the screen size; a web-server feed
 * may supply any size. Caches wallpapers locally for instant loading and offline resilience.
 */
final class OnlineWallpapers {
    private static final String TAG = "TvHome.Online";
    static final String DIR = "online-wallpapers";
    private static final String PREFS_NAME = "online_wallpapers";
    private static final String PREF_LAST_SYNC = "last_sync_time";
    private static final String PREF_TIER_UHD = "tier_uhd";
    private static final long REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final int MAX_CACHE_FILES = 150;
    /** The 4K tier is capped by total bytes instead of file count: about 85 photos at the measured average. */
    static final long MAX_UHD_CACHE_BYTES = 96L * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024; // 25 MB limit per image

    /** Set once at startup from {@link WallpaperQuality#choose}; read by sync and prune threads. */
    private static volatile boolean uhdTier;

    /**
     * Applies the tier once per process. When the panel or heap changed the last visit, the next
     * sync is due immediately and the old tier's cached files are pruned once the new ones arrive.
     */
    static void configure(Context ctx, boolean uhd) {
        uhdTier = uhd;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (sp.getBoolean(PREF_TIER_UHD, false) != uhd) {
            sp.edit().putBoolean(PREF_TIER_UHD, uhd).putLong(PREF_LAST_SYNC, 0).apply();
        }
    }

    static boolean isUhd() {
        return uhdTier;
    }

    static class WallpaperItem {
        final String url;
        final String title;
        final String description;
        final String category;
        /** The place shown under the title ("Lahore, Pakistan"); empty for collections that show a category. */
        final String place;
        /** Mean luma measured on the 2560x1440 download; 0 when a picture has no tone data. */
        final double luma;
        /** Which rotations this picture suits; BOTH for pictures without tone data. */
        final DayNightWallpapers.Tone tone;
        /** True only when the catalog entry supplied a measured luma and an explicit tone. */
        final boolean explicitTag;

        WallpaperItem(String url, String title, String description, String category) {
            this(url, title, description, category, "");
        }

        WallpaperItem(String url, String title, String description, String category, String place) {
            this(url, title, description, category, place, 0, DayNightWallpapers.Tone.BOTH, false);
        }

        WallpaperItem(String url, String title, String description, String category, String place,
                      double luma, DayNightWallpapers.Tone tone) {
            this(url, title, description, category, place, luma, tone, true);
        }

        private WallpaperItem(String url, String title, String description, String category, String place,
                              double luma, DayNightWallpapers.Tone tone, boolean explicitTag) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.category = category;
            this.place = place;
            this.luma = luma;
            this.tone = tone;
            this.explicitTag = explicitTag;
        }
    }

    // Built-in curated catalog of dark, moody, atmospheric 4K desktop wallpapers (Unsplash)
    private static final WallpaperItem[] CURATED_CATALOG = new WallpaperItem[]{
            // Malaysia: Tempat Menarik di Malaysia (16 Koleksi Pexels 4K Ultra HD)
            new WallpaperItem("https://images.pexels.com/photos/40168956/pexels-photo-40168956.jpeg?w=2560&h=1440&fit=crop",
                    "Borneo Rainforest Orangutan", "Iconic wildlife species in the canopy of Borneo's tropical rainforest, a natural treasure", "Malaysia_Sabah", "", 87.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/36767161/pexels-photo-36767161.jpeg?w=2560&h=1440&fit=crop",
                    "Kuala Lumpur Sunset Silhouette", "The capital's metropolitan skyline set against the glow of saga red and orange at dusk", "Malaysia_WP", "", 108.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/338416/pexels-photo-338416.jpeg?w=2560&h=1440&fit=crop",
                    "Putra Mosque & Putrajaya Lake", "The beauty of Putra Mosque's pink dome reflecting gracefully on the calm lake surface", "Malaysia_Putrajaya", "", 140.9, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/33676568/pexels-photo-33676568.jpeg?w=2560&h=1440&fit=crop",
                    "KLCC Twin Towers Peak", "The Petronas Twin Towers rising majestically through a sea of golden clouds on the Kuala Lumpur horizon", "Malaysia_WP", "", 109.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/39690291/pexels-photo-39690291.jpeg?w=2560&h=1440&fit=crop",
                    "Straits of Malacca Floating Mosque", "The famous floating mosque on the Straits of Malacca coast, lit by sparkling night lights and a copper sky", "Malaysia_Melaka", "", 117.5, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/33708542/pexels-photo-33708542.jpeg?w=2560&h=1440&fit=crop",
                    "Kundasang Mountain Chalet", "A unique dwelling on green hillsides set against the cool air of Sabah's highland range", "Malaysia_Sabah", "", 114.1, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/14776919/pexels-photo-14776919.jpeg?w=2560&h=1440&fit=crop",
                    "Winding Road of the Titiwangsa Range", "A winding route through green mountain ridges shrouded in thick fog at dawn", "Malaysia_Pahang", "", 117.1, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/5224545/pexels-photo-5224545.jpeg?w=2560&h=1440&fit=crop",
                    "Dawn over the Pahang River Valley", "Golden morning sunlight spilling over a serene river and hillside villages", "Malaysia_Pahang", "", 108.1, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/11643318/pexels-photo-11643318.jpeg?w=2560&h=1440&fit=crop",
                    "Red Sunset over the River Valley", "A sunset panorama refracting copper and orange hues along the river bends and rolling hills", "Malaysia_Pahang", "", 71.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/28727775/pexels-photo-28727775.jpeg?w=2560&h=1440&fit=crop",
                    "Tanjung Balau Coastal Shore", "Crystal blue sea and a distinctive rocky cape in Desaru facing the South China Sea", "Malaysia_Johor", "", 100.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/38188372/pexels-photo-38188372.jpeg?w=2560&h=1440&fit=crop",
                    "Terengganu Drawbridge", "The iconic Kuala Terengganu drawbridge bathed in the glow of the setting sun", "Malaysia_Terengganu", "", 131.4, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/13890758/pexels-photo-13890758.jpeg?w=2560&h=1440&fit=crop",
                    "Royal Town of Kuala Kangsar", "Aerial view of a charming heritage town on the banks of the Perak River framed by tropical greenery", "Malaysia_Perak", "", 67.0, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/139974/pexels-photo-139974.jpeg?w=2560&h=1440&fit=crop",
                    "Fisherman Boats with Jalur Gemilang", "Traditional fishing boats anchored calmly at the river mouth under a pink-tinted dusk sky", "Malaysia_Terengganu", "", 133.8, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/5649468/pexels-photo-5649468.jpeg?w=2560&h=1440&fit=crop",
                    "Shah Alam State Mosque", "The majestic blue dome and four illuminated minarets of Sultan Salahuddin Abdul Aziz Shah Mosque at night", "Malaysia_Selangor", "", 52.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/130576/pexels-photo-130576.jpeg?w=2560&h=1440&fit=crop",
                    "Cameron Highlands Tea Plantation", "Rolling fresh green tea hills under a clear blue sky in the highlands of the Pahang range", "Malaysia_Pahang", "", 102.3, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/35905094/pexels-photo-35905094.jpeg?w=2560&h=1440&fit=crop",
                    "Decorated Heritage Trishaw of Malacca", "The unique flower-decorated tourist trishaw and cheerful tunes roaming the historic heritage lanes of Malacca", "Malaysia_Melaka", "", 104.3, DayNightWallpapers.Tone.DAY),

            // Nature & Landscapes (Alam Semula Jadi - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1519681393784-d120267933ba?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Moody Alpine Mountains", "Silhouetted mountain peaks under starry skies", "Landscape", "", 79.6, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/1366919/pexels-photo-1366919.jpeg?w=2560&h=1440&fit=crop",
                    "Moody Mountain Fog", "Mist rolling through dense mountain evergreen pine forest", "Landscape", "", 143.8, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1531366936337-7c912a4589a7?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Aurora Borealis", "Green atmospheric aurora dancing across arctic skies", "Nature", "", 80.2, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/1624496/pexels-photo-1624496.jpeg?w=2560&h=1440&fit=crop",
                    "Starry Night Over Pines", "Bright starry cosmos illuminating tall silhouette pine forest", "Nature", "", 42.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1448375240586-882707db888b?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Misty Pine Forest", "Dense evergreen canopy cloaked in rolling fog", "Forest", "", 75.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/1287145/pexels-photo-1287145.jpeg?w=2560&h=1440&fit=crop",
                    "Serene Alpine Twilight", "Calm dusk sky casting reflections across quiet mountain lake", "Nature", "", 155.2, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Moody Lake Twilight", "Still dark waters reflecting misty mountain ridges", "Landscape", "", 107.9, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Desert Dunes", "Shadows sweeping over desert dunes under the night sky", "Desert", "", 118.9, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/3408744/pexels-photo-3408744.jpeg?w=2560&h=1440&fit=crop",
                    "Majestic Snow Mountain Peak", "Crisp dramatic snow ridges against deep blue atmosphere", "Mountains", "", 105.9, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Volcanic Landscape", "Black volcanic terrain and quiet minimalist horizon", "Nature", "", 112.2, DayNightWallpapers.Tone.DAY),

            // Cityscape & Architecture (Pencakar Langit & Bandar - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Tokyo Cyberpunk Alley", "Moody neon reflections in a narrow Tokyo alleyway", "Cityscape", "", 140.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/374870/pexels-photo-374870.jpeg?w=2560&h=1440&fit=crop",
                    "Metropolis Skyline Twilight", "Glowing skyscrapers towering over serene bay water", "Cityscape", "", 145.1, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1514565131-fce0801e5785?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Neon Rain Street", "Vibrant reflections shimmering on wet asphalt", "Cityscape", "", 131.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/3052361/pexels-photo-3052361.jpeg?w=2560&h=1440&fit=crop",
                    "Modern Glass Skyscraper", "Geometric glass architectural patterns rising into sky", "Architecture", "", 26.3, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1653142267767-a66c7cf17bc1?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Cyberpunk Neon Night", "Futuristic high-density city streets glowing in vibrant magenta", "Cityscape", "", 82.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1494548162494-384bba4ab999?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Foggy Suspension Bridge", "Golden Gate towers emerging through evening fog", "Architecture", "", 115.8, DayNightWallpapers.Tone.DAY),

            // Space & Astronomy (Angkasa & Kosmos - Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Night Earth Orbit", "Planet Earth illuminated at night from outer space", "Space", "", 25.7, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Milky Way Galaxy", "Deep night sky filled with stellar constellations", "Astronomy", "", 30.2, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Deep Space Nebula", "Cosmic interstellar gas clouds and distant stars", "Cosmos", "", 134.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Cosmic Dark Starfield", "Deep black void speckled with distant galaxies", "Space", "", 54.5, DayNightWallpapers.Tone.NIGHT),

            // Ocean & Water (Lautan & Pantai - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Deep Underwater Abyss", "Sunlight penetrating into the dark deep ocean", "Underwater", "", 67.6, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Midnight Ocean Waves", "Deep blue waves crashing on a tranquil dark shore", "Oceans", "", 185.2, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/1001682/pexels-photo-1001682.jpeg?w=2560&h=1440&fit=crop",
                    "Deep Ocean Wave Crest", "Powerful sea water curling in turquoise deep shades", "Oceans", "", 149.3, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/1032650/pexels-photo-1032650.jpeg?w=2560&h=1440&fit=crop",
                    "Tropical Turquoise Deep Water", "Aerial view of calm ocean reef gradient", "Oceans", "", 165.2, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Ocean Trench", "Bioluminescent glow illuminating the deep ocean", "Underwater", "", 121.9, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/1295138/pexels-photo-1295138.jpeg?w=2560&h=1440&fit=crop",
                    "Dramatic Sea Cliffs", "Dark oceanic cliffs meeting moody breaking waves", "Coast", "", 132.9, DayNightWallpapers.Tone.BOTH),

            // Minimalist & Atmospheric (Minimalis Gelap OLED - Unsplash 4K Curated from https://unsplash.com/s/photos/dark-wallpaper)
            new WallpaperItem("https://images.unsplash.com/photo-1484950763426-56b5bf172dbb?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "4x4 Vehicle Silhouette", "Silhouette of a four-wheel-drive vehicle beneath a vast, quiet night sky of stars", "Minimalist_OLED", "", 17.7, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1533134486753-c833f0ed4866?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Grey Sand Waves", "Misty grey sand curves along the Karekare coastline of New Zealand", "Minimalist_OLED", "", 20.7, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1762417108293-91614140073a?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "OLED Black Cylinder Texture", "Minimalist dark 3D black cylinder surface architecture", "Minimalist_OLED", "", 11.6, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1507752533523-5186b0bc4c43?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Golden Twilight Skyscrapers", "Montreal metropolitan skyline bathed in golden twilight reflections", "Minimalist_OLED", "", 33.0, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1470813740244-df37b8c1edcb?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Blue Starry Night", "The beauty of the galaxy and blue star clusters beyond a rocky gorge", "Minimalist_OLED", "", 20.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1509023464722-18d996393ca8?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Mount Fuji & Silent Lake", "Mount Fuji's silhouette over a silent Japanese lake against a deep dark sky", "Minimalist_OLED", "", 74.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1491466424936-e304919aada7?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "OLED Black Sand Waves", "Minimalist black dune curves with perfect shadow contrast", "Minimalist_OLED", "", 38.3, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1622737133809-d95047b9e673?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Polygon Geometry", "High-dimensional monochrome 3D geometric architecture for OLED screens", "Minimalist_OLED", "", 33.8, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Silky Black Liquid Flow", "Glossy black liquid waves flowing gracefully in absolute darkness", "Minimalist_OLED", "", 124.2, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Minimalist Dark Grid", "Modern perspective line structure against a deep black background", "Minimalist_OLED", "", 147.6, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1507499739999-097706ad8914?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Night Monolith Gateway", "Minimalist concrete architecture standing tall under the depth of a quiet night", "Minimalist_OLED", "", 60.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1534447677768-be436bb09401?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Monochrome Mountain Mist", "Misty mountain ranges in dramatic black-and-white tones", "Minimalist_OLED", "", 55.3, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1518770660439-4636190af475?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Cybernetic Circuit", "Advanced technology circuit board in minimalist dark lighting", "Minimalist_OLED", "", 79.3, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Pigment Scatter", "Explosion of fine, highly textured charcoal-black dust particles", "Minimalist_OLED", "", 73.8, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Classic Charcoal Canvas", "Dark oil painting art texture in monochrome charcoal tones", "Minimalist_OLED", "", 81.8, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1519751138087-5bf79df62d5b?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Monstera Leaf Silhouette", "Dark tropical leaves against a mysterious black shadow backdrop", "Minimalist_OLED", "", 190.5, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.unsplash.com/photo-1511447333015-45b65e60f6d5?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Dark Light Prism", "Delicate neon light spectrum radiating through absolute darkness", "Minimalist_OLED", "", 39.7, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Pure Black OLED Texture", "Minimalist charcoal-black surface designed to save OLED pixels", "Minimalist_OLED", "", 25.1, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.unsplash.com/photo-1491557345352-5929e343eb89?w=2560&h=1440&fit=crop&crop=entropy&q=85",
                    "Obsidian Mountain Peak", "Sharp rocky mountain peaks piercing dramatic black storm clouds", "Minimalist_OLED", "", 135.9, DayNightWallpapers.Tone.DAY),

            // Pakistan: two collections. Monuments holds landmarks, mosques, forts, historic
            // buildings and city skylines; Nature holds the northern valleys, lakes and the coast.
            // The fourth value is the place line under the title; the third stays the category.
            // Commons files are served at 1920 px from originals of at least 3840 px, the only
            // thumbnail width its CDN still returns for these photos.
            // Monuments
            new WallpaperItem("https://images.pexels.com/photos/5487986/pexels-photo-5487986.jpeg?w=2560&h=1440&fit=crop",
                    "Badshahi Mosque", "The lit Mughal mosque beneath the Milky Way, with Minar-e-Pakistan on the horizon", "Pakistan_Monuments", "Lahore, Pakistan", 34.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/11774127/pexels-photo-11774127.jpeg?w=2560&h=1440&fit=crop",
                    "Alamgiri Gate", "The gateway into Lahore Fort above its lawns, with the white pavilion to the right", "Pakistan_Monuments", "Lahore, Pakistan", 99.4, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/16021536/pexels-photo-16021536.jpeg?w=2560&h=1440&fit=crop",
                    "Lahore at Night", "Light trails along the canal and the lit Arfa Software Technology Park tower", "Pakistan_Monuments", "Lahore, Pakistan", 62.9, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/38773558/pexels-photo-38773558.jpeg?w=2560&h=1440&fit=crop",
                    "Islamabad from Above", "Green avenues running toward the Margalla Hills, with Faisal Mosque in the distance", "Pakistan_Monuments", "Islamabad, Pakistan", 87.4, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/34096455/pexels-photo-34096455.jpeg?w=2560&h=1440&fit=crop",
                    "Karachi at Night", "Dense city lights spreading to the horizon after dark", "Pakistan_Monuments", "Karachi, Pakistan", 41.8, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/33045005/pexels-photo-33045005.jpeg?w=2560&h=1440&fit=crop",
                    "Karachi Skyline", "High-rise apartment blocks and tree-lined streets seen from above", "Pakistan_Monuments", "Karachi, Pakistan", 112.5, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/815880/pexels-photo-815880.jpeg?w=2560&h=1440&fit=crop",
                    "Derawar Fort", "The fort's round towers rising from the Cholistan desert at sunset", "Pakistan_Monuments", "Bahawalpur, Pakistan", 94.7, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/34535290/pexels-photo-34535290.jpeg?w=2560&h=1440&fit=crop",
                    "Makli Necropolis", "Carved stone columns along a corridor of tombs at Makli", "Pakistan_Monuments", "Thatta, Pakistan", 94.8, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/14926132/pexels-photo-14926132.jpeg?w=2560&h=1440&fit=crop",
                    "Lahore Fort", "The fort's walls and domes under a warm sky, with the flag on the rampart", "Pakistan_Monuments", "Lahore, Pakistan", 148.4, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/13239874/pexels-photo-13239874.jpeg?w=2560&h=1440&fit=crop",
                    "Minar-e-Pakistan", "The minaret rising above its lawns and trees under a clear sky", "Pakistan_Monuments", "Lahore, Pakistan", 159.1, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/14394126/pexels-photo-14394126.jpeg?w=2560&h=1440&fit=crop",
                    "Pakistan Monument", "The lit petals of the monument around its central star", "Pakistan_Monuments", "Islamabad, Pakistan", 54.3, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/31377718/pexels-photo-31377718.jpeg?w=2560&h=1440&fit=crop",
                    "Abbasi Mosque, Ahmedpur East", "The white domes and four minarets of the mosque above its courtyard", "Pakistan_Monuments", "Ahmedpur East, Pakistan", 162.2, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/12912453/pexels-photo-12912453.jpeg?w=2560&h=1440&fit=crop",
                    "Badshahi Mosque", "The red sandstone mosque and its domes under a pink evening sky", "Pakistan_Monuments", "Lahore, Pakistan", 123.3, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Tooba_Masjid.jpg/1920px-Tooba_Masjid.jpg",
                    "Tooba Masjid", "The white dome and minarets reflected in the pool at blue hour", "Pakistan_Monuments", "Karachi, Pakistan", 141.4, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/The_Scenic_Hazuri_Bagh_Baradari.jpg/1920px-The_Scenic_Hazuri_Bagh_Baradari.jpg",
                    "Hazuri Bagh Baradari", "The white baradari at the end of its garden walk, between flower beds", "Pakistan_Monuments", "Lahore, Pakistan", 117.1, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/38383016/pexels-photo-38383016.jpeg?w=2560&h=1440&fit=crop",
                    "Faisal Mosque", "The lit mosque and its minarets reflected on wet marble", "Pakistan_Monuments", "Islamabad, Pakistan", 66.2, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Mohatta_Palace_2016.jpg/1920px-Mohatta_Palace_2016.jpg",
                    "Mohatta Palace", "The pink stone palace and its domed towers above the lawn", "Pakistan_Monuments", "Karachi, Pakistan", 149.5, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/35734282/pexels-photo-35734282.jpeg?w=2560&h=1440&fit=crop",
                    "Lahore Railway Station", "The station's twin clock towers and red-brick front above the forecourt", "Pakistan_Monuments", "Lahore, Pakistan", 121.5, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Grand_Jamia_Masjid.jpg/1920px-Grand_Jamia_Masjid.jpg",
                    "Grand Jamia Mosque", "The lit dome, minarets and fountains of the mosque in Bahria Town, Lahore, after dark", "Pakistan_Monuments", "Lahore, Pakistan", 57.6, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/23961877/pexels-photo-23961877.jpeg?w=2560&h=1440&fit=crop",
                    "Grand Jamia Mosque", "Brick arcade and dome of the Grand Jamia Mosque in Bahria Town, Lahore", "Pakistan_Monuments", "Lahore, Pakistan", 99.0, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/14406067/pexels-photo-14406067.jpeg?w=2560&h=1440&fit=crop",
                    "Badshahi Mosque", "The mosque's red sandstone facade and domes in close view", "Pakistan_Monuments", "Lahore, Pakistan", 149.6, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/Frere_Hall_Karachi_City.jpg/1920px-Frere_Hall_Karachi_City.jpg",
                    "Frere Hall", "The Gothic facade and arcade under a deep blue sky", "Pakistan_Monuments", "Karachi, Pakistan", 61.4, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/8/86/Mazar-e-Quaid_Karachi.jpg/1920px-Mazar-e-Quaid_Karachi.jpg",
                    "Mazar-e-Quaid", "The white mausoleum beneath a broken cloud sky", "Pakistan_Monuments", "Karachi, Pakistan", 98.1, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.unsplash.com/photo-1696691907658-be7db3beae7b?w=2560&h=1440&fit=crop&crop=entropy&q=75",
                    "Old Tollinton Market", "The long roofline and clock towers of the building under a pale sky", "Pakistan_Monuments", "Lahore, Pakistan", 136.6, DayNightWallpapers.Tone.DAY),
            // Nature
            new WallpaperItem("https://images.pexels.com/photos/38459967/pexels-photo-38459967.jpeg?w=2560&h=1440&fit=crop",
                    "Rawal Lake", "The sun setting behind the Margalla Hills, reflected across Rawal Lake", "Pakistan_Nature", "Islamabad, Pakistan", 94.9, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/28244078/pexels-photo-28244078.jpeg?w=2560&h=1440&fit=crop",
                    "Clifton Beach", "A horse and its handler on the wet sand at dusk, in black and white", "Pakistan_Nature", "Karachi, Pakistan", 117.7, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/19442083/pexels-photo-19442083.jpeg?w=2560&h=1440&fit=crop",
                    "Hunza Valley in Autumn", "Snow peaks above the Hunza valley and its autumn poplar groves", "Pakistan_Nature", "Hunza, Pakistan", 72.5, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/15916726/pexels-photo-15916726.jpeg?w=2560&h=1440&fit=crop",
                    "Hunza Valley", "Clouds and low light over the Hunza valley, with Rakaposhi's snows beyond", "Pakistan_Nature", "Hunza, Pakistan", 93.3, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/35302563/pexels-photo-35302563.jpeg?w=2560&h=1440&fit=crop",
                    "Golden Peaks of Hunza", "A snow ridge catching the last sunlight above the valley floor", "Pakistan_Nature", "Hunza, Pakistan", 75.6, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/8835247/pexels-photo-8835247.jpeg?w=2560&h=1440&fit=crop",
                    "Passu Cones", "The jagged Passu Cones rising above the dry Hunza valley", "Pakistan_Nature", "Hunza, Pakistan", 94.7, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/13462643/pexels-photo-13462643.jpeg?w=2560&h=1440&fit=crop",
                    "Attabad Lake", "Turquoise water and moored boats between the Karakoram slopes", "Pakistan_Nature", "Hunza, Pakistan", 133.6, DayNightWallpapers.Tone.DAY),
            new WallpaperItem("https://images.pexels.com/photos/29401243/pexels-photo-29401243.jpeg?w=2560&h=1440&fit=crop",
                    "Nanga Parbat at Sunrise", "Dawn light on the snow slopes of Nanga Parbat", "Pakistan_Nature", "Diamer, Pakistan", 92.5, DayNightWallpapers.Tone.BOTH),
            new WallpaperItem("https://images.pexels.com/photos/186985/pexels-photo-186985.jpeg?w=2560&h=1440&fit=crop",
                    "Swat Valley", "Fields and forested hills along the Swat river under a storm-dark sky", "Pakistan_Nature", "Swat, Pakistan", 66.6, DayNightWallpapers.Tone.NIGHT),
            new WallpaperItem("https://images.pexels.com/photos/36870354/pexels-photo-36870354.jpeg?w=2560&h=1440&fit=crop",
                    "Karakoram Peaks", "Rock spires and snow along a Karakoram ridge", "Pakistan_Nature", "Gilgit-Baltistan, Pakistan", 101.9, DayNightWallpapers.Tone.BOTH)
    };

    private OnlineWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base != null ? new File(base, DIR) : null;
    }

    /** Returns all valid downloaded wallpapers in sorted order. */
    static List<File> list(Context ctx) {
        List<File> out = new ArrayList<>();
        File d = dir(ctx);
        File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public static boolean matchesCategory(String itemCat, String selectedCat) {
        if (selectedCat == null || selectedCat.isEmpty() || "All".equalsIgnoreCase(selectedCat)) return true;
        if (itemCat == null) return false;
        String ic = itemCat.toLowerCase(Locale.ROOT);
        String sc = selectedCat.toLowerCase(Locale.ROOT);
        // The old "Pakistan" key stays valid for anyone who still has it selected: it means
        // every Pakistan photo. The two new keys split it by content.
        if (sc.equals("pakistan")) return ic.startsWith("pakistan_");
        if (sc.startsWith("pakistan") && sc.contains("monument")) return ic.equals("pakistan_monuments");
        if (sc.startsWith("pakistan") && sc.contains("nature")) return ic.equals("pakistan_nature");
        if (sc.startsWith("malaysia_")) {
            String state = sc.substring("malaysia_".length());
            return ic.contains(state);
        }
        if (sc.equals("malaysia") || sc.equals("malaysia_all")) {
            return ic.contains("malay");
        }
        if (sc.contains("sky") || sc.contains("arch") || sc.contains("city")) {
            return ic.contains("sky") || ic.contains("arch") || ic.contains("city") || ic.contains("urban");
        }
        if (sc.contains("nat") || sc.contains("land")) {
            return ic.contains("nat") || ic.contains("land") || ic.contains("mount") || ic.contains("forest") || ic.contains("desert");
        }
        if (sc.contains("space") || sc.contains("cosmo") || sc.contains("astro")) {
            return ic.contains("space") || ic.contains("astro") || ic.contains("cosmo");
        }
        if (sc.contains("ocean") || sc.contains("water") || sc.contains("coast")) {
            return ic.contains("ocean") || ic.contains("underwater") || ic.contains("coast");
        }
        if (sc.contains("minim") || sc.contains("oled") || sc.contains("dark")) {
            return ic.contains("minim") || ic.contains("oled") || ic.contains("dark") || ic.contains("atmos") || ic.contains("weather");
        }
        if (sc.contains("malay")) {
            return ic.contains("malay");
        }
        return ic.contains(sc);
    }

    /** Cache file names (without extension) the catalog asks for at the active tier, optionally one category only. */
    static Set<String> currentNames(String category) {
        Set<String> names = new HashSet<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (category == null || matchesCategory(item.category, category)) {
                names.add(hashFilename(WallpaperQuality.tierUrl(item.url, uhdTier)));
            }
        }
        return names;
    }

    /**
     * The catalog's current category for a cached file, looked up by the URL hash in its name.
     * Old caches keep the category they were written with; this reads the split categories
     * without downloading the same picture again. Null when the catalog no longer lists it.
     */
    static String catalogCategory(String cacheBaseName) {
        WallpaperItem item = catalogItem(cacheBaseName);
        return item != null ? item.category : null;
    }

    /** The catalog entry a cached file came from, at either tier, or null for feed pictures and unknown files. */
    static WallpaperItem catalogItem(String cacheBaseName) {
        for (WallpaperItem item : CURATED_CATALOG) {
            if (hashFilename(item.url).equals(cacheBaseName)
                    || hashFilename(WallpaperQuality.tierUrl(item.url, true)).equals(cacheBaseName)) {
                return item;
            }
        }
        return null;
    }

    /** Catalog entries matching a picker key; feed and uploaded pictures are never part of it. */
    static List<WallpaperItem> catalogItems(String category) {
        List<WallpaperItem> out = new ArrayList<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (category == null || matchesCategory(item.category, category)) out.add(item);
        }
        return out;
    }

    /**
     * The tone data for a cached picture. Pictures outside the catalog (uploads, feed) are BOTH,
     * so day/night filtering never hides them.
     */
    static DayNightWallpapers.Photo tagged(File picture) {
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        WallpaperItem item = catalogItem(base);
        if (item == null) {
            return new DayNightWallpapers.Photo(picture, 0, DayNightWallpapers.Tone.BOTH);
        }
        return new DayNightWallpapers.Photo(picture, item.luma, item.tone);
    }

    static void cleanOutdatedCategoryWallpapers(File d, String category) {
        if (d == null || !d.exists()) return;
        // Drop pictures the catalog no longer lists even when no download follows, so a removed
        // photo is pruned the first time a collection is opened. Feed pictures are kept.
        pruneUnreferenced(d);
        Set<String> validHashes = currentNames(category);
        File[] files = d.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        // After a tier change the cache may still hold only the other tier's files. Keep them as a
        // fallback until at least one file of the active tier has arrived.
        boolean anyCurrentTier = false;
        for (File f : files) {
            if (validHashes.contains(f.getName().replace(".jpg", ""))) {
                anyCurrentTier = true;
                break;
            }
        }
        if (!anyCurrentTier) return;
        for (File f : files) {
            String base = f.getName().replace(".jpg", "");
            if (!validHashes.contains(base)) {
                String[] desc = describe(f);
                if (desc != null && desc.length >= 3 && matchesCategory(desc[2], category)) {
                    delete(f, d, base);
                    Log.i(TAG, "Removed outdated " + category + " wallpaper: " + f.getName());
                }
            }
        }
    }

    /**
     * Deletes cached pictures no catalog entry points at any more (an old URL size, a replaced
     * photo), so the rotation can never show them. Pictures from a web-server feed are kept: the
     * catalog does not list those. Does nothing unless at least one picture the catalog still
     * lists is on disk, so a bad edit can never empty the cache.
     */
    static void pruneUnreferenced(File d) {
        if (d == null || !d.exists()) return;
        Set<String> current = currentNames(null);
        File[] files = d.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        int kept = 0;
        for (File f : files) {
            if (current.contains(f.getName().replace(".jpg", "")) || isFeed(f)) kept++;
        }
        if (kept == 0) return;
        for (File f : files) {
            String base = f.getName().replace(".jpg", "");
            if (current.contains(base) || isFeed(f)) continue;
            delete(f, d, base);
            Log.i(TAG, "Removed unreferenced wallpaper: " + f.getName());
        }
    }

    /** A picture fetched from a web-server feed: the curated catalog never lists those. */
    private static boolean isFeed(File picture) {
        String[] desc = describe(picture);
        return desc != null && desc.length >= 3 && "Web Server".equals(desc[2]);
    }

    private static void delete(File picture, File d, String base) {
        picture.delete();
        File txt = new File(d, base + ".txt");
        if (txt.exists()) txt.delete();
    }

    static List<File> listByCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d != null && d.exists()) {
            cleanOutdatedCategoryWallpapers(d, category);
        }
        List<File> all = list(ctx);
        if (category == null || category.equalsIgnoreCase("All")) return all;
        List<File> filtered = new ArrayList<>();
        for (File f : all) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String cat = catalogCategory(base);
            String[] desc = cat == null ? describe(f) : null;
            if (cat == null && desc != null && desc.length >= 3) {
                // A Pakistan file the catalog no longer lists is an old URL (a replaced photo):
                // hide it even before the next sync prunes it, so legacy "Pakistan" users do not
                // see the old and the new picture side by side.
                if (desc[2].toLowerCase(Locale.ROOT).startsWith("pakistan_")) continue;
                cat = desc[2];
            }
            if (cat != null && matchesCategory(cat, category)) {
                filtered.add(f);
            }
        }
        return filtered;
    }

    static boolean syncCategory(Context ctx, String category) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        cleanOutdatedCategoryWallpapers(d, category);

        List<WallpaperItem> candidates = new ArrayList<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (matchesCategory(item.category, category)) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) return false;

        boolean anyDownloaded = false;
        int targetNew = candidates.size();
        int count = 0;

        for (WallpaperItem item : candidates) {
            if (count >= targetNew) break;
            String itemUrl = WallpaperQuality.tierUrl(item.url, uhdTier);
            String fileName = hashFilename(itemUrl) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue;
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(itemUrl, tempFile)) {
                    String meta = (item.title != null ? item.title : "Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : category)
                            + (item.place != null && !item.place.isEmpty() ? "\n" + item.place : "");
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }
                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        count++;
                        Log.i(TAG, "Saved category wallpaper [" + category + "]: " + item.title);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }
        if (anyDownloaded) {
            pruneUnreferenced(d);
            pruneCache(d);
        }
        return anyDownloaded;
    }

    /** Returns true if sync is needed (empty local pool or refresh interval elapsed). */
    static boolean due(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long last = sp.getLong(PREF_LAST_SYNC, 0);
        return list(ctx).isEmpty() || (System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS);
    }

    /**
     * Reads companion .txt file (same name as picture with .txt extension).
     * Line 1: Title
     * Line 2: Description / Overview
     * Line 3: Category / Source
     * Line 4 (optional): the place to show under the title, e.g. "Lahore, Pakistan"
     */
    static String[] describe(File picture) {
        if (picture == null) return null;
        String name = picture.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return null;
        String base = name.substring(0, dot);
        // A curated photo is named by its catalog entry every time, so an upgrade shows the current
        // title even when the .txt beside the cache still holds an older name. Feed pictures, which
        // the catalog does not list, keep reading their .txt.
        WallpaperItem catalogItem = catalogItem(base);
        if (catalogItem != null) {
            return new String[]{
                    catalogItem.title,
                    catalogItem.description == null ? "" : catalogItem.description,
                    catalogItem.category == null ? "" : catalogItem.category,
                    catalogItem.place == null ? "" : catalogItem.place
            };
        }
        File text = new File(picture.getParentFile(), base + ".txt");
        if (!text.isFile() || text.length() == 0 || text.length() > 8192) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(text), StandardCharsets.UTF_8))) {
            String title = r.readLine();
            if (title == null || title.trim().isEmpty()) return null;
            String desc = r.readLine();
            String cat = r.readLine();
            String place = r.readLine();
            return new String[]{
                    title.trim(),
                    desc == null ? "" : desc.trim(),
                    cat == null ? "" : cat.trim(),
                    place == null ? "" : place.trim()
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Synchronizes wallpapers from an online feed or the built-in curated catalog.
     * Downloads on the caller's thread; call from a background thread.
     * Returns true if one or more fresh pictures were added to the local pool.
     */
    static boolean sync(Context ctx, String feedUrl) {
        File d = dir(ctx);
        if (d == null) return false;
        if (!d.exists() && !d.mkdirs()) return false;

        List<WallpaperItem> items = new ArrayList<>();
        if (feedUrl != null && !feedUrl.trim().isEmpty()) {
            List<WallpaperItem> remote = fetchFeed(feedUrl.trim());
            if (remote != null && !remote.isEmpty()) {
                items.addAll(remote);
                Log.i(TAG, "Loaded " + items.size() + " wallpapers from remote feed: " + feedUrl);
            }
        }

        // If no remote items, use the curated catalog
        if (items.isEmpty()) {
            Collections.addAll(items, CURATED_CATALOG);
        }

        // Shuffle candidate pool so rotation changes over time
        List<WallpaperItem> pool = new ArrayList<>(items);
        Collections.shuffle(pool);

        boolean anyDownloaded = false;
        int targetNew = 5; // Download up to 5 fresh pictures per sync
        int downloadedCount = 0;

        for (WallpaperItem item : pool) {
            if (downloadedCount >= targetNew) break;
            String itemUrl = WallpaperQuality.tierUrl(item.url, uhdTier);
            String fileName = hashFilename(itemUrl) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue; // Already cached
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(itemUrl, tempFile)) {
                    // Write metadata
                    String meta = (item.title != null ? item.title : "Wallpaper") + "\n"
                            + (item.description != null ? item.description : "") + "\n"
                            + (item.category != null ? item.category : "Desktop")
                            + (item.place != null && !item.place.isEmpty() ? "\n" + item.place : "");
                    try (OutputStream os = new FileOutputStream(tempTxt)) {
                        os.write(meta.getBytes(StandardCharsets.UTF_8));
                    }

                    File finalTxt = new File(d, fileName.replace(".jpg", ".txt"));
                    if (tempFile.renameTo(targetFile)) {
                        tempTxt.renameTo(finalTxt);
                        anyDownloaded = true;
                        downloadedCount++;
                        Log.i(TAG, "Saved wallpaper: " + item.title + " (" + targetFile.length() + " bytes)");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed downloading " + item.url + ": " + e);
            } finally {
                if (tempFile.exists()) tempFile.delete();
                if (tempTxt.exists()) tempTxt.delete();
            }
        }

        // Pictures the catalog no longer lists (an old size, a replaced photo) go now, so the
        // rotation can never pick one.
        if (anyDownloaded) pruneUnreferenced(d);

        // Prune old files if cache exceeds maximum allowed files
        pruneCache(d);

        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SYNC, System.currentTimeMillis())
                .apply();

        return anyDownloaded;
    }

    static String hashFilename(String url) {
        return "online_" + Math.abs(url.hashCode());
    }

    private static boolean downloadUrlToFile(String strUrl, File dest) {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 GlassTV/2.4 (https://github.com/aqmangge/glass-tv-home)");
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + code + " for " + strUrl);
                return false;
            }

            in = conn.getInputStream();
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    Log.w(TAG, "Image exceeds byte limit, aborting: " + strUrl);
                    return false;
                }
                out.write(buf, 0, read);
            }
            out.flush();
            return total > 10_000;
        } catch (Exception e) {
            Log.w(TAG, "Download error: " + e.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static List<WallpaperItem> fetchFeed(String feedUrl) {
        List<WallpaperItem> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(feedUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (GlassTV; Android)");
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return list;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String raw = sb.toString().trim();
            if (raw.startsWith("[")) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.get(i);
                    if (o instanceof String) {
                        list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                    } else if (o instanceof JSONObject) {
                        JSONObject obj = (JSONObject) o;
                        String u = obj.optString("url");
                        if (!u.isEmpty()) {
                            list.add(new WallpaperItem(
                                    u,
                                    obj.optString("title", "Online Wallpaper"),
                                    obj.optString("description", ""),
                                    obj.optString("category", "Web Server")));
                        }
                    }
                }
            } else if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                JSONArray arr = obj.optJSONArray("wallpapers");
                if (arr == null) arr = obj.optJSONArray("images");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.get(i);
                        if (o instanceof String) {
                            list.add(new WallpaperItem((String) o, "Online Wallpaper", "", "Web Server"));
                        } else if (o instanceof JSONObject) {
                            JSONObject item = (JSONObject) o;
                            String u = item.optString("url");
                            if (!u.isEmpty()) {
                                list.add(new WallpaperItem(
                                        u,
                                        item.optString("title", "Online Wallpaper"),
                                        item.optString("description", ""),
                                        item.optString("category", "Web Server")));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Feed parse error: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static void pruneCache(File dir) {
        if (uhdTier) {
            pruneUhdCacheBySize(dir);
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;

        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        // Sort oldest first
        Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toRemove = list.size() - MAX_CACHE_FILES;
        for (int i = 0; i < toRemove; i++) {
            File f = list.get(i);
            File txt = new File(dir, f.getName().replace(".jpg", ".txt"));
            f.delete();
            if (txt.exists()) txt.delete();
        }
    }

    /** The 4K tier is bounded by total bytes: oldest files go until the folder fits. */
    private static void pruneUhdCacheBySize(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length == 0) return;
        List<File> list = new ArrayList<>();
        Collections.addAll(list, files);
        Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long total = 0;
        for (File f : list) total += f.length();
        for (File f : list) {
            if (total <= MAX_UHD_CACHE_BYTES) break;
            total -= f.length();
            File txt = new File(dir, f.getName().replace(".jpg", ".txt"));
            f.delete();
            if (txt.exists()) txt.delete();
        }
    }
}
