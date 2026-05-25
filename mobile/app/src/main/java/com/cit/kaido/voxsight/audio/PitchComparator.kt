package com.cit.kaido.voxsight.audio

import kotlin.math.log2
import kotlin.math.pow

/**
 * PitchComparator
 *
 * Performs the mathematical core of the pitch feedback loop:
 *
 *  1. Converts a (step, octave) MusicXML note pair into its equal-temperament
 *     frequency in Hz.
 *  2. Computes the signed cent deviation between a [DetectedPitch] and a
 *     [TargetNote].
 *  3. Produces a [FeedbackResult] verdict.
 *
 * SDD Class Diagram:
 *   PitchComparator
 *     -TOLERANCE_CENTS : int = 5
 *     +calculateCentDeviation(detected : DetectedPitch, target : TargetNote) : float
 *     +evaluateMatch(deviation : float) : FeedbackResult
 *
 * All methods are pure functions — no state, no threading concerns.
 */
object PitchComparator {

    // ── SDD-mandated match tolerance ─────────────────────────────────────────

    /**
     * A result is classified as a *match* when the absolute cent deviation
     * is within this threshold.
     * Source: SDD §4.1 Class Diagram  ("TOLERANCE_CENTS : int = 5").
     */
    const val TOLERANCE_CENTS: Int = 5

    // ── MIDI / frequency lookup table ─────────────────────────────────────────

    /**
     * Equal-temperament semitone offsets within an octave (C = 0).
     * Sharps are used as canonical spellings; flats are mapped to their
     * enharmonic equivalents (e.g. Bb → A#).
     */
    private val STEP_TO_SEMITONE: Map<String, Int> = mapOf(
        "C"  to 0,
        "C#" to 1, "DB" to 1,
        "D"  to 2,
        "D#" to 3, "EB" to 3,
        "E"  to 4,
        "F"  to 5,
        "F#" to 6, "GB" to 6,
        "G"  to 7,
        "G#" to 8, "AB" to 8,
        "A"  to 9,
        "A#" to 10, "BB" to 10,
        "B"  to 11
    )

    /** Reference: A4 = 440 Hz, MIDI note 69. */
    private const val A4_HZ = 440.0
    private const val A4_MIDI = 69

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts a MusicXML [step] (e.g. "C", "F#") and [octave] (e.g. 4) into
     * the corresponding equal-temperament frequency in Hz.
     *
     * Formula:
     *   midiNote = (octave + 1) * 12 + semitone
     *   freq     = 440 * 2^((midiNote - 69) / 12)
     *
     * Returns `null` if [step] is not a recognised note name.
     */
    fun noteToHz(step: String, octave: Int): Float? {
        val semitone = STEP_TO_SEMITONE[step.uppercase()] ?: return null
        // In MIDI: C4 = 60, so C-octave starts at (octave+1)*12.
        val midiNote = (octave + 1) * 12 + semitone
        return (A4_HZ * 2.0.pow((midiNote - A4_MIDI) / 12.0)).toFloat()
    }

    /**
     * Computes the **signed** cent deviation of [detected] relative to
     * [target].
     *
     * Formula (standard musicology):
     *   cents = 1200 × log₂(detectedHz / targetHz)
     *
     * Positive values → singer is SHARP (above target).
     * Negative values → singer is FLAT  (below target).
     *
     * Returns `Float.NaN` if either frequency is ≤ 0 (i.e., no valid pitch).
     */
    fun calculateCentDeviation(detected: DetectedPitch, target: TargetNote): Float {
        if (detected.frequencyHz <= 0f || target.pitchFrequencyHz <= 0f) {
            return Float.NaN
        }
        val ratio = detected.frequencyHz.toDouble() / target.pitchFrequencyHz.toDouble()
        return (1200.0 * log2(ratio)).toFloat()
    }

    /**
     * Evaluates a pre-computed [deviationCents] value and wraps it in a
     * [FeedbackResult].
     *
     * A result is marked as [FeedbackResult.isMatch] = true when
     * |deviationCents| ≤ [TOLERANCE_CENTS] (SDD §4.1: ±5 cents).
     *
     * [FeedbackResult.colorBand] is derived from the SRS visual thresholds:
     *   Green  ≤ 50 cents, Yellow ≤ 100, Red > 100.
     *
     * Returns `null` if [deviationCents] is NaN (no detectable pitch).
     */
    fun evaluateMatch(deviationCents: Float): FeedbackResult? {
        if (deviationCents.isNaN()) return null
        val isMatch = kotlin.math.abs(deviationCents) <= TOLERANCE_CENTS
        return FeedbackResult(
            isMatch = isMatch,
            deviationCents = deviationCents
        )
    }

    /**
     * Convenience overload: computes deviation AND evaluates match in one call.
     *
     * Returns `null` if no valid deviation can be computed.
     */
    fun compare(detected: DetectedPitch, target: TargetNote): FeedbackResult? {
        val deviation = calculateCentDeviation(detected, target)
        return evaluateMatch(deviation)
    }

    /**
     * Produces a human-readable note label (e.g. "A4", "C#5") from a
     * [step] + [octave] pair. Used only for debug overlays and logging.
     */
    fun noteLabel(step: String, octave: Int): String = "$step$octave"
}
