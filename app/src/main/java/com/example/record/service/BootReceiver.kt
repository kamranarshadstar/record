package com.example.record.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed.")
            
            // On Android 14+ (API 34), microphone foreground services cannot be started from the background 
            // without a specific exemption. We'll skip auto-start on boot for those versions to avoid crashes.
            if (Build.VERSION.SDK_INT >= 34) {
                Log.w("BootReceiver", "Skipping auto-start on boot for Android 14+ due to FGS microphone restrictions.")
                return
            }

            // For older versions, check permission before starting
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val serviceIntent = Intent(context, AudioRecordingService::class.java).apply {
                    action = AudioRecordingService.ACTION_START_RECORDING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i("BootReceiver", "Service auto-started successfully.")
            } else {
                Log.w("BootReceiver", "Permission missing, not starting service on boot.")
            }
        }
    }
}
