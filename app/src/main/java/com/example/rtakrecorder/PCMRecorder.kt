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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import java.io.ByteArrayOutputStream

import android.media.AudioAttributes
import android.media.AudioTrack

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    private val staticBufferSize = 4096 // Using a fixed buffer size
    private var isRecording = false
    private var outputFile: File? = null

    fun startRecording() {
        Log.d("PCMRecorder", "Starting recording with static buffer size: $staticBufferSize bytes")

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
                .setBufferSizeInBytes(staticBufferSize) // Use static buffer size
                .build()

            audioRecorder?.startRecording()
            isRecording = true

            // Start recording in a background thread
            Thread {
                val shortBuffer = ShortArray(staticBufferSize / 2) // Buffer size in short units
                while (isRecording) {
                    val read = audioRecorder?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        Log.d("PCMRecorder", "PCM Frame - Samples Read: $read")
                        // Convert shortBuffer to byte array for writing to file
                        val byteBuffer = ByteBuffer.allocate(read * 2)
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        shortBuffer.take(read).forEach { sample ->
                            byteBuffer.putShort(sample)
                        }
                        fileOutputStream.write(byteBuffer.array())
                    }
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
                pcmFile.name.replace(".pcm", ".c2") // Change the extension for Codec2 file
            )
            try {
                val pcmBytes = pcmFile.readBytes()
                Log.d("PCMRecorder", "Original PCM Size: ${pcmBytes.size} bytes")

                // Create Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)
                val frameSize = codec2.getPCMFrameSize() // Typically 160 samples
                val totalFrames = pcmBytes.size / frameSize
                val leftoverBytes = pcmBytes.size % frameSize

                Log.d("PCMRecorder", "Frame Size: $frameSize, Total Frames: $totalFrames, Leftover Bytes: $leftoverBytes")

                // Align PCM data
                val alignedPCMBytes = pcmBytes.copyOf(totalFrames * frameSize)
                Log.d("PCMRecorder", "Aligned PCM Size: ${alignedPCMBytes.size} bytes")

                // Encode aligned PCM bytes to Codec2 format
                val codec2ByteBuffer = codec2.encode(ByteBuffer.wrap(alignedPCMBytes))
                Log.d("PCMRecorder", "PCM Bytes Passed to Encoder: ${alignedPCMBytes.size}")
                Log.d("PCMRecorder", "Encoded Buffer Size (before trimming): ${codec2ByteBuffer.remaining()} bytes")

                // Trim trailing zero-padding from the encoded buffer
                val trimmedEncodedBuffer = ByteArray(codec2ByteBuffer.remaining())
                codec2ByteBuffer.get(trimmedEncodedBuffer)

                var actualEncodedSize = trimmedEncodedBuffer.size
                while (actualEncodedSize > 0 && trimmedEncodedBuffer[actualEncodedSize - 1] == 0.toByte()) {
                    actualEncodedSize--
                }

                Log.d("PCMRecorder", "Encoded Buffer Size (after trimming): $actualEncodedSize bytes")

                // Prepare the Codec2 file header
                val header = byteArrayOf(
                    0xC0.toByte(), 0xDE.toByte(), 0xC2.toByte(), // Magic number "CODEC2"
                    0x01, 0x00,                                 // Version (major 1, minor 0)
                    Codec2.Mode._2400.ordinal.toByte(),         // Mode
                    0x00                                        // Flags
                )
                Log.d("PCMRecorder", "Header prepared for Codec2 file: ${header.joinToString()}")

                // Combine header and trimmed encoded data
                val finalData = ByteArray(header.size + actualEncodedSize)
                System.arraycopy(header, 0, finalData, 0, header.size)
                System.arraycopy(trimmedEncodedBuffer, 0, finalData, header.size, actualEncodedSize)

                // Write to file
                codec2OutputFile.writeBytes(finalData)
                Log.d("PCMRecorder", "Final Codec2 File Size: ${finalData.size} bytes")
                Log.d("PCMRecorder", "Codec2 file written successfully: ${codec2OutputFile.absolutePath}")

                codec2.close()
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Encoding to Codec2 failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }







    fun playCodec2File(codec2FilePath: String) {
        // Decoding and playback implementation with logging
        Thread {
            try {
                // Read Codec2 data from file
                val codec2InputStream = FileInputStream(codec2FilePath)
                val codec2Bytes = codec2InputStream.readBytes()
                codec2InputStream.close()
                Log.d("PCMRecorder", "Read Codec2 File Size: ${codec2Bytes.size} bytes")

                // Create Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)

                // Decode Codec2 data to PCM
                val pcmByteBuffer = codec2.decode(ByteBuffer.wrap(codec2Bytes))
                Log.d(
                    "PCMRecorder",
                    "Decoded PCM Size: ${pcmByteBuffer.remaining()} bytes"
                )

                val pcmBytes = ByteArray(pcmByteBuffer.remaining())
                pcmByteBuffer.get(pcmBytes)

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
                    .setBufferSizeInBytes(pcmBytes.size)
                    .build()

                audioTrack.play()
                audioTrack.write(pcmBytes, 0, pcmBytes.size)
                audioTrack.stop()
                audioTrack.release()

                codec2.close()

                Log.d("PCMRecorder", "Playback Completed Successfully")
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
