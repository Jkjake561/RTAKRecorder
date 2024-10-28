package com.example.rtakrecorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class PCMRecorder {
    private var audioRecorder: AudioRecord? = null
    private var bufferSize = AudioRecord.getMinBufferSize(
        8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    private var isRecording = false

    fun startRecording() {
        // Initialize AudioRecord object
        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            8000,  // Sampling rate 8kHz
            AudioFormat.CHANNEL_IN_MONO,  // Mono recording
            AudioFormat.ENCODING_PCM_16BIT,  // 16-bit PCM encoding
            bufferSize
        )

        // Start recording
        audioRecorder?.startRecording()
        isRecording = true

        // Start a new thread to capture audio data
        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                Log.d("PCMRecorder", "Read $read samples")
                // Here, we'll process the audio buffer and later encode it into Codec 2 format
            }
        }.start()
    }

    fun stopRecording() {
        isRecording = false
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        Log.d("PCMRecorder", "Recording stopped")
    }
}
