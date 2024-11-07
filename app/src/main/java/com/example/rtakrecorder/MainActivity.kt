package com.example.rtakrecorder

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.rtakrecorder.ui.theme.RTAKrecorderTheme
import android.media.AudioFormat
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import java.io.FileInputStream
import android.media.AudioTrack
import android.media.AudioAttributes
import android.content.Context

class MainActivity : ComponentActivity() {

    // Use the modern Activity Result API for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, continue with the app
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show() // Use 'this' as context here
        } else {
            // Permission denied
            Toast.makeText(this, "Permission Denied. Please allow microphone access.", Toast.LENGTH_LONG).show() // Use 'this' as context here
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set the content before handling permissions
        setContent {
            val context = this@MainActivity // or use LocalContext.current in Composable scope
            RTAKrecorderTheme {
                RecordingButtons(PCMRecorder(context))
            }
        }

        // Check if the permission is already granted
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, continue with the app
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show() // Use 'this' as context here
        } else {
            // Request the permission
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun RecordingButtons(recorder: PCMRecorder) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("RecordingButtons", "Start Recording button pressed")
                    recorder.startRecording()
                } else {
                    Toast.makeText(context, "Microphone permission is required to record", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Start Recording")
        }
        Button(
            onClick = {
                Log.d("RecordingButtons", "Stop Recording button pressed")
                recorder.stopRecording()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Stop Recording")
        }
        Button(
            onClick = {
                recorder.getOutputFilePath()?.let { filePath ->
                    Log.d("RecordingButtons", "Play Recording button pressed")
                    playPCMFile(filePath)
                } ?: run {
                    Toast.makeText(context, "No file to play", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Play Recording")
        }
        // New button to play the Codec2 file
        Button(
            onClick = {
                recorder.getOutputFilePath()?.let { filePath ->
                    val codec2FilePath = filePath.replace(".pcm", ".c2")
                    recorder.playCodec2File(codec2FilePath)
                } ?: run {
                    Toast.makeText(context, "No Codec2 file to play", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Play Codec2 File")
        }
    }
}

// Play PCM file using AudioTrack
fun playPCMFile(filePath: String) {
    Thread {
        try {
            val fileInputStream = FileInputStream(filePath)
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

@Preview(showBackground = true)
@Composable
fun RecordingButtonsPreview() {
    val context = LocalContext.current // Use a default or mock context
    RTAKrecorderTheme {
        RecordingButtons(PCMRecorder(context))  // Pass the context here
    }
}