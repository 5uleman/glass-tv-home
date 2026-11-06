package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class VpnAppsTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long T0 = 1_700_000_000_000L;

    private static VpnApps.Candidate c(String pkg, String label, long install) {
        return new VpnApps.Candidate(pkg, label, true, install, -1, -1);
    }

    private static VpnApps.Candidate c(String pkg, boolean launchable) {
        return new VpnApps.Candidate(pkg, pkg, launchable, T0, -1, -1);
    }

    private static VpnApps.Candidate used(String pkg, long lastUsed, long foreground) {
        return new VpnApps.Candidate(pkg, pkg, true, T0, lastUsed, foreground);
    }

    private static String first(List<VpnApps.Candidate> ranked) {
        return ranked.get(0).packageName;
    }

    @Test
    public void nothingFoundStaysEmpty() {
        assertTrue(VpnApps.rank(Collections.emptyList(), "com.suleman.glasstv", null, true).isEmpty());
    }

    @Test
    public void oneCandidateIsTheChoice() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(
                Collections.singletonList(c("com.one.vpn", "One", T0)),
                "com.suleman.glasstv", null, true);
        assertEquals(1, ranked.size());
        assertEquals("com.one.vpn", first(ranked));
    }

    @Test
    public void severalWithUsageOpenTheMostRecentlyUsed() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        used("com.old.vpn", T0 - DAY, 10 * DAY),
                        used("com.recent.vpn", T0, DAY),
                        used("com.mid.vpn", T0 - DAY / 2, 3 * DAY)),
                "com.suleman.glasstv", null, true);
        assertEquals("com.recent.vpn", first(ranked));
        assertEquals("com.mid.vpn", ranked.get(1).packageName);
        assertEquals("com.old.vpn", ranked.get(2).packageName);
    }

    @Test
    public void foregroundTimeBreaksAnEqualLastUsed() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        used("com.short.vpn", T0, 60_000),
                        used("com.long.vpn", T0, 3 * DAY)),
                "com.suleman.glasstv", null, true);
        assertEquals("com.long.vpn", first(ranked));
    }

    @Test
    public void withoutUsageAccessTheSavedChoiceThenTheNewestWin() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        c("com.old.vpn", "Old", T0 - 10 * DAY),
                        c("com.saved.vpn", "Saved", T0 - 5 * DAY),
                        c("com.new.vpn", "New", T0)),
                "com.suleman.glasstv", "com.saved.vpn", false);
        assertEquals("com.saved.vpn", first(ranked));
        assertEquals("com.new.vpn", ranked.get(1).packageName);
        assertEquals("com.old.vpn", ranked.get(2).packageName);
    }

    @Test
    public void withoutUsageAccessAndNoSavedChoiceTheNewestInstallWins() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        c("com.old.vpn", "Old", T0 - 10 * DAY),
                        c("com.new.vpn", "New", T0)),
                "com.suleman.glasstv", null, false);
        assertEquals("com.new.vpn", first(ranked));
    }

    @Test
    public void theSavedChoiceBreaksAnEqualUsageTie() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        used("com.a.vpn", T0, DAY),
                        used("com.b.vpn", T0, DAY)),
                "com.suleman.glasstv", "com.b.vpn", true);
        assertEquals("com.b.vpn", first(ranked));
    }

    @Test
    public void fullTiesFallToABrandThenThePackageName() {
        List<VpnApps.Candidate> branded = VpnApps.rank(Arrays.asList(
                        c("com.unknown.vpn", "Unknown", T0),
                        c("ch.protonvpn.android", "Proton", T0)),
                "com.suleman.glasstv", null, false);
        assertEquals("ch.protonvpn.android", first(branded));

        List<VpnApps.Candidate> alphabetical = VpnApps.rank(Arrays.asList(
                        c("com.zeta.vpn", "Zeta", T0),
                        c("com.alpha.vpn", "Alpha", T0)),
                "com.suleman.glasstv", null, false);
        assertEquals("com.alpha.vpn", first(alphabetical));
    }

    @Test
    public void aBrandNeverOverridesUsageOrANewerInstall() {
        List<VpnApps.Candidate> byUsage = VpnApps.rank(Arrays.asList(
                        used("ch.protonvpn.android", T0 - DAY, DAY),
                        used("com.other.vpn", T0, DAY)),
                "com.suleman.glasstv", null, true);
        assertEquals("com.other.vpn", first(byUsage));

        List<VpnApps.Candidate> byInstall = VpnApps.rank(Arrays.asList(
                        c("ch.protonvpn.android", "Proton", T0 - 10 * DAY),
                        c("com.other.vpn", "Other", T0)),
                "com.suleman.glasstv", null, false);
        assertEquals("com.other.vpn", first(byInstall));
    }

    @Test
    public void unlaunchableAppsAreDropped() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(Arrays.asList(
                        c("com.broken.vpn", false),
                        c("com.working.vpn", true)),
                "com.suleman.glasstv", null, true);
        assertEquals(1, ranked.size());
        assertEquals("com.working.vpn", first(ranked));
    }

    @Test
    public void ourOwnPackageIsExcluded() {
        List<VpnApps.Candidate> ranked = VpnApps.rank(
                Collections.singletonList(c("com.suleman.glasstv", true)),
                "com.suleman.glasstv", null, true);
        assertTrue(ranked.isEmpty());
    }
}
