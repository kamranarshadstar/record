package com.example.record.audio

import android.content.Context
import android.util.Log
import com.example.record.security.FileEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Audio uploader that decrypts local files and uploads them.
 */
class AudioUploader(
    private val context: Context,
    private val baseUrl: String = "https://your-upload-server.com/api/upload",
    private val clientId: String = "android-device-001",
    private val authToken: String = "DEVICE_TOKEN_ABC123"
) {

    companion object {
        private const val TAG = "AudioUploader"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1_000L
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun uploadChunk(
        filePath: String,
        fileName: String,
        startTime: Long,
        endTime: Long,
        duration: Long
    ) = withContext(Dispatchers.IO) {

        val encryptedFile = File(filePath)
        if (!encryptedFile.exists()) {
            Log.e(TAG, "Encrypted file not found: $filePath")
            return@withContext
        }

        // 1. Decrypt audio to a temporary file
        val tempWavFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.wav")
        try {
            FileEncryptionManager.decryptFile(context, encryptedFile, tempWavFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt audio for upload", e)
            return@withContext
        }

        // 2. Decrypt and read sensors data from local .json.enc file
        val encryptedJsonFile = File(filePath.replace(".wav.enc", ".json.enc"))
        var sensorsJson: String? = null
        if (encryptedJsonFile.exists()) {
            try {
                val decryptedJsonStream = FileEncryptionManager.openEncryptedInput(context, encryptedJsonFile)
                val jsonText = decryptedJsonStream.bufferedReader().use { it.readText() }
                val jsonObject = com.google.gson.JsonParser.parseString(jsonText).asJsonObject
                if (jsonObject.has("sensors")) {
                    sensorsJson = jsonObject.get("sensors").toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decrypting sensors from JSON file", e)
            }
        }

        var attempt = 0
        var backoff = INITIAL_BACKOFF_MS

        while (attempt < MAX_RETRIES) {
            try {
                // 3. Attempt the upload with the decrypted temp file
                uploadOnce(tempWavFile, fileName, startTime, endTime, duration, sensorsJson)

                // 4. Success! Cleanup
                if (encryptedJsonFile.exists()) encryptedJsonFile.delete()
                if (encryptedFile.exists()) encryptedFile.delete()
                if (tempWavFile.exists()) tempWavFile.delete()

                Log.d(TAG, "Successfully uploaded and removed encrypted/temp files for: $fileName")
                return@withContext

            } catch (e: Exception) {
                attempt++
                Log.e(TAG, "Upload failed (attempt $attempt/$MAX_RETRIES): ${e.message}")

                if (attempt >= MAX_RETRIES) {
                    if (tempWavFile.exists()) tempWavFile.delete()
                    throw e
                }

                delay(backoff)
                backoff *= 2
            }
        }
    }

    private fun uploadOnce(
        file: File,
        fileName: String,
        startTime: Long,
        endTime: Long,
        duration: Long,
        sensorsJson: String?
    ) {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "$fileName.wav",
                body = file.asRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("startTime", startTime.toString())
            .addFormDataPart("endTime", endTime.toString())
            .addFormDataPart("duration", duration.toString())

        if (sensorsJson != null) {
            builder.addFormDataPart("sensors", sensorsJson)
        }

        val requestBody = builder.build()

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .addHeader("X-Client-ID", clientId)
            .addHeader("X-Auth-Token", authToken)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw IOException("Server error ${response.code}: $errorBody")
            }
        }
    }
}
