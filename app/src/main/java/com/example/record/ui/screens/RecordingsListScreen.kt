package com.example.record.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.record.data.database.AudioChunk
import com.example.record.ui.viewmodel.PlaybackState
import com.example.record.ui.viewmodel.RecordingsListViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecordingsListScreen(
    viewModel: RecordingsListViewModel
) {
    val searchResults by viewModel.recordings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val uploadStatus by viewModel.uploadStatus.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search recordings...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true
        )

        // Upload controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = { viewModel.uploadAllChunks() }) {
                Text("Upload all")
            }
        }

        // Recordings List
        if (searchResults.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults, key = { it.id }) { chunk ->
                    RecordingItem(
                        chunk = chunk,
                        playbackState = playbackState,
                        uploadStatus = uploadStatus[chunk.id],
                        onPlay = { viewModel.playRecording(chunk) },
                        onPause = { viewModel.pausePlayback() },
                        onResume = { viewModel.resumePlayback() },
                        onStop = { viewModel.stopPlayback() },
                        onSeek = { viewModel.seekTo(it) },
                        onDelete = { viewModel.deleteRecording(chunk) },
                        onUpload = { viewModel.uploadChunk(chunk) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingItem(
    chunk: AudioChunk,
    playbackState: PlaybackState,
    uploadStatus: String?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit
) {
    val isActiveChunk = chunk.id == playbackState.chunkId
    val isPlaying = isActiveChunk && playbackState.isPlaying

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTimestamp(chunk.startTime),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Duration: ${formatDuration(chunk.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onUpload, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = "Upload",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transcription Preview
            chunk.transcription?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Upload status
            if (!uploadStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upload: $uploadStatus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Playback Controls (only when this chunk is active)
            if (isActiveChunk) {
                Spacer(modifier = Modifier.height(12.dp))

                // Duration Text Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(playbackState.currentPosition.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatDuration(playbackState.duration.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Seekbar
                Slider(
                    value = playbackState.currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..playbackState.duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPause) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Collapsed - just a play button
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(onClick = onPlay) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
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
fun PreviewRecordingsListScreen() {
    // Note: Preview without actual ViewModel - for UI testing only
    Text("Recordings List Preview - Requires ViewModel in actual app")
}