package com.example.record.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingStatus {
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    fun setRecording(active: Boolean) {
        _isRecording.value = active
    }
}
