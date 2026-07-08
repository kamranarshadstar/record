package com.example.record

import android.app.Application
import com.example.record.data.database.AppDatabase
import com.example.record.data.database.AudioChunkDao
import com.example.record.data.repository.UserPreferencesRepository

class RecordApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val audioChunkDao: AudioChunkDao by lazy { database.audioChunkDao() }
    val serverJobDao by lazy { database.serverJobDao() }

    val userPreferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }

    val sensorRepository: com.example.record.sensor.SensorRepository by lazy { com.example.record.sensor.SensorRepository(this) }

    val systemHealthRepository: com.example.record.data.repository.SystemHealthRepository by lazy { com.example.record.data.repository.SystemHealthRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // These are now initialized on first access via 'by lazy'
        // You can still force initialization here if needed, but it's often not necessary.
        // e.g., val _ = database
    }
}
