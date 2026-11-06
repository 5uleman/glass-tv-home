package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which text the hero shelf shows in each wallpaper mode and with the setting on or off. */
public class HeroInfoTest {

    @Test
    public void aCatalogPictureShowsItsOwnTitleWhenTheSettingIsOn() {
        assertEquals(HeroInfo.Source.PICTURE, HeroInfo.source(false, true, false));
    }

    @Test
    public void theDarkScreenAlwaysNamesTheApp() {
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(true, true, false));
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(true, false, false));
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(true, true, true));
    }

    @Test
    public void turningTheSettingOffNamesTheAppInEveryWallpaperMode() {
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(false, false, false));
        assertEquals(HeroInfo.Source.APP, HeroInfo.source(false, false, true));
    }

    @Test
    public void anUploadedPictureShowsNoTextWhileTheSettingIsOn() {
        assertEquals(HeroInfo.Source.NONE, HeroInfo.source(false, true, true));
    }
}
