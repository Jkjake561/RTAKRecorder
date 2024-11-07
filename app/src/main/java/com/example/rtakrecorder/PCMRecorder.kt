package com.example.rtakrecorder
import com.example.rtakrecorder.Codec2
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class PCMRecorder(private val context: Context) {
    private var audioRecorder: AudioRecord? = null
    val bufferSize = AudioRecord.getMinBufferSize(
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
                val buffer = ShortArray(bufferSize / 2) // Using a smaller buffer to better align with AudioTrack playback
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
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)
                val codec2OutputStream = FileOutputStream(codec2OutputFile)

                val pcmInputStream = FileInputStream(pcmFile)
                val pcmBuffer = ByteArray(320) // 160 samples * 2 bytes per sample

                var bytesRead: Int
                while (pcmInputStream.read(pcmBuffer).also { bytesRead = it } == pcmBuffer.size) {
                    val encodedData = codec2.encode(pcmBuffer)
                    codec2OutputStream.write(encodedData.array())
                }

                // Handle any remaining samples (padding may be necessary)
                if (bytesRead > 0 && bytesRead < pcmBuffer.size) {
                    // Pad the remaining buffer with zeros
                    pcmBuffer.fill(0, bytesRead, pcmBuffer.size)
                    val encodedData = codec2.encode(pcmBuffer)
                    codec2OutputStream.write(encodedData.array())
                }

                pcmInputStream.close()
                codec2OutputStream.close()
                codec2.close()

                Log.d("PCMRecorder", "Encoded file saved at: ${codec2OutputFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("PCMRecorder", "Encoding to Codec2 failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun playPCMFile(pcmFilePath: String) {
        Thread {
            try {
                val fileInputStream = FileInputStream(pcmFilePath)
                val bufferSize = AudioTrack.getMinBufferSize(
                    Codec2.REQUIRED_SAMPLE_RATE,
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
                            .setSampleRate(Codec2.REQUIRED_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                val buffer = ByteArray(bufferSize)
                audioTrack.play()

                var read: Int
                while (fileInputStream.read(buffer).also { read = it } != -1) {
                    audioTrack.write(buffer, 0, read)
                }

                audioTrack.stop()
                audioTrack.release()
                fileInputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun playCodec2File(codec2FilePath: String) {
        Thread {
            try {
                val codec2 = Codec2.createInstance(Codec2.Mode._2400)
                val codec2InputStream = FileInputStream(codec2FilePath)
                val encodedFrameSize = codec2.getEncodedFrameSize() // Should match the Codec2 frame size for the mode
                val codec2Buffer = ByteArray(encodedFrameSize)

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
                    .setBufferSizeInBytes(Codec2.REQUIRED_SAMPLE_RATE * 2) // Adjust buffer size as needed
                    .build()

                audioTrack.play()

                var bytesRead: Int
                while (codec2InputStream.read(codec2Buffer).also { bytesRead = it } == codec2Buffer.size) {
                    val decodedData = codec2.decode(codec2Buffer)
                    val pcmBytes = ByteArray(decodedData.remaining())
                    decodedData.get(pcmBytes)
                    audioTrack.write(pcmBytes, 0, pcmBytes.size)
                }

                audioTrack.stop()
                audioTrack.release()
                codec2InputStream.close()
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
