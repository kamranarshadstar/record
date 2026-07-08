package com.example.record.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_jobs")
data class ServerJob(
    @PrimaryKey
    val jobId: String,
    val clientId: String,
    val audioPath: String?,
    val jsonPath: String?,
    val startTime: Long?,
    val endTime: Long?,
    val durationMs: Long?,
    val serverTs: Long?,
    val transcript: String?,
    val createdAt: String?,
    val updatedAt: String?
)
