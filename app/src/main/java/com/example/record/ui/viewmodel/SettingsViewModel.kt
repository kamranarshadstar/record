package com.example.record.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.record.RecordApp
import com.example.record.data.repository.*
import com.example.record.sensor.SensorDetails
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RecordApp
    private val repository = app.userPreferencesRepository
    private val sensorRepository = app.sensorRepository
    private val healthRepository = app.systemHealthRepository

    private val _diagnosticResult = MutableStateFlow(DiagnosticResult())
    val diagnosticResult: StateFlow<DiagnosticResult> = _diagnosticResult

    val preferences: StateFlow<UserPreferences?> = repository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun runDiagnostics() {
        viewModelScope.launch {
            val prefs = preferences.value ?: return@launch
            val url = prefs.uploadServerUrl
            if (url.isBlank()) {
                _diagnosticResult.value = DiagnosticResult(
                    connectivity = HealthStatus.Error("URL is empty")
                )
                return@launch
            }

            // Reset
            _diagnosticResult.value = DiagnosticResult()

            // Run Deep Upload Test (Includes connectivity, auth, and format verification)
            _diagnosticResult.update { it.copy(upload = HealthStatus.Testing) }
            val uploadStatus = healthRepository.testUpload(url, prefs.clientId, prefs.authToken)
            _diagnosticResult.update { it.copy(upload = uploadStatus) }
        }
    }

    // Sensor State Accessors
    val availableSensors: List<SensorDetails> by lazy {
        sensorRepository.getAvailableSensors()
    }

    val isMasterSensorEnabled: StateFlow<Boolean> = sensorRepository.isMasterEnabledFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun setMasterSensorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sensorRepository.setMasterEnabled(enabled)
        }
    }

    fun isSensorEnabledFlow(sensorId: String): Flow<Boolean> {
        return sensorRepository.isSensorEnabledFlow(sensorId)
    }

    fun setSensorEnabled(sensorId: String, enabled: Boolean) {
        viewModelScope.launch {
            sensorRepository.setSensorEnabled(sensorId, enabled)
        }
    }

    fun updateMaxConcurrentUploads(count: Int) {
        viewModelScope.launch {
            repository.updateMaxConcurrentUploads(count)
        }
    }

    fun updateChunkInterval(minutes: Int) {
        viewModelScope.launch {
            repository.updateChunkInterval(minutes)
        }
    }

    fun updateMaxStorage(mb: Int) {
        viewModelScope.launch {
            repository.updateMaxStorage(mb)
        }
    }

    fun updateUploadServerUrl(url: String) {
        viewModelScope.launch {
            repository.updateUploadServerUrl(url)
        }
    }

    fun updateClientId(clientId: String) {
        viewModelScope.launch {
            repository.updateClientId(clientId)
        }
    }

    fun updateAuthToken(token: String) {
        viewModelScope.launch {
            repository.updateAuthToken(token)
        }
    }
}
