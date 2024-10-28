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

class MainActivity : ComponentActivity() {

    // Use the modern Activity Result API for permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, continue with the app
            Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
            setContent {
                RTAKrecorderTheme {
                    RecordingButtons(PCMRecorder())
                }
            }
        } else {
            // Permission denied, provide feedback and let the user retry
            Toast.makeText(this, "Permission Denied. Please allow microphone access.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if the permission is already granted
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, continue with the app
            setContent {
                RTAKrecorderTheme {
                    RecordingButtons(PCMRecorder())
                }
            }
        } else {
            // Request the permission
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun RecordingButtons(recorder: PCMRecorder) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { recorder.startRecording() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Start Recording")
        }
        Button(
            onClick = { recorder.stopRecording() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Stop Recording")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingButtonsPreview() {
    RTAKrecorderTheme {
        RecordingButtons(PCMRecorder())
    }
}
