package com.cit.kaido.voxsight.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            SelectPracticeModeModal(
                onDismiss = {
                    navController.popBackStack()
                },
                onModeSelected = { micEnabled ->
                    practiceViewModel.setMicrophoneEnabled(micEnabled)
                    // Pop the modal and navigate to practice
                    navController.popBackStack()
                    navController.navigate("practice")
                }
            )
        }

        composable("practice") {
            val showPauseModal by practiceViewModel.showPauseModal.collectAsState()
            val isMicEnabled by practiceViewModel.isMicrophoneEnabled.collectAsState()
            val currentScore by practiceViewModel.currentScore.collectAsState()

            // In a real implementation, we would intercept the native back press or a pause button.
            // For now, Module2PracticeScreen would ideally have an onPause callback.
            // But if it doesn't, the user can trigger it through the screen's UI.
            Module2PracticeScreen(
                score = currentScore,
                isMicEnabled = isMicEnabled,
                onPauseClicked = {
                    if (isMicEnabled) {
                        practiceViewModel.setShowPauseModal(true)
                    }
                },
                onBackClicked = {
                    navController.popBackStack()
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
