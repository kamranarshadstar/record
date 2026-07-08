package com.example.record.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class UserPreferences(
    val chunkIntervalMinutes: Int,
    val maxStorageMb: Int,
    val useExternalStorage: Boolean,
    val uploadServerUrl: String,
    val clientId: String,
    val authToken: String,
    val maxConcurrentUploads: Int
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val CHUNK_INTERVAL_MINUTES = intPreferencesKey("chunk_interval_minutes")
        val MAX_STORAGE_MB = intPreferencesKey("max_storage_mb")
        val USE_EXTERNAL_STORAGE = booleanPreferencesKey("use_external_storage")
        val UPLOAD_SERVER_URL = stringPreferencesKey("upload_server_url")
        val CLIENT_ID = stringPreferencesKey("client_id")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val MAX_CONCURRENT_UPLOADS = intPreferencesKey("max_concurrent_uploads")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            UserPreferences(
                chunkIntervalMinutes = preferences[PreferencesKeys.CHUNK_INTERVAL_MINUTES] ?: 5,
                maxStorageMb = preferences[PreferencesKeys.MAX_STORAGE_MB] ?: 1024,
                useExternalStorage = preferences[PreferencesKeys.USE_EXTERNAL_STORAGE] ?: false,
                uploadServerUrl = preferences[PreferencesKeys.UPLOAD_SERVER_URL] ?: "https://your-upload-server.com/api/upload",
                clientId = preferences[PreferencesKeys.CLIENT_ID] ?: "android-device-001",
                authToken = preferences[PreferencesKeys.AUTH_TOKEN] ?: "DEVICE_TOKEN_ABC123",
                maxConcurrentUploads = preferences[PreferencesKeys.MAX_CONCURRENT_UPLOADS] ?: 3
            )
        }

    suspend fun updateMaxConcurrentUploads(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_CONCURRENT_UPLOADS] = count
        }
    }

    suspend fun updateUploadServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPLOAD_SERVER_URL] = url
        }
    }

    suspend fun updateChunkInterval(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CHUNK_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun updateMaxStorage(mb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_STORAGE_MB] = mb
        }
    }

    suspend fun updateUseExternalStorage(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_EXTERNAL_STORAGE] = enabled
        }
    }

    suspend fun updateClientId(clientId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
        }
    }

    suspend fun updateAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTH_TOKEN] = token
        }
    }
}