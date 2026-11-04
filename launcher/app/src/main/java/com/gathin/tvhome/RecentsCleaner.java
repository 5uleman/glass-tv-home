package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clear all for the recent-apps deck. Only {@link ActivityManager#killBackgroundProcesses} is used:
 * no root, no shell, no force-stop. Apps that must keep running — the launcher, system and
 * persistent apps, the keyboard, accessibility services and a running VPN app — are left alone,
 * and Android itself will ignore the kill for anything currently in the foreground.
 */
final class RecentsCleaner {

    private static final String TAG = "TvHome.Recents";
    private static final long SETTLE_MS = 800;
    private static final String ACTION_INPUT_METHOD = "android.view.InputMethod";
    private static final String ACTION_VPN_SERVICE = "android.net.VpnService";

    interface Callback {
        /** Called once the system has had a moment to account for the kills. */
        void onDone(int apps, long freedBytes);
    }

    interface OneCallback {
        /**
         * {@code closed} says the system was asked to stop the app; {@code keptForVpn} says it was
         * left alone only because a VPN is connected.
         */
        void onDone(boolean closed, boolean keptForVpn, long freedBytes);
    }

    /** The listed app a connected VPN is holding open, or null when there is nothing to say. */
    static String vpnKeptLabel(Context context, List<RecentAppItem> items) {
        if (items == null || !vpnActive(context)) return null;
        Set<String> keep = protectedPackages(context);
        for (RecentAppItem item : items) {
            if (item != null && !item.isCloseAll && item.packageName != null
                    && keep.contains(item.packageName)) {
                return item.label;
            }
        }
        return null;
    }

    /** Same rules as {@link #clear}, for one card in the deck. */
    static void closeOne(Context context, RecentAppItem item, OneCallback callback) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null || item == null || callback == null) {
            if (callback != null) callback.onDone(false, false, 0);
            return;
        }
        long before = availBytes(am);
        Set<String> keep = protectedPackages(context);
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        String pkg = item.packageName;
        boolean vpn = vpnActive(context);
        boolean allowed = shouldKill(pkg, self, isSystemApp(pm, pkg), isPersistent(pm, pkg), keep);
        boolean keptForVpn = !allowed && vpn && pkg != null && keep.contains(pkg);
        if (allowed) {
            try {
                am.killBackgroundProcesses(pkg);
                RecentsDeckItems.forget(context, java.util.Collections.singleton(pkg));
            } catch (Exception e) {
                Log.w(TAG, "Could not ask the system to stop " + pkg, e);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> callback.onDone(allowed, keptForVpn, allowed ? Math.max(0, availBytes(am) - before) : 0),
                SETTLE_MS);
    }

    private RecentsCleaner() {
    }

    static void clear(Context context, List<RecentAppItem> items, Callback callback) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null || items == null) {
            callback.onDone(0, 0);
            return;
        }
        long before = availBytes(am);
        Set<String> keep = protectedPackages(context);
        Set<String> cleared = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();

        for (RecentAppItem item : items) {
            if (item == null || item.isCloseAll) continue;
            String pkg = item.packageName;
            if (!shouldKill(pkg, self, isSystemApp(pm, pkg), isPersistent(pm, pkg), keep)) continue;
            try {
                am.killBackgroundProcesses(pkg);
                cleared.add(pkg);
            } catch (Exception e) {
                Log.w(TAG, "Could not ask the system to stop " + pkg, e);
            }
        }
        RecentsDeckItems.forget(context, cleared);

        int count = cleared.size();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> callback.onDone(count, Math.max(0, availBytes(am) - before)), SETTLE_MS);
    }

    /** Pure decision, so the exclusions can be tested without a device. */
    static boolean shouldKill(String pkg, String self, boolean system, boolean persistent,
                              Set<String> protectedPackages) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals(self)) return false;
        if (system || persistent) return false;
        return protectedPackages == null || !protectedPackages.contains(pkg);
    }

    private static long availBytes(ActivityManager am) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem;
    }

    private static boolean isSystemApp(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (Exception e) {
            return true; // unknown package: leave it alone
        }
    }

    private static boolean isPersistent(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static Set<String> protectedPackages(Context context) {
        Set<String> keep = new HashSet<>();
        keep.add(context.getPackageName());
        PackageManager pm = context.getPackageManager();
        if (pm == null) return keep;

        addServicePackages(pm, ACTION_INPUT_METHOD, keep);
        addComponentPackage(context, Settings.Secure.DEFAULT_INPUT_METHOD, keep);

        String a11y = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        addFlattened(a11y, keep);

        if (vpnActive(context)) {
            // Every app that can run a VPN declares a VpnService, so skipping them all covers the
            // one that is actually connected without asking for the owner UID (a system-only API).
            addServicePackages(pm, ACTION_VPN_SERVICE, keep);
        }
        return keep;
    }

    private static void addServicePackages(PackageManager pm, String action, Set<String> out) {
        try {
            List<ResolveInfo> services = pm.queryIntentServices(new Intent(action), 0);
            if (services == null) return;
            for (ResolveInfo info : services) {
                if (info != null && info.serviceInfo != null && info.serviceInfo.packageName != null) {
                    out.add(info.serviceInfo.packageName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not list services for " + action, e);
        }
    }

    private static void addComponentPackage(Context context, String setting, Set<String> out) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), setting);
            addFlattened(value, out);
        } catch (Exception ignored) {
        }
    }

    private static void addFlattened(String value, Set<String> out) {
        if (value == null || value.isEmpty()) return;
        for (String part : value.split(":")) {
            int slash = part.indexOf('/');
            if (slash > 0) out.add(part.substring(0, slash).trim());
        }
    }

    private static boolean vpnActive(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network[] networks = cm.getAllNetworks();
            if (networks == null) return false;
            for (Network network : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not check for an active VPN", e);
        }
        return false;
    }
}
