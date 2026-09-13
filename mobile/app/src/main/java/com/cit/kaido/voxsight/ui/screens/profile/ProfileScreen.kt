package com.cit.kaido.voxsight.ui.screens.profile

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.UpdateProfileRequest
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    username: String,
    onBackClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onSettingsClicked: () -> Unit = {},
    onUpgradeToPremiumClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("voxsight_prefs", Context.MODE_PRIVATE) }
    val userId = remember { prefs.getLong("logged_in_user_id", -1L) }

    var currentUsername by remember { mutableStateOf(username) }
    var currentEmail by remember { mutableStateOf(prefs.getString("logged_in_email", "") ?: "") }
    var memberStatus by remember { mutableStateOf(prefs.getString("logged_in_status", "FREE") ?: "FREE") }
    var voicePart by remember { mutableStateOf(prefs.getString("setting_voice_part", "SOPRANO") ?: "SOPRANO") }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Edit fields
    var editUsername by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editVoicePart by remember { mutableStateOf("SOPRANO") }
    var isUpdating by remember { mutableStateOf(false) }

    // Fetch latest profile from backend if logged in
    LaunchedEffect(Unit) {
        if (userId > 0L) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val response = ApiClient.userService.getProfile(userId)
                    if (response.isSuccessful && response.body() != null) {
                        val profile = response.body()!!
                        withContext(Dispatchers.Main) {
                            currentUsername = profile.username
                            currentEmail = profile.email
                            memberStatus = profile.memberStatus ?: "FREE"
                            profile.defaultVoicePart?.let { voicePart = it }

                            prefs.edit()
                                .putString("logged_in_username", currentUsername)
                                .putString("logged_in_email", currentEmail)
                                .putString("logged_in_status", memberStatus)
                                .putString("setting_voice_part", voicePart)
                                .apply()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val topPurple = Color(0xFF38036B)
    val lightPurple = Color(0xFFEFE8F5)
    val orangeLight = Color(0xFFFFE0B2)
    val orangeDark = Color(0xFFFF9800)
    val redLight = Color(0xFFFFEBEE)
    val redDark = Color(0xFFD32F2F)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxBackground)
    ) {
        // --- Top Purple Section ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(topPurple)
                .padding(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                // Back Button Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onBackClicked() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "GO TO DASHBOARD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Profile Info Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF606A85)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile Picture",
                            tint = Color(0xFFE6EAEB),
                            modifier = Modifier
                                .size(90.dp)
                                .offset(y = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUsername,
                                color = Color.White,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    editUsername = currentUsername
                                    editEmail = currentEmail
                                    editVoicePart = voicePart
                                    showEditDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = Color.White
                                )
                            }
                        }
                        if (currentEmail.isNotBlank()) {
                            Text(
                                text = currentEmail,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "$memberStatus MEMBER",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = orangeDark.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = voicePart,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Action List ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileCard(
                title = "Upgrade to Premium",
                subtitle = "Unlock infinite scores",
                icon = Lucide.Crown,
                iconBgColor = orangeLight,
                iconColor = orangeDark,
                onClick = onUpgradeToPremiumClicked
            )

            ProfileCard(
                title = "Settings",
                subtitle = "Practice preferences, soundfonts & audio",
                icon = Icons.Outlined.Settings,
                iconBgColor = lightPurple,
                iconColor = topPurple,
                onClick = onSettingsClicked
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            ProfileCard(
                title = "Log out",
                titleColor = redDark,
                icon = Icons.AutoMirrored.Outlined.Logout,
                iconBgColor = redLight,
                iconColor = redDark,
                showArrow = false,
                onClick = { showLogoutDialog = true }
            )
        }
    }

    // Edit Profile Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUpdating) showEditDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Preferred Voice Part", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("SOPRANO", "ALTO", "TENOR", "BASS").forEach { part ->
                            val isSel = editVoicePart.equals(part, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) topPurple else lightPurple)
                                    .clickable { editVoicePart = part },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = part.take(3),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color.White else topPurple
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUpdating,
                    onClick = {
                        if (editUsername.isBlank() || editEmail.isBlank()) {
                            Toast.makeText(context, "Username and email cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (userId <= 0L) {
                            currentUsername = editUsername
                            currentEmail = editEmail
                            voicePart = editVoicePart
                            prefs.edit()
                                .putString("logged_in_username", editUsername)
                                .putString("logged_in_email", editEmail)
                                .putString("setting_voice_part", editVoicePart)
                                .apply()
                            showEditDialog = false
                            return@Button
                        }

                        isUpdating = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val response = ApiClient.userService.updateProfile(
                                    userId = userId,
                                    request = UpdateProfileRequest(
                                        username = editUsername,
                                        email = editEmail,
                                        defaultVoicePart = editVoicePart
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    isUpdating = false
                                    if (response.isSuccessful && response.body() != null) {
                                        val updated = response.body()!!
                                        currentUsername = updated.username
                                        currentEmail = updated.email
                                        updated.defaultVoicePart?.let { voicePart = it }

                                        prefs.edit()
                                            .putString("logged_in_username", currentUsername)
                                            .putString("logged_in_email", currentEmail)
                                            .putString("setting_voice_part", voicePart)
                                            .apply()

                                        Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                        showEditDialog = false
                                    } else {
                                        Toast.makeText(context, "Failed to update profile", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isUpdating = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = topPurple)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isUpdating,
                    onClick = { showEditDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "Log out", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to log out?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClicked()
                    }
                ) {
                    Text("Yes", color = redDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ProfileCard(
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color(0xFF1E1E1E),
    icon: ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Arrow",
                tint = Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
