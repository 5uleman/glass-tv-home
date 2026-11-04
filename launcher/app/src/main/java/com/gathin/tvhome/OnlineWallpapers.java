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
 * Dynamic HD (1920x1080) wallpaper manager.
 * Fetches high-resolution (3840x2160) atmospheric and moody desktop wallpapers
 * directly from an online feed or a curated Unsplash catalog.
 * Caches wallpapers locally for instant loading and offline resilience.
 */
final class OnlineWallpapers {
    private static final String TAG = "TvHome.Online";
    static final String DIR = "online-wallpapers";
    private static final String PREFS_NAME = "online_wallpapers";
    private static final String PREF_LAST_SYNC = "last_sync_time";
    private static final long REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final int MAX_CACHE_FILES = 150;
    private static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024; // 25 MB limit per image

    static class WallpaperItem {
        final String url;
        final String title;
        final String description;
        final String category;
        /** The place shown under the title ("Lahore, Pakistan"); empty for collections that show a category. */
        final String place;

        WallpaperItem(String url, String title, String description, String category) {
            this(url, title, description, category, "");
        }

        WallpaperItem(String url, String title, String description, String category, String place) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.category = category;
            this.place = place;
        }
    }

    // Built-in curated catalog of dark, moody, atmospheric 4K desktop wallpapers (Unsplash)
    private static final WallpaperItem[] CURATED_CATALOG = new WallpaperItem[]{
            // Malaysia: Tempat Menarik di Malaysia (16 Koleksi Pexels 4K Ultra HD)
            new WallpaperItem("https://images.pexels.com/photos/40168956/pexels-photo-40168956.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Borneo Rainforest Orangutan", "Iconic wildlife species in the canopy of Borneo's tropical rainforest, a natural treasure", "Malaysia_Sabah"),
            new WallpaperItem("https://images.pexels.com/photos/36767161/pexels-photo-36767161.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Kuala Lumpur Sunset Silhouette", "The capital's metropolitan skyline set against the glow of saga red and orange at dusk", "Malaysia_WP"),
            new WallpaperItem("https://images.pexels.com/photos/338416/pexels-photo-338416.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Putra Mosque & Putrajaya Lake", "The beauty of Putra Mosque's pink dome reflecting gracefully on the calm lake surface", "Malaysia_Putrajaya"),
            new WallpaperItem("https://images.pexels.com/photos/33676568/pexels-photo-33676568.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "KLCC Twin Towers Peak", "The Petronas Twin Towers rising majestically through a sea of golden clouds on the Kuala Lumpur horizon", "Malaysia_WP"),
            new WallpaperItem("https://images.pexels.com/photos/39690291/pexels-photo-39690291.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Straits of Malacca Floating Mosque", "The famous floating mosque on the Straits of Malacca coast, lit by sparkling night lights and a copper sky", "Malaysia_Melaka"),
            new WallpaperItem("https://images.pexels.com/photos/33708542/pexels-photo-33708542.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Kundasang Mountain Chalet", "A unique dwelling on green hillsides set against the cool air of Sabah's highland range", "Malaysia_Sabah"),
            new WallpaperItem("https://images.pexels.com/photos/14776919/pexels-photo-14776919.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Winding Road of the Titiwangsa Range", "A winding route through green mountain ridges shrouded in thick fog at dawn", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/5224545/pexels-photo-5224545.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Dawn over the Pahang River Valley", "Golden morning sunlight spilling over a serene river and hillside villages", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/11643318/pexels-photo-11643318.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Red Sunset over the River Valley", "A sunset panorama refracting copper and orange hues along the river bends and rolling hills", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/28727775/pexels-photo-28727775.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Tanjung Balau Coastal Shore", "Crystal blue sea and a distinctive rocky cape in Desaru facing the South China Sea", "Malaysia_Johor"),
            new WallpaperItem("https://images.pexels.com/photos/38188372/pexels-photo-38188372.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Terengganu Drawbridge", "The iconic Kuala Terengganu drawbridge bathed in the glow of the setting sun", "Malaysia_Terengganu"),
            new WallpaperItem("https://images.pexels.com/photos/13890758/pexels-photo-13890758.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Royal Town of Kuala Kangsar", "Aerial view of a charming heritage town on the banks of the Perak River framed by tropical greenery", "Malaysia_Perak"),
            new WallpaperItem("https://images.pexels.com/photos/139974/pexels-photo-139974.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Fisherman Boats with Jalur Gemilang", "Traditional fishing boats anchored calmly at the river mouth under a pink-tinted dusk sky", "Malaysia_Terengganu"),
            new WallpaperItem("https://images.pexels.com/photos/5649468/pexels-photo-5649468.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Shah Alam State Mosque", "The majestic blue dome and four illuminated minarets of Sultan Salahuddin Abdul Aziz Shah Mosque at night", "Malaysia_Selangor"),
            new WallpaperItem("https://images.pexels.com/photos/130576/pexels-photo-130576.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Cameron Highlands Tea Plantation", "Rolling fresh green tea hills under a clear blue sky in the highlands of the Pahang range", "Malaysia_Pahang"),
            new WallpaperItem("https://images.pexels.com/photos/35905094/pexels-photo-35905094.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Decorated Heritage Trishaw of Malacca", "The unique flower-decorated tourist trishaw and cheerful tunes roaming the historic heritage lanes of Malacca", "Malaysia_Melaka"),

            // Nature & Landscapes (Alam Semula Jadi - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Moody Alpine Mountains", "Silhouetted mountain peaks under starry skies", "Landscape"),
            new WallpaperItem("https://images.pexels.com/photos/1366919/pexels-photo-1366919.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Moody Mountain Fog", "Mist rolling through dense mountain evergreen pine forest", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1531366936337-7c912a4589a7?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Aurora Borealis", "Green atmospheric aurora dancing across arctic skies", "Nature"),
            new WallpaperItem("https://images.pexels.com/photos/1624496/pexels-photo-1624496.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Starry Night Over Pines", "Bright starry cosmos illuminating tall silhouette pine forest", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1448375240586-882707db888b?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Misty Pine Forest", "Dense evergreen canopy cloaked in rolling fog", "Forest"),
            new WallpaperItem("https://images.pexels.com/photos/1287145/pexels-photo-1287145.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Serene Alpine Twilight", "Calm dusk sky casting reflections across quiet mountain lake", "Nature"),
            new WallpaperItem("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Moody Lake Twilight", "Still dark waters reflecting misty mountain ridges", "Landscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Desert Dunes", "Shadows sweeping over desert dunes under the night sky", "Desert"),
            new WallpaperItem("https://images.pexels.com/photos/3408744/pexels-photo-3408744.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Majestic Snow Mountain Peak", "Crisp dramatic snow ridges against deep blue atmosphere", "Mountains"),
            new WallpaperItem("https://images.unsplash.com/photo-1508739773434-c26b3d09e071?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Volcanic Landscape", "Black volcanic terrain and quiet minimalist horizon", "Nature"),

            // Cityscape & Architecture (Pencakar Langit & Bandar - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Tokyo Cyberpunk Alley", "Moody neon reflections in a narrow Tokyo alleyway", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/374870/pexels-photo-374870.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Metropolis Skyline Twilight", "Glowing skyscrapers towering over serene bay water", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Neon Rain Street", "Vibrant reflections shimmering on wet asphalt", "Cityscape"),
            new WallpaperItem("https://images.pexels.com/photos/3052361/pexels-photo-3052361.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Modern Glass Skyscraper", "Geometric glass architectural patterns rising into sky", "Architecture"),
            new WallpaperItem("https://images.unsplash.com/photo-1653142267767-a66c7cf17bc1?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Cyberpunk Neon Night", "Futuristic high-density city streets glowing in vibrant magenta", "Cityscape"),
            new WallpaperItem("https://images.unsplash.com/photo-1494548162494-384bba4ab999?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Foggy Suspension Bridge", "Golden Gate towers emerging through evening fog", "Architecture"),

            // Space & Astronomy (Angkasa & Kosmos - Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Night Earth Orbit", "Planet Earth illuminated at night from outer space", "Space"),
            new WallpaperItem("https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Milky Way Galaxy", "Deep night sky filled with stellar constellations", "Astronomy"),
            new WallpaperItem("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Deep Space Nebula", "Cosmic interstellar gas clouds and distant stars", "Cosmos"),
            new WallpaperItem("https://images.unsplash.com/photo-1419242902214-272b3f66ee7a?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Cosmic Dark Starfield", "Deep black void speckled with distant galaxies", "Space"),

            // Ocean & Water (Lautan & Pantai - Pexels & Unsplash 4K)
            new WallpaperItem("https://images.unsplash.com/photo-1544551763-46a013bb70d5?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Deep Underwater Abyss", "Sunlight penetrating into the dark deep ocean", "Underwater"),
            new WallpaperItem("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Midnight Ocean Waves", "Deep blue waves crashing on a tranquil dark shore", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1001682/pexels-photo-1001682.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Deep Ocean Wave Crest", "Powerful sea water curling in turquoise deep shades", "Oceans"),
            new WallpaperItem("https://images.pexels.com/photos/1032650/pexels-photo-1032650.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Tropical Turquoise Deep Water", "Aerial view of calm ocean reef gradient", "Oceans"),
            new WallpaperItem("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Ocean Trench", "Bioluminescent glow illuminating the deep ocean", "Underwater"),
            new WallpaperItem("https://images.pexels.com/photos/1295138/pexels-photo-1295138.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Dramatic Sea Cliffs", "Dark oceanic cliffs meeting moody breaking waves", "Coast"),

            // Minimalist & Atmospheric (Minimalis Gelap OLED - Unsplash 4K Curated from https://unsplash.com/s/photos/dark-wallpaper)
            new WallpaperItem("https://images.unsplash.com/photo-1484950763426-56b5bf172dbb?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "4x4 Vehicle Silhouette", "Silhouette of a four-wheel-drive vehicle beneath a vast, quiet night sky of stars", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1533134486753-c833f0ed4866?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Grey Sand Waves", "Misty grey sand curves along the Karekare coastline of New Zealand", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1762417108293-91614140073a?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "OLED Black Cylinder Texture", "Minimalist dark 3D black cylinder surface architecture", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1507752533523-5186b0bc4c43?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Golden Twilight Skyscrapers", "Montreal metropolitan skyline bathed in golden twilight reflections", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1470813740244-df37b8c1edcb?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Blue Starry Night", "The beauty of the galaxy and blue star clusters beyond a rocky gorge", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1509023464722-18d996393ca8?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Mount Fuji & Silent Lake", "Mount Fuji's silhouette over a silent Japanese lake against a deep dark sky", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1491466424936-e304919aada7?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "OLED Black Sand Waves", "Minimalist black dune curves with perfect shadow contrast", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1622737133809-d95047b9e673?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Polygon Geometry", "High-dimensional monochrome 3D geometric architecture for OLED screens", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Silky Black Liquid Flow", "Glossy black liquid waves flowing gracefully in absolute darkness", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1550684848-fac1c5b4e853?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Minimalist Dark Grid", "Modern perspective line structure against a deep black background", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1507499739999-097706ad8914?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Night Monolith Gateway", "Minimalist concrete architecture standing tall under the depth of a quiet night", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Monochrome Mountain Mist", "Misty mountain ranges in dramatic black-and-white tones", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Cybernetic Circuit", "Advanced technology circuit board in minimalist dark lighting", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1541701494587-cb58502866ab?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Pigment Scatter", "Explosion of fine, highly textured charcoal-black dust particles", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Classic Charcoal Canvas", "Dark oil painting art texture in monochrome charcoal tones", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1519751138087-5bf79df62d5b?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Monstera Leaf Silhouette", "Dark tropical leaves against a mysterious black shadow backdrop", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1511447333015-45b65e60f6d5?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Dark Light Prism", "Delicate neon light spectrum radiating through absolute darkness", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Pure Black OLED Texture", "Minimalist charcoal-black surface designed to save OLED pixels", "Minimalist_OLED"),
            new WallpaperItem("https://images.unsplash.com/photo-1491557345352-5929e343eb89?auto=format&fit=crop&w=1920&h=1080&q=80",
                    "Obsidian Mountain Peak", "Sharp rocky mountain peaks piercing dramatic black storm clouds", "Minimalist_OLED"),

            // Pakistan (Pexels, 1920x1080, dusk or night where possible so the white UI text reads).
            // The fourth value is the place line under the title; the third stays the category.
            // Lahore
            new WallpaperItem("https://images.pexels.com/photos/5487986/pexels-photo-5487986.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Badshahi Mosque Under the Milky Way", "The lit Mughal mosque beneath the Milky Way, with Minar-e-Pakistan on the horizon", "Pakistan_Lahore", "Lahore, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/11774127/pexels-photo-11774127.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Alamgiri Gate", "The gateway into Lahore Fort above its lawns, with the white pavilion to the right", "Pakistan_Lahore", "Lahore, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/16021536/pexels-photo-16021536.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Lahore at Night", "Light trails along the canal and the lit Arfa Software Technology Park tower", "Pakistan_Lahore", "Lahore, Pakistan"),
            // Islamabad
            new WallpaperItem("https://images.pexels.com/photos/16693762/pexels-photo-16693762.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Faisal Mosque at Dusk", "The white tents and minarets of Faisal Mosque below the Margalla Hills at twilight", "Pakistan_Islamabad", "Islamabad, Pakistan"),
            new WallpaperItem("https://images.unsplash.com/photo-1706511920650-b690cd6619e8?auto=format&fit=crop&crop=entropy&w=1920&h=1080&q=85",
                    "Faisal Mosque in Golden Mist", "The white mosque and its four minarets rising through gold morning haze", "Pakistan_Islamabad", "Islamabad, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/38459967/pexels-photo-38459967.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Rawal Lake at Sunset", "The sun setting behind the Margalla Hills, reflected across Rawal Lake", "Pakistan_Islamabad", "Islamabad, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/38773558/pexels-photo-38773558.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Islamabad from Above", "Green avenues running toward the Margalla Hills, with Faisal Mosque in the distance", "Pakistan_Islamabad", "Islamabad, Pakistan"),
            // Karachi
            new WallpaperItem("https://images.pexels.com/photos/34096455/pexels-photo-34096455.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Karachi at Night", "Dense city lights spreading to the horizon after dark", "Pakistan_Karachi", "Karachi, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/28244078/pexels-photo-28244078.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Clifton Beach", "A horse and its handler on the wet sand at dusk, in black and white", "Pakistan_Karachi", "Karachi, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/33045005/pexels-photo-33045005.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Karachi Skyline", "High-rise apartment blocks and tree-lined streets seen from above", "Pakistan_Karachi", "Karachi, Pakistan"),
            // The north
            new WallpaperItem("https://images.pexels.com/photos/19442083/pexels-photo-19442083.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Hunza Valley in Autumn", "Snow peaks above the Hunza valley and its autumn poplar groves", "Pakistan_Hunza", "Hunza, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/15916726/pexels-photo-15916726.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Hunza Valley at Sunset", "Clouds and low light over the Hunza valley, with Rakaposhi's snows beyond", "Pakistan_Hunza", "Hunza, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/35302563/pexels-photo-35302563.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Golden Peaks of Hunza", "A snow ridge catching the last sunlight above the valley floor", "Pakistan_Hunza", "Hunza, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/8835247/pexels-photo-8835247.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Passu Cones", "The jagged Passu Cones rising above the dry Hunza valley", "Pakistan_Hunza", "Hunza, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/13462643/pexels-photo-13462643.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Attabad Lake", "Turquoise water and moored boats between the Karakoram slopes", "Pakistan_Hunza", "Hunza, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/29401243/pexels-photo-29401243.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Nanga Parbat at Sunrise", "Dawn light on the snow slopes of Nanga Parbat", "Pakistan_Diamer", "Diamer, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/186985/pexels-photo-186985.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Swat Valley", "Fields and forested hills along the Swat river under a storm-dark sky", "Pakistan_Swat", "Swat, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/36870354/pexels-photo-36870354.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Karakoram Peaks", "Rock spires and snow along a Karakoram ridge", "Pakistan_Karakoram", "Gilgit-Baltistan, Pakistan"),
            // Historic sites
            new WallpaperItem("https://images.pexels.com/photos/815880/pexels-photo-815880.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Derawar Fort", "The fort's round towers rising from the Cholistan desert at sunset", "Pakistan_Bahawalpur", "Bahawalpur, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/34535290/pexels-photo-34535290.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Makli Necropolis", "Carved stone columns along a corridor of tombs at Makli", "Pakistan_Thatta", "Thatta, Pakistan"),
            // Landmarks
            new WallpaperItem("https://images.pexels.com/photos/14926132/pexels-photo-14926132.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Lahore Fort at Sunset", "The fort's walls and domes under a warm sky, with the flag on the rampart", "Pakistan_Lahore", "Lahore, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/13239874/pexels-photo-13239874.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Minar-e-Pakistan in Daylight", "The minaret rising above its lawns and trees under a clear sky", "Pakistan_Lahore", "Lahore, Pakistan"),
            new WallpaperItem("https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/Eiffel_Tower_replica_in_Bahria_Town.jpg/1920px-Eiffel_Tower_replica_in_Bahria_Town.jpg",
                    "Eiffel Tower Replica at Dusk", "The replica tower and its lit grounds on the edge of Bahria Town at evening", "Pakistan_Lahore", "Bahria Town, Lahore"),
            new WallpaperItem("https://images.pexels.com/photos/14394126/pexels-photo-14394126.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Pakistan Monument at Night", "The lit petals of the monument around its central star", "Pakistan_Islamabad", "Islamabad, Pakistan"),
            new WallpaperItem("https://images.pexels.com/photos/4253018/pexels-photo-4253018.jpeg?auto=compress&cs=tinysrgb&w=1920&h=1080&fit=crop",
                    "Abbasi Mosque at Derawar", "The mosque's white domes seen through the ruined walls of Derawar Fort", "Pakistan_Bahawalpur", "Bahawalpur, Pakistan")
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

    /** Cache file names (without extension) the catalog asks for, optionally for one category only. */
    static Set<String> currentNames(String category) {
        Set<String> names = new HashSet<>();
        for (WallpaperItem item : CURATED_CATALOG) {
            if (category == null || matchesCategory(item.category, category)) {
                names.add(hashFilename(item.url));
            }
        }
        return names;
    }

    static void cleanOutdatedCategoryWallpapers(File d, String category) {
        if (d == null || !d.exists()) return;
        Set<String> validHashes = currentNames(category);
        File[] files = d.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
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
            String[] desc = describe(f);
            if (desc != null && desc.length >= 3) {
                if (matchesCategory(desc[2], category)) {
                    filtered.add(f);
                }
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
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue;
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
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
        if (anyDownloaded) pruneUnreferenced(d);
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
        File text = new File(picture.getParentFile(), name.substring(0, dot) + ".txt");
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
            String fileName = hashFilename(item.url) + ".jpg";
            File targetFile = new File(d, fileName);
            if (targetFile.exists() && targetFile.length() > 50_000) {
                continue; // Already cached
            }

            File tempFile = new File(d, fileName + ".tmp");
            File tempTxt = new File(d, fileName.replace(".jpg", ".txt") + ".tmp");
            try {
                if (downloadUrlToFile(item.url, tempFile)) {
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
                        Log.i(TAG, "Saved 4K wallpaper: " + item.title + " (" + targetFile.length() + " bytes)");
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

    private static String hashFilename(String url) {
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
}
