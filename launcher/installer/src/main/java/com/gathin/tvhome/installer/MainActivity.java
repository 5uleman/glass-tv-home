package com.gathin.tvhome.installer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "installer_prefs";
    private static final String KEY_LAST_IP = "last_tv_ip";

    private TextView tvLocalIp;
    private EditText etTvIp;
    private EditText etTvPort;
    private Button btnScan;
    private Button btnInstall;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnLaunch;
    private Button btnWebServer;
    private TextView tvWebUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLocalIp = findViewById(R.id.tvLocalIp);
        etTvIp = findViewById(R.id.etTvIp);
        etTvPort = findViewById(R.id.etTvPort);
        btnScan = findViewById(R.id.btnScan);
        btnInstall = findViewById(R.id.btnInstall);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        btnLaunch = findViewById(R.id.btnLaunch);
        btnWebServer = findViewById(R.id.btnWebServer);
        tvWebUrl = findViewById(R.id.tvWebUrl);

        // Load local IP
        String localIp = TvScanner.getLocalIpAddress(this);
        tvLocalIp.setText("Your Phone IP: " + localIp);

        // Load saved TV IP
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedIp = sp.getString(KEY_LAST_IP, "");
        if (!savedIp.isEmpty()) {
            etTvIp.setText(savedIp);
        } else {
            // Suggest subnet IP
            if (localIp.contains(".")) {
                String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
                etTvIp.setHint(subnet + "xxx");
            }
        }

        // 1. Scan Wi-Fi Button
        btnScan.setOnClickListener(v -> scanNetwork());

        // 2. Install Button
        btnInstall.setOnClickListener(v -> startInstall());

        // 3. Launch App Button
        btnLaunch.setOnClickListener(v -> launchAppOnTv());

        // 4. Web Server Button
        btnWebServer.setOnClickListener(v -> toggleWebServer());
    }

    private void scanNetwork() {
        btnScan.setEnabled(false);
        btnScan.setText("🔄 Scanning Wi-Fi Network...");
        tvStatus.setText("Status: Searching for Android TV on the local network...");

        TvScanner.scan(this, new TvScanner.ScanCallback() {
            @Override
            public void onDeviceFound(String ip) {
                etTvIp.setText(ip);
                tvStatus.setText("Status: Android TV found at " + ip + "!");
                Toast.makeText(MainActivity.this, "TV found: " + ip, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanFinished(List<String> devices) {
                btnScan.setEnabled(true);
                btnScan.setText("🔍 Scan for Android TV on Wi-Fi Network");
                if (devices.isEmpty()) {
                    tvStatus.setText("Status: No TV detected automatically. Please make sure USB Debugging is enabled or enter the TV IP manually.");
                } else {
                    tvStatus.setText("Status: Scan complete. " + devices.size() + " TV device(s) found.");
                }
            }
        });
    }

    private void startInstall() {
        String ip = etTvIp.getText().toString().trim();
        String portStr = etTvPort.getText().toString().trim();
        int port = 5555;
        try {
            port = Integer.parseInt(portStr);
        } catch (Exception ignored) {
        }

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter your Android TV IP", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save IP for next time
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_LAST_IP, ip).apply();

        btnInstall.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        tvStatus.setText("Status: Connecting to TV...");

        AdbInstaller.install(this, ip, port, new AdbInstaller.InstallCallback() {
            @Override
            public void onProgress(int percent, String message) {
                progressBar.setProgress(percent);
                tvStatus.setText("Status: " + message);
            }

            @Override
            public void onSuccess(String message) {
                btnInstall.setEnabled(true);
                progressBar.setProgress(100);
                tvStatus.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnInstall.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                tvStatus.setText(error);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchAppOnTv() {
        String ip = etTvIp.getText().toString().trim();
        String portStr = etTvPort.getText().toString().trim();
        int port = 5555;
        try {
            port = Integer.parseInt(portStr);
        } catch (Exception ignored) {
        }

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter the TV IP", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("Launching Glass TV Home on the TV screen...");
        AdbInstaller.launchTvHome(this, ip, port, new AdbInstaller.InstallCallback() {
            @Override
            public void onProgress(int percent, String message) {
                tvStatus.setText("Status: " + message);
            }

            @Override
            public void onSuccess(String message) {
                tvStatus.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                tvStatus.setText(error);
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleWebServer() {
        if (LocalApkServer.isRunning()) {
            LocalApkServer.stop();
            btnWebServer.setText("🌐 TV Web Server");
            tvWebUrl.setVisibility(View.GONE);
            tvStatus.setText("Local web server stopped.");
        } else {
            LocalApkServer.start(this, 8080, new LocalApkServer.ServerStateCallback() {
                @Override
                public void onStarted(String url) {
                    runOnUiThread(() -> {
                        btnWebServer.setText("⏹️ Stop Server");
                        tvWebUrl.setVisibility(View.VISIBLE);
                        tvWebUrl.setText("Type on TV: " + url);
                        tvStatus.setText("Open a web browser or Downloader on the TV and type the address above to download.");
                        Toast.makeText(MainActivity.this, "Web Server Active: " + url, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onStopped() {
                    runOnUiThread(() -> {
                        btnWebServer.setText("🌐 TV Web Server");
                        tvWebUrl.setVisibility(View.GONE);
                    });
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalApkServer.stop();
    }
}
