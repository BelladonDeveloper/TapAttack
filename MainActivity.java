package tech.dodd.tapattack;

import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;

public class MainActivity extends AppCompatActivity {


    private Button mainButton;
    private TextView scoreView;
    private TextView timeView;
    private GoogleApiClient apiClient;

    private static final String TAG = "MainActivity";

    private int score = 0;
    private boolean playing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Games.API)
                .addScope(Games.SCOPE_GAMES)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Log.e(TAG, "Could not connect to Play games services");
                        finish();
                    }
                }).build();




        mainButton = (Button) findViewById(R.id.main_button);
        scoreView = (TextView) findViewById(R.id.score_view);
        timeView = (TextView) findViewById(R.id.time_view);

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
                    if(score>100) {
                        Games.Achievements
                                .unlock(apiClient,
                                        getString(R.string.achievement_lightning_fast));
                    }
                    scoreView.setText("Score: " + score + " points");
                }
            }
        });
    }

    public void showLeaderboard(View v) {
        startActivityForResult(
                Games.Leaderboards.getLeaderboardIntent(apiClient,
                        getString(R.string.leaderboard_most_taps_attacked)), 0);
    }

    public void showAchievements(View v) {
        startActivityForResult(
                Games.Achievements
                        .getAchievementsIntent(apiClient), 1);
    }


}
