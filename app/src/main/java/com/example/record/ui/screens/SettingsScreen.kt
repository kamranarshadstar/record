package com.example.record.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.record.data.repository.UserPreferences
import com.example.record.sensor.SensorDetails
import com.example.record.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isMasterSensorEnabled by viewModel.isMasterSensorEnabled.collectAsStateWithLifecycle()
    val diagnosticResult by viewModel.diagnosticResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Configure system and hardware tracking",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        if (prefs == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else {
            SettingsContent(
                padding = padding,
                preferences = prefs!!,
                isMasterSensorEnabled = isMasterSensorEnabled,
                diagnosticResult = diagnosticResult,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun SettingsContent(
    padding: PaddingValues,
    preferences: UserPreferences,
    isMasterSensorEnabled: Boolean,
    diagnosticResult: com.example.record.data.repository.DiagnosticResult,
    viewModel: SettingsViewModel
) {
    val scrollState = rememberScrollState()

    // Local state for UI responsiveness before saving
    var uploadUrl by remember(preferences.uploadServerUrl) { mutableStateOf(preferences.uploadServerUrl) }
    var clientId by remember(preferences.clientId) { mutableStateOf(preferences.clientId) }
    var authToken by remember(preferences.authToken) { mutableStateOf(preferences.authToken) }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        // --- 1. SYSTEM DIAGNOSTICS ---
        SectionHeader("SYSTEM HEALTH")
        DiagnosticCard(
            status = diagnosticResult.upload,
            onRun = { viewModel.runDiagnostics() }
        )

        // --- 2. RECORDING SETTINGS ---
        SectionHeader("RECORDING & STORAGE")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SliderSetting(
                    icon = Icons.Default.Timer,
                    label = "Chunk Interval",
                    value = preferences.chunkIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.updateChunkInterval(it.toInt()) },
                    valueRange = 1f..30f,
                    unit = "min"
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SliderSetting(
                    icon = Icons.Default.SdStorage,
                    label = "Max Local Storage",
                    value = preferences.maxStorageMb.toFloat(),
                    onValueChange = { viewModel.updateMaxStorage(it.toInt()) },
                    valueRange = 100f..5000f,
                    unit = "MB"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SliderSetting(
                    icon = Icons.Default.Upload,
                    label = "Parallel Uploads",
                    value = preferences.maxConcurrentUploads.toFloat(),
                    onValueChange = { viewModel.updateMaxConcurrentUploads(it.toInt()) },
                    valueRange = 1f..10f,
                    unit = "threads"
                )
            }
        }

        // --- 3. SERVER CONFIGURATION ---
        SectionHeader("CLOUD & SERVER")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                InputField(
                    icon = Icons.Default.Link,
                    label = "Server URL",
                    value = uploadUrl,
                    onValueChange = { uploadUrl = it },
                    onSave = { viewModel.updateUploadServerUrl(uploadUrl.trim()) }
                )
                InputField(
                    icon = Icons.Default.Fingerprint,
                    label = "Device Client ID",
                    value = clientId,
                    onValueChange = { clientId = it },
                    onSave = { viewModel.updateClientId(clientId.trim()) }
                )
                InputField(
                    icon = Icons.Default.VpnKey,
                    label = "Authentication Token",
                    value = authToken,
                    onValueChange = { authToken = it },
                    onSave = { viewModel.updateAuthToken(authToken.trim()) },
                    isPassword = true
                )
            }
        }

        // --- 4. HARDWARE SENSORS ---
        SectionHeader("HARDWARE SENSORS")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sensors, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Master Sensor Switch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Global control for all loggers", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isMasterSensorEnabled, onCheckedChange = { viewModel.setMasterSensorEnabled(it) })
                }

                AnimatedVisibility(visible = isMasterSensorEnabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        viewModel.availableSensors.forEach { sensor ->
                            SensorToggleRow(
                                sensor = sensor,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
fun DiagnosticCard(
    status: com.example.record.data.repository.HealthStatus,
    onRun: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Upload Capability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                when (status) {
                    is com.example.record.data.repository.HealthStatus.Idle -> Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) { Text("NOT TESTED") }
                    is com.example.record.data.repository.HealthStatus.Testing -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    is com.example.record.data.repository.HealthStatus.Success -> Badge(containerColor = Color(0xFF4CAF50)) { Text("VERIFIED", color = Color.White) }
                    is com.example.record.data.repository.HealthStatus.Error -> Badge(containerColor = MaterialTheme.colorScheme.error) { Text("FAILED", color = Color.White) }
                }
            }
            
            if (status is com.example.record.data.repository.HealthStatus.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = onRun,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Run Deep Connection Test")
            }
        }
    }
}

@Composable
fun SliderSetting(
    icon: ImageVector,
    label: String,
    value: Float,
    unit: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${value.toInt()} $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
fun InputField(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    isPassword: Boolean = false
) {
    var isEditing by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    isEditing = true
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            if (isEditing) {
                IconButton(
                    onClick = {
                        onSave()
                        isEditing = false
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun SensorToggleRow(
    sensor: SensorDetails,
    viewModel: SettingsViewModel
) {
    val isEnabled by viewModel.isSensorEnabledFlow(sensor.id).collectAsStateWithLifecycle(initialValue = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sensor.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "Hardware Log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                viewModel.setSensorEnabled(sensor.id, checked)
            }
        )
    }
}
