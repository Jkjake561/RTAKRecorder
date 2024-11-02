package com.example.rtakrecorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    private var bufferSize = AudioRecord.getMinBufferSize(
        Codec2.REQUIRED_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private var isRecording = false
    private var outputFile: File? = null

    fun startRecording() {
        Log.d("PCMRecorder", "Starting recording")

        // Create the output directory
        val outputDir = File(context.getExternalFilesDir(null), "Recordings")
        if (!outputDir.exists()) outputDir.mkdirs()

        // Set the output PCM file
        outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.pcm")

        try {
            val fileOutputStream = FileOutputStream(outputFile)

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            audioRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Codec2.REQUIRED_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecorder?.startRecording()
            isRecording = true

            // Start recording in a background thread
            Thread {
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
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

        // Encode the PCM file to Codec2 format
        encodePCMToCodec2()
    }

    private fun encodePCMToCodec2() {
        outputFile?.let { pcmFile ->
            val codec2OutputFile = File(
                pcmFile.parent,
                pcmFile.name.replace(".pcm", ".c2")  // Change the extension for Codec2 file
            )
            try {
                // Initialize Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)
                val pcmInputStream = FileInputStream(pcmFile)
                val codec2OutputStream = FileOutputStream(codec2OutputFile)

                val buffer = ByteArray(bufferSize)
                var bytesRead: Int

                while (pcmInputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        // Use only the portion of the buffer that has been read
                        val pcmData = buffer.copyOf(bytesRead)
                        val codec2Data = codec2.encode(pcmData)

                        if (codec2Data != null) {
                            // Create a byte array from the direct ByteBuffer
                            val encodedBytes = ByteArray(codec2Data.remaining())
                            codec2Data.get(encodedBytes)

                            // Write the encoded data to the output file
                            codec2OutputStream.write(encodedBytes)
                        } else {
                            Log.e("PCMRecorder", "Codec2 encoding returned null data.")
                        }
                    }
                }

                pcmInputStream.close()
                codec2OutputStream.close()

                Log.d("PCMRecorder", "Encoded file saved at: ${codec2OutputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Encoding to Codec2 failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun getOutputFilePath(): String? {
        return outputFile?.absolutePath
    }
}


