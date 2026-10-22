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
import com.cit.kaido.voxsight.ui.screens.practice.VoicePart
import com.cit.kaido.voxsight.ui.screens.practice.toSATBVoice
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val practiceViewModel: PracticeViewModel = viewModel()
    
    val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
    val isLoggedIn = prefs.getString("logged_in_username", null) != null
    val startDest = if (isLoggedIn) "upload" else "landing"

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (isLoggedIn) {
            val savedEmail = prefs.getString("logged_in_email", null)
            val savedUsername = prefs.getString("logged_in_username", null)
            withContext(Dispatchers.IO) {
                try {
                    val client = com.cit.kaido.voxsight.network.Supabase.client
                    val nowIso = currentUtcTimestamp()
                    if (!savedEmail.isNullOrBlank()) {
                        client.postgrest["User"].update({ set("last_login", nowIso) }) {
                            filter { eq("email", savedEmail) }
                        }
                    } else if (!savedUsername.isNullOrBlank()) {
                        client.postgrest["User"].update({ set("last_login", nowIso) }) {
                            filter { eq("username", savedUsername) }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore network/RLS errors on startup timestamp sync
                }
            }
        }
    }
    
    val navigateToReview by com.cit.kaido.voxsight.ui.screens.upload.UploadManager.navigateToReview.collectAsState()
    androidx.compose.runtime.LaunchedEffect(navigateToReview) {
        if (navigateToReview) {
            val xml = com.cit.kaido.voxsight.ui.screens.upload.UploadManager.pendingMusicXml ?: ""
            val title = com.cit.kaido.voxsight.ui.screens.upload.UploadManager.pendingScoreTitle ?: ""
            if (xml.isNotBlank()) {
                practiceViewModel.pendingMusicXml = xml
                practiceViewModel.pendingScoreTitle = title
                if (navController.currentDestination?.route != "review") {
                    com.cit.kaido.voxsight.util.PremiumManager.consumeUploadCredit(context)
                    navController.navigate("review")
                }
            }
            com.cit.kaido.voxsight.ui.screens.upload.UploadManager.navigateToReview.value = false
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        
        composable("landing") {
            LandingScreen(
                onLoginClicked = { navController.navigate("login") },
                onSignUpClicked = { navController.navigate("register") }
            )
        }

        composable("login") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

            val initiateFacebookLogin = FacebookLoginHandler(
                onSuccess = { email, name ->
                    coroutineScope.launch(Dispatchers.IO) {
                        handleSocialLoginSuccess(context, navController, email, name, "FACEBOOK")
                    }
                },
                onError = { error ->
                    android.widget.Toast.makeText(context, "Facebook Login Error: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            )

            LoginScreen(
                onBackClicked = { navController.popBackStack() },
                onGetStartedClicked = { 
                    navController.navigate("register") {
                        popUpTo("landing")
                    }
                },
                onGoogleSignInClicked = {
                    coroutineScope.launch(Dispatchers.IO) {
                        handleGoogleLoginOrRegister(context, navController)
                    }
                },
                onFacebookSignInClicked = initiateFacebookLogin,
                onSignInClicked = { email, password ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val trimmedEmail = email.trim()
                            val client = com.cit.kaido.voxsight.network.Supabase.client
                            
                            // Query the User table by email first, then verify the salted BCrypt hash
                            val users = client.postgrest["User"].select {
                                filter {
                                    eq("email", trimmedEmail)
                                }
                            }.decodeList<com.cit.kaido.voxsight.model.User>()
                            
                            val matchedUser = users.firstOrNull { candidate ->
                                val storedHash = candidate.passwordHash
                                if (storedHash.startsWith("\$2")) {
                                    // Salted BCrypt hash verification
                                    try {
                                        org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash)
                                    } catch (_: Exception) {
                                        false
                                    }
                                } else {
                                    // Legacy SHA-256 fallback (auto-upgrades to BCrypt on match)
                                    val md = java.security.MessageDigest.getInstance("SHA-256")
                                    val legacySha256 = md.digest(password.toByteArray(Charsets.UTF_8))
                                        .joinToString("") { "%02x".format(it) }
                                    val isLegacyMatch = storedHash == legacySha256
                                    if (isLegacyMatch) {
                                        try {
                                            val upgradedHash = org.mindrot.jbcrypt.BCrypt.hashpw(
                                                password,
                                                org.mindrot.jbcrypt.BCrypt.gensalt(12)
                                            )
                                            client.postgrest["User"].update(
                                                { set("password_hash", upgradedHash) }
                                            ) {
                                                filter { eq("email", candidate.email) }
                                            }
                                        } catch (_: Exception) {
                                            // Ignore if update fails; login still succeeds
                                        }
                                    }
                                    isLegacyMatch
                                }
                            }
                            
                            if (matchedUser != null) {
                                try {
                                    val nowIso = currentUtcTimestamp()
                                    client.postgrest["User"].update(
                                        { set("last_login", nowIso) }
                                    ) {
                                        filter { eq("email", matchedUser.email) }
                                    }
                                } catch (_: Exception) {
                                    // Non-fatal if update fails
                                }

                                val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putString("logged_in_username", matchedUser.username)
                                    putString("logged_in_email", matchedUser.email)
                                    if (!matchedUser.profilePicture.isNullOrBlank()) {
                                        putString("logged_in_profile_picture", matchedUser.profilePicture)
                                    } else {
                                        remove("logged_in_profile_picture")
                                    }
                                    apply()
                                }
                                com.cit.kaido.voxsight.util.PremiumManager.syncFromSupabase(context, matchedUser.username, matchedUser.email)

                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Welcome back, ${matchedUser.username}!", android.widget.Toast.LENGTH_SHORT).show()
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
            var serverEmailError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

            val initiateFacebookLogin = FacebookLoginHandler(
                onSuccess = { email, name ->
                    coroutineScope.launch(Dispatchers.IO) {
                        handleSocialLoginSuccess(context, navController, email, name, "FACEBOOK")
                    }
                },
                onError = { error ->
                    android.widget.Toast.makeText(context, "Facebook Login Error: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            )

            RegistrationScreen(
                onBackClicked = { navController.popBackStack() },
                onSignInClicked = { 
                    navController.navigate("login") {
                        popUpTo("landing")
                    }
                },
                onGoogleSignInClicked = {
                    coroutineScope.launch(Dispatchers.IO) {
                        handleGoogleLoginOrRegister(context, navController)
                    }
                },
                onFacebookSignInClicked = initiateFacebookLogin,
                serverEmailError = serverEmailError,
                onClearServerEmailError = { serverEmailError = null },
                onSignUpClicked = { name, email, password ->
                    serverEmailError = null
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // Salted BCrypt password hashing (cost factor 12)
                            val bcryptHash = org.mindrot.jbcrypt.BCrypt.hashpw(
                                password,
                                org.mindrot.jbcrypt.BCrypt.gensalt(12)
                            )

                            val newUser = com.cit.kaido.voxsight.model.User(
                                username = name,
                                email = email.trim(),
                                passwordHash = bcryptHash,
                                lastLogin = currentUtcTimestamp()
                            )
                            
                            val client = com.cit.kaido.voxsight.network.Supabase.client
                            client.postgrest["User"].insert(newUser)
                            
                            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("logged_in_username", name)
                                .putString("logged_in_email", email.trim())
                                .remove("logged_in_profile_picture")
                                .apply()
                            com.cit.kaido.voxsight.util.PremiumManager.initializeNewUserCredits(context, name, email.trim())
                            
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Registration Successful!", android.widget.Toast.LENGTH_SHORT).show()
                                navController.navigate("upload") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            }
                        } catch (e: Exception) {
                            val errorMsg = e.message.orEmpty()
                            val isDuplicateEmail = errorMsg.contains("User_email_key", ignoreCase = true) ||
                                errorMsg.contains("duplicate key", ignoreCase = true) ||
                                errorMsg.contains("already exists", ignoreCase = true) ||
                                errorMsg.contains("23505")

                            withContext(Dispatchers.Main) {
                                if (isDuplicateEmail) {
                                    serverEmailError = "Email already exists"
                                } else {
                                    android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            )
        }

        composable("upload") {
            val uploadCoroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            UploadScoreScreen(
                onNavigateToPractice = { score ->
                    practiceViewModel.setCurrentScore(score)
                    navController.navigate("select_mode")
                },
                onNavigateToReview = { musicXml, title ->
                    practiceViewModel.pendingMusicXml = musicXml
                    practiceViewModel.pendingScoreTitle = title
                    if (navController.currentDestination?.route != "review") {
                        uploadCoroutineScope.launch {
                            com.cit.kaido.voxsight.util.PremiumManager.consumeUploadCredit(context)
                        }
                        navController.navigate("review")
                    }
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToShop = {
                    navController.navigate("shop")
                }
            )
        }

        composable("profile") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
            val username = prefs.getString("logged_in_username", "Guest User") ?: "Guest User"
            val profilePicture = prefs.getString("logged_in_profile_picture", null)

            ProfileScreen(
                username = username,
                profilePictureBase64 = profilePicture,
                onBackClicked = { navController.popBackStack() },
                onLogoutClicked = {
                    prefs.edit()
                        .remove("logged_in_username")
                        .remove("logged_in_profile_picture")
                        .apply()
                    com.cit.kaido.voxsight.util.PremiumManager.clearActiveSessionPremium(context)
                    // Navigate back to landing and clear the backstack
                    navController.navigate("landing") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onMyMusicSheetsClicked = {
                    navController.navigate("my_music_sheets")
                },
                onUpgradeToPremiumClicked = {
                    navController.navigate("shop")
                },
                onSettingsClicked = {
                    navController.navigate("settings")
                },
                onEditProfileClicked = {
                    navController.navigate("edit_profile")
                }
            )
        }

        composable("my_music_sheets") {
            com.cit.kaido.voxsight.ui.screens.profile.MyMusicSheetsScreen(
                onBackClicked = { navController.popBackStack() },
                onUpgradeClicked = { navController.navigate("premium_upgrade") },
                onOpenScore = { score ->
                    practiceViewModel.setCurrentScore(score)
                    navController.navigate("select_mode")
                }
            )
        }

        composable("shop") {
            com.cit.kaido.voxsight.ui.screens.profile.ShopScreen(
                onBackClicked = { navController.popBackStack() },
                onPremiumClicked = { navController.navigate("premium_upgrade") }
            )
        }

        composable("edit_profile") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
            val username = prefs.getString("logged_in_username", "Guest User") ?: "Guest User"
            val email = prefs.getString("logged_in_email", null)
            val currentProfilePicture = prefs.getString("logged_in_profile_picture", null)

            com.cit.kaido.voxsight.ui.screens.profile.EditProfileScreen(
                currentName = username,
                currentProfilePictureBase64 = currentProfilePicture,
                onBackClicked = { navController.popBackStack() },
                onSaveClicked = { newName, newProfilePicture ->
                    val trimmedName = newName.trim()
                    if (trimmedName.isNotBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val client = com.cit.kaido.voxsight.network.Supabase.client
                                if (!email.isNullOrBlank()) {
                                    client.postgrest["User"].update(
                                        { set("username", trimmedName) }
                                    ) {
                                        filter { eq("email", email) }
                                    }
                                    if (!newProfilePicture.isNullOrBlank()) {
                                        client.postgrest["User"].update(
                                            { set("profile_picture", newProfilePicture) }
                                        ) {
                                            filter { eq("email", email) }
                                        }
                                    }
                                } else {
                                    client.postgrest["User"].update(
                                        { set("username", trimmedName) }
                                    ) {
                                        filter { eq("username", username) }
                                    }
                                    if (!newProfilePicture.isNullOrBlank()) {
                                        client.postgrest["User"].update(
                                            { set("profile_picture", newProfilePicture) }
                                        ) {
                                            filter { eq("username", trimmedName) }
                                        }
                                    }
                                }

                                prefs.edit().apply {
                                    putString("logged_in_username", trimmedName)
                                    if (!newProfilePicture.isNullOrBlank()) {
                                        putString("logged_in_profile_picture", newProfilePicture)
                                    }
                                    apply()
                                }

                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Profile updated!", android.widget.Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Failed to update profile: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("settings") {
            com.cit.kaido.voxsight.ui.screens.profile.SettingsScreen(
                onBackClicked = { navController.popBackStack() },
                onAboutClicked = { navController.navigate("about") },
                onChangeHeaderUIClicked = { navController.navigate("change_header_ui") },
                onTosClicked = { navController.navigate("tos") },
                onPrivacyClicked = { navController.navigate("privacy") }
            )
        }

        composable("tos") {
            com.cit.kaido.voxsight.ui.screens.profile.LegalScreen(
                title = "Terms of Service",
                onBackClicked = { navController.popBackStack() }
            )
        }

        composable("privacy") {
            com.cit.kaido.voxsight.ui.screens.profile.LegalScreen(
                title = "Privacy Policy",
                onBackClicked = { navController.popBackStack() }
            )
        }

        composable("change_header_ui") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
            val currentHeaderUI = prefs.getString("header_ui_preference", "default") ?: "default"

            com.cit.kaido.voxsight.ui.screens.profile.ChangeHeaderUIScreen(
                onBackClicked = { navController.popBackStack() },
                currentSelection = currentHeaderUI,
                onSelectionChanged = { newSelection ->
                    prefs.edit().putString("header_ui_preference", newSelection).apply()
                }
            )
        }

        composable("about") {
            com.cit.kaido.voxsight.ui.screens.profile.AboutScreen(
                onBackClicked = { navController.popBackStack() }
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
                            val savedMeta = withContext(Dispatchers.IO) {
                                com.cit.kaido.voxsight.storage.LocalScoreManager.saveScore(context, finalScore)
                            }
                            
                            // Redirect to Mode Gatekeeper with the saved score's assigned ID
                            val scoreWithId = finalScore.copy(id = savedMeta.id)
                            practiceViewModel.setCurrentScore(scoreWithId)
                            com.cit.kaido.voxsight.ui.screens.upload.UploadManager.clearReadyState()
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
            val currentScore by practiceViewModel.currentScore.collectAsState()
            var pendingVoicePart by androidx.compose.runtime.remember { 
                androidx.compose.runtime.mutableStateOf<com.cit.kaido.voxsight.ui.screens.practice.VoicePart?>(null) 
            }
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
                    practiceViewModel.setSelectedVoicePart(pendingVoicePart)
                    practiceViewModel.setMicrophoneEnabled(true)
                    navController.popBackStack()
                    navController.navigate("practice")
                } else {
                    android.widget.Toast.makeText(context, "Microphone permission required for Pitch Tracking", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            SelectPracticeModeModal(
                score = currentScore,
                scoreId = currentScore?.id,
                scoreTitle = currentScore?.title,
                onDismiss = {
                    navController.popBackStack()
                },
                onModeSelected = { micEnabled, chosenPart ->
                    practiceViewModel.setSelectedVoicePart(chosenPart)
                    if (micEnabled) {
                        if (permissionGranted) {
                            practiceViewModel.setMicrophoneEnabled(true)
                            navController.popBackStack()
                            navController.navigate("practice")
                        } else {
                            pendingVoicePart = chosenPart
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
            val context = androidx.compose.ui.platform.LocalContext.current
            val showPauseModal by practiceViewModel.showPauseModal.collectAsState()
            val isMicEnabled by practiceViewModel.isMicrophoneEnabled.collectAsState()
            val isMusicPlaying by practiceViewModel.isPlaying.collectAsState()
            val currentScore by practiceViewModel.currentScore.collectAsState()
            val selectedVoicePart by practiceViewModel.selectedVoicePart.collectAsState()
            val pitchAttempts by practiceViewModel.pitchAttempts.collectAsState()
            val pitchUiState by practiceViewModel.pitchUiState.collectAsState()

            androidx.compose.runtime.LaunchedEffect(isMicEnabled) {
                if (!isMicEnabled) {
                    practiceViewModel.endPitchSession()
                }
            }

            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    practiceViewModel.endPitchSession()
                }
            }

            Module2PracticeScreen(
                score = currentScore,
                initialSelectedPart = selectedVoicePart,
                isMicEnabled = isMicEnabled,
                pitchAttempts = pitchAttempts,
                pitchUiState = pitchUiState,
                externalPlayingState = isMusicPlaying,
                onMusicPlay = {
                    practiceViewModel.setPlaying(true)
                    if (isMicEnabled) {
                        val recordingsDir = com.cit.kaido.voxsight.storage.ScoreRecordingManager.getRecordingsDir(
                            context,
                            scoreId = currentScore?.id,
                            scoreTitle = currentScore?.title
                        )
                        practiceViewModel.startPitchSession(recordingsDir)
                    }
                },
                onMusicPause = {
                    practiceViewModel.setPlaying(false)
                    if (isMicEnabled) {
                        practiceViewModel.pausePitchSession()
                    }
                },
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
                    practiceViewModel.setPlaying(false)
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
                        practiceViewModel.setPlaying(false)
                        practiceViewModel.endPitchSession()
                        navController.navigate("summary") {
                            popUpTo("upload") { inclusive = false } // clear backstack up to upload
                        }
                    }
                )
            }
        }

        composable("summary") {
            val context = androidx.compose.ui.platform.LocalContext.current
            // Provide the full summary from the view model
            val summary = practiceViewModel.getSessionSummary()
            val currentScore by practiceViewModel.currentScore.collectAsState()
            val selectedVoicePart by practiceViewModel.selectedVoicePart.collectAsState()
            val pitchAttempts by practiceViewModel.pitchAttempts.collectAsState()

            val activeVoice = selectedVoicePart?.toSATBVoice()
                ?: pitchAttempts.firstOrNull { it.satbVoice != com.cit.kaido.voxsight.model.SATBVoice.UNKNOWN }?.satbVoice
                ?: com.cit.kaido.voxsight.model.SATBVoice.SOPRANO

            com.cit.kaido.voxsight.ui.screens.practice.PracticeSummaryScreen(
                summary = summary,
                score = currentScore,
                selectedVoice = activeVoice,
                pitchAttempts = pitchAttempts,
                onBackToLibrary = {
                    navController.popBackStack("upload", inclusive = false)
                },
                onRepeatPractice = {
                    practiceViewModel.setPlaying(false)
                    navController.navigate("practice") {
                        popUpTo("practice") { inclusive = true }
                    }
                }
            )
        }
    }
}

private suspend fun handleGoogleLoginOrRegister(
    context: android.content.Context,
    navController: androidx.navigation.NavController
) {
    try {
        val credentialManager = androidx.credentials.CredentialManager.create(context)
        
        
        val webClientId = "780600362616-v3u37mq8kgo8qksgep3b42gkvsf764om.apps.googleusercontent.com"
        
        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
            
        val request = androidx.credentials.GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
            
        val result = credentialManager.getCredential(
            request = request,
            context = context
        )
        
        val credential = result.credential
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            
            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
            
            val email = googleIdTokenCredential.id
            val name = googleIdTokenCredential.displayName ?: "Google User"
            
            handleSocialLoginSuccess(context, navController, email, name, "GOOGLE")
        }
    } catch (e: Exception) {
        if (e is androidx.credentials.exceptions.GetCredentialCancellationException) {
            // Cancelled
        } else {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Google Sign In Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun currentUtcTimestamp(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date())
}

private suspend fun handleSocialLoginSuccess(
    context: android.content.Context,
    navController: androidx.navigation.NavController,
    email: String,
    name: String,
    provider: String
) {
    val client = com.cit.kaido.voxsight.network.Supabase.client
    val nowIso = currentUtcTimestamp()
            
    val users = client.postgrest["User"].select {
        filter { eq("email", email) }
    }.decodeList<com.cit.kaido.voxsight.model.User>()
    
    if (users.isEmpty()) {
        val newUser = com.cit.kaido.voxsight.model.User(
            username = name,
            email = email,
            passwordHash = "${provider}_OAUTH",
            lastLogin = nowIso
        )
        client.postgrest["User"].insert(newUser)
        com.cit.kaido.voxsight.util.PremiumManager.initializeNewUserCredits(context, name, email)
    } else {
        try {
            client.postgrest["User"].update(
                { set("last_login", nowIso) }
            ) {
                filter { eq("email", email) }
            }
        } catch (_: Exception) {
            // Non-fatal if update fails
        }
    }
    
    val resolvedUsername = users.firstOrNull()?.username?.takeIf { it.isNotBlank() } ?: name
    val resolvedProfilePicture = users.firstOrNull()?.profilePicture

    val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().apply {
        putString("logged_in_username", resolvedUsername)
        putString("logged_in_email", email)
        if (!resolvedProfilePicture.isNullOrBlank()) {
            putString("logged_in_profile_picture", resolvedProfilePicture)
        } else {
            remove("logged_in_profile_picture")
        }
        apply()
    }
    com.cit.kaido.voxsight.util.PremiumManager.syncFromSupabase(context, resolvedUsername, email)

    withContext(Dispatchers.Main) {
        android.widget.Toast.makeText(context, "Welcome, $resolvedUsername!", android.widget.Toast.LENGTH_SHORT).show()
        navController.navigate("upload") {
            popUpTo("landing") { inclusive = true }
        }
    }
}

@Composable
fun FacebookLoginHandler(
    onSuccess: (email: String, name: String) -> Unit,
    onError: (Exception) -> Unit
): () -> Unit {
    val callbackManager = androidx.compose.runtime.remember { com.facebook.CallbackManager.Factory.create() }
    val loginManager = com.facebook.login.LoginManager.getInstance()
    
    androidx.compose.runtime.DisposableEffect(Unit) {
        loginManager.registerCallback(callbackManager, object : com.facebook.FacebookCallback<com.facebook.login.LoginResult> {
            override fun onSuccess(result: com.facebook.login.LoginResult) {
                val request = com.facebook.GraphRequest.newMeRequest(result.accessToken) { obj, response ->
                    try {
                        val email = obj?.getString("email") ?: ""
                        val name = obj?.getString("name") ?: "Facebook User"
                        onSuccess(email, name)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
                val parameters = android.os.Bundle()
                parameters.putString("fields", "id,name,email")
                request.parameters = parameters
                request.executeAsync()
            }
            override fun onCancel() {}
            override fun onError(error: com.facebook.FacebookException) {
                onError(error)
            }
        })
        onDispose {
            loginManager.unregisterCallback(callbackManager)
        }
    }
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = loginManager.createLogInActivityResultContract(callbackManager, null)
    ) { }
    
    return {
        launcher.launch(listOf("email", "public_profile"))
    }
}
