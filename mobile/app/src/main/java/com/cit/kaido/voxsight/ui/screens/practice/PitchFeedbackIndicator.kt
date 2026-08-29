package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.pitch.ColorBand
import com.cit.kaido.voxsight.pitch.FeedbackResult
import com.cit.kaido.voxsight.pitch.PitchComparator
import com.cit.kaido.voxsight.pitch.PitchDirection
import com.cit.kaido.voxsight.pitch.PitchUiState
import com.cit.kaido.voxsight.ui.theme.VoxAccentGreen
import com.cit.kaido.voxsight.ui.theme.VoxCardBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import kotlin.math.abs

// ── Palette for the three SRS color bands ──────────────────────────────────
private val GreenAccent = Color(0xFF4CAF50)
private val YellowAccent = Color(0xFFFFB300)
private val RedAccent = Color(0xFFE53935)

/**
 * The main UI component for Module 4 real-time pitch feedback.
 * Depending on the [PitchUiState], it displays either an idle state,
 * a listening indicator, a noise warning, or the active deviation meter.
 */
@Composable
fun PitchFeedbackIndicator(
    state: PitchUiState,
    modifier: Modifier = Modifier
) {
    val result = (state as? PitchUiState.Active)?.result
    val isMatch = result?.isMatch == true

    val targetColor = when (state) {
        is PitchUiState.Active -> if (result!!.isMatch) GreenAccent else result.colorBand.toColor()
        is PitchUiState.NoiseWarning -> YellowAccent
        is PitchUiState.Listening -> Color(0xFF7E57C2) // VoxPurpleAccent
        else -> VoxTextSubtitle
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "pitch_color"
    )

    val clampedCents = result?.deviationCents?.coerceIn(-200f, 200f) ?: 0f
    val normDeviation by animateFloatAsState(
        targetValue = clampedCents / 200f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pitch_needle"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VoxCardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, VoxCardStroke)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            DeviationMeter(
                normDeviation = normDeviation,
                accentColor = animatedColor,
                isMatch = isMatch
            )
        }
    }
}

/**
 * Horizontal deviation meter drawn on a [Canvas].
 *
 * Layout:
 *  • Full-width grey track
 *  • Coloured filled arc from centre to needle position
 *  • Needle tick mark at the deviation position
 *  • Centre-line marker (the "zero / on-pitch" reference)
 *
 * [normDeviation] ranges from -1.0 (200 ¢ flat) to +1.0 (200 ¢ sharp).
 */
@Composable
private fun DeviationMeter(
    normDeviation: Float,
    accentColor: Color,
    isMatch: Boolean
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val trackH    = 6.dp.toPx()
        val trackY    = (size.height - trackH) / 2f
        val trackR    = trackH / 2f
        val centreX   = size.width / 2f
        val needleX   = centreX + normDeviation * centreX   // maps ±1 → ±half-width
        val clampedNX = needleX.coerceIn(trackR, size.width - trackR)

        // ── Background track ────────────────────────────────────────
        drawRoundRect(
            color = VoxCardStroke,
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackR)
        )

        // ── Fill from centre to needle ──────────────────────────────
        val fillLeft  = minOf(centreX, clampedNX)
        val fillRight = maxOf(centreX, clampedNX)
        if (fillRight - fillLeft > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.7f)),
                    startX = fillLeft,
                    endX = fillRight
                ),
                topLeft = Offset(fillLeft, trackY),
                size = Size(fillRight - fillLeft, trackH)
            )
        }

        // ── Centre reference line ───────────────────────────────────
        val centreMarkH = 14.dp.toPx()
        drawLine(
            color = if (isMatch) accentColor else VoxTextSubtitle,
            start = Offset(centreX, trackY - (centreMarkH - trackH) / 2f),
            end   = Offset(centreX, trackY + (centreMarkH + trackH) / 2f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // ── Needle tick ─────────────────────────────────────────────
        val needleH = 20.dp.toPx()
        drawLine(
            color = accentColor,
            start = Offset(clampedNX, trackY - (needleH - trackH) / 2f),
            end   = Offset(clampedNX, trackY + (needleH + trackH) / 2f),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )

        // ── Needle cap (circle) ─────────────────────────────────────
        val capR = 5.dp.toPx()
        drawCircle(
            color = accentColor,
            radius = capR,
            center = Offset(clampedNX, size.height / 2f),
            style = Stroke(width = 2.5f)
        )
    }
}
