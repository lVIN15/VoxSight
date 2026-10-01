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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import java.io.File
import java.util.Locale

@Composable
fun PastVocalTakesDialog(
    scoreTitle: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
        onDismissRequest = {
            player.release()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, VoxCardStroke.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Past Vocal Takes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
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

                    IconButton(
                        onClick = {
                            player.release()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFF2F3F9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (recordings.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
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
                            text = "Practice with Test Pitch enabled to automatically record your vocal takes for playback review.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // List of Takes
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recordings.forEachIndexed { index, item ->
                            val isCurrent = activePlayingFile?.absolutePath == item.file.absolutePath
                            TakeCardItem(
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

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = {
                        player.release()
                        onDismiss()
                    }) {
                        Text(
                            text = "DONE",
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
private fun TakeCardItem(
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
