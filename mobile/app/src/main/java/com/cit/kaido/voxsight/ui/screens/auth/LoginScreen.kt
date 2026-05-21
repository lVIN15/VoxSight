package com.cit.kaido.voxsight.ui.screens.auth

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import com.cit.kaido.voxsight.ui.theme.VoxM3OnSurface
import com.cit.kaido.voxsight.ui.theme.VoxM3OnSurfaceVariant
import com.cit.kaido.voxsight.ui.theme.VoxM3OutlineVariant
import com.cit.kaido.voxsight.ui.theme.VoxM3Primary
import com.cit.kaido.voxsight.ui.theme.VoxM3PrimaryContainer
import com.cit.kaido.voxsight.ui.theme.VoxM3Surface
import com.cit.kaido.voxsight.ui.theme.VoxM3SurfaceContainerLow
import com.cit.kaido.voxsight.ui.theme.VoxM3SurfaceContainerLowest
import com.cit.kaido.voxsight.ui.theme.VoxM3SurfaceVariant

/**
 * Login Screen — 1:1 match to Stitch design "Login - VoxSight"
 *
 * Layout:
 *  - Purple gradient header with back arrow, "Don't have an account?" + "Get Started"
 *  - "VoxSight" brand centered in header
 *  - White card overlapping header (negative margin)
 *  - "Welcome Back" / "Enter your details below"
 *  - Email + Password fields with floating labels
 *  - "Sign in" gradient CTA
 *  - "Forgot your password?" link
 *  - Divider "Or sign in with"
 *  - Google + Facebook social buttons (2-col grid)
 */
@Composable
fun LoginScreen(
    onBackClicked: () -> Unit,
    onGetStartedClicked: () -> Unit,
    onSignInClicked: (email: String, password: String) -> Unit,
    onForgotPasswordClicked: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

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
                            text = "Don't have an account?",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { onGetStartedClicked() }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Get Started",
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

        // ── Login Card (overlapping header) ─────────────────────
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
                text = "Welcome Back",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                color = VoxM3OnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your details below",
                fontSize = 16.sp,
                color = VoxM3OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Email Field ─────────────────────────────────────
            VoxOutlinedField(
                value = email,
                onValueChange = { email = it },
                label = "Email Address",
                placeholder = "name@example.com",
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Password Field ──────────────────────────────────
            VoxOutlinedField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                placeholder = "Enter your password",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sign In Button ──────────────────────────────────
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
                    .clickable { onSignInClicked(email, password) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign in",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Forgot password
            Text(
                text = "Forgot your password?",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoxM3OnSurfaceVariant,
                modifier = Modifier.clickable { onForgotPasswordClicked() }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Divider ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = VoxM3SurfaceVariant
                )
                Text(
                    text = "Or sign in with",
                    fontSize = 12.sp,
                    color = VoxM3OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = VoxM3SurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Social Login Buttons ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Google
                SocialLoginButton(
                    label = "Google",
                    iconResId = R.drawable.ic_google,
                    modifier = Modifier.weight(1f)
                )
                // Facebook
                SocialLoginButton(
                    label = "Facebook",
                    labelColor = Color(0xFF1877F2),
                    iconResId = R.drawable.ic_facebook,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Reusable outlined text field matching the Stitch design's
 * ghost-border style with floating label.
 */
@Composable
fun VoxOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                color = VoxM3OnSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePassword?.invoke() }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff
                        else Icons.Outlined.Visibility,
                        contentDescription = "Toggle password",
                        tint = VoxM3OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = VoxM3OutlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = VoxM3SurfaceContainerLowest,
            unfocusedContainerColor = VoxM3SurfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = VoxM3OnSurface,
            unfocusedTextColor = VoxM3OnSurface,
            focusedLabelColor = VoxM3OnSurfaceVariant,
            unfocusedLabelColor = VoxM3OnSurfaceVariant,
            cursorColor = VoxM3Primary
        )
    )
}

/**
 * Social login button (Google / Facebook) matching Stitch design.
 */
@Composable
fun SocialLoginButton(
    label: String,
    labelColor: Color = VoxM3OnSurface,
    iconResId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = VoxM3OutlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(VoxM3SurfaceContainerLowest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )
        }
    }
}
