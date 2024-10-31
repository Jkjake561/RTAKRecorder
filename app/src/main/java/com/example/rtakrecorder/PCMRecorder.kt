package com.example.rtakrecorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    private var bufferSize = AudioRecord.getMinBufferSize(Codec2.REQUIRED_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var isRecording = false
    private var outputFile: File? = null

    fun startRecording() {
        Log.d("PCMRecorder", "Starting recording")

        // Updated to use getExternalFilesDir instead of deprecated Environment method
        val outputDir = File(context.getExternalFilesDir(null), "Recordings")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.pcm")

        try {
            val fileOutputStream = FileOutputStream(outputFile)

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }

            audioRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Codec2.REQUIRED_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build();

            audioRecorder?.startRecording()
            isRecording = true

            // Start the recording in a background thread
            Thread {
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                    Log.d("PCMRecorder", "Recording: Read $read samples")
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        byteBuffer[i * 2] = (buffer[i].toInt() and 0x00FF).toByte()
                        byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                    }
                    fileOutputStream.write(byteBuffer, 0, byteBuffer.size)
                }
                fileOutputStream.close()
            }.start()

        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("PCMRecorder", "Error writing file: ${e.message}")
        }
    }

    fun stopRecording() {
        Log.d("PCMRecorder", "Stopping recording")
        isRecording = false
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
    }

    fun getOutputFilePath(): String? {
        return outputFile?.absolutePath
    }
}

