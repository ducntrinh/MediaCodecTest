package com.vng.mediacodectest;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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
    private MediaMuxer mMuxer;
    private int mEncodeStatus;
    private MediaCodec.BufferInfo mBufferInfo;
    private Surface mInputSurface;
    private MediaFormat mEncodedFormat;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    public Encoder(int width, int height, int bitrate, int fps) {
        mBufferInfo = new MediaCodec.BufferInfo();
        mEncodedFormat = new MediaFormat();

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

        // Initialize muxer (hardcoded output file
        try {
            mMuxer = new MediaMuxer("/sdcard/example.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Stop encoder and muxer
     */
    public void pause() {
        if (mEncoder != null) {
            mEncoder.stop();
        }
        if (mMuxer != null) {
            mMuxer.stop();
        }
    }

    public void resume() {
        if (mEncoder != null) {
            mEncoder.start();
        }
        if (mMuxer != null) {
            mMuxer.start();
        }
    }

    /**
     * Stop and release encoder
     */
    public void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    /**
     * Extract all buffered data in encoder to file
     *
     * @param isEOS notify end of stream
     */
    public void drainEncoder(boolean isEOS) {
        final int TIMEOUT_USEC = 10000;

        if (isEOS) {
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (mMuxerStarted) {
                    throw new RuntimeException("Format changed twice");
                }

                MediaFormat newFormat = mEncoder.getOutputFormat();

                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                // Ignore
            } else {
                // Process output data
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("Muxer hasn't started");
                    }

                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                }

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }

    }
}
