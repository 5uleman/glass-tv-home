package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The apps the recent-apps deck shows, newest first: launches the launcher recorded itself come
 * first, then whatever the system still reports. Only launchable user apps: no launcher, no system
 * apps, no non-launchable packages.
 */
final class RecentsDeckItems {

    static final int LIMIT = 10;
    private static final String PREFS_NAME = "recent_apps_prefs";
    private static final String KEY_HISTORY = "recent_history";

    private RecentsDeckItems() {
    }

    static List<RecentAppItem> load(Context ctx) {
        List<RecentAppItem> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        if (pm == null) return out;

        LinkedHashSet<String> packages = new LinkedHashSet<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String saved = sp.getString(KEY_HISTORY, "");
            if (saved != null && !saved.isEmpty()) {
                for (String pkg : saved.split(",")) {
                    if (!pkg.isEmpty()) packages.add(pkg);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (RecentAppItem item : RecentAppsManager.getRecentApps(ctx)) {
                if (item != null && item.packageName != null && !item.packageName.isEmpty()) {
                    packages.add(item.packageName);
                }
            }
        } catch (Exception ignored) {
        }

        for (String pkg : packages) {
            if (out.size() >= LIMIT) break;
            if (pkg.equals(ctx.getPackageName())) continue;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                if ((info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                    continue;
                }
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                CharSequence label = info.loadLabel(pm);
                Drawable icon = info.loadIcon(pm);
                out.add(new RecentAppItem(pkg, String.valueOf(label), icon, launch));
            } catch (PackageManager.NameNotFoundException ignored) {
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
