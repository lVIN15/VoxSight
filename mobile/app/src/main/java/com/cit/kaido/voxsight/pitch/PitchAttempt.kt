package com.cit.kaido.voxsight.pitch

data class PitchAttempt(
    val eventId: String,         // Which exact note event in the score
    val targetHz: Float,         // Expected frequency
    val detectedHz: Float,       // What the user actually sang
    val deviationCents: Float,   // How far off
    val isMatch: Boolean,        // Within ±10 cents?
    val timestampMs: Long,       // When this attempt was recorded
    val noteName: String = "",   // Human readable note (e.g. C4)
    val measureNumber: Int = 1
)
