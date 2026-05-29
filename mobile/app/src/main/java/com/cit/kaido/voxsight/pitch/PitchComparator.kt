package com.cit.kaido.voxsight.pitch

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

object PitchComparator {

    const val TOLERANCE_CENTS = 10f

    /**
     * Calculates the deviation in cents between the detected frequency and the target frequency.
     * Formula: 1200 * log2(detectedHz / targetHz)
     */
    fun calculateCentDeviation(detectedHz: Float, targetHz: Float): Float {
        if (targetHz <= 0f || detectedHz <= 0f) return 0f
        return 1200f * log2(detectedHz / targetHz)
    }

    /**
     * Evaluates if the detected pitch is within the acceptable tolerance window.
     */
    fun isMatch(deviationCents: Float): Boolean {
        return abs(deviationCents) <= TOLERANCE_CENTS
    }

    /**
     * Converts a pitch name (e.g. "C4", "A4") to its target frequency in Hz.
     * Uses A4 = 440Hz standard.
     */
    fun calculateTargetFrequency(pitchName: String): Float {
        if (pitchName.isEmpty()) return 0f
        
        val regex = Regex("([A-G][b#]?)([0-9])")
        val match = regex.find(pitchName) ?: return 0f
        
        val note = match.groupValues[1]
        val octave = match.groupValues[2].toIntOrNull() ?: return 0f
        
        val semitonesFromC = mapOf(
            "C" to 0, "C#" to 1, "Db" to 1,
            "D" to 2, "D#" to 3, "Eb" to 3,
            "E" to 4,
            "F" to 5, "F#" to 6, "Gb" to 6,
            "G" to 7, "G#" to 8, "Ab" to 8,
            "A" to 9, "A#" to 10, "Bb" to 10,
            "B" to 11
        )
        
        val semitoneOffset = semitonesFromC[note] ?: 0
        // A4 is index 9, octave 4. Its total semitone distance from C0 is 4*12 + 9 = 57
        val currentDist = octave * 12 + semitoneOffset
        val distFromA4 = currentDist - 57
        
        return (440.0 * 2.0.pow(distFromA4 / 12.0)).toFloat()
    }
}
