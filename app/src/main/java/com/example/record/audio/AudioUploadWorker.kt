package com.example.record.audio

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.record.RecordApp
import kotlinx.coroutines.flow.first
import java.io.File

class AudioUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started for chunk ${inputData.getLong("chunkId", -1L)}")
        val filePath = inputData.getString("filePath") ?: return Result.failure()
        val fileName = inputData.getString("fileName") ?: return Result.failure()
        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found at $filePath. Cannot upload.")
            return Result.failure()
        }

        val startTime = inputData.getLong("startTime", 0L)
        val endTime = inputData.getLong("endTime", 0L)
        val duration = inputData.getLong("duration", 0L)
        val uploadServerUrl = inputData.getString("uploadServerUrl") ?: return Result.failure()
        val chunkId = inputData.getLong("chunkId", -1L)

        val app = applicationContext as RecordApp
        val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
        val clientId = prefs.clientId
        val authToken = prefs.authToken

        val audioUploader = AudioUploader(
            context = applicationContext,
            baseUrl = uploadServerUrl,
            clientId = clientId,
            authToken = authToken
        )

        return try {
            audioUploader.uploadChunk(filePath, fileName, startTime, endTime, duration)

            // If upload is successful, delete the chunk from the database
            if (chunkId != -1L) {
                val database = (applicationContext as RecordApp).database
                val audioChunkDao = database.audioChunkDao()
                val chunkToDelete = audioChunkDao.getChunkById(chunkId)
                chunkToDelete?.let { audioChunkDao.deleteChunk(it) }
            }
            Log.d(TAG, "Audio chunk uploaded and removed from DB: $fileName")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for chunk $fileName: ${e.message}")
            // WorkManager will retry if Result.retry() is returned
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "AudioUploadWorker"
    }
}
