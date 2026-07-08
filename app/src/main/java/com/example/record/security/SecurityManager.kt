package com.example.record.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import java.security.GeneralSecurityException
import android.util.Log
import javax.crypto.SecretKey

object SecurityManager {

    private const val KEY_ALIAS = "database_encryption_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            // This can happen if the app was uninstalled and reinstalled, and the old key is no longer valid.
            // We need to clear the old preferences and create new ones.
            Log.e("SecurityManager", "Error decrypting shared preferences, clearing and recreating.", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            // Recursively call to get a fresh, valid passphrase.
            return getDatabasePassphrase(context)
        }

        val existingPassphrase = sharedPreferences.getString(DB_PASSPHRASE_KEY, null)
        if (existingPassphrase != null) {
            return existingPassphrase.toByteArray(Charsets.UTF_8)
        }

        // Generate a new passphrase if it doesn't exist
        val random = SecureRandom()
        val passphraseBytes = ByteArray(32)
        random.nextBytes(passphraseBytes)
        val newPassphrase = android.util.Base64.encodeToString(passphraseBytes, android.util.Base64.NO_WRAP)
        
        sharedPreferences.edit()
            .putString(DB_PASSPHRASE_KEY, newPassphrase)
            .apply()

        return newPassphrase.toByteArray(Charsets.UTF_8)
    }

    fun getFileEncryptionKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
}
