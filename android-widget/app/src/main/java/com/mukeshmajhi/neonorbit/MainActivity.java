package com.mukeshmajhi.neonorbit;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    public static final String GAME_URL = "https://mukeshmajhi.com.np/";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openGame();
    }

    private void openGame() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GAME_URL)));
        finish();
    }
}
