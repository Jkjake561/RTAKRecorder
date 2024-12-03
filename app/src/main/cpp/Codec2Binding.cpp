#include <jni.h>
#include <codec2.h>
#include <cstdlib>
#include <cstddef>
#include <string>
#include <cmath>
#include <android/log.h>

//
// Created by gabeg on 10/29/2024.
//


extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_rtakrecorder_Codec2_nativeCreateCodec2State(JNIEnv *env, jclass clazz, jint mode, jclass runtime_exception_class) {
    int codec2_mode = -1;
    switch (mode) {
        case 0:
            codec2_mode = CODEC2_MODE_3200;
            break;
        case 1:
            codec2_mode = CODEC2_MODE_2400;
            break;
        case 2:
            codec2_mode = CODEC2_MODE_1600;
            break;
        case 3:
            codec2_mode = CODEC2_MODE_1400;
            break;
        case 4:
            codec2_mode = CODEC2_MODE_1300;
            break;
        case 5:
            codec2_mode = CODEC2_MODE_1200;
            break;
        case 6:
            codec2_mode = CODEC2_MODE_700C;
            break;
        default:
            std::string exception_str = "Unknown Codec2 mode ordinal: " + std::to_string(mode);
            env->ThrowNew(runtime_exception_class, exception_str.c_str());
            return 0;
    }

    struct CODEC2* codec2_state = codec2_create(codec2_mode);
    if (codec2_state == nullptr) {
        env->ThrowNew(runtime_exception_class, "Error occurred while creating Codec2 state");
        return 0;
    }

    return reinterpret_cast<jlong>(codec2_state);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_rtakrecorder_Codec2_nativeDestroyCodec2State(JNIEnv *env, jclass clazz, jlong codec2_state_ptr) {
    if (codec2_state_ptr != 0)
        codec2_destroy(reinterpret_cast<CODEC2*>(codec2_state_ptr));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_rtakrecorder_Codec2_nativeEncodeCodec2(JNIEnv *env, jclass clazz, jlong codec2_state_ptr, jobject direct_byte_buffer, jint sample_count, jclass runtime_exception_class) {
    // Retrieve PCM data from DirectByteBuffer
    short* pcm_data = reinterpret_cast<short*>(env->GetDirectBufferAddress(direct_byte_buffer));
    if (pcm_data == nullptr) {
        env->ThrowNew(runtime_exception_class, "Error occurred while retrieving data pointer from DirectByteBuffer");
        return nullptr;
    }

    // Retrieve the codec2 state
    CODEC2* codec2_state = reinterpret_cast<CODEC2*>(codec2_state_ptr);
    if (codec2_state == nullptr) {
        env->ThrowNew(runtime_exception_class, "Codec2 state pointer is null");
        return nullptr;
    }

    // Calculate frame and buffer sizes
    jint bytes_per_frame = codec2_bytes_per_frame(codec2_state);
    jint samples_per_frame = codec2_samples_per_frame(codec2_state);
    jlong output_buf_size = static_cast<jlong>(ceil(static_cast<double>(sample_count) / samples_per_frame)) * bytes_per_frame;

    // Allocate the output buffer in native memory
    unsigned char* output_buffer = reinterpret_cast<unsigned char*>(malloc(output_buf_size));
    if (output_buffer == nullptr) {
        env->ThrowNew(runtime_exception_class, "Failed to allocate memory for output buffer");
        return nullptr;
    }

    // Encode PCM data into Codec2 format
    jlong output_buf_offset = 0;
    for (jlong i = 0; i < sample_count; i += samples_per_frame) {
        if ((sample_count - i) >= samples_per_frame) {
            codec2_encode(codec2_state, &output_buffer[output_buf_offset], &pcm_data[i]);
            output_buf_offset += bytes_per_frame;
        }
        else {
            // Handle incomplete frame by padding with zeros
            std::vector<short> padded_input(samples_per_frame, 0);
            memcpy(padded_input.data(), &pcm_data[i], (sample_count - i) * sizeof(short));
            codec2_encode(codec2_state, &output_buffer[output_buf_offset], padded_input.data());
            output_buf_offset += bytes_per_frame;
        }
    }

    // Return the output buffer as a DirectByteBuffer
    jobject direct_output_buffer = env->NewDirectByteBuffer(output_buffer, output_buf_size);
    if (direct_output_buffer == nullptr) {
        free(output_buffer); // Free memory if DirectByteBuffer creation fails
        env->ThrowNew(runtime_exception_class, "Failed to create DirectByteBuffer for encoded data");
        return nullptr;
    }

    return direct_output_buffer;
}


extern "C" {
// Declare the logToJava function before using it
void logToJava(JNIEnv *env, const char *tag, const char *message);

JNIEXPORT jobject JNICALL
Java_com_example_rtakrecorder_Codec2_nativeDecodeCodec2(JNIEnv *env, jclass clazz, jlong codec2_state_ptr, jobject encodedDataBuffer, jclass runtime_exception_class) {
    if (!codec2_state_ptr) {
        env->ThrowNew(runtime_exception_class, "Invalid codec2_state_ptr");
        return nullptr;
    }

    unsigned char* encodedBytes = reinterpret_cast<unsigned char *>(env->GetDirectBufferAddress(encodedDataBuffer));

    if (!encodedBytes) {
        env->ThrowNew(runtime_exception_class, "Direct buffer access failed");
        return nullptr;
    }

    CODEC2* codec2_state = reinterpret_cast<CODEC2*>(codec2_state_ptr);

    jint bytesPerFrame = codec2_bytes_per_frame(codec2_state);
    jint samplesPerFrame = codec2_samples_per_frame(codec2_state);
    jlong encodedLen = env->GetDirectBufferCapacity(encodedDataBuffer);

    if (encodedLen % bytesPerFrame != 0) {
        env->ThrowNew(runtime_exception_class, "Encoded data is not aligned with frame size");
        return nullptr;
    }

    jlong frameCount = encodedLen / bytesPerFrame;
    jlong outputLen = frameCount * samplesPerFrame * static_cast<jlong>(sizeof(short));
    if (outputLen > INT32_MAX) {
        env->ThrowNew(runtime_exception_class, "Output buffer size is too large for ByteBuffer");
        return nullptr;
    }

    short* outputSamples = reinterpret_cast<short*>(malloc(outputLen));
    if (!outputSamples) {
        env->ThrowNew(runtime_exception_class, "Error occurred while allocating output buffer");
        return nullptr;
    }

    for (int i = 0; i < frameCount; ++i)
        codec2_decode(codec2_state, &outputSamples[i * samplesPerFrame],&encodedBytes[i * bytesPerFrame]);

    jobject outputBuffer = env->NewDirectByteBuffer(outputSamples, outputLen);
    if (!outputBuffer) {
        env->ThrowNew(runtime_exception_class, "Error occurred while creating output ByteBuffer");
        return nullptr;
    }

    return outputBuffer;
}

// Define logToJava after its declaration
void logToJava(JNIEnv *env, const char *tag, const char *message) {
    jclass logClass = env->FindClass("android/util/Log");
    jmethodID logMethod = env->GetStaticMethodID(logClass, "d",
                                                 "(Ljava/lang/String;Ljava/lang/String;)I");
    jstring tagStr = env->NewStringUTF(tag);
    jstring msgStr = env->NewStringUTF(message);
    env->CallStaticIntMethod(logClass, logMethod, tagStr, msgStr);
    env->DeleteLocalRef(tagStr);
    env->DeleteLocalRef(msgStr);
}
}