package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.ui.theme.VoxAccentGreen
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurpleAccent
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Module 2 UI: Audio-Visual Selective Focus (2.1) + Assigned Part Playback (2.2).
 */
@Composable
fun Module2PracticeScreen(
    score: MusicXmlScore? = null,
    fallbackTitle: String = stringResource(R.string.practice_title)
) {
    val resolvedScore = score ?: sampleMusicXmlScore(fallbackTitle)
    val staffNotes = remember(resolvedScore) {
        buildStaffNotes(resolvedScore.notes)
    }

    var selectedPart by remember { mutableStateOf(VoicePart.Soprano) }
    var audioMuteEnabled by remember { mutableStateOf(true) }
    var visualFocusEnabled by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.24f) }

    val totalSeconds = resolvedScore.totalSeconds
    val currentSeconds = (totalSeconds * progress).roundToInt()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            VoxBackground,
            VoxCardBackground.copy(alpha = 0.6f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PracticeTopBar(title = resolvedScore.title)

            VoicePartCard(
                selectedPart = selectedPart,
                onPartSelected = { selectedPart = it },
                audioMuteEnabled = audioMuteEnabled,
                onAudioMuteChange = { audioMuteEnabled = it },
                visualFocusEnabled = visualFocusEnabled,
                onVisualFocusChange = { visualFocusEnabled = it }
            )

            ScoreCard(
                selectedPart = selectedPart,
                visualFocusEnabled = visualFocusEnabled,
                playheadProgress = progress,
                staffNotes = staffNotes
            )

            PlaybackControlBar(
                isPlaying = isPlaying,
                onPlayPause = { isPlaying = !isPlaying },
                progress = progress,
                onProgressChange = { progress = it },
                currentTime = formatTime(currentSeconds),
                totalTime = formatTime(totalSeconds)
            )
        }
    }
}

private enum class VoicePart(val label: String, val shortLabel: String) {
    Soprano("Soprano", "S."),
    Alto("Alto", "A."),
    Tenor("Tenor", "T."),
    Bass("Bass", "B.")
}

private data class StaffNote(
    val index: Int,
    val lineIndex: Int,
    val voice: Int = 1,
    val type: String = "quarter"
)

@Composable
private fun PracticeTopBar(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(8.dp)
                .background(VoxAccentGreen, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold
            ),
            color = VoxTextPrimary,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options)
            )
        }
    }
}

@Composable
private fun VoicePartCard(
    selectedPart: VoicePart,
    onPartSelected: (VoicePart) -> Unit,
    audioMuteEnabled: Boolean,
    onAudioMuteChange: (Boolean) -> Unit,
    visualFocusEnabled: Boolean,
    onVisualFocusChange: (Boolean) -> Unit
) {
    Surface(
        color = VoxCardBackground,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.voice_part_label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = VoxTextSubtitle
            )

            PartSelectorUI(
                selectedPart = selectedPart,
                onPartSelected = onPartSelected
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Outlined.VolumeOff,
                    label = stringResource(R.string.audio_mute_label),
                    checked = audioMuteEnabled,
                    onCheckedChange = onAudioMuteChange
                )
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Visibility,
                    label = stringResource(R.string.visual_focus_label),
                    checked = visualFocusEnabled,
                    onCheckedChange = onVisualFocusChange
                )
            }
        }
    }
}

@Composable
private fun PartSelectorUI(
    selectedPart: VoicePart,
    onPartSelected: (VoicePart) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VoicePart.values().forEach { part ->
            val isSelected = part == selectedPart
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clickable { onPartSelected(part) },
                color = if (isSelected) VoxPurplePrimary else Color.White,
                shape = RoundedCornerShape(999.dp),
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, VoxCardStroke)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = part.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isSelected) Color.White else VoxTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VoxCardStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoxPurplePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VoxTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VoxPurplePrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = VoxCardStroke
                )
            )
        }
    }
}

@Composable
private fun ScoreCard(
    selectedPart: VoicePart,
    visualFocusEnabled: Boolean,
    playheadProgress: Float,
    staffNotes: List<StaffNote>
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp)
        ) {
            AudioVisualMixer(
                selectedPart = selectedPart,
                visualFocusEnabled = visualFocusEnabled,
                playheadProgress = playheadProgress,
                staffNotes = staffNotes
            )
        }
    }
}

