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
import androidx.compose.material.icons.outlined.MoreVert
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
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary

@Composable
fun PracticeSummaryScreen(
    accuracy: Int,
    onBackToLibrary: () -> Unit
) {
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

                IconButton(
                    onClick = { },
                    modifier = Modifier.background(Color(0xFFF2F3F9), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More",
                        tint = VoxPurplePrimary
                    )
                }
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

            // Measures to Review
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Measures to Review",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color(0xFF191C20)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Measure 12 (Flat)
                MeasureReviewCard(
                    measureName = "Measure 12",
                    issueType = "FLAT",
                    isSharp = false
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Measure 24 (Sharp)
                MeasureReviewCard(
                    measureName = "Measure 24",
                    issueType = "SHARP",
                    isSharp = true
                )
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
                    .clickable { /* Repeat flagged measures logic */ },
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
                        text = "Repeat Flagged Measures",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                        color = Color.White
                    )
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
