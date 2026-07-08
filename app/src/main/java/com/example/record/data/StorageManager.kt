package com.example.record.data

import android.content.Context
import android.util.Log
import com.example.record.data.database.AudioChunkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages storage cleanup for audio recordings.
 * 
 * Monit ors total recording size and automatically deletes oldest recordings
 * when the user-configured storage limit is exceeded.
 */
class StorageManager(
    private val context: Context,
    private val dao: AudioChunkDao
) {
    companion object {
        private const val TAG = "StorageManager"
    }

    /**
     * Cleans up old recordings when storage exceeds limit.
     * Deletes oldest files first until size is within limit.
     * 
     * @param maxStorageMb Maximum storage limit in MB
     */
    suspend fun cleanupOldRecordings(maxStorageMb: Int) {
        withContext(Dispatchers.IO) {
            try {
                val recordingsDir = File(context.filesDir, "recordings")
                if (!recordingsDir.exists()) {
                    Log.d(TAG, "Recordings directory does not exist, no cleanup needed")
                    return@withContext
                }

                val files = recordingsDir.listFiles()?.toMutableList() ?: emptyList<File>().toMutableList()
                if (files.isEmpty()) {
                    Log.d(TAG, "No recording files found")
                    return@withContext
                }

                // Sort by modification time (oldest first)
                files.sortBy { it.lastModified() }

                var totalSize = files.sumOf { it.length() }
                val maxSizeBytes = maxStorageMb * 1024L * 1024L

                Log.d(TAG, "Storage check: ${totalSize / (1024 * 1024)} MB used / $maxStorageMb MB limit")

                var deletedCount = 0
                while (totalSize > maxSizeBytes && files.isNotEmpty()) {
                    val oldest = files.removeAt(0)
                    val size = oldest.length()
                    
                    try {
                        if (oldest.delete()) {
                            totalSize -= size
                            deletedCount++
                            
                            // Also remove from database
                            val chunk = dao.getChunkByPath(oldest.absolutePath)
                            if (chunk != null) {
                                dao.deleteChunk(chunk)
                                Log.d(TAG, "Deleted old recording: ${oldest.name} (${size / 1024} KB)")
                            }
                        } else {
                            Log.w(TAG, "Failed to delete file: ${oldest.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting file: ${oldest.name}", e)
                    }
                }

                if (deletedCount > 0) {
                    Log.i(TAG, "Cleanup complete: deleted $deletedCount files, now using ${totalSize / (1024 * 1024)} MB")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during storage cleanup", e)
            }
        }
    }

    /**
     * Get current total storage usage of recordings.
     */
    suspend fun getCurrentStorageUsageMb(): Double {
        return withContext(Dispatchers.IO) {
            try {
                val recordingsDir = File(context.filesDir, "recordings")
                if (!recordingsDir.exists()) return@withContext 0.0

                val totalBytes = recordingsDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }

                totalBytes / (1024.0 * 1024.0)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating storage usage", e)
                0.0
            }
        }
    }
}
