package com.example.record.audio

/**
 * Callback interface for when an audio chunk has been completed (finalized).
 */
interface OnChunkCompletedListener {
    fun onChunkCompleted(
        filePath: String,
        startTime: Long,
        endTime: Long,
        duration: Long
    )
    fun onChunkStarted(
        filePath: String,
        startTime: Long
    ) {}
}
