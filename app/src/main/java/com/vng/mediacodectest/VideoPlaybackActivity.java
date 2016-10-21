package com.vng.mediacodectest;

import android.app.Activity;
import android.os.Bundle;
import android.widget.VideoView;

public class VideoPlaybackActivity extends Activity {
    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_playback);

        mVideoView = (VideoView) findViewById(R.id.video_view);
        mVideoView.setVideoPath("/sdcard/example.mp4");
        mVideoView.start();
    }
}
