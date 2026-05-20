package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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

private data class StaffNote(val index: Int, val lineIndex: Int)

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
    val notesByPart = remember(staffNotes) {
        VoicePart.values().associateWith { staffNotes }
    }

    val scrollState = rememberScrollState()
    val staffHeight = 44.dp
    val rowSpacing = 18.dp
    val labelWidth = 24.dp
    val noteSpacing = 32.dp
    val startPadding = 12.dp
    val endPadding = 24.dp
    val noteCount = staffNotes.size.coerceAtLeast(1)
    val baseContentWidth = startPadding + endPadding + noteSpacing * (noteCount - 1)

    Row(modifier = Modifier.fillMaxSize()) {
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val contentWidth = baseContentWidth.coerceAtLeast(maxWidth)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier.width(contentWidth),
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
    val contentAlpha = if (visualFocusEnabled && !isActive) 0.2f else 1f
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

        repeat(5) { index ->
            val y = top + index * lineGap
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        notes.forEachIndexed { idx, note ->
            val x = startPx + (note.index * spacingPx)
            val y = top + note.lineIndex * lineGap
            val color = if (isActive && idx == 0) VoxPurpleAccent else noteColor
            drawCircle(
                color = color.copy(alpha = contentAlpha),
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
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
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
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
        MusicXmlNote("C", 4, 4),
        MusicXmlNote("E", 4, 4),
        MusicXmlNote("G", 4, 4),
        MusicXmlNote("A", 4, 4)
    )
    return MusicXmlScore(
        title = title,
        composer = "Unknown Composer",
        notes = notes,
        totalSeconds = 180
    )
}

private fun buildStaffNotes(
    notes: List<MusicXmlNote>
): List<StaffNote> {
    if (notes.isEmpty()) {
        return listOf(
            StaffNote(0, 2),
            StaffNote(2, 1),
            StaffNote(4, 3)
        )
    }

    return notes.mapIndexed { index, note ->
        val lineIndex = mapPitchToLine(note.step, note.octave)
        StaffNote(index, lineIndex)
    }
}

private fun mapPitchToLine(step: String, octave: Int): Int {
    val normalized = step.replace("b", "").replace("#", "").uppercase()
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

    val baseIndex = 4 * 7
    val pitchIndex = (octave * 7) + stepIndex
    val delta = pitchIndex - baseIndex
    val wrapped = ((delta % 5) + 5) % 5
    return 4 - wrapped
}
