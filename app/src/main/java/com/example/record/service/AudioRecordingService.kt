package com.example.record.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.record.MainActivity
import com.example.record.R
import com.example.record.RecordApp
import com.example.record.audio.AudioRecorderEngine
import com.example.record.audio.OnChunkCompletedListener
import com.example.record.audio.WorkManagerHelper
import com.example.record.data.StorageManager
import com.example.record.data.database.AudioChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class AudioRecordingService : Service() {

    companion object {
        private const val TAG = "AudioRecordingService"
        const val ACTION_START_RECORDING = "com.example.record.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.record.ACTION_STOP_RECORDING"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecorderEngine: AudioRecorderEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val storageManager by lazy { StorageManager(this, (application as RecordApp).audioChunkDao) }

    private val activeSnapshots = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Map<String, Any>>>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (RecordingStatus.isRecording.value) {
            Log.w(TAG, "Already recording")
            return
        }

        // Check permission before starting FGS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start recording: RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        // Start foreground with notification and type
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return
        }

        // Acquire wake lock
        acquireWakeLock()

        val app = application as RecordApp

        serviceScope.launch {
            try {
                val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                val chunkDurationMs = prefs.chunkIntervalMinutes * 60L * 1000L

                val outputDir = File(filesDir, "recordings")

                audioRecorderEngine = AudioRecorderEngine(
                    context = applicationContext,
                    outputDir = outputDir,
                    chunkDurationMs = chunkDurationMs
                ).apply {
                    listener = object : OnChunkCompletedListener {
                        override fun onChunkStarted(filePath: String, startTime: Long) {
                            // Use a non-service scope so the snapshot continues even if service stops/chunks rotate
                            val deferredSnapshot = CoroutineScope(Dispatchers.IO).async {
                                try {
                                    app.sensorRepository.takeSnapshot()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error taking sensor snapshot", e)
                                    emptyMap<String, Any>()
                                }
                            }
                            activeSnapshots[filePath] = deferredSnapshot
                        }

                        override fun onChunkCompleted(
                            filePath: String,
                            startTime: Long,
                            endTime: Long,
                            duration: Long
                        ) {
                            // Use a non-service scope to ensure completion even if service is stopping
                            CoroutineScope(Dispatchers.IO).launch {
                                val sensorsMap = try {
                                    activeSnapshots.remove(filePath)?.await() ?: emptyMap()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to await sensor snapshot for $filePath", e)
                                    emptyMap()
                                }

                                val currentPrefs = app.userPreferencesRepository.userPreferencesFlow.first()

                                // Write JSON file on device under a 'sensors' key
                                val jsonFile = File(filePath.replace(".wav.enc", ".json"))
                                val metadata = mutableMapOf<String, Any>(
                                    "startTime" to startTime,
                                    "endTime" to endTime,
                                    "duration" to duration,
                                    "client_id" to currentPrefs.clientId
                                )
                                if (sensorsMap.isNotEmpty()) {
                                    metadata["sensors"] = sensorsMap
                                }

                                try {
                                    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                                    val jsonContent = gson.toJson(metadata)
                                    
                                    // Encrypt JSON metadata
                                    val encryptedJsonFile = File(jsonFile.absolutePath + ".enc")
                                    val encryptedOutput = com.example.record.security.FileEncryptionManager.openEncryptedOutput(applicationContext, encryptedJsonFile)
                                    encryptedOutput.use { it.write(jsonContent.toByteArray()) }
                                    
                                    Log.i(TAG, "Written encrypted sensor and metadata JSON to: ${encryptedJsonFile.absolutePath}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error writing encrypted JSON metadata file", e)
                                }

                                val chunkTranscription = null

                                val chunk = AudioChunk(
                                    filePath = filePath,
                                    startTime = startTime,
                                    endTime = endTime,
                                    duration = duration,
                                    transcription = chunkTranscription
                                )

                                val insertedId = app.audioChunkDao.insertChunk(chunk)
                                val savedChunk = chunk.copy(id = insertedId)
                                Log.i(TAG, "Chunk saved to database: $filePath (ID: $insertedId)")
                                
                                Log.d(TAG, "Checking if should upload: URL='${currentPrefs.uploadServerUrl}'")
                                // Automatically enqueue upload if server URL is available
                                if (currentPrefs.uploadServerUrl.isNotBlank()) {
                                    WorkManagerHelper.enqueueUpload(applicationContext, savedChunk, currentPrefs.uploadServerUrl)
                                } else {
                                    Log.w(TAG, "Upload URL is blank, skipping background upload")
                                }

                                // Storage cleanup
                                storageManager.cleanupOldRecordings(currentPrefs.maxStorageMb)
                            }
                        }
                    }
                    startRecording()
                }

                RecordingStatus.setRecording(true)
                Log.i(TAG, "Recording service started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording engine", e)
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        if (!RecordingStatus.isRecording.value) return

        audioRecorderEngine?.stopRecording()
        audioRecorderEngine = null

        RecordingStatus.setRecording(false)

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "Recording service stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for audio recording service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "record::AudioRecordingWakeLock"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
}
