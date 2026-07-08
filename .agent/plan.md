# Project Plan

Audio: a 24/7 audio capture app with chunking to WAV, with a foreground service, UI with Compose, Storage in Room, DataStore for preferences, on-device transcription MVP, error handling.

## Project Brief

# Project Brief: Record (Audio)

A robust, professional-grade Android application designed for continuous, 24/7 audio monitoring. The app records audio in the background, automatically splitting data into manageable, timestamped chunks while maintaining a searchable metadata index.

## Features
*   Persistent Foreground Recording: high-priority foreground service with sticky notification; background execution.
*   Automatic Audio Chunking: WAV/PCM at 16 kHz mono; chunk into configurable intervals (default 5 minutes).
*   Metadata Management: index chunks with start/end timestamps and durations; on-device transcription optional MVP.
*   On-Device Transcription MVP: STT engine on-device; stored with metadata.
*   Dynamic Storage Management: user-configurable limits; cleanup of old recordings.

## Tech Stack
*   Kotlin
*   Jetpack Compose (Material 3)
*   Coroutines & Flow
*   Foreground Service with proper permissions and wake lock
*   AudioRecord (or MediaRecorder) for capture
*   Room for metadata; DataStore for preferences
*   DI: simple manual DI or Hilt (MVP)
*   KSP for code generation when needed

## UI & Navigation
*   Dashboard: live status, elapsed time, chunk info, start/stop FAB, recent chunks
*   Recordings List: search, list with timestamps, durations; swipe-to-delete
*   Settings: chunk interval, max storage, auto-cleanup, transcription toggle
*   Bottom navigation: Dashboard / Recordings / Settings
*   Edge-to-edge display with insets handling

## Acceptance Criteria
*   M3 color scheme implemented (light/dark) with vibrant palette
*   Dashboard shows status, elapsed time, FAB, and last chunk with transcription
*   List screen shows chunks with timestamps and durations, plus search
*   Settings adjust chunk interval, storage, auto-cleanup, transcription
*   Foreground service works 24/7, can restart after termination
*   Metadata stored in Room; WAV files saved in local storage; DataStore stores preferences
*   Optional on-device transcription MVP notes
*   Build passes and app compiles Debug variant

## Milestones
1) Implement Theme (Color.kt, Theme.kt)
2) Create VM skeletons (RecordingViewModel, RecordingsListViewModel, SettingsViewModel)
3) Wire with RecordApp for DAO, Repo, and DataStore
4) Create a minimal UI skeleton (Dashboard) to validate wiring
5) Implement ForegroundService integration and audio chunking core
6) Implement UI screens (Dashboard, Recordings, Settings)
7) Add transcription MVP hooking
8) Quality gate and refinement loop

**Blockers / Dependencies Needed**
- Access to actual RecordApp field names (e.g., audioChunkDao, userPreferencesRepository)
- Ensure permissions manifest includes RECORD_AUDIO, FOREGROUND_SERVICE, POST_NOTIFICATIONS, etc.
- Resource IDs for icons if we want to align with the design.

Would you like me to APPROVE this plan and start implementing Task 1: Color.kt and Theme.kt and the 3 ViewModels now?

## Implementation Steps
**Total Duration:** 7m 48s

### Task_1_CoreInfrastructure: Set up the core data layer and recording infrastructure: (1) Define Room entity (AudioChunk with id, filePath, startTime, endTime, duration, transcription) and DAO with Flow-based queries, plus the Room database class. (2) Create a DataStore-based preferences repository for user settings (chunk interval, max storage). (3) Implement the AudioRecorder engine using AudioRecord API that writes PCM data to WAV files, with automatic chunking at configurable intervals. (4) Build the ForegroundService (AudioRecordingService) with foregroundServiceType=microphone, sticky notification, proper permission declarations in AndroidManifest (RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS), and wakelock. Wire the AudioRecorder into the service so it starts/stops recording via intents and persists each completed chunk's metadata to Room. (5) Create an Application class with manual DI (or simple singleton container) providing database, repository, and datastore instances.
- **Status:** COMPLETED
- **Updates:** Task 1 completed successfully. All core infrastructure implemented and build passes.
- **Acceptance Criteria:**
  - Room database with AudioChunk entity and DAO compiles and provides Flow-based queries
  - DataStore preferences repository stores and retrieves chunk interval and max storage settings
  - AudioRecorder writes valid WAV files and splits at configurable intervals
  - ForegroundService starts/stops recording via intents with sticky notification and microphone foreground service type
  - Manifest declares RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS permissions
  - Manual DI container provides singleton instances of DB, repos, and DataStore
  - Project builds successfully with ./gradlew :app:assembleDebug
- **Duration:** 7m 48s

