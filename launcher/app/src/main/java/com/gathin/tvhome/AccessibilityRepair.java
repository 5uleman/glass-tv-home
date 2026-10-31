package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether the launcher should rebuild the accessibility settings so HomeKeyService binds
 * again after a boot, and builds the service list that write needs. Plain Java on purpose: the
 * decision is unit tested on the JVM, away from the Android classes that apply it.
 */
final class AccessibilityRepair {

    private AccessibilityRepair() {
    }

    /**
     * True only when the launcher may repair: it holds WRITE_SECURE_SETTINGS, the user already
     * opted our service in (it is still in the list), and the service is not bound.
     */
    static boolean shouldRepair(boolean hasGrant, boolean listed, boolean bound) {
        return hasGrant && listed && !bound;
    }

    static boolean isListed(String current, String service) {
        if (current == null || service == null) return false;
        for (String entry : split(current)) {
            if (entry.equals(service)) return true;
        }
        return false;
    }

    /** The list with our service taken out, every other service untouched and in its old order. */
    static String withoutService(String current, String service) {
        List<String> keep = new ArrayList<>();
        for (String entry : split(current)) {
            if (!entry.equals(service)) keep.add(entry);
        }
        return join(keep);
    }

    /**
     * The full list again with our service moved to the end. The moved entry is what makes the
     * write a real change, so Android re-evaluates and binds the service.
     */
    static String withServiceLast(String current, String service) {
        List<String> keep = new ArrayList<>();
        for (String entry : split(current)) {
            if (!entry.equals(service)) keep.add(entry);
        }
        if (service != null) keep.add(service);
        return join(keep);
    }

    private static List<String> split(String current) {
        List<String> out = new ArrayList<>();
        if (current == null) return out;
        for (String entry : current.split(":")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static String join(List<String> entries) {
        StringBuilder out = new StringBuilder();
        for (String entry : entries) {
            if (out.length() > 0) out.append(':');
            out.append(entry);
        }
        return out.toString();
    }
}
