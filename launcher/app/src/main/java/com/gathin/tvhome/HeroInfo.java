package com.gathin.tvhome;

/**
 * What the hero shelf says. The rule is the same for every wallpaper source: the picture's own
 * title when "Image Title & Info" is on and the picture really has one; otherwise the focused dock
 * app's name, in the same shelf the dark "no wallpaper" screen has always used.
 */
final class HeroInfo {

    enum Source {
        /** The picture's place name, country and description. */
        PICTURE,
        /** The focused app's name, with no category or description. */
        APP
    }

    private HeroInfo() {}

    /** A title counts only when it has visible characters; whitespace is not a title. */
    static boolean hasTitle(String title) {
        return title != null && !title.trim().isEmpty();
    }

    static Source source(boolean noWallpaper, boolean descEnabled, String title) {
        if (noWallpaper) return Source.APP;      // the dark screen always names the app
        if (!descEnabled) return Source.APP;     // off always names the app
        return hasTitle(title) ? Source.PICTURE : Source.APP;
    }

    /** The big line: the picture's title, or the focused app's name. */
    static String label(Source source, String title, String appName) {
        return source == Source.PICTURE ? title : appName;
    }
}
