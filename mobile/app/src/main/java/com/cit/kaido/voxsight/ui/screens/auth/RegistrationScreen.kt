package com.cit.kaido.voxsight.ui.screens.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cit.kaido.voxsight.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import com.cit.kaido.voxsight.ui.theme.VoxM3OnSurface
import com.cit.kaido.voxsight.ui.theme.VoxM3OnSurfaceVariant
import com.cit.kaido.voxsight.ui.theme.VoxM3Primary
import com.cit.kaido.voxsight.ui.theme.VoxM3PrimaryContainer
import com.cit.kaido.voxsight.ui.theme.VoxM3Surface
import com.cit.kaido.voxsight.ui.theme.VoxM3SurfaceContainerLowest
import com.cit.kaido.voxsight.ui.theme.VoxM3SurfaceVariant

/**
 * Registration Screen — 1:1 match to Stitch design "Registration - VoxSight"
 */
@Composable
fun RegistrationScreen(
    onBackClicked: () -> Unit,
    onSignInClicked: () -> Unit,
    onSignUpClicked: (name: String, email: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val totalImages = 3
    var currentImageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            currentImageIndex = (currentImageIndex + 1) % totalImages
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxM3Surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header with Auto-Swiping Background ─────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Auto-swiping images
            Crossfade(
                targetState = currentImageIndex,
                animationSpec = tween(1000),
                label = "bgFade",
                modifier = Modifier.matchParentSize()
            ) { index ->
                val imageRes = when (index) {
                    0 -> R.drawable.bg_landing_1
                    1 -> R.drawable.bg_landing_2
                    2 -> R.drawable.bg_landing_3
                    else -> R.drawable.bg_landing_1
                }
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark overlay for text readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 64.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // Top bar row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBackClicked,
                        modifier = Modifier
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account?",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { onSignInClicked() }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Sign in",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Brand name
                Text(
                    text = "VoxSight",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── Registration Card ───────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-40).dp)
                .padding(horizontal = 16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(VoxM3SurfaceContainerLowest)
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Get started free.",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = VoxM3OnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Free forever. No credit card needed.",
                fontSize = 14.sp,
                color = VoxM3OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Name Field ──────────────────────────────────────
            VoxOutlinedField(
                value = name,
                onValueChange = { name = it },
                label = "YOUR NAME",
                placeholder = "John Doe"
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Email Field ─────────────────────────────────────
            VoxOutlinedField(
                value = email,
                onValueChange = { email = it },
                label = "EMAIL ADDRESS",
                placeholder = "name@example.com",
                keyboardType = KeyboardType.Email
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Field ──────────────────────────────────
            VoxOutlinedField(
                value = password,
                onValueChange = { password = it },
                label = "PASSWORD",
                placeholder = "••••••••••••",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )

            // Password strength indicator
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val strengthColor = if (password.length > 8) Color(0xFF5BB85B) else Color(0xFFE5A022)
                    val strengthText = if (password.length > 8) "STRONG" else "WEAK"

                    Box(modifier = Modifier.width(16.dp).height(4.dp).clip(RoundedCornerShape(50)).background(strengthColor))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.width(16.dp).height(4.dp).clip(RoundedCornerShape(50)).background(strengthColor))
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (password.length > 8) strengthColor else VoxM3SurfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strengthText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = strengthColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Confirm Password Field ──────────────────────────
            VoxOutlinedField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "CONFIRM PASSWORD",
                placeholder = "••••••••••••",
                isPassword = true,
                passwordVisible = confirmPasswordVisible,
                onTogglePassword = { confirmPasswordVisible = !confirmPasswordVisible }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Sign Up Button ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = VoxM3Primary.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(VoxM3Primary, VoxM3PrimaryContainer)
                        )
                    )
                    .clickable { onSignUpClicked(name, email, password) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign up",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Divider ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = VoxM3SurfaceVariant)
                Text(
                    text = "Or sign up with",
                    fontSize = 14.sp,
                    color = VoxM3OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = VoxM3SurfaceVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Social Login Buttons ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SocialLoginButton(
                    label = "Google",
                    iconResId = R.drawable.ic_google,
                    modifier = Modifier.weight(1f)
                )
                SocialLoginButton(
                    label = "Facebook",
                    labelColor = Color(0xFF1877F2),
                    iconResId = R.drawable.ic_facebook,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
