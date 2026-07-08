package com.example.record.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.record.RecordApp
import com.example.record.data.database.ServerJob
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class JobModel(
    val job_id: String,
    val client_id: String,
    val audio_path: String?,
    val json_path: String?,
    val start_time: Long?,
    val end_time: Long?,
    val duration_ms: Long?,
    val server_ts: Long?,
    val transcript: String?,
    val created_at: String?,
    val updated_at: String?
)

sealed class TranscriptUiState {
    object Loading : TranscriptUiState()
    data class Success(val jobs: List<ServerJob>, val isRefreshing: Boolean = false, val isLastPage: Boolean = false) : TranscriptUiState()
    data class Error(val message: String) : TranscriptUiState()
}

class TranscriptViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RecordApp
    private val preferencesRepository = app.userPreferencesRepository
    private val serverJobDao = app.serverJobDao

    private val _isRefreshing = MutableStateFlow(false)
    private val _isLastPage = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    
    private var currentOffset = 0

    companion object {
        private const val PAGE_SIZE = 15
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<TranscriptUiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) serverJobDao.getAllJobs()
            else serverJobDao.searchJobs(query)
        },
        _isRefreshing,
        _isLastPage
    ) { jobs, refreshing, lastPage ->
        if (jobs.isEmpty() && !_isRefreshing.value) {
            TranscriptUiState.Loading // Initial loading state
        } else {
            TranscriptUiState.Success(jobs, refreshing, lastPage)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TranscriptUiState.Loading)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    init {
        refreshJobs()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshJobs() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                val clientId = prefs.clientId
                
                // For refresh, we start from offset 0
                val jobs = doFetchJobs(clientId, limit = PAGE_SIZE, offset = 0)
                
                // Clear and replace cache for the first page
                serverJobDao.clearAllJobs()
                serverJobDao.insertJobs(jobs.map { it.toEntity() })
                
                currentOffset = jobs.size
                _isLastPage.value = jobs.size < PAGE_SIZE
            } catch (e: Exception) {
                Log.e("TranscriptViewModel", "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun fetchNextPage() {
        if (_isRefreshing.value || _isLoadingMore.value || _isLastPage.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                val clientId = prefs.clientId
                
                val jobs = doFetchJobs(clientId, limit = PAGE_SIZE, offset = currentOffset)
                
                if (jobs.isNotEmpty()) {
                    serverJobDao.insertJobs(jobs.map { it.toEntity() })
                    currentOffset += jobs.size
                }
                
                _isLastPage.value = jobs.size < PAGE_SIZE
            } catch (e: Exception) {
                Log.e("TranscriptViewModel", "Fetch more failed", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun deleteJob(job: ServerJob) {
        viewModelScope.launch {
            try {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                val clientId = prefs.clientId
                
                val success = doDeleteJob(clientId, job.jobId)
                if (success) {
                    serverJobDao.deleteJob(job)
                }
            } catch (e: Exception) {
                Log.e("TranscriptViewModel", "Delete failed", e)
            }
        }
    }

    private suspend fun doDeleteJob(clientId: String, jobId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "http://100.110.2.1:8001/api/jobs/$jobId"
        val request = Request.Builder()
            .url(url)
            .header("X-Client-ID", clientId)
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    private suspend fun doFetchJobs(clientId: String, limit: Int, offset: Int): List<JobModel> = withContext(Dispatchers.IO) {
        val url = "http://100.110.2.1:8001/api/jobs?limit=$limit&offset=$offset"
        val request = Request.Builder()
            .url(url)
            .header("X-Client-ID", clientId)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Server returned code ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw Exception("Response body is empty")
            val type = object : TypeToken<List<JobModel>>() {}.type
            gson.fromJson<List<JobModel>>(bodyString, type) ?: emptyList()
        }
    }

    private fun JobModel.toEntity() = ServerJob(
        jobId = job_id,
        clientId = client_id,
        audioPath = audio_path,
        jsonPath = json_path,
        startTime = start_time,
        endTime = end_time,
        durationMs = duration_ms,
        serverTs = server_ts,
        transcript = transcript,
        createdAt = created_at,
        updatedAt = updated_at
    )
}
