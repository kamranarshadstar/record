package com.example.record.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object FileEncryptionManager {

    fun getEncryptedFile(context: Context, file: File): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    fun encryptFile(context: Context, inputFile: File, encryptedFile: File) {
        val encrypted = getEncryptedFile(context, encryptedFile)
        inputFile.inputStream().use { input ->
            encrypted.openFileOutput().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun decryptFile(context: Context, encryptedFile: File, outputFile: File) {
        val encrypted = getEncryptedFile(context, encryptedFile)
        encrypted.openFileInput().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    
    fun openEncryptedOutput(context: Context, file: File): OutputStream {
        return getEncryptedFile(context, file).openFileOutput()
    }

    fun openEncryptedInput(context: Context, file: File): InputStream {
        return getEncryptedFile(context, file).openFileInput()
    }
}
