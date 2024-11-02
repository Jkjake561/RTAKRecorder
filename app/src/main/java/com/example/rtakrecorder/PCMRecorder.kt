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


import android.media.AudioAttributes

import android.media.AudioTrack




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
                // Read PCM file into byte array
                val pcmInputStream = FileInputStream(pcmFile)
                val pcmBytes = pcmInputStream.readBytes()
                pcmInputStream.close()

                // Create Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)

                // Encode PCM bytes to Codec2 format
                val codec2ByteBuffer = codec2.encode(pcmBytes)

                // Write encoded data to .c2 file
                val codec2OutputStream = FileOutputStream(codec2OutputFile)
                val codec2Bytes = ByteArray(codec2ByteBuffer.remaining())
                codec2ByteBuffer.get(codec2Bytes)
                codec2OutputStream.write(codec2Bytes)
                codec2OutputStream.close()

                // Close the Codec2 instance
                codec2.close()

                Log.d("PCMRecorder", "Encoded file saved at: ${codec2OutputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Encoding to Codec2 failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    fun playCodec2File(codec2FilePath: String) {
        Thread {
            try {
                // Read Codec2 data from file
                val codec2InputStream = FileInputStream(codec2FilePath)
                val codec2Bytes = codec2InputStream.readBytes()
                codec2InputStream.close()

                // Create Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)

                // Decode Codec2 data to PCM
                val pcmByteBuffer = codec2.decode(codec2Bytes)

                // Prepare AudioTrack for playback
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Codec2.REQUIRED_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcmByteBuffer.remaining())
                    .build()

                // Play the PCM data
                audioTrack.play()
                val pcmBytes = ByteArray(pcmByteBuffer.remaining())
                pcmByteBuffer.get(pcmBytes)
                audioTrack.write(pcmBytes, 0, pcmBytes.size)
                audioTrack.stop()
                audioTrack.release()

                // Close the Codec2 instance
                codec2.close()
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Playback of Codec2 file failed: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
    fun getOutputFilePath(): String? {
        return outputFile?.absolutePath
    }
}


