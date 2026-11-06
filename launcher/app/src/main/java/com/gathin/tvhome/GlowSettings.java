package com.gathin.tvhome;

/**
 * The App Color Glow switch is stored per mode. The dark "no wallpaper" screen has its own key and
 * starts on, so turning the glow off over a picture never dims the dark screen, and switching modes
 * never rewrites the other mode's choice.
 */
final class GlowSettings {

    static final String WALLPAPER_KEY = "app_color_glow";
    static final String DARK_KEY = "app_color_glow_dark";
    static final boolean DEFAULT = true;

    private GlowSettings() {}

    /** The key the App Color Glow row reads and writes in this mode. */
    static String keyFor(boolean noWallpaper) {
        return noWallpaper ? DARK_KEY : WALLPAPER_KEY;
    }

    /** The glow that applies in a mode; each value is that mode's own stored choice. */
    static boolean effective(boolean noWallpaper, boolean wallpaperOn, boolean darkOn) {
        return noWallpaper ? darkOn : wallpaperOn;
    }
}
