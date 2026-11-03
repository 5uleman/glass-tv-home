package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RecentsCleanerTest {

    private static final String SELF = "com.suleman.glasstv";

    @Test
    public void ordinaryAppIsCleared() {
        assertTrue(RecentsCleaner.shouldKill("com.example.player", SELF, false, false,
                Collections.<String>emptySet()));
    }

    @Test
    public void launcherIsNeverCleared() {
        assertFalse(RecentsCleaner.shouldKill(SELF, SELF, false, false,
                Collections.<String>emptySet()));
    }

    @Test
    public void systemAndPersistentAppsAreSkipped() {
        assertFalse(RecentsCleaner.shouldKill("com.android.tv.settings", SELF, true, false,
                Collections.<String>emptySet()));
        assertFalse(RecentsCleaner.shouldKill("com.android.phone", SELF, false, true,
                Collections.<String>emptySet()));
    }

    @Test
    public void keyboardAccessibilityAndVpnAppsAreSkipped() {
        Set<String> keep = new HashSet<>();
        keep.add("com.example.keyboard");
        keep.add("com.example.a11y");
        keep.add("ch.protonvpn.android");
        assertFalse(RecentsCleaner.shouldKill("com.example.keyboard", SELF, false, false, keep));
        assertFalse(RecentsCleaner.shouldKill("com.example.a11y", SELF, false, false, keep));
        assertFalse(RecentsCleaner.shouldKill("ch.protonvpn.android", SELF, false, false, keep));
    }

    @Test
    public void emptyOrMissingNamesAreSkipped() {
        assertFalse(RecentsCleaner.shouldKill(null, SELF, false, false, Collections.<String>emptySet()));
        assertFalse(RecentsCleaner.shouldKill("", SELF, false, false, Collections.<String>emptySet()));
    }
}
