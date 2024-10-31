#include <jni.h>
#include <codec2.h>
#include <cstdlib>
#include <cstddef>
#include <string>
#include <cmath>

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
Java_com_example_rtakrecorder_Codec2_nativeEncodeCodec2(JNIEnv *env, jclass clazz, jlong codec2_state_ptr, jobject direct_byte_buffer, jbyteArray byte_array, jclass runtime_exception_class) {
    int pcm_data_len = 0;
    short* pcm_data = nullptr;
    if (direct_byte_buffer != nullptr) {
        // Direct byte buffers cannot be larger than INT32_MAX, this is a safe cast.
        pcm_data_len = static_cast<int>(env->GetDirectBufferCapacity(direct_byte_buffer));
        if (pcm_data_len <= -1) {
            env->ThrowNew(runtime_exception_class, R"(Error occurred while retrieving capacity from "directByteBuffer")");
            return nullptr;
        }

        pcm_data = reinterpret_cast<short*>(env->GetDirectBufferAddress(direct_byte_buffer));
        if (pcm_data == nullptr) {
            env->ThrowNew(runtime_exception_class,R"(Error occurred while retrieving data pointer from "directByteBuffer")");
            return nullptr;
        }
    }
    else {
        pcm_data_len = static_cast<int>(env->GetArrayLength(byte_array));
        pcm_data = reinterpret_cast<short*>(env->GetPrimitiveArrayCritical(byte_array,nullptr));
        if (pcm_data == nullptr) {
            env->ThrowNew(runtime_exception_class,R"(Error occurred while retrieving data pointer from "byteArray")");
            return nullptr;
        }
    }

    pcm_data_len /= sizeof(short);

    CODEC2* codec2_state = reinterpret_cast<CODEC2*>(codec2_state_ptr);
    int bytes_per_frame = codec2_bytes_per_frame(codec2_state);
    int samples_per_frame = codec2_samples_per_frame(codec2_state);
    int output_buf_len = static_cast<int>(ceil(static_cast<double>(pcm_data_len) / samples_per_frame)) * samples_per_frame;

    unsigned char* output_buffer = reinterpret_cast<unsigned char*>(malloc(output_buf_len));
    if (output_buffer == nullptr) {
        if (byte_array != nullptr)
            env->ReleasePrimitiveArrayCritical(byte_array, pcm_data, JNI_ABORT);
        env->ThrowNew(runtime_exception_class, "Error occurred while allocating memory for output buffer");
        return nullptr;
    }

    int output_buf_offset = 0;
    for (int64_t i = 0; i < pcm_data_len; i += samples_per_frame) {
        if ((pcm_data_len - i) >= samples_per_frame) {
            codec2_encode(codec2_state, &output_buffer[output_buf_offset], &pcm_data[i]);
            output_buf_offset += bytes_per_frame;
        }
        else {
            short padded_input[samples_per_frame];
            memset(padded_input, 0, samples_per_frame * sizeof(short)); // Zero out the padding array.
            memcpy(padded_input, &pcm_data[i], (pcm_data_len - i) * sizeof(short)); // Copy over the remaining PCM data.
            codec2_encode(codec2_state, &output_buffer[output_buf_offset], padded_input);
        }
    }

    if (byte_array != nullptr)
        env->ReleasePrimitiveArrayCritical(byte_array, pcm_data, JNI_ABORT);

    return env->NewDirectByteBuffer(output_buffer, static_cast<jlong>(output_buf_len));
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_example_rtakrecorder_Codec2_nativeDecodeCodec2(JNIEnv *env, jclass clazz, jlong codec2_state_ptr, jobject direct_byte_buffer, jbyteArray byte_array, jclass runtime_exception_class) {
    int codec2_data_len = 0;
    unsigned char* codec2_data = nullptr;
    if (direct_byte_buffer != nullptr) {
        // Direct byte buffers cannot be larger than INT32_MAX, this is a safe cast.
        codec2_data_len = static_cast<int>(env->GetDirectBufferCapacity(direct_byte_buffer));
        if (codec2_data_len <= -1) {
            env->ThrowNew(runtime_exception_class, R"(Error occurred while retrieving capacity from "directByteBuffer")");
            return nullptr;
        }

        codec2_data = reinterpret_cast<unsigned char*>(env->GetDirectBufferAddress(direct_byte_buffer));
        if (codec2_data == nullptr) {
            env->ThrowNew(runtime_exception_class,R"(Error occurred while retrieving data pointer from "directByteBuffer")");
            return nullptr;
        }
    }
    else {
        codec2_data_len = static_cast<int>(env->GetArrayLength(byte_array));
        codec2_data = reinterpret_cast<unsigned char*>(env->GetPrimitiveArrayCritical(byte_array, nullptr));
        if (codec2_data == nullptr) {
            env->ThrowNew(runtime_exception_class,R"(Error occurred while retrieving data pointer from "byteArray")");
            return nullptr;
        }
    }

    CODEC2* codec2_state = reinterpret_cast<CODEC2*>(codec2_state_ptr);
    int bytes_per_frame = codec2_bytes_per_frame(codec2_state);
    int samples_per_frame = codec2_samples_per_frame(codec2_state);
    int output_buf_len = (codec2_data_len / bytes_per_frame) * samples_per_frame;

    short* output_buffer = reinterpret_cast<short*>(malloc(output_buf_len * sizeof(short)));
    if (output_buffer == nullptr) {
        if (byte_array != nullptr)
            env->ReleasePrimitiveArrayCritical(byte_array, codec2_data, JNI_ABORT);
        env->ThrowNew(runtime_exception_class, "Error occurred while allocating memory for output buffer");
        return nullptr;
    }

    int output_buf_offset = 0;
    for (int i = 0; i < codec2_data_len; i += bytes_per_frame) {
        codec2_decode(codec2_state, &output_buffer[output_buf_offset], &codec2_data[i]);
        output_buf_offset += samples_per_frame;
    }

    if (byte_array != nullptr)
        env->ReleasePrimitiveArrayCritical(byte_array, codec2_data, JNI_ABORT);

    return env->NewDirectByteBuffer(output_buffer, static_cast<jlong>(output_buf_len * sizeof(short)));
}