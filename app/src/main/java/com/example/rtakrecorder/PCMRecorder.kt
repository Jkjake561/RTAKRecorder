package com.example.rtakrecorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.Context

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    private var bufferSize = AudioRecord.getMinBufferSize(
        8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
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
            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, 8000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )
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

