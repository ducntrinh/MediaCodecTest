package com.vng.mediacodectest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by ductn on 10/20/16.
 */

public class Encoder {
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 1;           // sync frame every second
    private MediaCodec mEncoder;
    private int mEncodeStatus;
    private MediaCodec.BufferInfo mBufferInfo;
    private Surface mInputSurface;
    private MediaFormat mEncodedFormat;

    public Encoder(int width, int height, int bitrate, int fps) {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void shutdown() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    public void drainEncoder() {
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mEncodedFormat = mEncoder.getOutputFormat();
            } else if (encoderStatus < 0) {
                // Ignore
            } else {
                // Process output data
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                Log.d("ENCODER", "New output buffer arrived");

                mEncoder.releaseOutputBuffer(encoderStatus, false);
            }
        }

    }
}
