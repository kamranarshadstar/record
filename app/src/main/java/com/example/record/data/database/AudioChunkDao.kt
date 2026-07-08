package com.example.record.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {
    @Query("SELECT * FROM audio_chunks ORDER BY startTime DESC")
    fun getAllChunks(): Flow<List<AudioChunk>>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): AudioChunk?

    @Query("SELECT * FROM audio_chunks WHERE filePath = :path")
    suspend fun getChunkByPath(path: String): AudioChunk?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunk): Long

    @Delete
    suspend fun deleteChunk(chunk: AudioChunk)

    @Query("DELETE FROM audio_chunks WHERE endTime < :timestamp")
    suspend fun deleteChunksOlderThan(timestamp: Long)

    @Query("SELECT SUM(duration) FROM audio_chunks")
    fun getTotalDuration(): Flow<Long?>

    @Query("SELECT * FROM audio_chunks WHERE transcription LIKE '%' || :query || '%' ORDER BY startTime DESC")
    fun searchByTranscription(query: String): Flow<List<AudioChunk>>
}
