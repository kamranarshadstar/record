package com.example.record.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.record.data.database.ServerJob
import com.example.record.ui.theme.RecordTheme
import com.example.record.ui.viewmodel.TranscriptUiState
import com.example.record.ui.viewmodel.TranscriptViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TranscriptScreen(
    viewModel: TranscriptViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    TranscriptContent(
        uiState = uiState,
        searchQuery = searchQuery,
        onRefresh = { viewModel.refreshJobs() },
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onLoadMore = { viewModel.fetchNextPage() },
        onDeleteJob = { viewModel.deleteJob(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptContent(
    uiState: TranscriptUiState,
    searchQuery: String,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDeleteJob: (ServerJob) -> Unit
) {
    val listState = rememberLazyListState()

    // Detect when user scrolls to the bottom
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItemsCount - 2 && totalItemsCount > 0
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Transcripts",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cloud synchronized recordings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sync")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    placeholder = { Text("Search by ID or content...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is TranscriptUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                is TranscriptUiState.Error -> {
                    ErrorState(message = state.message, onRetry = onRefresh)
                }
                is TranscriptUiState.Success -> {
                    if (state.jobs.isEmpty()) {
                        EmptyState(isSearching = searchQuery.isNotEmpty())
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp) // Tight spacing for modern list look
                        ) {
                            items(state.jobs, key = { it.jobId }) { job ->
                                var isRemoved by remember { mutableStateOf(false) }
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                            isRemoved = true
                                            onDeleteJob(job)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                )

                                AnimatedVisibility(
                                    visible = !isRemoved,
                                    exit = shrinkVertically(
                                        animationSpec = spring(),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut()
                                ) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            val color = when (dismissState.dismissDirection) {
                                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                                else -> Color.Transparent
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(color),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 24.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Delete",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    )
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color.White
                                                    )
                                                }
                                            }
                                        },
                                        content = {
                                            JobTranscriptCard(job = job)
                                        }
                                    )
                                }
                            }

                            if (!state.isLastPage) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobTranscriptCard(job: ServerJob) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Row: Info and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = remember(job.createdAt) { formatIsoTimestamp(job.createdAt) },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Text(
                    text = job.audioPath?.substringAfterLast("/") ?: "audio_file.wav",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Section: Transcript Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp)
            ) {
                Text(
                    text = if (job.transcript.isNullOrBlank()) "Processing audio... Please check back later." else job.transcript,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = if (job.transcript.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Duration and Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val durationText = job.durationMs?.let {
                    val seconds = (it / 1000) % 60
                    val minutes = (it / (1000 * 60))
                    String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
                } ?: "N/A"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!job.transcript.isNullOrBlank()) {
                    Text(
                        text = "Transcribed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        // Bottom border for list separation
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Default.Search else Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isSearching) "No matches found" else "No recordings yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isSearching) "Try adjusting your search terms" else "Upload some audio to see them here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Try Again")
        }
    }
}

private fun formatIsoTimestamp(isoString: String?): String {
    if (isoString == null) return ""
    return try {
        val cleaned = isoString.take(19).replace(" ", "T")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(cleaned)
        if (date != null) {
            val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            formatter.format(date)
        } else {
            isoString
        }
    } catch (e: Exception) {
        isoString
    }
}

@Preview(showBackground = true)
@Composable
fun TranscriptScreenPreview() {
    RecordTheme {
        TranscriptContent(
            uiState = TranscriptUiState.Success(
                jobs = listOf(
                    ServerJob(
                        jobId = "123456789",
                        clientId = "client1",
                        audioPath = "/path/to/audio1.wav",
                        jsonPath = null,
                        startTime = null,
                        endTime = null,
                        durationMs = 125000,
                        serverTs = null,
                        transcript = "This is a sample transcript for the first job. It should show how the card looks with some text.",
                        createdAt = "2023-10-27T10:00:00",
                        updatedAt = "2023-10-27T10:00:00"
                    ),
                    ServerJob(
                        jobId = "987654321",
                        clientId = "client1",
                        audioPath = "/path/to/audio2.wav",
                        jsonPath = null,
                        startTime = null,
                        endTime = null,
                        durationMs = 45000,
                        serverTs = null,
                        transcript = null,
                        createdAt = "2023-10-27T11:30:00",
                        updatedAt = "2023-10-27T11:30:00"
                    )
                )
            ),
            searchQuery = "",
            onRefresh = {},
            onSearchQueryChange = {},
            onLoadMore = {},
            onDeleteJob = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TranscriptScreenEmptyPreview() {
    RecordTheme {
        TranscriptContent(
            uiState = TranscriptUiState.Success(jobs = emptyList()),
            searchQuery = "",
            onRefresh = {},
            onSearchQueryChange = {},
            onLoadMore = {},
            onDeleteJob = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TranscriptScreenLoadingPreview() {
    RecordTheme {
        TranscriptContent(
            uiState = TranscriptUiState.Loading,
            searchQuery = "",
            onRefresh = {},
            onSearchQueryChange = {},
            onLoadMore = {},
            onDeleteJob = {}
        )
    }
}
