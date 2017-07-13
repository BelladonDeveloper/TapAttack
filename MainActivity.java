package tech.dodd.tapattack;

import android.content.Intent;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TapAttack";
    private Button mainButton;
    private TextView scoreView;
    private TextView timeView;
    private GoogleApiClient apiClient;
    private Button buttonSignIn;

    private int score = 0;
    private boolean playing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainButton = (Button) findViewById(R.id.main_button);
        scoreView = (TextView) findViewById(R.id.score_view);
        timeView = (TextView) findViewById(R.id.time_view);
        buttonSignIn = (Button) findViewById(R.id.sign_in_button);

        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!playing) {
                    // The first click
                    playing = true;
                    mainButton.setText("Keep Clicking");

                    // Initialize CountDownTimer to 60 seconds
                    new CountDownTimer(60000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            timeView.setText("Time remaining: " + millisUntilFinished / 1000);
                        }

                        @Override
                        public void onFinish() {
                            playing = false;
                            timeView.setText("Game over");
                            mainButton.setVisibility(View.GONE);

                            Games.Leaderboards.submitScore(apiClient,
                                    getString(R.string.leaderboard_most_taps_attacked),
                                    score);
                        }
                    }.start();  // Start the timer
                } else {
                    // Subsequent clicks
                    score++;
                    scoreView.setText("Score: " + score + " points");
                    if (score > 100) {
                        Games.Achievements
                                .unlock(apiClient,
                                        getString(R.string.achievement_lightning_fast));
                    }

                }
            }
        });
    }

    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);

        //If the user signs out on the Leaderboard or Achievements screen disconnect / reenable sign in from button
        if ((responseCode == GamesActivityResultCodes.RESULT_RECONNECT_REQUIRED) && ((requestCode == 0) || (requestCode == 1))) {
            apiClient.stopAutoManage(this);
            apiClient.disconnect();
            apiClient = null;
        }
    }

    public void showLeaderboard(View v) {
        if (apiClient != null && apiClient.isConnected()) {
            startActivityForResult(
                    Games.Leaderboards.getLeaderboardIntent(apiClient,
                            getString(R.string.leaderboard_most_taps_attacked)), 0);
        } else {
            Toast.makeText(this, R.string.notconnectedtext, Toast.LENGTH_LONG).show();
        }
    }

    public void showAchievements(View v) {
        if (apiClient != null && apiClient.isConnected()) {
            startActivityForResult(
                    Games.Achievements
                            .getAchievementsIntent(apiClient), 1);
        } else {
            Toast.makeText(this, R.string.notconnectedtext, Toast.LENGTH_LONG).show();
        }
    }

    public void onSignInButtonClicked(View v) {
        if (apiClient != null && apiClient.isConnected()) {
            Toast.makeText(this, R.string.connectedtext, Toast.LENGTH_LONG).show();
        } else {
            apiClient = new GoogleApiClient.Builder(this)
                    .addApi(Games.API)
                    .addScope(Games.SCOPE_GAMES)
                    .setViewForPopups(findViewById(android.R.id.content))
                    .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                            Log.e(TAG, "Could not connect to Play games services");
                        }
                    }).build();
        }
    }
}