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
import android.media.AudioManager
import android.media.AudioTrack
import java.io.FileNotFoundException
import java.nio.ShortBuffer

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    private var isRecording = false
    private var pcmFile: File? = null
    private var codec2File: File? = null
    private val staticBufferSize = 4096

    // Start recording and save PCM file
    fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("PCMRecorder", "Permission not granted")
            return
        }

        Log.d("PCMRecorder", "Starting PCM recording")

        val outputDir = File(context.getExternalFilesDir(null), "Recordings")
        if (!outputDir.exists()) outputDir.mkdirs()

        pcmFile = File(outputDir, "recording_${System.currentTimeMillis()}.pcm")
        Log.d("PCMRecorder", "PCM file path: ${pcmFile!!.absolutePath}")

        try {
            val bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(8000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecorder?.startRecording()
            isRecording = true

            val buffer = ShortArray(bufferSize / 2)
            val outputStream = pcmFile!!.outputStream()

            Thread {
                try {
                    while (isRecording) {
                        val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            // Convert ShortArray to ByteArray
                            val byteArray = ByteArray(read * 2)
                            for (i in 0 until read) {
                                val shortValue = buffer[i].toInt()
                                byteArray[i * 2] = (shortValue and 0xFF).toByte()
                                byteArray[i * 2 + 1] = ((shortValue shr 8) and 0xFF).toByte()
                            }

                            // Write ByteArray to file
                            synchronized(outputStream) {
                                outputStream.write(byteArray)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e("PCMRecorder", "Error writing to PCM file: ${e.message}")
                } finally {
                    synchronized(outputStream) {
                        outputStream.close()
                    }
                    Log.d("PCMRecorder", "PCM file closed successfully")
                }
            }.start()
        } catch (e: IOException) {
            Log.e("PCMRecorder", "Error during PCM recording: ${e.message}")
        }
    }


    fun stopRecording() {
        if (!isRecording) return

        Log.d("PCMRecorder", "Stopping recording")
        isRecording = false

        audioRecorder?.apply {
            stop()
            release()
        }
        audioRecorder = null
        Log.d("PCMRecorder", "Recording stopped")
    }

    // Encode PCM to Codec2
    fun encodePCMToCodec2() {
        if (pcmFile == null || !pcmFile!!.exists()) {
            Log.e("PCMRecorder", "No PCM file available for encoding")
            return
        }

        codec2File = File(pcmFile!!.parent, pcmFile!!.name.replace(".pcm", ".c2"))
        Log.d("PCMRecorder", "Codec2 file path: ${codec2File!!.absolutePath}")

        try {
            val codec2 = Codec2.createInstance(Codec2.Mode._2400)
            val pcmStream = pcmFile!!.inputStream().buffered()
            codec2File!!.outputStream().use { output ->
                // Write Codec2 header
                val header = Codec2.makeHeader(Codec2.Mode._2400.ordinal, false)
                output.write(header)

                val pcmFrameSize = codec2.pcmFrameSize
                val bytesPerSample = 2 // 16-bit PCM (2 bytes per sample)
                val buffer = ByteArray(pcmFrameSize * bytesPerSample)

                while (true) {
                    val bytesRead = pcmStream.read(buffer)
                    if (bytesRead <= 0) break // End of file

                    // Handle incomplete PCM frames
                    val paddedBuffer = if (bytesRead < buffer.size) {
                        // Create a padded buffer with zeroes
                        buffer.copyOf().also { padding ->
                            for (i in bytesRead until buffer.size) {
                                padding[i] = 0
                            }
                        }
                    } else {
                        buffer
                    }

                    // Create a Direct ByteBuffer for the native method
                    val directBuffer = ByteBuffer.allocateDirect(paddedBuffer.size).order(ByteOrder.LITTLE_ENDIAN)
                    directBuffer.put(paddedBuffer)
                    directBuffer.flip() // Prepare for reading

                    // Pass the DirectByteBuffer to the Codec2 encode method
                    val encodedBuffer = codec2.encode(directBuffer)
                    val encodedData = ByteArray(encodedBuffer.remaining())
                    encodedBuffer.get(encodedData) // Extract data from ByteBuffer
                    output.write(encodedData)
                }
            }
            codec2.close()
            Log.d("PCMRecorder", "PCM successfully encoded to Codec2")
            Log.d("Codec2Encoding", "Final encoded file size: ${codec2File!!.length()}")
        } catch (e: Exception) {
            Log.e("PCMRecorder", "Error during Codec2 encoding: ${e.message}")
            e.printStackTrace()
        }
    }




    // Play PCM file
    fun playPCMFile() {
        if (pcmFile == null || !pcmFile!!.exists()) {
            Log.e("PCMRecorder", "PCM file not found")
            return
        }

        Thread {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    8000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(8000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                audioTrack.play()
                pcmFile!!.inputStream().use { input ->
                    val buffer = ByteArray(bufferSize)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        audioTrack.write(buffer, 0, read)
                    }
                }
                audioTrack.stop()
                audioTrack.release()
                Log.d("PCMRecorder", "PCM playback completed")
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Error during PCM playback: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
    // Play Codec2 file
    // Play Codec2 file with pre-decoding
    fun playCodec2File() {
        if (codec2File == null || !codec2File!!.exists()) {
            Log.e("Codec2Playback", "Codec2 file not found")
            return
        }

        Thread {
            try {
                // Create a Codec2 instance
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)

                // Read encoded data from the file
                val encodedBytes = codec2File!!.readBytes()

                // Validate the header (assuming 7 bytes for Codec2 header)
                val headerSize = 7
                if (encodedBytes.size <= headerSize) {
                    Log.e("Codec2Playback", "Encoded file too small or missing header")
                    return@Thread
                }

                // Log header details
                Log.d("Codec2Playback", "File size: ${encodedBytes.size}")
                Log.d("Codec2Playback", "Header size: $headerSize")
                Log.d("Codec2Playback", "Payload size: ${encodedBytes.size - headerSize}")
                Log.d("Codec2Playback", "Expected frame size: ${codec2.encodedFrameSize}")

                // Prepare a buffer for the decoded PCM data
                val inputBufferSize = encodedBytes.size - headerSize

                // Decode each frame
                val frameBuffer = ByteBuffer.allocateDirect(inputBufferSize).order(ByteOrder.LITTLE_ENDIAN)

                // Copy the frame data into the frameBuffer
                frameBuffer.put(encodedBytes, headerSize, inputBufferSize)

                // Decode the frame into PCM samples
                val temp = codec2.decode(frameBuffer);

                // Configure AudioTrack for playback
                val bufferSize = AudioTrack.getMinBufferSize(
                    8000, // Matches Codec2 sample rate
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2 // Double the minimum buffer size for smooth playback

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
                            .setSampleRate(8000) // Ensure this matches Codec2 config
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                // Start playback
                audioTrack.play()
                audioTrack.write(temp, temp.capacity(), AudioTrack.WRITE_BLOCKING);

                // Stop and release resources
                audioTrack.stop()
                audioTrack.release()
                codec2.close()

                Log.d("Codec2Playback", "Codec2 playback completed successfully")

            } catch (e: Exception) {
                Log.e("Codec2Playback", "Error during Codec2 playback: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    fun getPCMFilePath(): String? = pcmFile?.absolutePath
    fun getCodec2FilePath(): String? = codec2File?.absolutePath
}
