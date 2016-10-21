package com.vng.mediacodectest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import com.vng.mediacodectest.gles.EglCore;
import com.vng.mediacodectest.gles.FullFrameRect;
import com.vng.mediacodectest.gles.Texture2dProgram;
import com.vng.mediacodectest.gles.WindowSurface;

import java.io.IOException;

/**
 * Created by ductn on 10/18/16.
 */

public class MainActivity extends Activity implements SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener {
    private EglCore mEGLCore;
    private WindowSurface mDisplaySurface;
    private WindowSurface mEncoderSurface;
    private FullFrameRect mFullFrameBlit;
    private Camera mCamera;
    private SurfaceTexture mCameraTexture;
    private int mTextureID;
    private Encoder mEncoder;
    private final float[] mTmpMatrix = new float[16];
    private Button mRecordButton;
    private boolean mIsRecording = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        SurfaceView sv = (SurfaceView) findViewById(R.id.surface_view);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        final Context context = this;

        mRecordButton = (Button) findViewById(R.id.btn_record);
        mRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsRecording) {
                    mIsRecording = true;
                    mRecordButton.setText("Stop record");
                } else if (mIsRecording) {
                    mIsRecording = false;
                    if (mEncoder != null) {
                        mEncoder.drainEncoder(true);
                    }
                    mEncoder.pause();
                    mRecordButton.setText("Start record");

                    // Switch to video playback activity
                    Intent intent = new Intent(context, VideoPlaybackActivity.class);
                    startActivity(intent);
                }
            }
        });
    }

    @Override
    protected void onResume(){
        super.onResume();
        openCamera(1280, 720);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Initialize variables
        mEGLCore = new EglCore(null, EglCore.FLAG_RECORDABLE);

        mDisplaySurface = new WindowSurface(mEGLCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        mFullFrameBlit = new FullFrameRect(new Texture2dProgram(
                Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureID = mFullFrameBlit.createTextureObject();

        // Specify ID for texture from camera
        mCameraTexture = new SurfaceTexture(mTextureID);
        mCameraTexture.setOnFrameAvailableListener(this);

        try {
            mCamera.setPreviewTexture(mCameraTexture);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        mEncoder = new Encoder(720, 1280, 1500000, 30);
        mEncoderSurface = new WindowSurface(mEGLCore, mEncoder.getInputSurface(), false);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        long tStart = System.currentTimeMillis();

        // Update the texture from camera to GPU
        mDisplaySurface.makeCurrent();
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);

        // Render to screen
        SurfaceView sv = (SurfaceView) findViewById(R.id.surface_view);
        int viewWidth = sv.getWidth();
        int viewHeight = sv.getHeight();
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        mFullFrameBlit.drawFrame(mTextureID, mTmpMatrix);
        mDisplaySurface.swapBuffers();

        // TODO: move to another thread
        if (mIsRecording) {
            // Render to encoder's surface
            mEncoderSurface.makeCurrent();
            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glViewport(0, 0, 720, 1280);
            mFullFrameBlit.drawFrame(mTextureID, mTmpMatrix);
            mEncoder.drainEncoder(false);
            mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
            mEncoderSurface.swapBuffers();
        }

        long tElapsed = System.currentTimeMillis() - tStart;
        Log.d("DEBUG:", "Process time is: " + tElapsed);
    }

    /**
     * Try to choose front camera and open it
     * @param width
     * @param height
     */
    private void openCamera(int width, int height) {
        if (mCamera != null) {
            // Camera has already initialized
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to open front camera
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }

        // Set proper parameter for camera
        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewSize(width, height);
        params.setPreviewFrameRate(30);
        mCamera.setDisplayOrientation(90);
        mCamera.setParameters(params);

        mCamera.startPreview();
    }

    /**
     * Stop camera preview and release camera
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
}