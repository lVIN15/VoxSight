package com.cit.kaido.voxsight.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.cit.kaido.voxsight.ui.screens.auth.LandingScreen
import com.cit.kaido.voxsight.ui.screens.auth.LoginScreen
import com.cit.kaido.voxsight.ui.screens.auth.RegistrationScreen
import com.cit.kaido.voxsight.ui.screens.practice.Module2PracticeScreen
import com.cit.kaido.voxsight.ui.screens.practice.PauseMenuModal
import com.cit.kaido.voxsight.ui.screens.practice.PracticeSummaryScreen
import com.cit.kaido.voxsight.ui.screens.practice.SelectPracticeModeModal
import com.cit.kaido.voxsight.ui.screens.upload.UploadScoreScreen
import com.cit.kaido.voxsight.ui.viewmodel.PracticeViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val practiceViewModel: PracticeViewModel = viewModel()

    NavHost(navController = navController, startDestination = "landing") {
        
        composable("landing") {
            LandingScreen(
                onLoginClicked = { navController.navigate("login") },
                onSignUpClicked = { navController.navigate("register") }
            )
        }

        composable("login") {
            LoginScreen(
                onBackClicked = { navController.popBackStack() },
                onGetStartedClicked = { 
                    navController.navigate("register") {
                        popUpTo("landing")
                    }
                },
                onSignInClicked = { _, _ ->
                    // Bypass to main app
                    navController.navigate("upload") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            )
        }

        composable("register") {
            RegistrationScreen(
                onBackClicked = { navController.popBackStack() },
                onSignInClicked = { 
                    navController.navigate("login") {
                        popUpTo("landing")
                    }
                },
                onSignUpClicked = { _, _, _ ->
                    // Bypass to main app
                    navController.navigate("upload") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            )
        }

        composable("upload") {
            UploadScoreScreen(
                onNavigateToPractice = { score ->
                    practiceViewModel.setCurrentScore(score)
                    navController.navigate("select_mode")
                }
            )
        }

        dialog("select_mode") {
            val context = androidx.compose.ui.platform.LocalContext.current
            var permissionGranted by androidx.compose.runtime.remember { 
                androidx.compose.runtime.mutableStateOf(
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) 
            }
            
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                permissionGranted = isGranted
                if (isGranted) {
                    practiceViewModel.setMicrophoneEnabled(true)
                    navController.popBackStack()
                    navController.navigate("practice")
                } else {
                    android.widget.Toast.makeText(context, "Microphone permission required for Pitch Tracking", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            SelectPracticeModeModal(
                onDismiss = {
                    navController.popBackStack()
                },
                onModeSelected = { micEnabled ->
                    if (micEnabled) {
                        if (permissionGranted) {
                            practiceViewModel.setMicrophoneEnabled(true)
                            navController.popBackStack()
                            navController.navigate("practice")
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        practiceViewModel.setMicrophoneEnabled(false)
                        navController.popBackStack()
                        navController.navigate("practice")
                    }
                }
            )
        }

        composable("practice") {
            val showPauseModal by practiceViewModel.showPauseModal.collectAsState()
            val isMicEnabled by practiceViewModel.isMicrophoneEnabled.collectAsState()
            val currentScore by practiceViewModel.currentScore.collectAsState()
            val pitchAttempts by practiceViewModel.pitchAttempts.collectAsState()
            val pitchUiState by practiceViewModel.pitchUiState.collectAsState()

            androidx.compose.runtime.LaunchedEffect(isMicEnabled) {
                if (isMicEnabled) {
                    practiceViewModel.startPitchSession()
                } else {
                    practiceViewModel.endPitchSession()
                }
            }

            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    practiceViewModel.endPitchSession()
                }
            }

            // In a real implementation, we would intercept the native back press or a pause button.
            // For now, Module2PracticeScreen would ideally have an onPause callback.
            // But if it doesn't, the user can trigger it through the screen's UI.
            Module2PracticeScreen(
                score = currentScore,
                isMicEnabled = isMicEnabled,
                pitchAttempts = pitchAttempts,
                pitchUiState = pitchUiState,
                onPauseClicked = {
                    if (isMicEnabled) {
                        practiceViewModel.setShowPauseModal(true)
                    }
                },
                onBackClicked = {
                    navController.popBackStack()
                },
                onNoteOn = { event ->
                    if (isMicEnabled) {
                        val targetHz = com.cit.kaido.voxsight.pitch.PitchComparator.calculateTargetFrequency(event.pitchName)
                        val target = com.cit.kaido.voxsight.ui.viewmodel.PracticeViewModel.ActivePitchTarget(
                            eventId = event.eventId,
                            targetHz = targetHz,
                            satbVoice = event.satbEnum,
                            noteName = event.pitchName
                        )
                        practiceViewModel.setPitchTargets(listOf(target))
                    }
                },
                onWaitPitch = { events ->
                    if (isMicEnabled && events.isNotEmpty()) {
                        val targets = events.map { event ->
                            val targetHz = com.cit.kaido.voxsight.pitch.PitchComparator.calculateTargetFrequency(event.pitchName)
                            com.cit.kaido.voxsight.ui.viewmodel.PracticeViewModel.ActivePitchTarget(
                                eventId = event.eventId,
                                targetHz = targetHz,
                                satbVoice = event.satbEnum,
                                noteName = event.pitchName
                            )
                        }
                        practiceViewModel.setPitchTargets(targets)
                        practiceViewModel.waitForPitchConfirmation(events.first().eventId)
                    }
                },
                onPlaybackComplete = {
                    if (isMicEnabled) {
                        practiceViewModel.endPitchSession()
                        navController.navigate("summary") {
                            popUpTo("upload") { inclusive = false }
                        }
                    }
                }
            )

            if (showPauseModal) {
                PauseMenuModal(
                    onResume = {
                        practiceViewModel.setShowPauseModal(false)
                        practiceViewModel.setPlaying(true)
                    },
                    onEndSession = {
                        practiceViewModel.setShowPauseModal(false)
                        navController.navigate("summary") {
                            popUpTo("upload") { inclusive = false } // clear backstack up to upload
                        }
                    }
                )
            }
        }

        composable("summary") {
            // Provide the full summary from the view model
            val summary = practiceViewModel.getSessionSummary()
            
            com.cit.kaido.voxsight.ui.screens.practice.PracticeSummaryScreen(
                summary = summary,
                onBackToLibrary = {
                    navController.popBackStack("upload", inclusive = false)
                }
            )
        }
    }
}
