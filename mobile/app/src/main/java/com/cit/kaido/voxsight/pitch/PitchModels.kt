package com.cit.kaido.voxsight.pitch

import androidx.compose.ui.graphics.Color

/**
 * FeedbackResult
 *
 * Immutable verdict produced by [PitchComparator.evaluateMatch].
 * Consumed by the UI to choose the visual indicator.
 */
data class FeedbackResult(
    val isMatch: Boolean,
    val deviationCents: Float,
    val captureTimestamp: Long = System.currentTimeMillis(),
    val colorBand: ColorBand = ColorBand.fromCents(deviationCents)
) {
    val direction: PitchDirection
        get() = when {
            isMatch -> PitchDirection.ON_PITCH
            deviationCents > 0f -> PitchDirection.SHARP
            else -> PitchDirection.FLAT
        }
}

enum class ColorBand {
    GREEN, YELLOW, RED;

    fun toColor(): Color = when (this) {
        GREEN -> Color(0xFF4CAF50)
        YELLOW -> Color(0xFFFFB300)
        RED -> Color(0xFFE53935)
    }

    companion object {
        fun fromCents(deviationCents: Float): ColorBand {
            val abs = kotlin.math.abs(deviationCents)
            return when {
                abs <= 50f -> GREEN
                abs <= 100f -> YELLOW
                else -> RED
            }
        }
    }
}

enum class PitchDirection {
    SHARP, FLAT, ON_PITCH
}

sealed class PitchUiState {
    object Idle : PitchUiState()
    object Listening : PitchUiState()
    object NoiseWarning : PitchUiState()
    data class Active(
        val result: FeedbackResult,
        val targetNoteName: String = ""
    ) : PitchUiState()
}
