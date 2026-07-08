package com.example.record.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_chunks")
data class AudioChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chunkIndex: Int = 0,
    val filePath: String,
    val startTime: Long, // Epoch millis
    val endTime: Long,   // Epoch millis
    val duration: Long,  // Millis
    val transcription: String? = null,
    val status: UploadStatus = UploadStatus.PENDING
)
