package com.mukeshmajhi.neonorbit;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    public static final String GAME_URL = "https://mukeshmajhi.com.np/";
    private static final String UPDATE_URL = "https://mukeshmajhi.com.np/app-update.json";
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView gameView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openGame();
        checkForUpdate();
    }

    private void openGame() {
        gameView = new WebView(this);
        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        gameView.setWebViewClient(new WebViewClient());
        gameView.loadUrl(GAME_URL);
        setContentView(gameView);
    }

    private void checkForUpdate() {
        updateExecutor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setRequestMethod("GET");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    JSONObject update = new JSONObject(body.toString());
                    int installedVersionCode = getInstalledVersionCode();
                    int latestVersionCode = update.optInt("versionCode", installedVersionCode);
                    String downloadUrl = update.optString("downloadUrl", GAME_URL);
                    String versionName = update.optString("versionName", "new version");
                    if (latestVersionCode > installedVersionCode) {
                        mainHandler.post(() -> showUpdateDialog(versionName, downloadUrl));
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // Update checks must never stop the game from opening offline.
            }
        });
    }

    @SuppressWarnings("deprecation")
    private int getInstalledVersionCode() throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        return info.versionCode;
    }

    private void showUpdateDialog(String versionName, String downloadUrl) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("New update available")
                .setMessage("Mukesh Majhi Neon Orbit " + versionName + " is ready. Update now for the latest version.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Update now", (dialog, which) -> {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(downloadUrl)));
                    } catch (Exception ignored) {
                        // Keep the game open if no browser can handle the URL.
                    }
                })
                .show();
    }

    @Override public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) gameView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (gameView != null) gameView.destroy();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
