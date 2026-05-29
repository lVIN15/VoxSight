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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxSightTheme {
                Box(
                    modifier = Modifier.safeDrawingPadding()
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
