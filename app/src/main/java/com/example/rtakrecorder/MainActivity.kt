package com.example.rtakrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
//import androidx.media3.common.util.UnstableApi
import com.example.rtakrecorder.ui.theme.RTAKrecorderTheme
import android.media.AudioTrack
import android.media.AudioAttributes


import androidx.activity.enableEdgeToEdge

import android.media.AudioFormat

import java.io.FileInputStream


class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission Denied. Please allow microphone access.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RTAKrecorderTheme {
                RecordingButtons(recorder = PCMRecorder(this))
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    recorder.startRecording()
                } else {
                    Toast.makeText(context, "Microphone permission is required to record", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Start Recording")
        }
        Button(
            onClick = { recorder.stopRecording() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Stop Recording")
        }
        Button(
            onClick = {
                val filePath = recorder.getPCMFilePath()
                if (filePath != null) {
                    recorder.playPCMFile()
                } else {
                    Toast.makeText(context, "No PCM file to play", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Play PCM File")
        }
        Button(
            onClick = {
                recorder.encodePCMToCodec2()
                val codec2Path = recorder.getCodec2FilePath()
                if (codec2Path != null) {
                    Toast.makeText(context, "Codec2 file encoded at: $codec2Path", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to encode Codec2 file", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Encode PCM to Codec2")
        }
        Button(
            onClick = {
                val filePath = recorder.getCodec2FilePath()
                if (filePath != null) {
                    recorder.playCodec2File()
                } else {
                    Toast.makeText(context, "No Codec2 file to play", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Play Codec2 File")
        }
    }
}



@Preview(showBackground = true)
@Composable
fun RecordingButtonsPreview() {
    RTAKrecorderTheme {
        RecordingButtons(PCMRecorder(LocalContext.current))
    }
}
