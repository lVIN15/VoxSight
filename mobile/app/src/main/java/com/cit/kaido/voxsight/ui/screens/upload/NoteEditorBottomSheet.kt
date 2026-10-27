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
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorBottomSheet(
    noteId: Int,
    pitchStep: String,
    alter: Int,
    octave: Int,
    durationType: String,
    voiceId: Int,
    voiceCode: String = "",
    measureNumber: Int,
    lyricText: String = "",
    isRest: Boolean = false,
    isDotted: Boolean = false,
    onLyricChanged: (String) -> Unit = {},
    onVoiceChanged: (String) -> Unit,
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

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Voice Assignment Section (SATB, Divisi, Merged Unisons)
            val activeToken = if (voiceCode.isNotBlank()) {
                val up = voiceCode.uppercase().trim()
                if (up == "BARITONE_SOLO" || up == "BARITONE" || up.contains("BARITONE") || up.contains("SOLO")) "SOLO" else up
            } else when (voiceId) {
                1 -> "S"
                2 -> "A"
                3 -> "T"
                4 -> "B"
                5 -> "SOLO"
                6 -> "OTHERS"
                else -> "S"
            }
            val initialCategory = when {
                activeToken in listOf("S1", "S2", "A1", "A2", "T1", "T2", "B1", "B2") -> "Divisi"
                activeToken in listOf("UNISON_TB", "UNISON_SA", "TUTTI") -> "Merged"
                activeToken in listOf("SOLO", "OTHERS") -> "Other"
                else -> "Core"
            }
            var selectedCategory by remember(activeToken) { mutableStateOf(initialCategory) }

            Text(
                text = "Voice / Part Assignment",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Segmented Category Filter Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Core", "Divisi", "Merged", "Other").forEach { cat ->
                    val isCatActive = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCatActive) Color.White else Color.Transparent)
                            .clickable { selectedCategory = cat }
                            .then(
                                if (isCatActive) Modifier.border(1.dp, VoxCardStroke, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isCatActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCatActive) VoxPurplePrimary else VoxTextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            data class VoiceOption(val code: String, val label: String, val color: Color)
            val options = when (selectedCategory) {
                "Core" -> listOf(
                    VoiceOption("S", "Soprano (S)", Color(0xFFE11D48)),
                    VoiceOption("A", "Alto (A)", Color(0xFF8E24AA)),
                    VoiceOption("T", "Tenor (T)", Color(0xFF2563EB)),
                    VoiceOption("B", "Bass (B)", Color(0xFF16A34A))
                )
                "Divisi" -> listOf(
                    VoiceOption("S1", "S1", Color(0xFFFB7185)),
                    VoiceOption("S2", "S2", Color(0xFF881337)),
                    VoiceOption("A1", "A1", Color(0xFFC084FC)),
                    VoiceOption("A2", "A2", Color(0xFF4C1D95)),
                    VoiceOption("T1", "T1", Color(0xFF0284C7)),
                    VoiceOption("T2", "T2", Color(0xFF1E3A8A)),
                    VoiceOption("B1", "B1", Color(0xFF65A30D)),
                    VoiceOption("B2", "B2", Color(0xFF064E3B))
                )
                "Merged" -> listOf(
                    VoiceOption("UNISON_SA", "S/A Unison", Color(0xFF4F46E5)),
                    VoiceOption("UNISON_TB", "T/B Unison", Color(0xFF0D9488)),
                    VoiceOption("TUTTI", "Tutti (All)", Color(0xFFCA8A04))
                )
                else -> listOf(
                    VoiceOption("SOLO", "Solo Voice", Color(0xFFFF6D00)),
                    VoiceOption("OTHERS", "Accompaniment", Color(0xFF546E7A))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { opt ->
                    val isSelected = activeToken == opt.code
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onVoiceChanged(opt.code) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) opt.color else VoxCardStroke,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        color = if (isSelected) opt.color.copy(alpha = 0.12f) else Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(opt.color)
                            )
                            Text(
                                text = opt.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) opt.color else VoxTextPrimary
                            )
                        }
                    }
                }
            }

            // 2. Pitch & Accidental Section (Only for normal notes)
            if (!isRest) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Pitch & Accidental",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = VoxTextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(10.dp))

                val alterSymbol = when (alter) {
                    -1 -> "♭"
                    1 -> "♯"
                    else -> ""
                }

                Surface(
                    color = Color(0xFFF9FAFB),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VoxCardStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: Note Hero Preview Badge (Left) + Step & Octave Steppers (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Note Hero Preview Box (compact, sleek)
                            Surface(
                                color = VoxPurpleIconBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(76.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$pitchStep$alterSymbol$octave",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoxPurplePrimary
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Pitch",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VoxTextSubtitle
                                    )
                                }
                            }

                            // Steppers Column (Step & Octave)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Step Adjuster Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Step",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VoxTextPrimary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(VoxPurpleIconBg)
                                                .clickable {
                                                    val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                                    val currentIndex = steps.indexOf(pitchStep)
                                                    if (pitchStep == "C") {
                                                        if (octave > 1) onPitchChanged("B", alter, octave - 1)
                                                    } else if (currentIndex > 0) {
                                                        onPitchChanged(steps[currentIndex - 1], alter, octave)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = "Step Down",
                                                tint = VoxPurplePrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Step Value Badge
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(26.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                                .border(1.dp, VoxCardStroke, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pitchStep,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VoxPurplePrimary
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(VoxPurpleIconBg)
                                                .clickable {
                                                    val steps = listOf("C", "D", "E", "F", "G", "A", "B")
                                                    val currentIndex = steps.indexOf(pitchStep)
                                                    if (pitchStep == "B") {
                                                        if (octave < 8) onPitchChanged("C", alter, octave + 1)
                                                    } else if (currentIndex != -1 && currentIndex < steps.size - 1) {
                                                        onPitchChanged(steps[currentIndex + 1], alter, octave)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                                contentDescription = "Step Up",
                                                tint = VoxPurplePrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                // Octave Adjuster Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Octave",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VoxTextPrimary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(VoxPurpleIconBg)
                                                .clickable {
                                                    if (octave > 1) onPitchChanged(pitchStep, alter, octave - 1)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = "Octave Down",
                                                tint = VoxPurplePrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Octave Value Badge
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .height(26.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                                .border(1.dp, VoxCardStroke, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = octave.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VoxPurplePrimary
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(VoxPurpleIconBg)
                                                .clickable {
                                                    if (octave < 8) onPitchChanged(pitchStep, alter, octave + 1)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                                contentDescription = "Octave Up",
                                                tint = VoxPurplePrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Divider
                        HorizontalDivider(color = VoxCardStroke.copy(alpha = 0.6f), thickness = 1.dp)

                        // Row 2: Accidental Segmented Control (Dedicated Full Width)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Accidental",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VoxTextSubtitle
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEDE9FE))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val accidentals = listOf(
                                    Triple(-1, "♭", "Flat"),
                                    Triple(0, "♮", "Natural"),
                                    Triple(1, "♯", "Sharp")
                                )

                                accidentals.forEach { (valAlter, symbol, name) ->
                                    val isAccSelected = alter == valAlter
                                    val (symbolSize, symbolOffsetY) = when (valAlter) {
                                        -1 -> 20.sp to (-3).dp    // Flat: enlarged to match Sharp visual size, lifted to optical baseline
                                        0  -> 20.sp to (-5.5).dp  // Natural: enlarged, lifted further (-5.5dp) so its central square body aligns directly with the "Natural" text
                                        1  -> 16.sp to (-0.5).dp  // Sharp: already prominent and visually balanced
                                        else -> 16.sp to 0.dp
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onPitchChanged(pitchStep, valAlter, octave) },
                                        color = if (isAccSelected) VoxPurplePrimary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = symbol,
                                                fontSize = symbolSize,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAccSelected) Color.White else VoxPurplePrimary,
                                                modifier = Modifier.offset(y = symbolOffsetY)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = name,
                                                fontSize = 12.sp,
                                                fontWeight = if (isAccSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAccSelected) Color.White else VoxTextPrimary
                                            )
                                        }
                                    }
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

            // State for Choose Placement Dialog
            var pendingAddType by remember { mutableStateOf<String?>(null) }

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
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isDurSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDurSelected) Color.White else VoxTextPrimary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VoxPurplePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = VoxPurpleIconBg,
                            labelColor = VoxPurplePrimary
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
                    onClick = { pendingAddType = "note" },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VoxPurplePrimary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Note", modifier = Modifier.size(16.dp), tint = VoxPurplePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Note", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VoxPurplePrimary)
                }

                OutlinedButton(
                    onClick = { pendingAddType = "rest" },
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

            // 7. Choose Placement Modal Dialog
            if (pendingAddType != null) {
                val isAddingNote = (pendingAddType == "note")
                AlertDialog(
                    onDismissRequest = { pendingAddType = null },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            text = "Choose Placement",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = VoxTextPrimary
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Where relative to the selected note would you like to insert it?",
                                fontSize = 14.sp,
                                color = VoxTextSubtitle
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Button 1: Insert Before (Left)
                            Button(
                                onClick = {
                                    val pos = "before"
                                    if (isAddingNote) {
                                        onAddNote(pos, pitchStep.ifBlank { "C" }, alter, if (octave in 1..8) octave else 4, durationType.ifBlank { "quarter" }, isDotted)
                                    } else {
                                        onAddRest(pos, durationType.ifBlank { "quarter" }, isDotted)
                                    }
                                    pendingAddType = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VoxPurplePrimary)
                            ) {
                                Text("◀  Insert Before (Left)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }

                            // Button 2: Insert After (Right)
                            Button(
                                onClick = {
                                    val pos = "after"
                                    if (isAddingNote) {
                                        onAddNote(pos, pitchStep.ifBlank { "C" }, alter, if (octave in 1..8) octave else 4, durationType.ifBlank { "quarter" }, isDotted)
                                    } else {
                                        onAddRest(pos, durationType.ifBlank { "quarter" }, isDotted)
                                    }
                                    pendingAddType = null
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                            ) {
                                Text("Insert After (Right)  ▶", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(
                            onClick = { pendingAddType = null }
                        ) {
                            Text("Cancel", color = VoxTextSubtitle, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        }
    }
}
