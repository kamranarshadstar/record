package com.example.record.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.record.R
import com.example.record.ui.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    recordingViewModel: RecordingViewModel,
    navController: NavController
) {
    val isRecording by recordingViewModel.isRecording.collectAsStateWithLifecycle()
    val elapsedTime by recordingViewModel.elapsedTime.collectAsStateWithLifecycle()
    val lastChunk by recordingViewModel.lastChunk.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Recording Status
                if (isRecording) {
                    RecordingIndicator()
                } else {
                    Text(
                        text = stringResource(R.string.dashboard_ready_to_record),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Center - Elapsed Time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatDuration(elapsedTime),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Recent Chunk Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (lastChunk != null) {
                    Text(
                        text = stringResource(R.string.dashboard_last_recording),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(lastChunk!!.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.dashboard_duration_prefix) + formatDuration(lastChunk!!.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    lastChunk!!.transcription?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dashboard_transcription_prefix) + it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
            
            // Bottom - Spacer
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // FAB for Start/Stop Recording
        FloatingActionButton(
            onClick = { recordingViewModel.toggleRecording() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (isRecording) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (isRecording) stringResource(R.string.content_description_stop_recording) else stringResource(R.string.content_description_start_recording),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun RecordingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(40.dp)
    ) {
        val blinkingColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.error,
            label = "RecordingBlink"
        )
        
        Surface(
            modifier = Modifier
                .size(12.dp),
            shape = MaterialTheme.shapes.small,
            color = blinkingColor
        ) {}
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = stringResource(R.string.recording_indicator_text),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1000) % 60
    val minutes = (durationMillis / (1000 * 60)) % 60
    val hours = (durationMillis / (1000 * 60 * 60))
    val totalMinutes = durationMillis / (1000 * 60)
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", totalMinutes, seconds)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PreviewDashboardScreen() {
    // Note: Preview without actual ViewModels - for UI testing only
    Text(stringResource(R.string.dashboard_preview_text))
}