package com.cit.kaido.voxsight.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cit.kaido.voxsight.pitch.PitchAttempt
import com.cit.kaido.voxsight.pitch.PitchComparator
import com.cit.kaido.voxsight.pitch.PitchDetectionEngine
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.launch

enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

class PracticeViewModel : ViewModel() {
    val pitchEngine = PitchDetectionEngine()

    private val _isMicrophoneEnabled = MutableStateFlow(false)
    val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _showPauseModal = MutableStateFlow(false)
    val showPauseModal: StateFlow<Boolean> = _showPauseModal.asStateFlow()

    private val _pitchAttempts = MutableStateFlow<List<PitchAttempt>>(emptyList())
    val pitchAttempts: StateFlow<List<PitchAttempt>> = _pitchAttempts.asStateFlow()

    private val _pitchUiState = MutableStateFlow<com.cit.kaido.voxsight.pitch.PitchUiState>(com.cit.kaido.voxsight.pitch.PitchUiState.Idle)
    val pitchUiState: StateFlow<com.cit.kaido.voxsight.pitch.PitchUiState> = _pitchUiState.asStateFlow()

    // ── Score & Playback State ────────────────────────
    private val _currentScore = MutableStateFlow<MusicXmlScore?>(null)
    val currentScore: StateFlow<MusicXmlScore?> = _currentScore.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    data class ActivePitchTarget(
        val eventId: String,
        val targetHz: Float,
        val satbVoice: com.cit.kaido.voxsight.model.SATBVoice,
        val noteName: String = ""
    )

    private val _activeTargets = MutableStateFlow<List<ActivePitchTarget>>(emptyList())
    val activeTargets: StateFlow<List<ActivePitchTarget>> = _activeTargets.asStateFlow()

    private var stableMatchFrames = 0
    private var confirmationContinuations = mutableMapOf<String, kotlin.coroutines.Continuation<Unit>>()

    init {
        viewModelScope.launch {
            pitchEngine.detectedPitchHz.collect { hz ->
                val targets = _activeTargets.value
                if (targets.isNotEmpty()) {
                    var anyMatch = false
                    
                    // Evaluate against all active targets
                    for (target in targets) {
                        val deviation = if (hz > 0f) PitchComparator.calculateCentDeviation(hz, target.targetHz) else 9999f
                        val isMatch = if (hz > 0f) PitchComparator.isMatch(deviation) else false
                        
                        if (isMatch) anyMatch = true
                        
                        val attempt = PitchAttempt(
                            eventId = target.eventId,
                            targetHz = target.targetHz,
                            detectedHz = hz,
                            deviationCents = deviation,
                            isMatch = isMatch,
                            timestampMs = System.currentTimeMillis(),
                            noteName = target.noteName
                        )
                        
                        val currentList = _pitchAttempts.value.toMutableList()
                        currentList.add(attempt)
                        _pitchAttempts.value = currentList
                    }
                    
                    // Persistence Window Logic (Stable frames)
                    if (anyMatch) {
                        stableMatchFrames++
                        if (stableMatchFrames >= 8) {
                            // Target achieved! Resume suspended coroutines for these targets
                            val achievedIds = targets.map { it.eventId }
                            achievedIds.forEach { eventId ->
                                confirmationContinuations.remove(eventId)?.resume(Unit)
                            }
                            // Clear targets since they are completed
                            _activeTargets.value = emptyList()
                        }
                    } else {
                        stableMatchFrames = 0
                    }
                    // PitchUiState update
                    if (hz <= 0f) {
                        _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Listening
                    } else {
                        val firstTarget = targets.first() // Use first active target for UI indicator
                        val deviation = PitchComparator.calculateCentDeviation(hz, firstTarget.targetHz)
                        val result = com.cit.kaido.voxsight.pitch.FeedbackResult(
                            isMatch = PitchComparator.isMatch(deviation),
                            deviationCents = deviation
                        )
                        _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Active(
                            result = result,
                            targetNoteName = firstTarget.noteName
                        )
                    }

                } else {
                    stableMatchFrames = 0
                    _pitchUiState.value = if (_isMicrophoneEnabled.value) {
                        com.cit.kaido.voxsight.pitch.PitchUiState.Listening
                    } else {
                        com.cit.kaido.voxsight.pitch.PitchUiState.Idle
                    }
                }
            }
        }
    }

    fun startPitchSession() {
        _pitchAttempts.value = emptyList()
        if (_isMicrophoneEnabled.value) {
            pitchEngine.start()
            _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Listening
        }
    }

