package com.example.rtakrecorder;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class Codec2 implements AutoCloseable {
    static {
        System.loadLibrary("Codec2Binding");
    }

    public static final int REQUIRED_SAMPLE_RATE = 8000;

    public enum Mode {
        _3200,
        _2400,
        _1600,
        _1400,
        _1300,
        _1200,
        _700C
    }

    private boolean closed = false;
    private final long codec2StatePtr;
    public final Mode mode;

    private Codec2(long codec2StatePtr, Mode mode) throws RuntimeException {
        this.codec2StatePtr = codec2StatePtr;
        this.mode = mode;
    }

    public static byte[] makeHeader(int mode, boolean includeFlags) {
        byte[] header = new byte[7];

        header[0] = (byte) 0xc0; // Codec2 magic number
        header[1] = (byte) 0xde; // Codec2 magic number
        header[2] = (byte) 0xc2; // Codec2 identifier
        header[3] = 1; // version_major
        header[4] = 0; // version_minor
        header[5] = (byte) mode; // codec mode
        header[6] = (byte) (includeFlags ? 1 : 0); // flags

        return header;
    }

    public int getEncodedFrameSize() {
        switch (mode) {
            case _3200:
            case _2400:
            case _1600:
                return 8;
            case _1400:
            case _1300:
            case _1200:
            case _700C:
                return 6;
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }
    }

    public int getPCMFrameSize() {
        return 160; // All modes use 160 PCM samples per frame at 8000 Hz sample rate
    }

    private static native long nativeCreateCodec2State(int mode, Class<RuntimeException> runtimeExceptionClass) throws RuntimeException;

    @NonNull
    public static Codec2 createInstance(@NotNull Mode mode) throws RuntimeException {
        long codec2StatePtr = nativeCreateCodec2State(mode.ordinal(), RuntimeException.class);
        return new Codec2(codec2StatePtr, mode);
    }

    private static native ByteBuffer nativeEncodeCodec2(
            long codec2StatePtr,
            ByteBuffer pcmBuffer,
            int sampleCount,
            Class<RuntimeException> runtimeExceptionClass
    ) throws RuntimeException;

    public ByteBuffer encode(ByteBuffer pcmBuffer) throws RuntimeException {
        if (!pcmBuffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer must be a direct buffer.");
        }
        return nativeEncodeCodec2(codec2StatePtr, pcmBuffer, pcmBuffer.remaining() / 2, RuntimeException.class);
    }

    private static native ByteBuffer nativeDecodeCodec2(long codec2StatePtr, ByteBuffer encodedData, Class<RuntimeException> runtimeExceptionClass) throws RuntimeException;

    public ByteBuffer decode(ByteBuffer encodedData) throws RuntimeException {
        if (!encodedData.isDirect())
            throw new IllegalArgumentException("Buffer must be direct");
        return nativeDecodeCodec2(codec2StatePtr, encodedData, RuntimeException.class).order(ByteOrder.nativeOrder());
    }

    private static native void nativeDestroyCodec2State(long codec2StatePtr);

    @Override
    public void close() throws Exception {
        if (!closed) {
            nativeDestroyCodec2State(codec2StatePtr);
            closed = true;
        } else {
            throw new Exception("Codec2 instance has already been closed");
        }
    }

    @Override
    protected void finalize() {
        if (!closed) {
            nativeDestroyCodec2State(codec2StatePtr);
        }
    }
}
