package com.cit.kaido.voxsight.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

class PracticeViewModel : ViewModel() {
    private val _isMicrophoneEnabled = MutableStateFlow(false)
    val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _showPauseModal = MutableStateFlow(false)
    val showPauseModal: StateFlow<Boolean> = _showPauseModal.asStateFlow()

    // Map<Int, Boolean> (Note Index -> WasCorrect)
    private val _pitchAttempts = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val pitchAttempts: StateFlow<Map<Int, Boolean>> = _pitchAttempts.asStateFlow()

    // ── Score & Playback State (Phase 4) ────────────────────────
    private val _currentScore = MutableStateFlow<MusicXmlScore?>(null)
    val currentScore: StateFlow<MusicXmlScore?> = _currentScore.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun setMicrophoneEnabled(enabled: Boolean) {
        _isMicrophoneEnabled.value = enabled
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

    fun recordPitchAttempt(noteIndex: Int, wasCorrect: Boolean) {
        val currentMap = _pitchAttempts.value.toMutableMap()
        currentMap[noteIndex] = wasCorrect
        _pitchAttempts.value = currentMap
    }

    fun calculateAccuracy(): Int {
        val attempts = _pitchAttempts.value
        if (attempts.isEmpty()) return 0
        val correctCount = attempts.values.count { it }
        return ((correctCount.toFloat() / attempts.size) * 100).toInt()
    }
}
