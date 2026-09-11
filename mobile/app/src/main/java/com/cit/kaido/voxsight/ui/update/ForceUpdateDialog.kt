package com.cit.kaido.voxsight.ui.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cit.kaido.voxsight.BuildConfig
import com.cit.kaido.voxsight.network.AppVersionResponse
import com.cit.kaido.voxsight.ui.theme.*

@Composable
fun ForceUpdateDialog(
    updateInfo: AppVersionResponse?,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Intercept back button to prevent dismissing; exit the app instead
    BackHandler {
        activity?.finishAffinity()
    }

    val currentVersion = BuildConfig.VERSION_NAME
    val requiredVersion = updateInfo?.minVersionName ?: "1.2"
    val downloadUrl = updateInfo?.downloadUrl?.ifEmpty {
        "https://github.com/lVIN15/VoxSight/releases/download/v1.2/voxsight-v1.2.apk"
    } ?: "https://github.com/lVIN15/VoxSight/releases/download/v1.2/voxsight-v1.2.apk"

    // If installed version already meets or exceeds required version, do not render dialog
    if (currentVersion.equals(requiredVersion, ignoreCase = true) || BuildConfig.VERSION_CODE >= (updateInfo?.minVersionCode ?: 1)) {
        return
    }

    Dialog(
        onDismissRequest = { /* Non-dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                VoxPurpleAccent.copy(alpha = 0.8f),
                                VoxCardStroke
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1E1B26),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Update Icon with pulsing halo background
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        VoxPurpleAccent.copy(alpha = 0.35f),
                                        VoxPurplePrimary.copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.5.dp, VoxPurpleAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = "Update Required",
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFFE9D5FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Update Required",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "A mandatory update of VoxSight is available. To ensure accurate sheet music conversion, voice isolation, and access new guided features, please update the app to continue.",
                        fontSize = 13.5.sp,
                        color = Color(0xFFD1D5DB),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Version comparison badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2B2538),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C3E61))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Installed: v$currentVersion",
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "➔",
                                fontSize = 12.sp,
                                color = VoxPurpleAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Required: v$requiredVersion",
                                fontSize = 12.sp,
                                color = Color(0xFF34D399), // Emerald
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    // Download & Update Button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoxPurpleAccent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Text(
                                text = "Download & Update Now",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Exit Application Option
                    TextButton(
                        onClick = {
                            activity?.finishAffinity()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF9CA3AF)
                            )
                            Text(
                                text = "Exit Application",
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }
    }
}
