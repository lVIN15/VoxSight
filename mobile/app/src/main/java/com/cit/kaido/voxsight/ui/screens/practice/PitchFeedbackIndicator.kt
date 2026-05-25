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
import com.cit.kaido.voxsight.audio.ColorBand
import com.cit.kaido.voxsight.audio.FeedbackResult
import com.cit.kaido.voxsight.audio.PitchComparator
import com.cit.kaido.voxsight.audio.PitchDirection
import com.cit.kaido.voxsight.ui.theme.VoxAccentGreen
import com.cit.kaido.voxsight.ui.theme.VoxCardBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import kotlin.math.abs

// ── Palette for the three SRS color bands ──────────────────────────────────

private val GreenAccent  = Color(0xFF2FBF71)   // matches VoxAccentGreen
private val YellowAccent = Color(0xFFFFC107)
private val RedAccent    = Color(0xFFE53935)
private val NeutralGrey  = Color(0xFFB0B0C0)

private fun ColorBand.toColor(): Color = when (this) {
    ColorBand.GREEN  -> GreenAccent
    ColorBand.YELLOW -> YellowAccent
    ColorBand.RED    -> RedAccent
}

// ── Sealed states visible to the composable ─────────────────────────────────

/**
 * Everything the [PitchFeedbackCard] needs to render.  Produced by
 * [PitchVisualizerController] and observed as Compose state.
 */
sealed interface PitchUiState {
    /** Microphone is off; card is hidden. */
    object Idle : PitchUiState

    /** Mic is on but no reliable pitch was detected this frame. */
    object Listening : PitchUiState

    /** Environmental noise is too high to make a reliable reading. */
    object NoiseWarning : PitchUiState

    /** A confident pitch was detected and compared against the target. */
    data class Active(val result: FeedbackResult, val targetNoteName: String) : PitchUiState
}

// ── Public entry-point composable ────────────────────────────────────────────

/**
 * Renders the Module 4 pitch-feedback overlay card.
 *
 * Shown only when [isMicEnabled] is true.  Animates smoothly between all
 * [PitchUiState] variants.
 *
 * SDD: RenderEngine.renderIndicator()  |  MAX_RENDER_LATENCY_MS = 500
 */
@Composable
fun PitchFeedbackCard(
    uiState: PitchUiState,
    isMicEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isMicEnabled) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VoxCardBackground,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        when (uiState) {
            is PitchUiState.Idle      -> ListeningRow(label = "Mic ready…", color = NeutralGrey)
            is PitchUiState.Listening -> ListeningRow(label = "Listening…", color = NeutralGrey)
            is PitchUiState.NoiseWarning -> NoiseWarningContent()
            is PitchUiState.Active    -> ActiveFeedbackContent(uiState)
        }
    }
}

// ── State-specific sub-composables ───────────────────────────────────────────

@Composable
private fun ListeningRow(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = VoxTextSubtitle
        )
    }
}

@Composable
private fun NoiseWarningContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(YellowAccent, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Noise warning — move to a quieter area",
            style = MaterialTheme.typography.labelMedium,
            color = VoxTextSubtitle
        )
    }
}

@Composable
private fun ActiveFeedbackContent(state: PitchUiState.Active) {
    val result = state.result

    // Animate indicator colour transitions
    val targetColor = if (result.isMatch) GreenAccent else result.colorBand.toColor()
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "pitch_color"
    )

    // Normalise deviation to ±1.0 for the needle position
    // Clamp at ±200 cents so the needle never leaves the track.
    val clampedCents = result.deviationCents.coerceIn(-200f, 200f)
    val normDeviation by animateFloatAsState(
        targetValue = clampedCents / 200f,        // -1.0 … +1.0
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pitch_needle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Row 1: status label + note name ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusLabel(result = result, color = animatedColor)

            if (state.targetNoteName.isNotBlank()) {
                Text(
                    text = "Target: ${state.targetNoteName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxTextSecondary
                )
            }
        }

        // ── Row 2: deviation meter ────────────────────────────────────
        DeviationMeter(
            normDeviation = normDeviation,
            accentColor = animatedColor,
            isMatch = result.isMatch
        )

        // ── Row 3: cents readout + direction label ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val centsText = when (result.direction) {
                PitchDirection.ON_PITCH -> "On pitch ✓"
                PitchDirection.SHARP    -> "+${abs(result.deviationCents).toInt()} ¢ sharp"
                PitchDirection.FLAT     -> "−${abs(result.deviationCents).toInt()} ¢ flat"
            }
            Text(
                text = centsText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = animatedColor
            )
            Text(
                text = "Tolerance ±${PitchComparator.TOLERANCE_CENTS} ¢",
                style = MaterialTheme.typography.labelSmall,
                color = VoxTextSubtitle
            )
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun StatusLabel(result: FeedbackResult, color: Color) {
    val label = if (result.isMatch) "● Match" else "● Off pitch"
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = color
    )
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
