package com.example.record.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.record.RecordApp
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.example.record.audio.AudioUploadWorker
import com.example.record.data.database.UploadStatus
import com.example.record.data.database.AudioChunk
import com.example.record.service.AudioRecordingService
import com.example.record.service.RecordingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<RecordApp>()
    
    val isRecording = RecordingStatus.isRecording

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime = _elapsedTime.asStateFlow()

    val lastChunk: StateFlow<AudioChunk?> = app.audioChunkDao.getAllChunks()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            while (true) {
                if (isRecording.value) {
                    _elapsedTime.value += 1000L
                } else {
                    _elapsedTime.value = 0L
                }
                delay(1000)
            }
        }
    }

    fun toggleRecording() {
        if (isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val intent = Intent(app, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START_RECORDING
        }
        app.startService(intent)
    }

    private fun stopRecording() {
        val intent = Intent(app, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP_RECORDING
        }
        app.startService(intent)
        uploadLastChunk()
    }

    /**
     * Upload the last chunk when recording completes.
     */
    fun uploadLastChunk() {
        viewModelScope.launch {
            val chunkToUpload = lastChunk.value

            if (chunkToUpload != null) {
                // Update chunk status to PENDING
                val updatedChunk = chunkToUpload.copy(status = UploadStatus.PENDING)
                app.audioChunkDao.insertChunk(updatedChunk) // Using insertChunk to update

                val uploadUrl = app.userPreferencesRepository.userPreferencesFlow
                    .first().uploadServerUrl

                if (uploadUrl.isBlank()) {
                    Log.e("RecordingViewModel", "Upload aborted: No Server URL in Settings. Chunk ${chunkToUpload.id} remains PENDING.")
                    return@launch
                }

                val inputData = Data.Builder()
                    .putString("filePath", chunkToUpload.filePath)
                    .putString("fileName", "chunk_${chunkToUpload.chunkIndex}")
                    .putLong("startTime", chunkToUpload.startTime)
                    .putLong("endTime", chunkToUpload.endTime)
                    .putLong("duration", chunkToUpload.duration)
                    .putString("uploadServerUrl", uploadUrl)
                    .putLong("chunkId", chunkToUpload.id)
                    .build()

                val uploadWorkRequest = OneTimeWorkRequest.Builder(AudioUploadWorker::class.java)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(app).enqueueUniqueWork(
                    "upload_chunk_${chunkToUpload.id}",
                    ExistingWorkPolicy.REPLACE,
                    uploadWorkRequest
                )

                Log.d("RecordingViewModel", "Enqueued upload work for chunk: ${chunkToUpload.id}")
            }
        }
    }
}
