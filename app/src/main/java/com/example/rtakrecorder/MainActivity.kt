package com.example.rtakrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rtakrecorder.ui.theme.RTAKrecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Use your Composable function directly in setContent
        setContent {
            RTAKrecorderTheme {
                RecordingButtons() // Calls the UI with start/stop buttons
            }
        }
    }
}

@Composable
fun RecordingButtons(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { /* Handle start recording here */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Start Recording")
        }
        Button(
            onClick = { /* Handle stop recording here */ },
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
        RecordingButtons()
    }
}
