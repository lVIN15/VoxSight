package com.cit.kaido.voxsight.pitch

import com.cit.kaido.voxsight.viewmodel.PitchFeedback
import kotlin.math.abs
import kotlin.math.log2

object PitchComparator {

    private const val TOLERANCE_CENTS = 50f

    fun calculateDeviation(detectedHz: Float, referenceHz: Float): Float {
        if (referenceHz <= 0f || detectedHz <= 0f) return Float.MAX_VALUE
        return 1200f * log2(detectedHz / referenceHz)
    }

    fun evaluate(deviationCents: Float): PitchFeedback {
        val absolute = abs(deviationCents)
        return when {
            absolute <= TOLERANCE_CENTS -> PitchFeedback.CORRECT
            absolute <= 100f -> PitchFeedback.CLOSE
            else -> PitchFeedback.INCORRECT
        }
    }

    fun isMatch(deviationCents: Float): Boolean {
        return abs(deviationCents) <= TOLERANCE_CENTS
    }
}