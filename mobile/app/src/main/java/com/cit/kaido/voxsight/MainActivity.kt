package com.cit.kaido.voxsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cit.kaido.voxsight.ui.navigation.AppNavigation
import com.cit.kaido.voxsight.ui.theme.VoxSightTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cit.kaido.voxsight.ui.update.AppUpdateManager
import com.cit.kaido.voxsight.ui.update.ForceUpdateDialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)
        
        // Initiate background check for mandatory version requirements
        AppUpdateManager.checkForUpdates(this)

        setContent {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionState = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permissionState != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            
            VoxSightTheme {
                val isUpdateRequired by AppUpdateManager.isUpdateRequired.collectAsState()
                val updateInfo by AppUpdateManager.updateInfo.collectAsState()

                    AppNavigation()

                    // Highest priority modal: blocks all interactions if version is deprecated
                    if (isUpdateRequired) {
                        ForceUpdateDialog(updateInfo = updateInfo)
                    }
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.action == "ACTION_REVIEW_SCORE") {
            com.cit.kaido.voxsight.ui.screens.upload.UploadManager.navigateToReview.value = true
        }
    }
}
