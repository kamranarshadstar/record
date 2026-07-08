package com.example.record.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.record.RecordApp
import com.example.record.audio.AudioPlayer
import com.example.record.audio.AudioUploader
import com.example.record.data.database.AudioChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

data class PlaybackState(
    val chunkId: Long = -1L,
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Long = 0
)

class RecordingsListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RecordApp
    private val dao = app.audioChunkDao
    private val audioPlayer = AudioPlayer(app)
    private val preferencesRepository = app.userPreferencesRepository

    private var dynamicSemaphore: Semaphore? = null

    private val _uploadStatus = MutableStateFlow<Map<Long, String>>(emptyMap())
    val uploadStatus: StateFlow<Map<Long, String>> = _uploadStatus

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private var seekUpdateJob: Job? = null

    val recordings: StateFlow<List<AudioChunk>> = combine(
        dao.getAllChunks(),
        _searchQuery
    ) { chunks, query ->
        if (query.isBlank()) chunks
        else chunks.filter {
            it.transcription?.contains(query, ignoreCase = true) == true ||
                    it.filePath.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun deleteRecording(chunk: AudioChunk) {
        viewModelScope.launch {
            if (_playbackState.value.chunkId == chunk.id) stopPlayback()
            dao.deleteChunk(chunk)
            val file = File(chunk.filePath)
            if (file.exists()) file.delete()
            val jsonFile = File(chunk.filePath.replace(".wav", ".json"))
            if (jsonFile.exists()) jsonFile.delete()
            _uploadStatus.update { it - chunk.id }
        }
    }

    fun uploadChunk(chunk: AudioChunk) {
        viewModelScope.launch {
            if (_uploadStatus.value[chunk.id] == "Uploading...") return@launch
            _uploadStatus.update { it + (chunk.id to "Waiting...") }

            val audioFile = File(chunk.filePath)
            if (!audioFile.exists()) {
                Log.e("Upload", "Audio file not found at ${chunk.filePath}. Cannot upload chunk ${chunk.id}.")
                _uploadStatus.update { it + (chunk.id to "Failed (File not found)") }
                return@launch
            }

            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                val maxConnections = prefs.maxConcurrentUploads.coerceAtLeast(1)

                if (dynamicSemaphore == null) {
                    dynamicSemaphore = Semaphore(maxConnections)
                }

                dynamicSemaphore?.withPermit {
                    _uploadStatus.update { it + (chunk.id to "Uploading...") }
                    val url = prefs.uploadServerUrl
                    if (url.isBlank()) throw IllegalStateException("Server URL not set")

                    AudioUploader(
                        context = app,
                        baseUrl = url,
                        clientId = prefs.clientId,
                        authToken = prefs.authToken
                    ).uploadChunk(
                        filePath = chunk.filePath,
                        fileName = "chunk_${chunk.id}.wav",
                        startTime = chunk.startTime,
                        endTime = chunk.endTime,
                        duration = chunk.duration
                    )

                    deleteRecording(chunk)
                }
            } catch (e: Exception) {
                Log.e("Upload", "Failed: ${chunk.id}", e)
                _uploadStatus.update { it + (chunk.id to "Failed: ${e.localizedMessage}") }
            }
        }
    }

    fun uploadAllChunks() {
        viewModelScope.launch {
            val allChunks = dao.getAllChunks().first()
            allChunks.forEach { uploadChunk(it) }
        }
    }

    fun playRecording(chunk: AudioChunk) {
        audioPlayer.playFile(chunk.filePath) {
            _playbackState.value = PlaybackState()
            seekUpdateJob?.cancel()
        }
        _playbackState.value = PlaybackState(chunk.id, true, 0, audioPlayer.duration.toLong())
        startSeekUpdates(chunk.id)
    }

    fun pausePlayback() {
        audioPlayer.pause()
        seekUpdateJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false) }
    }

    fun resumePlayback() {
        audioPlayer.resume()
        _playbackState.update { it.copy(isPlaying = true) }
        startSeekUpdates(_playbackState.value.chunkId)
    }

    fun stopPlayback() {
        audioPlayer.stop()
        seekUpdateJob?.cancel()
        _playbackState.value = PlaybackState()
    }

    fun seekTo(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(currentPosition = positionMs) }
    }

    private fun startSeekUpdates(chunkId: Long) {
        seekUpdateJob?.cancel()
        seekUpdateJob = viewModelScope.launch {
            while (isActive) {
                _playbackState.update { state ->
                    if (state.chunkId == chunkId) state.copy(currentPosition = audioPlayer.currentPosition) else state
                }
                delay(200)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        seekUpdateJob?.cancel()
        audioPlayer.stop()
    }
}