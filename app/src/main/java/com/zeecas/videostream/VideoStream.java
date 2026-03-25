package com.zeecas.videostream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class VideoStream extends Activity {

    private static final String TAG = "VideoStream";
    private static final int SPEECH_REQUEST_CODE = 42;
    private String mServerIp = "192.168.1.254:5000";
    private String mPollUrl = "";
    private String mVideoUrl = "";

    private VideoView mVideoView;
    private MediaController mMediaController;
    private TextView mWaitingText;

    private String mCurrentVideoUrl = "";
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPollRunnable;

    private GestureDetector mGestureDetector;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mServerIp = prefs.getString("server_ip", "192.168.1.254:5000");
        updateUrls();

        // Hide Title Bar and make Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_video_stream);

        mVideoView = (VideoView) findViewById(R.id.videoView);
        mWaitingText = (TextView) findViewById(R.id.waitingText);

        // Initialize MediaController
        mMediaController = new MediaController(this);
        mMediaController.setAnchorView(mVideoView);
        mVideoView.setMediaController(mMediaController);

        mVideoView.requestFocus();

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.i(TAG, "Video is prepared and ready to play");
                mVideoView.start();
            }
        });

        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "Error playing video: what=" + what + " extra=" + extra);
                return true; // Return true to indicate we handled the error (prevents default dialog)
            }
        });

        // Start polling server
        mPollRunnable = new Runnable() {
            @Override
            public void run() {
                checkLatestVideo();
                mHandler.postDelayed(this, 3000); // Poll every 3 seconds
            }
        };
        mHandler.post(mPollRunnable);

        mGestureDetector = createGestureDetector(this);
    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.SWIPE_RIGHT) { // Swipe forward
                    if (mVideoView.isPlaying() || mVideoView.canPause()) {
                        int target = Math.min(mVideoView.getCurrentPosition() + 10000, mVideoView.getDuration());
                        mVideoView.seekTo(target);
                        return true;
                    }
                } else if (gesture == Gesture.SWIPE_LEFT) { // Swipe backward
                    if (mVideoView.isPlaying() || mVideoView.canPause()) {
                        int target = Math.max(mVideoView.getCurrentPosition() - 10000, 0);
                        mVideoView.seekTo(target);
                        return true;
                    }
                } else if (gesture == Gesture.TAP) {
                    if (mVideoView.isPlaying()) {
                        mVideoView.pause();
                    } else {
                        mVideoView.start();
                    }
                    return true;
                } else if (gesture == Gesture.LONG_PRESS) {
                    promptServerIp();
                    return true;
                }
                return false;
            }
        });
        return gestureDetector;
    }

    // ----------- CONFIGURATION VOICE PROMPT LOGIC -----------

    private void updateUrls() {
        mPollUrl = "http://" + mServerIp + "/get_youtube_url";
        mVideoUrl = "http://" + mServerIp + "/youtube";
    }

    private void promptServerIp() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak Server IP (e.g. 192 168 1 254)");
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);

                // Convert spoken "192 168 1 254" or "192 dot 168" into "192.168.1.254"
                String ip = spokenText.toLowerCase()
                        .replace("dot", ".")
                        .replace("point", ".")
                        .replaceAll("[^0-9\\.]+", ".");

                // Clean up multiple dots
                ip = ip.replaceAll("\\.+", ".");
                // Remove leading/trailing dots
                ip = ip.replaceAll("^\\.|\\.$", "");

                Log.i(TAG, "Parsed IP: " + ip);

                if (!ip.isEmpty()) {
                    mServerIp = ip + ":5000";
                    updateUrls();

                    SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                    prefs.edit().putString("server_ip", mServerIp).apply();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mPollRunnable);
        super.onDestroy();
    }

    private void checkLatestVideo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(mPollUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    reader.close();

                    JSONObject obj = new JSONObject(json.toString());
                    final String fetchedUrl = obj.getString("url");

                    if (!fetchedUrl.equals(mCurrentVideoUrl)) {
                        mCurrentVideoUrl = fetchedUrl;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!mCurrentVideoUrl.isEmpty()) {
                                    Log.i(TAG, "New video detected, setting URI");
                                    mWaitingText.setVisibility(View.GONE);
                                    mVideoView.setVisibility(View.VISIBLE);
                                    mVideoView.setVideoURI(Uri.parse(mVideoUrl));
                                } else {
                                    mWaitingText.setVisibility(View.VISIBLE);
                                    mVideoView.setVisibility(View.GONE);
                                    if (mVideoView.isPlaying()) {
                                        mVideoView.stopPlayback();
                                    }
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Polling error: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            // Re-show the media controller when user taps the touchpad
            if (!mMediaController.isShowing()) {
                mMediaController.show();
            } else {
                if (mVideoView.isPlaying()) {
                    mVideoView.pause();
                } else {
                    mVideoView.start();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