@Composable
private fun AudioVisualMixer(
    selectedPart: VoicePart,
    visualFocusEnabled: Boolean,
    playheadProgress: Float,
    staffNotes: List<StaffNote>
) {
    // Distribute notes to SATB parts based on voice number from MusicXML.
    // Voice 1 → Soprano, 2 → Alto, 3 → Tenor, 4 → Bass.
    // If the file only uses voice 1, fall back to pitch-range heuristic.
    val notesByPart = remember(staffNotes) {
        distributeNotesToParts(staffNotes)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollState = rememberScrollState()
    val staffHeight = 44.dp
    val rowSpacing = 18.dp
    val labelWidth = 24.dp
    val noteSpacing = 32.dp
    val startPadding = 12.dp
    val endPadding = 24.dp

    // Compute the maximum note count across all parts for proper width
    val maxNoteCount = notesByPart.values
        .maxOfOrNull { it.size }
        ?.coerceAtLeast(1) ?: 1
    val baseContentWidth = startPadding + endPadding + noteSpacing * (maxNoteCount - 1)

    // Measure the viewport width via onGloballyPositioned instead of
    // BoxWithConstraints (whose scope was not resolving, causing 0 width).
    var viewportWidthDp by remember { mutableStateOf(baseContentWidth) }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Part labels (S. A. T. B.) ───────────────────────────
        Column(
            modifier = Modifier
                .width(labelWidth)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            VoicePart.values().forEach { part ->
                Box(
                    modifier = Modifier.height(staffHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val labelAlpha = if (visualFocusEnabled && part != selectedPart) 0.2f else 1f
                    Text(
                        text = part.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = VoxTextSubtitle.copy(alpha = labelAlpha)
                    )
                }
            }
        }

        // ── Scrollable score area ───────────────────────────────
        // Use weight(1f) so the Row gives this child all remaining
        // space after the fixed-width label column.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        viewportWidthDp = coordinates.size.width.toDp()
                    }
                }
        ) {
            val contentWidth = baseContentWidth.coerceAtLeast(viewportWidthDp)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing)
                ) {
                    VoicePart.values().forEach { part ->
                        StaffCanvas(
                            notes = notesByPart[part].orEmpty(),
                            isActive = part == selectedPart,
                            visualFocusEnabled = visualFocusEnabled,
                            noteSpacing = noteSpacing,
                            startPadding = startPadding,
                            staffHeight = staffHeight
                        )
                    }
                }

                PlayheadLine(
                    progress = playheadProgress,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(contentWidth)
                )
            }
        }
    }
}

@Composable
private fun StaffCanvas(
    notes: List<StaffNote>,
    isActive: Boolean,
    visualFocusEnabled: Boolean,
    noteSpacing: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    staffHeight: androidx.compose.ui.unit.Dp
) {
    val contentAlpha = if (visualFocusEnabled && !isActive) 0.15f else 1f
    val lineColor = VoxCardStroke.copy(alpha = contentAlpha)
    val noteColor = if (isActive) VoxPurplePrimary else VoxTextSecondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(staffHeight)
    ) {
        val lineGap = size.height / 6f
        val top = lineGap
        val spacingPx = noteSpacing.toPx()
        val startPx = startPadding.toPx()

        // Draw 5 staff lines
        repeat(5) { index ->
            val y = top + index * lineGap
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        // Draw each note with proper music notation
        notes.forEachIndexed { idx, note ->
            val cx = startPx + (note.index * spacingPx)
            val cy = top + note.lineIndex * lineGap
            val color = if (isActive && idx == 0) VoxPurpleAccent else noteColor
            drawMusicalNote(
                cx = cx,
                cy = cy,
                noteType = note.type,
                color = color.copy(alpha = contentAlpha),
                lineGap = lineGap,
                lineIndex = note.lineIndex
            )
        }
    }
}

/**
 * Draws a single musical note with the correct notation shape:
 * whole (hollow, no stem), half (hollow + stem), quarter (filled + stem),
 * eighth (filled + stem + flag), 16th (filled + stem + 2 flags).
 */
