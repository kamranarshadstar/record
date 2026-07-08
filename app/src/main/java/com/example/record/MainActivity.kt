package com.example.record

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.record.ui.screens.DashboardScreen
import com.example.record.ui.screens.RecordingsListScreen
import com.example.record.ui.screens.SettingsScreen
import com.example.record.ui.screens.TranscriptScreen
import com.example.record.ui.theme.RecordTheme
import com.example.record.ui.viewmodel.RecordingViewModel
import com.example.record.ui.viewmodel.RecordingsListViewModel
import com.example.record.ui.viewmodel.SettingsViewModel
import com.example.record.ui.viewmodel.TranscriptViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Recordings : Screen("recordings", "Recordings", Icons.AutoMirrored.Filled.List)
    object Transcript : Screen("transcript", "Transcript", Icons.AutoMirrored.Filled.Note)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecordTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Dashboard, Screen.Recordings, Screen.Transcript, Screen.Settings)

    // Permissions
    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            // Handle denied permissions (e.g., show a dialog)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(screen.icon, contentDescription = screen.label)
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                val recordingViewModel: RecordingViewModel = viewModel()
                DashboardScreen(recordingViewModel, navController)
            }
            composable(Screen.Recordings.route) {
                val viewModel: RecordingsListViewModel = viewModel()
                RecordingsListScreen(viewModel)
            }
            composable(Screen.Transcript.route) {
                val viewModel: TranscriptViewModel = viewModel()
                TranscriptScreen(viewModel)
            }
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel()
                SettingsScreen(viewModel)
            }
        }
    }
}