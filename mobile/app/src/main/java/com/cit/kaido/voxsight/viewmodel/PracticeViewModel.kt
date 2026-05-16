package com.cit.kaido.voxsight.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PracticeMode { NONE, LISTEN, TEST_PITCH }

enum class PitchFeedback { INACTIVE, CORRECT, CLOSE, INCORRECT }

data class NoteAttempt(
    val noteLabel: String,
    val detectedHz: Float,
    val deviationCents: Float,
    val isMatch: Boolean,
    val measureNumber: Int
)

data class PracticeUiState(
    val selectedMode: PracticeMode = PracticeMode.NONE,
    val isSessionActive: Boolean = false,
    val detectedHz: Float = 0f,
    val currentFeedback: PitchFeedback = PitchFeedback.INACTIVE,
    val deviationCents: Float = 0f,
    val attempts: List<NoteAttempt> = emptyList(),
    val overallAccuracy: Float = 0f,
    val flaggedMeasures: List<Int> = emptyList(),
    val hasMicPermission: Boolean = false,
    val showNoiseWarning: Boolean = false
)

class PracticeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState

    fun selectMode(mode: PracticeMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun setMicPermission(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasMicPermission = granted)
    }

    fun startSession() {
        _uiState.value = _uiState.value.copy(
            isSessionActive = true,
            attempts = emptyList(),
            overallAccuracy = 0f,
            flaggedMeasures = emptyList()
        )
    }

    fun updatePitchFeedback(detectedHz: Float, deviationCents: Float, feedback: PitchFeedback) {
        _uiState.value = _uiState.value.copy(
            detectedHz = detectedHz,
            deviationCents = deviationCents,
            currentFeedback = feedback
        )
    }

    fun setNoiseWarning(show: Boolean) {
        _uiState.value = _uiState.value.copy(showNoiseWarning = show)
    }

    fun logAttempt(attempt: NoteAttempt) {
        val updated = _uiState.value.attempts + attempt
        _uiState.value = _uiState.value.copy(attempts = updated)
    }

    fun endSession() {
        val attempts = _uiState.value.attempts
        if (attempts.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isSessionActive = false,
                currentFeedback = PitchFeedback.INACTIVE
            )
            return
        }

        val accuracy = (attempts.count { it.isMatch }.toFloat() / attempts.size) * 100f
        val flagged = attempts
            .filter { !it.isMatch }
            .map { it.measureNumber }
            .distinct()

        _uiState.value = _uiState.value.copy(
            isSessionActive = false,
            overallAccuracy = accuracy,
            flaggedMeasures = flagged,
            currentFeedback = PitchFeedback.INACTIVE
        )
    }

    fun resetSession() {
        _uiState.value = PracticeUiState()
    }
}