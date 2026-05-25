package com.cit.kaido.voxsight.audio

/**
 * DetectedPitch
 *
 * Immutable value object produced by [PitchDetectionEngine] and consumed by
 * [PitchComparator].
 *
 * SDD Data Model:
 *   DetectedPitch
 *     +frequencyHz : float
 *     +confidence  : float
 *
 * @param frequencyHz  Fundamental frequency in Hz as reported by TarsosDSP YIN.
 *                     Always > 0 when this object is constructed.
 * @param confidence   TarsosDSP probability score in range 0.0–1.0.
 * @param timestampMs  System wall-clock time (System.currentTimeMillis()) at
 *                     the moment of detection, used to link a [DetectedPitch]
 *                     to the correct [TargetNote] window.
 */
data class DetectedPitch(
    val frequencyHz: Float,
    val confidence: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * TargetNote
 *
 * Represents the expected note a singer should currently be singing.
 * Produced by [ScoreAlignmentManager] and consumed by [PitchComparator].
 *
 * SDD Data Model:
 *   TargetNote
 *     +pitchFrequencyHz : float
 *     +durationMs       : long
 *
 * @param pitchFrequencyHz  Ideal frequency of the note in Hz, derived from
 *                          the MusicXML step/octave values via equal temperament.
 * @param durationMs        Note duration in milliseconds, used by the alignment
 *                          manager to advance the playhead window.
 * @param noteName          Human-readable label, e.g. "A4", "C#5". Used only
 *                          for logging and debug overlays; not part of the
 *                          match logic.
 */
data class TargetNote(
    val pitchFrequencyHz: Float,
    val durationMs: Long,
    val noteName: String = ""
)

/**
 * FeedbackResult
 *
 * Immutable verdict produced by [PitchComparator.evaluateMatch].
 * Consumed by the RenderEngine (Phase 4) to choose the visual indicator.
 *
 * SDD Data Model:
 *   FeedbackResult
 *     +isMatch          : boolean
 *     +deviationCents   : float
 *     +captureTimestamp : long
 *
 * @param isMatch          True when |deviationCents| ≤ [PitchComparator.TOLERANCE_CENTS].
 * @param deviationCents   Signed cents deviation (+sharp, -flat).
 * @param captureTimestamp Wall-clock time this result was produced.
 * @param colorBand        Visual severity bucket derived from the SRS color
 *                         thresholds: GREEN / YELLOW / RED.
 */
data class FeedbackResult(
    val isMatch: Boolean,
    val deviationCents: Float,
    val captureTimestamp: Long = System.currentTimeMillis(),
    val colorBand: ColorBand = ColorBand.fromCents(deviationCents)
) {
    /**
     * Convenience: returns "SHARP" when the singer is above the target,
     * "FLAT" when below, or "ON_PITCH" when within tolerance.
     */
    val direction: PitchDirection
        get() = when {
            isMatch -> PitchDirection.ON_PITCH
            deviationCents > 0f -> PitchDirection.SHARP
            else -> PitchDirection.FLAT
        }
}

/**
 * Color band thresholds from the SRS:
 *   Green  — |deviation| ≤ 50 cents  (close enough for the user to hear as "on pitch")
 *   Yellow — 51–100 cents            (noticeably out of tune)
 *   Red    — > 100 cents             (clearly wrong note or half-step off)
 */
enum class ColorBand {
    GREEN, YELLOW, RED;

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

/**
 * Indicates whether the singer is above, below, or on the target pitch.
 */
enum class PitchDirection {
    SHARP, FLAT, ON_PITCH
}