private fun DrawScope.drawMusicalNote(
    cx: Float,
    cy: Float,
    noteType: String,
    color: Color,
    lineGap: Float,
    lineIndex: Int
) {
    val headW = lineGap * 1.4f
    val headH = lineGap * 0.85f
    val stemLen = lineGap * 3f
    val stemW = 1.5.dp.toPx()

    val isHollow = noteType == "whole" || noteType == "half"
    val hasStem = noteType != "whole"
    val flagCount = when (noteType) {
        "eighth" -> 1
        "16th" -> 2
        "32nd" -> 3
        else -> 0
    }
    // Stem direction: notes on or above middle line (index<=2) → stem down
    val stemDown = lineIndex <= 2

    // ── Note head (tilted oval) ──────────────────────────────
    val w = if (noteType == "whole") headW * 1.15f else headW
    rotate(degrees = -18f, pivot = Offset(cx, cy)) {
        if (isHollow) {
            drawOval(
                color = color,
                topLeft = Offset(cx - w / 2, cy - headH / 2),
                size = Size(w, headH),
                style = Stroke(width = 2f)
            )
        } else {
            drawOval(
                color = color,
                topLeft = Offset(cx - w / 2, cy - headH / 2),
                size = Size(w, headH)
            )
        }
    }

    if (!hasStem) return

    // ── Stem ─────────────────────────────────────────────────
    val stemX = if (stemDown) cx - headW * 0.42f else cx + headW * 0.42f
    val stemEndY = if (stemDown) cy + stemLen else cy - stemLen
    drawLine(
        color = color,
        start = Offset(stemX, cy),
        end = Offset(stemX, stemEndY),
        strokeWidth = stemW
    )

    // ── Flags ────────────────────────────────────────────────
    if (flagCount > 0) {
        val flagLen = lineGap * 1.6f
        for (i in 0 until flagCount) {
            val offset = i * lineGap * 0.65f
            val fStart = if (stemDown) stemEndY - offset else stemEndY + offset
            val curveDir = if (stemDown) -1f else 1f
            val path = Path().apply {
                moveTo(stemX, fStart)
                cubicTo(
                    stemX + flagLen * 0.3f, fStart + curveDir * lineGap * 0.4f,
                    stemX + flagLen * 0.7f, fStart + curveDir * lineGap * 0.9f,
                    stemX + flagLen * 0.4f, fStart + curveDir * lineGap * 1.6f
                )
            }
            drawPath(path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PlayheadLine(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val clamped = progress.coerceIn(0.05f, 0.95f)
        val x = size.width * clamped
        drawLine(
            color = VoxPurpleAccent,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlaybackControlBar(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    currentTime: String,
    totalTime: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.labelSmall,
                color = VoxTextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = totalTime,
                style = MaterialTheme.typography.labelSmall,
                color = VoxTextSecondary
            )
        }

        Slider(
            value = progress,
            onValueChange = onProgressChange,
            colors = SliderDefaults.colors(
                thumbColor = VoxPurplePrimary,
                activeTrackColor = VoxPurplePrimary,
                inactiveTrackColor = VoxCardStroke
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(R.string.cd_skip_previous),
                    tint = VoxTextSecondary
                )
            }

            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onPlayPause() },
                shape = CircleShape,
                color = VoxPurplePrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(R.string.cd_skip_next),
                    tint = VoxTextSecondary
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainder)
}

private fun sampleMusicXmlScore(title: String): MusicXmlScore {
    val notes = listOf(
        MusicXmlNote("C", 5, 4, voice = 1, type = "quarter"),
        MusicXmlNote("E", 5, 4, voice = 1, type = "quarter"),
        MusicXmlNote("G", 5, 2, voice = 1, type = "half"),
        MusicXmlNote("A", 5, 1, voice = 1, type = "whole"),
        MusicXmlNote("A", 4, 4, voice = 2, type = "eighth"),
        MusicXmlNote("G", 4, 4, voice = 2, type = "quarter"),
        MusicXmlNote("F", 4, 4, voice = 2, type = "quarter"),
        MusicXmlNote("E", 4, 2, voice = 2, type = "half"),
        MusicXmlNote("E", 3, 4, voice = 3, type = "quarter"),
        MusicXmlNote("D", 3, 4, voice = 3, type = "eighth"),
        MusicXmlNote("C", 3, 4, voice = 3, type = "quarter"),
        MusicXmlNote("B", 2, 4, voice = 4, type = "half"),
        MusicXmlNote("A", 2, 4, voice = 4, type = "quarter"),
        MusicXmlNote("G", 2, 4, voice = 4, type = "quarter")
    )
    return MusicXmlScore(
        title = title,
        composer = "Unknown Composer",
        notes = notes,
        totalSeconds = 180
    )
}

/**
 * Converts parsed MusicXmlNotes into visual StaffNote positions,
 * preserving voice assignment and note type for proper rendering.
 */
private fun buildStaffNotes(
    notes: List<MusicXmlNote>
): List<StaffNote> {
    if (notes.isEmpty()) {
        return listOf(
            StaffNote(0, 2, voice = 1, type = "quarter"),
            StaffNote(2, 3, voice = 2, type = "half"),
            StaffNote(1, 1, voice = 3, type = "eighth"),
            StaffNote(3, 4, voice = 4, type = "whole")
        )
    }

    // Group by voice and assign sequential index within each voice
    val byVoice = notes.groupBy { it.voice }
    return byVoice.flatMap { (voice, voiceNotes) ->
        voiceNotes.mapIndexed { index, note ->
            StaffNote(
                index = index,
                lineIndex = mapPitchToLine(note.step, note.octave),
                voice = voice,
                type = note.type
            )
        }
    }
}

/**
 * Distributes StaffNotes into the four SATB parts.
 *
 * Strategy:
 *  1. If the MusicXML has distinct voices (1-4), map them directly.
 *  2. If all notes share voice 1 (common in single-staff exports),
 *     distribute by pitch range: highest → Soprano, lowest → Bass.
 */
private fun distributeNotesToParts(
    staffNotes: List<StaffNote>
): Map<VoicePart, List<StaffNote>> {
    val distinctVoices = staffNotes.map { it.voice }.distinct().sorted()

    // Direct mapping when the file has multiple voices
    if (distinctVoices.size >= 2) {
        val voiceToPart = mutableMapOf<Int, VoicePart>()
        val parts = VoicePart.values()
        distinctVoices.forEachIndexed { idx, v ->
            voiceToPart[v] = parts[idx.coerceAtMost(parts.lastIndex)]
        }
        val grouped = staffNotes.groupBy { voiceToPart[it.voice] ?: VoicePart.Soprano }
        // Re-index notes within each part so they space correctly
        return parts.associateWith { part ->
            (grouped[part] ?: emptyList()).mapIndexed { i, n ->
                n.copy(index = i)
            }
        }
    }

    // Fallback: single voice → split by pitch quartile
    if (staffNotes.isEmpty()) {
        return VoicePart.values().associateWith { emptyList() }
    }

    val sorted = staffNotes.sortedBy { it.lineIndex }
    val chunkSize = (sorted.size / 4).coerceAtLeast(1)
    val parts = VoicePart.values()
    return parts.mapIndexed { idx, part ->
        val start = idx * chunkSize
        val end = if (idx == parts.lastIndex) sorted.size else ((idx + 1) * chunkSize).coerceAtMost(sorted.size)
        val chunk = if (start < sorted.size) sorted.subList(start, end) else emptyList()
        part to chunk.mapIndexed { i, n -> n.copy(index = i) }
    }.toMap()
}

/**
 * Maps a note pitch (step + octave) to a staff line index (0-4).
 *
 * Uses standard treble-clef positioning:
 *  Line 0 (top)    = F5
 *  Line 1          = D5
 *  Line 2 (middle) = B4
 *  Line 3          = G4
 *  Line 4 (bottom) = E4
 *
 * Notes outside this range are clamped to the nearest line.
 */
private fun mapPitchToLine(step: String, octave: Int): Int {
    val normalized = step.replace("b", "").replace("#", "").uppercase()
    // Diatonic pitch number (C=0, D=1, ... B=6)
    val stepIndex = when (normalized) {
        "C" -> 0
        "D" -> 1
        "E" -> 2
        "F" -> 3
        "G" -> 4
        "A" -> 5
        "B" -> 6
        else -> 0
    }

    // Absolute diatonic position (C4 = 28)
    val pitchPos = (octave * 7) + stepIndex

    // Reference: E4 (bottom staff line, index 4) = position 30
    // Each line spans 2 diatonic steps
    // Line 4 = E4 (30), Line 3 = G4 (32), Line 2 = B4 (34),
    // Line 1 = D5 (36), Line 0 = F5 (38)
    val e4Pos = 30
    val lineFromBottom = (pitchPos - e4Pos + 1) / 2
    val lineIndex = 4 - lineFromBottom

    return lineIndex.coerceIn(0, 4)
}
