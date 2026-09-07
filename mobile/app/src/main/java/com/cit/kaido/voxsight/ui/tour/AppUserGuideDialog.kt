package com.cit.kaido.voxsight.ui.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxM3PrimaryContainer
import com.cit.kaido.voxsight.ui.theme.VoxPurpleAccent
import com.cit.kaido.voxsight.ui.theme.VoxPurpleLight
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary

data class GuidePage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconTint: Color = VoxPurplePrimary,
    val iconBg: Color = VoxPurpleLight
)

/**
 * High-impact interactive User Guide that automatically appears on fresh install / app launch.
 * Includes [Skip] and [Next] -> [Get Started] buttons.
 */
@Composable
fun AppUserGuideDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val pages = remember {
        listOf(
            GuidePage(
                icon = Icons.Outlined.MusicNote,
                title = "Welcome to VoxSight",
                subtitle = "CHORAL SIGHT-READING COMPANION",
                description = "VoxSight helps amateur choir members practice repertoire independently. Digitize sheet music, isolate your voice part, and get real-time feedback as you sing.",
                iconTint = Color(0xFF5B2D8E),
                iconBg = Color(0xFFEDE5F4)
            ),
            GuidePage(
                icon = Icons.Outlined.CameraAlt,
                title = "1. Digitize Sheet Music",
                subtitle = "OPTICAL MUSIC RECOGNITION (OMR)",
                description = "Snap a photo of your physical choir sheet music or import MusicXML files directly. Our recognition engine transcribes staves, noteheads, and lyrics automatically.",
                iconTint = Color(0xFF7C3AED),
                iconBg = Color(0xFFF3E8FF)
            ),
            GuidePage(
                icon = Icons.Outlined.Tune,
                title = "2. Isolate Your Voice",
                subtitle = "SOPRANO • ALTO • TENOR • BASS",
                description = "Select your vocal part to highlight notes in vibrant glowing auras. Mute your part to practice a cappella, or mute others to focus solely on your melody.",
                iconTint = Color(0xFF2563EB),
                iconBg = Color(0xFFEFF6FF)
            ),
            GuidePage(
                icon = Icons.Outlined.Mic,
                title = "3. Real-Time Pitch Feedback",
                subtitle = "ACCURACY DETECTION (YIN ALGORITHM)",
                description = "Sing along with the 60 FPS scrolling score! Your microphone analyzes your singing note-by-note: green means in-tune, while red alerts you if you are sharp or flat.",
                iconTint = Color(0xFF059669),
                iconBg = Color(0xFFECFDF5)
            )
        )
    }

    var currentPageIndex by remember { mutableIntStateOf(0) }
    val currentPage = pages[currentPageIndex]
    val isLastPage = currentPageIndex == pages.size - 1

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    detectTapGestures { /* consume background touches */ }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Pill: Tag + Counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = VoxPurpleLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "VOXSIGHT USER GUIDE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = VoxPurplePrimary,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "Step ${currentPageIndex + 1} of ${pages.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Animated Page Content
                    Crossfade(targetState = currentPage, label = "guidePage") { page ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Large Illustrated Icon Circle
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(page.iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = page.title,
                                    tint = page.iconTint,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Subtitle / Tagline
                            Text(
                                text = page.subtitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoxPurpleAccent,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Title
                            Text(
                                text = page.title,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = VoxTextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Description
                            Text(
                                text = page.description,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = VoxTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Pagination Dots
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in pages.indices) {
                            val isActive = i == currentPageIndex
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(6.dp)
                                    .width(if (isActive) 24.dp else 6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isActive) VoxPurplePrimary else Color(0xFFD1D5DB))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Actions Row: Skip and Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Skip",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VoxTextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                if (isLastPage) {
                                    onDismiss()
                                } else {
                                    currentPageIndex++
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoxPurplePrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = if (isLastPage) "Get Started ✓" else "Next ➔",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
