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
import com.cit.kaido.voxsight.ui.screens.profile.ProfileScreen
import com.cit.kaido.voxsight.ui.screens.profile.PremiumUpgradeScreen
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
import io.github.jan.supabase.postgrest.postgrest

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
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            LoginScreen(
                onBackClicked = { navController.popBackStack() },
                onGetStartedClicked = { 
                    navController.navigate("register") {
                        popUpTo("landing")
                    }
                },
                onSignInClicked = { email, password ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Hash the password the same way we did in Registration
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            val hashBytes = md.digest(password.toByteArray(Charsets.UTF_8))
                            val hashString = hashBytes.joinToString("") { "%02x".format(it) }
                            
                            val client = com.cit.kaido.voxsight.network.Supabase.client
                            
                            // Query the custom User table
                            val users = client.postgrest["User"].select {
                                filter {
                                    eq("email", email)
                                    eq("password_hash", hashString)
                                }
                            }.decodeList<com.cit.kaido.voxsight.model.User>()
                            
                            if (users.isNotEmpty()) {
                                val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
                                prefs.edit().putString("logged_in_username", users.first().username).apply()

                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Welcome back, ${users.first().username}!", android.widget.Toast.LENGTH_SHORT).show()
                                    navController.navigate("upload") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Invalid email or password", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Login Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }

        composable("register") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            RegistrationScreen(
                onBackClicked = { navController.popBackStack() },
                onSignInClicked = { 
                    navController.navigate("login") {
                        popUpTo("landing")
                    }
                },
                onSignUpClicked = { name, email, password ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Simple SHA-256 hash for demonstration (NOT recommended for production)
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            val hashBytes = md.digest(password.toByteArray(Charsets.UTF_8))
                            val hashString = hashBytes.joinToString("") { "%02x".format(it) }

                            val newUser = com.cit.kaido.voxsight.model.User(
                                username = name,
                                email = email,
                                passwordHash = hashString
                            )
                            
                            val client = com.cit.kaido.voxsight.network.Supabase.client
                            client.postgrest["User"].insert(newUser)
                            
                            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("logged_in_username", name).apply()
                            
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Registration Successful!", android.widget.Toast.LENGTH_SHORT).show()
                                navController.navigate("upload") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
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
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                }
            )
        }

        composable("profile") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
            val username = prefs.getString("logged_in_username", "Guest User") ?: "Guest User"

            ProfileScreen(
                username = username,
                onBackClicked = { navController.popBackStack() },
                onLogoutClicked = {
                    prefs.edit().remove("logged_in_username").apply()
                    // Navigate back to landing and clear the backstack
                    navController.navigate("landing") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onUpgradeToPremiumClicked = {
                    navController.navigate("premium_upgrade")
                }
            )
        }

        composable("premium_upgrade") {
            PremiumUpgradeScreen(
                onCancelClicked = { navController.popBackStack() }
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
                                metadataJson = parsedScore.metadataJson
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
                            noteName = event.pitchName,
                            measureNumber = event.measureNumber
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
                                noteName = event.pitchName,
                                measureNumber = event.measureNumber
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
                },
                onRepeatPractice = {
                    practiceViewModel.startPitchSession()
                    navController.navigate("practice") {
                        popUpTo("practice") { inclusive = true }
                    }
                }
            )
        }
    }
}
