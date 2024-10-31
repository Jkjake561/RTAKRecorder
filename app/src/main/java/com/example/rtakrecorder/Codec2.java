package com.example.rtakrecorder;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public class Codec2 implements AutoCloseable {
    static {
        System.loadLibrary("Codec2Binding");
    }

    public static final int REQUIRED_SAMPLE_RATE = 8000;

    public static enum Mode {
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

    private static native long nativeCreateCodec2State(int mode, Class<RuntimeException> runtimeExceptionClass) throws RuntimeException;

    @NonNull
    public static Codec2 createInstance(@NotNull Mode mode) throws RuntimeException {
        long codec2StatePtr = nativeCreateCodec2State(mode.ordinal(), RuntimeException.class);
        return new Codec2(codec2StatePtr, mode);
    }

    // Both "directByteBuffer" and "byteArray" cannot be non-null, only one or the other can be passed.
    private static native ByteBuffer nativeEncodeCodec2(long codec2StatePtr, ByteBuffer directByteBuffer, byte[] byteArray, Class<RuntimeException> runtimeExceptionClass) throws RuntimeException;

    public ByteBuffer encode(@NotNull ByteBuffer pcmBuffer) throws RuntimeException {
        if (pcmBuffer.isDirect())
            return nativeEncodeCodec2(codec2StatePtr, pcmBuffer, null, RuntimeException.class);
        else {
            if (pcmBuffer.hasArray())
                return nativeEncodeCodec2(codec2StatePtr, null, pcmBuffer.array(), RuntimeException.class);
            else
                throw new RuntimeException("Unable to retrieve backing array of non-direct PCM buffer");
        }
    }

    // Both "directByteBuffer" and "byteArray" cannot be non-null, only one or the other can be passed.
    private static native ByteBuffer nativeDecodeCodec2(long codec2StatePtr, ByteBuffer directByteBuffer, byte[] byteArray, Class<RuntimeException> runtimeExceptionClass) throws  RuntimeException;

    public ByteBuffer encode(@NotNull byte[] pcmByteArray) throws RuntimeException {
        return nativeEncodeCodec2(codec2StatePtr, null, pcmByteArray, RuntimeException.class);
    }

    private static native void nativeDestroyCodec2State(long codec2StatePtr);

    @Override
    public void close() throws Exception {
        if (!closed) {
            nativeDestroyCodec2State(codec2StatePtr);
            closed = true;
        }
        else
            throw new Exception("Codec2 instance has already been closed");
    }

    @Override
    protected void finalize() {
        if (!closed)
            nativeDestroyCodec2State(codec2StatePtr);
    }
}
