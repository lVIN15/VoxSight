package com.cit.kaido.voxsight.ui.screens.upload

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurpleIconBg
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorBottomSheet(
    noteId: Int,
    pitchStep: String,
    alter: Int,
    octave: Int,
    durationType: String,
    voiceId: Int,
    measureNumber: Int,
    onVoiceChanged: (Int) -> Unit,
    onPitchChanged: (step: String, alter: Int, octave: Int) -> Unit,
    onDurationChanged: (String) -> Unit,
    onDeleteClicked: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VoxCardStroke) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            Text(
                text = "Edit Note",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Measure $measureNumber • Note Index $noteId",
                style = MaterialTheme.typography.bodyMedium,
                color = VoxTextSubtitle
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Voice Assignment Section (SATB)
            Text(
                text = "Voice Assignment",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val voices = listOf(
                    1 to "Soprano",
                    2 to "Alto",
                    3 to "Tenor",
                    4 to "Bass"
                )
                voices.forEach { (id, name) ->
                    val isSelected = voiceId == id
                    val voiceColor = when (id) {
                        1 -> Color(0xFF9C27B0) // Soprano - Purple
                        2 -> Color(0xFFFF9800) // Alto - Orange
                        3 -> Color(0xFF4CAF50) // Tenor - Green
                        4 -> Color(0xFF2196F3) // Bass - Blue
                        else -> VoxPurplePrimary
                    }
                    
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onVoiceChanged(id) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) voiceColor else VoxCardStroke,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        color = if (isSelected) voiceColor.copy(alpha = 0.1f) else Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.substring(0, 1),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) voiceColor else VoxTextSubtitle
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Pitch Transposer Section
            Text(
                text = "Pitch / Octave",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pitch Display
                val alterSymbol = when (alter) {
                    -1 -> "♭"
                    1 -> "♯"
                    else -> ""
                }
                
                Surface(
                    color = VoxPurpleIconBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(100.dp)
                        .height(130.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$pitchStep$alterSymbol$octave",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxPurplePrimary
                        )
                    }
                }

                // Semitone adjusters (Stacked vertically next to the display card)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Semitone Pitch Adjuster
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pitch", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { 
                                    // Move pitch step up with octave rollover
                                    val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                    val currentIndex = steps.indexOf(pitchStep)
                                    if (pitchStep == "B") {
                                        if (octave < 8) {
                                            onPitchChanged("C", alter, octave + 1)
                                        }
                                    } else if (currentIndex != -1) {
                                        onPitchChanged(steps[currentIndex + 1], alter, octave)
                                    }
                                },
                                modifier = Modifier.size(32.dp).background(VoxPurpleIconBg, CircleShape)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Step Up", tint = VoxPurplePrimary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { 
                                    // Move pitch step down with octave rollover
                                    val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                    val currentIndex = steps.indexOf(pitchStep)
                                    if (pitchStep == "C") {
                                        if (octave > 1) {
                                            onPitchChanged("B", alter, octave - 1)
                                        }
                                    } else if (currentIndex > 0) {
                                        onPitchChanged(steps[currentIndex - 1], alter, octave)
                                    }
                                },
                                modifier = Modifier.size(32.dp).background(VoxPurpleIconBg, CircleShape)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Step Down", tint = VoxPurplePrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Accidental Switchers (Flat / Natural / Sharp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Accidental", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(VoxPurpleIconBg)
                                .padding(1.dp)
                        ) {
                            val accidentals = listOf(-1 to "♭", 0 to "♮", 1 to "♯")
                            accidentals.forEach { (valAlter, symbol) ->
                                val isAccSelected = alter == valAlter
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isAccSelected) VoxPurplePrimary else Color.Transparent)
                                        .clickable { onPitchChanged(pitchStep, valAlter, octave) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAccSelected) Color.White else VoxPurplePrimary
                                    )
                                }
                            }
                        }
                    }

                    // Octave Adjuster
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Octave", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { 
                                    if (octave < 8) onPitchChanged(pitchStep, alter, octave + 1)
                                },
                                modifier = Modifier.size(32.dp).background(VoxPurpleIconBg, CircleShape)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Octave Up", tint = VoxPurplePrimary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { 
                                    if (octave > 1) onPitchChanged(pitchStep, alter, octave - 1)
                                },
                                modifier = Modifier.size(32.dp).background(VoxPurpleIconBg, CircleShape)
                            ) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Octave Down", tint = VoxPurplePrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Duration Type Selector Section
            Text(
                text = "Note Duration",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val durations = listOf(
                    "whole" to "Whole 𝅝",
                    "half" to "Half 𝅗𝅥",
                    "quarter" to "Quarter 𝅘𝅥",
                    "eighth" to "Eighth 𝅘𝅥𝅮",
                    "16th" to "16th 𝅘𝅥𝅯"
                )
                durations.forEach { (type, label) ->
                    val isDurSelected = durationType.lowercase() == type
                    FilterChip(
                        selected = isDurSelected,
                        onClick = { onDurationChanged(type) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VoxPurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Action Buttons (Delete note)
            OutlinedButton(
                onClick = onDeleteClicked,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Note",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DELETE NOTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
