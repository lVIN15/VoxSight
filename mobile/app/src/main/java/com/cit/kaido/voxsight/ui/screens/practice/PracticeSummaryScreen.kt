package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.ui.components.RealMeasureNotationView
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.pitch.PitchAttempt

@Composable
fun PracticeSummaryScreen(
    summary: SessionSummary,
    score: MusicXmlScore? = null,
    selectedVoice: SATBVoice = SATBVoice.SOPRANO,
    pitchAttempts: List<PitchAttempt> = emptyList(),
    onBackToLibrary: () -> Unit,
    onRepeatPractice: () -> Unit = {}
) {
    val accuracy = summary.accuracyPercentage.toInt()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp) // padding for the bottom CTA
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackToLibrary,
                    modifier = Modifier.background(Color(0xFFF2F3F9), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = VoxPurplePrimary
                    )
                }
                
                Text(
                    text = "Practice Summary",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF191C20)
                )

                // Spacer matching back button size to keep title centered
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Accuracy Visualization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(256.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 16.dp.toPx()
                        // Background ring
                        drawArc(
                            color = Color(0xFFE7E8EE),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress ring
                        val sweep = (accuracy / 100f) * 360f
                        drawArc(
                            color = VoxPurplePrimary,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$accuracy%",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = VoxPurplePrimary
                        )
                        Text(
                            text = "PITCH ACCURACY",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = Color(0xFF4A4452)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Analytics Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                
                // Vocal Highlights Section
                if (summary.vocalHighlight != null) {
                    Text(
                        text = "Vocal Range Highlights",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = Color(0xFF191C20)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    VocalHighlightCard(summary.vocalHighlight)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Top Problematic Area Section (Displays the exact measure with the most mistakes)
                if (summary.topProblematicMeasure != null) {
                    val measure = summary.topProblematicMeasure
                    Text(
                        text = "Top Area to Practice",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = Color(0xFF191C20)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val issueLabel = if (measure.mistakeCount > 0) {
                        "${measure.mistakeCount} ${if (measure.mistakeCount == 1) "MISTAKE" else "MISTAKES"}${if (measure.averageDeviation != 0f) " • " + (if (measure.isSharp) "SHARP" else "FLAT") else ""}"
                    } else {
                        if (measure.isSharp) "SHARP" else "FLAT"
                    }

                    if (score != null) {
                        // Render authentic 5-line staff sheet music notation for the measure with the most mistakes
                        RealMeasureNotationView(
                            measureNumber = measure.measureNumber,
                            score = score,
                            voice = selectedVoice,
                            pitchAttempts = pitchAttempts
                        )
                    } else {
                        MeasureReviewCard(
                            measureName = "Measure ${measure.measureNumber}",
                            issueType = issueLabel,
                            isSharp = measure.isSharp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (summary.problematicNotes.isNotEmpty()) {
                    val note = summary.problematicNotes.first()
                    Text(
                        text = "Top Area to Practice",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = Color(0xFF191C20)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val noteMeasure = score?.notes?.firstOrNull { it.step.equals(note.noteName.take(1), true) }?.measureNumber ?: 1
                    if (score != null) {
                        RealMeasureNotationView(
                            measureNumber = noteMeasure,
                            score = score,
                            voice = selectedVoice,
                            pitchAttempts = pitchAttempts
                        )
                    } else {
                        MeasureReviewCard(
                            measureName = "Note ${note.noteName}",
                            issueType = if (note.isSharp) "SHARP" else "FLAT",
                            isSharp = note.isSharp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (summary.totalNotesAttempted > 0) {
                    Text(
                        text = "Amazing Job! No significant pitch errors detected.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }

        // Bottom CTA
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(VoxBackground.copy(alpha = 0.9f))
                .border(1.dp, Color(0xFFF2F3F9))
                .padding(24.dp)
        ) {
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(VoxPurplePrimary, Color(0xFF4A148C))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(gradientBrush)
                    .clickable { onRepeatPractice() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Repeat Practice",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun VocalHighlightCard(highlight: VocalHighlight) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(90.dp)
                    .background(Color(0xFF4CAF50)) // Green indicator
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp)
            ) {
                Text(
                    text = "Vocal Range Exhibited",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Lowest Hit", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(highlight.lowestNote, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Highest Hit", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(highlight.highestNote, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureReviewCard(
    measureName: String,
    issueType: String,
    isSharp: Boolean
) {
    val indicatorColor = if (isSharp) Color(0xFFFBC02D) else Color(0xFFBA1A1A)
    val icon = if (isSharp) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left border indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(110.dp) // approximate height of card content
                    .background(indicatorColor)
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = measureName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF191C20)
                        )
                        Text(
                            text = issueType,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                            color = indicatorColor
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = issueType,
                        tint = indicatorColor
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Abstract notation snippet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2F3F9))
                        .border(1.dp, indicatorColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Staff lines
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFCDC3D4)))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFCDC3D4)))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFCDC3D4)))
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                    // Music note shifted up or down
                    val yOffset = if (isSharp) (-12).dp else 12.dp
                    Icon(
                        imageVector = Icons.Outlined.MusicNote, // Using standard outline as placeholder for filled
                        contentDescription = "Note",
                        tint = indicatorColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(top = if (isSharp) 0.dp else yOffset, bottom = if (!isSharp) 0.dp else (-yOffset))
                    )
                }
            }
        }
    }
}
