package com.vng.mediacodectest;

import android.app.Activity;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vng.mediacodectest.gles.EglCore;
import com.vng.mediacodectest.gles.FullFrameRect;
import com.vng.mediacodectest.gles.Texture2dProgram;
import com.vng.mediacodectest.gles.WindowSurface;

import java.io.IOException;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView sv = (SurfaceView) findViewById(R.id.surface_view);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);
    }

    @Override
    protected void onResume(){
        super.onResume();
        openCamera(1280, 720);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mEGLCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEGLCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();
        mFullFrameBlit = new FullFrameRect(new Texture2dProgram(
                Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureID = mFullFrameBlit.createTextureObject();

        // Get texture from camera
        mCameraTexture = new SurfaceTexture(mTextureID);
        mCameraTexture.setOnFrameAvailableListener(this);

        try {
            mCamera.setPreviewTexture(mCameraTexture);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        mEncoder = new Encoder(1280, 720, 1500000, 30);
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

        // Render to encoder's surface
        mEncoderSurface.makeCurrent();
        GLES20.glViewport(0, 0, 1280, 720);
        mFullFrameBlit.drawFrame(mTextureID, mTmpMatrix);
        mEncoder.drainEncoder();
        mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
        mEncoderSurface.swapBuffers();
    }

    /**
     * Try to choose front camera and open it
     * @param width
     * @param height
     */
    private void openCamera(int width, int height) {
        if (mCamera != null) {
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }

        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewSize(width, height);
        params.setPreviewFrameRate(30);
        mCamera.setParameters(params);
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