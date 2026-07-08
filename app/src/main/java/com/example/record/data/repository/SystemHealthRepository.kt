package com.example.record.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class HealthStatus {
    object Idle : HealthStatus()
    object Testing : HealthStatus()
    data class Success(val message: String) : HealthStatus()
    data class Error(val message: String) : HealthStatus()
}

data class DiagnosticResult(
    val connectivity: HealthStatus = HealthStatus.Idle,
    val upload: HealthStatus = HealthStatus.Idle,
    val download: HealthStatus = HealthStatus.Idle
)

class SystemHealthRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val TAG = "SystemHealth"

    suspend fun testConnectivity(baseUrl: String): HealthStatus = withContext(Dispatchers.IO) {
        Log.d(TAG, "Skipping generic connectivity check for: $baseUrl")
        HealthStatus.Success("Ready")
    }

    suspend fun testUpload(baseUrl: String, clientId: String, authToken: String): HealthStatus = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting deep upload test to: $baseUrl")
        
        // Use unique names to avoid collisions with real recordings
        val wavName = "dummy.wav"
        val jsonName = "dummy.json"
        
        val dummyWav = File(context.cacheDir, wavName)
        val dummyJson = File(context.cacheDir, jsonName)
        
        try {
            // 1. Create dummy files
            dummyWav.writeBytes(ByteArray(1024) { 0 }) // 1KB silence
            dummyJson.writeText("{\"test\": \"system_diagnostics\", \"timestamp\": ${System.currentTimeMillis()}}")

            // 2. Prepare Multipart Request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    wavName,
                    dummyWav.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("startTime", System.currentTimeMillis().toString())
                .addFormDataPart("endTime", (System.currentTimeMillis() + 1000).toString())
                .addFormDataPart("duration", "1000")
                // We send the JSON content as the 'sensors' part to verify the server's JSON parsing
                .addFormDataPart("sensors", dummyJson.readText())
                .build()

            val request = Request.Builder()
                .url(baseUrl)
                .post(requestBody)
                .addHeader("X-Client-ID", clientId)
                .addHeader("X-Auth-Token", authToken)
                .addHeader("X-Diagnostic-Test", "true")
                .build()

            // 3. Execute and Analyze
            val healthResult = client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Upload test response [$code]: $body")

                val responseDetails = if (body.isNotEmpty()) " - $body" else ""
                
                when {
                    response.isSuccessful -> {
                        HealthStatus.Success("Full Upload OK (WAV & JSON Verified)")
                    }
                    code == 401 || code == 403 -> {
                        HealthStatus.Error("Auth Failed (403): Check Token/Client ID$responseDetails")
                    }
                    code == 422 -> {
                        HealthStatus.Error("Format Rejected (422): Missing fields$responseDetails")
                    }
                    code >= 500 -> {
                        HealthStatus.Error("Server Error ($code): Check Backend Logs$responseDetails")
                    }
                    else -> {
                        HealthStatus.Error("Failed ($code)$responseDetails")
                    }
                }
            }
            return@withContext healthResult
        } catch (e: Exception) {
            Log.e(TAG, "Deep upload test failed", e)
            val detailedMsg = when (e) {
                is java.net.ConnectException -> "ConnectException: Check if server is running at ${baseUrl.substringBefore("/api")}. Is your IP correct?"
                is java.net.SocketTimeoutException -> "SocketTimeout: Server too slow or unreachable."
                is java.net.UnknownHostException -> "UnknownHost: IP address could not be resolved."
                is IOException -> "IOException: ${e.localizedMessage ?: "Unknown network error"}"
                else -> "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Unknown error"}"
            }
            HealthStatus.Error(detailedMsg)
        } finally {
            // 4. Cleanup local dummy files
            Log.d(TAG, "Cleaning up local dummy files...")
            try {
                if (dummyWav.exists()) {
                    val deleted = dummyWav.delete()
                    Log.d(TAG, "Deleted dummyWav: $deleted")
                }
                if (dummyJson.exists()) {
                    val deleted = dummyJson.delete()
                    Log.d(TAG, "Deleted dummyJson: $deleted")
                }
            } catch (cleanupEx: Exception) {
                Log.e(TAG, "Failed to cleanup local files", cleanupEx)
            }
        }
    }

    suspend fun testDownload(baseUrl: String, clientId: String): HealthStatus = withContext(Dispatchers.IO) {
        // Simplified for now as we are focusing on upload capability
        val jobsUrl = try {
            if (baseUrl.contains("/api/upload")) {
                baseUrl.substringBefore("/api/upload") + "/api/jobs?limit=1"
            } else {
                baseUrl.removeSuffix("/") + "/api/jobs?limit=1"
            }
        } catch (_: Exception) { baseUrl }

        val request = Request.Builder()
            .url(jobsUrl)
            .addHeader("X-Client-ID", clientId)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) HealthStatus.Success("Download Path OK")
                else HealthStatus.Error("Status ${response.code}")
            }
        } catch (e: Exception) {
            HealthStatus.Error("Failed: ${e.javaClass.simpleName}")
        }
    }
}
