package com.example.record.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.record.data.repository.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class SensorDetails(
    val id: String,
    val name: String,
    val sensorType: Int?, // null for location
    val isPhysical: Boolean
)

class SensorRepository(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    companion object {
        const val MASTER_KEY = "sensor_master_enabled"
        const val LOCATION_ID = "sensor_location"
    }

    /**
     * Enumerates only the core physical hardware sensors.
     * Excludes Location and all software-derived fusion sensors (Gravity, Rotation Vector, etc.)
     */
    fun getAvailableSensors(): List<SensorDetails> {
        val list = mutableListOf<SensorDetails>()
        
        // Define the whitelist of core physical sensor types
        val physicalTypes = setOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY,
            Sensor.TYPE_STEP_COUNTER
        )

        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        
        allSensors.forEach { sensor ->
            if (sensor.type in physicalTypes) {
                // Ensure we don't add duplicate types (e.g., wake-up vs non-wake-up versions)
                // We prefer the non-wake-up version for logging to save battery
                if (!sensor.isWakeUpSensor) {
                    list.add(
                        SensorDetails(
                            id = getSensorUniqueId(sensor),
                            name = getNormalizedSensorName(sensor),
                            sensorType = sensor.type,
                            isPhysical = true
                        )
                    )
                }
            }
        }

        // De-duplicate by type to ensure only one of each category
        return list.distinctBy { it.sensorType }
    }

    /**
     * Unique key for DataStore based on sensor ID.
     */
    private fun getSensorUniqueId(sensor: Sensor): String {
        val normalizedName = sensor.name.lowercase(Locale.US)
            .replace(" ", "_")
            .replace("[^a-z0-9_]".toRegex(), "")
        return "sensor_${sensor.type}_$normalizedName"
    }

    private fun getNormalizedSensorName(sensor: Sensor): String {
        return when (sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_PRESSURE -> "Barometric Pressure"
            Sensor.TYPE_LIGHT -> "Light"
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_GRAVITY -> "Gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
            Sensor.TYPE_STEP_COUNTER -> "Step Counter"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
            else -> sensor.name
        }
    }

    // --- DataStore Persistence ---

    fun isMasterEnabledFlow(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey(MASTER_KEY)] ?: true
        }
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey(MASTER_KEY)] = enabled
        }
    }

    fun isSensorEnabledFlow(sensorId: String): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[booleanPreferencesKey("enabled_$sensorId")] ?: true
        }
    }

    suspend fun setSensorEnabled(sensorId: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("enabled_$sensorId")] = enabled
        }
    }

    // --- Sensor Snapshot Gathering ---

    suspend fun takeSnapshot(): Map<String, Any> = withContext(Dispatchers.IO) {
        val masterEnabled = isMasterEnabledFlow().first()
        if (!masterEnabled) return@withContext emptyMap<String, Any>()

        val available = getAvailableSensors()
        val enabledSensors = available.filter { details ->
            isSensorEnabledFlow(details.id).first()
        }

        val snapshotData = mutableMapOf<String, Any>()

        for (details in enabledSensors) {
            // Physical sensor snapshot
            val physicalSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            val matchingSensor = physicalSensors.firstOrNull { getSensorUniqueId(it) == details.id }
            if (matchingSensor != null) {
                val values = withTimeoutOrNull(2000L) {
                    getSensorReading(matchingSensor)
                }
                if (values != null && values.isNotEmpty()) {
                    val key = getSensorJsonKey(matchingSensor)
                    val data = formatSensorValues(matchingSensor.type, values)
                    if (data != null) {
                        snapshotData[key] = data
                    }
                }
            }
        }

        return@withContext snapshotData
    }

    private suspend fun getSensorReading(sensor: Sensor): FloatArray? {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : SensorEventListener {
                private var resumed = false
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && !resumed) {
                        resumed = true
                        sensorManager.unregisterListener(this)
                        continuation.resume(event.values.clone())
                    }
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            continuation.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    private fun getSensorJsonKey(sensor: Sensor): String {
        return when (sensor.type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "ambient_temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "humidity"
            Sensor.TYPE_PRESSURE -> "barometric_pressure"
            Sensor.TYPE_LIGHT -> "light"
            Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            Sensor.TYPE_GYROSCOPE -> "gyroscope"
            Sensor.TYPE_GRAVITY -> "gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
            Sensor.TYPE_STEP_COUNTER -> "step_counter"
            Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
            Sensor.TYPE_PROXIMITY -> "proximity"
            Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
            else -> sensor.name.lowercase(Locale.US).replace(" ", "_").replace("[^a-z0-9_]".toRegex(), "")
        }
    }

    private fun formatSensorValues(type: Int, values: FloatArray): Map<String, Any>? {
        return when (type) {
            Sensor.TYPE_AMBIENT_TEMPERATURE -> mapOf("value_celsius" to values[0].toDouble())
            Sensor.TYPE_RELATIVE_HUMIDITY -> mapOf("value_percent" to values[0].toDouble())
            Sensor.TYPE_PRESSURE -> mapOf("value_hpa" to values[0].toDouble())
            Sensor.TYPE_LIGHT -> mapOf("value_lux" to values[0].toDouble())
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_MAGNETIC_FIELD -> {
                if (values.size >= 3) {
                    mapOf("x" to values[0].toDouble(), "y" to values[1].toDouble(), "z" to values[2].toDouble())
                } else null
            }
            Sensor.TYPE_STEP_COUNTER -> mapOf("steps" to values[0].toDouble())
            Sensor.TYPE_PROXIMITY -> mapOf("distance_cm" to values[0].toDouble())
            Sensor.TYPE_ROTATION_VECTOR -> {
                if (values.size >= 4) {
                    mapOf("x" to values[0].toDouble(), "y" to values[1].toDouble(), "z" to values[2].toDouble(), "scalar" to values[3].toDouble())
                } else if (values.size >= 3) {
                    mapOf("x" to values[0].toDouble(), "y" to values[1].toDouble(), "z" to values[2].toDouble(), "scalar" to 0.0)
                } else null
            }
            else -> mapOf("values" to values.map { it.toDouble() })
        }
    }
}
