package com.cit.kaido.voxsight.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.BuildConfig
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.ChangePasswordRequest
import com.cit.kaido.voxsight.network.UpdateSettingsRequest
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.update.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("voxsight_prefs", Context.MODE_PRIVATE) }

    val userId = remember { prefs.getLong("logged_in_user_id", -1L) }

    // State initialized from SharedPreferences
    var defaultVoicePart by remember { mutableStateOf(prefs.getString("setting_voice_part", "SOPRANO") ?: "SOPRANO") }
    var staffOpacity by remember { mutableFloatStateOf(prefs.getFloat("setting_staff_opacity", 0.20f)) }
    var soundfontTone by remember { mutableStateOf(prefs.getString("setting_soundfont", "AAH") ?: "AAH") }
    var pitchTolerance by remember { mutableIntStateOf(prefs.getInt("setting_pitch_tolerance", 25)) }

    // Dialog state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordChangeLoading by remember { mutableStateOf(false) }

    // Cache calculation
    var cacheSizeMb by remember { mutableStateOf("0.0 MB") }

    fun calculateCache() {
        val cacheDir = context.cacheDir
        val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        val mb = bytes.toDouble() / (1024 * 1024)
        cacheSizeMb = "%.1f MB".format(mb)
    }

    LaunchedEffect(Unit) {
        calculateCache()

        // Sync latest settings from backend if user logged in
        if (userId > 0L) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val response = ApiClient.userService.getSettings(userId)
                    if (response.isSuccessful && response.body() != null) {
                        val remote = response.body()!!
                        withContext(Dispatchers.Main) {
                            defaultVoicePart = remote.defaultVoicePart
                            soundfontTone = remote.soundfontTone
                            staffOpacity = remote.staffDimmingOpacity
                            pitchTolerance = remote.pitchToleranceCents

                            prefs.edit()
                                .putString("setting_voice_part", defaultVoicePart)
                                .putString("setting_soundfont", soundfontTone)
                                .putFloat("setting_staff_opacity", staffOpacity)
                                .putInt("setting_pitch_tolerance", pitchTolerance)
                                .apply()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun saveSettings(
        newPart: String = defaultVoicePart,
        newOpacity: Float = staffOpacity,
        newTone: String = soundfontTone,
        newTolerance: Int = pitchTolerance
    ) {
        defaultVoicePart = newPart
        staffOpacity = newOpacity
        soundfontTone = newTone
        pitchTolerance = newTolerance

        prefs.edit()
            .putString("setting_voice_part", newPart)
            .putString("setting_soundfont", newTone)
            .putFloat("setting_staff_opacity", newOpacity)
            .putInt("setting_pitch_tolerance", newTolerance)
            .apply()

        if (userId > 0L) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    ApiClient.userService.updateSettings(
                        userId = userId,
                        request = UpdateSettingsRequest(
                            defaultVoicePart = newPart,
                            soundfontTone = newTone,
                            staffDimmingOpacity = newOpacity,
                            pitchToleranceCents = newTolerance
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    val topPurple = Color(0xFF38036B)
    val lightPurple = Color(0xFFEFE8F5)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topPurple
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VoxBackground)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Section 1: Practice Preferences
            SettingsSectionHeader(title = "CHORAL PRACTICE PREFERENCES")

            SettingsCard {
                // Preferred Vocal Part
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Default Voice Part", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("Default highlighted voice during rehearsal", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("SOPRANO", "ALTO", "TENOR", "BASS").forEach { part ->
                            val isSelected = defaultVoicePart.equals(part, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) topPurple else lightPurple)
                                    .clickable { saveSettings(newPart = part) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = part.take(3),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else topPurple
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF0EBE1))

                // Staff Dimming Opacity
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Staff Dimming Opacity", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("Visual dimming of unassigned voice staves", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0.10f to "10% (Dim)", 0.20f to "20% (Default)", 0.35f to "35% (Soft)").forEach { (value, label) ->
                            val isSelected = kotlin.math.abs(staffOpacity - value) < 0.05f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) topPurple else lightPurple)
                                    .clickable { saveSettings(newOpacity = value) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else topPurple
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF0EBE1))

                // Vocal Soundfont
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Headphones, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Sustained Vocal Tone", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("Vowel soundfont used for audio playback", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("AAH" to "\"Aah\" Choir", "OOH" to "\"Ooh\" Warm").forEach { (tone, label) ->
                            val isSelected = soundfontTone.equals(tone, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) topPurple else lightPurple)
                                    .clickable { saveSettings(newTone = tone) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else topPurple
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Audio & Intonation
            SettingsSectionHeader(title = "AUDIO & INTONATION DETECTION")

            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Pitch Detection Tolerance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("Accuracy threshold for in-tune intonation indicator", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(10 to "Strict (±10c)", 25 to "Standard (±25c)", 40 to "Forgiving (±40c)").forEach { (cents, label) ->
                            val isSelected = pitchTolerance == cents
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) topPurple else lightPurple)
                                    .clickable { saveSettings(newTolerance = cents) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.White else topPurple
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Storage & Security
            SettingsSectionHeader(title = "ACCOUNT & DATA")

            SettingsCard {
                // Change Password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPasswordDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                        Text("Update your account security password", fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray)
                }

                HorizontalDivider(color = Color(0xFFF0EBE1))

                // Cache Cleanup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cacheDir = context.cacheDir
                            var deletedFiles = 0
                            cacheDir.listFiles()?.forEach { file ->
                                if (file.name.startsWith("scanned_") || file.name.startsWith("sheet_music_") || file.name.startsWith("raw_capture_")) {
                                    file.delete()
                                    deletedFiles++
                                }
                            }
                            calculateCache()
                            Toast.makeText(context, "Cleared $deletedFiles temporary scan files ($cacheSizeMb remaining)", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear Scan Cache", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                        Text("Temporary image and PDF cache: $cacheSizeMb", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("Clear", color = topPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Section 4: App Information
            SettingsSectionHeader(title = "APPLICATION")

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppUpdateManager.checkForUpdates(context)
                            Toast.makeText(context, "Checking for latest updates...", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = topPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VoxSight Version", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                        Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("Check for updates", color = topPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Change Password Dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!passwordChangeLoading) showPasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (min 6 characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !passwordChangeLoading,
                    onClick = {
                        if (currentPassword.isBlank() || newPassword.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPassword.length < 6) {
                            Toast.makeText(context, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (userId <= 0L) {
                            Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        passwordChangeLoading = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val response = ApiClient.userService.changePassword(
                                    userId = userId,
                                    request = ChangePasswordRequest(
                                        currentPassword = currentPassword,
                                        newPassword = newPassword
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    passwordChangeLoading = false
                                    if (response.isSuccessful && response.body()?.success == true) {
                                        Toast.makeText(context, "Password changed successfully!", Toast.LENGTH_SHORT).show()
                                        showPasswordDialog = false
                                        currentPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                    } else {
                                        val errorMsg = response.body()?.message ?: "Failed to change password."
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    passwordChangeLoading = false
                                    Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = topPurple)
                ) {
                    if (passwordChangeLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Update Password")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !passwordChangeLoading,
                    onClick = { showPasswordDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color.Gray,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Column(content = content)
    }
}
