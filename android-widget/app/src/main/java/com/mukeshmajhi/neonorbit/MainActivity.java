package com.mukeshmajhi.neonorbit;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        checkForUpdate();
    }

    private void openGame() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GAME_URL)));
        finish();
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
                    int latestVersionCode = update.optInt("versionCode", BuildConfig.VERSION_CODE);
                    String downloadUrl = update.optString("downloadUrl", GAME_URL);
                    String versionName = update.optString("versionName", "new version");
                    if (latestVersionCode > BuildConfig.VERSION_CODE) {
                        mainHandler.post(() -> showUpdateDialog(versionName, downloadUrl));
                    } else {
                        mainHandler.post(this::openGame);
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // Update checks must never stop the game from opening offline.
                mainHandler.post(this::openGame);
            }
        });
    }

    private void showUpdateDialog(String versionName, String downloadUrl) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("New update available")
                .setMessage("Mukesh Majhi Neon Orbit " + versionName + " is ready. Update now for the latest version.")
                .setNegativeButton("Later", (dialog, which) -> openGame())
                .setPositiveButton("Update now", (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))))
                .show();
    }

    @Override protected void onDestroy() {
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
