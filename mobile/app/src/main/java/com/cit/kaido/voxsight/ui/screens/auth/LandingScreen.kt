package com.cit.kaido.voxsight.ui.screens.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.ui.theme.VoxM3PrimaryContainer
import kotlinx.coroutines.delay

/**
 * VoxSight Landing Page — 1:1 match to Stitch design
 * "VoxSight Landing Page - Refined Notes Layout"
 *
 * Layout: Full-screen dark background with gradient overlay,
 * floating music note, brand headline, carousel dots,
 * and a white bottom sheet with Log in / Sign up CTAs.
 */
@Composable
fun LandingScreen(
    onLoginClicked: () -> Unit,
    onSignUpClicked: () -> Unit
) {
    // Floating animation for the music note
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val floatRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatRotate"
    )

    // ── Auto-swiping Background Setup ────────────────────────
    // We'll use 3 background states based on the images you uploaded. 
    val totalImages = 3
    var currentImageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000) // Change image every 4 seconds
            currentImageIndex = (currentImageIndex + 1) % totalImages
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Auto-swiping image layer
        Crossfade(
            targetState = currentImageIndex,
            animationSpec = tween(1000),
            label = "bgFade"
        ) { index ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val imageRes = when (index) {
                    0 -> R.drawable.bg_landing_1
                    1 -> R.drawable.bg_landing_2
                    2 -> R.drawable.bg_landing_3
                    else -> R.drawable.bg_landing_1
                }
                
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Background gradient overlay ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // ── Main content (top half) ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ── Floating Music Note + Brand ─────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating music note with purple glow
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = "Music Note",
                    tint = Color.White,
                    modifier = Modifier
                        .size(72.dp)
                        .offset { IntOffset(0, floatOffset.dp.roundToPx()) }
                        .rotate(floatRotation)
                        .drawBehind {
                            // Purple glow effect
                            drawCircle(
                                color = VoxM3PrimaryContainer.copy(alpha = 0.5f),
                                radius = size.minDimension * 0.8f,
                                center = center
                            )
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Brand name
                Text(
                    text = "VOXSIGHT",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 30.sp,
                    color = Color.White,
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Headline ────────────────────────────────────────
            Text(
                text = "CHORAL PRECISION.\nVOCAL MASTERY.",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Carousel pagination dots ────────────────────────
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                for (i in 0 until totalImages) {
                    if (i == currentImageIndex) {
                        // Active dot (wider pill)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    } else {
                        // Inactive dot
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                    }
                    if (i < totalImages - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // ── Bottom Sheet ────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 40.dp,
                        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .background(Color.White)
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Log in button — filled brand purple
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(VoxM3PrimaryContainer)
                        .clickable { onLoginClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Log in",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sign up button — outlined brand purple
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.5.dp, VoxM3PrimaryContainer, RoundedCornerShape(50))
                        .clickable { onSignUpClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign up",
                        color = VoxM3PrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Footer terms
                Text(
                    text = "By continuing, you agree to VoxSight's",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 12.sp,
                        color = VoxM3PrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { }
                    )
                    Text(
                        text = " and ",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    Text(
                        text = "Terms of Use",
                        fontSize = 12.sp,
                        color = VoxM3PrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { }
                    )
                }
            }
        }
    }
}
