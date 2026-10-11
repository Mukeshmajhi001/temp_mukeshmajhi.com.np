package com.mukeshmajhi.neonorbit;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String GAME_URL = "https://mukeshmajhi.com.np/";
    private static final String UPDATE_URL = "https://mukeshmajhi.com.np/app-update.json";
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView gameView;
    private AlertDialog downloadDialog;
    private ProgressBar downloadProgress;
    private TextView downloadStatus;
    private volatile HttpURLConnection activeDownload;
    private volatile boolean downloadCancelled;

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
                .setPositiveButton("Update now", (dialog, which) -> startUpdateDownload(downloadUrl))
                .show();
    }

    private void startUpdateDownload(String downloadUrl) {
        if (downloadUrl == null || !downloadUrl.startsWith("https://") || !downloadUrl.toLowerCase(Locale.US).contains(".apk")) {
            showDownloadError("The update URL is not a secure APK link.");
            return;
        }
        downloadCancelled = false;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 8, 48, 8);
        downloadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);
        downloadStatus = new TextView(this);
        downloadStatus.setGravity(Gravity.CENTER);
        downloadStatus.setText("Preparing update...");
        layout.addView(downloadProgress, new LinearLayout.LayoutParams(-1, 24));
        layout.addView(downloadStatus, new LinearLayout.LayoutParams(-1, 48));
        downloadDialog = new AlertDialog.Builder(this)
                .setTitle("Downloading update")
                .setView(layout)
                .setNegativeButton("Cancel", (dialog, which) -> downloadExecutorCancel())
                .create();
        downloadDialog.setCanceledOnTouchOutside(false);
        downloadDialog.show();
        updateExecutor.execute(() -> downloadApk(downloadUrl));
    }

    private void downloadExecutorCancel() {
        downloadCancelled = true;
        HttpURLConnection connection = activeDownload;
        if (connection != null) connection.disconnect();
    }

    private void downloadApk(String downloadUrl) {
        File apk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "neon-orbit-update.apk");
        File partialApk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "neon-orbit-update.apk.part");
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            activeDownload = connection;
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("Update download returned HTTP " + connection.getResponseCode());
            }
            int total = connection.getContentLength();
            long downloaded = 0;
            if (partialApk.exists()) partialApk.delete();
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partialApk)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (downloadCancelled) throw new Exception("Update cancelled");
                    output.write(buffer, 0, read);
                    downloaded += read;
                    int percent = total > 0 ? (int) ((downloaded * 100) / total) : 0;
                    long progressDownloaded = downloaded;
                    int progressTotal = total;
                    mainHandler.post(() -> updateDownloadProgress(percent, progressDownloaded, progressTotal));
                }
            } finally {
                connection.disconnect();
                activeDownload = null;
            }
            if (downloadCancelled) throw new Exception("Update cancelled");
            if (partialApk.length() < 4 || !isZipApk(partialApk)) {
                partialApk.delete();
                throw new Exception("The update URL did not return a valid APK file");
            }
            // Swap atomically as far as the app's private storage allows, keeping a rollback copy.
            File backupApk = new File(apk.getParentFile(), "neon-orbit-update.apk.bak");
            if (backupApk.exists()) backupApk.delete();
            boolean movedOld = apk.exists() && apk.renameTo(backupApk);
            if (!partialApk.renameTo(apk)) {
                if (movedOld) backupApk.renameTo(apk);
                throw new Exception("Could not prepare the APK installer file");
            }
            if (backupApk.exists()) backupApk.delete();
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) installDownloadedApk(apk);
            });
        } catch (Exception error) {
            if (partialApk.exists()) partialApk.delete();
            if (!downloadCancelled) mainHandler.post(() -> showDownloadError(error.getMessage()));
        }
    }

    private boolean isZipApk(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.read() == 'P' && input.read() == 'K';
        }
    }

    private void updateDownloadProgress(int percent, long downloaded, int total) {
        if (downloadProgress == null || downloadStatus == null) return;
        downloadProgress.setProgress(percent);
        String downloadedMb = String.format(java.util.Locale.US, "%.1f MB", downloaded / 1048576f);
        String totalMb = total > 0 ? String.format(java.util.Locale.US, " / %.1f MB", total / 1048576f) : "";
        downloadStatus.setText(percent + "%  •  " + downloadedMb + totalMb);
    }

    private void installDownloadedApk(File apk) {
        if (downloadDialog != null) downloadDialog.dismiss();
        try {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception error) {
            showDownloadError("Android could not open the downloaded installer. Please try the update again.");
        }
    }

    private void showDownloadError(String message) {
        if (downloadDialog != null) downloadDialog.dismiss();
        new AlertDialog.Builder(this)
                .setTitle("Update failed")
                .setMessage(message == null || message.isEmpty()
                        ? "The update could not be downloaded. Please try again later."
                        : message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) gameView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        downloadExecutorCancel();
        if (gameView != null) gameView.destroy();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
