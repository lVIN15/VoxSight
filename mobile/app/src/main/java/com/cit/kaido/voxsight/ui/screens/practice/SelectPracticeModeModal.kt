package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cit.kaido.voxsight.audio.PerformanceAudioPlayer
import com.cit.kaido.voxsight.storage.ScoreRecordingItem
import com.cit.kaido.voxsight.storage.ScoreRecordingManager
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurpleLight
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import java.io.File
import java.util.Locale

@Composable
fun SelectPracticeModeModal(
    scoreTitle: String? = null,
    onDismiss: () -> Unit,
    onModeSelected: (Boolean) -> Unit // true if mic enabled (Test Pitch), false if Listen Only
) {
    val context = LocalContext.current
    var isViewingRecordings by remember { mutableStateOf(false) }
    var recordings by remember(scoreTitle) {
        mutableStateOf(ScoreRecordingManager.getRecordings(context, scoreTitle))
    }
    var activePlayingFile by remember { mutableStateOf<File?>(null) }

    val player = remember { PerformanceAudioPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    val isPlaying by player.isPlaying.collectAsState()
    val currentPosMs by player.currentPositionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val currentSpeed by player.playbackSpeed.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, VoxCardStroke.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isViewingRecordings) {
                    // ── Mode Selection View ────────────────────────
                    Text(
                        text = "Select Practice Mode",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = VoxPurplePrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!scoreTitle.isNullOrBlank()) "How would you like to practice \"$scoreTitle\"?" else "How would you like to practice this score?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4A4452),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Listen Only Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onModeSelected(false) },
                        color = Color(0xFFF2F3F9),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE1E2E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Headphones,
                                    contentDescription = "Listen Only",
                                    tint = VoxPurplePrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Listen Only",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF191C20)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Study the notes and timing. Microphone is disabled.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = Color(0xFF4A4452)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Pitch Option
                    val gradientBrush = Brush.linearGradient(
                        colors = listOf(VoxPurplePrimary, Color(0xFF4A148C))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(gradientBrush)
                            .clickable { onModeSelected(true) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Test Pitch",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Test Pitch",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sing along and get real-time accuracy feedback.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = VoxPurpleLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Past Vocal Takes Option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isViewingRecordings = true },
                        color = Color(0xFFF9F6FC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VoxPurplePrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = "Past Vocal Takes",
                                    tint = VoxPurplePrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Past Vocal Takes",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF191C20)
                                    )
                                    if (recordings.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(VoxPurplePrimary.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${recordings.size} Take${if (recordings.size == 1) "" else "s"}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = VoxPurplePrimary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (recordings.isNotEmpty()) {
                                        "Listen to your recorded practice sessions to track improvement."
                                    } else {
                                        "No recordings yet. Complete a Test Pitch take to save playback."
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = Color(0xFF4A4452)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CANCEL",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                            color = Color(0xFF4A4452)
                        )
                    }

                } else {
                    // ── Past Vocal Takes Review View ────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                player.pause()
                                isViewingRecordings = false
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = VoxPurplePrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Past Vocal Takes",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = VoxPurplePrimary
                            )
                            if (!scoreTitle.isNullOrBlank()) {
                                Text(
                                    text = scoreTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (recordings.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MicNone,
                                contentDescription = "No recordings",
                                tint = Color.LightGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No recorded takes yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF191C20)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Start a practice session with Test Pitch enabled! Your singing will automatically be recorded for playback comparison.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VoxPurplePrimary)
                                    .clickable { onModeSelected(true) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Start Test Pitch",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else {
                        // Scrollable List of Takes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            recordings.forEachIndexed { index, item ->
                                val isCurrent = activePlayingFile?.absolutePath == item.file.absolutePath
                                TakePlaybackCard(
                                    item = item,
                                    takeNumber = recordings.size - index,
                                    isCurrentlyPlaying = isCurrent && isPlaying,
                                    isCurrentSelection = isCurrent,
                                    currentPosMs = if (isCurrent) currentPosMs else 0,
                                    durationMs = if (isCurrent) durationMs else item.durationMs,
                                    speed = if (isCurrent) currentSpeed else 1.0f,
                                    onPlayPause = {
                                        if (isCurrent && isPlaying) {
                                            player.pause()
                                        } else {
                                            if (!isCurrent) {
                                                activePlayingFile = item.file
                                                player.load(item.file)
                                            }
                                            player.play()
                                        }
                                    },
                                    onSeek = { targetMs ->
                                        if (isCurrent) player.seekTo(targetMs)
                                    },
                                    onSpeedChange = { nextSpeed ->
                                        if (isCurrent) player.setSpeed(nextSpeed)
                                    },
                                    onDelete = {
                                        if (isCurrent) {
                                            player.release()
                                            activePlayingFile = null
                                        }
                                        ScoreRecordingManager.deleteRecording(item.file)
                                        recordings = ScoreRecordingManager.getRecordings(context, scoreTitle)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = {
                        player.pause()
                        isViewingRecordings = false
                    }) {
                        Text(
                            text = "BACK TO MODES",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                            color = Color(0xFF4A4452)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TakePlaybackCard(
    item: ScoreRecordingItem,
    takeNumber: Int,
    isCurrentlyPlaying: Boolean,
    isCurrentSelection: Boolean,
    currentPosMs: Int,
    durationMs: Int,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = if (isCurrentSelection) Color(0xFFFBF9FD) else Color(0xFFF7F7FA),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentSelection) VoxPurplePrimary.copy(alpha = 0.4f) else Color(0xFFE7E8EE)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentlyPlaying) VoxPurplePrimary else Color(0xFFEAEAF0))
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isCurrentlyPlaying) "Pause" else "Play",
                        tint = if (isCurrentlyPlaying) Color.White else VoxPurplePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info: Take # and Timestamp
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Take #$takeNumber",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF191C20)
                        )
                        item.accuracyPercentage?.let { acc ->
                            Spacer(modifier = Modifier.width(8.dp))
                            val accColor = when {
                                acc >= 80 -> Color(0xFF2E7D32)
                                acc >= 60 -> Color(0xFFE65100)
                                else -> Color(0xFFC62828)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$acc% Acc",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = accColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.formattedDate,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color.Gray
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Take",
                        tint = Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Scrub & Speed controls when actively selected/playing
            if (isCurrentSelection) {
                Spacer(modifier = Modifier.height(10.dp))

                val safeDuration = durationMs.coerceAtLeast(1)
                val progress = (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                Slider(
                    value = progress,
                    onValueChange = { ratio ->
                        onSeek((ratio * safeDuration).toInt())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = VoxPurplePrimary,
                        activeTrackColor = VoxPurplePrimary,
                        inactiveTrackColor = Color(0xFFE7E8EE)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatAudioTime(currentPosMs)} / ${formatAudioTime(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )

                    // Speed toggle pill
                    val nextSpeed = when {
                        speed <= 0.85f -> 1.0f
                        speed in 0.95f..1.05f -> 1.2f
                        else -> 0.8f
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEAEAF0))
                            .clickable { onSpeedChange(nextSpeed) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = VoxPurplePrimary
                        )
                    }
                }
            }
        }
    }
}

private fun formatAudioTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
