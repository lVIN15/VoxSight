package com.cit.kaido.voxsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cit.kaido.voxsight.ui.navigation.AppNavigation
import com.cit.kaido.voxsight.ui.theme.VoxSightTheme

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cit.kaido.voxsight.ui.update.AppUpdateManager
import com.cit.kaido.voxsight.ui.update.ForceUpdateDialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initiate background check for mandatory version requirements
        AppUpdateManager.checkForUpdates(this)

        setContent {
            VoxSightTheme {
                val isUpdateRequired by AppUpdateManager.isUpdateRequired.collectAsState()
                val updateInfo by AppUpdateManager.updateInfo.collectAsState()

                Box(
                    modifier = Modifier.safeDrawingPadding()
                ) {
                    AppNavigation()

                    // Highest priority modal: blocks all interactions if version is deprecated
                    if (isUpdateRequired) {
                        ForceUpdateDialog(updateInfo = updateInfo)
                    }
                }
            }
        }
    }
}
