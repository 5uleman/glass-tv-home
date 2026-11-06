package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which VPN app the network menu's VPN row opens. Discovery is the platform's own
 * {@code android.net.VpnService} declaration, never a list of brands; this class only ranks the
 * apps that were found and can be launched. Pure, so the ordering rules are unit tested.
 */
final class VpnApps {

    /** A launchable VPN app with the facts the ranking needs. Usage values are -1 when unknown. */
    static final class Candidate {
        final String packageName;
        final String label;
        final boolean launchable;
        final long firstInstallMs;
        final long lastUsedMs;
        final long foregroundMs30d;

        Candidate(String packageName, String label, boolean launchable,
                  long firstInstallMs, long lastUsedMs, long foregroundMs30d) {
            this.packageName = packageName;
            this.label = label;
            this.launchable = launchable;
            this.firstInstallMs = firstInstallMs;
            this.lastUsedMs = lastUsedMs;
            this.foregroundMs30d = foregroundMs30d;
        }
    }

    /** Known brands only break a complete tie; they never decide whether an app is a candidate. */
    private static final Set<String> KNOWN_BRANDS = new HashSet<>();

    static {
        KNOWN_BRANDS.add("ch.protonvpn.android");
        KNOWN_BRANDS.add("com.nordvpn.android");
        KNOWN_BRANDS.add("com.expressvpn.vpn");
        KNOWN_BRANDS.add("com.surfshark.vpnclient.android");
        KNOWN_BRANDS.add("com.privateinternetaccess.android");
        KNOWN_BRANDS.add("com.windscribe.vpn");
        KNOWN_BRANDS.add("com.tunnelbear.android");
        KNOWN_BRANDS.add("net.openvpn.openvpn");
        KNOWN_BRANDS.add("de.blinkt.openvpn");
        KNOWN_BRANDS.add("com.wireguard.android");
    }

    private VpnApps() {
    }

    /**
     * Drops unlaunchable apps and this launcher, then orders what is left:
     * last used (with usage access), 30-day foreground time, the app this button opened last,
     * the newest install, known brand, package name.
     *
     * @param usageAvailable false when the launcher has no usage access: the two usage rules are skipped
     */
    static List<Candidate> rank(List<Candidate> candidates, String ownPackage,
                                String lastOpenedPackage, boolean usageAvailable) {
        List<Candidate> usable = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : candidates) {
            if (c == null || c.packageName == null || c.packageName.isEmpty()) continue;
            if (c.packageName.equals(ownPackage)) continue;
            if (!c.launchable) continue;
            if (!seen.add(c.packageName)) continue;
            usable.add(c);
        }
        Comparator<Candidate> order = (a, b) -> {
            if (usageAvailable) {
                int c = Long.compare(b.lastUsedMs, a.lastUsedMs);
                if (c != 0) return c;
                c = Long.compare(b.foregroundMs30d, a.foregroundMs30d);
                if (c != 0) return c;
            }
            int c = Boolean.compare(isSaved(b, lastOpenedPackage), isSaved(a, lastOpenedPackage));
            if (c != 0) return c;
            c = Long.compare(b.firstInstallMs, a.firstInstallMs);
            if (c != 0) return c;
            c = Boolean.compare(KNOWN_BRANDS.contains(b.packageName), KNOWN_BRANDS.contains(a.packageName));
            if (c != 0) return c;
            return a.packageName.compareTo(b.packageName);
        };
        usable.sort(order);
        return usable;
    }

    private static boolean isSaved(Candidate c, String lastOpenedPackage) {
        return lastOpenedPackage != null && lastOpenedPackage.equals(c.packageName);
    }
}
