package com.gathin.tvhome;

/**
 * What the hero shelf says. The dark "no wallpaper" screen has always named the focused dock app
 * instead of the picture; with the "Image Title & Info" setting off, every wallpaper mode does the
 * same, through the same shelf.
 */
final class HeroInfo {

    enum Source {
        /** The picture's place name, country and description. */
        PICTURE,
        /** The focused app's name, with no category or description. */
        APP,
        /** Nothing: a user's own upload keeps the screen clean while the setting is on. */
        NONE
    }

    private HeroInfo() {}

    static Source source(boolean noWallpaper, boolean descEnabled, boolean custom) {
        if (noWallpaper) return Source.APP;   // the dark screen always names the app
        if (!descEnabled) return Source.APP;  // off replaces the picture's text with the app's name
        return custom ? Source.NONE : Source.PICTURE;
    }
}
