package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The hero rule for every wallpaper source: online collections, photos sent from a phone, uploaded
 * photos, a single pinned photo, places, and the dark "no wallpaper" screen, with the setting on and
 * off and with a title present, empty or only whitespace.
 */
public class HeroInfoTest {

    private static final String TITLE = "Lahore Fort";
    private static final String APP = "YouTube";

    private static final class Source {
        final String name;
        final boolean noWallpaper;

        Source(String name, boolean noWallpaper) {
            this.name = name;
            this.noWallpaper = noWallpaper;
        }
    }

    private static final Source[] SOURCES = {
            new Source("online collection", false),
            new Source("sent from phone", false),
            new Source("uploaded photo", false),
            new Source("single pinned photo", false),
            new Source("places", false),
            new Source("no wallpaper", true),
    };

    /** A title that is present, plus the three ways it can be missing. */
    private static final String[] TITLE_STATES = {TITLE, null, "", "   ", "\t\n "};

    @Test
    public void everySourceWithTheSettingOnAndOffAndEveryTitleState() {
        for (Source s : SOURCES) {
            for (boolean on : new boolean[]{true, false}) {
                for (String title : TITLE_STATES) {
                    boolean hasTitle = title != null && !title.trim().isEmpty();
                    HeroInfo.Source want =
                            (!s.noWallpaper && on && hasTitle) ? HeroInfo.Source.PICTURE
                                    : HeroInfo.Source.APP;
                    assertEquals(s.name + ", setting " + (on ? "on" : "off") + ", title ["
                                    + title + "]",
                            want, HeroInfo.source(s.noWallpaper, on, title));
                }
            }
        }
    }

    @Test
    public void emptyAndWhitespaceTitlesAreNotTitles() {
        assertFalse(HeroInfo.hasTitle(null));
        assertFalse(HeroInfo.hasTitle(""));
        assertFalse(HeroInfo.hasTitle(" "));
        assertFalse(HeroInfo.hasTitle("\t"));
        assertFalse(HeroInfo.hasTitle("\n"));
        assertFalse(HeroInfo.hasTitle("   \t \n "));
        assertTrue(HeroInfo.hasTitle(TITLE));
        assertTrue(HeroInfo.hasTitle("  Lahore Fort  "));
    }

    @Test
    public void aTitlelessPictureShowsTheAppNameThatFollowsFocus() {
        // Setting on, no title: the label is the app's name, and it changes with the focused app.
        for (String app : new String[]{APP, "Netflix", "VLC", "Spotify"}) {
            assertEquals(app, HeroInfo.label(
                    HeroInfo.source(false, true, "  "), null, app));
        }
        // Setting off, a picture that does have a title: still the focused app's name.
        assertEquals("VLC", HeroInfo.label(
                HeroInfo.source(false, false, TITLE), TITLE, "VLC"));
        // Set on with a title: the title, and the app name is ignored.
        assertEquals(TITLE, HeroInfo.label(
                HeroInfo.source(false, true, TITLE), TITLE, APP));
    }

    @Test
    public void theDarkScreenIgnoresATitleAndTheSetting() {
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(true, true, TITLE));
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(true, false, TITLE));
        assertEquals(APP, HeroInfo.label(HeroInfo.Source.APP, TITLE, APP));
    }
}
