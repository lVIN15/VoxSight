package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cit.kaido.voxsight.storage.ScoreRecordingItem
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import kotlin.math.abs

@Composable
fun TakeSummaryFeedbackDialog(
    item: ScoreRecordingItem,
    takeNumber: Int,
    onDismiss: () -> Unit
) {
    val summary = item.summary
    val acc = item.accuracyPercentage ?: summary?.accuracyPercentage?.toInt() ?: 0

    val accColor = when {
        acc >= 80 -> Color(0xFF2E7D32)
        acc >= 60 -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, VoxCardStroke.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
            color = Color.White,
            shadowElevation = 16.dp
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Take #$takeNumber Feedback",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = VoxPurplePrimary
                            )
                        }
                        Text(
                            text = item.formattedDate,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color.Gray
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFFF2F3F9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Accuracy & Progression Hero Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF8F6FB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PITCH ACCURACY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "$acc%",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 44.sp
                                ),
                                color = accColor
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Accuracy Delta Comparison Pill
                            if (item.accuracyDelta != null) {
                                val delta = item.accuracyDelta
                                val isPositive = delta > 0
                                val isNegative = delta < 0
                                val trendColor = when {
                                    isPositive -> Color(0xFF2E7D32)
                                    isNegative -> Color(0xFFC62828)
                                    else -> Color.Gray
                                }
                                val trendIcon = if (isPositive) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown
                                val trendText = when {
                                    isPositive -> "+$delta% Improvement vs Previous Take"
                                    isNegative -> "$delta% vs Previous Take"
                                    else -> "Same Accuracy as Previous Take"
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = trendColor.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = trendIcon,
                                            contentDescription = null,
                                            tint = trendColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = trendText,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            color = trendColor
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Initial Baseline Take",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = Color.Gray
                                )
                            }

                            if (!summary?.satbVoice.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Part: ${summary?.satbVoice?.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = VoxPurplePrimary
                                )
                            }
                        }
                    }

                    // Notes Attempted & Match Stats
                    if (summary != null && summary.totalNotesAttempted > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFAF9FC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEBF0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${summary.correctNotes} / ${summary.totalNotesAttempted}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF191C20)
                                    )
                                    Text(
                                        text = "Notes In-Tune",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color.Gray
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(30.dp)
                                        .background(Color(0xFFE2E2E6))
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val avgCents = abs(summary.averageDeviationCents)
                                    Text(
                                        text = "±${String.format(java.util.Locale.US, "%.1f", avgCents)}¢",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (avgCents <= 20f) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "Avg Deviation",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Vocal Range Exhibited
                    if (!summary?.vocalHighlightHighest.isNullOrBlank() && !summary?.vocalHighlightLowest.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEBF0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Vocal Range Exhibited",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "Lowest: ${summary?.vocalHighlightLowest}  •  Highest: ${summary?.vocalHighlightHighest}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF191C20)
                                    )
                                }
                            }
                        }
                    }

                    // Top Problematic Measure
                    summary?.topProblematicMeasure?.let { measure ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF8F0),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE0B2))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Focus Passage",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "Measure ${measure.measureNumber} (${measure.mistakeCount} missed ${if (measure.mistakeCount == 1) "note" else "notes"})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF191C20)
                                    )
                                }
                            }
                        }
                    }

                    // Problematic Notes
                    if (!summary?.problematicNotes.isNullOrEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEBF0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Notes Needing Intonation Attention",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                summary!!.problematicNotes.forEach { prob ->
                                    val tendency = if (prob.isSharp) "Sharp (+)" else "Flat (-)"
                                    val cents = String.format(java.util.Locale.US, "%.0f", abs(prob.averageDeviation))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prob.noteName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF191C20)
                                        )
                                        Text(
                                            text = "$tendency $cents¢",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (prob.isSharp) Color(0xFFC62828) else Color(0xFF1565C0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VoxPurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "CLOSE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
