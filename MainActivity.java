package tech.dodd.tapattack;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TapAttack"; // tag for debug logging
    private Button mainButton; //Used to start playing the game
    private TextView nameView; //Used to display the users Google Play Games username
    private TextView scoreView; //Used to display the game score
    private TextView timeView; //Used to display the game time limit
    private LinearLayout layoutSignin, layoutSignout; //We will toggle a Signin and Signout layout

    private int clickScore = 0; //The game score
    private boolean playing = false; //Whether the game is being played or not

    // Client used to sign in with Google APIs
    private GoogleSignInClient mGoogleSignInClient;

    // Client variables
    private AchievementsClient mAchievementsClient;
    private LeaderboardsClient mLeaderboardsClient;
    //private EventsClient mEventsClient;
    private PlayersClient mPlayersClient;

    // request codes we use when invoking an external activity
    private static final int RC_UNUSED = 5001;
    private static final int RC_SIGN_IN = 9001;

    // achievements and scores we're pending to push to the cloud
    // (waiting for the user to sign in, for instance)
    private final AccomplishmentsOutbox mOutbox = new AccomplishmentsOutbox();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainButton = findViewById(R.id.main_button); //Used to start playing the game
        nameView = findViewById(R.id.name_view); //Used to display the users Google Play Games username
        scoreView = findViewById(R.id.score_view); //Used to display the game score
        timeView = findViewById(R.id.time_view); //Used to display the game time limit
        SignInButton buttonSignIn = findViewById(R.id.sign_in_button); //Google Signin Button
        layoutSignin = findViewById(R.id.sign_in_bar); //layout for displaying Signin Elements
        layoutSignout = findViewById(R.id.sign_out_bar); //layout for displaying Signout Elements

        //Used when the Google Signin button is pressed.
        buttonSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSignInIntent();
            }
        });

        // Create the client used to sign in to Google services.
        mGoogleSignInClient = GoogleSignIn.getClient(this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build());


        //The simple game - click as many times as you can in 30 seconds
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!playing) {
                    // The first click
                    playing = true;
                    clickScore = 0;
                    mainButton.setText("Keep Clicking");

                    // Initialize CountDownTimer to 30 seconds
                    new CountDownTimer(30000, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            //As each second ticks down during the game the textView is updated.
                            timeView.setText("Time remaining: " + millisUntilFinished / 1000);
                        }

                        @Override
                        public void onFinish() {
                            //When the game is finished - the time has run out.

                            //Resets the game
                            playing = false;
                            mainButton.setText("Start");
                            timeView.setText("Game over");

                            //Updates the number of times the game is played.
                            mOutbox.mNumPlays++;
                            // update leaderboards
                            updateLeaderboards(clickScore);
                        }
                    }.start();  // Start the timer
                } else {
                    // Subsequent clicks
                    clickScore++;
                    scoreView.setText("Score: " + clickScore + " points");
                    // check for achievements
                    checkForAchievements(clickScore);
                }
            }
        });
    }

    private boolean isSignedIn() {
        return GoogleSignIn.getLastSignedInAccount(this) != null;
    }

    private void signInSilently() {
        Log.d(TAG, "signInSilently()");

        mGoogleSignInClient.silentSignIn().addOnCompleteListener(this,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInSilently(): success");
                            onConnected(task.getResult());
                        } else {
                            Log.d(TAG, "signInSilently(): failure", task.getException());
                            onDisconnected();
                        }
                    }
                });
    }

    private void startSignInIntent() {
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        // Since the state of the signed in user can change when the activity is not active
        // it is recommended to try and sign in silently from when the app resumes.
        signInSilently();
    }

    private void signOut() {
        Log.d(TAG, "signOut()");

        if (!isSignedIn()) {
            Log.w(TAG, "signOut() called, but was not signed in!");
            return;
        }

        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        boolean successful = task.isSuccessful();
                        Log.d(TAG, "signOut(): " + (successful ? "success" : "failed"));

                        onDisconnected();
                    }
                });
    }

    public void onShowAchievementsRequested(View v) {
        //If the Achievements button is pressed and the user is signed in show Achievements else Toast
        if (isSignedIn()) {
            mAchievementsClient.getAchievementsIntent()
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            startActivityForResult(intent, RC_UNUSED);
                }
            })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            handleException(e, getString(R.string.achievements_exception));
                        }
                    });
        }else{
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
        }
    }

    public void onShowLeaderboardsRequested(View v) {
        //If the Leaderboards button is pressed and the user is signed in show Leaderboards else Toast
        if (GoogleSignIn.getLastSignedInAccount(this) != null){
            mLeaderboardsClient.getAllLeaderboardsIntent()
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            startActivityForResult(intent, RC_UNUSED);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            handleException(e, getString(R.string.leaderboards_exception));
                        }
                    });
        }else{
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
        }

    }

    private void handleException(Exception e, String details) {
        int status = 0;

        if (e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            status = apiException.getStatusCode();
        }

        String message = getString(R.string.status_exception_error, details, status, e);

        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, null)
                .show();
    }

    // Checks if n is prime. We don't consider 0 and 1 to be prime.
    // This is not an implementation we are mathematically proud of, but it gets the job done.
    private boolean isPrime(int n) {
        int i;
        if (n == 0 || n == 1) {
            return false;
        }
        for (i = 2; i <= n / 2; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    /*
     * Check for achievements and unlock the appropriate ones.
     *
     * @param clickScore the score the user got.
     */
    private void checkForAchievements(int score) {
        // Check if each condition is met; if so, unlock the corresponding achievement.
        if (isPrime(score)) {
            mOutbox.mPrimeAchievement = true;
            achievementToast(getString(R.string.achievement_prime_toast_text));
            pushAccomplishments();
        }
        if (score == 100) {
            mOutbox.mLightningAchievement = true;
            achievementToast(getString(R.string.achievement_lightning_toast_text));
            pushAccomplishments();
        }
        if (score == 10) {
            mOutbox.mLazyAchievement = true;
            achievementToast(getString(R.string.achievement_lazy_toast_text));
            pushAccomplishments();
        }
        if (score == 37) {
            mOutbox.mLeetAchievement = true;
            achievementToast(getString(R.string.achievement_leet_toast_text));
            pushAccomplishments();
        }
    }

    private void achievementToast(String achievement) {
        // Only show toast if not signed in. If signed in, the standard Google Play
        // toasts will appear, so we don't need to show our own.
        if (!isSignedIn()) {
            Toast.makeText(this, getString(R.string.achievement) + ": " + achievement,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void pushAccomplishments() {
        if (!isSignedIn()) {
            // can't push to the cloud, try again later
            return;
        }
        if (mOutbox.mPrimeAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_prime_clicks));
            mOutbox.mPrimeAchievement = false;
        }
        if (mOutbox.mLightningAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_lightning_fast));
            mOutbox.mLightningAchievement = false;
        }
        if (mOutbox.mLazyAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_lazy_clicks));
            mOutbox.mLazyAchievement = false;
        }
        if (mOutbox.mLeetAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_leet_clicks));
            mOutbox.mLeetAchievement = false;
        }
        if (mOutbox.mNumPlays > 0) {
            mAchievementsClient.increment(getString(R.string.achievement_play_a_whole_lot),
                    mOutbox.mNumPlays);
            mAchievementsClient.increment(getString(R.string.achievement_play_a_lot),
                    mOutbox.mNumPlays);
            mOutbox.mNumPlays = 0;
        }
        //Change this number to only submit scores higher than x
        if (mOutbox.mClickScore >= 0) {
            mLeaderboardsClient.submitScore(getString(R.string.leaderboard_most_taps_attacked),
                    mOutbox.mClickScore);
            mOutbox.mClickScore = -1;
        }
    }

    //Update leaderboards with the user's score.
    private void updateLeaderboards(int score) {
        if (mOutbox.mClickScore < score) {
            mOutbox.mClickScore = score;
            pushAccomplishments();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(intent);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                onConnected(account);
            } catch (ApiException apiException) {
                String message = apiException.getMessage();
                if (message == null || message.isEmpty()) {
                    message = getString(R.string.signin_other_error);
                }

                onDisconnected();

                if(message.startsWith("13")){
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.alert_nologin_title)
                            .setMessage(R.string.alert_nologin_message)
                            .setNeutralButton(android.R.string.ok, null)
                            .show();
                }else{
                    new AlertDialog.Builder(this)
                            .setMessage(message)
                            .setNeutralButton(android.R.string.ok, null)
                            .show();
                }
            }
        }
    }

    private void onConnected(GoogleSignInAccount googleSignInAccount) {
        Log.d(TAG, "onConnected(): connected to Google APIs");
        
        //Added this to show Google Play Games Achievement Notifications - If statement because it can be null if user signsout
        if (isSignedIn()) {
            GamesClient gamesClient = Games.getGamesClient(MainActivity.this, GoogleSignIn.getLastSignedInAccount(this));
            gamesClient.setViewForPopups(findViewById(R.id.gps_popup));
        }

        mAchievementsClient = Games.getAchievementsClient(this, googleSignInAccount);
        mLeaderboardsClient = Games.getLeaderboardsClient(this, googleSignInAccount);
        mPlayersClient = Games.getPlayersClient(this, googleSignInAccount);

        // Hide sign-in button and Show sign-out button on main menu
        layoutSignin.setVisibility(View.GONE);
        layoutSignout.setVisibility(View.VISIBLE);

        // Set the greeting appropriately on main menu
        mPlayersClient.getCurrentPlayer().addOnCompleteListener(new OnCompleteListener<Player>() {
            @Override
            public void onComplete(@NonNull Task<Player> task) {
                String displayName;
                if (task.isSuccessful()) {
                    displayName = task.getResult().getDisplayName();
                } else {
                    Exception e = task.getException();
                    handleException(e, getString(R.string.players_exception));
                    displayName = "???";
                }
                nameView.setText("Hello, " + displayName);
            }
        });


        // if we have accomplishments to push, push them
        if (!mOutbox.isEmpty()) {
            pushAccomplishments();
            Toast.makeText(this, getString(R.string.your_progress_will_be_uploaded),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onDisconnected() {
        Log.d(TAG, "onDisconnected()");

        mAchievementsClient = null;
        mLeaderboardsClient = null;
        mPlayersClient = null;

        // Show sign-in button and signed-out greeting on main menu
        layoutSignout.setVisibility(View.GONE);
        layoutSignin.setVisibility(View.VISIBLE);
        nameView.setText(getString(R.string.signed_out_greeting));
    }


    public void onSignInButtonClicked(View v) {
        startSignInIntent();
    }


    public void onSignOutButtonClicked(View v) {
        signOut();
    }

    private class AccomplishmentsOutbox {
        boolean mPrimeAchievement = false;
        boolean mLazyAchievement = false;
        boolean mLeetAchievement = false;
        boolean mLightningAchievement = false;
        int mNumPlays = 0;
        int mClickScore = -1;

        boolean isEmpty() {
            return !mPrimeAchievement && !mLazyAchievement && !mLeetAchievement &&
                    !mLightningAchievement && mNumPlays == 0 && mClickScore < 0;
        }

    }
}
