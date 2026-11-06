package com.cit.kaido.voxsight.ui.screens.upload

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsBottomSheet(
    settings: EditorSettings,
    isDeviceLowEnd: Boolean,
    onSettingsChanged: (EditorSettings) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Editor Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoxTextPrimary
                    )
                    Text(
                        text = if (isDeviceLowEnd) "Resource-constrained device profile detected" else "Standard device profile detected",
                        fontSize = 12.sp,
                        color = if (isDeviceLowEnd) Color(0xFFD97706) else VoxTextSubtitle
                    )
                }
                TextButton(
                    onClick = {
                        onSettingsChanged(EditorSettings())
                    }
                ) {
                    Text("Reset Defaults", fontSize = 12.sp, color = VoxPurplePrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // Section 1: Performance Profile
            SettingsSectionHeader(title = "Performance Profile")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceProfile.values().forEach { profile ->
                    val isSelected = settings.performanceProfile == profile
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) VoxPurplePrimary.copy(alpha = 0.12f) else Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, if (isSelected) VoxPurplePrimary else Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSettingsChanged(settings.copy(performanceProfile = profile))
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = profile.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VoxPurplePrimary else VoxTextPrimary
                            )
                        }
                    }
                }
            }
            Text(
                text = when (settings.performanceProfile) {
                    PerformanceProfile.AUTOMATIC -> "Selects lightweight rendering on low-RAM hardware and high fidelity on capable devices."
                    PerformanceProfile.PERFORMANCE -> "Disables animations, transitions, and redundant canvas redraws for maximum responsiveness."
                    PerformanceProfile.QUALITY -> "Enables smooth transitions and high-fidelity rendering."
                },
                fontSize = 11.sp,
                color = VoxTextSubtitle,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(color = Color(0xFFF3F4F6))

            // Section 2: Sheet Music Display
            SettingsSectionHeader(title = "Sheet Music Display")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreScaling.values().forEach { scaling ->
                    val isSelected = settings.scoreScaling == scaling
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) VoxPurplePrimary.copy(alpha = 0.12f) else Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, if (isSelected) VoxPurplePrimary else Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSettingsChanged(settings.copy(scoreScaling = scaling))
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = scaling.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VoxPurplePrimary else VoxTextPrimary
                            )
                        }
                    }
                }
            }

            SettingsToggleRow(
                title = "Show Lyrics",
                subtitle = "Display lyric syllables underneath staff notes",
                checked = settings.showLyrics,
                onCheckedChange = { onSettingsChanged(settings.copy(showLyrics = it)) }
            )
            SettingsToggleRow(
                title = "Show Voice / Part Labels",
                subtitle = "Display SATB voice badges and part names on staves",
                checked = settings.showVoiceLabels,
                onCheckedChange = { onSettingsChanged(settings.copy(showVoiceLabels = it)) }
            )
            SettingsToggleRow(
                title = "Show Measure Numbers",
                subtitle = "Display measure numbering above each system",
                checked = settings.showMeasureNumbers,
                onCheckedChange = { onSettingsChanged(settings.copy(showMeasureNumbers = it)) }
            )
            SettingsToggleRow(
                title = "Show Parts Legend",
                subtitle = "Keep SATB voice color chip legend visible on screen",
                checked = settings.showPartsLegend,
                onCheckedChange = { onSettingsChanged(settings.copy(showPartsLegend = it)) }
            )

            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 8.dp))

            // Section 3: Editing Toolbar Density & Groups
            SettingsSectionHeader(title = "Editing Deck")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolbarDensity.values().forEach { density ->
                    val isSelected = settings.toolbarDensity == density
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) VoxPurplePrimary.copy(alpha = 0.12f) else Color(0xFFF9FAFB),
                        border = BorderStroke(1.dp, if (isSelected) VoxPurplePrimary else Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSettingsChanged(settings.copy(toolbarDensity = density))
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "${density.label} Density",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VoxPurplePrimary else VoxTextPrimary
                            )
                        }
                    }
                }
            }

            SettingsToggleRow(
                title = "Show Toolbar Text Labels",
                subtitle = "Display group and button labels alongside notation icons",
                checked = settings.showToolLabels,
                onCheckedChange = { onSettingsChanged(settings.copy(showToolLabels = it)) }
            )
            SettingsToggleRow(
                title = "Auto-Collapse Editing Deck",
                subtitle = "Slide bottom deck down when a note is deselected",
                checked = settings.autoCollapseDeck,
                onCheckedChange = { onSettingsChanged(settings.copy(autoCollapseDeck = it)) }
            )

            Text(
                text = "Visible Tool Groups",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VoxTextPrimary,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolGroupFilterChip("Notes", settings.showNotesGroup) {
                    onSettingsChanged(settings.copy(showNotesGroup = it))
                }
                ToolGroupFilterChip("Rests", settings.showRestsGroup) {
                    onSettingsChanged(settings.copy(showRestsGroup = it))
                }
                ToolGroupFilterChip("Mod (♯/♭/•)", settings.showModGroup) {
                    onSettingsChanged(settings.copy(showModGroup = it))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolGroupFilterChip("Curves (⌒/𝆢)", settings.showCurvesGroup) {
                    onSettingsChanged(settings.copy(showCurvesGroup = it))
                }
                ToolGroupFilterChip("Voices (SATB)", settings.showVoiceGroup) {
                    onSettingsChanged(settings.copy(showVoiceGroup = it))
                }
                ToolGroupFilterChip("Actions", settings.showActionsGroup) {
                    onSettingsChanged(settings.copy(showActionsGroup = it))
                }
            }

            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 8.dp))

            // Section 4: Navigation & Audio
            SettingsSectionHeader(title = "Navigation & Audio")
            SettingsToggleRow(
                title = "Auto-Scroll to Selected Note",
                subtitle = "Scroll notation viewport automatically when stepping between notes",
                checked = settings.autoScrollToNote,
                onCheckedChange = { onSettingsChanged(settings.copy(autoScrollToNote = it)) }
            )
            SettingsToggleRow(
                title = "Audio Pitch Preview",
                subtitle = "Synthesize pitch tone when tapping or dragging notes",
                checked = settings.audioPreview,
                onCheckedChange = { onSettingsChanged(settings.copy(audioPreview = it)) }
            )
            SettingsToggleRow(
                title = "Haptic Feedback",
                subtitle = "Vibrate subtly on note selection and gesture snaps",
                checked = settings.hapticFeedback,
                onCheckedChange = { onSettingsChanged(settings.copy(hapticFeedback = it)) }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = VoxTextSubtitle,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VoxTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = VoxTextSubtitle,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VoxPurplePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB)
            )
        )
    }
}

@Composable
private fun RowScope.ToolGroupFilterChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (checked) VoxPurplePrimary.copy(alpha = 0.1f) else Color(0xFFF9FAFB),
        border = BorderStroke(1.dp, if (checked) VoxPurplePrimary else Color(0xFFE5E7EB)),
        modifier = Modifier
            .weight(1f)
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                color = if (checked) VoxPurplePrimary else VoxTextSubtitle
            )
        }
    }
}
