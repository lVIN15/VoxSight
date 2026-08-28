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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapHoriz
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
    lyricText: String = "",
    isRest: Boolean = false,
    isDotted: Boolean = false,
    onLyricChanged: (String) -> Unit = {},
    onVoiceChanged: (Int) -> Unit,
    onPitchChanged: (step: String, alter: Int, octave: Int) -> Unit,
    onDurationChanged: (String, Boolean) -> Unit,
    onToggleDot: (Boolean) -> Unit,
    onConvertToRest: () -> Unit,
    onConvertToNote: (step: String, alter: Int, octave: Int) -> Unit,
    onAddNote: (position: String, step: String, alter: Int, octave: Int, type: String, dotted: Boolean) -> Unit,
    onAddRest: (position: String, type: String, dotted: Boolean) -> Unit,
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
                text = if (isRest) "Edit Rest" else "Edit Note",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Measure $measureNumber • Element ID #$noteId",
                style = MaterialTheme.typography.bodyMedium,
                color = VoxTextSubtitle
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 0. Convert between Note and Rest
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (isRest) {
                            onConvertToNote(pitchStep.ifBlank { "C" }, alter, if (octave in 1..8) octave else 4)
                        } else {
                            onConvertToRest()
                        }
                    }
                    .border(1.dp, VoxPurplePrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                color = VoxPurpleIconBg.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = "Convert Element",
                        tint = VoxPurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRest) "Convert Rest → Normal Note" else "Convert Note → Rest",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = VoxPurplePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Voice Assignment Section (SATB)
            Text(
                text = "Voice / Part Assignment",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val voices = listOf(
                    1 to "Soprano (S)",
                    2 to "Alto (A)",
                    3 to "Tenor (T)",
                    4 to "Bass (B)"
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
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.substringAfter("(").substringBefore(")"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) voiceColor else VoxTextSubtitle
                            )
                        }
                    }
                }
            }

            // 2. Pitch Transposer Section (Only for normal notes)
            if (!isRest) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Pitch & Accidental",
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
                    val alterSymbol = when (alter) {
                        -1 -> "♭"
                        1 -> "♯"
                        else -> ""
                    }

                    Surface(
                        color = VoxPurpleIconBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .width(96.dp)
                            .height(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$pitchStep$alterSymbol$octave",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoxPurplePrimary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Semitone Pitch Step Adjuster
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Step", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                        val currentIndex = steps.indexOf(pitchStep)
                                        if (pitchStep == "B") {
                                            if (octave < 8) onPitchChanged("C", alter, octave + 1)
                                        } else if (currentIndex != -1 && currentIndex < steps.size - 1) {
                                            onPitchChanged(steps[currentIndex + 1], alter, octave)
                                        }
                                    },
                                    modifier = Modifier.size(30.dp).background(VoxPurpleIconBg, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Step Up", tint = VoxPurplePrimary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                        val currentIndex = steps.indexOf(pitchStep)
                                        if (pitchStep == "C") {
                                            if (octave > 1) onPitchChanged("B", alter, octave - 1)
                                        } else if (currentIndex > 0) {
                                            onPitchChanged(steps[currentIndex - 1], alter, octave)
                                        }
                                    },
                                    modifier = Modifier.size(30.dp).background(VoxPurpleIconBg, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Step Down", tint = VoxPurplePrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Accidental Switchers (Flat / Natural / Sharp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Accidental", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
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
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isAccSelected) VoxPurplePrimary else Color.Transparent)
                                            .clickable { onPitchChanged(pitchStep, valAlter, octave) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = symbol,
                                            fontSize = 13.sp,
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
                            Text("Octave", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = VoxTextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { if (octave < 8) onPitchChanged(pitchStep, alter, octave + 1) },
                                    modifier = Modifier.size(30.dp).background(VoxPurpleIconBg, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Octave Up", tint = VoxPurplePrimary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { if (octave > 1) onPitchChanged(pitchStep, alter, octave - 1) },
                                    modifier = Modifier.size(30.dp).background(VoxPurpleIconBg, CircleShape)
                                ) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Octave Down", tint = VoxPurplePrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Duration & Dotted Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isRest) "Rest Duration" else "Note Duration",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = VoxTextPrimary
                )

                // Dotted toggle button
                FilterChip(
                    selected = isDotted,
                    onClick = { onToggleDot(!isDotted) },
                    label = {
                        Text(
                            text = if (isDotted) "• Dotted (Active)" else (if (isRest) "• Dot Rest" else "• Dot Note"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF9800),
                        selectedLabelColor = Color.White,
                        containerColor = VoxPurpleIconBg,
                        labelColor = VoxPurplePrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val durations = if (isRest) {
                    listOf(
                        "whole" to "Whole 𝄻",
                        "half" to "Half 𝄼",
                        "quarter" to "Quarter 𝄽",
                        "eighth" to "Eighth 𝄾",
                        "16th" to "16th 𝄿",
                        "32nd" to "32nd 𝅀"
                    )
                } else {
                    listOf(
                        "whole" to "Whole 𝅝",
                        "half" to "Half 𝅗𝅥",
                        "quarter" to "Quarter 𝅘𝅥",
                        "eighth" to "Eighth 𝅘𝅥𝅮",
                        "16th" to "16th 𝅘𝅥𝅯",
                        "32nd" to "32nd 𝅘𝅥𝅰"
                    )
                }
                durations.forEach { (type, label) ->
                    val isDurSelected = durationType.lowercase() == type
                    FilterChip(
                        selected = isDurSelected,
                        onClick = { onDurationChanged(type, isDotted) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VoxPurplePrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // 4. Lyric / Word Input Section (Only for pitch notes)
            if (!isRest) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Lyric / Syllable",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = VoxTextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))
                var currentLyric by remember(noteId, lyricText) { mutableStateOf(lyricText) }
                OutlinedTextField(
                    value = currentLyric,
                    onValueChange = {
                        currentLyric = it
                        onLyricChanged(it)
                    },
                    placeholder = { Text("e.g. Hal-le-lu-jah", color = VoxTextSubtitle.copy(alpha = 0.6f), fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VoxPurplePrimary,
                        unfocusedBorderColor = VoxCardStroke,
                        focusedTextColor = VoxTextPrimary,
                        unfocusedTextColor = VoxTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Add Note / Add Rest Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onAddNote("after", pitchStep.ifBlank { "C" }, alter, octave, durationType.ifBlank { "quarter" }, isDotted)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VoxPurplePrimary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Note", modifier = Modifier.size(16.dp), tint = VoxPurplePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VoxPurplePrimary)
                }

                OutlinedButton(
                    onClick = {
                        onAddRest("after", durationType.ifBlank { "quarter" }, isDotted)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF673AB7))
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Rest", modifier = Modifier.size(16.dp), tint = Color(0xFF673AB7))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Rest", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF673AB7))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Delete Action Button
            OutlinedButton(
                onClick = onDeleteClicked,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRest) "DELETE REST" else "DELETE NOTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
