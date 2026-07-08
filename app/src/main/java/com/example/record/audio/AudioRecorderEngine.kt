package com.example.record.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.record.security.FileEncryptionManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Core audio recording engine using [AudioRecord] API.
 *
 * Records PCM 16-bit mono at 16 kHz sample rate, writing data into WAV files.
 * Automatically chunks audio: when the configured chunk duration elapses,
 * the current WAV file is finalized and a new one is started.
 */
class AudioRecorderEngine(
    private val context: Context,
    private val outputDir: File,
    private var chunkDurationMs: Long = 5L * 60L * 1000L // default 5 minutes
) {
    companion object {
        private const val TAG = "AudioRecorderEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = 16
        private const val NUM_CHANNELS = 1
    }

    // Use AtomicReference for thread-safe access to the listener
    private val _listener = AtomicReference<OnChunkCompletedListener?>()
    var listener: OnChunkCompletedListener?
        get() = _listener.get()
        set(value) = _listener.set(value)

    @Volatile
    private var isRecording = false

    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // Current chunk state
    private var currentFile: File? = null
    private var currentOutputStream: FileOutputStream? = null
    private var currentChunkStartTime: Long = 0L
    private var totalBytesWritten: Long = 0L

    fun setChunkDuration(durationMs: Long) {
        this.chunkDurationMs = durationMs
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        if (!outputDir.exists()) {
            // Attempt to create directories and check if successful
            if (!outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create output directory: ${outputDir.absolutePath}")
                return // Abort recording if directory can't be created
            }
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread({
            recordLoop(bufferSize)
        }, "AudioRecorderThread").also { it.start() }

        Log.i(TAG, "Recording started")
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        recordingThread?.join(3000)
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Finalize the last chunk
        finalizeCurrentChunk()

        Log.i(TAG, "Recording stopped")
    }

    private fun recordLoop(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)

        // Start first chunk
        startNewChunk()

        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: -1
            if (bytesRead > 0) {
                try {
                    currentOutputStream?.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                } catch (e: IOException) {
                    Log.e(TAG, "Error writing audio data", e)
                }

                // Check if chunk duration has elapsed
                val elapsed = System.currentTimeMillis() - currentChunkStartTime
                if (elapsed >= chunkDurationMs) {
                    finalizeCurrentChunk()
                    startNewChunk()
                }
            } else if (bytesRead < 0) {
                Log.e(TAG, "Error reading audio data: $bytesRead")
            }
        }
    }

    private fun startNewChunk() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "audio_$timestamp.wav"
        currentFile = File(outputDir, fileName)
        currentChunkStartTime = System.currentTimeMillis()
        totalBytesWritten = 0L

        try {
            currentOutputStream = FileOutputStream(currentFile!!)
            // Write placeholder WAV header (44 bytes)
            writeWavHeader(currentOutputStream!!, 0)
            
            currentFile?.let { file ->
                _listener.get()?.onChunkStarted(file.absolutePath, currentChunkStartTime)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating chunk file", e)
        }
    }

    private fun finalizeCurrentChunk() {
        val file = currentFile ?: return
        val startTime = currentChunkStartTime
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val bytesWritten = totalBytesWritten

        try {
            currentOutputStream?.close()
            currentOutputStream = null

            // Update WAV header with actual data size
            if (file.exists() && bytesWritten > 0) {
                updateWavHeader(file, bytesWritten)
                
                // --- ENCRYPTION AT REST ---
                val encryptedFile = File(file.absolutePath + ".enc")
                FileEncryptionManager.encryptFile(context, file, encryptedFile)
                file.delete() // Delete the plain text file
                
                // Safely get the listener and invoke the callback with the encrypted path
                _listener.get()?.onChunkCompleted(
                    filePath = encryptedFile.absolutePath,
                    startTime = startTime,
                    endTime = endTime,
                    duration = duration
                )
                Log.i(TAG, "Chunk completed and encrypted: ${encryptedFile.name}, duration: ${duration}ms")
            } else {
                // Remove empty files
                file.delete()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error finalizing chunk", e)
        }

        currentFile = null
        totalBytesWritten = 0L
    }

    /**
     * Writes a WAV file header. The data size is initially 0 and must be
     * updated after recording via [updateWavHeader].
     */
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Long) {
        val totalDataLen = dataSize + 36
        val byteRate = (SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8).toLong()
        val blockAlign = (NUM_CHANNELS * BITS_PER_SAMPLE / 8).toShort()

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeLittleEndianInt(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt sub-chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeLittleEndianInt(header, 16, 16) // Sub-chunk size (16 for PCM)
        writeLittleEndianShort(header, 20, 1) // Audio format (1 = PCM)
        writeLittleEndianShort(header, 22, NUM_CHANNELS.toShort())
        writeLittleEndianInt(header, 24, SAMPLE_RATE)
        writeLittleEndianInt(header, 28, byteRate.toInt())
        writeLittleEndianShort(header, 32, blockAlign)
        writeLittleEndianShort(header, 34, BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeLittleEndianInt(header, 40, dataSize.toInt())

        outputStream.write(header, 0, 44)
    }

    /**
     * Updates the WAV header of a completed file with the actual data size.
     */
    private fun updateWavHeader(file: File, dataSize: Long) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalDataLen = dataSize + 36

                // Update RIFF chunk size (offset 4)
                raf.seek(4)
                raf.write(intToLittleEndianBytes(totalDataLen.toInt()))

                // Update data sub-chunk size (offset 40)
                raf.seek(40)
                raf.write(intToLittleEndianBytes(dataSize.toInt()))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }

    private fun writeLittleEndianInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeLittleEndianShort(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun intToLittleEndianBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
}