    fun endPitchSession() {
        pitchEngine.stop()
        _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Idle
    }

    fun setPitchTargets(targets: List<ActivePitchTarget>) {
        _activeTargets.value = targets
        stableMatchFrames = 0
    }

    suspend fun waitForPitchConfirmation(eventId: String) {
        // If we are not recording, or the event isn't an active target, return immediately
        if (!_isMicrophoneEnabled.value) return
        
        // Timeout safeguard to prevent indefinite freezing (e.g. 5 seconds)
        try {
            kotlinx.coroutines.withTimeout(5000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    confirmationContinuations[eventId] = continuation
                    continuation.invokeOnCancellation {
                        confirmationContinuations.remove(eventId)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Target not achieved in time, proceed anyway so app doesn't break
            confirmationContinuations.remove(eventId)
            _activeTargets.value = emptyList()
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        _isMicrophoneEnabled.value = enabled
        if (!enabled) {
            pitchEngine.stop()
            _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Idle
        } else {
            if (_playbackState.value == PlaybackState.PLAYING) {
                 _pitchUiState.value = com.cit.kaido.voxsight.pitch.PitchUiState.Listening
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setShowPauseModal(show: Boolean) {
        _showPauseModal.value = show
    }

    fun setCurrentScore(score: MusicXmlScore?) {
        _currentScore.value = score
    }

    fun setPlaybackProgress(progress: Float) {
        _playbackProgress.value = progress.coerceIn(0f, 1f)
    }

    fun setPlaybackState(state: PlaybackState) {
        _playbackState.value = state
    }

    fun getSessionSummary(): com.cit.kaido.voxsight.ui.screens.practice.SessionSummary {
        val attempts = _pitchAttempts.value
        val uniqueNoteAttempts = attempts.groupBy { it.eventId }
        val count = uniqueNoteAttempts.size
        
        // Count as correct if any attempt for that eventId was a match
        val correctNotes = uniqueNoteAttempts.values.count { noteAttempts ->
            noteAttempts.any { it.isMatch }
        }

        // Get the best attempt per event to calculate average deviation
        val bestAttempts = uniqueNoteAttempts.values.mapNotNull { noteAttempts ->
            noteAttempts.minByOrNull { kotlin.math.abs(it.deviationCents) }
        }
        val avgDev = if (bestAttempts.isNotEmpty()) {
            bestAttempts.map { kotlin.math.abs(it.deviationCents) }.average().toFloat()
        } else {
            0f
        }

        // Calculate Problematic Notes
        // Group all attempts by noteName, filter out empty ones
        val attemptsByNote = attempts.filter { it.noteName.isNotBlank() }.groupBy { it.noteName }
        val problematicNotes = attemptsByNote.mapNotNull { (noteName, noteAttempts) ->
            // Only consider it problematic if the overall average deviation is high (> 30 cents maybe)
            val avgNoteDev = noteAttempts.map { it.deviationCents }.average().toFloat()
            if (kotlin.math.abs(avgNoteDev) > 20f) {
                com.cit.kaido.voxsight.ui.screens.practice.ProblematicNote(
                    noteName = noteName,
                    averageDeviation = avgNoteDev,
                    isSharp = avgNoteDev > 0
                )
            } else null
        }.sortedByDescending { kotlin.math.abs(it.averageDeviation) }.take(3)

        // Calculate Vocal Highlights
        val successfulAttempts = attempts.filter { it.isMatch && it.noteName.isNotBlank() }
        val vocalHighlight = if (successfulAttempts.isNotEmpty()) {
            val highest = successfulAttempts.maxByOrNull { it.targetHz }?.noteName ?: ""
            val lowest = successfulAttempts.minByOrNull { it.targetHz }?.noteName ?: ""
            if (highest != lowest) {
                com.cit.kaido.voxsight.ui.screens.practice.VocalHighlight(highest, lowest)
            } else null
        } else null

        return com.cit.kaido.voxsight.ui.screens.practice.SessionSummary(
            totalNotesAttempted = count,
            correctNotes = correctNotes,
            averageDeviationCents = avgDev,
            problematicNotes = problematicNotes,
            vocalHighlight = vocalHighlight
        )
    }

    fun calculateAccuracy(): Int {
        return getSessionSummary().accuracyPercentage.toInt()
    }

    override fun onCleared() {
        super.onCleared()
        pitchEngine.stop()
    }
}
