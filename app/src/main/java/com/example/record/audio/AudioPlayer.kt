package com.example.record.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.record.security.FileEncryptionManager
import java.io.File

/**
 * Handles playback of recorded audio files. Decrypts encrypted files to a temporary
 * location before playback.
 */
class AudioPlayer(private val context: Context) {
    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var tempPlaybackFile: File? = null

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val currentPosition: Int get() = try {
        mediaPlayer?.currentPosition ?: 0
    } catch (e: Exception) {
        Log.w(TAG, "Error getting current position", e)
        0
    }
    val duration: Int get() = try {
        mediaPlayer?.duration ?: 0
    } catch (e: Exception) {
        Log.w(TAG, "Error getting duration", e)
        0
    }

    /**
     * Play an audio file from file path. Handles decryption if the file is encrypted.
     */
    fun playFile(filePath: String, onCompletion: () -> Unit) {
        stop()
        
        try {
            val file = File(filePath)
            val sourceFile = if (filePath.endsWith(".enc")) {
                // Decrypt to temp file
                val tempFile = File(context.cacheDir, "play_temp_${System.currentTimeMillis()}.wav")
                FileEncryptionManager.decryptFile(context, file, tempFile)
                tempPlaybackFile = tempFile
                tempFile
            } else {
                file
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(sourceFile))
                prepare()
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    cleanupTempFile()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Playback error: what=$what, extra=$extra")
                    cleanupTempFile()
                    onCompletion()
                    true
                }
                start()
                Log.d(TAG, "Started playing: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing file: $filePath", e)
            cleanupTempFile()
            onCompletion()
        }
    }

    private fun cleanupTempFile() {
        tempPlaybackFile?.let {
            if (it.exists()) it.delete()
        }
        tempPlaybackFile = null
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing playback", e)
        }
    }

    fun resume() {
        try {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun stop() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
                mediaPlayer!!.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        } finally {
            mediaPlayer = null
            cleanupTempFile()
        }
    }
}
