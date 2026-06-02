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
import com.cit.kaido.voxsight.ui.screens.upload.ScoreReviewScreen
import com.cit.kaido.voxsight.ui.screens.upload.regenerateEventsJsonFromScore
import com.cit.kaido.voxsight.ui.viewmodel.PracticeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

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
                },
                onNavigateToReview = { musicXml, title ->
                    practiceViewModel.pendingMusicXml = musicXml
                    practiceViewModel.pendingScoreTitle = title
                    navController.navigate("review")
                }
            )
        }

        composable("review") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            
            ScoreReviewScreen(
                musicXml = practiceViewModel.pendingMusicXml,
                onConfirm = { modifiedXml ->
                    coroutineScope.launch {
                        // Save XML to a temporary file
                        val xmlTempFile = java.io.File(context.cacheDir, "edited_${System.currentTimeMillis()}.xml")
                        withContext(Dispatchers.IO) {
                            java.io.FileOutputStream(xmlTempFile).use { output ->
                                output.write(modifiedXml.toByteArray(Charsets.UTF_8))
                            }
                        }
                        
                        // Parse local XML into score structure
                        val parsedScore = com.cit.kaido.voxsight.ui.screens.practice.parseMusicXmlScore(
                            context,
                            android.net.Uri.fromFile(xmlTempFile),
                            practiceViewModel.pendingScoreTitle
                        )
                        
                        if (parsedScore != null) {
                            // Generate playback events locally
                            val eventsJson = withContext(Dispatchers.Default) {
                                regenerateEventsJsonFromScore(parsedScore)
                            }
                            
                            val finalScore = parsedScore.copy(
                                eventsJson = eventsJson,
                                metadataJson = "{}"
                            )
                            
                            // Save to cache
                            withContext(Dispatchers.IO) {
                                com.cit.kaido.voxsight.storage.LocalScoreManager.saveScore(context, finalScore)
                            }
                            
                            // Redirect to Mode Gatekeeper
                            practiceViewModel.setCurrentScore(finalScore)
                            navController.navigate("select_mode") {
                                popUpTo("upload") { inclusive = false }
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Failed to parse modified score", android.widget.Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
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
                onPauseClicked = {
                    if (isMicEnabled) {
                        practiceViewModel.setShowPauseModal(true)
                    }
                },
                onBackClicked = {
                    navController.popBackStack()
                },
                onNoteOn = { event ->
                    // Just a callback, no longer need to set target here as onWaitPitch handles it
                },
                onWaitPitch = { events ->
                    if (isMicEnabled && events.isNotEmpty()) {
                        val targets = events.map { event ->
                            val targetHz = com.cit.kaido.voxsight.pitch.PitchComparator.calculateTargetFrequency(event.pitchName)
                            com.cit.kaido.voxsight.ui.viewmodel.PracticeViewModel.ActivePitchTarget(
                                eventId = event.eventId,
                                targetHz = targetHz,
                                satbVoice = event.satbEnum
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
            // Provide the accuracy from the view model
            val accuracy = practiceViewModel.calculateAccuracy()
            
            PracticeSummaryScreen(
                accuracy = accuracy,
                onBackToLibrary = {
                    navController.popBackStack("upload", inclusive = false)
                }
            )
        }
    }
}