### Task_2_UIScreens: Build the full Jetpack Compose UI with Material 3 theming and edge-to-edge display: (1) Create a vibrant M3 color scheme (light/dark) using Material Color Utilities with a recording-themed palette (deep red/coral primary). Update Theme.kt, Color.kt. (2) Implement the main Recording Dashboard screen showing: live recording status with animated indicator, elapsed time, current chunk info, start/stop toggle FAB, and a list of recent recordings from Room (via ViewModel + Flow). (3) Implement a Recordings List screen with search bar, showing all audio chunks with timestamps and durations, with swipe-to-delete. (4) Implement a Settings screen with options for chunk interval (slider/dropdown), max storage limit, and auto-cleanup toggle, backed by DataStore. (5) Set up Navigation (NavHost) between Dashboard, Recordings, and Settings using bottom navigation bar. (6) Wire ViewModels: RecordingViewModel (controls service start/stop, observes recording state), RecordingsListViewModel (queries Room, handles search/delete), SettingsViewModel (reads/writes DataStore). (7) Enable edge-to-edge display with proper WindowInsets handling. Add @Preview annotations for all screens.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - M3 color scheme with light/dark themes applied, vibrant recording-themed palette
  - Dashboard screen shows recording status, elapsed time, start/stop FAB
  - Recordings list screen displays all chunks with timestamps, durations, and search functionality
  - Settings screen allows configuring chunk interval, max storage, auto-cleanup
  - Bottom navigation between Dashboard, Recordings, and Settings works correctly
  - ViewModels properly observe Room Flows and DataStore preferences
  - Edge-to-edge display implemented with correct insets handling
  - All screens have @Preview annotations
  - The implemented UI must match the design provided in C:/Users/kamran/AndroidStudioProjects/record/input_images/ui_mockup.png
  - Project builds successfully with ./gradlew :app:assembleDebug
- **StartTime:** 2026-03-23 19:50:43 CET

### Task_3_TranscriptionAndStorageMgmt: Implement on-device transcription and dynamic storage management: (1) Create a TranscriptionManager that uses Android's SpeechRecognizer or MediaRecognizer API to transcribe completed WAV chunks on-device. Run transcription in a coroutine after each chunk completes, updating the Room entity with the transcribed text. Handle cases where speech recognition is unavailable gracefully. (2) Implement StorageManager that monitors total recording storage usage, compares against user-configured limits from DataStore, and automatically deletes oldest recordings (both files and Room entries) when limits are exceeded. Trigger cleanup after each new chunk is saved. (3) Wire transcription results into the Recordings List UI so transcribed text appears under each chunk and is searchable. (4) Add audio playback capability using MediaPlayer/ExoPlayer so users can tap a recording to play it back with basic controls (play/pause/seek).
- **Status:** PENDING
- **Acceptance Criteria:**
  - On-device transcription runs after each chunk completes and stores text in Room
  - Transcription handles unavailable speech recognition gracefully with fallback message
  - StorageManager auto-deletes oldest recordings when storage limit is exceeded
  - Transcribed text is visible in Recordings List and searchable
  - Audio playback works with play/pause controls when tapping a recording
  - Project builds successfully with ./gradlew :app:assembleDebug

### Task_4_AppIconAndPolish: Create adaptive app icon and final polish: (1) Design an adaptive app icon with a microphone/waveform motif matching the recording theme (red/coral foreground on themed background) by updating ic_launcher_foreground.xml and ic_launcher_background.xml. (2) Update strings.xml with proper app name 'Record'. (3) Add runtime permission request flow on first launch for RECORD_AUDIO and POST_NOTIFICATIONS using Accompanist Permissions. (4) Handle edge cases: service restart on device reboot (optional BOOT_COMPLETED), notification channel creation, proper service lifecycle on app kill.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Adaptive app icon shows microphone/waveform design with themed colors
  - Runtime permissions requested gracefully on first launch
  - Notification channel created properly for foreground service
  - Service lifecycle handles app kill and restarts correctly
  - Project builds successfully with ./gradlew :app:assembleDebug

### Task_5_RunAndVerify: Final build, run, and verification by critic_agent: (1) Run ./gradlew :app:assembleDebug to confirm clean build. (2) Run ./gradlew :app:testDebugUnitTest to ensure all existing tests pass. (3) critic_agent must verify: application stability (no crashes on launch, navigation, start/stop recording), confirm alignment with all user requirements (persistent foreground recording, automatic chunking, metadata management, on-device transcription, dynamic storage management), verify M3 theming and edge-to-edge display, check UI matches the mockup design, and report any critical UI issues.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Build passes with ./gradlew :app:assembleDebug
  - All existing tests pass with ./gradlew :app:testDebugUnitTest
  - App does not crash on launch, navigation between screens, or start/stop recording
  - Foreground service runs persistently in background with notification
  - Audio chunks are created at configured intervals with correct metadata in Room
  - On-device transcription produces searchable text for recordings
  - Storage management auto-cleans old recordings when limit exceeded
  - M3 theming with light/dark mode and edge-to-edge display working correctly
  - Make sure all existing tests pass

