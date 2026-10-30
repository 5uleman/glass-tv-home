package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages OTA in-app update checks, changelog display, and seamless APK downloading
 * for Glass TV Home directly on Android TV.
 */
public final class AppUpdateManager {
    /** Updates are off: no check may reach GitHub, from any entry point. Flip to true to re-enable. */
    static final boolean UPDATES_ENABLED = false;
    private static final String TAG = "AppUpdateManager";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/aqmangge/glass-tv-home/main/release/version.json";
    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK = "last_check_timestamp";
    public static final String KEY_AUTO_UPDATE = "pref_auto_update";
    public static final String KEY_AUTO_UPDATE_TIME = "pref_auto_update_time";
    public static final String DEFAULT_AUTO_UPDATE_TIME = "03:30 AM";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppUpdateManager() {
    }

    public static boolean isAutoUpdateEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_AUTO_UPDATE, true); // default ON
    }

    public static void setAutoUpdateEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply();
    }

    public static String getAutoUpdateTime(Context context) {
        if (context == null) return DEFAULT_AUTO_UPDATE_TIME;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_AUTO_UPDATE_TIME, DEFAULT_AUTO_UPDATE_TIME);
    }

    public static void setAutoUpdateTime(Context context, String time) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_AUTO_UPDATE_TIME, time != null ? time : DEFAULT_AUTO_UPDATE_TIME).apply();
    }

    public static void checkAutoUpdateIfDue(Activity activity) {
        if (activity == null || !isAutoUpdateEnabled(activity)) return;
        SharedPreferences sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = sp.getLong(KEY_LAST_CHECK, 0);
        // Check once every 12 hours at least
        if (System.currentTimeMillis() - lastCheck >= 12 * 3600 * 1000L) {
            checkForUpdates(activity, false);
        }
    }

    public interface CheckCallback {
        void onResult(boolean hasUpdate, String newVersionName, String changelog, String apkUrl);
    }

    /**
     * Checks for updates. If manualCheck is true, shows feedback toasts even when up-to-date.
     */
    public static void checkForUpdates(Activity activity, boolean manualCheck) {
        if (!UPDATES_ENABLED) return;
        if (activity == null || activity.isFinishing()) return;

        if (manualCheck) {
            Toast.makeText(activity, i18n(activity, "🔄 Checking for the latest update...", "🔄 Memeriksa pembaruan terbaru...", "🔄 Menyemak kemas kini terkini..."), Toast.LENGTH_SHORT).show();
        }

        executor.execute(() -> {
            try {
                URL url = new URL(VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    if (manualCheck) {
                        mainHandler.post(() -> Toast.makeText(activity, i18n(activity, "❌ Failed to reach the update server (HTTP ", "❌ Gagal menghubungi server pembaruan (HTTP ", "❌ Gagal menyemak pelayan kemas kini (HTTP ") + responseCode + ")", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                int remoteVersionCode = json.optInt("versionCode", 0);
                String remoteVersionName = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "");
                String changelog = json.optString("changelog", i18n(activity, "Performance and stability improvements.", "Peningkatan performa dan stabilitas.", "Penambahbaikan prestasi dan kestabilan."));

                PackageManager pm = activity.getPackageManager();
                PackageInfo pInfo = pm.getPackageInfo(activity.getPackageName(), 0);
                int currentVersionCode = pInfo.versionCode;
                String currentVersionName = pInfo.versionName;

                // Save timestamp
                SharedPreferences sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                sp.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();

                mainHandler.post(() -> {
                    if (activity.isFinishing()) return;

                    if (remoteVersionCode > currentVersionCode) {
                        showUpdateDialog(activity, remoteVersionName, currentVersionName, changelog, apkUrl);
                    } else {
                        if (manualCheck) {
                            Toast.makeText(activity, i18n(activity, "✅ Glass TV Home is already up to date (v", "✅ Glass TV Home sudah versi terbaru (v", "✅ Glass TV Home sudah di versi terkini (v") + currentVersionName + ")!", Toast.LENGTH_LONG).show();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error checking updates: " + e.getMessage());
                if (manualCheck) {
                    mainHandler.post(() -> Toast.makeText(activity, i18n(activity, "❌ Update check error. Please make sure Wi-Fi is connected.", "❌ Kesalahan memeriksa pembaruan. Pastikan Wi-Fi tersambung.", "❌ Ralat menyemak kemas kini. Sila pastikan Wi-Fi bersambung."), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static String i18n(Activity activity, String en, String id, String ms) {
        String lang = AppLanguage.getLanguage(activity);
        if (AppLanguage.LANG_EN.equals(lang)) return en;
        if (AppLanguage.LANG_ID.equals(lang)) return id;
        return ms;
    }

    private static void showUpdateDialog(Activity activity, String newVersionName, String currentVersionName, String changelog, String apkUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(i18n(activity, "🚀 New Update Found (v", "🚀 Pembaruan Baru Ditemukan (v", "🚀 Kemas Kini Baharu Ditemui (v") + newVersionName + ")");
        
        String message = i18n(activity, "Current Version: v", "Versi Saat Ini: v", "Versi Semasa: v") + currentVersionName + "\n"
                + i18n(activity, "Latest Version: v", "Versi Terbaru: v", "Versi Terkini: v") + newVersionName + "\n\n"
                + i18n(activity, "📋 Release Notes:\n", "📋 Catatan Pembaruan:\n", "📋 Nota Pembaharuan:\n") + changelog + "\n\n"
                + i18n(activity, "Do you want to download and install it now?", "Apakah Anda ingin mengunduh dan memasangnya sekarang?", "Adakah anda ingin memuat turun dan memasang sekarang?");
        builder.setMessage(message);

        builder.setPositiveButton(i18n(activity, "Download & Install", "Unduh & Pasang", "Muat Turun & Pasang"), (dialog, which) -> {
            dialog.dismiss();
            downloadAndInstallApk(activity, apkUrl, newVersionName);
        });

        builder.setNegativeButton(i18n(activity, "Not Now", "Nanti Saja", "Nanti Sahaja"), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private static void downloadAndInstallApk(Activity activity, String apkUrl, String versionName) {
        // Check install permission on Android 8.0+ (Oreo, API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                AlertDialog.Builder permDialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog);
                permDialog.setTitle(i18n(activity, "⚠️ Install Permission Required", "⚠️ Izin Pemasangan Diperlukan", "⚠️ Kebenaran Pemasangan Diperlukan"));
                permDialog.setMessage(i18n(activity, "Enable 'Allow from this source' so Glass TV Home can install automatic updates.", "Aktifkan 'Allow from this source' agar Glass TV Home dapat memasang pembaruan otomatis.", "Sila aktifkan 'Allow from this source' untuk membenarkan Glass TV Home memasang kemas kini automatik."));
                permDialog.setPositiveButton(i18n(activity, "Open Settings", "Buka Pengaturan", "Buka Tetapan"), (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(activity, i18n(activity, "Open Settings > Security > Install Unknown Apps", "Buka Pengaturan > Keamanan > Install Unknown Apps", "Buka Tetapan > Keselamatan > Install Unknown Apps"), Toast.LENGTH_LONG).show();
                    }
                });
                permDialog.setNegativeButton(i18n(activity, "Cancel", "Batal", "Batal"), (d, w) -> d.dismiss());
                permDialog.show();
                return;
            }
        }

        ProgressDialog progressDialog = new ProgressDialog(activity, R.style.Theme_TvHome_GlassDialog);
        progressDialog.setTitle(i18n(activity, "📥 Downloading Update (v", "📥 Mengunduh Pembaruan (v", "📥 Memuat Turun Kemas Kini (v") + versionName + ")");
        progressDialog.setMessage(i18n(activity, "Downloading the installation file...", "Sedang mengunduh file pemasangan...", "Sedang memuat turun fail pemasangan..."));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.setCancelable(false);
        progressDialog.show();

        executor.execute(() -> {
            File cacheDir = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
            File apkFile = new File(cacheDir, "glass-tv-update.apk");

            InputStream is = null;
            FileOutputStream fos = null;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();

                int fileLength = conn.getContentLength();
                is = conn.getInputStream();
                fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;

                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    fos.write(buffer, 0, count);
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> progressDialog.setProgress(progress));
                    }
                }
                fos.flush();

                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(activity, apkFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(activity, i18n(activity, "❌ Update download error: ", "❌ Kesalahan mengunduh pembaruan: ", "❌ Ralat memuat turun kemas kini: ") + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try {
                    if (fos != null) fos.close();
                    if (is != null) is.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, i18n(activity, "Update APK file not found.", "File APK pembaruan tidak ditemukan.", "Fail APK kemas kini tidak dijumpai."), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install error: " + e.getMessage());
            Toast.makeText(activity, i18n(activity, "❌ Failed to start installation: ", "❌ Gagal memulai pemasangan: ", "❌ Gagal memulakan pemasangan: ") + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
