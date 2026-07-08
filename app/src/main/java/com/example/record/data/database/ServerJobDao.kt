package com.example.record.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerJobDao {
    @Query("SELECT * FROM server_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<ServerJob>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<ServerJob>)

    @Query("DELETE FROM server_jobs")
    suspend fun clearAllJobs()

    @Query("SELECT * FROM server_jobs WHERE transcript LIKE '%' || :query || '%' OR jobId LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchJobs(query: String): Flow<List<ServerJob>>

    @Delete
    suspend fun deleteJob(job: ServerJob)
}
