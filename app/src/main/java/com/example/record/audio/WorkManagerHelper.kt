package com.example.record.audio

import android.content.Context
import androidx.work.*
import com.example.record.data.database.AudioChunk

object WorkManagerHelper {
    fun enqueueUpload(context: Context, chunk: AudioChunk, uploadServerUrl: String) {
        val data = Data.Builder()
            .putString("filePath", chunk.filePath)
            .putString("fileName", "chunk_${chunk.id}")
            .putLong("startTime", chunk.startTime)
            .putLong("endTime", chunk.endTime)
            .putLong("duration", chunk.duration)
            .putString("uploadServerUrl", uploadServerUrl)
            .putLong("chunkId", chunk.id)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<AudioUploadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .addTag("upload_chunk_${chunk.id}")
            .build()

        android.util.Log.d("WorkManagerHelper", "Enqueuing upload for chunk ${chunk.id} to $uploadServerUrl")
        WorkManager.getInstance(context).enqueue(uploadWorkRequest)
    }
}
